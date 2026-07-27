package com.testingautomation.testautomation.repositories;

import com.testingautomation.testautomation.entities.ProjectEnvironment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectEnvironmentRepository extends MongoRepository<ProjectEnvironment, String> {

    /** Fetch all environments for a given project */
    List<ProjectEnvironment> findByProjectId(String projectId);

    /** Find the default environment for a project, used to pre-select in Run dialog */
    Optional<ProjectEnvironment> findByProjectIdAndIsDefaultTrue(String projectId);

    /**
     * Unset the current default for a project before setting a new one.
     * This ensures only one default environment exists per project at all times.
     */
    @Query("{ 'projectId': ?0, 'isDefault': true }")
    @Update("{ '$set': { 'isDefault': false } }")
    void clearDefaultForProject(String projectId);
}
