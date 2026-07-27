package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.requestDto.CreateEnvironmentRequest;
import com.testingautomation.testautomation.entities.ProjectEnvironment;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.repositories.ProjectEnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectEnvironmentService {

    @Autowired
    private ProjectEnvironmentRepository repository;

    /**
     * Creates a new environment for a project.
     * If isDefault=true, the current default for that project is cleared first
     * so there is always at most one default per project.
     */
    public ProjectEnvironment create(String projectId, CreateEnvironmentRequest request) {
        if (request.isDefault()) {
            repository.clearDefaultForProject(projectId);
        }

        ProjectEnvironment env = ProjectEnvironment.builder()
                .projectId(projectId)
                .name(request.getName())
                .baseUrl(request.getBaseUrl())
                .isDefault(request.isDefault())
                .variables(request.getVariables())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return repository.save(env);
    }

    /** Returns all environments for a project, used to populate the Run dialog */
    public List<ProjectEnvironment> getByProject(String projectId) {
        return repository.findByProjectId(projectId);
    }

    /**
     * Updates an existing environment.
     * If isDefault=true, clears any existing default for the project first.
     */
    public ProjectEnvironment update(String id, CreateEnvironmentRequest request) {
        ProjectEnvironment env = repository.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Environment not found: " + id));

        if (request.isDefault()) {
            repository.clearDefaultForProject(env.getProjectId());
        }

        env.setName(request.getName());
        env.setBaseUrl(request.getBaseUrl());
        env.setDefault(request.isDefault());
        env.setVariables(request.getVariables());
        env.setUpdatedAt(Instant.now());

        return repository.save(env);
    }

    /** Deletes an environment. Does not affect any stored flows. */
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new GlobalExceptionHandler.ResourceNotFoundException(
                    "Environment not found: " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Used by FlowOrchestratorService to load the environment before execution.
     * Returns empty if no environmentId was provided (backward compatible path).
     */
    public Optional<ProjectEnvironment> findById(String id) {
        return repository.findById(id);
    }
}
