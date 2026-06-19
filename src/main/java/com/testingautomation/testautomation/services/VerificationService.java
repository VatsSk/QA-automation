package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.entities.Verify;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.services.orchestratorService.ScenarioOrchestratorService;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VerificationService {

    private final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    /**
     * Verifies page elements for a scenario by running querySelector checks.
     *
     * <p>For each {@link Verify} item in the chosen verification list, this method
     * executes {@code document.querySelector(cssSelector)} via JavaScript, retrieves
     * the element's {@code textContent}, and compares it against the expected result.
     * Each item's {@code status} and {@code message} are updated accordingly.</p>
     *
     * <p>After processing all items the scenario's {@code verificationStatus} is set to:
     * <ul>
     *   <li>{@code PASSED}  – every check matched</li>
     *   <li>{@code FAILED}  – every check failed</li>
     *   <li>{@code PARTIAL} – some passed, some failed</li>
     * </ul>
     *
     * @param driver              the active Selenium WebDriver
     * @param scenario            the scenario whose verification list to process
    //     * @param verificationExtractor a function that extracts the desired verification list
     *                             from the scenario (e.g. {@code Scenario::getInitialVerification}
     *                             or {@code Scenario::getFinalVerification})
     */
    public void verifyScenarioPageFinal(
            WebDriver driver,
            Scenario scenario,
            List<Verify> verifications,
            TestCaseDTO resultTestCase,
            Map<Integer,List<Verify>> verificationResultMap,
            String expectedResult,
            boolean exceptionOccurred
    ) {
        if(exceptionOccurred) {
            resultTestCase.setResult("Failed");
            return;
        }
        if (verifications == null || verifications.isEmpty()) {
            logger.info("Scenario [{}] has no verification items in the requested list – nothing to verify.",
                    scenario.getId());
            resultTestCase.setResult("Passed");
            return;
        }
        Map<String,String> values=resultTestCase.getValues();

        logger.info("Scenario [{}] – starting page verification for {} items",
                scenario.getId(), verifications.size());

        JavascriptExecutor js = (JavascriptExecutor) driver;
        int passCount = 0;
        int failCount = 0;

        List<Verify> loopVerification=new ArrayList<>(verifications);
        for (Verify verify : loopVerification) {
            String cssSelector = verify.getCssSelector();
            String expected = verify.getExpectedResult();

            if (cssSelector == null || cssSelector.isBlank()) {
                verify.setStatus(false);
                verify.setMessage("CSS selector is null or blank – cannot query element.");
                failCount++;
                logger.warn("Verification skipped – empty CSS selector. Expected: '{}'", expected);
                continue;
            }

            try {
                // Use document.querySelector to find the element and return its textContent
                String actualText = (String) js.executeScript(
                        "var el = document.querySelector(arguments[0]); "
                                + "return el ? el.textContent.trim() : null;",
                        cssSelector
                );
                if(actualText ==  null && expected==null) {
                    verify.setStatus(true);
                    passCount++;
                } else if (actualText == null) {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Element not found for selector '%s'.", cssSelector));
                    failCount++;
                    logger.warn("Verification FAILED – element not found. Selector: '{}', Expected: '{}'",
                            cssSelector, expected);
                } else if (actualText.equals(expected)) {
                    verify.setStatus(true);
                    verify.setMessage(String.format(
                            "Passed – text content matches. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    passCount++;
                    logger.info("Verification PASSED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                } else {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Failed – text content mismatch. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    failCount++;
                    logger.warn("Verification FAILED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                }
            } catch (Exception e) {
                verify.setStatus(false);
                verify.setMessage(String.format(
                        "Error while querying selector '%s': %s", cssSelector, e.getMessage()));
                failCount++;
                logger.error("Verification ERROR – Selector: '{}', Exception: {}",
                        cssSelector, e.getMessage(), e);
            }
        }
        verificationResultMap.put(Integer.parseInt(resultTestCase.getTestcaseId()),loopVerification);

        if((passCount==verifications.size() && "Passed".equalsIgnoreCase(expectedResult))
                || "Failed".equalsIgnoreCase(expectedResult)) {
            resultTestCase.setResult("Passed");
        }else{
            resultTestCase.setResult("Failed");
        }

        logger.info("Scenario [{}] verification complete – Passed: {}, Failed: {}, Status: {}",
                scenario.getId(), passCount, failCount, scenario.getVerificationStatus());
    }

    public void verifyScenarioPageInitial(
            WebDriver driver,
            Scenario scenario,
            List<Verify> verifications,
            TestCaseDTO resultTestCase,
            Map<Integer,List<Verify>> verificationResultMap,
            boolean isInitial
    ) {

        if (verifications == null || verifications.isEmpty()) {
            logger.info("Scenario [{}] has no verification items in the requested list – nothing to verify.",
                    scenario.getId());
            resultTestCase.setResult("Passed");
            scenario.setScenarioStatus(RunStatus.PASSED);
            return;
        }
        Map<String,String> values=resultTestCase.getValues();

        logger.info("Scenario [{}] – starting page verification for {} items",
                scenario.getId(), verifications.size());

        JavascriptExecutor js = (JavascriptExecutor) driver;
        int passCount = 0;
        int failCount = 0;

        List<Verify> loopVerification=new ArrayList<>(verifications);
        for (Verify verify : loopVerification) {
            String cssSelector = verify.getCssSelector();
            String expected = verify.getExpectedResult();

            if (cssSelector == null || cssSelector.isBlank()) {
                verify.setStatus(false);
                verify.setMessage("CSS selector is null or blank – cannot query element.");
                failCount++;
                logger.warn("Verification skipped – empty CSS selector. Expected: '{}'", expected);
                continue;
            }

            try {
                // Use document.querySelector to find the element and return its textContent
                String actualText = (String) js.executeScript(
                        "var el = document.querySelector(arguments[0]); "
                                + "return el ? el.textContent.trim() : null;",
                        cssSelector
                );

                if (actualText == null) {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Element not found for selector '%s'.", cssSelector));
                    failCount++;
                    logger.warn("Verification FAILED – element not found. Selector: '{}', Expected: '{}'",
                            cssSelector, expected);
                } else if (actualText.equals(expected)) {
                    verify.setStatus(true);
                    verify.setMessage(String.format(
                            "Passed – text content matches. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    passCount++;
                    logger.info("Verification PASSED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                } else {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Failed – text content mismatch. Expected: '%s', Actual: '%s'.",
                            expected, actualText));
                    failCount++;
                    logger.warn("Verification FAILED – Selector: '{}', Expected: '{}', Actual: '{}'",
                            cssSelector, expected, actualText);
                }
            } catch (Exception e) {
                verify.setStatus(false);
                verify.setMessage(String.format(
                        "Error while querying selector '%s': %s", cssSelector, e.getMessage()));
                failCount++;
                logger.error("Verification ERROR – Selector: '{}', Exception: {}",
                        cssSelector, e.getMessage(), e);
            }
        }
        verificationResultMap.put(Integer.parseInt(resultTestCase.getTestcaseId()),loopVerification);
        if(isInitial){
            // Determine overall verification status
            if (passCount == verifications.size()) {
                values.put("verificationStatus","Passed");
            } else {
                values.put("verificationStatus","Failed");
            }
        }else{
            if (passCount == verifications.size()) {
                resultTestCase.setResult("Passed");
                if(scenario.getScenarioStatus() == RunStatus.DRAFT ){
                    scenario.setScenarioStatus(RunStatus.PASSED);
                }else if(scenario.getScenarioStatus()==RunStatus.FAILED){
                    scenario.setScenarioStatus(RunStatus.PARTIAL);
                }
            } else {
                resultTestCase.setResult("Failed");
                if(scenario.getScenarioStatus()==RunStatus.PASSED){
                    scenario.setScenarioStatus(RunStatus.PARTIAL);
                }else if(scenario.getScenarioStatus()==RunStatus.DRAFT){
                    scenario.setScenarioStatus(RunStatus.FAILED);
                }
            }
//            if (failCount == verifications.size())
        }

        logger.info("Scenario [{}] verification complete – Passed: {}, Failed: {}, Status: {}",
                scenario.getId(), passCount, failCount, scenario.getVerificationStatus());
    }

    public boolean verifyScenario(
            WebDriver driver,
            Scenario scenario,
            List<Verify> verifications,
            TestCaseDTO resultTestCase,
            Map<Integer,List<Verify>> verificationResultMap
    ) {
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
                String actualText = (String) js.executeScript(
                        "var el = document.querySelector(arguments[0]); "
                                + "return el ? el.textContent.trim() : null;",
                        cssSelector
                );
                if(actualText ==  null && expected==null) {
                    verify.setStatus(true);
                } else if (actualText == null) {
                    verify.setStatus(false);
                    verify.setMessage(String.format(
                            "Element not found for selector '%s'.", cssSelector));
                    logger.warn("Verification FAILED – element not found. Selector: '{}', Expected: '{}'",
                            cssSelector, expected);
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
