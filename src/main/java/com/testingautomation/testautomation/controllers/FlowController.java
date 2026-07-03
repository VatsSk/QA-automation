package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.services.flowService.FlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/flows")
public class FlowController {

    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private FlowService flowService;

    @PostMapping
    public ResponseEntity<Flow> createOrUpdateFlow(@RequestBody Flow flow) {
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
        logger.info("Triggered execution for Flow: {}", flow.getName());
        
        // TODO: Pass this flow to a background execution orchestrator that uses FlowExecutionService
        // and launches Selenium. This should be run asynchronously so the API doesn't hang.
        
        return ResponseEntity.ok("Flow execution started for: " + flow.getName());
    }
}
