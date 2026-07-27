package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.dto.requestDto.CreateEnvironmentRequest;
import com.testingautomation.testautomation.entities.ProjectEnvironment;
import com.testingautomation.testautomation.services.ProjectEnvironmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing Project Environments.
 *
 * All endpoints are scoped under a project:
 *   /api/projects/{projectId}/environments
 *
 * These environments are used at flow execution time to override
 * the origin of NAVIGATE steps. They do not affect stored flows.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/environments")
public class ProjectEnvironmentController {

    @Autowired
    private ProjectEnvironmentService service;

    /**
     * Create a new environment for a project.
     *
     * POST /api/projects/{projectId}/environments
     * Body: { "name": "DEV", "baseUrl": "https://dev.example.com", "isDefault": false }
     *
     * Returns 201 Created with the saved environment.
     */
    @PostMapping
    public ResponseEntity<ProjectEnvironment> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        ProjectEnvironment created = service.create(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * List all environments for a project.
     * Used by the frontend Run dialog to populate environment options.
     *
     * GET /api/projects/{projectId}/environments
     * Returns 200 OK with list of environments.
     */
    @GetMapping
    public ResponseEntity<List<ProjectEnvironment>> getAll(@PathVariable String projectId) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    /**
     * Update an existing environment.
     *
     * PUT /api/projects/{projectId}/environments/{id}
     * Body: { "name": "DEV", "baseUrl": "https://new-dev.example.com", "isDefault": true }
     *
     * Returns 200 OK with updated environment.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectEnvironment> update(
            @PathVariable String projectId,
            @PathVariable String id,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        ProjectEnvironment updated = service.update(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an environment.
     * This does not affect any stored flows.
     *
     * DELETE /api/projects/{projectId}/environments/{id}
     * Returns 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
