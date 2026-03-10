package com.testingautomation.testautomation.loader;


import com.opencsv.CSVReaderHeaderAware;
import com.testingautomation.testautomation.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CsvTestCaseLoader {
    private final Logger logger = LoggerFactory.getLogger(CsvTestCaseLoader.class);

    /**
     * CSV format expectation:
     * header row with column names. Required columns: testCaseId, url
     * other columns will be treated as input fields matching scanner's id or name.
     */
    public List<TestCase> load(String csvPath) throws Exception {
        logger.info("Loading CSV testcases from {}", csvPath);
        List<TestCase> list = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(csvPath))) {
            Map<String,String> row;
            while ((row = reader.readMap()) != null) {
                String id = row.getOrDefault("testCaseId", UUID.randomUUID().toString());
                String url = row.get("url");
                Map<String,String> values = new HashMap<>(row);
                values.remove("testCaseId"); // keep only input columns
                list.add(new TestCase(id, url, values));
                logger.debug("Loaded testcase {} url={}", id, url);
            }
        }
        logger.info("Total testcases loaded: {}", list.size());
        return list;
    }
    public List<TestCase> load(MultipartFile file) throws Exception {

        logger.info("Loading CSV testcases from uploaded file {}", file.getOriginalFilename());

        List<TestCase> list = new ArrayList<>();

        try (CSVReaderHeaderAware reader =
                     new CSVReaderHeaderAware(
                             new InputStreamReader(file.getInputStream()))) {

            Map<String,String> row;

            while ((row = reader.readMap()) != null) {

                String id = row.getOrDefault("testCaseId", UUID.randomUUID().toString().substring(0,8));
                String url = row.get("url");

                Map<String,String> values = new HashMap<>(row);
                values.remove("testCaseId");

                list.add(new TestCase(id, url, values));

                logger.debug("Loaded testcase {} url={}", id, url);
            }
        }

        logger.info("Total testcases loaded: {}", list.size());

        return list;
    }
}