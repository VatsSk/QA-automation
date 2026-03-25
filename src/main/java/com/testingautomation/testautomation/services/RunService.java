package com.testingautomation.testautomation.services;


import com.testingautomation.testautomation.config.StorageProperties;
import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.dto.responseDto.PagedResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResultsResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.mapper.EntityMapper;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.Scenario;
import com.testingautomation.testautomation.model.ScenarioStatus;
import com.testingautomation.testautomation.orchestratorService.ScenarioOrchestratorService;
import com.testingautomation.testautomation.pojo.RunFilterParams;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.requestDto.RunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final EntityMapper mapper;
    private final RunnerService runnerService;
    private final ScenarioOrchestratorService scenarioOrchestratorService;
    private final StorageProperties storageProperties;
    private final S3Client s3Client;

    // ── Filtered list ─────────────────────────────────────────────────

    public PagedResponse<RunResponse> getFilteredRuns(RunFilterParams params) {
        List<Run> runs = runRepository.findByFilters(params);
        long total = runRepository.countByFilters(params);

        int totalPages = (int) Math.ceil((double) total / params.getSize());

        return PagedResponse.<RunResponse>builder()
                .results(mapper.toRunResponseList(runs))
                .totalCount(total)
                .page(params.getPage())
                .size(params.getSize())
                .totalPages(totalPages)
                .hasNext(params.getPage() < totalPages - 1)
                .hasPrevious(params.getPage() > 0)
                .build();
    }

    // ── Filter metadata (for dropdown population) ─────────────────────

    public Map<String, Object> getFilterMeta(String projectId, String moduleId) {
        return runRepository.aggregateFilterMeta(projectId, moduleId);
    }

    // ── Single run ────────────────────────────────────────────────────

    public RunResponse getRunById(String id) {
        return mapper.toRunResponse(findRunOrThrow(id));
    }

    // ── Create ────────────────────────────────────────────────────────

    public RunResponse createRun(String projectId, String moduleId, RunRequest request) {
        Run run = mapper.toRun(request);
        run.setProjectId(projectId);
        run.setModuleId(moduleId);
        run.setStatus(RunStatus.DRAFT);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        assignScenarioIds(run);
        run.setScenarioCount(run.getScenariosList() != null ? run.getScenariosList().size() : 0);

        Run saved = runRepository.save(run);
        log.info("Created run {} in project={} module={}", saved.getId(), projectId, moduleId);
        return mapper.toRunResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────────────

    public RunResponse updateRun(String id, RunRequest request) {
        Run run = findRunOrThrow(id);
        if (run.getStatus() == RunStatus.RUNNING) {
            throw new GlobalExceptionHandler.BadRequestException("Cannot edit a run that is currently RUNNING");
        }
        mapper.updateRunFromRequest(request, run);
        run.setUpdatedAt(Instant.now());
        if (run.getScenariosList() != null) {
            assignScenarioIds(run);
            run.setScenarioCount(run.getScenariosList().size());
        }
        return mapper.toRunResponse(runRepository.save(run));
    }

    // ── Delete ────────────────────────────────────────────────────────

    public void deleteRun(String id) {
        Run run = findRunOrThrow(id);
        runRepository.delete(run);
        log.info("Deleted run {}", id);
    }

    // ── Clone ─────────────────────────────────────────────────────────

    /**
     * Deep-clones the run configuration into a new DRAFT run.
     * resultStatement, scenariosList config, tags, metadata, runType are all copied.
     * Result fields (resultCsv, screenshots, scenario statuses) are cleared.
     */
    public RunResponse cloneRun(String id) {
        Run original = findRunOrThrow(id);

        Run clone = Run.builder()
                .runName(original.getRunName() + " (Clone)")
                .createdBy(original.getCreatedBy())
                .projectId(original.getProjectId())
                .moduleId(original.getModuleId())
                .status(RunStatus.DRAFT)
                .runType(original.getRunType())
                .resultStatement(original.getResultStatement())   // copied from original
                .metadata(original.getMetadata() != null ? new HashMap<>(original.getMetadata()) : null)
                .tags(original.getTags() != null ? new ArrayList<>(original.getTags()) : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Deep-clone scenarios — clear result fields
        if (original.getScenariosList() != null) {
            List<Scenario> clonedScenarios = original.getScenariosList().stream()
                    .map(this::cloneScenario)
                    .collect(Collectors.toList());
            clone.setScenariosList(clonedScenarios);
            clone.setScenarioCount(clonedScenarios.size());
        }

        Run saved = runRepository.save(clone);
        log.info("Cloned run {} -> new run {}", id, saved.getId());
        return mapper.toRunResponse(saved);
    }

    // ── Execute ───────────────────────────────────────────────────────

    /**
     * Triggers execution of the run via POST /runner/run-auth.
     * resultStatement from the Run document is appended as a query param.
     * Run state is updated to RUNNING immediately, then updated again on completion.
     */
    public RunResponse executeRun(String id) {
        log.info("RUN STARTED for runId={}", id);

        Run run = findRunOrThrow(id);

        if (run.getStatus() == RunStatus.RUNNING) {
            throw new GlobalExceptionHandler.BadRequestException("Run is already in RUNNING state");
        }

        if (run.getScenariosList() == null || run.getScenariosList().isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException("Cannot execute a run with no scenarios");
        }

        WebDriver driver = null;
        Run updated = run;

        try {
            updated.setStatus(RunStatus.RUNNING);
            updated.setUpdatedAt(java.time.Instant.now());
            updated = runRepository.save(updated);

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1366,768");

            driver = new ChromeDriver(options);

            updated = scenarioOrchestratorService.executeScenarios(updated, driver, id);

            if (updated.getStatus() == RunStatus.RUNNING) {
                updated.setStatus(RunStatus.PASSED);
                updated.setReason("All scenarios executed successfully");
                updated.setUpdatedAt(java.time.Instant.now());
                updated = runRepository.save(updated);
            }

            log.info("Executing run {} completed successfully (resultStatement='{}')",
                    id, updated.getResultStatement());

            return mapper.toRunResponse(updated);

        } catch (GlobalExceptionHandler.ResourceNotFoundException |
                 GlobalExceptionHandler.BadRequestException ex) {

            markRunFailed(updated, ex.getMessage());
            throw ex;

        } catch (GlobalExceptionHandler.RunnerIntegrationException ex) {

            markRunFailed(updated, ex.getMessage());
            throw ex;

        } catch (org.openqa.selenium.TimeoutException ex) {

            String message = "Test execution failed: target web page is slow or element did not become visible in time";
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (org.openqa.selenium.NoSuchElementException ex) {

            String message = "Test execution failed: required element not found on target web page";
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (org.openqa.selenium.ElementClickInterceptedException ex) {

            String message = "Test execution failed: element click was intercepted by another UI element";
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (org.openqa.selenium.ElementNotInteractableException ex) {

            String message = "Test execution failed: element exists but is not interactable on target web page";
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (org.openqa.selenium.StaleElementReferenceException ex) {

            String message = "Test execution failed: page updated and element reference became stale";
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (org.openqa.selenium.WebDriverException ex) {

            String message = "Browser automation failed during test execution: " + ex.getMessage();
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } catch (Exception ex) {

            String message = "Unexpected execution failure: " + ex.getMessage();
            markRunFailed(updated, message);
            throw new GlobalExceptionHandler.RunnerIntegrationException(message, ex);

        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    log.info("Browser closed for runId={}", id);
                } catch (Exception quitEx) {
                    log.warn("Failed to close browser for runId={}: {}", id, quitEx.getMessage(), quitEx);
                }
            }
        }
    }
    private void markRunFailed(Run run, String failureReason) {
        try {
            run.setStatus(RunStatus.FAILED);
            run.setReason(failureReason); // Store failure reason in reason field, not resultStatement
            run.setUpdatedAt(java.time.Instant.now());
            runRepository.save(run);
        } catch (Exception dbEx) {
            log.error("Failed to update run status to FAILED for runId={}: {}",
                    run.getId(), dbEx.getMessage(), dbEx);
        }
    }

    // ── Results ───────────────────────────────────────────────────────

    public RunResultsResponse getRunResults(String id) {
        Run run = findRunOrThrow(id);

        Map<ScenarioStatus, Long> statusCounts = new EnumMap<>(ScenarioStatus.class);
        List<String> allScreenshots = new ArrayList<>();
        List<String> allResultCsvs  = new ArrayList<>();

        if (run.getScenariosList() != null) {
            for (Scenario s : run.getScenariosList()) {
                if (s.getStatus() != null) {
                    statusCounts.merge(s.getStatus(), 1L, Long::sum);
                }
                if (s.getScreenshots() != null) allScreenshots.addAll(s.getScreenshots());
                if (StringUtils.hasText(s.getResultCsv()))  allResultCsvs.add(s.getResultCsv());
            }
        }

        return RunResultsResponse.builder()
                .runId(run.getId())
                .runName(run.getRunName())
                .runStatus(run.getStatus())
                .totalScenarios(run.getScenarioCount())
                .scenarioStatusCounts(statusCounts)
                .allScreenshots(allScreenshots)
                .allResultCsvs(allResultCsvs)
                .resultStatement(run.getResultStatement())   // from Run top-level field
                .reason(run.getReason())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Run findRunOrThrow(String id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException("Run not found: " + id));
    }

    private void assignScenarioIds(Run run) {
        if (run.getScenariosList() == null) return;
        for (int i = 0; i < run.getScenariosList().size(); i++) {
            Scenario s = run.getScenariosList().get(i);
            if (!StringUtils.hasText(s.getId())) {
                s.setId(UUID.randomUUID().toString());
            }
            if (s.getSequenceNo() == null) {
                s.setSequenceNo(i + 1);
            }
            if (s.getCreatedAt() == null) s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
        }
    }

    private Scenario cloneScenario(Scenario original) {
        return Scenario.builder()
                .id(UUID.randomUUID().toString())
                .type(original.getType())
                .sequenceNo(original.getSequenceNo())
                .url(original.getUrl())
                .cssOpener(original.getCssOpener())
                .value(original.getValue())
                .statement(original.getStatement())
                .csv(original.getCsv())
                .manualTestCases(original.getManualTestCases())
                // result fields cleared:
                .resultCsv(null)
                .screenshots(null)
                .status(ScenarioStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .assertions(original.getAssertions())
                .build();
    }



    public List<LinkedHashMap<String, String>> getScenarioWiseResult(String s3Path) {
        try {
            // 1) Convert full S3 URL -> S3 object key
            String key = extractKeyFromS3Url(s3Path);

            // 2) Fetch object from S3 using credentials configured in S3Client
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(request);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                // 3) Read header row
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    return List.of();
                }

                List<String> headers = parseCsvLine(headerLine);

                // remove UTF-8 BOM from first header if present
                if (!headers.isEmpty()) {
                    headers.set(0, removeBom(headers.get(0)));
                }

                // 4) Read all rows
                List<LinkedHashMap<String, String>> result = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    List<String> values = parseCsvLine(line);

                    LinkedHashMap<String, String> rowMap = new LinkedHashMap<>();
                    for (int i = 0; i < headers.size(); i++) {
                        String header = headers.get(i);
                        String value = i < values.size() ? values.get(i) : "";
                        rowMap.put(header, value);
                    }

                    result.add(rowMap);
                }

                return result;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load scenario result CSV from S3 path: " + s3Path, e);
        }
    }

    /**
     * Extract S3 key from full S3 URL.
     *
     * Example input:
     * https://interns-tf-project.s3.ap-southeast-1.amazonaws.com/qa_automation/.../scenario-results.csv
     *
     * Output:
     * qa_automation/.../scenario-results.csv
     */
    private String extractKeyFromS3Url(String s3Path) {
        try {
            URI uri = URI.create(s3Path);

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Invalid S3 URL: empty path");
            }

            // remove leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            String basePrefix = storageProperties.getBasePrefix();

            // If basePrefix exists and path does not already contain it, prepend it
            if (basePrefix != null && !basePrefix.isBlank()) {
                String normalizedPrefix = basePrefix.endsWith("/")
                        ? basePrefix.substring(0, basePrefix.length() - 1)
                        : basePrefix;

                if (!path.startsWith(normalizedPrefix + "/") && !path.equals(normalizedPrefix)) {
                    path = normalizedPrefix + "/" + path;
                }
            }

            return path;

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid S3 URL: " + s3Path, e);
        }
    }

    /**
     * CSV parser supporting quoted values and escaped quotes ("")
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                // escaped quote inside quoted value
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        result.add(current.toString());
        return result;
    }

    /**
     * Remove UTF-8 BOM if present in first header
     */
    private String removeBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }


}
