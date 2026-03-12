package com.testingautomation.testautomation.requestDto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating or updating a Run.
 *
 * resultStatement lives here at the run level.
 * It is NOT inside any ScenarioRequest.
 * When POST /api/runs/{id}/execute is called, resultStatement is appended
 * as a query param to POST /runner/run-auth.
 */
@Data
public class RunRequest {

    @NotBlank(message = "Run name is required")
    private String runName;

    private String createdBy;
    private String runType;

    /**
     * Final assert message for the entire run.
     * Passed as ?resultStatement=... query param to /runner/run-auth.
     */
    private String resultStatement;

    private List<ScenarioRequest> scenariosList;
    private Map<String, Object> metadata;
    private List<String> tags;
}