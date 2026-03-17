package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.StepAction;
import com.testingautomation.testautomation.executor.SeleniumExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Service
public class TestResultWriter {
    private final Logger logger = LoggerFactory.getLogger(TestResultWriter.class);
    /**
     * Append a line to CSV file in a thread-safe manner.
     */
    public synchronized void writeCsvLine(Path resultsCsv, String line) {
        try (BufferedWriter bw = Files.newBufferedWriter(resultsCsv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException ex) {
            logger.error("Failed to write CSV line: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Writes a CSV row describing a single step result to the given CSV path.
     */
    public void writeStepResultRow(WebDriver driver1, Path resultsCsv, String testCaseId, int stepNo, StepAction s,
                                    String status, String errorMessage, String screenshotPath) {
        String desc = s.getDescription() != null ? s.getDescription().replaceAll(",", " ") : "";
        String locatorType = s.getLocatorType() != null ? s.getLocatorType() : "";
        String locator = s.getLocator() != null ? s.getLocator().replaceAll(",", " ") : "";
        String payload = s.getPayload() != null ? s.getPayload().replaceAll(",", " ") : "";
        String pageUrl = driver1 != null ? safe(driver1.getCurrentUrl()) : "";
        String timestamp = safe(Instant.now().toString());
        String line = String.format("%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                testCaseId,
                stepNo,
                desc,
                locatorType,
                locator,
                payload,
                status,
                errorMessage,
                screenshotPath,
                pageUrl,
                timestamp
        );
        writeCsvLine(resultsCsv, line);
    }

    public void writeFinalResultRow(WebDriver driver1,Path resultsCsv, String testCaseId,StepAction s,
                                     String status, String screenshotPath) {
        String desc = s.getDescription() != null ? s.getDescription().replaceAll(",", " ") : "";
        String locatorType = s.getLocatorType() != null ? s.getLocatorType() : "";
        String locator = s.getLocator() != null ? s.getLocator().replaceAll(",", " ") : "";
        String payload = s.getPayload() != null ? s.getPayload().replaceAll(",", " ") : "";
        String pageUrl = driver1 != null ? safe(driver1.getCurrentUrl()) : "";
        String timestamp = safe(Instant.now().toString());
        String line = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                testCaseId,
//                    stepNo,
                desc,
                locatorType,
                locator,
                payload,
                status,
                screenshotPath,
                pageUrl,
                timestamp
        );
        writeCsvLine(resultsCsv, line);
    }
    public static String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n]", " ").replaceAll(",", " ");
    }
}
