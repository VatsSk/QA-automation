package com.testingautomation.testautomation.client;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingautomation.testautomation.model.FieldDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@Component
public class ScannerClient {
    private final Logger logger = LoggerFactory.getLogger(ScannerClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public List<FieldDescriptor> scan(String scannerApiUrl) throws Exception {
        logger.info("Calling scanner API: {}", scannerApiUrl);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(scannerApiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AutoTestScannerClient/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Scan API Response: " + resp.body());
        if (resp.statusCode() != 200) {
            logger.error("Scanner returned status {}", resp.statusCode());
            throw new RuntimeException("Scanner error: " + resp.statusCode());
        }
        String body = resp.body();
        logger.debug("Scanner JSON: {}", body);
        return mapper.readValue(body, new TypeReference<List<FieldDescriptor>>() {});
    }
}