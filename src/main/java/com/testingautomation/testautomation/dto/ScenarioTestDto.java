package com.testingautomation.testautomation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScenarioTestDto {
    private List<TestCase> testCases;
    private String resultCsv;

    public ScenarioTestDto(List<TestCase> testCases, String resultCsv) {
        this.testCases = testCases;
        this.resultCsv = resultCsv;
    }
}
