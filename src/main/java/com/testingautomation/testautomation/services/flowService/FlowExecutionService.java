package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.VerificationService;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import com.testingautomation.testautomation.utils.TextExtractor;
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
    @Autowired
    private FlowSseService flowSseService;

    public void executeStep(WebDriver driver, FlowStep step, Flow flow) {

//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
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
        flowSseService.sendStepStarted(flow.getId(), new com.testingautomation.testautomation.dto.FlowStepEvent(
                flow.getId(), step.getId(), step.getStepOrder(), step.getExecutionStatus(), step.getExecutionMessage(), null
        ));
//        flowRepository.save(flow); // Immediately persist RUNNING state so UI updates via SSE

        int retries = step.getRetryCount() != null ? step.getRetryCount() : 1;
        int attempts = 0;
        boolean success = false;
        String color = "green";
        if (actionType == ActionType.VERIFY) {
            color = "#e6b800"; // dark yellow / gold
        } else if (actionType == ActionType.HOVER) {
            color = "purple";
        }

        int waitTime = Boolean.TRUE.equals(step.getOverrideWait()) && step.getWait() != null 
                ? step.getWait() 
                : (flow.getDefaultWait() != null ? flow.getDefaultWait() : 5000);
                
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(waitTime));

        while (attempts <= retries && !success) {
            attempts++;
            WebElement element = null;
            try {

                
                // Fetch element if required
                if(actionType==ActionType.VERIFY){
                    try{
                        element=verificationService.findBestElement(driver,step.getSelector(),Duration.ofMillis(waitTime));
                    }catch(Exception ex){
                        throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"Unable to find element in Verify","Unable to find element with "+step.getSelector(),ex);
                    }
                }
                else if (actionType != ActionType.NAVIGATE && actionType != ActionType.WAIT && actionType != ActionType.SCROLL && actionType != ActionType.URL_CHANGE) {
                    if (step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                        try {
                            element = wait.until(ExpectedConditions.presenceOfElementLocated(TextExtractor.resolveLocator(step.getSelector())));
                        }catch(Exception ex) {
                            throw new GlobalExceptionHandler.FlowExecutionException(
                                    step.getStepOrder(),
                                    step.getName(),
                                    actionType,
                                    "Locator is empty but action type requires an element",
                                    "unable to find element using locator "+step.getSelector(),
                                    null
                            );
                        }
                    }
                } else if (actionType == ActionType.SCROLL && step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                    element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
                }

                boolean takePreScreenshot = (actionType == ActionType.CLICK || actionType == ActionType.VERIFY || actionType == ActionType.SELECT);
                if (takePreScreenshot) {
                    takeScreenshotIfRequired(driver, element, step, flow, attempts, color);
                }

                switch (actionType) {
                    case NAVIGATE: actionHandlerService.handleNavigate(driver, step); break;
                    case WAIT: actionHandlerService.handleWait(step); break;
                    case TYPE: actionHandlerService.handleType(driver, element, step); break;
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
                    case URL_CHANGE: actionHandlerService.handleUrlChange(step, flow); break;
                    default: logger.info("ActionType [{}] is not yet fully integrated.", actionType);
                }
                
                success = true;
                step.setExecutionStatus(ExecutionStatus.PASSED);
                step.setExecutionMessage("Success");
                if (!takePreScreenshot) {
                    takeScreenshotIfRequired(driver, element, step, flow, attempts, color);
                }
                flowSseService.sendStepUpdated(flow.getId(), new com.testingautomation.testautomation.dto.FlowStepEvent(
                        flow.getId(), step.getId(), step.getStepOrder(), step.getExecutionStatus(), step.getExecutionMessage(), null
                ));
            }catch(GlobalExceptionHandler.FlowExecutionException ex){
                takeScreenshotIfRequired(driver,element ,step, flow,attempts, "red");
                if (attempts > retries) {
                    step.setExecutionStatus(ExecutionStatus.FAILED);
                    step.setExecutionMessage(ex.getUserMessage());
                    logger.error("Step [{}] failed after {} attempts in flowExecutionStatus. Error: {}", step.getName(), attempts, ex.getMessage());
                    step.setExecutionCompletedAt(Instant.now());
                    flowSseService.sendStepFailed(flow.getId(), new com.testingautomation.testautomation.dto.FlowStepEvent(
                            flow.getId(), step.getId(), step.getStepOrder(), step.getExecutionStatus(), step.getExecutionMessage(), null
                    ));
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        logger.info("flowExecutionException {}",ex.getMessage());
                        throw ex;
                    }
                } else {
                    logger.warn("Step [{}] failed, retrying... Attempt {}/{}", step.getName(), attempts, retries);
                }
            }
            catch (Exception e) {
                takeScreenshotIfRequired(driver, element,step, flow,attempts, "red");
                if (attempts > retries) {
                    step.setExecutionStatus(ExecutionStatus.FAILED);
                    step.setExecutionMessage("Unexpected error");
                    logger.error("Step [{}] failed after {} attempts. Error: {}", step.getName(), attempts, e.getMessage());
                    step.setExecutionCompletedAt(Instant.now());
                    flowSseService.sendStepFailed(flow.getId(), new com.testingautomation.testautomation.dto.FlowStepEvent(
                            flow.getId(), step.getId(), step.getStepOrder(), step.getExecutionStatus(), step.getExecutionMessage(), null
                    ));
                    
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        logger.info("changing exception into flowExecutionException");
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

    private void takeScreenshotIfRequired(WebDriver driver, FlowStep step, Flow flow, int attempt) {
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
    private void takeScreenshotIfRequired(WebDriver driver,WebElement element ,FlowStep step, Flow flow,int attempt, String color) {
        if (Boolean.TRUE.equals(step.getCaptureScreenshot())) {
            try {
                String stepId = ""+ step.getStepOrder();

                Path scenarioDir = Paths.get(resultsBaseDir, flow.getFlowBasePath());
                Files.createDirectories(scenarioDir);

                screenshotService.takeScreenshot(driver,element,stepId, stepId+"_"+attempt + "_screenshot", scenarioDir, flow.getFlowBasePath(), color);
            } catch (Exception e) {
                logger.error("Failed to capture screenshot for step [{}]", step.getName(), e);
            }
        }
    }
}
