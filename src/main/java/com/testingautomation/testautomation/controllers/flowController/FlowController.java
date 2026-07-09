package com.testingautomation.testautomation.controllers.flowController;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.services.flowService.FlowService;
import com.testingautomation.testautomation.services.flowService.FlowSseService;
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
    private com.testingautomation.testautomation.services.flowService.FlowOrchestratorService flowOrchestratorService;

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
        Optional<Flow> flow = flowService.getFlowById(id);
        return flow.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<String> runFlow(@PathVariable String id) {
        Optional<Flow> flowOptional = flowService.getFlowById(id);
        if (flowOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Flow flow = flowOptional.get();
        logger.info("Triggering execution for Flow: {}", flow.getName());
        
        // Pass to orchestrator which will handle it in a background thread
        flowOrchestratorService.orchestrate(flow);
        
        return ResponseEntity.ok("Flow execution started for: " + flow.getName());
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

