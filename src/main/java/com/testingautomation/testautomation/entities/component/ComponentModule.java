package com.testingautomation.testautomation.entities.component;

import com.testingautomation.testautomation.entities.baseEntity.BaseEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "comp_module")
@CompoundIndexes({
    @CompoundIndex(name = "project_name_idx", def = "{'projectId': 1, 'name': 1}")
})
public class ComponentModule extends BaseEntity {
    @Indexed
    private String projectId;
    private String name;
    private String description;
}
