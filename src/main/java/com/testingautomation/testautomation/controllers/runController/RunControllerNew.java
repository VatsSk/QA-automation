package com.testingautomation.testautomation.controllers.runController;


import com.testingautomation.testautomation.dto.responseDto.PagedResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResultsResponse;
import com.testingautomation.testautomation.dto.responseDto.ScenarioScreenshotsResponse;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.enums.ScenarioType;
import com.testingautomation.testautomation.dto.filterDto.RunFilterParams;
import com.testingautomation.testautomation.dto.requestDto.RunRequest;
import com.testingautomation.testautomation.services.RunService;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor

public class RunControllerNew {
    private final Logger log = LoggerFactory.getLogger(RunControllerNew.class);

    private final RunService runService;
    private final ScreenshotService screenshotService;

    @Value("${pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${pagination.max-page-size:100}")
    private int maxPageSize;

    // ── Filtered run list ──────────────────────────────────────────────

    /**
     * GET /api/projects/{projectId}/modules/{moduleId}/runs
     *
     * Combinable filter params:
     *   status       — comma-separated: DRAFT,RUNNING,PASSED,FAILED,PARTIAL
     *   type         — comma-separated scenario types: URL,MODAL,URL_NAV,MODAL_NAV,SEARCH_NAV
     *   createdBy    — exact user id
     *   search       — text search across runName + scenario statements
     *   from         — ISO-8601 lower bound for createdAt
     *   to           — ISO-8601 upper bound for createdAt
     *   tag          — repeatable: &tag=smoke&tag=regression
     *   page         — 0-indexed (default 0)
     *   size         — page size (default 20, max 100)
     *   sort         — field name, prefix - for DESC: -createdAt, createdAt, status, runName
     */
    @GetMapping("/api/projects/{projectId}/modules/{moduleId}/runs")
    public ResponseEntity<PagedResponse<RunResponse>> getRuns(
            @PathVariable String projectId,
            @PathVariable String moduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) List<String> tag,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(required = false)    Integer size,
            @RequestParam(defaultValue = "-createdAt") String sort) {

        int resolvedSize = resolveSize(size);

        RunFilterParams params = RunFilterParams.builder()
                .projectId(projectId)
                .moduleId(moduleId)
                .statuses(parseStatuses(status))
                .types(parseTypes(type))
                .createdBy(StringUtils.hasText(createdBy) ? createdBy : null)
                .search(StringUtils.hasText(search) ? search.trim() : null)
                .from(from)
                .to(to)
                .tags(tag)
                .page(Math.max(0, page))
                .size(resolvedSize)
                .sort(sort)
                .build();

        return ResponseEntity.ok(runService.getFilteredRuns(params));
    }

    @GetMapping("/api/runlist/{createdBy}")
    public ResponseEntity<List<RunResponse>> getAllRuns(@PathVariable String createdBy){
        log.info("api/runs/{createdBy} API called using :{}",createdBy);
        List<RunResponse> runResponses=runService.getAllRunByCreatedBy(createdBy);
        return ResponseEntity.ok(runResponses);
    }

    @PostMapping("/api/runs/execute-queue")
    public ResponseEntity<?> executeQueue(@RequestBody List<String> runIds) {

        log.info("Executing queue: {}", runIds);
        runService.executeAllRun(runIds);
        return ResponseEntity.ok().build();
    }

    // ── Filter meta (for dropdown population) ─────────────────────────

    @GetMapping("/api/runs/filters/meta")
    public ResponseEntity<Map<String, Object>> getFilterMeta(
            @RequestParam String projectId,
            @RequestParam String moduleId) {
        return ResponseEntity.ok(runService.getFilterMeta(projectId, moduleId));
    }

    // ── Single run ────────────────────────────────────────────────────

    @GetMapping("/api/runs/{id}")
    public ResponseEntity<RunResponse> getRun(@PathVariable String id) {
        return ResponseEntity.ok(runService.getRunById(id));
    }

    // ── Create ────────────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/modules/{moduleId}/runs")
    public ResponseEntity<RunResponse> createRun(
            @PathVariable String projectId,
            @PathVariable String moduleId,
            @Valid @RequestBody RunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(runService.createRun(projectId, moduleId, request));
    }

    // ── Update ────────────────────────────────────────────────────────

    @PutMapping("/api/runs/{id}")
    public ResponseEntity<RunResponse> updateRun(
            @PathVariable String id,
            @Valid @RequestBody RunRequest request) {
        return ResponseEntity.ok(runService.updateRun(id, request));
    }

    // ── Delete ────────────────────────────────────────────────────────

    @DeleteMapping("/api/runs/{id}")
    public ResponseEntity<Void> deleteRun(@PathVariable String id) {
        runService.deleteRun(id);
        return ResponseEntity.noContent().build();
    }

    // ── Clone ─────────────────────────────────────────────────────────

    @PostMapping("/api/runs/{id}/clone")
    public ResponseEntity<RunResponse> cloneRun(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(runService.cloneRun(id));
    }

    // ── Execute (calls /runner/run-auth) ──────────────────────────────

    /**
     * POST /api/runs/{id}/execute
     *
     * Builds payload from stored run config.
     * Appends run.resultStatement as query param to POST /runner/run-auth.
     * Updates Run status in Mongo based on runner response.
     */
    @PostMapping("/api/runs/{id}/execute")
    public ResponseEntity<RunResponse> executeRun(@PathVariable String id) {
        return ResponseEntity.ok(runService.executeRun(id,false));
    }

    // ── Results ───────────────────────────────────────────────────────

    @GetMapping("/api/runs/{id}/results")
    public ResponseEntity<RunResultsResponse> getRunResults(@PathVariable String id) {
        return ResponseEntity.ok(runService.getRunResults(id));
    }

    @GetMapping("/api/runs/scenario/result")
    public ResponseEntity<List<LinkedHashMap<String,String>>> getScenarioWiseResult(@RequestParam("s3Path") String s3Path) {
        return ResponseEntity.ok(runService.getScenarioWiseResult(s3Path));
    }

    @GetMapping("/scenario-screenshots")
    public ResponseEntity<ScenarioScreenshotsResponse> getScenarioScreenshots(
            @RequestParam("prefix") String prefix
    ) {
        return ResponseEntity.ok(screenshotService.getScenarioScreenshots(prefix));
    }


    // ── Parse helpers ─────────────────────────────────────────────────

    private List<RunStatus> parseStatuses(String statusParam) {
        if (!StringUtils.hasText(statusParam)) return null;
        return Arrays.stream(statusParam.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> {
                    try { return RunStatus.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<ScenarioType> parseTypes(String typeParam) {
        if (!StringUtils.hasText(typeParam)) return null;
        return Arrays.stream(typeParam.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> {
                    try { return ScenarioType.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private int resolveSize(Integer requested) {
        if (requested == null) return defaultPageSize;
        return Math.min(Math.max(1, requested), maxPageSize);
    }
}