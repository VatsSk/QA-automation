package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.config.WebDriverConfig.WebDriverFactory;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class FlowOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(FlowOrchestratorService.class);

    @Autowired
    private WebDriverFactory webDriverFactory;

    @Autowired
    private FlowExecutionService flowExecutionService;
    @Autowired
    private FlowRepository flowRepository;

    @Value("${storage.s3.base-prefix}")
    private String basePrefix;
    @Value("${storage.s3.bucket-name}")
    private String bucket;
    @Autowired
    private StorageService storageService;

    public void orchestrate(Flow flow) {
        logger.info("Orchestrating background execution for flow: {}", flow.getName());
        CompletableFuture.runAsync(() -> executeFlow(flow));
    }

    private void executeFlow(Flow flow) {

        WebDriver driver = null;
        try {
            flow.setExecutionStartedAt(Instant.now());
            flow.setExecutionStatus(ExecutionStatus.RUNNING);
            flow.setExecutionMessage(null);
            String flowPrefix = basePrefix + "/" + flow.getProjectId() + "/" + flow.getModuleId() + "/" + flow.getId();
            //deleting existing objects from the s3 for run
            if (storageService.doesPrefixHaveObjects(bucket, flowPrefix)) {
                storageService.deleteFolderExceptTestCase(bucket, flowPrefix);
            }
            flow.setFlowBasePath(flowPrefix);
            flow.setUpdatedAt(Instant.now());
            for(FlowStep step : flow.getSteps()){
                step.setExecutionMessage(null);
                step.setExecutionCompletedAt(null);
                step.setExecutionStartedAt(null);
                step.setExecutionStatus(ExecutionStatus.DRAFT);
            }
            flowRepository.save(flow);
            logger.info("Initializing WebDriver for flow: {}", flow.getName());

            
            driver = webDriverFactory.createDriver();

            if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
                logger.warn("Flow [{}] has no steps to execute.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.PASSED);
                flow.setExecutionMessage("No steps to execute");
                flow.setExecutionCompletedAt(Instant.now());
                flow.setUpdatedAt(Instant.now());
                flowRepository.save(flow);
                return;
            }

            for (FlowStep step : flow.getSteps()) {

                flowExecutionService.executeStep(driver, step, flow);

            }

            flow.setExecutionStatus(ExecutionStatus.PASSED);
            flow.setExecutionMessage("Flow executed successfully");
            logger.info("Successfully executed flow: {}", flow.getName());

        } catch (Exception e) {
            logger.error("Error executing flow [{}]: {}", flow.getName(), e.getMessage(), e);
            flow.setExecutionStatus(ExecutionStatus.FAILED);
            flow.setExecutionMessage(e.getMessage());
        } finally {
            flow.setExecutionCompletedAt(Instant.now());
            flow.setUpdatedAt(Instant.now());
            flowRepository.save(flow);
            if (driver != null) {
                logger.info("Quitting WebDriver for flow: {}", flow.getName());
                driver.quit();
            }
        }

    }
}
