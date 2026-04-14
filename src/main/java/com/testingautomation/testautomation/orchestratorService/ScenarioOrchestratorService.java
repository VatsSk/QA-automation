package com.testingautomation.testautomation.orchestratorService;


import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.*;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.services.UiScannerService;
import com.testingautomation.testautomation.services.AssertionStepGenerator;
import com.testingautomation.testautomation.services.S3StorageService;
import com.testingautomation.testautomation.services.ScreenshotService;
import com.testingautomation.testautomation.tableSaw.TableSawService;
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
import java.util.*;

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
                    "step "+TimestampUtil.generateTimestamp(),
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
                        "step "+TimestampUtil.generateTimestamp(),
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
                        "step "+ TimestampUtil.generateTimestamp(),
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
                                        , Map<String, List<TestCaseDTO>> scenarioResultsMap,int scenarioLen,int currIdx) throws Exception {
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
                ResultRun runResult =executor.run(driver, current.getUrl(), steps, tcRunId,successMsg,scenarioDir,scenarioPrefix,expected,scenarioLen,currIdx);
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
            }else if (currScenario.getType() == ScenarioType.ASSERT) {
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

                    WebElement opener = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector(currScenario.getCssOpener())
                    ));

                    opener.click();
                    String url = screenshotService.takeScreenshot(
                            driver,
                            (modalFormTcIdx +1)+"",
                            "step passed",
                            navigationScreenshotDir,
                            scenarioPrefix
                    );

                    logger.info("Modal opener clicked successfully");

                    scenario.setScenarioStatus(RunStatus.PASSED);
                    resultTestCase.setResult("Passed");
                }
                else if (currScenario.getType() == ScenarioType.SEARCH_NAV) {

                    logger.info("Executing NAV_SEARCH using selector: {} and value: {}",
                            currScenario.getCssOpener(), currScenario.getValue());

                    WebElement opener = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(currScenario.getCssOpener())
                    ));

                    String value = currScenario.getValue();

                    logger.info("Search element located: tag={}", opener.getTagName());

                    // CASE 1 — search input field
                    if (opener.getTagName().equalsIgnoreCase("input") ||
                            "true".equals(opener.getAttribute("contenteditable"))) {

                        logger.info("Detected input search field");

                        // open tree selector if present
                        try {
                            WebElement treeOpener = driver.findElement(By.cssSelector(".treeSelector-input-box"));
                            if (treeOpener.isDisplayed()) {
                                treeOpener.click();
                                logger.info("Opened tree selector dropdown");
                            }
                        } catch (Exception ignored) {
                            logger.info("No tree selector opener found, continuing normal search");
                        }

                        opener.clear();
                        opener.sendKeys(value);
                        // 📸 Step 1 → after typing
                        screenshotService.takeScreenshot(
                                driver,
                                (modalFormTcIdx +1)+"",
                                "step passed",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                        logger.info("Typed search value: {}", value);

                        // wait for filtering
                        Thread.sleep(500);

                        WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//*[@data-title='" + value + "']//input")
                        ));

                        option.click();

                        logger.info("Clicked checkbox for option: {}", value);
                        // 📸 Step 2 → after selecting option
                        screenshotService.takeScreenshot(
                                driver,
                                (modalFormTcIdx +1)+"",
                                "step passed",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                        // close dropdown to apply filter
//                        opener.sendKeys(Keys.TAB);
//                        logger.info("Closed dropdown using TAB");
                        driver.findElement(By.tagName("body")).click();
                        logger.info("Closed dropdown using body click fallback");
                        // 📸 Step 3 → after closing dropdown
                        screenshotService.takeScreenshot(
                                driver,
                                (modalFormTcIdx +1)+"",
                                "step passed",
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                    }

                    // CASE 2 — dropdown opener
                    else {

                        logger.info("Detected dropdown/tree selector opener");

                        wait.until(ExpectedConditions.elementToBeClickable(opener)).click();

                        try {

                            logger.info("Trying checkbox selection using data-title");

                            WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                    By.xpath("//*[@data-title='" + value + "']//input")
                            ));

                            option.click();

                            logger.info("Clicked checkbox for option: {}", value);
                            screenshotService.takeScreenshot(
                                    driver,
                                    (modalFormTcIdx +1)+"",
                                    "step passed",
                                    navigationScreenshotDir,
                                    scenarioPrefix
                            );

                        }
                        catch (Exception ignored) {

                            logger.info("data-title failed. Trying exact text");

                            try {

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[text()='" + value + "']")
                                ));

                                option.click();

                                logger.info("Option selected using exact text");
                                screenshotService.takeScreenshot(
                                        driver,
                                        (modalFormTcIdx +1)+"",
                                        "step passed",
                                        navigationScreenshotDir,
                                        scenarioPrefix
                                );

                            }
                            catch (Exception ignored2) {

                                logger.info("Exact text failed. Trying partial text");

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[contains(text(),'" + value + "')]")
                                ));

                                option.click();

                                logger.info("Option selected using partial text");
                                screenshotService.takeScreenshot(
                                        driver,
                                        (modalFormTcIdx +1)+"",
                                        "step passed",
                                        navigationScreenshotDir,
                                        scenarioPrefix
                                );
                            }
                        }
                        driver.findElement(By.tagName("body")).click();
                        logger.info("Closed dropdown using body click fallback");
                        screenshotService.takeScreenshot(
                                driver,
                                (modalFormTcIdx +1)+"",
                                "step err" + TimestampUtil.generateTimestamp(),
                                navigationScreenshotDir,
                                scenarioPrefix
                        );

                    }

                    scenario.setScenarioStatus(RunStatus.PASSED);
                    resultTestCase.setResult("Passed");
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
                throw e;
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
            }

            run.getScenariosList().set(currIdx, scenario);

            currIdx++;
        }

        logger.info("Navigation phase completed. Final index {}", currIdx);

        return currIdx;
    }
    public void runModalGeneric(WebDriver driver,List<Scenario> scenarios,String successMsg,int currIdx,String baseS3Prefix,Run run
                                            , Map<String, List<TestCaseDTO>> scenarioResultsMap) throws Exception {
        List<TestCaseDTO> testCases=null;

        int currEle=-1;

        try{
            currEle=handleNavigation(driver,scenarios,currIdx,0,baseS3Prefix,run,scenarioResultsMap);
        }catch (GlobalExceptionHandler.InvalidCountException ex){
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
            runAssertionGeneric(driver,currModal,baseS3Prefix);
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
            String baseS3Prefix
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
            executor.runAssertionSteps(driver, steps,scenarioDir,scenarioPrefix);
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
            System.out.println("exception encountered "+ e.getMessage());
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


}