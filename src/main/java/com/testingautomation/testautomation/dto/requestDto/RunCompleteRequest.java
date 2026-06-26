package com.testingautomation.testautomation.dto.requestDto;

import com.testingautomation.testautomation.enums.RunStatus;
import lombok.Data;

@Data
public class RunCompleteRequest {
    private RunStatus status;
    private String reason;
}
