package com.testingautomation.testautomation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScenarioDescriptorModal {
    private List<ScenarioDescriptor> tests;
    private String runId;
}
