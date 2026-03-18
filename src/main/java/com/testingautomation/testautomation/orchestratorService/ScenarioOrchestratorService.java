package com.testingautomation.testautomation.orchestratorService;


import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.*;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.requestDto.TestConfigPayload;
import com.testingautomation.testautomation.requestDto.TestConfigRequest;
import com.testingautomation.testautomation.scan.UiScannerService;
import com.testingautomation.testautomation.services.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ScenarioOrchestratorService {
    private final String resultsBaseDir = "test-results";
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

    private void appendNavigationScreenshot(String runId, int scenarioIndex, String url) {

        Query query = new Query(Criteria.where("_id").is(runId));

        Update update = new Update()
                .push("scenariosList." + scenarioIndex + ".ssPaths", url);

        mongoTemplate.updateFirst(query, update, Run.class);
    }

    /**
     * Top-level: execute the list of scenarios in sequence (one by one).
     * Keeps single driver/session alive (login should be done before calling this).
     */
    public Run executeScenarios(Run run, WebDriver driver, String globalRunId) {
        String baseS3Prefix =basePrefix+"/"+
                run.getProjectId()+ "/" +
                        run.getModuleId() + "/" +
                        globalRunId;
        List<Scenario> scenarios = run.getScenariosList();
        logger.info("[{}] Executing {} scenarios sequentially", globalRunId, scenarios.size());
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario current = scenarios.get(i);
            String scenarioId = (i+1)+"";
            String scenarioPrefix =
                    baseS3Prefix + "/" + scenarioId;
            String scenarioRunId = globalRunId + "_S" + (i + 1) + (current.getId() != null ? "_" + current.getId() : "");
            try {
                ScenarioTestDto scenarioTestDto=null;
                if (current.getType() == ScenarioType.URL) {
                    // check next scenario
                    scenarioTestDto=runUrlGeneric(
                            driver,
                            current.getUrl(),
                            current.getCsv(),
                            scenarioRunId,
                            run.getResultStatement(),
                            scenarioPrefix,
                            i,
                            scenarios.size()
                    );
                    Scenario dbScenario = run.getScenariosList().get(i);

                    dbScenario.setResultCsv(scenarioTestDto.getResultCsv());
                    run.setStatus(scenarioTestDto.getOverAllScenarioStatus());
                }
                else{
                    scenarioTestDto=runModalGeneric(
                            driver,
                            scenarioRunId,
                            scenarios,
                            run.getResultStatement(),
                            i,
                            baseS3Prefix,
                            run

                    );
                    Scenario dbScenario = run.getScenariosList().get(i);

                    dbScenario.setResultCsv(scenarioTestDto.getResultCsv());
                    run.setStatus(scenarioTestDto.getOverAllScenarioStatus());
                    break;
                }

            } catch (Exception e) {
                run.setStatus(RunStatus.ERROR);

                logger.error("[{}] scenario failed but continuing: {}",scenarioRunId,e.getMessage(), e);
            }
        }





//        run.setStatus(RunStatus.COMPLETED);
        runRepository.save(run);
        return run;
    }

    /**
     * Generic URL method:
     * - scan the page at url
     * - load testcases from csvPath
     * - loop over each testcase, generate steps and execute using executor.run(...)
     */
    public ScenarioTestDto runUrlGeneric(WebDriver driver, String url, String csvUrl, String runIdPrefix,String successMsg,String scenarioPrefix,int currIdx,int sizeOfScenarios) throws Exception {
        logger.info("[{}] runUrlGeneric start for URL: {}", runIdPrefix, url);
        List<TestCaseDTO> testCases=null;
        // 1) scan page (fields)
        List<FieldDescriptor> fields = scannerService.scanPage(url, driver);
        logger.info("[{}] scanned {} fields", runIdPrefix, fields.size());
        // 2) load testcases for this scenario
        logger.info("$$$$$$$$ CURRENT CSV FILEEE $$$$$$$$"+csvUrl);
        testCases = csvLoader.loadFromS3(csvUrl);
        logger.info("[{}] loaded {} testcases", runIdPrefix, testCases.size());
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
                ResultRun runResult =executor.run(driver, url, steps, tcRunId,successMsg,scenarioDir,scenarioPrefix,currIdx,sizeOfScenarios,expected);


                if (expected != null) {
                    if(expected.equalsIgnoreCase(runResult.getStatus()) ){
                        tc.setResult("Passed");
                        totalPasses++;
                    }else{
                        tc.setResult(runResult.getStatus());
                        totalFails++;
                    }
                }


                tc.setUrls(runResult.getScreenshots());
                logger.info("[{}] Completed testcase {}", tcRunId, tc);
            } catch (Exception e) {
                logger.error("[{}] testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
            }
        }
        Path scenarioCsv = csvLoader.writeScenarioCsv(testCases, scenarioDir);
        String s3Key = scenarioPrefix + "/scenario-results.csv";

        String finalCsvUrl=s3StorageService.uploadFile(scenarioCsv, s3Key);


        ScenarioTestDto scenarioTestDto=new ScenarioTestDto(testCases,finalCsvUrl);
        if (totalPasses == testCases.size()) {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.PASSED);
        }
        else if (totalFails == testCases.size()) {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.FAILED);
        }
        else {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.PARTIAL);

        }


        return scenarioTestDto;
    }



    public int handleNavigation(WebDriver driver, List<Scenario> scenarios, int currIdx,int modalFormTcIdx,String baseS3Prefix) {

        logger.info("Starting navigation handling from index {}", currIdx);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        while (currIdx < scenarios.size()) {

            Scenario currScenario = scenarios.get(currIdx);

            logger.info("Processing scenario index {} type {}", currIdx, currScenario.getType());

            if (currScenario.getType() == ScenarioType.MODAL) {
                logger.info("Reached MODAL scenario at index {}, stopping navigation phase", currIdx);
                return currIdx;
            }

            try {

                if (currScenario.getType() == ScenarioType.URL_NAV) {

                    logger.info("Navigating to URL: {}", currScenario.getUrl());
                    driver.get(currScenario.getUrl());

                }
                else if (currScenario.getType() == ScenarioType.MODAL_NAV) {

                    logger.info("Opening modal using selector: {}", currScenario.getCssOpener());

                    WebElement opener = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector(currScenario.getCssOpener())
                    ));

                    opener.click();

                    logger.info("Modal opener clicked successfully");

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

                        logger.info("Typed search value: {}", value);

                        // wait for filtering
                        Thread.sleep(500);

                        WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//*[@data-title='" + value + "']//input")
                        ));

                        option.click();

                        logger.info("Clicked checkbox for option: {}", value);

                        // close dropdown to apply filter
