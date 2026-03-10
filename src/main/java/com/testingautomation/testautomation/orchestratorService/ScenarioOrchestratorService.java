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
    public void executeScenarios(WebDriver driver, List<ScenarioDescriptor> scenarios, String globalRunId,String successMsg) {
        logger.info("[{}] Executing {} scenarios sequentially", globalRunId, scenarios.size());
        ScenarioDescriptor lastScenario = null;
        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioDescriptor s = scenarios.get(i);
            String scenarioRunId = globalRunId + "_S" + (i + 1) + (s.getId() != null ? "_" + s.getId() : "");
            try {

                if (s.getType() == ScenarioDescriptor.Type.URL) {
                    lastScenario = s;

                    runUrlGeneric(
                            driver,
                            s.getUrl(),
                            s.getCsvFile(),
                            scenarioRunId,
                            successMsg
                    );

                } else if (s.getType() == ScenarioDescriptor.Type.MODAL) {

                    runModalGeneric(
                            driver,
                            s.getOpenerCss(),
                            s.getCsvFile(),
                            scenarioRunId,
                            lastScenario,
                            successMsg
                    );

                }

            } catch (Exception e) {

                logger.error("[{}] scenario failed but continuing: {}", scenarioRunId, e.getMessage(), e);
            }
        }
    }

    /**
     * Generic URL method:
     * - scan the page at url
     * - load testcases from csvPath
     * - loop over each testcase, generate steps and execute using executor.run(...)
     */
    public void runUrlGeneric(WebDriver driver, String url, MultipartFile csvFile, String runIdPrefix,String successMsg) throws Exception {
        logger.info("[{}] runUrlGeneric start for URL: {}", runIdPrefix, url);

        // 1) scan page (fields)
        List<FieldDescriptor> fields = scannerService.scanPage(url, driver);
        logger.info("[{}] scanned {} fields", runIdPrefix, fields.size());
        for(FieldDescriptor fieldDescriptor: fields){
            if(fieldDescriptor.dataTarget!=null && fieldDescriptor.dataTarget.equalsIgnoreCase("#edit-employee-modal")){
                System.out.println("found: "+fieldDescriptor);
            }
        }

        // 2) load testcases for this scenario
        List<TestCase> testCases = csvLoader.load(csvFile);
        logger.info("[{}] loaded {} testcases from {}", runIdPrefix, testCases.size(), csvFile.getOriginalFilename());


        // 3) for each testcase -> generate steps & run
        for (TestCase tc : testCases) {
            String tcRunId = runIdPrefix + "_" + tc.getId();
            try {
                logger.info("[{}] Generating steps for testcase {}", tcRunId, tc.getId());
                List<StepAction> steps = stepGenerator.generateSteps(fields, tc);
                logger.info("generated steps are : {}",steps);
                logger.info("[{}] Executing {} steps", tcRunId, steps.size());
                executor.run(driver, url, steps, tcRunId,successMsg);
                logger.info("[{}] Completed testcase {}", tcRunId, tc.getId());
            } catch (Exception e) {
                logger.error("[{}] testcase failed, continuing: {}", tcRunId, e.getMessage(), e);
            }
        }
    }

    /**
     * Generic Modal method:
     * - click opener button (css) to open modal
     * - scan current page DOM for modal fields
     * - load testcases from csvPath
     * - loop over each testcase -> generate steps & run using executor.runOnRenderedPage(...)
     */
    public void runModalGeneric(WebDriver driver, String openerCss, MultipartFile csvFile, String runIdPrefix,ScenarioDescriptor lastScenario,String successMsg) throws Exception {
        logger.info("[{}] runModalGeneric start using opener: {}", runIdPrefix, openerCss);

        try {
//            WebElement opener = driver.findElement(By.cssSelector(openerCss));
//            opener.click();
            // small generic wait so modal DOM gets attached (replace with explicit wait if you have modal selector)
            Thread.sleep(400);

            List<FieldDescriptor> modalFields = scannerService.scanCurrentPage(driver);
            logger.info("[{}] scanned {} modal fields", runIdPrefix, modalFields.size());

            // load modal testcases
            List<TestCase> testCases = csvLoader.load(csvFile);
            logger.info("[{}] loaded {} modal testcases from {}", runIdPrefix, testCases.size(), csvFile.getOriginalFilename());
            int caseCounter=0;
            for (TestCase tc : testCases) {
                String tcRunId = runIdPrefix + "_" + tc.getId();
                try {
                    caseCounter++;
                    List<StepAction> steps = stepGenerator.generateSteps(modalFields, tc);
                    logger.info("[{}] Executing {} modal steps", tcRunId, steps.size());
                    executor.runOnRenderedPage(driver, steps, tcRunId,successMsg);
                    logger.info("[{}] Completed modal testcase {}", tcRunId, tc.getId());
                    if(caseCounter<testCases.size())
                        runUrlGeneric(driver,lastScenario.getUrl(), lastScenario.getCsvFile(),"lastUrl",successMsg);
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



}