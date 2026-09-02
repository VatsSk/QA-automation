package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.FlowExecutionContext;
import com.testingautomation.testautomation.dto.FrameNode;
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
import java.util.Objects;

@Service
public class FrameContextManager {
    private static final Logger logger = LoggerFactory.getLogger(FrameContextManager.class);

    public void invalidateContext(FlowExecutionContext context) {
        context.setCurrentFramePath(new ArrayList<>());
        context.setFrameContextValid(false);
        logger.info("[FRAME] Context invalidated");
    }

    public void ensureFrameContext(FlowExecutionContext context, List<FrameNode> targetPath, Duration timeout) {
        if (targetPath == null) {
            targetPath = new ArrayList<>();
        }

        WebDriver driver = context.getDriver();
        List<FrameNode> currentPath = context.getCurrentFramePath();
        
        // If context is invalid (e.g. after navigation), start from default content
        if (!context.isFrameContextValid()) {
            logger.info("[FRAME] Context was invalid. Switching to default content.");
            driver.switchTo().defaultContent();
            currentPath = new ArrayList<>();
            context.setFrameContextValid(true);
        }

        // Find common prefix
        int commonPrefixLen = 0;
        int minLen = Math.min(currentPath.size(), targetPath.size());
        for (int i = 0; i < minLen; i++) {
            if (Objects.equals(currentPath.get(i), targetPath.get(i))) {
                commonPrefixLen++;
            } else {
                break;
            }
        }

        // Move to parent or default content if we need to back out
        if (commonPrefixLen < currentPath.size()) {
            if (commonPrefixLen == 0) {
                logger.info("[FRAME] Current: {} Target: {}. Switching to default content.", formatPath(currentPath), formatPath(targetPath));
                driver.switchTo().defaultContent();
                currentPath.clear();
            } else {
                // Selenium allows parentFrame(), we can use it to back out
                for (int i = currentPath.size(); i > commonPrefixLen; i--) {
                    logger.info("[FRAME] Current: {}. Moving to parent frame.", formatPath(currentPath));
                    driver.switchTo().parentFrame();
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }

        // Switch into new frames
        for (int i = commonPrefixLen; i < targetPath.size(); i++) {
            FrameNode targetFrame = targetPath.get(i);
            logger.info("[FRAME] Current: {} Target: {}. Switching into: {}", formatPath(currentPath), formatPath(targetPath), targetFrame.getSelector());
            
            try {
                switchIntoFrame(driver, targetFrame, timeout);
                currentPath.add(targetFrame);
                logger.info("[FRAME] Context updated: {}", formatPath(currentPath));
            } catch (Exception e) {
                logger.error("[FRAME] Failed to switch into frame: {}", targetFrame, e);
                // Invalidate context since we failed mid-traversal
                invalidateContext(context);
                throw new RuntimeException("FRAME_ACCESS_FAILED: Unable to access target iframe. " + targetFrame.getSelector(), e);
            }
        }
        
        context.setCurrentFramePath(new ArrayList<>(currentPath));
    }

    protected void switchIntoFrame(WebDriver driver, FrameNode frame, Duration timeout) {
        WebDriverWait wait = new WebDriverWait(driver, timeout);

        try {
            wait.until(customFrameToBeAvailableAndSwitchToIt(frame));
        } catch (org.openqa.selenium.StaleElementReferenceException e) {
            logger.warn("[FRAME] Stale frame detected, retrying...");
            wait.until(customFrameToBeAvailableAndSwitchToIt(frame));
        }

        // After switching into the iframe, wait for the document inside to reach readyState=complete.
        // Cross-origin game iframes (e.g. Evolution Gaming) can take significant time to load their
        // content. Without this wait, element lookup starts while the React/Angular UI is still
        // initializing, causing "element not found" errors even though the frame switch succeeded.
        try {
            WebDriverWait contentWait = new WebDriverWait(driver, timeout);
            contentWait.until(driver1 ->
                "complete".equals(((JavascriptExecutor) driver1).executeScript("return document.readyState"))
            );
            logger.info("[FRAME] Document inside frame [{}] is ready (readyState=complete).", frame.getSelector());
        } catch (Exception e) {
            // Non-fatal: some cross-origin iframes block JS access; log and continue.
            logger.warn("[FRAME] Could not verify readyState inside frame [{}]: {}. Proceeding anyway.", frame.getSelector(), e.getMessage());
        }

        // ── DIAGNOSTIC LOGGING ──────────────────────────────────────────────────────
        // Log details of the document we just switched into. This helps detect nested
        // iframe structures where the target element lives deeper than expected.
        try {
            String currentUrl   = (String) ((JavascriptExecutor) driver).executeScript("return window.location.href");
            String pageTitle    = (String) ((JavascriptExecutor) driver).executeScript("return document.title");
            Long   nestedFrames = (Long)   ((JavascriptExecutor) driver).executeScript("return document.querySelectorAll('iframe').length");
            Long   buttonCount  = (Long)   ((JavascriptExecutor) driver).executeScript("return document.querySelectorAll('button').length");
            logger.info("[FRAME-DIAG] Inside frame [{}] → URL: {}", frame.getSelector(), currentUrl);
            logger.info("[FRAME-DIAG] Inside frame [{}] → Title: '{}' | Nested iframes: {} | Buttons found: {}",
                    frame.getSelector(), pageTitle, nestedFrames, buttonCount);

            // Dump button texts so we can verify XPath text conditions match reality
            if (buttonCount != null && buttonCount > 0) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, String>> buttonInfos =
                    (java.util.List<java.util.Map<String, String>>) ((JavascriptExecutor) driver).executeScript(
                        "return Array.from(document.querySelectorAll('button')).map(function(b, i) {" +
                        "  return {" +
                        "    index: String(i)," +
                        "    text:  b.textContent.replace(/\\s+/g,' ').trim()," +
                        "    'class': b.className || ''" +
                        "  };" +
                        "});"
                    );
                if (buttonInfos != null) {
                    logger.info("[FRAME-DIAG] Buttons inside frame [{}]:", frame.getSelector());
                    for (java.util.Map<String, String> btn : buttonInfos) {
                        logger.info("[FRAME-DIAG]   button[{}] text='{}' | class='{}'",
                                btn.get("index"), btn.get("text"), btn.get("class"));
                    }
                }
            }

            if (nestedFrames != null && nestedFrames > 0) {
                logger.warn("[FRAME-DIAG] ⚠ {} nested iframe(s) detected inside [{}]. Dumping their attributes:",
                        nestedFrames, frame.getSelector());
                // Dump id, name, src, class of every nested iframe so we know what selector to use
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, String>> iframeAttrs =
                    (java.util.List<java.util.Map<String, String>>) ((JavascriptExecutor) driver).executeScript(
                        "return Array.from(document.querySelectorAll('iframe')).map(function(f, i) {" +
                        "  return {" +
                        "    index:   String(i)," +
                        "    id:      f.id || ''," +
                        "    name:    f.name || ''," +
                        "    src:     f.src || ''," +
                        "    'class': f.className || ''" +
                        "  };" +
                        "});"
                    );
                if (iframeAttrs != null) {
                    for (java.util.Map<String, String> attrs : iframeAttrs) {
                        logger.warn("[FRAME-DIAG]   nested iframe[{}] → id='{}' | name='{}' | class='{}' | src='{}'",
                                attrs.get("index"), attrs.get("id"), attrs.get("name"),
                                attrs.get("class"), attrs.get("src"));
                    }
                }
                logger.warn("[FRAME-DIAG] Update the step's framePath to [\"iframe\", \"<nested-selector>\"] " +
                        "using the id/name/class above.");
            }
        } catch (Exception e) {
            logger.warn("[FRAME-DIAG] Could not run diagnostics inside frame [{}]: {}", frame.getSelector(), e.getMessage());
        }
        // ── END DIAGNOSTIC LOGGING ───────────────────────────────────────────────────
    }

    private org.openqa.selenium.support.ui.ExpectedCondition<WebDriver> customFrameToBeAvailableAndSwitchToIt(FrameNode frame) {
        return new org.openqa.selenium.support.ui.ExpectedCondition<WebDriver>() {
            @Override
            public WebDriver apply(WebDriver driver) {
                try {
                    By locator = getLocatorForFrame(frame);
                    List<WebElement> frames = driver.findElements(locator);
                    int index = frame.getIndex() != null ? frame.getIndex() : 0;
                    if (frames.size() > index) {
                        return driver.switchTo().frame(frames.get(index));
                    }
                    return null;
                } catch (org.openqa.selenium.NoSuchFrameException | org.openqa.selenium.StaleElementReferenceException e) {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "frame to be available: " + frame.getSelector() + " at index: " + frame.getIndex();
            }
        };
    }

    private By getLocatorForFrame(FrameNode frame) {
        // Priority: id > name > css > xpath
        if (frame.getId() != null && !frame.getId().isEmpty()) {
            return By.id(frame.getId());
        }
        if (frame.getName() != null && !frame.getName().isEmpty()) {
            return By.name(frame.getName());
        }
        if (frame.getSelector() != null && !frame.getSelector().isEmpty()) {
            if ("xpath".equalsIgnoreCase(frame.getSelectorType())) {
                return By.xpath(frame.getSelector());
            } else {
                return By.cssSelector(frame.getSelector());
            }
        }
        
        throw new IllegalArgumentException("FrameNode missing locator information");
    }
    
    private String formatPath(List<FrameNode> path) {
        if (path == null || path.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < path.size(); i++) {
            sb.append("\"").append(path.get(i).getSelector()).append("\"");
            if (i < path.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
