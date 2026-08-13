package com.testingautomation.testautomation.controllers.flowController;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import com.testingautomation.testautomation.services.flowService.FlowOrchestratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flows/debug")
public class FlowDebugController {

    @Autowired
    private FlowOrchestratorService flowOrchestratorService;

    @Autowired
    private FlowRepository flowRepository;

    @PutMapping("/{flowId}/steps/{stepOrder}")
    public ResponseEntity<?> editStep(@PathVariable String flowId, @PathVariable int stepOrder, @RequestBody FlowStep updatedStep) {
        Flow flow = flowRepository.findById(flowId).orElse(null);
        if (flow == null) {
            return ResponseEntity.notFound().build();
        }

        boolean updated = false;
        for (int i = 0; i < flow.getSteps().size(); i++) {
            FlowStep step = flow.getSteps().get(i);
            if (step.getStepOrder() == stepOrder) {
                // Keep the original id if any, but update everything else
                updatedStep.setId(step.getId());
                flow.getSteps().set(i, updatedStep);
                updated = true;
                break;
            }
        }

        if (!updated) {
            return ResponseEntity.notFound().build();
        }

        flowRepository.save(flow);
        return ResponseEntity.ok("Step updated successfully");
    }

    @PostMapping("/{flowId}/resume")
    public ResponseEntity<?> resumeFlow(@PathVariable String flowId, @RequestParam int stepNo) {
        flowOrchestratorService.resumeFlow(flowId, stepNo);
        return ResponseEntity.ok("Flow resumption initiated");
    }
}
