//package com.testingautomation.testautomation.executorService;
//
//import com.testingautomation.testautomation.model.StepAction;
//import org.apache.commons.io.FileUtils;
//import org.openqa.selenium.*;
//import org.openqa.selenium.support.ui.WebDriverWait;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.StandardOpenOption;
//import java.time.Duration;
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//import java.util.Arrays;
//import java.util.List;
//import java.util.logging.Logger;
//
//public class ExecutorService {
//    Logger logger = Logger.getLogger(ExecutorService.class.getName());
//    private By locatorFrom(String locatorType, String locator) {
//        if ("css".equalsIgnoreCase(locatorType)) return By.cssSelector(locator);
//        return By.xpath(locator);
//    }
//
//    /**
//     * Takes screenshot and returns the saved filename (full path) or empty string on failure.
//     * Ensures a small repaint buffer and scroll to top before capture.
//     */
//    private String takeScreenshot(String name, Path screenshotsDir) {
//        try {
//            // ensure top-left visible and allow repaint
//            try {
//                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0,0)");
//                Thread.sleep(300);
//            } catch (Exception ignored) {}
//
//            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
//            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
//            String filename = screenshotsDir.resolve(name + "_" + timestamp + ".png").toString();
//            FileUtils.copyFile(src, new File(filename));
//            logger.info("Saved screenshot: {}", filename);
//            return filename;
//        } catch (Exception ex) {
//            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
//            return "";
//        }
//    }
//
//    /**
//     * Writes a CSV row describing a single step result to the given CSV path.
//     */
//    private void writeStepResultRow(Path resultsCsv, String testCaseId, int stepNo, StepAction s,
//                                    String status, String errorMessage, String screenshotPath) {
//        String desc = s.getDescription() != null ? s.getDescription().replaceAll(",", " ") : "";
//        String locatorType = s.getLocatorType() != null ? s.getLocatorType() : "";
//        String locator = s.getLocator() != null ? s.getLocator().replaceAll(",", " ") : "";
//        String payload = s.getPayload() != null ? s.getPayload().replaceAll(",", " ") : "";
//        String pageUrl = driver != null ? safe(driver.getCurrentUrl()) : "";
//        String timestamp = safe(Instant.now().toString());
//        String line = String.format("%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s",
//                testCaseId,
//                stepNo,
//                desc,
//                locatorType,
//                locator,
//                payload,
//                status,
//                errorMessage,
//                screenshotPath,
//                pageUrl,
//                timestamp
//        );
//        writeCsvLine(resultsCsv, line);
//    }
//
//    /**
//     * Append a line to CSV file in a thread-safe manner.
//     */
//    private synchronized void writeCsvLine(Path resultsCsv, String line) {
//        try (BufferedWriter bw = Files.newBufferedWriter(resultsCsv, StandardCharsets.UTF_8,
//                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
//            bw.write(line);
//            bw.newLine();
//        } catch (IOException ex) {
//            logger.error("Failed to write CSV line: {}", ex.getMessage(), ex);
//        }
//    }
//
//    private static String safe(String s) {
//        if (s == null) return "";
//        return s.replaceAll("[\\r\\n]", " ").replaceAll(",", " ");
//    }
//
//    /**
//     * Wait for the page to be painted where a meaningful UI element is visible.
//     * This waits for document.readyState == 'complete' AND for one of a few
//     * selectors that indicate the UI is painted.
//     */
//    private void waitForPageToRender() {
//        try {
//            new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(15))
//                    .until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
//
//            // wait for one of the meaningful elements to be visible (login button, username, etc.)
//            List<By> anchors = Arrays.asList(
//                    By.cssSelector("#app-login-btn"),
//                    By.cssSelector("#username"),
//                    By.cssSelector("#companyIdentifier"),
//                    By.cssSelector("input")
//            );
//
//            new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(15))
//                    .until(d -> {
//                        for (By b : anchors) {
//                            try {
//                                if (!d.findElements(b).isEmpty() && d.findElement(b).isDisplayed()) {
//                                    return true;
//                                }
//                            } catch (Exception ignored) {}
//                        }
//                        return false;
//                    });
//
//            // small render buffer to allow CSS/animations/fonts to paint
//            Thread.sleep(700);
//
//        } catch (Exception e) {
//            logger.warn("UI render wait timeout — continuing: {}", e.getMessage());
//        }
//    }
//    private void scrollIntoView(WebElement el) {
//        ((JavascriptExecutor) driver)
//                .executeScript("arguments[0].scrollIntoView({block:'center'});", el);
//    }
//    private void waitUntilEditable(WebElement el) {
//        new WebDriverWait(driver, Duration.ofSeconds(5))
//                .until(d -> el.isDisplayed() && el.isEnabled());
//    }
//    private boolean waitForUrlChange(String beforeUrl) {
//        try {
//            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
//
//            return wait.until(d -> {
//                String afterUrl = d.getCurrentUrl();
//                return !afterUrl.equals(beforeUrl);
//            });
//
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}
