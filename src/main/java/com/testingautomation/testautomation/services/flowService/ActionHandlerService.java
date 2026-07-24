package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.VerificationService;
import lombok.AllArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;

@Service
@AllArgsConstructor
public class ActionHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(ActionHandlerService.class);

    @Autowired
    private final VerificationService verificationService;
    public void handleNavigate(WebDriver driver, FlowStep step) {
        String url = step.getValue();
        if (url == null || url.isEmpty()) {
            logger.warn("Cannot perform NAVIGATE action: URL is missing in step [{}]", step.getName());
            return;
        }
        logger.info("Navigating to URL: {}", url);
        try{
            driver.get(url);
        }catch(Exception e){
            throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"Navigation error!","Unable to Navigate on "+step.getValue(),e);
        }
    }

    public void handleType(WebElement element, FlowStep step) {
        if (element == null) return;
        logger.info("Typing value [{}] into element", step.getValue());
        try{
            element.clear();
            if (step.getValue() != null) {
                element.sendKeys(step.getValue());
            }
        }catch(Exception e){
            throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"Type error!","Unable to Type on element with "+step.getSelector()+" selector",e);
        }
    }

    public void handleClick(WebDriver driver, WebElement element, FlowStep step) {
        if (element == null) return;

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Scroll into view
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center',inline:'center'});",
                    element);

            // Wait until clickable
            wait.until(ExpectedConditions.elementToBeClickable(element));

            // Native Selenium click (preferred)
            element.click();

            logger.info("Clicked [{}]", step.getSelector());
            return;

        } catch (ElementClickInterceptedException e) {

            logger.warn("Click intercepted, trying Actions click.");

        } catch (ElementNotInteractableException e) {

            logger.warn("Element not interactable, trying Actions click.");

        } catch (StaleElementReferenceException e) {

            throw new GlobalExceptionHandler.FlowExecutionException(
                    step.getStepOrder(), step.getName(), step.getActionType(),
                    "Click error!", "Element became stale: " + step.getSelector(), e);
        }

        // Fallback 1 - Actions API
        try {
            new Actions(driver)
                    .moveToElement(element)
                    .pause(Duration.ofMillis(100))
                    .click()
                    .perform();

            logger.info("Clicked using Actions [{}]", step.getSelector());
            return;

        } catch (Exception ignored) {
        }

        // Fallback 2 - JavaScript click
        try {
            ((JavascriptExecutor) driver)
                    .executeScript("arguments[0].click();", element);

            logger.info("Clicked using JavaScript [{}]", step.getSelector());
            return;

        } catch (Exception ignored) {
        }

        // Fallback 3 - Full mouse event sequence
        try {
            ((JavascriptExecutor) driver).executeScript("""
            const el = arguments[0];
            ['pointerover','mouseover','mousemove',
             'pointerdown','mousedown',
             'pointerup','mouseup','click']
            .forEach(type => {
                const C = type.startsWith('pointer') ? PointerEvent : MouseEvent;
                el.dispatchEvent(new C(type,{
                    bubbles:true,
                    cancelable:true,
                    view:window
                }));
            });
        """, element);

            logger.info("Clicked using mouse event sequence [{}]", step.getSelector());
            return;

        } catch (Exception e) {

            throw new GlobalExceptionHandler.FlowExecutionException(
                    step.getStepOrder(),
                    step.getName(),
                    step.getActionType(),
                    "Click error!",
                    "Unable to click " + step.getSelector(),
                    e);
        }
    }
    public void handleCheckbox(WebElement element, FlowStep step) {
        if (element == null) return;
        boolean targetState = Boolean.parseBoolean(step.getValue());
        boolean currentState = element.isSelected();
        logger.info("Checkbox target state: [{}], current state: [{}]", targetState, currentState);
        logger.info("Displayed : {}", element.isDisplayed());
        logger.info("Enabled   : {}", element.isEnabled());
        logger.info("Selected  : {}", element.isSelected());
        logger.info("Location  : {}", element.getLocation());
        logger.info("Size      : {}", element.getSize());
        try{
            element.click();
        }catch(Exception e){
            throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"CheckBox error!","Unable to click (checkbox) on "+step.getSelector(),e);
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
        try{
            Actions actions = new Actions(driver);
            actions.moveToElement(element).perform();
        }catch(Exception e){
            throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"Hover error!","Unable to hover on "+step.getSelector(),e);
        }

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

    public void handleVerify(WebDriver driver, WebElement element, FlowStep step,int waitTime) {
//        if(element!=null){
//            handleHover(driver,element,step);
//        }

        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'center'});",
                element);
        com.testingautomation.testautomation.enums.flow.VerificationType vType = step.getVerificationType();
        if (vType == null) {
            logger.warn("VerificationType is null for step [{}], defaulting to VISIBLE check", step.getName());
            vType = com.testingautomation.testautomation.enums.flow.VerificationType.VISIBLE;
        }

        String expected = step.getExpectedValue();
        logger.info("Running verification [{}] on step [{}], expected value: [{}]", vType, step.getName(), expected);

        switch (vType) {

            // ── Element state ────────────────────────────────────────────────────────
            case VISIBLE: {
                logger.info("Verifying visibility of element [{}]", step.getName());
                logger.info("Element : {}",element);
                if (element == null || !element.isDisplayed()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"VISIBLE verification failed","VISIBLE verification failed: element is not visible. Selector: " + step.getSelector(),null);
                }
                logger.info("VISIBLE verification passed.");
                break;
            }

            case NOT_VISIBLE: {
                // element may already be null (not found), or present but hidden — both are acceptable
                boolean notVisible = (element == null) || !element.isDisplayed();
                if (!notVisible) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"NOT_VISIBLE verification failed","NOT_VISIBLE verification failed: element is visible. Selector: " + step.getSelector(),null);
                }
                logger.info("NOT_VISIBLE verification passed.");
                break;
            }

            case EXISTS: {
                // Presence in the DOM is sufficient — it need not be displayed
                java.util.List<WebElement> existsMatches = driver.findElements(By.cssSelector(step.getSelector()));
                if (existsMatches.isEmpty()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"EXISTS verification failed","EXISTS verification failed: no element found in DOM. Selector: " + step.getSelector(),null);
                }
                logger.info("EXISTS verification passed. Found {} element(s).", existsMatches.size());
                break;
            }

            case NOT_EXISTS: {
                java.util.List<WebElement> notExistsMatches = driver.findElements(By.cssSelector(step.getSelector()));
                if (!notExistsMatches.isEmpty()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"NOT_EXISTS verification failed","NOT_EXISTS verification failed: element(s) found in DOM. Selector: " + step.getSelector(),null);
                }
                logger.info("NOT_EXISTS verification passed.");
                break;
            }

            case ENABLED: {
                if (element == null || !element.isEnabled()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"ENABLED verification failed","ENABLED verification failed: element is not enabled. Selector: " + step.getSelector(),null);
                }
                logger.info("ENABLED verification passed.");
                break;
            }

            case DISABLED: {
                if (element == null || element.isEnabled()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"DISABLED verification failed","DISABLED verification failed: element is enabled (expected disabled). Selector: " + step.getSelector(),null);
                }
                logger.info("DISABLED verification passed.");
                break;
            }

            case CHECKED: {
                if (element == null || !element.isSelected()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"CHECKED verification failed","CHECKED verification failed: element is not checked/selected. Selector: " + step.getSelector(),null);
                }
                logger.info("CHECKED verification passed.");
                break;
            }

            case UNCHECKED: {
                if (element == null || element.isSelected()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"UNCHECKED verification failed","UNCHECKED verification failed: element is checked/selected (expected unchecked). Selector: " + step.getSelector(),null);
                }
                logger.info("UNCHECKED verification passed.");
                break;
            }

            // ── Text / Value content ─────────────────────────────────────────────────
            case TEXT: {
                logger.info("Element before {}",element);
                if (element == null) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"TEXT verification failed","TEXT verification failed: element is null. Selector: " + step.getSelector(),null);
                }
                String rawActual = element.getText();
                if (rawActual == null || rawActual.isEmpty()) {
                    rawActual = (String) ((JavascriptExecutor) driver)
                            .executeScript("return arguments[0].textContent;", element);
                }

