package com.testingautomation.testautomation.dto;

import org.openqa.selenium.WebDriver;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds all state for a single flow execution.
 *
 * <p>One instance is created per execution and never shared between concurrent runs
 * of the same flow. The {@link #runtimeVariables} map is populated once before
 * any step executes and is then read-only throughout the execution.</p>
 */
public class FlowExecutionContext {
    private WebDriver driver;
    private Map<String, String> tabRefToHandle = new HashMap<>();
    private Deque<String> windowStack = new ArrayDeque<>();
    private String currentTabRef;
    private String flowId;

    /**
     * Runtime variable values for this execution, keyed by canonical variable name.
     * Initialized once in the orchestrator before step execution begins.
     * Must be treated as read-only by all consumers.
     */
    private Map<String, String> runtimeVariables = Collections.emptyMap();

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

    /**
     * Returns the runtime variable values for this execution.
     * This map is unmodifiable after initialization.
     */
    public Map<String, String> getRuntimeVariables() {
        return runtimeVariables;
    }

    /**
     * Sets the runtime variable map. Should be called exactly once by the
     * orchestrator before step execution begins.
     */
    public void setRuntimeVariables(Map<String, String> runtimeVariables) {
        this.runtimeVariables = runtimeVariables != null ? runtimeVariables : Collections.emptyMap();
    }
}
