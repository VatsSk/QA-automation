package com.testingautomation.testautomation.repositories.flowRepos;

import com.testingautomation.testautomation.entities.component.Component;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentRepository extends MongoRepository<Component, String> {
    List<Component> findByProjectIdAndCompModuleId(String projectId, String compModuleId);
}
