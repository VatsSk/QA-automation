package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.FlowStepEvent;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.VerificationService;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import com.testingautomation.testautomation.utils.TextExtractor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

@Service
public class FlowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);

    @Autowired private ActionHandlerService actionHandlerService;
    @Autowired private com.testingautomation.testautomation.repositories.flowRepos.FlowRepository flowRepository;
    @Autowired private ScreenshotService screenshotService;
    @Autowired private WebDriverRegistry webDriverRegistry;
    @Autowired private VerificationService verificationService;
    @Autowired private FlowSseService flowSseService;

    private final String resultsBaseDir = "test-results";

    // ── Public entry point ────────────────────────────────────────────────────

    public void executeStep(WebDriver driver, FlowStep step, Flow flow) {
        ActionType actionType = step.getActionType();
        if (actionType == null) {
            logger.warn("ActionType is null for step [{}]", step.getName());
            step.setExecutionStatus(ExecutionStatus.FAILED);
            step.setExecutionMessage("ActionType is null");
            return;
        }

        logger.info("Executing step [{}] of ActionType [{}] with Locator [{}]", step.getName(), actionType, step.getSelector());
        step.setExecutionStartedAt(Instant.now());
        step.setExecutionStatus(ExecutionStatus.RUNNING);
        sendStepEvent(flowSseService::sendStepStarted, flow, step);

        int retries  = step.getRetryCount() != null ? step.getRetryCount() : 1;
        int attempts = 0;
        boolean success = false;
        String color = resolveStepColor(actionType);

        int waitTime = Boolean.TRUE.equals(step.getOverrideWait()) && step.getWait() != null
                ? step.getWait()
                : (flow.getDefaultWait() != null ? flow.getDefaultWait() : 5000);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(waitTime));

        while (attempts <= retries && !success) {
            attempts++;
            WebElement element = null;
            try {
                element = resolveElement(driver, wait, step, actionType, waitTime);

                boolean takePreScreenshot = (actionType == ActionType.CLICK || actionType == ActionType.VERIFY || actionType == ActionType.SELECT);
                if (takePreScreenshot) {
                    takeScreenshotIfRequired(driver, element, step, flow, attempts, color);
                }

                dispatchAction(driver, element, step, actionType, waitTime);

                success = true;
                step.setExecutionStatus(ExecutionStatus.PASSED);
                step.setExecutionMessage("Success");
                if (!takePreScreenshot) {
                    takeScreenshotIfRequired(driver, element, step, flow, attempts, color);
                }
                sendStepEvent(flowSseService::sendStepUpdated, flow, step);

            } catch (GlobalExceptionHandler.FlowExecutionException ex) {
                takeScreenshotIfRequired(driver, element, step, flow, attempts, "red");
                if (attempts > retries) {
                    markStepFailed(flow, step, ex.getUserMessage(), ex.getMessage(), attempts);
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        if (pauseIfDebugEnabled(flow, step, driver, "Execution paused due to error: " + ex.getMessage())) return;
                        logger.info("flowExecutionException {}", ex.getMessage());
                        throw ex;
                    }
                } else {
                    logger.warn("Step [{}] failed, retrying... Attempt {}/{}", step.getName(), attempts, retries);
                }

            } catch (Exception e) {
                takeScreenshotIfRequired(driver, element, step, flow, attempts, "red");
                if (attempts > retries) {
                    markStepFailed(flow, step, "Unexpected error", e.getMessage(), attempts);
                    if (!Boolean.TRUE.equals(step.getContinueOnFailure())) {
                        if (pauseIfDebugEnabled(flow, step, driver, "Execution paused due to unexpected error: " + e.getMessage())) return;
                        logger.info("changing exception into flowExecutionException");
                        throw new GlobalExceptionHandler.FlowExecutionException(
                                step.getStepOrder(), step.getName(), actionType,
                                "Step failed after " + attempts + " attempts. Error: " + e.getMessage(),
                                "Execution failed for step", e);
                    }
                } else {
                    logger.warn("Step [{}] failed, retrying... Attempt {}/{}", step.getName(), attempts, retries);
                }
            }
        }

        step.setExecutionCompletedAt(Instant.now());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the screenshot highlight color based on the action type.
     */
    private String resolveStepColor(ActionType actionType) {
        if (actionType == ActionType.VERIFY) return "#e6b800";
        if (actionType == ActionType.HOVER)  return "purple";
        return "green";
    }

    /**
     * Finds the WebElement needed by the step, or returns null for action types
     * that do not require one (NAVIGATE, WAIT, etc.).
     */
    private WebElement resolveElement(WebDriver driver, WebDriverWait wait, FlowStep step,
                                      ActionType actionType, int waitTime) {
        if (actionType == ActionType.VERIFY) {
            try {
                return verificationService.findBestElement(driver, step.getSelector(), Duration.ofMillis(waitTime));
            } catch (Exception ex) {
                throw new GlobalExceptionHandler.FlowExecutionException(
                        step.getStepOrder(), step.getName(), actionType,
                        "Unable to find element in Verify",
                        "Unable to find element with " + step.getSelector(), ex);
            }
        }

        if (actionType != ActionType.NAVIGATE && actionType != ActionType.WAIT && actionType != ActionType.SCROLL) {
            if (step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
                try {
                    return wait.until(ExpectedConditions.presenceOfElementLocated(TextExtractor.resolveLocator(step.getSelector())));
                } catch (Exception ex) {
                    throw new GlobalExceptionHandler.FlowExecutionException(
                            step.getStepOrder(), step.getName(), actionType,
                            "Locator is empty but action type requires an element",
                            "unable to find element using locator " + step.getSelector(), null);
                }
            }
        }

        if (actionType == ActionType.SCROLL && step.getSelector() != null && !step.getSelector().trim().isEmpty()) {
            return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(step.getSelector())));
        }

        return null;
    }

    /**
     * Dispatches the action to the appropriate handler based on the action type.
     */
    private void dispatchAction(WebDriver driver, WebElement element, FlowStep step,
                                ActionType actionType, int waitTime) {
        switch (actionType) {
            case NAVIGATE:    actionHandlerService.handleNavigate(driver, step);          break;
            case WAIT:        actionHandlerService.handleWait(step);                      break;
            case TYPE:        actionHandlerService.handleType(element, step);             break;
            case CLICK:       actionHandlerService.handleClick(driver, element, step);    break;
            case CHECKBOX:    actionHandlerService.handleCheckbox(element, step);         break;
            case RADIO:       actionHandlerService.handleRadio(element, step);            break;
            case SELECT:      actionHandlerService.handleSelect(element, step);           break;
            case DATE:        actionHandlerService.handleDate(element, step);             break;
            case FILE_UPLOAD: actionHandlerService.handleFileUpload(element, step);       break;
            case HOVER:       actionHandlerService.handleHover(driver, element, step);    break;
            case SCROLL:      actionHandlerService.handleScroll(driver, element, step);   break;
            case PRESS_KEY:   actionHandlerService.handlePressKey(element, step);         break;
            case DRAG_DROP:   actionHandlerService.handleDragDrop(driver, element, step); break;
            case VERIFY:      actionHandlerService.handleVerify(driver, element, step, waitTime); break;
            default:          logger.info("ActionType [{}] is not yet fully integrated.", actionType);
        }
    }

    /**
     * Marks a step as FAILED, logs the error, and sends an SSE failed event.
     */
    private void markStepFailed(Flow flow, FlowStep step, String userMessage, String logMessage, int attempts) {
        step.setExecutionStatus(ExecutionStatus.FAILED);
        step.setExecutionMessage(userMessage);
        step.setExecutionCompletedAt(Instant.now());
        logger.error("Step [{}] failed after {} attempts. Error: {}", step.getName(), attempts, logMessage);
        sendStepEvent(flowSseService::sendStepFailed, flow, step);
    }

    /**
     * If debug mode is enabled, transitions the step to PAUSED, saves the WebDriver
     * session, and returns true so the caller can return early.
     * Returns false if debug mode is off (normal failure path).
     */
    private boolean pauseIfDebugEnabled(Flow flow, FlowStep step, WebDriver driver, String pauseMessage) {
        if (!Boolean.TRUE.equals(flow.getIsDebugEnabled())) return false;
        step.setExecutionStatus(ExecutionStatus.PAUSED);
        step.setExecutionMessage(pauseMessage);
        sendStepEvent(flowSseService::sendStepFailed, flow, step);
        webDriverRegistry.registerDriver(flow.getId(), driver);
        return true;
    }

    /**
     * Sends an SSE step event using the given sender function.
     * Centralises creation of FlowStepEvent so it is never duplicated.
     */
    private void sendStepEvent(java.util.function.BiConsumer<String, FlowStepEvent> sender, Flow flow, FlowStep step) {
        sender.accept(flow.getId(), new FlowStepEvent(
                flow.getId(), step.getId(), step.getStepOrder(),
                step.getExecutionStatus(), step.getExecutionMessage(), null
        ));
    }

    /**
     * Takes a screenshot with element highlighting.
     */
    private void takeScreenshotIfRequired(WebDriver driver, WebElement element, FlowStep step,
                                          Flow flow, int attempt, String color) {
        if (!Boolean.TRUE.equals(step.getCaptureScreenshot())) return;
        try {
            String stepId = "" + step.getStepOrder();
            Path scenarioDir = Paths.get(resultsBaseDir, flow.getFlowBasePath());
            Files.createDirectories(scenarioDir);
            screenshotService.takeScreenshot(driver, element, stepId, stepId + "_" + attempt + "_screenshot",
                    scenarioDir, flow.getFlowBasePath(), color);
        } catch (Exception e) {
            logger.error("Failed to capture screenshot for step [{}]", step.getName(), e);
        }
    }
}
