package com.testingautomation.testautomation.services.executorService;

import com.ibm.icu.impl.Assert;
import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.enums.DataType;
import com.testingautomation.testautomation.enums.ManageColumnAction;
import com.testingautomation.testautomation.enums.Operator;
import com.testingautomation.testautomation.enums.ScenarioType;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.TableFilterExpression;
import com.testingautomation.testautomation.services.TableSawService;
import com.testingautomation.testautomation.utils.TableColumnValidator;
import com.testingautomation.testautomation.utils.promptUtils.PromptBuilder;
import com.testingautomation.testautomation.config.llmConfig.LLMServices;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import com.testingautomation.testautomation.services.screenShotsService.AIScreenshotService;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.tablesaw.api.Table;

import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;


@Component
public class SeleniumExecutor {
    private final Logger logger = LoggerFactory.getLogger(SeleniumExecutor.class);

    private final String resultsBaseDir;
    private final boolean screenshotOnStep;
    private final ScreenshotService screenshotService;
    private final LLMServices lLMServices;
    private final AIScreenshotService aiScreenshotService;
    private final TableSawService tableSawService;

    public SeleniumExecutor(org.springframework.core.env.Environment env, ScreenshotService screenshotService, LLMServices lLMServices, AIScreenshotService aiScreenshotService, TableSawService tableSawService) {
        this.resultsBaseDir = env.getProperty("autotest.results.base-dir", "./test-results");
        this.screenshotOnStep = Boolean.parseBoolean(env.getProperty("autotest.screenshot-on-step", "false"));
        this.screenshotService = screenshotService;
        this.lLMServices = lLMServices;
        this.aiScreenshotService = aiScreenshotService;
        this.tableSawService = tableSawService;
    }

