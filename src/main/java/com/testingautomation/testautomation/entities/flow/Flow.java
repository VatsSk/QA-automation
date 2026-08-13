package com.testingautomation.testautomation.entities.flow;

import com.testingautomation.testautomation.entities.baseEntity.ExecutionEntity;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

//    private Integer version = 1;

    private Integer defaultWait = 5000;
    private String flowBasePath;

    private Boolean isDebugEnabled = false;

    private List<FlowStep> steps = new ArrayList<>();
}