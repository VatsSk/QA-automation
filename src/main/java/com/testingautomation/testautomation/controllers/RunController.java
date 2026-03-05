package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.client.ScannerClient;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.StepAction;
import com.testingautomation.testautomation.model.TestCase;
import com.testingautomation.testautomation.scan.UiScannerService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/runner")
public class RunController {
    private final Logger logger = LoggerFactory.getLogger(RunController.class);
//    private final ScannerClient scannerClient;
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;

    public RunController(UiScannerService scannerService, CsvTestCaseLoader csvLoader, StepGenerator stepGenerator, SeleniumExecutor executor) {
//        this.scannerClient = scannerClient;

        this.csvLoader = csvLoader;
        this.scannerService = scannerService;
        this.stepGenerator = stepGenerator;
        this.executor = executor;
    }

    /**
     * Example:
     * GET /runner/run?scannerApi=http://localhost:8080/scanner/scan?url=...&csvPath=/tmp/tests.csv
     */
    @GetMapping("/run")
    public String runTests(@RequestParam String targetUrl,@RequestParam String csvPath) {
        try {
            List<FieldDescriptor> fields = scannerService.scanPage(targetUrl);
            logger.info("scannerService scannerService scannerService scannerService scannerService");
            logger.info("Length of fields :"+fields.size());
            System.out.println("Data from fields:  "+fields);


            List<TestCase> testCases = csvLoader.load(csvPath);
            System.out.println("Data from test cases:  "+testCases);
            for (TestCase tc : testCases) {
                ChromeOptions options = new ChromeOptions();
//                options.addArguments("--headless=new");
//                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1366,768");
                WebDriver driver = new ChromeDriver(options);;
                try {
                // If CSV row provides a url override, use it
                    String url = (tc.getUrl() != null && !tc.getUrl().isBlank())
                            ? tc.getUrl()
                            : targetUrl;

                    List<StepAction> steps =
                            stepGenerator.generateSteps(fields, tc);

                    executor.run(driver, url, steps, tc.getId());

                } finally {
                    driver.quit();   // 🔥 THIS IS REQUIRED
                }
            }
            return "Run completed";
        } catch (Exception e) {
            logger.error("Run failed", e);
            return "Run failed: " + e.getMessage();
        }

    }

    /**
     * Example:
     * GET /runner/run-auth
     *   ?loginUrl=http://localhost:8080/login
     *   &targetUrl=http://localhost:8080/dashboard
     *   &csvPath=/tmp/tests.csv
     */
    // http://43.205.165.113/web/0/employee/list?lang=en //
//    http://localhost:8080/runner/run-auth?targetUrl=http://43.205.165.113/login?lang=en&trueCred=trueCredentials.csv&targetUrl=http://43.205.165.113/web/0/employee/list?lang=en&csvPath=addEmployee.csv
    @GetMapping("/run-auth")
    public String runTestsWithLogin(
            @RequestParam String loginUrl,
            @RequestParam String trueCred,
            @RequestParam String targetUrl,
            @RequestParam String csvPath
    ) {
        logger.info("========== AUTHENTICATED RUN STARTED ==========");
        logger.info("Login URL: {}", loginUrl);
        logger.info("Target URL: {}", targetUrl);

        try {
            List<TestCase> testCases = csvLoader.load(csvPath);
            List<TestCase> validCredentials = csvLoader.load(trueCred);
            List<TestCase> validTaskTests = csvLoader.load("newTaskTests.csv");

            logger.info("Loaded {} main test cases", testCases.size());
            logger.info("Loaded {} task form test cases", validTaskTests.size());

            if (validCredentials.isEmpty()) {
                throw new RuntimeException("No valid login credential provided");
            }

            TestCase testCaseValid = validCredentials.get(0);
            logger.info("Using login credential: {}", testCaseValid.getId());

            for (TestCase tc : testCases) {

                logger.info("--------------------------------------------------");
                logger.info("Starting TestCase: {}", tc.getId());

                WebDriverManager.chromedriver().setup();

                ChromeOptions options = new ChromeOptions();
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1366,768");

                WebDriver driver = new ChromeDriver(options);

                try {

                    logger.info("[{}] Step 1: Scanning Login Page", tc.getId());
                    List<FieldDescriptor> loginFields =
                            scannerService.scanPage(loginUrl, driver);

                    logger.info("[{}] Found {} login elements",
                            tc.getId(), loginFields.size());

                    List<StepAction> loginSteps =
                            stepGenerator.generateSteps(loginFields, testCaseValid);

                    logger.info("[{}] Executing Login Steps ({})",
                            tc.getId(), loginSteps.size());

                    executor.run(driver,
                            loginUrl,
                            loginSteps,
                            tc.getId() + "_LOGIN");

                    logger.info("[{}] Login completed. Current URL: {}",
                            tc.getId(), driver.getCurrentUrl());

                    // TARGET PAGE
                    logger.info("[{}] Step 2: Scanning Target Page", tc.getId());

                    List<FieldDescriptor> targetFields =
                            scannerService.scanPage(targetUrl, driver);

                    logger.info("[{}] Found {} target elements",
                            tc.getId(), targetFields.size());

                    List<StepAction> testSteps =
                            stepGenerator.generateSteps(targetFields, tc);

                    logger.info("[{}] Executing Add Task Button Steps ({})",
                            tc.getId(), testSteps.size());

                    executor.run(driver,
                            targetUrl,
                            testSteps,
                            tc.getId() + "_add_task_button");

                    logger.info("[{}] Add button executed. Current URL: {}",
                            tc.getId(), driver.getCurrentUrl());

                    // MODAL SCAN
                    logger.info("[{}] Scanning modal fields from current DOM",
                            tc.getId());

                    List<FieldDescriptor> newFields =
                            scannerService.scanCurrentPage(driver);

                    logger.info("[{}] Found {} modal fields",
                            tc.getId(), newFields.size());

                    // FORM TEST EXECUTION LOOP
                    for (TestCase testTasks : validTaskTests) {

                        logger.info("[{}] Executing Task Form Test: {}",
                                tc.getId(), testTasks.getId());

                        List<StepAction> formFillSteps =
                                stepGenerator.generateSteps(newFields, testTasks);

                        logger.info("[{}] Generated {} form steps size",
                                testTasks.getId(), formFillSteps.size());
                        logger.info("[{}] Generated {} form steps",
                                testTasks.getId(), formFillSteps);

                        executor.runOnRenderedPage(
                                driver,
                                formFillSteps,
                                testTasks.getId() + "_task_form"
                        );

                        logger.info("[{}] Completed Task Form Test",
                                testTasks.getId());
                    }

                    logger.info("[{}] All form scenarios completed",
                            tc.getId());

                } catch (Exception e) {
                    logger.error("[{}] Test execution failed: {}",
                            tc.getId(), e.getMessage(), e);
                } finally {
                    logger.info("[{}] Closing browser session", tc.getId());
                    driver.quit();
                }
            }

            logger.info("========== AUTHENTICATED RUN COMPLETED ==========");
            return "Authenticated run completed";

        } catch (Exception e) {
            logger.error("RUN FAILED: {}", e.getMessage(), e);
            return "Run failed: " + e.getMessage();
        }
    }
}