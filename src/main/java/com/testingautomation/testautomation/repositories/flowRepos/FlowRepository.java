package com.testingautomation.testautomation.repositories.flowRepos;

import com.testingautomation.testautomation.entities.flow.Flow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowRepository extends MongoRepository<Flow, String> {
    List<Flow> findByProjectIdAndModuleId(String projectId, String moduleId);

    @Query(
            value = "{'_id': ?0}",
            fields = "{'projectId': 1,'moduleId': 1,'_id': 0}"
    )
    Flow flowWithProjIdAndModId(String Id);
}
