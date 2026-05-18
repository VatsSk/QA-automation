package com.testingautomation.testautomation.services.orchestratorService;


import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.enums.DateSelectionType;
import com.testingautomation.testautomation.enums.DateSelectionType;
import com.testingautomation.testautomation.enums.ManageColumnAction;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.enums.ScenarioType;
import com.testingautomation.testautomation.services.executorService.SeleniumExecutor;
import com.testingautomation.testautomation.services.fallback.FallbackExecutor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.testingautomation.testautomation.utils.ExceptionUtil.getUserFriendlyErrorMessage;

@Service
@RequiredArgsConstructor
public class ScenarioOrchestratorService {
    private final String resultsBaseDir = "test-results";
    private final ScreenshotService screenshotService;
    private final AssertionStepGenerator assertionStepGenerator;
    @Value("${storage.s3.base-prefix}")
    private  String basePrefix;
    private final Logger logger = LoggerFactory.getLogger(ScenarioOrchestratorService.class);

    // your existing components (assumed to be available)
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;
    private final RunRepository runRepository;
    private final MongoTemplate mongoTemplate;
    private final S3StorageService s3StorageService;


    /**
     * Top-level: execute the list of scenarios in sequence (one by one).
     * Keeps single driver/session alive (login should be done before calling this).
     */
    public Run executeScenarios(Run run, WebDriver driver, String globalRunId) {
        String baseS3Prefix =basePrefix+"/"+ run.getProjectId()+ "/" + run.getModuleId() + "/" + globalRunId;
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
                    System.out.println(scenarioResultsMap);
//                    current.setScenarioStatus(scenarioTestDto.getOverAllScenarioStatus());
                    break;
                }

            } catch (Exception e) {
                current.setScenarioStatus(RunStatus.FAILED);

                // Create user-friendly error message
                String userMessage = getUserFriendlyErrorMessage(e, current, i);

                // Log detailed error for debugging
                logger.error("Scenario #{} ({}) failed: {}", i + 1, current.getType(), e.getMessage(), e);

                // Stop execution by throwing user-friendly exception
                throw new GlobalExceptionHandler.RunnerIntegrationException(userMessage, e);
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
            throw new GlobalExceptionHandler.RunnerIntegrationException("Failed to create directory for VERIFY_PAGE scenario", e);
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

            } catch (TimeoutException e) {
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
            , Map<String, List<TestCaseDTO>> scenarioResultsMap,int scenarioSize,int currScenarioIdx) throws Exception {
        List<FieldDescriptor> fields = scannerService.scanPage(current.getUrl(), driver);
        logger.info("$$$$$$$$ CURRENT CSV FILEEE $$$$$$$$"+current.getCsv());
        List<TestCaseDTO>  testCases = csvLoader.loadFromS3(current.getCsv());
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);

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



    public int handleNavigation(WebDriver driver, List<Scenario> scenarios, int currIdx, int modalFormTcIdx, String baseS3Prefix, Run run,
                                Map<String, List<TestCaseDTO>> scenarioResultsMap) throws Exception {

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
            int stepCounter = 1;
            String scenarioId = (currIdx+1)+"";
            String scenarioPrefix =
                    baseS3Prefix + "/" + scenarioId;

            Scenario scenario = run.getScenariosList().get(currIdx);
            scenario.setScenarioBasePath(scenarioPrefix);
            run.getScenariosList().set(currIdx,scenario);

            // single generic testcase for this navigation scenario
            TestCaseDTO resultTestCase = new TestCaseDTO((modalFormTcIdx+1)+"", new HashMap<>());

            resultTestCase.setExpectedResult("Passed");
//            resultTestCases.add(resultTestCase);

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
                if (currScenario.getType() == ScenarioType.URL_NAV) {

                    logger.info("Navigating to URL: {}", currScenario.getUrl());
                    driver.get(currScenario.getUrl());
                    String url = screenshotService.takeScreenshot(
                            driver,
                            (modalFormTcIdx +1)+"",
                            "step passed" ,
                            navigationScreenshotDir,
                            scenarioPrefix
                    );

                    scenario.setScenarioStatus(RunStatus.PASSED);
                    resultTestCase.setResult("Passed");
                }
                else if (currScenario.getType() == ScenarioType.MODAL_NAV) {

                    logger.info("Opening modal using selector: {}", currScenario.getCssOpener());

                    By openerBy = By.cssSelector(currScenario.getCssOpener());

                    boolean clicked = false;

                    // 1️⃣ TRY SAFE CLICK FIRST
                    try {
                        safeClick(driver, openerBy);
                        clicked = true;
                        logger.info("Modal opened using safeClick");
                    } catch (Exception safeEx) {
                        logger.warn("safeClick failed, falling back to smartClick. Reason: {}", safeEx.getMessage());
                    }

                    // 2️⃣ FALLBACK TO SMART CLICK
                    if (!clicked) {
                        try {
                            smartClick(driver, openerBy);
                            clicked = true;
                            logger.info("Modal opened using smartClick");
                        } catch (Exception smartEx) {
                            throw new GlobalExceptionHandler.ResourceNotFoundException("Both safeClick and smartClick failed for modal opener "+smartEx.getMessage());
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
                else if(currScenario.getType() == ScenarioType.SEARCH_NAV){
                    currIdx = handleSearchNavScenario(
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

                    continue;
                }
                else if(currScenario.getType() == ScenarioType.FORM_MODAL){

                    String csvFile = currScenario.getCsv();
                    logger.info("$$$$$$$$ CURRENT CSV FILEEE $$$$$$$$"+csvFile);
                    List<TestCaseDTO> testCases = csvLoader.loadFromS3(csvFile);
                    if(modalFormTcIdx>=testCases.size()){
                        throw new GlobalExceptionHandler.InvalidCountException("Invalid test case index: " + modalFormTcIdx);
                    }
                    resultTestCase= testCases.get(modalFormTcIdx);
                    handleModalScenario(driver, currScenario, resultTestCase,scenarioPrefix,navigationScreenshotDir);

                    scenario.setScenarioStatus(RunStatus.PASSED);
                    resultTestCase.setResult("Passed");
                }
                else if (currScenario.getType() == ScenarioType.FILTER_NAV) {
                    handleFilterNavScenario(driver, wait, currScenario, scenario, resultTestCase);
                }
                else if (currScenario.getType() == ScenarioType.DATE_RANGE_NAV) {

                    DateRangeNavDto dateRange = currScenario.getDateRangeNavDto();

                    if (dateRange == null) {
                        throw new GlobalExceptionHandler.ResourceNotFoundException("DateRange configuration is missing");
                    }

                    // =========================
                    // Open Calendar
                    // =========================
                    WebElement inputElement = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.cssSelector(dateRange.getInputSelector())
                            )
                    );

                    inputElement.click();
                    screenshotService.takeScreenshot(
                            driver,
                            1+"",
                            "step passed",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );
                    // =========================
                    // Wait for Calendar Container
                    // =========================
                    String containerSelector =
                            dateRange.getCalendarContainerSelector() != null
                                    ? dateRange.getCalendarContainerSelector()
                                    : ".daterangepicker";

                    wait.until(
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.cssSelector(containerSelector)
                            )
                    );

                    // =========================
                    // PRESET MODE
                    // =========================
                    if (dateRange.getSelectionType() == DateSelectionType.PRESET) {

                        if (dateRange.getPreset() == null) {
                            throw new GlobalExceptionHandler.ResourceNotFoundException(
                                    "Preset value is required for PRESET selection type"
                            );
                        }

                        String preset = Arrays.stream(
                                        dateRange.getPreset().toString()
                                                .replace("_", " ")
                                                .toLowerCase()
                                                .split(" "))
                                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                                .collect(Collectors.joining(" "));

                        logger.info("Preset value = {}", preset);

                        // Scope to visible daterangepicker
                        String presetXpath =
                                "//div[contains(@class,'daterangepicker') and contains(@style,'display: block')]"
                                        + "//li[@data-range-key='" + preset + "']";

                        WebElement presetElement = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.xpath(presetXpath)
                                )
                        );

                        // Scroll into view
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView(true);",
                                presetElement
                        );

                        // Wait small moment for animation
                        Thread.sleep(300);

                        try {
                            wait.until(ExpectedConditions.elementToBeClickable(presetElement));
                            presetElement.click();
                        } catch (Exception e) {

                            logger.info("Normal click failed. Using JS click.");

                            ((JavascriptExecutor) driver).executeScript(
                                    "arguments[0].click();",
                                    presetElement
                            );
                        }

                        screenshotService.takeScreenshot(
                                driver,
                                "2",
                                "preset selected",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );
                    }

                    // =========================
                    // CUSTOM MODE
                    // =========================
                    else if (dateRange.getSelectionType()
                            == DateSelectionType.CUSTOM) {

                        if (dateRange.getStartDate() == null
                                || dateRange.getEndDate() == null) {

                            throw new GlobalExceptionHandler.ResourceNotFoundException(
                                    "StartDate and EndDate are required for CUSTOM mode"
                            );
                        }

                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofPattern(
                                        dateRange.getDateFormat() != null
                                                ? dateRange.getDateFormat()
                                                : "dd/MM/yyyy HH:mm"
                                );

                        LocalDateTime startDateTime =
                                LocalDateTime.parse(
                                        dateRange.getStartDate(),
                                        formatter
                                );

                        LocalDateTime endDateTime =
                                LocalDateTime.parse(
                                        dateRange.getEndDate(),
                                        formatter
                                );

                        // =========================
                        // SELECT START DATE
                        // =========================
                        selectDate(
                                driver,
                                wait,
                                containerSelector,
                                startDateTime.toLocalDate()
                        );

                        screenshotService.takeScreenshot(
                                driver,
                                "3",
                                "start date selected",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                        // =========================
                        // SELECT END DATE
                        // =========================
                        selectDate(
                                driver,
                                wait,
                                containerSelector,
                                endDateTime.toLocalDate()
                        );

                        screenshotService.takeScreenshot(
                                driver,
                                "4",
                                "end date selected",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                    }

                    // =========================
                    // APPLY BUTTON
                    // =========================
                    Boolean autoApply = dateRange.getAutoApply();

                    if (autoApply == null || !autoApply) {

                        String applySelector =
                                dateRange.getApplyButtonSelector() != null
                                        ? dateRange.getApplyButtonSelector()
                                        : ".applyBtn";

                        WebElement applyBtn = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.cssSelector(applySelector)
                                )
                        );

                        applyBtn.click();
                        screenshotService.takeScreenshot(
                                driver,
                                5+"",
                                "step passed",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );
                    }
                }
                else if(currScenario.getType()== ScenarioType.MANAGE_COL_NAV){
                    handleManageColumnScenario(driver, wait, currScenario);
                }

                Thread.sleep(1000);

            }
            catch (GlobalExceptionHandler.InvalidCountException e) {
                scenario.setScenarioStatus(RunStatus.FAILED);
                resultTestCase.setResult("Failed - Test case index out of bounds");

                logger.error("Navigation step failed at index {} type {} selector {}",
                        currIdx,
                        currScenario.getType(),
                        currScenario.getCssOpener(),
                        e);
                String urlSs= screenshotService.takeScreenshot(driver,(modalFormTcIdx+1)+"","error",navigationScreenshotDir,scenarioPrefix);
                throw e;
            }
            catch (Exception e) {
                scenario.setScenarioStatus(RunStatus.ERROR);
                resultTestCase.setResult("Error - " + e.getMessage());

                logger.error("Unexpected error while executing scenario at index {} type {}",
                        currIdx,
                        currScenario.getType(),
                        e);
                throw new GlobalExceptionHandler.ResourceNotFoundException("Error - " + e.getMessage());
            }
            try {
                scenarioResultsMap.computeIfAbsent(scenarioPrefix, k -> new ArrayList<>())
                        .add(resultTestCase);

                logger.info("Stored testcase {} in scenarioResultsMap for scenarioPrefix {}. Current count={}",
                        resultTestCase.getTestcaseId(),
                        scenarioPrefix,
                        scenarioResultsMap.get(scenarioPrefix).size());
                logger.info("Current status of map: {}",scenarioResultsMap.get(scenarioPrefix));

            } catch (Exception e) {
                logger.error("Failed to store testcase result in scenarioResultsMap for scenario at index {}", currIdx, e);
                throw new GlobalExceptionHandler.ResourceNotFoundException("Failed to store testcase result in scenarioResultsMap for scenario at index " + currIdx);
            }

            run.getScenariosList().set(currIdx, scenario);

            currIdx++;
        }

        logger.info("Navigation phase completed. Final index {}", currIdx);

        return currIdx;
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
            , Map<String, List<TestCaseDTO>> scenarioResultsMap) throws Exception {
        List<TestCaseDTO> testCases=null;

        int currEle=-1;

        try{
            currEle=handleNavigation(driver,scenarios,currIdx,0,baseS3Prefix,run,scenarioResultsMap);
        }catch (Exception ex){
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



        if(currModal.getType()==ScenarioType.ASSERT){
            logger.info("Index adjustment completed - original: {}, adjusted: {}, scenario type: {}",
                    currEle, currModal.getType());
            runAssertionGeneric(driver,currModal,baseS3Prefix,scenarios);
            return;
        }
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);
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
                    handleNavigation(driver,scenarios,currIdx,counterIdx,baseS3Prefix,run,scenarioResultsMap);
                logger.info("[{}] Completed modal testcase {}", tcRunId, tc);

            }

        }
        catch (Exception e) {
            logger.error("[{}] failed to open modal or execute tests: {}",scenarioPrefix, e.getMessage());
            throw new GlobalExceptionHandler.ResourceNotFoundException("Failed to open modal");
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
    ) throws Exception {

        int scenarioId = scenario.getSequenceNo();
        String scenarioPrefix = baseS3Prefix + "/" + scenarioId;

        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);

        List<StepAction> steps =
                assertionStepGenerator.generateAssertionSteps(
                        scenario.getAssertions()
                );

        logger.info("steps of Assert scenario : {}",steps);
        try {
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
            Path csvPath = csvLoader.writeScenarioCsv(testDtos,scenarioDir);
            String s3Key = scenarioPrefix;
            String finalCsvUrl = s3StorageService.uploadFile(csvPath, s3Key);
            scenario.setResultCsv(finalCsvUrl);


        } catch (Exception e) {
            logger.error("exception encountered "+ e.getMessage());
            throw new GlobalExceptionHandler.RunnerIntegrationException(e.getMessage(),e);
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
        By by = resolveLocator(cssSelector);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

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

            } catch (TimeoutException e) {
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

        } catch (TimeoutException e) {
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
            WebDriver driver,
            WebDriverWait wait,
            Scenario currScenario
    ) throws InterruptedException {

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
    ) throws InterruptedException {

        logger.info(
                "Executing NAV_SEARCH using selector: {} and value: {}",
                currScenario.getCssOpener(),
                currScenario.getValue()
        );

        WebElement opener = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector(currScenario.getCssOpener())
                )
        );

        String value = currScenario.getValue();

        logger.info(
                "Search element located: tag={}",
                opener.getTagName()
        );

        boolean isInputSearch =
                opener.getTagName().equalsIgnoreCase("input")
                        || "true".equals(
                        opener.getAttribute("contenteditable")
                );

        // =====================================================
        // CASE 1 → INPUT SEARCH FIELD
        // =====================================================

        if (isInputSearch) {

            logger.info("Detected input search field");

            FallbackExecutor.execute(

                    List.of(
                            "EMPLOYEE_DROPDOWN",
                            "TREE_SELECTOR",
                            "NO_ACTION"
                    ),

                    List.of(
                            () -> {

                                WebElement dropdownBtn =
                                        wait.until(
                                                ExpectedConditions
                                                        .elementToBeClickable(
                                                                By.cssSelector(
                                                                        ".dropdown-btn"
                                                                )
                                                        )
                                        );

                                dropdownBtn.click();

                                logger.info(
                                        "Opened employee dropdown"
                                );

                                return true;
                            },

                            () -> {

                                WebElement treeOpener =
                                        driver.findElement(
                                                By.cssSelector(
                                                        ".treeSelector-input-box"
                                                )
                                        );

                                if (!treeOpener.isDisplayed()) {
                                    throw new RuntimeException(
                                            "Tree selector not visible"
                                    );
                                }

                                treeOpener.click();

                                logger.info(
                                        "Opened tree selector dropdown"
                                );

                                return true;
                            },

                            () -> {

                                logger.info(
                                        "No dropdown opener needed"
                                );

                                return true;
                            }
                    )
            );

            opener.clear();
            opener.sendKeys(value);

            logger.info(
                    "Typed search value: {}",
                    value
            );

            screenshotService.takeScreenshot(
                    driver,
                    String.valueOf(modalFormTcIdx + 1),
                    "step passed",
                    navigationScreenshotDir,
                    scenarioPrefix
            );

            Thread.sleep(500);

            WebElement option = FallbackExecutor.execute(

                    List.of(
                            "DATA_TITLE",
                            "TITLE_LIST_SPAN",
                            "EXACT_TEXT",
                            "PARTIAL_TEXT"
                    ),

                    List.of(

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[@data-title='"
                                                                    + value
                                                                    + "']//input"
                                                    )
                                            )
                            ),

                            () -> wait.until(
                                    ExpectedConditions
                                            .presenceOfElementLocated(
                                                    By.xpath(
                                                            "//span[contains(@class,'tittle-list') and contains(text(),'"
                                                                    + value
                                                                    + "')]/preceding-sibling::input[@type='checkbox']"
                                                    )
                                            )
                            ),

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[text()='"
                                                                    + value
                                                                    + "']"
                                                    )
                                            )
                            ),

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[contains(text(),'"
                                                                    + value
                                                                    + "')]"
                                                    )
                                            )
                            )
                    )
            );

            try {

                option.click();

                logger.info("Clicked option normally");

            }
            catch (Exception clickEx) {

                logger.warn("Normal click failed, using JS click");

                ((JavascriptExecutor) driver)
                        .executeScript(
                                "arguments[0].click();",
                                option
                        );

                logger.info("Clicked option using JS click");
            }

            logger.info(
                    "Successfully selected option: {}",
                    value
            );

            screenshotService.takeScreenshot(
                    driver,
                    String.valueOf(modalFormTcIdx + 1),
                    "step passed",
                    navigationScreenshotDir,
                    scenarioPrefix
            );

            try {

                WebElement applyButton = driver.findElement(By.id("apply-button"));

                if (applyButton.isDisplayed()) {

                    applyButton.click();

                    logger.info("Clicked apply button");
                }

            }
            catch (Exception ignored) {

                logger.info("No apply button found");
            }

            driver.findElement(By.tagName("body")).click();

            logger.info("Closed dropdown using body click");

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
                    !driver.findElements(
                            By.cssSelector(".select2-container--default")
                    ).isEmpty()
                            ||
                            !driver.findElements(
                                    By.cssSelector(".select2-hidden-accessible")
                            ).isEmpty();

            if (isSelect2) {

                logger.info("Detected Select2 dropdown");

                selectSelect2(
                        driver,
                        currScenario.getCssOpener(),
                        value
                );

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

            wait.until(
                    ExpectedConditions.elementToBeClickable(opener)
            ).click();

            WebElement option = FallbackExecutor.execute(

                    List.of(
                            "DATA_TITLE",
                            "EXACT_TEXT",
                            "PARTIAL_TEXT"
                    ),

                    List.of(

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[@data-title='"
                                                                    + value
                                                                    + "']//input"
                                                    )
                                            )
                            ),

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[text()='"
                                                                    + value
                                                                    + "']"
                                                    )
                                            )
                            ),

                            () -> wait.until(
                                    ExpectedConditions
                                            .elementToBeClickable(
                                                    By.xpath(
                                                            "//*[contains(text(),'"
                                                                    + value
                                                                    + "')]"
                                                    )
                                            )
                            )
                    )
            );

            try {

                option.click();

            }
            catch (Exception e) {

                ((JavascriptExecutor) driver)
                        .executeScript(
                                "arguments[0].click();",
                                option
                        );
            }

            logger.info(
                    "Dropdown option selected successfully"
            );

            screenshotService.takeScreenshot(
                    driver,
                    String.valueOf(modalFormTcIdx + 1),
                    "step passed",
                    navigationScreenshotDir,
                    scenarioPrefix
            );

            driver.findElement(By.tagName("body")).click();

            logger.info("Closed dropdown");
        }

        scenario.setScenarioStatus(RunStatus.PASSED);
        resultTestCase.setResult("Passed");

        return currIdx + 1;
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