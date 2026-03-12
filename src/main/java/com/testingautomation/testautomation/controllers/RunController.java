package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.dto.ScenarioDescriptorModal;
import com.testingautomation.testautomation.dto.TestConfigPayload;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import com.testingautomation.testautomation.generator.StepGenerator;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.dto.ScenarioDescriptor;
import com.testingautomation.testautomation.dto.ScenarioDescriptorModal;
import com.testingautomation.testautomation.orchestratorService.ScenarioOrchestratorService;
import com.testingautomation.testautomation.scan.UiScannerService;
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
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

//    @PostMapping(value = "/run-auth", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<?> receiveTests(
//            @RequestPart("testConfiguration") TestConfigPayload payload,
//            @RequestParam("testResultStatement") String successMsg,
//            MultipartHttpServletRequest request) {
//
//
//                System.out.println(" -> " + successMsg);
//
//
//        ChromeOptions options = new ChromeOptions();
////                options.addArguments("--headless=new");
////                options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
//        options.addArguments("--disable-gpu");
//        options.addArguments("--window-size=1366,768");
//        WebDriver driver = new ChromeDriver(options);
//
//        ScenarioDescriptorModal scenarioDescriptorModal = scenarioOrchestratorService.scenarioDescriptorMapper(payload,request);
//        List<ScenarioDescriptor> scenarios=scenarioDescriptorModal.getTests();
//        if(scenarios==null){
//            return ResponseEntity.badRequest().body("no scenario to look up to!");
//        }
//        // --- At this point, you have a List<ScenarioDescriptor> ready for Selenium! ---
//        System.out.println("Successfully created " + scenarios.size() + " ScenarioDescriptors.");
//        File zipFile=null;
//        Resource resource=null;
//        try {
//            scenarioOrchestratorService.executeScenarios(driver, scenarios, scenarioDescriptorModal.getRunId(), successMsg);
//
//        }catch (Exception ex){
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("Test execution failed");
//        }finally {
//           driver.quit();
//        }
//        return ResponseEntity.ok().build();
//    }

@PostMapping(value = "/run-auth")
public ResponseEntity<?> receiveTests(
        @RequestParam("runId") String runId) {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--disable-gpu");
    options.addArguments("--window-size=1366,768");
    WebDriver driver = new ChromeDriver(options);
    try {
        scenarioOrchestratorService.executeScenarios(driver,runId);

    }catch (Exception ex){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Test execution failed");
    }finally {
        driver.quit();
    }
    return ResponseEntity.ok().build();
}



}