package com.testingautomation.testautomation.dto.responseDto;

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
public class ProjectResponse {
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private String baseUrl;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
}