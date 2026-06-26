package com.testingautomation.testautomation.services;


import com.testingautomation.testautomation.config.s3Config.StorageProperties;
import com.testingautomation.testautomation.dto.AssertionDto;
import com.testingautomation.testautomation.dto.FilterScenarioDto;
import com.testingautomation.testautomation.dto.responseDto.PagedResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResultsResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.config.mapperConfig.EntityMapper;
import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import com.testingautomation.testautomation.services.AgentService;
import com.testingautomation.testautomation.dto.filterDto.RunFilterParams;
import com.testingautomation.testautomation.repositories.runRepos.RunRepository;
import com.testingautomation.testautomation.dto.requestDto.RunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
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
    private final AgentService agentService;
    private final StorageProperties storageProperties;
    private final S3Client s3Client;
    private final StorageService storageService;
    @Value("${S3_BUCKET}")
    private String bucketName;
    @Value("${S3_BASE_PREFIX}")
    private String prefix;

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
        run.getScenariosList().forEach(scenario -> scenario.setScenarioStatus(RunStatus.DRAFT));

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
        System.out.println("bucket"+bucketName);
        System.out.println("prefix"+prefix);

        Run run = findRunOrThrow(id);
        String projectId = run.getProjectId();
        String moduleId = run.getModuleId();

        String basePrefix=prefix+"/"+ projectId+ "/" + moduleId + "/" + id;
        if(storageService.doesPrefixHaveObjects(bucketName,basePrefix)){
            storageService.deleteFolder(bucketName,basePrefix);
        }
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
                .tags(original.getTags() != null ? new ArrayList<>(original.getTags()) : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        System.out.println("original :" + original);

        // Deep-clone scenarios — clear result fields
        if (original.getScenariosList() != null) {
            List<Scenario> clonedScenarios = original.getScenariosList().stream()
                    .map(this::cloneScenario)
                    .collect(Collectors.toList());
            clone.setScenariosList(clonedScenarios);
            clone.setScenarioCount(clonedScenarios.size());
        }
        System.out.println("clone :" + clone);


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
    public RunResponse executeRun(String id, boolean isBulk) {
        log.info("RUN STARTED for runId={}", id);

        Run run = overwriteRunResults(id, isBulk);

        if (run.getScenariosList() == null || run.getScenariosList().isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException("Cannot execute a run with no scenarios");
        }

        // Delegate execution to the QA Agent running on the user's machine.
        // Backend never creates a WebDriver or executes Selenium.
        agentService.assignRun(id, run.getCreatedBy());

        log.info("Run {} assigned to agent for user {}", id, run.getCreatedBy());
        return mapper.toRunResponse(run);
    }

    public List<RunResponse> getAllRunByCreatedBy(String createdBy){
        List<Run> runs=runRepository.getAllRunsByCreatedBy(createdBy);
        List<RunResponse> runResponses = runs.stream()
                .map(mapper::toRunResponse)   // your mapper method
                .toList();

        return runResponses;
    }
    public List<RunResponse> executeAllRun(List<String> runIds){
        runRepository.updateStatusForRuns(runIds,RunStatus.RUNNING);
        List<RunResponse> runResponses=new ArrayList<>();
        for(String runId:runIds){
            runResponses.add(executeRun(runId,true));
        }
        return runResponses;
    }

    // ── Results ───────────────────────────────────────────────────────

    public RunResultsResponse getRunResults(String id) {
        Run run = findRunOrThrow(id);

        Map<RunStatus, Long> statusCounts = new EnumMap<>(RunStatus.class);
        List<String> allScreenshots = new ArrayList<>();
        List<String> allResultCsvs  = new ArrayList<>();

        if (run.getScenariosList() != null) {
            for (Scenario s : run.getScenariosList()) {
                if (s.getScenarioStatus() != null) {
                    statusCounts.merge(s.getScenarioStatus(), 1L, Long::sum);
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
    public Run overwriteRunResults(String id, boolean isBulk) {

        Run existing = findRunOrThrow(id);

        if (!isBulk && existing.getStatus() == RunStatus.RUNNING) {
            throw new GlobalExceptionHandler.BadRequestException(
                    "Run is already in RUNNING state");
        }

        Run freshRun = new Run();

        // Keep same run id
        freshRun.setId(existing.getId());

        // Preserve run metadata
        freshRun.setRunName(existing.getRunName());
        freshRun.setCreatedBy(existing.getCreatedBy());
        freshRun.setProjectId(existing.getProjectId());
        freshRun.setModuleId(existing.getModuleId());
        freshRun.setRunType(existing.getRunType());
        freshRun.setScenarioCount(existing.getScenarioCount());
        freshRun.setResultStatement(existing.getResultStatement());
        freshRun.setTags(existing.getTags());

        // Reset run execution state
        freshRun.setStatus(RunStatus.RUNNING);
        freshRun.setReason(null);

        freshRun.setCreatedAt(existing.getCreatedAt());
        freshRun.setUpdatedAt(Instant.now());

        List<Scenario> freshScenarios = new ArrayList<>();

        if (existing.getScenariosList() != null) {

            for (Scenario oldScenario : existing.getScenariosList()) {

                Scenario s = new Scenario();

                // Preserve scenario definition
                s.setId(oldScenario.getId());
                s.setType(oldScenario.getType());
                s.setSequenceNo(oldScenario.getSequenceNo());
                s.setUrl(oldScenario.getUrl());
                s.setCsv(oldScenario.getCsv());
                s.setCssOpener(oldScenario.getCssOpener());
                s.setValue(oldScenario.getValue());
                s.setStatement(oldScenario.getStatement());
                s.setClickCss(oldScenario.getClickCss());
                s.setApplyFilterBtn(oldScenario.getApplyFilterBtn());
                s.setColumns(oldScenario.getColumns());
                s.setSaveBtnCss(oldScenario.getSaveBtnCss());
                s.setFinalVerify(oldScenario.getFinalVerify());
                s.setInitialVerify(oldScenario.getInitialVerify());
                s.setFinalVerifyResultMap(oldScenario.getFinalVerifyResultMap());
                s.setInitialVerifyResultMap(oldScenario.getInitialVerifyResultMap());
                s.setScenarioStatus(RunStatus.DRAFT);


                // Copy filters
                if (oldScenario.getFilters() != null) {
                    List<FilterScenarioDto> filters =
                            oldScenario.getFilters()
                                    .stream()
                                    .map(this::cloneFilter)
                                    .collect(Collectors.toList());

                    s.setFilters(filters);
                }

                // Copy assertions and clear execution results
                if (oldScenario.getAssertions() != null) {

                    List<AssertionDto> assertions =
                            oldScenario.getAssertions()
                                    .stream()
                                    .map(this::cloneAssertion)
                                    .collect(Collectors.toList());

                    s.setAssertions(assertions);
                }

                // Clear scenario execution results
                s.setResultCsv(null);
                s.setScreenshots(null);

                s.setCreatedAt(oldScenario.getCreatedAt());
                s.setUpdatedAt(Instant.now());

                freshScenarios.add(s);
            }
        }

        freshRun.setScenariosList(freshScenarios);

        return runRepository.save(freshRun);
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
                .scenarioStatus(RunStatus.DRAFT)
                .finalVerify(original.getFinalVerify())
                .initialVerify(original.getInitialVerify())
                .verificationStatus(RunStatus.DRAFT)
                // ✅ FIX: clone filters
                .filters(
                        original.getFilters() != null
                                ? original.getFilters().stream()
                                .map(this::cloneFilter)
                                .collect(Collectors.toList())
                                : null
                )

                // ✅ FIX: clone apply button
                .applyFilterBtn(original.getApplyFilterBtn())
                // result fields cleared:
                .resultCsv(null)
                .screenshots(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .assertions(
                        original.getAssertions() != null
                                ? original.getAssertions().stream()
                                .map(this::cloneAssertion)
                                .collect(Collectors.toList())
                                : null
                )
                .build();
    }
    private AssertionDto cloneAssertion(AssertionDto a) {
        return AssertionDto.builder()
                .type(a.getType())
                .locator(a.getLocator())
                .expected(a.getExpected())
                .columnName(a.getColumnName())
                .tableId(a.getTableId())
                .rowsBtn(a.getRowsBtn())
                .order(a.getOrder())
                .prompt(a.getPrompt())
                .build();
    }

    private FilterScenarioDto cloneFilter(FilterScenarioDto f) {
        FilterScenarioDto copy = new FilterScenarioDto();
        copy.setQuerySelector(f.getQuerySelector());
        copy.setColumnName(f.getColumnName());
        copy.setFilterType(f.getFilterType());
        copy.setOperation(f.getOperation());
        copy.setValue(f.getValue());
        copy.setValueSelector(f.getValueSelector());
        copy.setLogicalOperator(f.getLogicalOperator());
        return copy;
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
