package com.testingautomation.testautomation.model;


import com.testingautomation.testautomation.dto.ScenarioDescriptor;
import com.testingautomation.testautomation.enums.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "runs")
@CompoundIndexes({
        @CompoundIndex(name = "project_module_idx", def = "{'projectId': 1, 'moduleId': 1}"),
        @CompoundIndex(name = "project_module_status_idx", def = "{'projectId': 1, 'moduleId': 1, 'status': 1}"),
        @CompoundIndex(name = "project_module_created_idx", def = "{'projectId': 1, 'moduleId': 1, 'createdAt': -1}")
})
public class Run {

    @Id
    private String id;

    @TextIndexed(weight = 3)
    private String runName;

    @Indexed
    private String createdBy;

    @Indexed
    private String projectId;

    @Indexed
    private String moduleId;

    @Indexed
    private RunStatus status;

    /** Optional run type label (e.g., "regression", "smoke") */
    private String runType;

    /** Count mirrors scenariosList.size() — kept for fast queries */
    private int scenarioCount;

    /**
     * THE FINAL ASSERT MESSAGE for the run.
     * - Stored here on the Run document.
     * - NOT stored inside any Scenario.
     * - Passed as query param ?resultStatement=... when calling POST /runner/run-auth.
     */
    private String resultStatement;

    /**
     * Embedded scenarios. ScenarioType never includes RESULT_STATEMENT.
     * Scenario.statement is text-indexed for search.
     */
    private List<ScenarioDescriptor> scenariosList;

    /** Arbitrary key-value metadata (browser, env, etc.) */
    private Map<String, Object> metadata;

    /** Freeform tags for filtering */
    private List<String> tags;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
