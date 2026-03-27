package com.testingautomation.testautomation.globalException;


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

    // ── Custom exceptions ─────────────────────────────────────────────

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String msg) { super(msg); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String msg) { super(msg); }
    }

    public static class RunnerIntegrationException extends RuntimeException {
        public RunnerIntegrationException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class InvalidCountException extends IndexOutOfBoundsException{
        public InvalidCountException(String msg) { super(msg); }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String msg, Throwable cause) { super(msg, cause); }
    }

    // ── Handlers ──────────────────────────────────────────────────────

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

