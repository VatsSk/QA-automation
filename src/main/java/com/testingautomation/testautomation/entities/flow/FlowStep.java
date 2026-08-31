package com.testingautomation.testautomation.entities.flow;

import com.testingautomation.testautomation.entities.baseEntity.ExecutionEntity;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.VerificationType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowStep extends ExecutionEntity {

    private Integer stepOrder;

    private String name;

    private ActionType actionType;

    private VerificationType verificationType;

    /**
     * Best locator received from extension.
     */
    private String selector;

    /**
     * Input value / URL / Selected value / Wait time etc.
     */
    private String value;

    /**
     * Used only by verification steps.
     */
    private String expectedValue;

    /**
     * Used for ATTRIBUTE verification.
     */
    private String attribute;

    /**
     * Used for TEXT verification. Can be "value", "text", or "placeholder".
     */
    private String textSource;

    /**
     * If false, Flow.defaultWait will be used.
     */
    private Boolean overrideWait = false;

    /**
     * Used only when overrideWait=true.
     */
    private Integer wait;

    private Integer retryCount = 0;

    private Boolean continueOnFailure = false;

    private Boolean captureScreenshot = true;

    private Boolean isComp;

    private String tabRef;

    private String sourceTabRef;

    private String fromTabRef;

    private String toTabRef;

    private String message;

    private Object triggeringElement;

    private Long pageReadyTimeoutMs;

//    private Boolean enabled = true;

//    private String remarks;
}