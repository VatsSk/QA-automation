package com.testingautomation.testautomation.dto;

import com.testingautomation.testautomation.enums.RunStatus;
import lombok.Data;

import java.util.List;

@Data
public class ScenarioTestDto {
    private List<TestCaseDTO> testCases;
    private String resultCsv;
    private RunStatus overAllScenarioStatus;

    public ScenarioTestDto(List<TestCaseDTO> testCases, String resultCsv) {
        this.testCases = testCases;
        this.resultCsv = resultCsv;
    }
}
