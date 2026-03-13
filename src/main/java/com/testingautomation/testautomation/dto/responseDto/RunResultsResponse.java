package com.testingautomation.testautomation.dto.responseDto;

import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.ScenarioStatus;
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
}