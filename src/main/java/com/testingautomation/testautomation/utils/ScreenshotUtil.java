package com.testingautomation.testautomation.utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
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
import java.util.List;

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

    public static List<File> captureScrollablePageScreenshots(WebDriver driver) throws IOException, InterruptedException {
        List<File> screenshots = new ArrayList<>();

        JavascriptExecutor js = (JavascriptExecutor) driver;

        Long totalHeight = ((Number) js.executeScript("return document.body.scrollHeight")).longValue();
        Long viewportHeight = ((Number) js.executeScript("return window.innerHeight")).longValue();

        if (totalHeight == null || viewportHeight == null || viewportHeight <= 0) {
            throw new RuntimeException("Unable to determine page dimensions for screenshot capture.");
        }

        log.info("Capturing scroll screenshots: totalHeight={}, viewportHeight={}", totalHeight, viewportHeight);

        long currentScroll = 0;
        int index = 1;

        while (currentScroll < totalHeight) {
            js.executeScript("window.scrollTo(0, arguments[0]);", currentScroll);
            Thread.sleep(1200); // allow lazy-loaded UI to render

            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File saved = saveScreenshot(shot, "ai_assert_" + index + ".png");
            screenshots.add(saved);

            log.info("Captured screenshot {} at scrollY={}", index, currentScroll);

            currentScroll += viewportHeight;

            // Prevent infinite loops on weird pages
            if (index > 20) {
                log.warn("Stopping screenshot capture after 20 screens for safety.");
                break;
            }

            index++;
        }

        // Scroll back to top after capture
        js.executeScript("window.scrollTo(0, 0);");
        Thread.sleep(500);

        return screenshots;
    }}
