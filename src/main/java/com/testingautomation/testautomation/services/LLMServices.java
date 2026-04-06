package com.testingautomation.testautomation.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMServices {
    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.url}")
    private String apiUrl;

    private final WebClient webClient;

    public LLMServices(WebClient.Builder builder) {
        this.webClient = builder.build();
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
}