package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebDriverRegistry {

    private final Map<String, FlowExecutionContext> registry = new ConcurrentHashMap<>();

    public void registerContext(String flowId, FlowExecutionContext context) {
        registry.put(flowId, context);
    }

    public FlowExecutionContext getContext(String flowId) {
        return registry.get(flowId);
    }

    public void removeContext(String flowId) {
        registry.remove(flowId);
    }
}
