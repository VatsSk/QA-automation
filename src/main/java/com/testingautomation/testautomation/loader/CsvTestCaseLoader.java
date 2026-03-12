package com.testingautomation.testautomation.loader;


import com.opencsv.CSVReaderHeaderAware;
import com.testingautomation.testautomation.dto.TestCase;
import com.testingautomation.testautomation.services.TestResultWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
public class CsvTestCaseLoader {
    private final Logger logger = LoggerFactory.getLogger(CsvTestCaseLoader.class);
    private final S3Client s3Client;
    @Value("${aws.s3.bucket}")
    private String bucketName;

    public CsvTestCaseLoader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * CSV format expectation:
     * header row with column names. Required columns: testCaseId, url
     * other columns will be treated as input fields matching scanner's id or name.
     */
//    public List<TestCase> load(String csvPath) throws Exception {
//        logger.info("Loading CSV testcases from {}", csvPath);
//        List<TestCase> list = new ArrayList<>();
//        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(csvPath))) {
//            Map<String,String> row;
//            while ((row = reader.readMap()) != null) {
//                String id = row.getOrDefault("testCaseId", UUID.randomUUID().toString());
//                String url = row.get("url");
//                Map<String,String> values = new HashMap<>(row);
//                values.remove("testCaseId"); // keep only input columns
//                list.add(new TestCase(id, url, values));
//                logger.debug("Loaded testcase {} url={}", id, url);
//            }
//        }
//        logger.info("Total testcases loaded: {}", list.size());
//        return list;
//    }
//    public List<TestCase> load(MultipartFile file) throws Exception {
//
//        logger.info("Loading CSV testcases from uploaded file {}", file.getOriginalFilename());
//
//        List<TestCase> list = new ArrayList<>();
//
//        try (CSVReaderHeaderAware reader =
//                     new CSVReaderHeaderAware(
//                             new InputStreamReader(file.getInputStream()))) {
//
//            Map<String,String> row;
//
//            while ((row = reader.readMap()) != null) {
//
//                String id = row.getOrDefault("testCaseId", UUID.randomUUID().toString().substring(0,8));
////                String url = row.get("url");
//                String expectedResult = row.get("expectedResult");
//
//                Map<String,String> values = new LinkedHashMap<>(row);
//                values.remove("testCaseId");
//                values.remove("expectedResult"); // remove from generic fields
//                TestCase testCase = new TestCase(id,values);
//                testCase.setExpectedResult(expectedResult);
//                list.add(testCase);
//
//                logger.debug("Loaded testcase {}", id);
//            }
//        }
//
//        logger.info("Total testcases loaded: {}", list.size());
//        System.out.println("testcases   ----  "+list);
//
//        return list;
//    }

    public List<TestCase> loadFromS3(String csvUrl) throws Exception {
        logger.info("Loading CSV testcases from S3: {}", csvUrl);
        List<TestCase> list = new ArrayList<>();
        String key = extractKeyFromUrl(csvUrl);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (InputStream inputStream = s3Client.getObject(request);
             CSVReaderHeaderAware reader =
                     new CSVReaderHeaderAware(new InputStreamReader(inputStream))) {

            Map<String, String> row;

            while ((row = reader.readMap()) != null) {

                String id = row.getOrDefault(
                        "testCaseId",
                        UUID.randomUUID().toString().substring(0, 8)
                );

                String expectedResult = row.get("expectedResult");

                Map<String, String> values = new LinkedHashMap<>(row);
                values.remove("testCaseId");
                values.remove("expectedResult");

                TestCase testCase = new TestCase(id, values);
                testCase.setExpectedResult(expectedResult);

                list.add(testCase);

                logger.debug("Loaded testcase {}", id);
            }
        }

        logger.info("Total testcases loaded: {}", list.size());

        return list;
    }

    private String extractKeyFromUrl(String csvUrl) {
        return csvUrl.substring(csvUrl.indexOf(".amazonaws.com/") + 14);
    }

    public Path writeScenarioCsv(List<TestCase> testCases, Path scenarioDir) throws IOException {

        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("No testcases found");
        }

        Path file = scenarioDir.resolve("scenario-results.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // header
            TestCase first = testCases.get(0);

            List<String> headers = new ArrayList<>(first.getValues().keySet());

            headers.add("expectedResult");
            headers.add("Result");

            writer.write(String.join(",", headers));
            writer.newLine();

            for (TestCase tc : testCases) {

                List<String> row = new ArrayList<>();

                for (String key : tc.getValues().keySet()) {
                    row.add(TestResultWriter.safe(tc.getValue(key)));
                }

                row.add(TestResultWriter.safe(tc.getExpectedResult()));
                row.add(TestResultWriter.safe(tc.getResult()));

                writer.write(String.join(",", row));
                writer.newLine();
            }
        }

        return file;
    }
}