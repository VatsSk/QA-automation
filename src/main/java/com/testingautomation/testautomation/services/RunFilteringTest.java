//package com.testingautomation.testautomation.services;
//
//package com.qa.manager.service;
//
//import com.qa.manager.dto.response.PagedResponse;
//import com.qa.manager.dto.response.RunResponse;
//import com.qa.manager.mapper.EntityMapper;
//import com.qa.manager.model.Run;
//import com.qa.manager.model.RunStatus;
//import com.qa.manager.model.ScenarioStatus;
//import com.qa.manager.model.ScenarioType;
//import com.qa.manager.model.embedded.Scenario;
//import com.qa.manager.repository.RunFilterParams;
//import com.qa.manager.repository.RunRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class RunFilteringTest {
//
//    @Mock RunRepository runRepository;
//    @Mock EntityMapper  mapper;
//    @Mock RunnerService runnerService;
//
//    @InjectMocks RunService runService;
//
//    private Run sampleRun;
//
//    @BeforeEach
//    void setUp() {
//        Scenario s1 = Scenario.builder()
//                .id(UUID.randomUUID().toString())
//                .type(ScenarioType.URL)
//                .sequenceNo(1)
//                .url("https://example.com/login")
//                .statement("Verify login page loads")
//                .status(ScenarioStatus.PENDING)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build();
//
//        Scenario s2 = Scenario.builder()
//                .id(UUID.randomUUID().toString())
//                .type(ScenarioType.MODAL)
//                .sequenceNo(2)
//                .cssOpener("#login-btn")
//                .statement("Click login modal opener")
//                .status(ScenarioStatus.PENDING)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build();
//
//        sampleRun = Run.builder()
//                .id("run-001")
//                .runName("Login Smoke Run")
//                .createdBy("alice")
//                .projectId("proj-001")
//                .moduleId("mod-001")
//                .status(RunStatus.PASSED)
//                .runType("smoke")
//                .resultStatement("Assert: user dashboard visible")   // top-level, not in scenario
//                .scenariosList(List.of(s1, s2))
//                .scenarioCount(2)
//                .tags(List.of("smoke", "login"))
//                .createdAt(Instant.now().minusSeconds(3600))
//                .updatedAt(Instant.now())
//                .build();
//    }
//
//    // ── Filtering tests ───────────────────────────────────────────────
//
//    @Test
//    void getFilteredRuns_delegatesToRepository() {
//        RunFilterParams params = RunFilterParams.builder()
//                .projectId("proj-001").moduleId("mod-001")
//                .statuses(List.of(RunStatus.PASSED))
//                .page(0).size(20).sort("-createdAt")
//                .build();
//
//        when(runRepository.findByFilters(params)).thenReturn(List.of(sampleRun));
//        when(runRepository.countByFilters(params)).thenReturn(1L);
//        when(mapper.toRunResponseList(any())).thenReturn(List.of(toResponse(sampleRun)));
//
//        PagedResponse<RunResponse> result = runService.getFilteredRuns(params);
//
//        assertThat(result.getTotalCount()).isEqualTo(1);
//        assertThat(result.getResults()).hasSize(1);
//        assertThat(result.getPage()).isEqualTo(0);
//        assertThat(result.getTotalPages()).isEqualTo(1);
//        assertThat(result.isHasNext()).isFalse();
//        assertThat(result.isHasPrevious()).isFalse();
//
//        verify(runRepository).findByFilters(params);
//        verify(runRepository).countByFilters(params);
//    }
//
//    @Test
//    void getFilteredRuns_emptyResultsHandledGracefully() {
//        RunFilterParams params = RunFilterParams.builder()
//                .projectId("proj-001").moduleId("mod-001")
//                .statuses(List.of(RunStatus.FAILED))
//                .page(0).size(20).sort("-createdAt")
//                .build();
//
//        when(runRepository.findByFilters(params)).thenReturn(List.of());
//        when(runRepository.countByFilters(params)).thenReturn(0L);
//        when(mapper.toRunResponseList(any())).thenReturn(List.of());
//
//        PagedResponse<RunResponse> result = runService.getFilteredRuns(params);
//
//        assertThat(result.getTotalCount()).isEqualTo(0);
//        assertThat(result.getResults()).isEmpty();
//        assertThat(result.getTotalPages()).isEqualTo(0);
//    }
//
//    @Test
//    void getFilteredRuns_paginationCalculatedCorrectly() {
//        RunFilterParams params = RunFilterParams.builder()
//                .projectId("proj-001").moduleId("mod-001")
//                .page(1).size(5).sort("-createdAt")
//                .build();
//
//        when(runRepository.findByFilters(params)).thenReturn(List.of(sampleRun));
//        when(runRepository.countByFilters(params)).thenReturn(12L); // 3 pages of 5
//        when(mapper.toRunResponseList(any())).thenReturn(List.of(toResponse(sampleRun)));
//
//        PagedResponse<RunResponse> result = runService.getFilteredRuns(params);
//
//        assertThat(result.getTotalPages()).isEqualTo(3);
//        assertThat(result.isHasNext()).isTrue();      // page 1, totalPages 3
//        assertThat(result.isHasPrevious()).isTrue();  // page > 0
//    }
//
//    // ── Clone tests ───────────────────────────────────────────────────
//
//    @Test
//    void cloneRun_copiesConfigAndClearsResults() {
//        when(runRepository.findById("run-001")).thenReturn(Optional.of(sampleRun));
//        when(runRepository.save(any(Run.class))).thenAnswer(inv -> {
//            Run r = inv.getArgument(0);
//            r.setId("run-002-clone");
//            return r;
//        });
//        when(mapper.toRunResponse(any(Run.class))).thenAnswer(inv -> toResponse(inv.getArgument(0)));
//
//        RunResponse cloned = runService.cloneRun("run-001");
//
//        assertThat(cloned.getRunName()).contains("Clone");
//        assertThat(cloned.getStatus()).isEqualTo(RunStatus.DRAFT);
//        // resultStatement must be copied to clone
//        assertThat(cloned.getResultStatement()).isEqualTo("Assert: user dashboard visible");
//    }
//
//    @Test
//    void cloneRun_scenarioResultFieldsAreCleared() {
//        // Give original scenarios some result data
//        sampleRun.getScenariosList().get(0).setResultCsv("s3://results/old.csv");
//        sampleRun.getScenariosList().get(0).setScreenshots(List.of("s3://screenshots/old.png"));
//        sampleRun.getScenariosList().get(0).setStatus(ScenarioStatus.PASSED);
//
//        when(runRepository.findById("run-001")).thenReturn(Optional.of(sampleRun));
//        when(runRepository.save(any(Run.class))).thenAnswer(inv -> {
//            Run r = inv.getArgument(0);
//            r.setId("clone-id");
//            return r;
//        });
//        when(mapper.toRunResponse(any(Run.class))).thenAnswer(inv -> toResponse(inv.getArgument(0)));
//
//        runService.cloneRun("run-001");
//
//        verify(runRepository).save(argThat(run -> {
//            Scenario firstScenario = run.getScenariosList().get(0);
//            return firstScenario.getResultCsv() == null
//                    && firstScenario.getScreenshots() == null
//                    && firstScenario.getStatus() == ScenarioStatus.PENDING;
//        }));
//    }
//
//    // ── resultStatement correctness ───────────────────────────────────
//
//    @Test
//    void resultStatement_isOnRunNotScenario() {
//        // Verify that no scenario has a resultStatement field
//        sampleRun.getScenariosList().forEach(scenario -> {
//            // ScenarioType should never be RESULT_STATEMENT
//            assertThat(scenario.getType()).isNotNull();
//            assertThat(scenario.getType().name()).doesNotContain("RESULT_STATEMENT");
//        });
//        // And the run-level field is set correctly
//        assertThat(sampleRun.getResultStatement()).isNotNull();
//        assertThat(sampleRun.getResultStatement()).contains("Assert");
//    }
//
//    // ── Helper ────────────────────────────────────────────────────────
//
//    private RunResponse toResponse(Run run) {
//        return RunResponse.builder()
//                .id(run.getId())
//                .runName(run.getRunName())
//                .createdBy(run.getCreatedBy())
//                .projectId(run.getProjectId())
//                .moduleId(run.getModuleId())
//                .status(run.getStatus())
//                .runType(run.getRunType())
//                .resultStatement(run.getResultStatement())
//                .scenarioCount(run.getScenarioCount())
//                .tags(run.getTags())
//                .createdAt(run.getCreatedAt())
//                .updatedAt(run.getUpdatedAt())
//                .build();
//    }
//}