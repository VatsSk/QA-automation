package com.testingautomation.testautomation.services.componentService;

import com.testingautomation.testautomation.entities.component.Component;
import com.testingautomation.testautomation.entities.component.ComponentModule;
import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.repositories.flowRepos.ComponentModuleRepository;
import com.testingautomation.testautomation.repositories.flowRepos.ComponentRepository;
import com.testingautomation.testautomation.repositories.flowRepos.FlowInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ComponentService {

    @Autowired
    private ComponentModuleRepository componentModuleRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private FlowInfoRepository flowInfoRepository;

    public List<ComponentModule> getModules(String projectId) {
        return componentModuleRepository.findByProjectId(projectId);
    }

    public ComponentModule createModule(ComponentModule module) {
        module.setCreatedAt(Instant.now());
        module.setUpdatedAt(Instant.now());
        return componentModuleRepository.save(module);
    }

    public List<Component> getComponents(String projectId, String moduleId) {
        return componentRepository.findByProjectIdAndCompModuleId(projectId, moduleId);
    }

    public Component createComponent(Component component) {
        component.setCreatedAt(Instant.now());
        component.setUpdatedAt(Instant.now());
        if (component.getSteps() != null) {
            component.getSteps().forEach(s -> s.setIsComp(true));
        }
        return componentRepository.save(component);
    }

    public Component updateComponent(String id, Component component) {
        component.setId(id);
        component.setUpdatedAt(Instant.now());
        if (component.getSteps() != null) {
            component.getSteps().forEach(s -> s.setIsComp(true));
        }
        return componentRepository.save(component);
    }

    public FlowInfo getFlowInfo(String flowId) {
        return flowInfoRepository.findByFlowId(flowId).orElse(null);
    }

    public FlowInfo saveFlowInfo(String flowId, FlowInfo flowInfo) {
        FlowInfo existing = flowInfoRepository.findByFlowId(flowId).orElse(null);
        if (existing != null) {
            flowInfo.setId(existing.getId());
            flowInfo.setCreatedAt(existing.getCreatedAt());
        } else {
            flowInfo.setCreatedAt(Instant.now());
        }
        flowInfo.setFlowId(flowId);
        flowInfo.setUpdatedAt(Instant.now());
        return flowInfoRepository.save(flowInfo);
    }
}
