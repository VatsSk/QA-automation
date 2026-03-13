package com.testingautomation.testautomation.requestDto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;
    private String createdBy;
    private String baseUrl;
    private List<String> tags;
}