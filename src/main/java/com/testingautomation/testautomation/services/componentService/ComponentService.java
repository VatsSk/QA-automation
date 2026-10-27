package com.testingautomation.testautomation.services.componentService;

import com.testingautomation.testautomation.entities.component.Component;
import com.testingautomation.testautomation.entities.component.ComponentModule;
import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.entities.component.FlowItem;
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

    public Component getComponent(String id) {
        return componentRepository.findById(id).orElse(null);
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
        Component existing = componentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Component not found with id: " + id));
        
        existing.setName(component.getName());
        existing.setDescription(component.getDescription());
        existing.setSteps(component.getSteps());
        
        if (component.getCompModuleId() != null) {
            existing.setCompModuleId(component.getCompModuleId());
        }
        
        existing.setUpdatedAt(Instant.now());
        if (existing.getSteps() != null) {
            existing.getSteps().forEach(s -> s.setIsComp(true));
        }
        
        Component saved = componentRepository.save(existing);

        // Sync updated componentName & moduleName across all FlowInfo items referencing this component
        try {
            String modName = null;
            if (existing.getCompModuleId() != null) {
                ComponentModule mod = componentModuleRepository.findById(existing.getCompModuleId()).orElse(null);
                if (mod != null) {
                    modName = mod.getName();
                }
            }
            List<FlowInfo> allFlowInfos = flowInfoRepository.findAll();
            for (FlowInfo fi : allFlowInfos) {
                if (fi.getFlowItems() != null) {
                    boolean modified = false;
                    for (FlowItem item : fi.getFlowItems()) {
                        if (id.equals(item.getComponentId())) {
                            item.setComponentName(existing.getName());
                            if (modName != null) {
                                item.setModuleName(modName);
                            }
                            modified = true;
                        }
                    }
                    if (modified) {
                        flowInfoRepository.save(fi);
                        componentFlowExecutionService.materializeFlow(fi);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error syncing component changes to flow_info: " + e.getMessage());
        }

        return saved;
    }

    @Autowired
    private com.testingautomation.testautomation.services.flowService.ComponentFlowExecutionService componentFlowExecutionService;

    public FlowInfo getFlowInfo(String id) {
        return flowInfoRepository.findById(id).orElse(null);
    }

    public FlowInfo createFlowInfo(FlowInfo flowInfo) {
        flowInfo.setCreatedAt(Instant.now());
        flowInfo.setUpdatedAt(Instant.now());
        enrichFlowItemsMetadata(flowInfo);
        FlowInfo saved = flowInfoRepository.save(flowInfo);
        componentFlowExecutionService.materializeFlow(saved);
        return saved;
    }

    public FlowInfo saveFlowInfo(String id, FlowInfo flowInfo) {
        FlowInfo existing = flowInfoRepository.findById(id).orElse(null);
        if (existing != null) {
            flowInfo.setId(existing.getId());
            flowInfo.setCreatedAt(existing.getCreatedAt());
        } else {
            flowInfo.setId(id);
            flowInfo.setCreatedAt(Instant.now());
        }
        flowInfo.setUpdatedAt(Instant.now());
        enrichFlowItemsMetadata(flowInfo);
        FlowInfo saved = flowInfoRepository.save(flowInfo);
        componentFlowExecutionService.materializeFlow(saved);
        return saved;
    }

    private void enrichFlowItemsMetadata(FlowInfo flowInfo) {
        if (flowInfo == null || flowInfo.getFlowItems() == null) return;
        for (FlowItem item : flowInfo.getFlowItems()) {
            if (item.getType() == com.testingautomation.testautomation.enums.flow.FlowItemType.COMPONENT && item.getComponentId() != null) {
                boolean needsCompName = item.getComponentName() == null || item.getComponentName().isBlank();
                boolean needsModName = item.getModuleName() == null || item.getModuleName().isBlank();
                if (needsCompName || needsModName) {
                    Component comp = componentRepository.findById(item.getComponentId()).orElse(null);
                    if (comp != null) {
                        if (needsCompName) {
                            item.setComponentName(comp.getName());
                        }
                        if (needsModName && comp.getCompModuleId() != null) {
                            ComponentModule mod = componentModuleRepository.findById(comp.getCompModuleId()).orElse(null);
                            if (mod != null) {
                                item.setModuleName(mod.getName());
                            }
                        }
                    }
                }
            }
        }
    }
}
