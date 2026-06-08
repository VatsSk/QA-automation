package com.testingautomation.testautomation.dto.responseDto;

import com.testingautomation.testautomation.dto.AssertionDto;
import com.testingautomation.testautomation.dto.DateRangeNavDto;
import com.testingautomation.testautomation.dto.FilterScenarioDto;
import com.testingautomation.testautomation.dto.ManageColumnItemDto;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.enums.ScenarioStatus;
import com.testingautomation.testautomation.enums.ScenarioType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a Scenario embedded in a Run.
 * resultStatement is NOT here — it's on RunResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResponse {
    private String id;
    private ScenarioType type;
    private Integer sequenceNo;
    private String url;
    private String cssOpener;
    private String value;
    private String statement;
    private String csv;
    private List<AssertionDto> assertions;
    private List<FilterScenarioDto> filters;
    private String applyFilterBtn;
    private DateRangeNavDto dateRangeNavDto;
    private String saveBtnCss;
    private List<ManageColumnItemDto> columns;
    private List<ManualTestCaseResponse> manualTestCases;
    private String resultCsv;
    private String clickCss;
    private List<String> screenshots;
    private ScenarioStatus status;
    private Instant actionPerformedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String scenarioBasePath;
    private RunStatus scenarioStatus; 
}
