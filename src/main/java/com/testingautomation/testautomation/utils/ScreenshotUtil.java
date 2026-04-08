package com.testingautomation.testautomation.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
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

    public static List<File> captureFullPage(WebDriver driver) throws IOException, InterruptedException {
        List<File> screenshots = new ArrayList<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Reset to top-left before starting
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(800);

        // Full document dimensions
        long totalWidth = ((Number) js.executeScript(
                "return Math.max(" +
                        "document.body.scrollWidth," +
                        "document.documentElement.scrollWidth," +
                        "document.body.offsetWidth," +
                        "document.documentElement.offsetWidth," +
                        "document.body.clientWidth," +
                        "document.documentElement.clientWidth" +
                        ");"
        )).longValue();

        long totalHeight = ((Number) js.executeScript(
                "return Math.max(" +
                        "document.body.scrollHeight," +
                        "document.documentElement.scrollHeight," +
                        "document.body.offsetHeight," +
                        "document.documentElement.offsetHeight," +
                        "document.body.clientHeight," +
                        "document.documentElement.clientHeight" +
                        ");"
        )).longValue();

        // Visible viewport dimensions
        long viewportWidth = ((Number) js.executeScript("return window.innerWidth;")).longValue();
        long viewportHeight = ((Number) js.executeScript("return window.innerHeight;")).longValue();

        if (totalWidth <= 0 || totalHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            throw new RuntimeException("Unable to determine page dimensions for full-page capture.");
        }

        // Use overlap so rows/elements don't get cut
        long horizontalStep = Math.max(1, Math.round(viewportWidth * 0.85));   // 15% overlap
        long verticalStep = Math.max(1, Math.round(viewportHeight * 0.85));    // 15% overlap

        List<Long> xPositions = buildFullCoveragePositions(totalWidth, viewportWidth, horizontalStep);
        List<Long> yPositions = buildFullCoveragePositions(totalHeight, viewportHeight, verticalStep);

        int index = 1;

        for (Long y : yPositions) {
            for (Long x : xPositions) {

                // Scroll the MAIN WINDOW
                js.executeScript("window.scrollTo(arguments[0], arguments[1]);", x, y);

                waitForWindowScrollSettle(driver);

                long actualX = ((Number) js.executeScript("return window.pageXOffset;")).longValue();
                long actualY = ((Number) js.executeScript("return window.pageYOffset;")).longValue();

                File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File saved = saveScreenshot(
                        shot,
                        String.format("page_%03d_reqX%d_reqY%d_actX%d_actY%d.png",
                                index, x, y, actualX, actualY)
                );

                screenshots.add(saved);

                log.info("Captured full-page tile {} => requested(x={}, y={}), actual(x={}, y={})",
                        index, x, y, actualX, actualY);

                index++;
            }
        }

        // Reset back to top-left
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(500);

        return screenshots;
    }
    private static void waitForWindowScrollSettle(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long lastX = -1;
        long lastY = -1;

        for (int i = 0; i < 15; i++) {
            long x = ((Number) js.executeScript("return window.pageXOffset;")).longValue();
            long y = ((Number) js.executeScript("return window.pageYOffset;")).longValue();

            if (x == lastX && y == lastY) {
                Thread.sleep(600); // extra settle for lazy UI
                return;
            }

            lastX = x;
            lastY = y;
            Thread.sleep(200);
        }

        Thread.sleep(600);
    }

    public static List<File> captureScrollableElementScreenshots(WebDriver driver, WebElement container)
            throws IOException, InterruptedException {

        List<File> screenshots = new ArrayList<>();

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Bring container into view
        js.executeScript("arguments[0].scrollIntoView({block:'start', inline:'start'});", container);
        Thread.sleep(1000);

        // Reset scroll first
        js.executeScript("arguments[0].scrollTop = 0; arguments[0].scrollLeft = 0;", container);
        Thread.sleep(800);

        long scrollWidth = ((Number) js.executeScript("return arguments[0].scrollWidth;", container)).longValue();
        long scrollHeight = ((Number) js.executeScript("return arguments[0].scrollHeight;", container)).longValue();
        long clientWidth = ((Number) js.executeScript("return arguments[0].clientWidth;", container)).longValue();
        long clientHeight = ((Number) js.executeScript("return arguments[0].clientHeight;", container)).longValue();

        if (scrollWidth <= 0 || scrollHeight <= 0 || clientWidth <= 0 || clientHeight <= 0) {
            throw new RuntimeException("Unable to determine container dimensions.");
        }

        log.info("Scrollable container => scrollWidth={}, scrollHeight={}, clientWidth={}, clientHeight={}",
                scrollWidth, scrollHeight, clientWidth, clientHeight);

        // Use fixed overlap so rows/columns don't get cut
        long horizontalStep = Math.max(1, Math.round(clientWidth * 0.85));   // 15% overlap
        long verticalStep = Math.max(1, Math.round(clientHeight * 0.85));    // 15% overlap

        List<Long> xPositions = buildFullCoveragePositions(scrollWidth, clientWidth, horizontalStep);
        List<Long> yPositions = buildFullCoveragePositions(scrollHeight, clientHeight, verticalStep);

        log.info("xPositions = {}", xPositions);
        log.info("yPositions = {}", yPositions);

        int index = 1;

        for (Long y : yPositions) {
            for (Long x : xPositions) {

                // Scroll INSIDE container
                js.executeScript(
                        "arguments[0].scrollLeft = arguments[1]; arguments[0].scrollTop = arguments[2];",
                        container, x, y
                );

                waitForContainerRender(driver, container);

                // Re-align container into viewport (important)
                js.executeScript("arguments[0].scrollIntoView({block:'start', inline:'start'});", container);
                Thread.sleep(300);

                // Confirm actual scroll landed correctly
                long actualX = ((Number) js.executeScript("return arguments[0].scrollLeft;", container)).longValue();
                long actualY = ((Number) js.executeScript("return arguments[0].scrollTop;", container)).longValue();

                File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File saved = saveScreenshot(
                        shot,
                        String.format("ai_assert_%03d_reqX%d_reqY%d_actX%d_actY%d.png",
                                index, x, y, actualX, actualY)
                );

                screenshots.add(saved);

                log.info("Captured screenshot {} => requested(x={}, y={}), actual(x={}, y={})",
                        index, x, y, actualX, actualY);

                index++;
            }
        }

        // Reset after capture
        js.executeScript("arguments[0].scrollTop = 0; arguments[0].scrollLeft = 0;", container);
        Thread.sleep(500);

        return screenshots;
    }

    private static List<Long> buildFullCoveragePositions(long totalSize, long viewportSize, long step) {
        List<Long> positions = new ArrayList<>();

        if (totalSize <= viewportSize) {
            positions.add(0L);
            return positions;
        }

        long maxScroll = totalSize - viewportSize;

        // Always start at 0
        positions.add(0L);

        long current = step;
        while (current < maxScroll) {
            positions.add(current);
            current += step;
        }

        // Always include final tail
        if (!positions.get(positions.size() - 1).equals(maxScroll)) {
            positions.add(maxScroll);
        }

        // Deduplicate and sort (extra safety)
        return positions.stream()
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }
    private static void waitForContainerRender(WebDriver driver, WebElement container) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long lastTop = -1;
        long lastLeft = -1;

        for (int i = 0; i < 15; i++) {
            long top = ((Number) js.executeScript("return arguments[0].scrollTop;", container)).longValue();
            long left = ((Number) js.executeScript("return arguments[0].scrollLeft;", container)).longValue();

            if (top == lastTop && left == lastLeft) {
                // Extra delay for lazy rendering / virtualization
                Thread.sleep(700);
                return;
            }

            lastTop = top;
            lastLeft = left;
            Thread.sleep(200);
        }

        Thread.sleep(700);
    }
    public static List<File> captureHeaderAcrossAllColumns(WebDriver driver, WebElement container)
            throws IOException, InterruptedException {

        List<File> screenshots = new ArrayList<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Reset to top so header is visible
        js.executeScript("arguments[0].scrollTop = 0; arguments[0].scrollLeft = 0;", container);
        Thread.sleep(800);

        long scrollWidth = ((Number) js.executeScript("return arguments[0].scrollWidth;", container)).longValue();
        long clientWidth = ((Number) js.executeScript("return arguments[0].clientWidth;", container)).longValue();

        long horizontalStep = Math.max(1, Math.round(clientWidth * 0.85));
        List<Long> xPositions = buildFullCoveragePositions(scrollWidth, clientWidth, horizontalStep);

        int index = 1;

        for (Long x : xPositions) {
            js.executeScript("arguments[0].scrollTop = 0; arguments[0].scrollLeft = arguments[1];", container, x);
            waitForContainerRender(driver, container);

            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File saved = saveScreenshot(shot, String.format("ai_header_%03d_x%d.png", index, x));

            screenshots.add(saved);
            index++;
        }

        return screenshots;
    }
}