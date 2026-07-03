package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.config.WebDriverConfig.WebDriverFactory;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
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

    public void orchestrate(Flow flow) {
        logger.info("Orchestrating background execution for flow: {}", flow.getName());
        CompletableFuture.runAsync(() -> executeFlow(flow));
    }

    private void executeFlow(Flow flow) {
        WebDriver driver = null;
        try {
            logger.info("Initializing WebDriver for flow: {}", flow.getName());
            
            flow.setExecutionStartedAt(Instant.now());
            flow.setExecutionStatus(ExecutionStatus.RUNNING);
            flow.setExecutionMessage(null);
            String flowPrefix = basePrefix + "/" + flow.getProjectId() + "/" + flow.getModuleId() + "/" + flow.getId();
            flow.setFlowBasePath(flowPrefix);
            flowRepository.save(flow);
            
            driver = webDriverFactory.createDriver();

            if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
                logger.warn("Flow [{}] has no steps to execute.", flow.getName());
                flow.setExecutionStatus(ExecutionStatus.PASSED);
                flow.setExecutionMessage("No steps to execute");
                flow.setExecutionCompletedAt(Instant.now());
                flowRepository.save(flow);
                return;
            }

            for (FlowStep step : flow.getSteps()) {

                flowExecutionService.executeStep(driver, step, flow);

            }

            flow.setExecutionStatus(ExecutionStatus.PASSED);
            flow.setExecutionMessage("Flow executed successfully");
            flow.setExecutionCompletedAt(Instant.now());
            flowRepository.save(flow);
            logger.info("Successfully executed flow: {}", flow.getName());

        } catch (Exception e) {
            logger.error("Error executing flow [{}]: {}", flow.getName(), e.getMessage(), e);
            flow.setExecutionStatus(ExecutionStatus.FAILED);
            flow.setExecutionMessage(e.getMessage());
            flow.setExecutionCompletedAt(Instant.now());
            flowRepository.save(flow);
        } finally {
            if (driver != null) {
                logger.info("Quitting WebDriver for flow: {}", flow.getName());
                driver.quit();
            }
        }
    }
}
