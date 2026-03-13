package com.testingautomation.testautomation.repo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.testingautomation.testautomation.model.Module;

import java.util.List;

@Repository
public interface ModuleRepository extends MongoRepository<Module, String> {

    List<Module> findByProjectIdOrderByCreatedAtDesc(String projectId);

    void deleteAllByProjectId(String projectId);

    boolean existsByNameAndProjectId(String name, String projectId);
}