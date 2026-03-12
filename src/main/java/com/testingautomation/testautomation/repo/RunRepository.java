package com.testingautomation.testautomation.repo;

import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends MongoRepository<Run, String>, RunRepositoryCustom {

    List<Run> findByProjectIdAndModuleIdOrderByCreatedAtDesc(String projectId, String moduleId);

    long countByProjectIdAndModuleId(String projectId, String moduleId);

    long countByProjectIdAndModuleIdAndStatus(String projectId, String moduleId, RunStatus status);

    void deleteAllByModuleId(String moduleId);

    void deleteAllByProjectId(String projectId);
}