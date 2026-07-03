package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class FlowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);

    public void executeStep(WebDriver driver, FlowStep step) {
//        if (!Boolean.TRUE.equals(step.getEnabled())) {
//            logger.info("Step [{}] is disabled. Skipping.", step.getName());
//            return;
//        }

        logger.info("Executing step [{}] of type [{}]", step.getName(), step.getStepType());

        if (step.getStepType() == null) {
            logger.warn("StepType is null for step [{}]", step.getName());
            return;
        }

        switch (step.getStepType()) {
            case ACTION:
                executeAction(driver, step);
                break;
            case VERIFICATION:
                // TODO: Implement verification logic later
                logger.info("Verification step type not yet implemented in this layout.");
                break;
            case CONTROL:
                // TODO: Implement control logic (wait, loop, etc.) later
                logger.info("Control step type not yet implemented in this layout.");
                break;
            default:
                logger.warn("Unknown StepType: {}", step.getStepType());
        }
    }

    private void executeAction(WebDriver driver, FlowStep step) {
        ActionType actionType = step.getActionType();
        if (actionType == null) {
            logger.warn("ActionType is null for step [{}]", step.getName());
            return;
        }

        // Determine wait time based on override or a default of 5 seconds
        int waitTime = Boolean.TRUE.equals(step.getOverrideWait()) && step.getWait() != null ? step.getWait() : 5000;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(waitTime));
        WebElement element = null;

        // Fetch element if a locator is provided
        if (step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
            try {
                element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
            } catch (Exception e) {
                logger.error("Failed to locate element for step [{}]: {}", step.getName(), e.getMessage());
                if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                    throw new RuntimeException("Element not found for selector: " + step.getSelector(), e);
                }
                return;
            }
        }

        switch (actionType) {
            case TYPE:
                handleTypeAction(element, step);
                break;
            case CLICK:
                handleClickAction(wait, element, step);
                break;
            case CHECKBOX:
                handleCheckboxAction(element, step);
                break;
            // TODO: Add other action types (NAVIGATE, SELECT, RADIO, DATE, etc.)
            default:
                logger.info("ActionType [{}] is not yet implemented.", actionType);
        }
    }

    private void handleTypeAction(WebElement element, FlowStep step) {
        if (element == null) {
            logger.warn("Cannot perform TYPE action: element is null");
            return;
        }
        logger.info("Typing value [{}] into element", step.getValue());
        element.clear();
        if (step.getValue() != null) {
            element.sendKeys(step.getValue());
        }
    }

    private void handleClickAction(WebDriverWait wait, WebElement element, FlowStep step) {
        if (element == null) {
            logger.warn("Cannot perform CLICK action: element is null");
            return;
        }
        logger.info("Clicking element");
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (Exception e) {
            logger.warn("Standard click failed, attempting fallback click. Error: {}", e.getMessage());
            element.click();
        }
    }

    private void handleCheckboxAction(WebElement element, FlowStep step) {
        if (element == null) {
            logger.warn("Cannot perform CHECKBOX action: element is null");
            return;
        }
        
        // Assume step.getValue() holds "true" or "false"
        boolean targetState = Boolean.parseBoolean(step.getValue());
        boolean currentState = element.isSelected();
        
        logger.info("Checkbox target state: [{}], current state: [{}]", targetState, currentState);
        
        if (targetState != currentState) {
            element.click();
        }
    }
}
