package com.testingautomation.testautomation.executor;

import com.testingautomation.testautomation.dto.ResultRun;
import com.testingautomation.testautomation.dto.StepAction;
import com.testingautomation.testautomation.dto.TestCase;
import com.testingautomation.testautomation.services.ScreenshotService;
import com.testingautomation.testautomation.services.TestResultWriter;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SeleniumExecutor {
    private final Logger logger = LoggerFactory.getLogger(SeleniumExecutor.class);

    private final String resultsBaseDir;
    private final boolean screenshotOnStep;
    private final TestResultWriter testResultWriter;
    private final ScreenshotService screenshotService;

    public SeleniumExecutor(org.springframework.core.env.Environment env,TestResultWriter testResultWriter,ScreenshotService screenshotService) {
        this.resultsBaseDir = env.getProperty("autotest.results.base-dir", "./test-results");
        this.screenshotOnStep = Boolean.parseBoolean(env.getProperty("autotest.screenshot-on-step", "false"));
        this.testResultWriter=testResultWriter;
        this.screenshotService=screenshotService;
    }

    /**
     * Runs a single test case. For each invocation we create a fresh run folder:
     *  <resultsBaseDir>/<testCaseId>_<yyyy-MM-dd_HH-mm-ss>/
     * containing results.csv and screenshots/.
     */
    public ResultRun run(WebDriver driver1, String startUrl, List<StepAction> steps, String testCaseId,
                      String successMsg,
                      Path scenarioDir, String scenarioPrefix,int currIdx , int sizeOfScenarios) {
        List<String> screenshotUrls = new ArrayList<>();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));

        Path runDir = scenarioDir.resolve(testCaseId + "_" + timestamp);
        Path screenshotsDir = runDir.resolve("screenshots");

        try {
            Files.createDirectories(screenshotsDir);
            logger.info("[{}] Run folder created: {}", testCaseId, runDir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create run directory: " + e.getMessage(), e);
        }

        logger.info("[{}] Starting run at {}", testCaseId, startUrl);
        boolean testPassed = true;
        int stepNo = 0;
        try {
            // viewport
            try {
                driver1.manage().window().setSize(new Dimension(1366, 900));
            } catch (Exception e) {logger.debug("Could not set window size: {}", e.getMessage());}

            driver1.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver1.get(startUrl);

            waitForPageToRender(driver1);
            logger.info("[{}] Page loaded: {}", testCaseId, driver1.getCurrentUrl());
            for (StepAction s : steps) {
                stepNo++;
                try {
                    logger.info("[{}] Step {}: {} -> locatorType={} locator={} payload={}",
                            testCaseId,
                            stepNo,
                            s.getDescription(),
                            s.getLocatorType(),
                            s.getLocator(),
                            s.getPayload());

                    performAction(driver1, s);

                    if (screenshotOnStep) {
                        String screenshotUrl = screenshotService.takeScreenshot(
                                driver1,
                                testCaseId,
                                testCaseId + "_step" + stepNo,
                                screenshotsDir,
                                scenarioPrefix
                        );
                        if(screenshotUrl!=null)
                            screenshotUrls.add(screenshotUrl);
                    }

                }
                catch (RuntimeException ex) {

                    if ("SKIPPED".equals(ex.getMessage())) {
                        logger.info("[{}] Step {} skipped", testCaseId, stepNo);
                        continue;
                    }

                    logger.error("[{}] Step {} failed: {}", testCaseId, stepNo, ex.getMessage(), ex);

                    String screenshotUrl = screenshotService.takeScreenshot(
                            driver1,
                            testCaseId,
                            testCaseId + "_step" + stepNo,
                            screenshotsDir,
                            scenarioPrefix
                    );
                    if(screenshotUrl!=null)
                        screenshotUrls.add(screenshotUrl);

                    testPassed = false;
                    break;
                }
            }

            // final success message check
            if (successMsg != null && !successMsg.trim().isEmpty() && currIdx!=(sizeOfScenarios-1)) {
                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);
                String screenshotUrl=screenshotService.takeScreenshot(
                        driver1,
                        testCaseId,
                        testCaseId + "_final_check",
                        screenshotsDir,
                        scenarioPrefix
                );
                if(screenshotUrl!=null)
                    screenshotUrls.add(screenshotUrl);
                if (!foundVisible) {
                    testPassed = false;
                    logger.warn("[{}] Success message NOT visible in viewport: '{}'", testCaseId, successMsg);
                }else{
                    logger.info("[{}] Success message visible in viewport: '{}' test is passed",
                            testCaseId, successMsg);
                }
            }else{
                logger.info("Not a scenario whose result needs to be justified with success message!");
            }

        }
        catch (Exception e) {
            testPassed = false;
            logger.error("[{}] Test run failed: {}", testCaseId, e.getMessage(), e);
        }
        ResultRun resultRun=new ResultRun(testPassed ? "PASSED" : "FAILED",screenshotUrls);

        return resultRun;
    }

    public ResultRun runOnRenderedPage(WebDriver driver1,
                                       List<StepAction> steps,
                                       String testCaseId,
                                       String successMsg,
                                       Path scenarioDir,
                                       String scenarioPrefix) {

        List<String> screenshotUrls = new ArrayList<>();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));

        Path runDir = scenarioDir.resolve(testCaseId + "_" + timestamp);
        Path screenshotsDir = runDir.resolve("screenshots");

        try {
            Files.createDirectories(screenshotsDir);
            logger.info("[{}] Run folder created: {}", testCaseId, runDir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create run directory: " + e.getMessage(), e);
        }

        logger.info("[{}] Executing on CURRENT UI (no navigation)", testCaseId);

        boolean testPassed = true;
        int stepNo = 0;

        try {

            try {
                driver1.manage().window().setSize(new Dimension(1366, 900));
            } catch (Exception e) {
                logger.debug("Could not set window size: {}", e.getMessage());
            }

            driver1.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

            for (StepAction s : steps) {

                stepNo++;

                try {

                    logger.info("[{}] Step {}: {} -> locatorType={} locator={} payload={}",
                            testCaseId,
                            stepNo,
                            s.getDescription(),
                            s.getLocatorType(),
                            s.getLocator(),
                            s.getPayload());

                    performAction(driver1, s);

                    if (screenshotOnStep) {

                        String screenshotUrl = screenshotService.takeScreenshot(
                                driver1,
                                testCaseId,
                                testCaseId + "_step" + stepNo,
                                screenshotsDir,
                                scenarioPrefix
                        );

                        if (screenshotUrl != null)
                            screenshotUrls.add(screenshotUrl);
                    }

                } catch (RuntimeException ex) {

                    if ("SKIPPED".equals(ex.getMessage())) {
                        logger.info("[{}] Step {} skipped", testCaseId, stepNo);
                        continue;
                    }

                    logger.error("[{}] Step {} failed: {}", testCaseId, stepNo, ex.getMessage(), ex);

                    String screenshotUrl = screenshotService.takeScreenshot(
                            driver1,
                            testCaseId,
                            testCaseId + "_step" + stepNo,
                            screenshotsDir,
                            scenarioPrefix
                    );

                    if (screenshotUrl != null)
                        screenshotUrls.add(screenshotUrl);

                    testPassed = false;
                    break;
                }
            }

            // final success message check
            if (successMsg != null && !successMsg.trim().isEmpty()) {

                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);

                String screenshotUrl = screenshotService.takeScreenshot(
                        driver1,
                        testCaseId,
                        testCaseId + "_final_check",
                        screenshotsDir,
                        scenarioPrefix
                );

                if (screenshotUrl != null)
                    screenshotUrls.add(screenshotUrl);

                if (!foundVisible) {
                    testPassed = false;

                    logger.warn("[{}] Success message NOT visible in viewport: '{}'",
                            testCaseId, successMsg);
                } else {

                    logger.info("[{}] Success message visible in viewport: '{}' test passed",
                            testCaseId, successMsg);
                }
            }

        } catch (Exception e) {

            testPassed = false;

            logger.error("[{}] Test run failed: {}", testCaseId, e.getMessage(), e);
        }

        ResultRun resultRun = new ResultRun(
                testPassed ? "PASSED" : "FAILED",
                screenshotUrls
        );

        return resultRun;
    }


    private void performAction(WebDriver driver1,StepAction s) {
        By by = locatorFrom(s.getLocatorType(), s.getLocator());

        switch (s.getType()) {

            case TYPE:
                if (s.getPayload() != null && !s.getPayload().isBlank()) {
                    WebElement el = new WebDriverWait(driver1, Duration.ofSeconds(10))
                            .until(ExpectedConditions.visibilityOfElementLocated(by));
                    waitUntilEditable(driver1, el);
                    scrollIntoView(driver1, el);
                    String locator = s.getLocator().toLowerCase();
                    // Detect date/time fields automatically
                    if (locator.contains("date") || locator.contains("time")
                            || locator.contains("start") || locator.contains("end")) {
                        setDateUsingJS(driver1, el, s.getPayload());
                    } else {
                        el.clear();
                        el.sendKeys(s.getPayload());
                        // Trigger UI events for frameworks like React/Angular
                        ((JavascriptExecutor) driver1).executeScript(
                                "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
                        ((JavascriptExecutor) driver1).executeScript(
                                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", el);
                    }
                } else {
                    logger.info("Skipping TYPE for locator {} because payload is empty", s.getLocator());
                    throw new RuntimeException("SKIPPED");
                }
                break;
            case CLICK:

                String beforeUrl = driver1.getCurrentUrl();

                WebElement el = new WebDriverWait(driver1, Duration.ofSeconds(10))
                        .until(ExpectedConditions.presenceOfElementLocated(by));

                scrollIntoView(driver1, el);

                // Skip click if radio/checkbox already selected
                String type = el.getAttribute("type");
                if (type != null && (type.equalsIgnoreCase("radio") || type.equalsIgnoreCase("checkbox"))) {
                    if (el.isSelected()) {
                        logger.info("Element already selected, skipping click: {}", by);
                        break;
                    }
                }

                try {
                    new WebDriverWait(driver1, Duration.ofSeconds(5))
                            .until(ExpectedConditions.elementToBeClickable(el));

                    el.click();

                } catch (ElementClickInterceptedException e) {
                    logger.warn("Normal click failed, retrying via JS");
                    ((JavascriptExecutor) driver1).executeScript("arguments[0].click();", el);
                }

                // Only check navigation but don't fail if it doesn't change
                boolean navigated = waitForUrlChange(driver1, beforeUrl);
                if (navigated) {
                    logger.info("Navigation detected after click");
                }

                break;
            case SELECT:
                if (s.getPayload() != null && !s.getPayload().isBlank()) {
                    org.openqa.selenium.support.ui.Select sel =
                            new org.openqa.selenium.support.ui.Select(driver1.findElement(by));
                    sel.selectByVisibleText(s.getPayload());
                } else {
                    logger.info("Skipping SELECT for locator {} because payload is empty", s.getLocator());
                    throw new RuntimeException("SKIPPED");
                }
                break;

            case VERIFY_TEXT:
                String pageText = driver1.findElement(by).getText();
                if (!pageText.contains(s.getPayload())) {
                    throw new RuntimeException("Text verification failed. Expected to contain: " + s.getPayload() + " but was: " + pageText);
                }
                break;

            case WAIT:
                try { Thread.sleep(Long.parseLong(s.getPayload())); } catch (InterruptedException ignored) {}
                break;

            default:
                logger.warn("Unknown action type: {}", s.getType());
        }
    }
    private void setDateUsingJS(WebDriver driver, WebElement el, String value) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].value=arguments[1];", el, value);
        js.executeScript("arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);
        js.executeScript("arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", el);
        js.executeScript("arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));", el);
    }

    private By locatorFrom(String locatorType, String locator) {
        if ("css".equalsIgnoreCase(locatorType)) return By.cssSelector(locator);
        return By.xpath(locator);
    }



    private boolean waitForUrlChange(WebDriver driver1,String beforeUrl) {
        try {
            WebDriverWait wait = new WebDriverWait(driver1, Duration.ofSeconds(5));

            return wait.until(d -> {
                String afterUrl = d.getCurrentUrl();
                return !afterUrl.equals(beforeUrl);
            });

        } catch (Exception e) {
            return false;
        }
    }



    /**
     * Wait for the page to be painted where a meaningful UI element is visible.
     * This waits for document.readyState == 'complete' AND for one of a few
     * selectors that indicate the UI is painted.
     */
    private void waitForPageToRender(WebDriver driver1) {
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver1, java.time.Duration.ofSeconds(15))
                    .until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));

            // wait for one of the meaningful elements to be visible (login button, username, etc.)
            List<By> anchors = Arrays.asList(
                    By.cssSelector("#app-login-btn"),
                    By.cssSelector("#username"),
                    By.cssSelector("#companyIdentifier"),
                    By.cssSelector("input")
            );

            new org.openqa.selenium.support.ui.WebDriverWait(driver1, java.time.Duration.ofSeconds(15))
                    .until(d -> {
                        for (By b : anchors) {
                            try {
                                if (!d.findElements(b).isEmpty() && d.findElement(b).isDisplayed()) {
                                    return true;
                                }
                            } catch (Exception ignored) {}
                        }
                        return false;
                    });

            // small render buffer to allow CSS/animations/fonts to paint
            Thread.sleep(700);

        } catch (Exception e) {
            logger.warn("UI render wait timeout — continuing: {}", e.getMessage());
        }
    }

    private void scrollIntoView(WebDriver driver1,WebElement el) {
        ((JavascriptExecutor) driver1)
                .executeScript("arguments[0].scrollIntoView({block:'center'});", el);
    }
    private void waitUntilEditable(WebDriver driver1,WebElement el) {
        new WebDriverWait(driver1, Duration.ofSeconds(5))
                .until(d -> el.isDisplayed() && el.isEnabled());
    }
    // add this helper method to the class
    private boolean isTextVisibleInViewport(WebDriver driver, String text) {
        if (text == null || text.trim().isEmpty()) return false;
        try {
            Object res = ((JavascriptExecutor) driver).executeScript(
                    "var needle = arguments[0];" +
                            "var elems = document.querySelectorAll('body *');" +
                            "for (var i = 0; i < elems.length; i++) {" +
                            "  var e = elems[i];" +
                            "  var rect = e.getBoundingClientRect();" +
                            "  if (rect.width <= 0 || rect.height <= 0) continue;" +
                            "  if (rect.bottom <= 0 || rect.right <= 0) continue;" +
                            "  if (rect.top >= (window.innerHeight || document.documentElement.clientHeight)) continue;" +
                            "  if (rect.left >= (window.innerWidth || document.documentElement.clientWidth)) continue;" +
                            "  var style = window.getComputedStyle(e);" +
                            "  if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') continue;" +
                            "  var txt = e.innerText || '';" +
                            "  if (txt.indexOf(needle) !== -1) return true;" +
                            "}" +
                            "return false;",
                    text
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception ex) {
            logger.warn("Error while checking visible text in viewport: {}", ex.getMessage());
            return false;
        }
    }


}