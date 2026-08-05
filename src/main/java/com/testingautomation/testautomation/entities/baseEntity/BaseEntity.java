package com.testingautomation.testautomation.entities.baseEntity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Getter
@Setter
public abstract class BaseEntity {

    @Id
    private String id;

    private String createdBy;

    private Instant createdAt;

    private Instant updatedAt;

}
