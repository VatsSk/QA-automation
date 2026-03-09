package com.testingautomation.testautomation.controllers;
//package com.testingautomation.testautomation.config.controller;

import com.testingautomation.testautomation.config.model.TfProject;
import com.testingautomation.testautomation.config.model.TfRun;
import com.testingautomation.testautomation.config.model.TfUser;
import com.testingautomation.testautomation.config.repository.TfProjectRepository;
import com.testingautomation.testautomation.config.repository.TfRunRepository;
import com.testingautomation.testautomation.config.repository.TfUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TestForge Config API
 *
 * Prefix: /api
 *
 * Auth:     POST /api/auth/login
 * Projects: GET|POST /api/projects
 *           GET|PUT|DELETE /api/projects/{id}
 * Runs:     GET|POST /api/projects/{id}/runs
 *           GET|DELETE /api/runs/{id}
 * Admin:    GET|POST /api/users
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class TestForgeController {

    private final TfUserRepository    users;
    private final TfProjectRepository projects;
    private final TfRunRepository     runs;

    public TestForgeController(TfUserRepository users,
                               TfProjectRepository projects,
                               TfRunRepository runs) {
        this.users    = users;
        this.projects = projects;
        this.runs     = runs;
    }

    // ════════════════════════════════════════════════════════════
    // AUTH  — no password; matched purely by username + email
    // ════════════════════════════════════════════════════════════

    /**
     * POST /api/auth/login
     * Body: { "username": "john", "email": "john@co.com" }
     * Returns 200 + user object, or 401 if not found.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.get("email");

        if (username == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and email are required"));
        }

        Optional<TfUser> user = users.findByUsernameAndEmail(username.trim(), email.trim());
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found. Contact your admin."));
        }
        return ResponseEntity.ok(user.get());
    }

    // ════════════════════════════════════════════════════════════
    // USERS (admin management — no frontend auth guards needed
    //        since you control who accesses these endpoints)
    // ════════════════════════════════════════════════════════════

    /** GET /api/users — list all users */
    @GetMapping("/users")
    public List<TfUser> listUsers() {
        return users.findAll();
    }

    /**
     * POST /api/users — create user
     * Body: { "username": "...", "email": "...", "displayName": "..." }
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody TfUser body) {
        if (users.existsByUsername(body.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        if (users.existsByEmail(body.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }
        body.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(users.save(body));
    }

    /** DELETE /api/users/{id} */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        if (!users.existsById(id)) return ResponseEntity.notFound().build();
        users.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ════════════════════════════════════════════════════════════
    // PROJECTS
    // ════════════════════════════════════════════════════════════

    /**
     * GET /api/projects?userId={id}
     * If userId is omitted, returns all projects (admin view).
     */
    @GetMapping("/projects")
    public List<TfProject> listProjects(@RequestParam(required = false) String userId) {
        if (userId != null && !userId.isBlank()) {
            return projects.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return projects.findAllByOrderByCreatedAtDesc();
    }

    /**
     * POST /api/projects
     * Body: { "name": "...", "description": "...", "baseUrl": "http://...", "userId": "..." }
     */
    @PostMapping("/projects")
    public ResponseEntity<?> createProject(@RequestBody TfProject body) {
        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name is required"));
        }
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(projects.save(body));
    }

    /** GET /api/projects/{id} */
    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProject(@PathVariable String id) {
        return projects.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/projects/{id}
     * Body: partial update — any subset of { name, description, baseUrl }
     */
    @PutMapping("/projects/{id}")
    public ResponseEntity<?> updateProject(@PathVariable String id,
                                           @RequestBody Map<String, String> body) {
        return projects.findById(id).map(p -> {
            if (body.containsKey("name"))        p.setName(body.get("name"));
            if (body.containsKey("description")) p.setDescription(body.get("description"));
            if (body.containsKey("baseUrl"))     p.setBaseUrl(body.get("baseUrl"));
            p.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(projects.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/projects/{id} — also cascades run deletion */
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable String id) {
        if (!projects.existsById(id)) return ResponseEntity.notFound().build();
        runs.deleteByProjectId(id);    // cascade
        projects.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ════════════════════════════════════════════════════════════
    // RUNS
    // ════════════════════════════════════════════════════════════

    /** GET /api/projects/{projectId}/runs */
    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<?> listRuns(@PathVariable String projectId) {
        if (!projects.existsById(projectId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(runs.findByProjectIdOrderByTsDesc(projectId));
    }

    /**
     * POST /api/projects/{projectId}/runs  — save a run result
     *
     * Body (TfRun JSON):
     * {
     *   "runName":       "My Run",
     *   "status":        "success",
     *   "httpStatus":    200,
     *   "successMsg":    "Login successful",
     *   "elapsed":       3240,
     *   "scenarioCount": 2,
     *   "responseData":  { ... },
     *   "steps":         [ { "sid":"...", "type":"FILL_FORM", "label":"...", ... } ],
     *   "screenshots":   []
     * }
     */
    @PostMapping("/projects/{projectId}/runs")
    public ResponseEntity<?> saveRun(@PathVariable String projectId,
                                     @RequestBody TfRun body) {
        if (!projects.existsById(projectId)) return ResponseEntity.notFound().build();
        body.setProjectId(projectId);
        if (body.getTs() == null) body.setTs(LocalDateTime.now());
        return ResponseEntity.ok(runs.save(body));
    }

    /** GET /api/runs/{id} */
    @GetMapping("/runs/{id}")
    public ResponseEntity<?> getRun(@PathVariable String id) {
        return runs.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/runs/{id} */
    @DeleteMapping("/runs/{id}")
    public ResponseEntity<?> deleteRun(@PathVariable String id) {
        if (!runs.existsById(id)) return ResponseEntity.notFound().build();
        runs.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /**
     * GET /api/projects/{id}/stats
     * Quick aggregate: total | passed | failed counts
     */
    @GetMapping("/projects/{id}/stats")
    public ResponseEntity<?> projectStats(@PathVariable String id) {
        if (!projects.existsById(id)) return ResponseEntity.notFound().build();
        long total  = runs.countByProjectId(id);
        long passed = runs.countByProjectIdAndStatus(id, "success");
        return ResponseEntity.ok(Map.of(
                "projectId", id,
                "total",     total,
                "passed",    passed,
                "failed",    total - passed
        ));
    }
}