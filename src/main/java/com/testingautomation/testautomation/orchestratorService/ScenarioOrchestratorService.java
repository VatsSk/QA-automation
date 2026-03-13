package com.testingautomation.testautomation.orchestratorService;

import com.testingautomation.testautomation.dto.*;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.repo.RunRepository;
import com.testingautomation.testautomation.scan.UiScannerService;
import com.testingautomation.testautomation.services.S3StorageService;
import com.testingautomation.testautomation.services.ScreenshotService;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@Service
public class ScenarioOrchestratorService {
    private final String resultsBaseDir = "test-results";
    private final Logger logger = LoggerFactory.getLogger(ScenarioOrchestratorService.class);

    // your existing components (assumed to be available)
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;
    private final S3StorageService s3StorageService;
    private final ScreenshotService screenshotService;
    private RunRepository runRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    public ScenarioOrchestratorService(CsvTestCaseLoader csvLoader,
                                       UiScannerService scannerService,
                                       StepGenerator stepGenerator,
                                       SeleniumExecutor executor, S3StorageService s3StorageService, RunRepository runRepository, ScreenshotService screenshotService) {
        this.csvLoader = csvLoader;
        this.scannerService = scannerService;
        this.stepGenerator = stepGenerator;
        this.executor = executor;
        this.s3StorageService = s3StorageService;
        this.runRepository= runRepository;
        this.screenshotService = screenshotService;
    }

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
    public Run executeScenarios(Run run,WebDriver driver,String globalRunId) {
        String baseS3Prefix =
                run.getProjectId()+ "/" +
                        run.getModuleId() + "/" +
                        globalRunId;
        List<ScenarioDescriptor> scenarios = run.getScenariosList();
        logger.info("[{}] Executing {} scenarios sequentially", globalRunId, scenarios.size());
        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioDescriptor current = scenarios.get(i);
            String scenarioId = i+"";
            String scenarioPrefix =
                    baseS3Prefix + "/scenarios/" + scenarioId;
            String scenarioRunId = globalRunId + "_S" + (i + 1) + (current.getId() != null ? "_" + current.getId() : "");
            try {
                ScenarioTestDto scenarioTestDto=null;
                if (current.getType() == ScenarioDescriptor.Type.URL) {
                    // check next scenario
                        scenarioTestDto=runUrlGeneric(
                                driver,
                                current.getUrl(),
                                current.getCsvUrl(),
                                scenarioRunId,
                                run.getResultStatement(),
                                scenarioPrefix,
                                i,
                                scenarios.size()
                        );
                    ScenarioDescriptor dbScenario = run.getScenariosList().get(i);

                    dbScenario.setResultCsvPath(scenarioTestDto.getResultCsv());
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
                    ScenarioDescriptor dbScenario = run.getScenariosList().get(i);

                    dbScenario.setResultCsvPath(scenarioTestDto.getResultCsv());
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
        testCases = csvLoader.loadFromS3(csvUrl);
        logger.info("[{}] loaded {} testcases", runIdPrefix, testCases.size());
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);

        int totalPasses = 0;
        int totalFails = 0;

        // 3) for each testcase -> generate steps & run
        for (TestCaseDTO tc : testCases) {

            String tcRunId ="tc_" + tc.getId();
            try {
                logger.info("[{}] Generating steps for testcase {}", tcRunId, tc.getId());
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


    public int handleNavigation(WebDriver driver, List<ScenarioDescriptor> scenarios, int currIdx,Run run) {
        String baseS3Prefix =
                run.getProjectId() + "/" +
                        run.getModuleId() + "/" +
                        run.getId();
        String scenarioId = currIdx+"";
        String scenarioPrefix =
                baseS3Prefix + "/scenarios/" + scenarioId;
        Path navigationScreenshotDir =
                Paths.get(resultsBaseDir, scenarioPrefix, "navigation", "screenshots");

        try {
            Files.createDirectories(navigationScreenshotDir);
        } catch (IOException e) {
            logger.error("Failed creating navigation screenshot directory", e);
        }

        logger.info("Starting navigation handling from index {}", currIdx);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        while (currIdx < scenarios.size()) {
            ScenarioDescriptor currScenario = scenarios.get(currIdx);
            logger.info("Processing scenario index {} type {}", currIdx, currScenario.getType());

            if (currScenario.getType() == ScenarioDescriptor.Type.MODAL) {
                logger.info("Reached MODAL scenario at index {}, stopping navigation phase", currIdx);
                return currIdx;
            }
            try {

                if (currScenario.getType() == ScenarioDescriptor.Type.NAV_URL) {

                    logger.info("Navigating to URL: {}", currScenario.getUrl());
                    driver.get(currScenario.getUrl());
                    String url = screenshotService.takeScreenshot(
                            driver,
                            "navigation",
                            "nav_url_" + currIdx,
                            navigationScreenshotDir,
                            scenarioPrefix + "/navigation"
                    );
                    appendNavigationScreenshot(run.getId(), currIdx, url);



                }
                else if (currScenario.getType() == ScenarioDescriptor.Type.NAV_MODAL) {

                    logger.info("Opening modal using selector: {}", currScenario.getOpenerCss());

                    WebElement opener = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector(currScenario.getOpenerCss())
                    ));

                    opener.click();

                    String url = screenshotService.takeScreenshot(
                            driver,
                            "nav_modal",
                            "nav_url_" + currIdx,
                            navigationScreenshotDir,
                            scenarioPrefix + "/modalNav"
                    );
                    appendNavigationScreenshot(run.getId(), currIdx, url);

                    logger.info("Modal opener clicked successfully");

                }
                else if (currScenario.getType() == ScenarioDescriptor.Type.NAV_SEARCH) {

                    logger.info("Executing NAV_SEARCH using selector: {} and value: {}",
                            currScenario.getOpenerCss(), currScenario.getValue());

                    WebElement opener = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(currScenario.getOpenerCss())
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

                        String url = screenshotService.takeScreenshot(
                                driver,
                                "searchNav",
                                "nav_url_" + currIdx,
                                navigationScreenshotDir,
                                scenarioPrefix + "/searchNav"
                        );
                        appendNavigationScreenshot(run.getId(), currIdx, url);

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
                            String url = screenshotService.takeScreenshot(
                                    driver,
                                    "searchNav",
                                    "nav_url_" + currIdx,
                                    navigationScreenshotDir,
                                    scenarioPrefix + "/searchNav"
                            );
                            appendNavigationScreenshot(run.getId(), currIdx, url);

                        }
                        catch (Exception ignored) {

                            logger.info("data-title failed. Trying exact text");

                            try {

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[text()='" + value + "']")
                                ));

                                option.click();

                                logger.info("Option selected using exact text");
                                String url = screenshotService.takeScreenshot(
                                        driver,
                                        "searchNav",
                                        "nav_url_" + currIdx,
                                        navigationScreenshotDir,
                                        scenarioPrefix + "/searchNav"
                                );
                                appendNavigationScreenshot(run.getId(), currIdx, url);

                            }
                            catch (Exception ignored2) {

                                logger.info("Exact text failed. Trying partial text");

                                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[contains(text(),'" + value + "')]")
                                ));

                                option.click();

                                logger.info("Option selected using partial text");
                                String url = screenshotService.takeScreenshot(
                                        driver,
                                        "searchNav",
                                        "nav_url_" + currIdx,
                                        navigationScreenshotDir,
                                        scenarioPrefix + "/searchNav"
                                );
                                appendNavigationScreenshot(run.getId(), currIdx, url);
                            }
                        }

                        // close dropdown to apply filter
//                        opener.sendKeys(Keys.TAB);
//                        logger.info("Closed dropdown using TAB");
                        driver.findElement(By.tagName("body")).click();
                        logger.info("Closed dropdown using body click");
                        String url = screenshotService.takeScreenshot(
                                driver,
                                "searchNav",
                                "nav_url_" + currIdx,
                                navigationScreenshotDir,
                                scenarioPrefix + "/searchNav"
                        );
                        appendNavigationScreenshot(run.getId(), currIdx, url);

                    }
                }

                Thread.sleep(500);

            }
            catch (Exception e) {

                logger.error("Navigation step failed at index {} type {} selector {}",
                        currIdx,
                        currScenario.getType(),
                        currScenario.getOpenerCss(),
                        e);
            }

            currIdx++;
        }

        logger.info("Navigation phase completed. Final index {}", currIdx);

        return currIdx;
    }

    public ScenarioTestDto runModalGeneric(WebDriver driver, String runIdPrefix,List<ScenarioDescriptor> scenarios,String successMsg,int currIdx,String baseS3Prefix,Run run) throws Exception {
        List<TestCaseDTO> testCases=null;

        int currEle=handleNavigation(driver,scenarios,currIdx,run);
        String scenarioPrefix =
                baseS3Prefix + "/scenarios/" + currEle;
       ScenarioDescriptor currModal=scenarios.get(currEle);
        Path scenarioDir = Paths.get(resultsBaseDir, scenarioPrefix);
        Files.createDirectories(scenarioDir);
        int counterIdx=0;
        int totalPasses = 0;
        int totalFails = 0;
        try {

            // load modal testcases
            testCases = csvLoader.loadFromS3(currModal.getCsvUrl());
            logger.info("[{}] loaded {} modal testcases from", runIdPrefix, testCases.size());

            for (TestCaseDTO tc : testCases) {
                String tcRunId = runIdPrefix + "_" + tc.getId();
                List<FieldDescriptor> modalFields = scannerService.scanCurrentPage(driver);
                logger.info("[{}] scanned {} modal fields", runIdPrefix, modalFields.size());
                counterIdx++;
                try {
                    List<StepAction> steps = stepGenerator.generateSteps(modalFields, tc);
                    logger.info("[{}] Executing {} modal steps", tcRunId, steps.size());
                    String expected = tc.getExpectedResult();
                    com.testingautomation.testautomation.dto.ResultRun resultRun =executor.runOnRenderedPage(driver, steps, tcRunId,successMsg,scenarioDir,scenarioPrefix,expected);
                    if (expected != null && expected.equalsIgnoreCase(resultRun.getStatus())) {
                        tc.setResult("Passed");
                        totalPasses++;
                    } else {
                        tc.setResult(resultRun.getStatus());
                        totalFails++;
                    }
                    tc.setUrls(resultRun.getScreenshots());
                    if(counterIdx<testCases.size())
                        handleNavigation(driver,scenarios,currIdx,run);
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

//    public ScenarioDescriptorModal scenarioDescriptorMapper(TestConfigPayload payload,
//                                                             MultipartHttpServletRequest request){
//        List<ScenarioDescriptor> scenarios = new ArrayList<>();
//
//        for (TestConfigRequest req : payload.getTests()) {
//
//            // 1. Grab the exact file using the fileKey (e.g., "file_0")
//            MultipartFile csvFile = request.getFile(req.getFileKey());
//
//            // 2. Safely parse the Enum type
//            ScenarioDescriptor.Type scenarioType;
//            try {
//                scenarioType = ScenarioDescriptor.Type.valueOf(req.getType().toUpperCase());
//            } catch (IllegalArgumentException e) {
//                return null;
//            }
//
//            // 3. Construct YOUR actual ScenarioDescriptor
//            ScenarioDescriptor descriptor = new ScenarioDescriptor(
//                    scenarioType,
//                    req.getId()==null?req.getOpenerCss(): req.getId(),
//                    req.getUrl(),
//                    req.getOpenerCss(),
//                    csvFile,
//                    req.getValue()
//            );
//
//            // 4. Add it to our list
//            scenarios.add(descriptor);
//
//        }
//
//        ScenarioDescriptorModal scenarioDescriptorModal=new ScenarioDescriptorModal();
//        scenarioDescriptorModal.setTests(scenarios);
//        scenarioDescriptorModal.setRunId(payload.getRunId());
//            return  scenarioDescriptorModal;
//    }


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
}