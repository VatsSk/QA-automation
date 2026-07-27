package com.testingautomation.testautomation.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "project_environments")
public class ProjectEnvironment {

    @Id
    private String id;

    /** The project this environment belongs to */
    @Indexed
    private String projectId;

    /** Human-readable label: QA, DEV, UAT, PROD, Custom */
    private String name;

    /**
     * The origin (protocol + host + port) to substitute at execution time.
     * Example: https://dev.trackofarm.in
     * Only the origin is used — never path or query params.
     */
    private String baseUrl;

    /** If true, this environment is pre-selected in the Run dialog */
    private boolean isDefault;

    /**
     * Reserved for future extensibility.
     * Add auth tokens, usernames, passwords, tenant IDs, API headers here
     * without changing the schema or the resolver contract.
     */
    @Builder.Default
    private Map<String, String> variables = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
