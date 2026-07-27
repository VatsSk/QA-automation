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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FlowOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(FlowOrchestratorService.class);

    private final Map<String, Flow> activeFlows = new ConcurrentHashMap<>();

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

//    public void orchestrate(Flow flow) {
//        logger.info("Orchestrating background execution for flow: {}", flow.getName());
//        CompletableFuture.runAsync(() -> executeFlow(flow));
//    }

    public void orchestrateQueue(java.util.List<Flow> flows) {
        logger.info("Orchestrating sequential background execution for {} flows", flows.size());
        for (Flow flow : flows) {
            executeFlow(flow);
        }
    }

    /**
     * Runs a queue of flows against a specific environment.
     * Each NAVIGATE step will have its origin replaced with the environment's baseUrl.
     */
    public void orchestrateQueue(java.util.List<Flow> flows, String environmentId) {
        logger.info("Orchestrating sequential background execution for {} flows with environmentId [{}]",
                flows.size(), environmentId);
        for (Flow flow : flows) {
            executeFlow(flow, environmentId);
        }
    }

    /**
     * Executes a flow using the recorded URLs as-is.
     * Backward compatible — all existing callers use this.
     */
    public void executeFlow(Flow flow) {
        executeFlowInternal(flow, null);
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
        ProjectEnvironment environment = null;
        if (environmentId != null && !environmentId.isBlank()) {
            environment = environmentService.findById(environmentId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Environment not found: " + environmentId));
            logger.info("Executing flow [{}] with environment [{}] (baseUrl: {})",
                    flow.getName(), environment.getName(), environment.getBaseUrl());
        }
        executeFlowInternal(flow, environment);
    }

    /**
     * Internal execution method shared by both public overloads.
     * When environment is null, behaves exactly as before.
     * When environment is set, resolves NAVIGATE step URLs before each step executes.
     */
    private void executeFlowInternal(Flow flow, ProjectEnvironment environment) {
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
                flowExecutionService.executeStep(driver, step, flow);
            }

            flow.setExecutionStatus(ExecutionStatus.PASSED);
            flow.setExecutionMessage("Flow executed successfully");
            logger.info("Successfully executed flow: {}", flow.getName());

        } catch (GlobalExceptionHandler.FlowExecutionException ex) {
            logger.error("Error executing flow [{}]: {}", flow.getName(), ex.getMessage(), ex);
            flow.setExecutionStatus(ExecutionStatus.FAILED);
            logger.info("user message {}", ex.getUserMessage());
            flow.setExecutionMessage(ex.getUserMessage());
        } catch (Exception e) {
            logger.error("Error executing flow [{}]: {}", flow.getName(), e.getMessage(), e);
            flow.setExecutionStatus(ExecutionStatus.FAILED);
            flow.setExecutionMessage("Unexpectedly step failed!");
        } finally {
            activeFlows.remove(flow.getId());
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
                logger.info("Quitting WebDriver for flow: {}", flow.getName());
                driver.quit();
            }
        }
    }
}
