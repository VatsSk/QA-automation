package com.testingautomation.testautomation.repositories.runRepos;

import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.enums.RunStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends MongoRepository<Run, String>, RunRepositoryCustom {

    Run getRunById(String id);

    List<Run> findByProjectIdAndModuleIdOrderByCreatedAtDesc(String projectId, String moduleId);

    long countByProjectIdAndModuleId(String projectId, String moduleId);

    long countByProjectIdAndModuleIdAndStatus(String projectId, String moduleId, RunStatus status);

    void deleteAllByModuleId(String moduleId);

    void deleteAllByProjectId(String projectId);
}