    /**
     * Runs a single test case. For each invocation we create a fresh run folder:
     *  <resultsBaseDir>/<testCaseId>_<yyyy-MM-dd_HH-mm-ss>/
     * containing results.csv and screenshots/.
     */
    public ResultRun run(WebDriver driver1, String startUrl, List<StepAction> steps, String testCaseId,
                         String successMsg, Path scenarioDir, String scenarioPrefix,String expectedResult,int scenarioSize,int currScenarioIdx) {
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
                                "step_" + stepNo,
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
                            "step" + stepNo,
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
//
            System.out.println("while login expected result is : "+expectedResult+", successMsg " +successMsg +" and condition : "+(expectedResult!=null && successMsg != null && !successMsg.trim().isEmpty()) );
            if ((currScenarioIdx==scenarioSize-1) && expectedResult!=null && successMsg != null && !successMsg.trim().isEmpty() ) {
                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);
                String screenshotUrl=screenshotService.takeScreenshot(
                        driver1,
                        testCaseId,
                        "final_check",
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
                logger.info("Not a scenario whose result needs to be justified with success message and also doesn't have expected column!");
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
                                       String scenarioPrefix,String expectedResult) {

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
                                "step" + stepNo,
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
                            "step" + stepNo,
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
            if (expectedResult!=null && successMsg != null && !successMsg.trim().isEmpty()) {

                boolean foundVisible = isTextVisibleInViewport(driver1, successMsg);

                String screenshotUrl = screenshotService.takeScreenshot(
                        driver1,
                        testCaseId,
                        "final_check",
                        screenshotsDir,
                        scenarioPrefix
                );

                if (screenshotUrl != null)
                    screenshotUrls.add(screenshotUrl);

                if (!foundVisible) {
                    testPassed = false;

                    logger.warn("[{}] Success message NOT visible in viewport: '{}'", testCaseId, successMsg);
                } else {

                    logger.info("[{}] Success message visible in viewport: '{}' test passed",
                            testCaseId, successMsg);
                }
            }else{
                logger.info("Not a scenario whose result needs to be justified with success message and also doesn't have expected column!");
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

                    WebDriverWait wait = new WebDriverWait(driver1, Duration.ofSeconds(10));

                    WebElement el = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(by)
                    );

                    waitUntilEditable(driver1, el);
                    scrollIntoView(driver1, el);

                    String locator = s.getLocator() != null ? s.getLocator().toLowerCase() : "";
                    String elementId = el.getAttribute("id") != null ? el.getAttribute("id").toLowerCase() : "";
                    String classes = el.getAttribute("class") != null ? el.getAttribute("class").toLowerCase() : "";
                    String type = el.getAttribute("type") != null ? el.getAttribute("type").toLowerCase() : "";
                    boolean readOnly = el.getAttribute("readonly") != null;

                    boolean isDateOrReadonly =
                            readOnly ||
                                    locator.contains("date") ||
                                    locator.contains("time") ||
                                    locator.contains("start") ||
                                    locator.contains("end") ||
                                    elementId.contains("date") ||
                                    elementId.contains("time") ||
                                    classes.contains("date") ||
                                    classes.contains("daterange") ||
                                    type.contains("date") ||
                                    type.contains("datetime");

                    if (isDateOrReadonly) {
                        JavascriptExecutor js = (JavascriptExecutor) driver1;

                        // remove readonly if present
                        js.executeScript("arguments[0].removeAttribute('readonly');", el);

                        // clear + set via JS
                        js.executeScript("arguments[0].value = '';", el);
                        js.executeScript("arguments[0].value = arguments[1];", el, s.getPayload());

                        // trigger UI events
                        js.executeScript(
                                "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                                        "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                                        "arguments[0].dispatchEvent(new Event('blur', { bubbles: true }));",
                                el
                        );

                    } else {
                        el.clear();
                        el.sendKeys(s.getPayload());

                        // Trigger UI events
                        ((JavascriptExecutor) driver1).executeScript(
                                "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));", el);

                        ((JavascriptExecutor) driver1).executeScript(
                                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", el);
                    }
                    // =========================
                    // 🔥 AUTOCOMPLETE HANDLING
                    // =========================

                    boolean isAutoComplete =
                            "combobox".equalsIgnoreCase(el.getAttribute("role")) ||
                                    (el.getAttribute("class") != null &&
                                            el.getAttribute("class").toLowerCase().contains("autocomplete"));

                    if (isAutoComplete) {

                        logger.info("Detected AUTOCOMPLETE field → waiting for suggestions");

                        // wait for dropdown container
                        WebElement container = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.cssSelector(".autocomplete-results"))
                        );

                        // wait for items
                        List<WebElement> options = wait.until(d -> {
                            List<WebElement> list = container.findElements(By.cssSelector(".autocomplete-item"));
                            return list.size() > 0 ? list : null;
                        });

                        logger.info("Selecting first autocomplete suggestion");

                        options.get(0).click();
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
//            case SELECT:
//                if (s.getPayload() != null && !s.getPayload().isBlank()) {
//                    org.openqa.selenium.support.ui.Select sel =
//                            new org.openqa.selenium.support.ui.Select(driver1.findElement(by));
//                    sel.selectByVisibleText(s.getPayload());
//                } else {
//                    logger.info("Skipping SELECT for locator {} because payload is empty", s.getLocator());
//                    throw new RuntimeException("SKIPPED");
//                }
//                break;
            case SELECT:

                if (s.getPayload() == null || s.getPayload().isBlank()) {
                    logger.info("Skipping SELECT for locator {} because payload is empty", s.getLocator());
                    throw new RuntimeException("SKIPPED");
                }

                WebElement selectElement = driver1.findElement(by);

                boolean isSelect2 = selectElement.getAttribute("class") != null &&
                        selectElement.getAttribute("class").contains("select2-hidden-accessible");

                if (!isSelect2) {

                    // Normal HTML select
                    org.openqa.selenium.support.ui.Select sel =
                            new org.openqa.selenium.support.ui.Select(selectElement);

                    sel.selectByVisibleText(s.getPayload());

                } else {

                    logger.info("Detected Select2 dropdown for {}", s.getLocator());

                    String selectId = selectElement.getAttribute("id");

                    WebDriverWait wait = new WebDriverWait(driver1, Duration.ofSeconds(10));

                    // open dropdown
                    WebElement container = driver1.findElement(
                            By.xpath("//select[@id='" + selectId + "']/following-sibling::span")
                    );
                    container.click();

                    try {

                        // -------- FIRST TRY : search based select2 --------
                        logger.info("Trying search-based Select2 selection");

                        WebElement search = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.cssSelector(".select2-search__field"))
                        );

                        search.clear();
                        search.sendKeys(s.getPayload());

                        WebElement option = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.xpath("//li[contains(@class,'select2-results__option') and contains(.,'" + s.getPayload() + "')]")
                                )
                        );

