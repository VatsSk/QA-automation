package com.testingautomation.testautomation.requestDto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModuleRequest {

    @NotBlank(message = "Module name is required")
    private String name;

    private String description;
    private String createdBy;
}