package com.testingautomation.testautomation.repositories.flowRepos;

import com.testingautomation.testautomation.entities.flow.Flow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowRepository extends MongoRepository<Flow, String> {
    List<Flow> findByProjectIdAndModuleId(String projectId, String moduleId);
}
