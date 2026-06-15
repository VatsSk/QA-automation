package com.testingautomation.testautomation.repositories.projectRepo;


import com.testingautomation.testautomation.entities.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {

    List<Project> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<Project> findAllByOrderByCreatedAtDesc();

    @Query(
            value = "{'_id': ?0}",
            fields = "{'_id' : 0,'loginUrl': 1}"
    )
    Project findLoginUrlById(String projectId);

    boolean existsByNameAndCreatedBy(String name, String createdBy);
}