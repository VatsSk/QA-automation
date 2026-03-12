package com.testingautomation.testautomation.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingautomation.testautomation.config.RunnerProperties;
import com.testingautomation.testautomation.config.StorageProperties;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.Scenario;
import com.testingautomation.testautomation.repo.RunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Integrates with the EXISTING execution endpoint:
 *
 *   POST /runner/run-auth
 *   Content-Type: multipart/form-data
 *
 *   Parts:
 *     - testConfiguration (JSON part) : TestConfigPayload mapped from our Run
 *     - testResultStatement (@RequestParam): run.resultStatement
 *
 *   Response: application/octet-stream ZIP file containing screenshots + CSVs
 *
 * Flow:
 *   1. Save run to DB with status=RUNNING  (called by RunService before this)
 *   2. Build multipart request
 *   3. POST to /runner/run-auth
 *   4. Receive ZIP, unzip, upload each file to S3
 *   5. Store S3 keys back on the Run scenarios
 *   6. Mark run PASSED / FAILED / PARTIAL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerService {

    private final RestTemplate       runnerRestTemplate;
    private final RunnerProperties runnerProperties;
    private final RunRepository runRepository;
    private final S3Client           s3Client;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    // ── Entry point ────────────────────────────────────────────────────────

    /**
     * Call POST /runner/run-auth with multipart/form-data.
     * Caller (RunService) must have already set status=RUNNING and saved.
     */
    public Run executeRun(Run run) {
        String runnerUrl = runnerProperties.getBaseUrl() + runnerProperties.getRunAuthPath();
        log.info("Calling runner [{}] for runId={}", runnerUrl, run.getId());

        try {
            // 1. Build multipart body
            HttpEntity<MultiValueMap<String, Object>> request = buildMultipartRequest(run);

            // 2. Call runner — response is a raw ZIP byte[]
            ResponseEntity<byte[]> response = runnerRestTemplate.exchange(
                    runnerUrl,
                    HttpMethod.POST,
                    request,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Runner returned ZIP ({} bytes) for runId={}", response.getBody().length, run.getId());
                processZipResponse(run, response.getBody());
                return markStatus(run);
            } else {
                log.warn("Runner returned non-2xx {} for runId={}", response.getStatusCode(), run.getId());
                return failRun(run, "Runner returned " + response.getStatusCode());
            }

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("Runner HTTP error for runId={}: {} — {}", run.getId(), ex.getStatusCode(), ex.getResponseBodyAsString());
            return failRun(run, ex.getStatusCode() + ": " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Runner call failed for runId={}: {}", run.getId(), ex.getMessage(), ex);
            return failRun(run, ex.getMessage());
        }
    }

    // ── Build multipart request ─────────────────────────────────────────────

    /**
     * Builds the multipart/form-data request the runner expects:
     *
     *   @RequestPart("testConfiguration") TestConfigPayload payload
     *   @RequestParam("testResultStatement") String successMsg
     */
    private HttpEntity<MultiValueMap<String, Object>> buildMultipartRequest(Run run) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Part 1: testConfiguration as JSON part
        String configJson = objectMapper.writeValueAsString(buildTestConfigPayload(run));
        ByteArrayResource jsonPart = new ByteArrayResource(configJson.getBytes()) {
            @Override public String getFilename() { return "testConfiguration"; }
        };
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("testConfiguration", new HttpEntity<>(jsonPart, jsonHeaders));

        // Part 2: testResultStatement as form field
        // resultStatement lives on Run, NOT on individual scenarios
        String stmt = run.getResultStatement() != null ? run.getResultStatement() : "";
        body.add("testResultStatement", stmt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Maps our Run → the TestConfigPayload shape the runner expects.
     * Mirrors the runner's TestConfigPayload fields based on the controller signature.
     */
    private Map<String, Object> buildTestConfigPayload(Run run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId",     run.getId());
        payload.put("runName",   run.getRunName());
        payload.put("projectId", run.getProjectId());
        payload.put("moduleId",  run.getModuleId());
        payload.put("runType",   run.getRunType());
        payload.put("metadata",  run.getMetadata() != null ? run.getMetadata() : Map.of());

        List<Map<String, Object>> scenarios = new ArrayList<>();
        if (run.getScenariosList() != null) {
            for (Scenario s : run.getScenariosList()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("id",          s.getId());
                sm.put("type",        s.getType() != null ? s.getType().name() : null);
                sm.put("sequenceNo",  s.getSequenceNo());
                sm.put("url",         s.getUrl());
                sm.put("cssOpener",   s.getCssOpener());
                sm.put("value",       s.getValue());
                sm.put("statement",   s.getStatement());
                sm.put("csv",         s.getCsv());
                sm.put("manualTestCases", s.getManualTestCases());
                scenarios.add(sm);
            }
        }
        payload.put("scenarios", scenarios);
        return payload;
    }

    // ── Process ZIP response ───────────────────────────────────────────────

    /**
     * Unzips the runner response and uploads each entry to S3.
     * Files are grouped back to scenarios by matching naming conventions
     * (runner typically names files: {runId}/{scenarioId}/screenshot_N.png, result.csv etc.)
     */
    private void processZipResponse(Run run, byte[] zipBytes) {
        // Map scenarioId -> scenario for fast lookup
        Map<String, Scenario> scenarioMap = new HashMap<>();
        if (run.getScenariosList() != null) {
            run.getScenariosList().forEach(s -> scenarioMap.put(s.getId(), s));
            // Init result lists
            run.getScenariosList().forEach(s -> {
                if (s.getScreenshots() == null) s.setScreenshots(new ArrayList<>());
            });
        }

        List<String> allKeys = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }

                byte[] fileBytes = zis.readAllBytes();
                String entryName = entry.getName();          // e.g. run_1234/scen-id/screenshot_1.png
                String s3Key     = "results/" + run.getId() + "/" + entryName;

                // Upload to S3
                uploadToS3(s3Key, fileBytes, guessMime(entryName));
                allKeys.add(s3Key);
                log.debug("Uploaded result file to S3: {}", s3Key);

                // Attach to the right scenario
                attachToScenario(scenarioMap, run, s3Key, entryName);
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to process ZIP for runId={}: {}", run.getId(), e.getMessage(), e);
            // Don't fail the whole run — partial results are better than nothing
        }

        log.info("Processed {} result files from ZIP for runId={}", allKeys.size(), run.getId());
    }

    /**
     * Tries to figure out which scenario a ZIP entry belongs to by matching
     * the entry path against known scenario IDs. Falls back to attaching to
     * the run-level list if no match found.
     */
    private void attachToScenario(Map<String, Scenario> scenarioMap, Run run,
                                  String s3Key, String entryName) {
        // Runner names files like: {runId}/{scenarioId}/screenshot_1.png or result.csv
        for (Map.Entry<String, Scenario> e : scenarioMap.entrySet()) {
            if (entryName.contains(e.getKey())) {
                Scenario sc = e.getValue();
                if (entryName.endsWith(".csv") || entryName.contains("result")) {
                    sc.setResultCsv(s3Key);
                } else {
                    if (sc.getScreenshots() == null) sc.setScreenshots(new ArrayList<>());
                    sc.getScreenshots().add(s3Key);
                }
                sc.setActionPerformedAt(Instant.now());
                return;
            }
        }
        // No scenario match — could be a run-level summary file, just log it
        log.debug("Could not match ZIP entry '{}' to a scenario", entryName);
    }

    private void uploadToS3(String key, byte[] bytes, String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(storageProperties.getBucketName())
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(req, RequestBody.fromBytes(bytes));
    }

    private static String guessMime(String name) {
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".csv"))  return "text/csv";
        if (name.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ── Status calculation ─────────────────────────────────────────────────

    /**
     * After processing the ZIP, determine overall run status:
     * - All scenarios PASSED  → PASSED
     * - All scenarios FAILED  → FAILED
     * - Mix                   → PARTIAL
     * - No scenario results   → PASSED (runner returned ZIP, assume success)
     */
    private Run markStatus(Run run) {
        if (run.getScenariosList() == null || run.getScenariosList().isEmpty()) {
            run.setStatus(RunStatus.PASSED);
        } else {
            long failed  = run.getScenariosList().stream()
                    .filter(s -> s.getStatus() != null && s.getStatus().name().equals("FAILED")).count();
            long passed  = run.getScenariosList().stream()
                    .filter(s -> s.getStatus() != null && s.getStatus().name().equals("PASSED")).count();
            long total   = run.getScenariosList().size();

            if (failed == 0)           run.setStatus(RunStatus.PASSED);
            else if (passed == 0)      run.setStatus(RunStatus.FAILED);
            else                       run.setStatus(RunStatus.PARTIAL);
        }
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    private Run failRun(Run run, String reason) {
        run.setStatus(RunStatus.FAILED);
        run.setUpdatedAt(Instant.now());
        if (run.getMetadata() == null) run.setMetadata(new HashMap<>());
        run.getMetadata().put("runnerError", reason);
        return runRepository.save(run);
    }
}