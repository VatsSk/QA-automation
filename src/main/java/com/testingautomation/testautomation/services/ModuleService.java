package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.responseDto.ModuleResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.mapper.EntityMapper;
import com.testingautomation.testautomation.repo.ModuleRepository;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.requestDto.ModuleRequest;
import  com.testingautomation.testautomation.model.Module;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final RunRepository runRepository;
    private final EntityMapper mapper;

    public List<ModuleResponse> getModulesByProject(String projectId) {
        return mapper.toModuleResponseList(
                moduleRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
    }

    public ModuleResponse getModuleById(String id) {
        return mapper.toModuleResponse(findOrThrow(id));
    }

    public ModuleResponse createModule(String projectId, ModuleRequest request) {
        Module module = mapper.toModule(request);
        module.setProjectId(projectId);
        module.setCreatedAt(Instant.now());
        module.setUpdatedAt(Instant.now());
        Module saved = moduleRepository.save(module);
        log.info("Created module {} in project {}", saved.getId(), projectId);
        return mapper.toModuleResponse(saved);
    }

    public ModuleResponse updateModule(String id, ModuleRequest request) {
        Module module = findOrThrow(id);
        mapper.updateModuleFromRequest(request, module);
        module.setUpdatedAt(Instant.now());
        return mapper.toModuleResponse(moduleRepository.save(module));
    }

    public void deleteModule(String id) {
        findOrThrow(id);
        runRepository.deleteAllByModuleId(id);
        moduleRepository.deleteById(id);
        log.info("Deleted module {} with cascaded runs", id);
    }

    private Module findOrThrow(String id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException("Module not found: " + id));
    }
}
