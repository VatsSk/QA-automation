package com.testingautomation.testautomation.entities.component;

import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.FlowItemType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowItem {
    private Integer order;
    private FlowItemType type;
    private FlowStep step;
    private String componentId;
}
