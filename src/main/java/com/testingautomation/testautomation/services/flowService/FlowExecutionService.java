package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

@Service
public class FlowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);

    @Autowired
    private ActionHandlerService actionHandlerService;

    @Autowired
    private ScreenshotService screenshotService;

    @Value("${storage.s3.base-prefix}")
    private String basePrefix;

    private final String resultsBaseDir = "test-results";

    public void executeStep(WebDriver driver, FlowStep step, Flow flow) {

        ActionType actionType = step.getActionType();
        if (actionType == null) {
            logger.warn("ActionType is null for step [{}]", step.getName());
            step.setExecutionStatus(ExecutionStatus.FAILED);
            step.setExecutionMessage("ActionType is null");
            return;
        }

        logger.info("Executing step [{}] of ActionType [{}]", step.getName(), actionType);
        step.setExecutionStartedAt(Instant.now());
        step.setExecutionStatus(ExecutionStatus.RUNNING);

        int retries = step.getRetryCount() != null ? step.getRetryCount() : 0;
        int attempts = 0;
        boolean success = false;

        int waitTime = Boolean.TRUE.equals(step.getOverrideWait()) && step.getWait() != null 
                ? step.getWait() 
                : (flow.getDefaultWait() != null ? flow.getDefaultWait() : 5000);
                
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(waitTime));

        while (attempts <= retries && !success) {
            attempts++;
            try {
                WebElement element = null;
                
                // Fetch element if required
                if (actionType != ActionType.NAVIGATE && actionType != ActionType.WAIT && actionType != ActionType.SCROLL) {
                    if (step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                        element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
                    } else {
                        throw new GlobalExceptionHandler.FlowExecutionException(
                                step.getStepOrder(),
                                step.getName(),
                                actionType,
                                "Locator is empty but action type requires an element",
                                "Missing locator",
                                null
                        );
                    }
                } else if (actionType == ActionType.SCROLL && step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                    element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
                }

                switch (actionType) {
                    case NAVIGATE: actionHandlerService.handleNavigate(driver, step); break;
                    case WAIT: actionHandlerService.handleWait(step); break;
                    case TYPE: actionHandlerService.handleType(element, step); break;
                    case CLICK: actionHandlerService.handleClick(driver, element, step); break;
                    case CHECKBOX: actionHandlerService.handleCheckbox(element, step); break;
                    case RADIO: actionHandlerService.handleRadio(element, step); break;
                    case SELECT: actionHandlerService.handleSelect(element, step); break;
                    case DATE: actionHandlerService.handleDate(element, step); break;
                    case FILE_UPLOAD: actionHandlerService.handleFileUpload(element, step); break;
                    case HOVER: actionHandlerService.handleHover(driver, element, step); break;
                    case SCROLL: actionHandlerService.handleScroll(driver, element, step); break;
                    case PRESS_KEY: actionHandlerService.handlePressKey(element, step); break;
                    case DRAG_DROP: actionHandlerService.handleDragDrop(driver, element, step); break;
                    case VERIFY: actionHandlerService.handleVerify(driver,element, step); break;
                    default: logger.info("ActionType [{}] is not yet fully integrated.", actionType);
                }
                
                success = true;
                step.setExecutionStatus(ExecutionStatus.PASSED);
                step.setExecutionMessage("Success");

            } catch (Exception e) {
                if (attempts > retries) {
                    step.setExecutionStatus(ExecutionStatus.FAILED);
                    step.setExecutionMessage(e.getMessage());
                    logger.error("Step [{}] failed after {} attempts. Error: {}", step.getName(), attempts, e.getMessage());
                    
                    takeScreenshotIfRequired(driver, step, flow);
                    
                    step.setExecutionCompletedAt(Instant.now());
                    
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        throw new com.testingautomation.testautomation.globalException.GlobalExceptionHandler.FlowExecutionException(
                                step.getStepOrder(),
                                step.getName(),
                                actionType,
                                "Step failed after " + attempts + " attempts. Error: " + e.getMessage(),
                                "Execution failed for step",
                                e
                        );
                    }
                } else {
                    logger.warn("Step [{}] failed, retrying... Attempt {}/{}", step.getName(), attempts, retries);
                }
            }
        }

            takeScreenshotIfRequired(driver, step, flow);
            step.setExecutionCompletedAt(Instant.now());

    }

    private void takeScreenshotIfRequired(WebDriver driver, FlowStep step, Flow flow) {
        if (Boolean.TRUE.equals(step.getCaptureScreenshot())) {
            try {
                String flowPrefix = basePrefix + "/" + flow.getProjectId() + "/" + flow.getModuleId() + "/" + flow.getId();
                String stepId = "step_" + (step.getStepOrder() != null ? step.getStepOrder() : "unknown");
                
                Path scenarioDir = Paths.get(resultsBaseDir, flowPrefix);
                Files.createDirectories(scenarioDir);
                
                screenshotService.takeScreenshot(driver, stepId, stepId + "_screenshot", scenarioDir, flowPrefix);
            } catch (Exception e) {
                logger.error("Failed to capture screenshot for step [{}]", step.getName(), e);
            }
        }
    }
}