//                 Normalize: collapse all whitespace sequences (spaces, newlines, tabs) → single space, then trim.
                rawActual   = rawActual   != null ? rawActual.replaceAll("\\s+", " ").trim() : "";
                expected = expected    != null ? expected.replaceAll("\\s+", " ").trim() : "";

                logger.info("TEXT verification. Expected: [{}], Actual: [{}]", expected, rawActual);

                if (!rawActual.trim().equals(expected.trim())) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"TEXT verification failed",String.format("TEXT verification failed: expected [%s] but found [%s]. Selector: %s", expected, rawActual, step.getSelector()),null);
                }
                logger.info("TEXT verification passed. Value: [{}]", rawActual);
                break;
            }

            case VALUE: {
                if (element == null) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"VALUE verification failed","VALUE verification failed: element is null. Selector: " + step.getSelector(),null);
                }
                String actualValue = element.getAttribute("value");
                if (actualValue == null) actualValue = "";
                String expectedValue = expected != null ? expected.trim() : "";
                if (!actualValue.trim().equals(expectedValue)) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"VALUE verification failed",String.format("VALUE verification failed: expected [%s] but found [%s]. Selector: %s",
                            expectedValue, actualValue.trim(), step.getSelector()),null);

                }
                logger.info("VALUE verification passed. Value: [{}]", actualValue);
                break;
            }

            // ── Attribute ────────────────────────────────────────────────────────────
            case ATTRIBUTE: {
                if (element == null) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"ATTRIBUTE verification failed","ATTRIBUTE verification failed: element is null. Selector: " + step.getSelector(),null);
                }
                String attrName = step.getAttribute();
                if (attrName == null || attrName.trim().isEmpty()) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"ATTRIBUTE verification failed","ATTRIBUTE verification failed: attribute name is not specified in step [" + step.getName() + "]",null);
                }
                String actualAttr = element.getAttribute(attrName);
                if (actualAttr == null) actualAttr = "";
                String expectedAttr = expected != null ? expected.trim() : "";
                if (!actualAttr.trim().equals(expectedAttr)) {
                    throw new GlobalExceptionHandler.FlowExecutionException(step.getStepOrder(),step.getName(),step.getActionType(),"ATTRIBUTE verification failed", String.format("ATTRIBUTE [%s] verification failed: expected [%s] but found [%s]. Selector: %s",
                            attrName, expectedAttr, actualAttr.trim(), step.getSelector()),null);
                }
                logger.info("ATTRIBUTE [{}] verification passed. Value: [{}]", attrName, actualAttr);
                break;
            }

            // ── Image src ────────────────────────────────────────────────────────────
            case IMAGE: {
                if (element == null) {
                    throw new RuntimeException("IMAGE verification failed: element is null. Selector: " + step.getSelector());
                }
                String attrToCheck = (step.getAttribute() != null && !step.getAttribute().trim().isEmpty())
                        ? step.getAttribute().trim() : "src";
                String actualSrc = element.getAttribute(attrToCheck);
                if (actualSrc == null) actualSrc = "";
                String expectedSrc = expected != null ? expected.trim() : "";
                // If expected is empty we only verify the attribute is non-empty (image loaded)
                if (expectedSrc.isEmpty()) {
                    if (actualSrc.isEmpty()) {
                        throw new RuntimeException(
                                String.format("IMAGE verification failed: attribute [%s] is empty on element. Selector: %s",
                                        attrToCheck, step.getSelector())
                        );
                    }
                } else if (!actualSrc.contains(expectedSrc)) {
                    throw new RuntimeException(
                            String.format("IMAGE verification failed: expected [%s] attribute to contain [%s] but found [%s]. Selector: %s",
                                    attrToCheck, expectedSrc, actualSrc, step.getSelector())
                    );
                }
                logger.info("IMAGE verification passed. [{}]=[{}]", attrToCheck, actualSrc);
                break;
            }

            // ── Page-level ───────────────────────────────────────────────────────────
            case URL: {
                String actualUrl = driver.getCurrentUrl();
                String expectedUrl = expected != null ? expected.trim() : "";
                if (!actualUrl.contains(expectedUrl)) {
                    throw new RuntimeException(
                            String.format("URL verification failed: expected URL to contain [%s] but current URL is [%s]",
                                    expectedUrl, actualUrl)
                    );
                }
                logger.info("URL verification passed. Current URL: [{}]", actualUrl);
                break;
            }

            case TITLE: {
                String actualTitle = driver.getTitle();
                String expectedTitle = expected != null ? expected.trim() : "";
                if (!actualTitle.contains(expectedTitle)) {
                    throw new RuntimeException(
                            String.format("TITLE verification failed: expected title to contain [%s] but found [%s]",
                                    expectedTitle, actualTitle)
                    );
                }
                logger.info("TITLE verification passed. Title: [{}]", actualTitle);
                break;
            }

            // ── Count ────────────────────────────────────────────────────────────────
            case COUNT: {
                java.util.List<WebElement> countMatches = driver.findElements(By.cssSelector(step.getSelector()));
                int actualCount = countMatches.size();
                int expectedCount;
                try {
                    expectedCount = Integer.parseInt(expected != null ? expected.trim() : "0");
                } catch (NumberFormatException e) {
                    throw new RuntimeException("COUNT verification failed: expected value [" + expected + "] is not a valid integer.");
                }
                if (actualCount != expectedCount) {
                    throw new RuntimeException(
                            String.format("COUNT verification failed: expected [%d] element(s) but found [%d]. Selector: %s",
                                    expectedCount, actualCount, step.getSelector())
                    );
                }
                logger.info("COUNT verification passed. Found [{}] element(s).", actualCount);
                break;
            }

            // ── AI (future / not yet implemented) ───────────────────────────────────
            case AI: {
                logger.warn("AI verification type is not yet implemented for step [{}]. Skipping.", step.getName());
                break;
            }

            default:
                logger.warn("Unhandled VerificationType [{}] in step [{}]. Skipping.", vType, step.getName());
        }
    }
}

