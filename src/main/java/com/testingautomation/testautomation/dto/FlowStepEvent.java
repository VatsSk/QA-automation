package com.testingautomation.testautomation.dto;

import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowStepEvent {

    private String flowId;
    private String stepId;
    private Integer stepOrder;
    private ExecutionStatus executionStatus;
    private String executionMessage;
    private String screenshotUrl;

}
