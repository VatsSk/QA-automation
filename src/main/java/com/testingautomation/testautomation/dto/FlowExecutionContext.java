package com.testingautomation.testautomation.dto;

import org.openqa.selenium.WebDriver;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class FlowExecutionContext {
    private WebDriver driver;
    private Map<String, String> tabRefToHandle = new HashMap<>();
    private Deque<String> windowStack = new ArrayDeque<>();
    private String currentTabRef;
    private String flowId;

    public FlowExecutionContext(WebDriver driver, String flowId) {
        this.driver = driver;
        this.flowId = flowId;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public Map<String, String> getTabRefToHandle() {
        return tabRefToHandle;
    }

    public void setTabRefToHandle(Map<String, String> tabRefToHandle) {
        this.tabRefToHandle = tabRefToHandle;
    }

    public Deque<String> getWindowStack() {
        return windowStack;
    }

    public void setWindowStack(Deque<String> windowStack) {
        this.windowStack = windowStack;
    }

    public String getCurrentTabRef() {
        return currentTabRef;
    }

    public void setCurrentTabRef(String currentTabRef) {
        this.currentTabRef = currentTabRef;
    }
}
