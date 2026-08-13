package com.testingautomation.testautomation.services.flowService;

import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebDriverRegistry {

    private final Map<String, WebDriver> registry = new ConcurrentHashMap<>();

    public void registerDriver(String flowId, WebDriver driver) {
        registry.put(flowId, driver);
    }

    public WebDriver getDriver(String flowId) {
        return registry.get(flowId);
    }

    public void removeDriver(String flowId) {
        registry.remove(flowId);
    }
}
