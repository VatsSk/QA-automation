package com.testingautomation.testautomation.services.orchestratorService;


import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.enums.DateSelectionType;
import com.testingautomation.testautomation.enums.DateSelectionType;
import com.testingautomation.testautomation.enums.ManageColumnAction;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.enums.ScenarioType;
import com.testingautomation.testautomation.services.executorService.SeleniumExecutor;
import com.testingautomation.testautomation.services.fallback.FallbackExecutor;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import com.testingautomation.testautomation.services.stepGeneratorService.StepGenerator;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.csvLoaderService.CsvTestCaseLoader;
import com.testingautomation.testautomation.entities.*;
import com.testingautomation.testautomation.repositories.runRepos.RunRepository;
import com.testingautomation.testautomation.services.UiScannerService;
import com.testingautomation.testautomation.services.stepGeneratorService.AssertionStepGenerator;
import com.testingautomation.testautomation.services.s3Service.S3StorageService;
import com.testingautomation.testautomation.services.screenShotsService.ScreenshotService;
import com.testingautomation.testautomation.utils.TimestampUtil;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.testingautomation.testautomation.utils.ExceptionUtil.getUserFriendlyErrorMessage;
import static com.testingautomation.testautomation.globalException.GlobalExceptionHandler.*;

@Service
@RequiredArgsConstructor
public class ScenarioOrchestratorService {
    private final String resultsBaseDir = "test-results";
    private final ScreenshotService screenshotService;
    private final AssertionStepGenerator assertionStepGenerator;
    @Value("${storage.s3.base-prefix}")
    private  String basePrefix;
    @Value("${storage.s3.bucket-name}")
    private String bucket;
    private final Logger logger = LoggerFactory.getLogger(ScenarioOrchestratorService.class);

    // your existing components (assumed to be available)
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;
    private final RunRepository runRepository;
    private final MongoTemplate mongoTemplate;
    private final S3StorageService s3StorageService;
    private final StorageService storageService;


    /**
     * Top-level: execute the list of scenarios in sequence (one by one).
     * Keeps single driver/session alive (login should be done before calling this).
     */
    public Run executeScenarios(Run run, WebDriver driver, String globalRunId) {
        String baseS3Prefix =basePrefix+"/"+ run.getProjectId()+ "/" + run.getModuleId() + "/" + globalRunId;

        //deleting existing objects from the s3 for run
        if (storageService.doesPrefixHaveObjects(bucket, baseS3Prefix)) {
            storageService.deleteFolder(bucket, baseS3Prefix);
        }

        List<Scenario> scenarios = run.getScenariosList();

        logger.info("[{}] Executing {} scenarios sequentially", globalRunId, scenarios.size());
        Map<String, List<TestCaseDTO>> scenarioResultsMap = new LinkedHashMap<>();
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario current = scenarios.get(i);
            String scenarioId = (i+1)+"";
            String scenarioPrefix =
                    baseS3Prefix + "/" + scenarioId;
            try {
                if (current.getType() == ScenarioType.URL) {
                    // check next scenario
                    runUrlGeneric(
                            driver,
                            current,
                            run.getResultStatement(),
                            scenarioPrefix,
                            scenarioResultsMap,
                            scenarios.size(),
                            i
                    );
                    current.setScenarioBasePath(scenarioPrefix);
                }else if(current.getType() == ScenarioType.VERIFY_PAGE){
                    runVerifyPageGenric(
                            driver,
                            current,
                            baseS3Prefix,
                            scenarioResultsMap
                    );
                }
                else{
                    runModalGeneric(
                            driver,
                            scenarios,
                            run.getResultStatement(),
                            i,
                            baseS3Prefix,
                            run,
                            scenarioResultsMap

                    );
//                    System.out.println(scenarioResultsMap);
//                    current.setScenarioStatus(scenarioTestDto.getOverAllScenarioStatus());
                    break;
                }

            }catch (ScenarioExecutionException e){
                current.setScenarioStatus(RunStatus.FAILED);
                throw e;
            }

        }