//                        opener.sendKeys(Keys.TAB);
//                        logger.info("Closed dropdown using TAB");
                        driver.findElement(By.tagName("body")).click();
                        logger.info("Closed dropdown using body click fallback");

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

                        }
                        catch (Exception ignored) {

                            logger.info("data-title failed. Trying exact text");

                            try {

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[text()='" + value + "']")
                                ));

                                option.click();

                                logger.info("Option selected using exact text");

                            }
                            catch (Exception ignored2) {

                                logger.info("Exact text failed. Trying partial text");

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[contains(text(),'" + value + "')]")
                                ));

                                option.click();

                                logger.info("Option selected using partial text");
                            }
                        }

                        // close dropdown to apply filter
//                        opener.sendKeys(Keys.TAB);
//                        logger.info("Closed dropdown using TAB");
                        driver.findElement(By.tagName("body")).click();
                        logger.info("Closed dropdown using body click fallback");

                    }
                }
                else if(currScenario.getType() == ScenarioType.FORM_MODAL){
//                  modalFormTcIdx= modalFormTcIdx==0?0:modalFormTcIdx--;

                    String csvFile = currScenario.getCsv();
                    logger.info("$$$$$$$$ CURRENT CSV FILEEE $$$$$$$$"+csvFile);
                    List<TestCaseDTO> testCases = csvLoader.loadFromS3(csvFile);
                    TestCaseDTO tc= testCases.get(modalFormTcIdx);
                    handleModalScenario(driver, currScenario, tc);

                }

                Thread.sleep(1000);

            }
            catch (Exception e) {

                logger.error("Navigation step failed at index {} type {} selector {}",
                        currIdx,
                        currScenario.getType(),
                        currScenario.getCssOpener(),
                        e);
            }

            currIdx++;
        }

        logger.info("Navigation phase completed. Final index {}", currIdx);

        return currIdx;
    }
    public ScenarioTestDto runModalGeneric(WebDriver driver, String runIdPrefix,List<Scenario> scenarios,String successMsg,int currIdx,String baseS3Prefix,Run run) throws Exception {
        List<TestCaseDTO> testCases=null;

        int currEle=handleNavigation(driver,scenarios,currIdx,0,baseS3Prefix);
        String scenarioPrefix =
                baseS3Prefix + "/scenarios-" + currEle;
        Scenario currModal=scenarios.get(currEle);
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);
        int counterIdx=0;
        int totalPasses = 0;
        int totalFails = 0;
        try {

            // load modal testcases
            testCases = csvLoader.loadFromS3(currModal.getCsv());
            logger.info("[{}] loaded {} modal testcases from", runIdPrefix, testCases.size());

            for (TestCaseDTO tc : testCases) {
                String tcRunId = tc.getTestcaseId();
                List<FieldDescriptor> modalFields = scannerService.scanCurrentPage(driver);
                logger.info("[{}] scanned {} modal fields", runIdPrefix, modalFields.size());
                counterIdx++;
                try {
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
                        handleNavigation(driver,scenarios,currIdx,counterIdx,baseS3Prefix);
                    logger.info("[{}] Completed modal testcase {}", tcRunId, tc);
                } catch (Exception e) {
                    logger.error("[{}] modal testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            logger.error("[{}] failed to open modal or execute tests: {}", runIdPrefix, e.getMessage(), e);
        }
        Path scenarioCsv = csvLoader.writeScenarioCsv(testCases, scenarioDir);
        String s3Key = scenarioPrefix + "/scenario-results.csv";

        String finalCsvUrl=s3StorageService.uploadFile(scenarioCsv, s3Key);
        ScenarioTestDto scenarioTestDto=new ScenarioTestDto(testCases,finalCsvUrl);
        if (totalPasses == testCases.size()) {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.PASSED);
        }
        else if (totalFails == testCases.size()) {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.FAILED);
        }
        else {
            scenarioTestDto.setOverAllScenarioStatus(RunStatus.PARTIAL);
        }

        return scenarioTestDto;
    }

    public List<ScenarioDescriptor> scenarioDescriptorMapper(TestConfigPayload payload,
                                                             MultipartHttpServletRequest request){
        List<ScenarioDescriptor> scenarios = new ArrayList<>();

        for (TestConfigRequest req : payload.getTests()) {

            // 1. Grab the exact file using the fileKey (e.g., "file_0")
            MultipartFile csvFile = request.getFile(req.getFileKey());

            // 2. Safely parse the Enum type
            ScenarioDescriptor.Type scenarioType;
            try {
                scenarioType = ScenarioDescriptor.Type.valueOf(req.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }

            // 3. Construct YOUR actual ScenarioDescriptor
            ScenarioDescriptor descriptor = new ScenarioDescriptor(
                    scenarioType,
                    req.getId()==null?req.getOpenerCss(): req.getId(),
                    req.getUrl(),
                    req.getOpenerCss(),
                    csvFile,
                    req.getIsClick(),
                    req.getClickCss(),
                    req.getValue()
            );

            // 4. Add it to our list
            scenarios.add(descriptor);

        }
        return  scenarios;
    }


    public File zipTestResults(String runId) throws IOException {
        Path baseDir = Paths.get("test-results");
        String zipFileName = "screenshots_" + runId + ".zip";
        Path zipPath = baseDir.resolve(zipFileName);
        try (ZipOutputStream zs =
                     new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

            Files.list(baseDir)
                    .filter(p -> p.getFileName().toString().startsWith(runId))
                    .forEach(folder -> {
                        try {
                            Files.walk(folder)
                                    .filter(p -> !Files.isDirectory(p))
                                    .forEach(file -> {
                                        try {
                                            ZipEntry zipEntry =
                                                    new ZipEntry(baseDir.relativize(file).toString());

                                            zs.putNextEntry(zipEntry);
                                            Files.copy(file, zs);
                                            zs.closeEntry();

                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        return zipPath.toFile();
    }

    private void handleModalScenario(
            WebDriver driver,
            Scenario scenario,
            TestCaseDTO tc
    ) {

        String cssSelector = scenario.getCssOpener();
        String value = scenario.getValue();
        boolean isClick =scenario.getClickCss()!=null;
        logger.info("is click : {}",isClick);
        boolean isSearch = !isClick;
        String secondId = scenario.getClickCss();
        logger.info("FORM_MODAL scenario details -> openerCss='{}', value='{}', clickCss='{}', isClick={}, isSearch={}, clickCss='{}'",
                cssSelector,
                value,
                scenario.getClickCss(),
                isClick,
                isSearch,
                secondId
        );


        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement element = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector(cssSelector))
        );

        String tag = element.getTagName();

        // =========================
        // HANDLE SELECT DROPDOWN
        // =========================

        if ("select".equalsIgnoreCase(tag)) {

            boolean isSelect2 = element.getAttribute("class") != null &&
                    element.getAttribute("class").contains("select2-hidden-accessible");

            if (isSelect2) {

                handleSelect2(driver, element, value);

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
                null,
                null,
                tc.getExpectedResult()

        );
    }
    private void handleSelect2(WebDriver driver, WebElement selectElement, String value) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        String selectId = selectElement.getAttribute("id");

        WebElement container = driver.findElement(
                By.xpath("//select[@id='" + selectId + "']/following-sibling::span")
        );

        container.click();

        try {

            // TRY SEARCH MODE
            WebElement search = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector(".select2-search__field"))
            );

            search.clear();
            search.sendKeys(value);

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
    }
}