package com.testingautomation.testautomation.repositories.flowRepos;

import com.testingautomation.testautomation.entities.component.FlowInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlowInfoRepository extends MongoRepository<FlowInfo, String> {
    Optional<FlowInfo> findByFlowId(String flowId);
}
