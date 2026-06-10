package com.testingautomation.testautomation.repositories.moduleRepos;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import com.testingautomation.testautomation.entities.Module;

import java.util.List;

@Repository
public interface ModuleRepository extends MongoRepository<Module, String> {

    List<Module> findByProjectIdOrderByCreatedAtDesc(String projectId);

    void deleteAllByProjectId(String projectId);

    boolean existsByNameAndProjectId(String name, String projectId);

}