package com.testingautomation.testautomation.entities.baseEntity;

import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public abstract class ExecutionEntity extends BaseEntity {

    private ExecutionStatus executionStatus = ExecutionStatus.DRAFT;

    private Long executionTime;

    private Instant executionStartedAt;

    private Instant executionCompletedAt;

    private String executionMessage;
}