                        option.click();

                    } catch (Exception searchFail) {

                        logger.warn("Search select2 failed, trying direct select. Reason: {}", searchFail.getMessage());

                        // -------- SECOND TRY : static select2 --------
                        WebElement option = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.xpath("//li[contains(@class,'select2-results__option') and contains(.,'" + s.getPayload() + "')]")
                                )
                        );

                        option.click();
                    }
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

    public void runAssertionSteps(WebDriver driver,
                                  List<StepAction> steps ,
                                  Path scenarioDir,
                                  String scenarioPrefix,
                                  List<Scenario> scenarios
    ) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        int tcIdx = 1;

        for (StepAction step : steps) {

            try {
                System.out.println("Executing assertion: " + step);

                switch (step.getType()) {

                    case ASSERT_VISIBLE:
                        assertVisible(driver, wait, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_NOT_VISIBLE:
                        assertNotVisible(driver, wait, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_ELEMENT_PRESENT:
                        assertElementPresent(driver, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_TEXT_EQUALS:
                        assertTextEquals(driver, wait, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_TEXT_CONTAINS:
                        assertTextContains(driver, wait, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_COLUMN_PRESENT:
                        assertAllColumnsPresent(driver, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_COUNT:
                        assertCount(driver, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_SORT_ORDER:
                        assertSorting(driver, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_API_CALLED:
                        assertApiCalled(step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_ATTRIBUTE:
                        assertAttribute(driver, wait, step);
                        step.getAssertion().setAssertResult("Passed");
                        break;

                    case ASSERT_AI:
                        assertAI(driver,wait,step,scenarioPrefix);
//                        step.getAssertion().setAssertResult("Passed");
                        break;
                    case ASSERT_FILTER:
                        assertFilters(driver,wait,step,scenarioPrefix,scenarios);
                        break;
                    case ASSERT_MANAGE_COLUMN:
                        assertManageColumn(driver,wait,step,scenarioPrefix,scenarios);
                        break;


                    default:
                        throw new IllegalArgumentException("Unsupported assertion: " + step.getType());
                }

            } catch (Exception e) {
                step.getAssertion().setAssertResult("FAILED");
                step.getAssertion().setErrorMessage(e.getMessage());

                System.out.println("❌ Assertion failed: " + step.getDescription()
                        + " | Reason: " + e.getMessage());
            }

            // 📸 Take screenshot for BOTH pass & fail
            String screenshotPath = screenshotService.takeScreenshot(driver, String.valueOf(tcIdx),"assert",scenarioDir,scenarioPrefix);
            tcIdx++;
        }
    }
//    private void assertManageColumn(
//            WebDriver driver,
//            WebDriverWait wait,
//            StepAction step,
//            String scenarioPrefix,
//            List<Scenario> scenarios
//    ) throws  Exception{
//        logger.info("Executing assertManageColumn");
//
//        waitForPageStable(driver);
//
//        List<ManageColumnItemDto> manageColumnItems = scenarios.stream()
//                .filter(scenario -> scenario.getType() == ScenarioType.MANAGE_COL_NAV)
//                .findFirst()
//                .map(Scenario::getColumns)
//                .orElseThrow(() ->
//                        new GlobalExceptionHandler.BadRequestException(
//                                "No MANAGE_COLUMN scenario found"
//                        ));
//
//        waitForPageStable(driver);
//
//        Table currTable = tableSawService.extractDataTableToTablesaw(driver, step);
//
//        // Extract actual visible column names from table
//        List<String> actualColumns = currTable.columnNames();
//
//        logger.info("Actual table columns: {}", actualColumns);
//
//        for (ManageColumnItemDto item : manageColumnItems) {
//
//            String expectedColumn =
//                    item.getExtractedName() != null
//                            ? item.getExtractedName()
//                            : item.getColumnName();
//
//            boolean exists = actualColumns.contains(expectedColumn);
//
//            // SHOW => column must exist
//            if (item.getAction() == ManageColumnAction.SHOW) {
//
//                if(!exists){
//                    throw new Exception("Column is not visible");
//                }
//
//                // Validate order if position provided
//                if (item.getPosition() != null) {
//
//                    int actualPosition = actualColumns.indexOf(expectedColumn) + 1;
//
//                    if(actualPosition != item.getPosition()){
//                        throw new Exception("Column position mismatch for: " + expectedColumn);
//                    }
//                }
//            }
//
//            // HIDE => column must NOT exist
//            else if (item.getAction() == ManageColumnAction.HIDE) {
//
//                if(exists){
//                    throw new Exception("Column is visible");
//                }
//            }
//        }
//
//        logger.info("Manage column assertion completed successfully");
//    }

    private void assertManageColumn(
            WebDriver driver,
            WebDriverWait wait,
            StepAction step,
            String scenarioPrefix,
            List<Scenario> scenarios
    ) throws Exception {

        logger.info("===== START : assertManageColumn =====");

        waitForPageStable(driver);

        List<ManageColumnItemDto> manageColumnItems = scenarios.stream()
                .filter(scenario -> scenario.getType() == ScenarioType.MANAGE_COL_NAV)
                .findFirst()
                .map(Scenario::getColumns)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.BadRequestException(
                                "No MANAGE_COLUMN scenario found"
                        ));

        logger.info("Fetched manage column items: {}", manageColumnItems);

        waitForPageStable(driver);

        Table currTable = tableSawService.extractDataTableToTablesaw(driver, step);

        // Extract actual visible column names from table
        List<String> actualColumns = currTable.columnNames();

        logger.info("Actual visible table columns: {}", actualColumns);

        for (ManageColumnItemDto item : manageColumnItems) {

            logger.info("------------------------------------------------");

            logger.info("Validating manage column item: {}", item);

            String expectedColumn =
                    item.getExtractedName() != null
                            ? item.getExtractedName()
                            : item.getColumnName();

            logger.info("Expected column name resolved to: {}", expectedColumn);

            boolean exists = actualColumns.contains(expectedColumn);

            logger.info(
                    "Column [{}] exists in table: {}",
                    expectedColumn,
                    exists
            );

            /*
             * =========================
             * SHOW VALIDATION
             * =========================
             */
            if (item.getAction() == ManageColumnAction.SHOW) {

                logger.info(
                        "Validating SHOW action for column [{}]",
                        expectedColumn
                );

                if (!exists) {

                    logger.error(
                            "SHOW validation failed. Column [{}] not found in visible table columns",
                            expectedColumn
                    );

                    throw new Exception(
                            "Column is not visible: " + expectedColumn
                    );
                }

                logger.info(
                        "SHOW validation passed for column [{}]",
                        expectedColumn
                );

                /*
                 * POSITION VALIDATION
                 */
                if (item.getPosition() != null) {

                    int actualPosition =
                            actualColumns.indexOf(expectedColumn) + 1;

                    logger.info(
                            "Expected position for column [{}] => {}",
                            expectedColumn,
                            item.getPosition()
                    );

                    logger.info(
                            "Actual position for column [{}] => {}",
                            expectedColumn,
                            actualPosition
                    );

                    if (actualPosition != item.getPosition()) {

                        logger.error(
                                "Position validation failed for column [{}]. Expected={}, Actual={}",
                                expectedColumn,
                                item.getPosition(),
                                actualPosition
                        );

                        throw new Exception(
                                "Column position mismatch for: "
                                        + expectedColumn
                                        + " | Expected="
                                        + item.getPosition()
                                        + " | Actual="
                                        + actualPosition
                        );
                    }

                    logger.info(
                            "Position validation passed for column [{}]",
                            expectedColumn
                    );
                }
            }

            /*
             * =========================
             * HIDE VALIDATION
             * =========================
             */
            else if (item.getAction() == ManageColumnAction.HIDE) {

                logger.info(
                        "Validating HIDE action for column [{}]",
                        expectedColumn
                );

                if (exists) {

                    logger.error(
                            "HIDE validation failed. Column [{}] is still visible",
                            expectedColumn
                    );

                    throw new Exception(
                            "Column is visible: " + expectedColumn
                    );
                }

                logger.info(
                        "HIDE validation passed for column [{}]",
                        expectedColumn
                );
            }
        }

        logger.info("All manage column assertions passed successfully");

        logger.info("===== END : assertManageColumn =====");
    }
    private void assertFilters(WebDriver driver,
                               WebDriverWait wait,
                               StepAction step,
                               String scenarioPrefix,
                               List<Scenario> scenarios) {
        logger.info("Executing assertFilter");
        waitForPageStable(driver);
        List<FilterScenarioDto> filters = scenarios.stream()
                .filter(scenario -> scenario.getType() == ScenarioType.FILTER_NAV)
                .findFirst()
                .map(Scenario::getFilters)
                .orElseThrow(() -> new GlobalExceptionHandler.BadRequestException("No FILTER_NAV scenario found"));
        assertTableFilter(driver,wait,step,scenarioPrefix,filters);
    }

    private void assertTableFilter(WebDriver driver,
                                   WebDriverWait wait,
                                   StepAction step,
                                   String scenarioPrefix,
                                   List<FilterScenarioDto> scenarios) {

        waitForPageStable(driver);

        Table currTable = tableSawService.extractDataTableToTablesaw(driver, step);

        TableFilterExpression expression =
                TableFilterExpression.compile(scenarioPrefix, currTable, scenarios);

        if (expression.matchesAllRows()) {
            step.getAssertion().setAssertResult("Passed");
        } else {
            throw new GlobalExceptionHandler.BadRequestException(
                    "Assertion Failed. Expression: " + expression.getDebugExpression()
            );
        }
    }

    private void assertAI(WebDriver driver, WebDriverWait wait, StepAction step,String scenarioPrefix) {
        try {
            logger.info("Running AI assertion for step: {}", step.getType());
            Path screenshotDir = Paths.get(resultsBaseDir, scenarioPrefix, "ai");

            int []stepCtr=new int[1];
            stepCtr[0]++;
            // Wait for page to stabilize
            waitForPageStable(driver);

            List<File> screenshots = new ArrayList<>();

            // =========================================================
            // 1) Capture WHOLE PAGE first (main window scrolling)
            // =========================================================
            screenshots.addAll(aiScreenshotService.captureFullPage(driver,scenarioPrefix,screenshotDir,stepCtr));


            // Reset page to top before container capture
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
            Thread.sleep(500);

            // =========================================================
            // 1) Find scrollable container (table/grid section)
            // =========================================================
            WebElement scrollableContainer = findMainScrollableContainer(driver);

            if (scrollableContainer != null) {

                // -----------------------------------------------------
                // 2a) Capture scrollable section HEADER first
                //     (all horizontal columns, sorting visibility)
                // -----------------------------------------------------
                screenshots.addAll(aiScreenshotService.captureHeaderAcrossAllColumns(driver, scrollableContainer,scenarioPrefix,screenshotDir,stepCtr));

                // -----------------------------------------------------
                // 2b) Capture scrollable section BODY next
                //     (all rows + all columns)
                // -----------------------------------------------------
                screenshots.addAll(aiScreenshotService.captureScrollableElementScreenshots(driver, scrollableContainer,scenarioPrefix,screenshotDir,stepCtr));
            } else {
                logger.warn("No scrollable container found. Proceeding with full-page screenshots only.");
            }

            if (screenshots.isEmpty()) {
                throw new GlobalExceptionHandler.BadRequestException("AI assertion failed: No screenshots captured.");
            }
            logger.info("Length of screenshot list: {}", screenshots.size());
            // =========================================================
            // 3) Build prompt
            // =========================================================
            String prompt = PromptBuilder.buildAIPromptOfStep(step);

            // =========================================================
            // 4) Send screenshots + prompt to LLM
            // =========================================================
//            logger.info("User prompt : {}",prompt);
            AIValidationResult result = lLMServices.analyzeScreenshots(prompt, screenshots);
            String runId = AIScreenshotService.getRunId(); // expose getter

            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "ai-assert-screenshots", runId);

            try {
                if (Files.exists(dir)) {
                    Files.walk(dir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            } catch (Exception e) {
                logger.warn("Failed to clean temp screenshots", e);
            }

// 🔥 Prevent memory leak in thread pools
            AIScreenshotService.clearRunId();

            logger.info("AI assertion response: {}", result);
//            step.setDescription(result.getReason());

            step.getAssertion().setReason(result.getReason());
            if (result.getStatus()== AIValidationResult.AssertStatus.FAILED) {
                throw new GlobalExceptionHandler.BadRequestException("AI assertion failed: " + result.getReason());
            }
            step.getAssertion().setAssertResult(result.getStatus().toString());


            logger.info("AI assertion passed for step: {}", step.getType());

        } catch (AssertionError ae) {
            logger.error("AI assertion failed for step {}: {}", step.getType(), ae.getMessage(), ae);
            throw ae;
        } catch (Exception e) {
            logger.error("AI assertion error in step {}: {}", step.getType(), e.getMessage(), e);
            throw new RuntimeException("AI assertion failed for step: " + step.getType(), e);
        }
    }
    public static WebElement findMainScrollableContainer(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Object result = js.executeScript("""
        const elements = Array.from(document.querySelectorAll('*'));

        function isVisible(el) {
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return rect.width > 200 &&
                   rect.height > 150 &&
                   style.display !== 'none' &&
                   style.visibility !== 'hidden' &&
                   style.opacity !== '0';
        }

        function isScrollable(el) {
            const style = window.getComputedStyle(el);
            const overflowY = style.overflowY;
            const overflowX = style.overflowX;

            const vertical = (overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight;
            const horizontal = (overflowX === 'auto' || overflowX === 'scroll') && el.scrollWidth > el.clientWidth;

            return vertical || horizontal;
        }

        const candidates = elements.filter(el => isVisible(el) && isScrollable(el));

        if (!candidates.length) return null;

        candidates.sort((a, b) => {
            const areaA = a.clientWidth * a.clientHeight;
            const areaB = b.clientWidth * b.clientHeight;
            return areaB - areaA;
        });

        return candidates[0];
    """);

        return (WebElement) result;
    }
    private void waitForPageStable(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            for (int i = 0; i < 10; i++) {
                String readyState = (String) js.executeScript("return document.readyState");
                if ("complete".equals(readyState)) {
                    break;
                }
                Thread.sleep(500);
            }

            // optional small delay for dynamic UI rendering
            Thread.sleep(1000);

        } catch (Exception e) {
            logger.warn("Could not fully verify page stability, continuing anyway.");
        }
    }
    private void assertElementPresent(WebDriver driver, StepAction step) {

        List<WebElement> elements = driver.findElements(getBy(step));

        if (elements == null || elements.isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException("Element not present in DOM: " + step.getLocator());
        }
    }
    private void assertVisible(WebDriver driver, WebDriverWait wait, StepAction step) {

        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(
                getBy(step)
        ));

        if (!el.isDisplayed()) {
            throw new GlobalExceptionHandler.BadRequestException("Element not visible: " + step.getLocator());
        }
    }
    private void assertNotVisible(WebDriver driver, WebDriverWait wait, StepAction step) {

        boolean invisible = wait.until(
                ExpectedConditions.invisibilityOfElementLocated(getBy(step))
        );

        if (!invisible) {
            throw new GlobalExceptionHandler.BadRequestException("Element is visible but should not be");
        }
    }

    private void assertTextEquals(WebDriver driver, WebDriverWait wait, StepAction step) {

        logger.info("assertTextEquals table Id : {}", step.getTableId());
        logger.info("assertTextEquals column name : {}", step.getColName());
        logger.info("assertTextEquals expectedValue : {}", step.getPayload());

        String tableLocator = step.getTableId();
        String colName = step.getColName();
        String expectedPayload = step.getPayload(); // assume comma-separated

        logger.info("---- Text Equals Assertion Started ----");

        WebElement table = driver.findElement(By.cssSelector(tableLocator));

        // STEP 1: Fetch headers (same as sorting)
        List<WebElement> headers = driver.findElements(By.cssSelector(".dataTables_scrollHead th"));

        List<String> actualColumns = headers.stream()
                .map(h -> {
                    String text = "";
                    try {
                        text = h.findElement(By.cssSelector("div")).getText().trim();
                    } catch (Exception e) {
                        text = h.getText().trim();
                    }
                    if (text.isEmpty()) {
                        text = h.getAttribute("aria-label");
                    }
                    if (text != null && text.contains(":")) {
                        text = text.split(":")[0].trim();
                    }
                    return text != null ? text : "";
                })
                .filter(s -> !s.isEmpty())
                .toList();

        logger.info("Actual columns from UI: {}", actualColumns);

        // STEP 2: Find column index
        int columnIndex = -1;
        for (int i = 0; i < actualColumns.size(); i++) {
            if (actualColumns.get(i).equalsIgnoreCase(colName)) {
                columnIndex = i;
                break;
            }
        }

        logger.info("---- found column index ---- {}", columnIndex);

        if (columnIndex == -1) {
            throw new RuntimeException("Column not found: " + colName);
        }

        // STEP 3: Fetch all row values for that column
        List<WebElement> rows = driver.findElements(
                By.cssSelector(tableLocator + " tbody tr")
        );

        Set<String> actualSet = new HashSet<>();

        for (WebElement row : rows) {
            List<WebElement> cols = row.findElements(By.tagName("td"));

            if (cols.size() > columnIndex) {
                actualSet.add(cols.get(columnIndex).getText().trim());
            }
        }

        logger.info("Actual values (Set): {}", actualSet);

        // STEP 4: Parse expected values
        Set<String> expectedSet = stream(expectedPayload.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        logger.info("Expected values (Set): {}", expectedSet);

        // STEP 5: Compare sets
        if (!actualSet.equals(expectedSet)) {

            logger.info("❌ Text assertion failed");

            // Log missing and extra values
            Set<String> missing = new HashSet<>(expectedSet);
            missing.removeAll(actualSet);

            Set<String> extra = new HashSet<>(actualSet);
            extra.removeAll(expectedSet);

            logger.info("Missing values: {}", missing);
            logger.info("Unexpected extra values: {}", extra);

            throw new GlobalExceptionHandler.BadRequestException("Text values do not match expected set");
        }

        logger.info("✅ Text assertion passed");
        logger.info("---- Text Equals Assertion Completed ----");
    }

    private void assertTextContains(WebDriver driver, WebDriverWait wait, StepAction step) {

        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(
                getBy(step)
        ));

        String actual = el.getText();

        if (!actual.contains(step.getPayload())) {
            throw new GlobalExceptionHandler.BadRequestException("Text does not contain: " + step.getPayload());
        }
    }

    private void assertColumnPresent(WebDriver driver, StepAction step) {

        List<WebElement> headers = driver.findElements(By.cssSelector("th"));
        logger.info("headers {}",headers);

        boolean found = headers.stream()
                .anyMatch(h -> h.getText().trim()
                        .equalsIgnoreCase(step.getPayload()));


        logger.info("found {}",found);

        if (!found) {
            throw new GlobalExceptionHandler.BadRequestException("Column not found: " + step.getPayload());
        }
    }
    private void assertAllColumnsPresent(WebDriver driver, StepAction step) {

        // Expected columns → comma separated
        String columnNames = step.getColName();
        logger.info("Received column payload: {}", columnNames);

        List<String> expectedColumns = stream(columnNames.split(","))
                .map(String::trim)
                .toList();

        logger.info("Parsed expected columns: {}", expectedColumns);

        WebElement table = driver.findElement(By.cssSelector(step.getTableId()));

        List<WebElement> headers = table.findElements(By.cssSelector("thead tr th"));

        // Get actual headers
//        List<WebElement> headers = driver.findElements(By.cssSelector("th"));
        logger.info("Total headers found on page: {}", headers.size());

        List<String> actualColumns = headers.stream()
                .map(h -> {
                    String text = "";
                    try {
                        text = h.findElement(By.cssSelector("div")).getText().trim();
                    } catch (Exception e) {
                        text = h.getText().trim();
                    }
                    if (text.isEmpty()) {
                        text = h.getAttribute("aria-label");
                    }
                    // clean unwanted aria suffix
                    if (text != null && text.contains(":")) {
                        text = text.split(":")[0].trim();
                    }
                    return text != null ? text : "";
                })
                .filter(s -> !s.isEmpty())
                .toList();

        logger.info("Actual columns from UI: {}", actualColumns);

        // Check each expected column
        List<String> missingColumns = new ArrayList<>();

        for (String expected : expectedColumns) {
            boolean found = actualColumns.stream()
                    .anyMatch(a -> a.equalsIgnoreCase(expected));

            if (found) {
                logger.info("✅ Column FOUND: {}", expected);
            } else {
                logger.warn("❌ Column MISSING: {}", expected);
                missingColumns.add(expected);
            }
        }

        // Final assertion
        if (!missingColumns.isEmpty()) {
            logger.error("Assertion failed. Missing columns: {} | Actual columns: {}",
                    missingColumns, actualColumns);

            throw new GlobalExceptionHandler.BadRequestException("Missing columns: " + missingColumns +
                    " | Actual: " + actualColumns);
        }

        logger.info("✅ All expected columns are present.");
    }

    private String assertCount(WebDriver driver, StepAction step) {

        logger.info("🔍 Starting ASSERT_COUNT validation...");

        String rowsQuery = step.getRowsBtn();
        String tableId = step.getTableId();
//        String rangeId = step.getRangeId();

        try {
            int selectedPageSize = getSelectedPageSize(driver, rowsQuery);
            int visibleRowCount = getVisibleRowCount(driver, tableId);

//            logger.info("📊 Selected page size: {}", selectedPageSize);
//            logger.info("📊 Visible row count: {}", visibleRowCount);

            if (selectedPageSize == visibleRowCount) {
                logger.info("✅ PASS: Page size matches visible rows");
            } else if (selectedPageSize > visibleRowCount) {

                int totalPages = getPaginationCount(driver);

                logger.info("📄 Total pages detected: {}", totalPages);

                if (totalPages == 1) {
                    logger.info("✅ PASS: Single page with fewer rows than page size");
                } else {
                    logger.error("❌ FAIL: Multiple pages exist but rows < page size");
                    throw new GlobalExceptionHandler.BadRequestException("Pagination mismatch detected");
                }

            } else {
                logger.error("❌ FAIL: Visible rows exceed selected page size");
                throw new GlobalExceptionHandler.BadRequestException("Row count exceeds page size");
            }
            return "passed";

        } catch (Exception e) {
            logger.error("❌ Exception in ASSERT_COUNT: {}", e.getMessage(), e);
            throw new GlobalExceptionHandler.BadRequestException("ASSERT_COUNT failed"+e);
        }


    }
    private void validateElementCount(WebDriver driver, StepAction step) {

        List<WebElement> elements = driver.findElements(getBy(step));
        int expected = Integer.parseInt(step.getPayload());

        logger.info("🔢 Expected elements: {}, Found: {}", expected, elements.size());

        if (elements.size() != expected) {
            throw new GlobalExceptionHandler.BadRequestException(
                    "Expected count: " + expected + " but got: " + elements.size()
            );
        }

        logger.info("✅ Element count assertion passed");
    }
    private int getPaginationCount(WebDriver driver) {

        WebElement ul = driver.findElement(By.cssSelector(".pagination"));

        List<WebElement> pages = ul.findElements(
                By.cssSelector("li.paginate_button:not(.previous):not(.next)")
        );

        int count = pages.size();

        logger.debug("📄 Pagination buttons (excluding prev/next): {}", count);

        return count;
    }
    private int getVisibleRowCount(WebDriver driver, String tableId) {

        List<WebElement> rows = driver.findElements(
                By.cssSelector(tableId + " tbody tr")
        );

        int count = rows.size();

        logger.debug("📋 Visible rows in table: {}", count);

        return count;
    }
    private int getSelectedPageSize(WebDriver driver, String rowsQuery) {

        WebElement btn = driver.findElement(By.cssSelector(rowsQuery));
        btn.click();

        WebElement active = driver.findElement(
                By.cssSelector(".dt-button-collection .button-page-length.active span")
        );

        int value = Integer.parseInt(active.getText().trim());

        logger.debug("🔘 Active page size from dropdown: {}", value);

        return value;
    }

    private void assertSorting(WebDriver driver, StepAction step) {
//        member-list-table
        String tableLocator = step.getTableId();
        logger.info("---- tableLocator {}----",tableLocator);
        String colName = step.getColName();
        logger.info("---- colName {}----",colName);
        String order = step.getOrder();
        logger.info("---- order {}----",order);
        logger.info("---- Sorting Assertion Started ----");
        WebElement table = driver.findElement(By.cssSelector(tableLocator));

        logger.info("---- table {} ----",table);
        List<WebElement> headers = driver.findElements(By.cssSelector(".dataTables_scrollHead th"));
        logger.info("---- found headers ---- {}",headers);
        List<String> actualColumns = headers.stream()
                .map(h -> {
                    String text = "";
                    try {
                        text = h.findElement(By.cssSelector("div")).getText().trim();
                    } catch (Exception e) {
                        text = h.getText().trim();
                    }
                    if (text.isEmpty()) {
                        text = h.getAttribute("aria-label");
                    }
                    // clean unwanted aria suffix
                    if (text != null && text.contains(":")) {
                        text = text.split(":")[0].trim();
                    }
                    return text != null ? text : "";
                })
                .filter(s -> !s.isEmpty())
                .toList();

        logger.info("Actual columns from UI: {}", actualColumns);


//        List<WebElement> elements = driver.findElements(getBy(step));
//        logger.info("Total elements found: {}", elements.size());
        int columnIndex=-1;
        for (int i = 0; i < actualColumns.size(); i++) {
            String text = actualColumns.get(i);

            if (text.equalsIgnoreCase(colName)) {
                columnIndex = i;
                break;
            }
        }
        logger.info("---- found column index ---- {}",columnIndex);
        if (columnIndex == -1) {
            throw new RuntimeException("Column not found: " + colName);
        }
        List<WebElement> rows = driver.findElements(
                By.cssSelector(tableLocator+" tbody tr")
        );

        List<String> values = new ArrayList<>();

        for (WebElement row : rows) {
            List<WebElement> cols = row.findElements(By.tagName("td"));

            if (cols.size() > columnIndex) {
                values.add(cols.get(columnIndex).getText().trim());
            }
        }

        System.out.println("Column Values: " + values);

//        List<String> actual = elements.stream()
//                .map(e -> e.getText().trim())
//                .toList();

        logger.info("Actual list: {}", values);

        List<String> sorted = new ArrayList<>(values);

        if ("descending".equalsIgnoreCase(order)) {
            logger.info("Sorting order: DESCENDING");
            sorted.sort(Collections.reverseOrder());
        } else {
            logger.info("Sorting order: ASCENDING");
            sorted.sort(String::compareTo);
        }

        logger.info("Expected sorted list: {}", sorted);

        if (!values.equals(sorted)) {
            logger.info("❌ Sorting is incorrect");

            // Optional: log mismatch details
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).equals(sorted.get(i))) {
                    logger.info("Mismatch at index {}: actual='{}', expected='{}'",
                            i, values.get(i), sorted.get(i));
                }
            }

            throw  new GlobalExceptionHandler.BadRequestException("Sorting is incorrect");
        }

        logger.info("✅ Sorting is correct");
        logger.info("---- Sorting Assertion Completed ----");
    }

    private void assertAttribute(WebDriver driver, WebDriverWait wait, StepAction step) {

        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(
                getBy(step)
        ));

        //need to look out
        String actual = el.getAttribute(step.getPayload());

        if (!actual.equals(step.getPayload())) {
            throw new GlobalExceptionHandler.BadRequestException("Attribute mismatch");
        }
    }

    private void assertApiCalled(StepAction step) {

        // TODO: integrate DevTools later
        System.out.println("API assertion placeholder: " + step.getPayload());
    }

    private By getBy(StepAction step) {

        logger.info("Resolving locator...");
        logger.info("Locator value: {}", step.getLocator());

        if(step.getLocator()!=null){
            return By.cssSelector(step.getLocator());
        }
        logger.info("❌ Invalid locator type: {}", step.getLocatorType());
        throw new IllegalArgumentException("Invalid locator type");
    }
}