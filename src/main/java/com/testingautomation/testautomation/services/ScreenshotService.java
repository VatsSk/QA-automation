package com.testingautomation.testautomation.services;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Service
public class ScreenshotService {
    private final Logger logger = LoggerFactory.getLogger(ScreenshotService.class);

    @Autowired
    private S3StorageService s3StorageService;

    @Value("${automation.screenshots.prefix}")
    private String screenshotPrefix;
    /**
     * Takes screenshot and returns the saved filename (full path) or empty string on failure.
     * Ensures a small repaint buffer and scroll to top before capture.
     */
//    public String takeScreenshot(WebDriver driver1, String name, Path screenshotsDir) {
//        try {
//            // ensure top-left visible and allow repaint
//            try {
//                ((JavascriptExecutor) driver1).executeScript("window.scrollTo(0,0)");
//                Thread.sleep(300);
//            } catch (Exception ignored) {}
//
//            File src = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.FILE);
//            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
//            String filename = screenshotsDir.resolve(name + "_" + timestamp + ".png").toString();
//            FileUtils.copyFile(src, new File(filename));
//            logger.info("Saved screenshot: {}", filename);
//            return filename;
//        } catch (Exception ex) {
//            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
//            return "";
//        }
//    }
    public String takeScreenshot(WebDriver driver1,
                                 String testCaseId,
                                 String name,
                                 Path screenshotsDir,
                                 String scenarioPrefix) {

        try {

            try {
                ((JavascriptExecutor) driver1).executeScript("window.scrollTo(0,0)");
                Thread.sleep(300);
            } catch (Exception ignored) {}

            File src = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.FILE);

            String timestamp = DateTimeFormatter.ISO_INSTANT
                    .format(Instant.now())
                    .replace(":", "-");

            String filename = name + "_" + timestamp + ".png";

            Path localPath = screenshotsDir.resolve(filename);

            FileUtils.copyFile(src, localPath.toFile());

            // S3 key
            String s3Key =
                    scenarioPrefix  +
                            "/" +
                            testCaseId +
                            "/screenshots/" +
                            filename;

            String url = s3StorageService.uploadFile(localPath, s3Key);

            logger.info("Screenshot uploaded to S3: {}", url);

            return url;

        } catch (Exception ex) {

            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);

            return null;
        }
    }
}
