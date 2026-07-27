package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FlowService {
    @Value("${S3_BUCKET}")
    private String bucketName;
    @Value("${S3_BASE_PREFIX}")
    private String prefix;

    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private StorageService storageService;

    @Autowired
    private FlowOrchestratorService flowOrchestratorService;

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
        Flow flow =flowRepository.flowWithProjIdAndModId(id);
        if(flow==null){
            throw new GlobalExceptionHandler.ResourceNotFoundException("Already been deleted !");
        }
        String projectId = flow.getProjectId();
        String moduleId = flow.getModuleId();

        String basePrefix=prefix+"/"+ projectId+ "/" + moduleId + "/" + id;
        if(storageService.doesPrefixHaveObjects(bucketName,basePrefix)){
            storageService.deleteFolder(bucketName,basePrefix);
        }
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

    public void executeAllFlows(List<String> flowIds) {
        executeAllFlows(flowIds, null);
    }

    /**
     * Executes all flows with an optional environment override.
     * When environmentId is provided, NAVIGATE steps in every flow will have
     * their origin replaced with the environment's baseUrl before execution.
     *
     * @param flowIds       IDs of flows to execute sequentially
     * @param environmentId Optional environment ID for URL override; null means use recorded URLs
     */
    public void executeAllFlows(List<String> flowIds, String environmentId) {
        List<Flow> flowsToExecute = new ArrayList<>();
        for (String flowId : flowIds) {
            Optional<Flow> flowOpt = flowRepository.findById(flowId);
            flowOpt.ifPresent(flowsToExecute::add);
        }
        if (!flowsToExecute.isEmpty()) {
            flowOrchestratorService.orchestrateQueue(flowsToExecute, environmentId);
        }
    }

    public List<String> getFlowIdsByModuleId(String moduleId) {
        return flowRepository.getFlowsOnlyIdsByModuleId(moduleId).stream()
                .map(Flow::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> getFlowIdsByProjectId(String projectId) {
        return flowRepository.getFlowsOnlyIdsByProjectId(projectId).stream()
                .map(Flow::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean stopFlow(String flowId) {
        return flowOrchestratorService.stopFlow(flowId);
    }

    public void stopModule(String moduleId) {
        flowOrchestratorService.stopModule(moduleId);
    }

    public void stopProject(String projectId) {
        flowOrchestratorService.stopProject(projectId);
    }

    public void stopQueue() {
        flowOrchestratorService.stopAll();
    }
}
