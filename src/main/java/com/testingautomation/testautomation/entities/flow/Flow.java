package com.testingautomation.testautomation.entities.flow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.testingautomation.testautomation.entities.baseEntity.ExecutionEntity;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Document(collection = "flows")
public class Flow extends ExecutionEntity {

    private String environment;

    private String baseUrl;

    private String projectId;

    private String moduleId;

    private String name;

    private String description;

    private Integer version;

    private Integer defaultWait = 5000;

    @com.fasterxml.jackson.annotation.JsonAlias({"url_change_wait", "urlChangeWait"})
    private Integer urlChangeWait;


    private Long defaultPageReadyTimeoutMs;

    private String flowBasePath;

    private boolean isPartComp;

    private Boolean isDebugEnabled = false;

    private List<FlowStep> steps = new ArrayList<>();

    /**
     * Flow-level variable definitions keyed by canonical variable name.
     * The map key is the canonical name (trimmed, case-preserved as stored).
     * Values are generation rules — never runtime-generated values.
     * Existing flows without this field will deserialize to an empty map.
     */
    @JsonDeserialize(using = FlowVariablesDeserializer.class)
    private Map<String, FlowVariableDefinition> variables = new HashMap<>();
}
