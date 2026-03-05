package com.testingautomation.testautomation.dto;

import lombok.Data;
import java.util.List;

@Data
public class TestConfigPayload {
    private List<TestConfigRequest> tests;
}

