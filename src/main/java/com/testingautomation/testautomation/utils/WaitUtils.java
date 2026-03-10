package com.testingautomation.testautomation.utils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import java.time.Duration;
import java.util.function.Function;

public class WaitUtils {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLLING = Duration.ofMillis(300);

    // Fluent wait builder
    private static FluentWait<WebDriver> fluent(WebDriver driver, Duration timeout) {
        return new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(POLLING)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(ElementClickInterceptedException.class);
    }

    public static WebElement waitForElementVisible(WebDriver driver, By locator, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public static WebElement waitForElementClickable(WebDriver driver, By locator, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public static boolean waitForUrlContains(WebDriver driver, String substring, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        return wait.until(ExpectedConditions.urlContains(substring));
    }

    public static void waitForPageLoad(WebDriver driver, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
    }

    // Wait for jQuery to finish (returns immediately if jQuery not present)
    public static void waitForJQueryToFinish(WebDriver driver, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        ExpectedCondition<Boolean> jQueryLoad = d -> {
            try {
                Object result = ((JavascriptExecutor) d).executeScript(
                        "return (window.jQuery != undefined) ? (jQuery.active == 0) : true;");
                return Boolean.TRUE.equals(result);
            } catch (JavascriptException e) {
                return true;
            }
        };
        wait.until(jQueryLoad);
    }

    // Wait until number of elements > n
    public static void waitForNumberOfElementsToBeMoreThan(WebDriver driver, By locator, int n, int secs) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(secs));
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(locator, n));
    }

    // Wait for a modal/container to be visible after clicking opener
    public static WebElement waitForModalVisibleAfterClick(WebDriver driver, By opener, By modalLocator, int secs) {
        // Wait for opener clickable, click, then wait for modal visible
        WebElement el = waitForElementClickable(driver, opener, secs);
        try {
            el.click();
        } catch (ElementClickInterceptedException ex) {
            // fallback: scroll and JS click
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", el);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
        return waitForElementVisible(driver, modalLocator, secs);
    }

    // Safe click with retries
    public static void safeClick(WebDriver driver, By locator, int secs) {
        FluentWait<WebDriver> wait = fluent(driver, Duration.ofSeconds(secs));
        wait.until(d -> {
            try {
                WebElement e = d.findElement(locator);
                ((JavascriptExecutor) d).executeScript("arguments[0].scrollIntoView(true);", e);
                if (!e.isDisplayed() || !e.isEnabled()) return false;
                try {
                    e.click();
                } catch (ElementClickInterceptedException ex) {
                    ((JavascriptExecutor) d).executeScript("arguments[0].click();", e);
                }
                return true;
            } catch (Exception ex) {
                return false;
            }
        });
    }
}