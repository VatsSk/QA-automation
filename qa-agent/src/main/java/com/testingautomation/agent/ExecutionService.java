package com.testingautomation.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executes a downloaded run using a local ChromeDriver.
 * Reports logs and screenshots back to the backend via AgentApiClient.
 * No Spring, no S3, no MongoDB — all results go through the REST API.
 */
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final AgentApiClient api;
    private final boolean headless;

    public ExecutionService(AgentApiClient api, boolean headless) {
        this.api      = api;
        this.headless = headless;
    }

    public void execute(String agentId, String runId, JsonNode runData) {
        WebDriver driver = null;
        int step = 0;
        try {
            driver = createDriver();
            log.info("Browser started for run {}", runId);

            JsonNode scenarios = runData.path("scenariosList");
            if (scenarios.isMissingNode() || scenarios.isEmpty()) {
                complete(agentId, runId, "FAILED", "No scenarios found in run");
                return;
            }

            boolean anyFailed = false;

            for (JsonNode scenario : scenarios) {
                step++;
                String type = scenario.path("type").asText("");
                String url  = scenario.path("url").asText("");

                api.uploadLog(runId, step, "RUNNING", "Starting scenario type=" + type);

                try {
                    executeScenario(driver, runId, scenario, step);
                    api.uploadLog(runId, step, "PASSED", "Scenario completed: " + type);
                } catch (Exception e) {
                    anyFailed = true;
                    api.uploadLog(runId, step, "FAILED", "Scenario failed: " + e.getMessage());
                    log.error("Scenario {} failed: {}", step, e.getMessage());
                    // take failure screenshot
                    uploadScreenshot(runId, driver, step + "-fail");
                    // continue remaining scenarios
                }
            }

            String finalStatus = anyFailed ? "FAILED" : "PASSED";
            complete(agentId, runId, finalStatus, "Execution completed");

        } catch (Exception e) {
            log.error("Fatal execution error for run {}: {}", runId, e.getMessage(), e);
            try { complete(agentId, runId, "ERROR", e.getMessage()); } catch (Exception ignored) {}
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Scenario dispatcher ───────────────────────────────────────────

    private void executeScenario(WebDriver driver, String runId, JsonNode sc, int step) throws Exception {
        String type = sc.path("type").asText("");
        String url  = sc.path("url").asText("");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        switch (type) {
            case "URL" -> {
                log.info("Navigating to {}", url);
                driver.get(url);
                waitForPageLoad(driver);
                uploadScreenshot(runId, driver, step + "-url");
            }
            case "MODAL", "MODAL_NAV", "FORM_MODAL" -> {
                String cssOpener = sc.path("cssOpener").asText("");
                if (!cssOpener.isBlank()) {
                    try {
                        WebElement opener = wait.until(
                                ExpectedConditions.elementToBeClickable(By.cssSelector(cssOpener)));
                        opener.click();
                    } catch (Exception e) {
                        log.warn("Standard click failed for opener '{}', trying JS click. Error: {}", cssOpener, e.getMessage());
                        WebElement opener = wait.until(
                                ExpectedConditions.presenceOfElementLocated(By.cssSelector(cssOpener)));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", opener);
                    }
                    Thread.sleep(500);
                }
                fillFormAndSubmit(driver, wait, runId, sc, step);
                uploadScreenshot(runId, driver, step + "-modal");
            }
            case "URL_NAV" -> {
                driver.get(url);
                waitForPageLoad(driver);
                fillFormAndSubmit(driver, wait, runId, sc, step);
                uploadScreenshot(runId, driver, step + "-url-nav");
            }
            case "SEARCH_NAV" -> {
                String cssOpener = sc.path("cssOpener").asText("");
                String value     = sc.path("value").asText("");
                if (!cssOpener.isBlank()) {
                    WebElement input = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(By.cssSelector(cssOpener)));
                    input.clear();
                    input.sendKeys(value);
                    Thread.sleep(300);
                }
                uploadScreenshot(runId, driver, step + "-search");
            }
            case "VERIFY_PAGE" -> runVerifyPage(driver, wait, runId, sc, step);
            case "ASSERT" -> runAssertions(driver, wait, runId, sc, step);
            case "FILTER_NAV" -> applyFilters(driver, wait, sc);
            default -> log.warn("Unsupported scenario type '{}', skipping", type);
        }
    }

    // ── Form fill helper ──────────────────────────────────────────────

    private void fillFormAndSubmit(WebDriver driver, WebDriverWait wait,
                                   String runId, JsonNode sc, int step) throws Exception {
        JsonNode testCases = sc.path("manualTestCases");
        if (testCases.isMissingNode() || testCases.isEmpty()) return;

        for (JsonNode tc : testCases) {
            JsonNode fields = tc.path("fields");
            if (fields.isMissingNode()) continue;

            fields.fields().forEachRemaining(entry -> {
                String selector = entry.getKey();
                String value    = entry.getValue().asText("");
                try {
                    WebElement el = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)));
                    String tag  = el.getTagName().toLowerCase();
                    String elType = el.getAttribute("type") != null
                            ? el.getAttribute("type").toLowerCase() : "";

                    if ("select".equals(tag)) {
                        new Select(el).selectByVisibleText(value);
                    } else if ("checkbox".equals(elType) || "radio".equals(elType)) {
                        if (!el.isSelected()) el.click();
                    } else {
                        el.clear();
                        el.sendKeys(value);
                    }
                } catch (Exception e) {
                    log.warn("Could not fill field {}: {}", selector, e.getMessage());
                }
            });

            // submit / save button
            String saveCss = sc.path("saveBtnCss").asText("");
            if (saveCss.isBlank()) {
                saveCss = sc.path("clickCss").asText("");
            }
            if (!saveCss.isBlank()) {
                try {
                    WebElement saveBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(By.cssSelector(saveCss)));
                    saveBtn.click();
                } catch (Exception e) {
                    log.warn("Standard click failed for save button '{}', trying JS click. Error: {}", saveCss, e.getMessage());
                    WebElement saveBtn = wait.until(
                            ExpectedConditions.presenceOfElementLocated(By.cssSelector(saveCss)));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", saveBtn);
                }
                Thread.sleep(500);
            }

            uploadScreenshot(runId, driver, step + "-form");
        }
    }

    // ── VERIFY_PAGE ───────────────────────────────────────────────────

    private void runVerifyPage(WebDriver driver, WebDriverWait wait,
                               String runId, JsonNode sc, int step) throws Exception {
        String url = sc.path("url").asText("");
        if (!url.isBlank()) {
            log.info("VERIFY_PAGE navigating to {}", url);
            driver.get(url);
            waitForPageLoad(driver);
        }

        uploadScreenshot(runId, driver, step + "-verify-nav");

        JsonNode finalVerify = sc.path("finalVerify");
        if (finalVerify.isMissingNode() || finalVerify.isEmpty()) {
            log.info("VERIFY_PAGE: no finalVerify items, skipping checks");
            return;
        }

        List<String> failures = new ArrayList<>();

        for (JsonNode verify : finalVerify) {
            String css      = verify.path("cssSelector").asText("");
            String expected = verify.path("expectedResult").asText("");

            if (css.isBlank()) continue;

            try {
                WebElement el = wait.until(d -> {
                    WebElement e = d.findElement(By.cssSelector(css));
                    return e.isDisplayed() ? e : null;
                });

                String actual = el.getText().trim();
                log.info("VERIFY_PAGE css='{}' expected='{}' actual='{}'", css, expected, actual);

                if (!actual.equals(expected)) {
                    String msg = "MISMATCH css='" + css + "' expected='" + expected + "' actual='" + actual + "'";
                    failures.add(msg);
                    api.uploadLog(runId, step, "FAILED", msg);
                    uploadScreenshot(runId, driver, step + "-verify-fail");
                } else {
                    api.uploadLog(runId, step, "PASSED", "Verified css='" + css + "' = '" + actual + "'");
                }
            } catch (Exception e) {
                String msg = "ERROR css='" + css + "': " + e.getMessage();
                failures.add(msg);
                api.uploadLog(runId, step, "FAILED", msg);
                uploadScreenshot(runId, driver, step + "-verify-error");
            }
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("VERIFY_PAGE failed: " + failures);
        }
    }

    // ── Assert helper ─────────────────────────────────────────────────

    private void runAssertions(WebDriver driver, WebDriverWait wait,
                               String runId, JsonNode sc, int step) throws Exception {
        JsonNode assertions = sc.path("assertions");
        if (assertions.isMissingNode()) return;

        int aIdx = 0;
        for (JsonNode a : assertions) {
            aIdx++;
            String assertType = a.path("type").asText("");
            String locator    = a.path("locator").asText("");
            String expected   = a.path("expected").asText("");

            try {
                switch (assertType) {
                    case "ASSERT_VISIBLE" -> {
                        WebElement el = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(locator)));
                        if (!el.isDisplayed())
                            throw new AssertionError("Element not visible: " + locator);
                    }
                    case "ASSERT_NOT_VISIBLE" -> wait.until(
                            ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(locator)));
                    case "ASSERT_ELEMENT_PRESENT" -> {
                        if (driver.findElements(By.cssSelector(locator)).isEmpty())
                            throw new AssertionError("Element not present: " + locator);
                    }
                    case "ASSERT_TEXT_CONTAINS" -> {
                        WebElement el = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(locator)));
                        if (!el.getText().contains(expected))
                            throw new AssertionError("Text '" + el.getText() + "' does not contain '" + expected + "'");
                    }
                    case "ASSERT_TEXT_EQUALS" -> {
                        WebElement el = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(locator)));
                        if (!el.getText().trim().equals(expected))
                            throw new AssertionError("Text mismatch: expected='" + expected + "' actual='" + el.getText() + "'");
                    }
                    default -> log.warn("Unsupported assertion type '{}', skipping", assertType);
                }
                api.uploadLog(runId, step * 100 + aIdx, "PASSED", "Assert " + assertType + " passed");
            } catch (AssertionError | Exception ae) {
                api.uploadLog(runId, step * 100 + aIdx, "FAILED", "Assert " + assertType + " failed: " + ae.getMessage());
                uploadScreenshot(runId, driver, step + "-assert-" + aIdx + "-fail");
                throw ae;
            }
            uploadScreenshot(runId, driver, step + "-assert-" + aIdx);
        }
    }

    // ── Filter helper ─────────────────────────────────────────────────

    private void applyFilters(WebDriver driver, WebDriverWait wait, JsonNode sc) {
        JsonNode filters = sc.path("filters");
        if (filters.isMissingNode()) return;

        for (JsonNode f : filters) {
            String qs    = f.path("querySelector").asText("");
            String value = f.path("value").asText("");
            if (qs.isBlank()) continue;
            try {
                WebElement el = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector(qs)));
                el.clear();
                el.sendKeys(value);
                Thread.sleep(300);
            } catch (Exception e) {
                log.warn("Filter apply failed for {}: {}", qs, e.getMessage());
            }
        }

        String applyBtn = sc.path("applyFilterBtn").asText("");
        if (!applyBtn.isBlank()) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(applyBtn))).click();
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("Apply filter button click failed: {}", e.getMessage());
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private WebDriver createDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        if (headless) {
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        }
        options.addArguments("--start-maximized");
        return new ChromeDriver(options);
    }

    private void waitForPageLoad(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
                d -> ((JavascriptExecutor) d)
                        .executeScript("return document.readyState").equals("complete"));
    }

    private void uploadScreenshot(String runId, WebDriver driver, String label) {
        try {
            File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dest = Paths.get(System.getProperty("java.io.tmpdir"),
                    "qa-agent-" + runId + "-" + label + "-" + UUID.randomUUID() + ".png");
            Files.copy(tmp.toPath(), dest);
            api.uploadScreenshot(runId, dest.toFile());
            Files.deleteIfExists(dest);
        } catch (Exception e) {
            log.warn("Screenshot upload skipped: {}", e.getMessage());
        }
    }

    private void complete(String agentId, String runId, String status, String reason) throws IOException {
        api.completeRun(agentId, runId, status, reason);
        log.info("Run {} marked {} — {}", runId, status, reason);
    }
}
