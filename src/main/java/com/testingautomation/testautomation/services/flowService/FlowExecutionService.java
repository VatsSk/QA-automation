package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.VerificationService;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidSelectorException;
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
    private com.testingautomation.testautomation.repositories.flowRepos.FlowRepository flowRepository;

    @Autowired
    private ScreenshotService screenshotService;


    private final String resultsBaseDir = "test-results";
    @Autowired
    private VerificationService verificationService;

    public void executeStep(WebDriver driver, FlowStep step, Flow flow) {

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ActionType actionType = step.getActionType();
        if (actionType == null) {
            logger.warn("ActionType is null for step [{}]", step.getName());
            step.setExecutionStatus(ExecutionStatus.FAILED);
            step.setExecutionMessage("ActionType is null");
            return;
        }

        logger.info("Executing step [{}] of ActionType [{}] with Locator [{}]", step.getName(), actionType,step.getSelector());
        step.setExecutionStartedAt(Instant.now());
        step.setExecutionStatus(ExecutionStatus.RUNNING);
        flowRepository.save(flow); // Immediately persist RUNNING state so UI updates

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
                if(actionType==ActionType.VERIFY){
                    element=verificationService.findBestElement(driver,step.getSelector(),Duration.ofMillis(waitTime));
                }
                else if (actionType != ActionType.NAVIGATE && actionType != ActionType.WAIT && actionType != ActionType.SCROLL) {
                    if (step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                        try{
                            element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
                        }catch(InvalidSelectorException e){
                            element= wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(step.getSelector())));
                        }
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
                    case VERIFY: actionHandlerService.handleVerify(driver,element, step,waitTime); break;
                    default: logger.info("ActionType [{}] is not yet fully integrated.", actionType);
                }
                
                success = true;
                step.setExecutionStatus(ExecutionStatus.PASSED);
                step.setExecutionMessage("Success");
                takeScreenshotIfRequired(driver, step, flow,attempts);
            } catch (Exception e) {
                takeScreenshotIfRequired(driver, step, flow,attempts);
                if (attempts > retries) {
                    step.setExecutionStatus(ExecutionStatus.FAILED);
                    step.setExecutionMessage(e.getMessage());
                    logger.error("Step [{}] failed after {} attempts. Error: {}", step.getName(), attempts, e.getMessage());
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


            step.setExecutionCompletedAt(Instant.now());

    }

    private void takeScreenshotIfRequired(WebDriver driver, FlowStep step, Flow flow,int attempt) {
        if (Boolean.TRUE.equals(step.getCaptureScreenshot())) {
            try {
                String stepId = ""+ step.getStepOrder();
                
                Path scenarioDir = Paths.get(resultsBaseDir, flow.getFlowBasePath());
                Files.createDirectories(scenarioDir);
                
                screenshotService.takeScreenshot(driver, stepId, stepId+"_"+attempt + "_screenshot", scenarioDir, flow.getFlowBasePath());
            } catch (Exception e) {
                logger.error("Failed to capture screenshot for step [{}]", step.getName(), e);
            }
        }
    }
}
