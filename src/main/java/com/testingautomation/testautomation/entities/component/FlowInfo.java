package com.testingautomation.testautomation.entities.component;

import com.testingautomation.testautomation.entities.baseEntity.BaseEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "flow_info")
@CompoundIndexes({
    @CompoundIndex(name = "project_flow_idx", def = "{'projectId': 1, 'flowId': 1}", unique = true)
})
public class FlowInfo extends BaseEntity {
    private String projectId;
    private String moduleId;
    
    @Indexed
    private String flowId;
    
    private Boolean containsComp = false;
    
    private List<FlowItem> flowItems = new ArrayList<>();
}
