package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.client.ScannerClient;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.StepAction;
import com.testingautomation.testautomation.model.TestCase;
import com.testingautomation.testautomation.scan.UiScannerService;
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
            System.out.println("Data from fields:  "+fields);
            List<TestCase> testCases = csvLoader.load(csvPath);
            System.out.println("Data from test cases:  "+testCases);
            for (TestCase tc : testCases) {
                // If CSV row provides a url override, use it
                String url = (tc.getUrl() != null && !tc.getUrl().isBlank()) ? tc.getUrl() : targetUrl;
                System.out.println(url);
                List<StepAction> steps = stepGenerator.generateSteps(fields, tc);
                executor.run(url, steps, tc.getId());
            }
            return "Run completed";
        } catch (Exception e) {
            logger.error("Run failed", e);
            return "Run failed: " + e.getMessage();
        }
    }
}