package com.testingautomation.testautomation.repo;


import com.testingautomation.testautomation.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {

    List<Project> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<Project> findAllByOrderByCreatedAtDesc();

    boolean existsByNameAndCreatedBy(String name, String createdBy);
}