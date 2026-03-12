package com.testingautomation.testautomation.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Embedded document inside Run.scenariosList.
 *
 * IMPORTANT: resultStatement is NOT here.
 * It lives as a top-level field on the Run document and is
 * sent as a query param to POST /runner/run-auth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scenario {


    private String id;

    /** Scenario type — never includes RESULT_STATEMENT */
    private ScenarioType type;

    private Integer sequenceNo;

    private String projectId;

    private String moduleId;

    /** Target URL for URL / URL_NAV / SEARCH_NAV types */
    private String url;

    /** CSS selector used to open modal for MODAL / MODAL_NAV types */
    private String cssOpener;

    /** Value to type or select */
    private String value;

    /** Human-readable statement (indexed for text search) */
    private String statement;

    /** S3/MinIO path to uploaded input CSV */
    private String csv;

    /** Inline manual test cases */
    private List<TestCase> manualTestCases;

    /** S3/MinIO path to result CSV written by runner */
    private String resultCsv;

    /** S3/MinIO paths to screenshots captured by runner */
    private List<String> screenshots;

    private ScenarioStatus status;

    private Instant actionPerformedAt;
    private Instant createdAt;
    private Instant updatedAt;
}