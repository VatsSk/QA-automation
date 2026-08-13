package com.testingautomation.testautomation.entities.component;

import com.testingautomation.testautomation.entities.baseEntity.BaseEntity;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "components")
@CompoundIndexes({
    @CompoundIndex(name = "project_module_idx", def = "{'projectId': 1, 'compModuleId': 1}"),
    @CompoundIndex(name = "project_module_name_idx", def = "{'projectId': 1, 'compModuleId': 1, 'name': 1}")
})
public class Component extends BaseEntity {
    private String projectId;
    private String compModuleId;
    private String name;
    private String description;
    
    private List<FlowStep> steps = new ArrayList<>();
}
