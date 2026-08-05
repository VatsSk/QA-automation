package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.responseDto.ModuleResponse;
import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.config.mapperConfig.EntityMapper;
import com.testingautomation.testautomation.repositories.moduleRepos.ModuleRepository;
import com.testingautomation.testautomation.repositories.runRepos.RunRepository;
import com.testingautomation.testautomation.dto.requestDto.ModuleRequest;
import  com.testingautomation.testautomation.entities.Module;
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
    private final RunService runService;

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
        module.setStatus(RunStatus.DRAFT);
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
    public ModuleResponse runAllRuns(String id){
        List<Run> runs=runRepository.getRunsByModuleId(id);
        RunStatus moduleStatus = RunStatus.RUNNING;
        moduleRepository.updateModuleStatus(id,moduleStatus);
        List<String> runIds=runs.stream().map(Run::getId).toList();
        ModuleResponse moduleResponse = new ModuleResponse();
        List<RunResponse> runResponses=runService.executeAllRun(runIds);
        long passedCount = runResponses.stream()
                .filter(run -> run.getStatus() == RunStatus.PASSED)
                .count();

        long failedCount = runResponses.stream()
                .filter(run -> run.getStatus() == RunStatus.FAILED)
                .count();

        if(passedCount==runResponses.size()){
            moduleRepository.updateModuleStatus(id,RunStatus.PASSED);
            moduleStatus=RunStatus.PASSED;
        }else if(failedCount==runResponses.size()){
            moduleRepository.updateModuleStatus(id,RunStatus.FAILED);
            moduleStatus=RunStatus.FAILED;
        }else{
            moduleRepository.updateModuleStatus(id,RunStatus.PARTIAL);
            moduleStatus=RunStatus.PARTIAL;
        }
        moduleResponse.setRunIds(runIds);
        moduleResponse.setStatus(moduleStatus);
        return moduleResponse;
    }



    private Module findOrThrow(String id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException("Module not found: " + id));
    }


}
