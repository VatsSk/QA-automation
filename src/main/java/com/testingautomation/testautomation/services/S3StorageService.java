package com.testingautomation.testautomation.services;


import com.testingautomation.testautomation.config.StorageProperties;
import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.dto.responseDto.ScreenshotItemResponse;
import com.testingautomation.testautomation.loader.CsvTestCaseLoader;
import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class S3StorageService {
    Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private StorageProperties storageProperties;

    @Value("${storage.s3.bucket-name}")
    private String bucket;

    @Value("${storage.s3.base-prefix}")
    private String resultsBaseDir;

    @Autowired
    private CsvTestCaseLoader csvLoader;

    private static final Duration PRESIGNED_GET_TTL = Duration.ofMinutes(15);

    public String uploadFile(Path file, String key) {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.putObject(request, file);

        // NOTE:
        // This returns a plain S3 URL (not signed).
        // Fine for logging/storage reference, but NOT enough for private bucket viewing.
        return s3Client.utilities()
                .getUrl(builder -> builder.bucket(bucket).key(key))
                .toExternalForm();
    }

    /**
     * List all image files under the given prefix and return presigned GET URLs.
     */
    public List<ScreenshotItemResponse> listScreenshotUrls(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("S3 prefix must not be blank");
        }

        String normalizedPrefix = prefix.trim();
        String bucket = storageProperties.getBucketName();

        // Optional safety restriction
        if (!normalizedPrefix.startsWith("qa_automation/")) {
            throw new IllegalArgumentException("Invalid screenshot prefix");
        }

        List<S3Object> allObjects = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(normalizedPrefix)
                    .maxKeys(1000);

            if (continuationToken != null) {
                builder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());

            if (response.contents() != null) {
                allObjects.addAll(response.contents());
            }

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;

        } while (continuationToken != null);

        return allObjects.stream()
                .filter(obj -> obj.key() != null && !obj.key().endsWith("/"))
                .filter(obj -> isImageFile(obj.key()))
                .sorted(Comparator.comparing(S3Object::key))
                .map(obj -> new ScreenshotItemResponse(
                        extractFileName(obj.key()),
                        generatePresignedGetUrl(obj.key())
                ))
                .toList();
    }

    /**
     * Temporary signed GET URL for private S3 object.
     */
    public String generatePresignedGetUrl(String key) {
        String bucket = storageProperties.getBucketName();
        Duration expiry = Duration.ofMinutes(storageProperties.getPresignedUrlExpiryMinutes());

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    private boolean isImageFile(String key) {
        String lower = key.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    private String extractFileName(String key) {
        int idx = key.lastIndexOf('/');
        return (idx >= 0 && idx < key.length() - 1)
                ? key.substring(idx + 1)
                : key;
    }

    public void writeAndUploadScenarioCsvs(
            Map<String, List<TestCaseDTO>> scenarioResultsMap,
            Run run
    ) {
        List<Scenario> scenarios = run.getScenariosList();

        for (Map.Entry<String, List<TestCaseDTO>> entry : scenarioResultsMap.entrySet()) {
            String scenarioS3Prefix = entry.getKey();
            List<TestCaseDTO> scenarioTestCases = entry.getValue();

            if (scenarioTestCases == null || scenarioTestCases.isEmpty()) {
                logger.warn("No testcase data found for scenarioPrefix {}", scenarioS3Prefix);
                continue;
            }

            try {
                // local directory = resultsBaseDir + scenario prefix path
                Path scenarioDir = Paths.get(resultsBaseDir, scenarioS3Prefix);
                Files.createDirectories(scenarioDir);

                Path scenarioCsv = csvLoader.writeScenarioCsv(scenarioTestCases, scenarioDir);

                // S3 key = same scenario prefix + file name
                String s3Key = scenarioS3Prefix + "/scenario-results.csv";
                String csvUrl = uploadFile(scenarioCsv, s3Key);

                logger.info("Uploaded CSV for scenarioPrefix {} -> {}", scenarioS3Prefix, csvUrl);

                for (Scenario sc : scenarios) {
                    if (scenarioS3Prefix.equals(sc.getScenarioBasePath())) {
                        sc.setResultCsv(csvUrl);
                        break;
                    }
                }

            } catch (Exception e) {
                logger.error("Failed writing/uploading CSV for scenarioPrefix {}", scenarioS3Prefix, e);
            }
        }
    }
}