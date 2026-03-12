package com.testingautomation.testautomation.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final StorageProperties storageProperties;
    private final RunnerProperties runnerProperties;

    /**
     * AWS S3 client.
     * If accessKey + secretKey are set in config, uses static credentials.
     * Otherwise falls back to DefaultCredentialsProvider (env vars, IAM role, ~/.aws, etc.).
     */
    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(storageProperties.getRegion()));

        if (StringUtils.hasText(storageProperties.getAccessKey())
                && StringUtils.hasText(storageProperties.getSecretKey())) {
            log.info("S3Client: using static credentials");
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            storageProperties.getAccessKey(),
                            storageProperties.getSecretKey()
                    )
            ));
        } else {
            log.info("S3Client: using DefaultCredentialsProvider (IAM role / env / ~/.aws)");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    /**
     * S3Presigner — used only for generating presigned GET URLs.
     */
    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(storageProperties.getRegion()));

        if (StringUtils.hasText(storageProperties.getAccessKey())
                && StringUtils.hasText(storageProperties.getSecretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            storageProperties.getAccessKey(),
                            storageProperties.getSecretKey()
                    )
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    /**
     * RestTemplate used exclusively to call POST /runner/run-auth.
     * Timeout driven by runner.timeout-seconds property.
     */
    @Bean
    public RestTemplate runnerRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(runnerProperties.getTimeoutSeconds()))
                .build();
    }

    /**
     * Permissive CORS for local dev — tighten in production via env override.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}