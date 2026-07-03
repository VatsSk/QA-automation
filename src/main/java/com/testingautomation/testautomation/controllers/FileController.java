package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.services.s3Service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final S3StorageService s3StorageService;

    @GetMapping("/presign")
    public ResponseEntity<Map<String, String>> presign(@RequestParam("key") String key) {
        String url = s3StorageService.generatePresignedGetUrl(key);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
