package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.dto.responseDto.UploadResponse;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    private static final Set<String> ALLOWED_CSV_TYPES = Set.of(
            "text/csv", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/csv", "text/plain");

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg",
            "image/gif", "image/webp");

    /**
     * POST /api/uploads/testcase
     * Accepts CSV or XLSX; stores in MinIO; returns S3 path.
     */
    @PostMapping(value = "/testcase", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadTestCaseCsv(
            @RequestParam("file")       MultipartFile file,
            @RequestParam("projectId")  String projectId,
            @RequestParam("moduleId")   String moduleId,
            @RequestParam(value = "runId", required = false, defaultValue = "") String runId,
            @RequestParam("sequenceNo") String sequenceNo) {

        validateFile(file, ALLOWED_CSV_TYPES, 20 * 1024 * 1024L, "CSV/XLSX");

        log.info("Project Id"+projectId+"------"+"Module Id "+moduleId);

        String path = storageService.uploadTestCaseCsv(file, projectId, moduleId, runId, sequenceNo);
//        String url  = storageService.generatePresignedUrl(path);


        return ResponseEntity.ok(UploadResponse.builder()
                .path(path)
                .url(path)
                .filename(file.getOriginalFilename())
                .sizeBytes(file.getSize())
                .build());
    }

//    @PostMapping(value = "/testcase", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<UploadResponse> uploadTestCaseCsv(
//            @RequestParam("file") MultipartFile file) {
//
//        validateFile(file, ALLOWED_CSV_TYPES, 20 * 1024 * 1024L, "CSV/XLSX");
//
//        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "demo-testcase.csv";
//        String path = "demo/testcases/" + UUID.randomUUID() + "-" + filename;
//        String url = "https://demo-bucket.s3.ap-south-1.amazonaws.com/" + path;
//
//        return ResponseEntity.ok(UploadResponse.builder()
//                .path(path)
//                .url(url)
//                .filename(filename)
//                .sizeBytes(file.getSize())
//                .build());
//    }

    /**
     * POST /api/uploads/screenshot
     * Accepts PNG/JPG/etc.; stores in MinIO; returns S3 path.
     */
    @PostMapping(value = "/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadScreenshot(
            @RequestParam("file") MultipartFile file) {

        validateFile(file, ALLOWED_IMAGE_TYPES, 10 * 1024 * 1024L, "image");

        String path = storageService.uploadScreenshot(file);
//        String url  = storageService.generatePresignedUrl(path);

        return ResponseEntity.ok(UploadResponse.builder()
                .path(path)
                .url(path)
                .filename(file.getOriginalFilename())
                .sizeBytes(file.getSize())
                .build());
    }

    private void validateFile(MultipartFile file, Set<String> allowed, long maxBytes, String typeName) {
        if (file.isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException("Uploaded file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new GlobalExceptionHandler.BadRequestException(
                    String.format("File too large. Max size for %s is %d MB", typeName, maxBytes / (1024 * 1024)));
        }
        String ct = file.getContentType();
        if (ct == null || !allowed.contains(ct.toLowerCase())) {
            throw new GlobalExceptionHandler.BadRequestException(
                    "Unsupported file type: " + ct + ". Allowed: " + allowed);
        }
    }
}