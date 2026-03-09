package com.testingautomation.testautomation.repo;

import com.testingautomation.testautomation.model.TfRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TfRunRepository extends MongoRepository<TfRun, String> {

    List<TfRun> findByProjectIdOrderByTsDesc(String projectId);

    long countByProjectId(String projectId);

    long countByProjectIdAndStatus(String projectId, String status);

    void deleteByProjectId(String projectId);
}