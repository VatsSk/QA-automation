package com.testingautomation.testautomation.services.screenShotsService;

import com.testingautomation.testautomation.dto.responseDto.ScenarioScreenshotsResponse;
import com.testingautomation.testautomation.services.s3Service.S3StorageService;
import com.testingautomation.testautomation.utils.UtilServices;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class ScreenshotService {
    private final Logger logger = LoggerFactory.getLogger(ScreenshotService.class);

    private final S3StorageService s3StorageService;
    private final UtilServices utilServices;

    public ScreenshotService(S3StorageService s3StorageService, UtilServices utilServices) {
        this.s3StorageService = s3StorageService;
        this.utilServices = utilServices;
    }

    /**
     * Captures screenshot in-memory as byte array and uploads directly to S3 without writing to local disk.
     */
    public String takeScreenshot(WebDriver driver1,
                                 String testCaseId,
                                 String name,
                                 String scenarioPrefix) {
        try {
            byte[] screenshotBytes = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.BYTES);
            String filename = name + ".png";
            String s3Key = scenarioPrefix + "/" + testCaseId + "/screenshots/" + filename;

            String url = s3StorageService.uploadBytes(screenshotBytes, s3Key, "image/png");
            logger.info("Screenshot uploaded directly to S3: {}", url);
            return url;
        } catch (Exception ex) {
            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Backward-compatible overload ignoring screenshotsDir and uploading directly to S3.
     */
    public String takeScreenshot(WebDriver driver1,
                                 String testCaseId,
                                 String name,
                                 Path screenshotsDir,
                                 String scenarioPrefix) {
        return takeScreenshot(driver1, testCaseId, name, scenarioPrefix);
    }

    /**
     * Captures element-highlighted screenshot in-memory as byte array and uploads directly to S3 without writing to local disk.
     */
    public String takeScreenshot(WebDriver driver1,
                                 WebElement element,
                                 String testCaseId,
                                 String name,
                                 String scenarioPrefix,
                                 String color) {
        try {
            // 🔥 highlight before capture
            utilServices.highlightElement(driver1, element, color);
            byte[] screenshotBytes = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.BYTES);
            String filename = name + ".png";
            String s3Key = scenarioPrefix + "/" + testCaseId + "/screenshots/" + filename;

            String url = s3StorageService.uploadBytes(screenshotBytes, s3Key, "image/png");
            // 🔄 restore after capture
            utilServices.restoreElement(driver1, element);
            logger.info("Screenshot uploaded directly to S3: {}", url);
            return url;
        } catch (Exception ex) {
            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Backward-compatible overload ignoring screenshotsDir and uploading directly to S3.
     */
    public String takeScreenshot(WebDriver driver1,
                                 WebElement element,
                                 String testCaseId,
                                 String name,
                                 Path screenshotsDir,
                                 String scenarioPrefix,
                                 String color) {
        return takeScreenshot(driver1, element, testCaseId, name, scenarioPrefix, color);
    }

    public ScenarioScreenshotsResponse getScenarioScreenshots(String prefix) {
        logger.info("Screenshot prefix is " + prefix);
        return new ScenarioScreenshotsResponse(
                s3StorageService.listScreenshotUrls(prefix)
        );
    }
}
