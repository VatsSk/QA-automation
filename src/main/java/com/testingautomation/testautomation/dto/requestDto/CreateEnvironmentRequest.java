package com.testingautomation.testautomation.dto.requestDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class CreateEnvironmentRequest {

    @NotBlank(message = "Environment name is required")
    private String name;

    @NotBlank(message = "Base URL is required")
    @Pattern(
        regexp = "^https?://[^\\s/$.?#].[^\\s]*",
        message = "Base URL must be a valid URL (e.g. https://dev.example.com)"
    )
    private String baseUrl;

    /** If true, this will become the default environment for the project */
    private boolean isDefault;

    /** Reserved for future: tokens, credentials, headers, tenant IDs */
    private Map<String, String> variables = new HashMap<>();
}
