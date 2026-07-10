package com.testingautomation.testautomation.globalException;


import com.testingautomation.testautomation.enums.ScenarioType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static class ScenarioExecutionException extends AutomationException {

        private final int scenarioIndex;

        private final ScenarioType scenarioType;

        private final String step;

        private final String userMessage;

        public ScenarioExecutionException(

                int scenarioIndex,

                ScenarioType scenarioType,

                String step,

                String userMessage,

                Throwable cause

        ) {

            super(userMessage, cause);

            this.scenarioIndex = scenarioIndex;

            this.scenarioType = scenarioType;

            this.step = step;

            this.userMessage = userMessage;

        }

        public int getScenarioIndex() {

            return scenarioIndex;

        }

        public ScenarioType getScenarioType() {

            return scenarioType;

        }

        public String getStep() {

            return step;

        }

        public String getUserMessage() {

            return userMessage;

        }

    }
    public static class FinalVerificationException extends AutomationException {

        private final int scenarioIndex;

        private final ScenarioType scenarioType;

        private final String step;

        private final String userMessage;

        public FinalVerificationException(

                int scenarioIndex,

                ScenarioType scenarioType,

                String step,

                String userMessage,

                Throwable cause

        ) {

            super(userMessage, cause);

            this.scenarioIndex = scenarioIndex;

            this.scenarioType = scenarioType;

            this.step = step;

            this.userMessage = userMessage;

        }

        public int getScenarioIndex() {

            return scenarioIndex;

        }

        public ScenarioType getScenarioType() {

            return scenarioType;

        }

        public String getStep() {

            return step;

        }

        public String getUserMessage() {

            return userMessage;

        }

    }
    public static class InitialVerificationException extends AutomationException {

        private final int scenarioIndex;

        private final ScenarioType scenarioType;

        private final String step;

        private final String userMessage;

        public InitialVerificationException(

                int scenarioIndex,

                ScenarioType scenarioType,

                String step,

                String userMessage,

                Throwable cause

        ) {

            super(userMessage, cause);

            this.scenarioIndex = scenarioIndex;

            this.scenarioType = scenarioType;

            this.step = step;

            this.userMessage = userMessage;

        }

        public int getScenarioIndex() {

            return scenarioIndex;

        }

        public ScenarioType getScenarioType() {

            return scenarioType;

        }

        public String getStep() {

            return step;

        }

        public String getUserMessage() {

            return userMessage;

        }

    }
    public static class SkipTestCaseException extends AutomationException{
        private final String testCaseNo;
        private final ScenarioType scenarioType;
        private final String message;
        public SkipTestCaseException(String testCaseNo,ScenarioType scenarioType,String message,Exception cause){
            super(message,cause);
            this.testCaseNo=testCaseNo;
            this.scenarioType=scenarioType;
            this.message=message;
        }
    }
    public static class AssertionExecutionException extends AutomationException {
        private final String assertionType;
        private final String userMessage;

        public AssertionExecutionException(
                String assertionType,
                String userMessage
        ) {

            super(userMessage);
            this.assertionType = assertionType;
            this.userMessage = userMessage;

        }

        public String getAssertionType() {
            return assertionType;
        }

        public String getUserMessage() {

            return userMessage;

        }

    }
    public static class FlowExecutionException extends AutomationException {
        private final Integer stepOrder;
        private final String stepName;
        private final com.testingautomation.testautomation.enums.flow.ActionType actionType;
        private final String reason;
        private final String userMessage;

        public FlowExecutionException(
                Integer stepOrder,
                String stepName,
                com.testingautomation.testautomation.enums.flow.ActionType actionType,
                String reason,
                String userMessage,
                Throwable cause
        ) {
            super(userMessage, cause);
            this.stepOrder = stepOrder;
            this.stepName = stepName;
            this.actionType = actionType;
            this.reason = reason;
            this.userMessage = userMessage;
        }

        public Integer getStepOrder() { return stepOrder; }
        public String getStepName() { return stepName; }
        public com.testingautomation.testautomation.enums.flow.ActionType getActionType() { return actionType; }
        public String getReason() { return reason; }
        public String getUserMessage() { return userMessage; }
    }


    // ── Custom exceptions ─────────────────────────────────────────────
    public static abstract class AutomationException extends RuntimeException {

        public AutomationException(String message) {
            super(message);
        }

        public AutomationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ResourceNotFoundException extends AutomationException {
        public ResourceNotFoundException(String msg) { super(msg); }
    }

    public static class BadRequestException extends AutomationException {

        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class RunnerIntegrationException extends AutomationException {
        public RunnerIntegrationException(String msg, Throwable cause) { super(msg, cause); }
    }


    public static class InvalidCountException extends AutomationException{
        public InvalidCountException(String msg) { super(msg); }
    }

    public static class TimeoutException extends AutomationException {
        public TimeoutException(String msg) { super(msg); }
    }

    public static class StorageException extends AutomationException {
        public StorageException(String msg, Throwable cause) { super(msg, cause); }
    }

    // ── Handlers ──────────────────────────────────────────────────────


    @ExceptionHandler(ScenarioExecutionException.class)
    public ResponseEntity<ErrorResponse> handleScenarioExecution(
            ScenarioExecutionException ex
    ) {
        log.error(
                "Scenario execution failed at step={} scenarioIndex={} type={}",
                ex.getStep(),
                ex.getScenarioIndex(),
                ex.getScenarioType(),
                ex
        );

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getUserMessage()
        );

        Map<String, String> details = new HashMap<>();

        details.put("scenarioIndex", String.valueOf(ex.getScenarioIndex()));
        details.put("scenarioType", ex.getScenarioType().name());
        details.put("step", ex.getStep());

        if (ex.getCause() != null) {
            details.put("reason", ex.getCause().getMessage());
        }

        body.setDetails(details);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(body);
    }


    @ExceptionHandler(AssertionExecutionException.class)
    public ResponseEntity<ErrorResponse> handleAssertionExecution(
            AssertionExecutionException ex
    ) {
        log.error(
                "Scenario execution failed at AssertionType={} and UserMessage={} ",
                ex.getAssertionType(),
                ex.getUserMessage(),
                ex
        );

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getUserMessage()
        );

        Map<String, String> details = new HashMap<>();

        details.put("AssertionType", String.valueOf(ex.getAssertionType()));
        if (ex.getCause() != null) {
            details.put("reason", ex.getCause().getMessage());
        }
        body.setDetails(details);
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {

        String path = ex.getResourcePath();

        // Ignore browser noise
        if (path.contains("favicon.ico") || path.contains(".well-known")) {
            log.debug("Ignored static resource request: {}", path);
            return ResponseEntity.notFound().build();
        }

        // For other missing resources (if any real case)
        log.warn("Static resource not found: {}", path);

        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidCountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCount(InvalidCountException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("status", "FAILED");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RunnerIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleRunnerIntegration(RunnerIntegrationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("status", "FAILED");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageError(StorageException ex) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Storage error: " + ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Validation failed");
        body.setDetails(errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal server error"));
    }
    @ExceptionHandler(TestExecutionException.class)
    public ResponseEntity<ErrorResponse> handleTestExecution(TestExecutionException ex) {
        log.error("Test execution failed: {}", ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage()
        );

        Map<String, String> details = new HashMap<>();
        details.put("step", ex.getStep());
        details.put("locator", ex.getLocator());
        details.put("reason", ex.getReason());
        body.setDetails(details);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleSeleniumTimeout(TimeoutException ex) {
        log.error("Selenium timeout: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ErrorResponse.of(
                        HttpStatus.REQUEST_TIMEOUT.value(),
                        "Target web page is slow or element did not become visible within timeout"
                ));
    }

    @ExceptionHandler(FlowExecutionException.class)
    public ResponseEntity<ErrorResponse> handleFlowExecution(
            FlowExecutionException ex
    ) {
        log.error(
                "Flow execution failed at stepOrder={} stepName={} actionType={}",
                ex.getStepOrder(),
                ex.getStepName(),
                ex.getActionType(),
                ex
        );

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getUserMessage()
        );

        Map<String, String> details = new HashMap<>();

        if (ex.getStepOrder() != null) {
            details.put("stepOrder", String.valueOf(ex.getStepOrder()));
        }
        details.put("stepName", ex.getStepName());
        if (ex.getActionType() != null) {
            details.put("actionType", ex.getActionType().name());
        }
        details.put("reason", ex.getReason());

        if (ex.getCause() != null) {
            details.put("cause", ex.getCause().getMessage());
        }

        body.setDetails(details);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(body);
    }

    @ExceptionHandler({
            NoSuchElementException.class,
            ElementNotInteractableException.class,
            ElementClickInterceptedException.class,
            StaleElementReferenceException.class
    })
    public ResponseEntity<ErrorResponse> handleSeleniumInteraction(Exception ex) {
        log.error("Selenium interaction failure: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "Test step could not be executed on target web page: " + ex.getClass().getSimpleName()
                ));
    }

    // ── Error response shape ──────────────────────────────────────────

    @lombok.Data
    public static class ErrorResponse {
        private int status;
        private String message;
        private Instant timestamp = Instant.now();
        private Map<String, String> details;

        public static ErrorResponse of(int status, String message) {
            ErrorResponse r = new ErrorResponse();
            r.status = status;
            r.message = message;
            return r;
        }
    }
    @Data
    public static class TestExecutionException extends RuntimeException {
        private final String step;
        private final String locator;
        private final String reason;
    }
}

