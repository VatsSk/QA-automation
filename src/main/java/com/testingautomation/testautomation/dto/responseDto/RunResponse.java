package com.testingautomation.testautomation.dto.responseDto;

import com.testingautomation.testautomation.model.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Full run response including embedded scenarios.
 * resultStatement is a top-level field — NOT inside any scenario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunResponse {
    private String id;
    private String runName;
    private String createdBy;
    private String projectId;
    private String moduleId;
    private RunStatus status;
    private String runType;
    private int scenarioCount;

    /**
     * Final assert message — lives on Run, passed as query param to runner.
     */
    private String resultStatement;

    private List<ScenarioResponse> scenariosList;
    private Map<String, Object> metadata;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
}