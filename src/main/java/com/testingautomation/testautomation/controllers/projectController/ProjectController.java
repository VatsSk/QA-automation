package com.testingautomation.testautomation.controllers.projectController;

import com.testingautomation.testautomation.dto.responseDto.ProjectResponse;
import com.testingautomation.testautomation.dto.requestDto.ProjectRequest;
import com.testingautomation.testautomation.services.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(
            @RequestParam(required = false) String createdBy) {
//        System.out.println("created by : "+ createdBy);
        List<ProjectResponse> projects = projectService.getProjects(createdBy);
        return ResponseEntity.ok(projects != null ? projects : List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable String id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @GetMapping("/url/{id}")
    public ResponseEntity<ProjectResponse> getLoginUrl(@PathVariable String id) {
        return ResponseEntity.ok(projectService.getLoginUrl(id));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String id,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
