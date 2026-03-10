package com.testingautomation.testautomation.orchestratorService;

import com.testingautomation.testautomation.dto.TestConfigPayload;
import com.testingautomation.testautomation.dto.TestConfigRequest;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.ScenarioDescriptor;
import com.testingautomation.testautomation.model.StepAction;
import com.testingautomation.testautomation.model.TestCase;
import com.testingautomation.testautomation.scan.UiScannerService;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@Service
public class ScenarioOrchestratorService {
    private final Logger logger = LoggerFactory.getLogger(ScenarioOrchestratorService.class);

    // your existing components (assumed to be available)
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;

    public ScenarioOrchestratorService(CsvTestCaseLoader csvLoader,
                                       UiScannerService scannerService,
                                       StepGenerator stepGenerator,
                                       SeleniumExecutor executor) {
        this.csvLoader = csvLoader;
        this.scannerService = scannerService;
        this.stepGenerator = stepGenerator;
        this.executor = executor;
    }

    /**
     * Top-level: execute the list of scenarios in sequence (one by one).
     * Keeps single driver/session alive (login should be done before calling this).
     */
    public void executeScenarios(WebDriver driver,List<ScenarioDescriptor> scenarios,String globalRunId,String successMsg) {
        logger.info("[{}] Executing {} scenarios sequentially", globalRunId, scenarios.size());
        ScenarioDescriptor lastUrlScenario = null;
        int lastUrlIdx=-1;
        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioDescriptor current = scenarios.get(i);
            String scenarioType = current.getType().name();

            String scenarioName = current.getId() != null
                    ? current.getId()
                    : "scenario";

            String scenarioRunId =
                    globalRunId +
                            "_S" + (i + 1);
            try {
                if (current.getType() == ScenarioDescriptor.Type.URL) {
                    lastUrlScenario = current;
                    lastUrlIdx=i;
                    // check next scenario
                    if (i + 1 < scenarios.size()
                            && scenarios.get(i + 1).getType() == ScenarioDescriptor.Type.MODAL) {
                        logger.info("about to fire handleModalCHain by executeScenarios");
                        // URL followed by modal chain
                        i = handleModalChain(driver, scenarios, i, lastUrlScenario, scenarioRunId,
                                successMsg
                        );
                        logger.info("outside handleModalchain but inside executeScenario with idx : {}",i);
                    } else {
                        // normal URL execution
                        runUrlGeneric(
                                driver,
                                current.getUrl(),
                                current.getCsvFile(),
                                scenarioRunId,
                                successMsg
                        );
                    }
                }
                else if (current.getType() == ScenarioDescriptor.Type.MODAL) {
                    runModalGeneric(driver, current.getOpenerCss(), current.getCsvFile(), scenarioRunId, scenarios,lastUrlIdx,
                            successMsg
                    );
                }

            } catch (Exception e) {

                logger.error("[{}] scenario failed but continuing: {}",scenarioRunId,e.getMessage(), e);
            }
        }

    }

    private int handleModalChain(WebDriver driver, List<ScenarioDescriptor> scenarios,int urlIndex,
                                 ScenarioDescriptor urlScenario,
                                 String globalRunId,
                                 String successMsg) {
        String scenarioRunId = globalRunId + "_S" + (urlIndex + 1);
        // execute the URL first
        try{
            runUrlGeneric(driver, urlScenario.getUrl(), urlScenario.getCsvFile(), scenarioRunId, successMsg);
        }catch (Exception ex){
            logger.error("ex while url firing!");
        }

        int i = urlIndex + 1;

        logger.info("inside Handle modal chain and urlIdx is : {} and {}",i,i < scenarios.size() && scenarios.get(i).getType() == ScenarioDescriptor.Type.MODAL);

        // find last modal
        while (i < scenarios.size() && scenarios.get(i).getType() == ScenarioDescriptor.Type.MODAL) {
            i++;
        }
        int lastModalIndex = i - 1;
        logger.info("Last modal idx is : {} ",lastModalIndex);
        try {
            // open intermediate modals
            for (int j = urlIndex + 1; j < lastModalIndex; j++) {
                ScenarioDescriptor modal = scenarios.get(j);
                logger.info("[{}] Opening intermediate modal {}", globalRunId, modal.getId());
                WebElement opener = driver.findElement(By.cssSelector(modal.getOpenerCss()));
                opener.click();
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.error("[{}] modal chain failed", globalRunId, e);
        }
        return lastModalIndex-1;
    }

    /**
     * Generic URL method:
     * - scan the page at url
     * - load testcases from csvPath
     * - loop over each testcase, generate steps and execute using executor.run(...)
     */
    public List<TestCase> runUrlGeneric(WebDriver driver, String url, MultipartFile csvFile, String runIdPrefix,String successMsg) throws Exception {
        logger.info("[{}] runUrlGeneric start for URL: {}", runIdPrefix, url);
        List<TestCase> testCases=null;
        List<FieldDescriptor> fields = scannerService.scanPage(url, driver);
        logger.info("[{}] scanned {} fields", runIdPrefix, fields.size());

        // 2) load testcases for this scenario
        testCases = csvLoader.load(csvFile);
        logger.info("[{}] loaded {} testcases from {}", runIdPrefix, testCases.size(), csvFile.getOriginalFilename());


        // 3) for each testcase -> generate steps & run
        for (TestCase tc : testCases) {
            String tcRunId = runIdPrefix + "_" + tc.getId();
            try {
                logger.info("[{}] Generating steps for testcase {}", tcRunId, tc.getId());
                List<StepAction> steps = stepGenerator.generateSteps(fields, tc);
                logger.info("generated steps are : {}",steps);
                logger.info("[{}] Executing {} steps", tcRunId, steps.size());
                String result=executor.run(driver, url, steps, tcRunId,successMsg);
                tc.setResult(result);
                logger.info("[{}] Completed testcase {}", tcRunId, tc.getId());
            } catch (Exception e) {
                logger.error("[{}] testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
            }
        }
        return testCases;
    }

    /**
     * Generic Modal method:
     * - click opener button (css) to open modal
     * - scan current page DOM for modal fields
     * - load testcases from csvPath
     * - loop over each testcase -> generate steps & run using executor.runOnRenderedPage(...)
     */
    public List<TestCase> runModalGeneric(WebDriver driver, String openerCss, MultipartFile csvFile, String runIdPrefix,List<ScenarioDescriptor> scenarios,int lastUrlIdx,String successMsg) throws Exception {
        logger.info("[{}] runModalGeneric start using opener: {}", runIdPrefix, openerCss);
        List<TestCase> testCases=null;
        ScenarioDescriptor lastUrlScenario=scenarios.get(lastUrlIdx);
        try {
//            WebElement opener = driver.findElement(By.cssSelector(openerCss));
//            opener.click();
            // small generic wait so modal DOM gets attached (replace with explicit wait if you have modal selector)
            Thread.sleep(400);


            // load modal testcases
            testCases = csvLoader.load(csvFile);
            logger.info("[{}] loaded {} modal testcases from {}", runIdPrefix, testCases.size(), csvFile.getOriginalFilename());
            int counterIdx=0;
            for (TestCase tc : testCases) {
                String tcRunId = runIdPrefix + "_" + tc.getId();
                List<FieldDescriptor> modalFields = scannerService.scanCurrentPage(driver);
                logger.info("[{}] scanned {} modal fields", runIdPrefix, modalFields.size());
                counterIdx++;
                try {
                    List<StepAction> steps = stepGenerator.generateSteps(modalFields, tc);
                    logger.info("[{}] Executing {} modal steps", tcRunId, steps.size());
                    String result=executor.runOnRenderedPage(driver, steps, tcRunId,successMsg);
                    tc.setResult(result);
                    if(counterIdx<testCases.size())
                        handleModalChain(driver,scenarios,lastUrlIdx, lastUrlScenario,tcRunId,successMsg);
                    logger.info("[{}] Completed modal testcase {}", tcRunId, tc.getId());
                } catch (Exception e) {
                    logger.error("[{}] modal testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
                }
            }

            // try to close modal to restore state for next scenario
            try {
                new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logger.error("[{}] failed to open modal or execute tests: {}", runIdPrefix, e.getMessage(), e);
        }
        return testCases;
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
                    req.getId(),
                    req.getUrl(),
                    csvFile
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
}