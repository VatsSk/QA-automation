package com.testingautomation.testautomation.executor;

import com.testingautomation.testautomation.model.StepAction;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class SeleniumExecutor {
    private final Logger logger = LoggerFactory.getLogger(SeleniumExecutor.class);

    private final String resultsBaseDir;
    private final boolean screenshotOnStep;

    public SeleniumExecutor(org.springframework.core.env.Environment env) {
        this.resultsBaseDir = env.getProperty("autotest.results.base-dir", "./test-results");
        this.screenshotOnStep = Boolean.parseBoolean(env.getProperty("autotest.screenshot-on-step", "false"));
    }

    /**
     * Runs a single test case. For each invocation we create a fresh run folder:
     *  <resultsBaseDir>/<testCaseId>_<yyyy-MM-dd_HH-mm-ss>/
     * containing results.csv and screenshots/.
     */
    public String run(WebDriver driver1, String startUrl, List<StepAction> steps, String testCaseId,String successMsg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        Path runDir = Paths.get(resultsBaseDir, testCaseId + "_" + timestamp);
        Path screenshotsDir = runDir.resolve("screenshots");
        Path resultsCsv = runDir.resolve("results.csv");
        Path finalCsv = runDir.getParent().getParent().resolve("finalResult.csv");
        System.out.println("Path to final csvvvv"+finalCsv.toString());
        try {
            Files.createDirectories(screenshotsDir);
            // create CSV file and write header
            if (Files.notExists(resultsCsv)) {
                Files.createFile(resultsCsv);
                writeCsvLine(resultsCsv, "testCaseId,stepNo,stepDescription,locatorType,locator,payload,status,errorMessage,screenshotPath,pageUrl,timestamp");
            }
            if(Files.notExists(finalCsv)) {
                Files.createFile(finalCsv);
                writeCsvLine(finalCsv, "testCaseId,description,locatorType,locator,payload,status,screenshotPath,pageUrl,timestamp");
            }
            logger.info("[{}] Run folder created: {}", testCaseId, runDir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create run directory: " + e.getMessage(), e);
        }

        logger.info("[{}] Starting run at {}", testCaseId, startUrl);
        boolean testPassed = true;
        int stepNo = 0;
        int passed = 0, failed = 0, skipped = 0;
        String finalResult = "PASSED";

        try {
            // ensure stable viewport
            try {
                driver1.manage().window().setSize(new Dimension(1366, 900));
            } catch (Exception e) {
                logger.debug("Could not set window size: {}", e.getMessage());
            }

            driver1.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver1.get(startUrl);

            // wait for UI to render (improved method, waits for meaningful visible element)
            waitForPageToRender(driver1);

            logger.info("[{}] Page loaded: {}", testCaseId, driver1.getCurrentUrl());
            String screenshotPath = "";
            String status = "PASSED";
            String errorMessage = "";
            for (StepAction s : steps) {
                stepNo++;
                screenshotPath = "";
                status = "PASSED";
                errorMessage = "";

                try {
                    logger.info("[{}] Step {}: {} -> locatorType={} locator={} payload={}",
                            testCaseId, stepNo, s.getDescription(), s.getLocatorType(), s.getLocator(), s.getPayload());

                    performAction(driver1,s);


                    // optionally take screenshot for every successful step
                    if (screenshotOnStep) {
                        screenshotPath = takeScreenshot(driver1,testCaseId + "_step" + stepNo, screenshotsDir);
                    }

                    logger.debug("[{}] Step {} completed", testCaseId, stepNo);
                    passed++;

                } catch (RuntimeException ex) {
                    // SKIPPED is thrown from performAction for missing payloads; handle it specially
                    if ("SKIPPED".equals(ex.getMessage())) {
                        status = "SKIPPED";
                        errorMessage = "";
                        skipped++;
                        logger.info("[{}] Step {} skipped", testCaseId, stepNo);
                        // write row and continue (skipped is not a failure)
                        writeStepResultRow(driver1,resultsCsv, testCaseId, stepNo, s, status, errorMessage, screenshotPath);
                        continue;
                    }

                    testPassed = false;
                    finalResult = "FAILED";
                    if ("FAILED_CLICK_NO_NAVIGATION".equals(ex.getMessage())) {
                        status = "FAILED";
                        errorMessage = "URL did not change after click";
                        logger.warn("[{}] Step {} failed: URL did not change after click", testCaseId, stepNo);
                    } else {
                        status = "FAILED";
                        errorMessage = ex.getMessage() != null ? ex.getMessage().replaceAll("[\\r\\n,]", " ") : "";
                        logger.error("[{}] Step {} failed: {}", testCaseId, stepNo, ex.getMessage(), ex);

                    }

                    // capture screenshot on failure (ensure UI repainted before capture)
                    screenshotPath = takeScreenshot(driver1,testCaseId + "_step" + stepNo, screenshotsDir);
                    failed++;

                    // write the failed step then break (stop test)
                    writeStepResultRow(driver1,resultsCsv, testCaseId, stepNo, s, status, errorMessage, screenshotPath);
                    break;
                }

                // write row for the step executed
                writeStepResultRow(driver1,resultsCsv, testCaseId, stepNo, s, status, errorMessage, screenshotPath);
            }
            // after you finish executing steps (and before writeFinalResultRow)
            if (successMsg != null && !successMsg.trim().isEmpty()) {
                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);
                if (foundVisible) {
                    status = "PASSED";
                    errorMessage = "";
                    testPassed = testPassed && true; // keep previous failures if any
                    logger.info("[{}] Success message visible in viewport: '{}'", testCaseId, successMsg);
                } else {
                    status = "FAILED";
                    // If there were earlier step failures, testPassed is already false; otherwise set it now
                    testPassed = false;
                    errorMessage = "Success message not visible in viewport: " + successMsg.replaceAll(",", " ");
                    logger.warn("[{}] Success message NOT visible in viewport: '{}'", testCaseId, successMsg);
                    // capture screenshot to help debug
                    screenshotPath = takeScreenshot(driver1, testCaseId + "_final_check", screenshotsDir);
                }
            }

            if(steps.size()>0){
               writeFinalResultRow(driver1,finalCsv,testCaseId,steps.get(steps.size()-1),status,screenshotPath);
            }else{
                logger.info("step is empty");
            }

            if (testPassed) {
                logger.info("[{}] Test finished successfully (passed={} skipped={} failed={})", testCaseId, passed, skipped, failed);

            } else {
                logger.info("[{}] Test finished with failures (passed={} skipped={} failed={})", testCaseId, passed, skipped, failed);
            }

        } catch (Exception e) {
            testPassed = false;
            finalResult = "FAILED";
            logger.error("[{}] Test run failed: {}", testCaseId, e.getMessage(), e);
        } finally {
            // final summary row for test case
            String overall = testPassed ? "PASSED" : "FAILED";
            String summaryDesc = String.format("SUMMARY for test %s", testCaseId);
            writeCsvLine(resultsCsv, String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    testCaseId,
                    "SUMMARY",
                    summaryDesc.replaceAll(",", " "),
                    "",
                    "",
                    "",
                    overall,
                    "",
                    "",
                    safe(driver1 != null ? driver1.getCurrentUrl() : ""),
                    safe(Instant.now().toString())
            ));
            if (failed > 0) {
                finalResult = "FAILED";
            } else if (passed == 0 && skipped > 0) {
                finalResult = "SKIPPED";
            } else {
                finalResult = "PASSED";
            }
            // NOTE: do not quit driver here; lifecycle handled by caller or Spring config
        }
        return finalResult;
    }

    public String runOnRenderedPage(
            WebDriver driver1,
            List<StepAction> steps,
            String testCaseId,
            String successMsg
    ) {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HHmm"));

        Path runDir = Paths.get(resultsBaseDir, testCaseId + "_" + timestamp);
        Path screenshotsDir = runDir.resolve("screenshots");
        Path resultsCsv = runDir.resolve("results.csv");
        Path finalCsv = runDir.getParent().getParent().resolve("finalResult.csv");

        try {
            Files.createDirectories(screenshotsDir);

            if (Files.notExists(resultsCsv)) {
                Files.createFile(resultsCsv);
                writeCsvLine(resultsCsv,
                        "testCaseId,stepNo,stepDescription,locatorType,locator,payload,status,errorMessage,screenshotPath,pageUrl,timestamp");
            }

            if (Files.notExists(finalCsv)) {
                Files.createFile(finalCsv);
                writeCsvLine(finalCsv,
                        "testCaseId,description,locatorType,locator,payload,status,screenshotPath,pageUrl,timestamp");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create run directory: " + e.getMessage(), e);
        }

        logger.info("[{}] Executing on CURRENT UI (no navigation)", testCaseId);

        boolean testPassed = true;
        int stepNo = 0;
        int passed = 0, failed = 0, skipped = 0;
        String finalResult = "PASSED";

        String screenshotPath = "";
        String status = "PASSED";
        String errorMessage = "";

        try {

            driver1.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

            for (StepAction s : steps) {
                stepNo++;
                screenshotPath = "";
                status = "PASSED";
                errorMessage = "";

                try {
                    logger.info("[{}] Step {}: {} -> locatorType={} locator={} payload={}",
                            testCaseId, stepNo,
                            s.getDescription(),
                            s.getLocatorType(),
                            s.getLocator(),
                            s.getPayload());

                    performAction(driver1, s);

                    if (screenshotOnStep) {
                        screenshotPath = takeScreenshot(
                                driver1,
                                testCaseId + "_step" + stepNo,
                                screenshotsDir
                        );
                    }

                    passed++;

                } catch (RuntimeException ex) {

                    if ("SKIPPED".equals(ex.getMessage())) {
                        status = "SKIPPED";
                        skipped++;
                        writeStepResultRow(driver1, resultsCsv,
                                testCaseId, stepNo, s,
                                status, "", screenshotPath);
                        continue;
                    }

                    testPassed = false;
                    status = "FAILED";
                    errorMessage = ex.getMessage() != null
                            ? ex.getMessage().replaceAll("[\\r\\n,]", " ")
                            : "";

                    screenshotPath = takeScreenshot(
                            driver1,
                            testCaseId + "_step" + stepNo,
                            screenshotsDir
                    );

                    failed++;

                    writeStepResultRow(driver1, resultsCsv,
                            testCaseId, stepNo, s,
                            status, errorMessage, screenshotPath);

                    break;
                }

                writeStepResultRow(driver1, resultsCsv,
                        testCaseId, stepNo, s,
                        status, errorMessage, screenshotPath);
            }
            if (successMsg != null && !successMsg.trim().isEmpty()) {
                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);
                logger.info("IS visible file------>"+foundVisible);
                if (foundVisible) {
                    status = "PASSED";
                    errorMessage = "";
                    testPassed = testPassed && true; // keep previous failures if any
                    logger.info("[{}] Success message visible in viewport: '{}'", testCaseId, successMsg);
                } else {
                    status = "FAILED";
                    // If there were earlier step failures, testPassed is already false; otherwise set it now
                    testPassed = false;
                    errorMessage = "Success message not visible in viewport: " + successMsg.replaceAll(",", " ");
                    logger.warn("[{}] Success message NOT visible in viewport: '{}'", testCaseId, successMsg);
                    // capture screenshot to help debug
                    screenshotPath = takeScreenshot(driver1, testCaseId + "_final_check", screenshotsDir);
                }
            }


            if (steps.size() > 0) {
                writeFinalResultRow(driver1, finalCsv,
                        testCaseId,
                        steps.get(steps.size() - 1),
                        status,
                        screenshotPath);
            }

            logger.info("[{}] Execution completed (passed={} skipped={} failed={})",
                    testCaseId, passed, skipped, failed);

        } catch (Exception e) {
            testPassed = false;
            finalResult = "FAILED";
            logger.error("[{}] Test run failed: {}", testCaseId, e.getMessage(), e);
        } finally {

            String overall = testPassed ? "PASSED" : "FAILED";
            String summaryDesc = String.format("SUMMARY for test %s", testCaseId);

            writeCsvLine(resultsCsv,
                    String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            testCaseId,
                            "SUMMARY",
                            summaryDesc.replaceAll(",", " "),
                            "",
                            "",
                            "",
                            overall,
                            "",
                            "",
                            safe(driver1.getCurrentUrl()),
                            safe(Instant.now().toString())
                    ));
            if (failed > 0) {
                finalResult = "FAILED";
            } else if (passed == 0 && skipped > 0) {
                finalResult = "SKIPPED";
            } else {
                finalResult = "PASSED";
            }

        }

        return finalResult;
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

    /**
     * Takes screenshot and returns the saved filename (full path) or empty string on failure.
     * Ensures a small repaint buffer and scroll to top before capture.
     */
    private String takeScreenshot(WebDriver driver1,String name, Path screenshotsDir) {
        try {
            // ensure top-left visible and allow repaint
            try {
                ((JavascriptExecutor) driver1).executeScript("window.scrollTo(0,0)");
                Thread.sleep(300);
            } catch (Exception ignored) {}

            File src = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.FILE);
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
            String filename = screenshotsDir.resolve(name + "_" + timestamp + ".png").toString();
            FileUtils.copyFile(src, new File(filename));
            logger.info("Saved screenshot: {}", filename);
            return filename;
        } catch (Exception ex) {
            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
            return "";
        }
    }

    /**
     * Writes a CSV row describing a single step result to the given CSV path.
     */
    private void writeStepResultRow(WebDriver driver1,Path resultsCsv, String testCaseId, int stepNo, StepAction s,
                                    String status, String errorMessage, String screenshotPath) {
        String desc = s.getDescription() != null ? s.getDescription().replaceAll(",", " ") : "";
        String locatorType = s.getLocatorType() != null ? s.getLocatorType() : "";
        String locator = s.getLocator() != null ? s.getLocator().replaceAll(",", " ") : "";
        String payload = s.getPayload() != null ? s.getPayload().replaceAll(",", " ") : "";
        String pageUrl = driver1 != null ? safe(driver1.getCurrentUrl()) : "";
        String timestamp = safe(Instant.now().toString());
        String line = String.format("%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                testCaseId,
                stepNo,
                desc,
                locatorType,
                locator,
                payload,
                status,
                errorMessage,
                screenshotPath,
                pageUrl,
                timestamp
        );
        writeCsvLine(resultsCsv, line);
    }

    private void writeFinalResultRow(WebDriver driver1,Path resultsCsv, String testCaseId,StepAction s,
                                     String status, String screenshotPath) {
        String desc = s.getDescription() != null ? s.getDescription().replaceAll(",", " ") : "";
        String locatorType = s.getLocatorType() != null ? s.getLocatorType() : "";
        String locator = s.getLocator() != null ? s.getLocator().replaceAll(",", " ") : "";
        String payload = s.getPayload() != null ? s.getPayload().replaceAll(",", " ") : "";
        String pageUrl = driver1 != null ? safe(driver1.getCurrentUrl()) : "";
        String timestamp = safe(Instant.now().toString());
        String line = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                testCaseId,
//                    stepNo,
                desc,
                locatorType,
                locator,
                payload,
                status,
                screenshotPath,
                pageUrl,
                timestamp
        );
        writeCsvLine(resultsCsv, line);
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
     * Append a line to CSV file in a thread-safe manner.
     */
    private synchronized void writeCsvLine(Path resultsCsv, String line) {
        try (BufferedWriter bw = Files.newBufferedWriter(resultsCsv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException ex) {
            logger.error("Failed to write CSV line: {}", ex.getMessage(), ex);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n]", " ").replaceAll(",", " ");
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