        logger.info("execution completed");
        s3StorageService.writeAndUploadScenarioCsvs(scenarioResultsMap,run);
        runRepository.save(run);
        return run;
    }

    private void runVerifyPageGenric(WebDriver driver, Scenario current, String baseS3Prefix,Map<String, List<TestCaseDTO>> scenarioResultsMap) {
        logger.info("Executing VERIFY_PAGE scenario - URL: {}, CSS Selector: {}",
                current.getUrl(), current.getCssOpener());

        String scenarioPrefix = baseS3Prefix + "/" + current.getSequenceNo();
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);

        try {
            Files.createDirectories(scenarioDir);
        } catch (IOException e) {
            logger.error("Failed to create scenario directory: {}", scenarioPrefix, e);
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        List<TestCaseDTO> testCases = new ArrayList<>();
        TestCaseDTO verifyResult = new TestCaseDTO("1", new HashMap<>());
        try {
            // Navigate to the URL
            logger.info("Navigating to URL: {}", current.getUrl());
            driver.get(current.getUrl());

            // Take screenshot after navigation
            String navScreenshot = screenshotService.takeScreenshot(
                    driver,
                    "1",
                    "step "+1,
                    scenarioDir,
                    scenarioPrefix
            );
            logger.info("Navigation screenshot taken: {}", navScreenshot);

            // Check if CSS selector exists in DOM
            logger.info("Checking if element exists with CSS selector: {}", current.getCssOpener());

            try {
                WebElement verifyElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector(current.getCssOpener())
                ));

                // Check if element is visible
                boolean isVisible = verifyElement.isDisplayed();
                logger.info("VERIFY_PAGE element found - tag: {}, visible: {}, text: '{}'",
                        verifyElement.getTagName(), isVisible, verifyElement.getText());

                // Take screenshot after verification
                String verifyScreenshot = screenshotService.takeScreenshot(
                        driver,
                        "1",
                        "step "+2,
                        scenarioDir,
                        scenarioPrefix
                );
                logger.info("Verification screenshot taken: {}", verifyScreenshot);

                if (isVisible) {
                    verifyResult.setResult("Passed");
                    logger.info("VERIFY_PAGE scenario PASSED - element is visible");
                } else {
                    verifyResult.setResult("Failed - Element exists but not visible");
                    logger.warn("VERIFY_PAGE scenario FAILED - element exists but not visible");
                }

            } catch (GlobalExceptionHandler.TimeoutException e) {
                logger.error("VERIFY_PAGE FAILED - element not found with selector: {}", current.getCssOpener());
                verifyResult.setResult("Failed - Element not found: " + current.getCssOpener());

                // Take screenshot of failure
                String failScreenshot = screenshotService.takeScreenshot(
                        driver,
                        "1",
                        "step "+ 3,
                        scenarioDir,
                        scenarioPrefix
                );
                logger.info("Failure screenshot taken: {}", failScreenshot);
            }

        } catch (Exception e) {
            logger.error("VERIFY_PAGE scenario failed with unexpected error", e);
            verifyResult.setResult("Failed - " + e.getMessage());
        }
        testCases.add(verifyResult);
        scenarioResultsMap.put(scenarioPrefix, new ArrayList<>(testCases));
        logger.info("[{}] Stored VERIFY_PAGE testcase(s) in scenarioResultsMap. Count={}",
                scenarioPrefix, testCases.size());
        current.setScenarioBasePath(scenarioPrefix);
        if (verifyResult.getResult().equals("Passed")) {
            current.setScenarioStatus(RunStatus.PASSED);
        } else {
            current.setScenarioStatus(RunStatus.FAILED);
        }
    }


    /**
     * Generic URL method:
     * - scan the page at url
     * - load testcases from csvPath
     * - loop over each testcase, generate steps and execute using executor.run(...)
     */
    public void runUrlGeneric(WebDriver driver,Scenario current,String successMsg,String scenarioPrefix
            , Map<String, List<TestCaseDTO>> scenarioResultsMap,int scenarioSize,int currScenarioIdx) {
        List<FieldDescriptor> fields = scannerService.scanPage(current.getUrl(), driver);
        logger.info("$$$$$$$$ CURRENT CSV FILEEE $$$$$$$$"+current.getCsv());
        List<TestCaseDTO> testCases=null;
        try {
            testCases = csvLoader.loadFromS3(current.getCsv());
        }catch (Exception e) {
            logger.error("Error while loading testcases from s3", e);
            throw new ScenarioExecutionException(
                    currScenarioIdx,
                    current.getType(),
                    "UNABLE_TO_LOAD_CSV_FILE",
                    "Unable to laod csv file ",
                    e
            );
        }
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        try {
            Files.createDirectories(scenarioDir);
        } catch (IOException e) {
            logger.error("Failed to create scenario directory: {}", scenarioDir, e);
        }

        int totalPasses = 0;
        int totalFails = 0;

        // 3) for each testcase -> generate steps & run
        for (TestCaseDTO tc : testCases) {
            System.out.println("Test case "+ tc);

            String tcRunId =tc.getTestcaseId();
            try {
                logger.info("[{}] Generating steps for testcase {}", tcRunId, tc.getTestcaseId());
                List<StepAction> steps = stepGenerator.generateSteps(fields, tc);
                logger.info("generated steps are : {}",steps);
                logger.info("[{}] Executing {} steps", tcRunId, steps.size());
                String expected = tc.getExpectedResult();
                logger.info("EXPECTED results are : {}",expected);
                ResultRun runResult =executor.run(driver, current.getUrl(), steps, tcRunId,successMsg,scenarioDir,scenarioPrefix,expected,scenarioSize,currScenarioIdx);
                if (expected != null) {
                    if(expected.equalsIgnoreCase(runResult.getStatus()) ){
                        tc.setResult("Passed");
                        totalPasses++;
                    }else{
                        tc.setResult(runResult.getStatus());
                        totalFails++;
                    }
                }else{
                    tc.setResult("Expected result not given !");
                    totalPasses++;
                }
                tc.setUrls(runResult.getScreenshots());
                logger.info("[{}] Completed testcase {}", tcRunId, tc);
            } catch (Exception e) {
                logger.error("[{}] testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
            }
        }
        scenarioResultsMap.put(scenarioPrefix, new ArrayList<>(testCases));

        logger.info("[{}] Stored {} URL testcases in scenarioResultsMap",
                scenarioPrefix, testCases.size());
        logger.info("Total passed {} and Total failed {} testcase size {}", totalPasses, totalFails,testCases.size());
        if (totalPasses == testCases.size()) {
            current.setScenarioStatus(RunStatus.PASSED);
        }
        else if (totalFails == testCases.size()) {
            current.setScenarioStatus(RunStatus.FAILED);
        }
        else {
            current.setScenarioStatus(RunStatus.PARTIAL);

        }
    }

    private int executeScenarioByType(
            WebDriver driver,
            WebDriverWait wait,
            Run run,
            Scenario currScenario,
            Scenario scenario,
            TestCaseDTO resultTestCase,
            int currIdx,
            int modalFormTcIdx,
            Path navigationScreenshotDir,
            String scenarioPrefix
    ) {
        try {
            return switch (currScenario.getType()) {
                case URL_NAV -> {
                    urlNavigation(
                            currScenario,
                            scenario,
                            driver,
                            modalFormTcIdx,
                            navigationScreenshotDir,
                            scenarioPrefix,
                            resultTestCase
                    );
                    yield currIdx;
                }
                case MODAL_NAV -> {
                    handleModalNav(
                            driver,
                            currScenario,
                            scenario,
                            resultTestCase,
                            modalFormTcIdx,
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                    yield currIdx;
                }
                case SEARCH_NAV -> handleSearchNavScenario(
                        driver,
                        wait,
                        currScenario,
                        scenario,
                        resultTestCase,
                        currIdx,
                        modalFormTcIdx,
                        navigationScreenshotDir,
                        scenarioPrefix
                );
                case FORM_MODAL -> {
                    handleFormModal(
                            currScenario,
                            driver,
                            resultTestCase,
                            scenarioPrefix,
                            navigationScreenshotDir,
                            scenario,
                            modalFormTcIdx
                    );
                    yield currIdx;
                }
                case FILTER_NAV -> {
                    handleFilterNavScenario(driver, wait, currScenario, scenario, resultTestCase);
                    yield currIdx;
                }
                case DATE_RANGE_NAV -> {
                    handleDateRangeNav(
                            driver,
                            wait,
                            currScenario,
                            scenario,
                            resultTestCase,
                            currIdx,
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                    yield currIdx;
                }
                case MANAGE_COL_NAV -> {
                    handleManageColumnScenario(currIdx, driver, wait, currScenario);
                    yield currIdx;
                }
                case ROW_COUNT_NAV -> {
                    handleRowCountNav(
                            driver,
                            wait,
                            currScenario,
                            scenario,
                            resultTestCase,
                            currIdx,
                            modalFormTcIdx,
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                    yield currIdx;
                }
                default -> throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_SCENARIO_TYPE",
                        "Unsupported scenario type: " + currScenario.getType(),
                        null
                );
            };
        }catch(ScenarioExecutionException e) {
            throw e;
        }
    }



    public int handleNavigation(WebDriver driver, List<Scenario> scenarios, int currIdx, int modalFormTcIdx, String baseS3Prefix, Run run,
                                Map<String, List<TestCaseDTO>> scenarioResultsMap) {

        logger.info("Starting navigation handling from index {}", currIdx);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Path navigationScreenshotDir =
                Paths.get(resultsBaseDir, baseS3Prefix, "navigation", "screenshots");

        try {
            Files.createDirectories(navigationScreenshotDir);
        } catch (IOException e) {
            logger.error("Failed creating navigation screenshot directory", e);
        }

        while (currIdx < scenarios.size()) {
            String scenarioId = (currIdx+1)+"";
            String scenarioPrefix =
                    baseS3Prefix + "/" + scenarioId;

            Scenario scenario = run.getScenariosList().get(currIdx);
            scenario.setScenarioBasePath(scenarioPrefix);
            run.getScenariosList().set(currIdx,scenario);
            // single generic testcase for this navigation scenario
            TestCaseDTO resultTestCase = new TestCaseDTO((modalFormTcIdx+1)+"", new HashMap<>());
            resultTestCase.setExpectedResult("Passed");
            Scenario currScenario = scenarios.get(currIdx);

            logger.info("Processing scenario index {} type {}", currIdx, currScenario.getType());

            if (currScenario.getType() == ScenarioType.MODAL) {
                logger.info("Reached MODAL scenario at index {}, stopping navigation phase", currIdx);
                return currIdx;
            }
            else if (currScenario.getType() == ScenarioType.ASSERT) {
                logger.info("Reached Assert scenario at index {}, stopping navigation phase", currIdx);
                return currIdx;
            }

            try {
                currIdx = executeScenarioByType(
                        driver,
                        wait,
                        run,
                        currScenario,
                        scenario,
                        resultTestCase,
                        currIdx,
                        modalFormTcIdx,
                        navigationScreenshotDir,
                        scenarioPrefix
                );
            }
            catch (ScenarioExecutionException ex) {
                scenario.setScenarioStatus(RunStatus.FAILED);
                resultTestCase.setResult("Failed "+ex.getMessage());

                logger.error(
                        "Scenario stopped at index {} type {}",
                        currIdx,
                        currScenario.getType(),
                        ex.getMessage().split("\n")[0]
                );

                screenshotService.takeScreenshot(driver,(modalFormTcIdx+1)+"","error",navigationScreenshotDir,scenarioPrefix);
                throw ex;
            }
                scenarioResultsMap.computeIfAbsent(scenarioPrefix, k -> new ArrayList<>())
                        .add(resultTestCase);

                logger.info("Stored testcase {} in scenarioResultsMap for scenarioPrefix {}. Current count={}",
                        resultTestCase.getTestcaseId(),
                        scenarioPrefix,
                        scenarioResultsMap.get(scenarioPrefix).size());
                logger.info("Current status of map: {}",scenarioResultsMap.get(scenarioPrefix));

            run.getScenariosList().set(currIdx, scenario);

            currIdx++;
        }

        logger.info("Navigation phase completed. Final index {}", currIdx);

        return currIdx;
    }

    private void handleRowCountNav(
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario,
            Scenario scenario,
            TestCaseDTO resultTestCase,
            int currIdx,
            int modalFormTcIdx,
            Path navigationScreenshotDir,
            String scenarioPrefix
    ) {

        final String step = "ROW_COUNT_NAV";

        try {

            // =====================================================
            // VALIDATE CONFIG
            // =====================================================

            String openerSelector = currScenario.getCssOpener();

            if (openerSelector == null || openerSelector.isBlank()) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_ROW_COUNT_SELECTOR",
                        "Row count navigation failed because opener selector is missing",
                        null
                );
            }

            if (currScenario.getValue() == null || currScenario.getValue().isBlank()) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_ROW_COUNT_VALUE",
                        "Row count navigation failed because row count value is missing",
                        null
                );
            }

            int rows;
            try {
                rows = Integer.parseInt(currScenario.getValue().trim());
            }
            catch (NumberFormatException ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "PARSE_ROW_COUNT_VALUE",
                        String.format(
                                "Row count navigation failed because value '%s' is not a valid integer",
                                currScenario.getValue()
                        ),
                        ex
                );
            }

            By openerBy = By.cssSelector(openerSelector);

            // =====================================================
            // OPEN ROW COUNT DROPDOWN
            // =====================================================

            boolean clicked = false;

            try {
                safeClick(driver, openerBy);
                clicked = true;
                logger.info("Row count dropdown opened using safeClick");
            }
            catch (Exception safeEx) {
                logger.warn(
                        "safeClick failed for row count dropdown, trying smartClick. Reason={}",
                        safeEx.getMessage()
                );
            }

            if (!clicked) {
                try {
                    smartClick(driver, openerBy);
                    clicked = true;
                    logger.info("Row count dropdown opened using smartClick");
                }
                catch (Exception smartEx) {
                    logger.info("Both click failed {}",smartEx.getMessage());
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "OPEN_ROW_COUNT_DROPDOWN",
                            String.format(
                                    "Failed to open row count dropdown using selector '%s'. Both safeClick and smartClick failed.",
                                    openerSelector
                            ),
                            smartEx
                    );
                }
            }

            // =====================================================
            // SCREENSHOT AFTER OPENING
            // =====================================================

            try {
                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "row count dropdown opened",
                        navigationScreenshotDir,
                        scenarioPrefix
                );
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "SCREENSHOT_AFTER_ROW_COUNT_OPEN",
                        "Row count dropdown opened, but screenshot capture failed",
                        ex
                );
            }

            // =====================================================
            // SELECT ROW COUNT OPTION
            // =====================================================

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "WAIT_BEFORE_ROW_COUNT_SELECT",
                        "Row count navigation was interrupted before selecting the dropdown value",
                        ex
                );
            }

            WebElement rowOption;
            try {
                rowOption =findRowCountOption(wait, driver, rows);
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "LOCATE_ROW_COUNT_OPTION",
                        String.format(
                                "Failed to locate row count option '%d' in the dropdown",
                                rows
                        ),
                        ex
                );
            }

            try {
                rowOption.click();
                logger.info("Clicked row count value {}", rows);
            }
            catch (Exception ex) {
                try {
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].click();",
                            rowOption
                    );
                    logger.info("Clicked row count value {} using JS click", rows);
                }
                catch (Exception jsEx) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "SELECT_ROW_COUNT_OPTION",
                            String.format(
                                    "Failed to select row count option '%d' even after JS click fallback",
                                    rows
                            ),
                            jsEx
                    );
                }
            }

            // =====================================================
            // SCREENSHOT AFTER SELECTION
            // =====================================================

            try {
                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "row count selected",
                        navigationScreenshotDir,
                        scenarioPrefix
                );
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "SCREENSHOT_AFTER_ROW_COUNT_SELECT",
                        "Row count value was selected, but screenshot capture failed",
                        ex
                );
            }

            // =====================================================
            // SUCCESS
            // =====================================================

            currScenario.setScenarioStatus(RunStatus.PASSED);
            resultTestCase.setResult("Passed");
        }
        catch (ScenarioExecutionException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new ScenarioExecutionException(
                    currIdx,
                    currScenario.getType(),
                    step,
                    "Unexpected failure while executing row count navigation",
                    ex
            );
        }
    }

    private WebElement findRowCountOption(WebDriverWait wait, WebDriver driver, int rows) {
        String value = String.valueOf(rows);

        By[] locators = new By[] {
                By.xpath("//a[@data-value='" + value + "']"),
                By.xpath("//div[contains(@class,'dt-button-collection')]"
                        + "//a[contains(@class,'button-page-length') and .//span[normalize-space()='" + value + "']]"),
                By.xpath("//div[contains(@class,'dt-button-collection')]"
                        + "//a[contains(@class,'button-page-length') and normalize-space()='" + value + "']")
        };

        for (By by : locators) {
            List<WebElement> elements = driver.findElements(by);
            if (!elements.isEmpty()) {
                try {
                    return wait.until(ExpectedConditions.elementToBeClickable(by));
                } catch (Exception ignored) {
                    return elements.get(0);
                }
            }
        }

        throw new NoSuchElementException("Row count option not found: " + rows);
    }

    private void handleDateRangeNav(
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario,
            Scenario scenario,
            TestCaseDTO resultTestCase,
            int currIdx,
            Path navigationScreenshotDir,
            String scenarioPrefix
    ) {

        final String stepBase = "DATE_RANGE_NAV";

        try {

            // =====================================================
            // VALIDATE CONFIG
            // =====================================================

            DateRangeNavDto dateRange = currScenario.getDateRangeNavDto();

            if (dateRange == null) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_DATE_RANGE_CONFIG",
                        "Date range navigation failed because dateRange configuration is missing",
                        null
                );
            }

            if (dateRange.getInputSelector() == null || dateRange.getInputSelector().isBlank()) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_DATE_RANGE_INPUT_SELECTOR",
                        "Date range navigation failed because input selector is missing",
                        null
                );
            }

            // =====================================================
            // OPEN CALENDAR
            // =====================================================

            WebElement inputElement;
            try {
                inputElement = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.cssSelector(dateRange.getInputSelector())
                        )
                );
                inputElement.click();
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "OPEN_DATE_RANGE_CALENDAR",
                        String.format(
                                "Failed to open date range calendar using selector '%s'",
                                dateRange.getInputSelector()
                        ),
                        ex
                );
            }

            try {
                screenshotService.takeScreenshot(
                        driver,
                        "1",
                        "calendar opened",
                        navigationScreenshotDir,
                        scenarioPrefix
                );
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "TAKE_SCREENSHOT_AFTER_OPEN",
                        "Date range navigation failed after opening calendar because screenshot capture failed",
                        ex
                );
            }

            // =====================================================
            // WAIT FOR CALENDAR CONTAINER
            // =====================================================

            String containerSelector =
                    dateRange.getCalendarContainerSelector() != null
                            ? dateRange.getCalendarContainerSelector()
                            : ".daterangepicker";

            try {
                wait.until(
                        ExpectedConditions.visibilityOfElementLocated(
                                By.cssSelector(containerSelector)
                        )
                );
            }
            catch (Exception ex) {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "WAIT_FOR_CALENDAR_CONTAINER",
                        String.format(
                                "Date range calendar container '%s' did not become visible",
                                containerSelector
                        ),
                        ex
                );
            }

            // =====================================================
            // PRESET MODE
            // =====================================================

            if (dateRange.getSelectionType() == DateSelectionType.PRESET) {

                if (dateRange.getPreset() == null) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "VALIDATE_PRESET_VALUE",
                            "Date range navigation failed because preset value is missing for PRESET mode",
                            null
                    );
                }

                String preset = Arrays.stream(
                                dateRange.getPreset().toString()
                                        .replace("_", " ")
                                        .toLowerCase()
                                        .split(" ")
                        )
                        .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                        .collect(Collectors.joining(" "));

                logger.info("Preset value = {}", preset);

                String presetXpath =
                        "//div[contains(@class,'daterangepicker') and contains(@style,'display: block')]"
                                + "//li[@data-range-key='" + preset + "']";

                WebElement presetElement;
                try {
                    presetElement = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.xpath(presetXpath)
                            )
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "LOCATE_PRESET_OPTION",
                            String.format(
                                    "Failed to locate preset option '%s' in the date range calendar",
                                    preset
                            ),
                            ex
                    );
                }

                try {
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].scrollIntoView(true);",
                            presetElement
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "SCROLL_PRESET_OPTION",
                            String.format(
                                    "Failed to scroll preset option '%s' into view",
                                    preset
                            ),
                            ex
                    );
                }

                try {
                    Thread.sleep(300);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "WAIT_AFTER_SCROLL_PRESET",
                            "Date range navigation was interrupted while waiting after scrolling preset option",
                            ex
                    );
                }

                try {
                    wait.until(ExpectedConditions.elementToBeClickable(presetElement));
                    presetElement.click();
                }
                catch (Exception ex) {
                    try {
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].click();",
                                presetElement
                        );
                    }
                    catch (Exception jsEx) {
                        throw new ScenarioExecutionException(
                                currIdx,
                                currScenario.getType(),
                                "SELECT_PRESET_OPTION",
                                String.format(
                                        "Failed to select preset option '%s' even after JS click fallback",
                                        preset
                                ),
                                jsEx
                        );
                    }
                }

                try {
                    screenshotService.takeScreenshot(
                            driver,
                            "2",
                            "preset selected",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "TAKE_SCREENSHOT_AFTER_PRESET",
                            "Date range navigation failed after preset selection because screenshot capture failed",
                            ex
                    );
                }
            }

            // =====================================================
            // CUSTOM MODE
            // =====================================================

            else if (dateRange.getSelectionType() == DateSelectionType.CUSTOM) {

                if (dateRange.getStartDate() == null || dateRange.getEndDate() == null) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "VALIDATE_CUSTOM_DATE_VALUES",
                            "Date range navigation failed because start date or end date is missing for CUSTOM mode",
                            null
                    );
                }

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                        dateRange.getDateFormat() != null
                                ? dateRange.getDateFormat()
                                : "dd/MM/yyyy HH:mm"
                );

                LocalDateTime startDateTime;
                LocalDateTime endDateTime;

                try {
                    startDateTime = LocalDateTime.parse(dateRange.getStartDate(), formatter);
                    endDateTime = LocalDateTime.parse(dateRange.getEndDate(), formatter);
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "PARSE_CUSTOM_DATE_VALUES",
                            String.format(
                                    "Failed to parse custom date range values. start='%s', end='%s', format='%s'",
                                    dateRange.getStartDate(),
                                    dateRange.getEndDate(),
                                    formatter
                            ),
                            ex
                    );
                }

                try {
                    selectDate(
                            driver,
                            wait,
                            containerSelector,
                            startDateTime.toLocalDate()
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "SELECT_START_DATE",
                            String.format(
                                    "Failed to select start date '%s' in custom date range",
                                    startDateTime.toLocalDate()
                            ),
                            ex
                    );
                }

                try {
                    screenshotService.takeScreenshot(
                            driver,
                            "3",
                            "start date selected",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "TAKE_SCREENSHOT_AFTER_START_DATE",
                            "Date range navigation failed after start date selection because screenshot capture failed",
                            ex
                    );
                }

                try {
                    selectDate(
                            driver,
                            wait,
                            containerSelector,
                            endDateTime.toLocalDate()
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "SELECT_END_DATE",
                            String.format(
                                    "Failed to select end date '%s' in custom date range",
                                    endDateTime.toLocalDate()
                            ),
                            ex
                    );
                }

                try {
                    screenshotService.takeScreenshot(
                            driver,
                            "4",
                            "end date selected",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "TAKE_SCREENSHOT_AFTER_END_DATE",
                            "Date range navigation failed after end date selection because screenshot capture failed",
                            ex
                    );
                }
            }
            else {
                throw new ScenarioExecutionException(
                        currIdx,
                        currScenario.getType(),
                        "VALIDATE_SELECTION_TYPE",
                        "Date range navigation failed because selection type is neither PRESET nor CUSTOM",
                        null
                );
            }

            // =====================================================
            // APPLY BUTTON
            // =====================================================

            Boolean autoApply = dateRange.getAutoApply();

            if (autoApply == null || !autoApply) {

                String applySelector =
                        dateRange.getApplyButtonSelector() != null
                                ? dateRange.getApplyButtonSelector()
                                : ".applyBtn";

                WebElement applyBtn;
                try {
                    applyBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.cssSelector(applySelector)
                            )
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "LOCATE_APPLY_BUTTON",
                            String.format(
                                    "Failed to locate clickable apply button using selector '%s'",
                                    applySelector
                            ),
                            ex
                    );
                }

                try {
                    applyBtn.click();
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "CLICK_APPLY_BUTTON",
                            String.format(
                                    "Failed to click apply button using selector '%s'",
                                    applySelector
                            ),
                            ex
                    );
                }

                try {
                    screenshotService.takeScreenshot(
                            driver,
                            "5",
                            "step passed",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "TAKE_SCREENSHOT_AFTER_APPLY",
                            "Date range navigation failed after clicking apply because screenshot capture failed",
                            ex
                    );
                }
            }

            // =====================================================
            // SUCCESS
            // =====================================================

            scenario.setScenarioStatus(RunStatus.PASSED);
            resultTestCase.setResult("Passed");
        }
        catch (ScenarioExecutionException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new ScenarioExecutionException(
                    currIdx,
                    currScenario.getType(),
                    stepBase,
                    "Unexpected failure while executing date range navigation",
                    ex
            );
        }
    }

    private void handleFormModal(
            Scenario currScenario,
            WebDriver driver,
            TestCaseDTO resultTestCase,
            String scenarioPrefix,
            Path navigationScreenshotDir,
            Scenario scenario,
            int modalFormTcIdx
    ) {

        try {

            // =====================================================
            // VALIDATE CSV CONFIGURATION
            // =====================================================

            String csvFile = currScenario.getCsv();

            logger.info(
                    "Processing modal CSV file: {}",
                    csvFile
            );

            if (csvFile == null || csvFile.isBlank()) {

                throw new ScenarioExecutionException(
                        modalFormTcIdx,
                        currScenario.getType(),
                        "VALIDATE_MODAL_CSV",
                        "Modal scenario execution failed because CSV file path is missing or empty",
                        null
                );
            }

            // =====================================================
            // LOAD TEST CASES
            // =====================================================

            List<TestCaseDTO> testCases;

            try {

                testCases = csvLoader.loadFromS3(csvFile);

            }
            catch (Exception ex) {

                throw new ScenarioExecutionException(
                        modalFormTcIdx,
                        currScenario.getType(),
                        "LOAD_MODAL_TESTCASES",
                        String.format(
                                "Failed to load modal test cases from CSV file '%s'",
                                csvFile
                        ),
                        ex
                );
            }

            // =====================================================
            // VALIDATE TEST CASE DATA
            // =====================================================

            if (testCases == null || testCases.isEmpty()) {

                throw new ScenarioExecutionException(
                        modalFormTcIdx,
                        currScenario.getType(),
                        "VALIDATE_MODAL_TESTCASES",
                        String.format(
                                "CSV file '%s' was loaded successfully but contains no modal test cases",
                                csvFile
                        ),
                        null
                );
            }

            // =====================================================
            // VALIDATE TEST CASE INDEX
            // =====================================================

            if (modalFormTcIdx < 0 || modalFormTcIdx >= testCases.size()) {

                throw new ScenarioExecutionException(
                        modalFormTcIdx,
                        currScenario.getType(),
                        "VALIDATE_MODAL_TESTCASE_INDEX",
                        String.format(
                                "Invalid modal test case index %d. Available test case count is %d",
                                modalFormTcIdx,
                                testCases.size()
                        ),
                        null
                );
            }

            // =====================================================
            // FETCH CURRENT TEST CASE
            // =====================================================

            resultTestCase = testCases.get(modalFormTcIdx);

            logger.info(
                    "Executing modal testcase: {}",
                    resultTestCase.getTestcaseId()
            );

            // =====================================================
            // EXECUTE MODAL SCENARIO
            // =====================================================



            try {

                handleModalScenario(
                        driver,
                        currScenario,
                        resultTestCase,
                        scenarioPrefix,
                        navigationScreenshotDir
                );

            }
            catch (ScenarioExecutionException ex) {

                // already meaningful → bubble upward directly
                throw ex;

            }
            catch (Exception ex) {

                throw new ScenarioExecutionException(
                        modalFormTcIdx,
                        currScenario.getType(),
                        "EXECUTE_MODAL_SCENARIO",
                        String.format(
                                "Modal scenario execution failed for testcase '%s'",
                                resultTestCase.getTestcaseId()
                        ),
                        ex
                );
            }

            // =====================================================
            // SUCCESS FLOW
            // =====================================================

            scenario.setScenarioStatus(
                    RunStatus.PASSED
            );

            resultTestCase.setResult(
                    "Passed"
            );

            logger.info(
                    "Modal testcase '{}' executed successfully",
                    resultTestCase.getTestcaseId()
            );
        }
        catch (ScenarioExecutionException ex) {

            // preserve meaningful business exception
            throw ex;
        }
        catch (Exception ex) {

            // final safety net
            throw new ScenarioExecutionException(
                    modalFormTcIdx,
                    currScenario.getType(),
                    "HANDLE_FORM_MODAL",
                    "Unexpected failure occurred while processing modal form scenario",
                    ex
            );
        }
    }

    private void handleModalNav(WebDriver driver, Scenario currScenario, Scenario scenario, TestCaseDTO resultTestCase, int modalFormTcIdx, Path navigationScreenshotDir, String scenarioPrefix) {


        logger.info("Opening modal using selector: {}", currScenario.getCssOpener());
        By openerBy;
        try {
         openerBy  = By.cssSelector(currScenario.getCssOpener());
        }catch (Exception ex) {
            logger.error("Failed to open modal using selector: {}", currScenario.getCssOpener(), ex.getMessage().split("\n")[0]);
            throw new ScenarioExecutionException(
                    modalFormTcIdx,
                    currScenario.getType(),
                    "ERROR_OPENING_SELECTOR",
                    "Unable to select "+currScenario.getCssOpener(),
                    ex

            );
        }


        boolean clicked = false;

        // 1️⃣ TRY SAFE CLICK FIRST
        try {
            safeClick(driver, openerBy);
            clicked = true;
            logger.info("Modal opened using safeClick");
        } catch (Exception safeEx) {
            logger.warn("safeClick failed, falling back to smartClick. Reason: {}",safeEx.getMessage().split("\n")[0]);
        }

        // 2️⃣ FALLBACK TO SMART CLICK
            if (!clicked) {

                try {

                    smartClick(driver, openerBy);

                    clicked = true;

                    logger.info(
                            "Modal opened using smartClick"
                    );

                }
                catch (
                        Exception ex
                ) {
                    logger.info("Unable to click "+ex.getMessage().split("\n")[0]);

                    throw new GlobalExceptionHandler.ScenarioExecutionException(
                            scenario.getSequenceNo(),
                            currScenario.getType(),
                            "OPEN_MODAL",
                            String.format(
                                    "Failed to open modal using selector '%s'. ",
                                    currScenario.getCssOpener()
                            ),
                            ex
                    );
                }
            }

        // 3️⃣ Screenshot AFTER success
        String url = screenshotService.takeScreenshot(
                driver,
                (modalFormTcIdx + 1) + "",
                "step passed",
                navigationScreenshotDir,
                scenarioPrefix
        );

        logger.info("Modal opener clicked successfully");

        scenario.setScenarioStatus(RunStatus.PASSED);
        resultTestCase.setResult("Passed");
    }

    private void urlNavigation(Scenario currScenario,
                               Scenario scenario,
                               WebDriver driver,
                               int modalFormTcIdx,
                               Path navigationScreenshotDir,
                               String scenarioPrefix,
                               TestCaseDTO resultTestCase) {
      try {
          logger.info("Navigating to URL: {}", currScenario.getUrl());

          driver.get(currScenario.getUrl());
          String url = screenshotService.takeScreenshot(
                  driver,
                  (modalFormTcIdx + 1) + "",
                  "step passed",
                  navigationScreenshotDir,
                  scenarioPrefix
          );

          scenario.setScenarioStatus(RunStatus.PASSED);
          resultTestCase.setResult("Passed");
      }  catch (WebDriverException | IllegalArgumentException e) {

          String reason = e.getMessage() != null
                  ? e.getMessage()
                  : e.getClass().getSimpleName();

          logger.error(
                  "URL navigation failed | scenarioIndex={} | url={} | reason={}",
                  modalFormTcIdx,
                  currScenario.getUrl(),
                  reason,
                  e
          );

          throw new GlobalExceptionHandler.ScenarioExecutionException(
                  modalFormTcIdx,
                  ScenarioType.URL_NAV,
                  "URL_NAVIGATION",
                  String.format(
                          "Failed to navigate to URL [%s]. Reason: %s",
                          currScenario.getUrl(),
                          reason
                  ),
                  e
          );
      }
    }

    private void moveManageColumn(
            WebDriver driver,
            String columnName,
            Integer position
    ){

        logger.info(
                "===== START : moveManageColumn | columnName={}, position={} =====",
                columnName,
                position
        );

        if(position == null){
            logger.info(
                    "Position is null for column [{}]. Skipping move operation.",
                    columnName
            );
            return;
        }

        String js = """
        const columnName = arguments[0];
        const position = arguments[1];

        const list = document.getElementById(
            "manage-column-sortable-list"
        );

        if(!list){
            return;
        }

        const items = [...list.children];

        const item = items.find(li =>li.querySelector(".column-title")?.textContent?.trim() === columnName);

        if (!item) return;
        

        /*
         * Move only if visible
         */
        const checkbox = item.querySelector(
            ".manage-column-checkbox"
        );

        if(!checkbox || !checkbox.checked){
            return;
        }

        item.remove();

        list.insertBefore(
            item,
            list.children[position]
        );
        """;

        logger.info(
                "Executing JavaScript for moving column [{}] to position [{}]",
                columnName,
                position
        );

        ((JavascriptExecutor) driver)
                .executeScript(
                        js,
                        columnName,
                        position - 1 // convert to 0-based
                );

        logger.info(
                "Successfully executed move operation for column [{}]",
                columnName
        );

        logger.info("===== END : moveManageColumn =====");
    }
    private String fetchVisibleColumns(WebDriver driver){

        logger.info("===== START : fetchVisibleColumns =====");

        String js = """
        return [...document.querySelectorAll(
            '#manage-column-sortable-list li'
        )]
        .map(li =>
            li.querySelector('.column-title')
                ?.textContent
                ?.trim()
        )
        .filter(Boolean)
        .join('|');
        """;

        logger.info("Executing JavaScript to fetch visible columns.");

        String visibleColumns = (String)
                ((JavascriptExecutor) driver)
                        .executeScript(js);

        logger.info(
                "Fetched visible columns sequence: {}",
                visibleColumns
        );

        logger.info("===== END : fetchVisibleColumns =====");

        return visibleColumns;
    }

    private void selectDate(
            WebDriver driver,
            WebDriverWait wait,
            String containerSelector,
            LocalDate targetDate
    ) {

        DateTimeFormatter monthFormatter =
                DateTimeFormatter.ofPattern("MMM yyyy");

        YearMonth targetMonth =
                YearMonth.from(targetDate);

        while (true) {

            WebElement leftMonthElement = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(
                                    containerSelector
                                            + " .drp-calendar.left th.month"
                            )
                    )
            );

            YearMonth leftMonth =
                    YearMonth.parse(
                            leftMonthElement.getText().trim(),
                            monthFormatter
                    );

            WebElement rightMonthElement = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(
                                    containerSelector
                                            + " .drp-calendar.right th.month"
                            )
                    )
            );

            YearMonth rightMonth =
                    YearMonth.parse(
                            rightMonthElement.getText().trim(),
                            monthFormatter
                    );

            // =========================
            // LEFT CALENDAR
            // =========================
            if (targetMonth.equals(leftMonth)) {

                clickDay(
                        wait,
                        containerSelector,
                        "left",
                        targetDate.getDayOfMonth()
                );

                return;
            }

            // =========================
            // RIGHT CALENDAR
            // =========================
            if (targetMonth.equals(rightMonth)) {

                clickDay(
                        wait,
                        containerSelector,
                        "right",
                        targetDate.getDayOfMonth()
                );

                return;
            }

            // =========================
            // NEXT MONTH
            // =========================
            WebElement nextButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector(
                                    containerSelector
                                            + " .next.available"
                            )
                    )
            );

            nextButton.click();

            wait.until(ExpectedConditions.stalenessOf(rightMonthElement));
        }
    }

    private void clickDay(
            WebDriverWait wait,
            String containerSelector,
            String side,
            int day
    ) {

        List<WebElement> dates = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(
                        By.cssSelector(
                                containerSelector
                                        + " .drp-calendar."
                                        + side
                                        + " td.available"
                        )
                )
        );

        for (WebElement date : dates) {

            String classes = date.getAttribute("class");

            if (date.getText().trim().equals(String.valueOf(day))
                    && !classes.contains("off")
                    && !classes.contains("disabled")) {

                wait.until(
                        ExpectedConditions.elementToBeClickable(date)
                );

                date.click();

                return;
            }
        }

        throw new GlobalExceptionHandler.ResourceNotFoundException(
                "Unable to select day: " + day
        );
    }

    public void runModalGeneric(WebDriver driver,List<Scenario> scenarios,String successMsg,int currIdx,String baseS3Prefix,Run run
            , Map<String, List<TestCaseDTO>> scenarioResultsMap) {
        List<TestCaseDTO> testCases=null;

        int currEle=-1;

        try{
            currEle=handleNavigation(driver,scenarios,currIdx,0,baseS3Prefix,run,scenarioResultsMap);
        }catch (ScenarioExecutionException ex){
            logger.info("Got Exception {}",ex.getMessage());
            throw ex;
        }
        String scenarioPrefix =
                baseS3Prefix + "/" + (currEle+1);

        if(currEle>=scenarios.size()){
            return;
        }

        Scenario currModal=scenarios.get(currEle);
        logger.info("Processing scenario at adjusted index {}: type={}, url={}",
                currEle, currModal.getType(), currModal.getUrl());

        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        try {
            Files.createDirectories(scenarioDir);
        }catch (IOException ex){
            logger.error("Unable to create directory {}",ex.getMessage());
        }
        if(currModal.getType()==ScenarioType.ASSERT){
            logger.info("Index adjustment completed - original: {}, adjusted: {}, scenario type: {}",
                    currEle,currModal.getUrl(), currModal.getType());
            runAssertionGeneric(driver,currModal,baseS3Prefix,scenarios);
            return;
        }

        int counterIdx=0;
        int totalPasses = 0;
        int totalFails = 0;
        try {
            // load modal testcases
            testCases = csvLoader.loadFromS3(currModal.getCsv());
            logger.info("[{}] loaded {} modal testcases from", scenarioPrefix, testCases.size());

            for (TestCaseDTO tc : testCases) {
                String tcRunId = tc.getTestcaseId();
                List<FieldDescriptor> modalFields = scannerService.scanCurrentPage(driver);
                logger.info("[{}] scanned {} modal fields", scenarioPrefix, modalFields.size());
                counterIdx++;

                List<StepAction> steps = stepGenerator.generateSteps(modalFields, tc);
                logger.info("[{}] Executing {} modal steps", tcRunId, steps.size());
                String expected = tc.getExpectedResult();
                ResultRun resultRun =executor.runOnRenderedPage(driver, steps, tcRunId,successMsg,scenarioDir,scenarioPrefix,expected);
                if (expected != null && expected.equalsIgnoreCase(resultRun.getStatus())) {
                    tc.setResult("Passed");
                    totalPasses++;
                } else {
                    tc.setResult(resultRun.getStatus());
                    totalFails++;
                }
                tc.setUrls(resultRun.getScreenshots());
                if(counterIdx<testCases.size())
                    try {
                        handleNavigation(driver, scenarios, currIdx, counterIdx, baseS3Prefix, run, scenarioResultsMap);
                    }catch (ScenarioExecutionException ex){
                        throw ex;
                    }
                logger.info("[{}] Completed modal testcase {}", tcRunId, tc);

            }

        }
        catch (ScenarioExecutionException ex){
            throw ex;
        }
        catch (Exception ex) {
            logger.error("[{}] failed to open modal or execute tests: {}",scenarioPrefix, ex.getMessage());
            throw new ScenarioExecutionException(
                    currEle,
                    currModal.getType(),
                    "EXECUTE_MODAL_TESTCASE",
                    String.format(
                            "Modal execution failed for "+currIdx, scenarioPrefix, ex.getMessage()
                    ),
                    ex
            );
        }
        // store modal scenario testcases in memory grouped by scenarioPrefix
        scenarioResultsMap.computeIfAbsent(scenarioPrefix, k -> new ArrayList<>())
                .addAll(testCases);

        logger.info("[{}] Stored {} modal testcases in scenarioResultsMap",
                scenarioPrefix, testCases.size());

//        ScenarioTestDto scenarioTestDto = new ScenarioTestDto(testCases, null);

        if (totalPasses == testCases.size()) {
            currModal.setScenarioStatus(RunStatus.PASSED);
        }
        else if (totalFails == testCases.size()) {
            currModal.setScenarioStatus(RunStatus.FAILED);
        }
        else {
            currModal.setScenarioStatus(RunStatus.PARTIAL);
        }
        logger.info("Total testcase "+testCases.size()+" passes "+totalPasses+" fails "+totalFails);
    }

    public void runAssertionGeneric(
            WebDriver driver,
            Scenario scenario,
            String baseS3Prefix,
            List<Scenario> scenarios
    ) {

        int scenarioId = scenario.getSequenceNo();
        String scenarioPrefix = baseS3Prefix + "/" + scenarioId;

        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        try {
            Files.createDirectories(scenarioDir);
        }catch (IOException e){
            logger.error("[{}] failed to create scenario dir: {}", scenarioPrefix, e.getMessage());
        }

        List<StepAction> steps =
                assertionStepGenerator.generateAssertionSteps(
                        scenario.getAssertions()
                );

        logger.info("steps of Assert scenario : {}",steps);

            executor.runAssertionSteps(driver, steps,scenarioDir,scenarioPrefix,scenarios);
            List<AssertionDto> assertionDtos=scenario.getAssertions();
            List<TestCaseDTO> testDtos = new ArrayList<>();
            int tcIdx = 1;
            int passedCount = 0;
            int failedCount = 0;

            for (AssertionDto assertDto : assertionDtos) {
                Map<String, String> valuesMap = new LinkedHashMap<>();

                // Put all AssertionDto fields into map
                valuesMap.put("type", assertDto.getType() != null ? assertDto.getType().name() : "");
                valuesMap.put("locator", assertDto.getLocator());
                valuesMap.put("expected", assertDto.getExpected());
                valuesMap.put("columnName", assertDto.getColumnName());
                valuesMap.put("tableId", assertDto.getTableId());
                valuesMap.put("rowsBtn", assertDto.getRowsBtn());
                valuesMap.put("order", assertDto.getOrder());
                valuesMap.put("errorMessage", assertDto.getErrorMessage());
                valuesMap.put("prompt", assertDto.getPrompt());
                valuesMap.put("reason",assertDto.getReason());


                // Create TestCaseDTO
                TestCaseDTO tc = new TestCaseDTO(String.valueOf(tcIdx), valuesMap);
                tcIdx++;
//                System.out.println("Final Status: "+assertDto.getAssertResult());
                if ("PASSED".equalsIgnoreCase(assertDto.getAssertResult())) {
                    passedCount++;
                } else if ("FAILED".equalsIgnoreCase(assertDto.getAssertResult())) {
                    failedCount++;
                }
                // Keep result separate
                tc.setResult(assertDto.getAssertResult());
                testDtos.add(tc);
            }
            int total = assertionDtos.size();

            if (failedCount == total) {
                scenario.setScenarioStatus(RunStatus.FAILED);
            } else if (passedCount == total) {
//                System.out.println("Scenario PASSED");
                scenario.setScenarioStatus(RunStatus.PASSED);
            } else {
                scenario.setScenarioStatus(RunStatus.PARTIAL);
            }
        try {
            Path csvPath = csvLoader.writeScenarioCsv(testDtos,scenarioDir);
            String s3Key = scenarioPrefix;
            String finalCsvUrl = s3StorageService.uploadFile(csvPath, s3Key);
            scenario.setResultCsv(finalCsvUrl);
        } catch (Exception e) {
            logger.error("exception encountered "+ e.getMessage());
            throw new ScenarioExecutionException(
                    scenarioId,
                    scenario.getType(),
                    "Problem while uploading csv to s3",
                    "Unable to load assertion results",
                    e
            );
        }
    }


    private void handleModalScenario(
            WebDriver driver,
            Scenario scenario,
            TestCaseDTO tc,
            String scenarioPrefix,
            Path navigationScreenshotDir
    ) {
        int stepCounter = 1;

        String cssSelector = scenario.getCssOpener();
        String value = scenario.getValue();
        boolean isClick =scenario.getClickCss()!=null;
        logger.info("is click : {}",isClick);
        boolean isSearch = !isClick;
        String secondId = scenario.getClickCss();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        if(cssSelector !=null){
            By by = resolveLocator(cssSelector);


            WebElement element = wait.until(
//                ExpectedConditions.presenceOfElementLocated(By.cssSelector(cssSelector))
                    ExpectedConditions.presenceOfElementLocated(by)
            );
            logger.info("FORM_MODAL scenario details -> openerCss='{}', value='{}', clickCss='{}', isClick={}, isSearch={}, clickCss='{}'",
                    cssSelector,
                    value,
                    scenario.getClickCss(),
                    isClick,
                    isSearch,
                    secondId
            );


            String tag = element.getTagName();

            // =========================
            // HANDLE SELECT DROPDOWN
            // =========================

            if ("select".equalsIgnoreCase(tag)) {

                boolean isSelect2 = element.getAttribute("class") != null &&
                        element.getAttribute("class").contains("select2-hidden-accessible");

                if (isSelect2) {
                    // 🔥 pass stepCounter using wrapper array (mutable)
                    int[] counter = new int[]{stepCounter};
                    handleSelect2(driver, element, value, counter, navigationScreenshotDir, scenarioPrefix);
                    stepCounter = counter[0]; // update back
                } else {

                    Select sel = new Select(element);
                    sel.selectByVisibleText(value);

                }

            } else {

                element.clear();
                element.sendKeys(value);
            }

            // Trigger change event
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", element);

            // 📸 Step 1 → after input/select
            screenshotService.takeScreenshot(
                    driver,
                    "modal_form",
                    "step_" +TimestampUtil.generateTimestamp(),
                    navigationScreenshotDir,
                    scenarioPrefix
            );

        }

        // =========================
        // CLICK MODE
        // =========================

        if (isClick) {

            logger.info("Click flag is TRUE. Attempting to click element with id: " + secondId);

            try {

                WebElement container = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(secondId))
                );

                // 🔥 find actual clickable child (a/button)
                WebElement clickable = container.findElement(By.xpath(".//a | .//button"));

                // scroll into view
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({block:'center'});", clickable);

                // 🔥 remove disabled if present
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].removeAttribute('disabled');", clickable);

                // wait until clickable
                wait.until(ExpectedConditions.elementToBeClickable(clickable));

                try {
                    clickable.click();
                } catch (Exception e) {
                    logger.warn("Normal click failed, using JS click");
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].click();", clickable);
                }

                // 📸 Step 2 → after click
                screenshotService.takeScreenshot(
                        driver,
                        "modal_form",
                        "step_" + stepCounter++,
                        navigationScreenshotDir,
                        scenarioPrefix
                );

                logger.info("Click action completed successfully for id: " + secondId);

            } catch (GlobalExceptionHandler.TimeoutException e) {
                logger.error("Timeout: Element with id '" + secondId + "' was not clickable.", e);
            } catch (Exception e) {
                logger.error("Unexpected error while clicking element with id: " + secondId, e);
            }

        } else {
            logger.info("Click flag is FALSE. Skipping click action.");
        }

        // =========================
        // SCAN PAGE
        // =========================

        List<FieldDescriptor> fields = scannerService.scanCurrentPage(driver);

        // create steps from fields + testcase
        List<StepAction> steps = stepGenerator.generateSteps(fields, tc);

        // =========================
        // EXECUTE
        // =========================

        executor.runOnRenderedPage(
                driver,
                steps,
                tc.getTestcaseId(),
                null,
                navigationScreenshotDir,
                scenarioPrefix,
                tc.getExpectedResult()

        );
    }
    private void handleSelect2(WebDriver driver, WebElement selectElement, String value,int[]stepCounter,Path navigationScreenshotDir, String scenarioPrefix) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        String selectId = selectElement.getAttribute("id");

        WebElement container = driver.findElement(
                By.xpath("//select[@id='" + selectId + "']/following-sibling::span")
        );

        container.click();
        // 📸 Step → dropdown opened
        screenshotService.takeScreenshot(
                driver,
                "modal_form",
                "step_" + stepCounter[0]++,
                navigationScreenshotDir,
                scenarioPrefix
        );

        try {

            // TRY SEARCH MODE
            WebElement search = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(".select2-search__field"))
            );

            search.clear();
            search.sendKeys(value);

            // 📸 Step → typed in select2 search
            screenshotService.takeScreenshot(
                    driver,
                    "modal_form",
                    "step_" + stepCounter[0]++,
                    navigationScreenshotDir,
                    scenarioPrefix
            );

            WebElement option = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//li[contains(@class,'select2-results__option') and contains(.,'" + value + "')]")
                    )
            );

            option.click();

        } catch (Exception e) {

            // TRY DIRECT SELECT
            WebElement option = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//li[contains(@class,'select2-results__option') and contains(.,'" + value + "')]")
                    )
            );

            option.click();
        }
        // 📸 Step → option selected
        screenshotService.takeScreenshot(
                driver,
                "modal_form",
                "step_" + stepCounter[0]++,
                navigationScreenshotDir,
                scenarioPrefix
        );
    }

    private By resolveLocator(String selector) {

        // If it looks like ID but contains spaces or special chars → use By.id
        if (selector.startsWith("#")) {
            String id = selector.substring(1);

            // If contains invalid CSS chars → use ID directly
            if (id.matches(".*[\\s()]+.*")) {
                return By.id(id);
            }
        }

        // If XPath
        if (selector.startsWith("//") || selector.startsWith("(")) {
            return By.xpath(selector);
        }

        // Default → CSS
        return By.cssSelector(selector);
    }

    public void safeClick(WebDriver driver, By locator) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));

            // 🔥 scroll first (important for sticky overlays)
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});", element
            );

            // 🔥 wait for clickable
            element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.click();

        } catch (GlobalExceptionHandler.TimeoutException e) {
            logger.info("time out exception");

            // 🔥 fallback 1: JS click on fresh element
            WebElement element = driver.findElement(locator);

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});", element
            );

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].click();", element
            );

        } catch (ElementClickInterceptedException e) {
            logger.info("ElementClickInterceptedException");


            // 🔥 fallback 2: JS click (bypass overlay)
            WebElement element = driver.findElement(locator);

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].click();", element
            );

        }
    }
    private String extractValue(String locatorString) {

        if (locatorString == null) {
            throw new IllegalArgumentException("Locator string cannot be null");
        }

        // Common pattern: value='YES' or value="YES"
        Pattern pattern = Pattern.compile("value\\s*=\\s*['\"](.*?)['\"]");
        Matcher matcher = pattern.matcher(locatorString);

        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new GlobalExceptionHandler.ResourceNotFoundException(
                "Could not extract value from locator: " + locatorString
        );
    }

    private String extractLabelText(WebDriver driver, String selector) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement label = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector))
        );

        String text = label.getText().trim();

        if (text.isEmpty()) {
            text = label.getAttribute("innerText");
        }

        return text != null ? text.trim() : "";
    }
    private void smartClick(WebDriver driver, By locator) {

        String loc = locator.toString();

        // 🔥 RADIO pattern
        if (loc.contains("value=") && loc.contains("radio")) {

            String value = extractValue(loc);

            By label = By.xpath("//label[.//input[@value='" + value + "']]");
            driver.findElement(label).click();
            return;
        }

        // 🔥 SELECT2 pattern
        if (loc.contains("select2") || loc.contains("form-select2")) {

            driver.findElement(locator).click();
            return;
        }

        // 🔥 fallback JS click
        WebElement el = driver.findElement(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    public void selectSelect2(WebDriver driver, String openerCss, String value) {

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Extract actual select id from select2 opener
        String selectId = openerCss
                .replace("#select2-", "")
                .replace("-container", "");

        String script =
                "var select = document.getElementById(arguments[0]);" +
                        "if (!select) throw 'Select not found: ' + arguments[0];" +

                        "var found = false;" +
                        "for (var i = 0; i < select.options.length; i++) {" +
                        "  if (select.options[i].text.trim() === arguments[1]) {" +
                        "    select.selectedIndex = i;" +
                        "    found = true;" +
                        "    break;" +
                        "  }" +
                        "}" +

                        "if (!found) throw 'Option not found: ' + arguments[1];" +

                        // 🔥 CRITICAL → notify Select2
                        "$(select).trigger('change');";

        js.executeScript(script, selectId, value);
    }

    private void handleManageColumnScenario(
            int currIdx,
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario
    )  {
    try {
        logger.info("===== START : handleManageColumnScenario =====");

        List<ManageColumnItemDto> targetColumns =
                currScenario.getColumns();

        logger.info("Fetched target columns: {}", targetColumns);

        if (targetColumns == null || targetColumns.isEmpty()) {
            logger.info("No target columns found. Exiting.");
            return;
        }

        for (ManageColumnItemDto column : targetColumns) {

            String columnSelector = column.getColumnName();
            Integer position = column.getPosition();
            ManageColumnAction action = column.getAction();


            logger.info(
                    "Processing columnSelector={}, action={}, position={}",
                    columnSelector, action, position
            );

            WebElement label = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(columnSelector)
                    )
            );
            String extractedColumn = label.getText().trim();
            column.setExtractedName(extractedColumn);

            logger.info("Extracted column name: {}", extractedColumn);

            String forAttr = label.getAttribute("for");

            WebElement checkbox = driver.findElement(By.id(forAttr));

            boolean isSelected = checkbox.isSelected();

            /*
             * =========================
             * HANDLE ACTION
             * =========================
             */
            if (action == ManageColumnAction.HIDE) {

                if (isSelected) {

                    logger.info("Hiding column: {}", columnSelector);

                    ((JavascriptExecutor) driver)
                            .executeScript("arguments[0].click();", checkbox);

                    Thread.sleep(300);
                }

                continue; // no move allowed for hidden columns
            }

            /*
             * SHOW or NULL => ensure visible
             */
            if (!isSelected) {

                logger.info("Ensuring column is visible: {}", columnSelector);

                ((JavascriptExecutor) driver)
                        .executeScript("arguments[0].click();", checkbox);

                Thread.sleep(300);
            }

            /*
             * =========================
             * HANDLE POSITION (MOVE)
             * =========================
             */
            if (position != null) {

                String columnTitle = label.getText().trim();

                logger.info(
                        "Moving column [{}] to position [{}]",
                        columnTitle,
                        position
                );

                moveManageColumn(driver, columnTitle, position);
            }
        }

//     Click save
        WebElement saveBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.cssSelector(currScenario.getSaveBtnCss())
                )
        );

        ((JavascriptExecutor) driver)
                .executeScript("arguments[0].click();", saveBtn);

        Thread.sleep(1500);

        logger.info("===== END : handleManageColumnScenario =====");
    } catch (Exception e) {
        throw new ScenarioExecutionException(
                currIdx,
                currScenario.getType(),
                "MANAGE_COLUMN",
                String.format(
                        "Exception occured"+e.getMessage()
                ),
                e
        );
    }
    }
    public void handleSelect2Dropdown(WebDriver driver, String openerCss, String value) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // =========================
            // 1️⃣ Open Select2 dropdown
            // =========================
            WebElement opener = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector(openerCss)
            ));

            // scroll into view (important for floating dropdowns)
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", opener);

            try {
                opener.click();
            } catch (Exception e) {
                // fallback JS click
                js.executeScript("arguments[0].click();", opener);
            }

            // =========================
            // 2️⃣ Wait for dropdown to open
            // =========================
            WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(".select2-container--open .select2-search__field")
            ));

            // =========================
            // 3️⃣ Type value (search)
            // =========================
            searchBox.clear();
            searchBox.sendKeys(value);

            // =========================
            // 4️⃣ Wait for results
            // =========================
            By optionsLocator = By.cssSelector(".select2-results__option");

            wait.until(ExpectedConditions.presenceOfElementLocated(optionsLocator));

            // =========================
            // 5️⃣ Select matching option
            // =========================
            try {
                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//li[contains(@class,'select2-results__option') and text()='" + value + "']")
                ));
                option.click();

            } catch (Exception e) {

                // fallback → partial match
                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//li[contains(@class,'select2-results__option') and contains(text(),'" + value + "')]")
                ));
                option.click();
            }

        } catch (Exception e) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Select2 handling failed for value: " + value);
        }
    }
    public void handleBootstrapSelect(WebDriver driver,
                                      String selector,
                                      String value) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        WebElement select = driver.findElement(By.cssSelector(selector));

        String id = select.getAttribute("id");

        // Generated bootstrap-select button
        WebElement button = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("button[data-id='" + id + "']")
        ));

        // scroll first
        js.executeScript(
                "arguments[0].scrollIntoView({block:'center'});",
                button
        );

        // safer click
        try {
            wait.until(ExpectedConditions.elementToBeClickable(button));
            button.click();

        } catch (Exception e) {

            js.executeScript("arguments[0].click();", button);
        }

        // Search box if exists
        try {

            WebElement search = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(".bs-searchbox input")
                    )
            );

            search.clear();
            search.sendKeys(value);

        } catch (Exception ignored) {}

        // Click option
        By optionLocator = By.xpath(
                "//div[contains(@class,'bootstrap-select')]" +
                        "[.//button[@data-id='" + id + "']]" +
                        "//div[contains(@class,'dropdown-menu') and contains(@class,'show')]" +
                        "//span[@class='text' and normalize-space()='" + value + "']"
        );

        WebElement option = wait.until(
                ExpectedConditions.visibilityOfElementLocated(optionLocator)
        );

        js.executeScript("arguments[0].scrollIntoView({block:'center'});", option);

        try {
            option.click();
        } catch (Exception e) {
            js.executeScript("arguments[0].click();", option);
        }
    }
    private int handleSearchNavScenario(
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario,
            Scenario scenario,
            TestCaseDTO resultTestCase,
            int currIdx,
            int modalFormTcIdx,
            Path navigationScreenshotDir,
            String scenarioPrefix
    ) {

        logger.info(
                "Executing NAV_SEARCH using selector: {} and value: {}",
                currScenario.getCssOpener(),
                currScenario.getValue()
        );

        try {
            WebElement opener = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(currScenario.getCssOpener())
                    )
            );

            String value = currScenario.getValue();

            logger.info("Search element located: tag={}", opener.getTagName());

            boolean isInputSearch =
                    opener.getTagName().equalsIgnoreCase("input")
                            || "true".equals(opener.getAttribute("contenteditable"));

            // =====================================================
            // CASE 1 → INPUT SEARCH FIELD
            // =====================================================
            if (isInputSearch) {

                logger.info("Detected input search field");

                try {
                    FallbackExecutor.execute(
                            List.of("EMPLOYEE_DROPDOWN", "TREE_SELECTOR", "NO_ACTION"),
                            List.of(
                                    () -> {
                                        WebElement dropdownBtn =
                                                wait.until(
                                                        ExpectedConditions.elementToBeClickable(
                                                                By.cssSelector(".dropdown-btn")
                                                        )
                                                );
                                        Thread.sleep(1000);
                                        dropdownBtn.click();
                                        logger.info("Opened employee dropdown");
                                        return true;
                                    },
                                    () -> {
                                        WebElement treeOpener =
                                                driver.findElement(
                                                        By.cssSelector(".treeSelector-input-box")
                                                );

                                        if (!treeOpener.isDisplayed()) {
                                            throw new ScenarioExecutionException(
                                                    currIdx,
                                                    currScenario.getType(),
                                                    "OPEN_SEARCH_DROPDOWN",
                                                    "Tree selector is present but not visible, so search navigation cannot continue",
                                                    null
                                            );
                                        }

                                        treeOpener.click();
                                        logger.info("Opened tree selector dropdown");
                                        return true;
                                    },
                                    () -> {
                                        logger.info("No dropdown opener needed");
                                        return true;
                                    }
                            )
                    );
                }
                catch (ScenarioExecutionException ex) {
                    throw ex;
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "OPEN_SEARCH_DROPDOWN",
                            "Failed to open search dropdown before typing search value",
                            ex
                    );
                }

                opener.clear();
                opener.sendKeys(value);

                logger.info("Typed search value: {}", value);

                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "step passed",
                        navigationScreenshotDir,
                        scenarioPrefix
                );

                WebElement option;
                try {
                    option = FallbackExecutor.execute(
                            List.of("DATA_TITLE", "TITLE_LIST_SPAN", "EXACT_TEXT", "PARTIAL_TEXT"),
                            List.of(
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[@data-title='" + value + "']//input")
                                            )
                                    ),
                                    () -> wait.until(
                                            ExpectedConditions.presenceOfElementLocated(
                                                    By.xpath(
                                                            "//span[contains(@class,'tittle-list') and contains(text(),'" + value + "')]/preceding-sibling::input[@type='checkbox']"
                                                    )
                                            )
                                    ),
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[text()='" + value + "']")
                                            )
                                    ),
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[contains(text(),'" + value + "')]")
                                            )
                                    )
                            )
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "LOCATE_SEARCH_OPTION",
                            "Failed to locate the searched option after typing the search value",
                            ex
                    );
                }

                try {
                    option.click();
                    logger.info("Clicked option normally");
                }
                catch (Exception clickEx) {
                    logger.warn("Normal click failed, using JS click");
                    try {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", option);
                        logger.info("Clicked option using JS click");
                    }
                    catch (Exception jsEx) {
                        throw new ScenarioExecutionException(
                                currIdx,
                                currScenario.getType(),
                                "SELECT_SEARCH_OPTION",
                                "Failed to select the searched option even after JS click fallback",
                                jsEx
                        );
                    }
                }

                logger.info("Successfully selected option: {}", value);

                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "step passed",
                        navigationScreenshotDir,
                        scenarioPrefix
                );

                // optional only, do not stop execution if absent
                try {
                    logger.info("Searching for apply button");
                    WebElement applyButton = driver.findElement(By.id("apply-button"));
                    if (applyButton.isDisplayed()) {
                        applyButton.click();
                        logger.info("Clicked apply button");
                    }
                }
                catch (Exception ignored) {
                    logger.info("No apply button found");
                }
                try {
                    driver.findElement(By.tagName("body")).click();
                    logger.info("Closed dropdown using body click");
                } catch (Exception e) {
                    logger.info("Unable to click");
                }

                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "step passed",
                        navigationScreenshotDir,
                        scenarioPrefix
                );
            }

            // =====================================================
            // CASE 2 → DROPDOWN OPENER
            // =====================================================
            else {

                boolean isSelect2 =
                        !driver.findElements(By.cssSelector(".select2-container--default")).isEmpty()
                                || !driver.findElements(By.cssSelector(".select2-hidden-accessible")).isEmpty();

                if (isSelect2) {
                    logger.info("Detected Select2 dropdown");

                    try {
                        selectSelect2(driver, currScenario.getCssOpener(), value);
                    }
                    catch (Exception ex) {
                        throw new ScenarioExecutionException(
                                currIdx,
                                currScenario.getType(),
                                "SELECT_SELECT2_OPTION",
                                "Failed to select option in Select2 dropdown",
                                ex
                        );
                    }

                    screenshotService.takeScreenshot(
                            driver,
                            String.valueOf(modalFormTcIdx + 1),
                            "step passed",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );

                    scenario.setScenarioStatus(RunStatus.PASSED);
                    resultTestCase.setResult("Passed");
                    return currIdx + 1;
                }

                logger.info("Detected normal dropdown opener");

                try {
                    wait.until(ExpectedConditions.elementToBeClickable(opener)).click();
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "OPEN_DROPDOWN",
                            "Failed to open dropdown for search navigation",
                            ex
                    );
                }

                WebElement option;
                try {
                    option = FallbackExecutor.execute(
                            List.of("DATA_TITLE", "EXACT_TEXT", "PARTIAL_TEXT"),
                            List.of(
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[@data-title='" + value + "']//input")
                                            )
                                    ),
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[text()='" + value + "']")
                                            )
                                    ),
                                    () -> wait.until(
                                            ExpectedConditions.elementToBeClickable(
                                                    By.xpath("//*[contains(text(),'" + value + "')]")
                                            )
                                    )
                            )
                    );
                }
                catch (Exception ex) {
                    throw new ScenarioExecutionException(
                            currIdx,
                            currScenario.getType(),
                            "LOCATE_DROPDOWN_OPTION",
                            "Failed to locate dropdown option after opening dropdown",
                            ex
                    );
                }

                try {
                    option.click();
                }
                catch (Exception ex) {
                    try {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", option);
                    }
                    catch (Exception jsEx) {
                        throw new ScenarioExecutionException(
                                currIdx,
                                currScenario.getType(),
                                "SELECT_DROPDOWN_OPTION",
                                "Failed to select dropdown option even after JS click fallback",
                                jsEx
                        );
                    }
                }

                logger.info("Dropdown option selected successfully");

                screenshotService.takeScreenshot(
                        driver,
                        String.valueOf(modalFormTcIdx + 1),
                        "step passed",
                        navigationScreenshotDir,
                        scenarioPrefix
                );

                // optional only, do not stop execution if absent
                try {
                    driver.findElement(By.tagName("body")).click();
                    logger.info("Closed dropdown");
                }
                catch (Exception ignored) {
                    logger.info("Could not close dropdown using body click, continuing");
                }
            }

            scenario.setScenarioStatus(RunStatus.PASSED);
            resultTestCase.setResult("Passed");
            return currIdx;
        } catch (ScenarioExecutionException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new ScenarioExecutionException(
                    currIdx,
                    currScenario.getType(),
                    "SEARCH_NAVIGATION",
                    "Unexpected failure during search navigation",
                    ex
            );
        }
    }

    private void handleFilterNavScenario(
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario,
            Scenario scenario,
            TestCaseDTO resultTestCase
    ) {

        logger.info("Executing FILTER_NAV scenario");

        List<FilterScenarioDto> filters = currScenario.getFilters();

        for (FilterScenarioDto filter : filters) {

            logger.info("Processing filter: {}", filter);

            By queryBy = By.cssSelector(filter.getQuerySelector());

            // =====================================================
            // 1) CLICK QUERY SELECTOR
            // =====================================================
            FallbackExecutor.execute(

                    List.of(
                            "SAFE_CLICK",
                            "SMART_CLICK"
                    ),

                    List.of(

                            () -> {
                                safeClick(driver, queryBy);
                                logger.info("Clicked query selector using safeClick");
                                return true;
                            },

                            () -> {
                                logger.warn("safeClick failed, using smartClick");
                                smartClick(driver, queryBy);
                                return true;
                            }
                    )
            );

            // =====================================================
            // 2) EXTRACT COLUMN NAME
            // =====================================================
            String columnText = FallbackExecutor.execute(

                    List.of(
                            "GET_TEXT",
                            "GET_INNER_TEXT"
                    ),

                    List.of(

                            () -> {
                                WebElement el = wait.until(
                                        ExpectedConditions.presenceOfElementLocated(queryBy)
                                );

                                String text = el.getText() != null ? el.getText().trim() : "";
                                if (text.isEmpty()) {
                                    throw new RuntimeException("Empty text");
                                }

                                return text;
                            },

                            () -> {
                                WebElement el = wait.until(
                                        ExpectedConditions.presenceOfElementLocated(queryBy)
                                );

                                String text = el.getAttribute("innerText");
                                if (text == null || text.trim().isEmpty()) {
                                    throw new RuntimeException("Empty innerText");
                                }

                                return text.trim();
                            }
                    )
            );

            filter.setColumnName(columnText);
            logger.info("Captured columnName: {}", columnText);

            // =====================================================
            // 3) HANDLE OPERATION
            // =====================================================
            FallbackExecutor.execute(

                    List.of(
                            "RADIO_CLICK"
                    ),

                    List.of(

                            () -> {
                                String value = filter.getOperation().toString();
                                logger.info("operation selection {}", value);

                                WebElement queryElement = wait.until(
                                        ExpectedConditions.presenceOfElementLocated(queryBy)
                                );

                                WebElement filterContainer = queryElement.findElement(
                                        By.xpath("../..")
                                );

                                logger.info("Filter container located");

                                WebElement radio = filterContainer.findElement(
                                        By.cssSelector("input[type='radio'][value='" + value + "']")
                                );

                                logger.info("Radio found inside current filter block");

                                ((JavascriptExecutor) driver).executeScript(
                                        "arguments[0].click();",
                                        radio
                                );

                                logger.info("Operation selected via JS click: {}", value);
                                return true;
                            }
                    )
            );

            // =====================================================
            // 4) HANDLE VALUE INPUT
            // =====================================================
            String valueSelector = filter.getValueSelector();

            if (valueSelector == null || valueSelector.isEmpty()) {
                logger.warn("No valueSelector provided, skipping value input");
            }
            else {

                WebElement valueEl = wait.until(
                        ExpectedConditions.presenceOfElementLocated(
                                By.cssSelector(valueSelector)
                        )
                );

                String tag = valueEl.getTagName();
                String id = valueEl.getAttribute("id");
                String classes = valueEl.getAttribute("class");

                if (tag.equalsIgnoreCase("input") || tag.equalsIgnoreCase("textarea")) {

                    logger.info("considered input or textarea");

                    FallbackExecutor.execute(

                            List.of(
                                    "CLICKABLE_INPUT",
                                    "DIRECT_INPUT"
                            ),

                            List.of(

                                    () -> {
                                        WebElement clickable = wait.until(
                                                ExpectedConditions.elementToBeClickable(valueEl)
                                        );
                                        clickable.clear();
                                        clickable.sendKeys(filter.getValue());
                                        logger.info("Handled as normal input");
                                        return true;
                                    },

                                    () -> {
                                        valueEl.clear();
                                        valueEl.sendKeys(filter.getValue());
                                        logger.info("Handled as normal input");
                                        return true;
                                    }
                            )
                    );
                }
                else if (id != null && id.startsWith("select2-")) {

                    logger.info("id starts with select2-");

                    FallbackExecutor.execute(

                            List.of(
                                    "SELECT2_UI"
                            ),

                            List.of(
                                    () -> {
                                        handleSelect2Dropdown(driver, valueSelector, filter.getValue());
                                        logger.info("Handled as Select2 dropdown UI");
                                        return true;
                                    }
                            )
                    );
                }
                else if (tag.equalsIgnoreCase("select")
                        && classes != null
                        && classes.contains("select2-hidden-accessible")) {

                    logger.info("Handling as Select2 hidden select");

                    FallbackExecutor.execute(

                            List.of(
                                    "SELECT2_HIDDEN"
                            ),

                            List.of(
                                    () -> {
                                        selectSelect2(driver, valueSelector, filter.getValue());
                                        logger.info("Handled as Select2 hidden select");
                                        return true;
                                    }
                            )
                    );
                }
                else if (tag.equalsIgnoreCase("select")
                        && classes != null
                        && classes.contains("selectpicker")) {

                    logger.info("Handling as Bootstrap Select dropdown");

                    FallbackExecutor.execute(

                            List.of(
                                    "BOOTSTRAP_SELECT"
                            ),

                            List.of(
                                    () -> {
                                        handleBootstrapSelect(driver, valueSelector, filter.getValue());
                                        logger.info("Handled as Bootstrap Select dropdown");
                                        return true;
                                    }
                            )
                    );
                }
                else {

                    logger.info("Handling as generic dropdown");

                    FallbackExecutor.execute(

                            List.of(
                                    "GENERIC_DROPDOWN"
                            ),

                            List.of(
                                    () -> {
                                        safeClick(driver, By.cssSelector(valueSelector));

                                        try {
                                            WebElement option = wait.until(
                                                    ExpectedConditions.elementToBeClickable(
                                                            By.xpath("//*[text()='" + filter.getValue() + "']")
                                                    )
                                            );
                                            option.click();
                                        }
                                        catch (Exception ignored) {
                                            WebElement option = wait.until(
                                                    ExpectedConditions.elementToBeClickable(
                                                            By.xpath("//*[contains(text(),'" + filter.getValue() + "')]")
                                                    )
                                            );
                                            option.click();
                                        }

                                        logger.info("Handled as generic dropdown");
                                        return true;
                                    }
                            )
                    );
                }
            }

            // =====================================================
            // 4.5) HANDLE LOGICAL OPERATOR (AND/OR)
            // =====================================================
            FallbackExecutor.execute(

                    List.of(
                            "LOGICAL_OPERATOR",
                            "NO_OPERATOR"
                    ),

                    List.of(

                            () -> {
                                System.out.println("logical operator : " + filter.getLogicalOperator());

                                if (filter.getLogicalOperator() != null) {

                                    String operatorId = filter.getLogicalOperator();

                                    WebElement toggle = driver.findElement(
                                            By.cssSelector(operatorId)
                                    );

                                    ((JavascriptExecutor) driver).executeScript(
                                            "arguments[0].click();",
                                            toggle
                                    );

                                    logger.info("Toggled logical operator: {}", operatorId);
                                }
                                else {
                                    logger.info("already And operator is chosen!");
                                }

                                return true;
                            },

                            () -> true
                    )
            );
        }

        // =====================================================
        // 5) APPLY FILTER BUTTON
        // =====================================================
        FallbackExecutor.execute(

                List.of(
                        "APPLY_FILTER_BUTTON",
                        "NO_APPLY_BUTTON"
                ),

                List.of(

                        () -> {
                            if (currScenario.getApplyFilterBtn() != null) {

                                By applyBtn = By.cssSelector(currScenario.getApplyFilterBtn());

                                WebElement button = wait.until(
                                        ExpectedConditions.elementToBeClickable(applyBtn)
                                );

                                ((JavascriptExecutor) driver).executeScript(
                                        "arguments[0].scrollIntoView({block:'center'});",
                                        button
                                );

                                try {
                                    button.click();
                                }
                                catch (Exception e) {
                                    ((JavascriptExecutor) driver).executeScript(
                                            "arguments[0].click();",
                                            button
                                    );
                                }

                                logger.info("Clicked Apply Filter button");
                            }
                            return true;
                        },

                        () -> {
                            logger.info("No apply filter button found");
                            return true;
                        }
                )
        );

        scenario.setScenarioStatus(RunStatus.PASSED);
        resultTestCase.setResult("Passed");
    }
}