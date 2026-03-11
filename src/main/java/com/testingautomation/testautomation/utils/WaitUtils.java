package com.testingautomation.testautomation.utils;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;

public final class WaitUtils {

    private WaitUtils() {}

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLLING = Duration.ofMillis(300);

    /* -----------------------------
       Fluent Wait (driver safe)
    ------------------------------*/
    private static FluentWait<WebDriver> fluent(WebDriver driver, Duration timeout) {
        return new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(POLLING)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .ignoring(ElementClickInterceptedException.class)
                .ignoring(ElementNotInteractableException.class);
    }

    /* -----------------------------
       Visibility Wait
    ------------------------------*/
    public static WebElement waitForVisible(WebDriver driver, By locator) {
        return fluent(driver, DEFAULT_TIMEOUT)
                .until(d -> d.findElement(locator));
    }

    public static WebElement waitForVisible(WebDriver driver, By locator, int secs) {
        return fluent(driver, Duration.ofSeconds(secs))
                .until(d -> d.findElement(locator));
    }

    /* -----------------------------
       Clickable Wait
    ------------------------------*/
    public static WebElement waitForClickable(WebDriver driver, By locator) {
        return fluent(driver, DEFAULT_TIMEOUT)
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    public static WebElement waitForClickable(WebDriver driver, By locator, int secs) {
        return fluent(driver, Duration.ofSeconds(secs))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    /* -----------------------------
       URL Wait
    ------------------------------*/
    public static void waitForUrlContains(WebDriver driver, String text) {
        fluent(driver, DEFAULT_TIMEOUT)
                .until(ExpectedConditions.urlContains(text));
    }

    public static void waitForUrlContains(WebDriver driver, String text, int secs) {
        fluent(driver, Duration.ofSeconds(secs))
                .until(ExpectedConditions.urlContains(text));
    }

    /* -----------------------------
       Page Load Wait
    ------------------------------*/
    public static void waitForPageLoad(WebDriver driver) {
        fluent(driver, DEFAULT_TIMEOUT)
                .until(d ->
                        ((JavascriptExecutor)d)
                                .executeScript("return document.readyState")
                                .equals("complete"));
    }

    /* -----------------------------
       AJAX Wait
    ------------------------------*/
    public static void waitForAjax(WebDriver driver) {
        fluent(driver, DEFAULT_TIMEOUT).until(d -> {
            try {
                Object result = ((JavascriptExecutor)d)
                        .executeScript("return window.jQuery != undefined && jQuery.active == 0");
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return true;
            }
        });
    }

    /* -----------------------------
       Safe Click (most important)
    ------------------------------*/
    public static void safeClick(WebDriver driver, By locator) {

        fluent(driver, DEFAULT_TIMEOUT).until(d -> {

            WebElement element = d.findElement(locator);

            ((JavascriptExecutor)d)
                    .executeScript("arguments[0].scrollIntoView({block:'center'});", element);

            if(!element.isDisplayed() || !element.isEnabled())
                return false;

            try {
                element.click();
                return true;
            }
            catch (ElementClickInterceptedException e) {

                ((JavascriptExecutor)d)
                        .executeScript("arguments[0].click();", element);

                return true;
            }
        });
    }

    /* -----------------------------
       Modal Wait Helper
    ------------------------------*/
    public static WebElement openModalAndWait(WebDriver driver,
                                              By opener,
                                              By modalLocator) {

        safeClick(driver, opener);

        return fluent(driver, DEFAULT_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(modalLocator));
    }

    /* -----------------------------
       Wait for element count
    ------------------------------*/
    public static void waitForMoreElements(WebDriver driver, By locator, int count) {
        fluent(driver, DEFAULT_TIMEOUT)
                .until(ExpectedConditions.numberOfElementsToBeMoreThan(locator, count));
    }

}