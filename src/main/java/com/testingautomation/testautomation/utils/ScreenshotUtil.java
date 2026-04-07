package com.testingautomation.testautomation.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v119.page.Page;
import org.slf4j.Logger;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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

        devTools.send(Page.enable());

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


    public static List<File> captureScrollablePageScreenshots(WebDriver driver)
            throws IOException, InterruptedException {

        List<File> screenshots = new ArrayList<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Get full page dimensions
        long totalWidth = ((Number) js.executeScript(
                "return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);"
        )).longValue();

        long totalHeight = ((Number) js.executeScript(
                "return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"
        )).longValue();

        // Get viewport dimensions
        long viewportWidth = ((Number) js.executeScript("return window.innerWidth;")).longValue();
        long viewportHeight = ((Number) js.executeScript("return window.innerHeight;")).longValue();

        if (totalWidth <= 0 || totalHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            throw new RuntimeException("Unable to determine page dimensions for screenshot capture.");
        }

        // Scroll by 80% of viewport to keep overlap (prevents content being cut)
        long verticalStep = Math.max(1, Math.round(viewportHeight * 0.80));
        long horizontalStep = Math.max(1, Math.round(viewportWidth * 0.80));

        log.info("Page dimensions => totalWidth={}, totalHeight={}, viewportWidth={}, viewportHeight={}",
                totalWidth, totalHeight, viewportWidth, viewportHeight);
        log.info("Scroll steps => horizontalStep={}, verticalStep={}", horizontalStep, verticalStep);

        // Build Y positions
        List<Long> yPositions = buildScrollPositions(totalHeight, viewportHeight, verticalStep);

        // Build X positions
        List<Long> xPositions = buildScrollPositions(totalWidth, viewportWidth, horizontalStep);

        int index = 1;

        for (Long y : yPositions) {
            for (Long x : xPositions) {
                js.executeScript("window.scrollTo(arguments[0], arguments[1]);", x, y);

                // wait for rendering/lazy-load/sticky elements to settle
                Thread.sleep(1200);

                File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                File saved = saveScreenshot(
                        shot,
                        String.format("ai_assert_%03d_x%d_y%d.png", index, x, y)
                );

                screenshots.add(saved);

                log.info("Captured screenshot {} at x={}, y={}", index, x, y);
                index++;
            }
        }

        // Reset to top-left
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(500);

        return screenshots;
    }

    private static List<Long> buildScrollPositions(long totalSize, long viewportSize, long step) {
        List<Long> positions = new ArrayList<>();

        if (totalSize <= viewportSize) {
            positions.add(0L);
            return positions;
        }

        long maxScroll = totalSize - viewportSize;
        long current = 0;

        while (current < maxScroll) {
            positions.add(current);
            current += step;
        }

        // Ensure the final tail is always captured
        if (positions.isEmpty() || positions.get(positions.size() - 1) != maxScroll) {
            positions.add(maxScroll);
        }

        // Remove accidental duplicates while preserving order
        List<Long> unique = new ArrayList<>();
        Long prev = null;
        for (Long pos : positions) {
            if (!pos.equals(prev)) {
                unique.add(pos);
            }
            prev = pos;
        }

        return unique;
    }

}