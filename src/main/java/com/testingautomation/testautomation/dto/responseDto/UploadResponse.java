package com.testingautomation.testautomation.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String path;        // S3/MinIO object key
    private String url;         // Presigned access URL
    private String filename;
    private long sizeBytes;
}