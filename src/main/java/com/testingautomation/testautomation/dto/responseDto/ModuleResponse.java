package com.testingautomation.testautomation.dto.responseDto;


import com.testingautomation.testautomation.enums.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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
    private List<String> runIds;
    private RunStatus status;
}