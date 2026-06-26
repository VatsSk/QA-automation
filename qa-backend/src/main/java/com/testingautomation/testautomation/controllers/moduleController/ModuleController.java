package com.testingautomation.testautomation.controllers.moduleController;

import com.testingautomation.testautomation.dto.responseDto.ModuleResponse;
import com.testingautomation.testautomation.dto.requestDto.ModuleRequest;
import com.testingautomation.testautomation.services.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping("/api/projects/{projectId}/modules")
    public ResponseEntity<List<ModuleResponse>> getModules(@PathVariable String projectId) {
        return ResponseEntity.ok(moduleService.getModulesByProject(projectId));
    }

    @GetMapping("/api/modules/{id}")
    public ResponseEntity<ModuleResponse> getModule(@PathVariable String id) {
        return ResponseEntity.ok(moduleService.getModuleById(id));
    }

    @PostMapping("/api/projects/{projectId}/modules")
    public ResponseEntity<ModuleResponse> createModule(
            @PathVariable String projectId,
            @Valid @RequestBody ModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moduleService.createModule(projectId, request));
    }

    @PutMapping("/api/modules/{id}")
    public ResponseEntity<ModuleResponse> updateModule(
            @PathVariable String id,
            @Valid @RequestBody ModuleRequest request) {
        return ResponseEntity.ok(moduleService.updateModule(id, request));
    }

    @DeleteMapping("/api/modules/{id}")
    public ResponseEntity<Void> deleteModule(@PathVariable String id) {
        moduleService.deleteModule(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("api/modules/{id}/all")
    public ResponseEntity<ModuleResponse> runAllRuns(@PathVariable String id){
        return ResponseEntity.ok(moduleService.runAllRuns(id));
    }
}