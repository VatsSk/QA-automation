package com.testingautomation.testautomation.dto.responseDto;

import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.enums.ScenarioStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunResultsResponse {
    private String runId;
    private String runName;
    private RunStatus runStatus;
    private int totalScenarios;
    private Map<ScenarioStatus, Long> scenarioStatusCounts;
    private List<String> allScreenshots;
    private List<String> allResultCsvs;
    /** resultStatement from the Run document */
    private String resultStatement;
    /** reason from the Run document - system-generated execution result/failure reason */
    private String reason;
}