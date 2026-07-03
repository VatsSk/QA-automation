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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class FlowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);

    @Autowired
    private ActionHandlerService actionHandlerService;

    public void executeStep(WebDriver driver, FlowStep step, Integer defaultFlowWait) {

        ActionType actionType = step.getActionType();
        if (actionType == null) {
            logger.warn("ActionType is null for step [{}]", step.getName());
            return;
        }
        
        logger.info("Executing step [{}] of ActionType [{}]", step.getName(), actionType);

        // Special actions that do not require element location
        if (actionType == ActionType.NAVIGATE) {
            actionHandlerService.handleNavigate(driver, step);
            return;
        } else if (actionType == ActionType.WAIT) {
            actionHandlerService.handleWait(step);
            return;
        }

        // Determine wait time based on override or flow's default wait
        int waitTime = Boolean.TRUE.equals(step.getOverrideWait()) && step.getWait() != null 
                ? step.getWait() 
                : (defaultFlowWait != null ? defaultFlowWait : 5000);
                
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
        } else if (actionType != ActionType.SCROLL) {
            logger.warn("Locator is empty but action type [{}] requires an element", actionType);
            return;
        }

        switch (actionType) {
            case TYPE:
                actionHandlerService.handleType(element, step);
                break;
            case CLICK:
                actionHandlerService.handleClick(driver, element, step);
                break;
            case CHECKBOX:
                actionHandlerService.handleCheckbox(element, step);
                break;
            case RADIO:
                actionHandlerService.handleRadio(element, step);
                break;
            case SELECT:
                actionHandlerService.handleSelect(element, step);
                break;
            case DATE:
                actionHandlerService.handleDate(element, step);
                break;
            case FILE_UPLOAD:
                actionHandlerService.handleFileUpload(element, step);
                break;
            case HOVER:
                actionHandlerService.handleHover(driver, element, step);
                break;
            case SCROLL:
                actionHandlerService.handleScroll(driver, element, step);
                break;
            case PRESS_KEY:
                actionHandlerService.handlePressKey(element, step);
                break;
            case DRAG_DROP:
                actionHandlerService.handleDragDrop(driver, element, step);
                break;
            case VERIFY:
                actionHandlerService.handleVerify(driver,element,step);
            default:
                logger.info("ActionType [{}] is not yet fully integrated.", actionType);
        }
    }
}
