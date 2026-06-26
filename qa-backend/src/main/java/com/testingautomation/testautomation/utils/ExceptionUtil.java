package com.testingautomation.testautomation.utils;

import com.testingautomation.testautomation.entities.Scenario;
import org.openqa.selenium.WebDriverException;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;

public class ExceptionUtil {
    public static String getUserFriendlyErrorMessage(Exception e, Scenario current, int scenarioIndex) {
        String scenarioType = current != null ? current.getType().toString() : "Unknown";
        String scenarioInfo = String.format("Scenario #%d (%s)", scenarioIndex + 1, scenarioType);

        if (e instanceof WebDriverException) {
            return scenarioInfo + " failed: Browser error - " + getWebDriverErrorMessage((WebDriverException) e);
        } else if (e instanceof TimeoutException) {
            return scenarioInfo + " failed: Operation timed out - Element not found or page is slow";
        } else if (e instanceof NoSuchElementException) {
            return scenarioInfo + " failed: Required element not found on the page";
        } else if (e instanceof IllegalArgumentException) {
            return scenarioInfo + " failed: Invalid configuration - " + e.getMessage();
        } else if (e.getMessage() != null && e.getMessage().contains("Index")) {
            return scenarioInfo + " failed: Internal error - Invalid scenario configuration";
        } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
            return scenarioInfo + " failed: Operation timed out - Check if page is responsive";
        } else {
            return scenarioInfo + " failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
        }
    }

    public static String getWebDriverErrorMessage(WebDriverException e) {
        String message = e.getMessage();
        if (message.contains("element not found")) {
            return "Required element not found on the page";
        } else if (message.contains("timeout")) {
            return "Operation timed out - Page may be slow or element not visible";
        } else if (message.contains("stale element")) {
            return "Page element is outdated - Please try again";
        } else if (message.contains("click")) {
            return "Unable to click element - Element may not be clickable";
        } else {
            return "Browser interaction error - " + message;
        }
    }
}
