package com.testingautomation.testautomation.mapper;


import com.testingautomation.testautomation.dto.responseDto.*;
import com.testingautomation.testautomation.model.Project;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.Scenario;
import com.testingautomation.testautomation.model.TestCase;
import com.testingautomation.testautomation.requestDto.*;
import com.testingautomation.testautomation.model.Module;
import org.mapstruct.*;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper — Spring component model set via compiler arg.
 * resultStatement mapping is explicit: RunRequest → Run (top-level),
 * ScenarioRequest/Response never touch resultStatement.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface EntityMapper {

    // ── Project ──────────────────────────────────────────────────────

    Project toProject(ProjectRequest request);

    ProjectResponse toProjectResponse(Project project);

    List<ProjectResponse> toProjectResponseList(List<Project> projects);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProjectFromRequest(ProjectRequest request, @MappingTarget Project project);

    // ── Module ───────────────────────────────────────────────────────

    Module toModule(ModuleRequest request);

    ModuleResponse toModuleResponse(Module module);

    List<ModuleResponse> toModuleResponseList(List<Module> modules);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateModuleFromRequest(ModuleRequest request, @MappingTarget Module module);

    // ── ManualTestCase ───────────────────────────────────────────────

    TestCase toManualTestCase(ManualTestCaseRequest request);

    ManualTestCaseResponse toManualTestCaseResponse(TestCase tc);

    List<TestCase> toManualTestCaseList(List<ManualTestCaseRequest> requests);

    List<ManualTestCaseResponse> toManualTestCaseResponseList(List<TestCase> list);

    // ── Scenario ─────────────────────────────────────────────────────

    /**
     * Maps ScenarioRequest → Scenario.
     * Explicitly ignores result fields — those are written by the runner.
     */
    @Mapping(target = "resultCsv",          ignore = true)
    @Mapping(target = "screenshots",        ignore = true)
    @Mapping(target = "status",             ignore = true)
    @Mapping(target = "actionPerformedAt",  ignore = true)
    @Mapping(target = "createdAt",          ignore = true)
    @Mapping(target = "updatedAt",          ignore = true)
    Scenario toScenario(ScenarioRequest request);

    ScenarioResponse toScenarioResponse(Scenario scenario);

    List<Scenario> toScenarioList(List<ScenarioRequest> requests);

    List<ScenarioResponse> toScenarioResponseList(List<Scenario> scenarios);

    // ── Run ──────────────────────────────────────────────────────────

    /**
     * Maps RunRequest → Run.
     * resultStatement maps normally (top-level field on both sides).
     * Status defaults to DRAFT — set by service layer.
     */
    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "status",        ignore = true)
    @Mapping(target = "scenarioCount", ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    Run toRun(RunRequest request);

    /**
     * Maps Run → RunResponse.
     * resultStatement passes through from Run to RunResponse.
     */
    RunResponse toRunResponse(Run run);

    List<RunResponse> toRunResponseList(List<Run> runs);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "status",        ignore = true)
    @Mapping(target = "scenarioCount", ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    void updateRunFromRequest(RunRequest request, @MappingTarget Run run);
}