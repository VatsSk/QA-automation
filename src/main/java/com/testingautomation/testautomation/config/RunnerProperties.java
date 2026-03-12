package com.testingautomation.testautomation.config;



import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "runner")
public class RunnerProperties {
    private String baseUrl;
    private String runAuthPath;
    private int timeoutSeconds;
}
