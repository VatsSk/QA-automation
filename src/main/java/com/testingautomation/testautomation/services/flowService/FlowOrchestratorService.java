package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.config.WebDriverConfig.WebDriverFactory;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class FlowOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(FlowOrchestratorService.class);

    @Autowired
    private WebDriverFactory webDriverFactory;

    @Autowired
    private FlowExecutionService flowExecutionService;

    public void orchestrate(Flow flow) {
        logger.info("Orchestrating background execution for flow: {}", flow.getName());
        // Run asynchronously so the API responds immediately
        CompletableFuture.runAsync(() -> executeFlow(flow));
    }

    private void executeFlow(Flow flow) {
        WebDriver driver = null;
        try {
            logger.info("Initializing WebDriver for flow: {}", flow.getName());
            driver = webDriverFactory.createDriver();

            if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
                logger.warn("Flow [{}] has no steps to execute.", flow.getName());
                return;
            }

            for (FlowStep step : flow.getSteps()) {
                flowExecutionService.executeStep(driver, step, flow);
            }

            logger.info("Successfully executed flow: {}", flow.getName());

        } catch (Exception e) {
            logger.error("Error executing flow [{}]: {}", flow.getName(), e.getMessage(), e);
        } finally {
            if (driver != null) {
                logger.info("Quitting WebDriver for flow: {}", flow.getName());
                driver.quit();
            }
        }
    }
}
