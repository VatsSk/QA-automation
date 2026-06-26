package com.testingautomation.testautomation.dto.requestDto;

import lombok.Data;

@Data
public class RunLogRequest {
    private int step;
    private String status;
    private String message;
}
