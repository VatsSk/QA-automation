package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.entities.Verify;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.services.orchestratorService.ScenarioOrchestratorService;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.testingautomation.testautomation.utils.TextExtractor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VerificationService {

    private final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    private WebElement getTopMostModal(WebDriver driver) {
        List<WebElement> candidates = driver.findElements(By.cssSelector(".modal.show, .swal2-container"));

        logger.info("[ModalDetection] Total modal candidates found: {}", candidates.size());

        if (candidates.isEmpty()) {
            logger.info("[ModalDetection] No modal candidates found.");
            return null;
        }

        // Filter visible
        List<WebElement> visible = candidates.stream()
                .filter(el -> {
                    try {
                        return el.isDisplayed();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        logger.info("[ModalDetection] Visible modal candidates: {}", visible.size());

        if (visible.isEmpty()) {
            logger.info("[ModalDetection] No visible modals.");
            return null;
        }

        // Get the last visible modal (top-most by DOM order)
        WebElement topModal = visible.get(visible.size() - 1);

        logger.info("[ModalDetection] Top-most modal selected at index {}.",
                visible.size() - 1);

        return topModal;
    }
    public WebElement findBestElement(WebDriver driver, String cssSelector, Duration timeout) {

        WebDriverWait wait = new WebDriverWait(driver, timeout);

        return wait.until(d -> {

            // 🔹 Step 1: Detect modal
            WebElement modal = getTopMostModal(d);
            logger.info("[FindElement] Modal present: {}", modal != null);
            if (modal != null) {
                String modalId = modal.getAttribute("id");
                String modalClass = modal.getAttribute("class");

                logger.info(
                        "[FindElement] Modal present: true, id='{}', class='{}'",
                        modalId,
                        modalClass
                );

                logger.debug("[FindElement] Modal HTML: {}", modal.getAttribute("outerHTML"));
            } else {
                logger.info("[FindElement] Modal present: false");
            }

            // 🔹 Step 2: Context
            SearchContext context = (modal != null) ? modal : d;

            // 🔹 Step 3: Collect all elements
            List<WebElement> elements;
            try {
                elements = context.findElements(TextExtractor.resolveLocator(cssSelector));
            } catch (Exception e) {
                logger.warn("[FindElement] Error finding elements for selector '{}': {}", cssSelector, e.getMessage());
                return null;
            }

            if (elements.isEmpty()) {
                return null;
            }

            // 🔹 Step 4: Filter valid
            List<WebElement> valid = elements.stream()
                    .filter(el -> {
                        try {
                            boolean displayed = el.isDisplayed();
                            boolean visible = isActuallyVisible(d, el);

                            if (!displayed || !visible) {
                                logger.debug("[Filter] Rejected element -> displayed: {}, visible: {}", displayed, visible);
                            }

                            return displayed && visible;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            logger.info("[FindElement] Valid elements after filtering: {}", valid.size());

            if (valid.isEmpty()) {
                logger.warn("[FindElement] No valid visible elements found for '{}'", cssSelector);
                return null;
            }

            // 🔹 Step 5: Pick best (largest area)
            WebElement best = valid.stream()
                    .max(Comparator.comparingInt(el -> {
                        Rectangle r = el.getRect();
                        return r.getWidth() * r.getHeight();
                    }))
                    .orElse(null);

            if (best != null) {
                Rectangle r = best.getRect();
                logger.info("[FindElement] Selected best element -> size: {}x{}, area: {}",
                        r.getWidth(), r.getHeight(), r.getWidth() * r.getHeight());
                logger.debug("[FindElement] Selected element HTML: {}", best.getAttribute("outerHTML"));
            }

            return best;
        });
    }
//    private boolean isActuallyVisible(WebDriver driver, WebElement element) {
//        JavascriptExecutor js = (JavascriptExecutor) driver;
//        return Boolean.TRUE.equals(js.executeScript("""
//        const el = arguments[0];
//
//        if (!el) return false;
//
//        const rect = el.getBoundingClientRect();
//
//        if (
//            rect.width === 0 ||
//            rect.height === 0
//        ) {
//            return false;
//        }
//
//        const centerX = rect.left + rect.width / 2;
//        const centerY = rect.top + rect.height / 2;
//
//        const topElement = document.elementFromPoint(centerX, centerY);
//
//        return topElement === el || el.contains(topElement);
//    """, element));
//    }
//private boolean isActuallyVisible(WebDriver driver, WebElement element) {
//    JavascriptExecutor js = (JavascriptExecutor) driver;
//
//    return Boolean.TRUE.equals(js.executeScript("""
//        const el = arguments[0];
//
//        if (!el) return false;
//
//        const style = window.getComputedStyle(el);
//        if (
//            style.display === 'none' ||
//            style.visibility === 'hidden' ||
//            parseFloat(style.opacity) === 0
//        ) {
//            return false;
//        }
//
//        const rect = el.getBoundingClientRect();
//        if (rect.width === 0 || rect.height === 0) {
//            return false;
//        }
//
//        // 🔥 FIX: Allow modal elements even if overlapped by map
//        const isInsideModal = el.closest('.modal.show') !== null;
//
//        if (isInsideModal) {
//            return true;
//        }
//
//        // fallback for non-modal elements
//        const centerX = rect.left + rect.width / 2;
//        const centerY = rect.top + rect.height / 2;
//
//        const topElement = document.elementFromPoint(centerX, centerY);
//
//        return topElement === el || el.contains(topElement);
//    """, element));
//}
private boolean isActuallyVisible(WebDriver driver, WebElement element) {
    JavascriptExecutor js = (JavascriptExecutor) driver;

    if (element == null) {
        logger.warn("Element is null → returning false");
        return false;
    }

    logger.info("Checking visibility for element: {}", element);

    Object result = js.executeScript("""
    const el = arguments[0];

    const log = [];
    
    if (!el) {
        log.push("Element is null");
        return { visible: false, log };
    }

    const style = window.getComputedStyle(el);

    log.push("display: " + style.display);
    log.push("visibility: " + style.visibility);
    log.push("opacity: " + style.opacity);

    if (
        style.display === 'none' ||
        style.visibility === 'hidden'
    ) {
        log.push("Element hidden due to CSS");
        return { visible: false, log };
    }

    const rect = el.getBoundingClientRect();

    log.push("rect.width: " + rect.width);
    log.push("rect.height: " + rect.height);

    if (rect.width === 0 || rect.height === 0) {
        log.push("Element has zero size");
        return { visible: false, log };
    }

    const isInsideModal = el.closest('.modal.show') !== null;
    log.push("isInsideModal: " + isInsideModal);

    if (isInsideModal) {
        log.push("Element inside modal → forcing visible");
        return { visible: true, log };
    }

    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    log.push("centerX: " + centerX);
    log.push("centerY: " + centerY);

    // 🔥 FIX: Resolve correct root (Shadow DOM safe)
    const root = el.getRootNode();
    let topElement = null;

    if (root && typeof root.elementFromPoint === 'function') {
        topElement = root.elementFromPoint(centerX, centerY);
        log.push("Using root.elementFromPoint");
    } else {
        topElement = document.elementFromPoint(centerX, centerY);
        log.push("Using document.elementFromPoint");
    }

    if (!topElement) {
        log.push("topElement is null → fallback to visible");
        return { visible: true, log }; // ⚠️ fallback instead of false
    }

    const matches = topElement === el;
    const contains = el.contains(topElement);

    log.push("topElement matches: " + matches);
    log.push("element contains topElement: " + contains);

    const finalResult = matches || contains;

    log.push("finalResult: " + finalResult);

    return { visible: finalResult, log };

""", element);

    if (result instanceof Map<?, ?> resMap) {
        Boolean visible = (Boolean) resMap.get("visible");
        Object logs = resMap.get("log");

        logger.info("==== Visibility Debug Logs Start ====");
        if (logs instanceof List<?> logList) {
            for (Object log : logList) {
                logger.info("JS LOG → {}", log);
            }
        }
        logger.info("==== Visibility Debug Logs End ====");

        logger.info("Final visibility result: {}", visible);
        return Boolean.TRUE.equals(visible);
    }

    logger.warn("Unexpected JS return format → {}", result);
    return false;
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
                WebElement element = findBestElement(driver, cssSelector, Duration.ofSeconds(10));

                String actualText = element.getText().trim();
                verify.setActual(actualText);
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
