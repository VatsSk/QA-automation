package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.component.Component;
import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.entities.component.FlowItem;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.FlowItemType;
import com.testingautomation.testautomation.repositories.flowRepos.ComponentRepository;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComponentFlowExecutionService {

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowOrchestratorService flowOrchestratorService;

    public Flow materializeFlow(FlowInfo flowInfo) {
        Flow flow = new Flow();
        // Link to FlowInfo ID so it overwrites on subsequent runs
        flow.setId(flowInfo.getId());
        flow.setProjectId(flowInfo.getProjectId());
        flow.setModuleId(flowInfo.getModuleId());
        flow.setName(flowInfo.getName());
        flow.setDescription(flowInfo.getDescription());
        flow.setPartComp(true);
        flow.setCreatedAt(Instant.now());
        flow.setUpdatedAt(Instant.now());

        List<FlowStep> runtimeSteps = buildRuntimeSteps(flowInfo);
        flow.setSteps(runtimeSteps);

        // Save to flows collection
        return flowRepository.save(flow);
    }

    public void executeComponentFlow(FlowInfo flowInfo, String environmentId) {
        Flow flow = materializeFlow(flowInfo);
        flowOrchestratorService.executeFlow(flow, environmentId);
    }

    private List<FlowStep> buildRuntimeSteps(FlowInfo flowInfo) {
        List<FlowStep> runtimeSteps = new ArrayList<>();
        if (flowInfo.getFlowItems() == null || flowInfo.getFlowItems().isEmpty()) {
            return runtimeSteps;
        }

        flowInfo.getFlowItems().sort(Comparator.comparingInt(FlowItem::getOrder));

        for (FlowItem item : flowInfo.getFlowItems()) {
            if (item.getType() == FlowItemType.STEP && item.getStep() != null) {
                FlowStep runtimeStep = cloneStep(item.getStep());
                runtimeStep.setId(UUID.randomUUID().toString());
                runtimeSteps.add(runtimeStep);
            } else if (item.getType() == FlowItemType.COMPONENT) {
                Optional<Component> compOpt = componentRepository.findById(item.getComponentId());
                if (compOpt.isPresent() && compOpt.get().getSteps() != null) {
                    for (FlowStep compStep : compOpt.get().getSteps()) {
                        FlowStep runtimeStep = cloneStep(compStep);
                        runtimeStep.setId(UUID.randomUUID().toString());
                        runtimeStep.setName(compOpt.get().getName() + " - " + compStep.getName());
                        runtimeSteps.add(runtimeStep);
                    }
                }
            }
        }

        int order = 1;

        for (FlowStep step : runtimeSteps) {
            step.setStepOrder(order++);
        }

        return runtimeSteps;
    }

    private FlowStep cloneStep(FlowStep original) {
        FlowStep clone = new FlowStep();
        clone.setName(original.getName());
        clone.setActionType(original.getActionType());
        clone.setVerificationType(original.getVerificationType());
        clone.setSelector(original.getSelector());
        clone.setValue(original.getValue());
        clone.setExpectedValue(original.getExpectedValue());
        clone.setAttribute(original.getAttribute());
        clone.setTextSource(original.getTextSource());
        clone.setOverrideWait(original.getOverrideWait());
        clone.setWait(original.getWait());
        clone.setRetryCount(original.getRetryCount());
        clone.setContinueOnFailure(original.getContinueOnFailure());
        clone.setCaptureScreenshot(original.getCaptureScreenshot());
        clone.setIsComp(original.getIsComp());
        clone.setFramePath(original.getFramePath());
        return clone;
    }
}
