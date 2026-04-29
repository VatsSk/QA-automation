package com.testingautomation.testautomation.services.s3Service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.testingautomation.testautomation.config.s3Config.StorageProperties;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
//    private final S3Presigner s3Presigner;
    private final StorageProperties props;

    /**
     * Upload a CSV/XLSX test-case file to S3 under the testcases/ prefix.
     * Returns the S3 object key (including basePrefix if configured) — stored in Mongo, NOT the full URL.
     */
    public String uploadTestCaseCsv(MultipartFile file,
                                    String projectId,
                                    String moduleId,
                                    String runId,
                                    String sequenceNo) {
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        String resolvedRun = (runId != null && !runId.isBlank()) ? runId : UUID.randomUUID().toString();
        String key = String.format("%s/%s/%s/%s/testcase.%s", projectId, moduleId, resolvedRun, sequenceNo, ext);

        try (
                Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                StringWriter stringWriter = new StringWriter();
                CSVReader csvReader = new CSVReader(reader);
                CSVWriter csvWriter = new CSVWriter(stringWriter)
        ) {
            List<String[]> rows = csvReader.readAll();

            if (rows == null || rows.isEmpty()) {
                throw new RuntimeException("Uploaded CSV is empty");
            }

            // Add header
            String[] originalHeader = rows.get(0);
            String[] newHeader = new String[originalHeader.length + 1];
            newHeader[0] = "testCaseId";
            System.arraycopy(originalHeader, 0, newHeader, 1, originalHeader.length);
            csvWriter.writeNext(newHeader);

            // Add data rows with 1,2,3...
            for (int i = 1; i < rows.size(); i++) {
                String[] originalRow = rows.get(i);
                String[] newRow = new String[originalRow.length + 1];
                newRow[0] = String.valueOf(i);   // 1-based index
                System.arraycopy(originalRow, 0, newRow, 1, originalRow.length);
                csvWriter.writeNext(newRow);
            }

            csvWriter.flush();

            byte[] modifiedCsvBytes = stringWriter.toString().getBytes(StandardCharsets.UTF_8);

            return upload(modifiedCsvBytes, "text/csv", key);

        } catch (Exception e) {
            throw new GlobalExceptionHandler.StorageException(
                    "Failed to process testcase CSV: " + file.getOriginalFilename(), e
            );
        }
    }

    public String uploadProjectLoginCsv(MultipartFile file,
                                        String projectName) {

        String ext = FilenameUtils.getExtension(file.getOriginalFilename());

        String safeProject =
                projectName == null || projectName.isBlank()
                        ? UUID.randomUUID().toString()
                        : projectName.trim()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");

        String key = String.format("%s/auth/login-credentials.%s", safeProject, ext);

        try (
                Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                StringWriter stringWriter = new StringWriter();
                CSVReader csvReader = new CSVReader(reader);
                CSVWriter csvWriter = new CSVWriter(stringWriter)
        ) {

            List<String[]> rows = csvReader.readAll();

            if (rows == null || rows.isEmpty()) {
                throw new RuntimeException("Uploaded CSV is empty");
            }

            // Add testCaseId to header
            String[] originalHeader = rows.get(0);
            String[] newHeader = new String[originalHeader.length + 1];

            newHeader[0] = "testCaseId";
            System.arraycopy(originalHeader, 0, newHeader, 1, originalHeader.length);

            csvWriter.writeNext(newHeader);

            // Add row numbers
            for (int i = 1; i < rows.size(); i++) {

                String[] originalRow = rows.get(i);
                String[] newRow = new String[originalRow.length + 1];

                newRow[0] = String.valueOf(i); // 1,2,3...
                System.arraycopy(originalRow, 0, newRow, 1, originalRow.length);

                csvWriter.writeNext(newRow);
            }

            csvWriter.flush();

            byte[] modifiedBytes =
                    stringWriter.toString().getBytes(StandardCharsets.UTF_8);

            return upload(modifiedBytes, "text/csv", key);

        } catch (Exception e) {

            throw new GlobalExceptionHandler.StorageException(
                    "Failed to upload login credential CSV: " +
                            file.getOriginalFilename(),
                    e
            );
        }
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


    // ── private ───────────────────────────────────────────────────────
    private String upload(byte[] fileBytes, String contentType, String key) {
        String finalKey = buildKey(key);
        validateKey(finalKey);

        log.info("S3 upload -> bucket='{}', key='{}', region='{}'",
                props.getBucketName(), finalKey, props.getRegion());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(finalKey)
                    .contentType(contentType)
                    .contentLength((long) fileBytes.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

            log.info("Uploaded to S3: s3://{}/{}", props.getBucketName(), finalKey);

            return s3Client.utilities()
                    .getUrl(builder -> builder.bucket(props.getBucketName()).key(finalKey))
                    .toExternalForm();

        } catch (Exception e) {
            log.error("S3 upload failed for key {}: {}", finalKey, e.getMessage(), e);
            throw new GlobalExceptionHandler.StorageException("S3 upload failed for key: " + finalKey, e);
        }
    }
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
                    .getUrl(builder -> builder.bucket(props.getBucketName()).key(finalKey))
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
