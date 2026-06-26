package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.entities.Verify;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.services.orchestratorService.ScenarioOrchestratorService;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VerificationService {

    private final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    private boolean isActuallyVisible(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return Boolean.TRUE.equals(js.executeScript("""
        const el = arguments[0];

        if (!el) return false;

        const rect = el.getBoundingClientRect();

        if (
            rect.width === 0 ||
            rect.height === 0
        ) {
            return false;
        }

        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;

        const topElement = document.elementFromPoint(centerX, centerY);

        return topElement === el || el.contains(topElement);
    """, element));
    }
    public boolean verifyScenario(
            WebDriver driver,
            Scenario scenario,
            List<Verify> verifications,
            TestCaseDTO resultTestCase,
            Map<Integer,List<Verify>> verificationResultMap
    )  {
//        logger.info();
        if (verifications == null || verifications.isEmpty()) {
            logger.info("Scenario [{}] has no verification items in the requested list – nothing to verify.",
                    scenario.getId());
            return true;
        }
        boolean result = true;
        logger.info("Scenario [{}] – starting page verification for {} items",
                scenario.getId(), verifications.size());

        JavascriptExecutor js = (JavascriptExecutor) driver;

        List<Verify> loopVerification=new ArrayList<>(verifications);
        for (Verify verify : loopVerification) {
            String cssSelector = verify.getCssSelector();
            String expected = verify.getExpectedResult();

            if (cssSelector == null || cssSelector.isBlank()) {
                verify.setStatus(false);
                verify.setMessage("CSS selector is null or blank – cannot query element.");
                logger.warn("Verification skipped – empty CSS selector. Expected: '{}'", expected);
                continue;
            }

            try {
                // Use document.querySelector to find the element and return its textContent
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                WebElement element = wait.until(driver1 -> {
                    WebElement e = driver1.findElement(By.cssSelector(cssSelector));

                    if (!e.isDisplayed()) {
                        return null;
                    }

                    return isActuallyVisible(driver1, e) ? e : null;
                });

                String actualText = element.getText().trim();

                logger.info("actualText : {}",actualText);
                logger.info("Displayed = {}", element.isDisplayed());
                logger.info("ActuallyVisible = {}", isActuallyVisible(driver, element));
                logger.info("getText = [{}]", element.getText());
                logger.info("innerText = [{}]", element.getAttribute("innerText"));
                logger.info("textContent = [{}]", element.getAttribute("textContent"));
                logger.info("OuterHTML = {}", element.getAttribute("outerHTML"));
                if(actualText ==  null && expected==null) {
                    verify.setStatus(true);
                } else if (actualText == null) {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Element not found for selector '%s'.", cssSelector));
                    logger.warn("Verification FAILED – element not found. Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected,actualText);
                } else if (actualText.equals(expected)) {
                    verify.setStatus(true);
                    verify.setMessage(String.format(
                            "Passed – text content matches. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    logger.info("Verification PASSED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                } else {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Failed – text content mismatch. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    logger.warn("Verification FAILED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                }
            } catch (Exception e) {
                verify.setStatus(false);
                verify.setMessage(String.format(
                        "Error while querying selector '%s': %s", cssSelector, e.getMessage()));
                logger.error("Verification ERROR – Selector: '{}', Exception: {}",
                        cssSelector, e.getMessage(), e);
            }
            result=result && verify.getStatus();
        }
        verificationResultMap.put(Integer.parseInt(resultTestCase.getTestcaseId()),loopVerification);
        return result;
    }
}
