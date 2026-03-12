package com.testingautomation.testautomation.services;




import com.testingautomation.testautomation.dto.responseDto.ProjectResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.mapper.EntityMapper;

import com.testingautomation.testautomation.model.Project;
import com.testingautomation.testautomation.repo.ModuleRepository;
import com.testingautomation.testautomation.repo.ProjectRepository;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.requestDto.ProjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ModuleRepository moduleRepository;
    private final RunRepository runRepository;
    private final EntityMapper mapper;

    public List<ProjectResponse> getProjects(String userId) {
        List<Project> projects = StringUtils.hasText(userId)
                ? projectRepository.findByCreatedByOrderByCreatedAtDesc(userId)
                : projectRepository.findAllByOrderByCreatedAtDesc();
        return mapper.toProjectResponseList(projects);
    }

    public ProjectResponse getProjectById(String id) {
        return mapper.toProjectResponse(findOrThrow(id));
    }

    public ProjectResponse createProject(ProjectRequest request) {
        Project project = mapper.toProject(request);
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        log.info("Created project {}", saved.getId());
        return mapper.toProjectResponse(saved);
    }

    public ProjectResponse updateProject(String id, ProjectRequest request) {
        Project project = findOrThrow(id);
        mapper.updateProjectFromRequest(request, project);
        project.setUpdatedAt(Instant.now());
        return mapper.toProjectResponse(projectRepository.save(project));
    }

    public void deleteProject(String id) {
        findOrThrow(id);
        // Cascade: remove modules and runs
        moduleRepository.deleteAllByProjectId(id);
        runRepository.deleteAllByProjectId(id);
        projectRepository.deleteById(id);
        log.info("Deleted project {} with cascaded modules and runs", id);
    }

    private Project findOrThrow(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException("Project not found: " + id));
    }
}