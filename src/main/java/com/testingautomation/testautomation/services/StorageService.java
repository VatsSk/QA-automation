package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.config.StorageProperties;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties props;

    /**
     * Upload a CSV/XLSX test-case file to S3 under the testcases/ prefix.
     * Returns the S3 object key (including basePrefix if configured) — stored in Mongo, NOT the full URL.
     */
    public String uploadTestCaseCsv(MultipartFile file,
                                    String projectId,
                                    String moduleId,
                                    String runId,
                                    String   sequenceNo) {
        String ext        = FilenameUtils.getExtension(file.getOriginalFilename());
        String resolvedRun = (runId != null && !runId.isBlank()) ? runId : UUID.randomUUID().toString();
        String key = String.format("%s/%s/%s/%s/testcase.%s",
                projectId, moduleId, resolvedRun, sequenceNo, ext);
        return upload(file, key);
    }
    /**
     * Upload a screenshot image to S3 under the screenshots/ prefix.
     * Returns the S3 object key (including basePrefix if configured).
     */
    public String uploadScreenshot(MultipartFile file) {
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        String relative = "screenshots/" + UUID.randomUUID() + "." + ext;
        return upload(file, relative);
    }

    /**
     * Generate a presigned GET URL for the given S3 object key (the key you stored).
     * The provided objectKey may be either:
     *  - the relative key you used when uploading (e.g. "testcases/abc.csv"), or
     *  - the stored key that already includes the basePrefix (e.g. "qa_automation/testcases/abc.csv").
     *
     * The method normalizes and applies the configured basePrefix automatically.
     */
//    public String generatePresignedUrl(String objectKey) {
//        String finalKey = buildKey(objectKey);
//        validateKey(finalKey);
//        try {
//            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
//                    .bucket(props.getBucketName())
//                    .key(finalKey)
//                    .build();
//
//            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
//                    .getObjectRequest(getObjectRequest)
//                    .signatureDuration(Duration.ofMinutes(props.getPresignedUrlExpiryMinutes()))
//                    .build();
//
//            return s3Presigner.presignGetObject(presignRequest).url().toString();
//        } catch (Exception e) {
//            log.error("Failed to generate presigned URL for key {}: {}", finalKey, e.getMessage());
//            throw new GlobalExceptionHandler.StorageException("Could not generate presigned URL for: " + finalKey, e);
//        }
//    }

    // ── private ───────────────────────────────────────────────────────

    private String upload(MultipartFile file, String key) {
        String finalKey = buildKey(key);
        validateKey(finalKey);

        // avoid logging secrets; log minimal info
        log.info("S3 upload -> bucket='{}', key='{}', region='{}'",
                props.getBucketName(), finalKey, props.getRegion());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(finalKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            log.info("Uploaded to S3: s3://{}/{}", props.getBucketName(), finalKey);

            return s3Client.utilities()
                    .getUrl(builder -> builder.bucket(props.getBucketName()).key(key))
                    .toExternalForm();

        } catch (IOException e) {
            throw new GlobalExceptionHandler.StorageException("Failed to read uploaded file: " + file.getOriginalFilename(), e);
        } catch (Exception e) {
            log.error("S3 upload failed for key {}: {}", finalKey, e.getMessage(), e);
            throw new GlobalExceptionHandler.StorageException("S3 upload failed for key: " + finalKey, e);
        }
    }

    /**
     * Build the final S3 key by applying basePrefix if configured.
     * - If props.basePrefix is empty -> returns cleanedKey
     * - If cleanedKey already starts with basePrefix -> returns cleanedKey unchanged
     * - Otherwise returns basePrefix/cleanedKey
     *
     * This method also removes any leading slashes from the provided key.
     */
    private String buildKey(String relativeOrAbsoluteKey) {
        if (relativeOrAbsoluteKey == null || relativeOrAbsoluteKey.isBlank()) {
            throw new GlobalExceptionHandler.StorageException("S3 key cannot be null or blank", null);
        }

        String cleaned = relativeOrAbsoluteKey.replaceAll("^/+", ""); // remove leading slashes
        String basePrefix = props.getBasePrefix();

        if (basePrefix == null || basePrefix.isBlank()) {
            return cleaned;
        }

        String normalizedPrefix = basePrefix.replaceAll("^/+", "").replaceAll("/+$", "");

        if (cleaned.startsWith(normalizedPrefix + "/") || cleaned.equals(normalizedPrefix)) {
            return cleaned;
        }

        return normalizedPrefix + "/" + cleaned;
    }

    /** Prevent path traversal attacks on S3 keys. */
    private void validateKey(String key) {
        if (key == null || key.contains("..") || key.contains("//") || key.startsWith("/")) {
            throw new GlobalExceptionHandler.StorageException("Invalid S3 key: " + key, null);
        }
    }
}