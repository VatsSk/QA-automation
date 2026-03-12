package com.testingautomation.testautomation.requestDto;


import com.testingautomation.testautomation.model.ScenarioStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ManualTestCaseRequest {
    private String testcaseId;
    private String name;
    private Map<String, Object> inputData;
    private String expectedResult;
    private String actualResult;
    private ScenarioStatus status;
    private List<String> screenshotPaths;
}
