package com.testingautomation.testautomation.controllers.flowController;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.services.flowService.FlowOrchestratorService;
import com.testingautomation.testautomation.services.flowService.FlowService;
import com.testingautomation.testautomation.services.flowService.FlowSseService;
import com.testingautomation.testautomation.services.flowService.WebDriverRegistry;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/flows")
public class FlowController {

    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private FlowService flowService;

    @Autowired
    private FlowSseService flowSseService;

    @Autowired
    private FlowOrchestratorService flowOrchestratorService;

    @Autowired
    private WebDriverRegistry webDriverRegistry;

    @PutMapping("/{id}")
    public ResponseEntity<Flow> createOrUpdateFlow(@PathVariable String id,@RequestBody Flow flow) {
        System.out.println("Updating Id");
        Flow savedFlow = flowService.saveFlow(flow);
        return ResponseEntity.ok(savedFlow);
    }

    @PostMapping("/draft")
    public ResponseEntity<Flow> saveFlowAsDraft(@RequestBody Flow flow) {
        Flow draftFlow = flowService.saveAsDraft(flow);
        return ResponseEntity.ok(draftFlow);
    }

    @GetMapping("/{projectId}/{moduleId}")
    public ResponseEntity<List<Flow>> getFlows(@PathVariable String projectId, @PathVariable String moduleId) {
        List<Flow> flows = flowService.getFlowsByModule(projectId, moduleId);
        return ResponseEntity.ok(flows);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Flow> getFlowById(@PathVariable String id) {
        Optional<Flow> activeFlow = flowOrchestratorService.getActiveFlow(id);
        if (activeFlow.isPresent()) {
            return ResponseEntity.ok(activeFlow.get());
        }
        Optional<Flow> flow = flowService.getFlowById(id);
        return flow.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<String> runFlow(
            @PathVariable String id,
            @RequestParam(required = false) String environmentId) {
        Optional<Flow> flowOptional = flowService.getFlowById(id);
        if (flowOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Flow flow = flowOptional.get();
        logger.info("Triggering execution for Flow: {} with environmentId: {}", flow.getName(), environmentId);

        // Close any paused browser session for this flow before starting fresh
        WebDriver existingDriver = webDriverRegistry.getDriver(id);
        if (existingDriver != null) {
            logger.info("Closing existing paused WebDriver for flow [{}] before new run", id);
            try { existingDriver.quit(); } catch (Exception ignored) {}
            webDriverRegistry.removeDriver(id);
        }

        flowOrchestratorService.executeFlow(flow, environmentId);
        return ResponseEntity.ok("Flow execution started for: " + flow.getName());
    }
    @PostMapping("/execute-queue")
    public ResponseEntity<?> executeQueue(
            @RequestBody List<String> flowIds,
            @RequestParam(required = false) String environmentId) {
        logger.info("Executing queue: {} with environmentId: {}", flowIds, environmentId);
        flowService.executeAllFlows(flowIds, environmentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/execute-module/{moduleId}")
    public ResponseEntity<?> executeModule(
            @PathVariable String moduleId,
            @RequestParam(required = false) String environmentId) {
        logger.info("Executing module: {} with environmentId: {}", moduleId, environmentId);
        List<String> flowIds = flowService.getFlowIdsByModuleId(moduleId);
        logger.info("Executing flows: {}", flowIds);
        flowService.executeAllFlows(flowIds, environmentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/execute-project/{projectId}")
    public ResponseEntity<?> executeProject(
            @PathVariable String projectId,
            @RequestParam(required = false) String environmentId) {
        List<String> flowIds = flowService.getFlowIdsByProjectId(projectId);
        flowService.executeAllFlows(flowIds, environmentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopFlow(@PathVariable String id) {
        logger.info("Stopping flow: {}", id);
        boolean stopped = flowService.stopFlow(id);
        if (stopped) {
            return ResponseEntity.ok("Flow execution stopped for: " + id);
        }
        return ResponseEntity.ok("Flow was not actively running or already stopped: " + id);
    }

    @PostMapping("/stop-queue")
    public ResponseEntity<?> stopQueue() {
        logger.info("Stopping queue execution");
        flowService.stopQueue();
        return ResponseEntity.ok("Queue execution stopped");
    }

    @PostMapping("/stop-module/{moduleId}")
    public ResponseEntity<?> stopModule(@PathVariable String moduleId) {
        logger.info("Stopping module execution: {}", moduleId);
        flowService.stopModule(moduleId);
        return ResponseEntity.ok("Module execution stopped: " + moduleId);
    }

    @PostMapping("/stop-project/{projectId}")
    public ResponseEntity<?> stopProject(@PathVariable String projectId) {
        logger.info("Stopping project execution: {}", projectId);
        flowService.stopProject(projectId);
        return ResponseEntity.ok("Project execution stopped: " + projectId);
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<Flow> cloneFlow(@PathVariable String id) {
        try {
            Flow clonedFlow = flowService.cloneFlow(id);
            return ResponseEntity.ok(clonedFlow);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * SSE endpoint for real-time flow execution progress.
     * Clients connect to this endpoint to receive step-by-step updates
     * as the flow executes in the background.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFlow(@PathVariable String id) {
        logger.info("SSE stream requested for flow [{}]", id);
        return flowSseService.register(id);
    }
}

