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
        try {
            List<TestCase> testCases = csvLoader.load(csvPath);
            List<TestCase> validCredentials = csvLoader.load(trueCred);
            TestCase testCaseValid = validCredentials.get(0);
            System.out.println("test case for valid credentials "+testCaseValid);

            if(testCaseValid ==null){
                throw new RuntimeException("Please provide one valid credential for login ");
            }
            System.out.println("testCases"+ testCases.size());

            for (TestCase tc : testCases) {
                WebDriverManager.chromedriver().setup();
                System.out.println("tc"+tc);

                ChromeOptions options = new ChromeOptions();
//                options.addArguments("--headless=new");
//                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1366,768");
                WebDriver driver = new ChromeDriver(options);; // 👈 single session

                try {
                    // 1️⃣ LOGIN
                    List<FieldDescriptor> loginFields = scannerService.scanPage(loginUrl, driver);
                    List<StepAction> loginSteps =
                            stepGenerator.generateSteps(loginFields, testCaseValid);

                    executor.run(driver,loginUrl, loginSteps, tc.getId() + "_LOGIN");

                    // 2️⃣ TARGET PAGE
                    List<FieldDescriptor> targetFields =
                            scannerService.scanPage(targetUrl, driver);

                    System.out.println("no of fieldDescriptor : "+ targetFields.size());
                    for(FieldDescriptor f:targetFields){
                        if(f.text !=null && f.text.equalsIgnoreCase("add")){
                            System.out.println("add is present : "+f);
                        }
                    }

                    System.out.println(targetFields);

                    List<StepAction> testSteps =
                            stepGenerator.generateSteps(targetFields, tc);

                    executor.run(driver,targetUrl, testSteps, tc.getId());

                } finally {
                    driver.quit();
                }
            }

            return "Authenticated run completed";

        } catch (Exception e) {
            logger.error("Run failed", e);
            return "Run failed: " + e.getMessage();
        }

    }
}