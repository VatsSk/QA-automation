package com.testingautomation.testautomation.services.csvLoaderService;


import com.opencsv.CSVReader;
import com.opencsv.CSVReaderHeaderAware;
import com.testingautomation.testautomation.dto.TestCaseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
public class CsvTestCaseLoader {
    private final Logger logger = LoggerFactory.getLogger(CsvTestCaseLoader.class);
    private final S3Client s3Client;
    @Value("${storage.s3.bucket-name}")
    private String bucketName;

    public CsvTestCaseLoader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<TestCaseDTO> loadFromS3(String csvUrl) throws Exception {

        logger.info("Loading CSV testcases from S3: {}", csvUrl);

        List<TestCaseDTO> list = new ArrayList<>();

        String key = extractKeyFromUrl(csvUrl);

        logger.info("Extracted S3 key: {}", key);
        logger.info(
                "S3 GET -> bucket='{}', key='{}'",
                bucketName,
                key
        );

        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

        try (
                InputStream inputStream =
                        s3Client.getObject(request);

                CSVReader reader =
                        new CSVReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String[] headers = reader.readNext();

            if (headers == null || headers.length == 0) {
                throw new RuntimeException(
                        "CSV file is empty or headers missing"
                );
            }

            logger.info(
                    "CSV Headers -> {}",
                    Arrays.toString(headers)
            );

            String[] row;

            while ((row = reader.readNext()) != null) {

                Map<String, String> values =
                        new LinkedHashMap<>();

                String testCaseId = null;
                String expectedResult = null;

                for (int i = 0; i < headers.length; i++) {

                    String header =
                            headers[i] != null
                                    ? headers[i].trim()
                                    : "";

                    String value =
                            i < row.length
                                    ? row[i]
                                    : "";

                    if ("testCaseId".equals(header)) {
                        testCaseId = value;
                        continue;
                    }

                    if ("expectedResult".equals(header)) {
                        expectedResult = value;
                        continue;
                    }

                    values.put(
                            header,
                            value
                    );
                }

                TestCaseDTO testCase =
                        new TestCaseDTO(
                                testCaseId,
                                values
                        );

                testCase.setExpectedResult(
                        expectedResult
                );

                list.add(testCase);

                logger.info(
                        "CSV Ordered Keys -> {}",
                        values.keySet()
                );
            }

        } catch (Exception e) {

            logger.error(
                    "S3 read failed -> bucket='{}', key='{}', error='{}'",
                    bucketName,
                    key,
                    e.getMessage(),
                    e
            );

            throw e;
        }

        logger.info(
                "Total testcases loaded: {}",
                list.size()
        );

        return list;
    }
    private String extractKeyFromUrl(String csvUrl) {
        if (csvUrl == null) return null;
        
        String awsMarker = ".amazonaws.com/";
        int awsIdx = csvUrl.indexOf(awsMarker);
        if (awsIdx != -1) {
            return csvUrl.substring(awsIdx + awsMarker.length());
        }
        
        String bucketMarker = "/" + bucketName + "/";
        int bucketIdx = csvUrl.indexOf(bucketMarker);
        if (bucketIdx != -1) {
            return csvUrl.substring(bucketIdx + bucketMarker.length());
        }
        
        return csvUrl;
    }

    public Path writeScenarioCsv(List<TestCaseDTO> testCases, Path scenarioDir) throws IOException {

        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("No testcases found");
        }

        // If scenario directory already exists, delete it completely
        if (Files.exists(scenarioDir)) {
            deleteDirectoryRecursively(scenarioDir);
        }

        // Create fresh directory
        Files.createDirectories(scenarioDir);

        Path file = scenarioDir.resolve("scenario-results.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            TestCaseDTO first = testCases.get(0);

            List<String> headers = new ArrayList<>();
            headers.add("testCaseId");
            headers.addAll(first.getValues().keySet());
            headers.add("expectedResult");
            headers.add("actualResult");
            headers.add("Result");

            writer.write(String.join(",", headers));
            writer.newLine();

            for (TestCaseDTO tc : testCases) {
                List<String> row = new ArrayList<>();

                row.add(safe(tc.getTestcaseId()));

                for (String key : first.getValues().keySet()) {
                    row.add(safe(tc.getValue(key)));
                }

                row.add(safe(tc.getExpectedResult()));
                row.add(safe(tc.getActual()));
                row.add(safe(tc.getResult()));


                writer.write(String.join(",", row));
                writer.newLine();
            }
        }

        return file;
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walk(path)
                .sorted(Comparator.reverseOrder()) // delete files first, then folders
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
    private String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n]", " ").replaceAll(",", " ");
    }
}