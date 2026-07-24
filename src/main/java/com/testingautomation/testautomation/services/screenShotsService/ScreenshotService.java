package com.testingautomation.testautomation.services.screenShotsService;

import com.testingautomation.testautomation.dto.responseDto.ScenarioScreenshotsResponse;
import com.testingautomation.testautomation.services.s3Service.S3StorageService;
import com.testingautomation.testautomation.utils.UtilServices;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
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

    public String takeScreenshot(WebDriver driver1,
                                 String testCaseId,
                                 String name,
                                 Path screenshotsDir,
                                 String scenarioPrefix) {

        try {
            File src = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.FILE);
            String filename = name  + ".png";
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

    public String takeScreenshot(WebDriver driver1,
                                 WebElement element,
                                 String testCaseId,
                                 String name,
                                 Path screenshotsDir,
                                 String scenarioPrefix) {

        try {
            // 🔥 highlight before capture
            utilServices.highlightElement(driver1, element);
            File src = ((TakesScreenshot) driver1).getScreenshotAs(OutputType.FILE);
            String filename = name  + ".png";
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
            // 🔄 restore after capture
            utilServices.restoreElement(driver1, element);
            logger.info("Screenshot uploaded to S3: {}", url);
            return url;
        } catch (Exception ex) {
            logger.error("Failed to take screenshot: {}", ex.getMessage(), ex);
            return null;
        }
    }

    public ScenarioScreenshotsResponse getScenarioScreenshots(String prefix) {
        logger.info("Screenshot prefix is "+prefix);
        return new ScenarioScreenshotsResponse(
                s3StorageService.listScreenshotUrls(prefix)
        );
    }
}
