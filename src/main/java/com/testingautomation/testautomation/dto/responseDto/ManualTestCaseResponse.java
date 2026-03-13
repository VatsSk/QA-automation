package com.testingautomation.testautomation.dto.responseDto;


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
public class ManualTestCaseResponse {
    private String testcaseId;
    private String name;
    private Map<String, Object> inputData;
    private String expectedResult;
    private String actualResult;
    private ScenarioStatus status;
    private List<String> screenshotPaths;
}