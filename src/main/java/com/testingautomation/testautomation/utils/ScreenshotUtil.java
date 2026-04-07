package com.testingautomation.testautomation.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v119.page.Page;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
public class ScreenshotUtil {
//    Logger logger = Logger.getLogger(ScreenshotUtil.class.getName());
    public static File saveScreenshot(File source, String fileName) throws IOException {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "ai-assert-screenshots");
        Files.createDirectories(dir);

        Path target = dir.resolve(fileName);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        return target.toFile();
    }

    public static List<File> captureFullPage(WebDriver driver) throws IOException {
        List<File> screenshots = new ArrayList<>();
        DevTools devTools = ((HasDevTools) driver).getDevTools();
        devTools.createSession();

        devTools.send(org.openqa.selenium.devtools.v121.page.Page.enable());

        String base64 = devTools.send(
                Page.captureScreenshot(
                        Optional.empty(),                 // format (png default)
                        Optional.empty(),                 // quality
                        Optional.empty(),                 // clip
                        Optional.of(true),                // fromSurface
                        Optional.of(true),                // captureBeyondViewport ✅ IMPORTANT
                        Optional.of(true)                 // optimizeForSpeed
                )
        );

        byte[] decoded = Base64.getDecoder().decode(base64);

        File file = new File("full_page.png");
        FileUtils.writeByteArrayToFile(file, decoded);
        screenshots.add(file);

        return screenshots;
    }

    public static List<File> captureSmartScrollableScreens(WebDriver driver)
            throws Exception {

        List<File> screenshots = new ArrayList<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 🔹 Step 1: Detect visible + scrollable elements
        List<WebElement> scrollables = (List<WebElement>) js.executeScript(
                "return Array.from(document.querySelectorAll('*')).filter(el => {" +
                        "  const style = window.getComputedStyle(el);" +
                        "  const rect = el.getBoundingClientRect();" +

                        "  const isVisible = style.display !== 'none' && " +
                        "                    style.visibility !== 'hidden' && " +
                        "                    rect.width > 0 && rect.height > 0 && " +
                        "                    rect.bottom > 0 && rect.right > 0 && " +
                        "                    rect.top < window.innerHeight && " +
                        "                    rect.left < window.innerWidth;" +

                        "  const isScrollable = (el.scrollHeight > el.clientHeight) || " +
                        "                       (el.scrollWidth > el.clientWidth);" +

                        "  return isVisible && isScrollable;" +
                        "});"
        );

        // 🔹 Step 2: Remove nested scrollables (keep top-most only)
        List<WebElement> valid = new ArrayList<>();

        for (WebElement el : scrollables) {

            Boolean isChildOfScrollable = (Boolean) js.executeScript(
                    "let parent = arguments[0].parentElement;" +
                            "while(parent) {" +
                            "  if(parent.scrollHeight > parent.clientHeight || parent.scrollWidth > parent.clientWidth) return true;" +
                            "  parent = parent.parentElement;" +
                            "}" +
                            "return false;",
                    el
            );

            if (!isChildOfScrollable) {
                valid.add(el);
            }
        }

        log.info("Final scrollable elements count: {}", valid.size());

        // 🔹 Step 3: Page vertical scroll
        Long totalHeight = ((Number) js.executeScript(
                "return document.body.scrollHeight")).longValue();

        Long viewportHeight = ((Number) js.executeScript(
                "return window.innerHeight")).longValue();

        Set<String> visited = new HashSet<>();
        int index = 1;

        for (long y = 0; y < totalHeight; y += viewportHeight) {

            // Scroll page vertically
            js.executeScript("window.scrollTo(0, arguments[0]);", y);
            Thread.sleep(800);

            for (WebElement el : valid) {

                Long scrollWidth = ((Number) js.executeScript(
                        "return arguments[0].scrollWidth", el)).longValue();

                Long visibleWidth = ((Number) js.executeScript(
                        "return arguments[0].clientWidth", el)).longValue();

                // 🔥 If no horizontal scroll → capture once
                if (scrollWidth <= visibleWidth) {

                    String key = y + "_0";
                    if (visited.contains(key)) continue;
                    visited.add(key);

                    File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    screenshots.add(saveScreenshot(shot, "ai_assert_" + index + ".png"));

                    log.info("Captured [{}] at Y={} (no horizontal scroll)", index, y);

                    index++;
                    continue;
                }

                // 🔥 Horizontal scroll loop
                for (long x = 0; x < scrollWidth; x += visibleWidth) {

                    String key = y + "_" + x;
                    if (visited.contains(key)) continue;
                    visited.add(key);

                    js.executeScript(
                            "arguments[0].scrollLeft = arguments[1];",
                            el, x
                    );

                    Thread.sleep(500);

                    File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    screenshots.add(saveScreenshot(shot,
                            "ai_assert_x_" + x + "_y_" + y + ".png"));

                    log.info("Captured [{}] at Y={}, elementX={}", index, y, x);

                    index++;

                    if (index > 60) return screenshots;
                }
            }
        }

        // 🔹 Reset scroll positions
        js.executeScript("window.scrollTo(0, 0);");
        for (WebElement el : valid) {
            js.executeScript("arguments[0].scrollLeft = 0;", el);
        }

        return screenshots;
    }
}
