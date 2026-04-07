package com.testingautomation.testautomation.llmconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingautomation.testautomation.utils.UtilServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
public class LLMServices {
    private final UtilServices utilServices;
    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.url}")
    private String apiUrl;

    private final WebClient webClient;

    public LLMServices(WebClient.Builder builder, UtilServices utilServices) {
        this.webClient = builder.build();
        this.utilServices = utilServices;
    }

    public String callLLM(String prompt) {
        Map<String, Object> request = new HashMap<>();

        request.put("model", "gpt-4o-mini");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        request.put("messages", messages);

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(res -> res.get("choices").get(0).get("message").get("content").asText())
                .block();
    }

    public AIValidationResult analyzeScreenshots(String prompt, List<File> screenshots) {

        try {
            // 1. Build multimodal content (text + images)
            List<Map<String, Object>> content = new ArrayList<>();

            // Add text prompt
            content.add(Map.of(
                    "type", "text",
                    "text", prompt
            ));

            // Add images
            for (File file : screenshots) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);

                Map<String, Object> imageBlock = new HashMap<>();
                imageBlock.put("type", "image_url");

                Map<String, String> imageUrl = new HashMap<>();
                imageUrl.put("url", "data:image/png;base64," + base64);

                imageBlock.put("image_url", imageUrl);

                content.add(imageBlock);
            }

            // 2. Build message
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", content);

            // 3. Build request body
            Map<String, Object> request = new HashMap<>();
            request.put("model", "gpt-4o"); // vision-capable model
            request.put("messages", List.of(message));
            request.put("temperature", 0);

            // 4. Call LLM
            String rawResponse = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(err -> new RuntimeException("LLM API error: " + err))
                    )
                    .bodyToMono(JsonNode.class)
                    .map(res -> res.get("choices").get(0).get("message").get("content").asText())
                    .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new RuntimeException("Empty response from LLM");
            }

            // 5. Extract JSON safely
            String json = utilServices.extractJson(rawResponse);

            // 6. Parse into object
            ObjectMapper mapper = new ObjectMapper();
            AIValidationResult result = mapper.readValue(json, AIValidationResult.class);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Error during AI screenshot analysis", e);
        }
    }
}