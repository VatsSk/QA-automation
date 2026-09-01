package com.testingautomation.testautomation.repositories.flowRepos;

import com.testingautomation.testautomation.entities.component.ComponentModule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentModuleRepository extends MongoRepository<ComponentModule, String> {
    List<ComponentModule> findByProjectId(String projectId);
}
