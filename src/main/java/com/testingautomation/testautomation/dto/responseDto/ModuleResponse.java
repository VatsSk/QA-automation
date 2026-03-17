package com.testingautomation.testautomation.dto.responseDto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleResponse {
    private String id;
    private String projectId;
    private String name;
    private String description;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}