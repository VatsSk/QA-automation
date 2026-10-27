package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.responseDto.RunResponse;
import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.entities.component.FlowItem;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowVariableDefinition;
import java.util.HashMap;
import java.util.Map;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FlowService {
    private static final Logger logger = LoggerFactory.getLogger(FlowService.class);
    @Value("${S3_BUCKET}")
    private String bucketName;
    @Value("${S3_BASE_PREFIX}")
    private String prefix;

    @Autowired
    private FlowRepository flowRepository;
    
    @Autowired
    private com.testingautomation.testautomation.repositories.flowRepos.FlowInfoRepository flowInfoRepository;
    @Autowired
    private ComponentFlowExecutionService componentFlowExecutionService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private FlowOrchestratorService flowOrchestratorService;

    @Autowired
    private FlowVariableService flowVariableService;

    public Flow saveFlow(Flow flow) {
        if (flow.getId() == null) {
            flow.setVersion(1);
            flow.setCreatedAt(Instant.now());
        } else {
            // Prevent accidental loss of variables during updates if request did not specify variables
            Optional<Flow> existingOpt = flowRepository.findById(flow.getId());
            if (existingOpt.isPresent()) {
                Flow existing = existingOpt.get();
                if (flow.getVariables() == null && existing.getVariables() != null) {
                    flow.setVariables(existing.getVariables());
                }
            }
        }
        // Validate variable definitions before persisting
        flowVariableService.validateDefinitions(flow.getVariables());
        flow.setUpdatedAt(Instant.now());
        if(flow.getCreatedAt() == null){
            flow.setCreatedAt(flow.getUpdatedAt());
        }
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
        try {
            flowInfoRepository.deleteById(id);
        } catch (Exception ignored) {}
    }

    public Flow cloneFlow(String id) {
        Optional<Flow> optionalFlow = flowRepository.findById(id);
        if (optionalFlow.isEmpty()) {
            throw new RuntimeException("Flow not found with id: " + id);
        }

        Flow original = optionalFlow.get();

        // 1. If this is a Component Flow, clone its FlowInfo definition and re-materialize
        Optional<FlowInfo> flowInfoOpt = flowInfoRepository.findById(id);
        if (flowInfoOpt.isPresent() || original.isPartComp()) {
            if (flowInfoOpt.isPresent()) {
                FlowInfo originalInfo = flowInfoOpt.get();
                FlowInfo cloneInfo = new FlowInfo();

                cloneInfo.setName(original.getName() + " - Copy");
                cloneInfo.setDescription(original.getDescription());
                cloneInfo.setProjectId(original.getProjectId());
                cloneInfo.setModuleId(original.getModuleId());
                cloneInfo.setContainsComp(true);
                cloneInfo.setCreatedAt(Instant.now());
                cloneInfo.setUpdatedAt(Instant.now());

                List<FlowItem> clonedItems = new ArrayList<>();
                if (originalInfo.getFlowItems() != null) {
                    for (FlowItem item : originalInfo.getFlowItems()) {
                        FlowItem itemClone = new FlowItem();
                        itemClone.setOrder(item.getOrder());
                        itemClone.setType(item.getType());
                        itemClone.setComponentId(item.getComponentId());
                        itemClone.setComponentName(item.getComponentName());
                        itemClone.setModuleName(item.getModuleName());
                        if (item.getStep() != null) {
                            itemClone.setStep(getFlowStep(item.getStep()));
                        }
                        clonedItems.add(itemClone);
                    }
                }
                cloneInfo.setFlowItems(clonedItems);

                // Save FlowInfo first so it generates its own unique ID
                FlowInfo savedInfo = flowInfoRepository.save(cloneInfo);
                savedInfo.setFlowId(savedInfo.getId());
                savedInfo = flowInfoRepository.save(savedInfo);

                // Materialize the runtime Flow with matching ID, runtime steps, and isPartComp = true
                Flow materializedFlow = componentFlowExecutionService.materializeFlow(savedInfo);
                materializedFlow.setExecutionStatus(ExecutionStatus.DRAFT);
                materializedFlow.setDefaultWait(original.getDefaultWait());
                materializedFlow.setUrlChangeWait(original.getUrlChangeWait());
                materializedFlow.setVersion(original.getVersion());
                return flowRepository.save(materializedFlow);
            }
        }

        // 2. Standard Flow cloning
        Flow clone = new Flow();
        clone.setProjectId(original.getProjectId());
        clone.setModuleId(original.getModuleId());
        clone.setName(original.getName() + " - Copy");
        clone.setDescription(original.getDescription());
        clone.setVersion(original.getVersion());
        clone.setDefaultWait(original.getDefaultWait());
        clone.setUrlChangeWait(original.getUrlChangeWait());
        clone.setPartComp(false);
        clone.setExecutionStatus(ExecutionStatus.DRAFT);
        clone.setCreatedAt(Instant.now());
        clone.setUpdatedAt(Instant.now());

        // Deep copy the steps to avoid sharing object references or database IDs
        List<FlowStep> clonedSteps = new ArrayList<>();
        if (original.getSteps() != null) {
            for (FlowStep step : original.getSteps()) {
                clonedSteps.add(getFlowStep(step));
            }
        }
        clone.setSteps(clonedSteps);

        // Deep copy variable definitions
        logger.info("Cloning the flow with variables {}",original.getVariables());
        if (original.getVariables() != null) {
            Map<String, FlowVariableDefinition> clonedVars = new HashMap<>();
            for (Map.Entry<String, FlowVariableDefinition> entry : original.getVariables().entrySet()) {
                FlowVariableDefinition def = entry.getValue();
                if (def != null) {
                    clonedVars.put(entry.getKey(), new FlowVariableDefinition(
                            def.getType(),
                            def.getLength(),
                            def.getCharset(),
                            def.getPrefix(),
                            def.getSuffix()
                    ));
                }
            }
            logger.info("Cloned variables {}",clonedVars);
            clone.setVariables(clonedVars);
        }
        return flowRepository.save(clone);
    }

    private FlowStep getFlowStep(FlowStep step) {
        FlowStep stepClone = new FlowStep();
        stepClone.setDescription(step.getDescription());
        stepClone.setStepOrder(step.getStepOrder());
        stepClone.setName(step.getName());
        stepClone.setActionType(step.getActionType());
        stepClone.setVerificationType(step.getVerificationType());
        stepClone.setSelector(step.getSelector());
        stepClone.setValue(step.getValue());
        stepClone.setUrl(step.getUrl());
        stepClone.setExpectedValue(step.getExpectedValue());
        stepClone.setAttribute(step.getAttribute());
        stepClone.setTextSource(step.getTextSource());
        stepClone.setOverrideWait(step.getOverrideWait());
        stepClone.setWait(step.getWait());
        stepClone.setRetryCount(step.getRetryCount());
        stepClone.setContinueOnFailure(step.getContinueOnFailure());
        stepClone.setCaptureScreenshot(step.getCaptureScreenshot());
        stepClone.setIsComp(step.getIsComp());
        stepClone.setComponentId(step.getComponentId());
        stepClone.setComponentName(step.getComponentName());
        return stepClone;
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
