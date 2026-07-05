package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FlowService {

    @Autowired
    private FlowRepository flowRepository;

    public Flow saveFlow(Flow flow) {
        if (flow.getId() == null) {
            flow.setCreatedAt(Instant.now());
        }
        flow.setUpdatedAt(Instant.now());
        return flowRepository.save(flow);
    }

    public Flow saveAsDraft(Flow flow) {
        flow.setExecutionStatus(ExecutionStatus.NOT_STARTED);
        return saveFlow(flow);
    }

    public List<Flow> getFlowsByModule(String projectId, String moduleId) {
        return flowRepository.findByProjectIdAndModuleId(projectId, moduleId);
    }

    public Optional<Flow> getFlowById(String id) {
        return flowRepository.findById(id);
    }

    public void deleteFlow(String id) {
        flowRepository.deleteById(id);
    }

    public Flow cloneFlow(String id) {
        Optional<Flow> optionalFlow = flowRepository.findById(id);
        if (optionalFlow.isEmpty()) {
            throw new RuntimeException("Flow not found with id: " + id);
        }

        Flow original = optionalFlow.get();
        Flow clone = new Flow();
        
        clone.setProjectId(original.getProjectId());
        clone.setModuleId(original.getModuleId());
        clone.setName(original.getName() + " - Copy");
        clone.setDescription(original.getDescription());
//        clone.setVersion(1);
        clone.setDefaultWait(original.getDefaultWait());
        clone.setExecutionStatus(ExecutionStatus.DRAFT);
        clone.setCreatedAt(Instant.now());
        clone.setUpdatedAt(Instant.now());
        
        // Deep copy the steps to avoid sharing object references or database IDs
        List<com.testingautomation.testautomation.entities.flow.FlowStep> clonedSteps = new java.util.ArrayList<>();
        if (original.getSteps() != null) {
            for (com.testingautomation.testautomation.entities.flow.FlowStep step : original.getSteps()) {
                com.testingautomation.testautomation.entities.flow.FlowStep stepClone = new com.testingautomation.testautomation.entities.flow.FlowStep();
                stepClone.setStepOrder(step.getStepOrder());
                stepClone.setName(step.getName());
                stepClone.setActionType(step.getActionType());
                stepClone.setVerificationType(step.getVerificationType());
                stepClone.setSelector(step.getSelector());
                stepClone.setValue(step.getValue());
                stepClone.setExpectedValue(step.getExpectedValue());
                stepClone.setAttribute(step.getAttribute());
                stepClone.setOverrideWait(step.getOverrideWait());
                stepClone.setWait(step.getWait());
                stepClone.setRetryCount(step.getRetryCount());
                stepClone.setContinueOnFailure(step.getContinueOnFailure());
                stepClone.setCaptureScreenshot(step.getCaptureScreenshot());
//                stepClone.setEnabled(step.getEnabled());
//                stepClone.setRemarks(step.getRemarks());
                clonedSteps.add(stepClone);
            }
        }
        clone.setSteps(clonedSteps);

        return flowRepository.save(clone);
    }
}
