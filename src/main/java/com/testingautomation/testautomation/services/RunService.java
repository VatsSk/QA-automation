package com.testingautomation.testautomation.services;


import com.testingautomation.testautomation.dto.responseDto.PagedResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResultsResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.mapper.EntityMapper;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.Scenario;
import com.testingautomation.testautomation.model.ScenarioStatus;
import com.testingautomation.testautomation.pojo.RunFilterParams;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.requestDto.RunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        Run run = findRunOrThrow(id);

        if (run.getStatus() == RunStatus.RUNNING) {
            throw new GlobalExceptionHandler.BadRequestException("Run is already in RUNNING state");
        }
        if (run.getScenariosList() == null || run.getScenariosList().isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException("Cannot execute a run with no scenarios");
        }

        log.info("Executing run {} (resultStatement='{}')", id, run.getResultStatement());
        Run updated = runnerService.executeRun(run);
        return mapper.toRunResponse(updated);
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
                .build();
    }
}
