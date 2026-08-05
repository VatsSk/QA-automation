package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.config.WebDriverConfig.WebDriverFactory;
import com.testingautomation.testautomation.entities.ProjectEnvironment;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import com.testingautomation.testautomation.services.ProjectEnvironmentService;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import com.testingautomation.testautomation.utils.NavigationUrlResolver;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FlowOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(FlowOrchestratorService.class);

    private final Map<String, Flow> activeFlows = new ConcurrentHashMap<>();
    private final Map<String, WebDriver> activeDrivers = new ConcurrentHashMap<>();
    private final Set<String> cancelledFlowIds = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelledModuleIds = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelledProjectIds = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelAll = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Optional<Flow> getActiveFlow(String id) {
        return Optional.ofNullable(activeFlows.get(id));
    }

    @Autowired
    private WebDriverFactory webDriverFactory;

    @Autowired
    private FlowExecutionService flowExecutionService;
    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowSseService flowSseService;

    @Value("${storage.s3.base-prefix}")
    private String basePrefix;
    @Value("${storage.s3.bucket-name}")
    private String bucket;
    @Autowired
    private StorageService storageService;

    @Autowired
    private NavigationUrlResolver navigationUrlResolver;

    @Autowired
    private ProjectEnvironmentService environmentService;

    private boolean isCancelled(Flow flow) {
        return cancelAll ||
               cancelledFlowIds.contains(flow.getId()) ||
               (flow.getModuleId() != null && cancelledModuleIds.contains(flow.getModuleId())) ||
               (flow.getProjectId() != null && cancelledProjectIds.contains(flow.getProjectId()));
    }

    public boolean stopFlow(String flowId) {
        logger.info("Request received to stop flow [{}]", flowId);
        cancelledFlowIds.add(flowId);
        WebDriver driver = activeDrivers.get(flowId);
        if (driver != null) {
            try {
                logger.info("Quitting WebDriver for stopped flow [{}]", flowId);
                driver.quit();
            } catch (Exception e) {
                logger.warn("Error quitting driver for stopped flow [{}]: {}", flowId, e.getMessage());
            }
        }
        Flow flow = activeFlows.get(flowId);
        if (flow != null) {
            flow.setExecutionStatus(ExecutionStatus.CANCELLED);
            flow.setExecutionMessage("Execution cancelled by user");
            flowRepository.save(flow);
            flowSseService.sendFlowCompleted(flow);
            return true;
        }
        return false;
    }

    public void stopModule(String moduleId) {
        logger.info("Request received to stop module [{}]", moduleId);
        cancelledModuleIds.add(moduleId);
        for (Flow activeFlow : activeFlows.values()) {
            if (moduleId.equals(activeFlow.getModuleId())) {
                stopFlow(activeFlow.getId());
            }
        }
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(5000); } catch (Exception ignored) {}
            cancelledModuleIds.remove(moduleId);
        });
    }

    public void stopProject(String projectId) {
        logger.info("Request received to stop project [{}]", projectId);
        cancelledProjectIds.add(projectId);
        for (Flow activeFlow : activeFlows.values()) {
            if (projectId.equals(activeFlow.getProjectId())) {
                stopFlow(activeFlow.getId());
            }
        }
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(5000); } catch (Exception ignored) {}
            cancelledProjectIds.remove(projectId);
        });
    }

    public void stopAll() {
        logger.info("Request received to stop all/queue executions");
        cancelAll = true;
        for (String flowId : new ArrayList<>(activeFlows.keySet())) {
            stopFlow(flowId);
        }
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(5000); } catch (Exception ignored) {}
            cancelAll = false;
        });
    }

    public void orchestrateQueue(java.util.List<Flow> flows) {
        orchestrateQueue(flows, null);
    }

    /**
     * Runs a queue of flows against a specific environment.
     * Each NAVIGATE step will have its origin replaced with the environment's baseUrl.
     */
    public void orchestrateQueue(java.util.List<Flow> flows, String environmentId) {
        logger.info("Orchestrating sequential background execution for {} flows with environmentId [{}]",
                flows.size(), environmentId);
        CompletableFuture.runAsync(() -> {
            for (Flow flow : flows) {
                if (isCancelled(flow)) {
                    logger.info("Skipping flow [{}] because execution was cancelled.", flow.getName());
                    flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                    flow.setExecutionMessage("Execution cancelled by user");
                    flow.setUpdatedAt(Instant.now());
                    flowRepository.save(flow);
                    continue;
                }
                ProjectEnvironment environment = null;
                if (environmentId != null && !environmentId.isBlank()) {
                    try {
                        environment = environmentService.findById(environmentId).orElse(null);
                    } catch (Exception ignored) {}
                }
                executeFlowInternal(flow, environment);
            }
        }, executor);
    }

    /**
     * Executes a flow using the recorded URLs as-is.
     * Backward compatible — all existing callers use this.
     */
    public void executeFlow(Flow flow) {
        executeFlow(flow, null);
    }

    /**
     * Executes a flow with environment-based URL override.
     * Loads the environment by ID and replaces the origin of every NAVIGATE step
     * with the environment's baseUrl before execution.
     * The stored Flow document is NOT modified.
     *
     * @param flow          The flow to execute
     * @param environmentId The ID of the selected environment, or null for no override
     */
    public void executeFlow(Flow flow, String environmentId) {
        CompletableFuture.runAsync(() -> {
            ProjectEnvironment environment = null;
            if (environmentId != null && !environmentId.isBlank()) {
                environment = environmentService.findById(environmentId)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Environment not found: " + environmentId));
                logger.info("Executing flow [{}] with environment [{}] (baseUrl: {})",
                        flow.getName(), environment.getName(), environment.getBaseUrl());
                flow.setEnvironment(environment.getName());
                flow.setBaseUrl(environment.getBaseUrl());
            }
            executeFlowInternal(flow, environment);
        }, executor);
    }

    /**
     * Internal execution method shared by both public overloads.
     * When environment is null, behaves exactly as before.
     * When environment is set, resolves NAVIGATE step URLs before each step executes.
     */
    private void executeFlowInternal(Flow flow, ProjectEnvironment environment) {
        if (isCancelled(flow)) {
            logger.info("Flow [{}] execution cancelled before starting.", flow.getName());
            flow.setExecutionStatus(ExecutionStatus.CANCELLED);
            flow.setExecutionMessage("Execution cancelled by user");
            flow.setUpdatedAt(Instant.now());
            flowRepository.save(flow);
            flowSseService.sendFlowCompleted(flow);
            return;
        }
        activeFlows.put(flow.getId(), flow);
        WebDriver driver = null;
        try {
            // ── Step 1: Override NAVIGATE URLs BEFORE marking the flow as RUNNING ──
            // Resolve all NAVIGATE step URLs upfront using the selected environment.
            // This happens before any status change or DB write, so if resolution
            // fails for any reason the flow never enters the RUNNING state.
            if (environment != null && flow.getSteps() != null) {
                for (FlowStep step : flow.getSteps()) {
                    if (step.getActionType() == ActionType.NAVIGATE) {
                        String resolvedUrl = navigationUrlResolver.resolve(step, environment);
                        step.setValue(resolvedUrl);
                        logger.info("Pre-resolved NAVIGATE step [{}]: {}", step.getName(), resolvedUrl);
                    }
                }
            }

            // ── Step 2: Mark flow as RUNNING and persist ──
            flow.setExecutionStartedAt(Instant.now());
            flow.setExecutionCompletedAt(null);
            flow.setExecutionStatus(ExecutionStatus.RUNNING);
            flow.setExecutionMessage(null);
            String flowPrefix = basePrefix + "/" + flow.getProjectId() + "/" + flow.getModuleId() + "/" + flow.getId();
            // Deleting existing objects from s3 for this run
            if (storageService.doesPrefixHaveObjects(bucket, flowPrefix)) {
                storageService.deleteFolderExceptTestCase(bucket, flowPrefix);
            }
            flow.setFlowBasePath(flowPrefix);
            flow.setUpdatedAt(Instant.now());
            for (FlowStep step : flow.getSteps()) {
                step.setExecutionMessage(null);
                step.setExecutionCompletedAt(null);
                step.setExecutionStartedAt(null);
                step.setExecutionStatus(ExecutionStatus.DRAFT);
            }
            // This save persists both the RUNNING status AND the resolved NAVIGATE URLs
            flowRepository.save(flow);
            // SSE: notify clients that flow execution has started
            flowSseService.sendFlowStarted(flow);
            logger.info("Initializing WebDriver for flow: {}", flow.getName());

            driver = webDriverFactory.createDriver();
            if (driver != null) {
                activeDrivers.put(flow.getId(), driver);
            }
            if (isCancelled(flow)) {
                logger.info("Flow [{}] execution cancelled after driver creation.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                flow.setExecutionMessage("Execution cancelled by user");
                return;
            }

            if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
                logger.warn("Flow [{}] has no steps to execute.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.PASSED);
                flow.setExecutionMessage("No steps to execute");
                flow.setExecutionCompletedAt(Instant.now());
                flow.setUpdatedAt(Instant.now());
                flowRepository.save(flow);
                flowSseService.sendFlowCompleted(flow);
                return;
            }

            // ── Step 3: Execute steps — URLs are already resolved, no env logic here ──
            for (FlowStep step : flow.getSteps()) {
                if (isCancelled(flow)) {
                    logger.info("Flow [{}] cancelled before step [{}].", flow.getName(), step.getName());
                    flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                    flow.setExecutionMessage("Execution cancelled by user");
                    break;
                }
                try {
                    flowExecutionService.executeStep(driver, step, flow);
                } catch (Exception e) {
                    if (isCancelled(flow)) {
                        logger.info("Flow [{}] step interrupted due to user cancellation.", flow.getName());
                        flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                        flow.setExecutionMessage("Execution cancelled by user");
                        break;
                    } else {
                        throw e;
                    }
                }
            }
            if (flow.getExecutionStatus() == ExecutionStatus.CANCELLED) {
                return;
            }

            flow.setExecutionStatus(ExecutionStatus.PASSED);
            flow.setExecutionMessage("Flow executed successfully");
            logger.info("Successfully executed flow: {}", flow.getName());

        } catch (GlobalExceptionHandler.FlowExecutionException ex) {
            if (isCancelled(flow)) {
                logger.info("Flow [{}] execution cancelled during step execution.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                flow.setExecutionMessage("Execution cancelled by user");
            } else {
                logger.error("Error executing flow [{}]: {}", flow.getName(), ex.getMessage(), ex);
                flow.setExecutionStatus(ExecutionStatus.FAILED);
                logger.info("user message {}", ex.getUserMessage());
                flow.setExecutionMessage(ex.getUserMessage());
            }
        } catch (Exception e) {
            if (isCancelled(flow)) {
                logger.info("Flow [{}] execution cancelled during step execution.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.CANCELLED);
                flow.setExecutionMessage("Execution cancelled by user");
            } else {
                logger.error("Error executing flow [{}]: {}", flow.getName(), e.getMessage(), e);
                flow.setExecutionStatus(ExecutionStatus.FAILED);
                flow.setExecutionMessage("Unexpectedly step failed!");
            }
        } finally {
            activeFlows.remove(flow.getId());
            activeDrivers.remove(flow.getId());
            cancelledFlowIds.remove(flow.getId());
            flow.setExecutionCompletedAt(Instant.now());
            flow.setUpdatedAt(Instant.now());
            flowRepository.save(flow);
            // SSE: send final state and complete all emitters
            if (flow.getExecutionStatus() == ExecutionStatus.FAILED) {
                flowSseService.sendFlowFailed(flow);
            } else {
                flowSseService.sendFlowCompleted(flow);
            }
            if (driver != null) {
                try {
                    logger.info("Quitting WebDriver for flow: {}", flow.getName());
                    driver.quit();
                } catch (Exception ignored) {}
            }
        }
    }
}
