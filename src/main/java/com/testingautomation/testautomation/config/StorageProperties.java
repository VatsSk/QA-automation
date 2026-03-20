package com.testingautomation.testautomation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Data
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class StorageProperties {

    /** AWS region, e.g. ap-southeast-1 */
    private String region;

    /** S3 bucket name, e.g. interns-tf-project */
    private String bucketName;

    /** Optional folder/prefix inside bucket, e.g. qa_automation */
    private String basePrefix;

    /** Optional: explicit access key (leave blank to use IAM role / env creds) */
    private String accessKey;

    /** Optional: explicit secret key (leave blank to use IAM role / env creds) */
    private String secretKey;

    /** Presigned URL expiry in minutes (default 60) */
    private int presignedUrlExpiryMinutes = 60;

}