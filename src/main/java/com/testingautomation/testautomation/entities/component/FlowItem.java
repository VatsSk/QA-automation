package com.testingautomation.testautomation.entities.component;

import com.testingautomation.testautomation.enums.flow.FlowItemType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowItem {
    private Integer order;
    private FlowItemType type;
    private String stepId;
    private String componentId;
}
