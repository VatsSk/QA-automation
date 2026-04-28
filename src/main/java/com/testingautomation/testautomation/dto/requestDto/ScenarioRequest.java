package com.testingautomation.testautomation.dto.requestDto;

import com.testingautomation.testautomation.dto.AssertionDto;
import com.testingautomation.testautomation.dto.FilterScenarioDto;
import com.testingautomation.testautomation.enums.ScenarioType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for creating/updating a Scenario inside a Run.
 *
 * NOTE: resultStatement is NOT here — it belongs on RunRequest.
 * Scenario types never include RESULT_STATEMENT.
 */
@Data
public class ScenarioRequest {

    private String id;

    @NotNull(message = "Scenario type is required")
    private ScenarioType type;

    private Integer sequenceNo;
    private String url;
    private String cssOpener;
    private String value;
    private String statement;

    /** S3 path — populated after upload via POST /api/uploads/testcase */
    private String csv;

    private List<ManualTestCaseRequest> manualTestCases;
    private List<AssertionDto> assertions;
    private List<FilterScenarioDto> filters;
    private String applyFilterBtn;
    private String clickCss;
}
