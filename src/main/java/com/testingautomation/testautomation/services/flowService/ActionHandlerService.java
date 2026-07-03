package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.FlowStep;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ActionHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(ActionHandlerService.class);

    public void handleNavigate(WebDriver driver, FlowStep step) {
        String url = step.getValue();
        if (url == null || url.isEmpty()) {
            logger.warn("Cannot perform NAVIGATE action: URL is missing in step [{}]", step.getName());
            return;
        }
        logger.info("Navigating to URL: {}", url);
        driver.get(url);
    }

    public void handleType(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Typing value [{}] into element", step.getValue());
        element.clear();
        if (step.getValue() != null) {
            element.sendKeys(step.getValue());
        }
    }

    public void handleClick(WebDriver driver, WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Clicking element");
        try {
            element.click();
        } catch (ElementClickInterceptedException e) {
            logger.warn("Standard click intercepted, using Javascript executor to click.");
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    public void handleCheckbox(WebElement element, FlowStep step) {
        if (element == null) return;
        boolean targetState = Boolean.parseBoolean(step.getValue());
        boolean currentState = element.isSelected();
        logger.info("Checkbox target state: [{}], current state: [{}]", targetState, currentState);
        if (targetState != currentState) {
            element.click();
        }
    }

    public void handleRadio(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Selecting radio button");
        if (!element.isSelected()) {
            element.click();
        }
    }

    public void handleSelect(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Selecting option with text/value [{}]", step.getValue());
        Select select = new Select(element);
        try {
            select.selectByVisibleText(step.getValue());
        } catch (Exception e) {
            logger.warn("Could not select by visible text, attempting by value.");
            select.selectByValue(step.getValue());
        }
    }

    public void handleDate(WebElement element, FlowStep step) {
        if (element == null) return;
        // Typically dating inputs can just be typed into using sendKeys in Selenium
        logger.info("Setting date value to [{}]", step.getValue());
        element.clear();
        element.sendKeys(step.getValue());
        // Alternatively, use JS if it's a readonly datepicker
        // ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", element, step.getValue());
    }

    public void handleFileUpload(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Uploading file from path [{}]", step.getValue());
        // The value should be the absolute path to the file
        File file = new File(step.getValue());
        if (!file.exists()) {
            logger.error("File does not exist at path: {}", step.getValue());
            return;
        }
        element.sendKeys(file.getAbsolutePath());
    }

    public void handleHover(WebDriver driver, WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Hovering over element");
        Actions actions = new Actions(driver);
        actions.moveToElement(element).perform();
    }

    public void handleWait(FlowStep step) {
        int waitTime = step.getValue() != null ? Integer.parseInt(step.getValue()) : 1000;
        logger.info("Hard waiting for {} milliseconds", waitTime);
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void handleScroll(WebDriver driver, WebElement element, FlowStep step) {
        logger.info("Scrolling to element");
        if (element != null) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
        } else if (step.getValue() != null) {
            // Scroll by pixel value, e.g. "0, 500"
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(" + step.getValue() + ");");
        }
    }

    public void handlePressKey(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Pressing key [{}]", step.getValue());
        String keyName = step.getValue().toUpperCase();
        try {
            Keys key = Keys.valueOf(keyName);
            element.sendKeys(key);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown key [{}], sending as standard text", keyName);
            element.sendKeys(step.getValue());
        }
    }

    public void handleDragDrop(WebDriver driver, WebElement sourceElement, FlowStep step) {
        if (sourceElement == null) return;
        // value should contain the target locator for the drop
        String targetLocator = step.getValue();
        if (targetLocator == null || targetLocator.isEmpty()) {
            logger.warn("Target locator value is empty for DragDrop step [{}]", step.getName());
            return;
        }
        
        logger.info("Dragging element to target [{}]", targetLocator);
        try {
            WebElement targetElement = driver.findElement(By.cssSelector(targetLocator));
            Actions actions = new Actions(driver);
            actions.dragAndDrop(sourceElement, targetElement).perform();
        } catch (Exception e) {
            logger.error("Failed to perform DragDrop to target {}: {}", targetLocator, e.getMessage());
        }
    }

    public void handleVerify(WebDriver driver, WebElement element, FlowStep step) {
    }
}
