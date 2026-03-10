package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.client.ScannerClient;
import com.testingautomation.testautomation.dto.TestConfigPayload;
import com.testingautomation.testautomation.dto.TestConfigRequest;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.ScenarioDescriptor;
import com.testingautomation.testautomation.model.StepAction;
import com.testingautomation.testautomation.model.TestCase;
import com.testingautomation.testautomation.orchestratorService.ScenarioOrchestratorService;
import com.testingautomation.testautomation.scan.UiScannerService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.core.io.Resource;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/runner")
public class RunController {
    private final Logger logger = LoggerFactory.getLogger(RunController.class);
//    private final ScannerClient scannerClient;
    private final CsvTestCaseLoader csvLoader;
    private final UiScannerService scannerService;
    private final StepGenerator stepGenerator;
    private final SeleniumExecutor executor;
    private final ScenarioOrchestratorService scenarioOrchestratorService;

    public RunController(UiScannerService scannerService, CsvTestCaseLoader csvLoader, StepGenerator stepGenerator, SeleniumExecutor executor,ScenarioOrchestratorService scenarioOrchestratorService) {
//        this.scannerClient = scannerClient;

        this.csvLoader = csvLoader;
        this.scannerService = scannerService;
        this.stepGenerator = stepGenerator;
        this.executor = executor;
        this.scenarioOrchestratorService=scenarioOrchestratorService;
    }

    /**
     * Example:
     * GET /runner/run?scannerApi=http://localhost:8080/scanner/scan?url=...&csvPath=/tmp/tests.csv
     */
//    @GetMapping("/run")
//    public String runTests(@RequestParam String targetUrl,@RequestParam String csvPath) {
//        try {
//            List<FieldDescriptor> fields = scannerService.scanPage(targetUrl);
//            logger.info("scannerService scannerService scannerService scannerService scannerService");
//            logger.info("Length of fields :"+fields.size());
//            System.out.println("Data from fields:  "+fields);
//
//
//            List<TestCase> testCases = csvLoader.load(csvPath);
//            System.out.println("Data from test cases:  "+testCases);
//            for (TestCase tc : testCases) {
//                ChromeOptions options = new ChromeOptions();
////                options.addArguments("--headless=new");
////                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
//                options.addArguments("--disable-gpu");
//                options.addArguments("--window-size=1366,768");
//                WebDriver driver = new ChromeDriver(options);;
//                try {
//                // If CSV row provides a url override, use it
//                    String url = (tc.getUrl() != null && !tc.getUrl().isBlank())
//                            ? tc.getUrl()
//                            : targetUrl;
//
//                    List<StepAction> steps =
//                            stepGenerator.generateSteps(fields, tc);
//
//                    executor.run(driver, url, steps, tc.getId(),"");
//
//                } finally {
//                    driver.quit();   // 🔥 THIS IS REQUIRED
//                }
//            }
//            return "Run completed";
//        } catch (Exception e) {
//            logger.error("Run failed", e);
//            return "Run failed: " + e.getMessage();
//        }
//
//    }

    /**
     * Example:
     * GET /runner/run-auth
     *   ?loginUrl=http://localhost:8080/login
     *   &targetUrl=http://localhost:8080/dashboard
     *   &csvPath=/tmp/tests.csv
     */
    // http://43.205.165.113/web/0/employee/list?lang=en //
//    http://localhost:8080/runner/run-auth?targetUrl=http://43.205.165.113/login?lang=en&trueCred=trueCredentials.csv&targetUrl=http://43.205.165.113/web/0/employee/list?lang=en&csvPath=addEmployee.csv
//
//    @GetMapping("/run-auth")
//    public String runTestsWithLogin(
//            @RequestParam String loginUrl,
//            @RequestParam String trueCred,
//            @RequestParam String targetUrl,
//            @RequestParam MultipartFile csvFile
//    ) {
//
//        logger.info("========== AUTHENTICATED SCENARIO RUN STARTED ==========");
//
//        try {
//
//            List<TestCase> validCredentials = csvLoader.load(trueCred);
//
//            if (validCredentials.isEmpty()) {
//                throw new RuntimeException("No valid login credential provided");
//            }
//
//            TestCase loginCredential = validCredentials.get(0);
//
//            // Create driver
//            WebDriver driver = scenarioOrchestratorService.createChromeDriver();
//
//            try {
//
//                // STEP 1 — LOGIN
//                logger.info("Running Login Step");
//
//                List<FieldDescriptor> loginFields =
//                        scannerService.scanPage(loginUrl, driver);
//
//                List<StepAction> loginSteps =
//                        stepGenerator.generateSteps(loginFields, loginCredential);
//
//                executor.run(
//                        driver,
//                        loginUrl,
//                        loginSteps,
//                        "LOGIN"
//                );
//
//                logger.info("Login completed. Current URL: {}", driver.getCurrentUrl());
//
//
//                // STEP 2 — BUILD SCENARIOS
//                List<ScenarioDescriptor> scenarios = List.of(
//
//                        new ScenarioDescriptor(
//                                ScenarioDescriptor.Type.URL,
//                                "ADD_TASK_BUTTON",
//                                targetUrl,
//                                null,
//                                csvFile
//                        )
//
////                        new ScenarioDescriptor(
////                                ScenarioDescriptor.Type.MODAL,
////                                "TASK_FORM_MODAL",
////                                null,
////                                "#add-task-btn",
////                                "newTaskTests.csv"
////                        )
//
//                );
//
//
//                // STEP 3 — RUN SCENARIOS
//                scenarioOrchestratorService.executeScenarios(
//                        driver,
//                        scenarios,
//                        "RUN_AUTH"
//                );
//
//            } finally {
//
//                driver.quit();
//            }
//
//            logger.info("========== SCENARIO RUN COMPLETED ==========");
//
//            return "Scenario execution completed";
//
//        } catch (Exception e) {
//
//            logger.error("RUN FAILED: {}", e.getMessage(), e);
//            return "Run failed: " + e.getMessage();
//        }
//    }

    @PostMapping(value = "/run-auth", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> receiveTests(
            @RequestPart("testConfiguration") TestConfigPayload payload,

            @RequestParam("testResultStatement") String successMsg,
            MultipartHttpServletRequest request) {


                System.out.println(" -> " + successMsg);


        // This list will hold your final, fully populated domain objects

        String runId = "run_" +LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm")) +"_" + UUID.randomUUID().toString().substring(0,6);


        ChromeOptions options = new ChromeOptions();
//                options.addArguments("--headless=new");
//                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1366,768");
        WebDriver driver = new ChromeDriver(options);

        List<ScenarioDescriptor> scenarios = scenarioOrchestratorService.scenarioDescriptorMapper(payload,request);
        if(scenarios==null){
            return ResponseEntity.badRequest().body("no scenario to look up to!");
        }
        // --- At this point, you have a List<ScenarioDescriptor> ready for Selenium! ---
        System.out.println("Successfully created " + scenarios.size() + " ScenarioDescriptors.");
        File zipFile=null;
        Resource resource=null;
        try {
            scenarioOrchestratorService.executeScenarios(driver, scenarios, runId,successMsg);
            zipFile = scenarioOrchestratorService.zipTestResults(runId);
            resource = new FileSystemResource(zipFile);

        }catch (Exception ex){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Test execution failed");
        }finally {
           driver.quit();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + zipFile.getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }



}