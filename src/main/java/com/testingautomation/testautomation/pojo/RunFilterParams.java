package com.testingautomation.testautomation.pojo;

import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.ScenarioType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Encapsulates all combinable filter criteria for run queries.
 * Passed from controller → service → custom repository impl.
 */
@Data
@Builder
public class RunFilterParams {

    private String projectId;      // required
    private String moduleId;       // required

    // --- combinable filters ---
    private List<RunStatus> statuses;
    private List<ScenarioType> types;   // matches scenariosList.type
    private String createdBy;
    private String search;              // text search across runName + scenario statements
    private Instant from;               // createdAt >= from
    private Instant to;                 // createdAt <= to
    private List<String> tags;          // any of these tags present

    // --- pagination & sorting ---
    private int page;
    private int size;
    private String sort;                // e.g. "createdAt", "-createdAt", "status", "runName"
}