package com.testingautomation.testautomation.requestDto;

import lombok.Data;
import java.util.List;

@Data
public class TestConfigPayload {
    private List<TestConfigRequest> tests;
}

