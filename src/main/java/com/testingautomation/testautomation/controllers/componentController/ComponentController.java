package com.testingautomation.testautomation.controllers.componentController;

import com.testingautomation.testautomation.entities.component.Component;
import com.testingautomation.testautomation.entities.component.ComponentModule;
import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.services.componentService.ComponentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/components")
public class ComponentController {

    @Autowired
    private ComponentService componentService;

    @GetMapping("/modules/{projectId}")
    public List<ComponentModule> getModules(@PathVariable String projectId) {
        return componentService.getModules(projectId);
    }

    @PostMapping("/modules")
    public ComponentModule createModule(@RequestBody ComponentModule module) {
        return componentService.createModule(module);
    }

    @GetMapping("/{projectId}/{moduleId}")
    public List<Component> getComponents(@PathVariable String projectId, @PathVariable String moduleId) {
        return componentService.getComponents(projectId, moduleId);
    }

    @PostMapping
    public Component createComponent(@RequestBody Component component) {
        System.out.println("inside create component controller");
        System.out.println("component: "+component);
        return componentService.createComponent(component);
    }

    @PutMapping("/{id}")
    public Component updateComponent(@PathVariable String id, @RequestBody Component component) {
        return componentService.updateComponent(id, component);
    }

    @GetMapping("/flow-info/{id}")
    public FlowInfo getFlowInfo(@PathVariable String id) {
        return componentService.getFlowInfo(id);
    }

    @PostMapping("/flow-info")
    public FlowInfo createFlowInfo(@RequestBody FlowInfo flowInfo) {
        System.out.println("FlowInfo : "+flowInfo);
        return componentService.createFlowInfo(flowInfo);
    }

    @PutMapping("/flow-info/{id}")
    public FlowInfo saveFlowInfo(@PathVariable String id, @RequestBody FlowInfo flowInfo) {
        return componentService.saveFlowInfo(id, flowInfo);
    }
}
