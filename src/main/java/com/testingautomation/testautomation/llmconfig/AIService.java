package com.testingautomation.testautomation.llmconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
public class AIService {
//    public AIValidationResult analyzeScreenshots(String prompt, List<File> screenshots) {
//        // 1. Convert images to Base64
//        // 2. Build multimodal request:
//        //    - text prompt
//        //    - image1
//        //    - image2
//        //    - ...
//        // 3. Call vision LLM
//        // 4. Parse strict JSON response
//        // 5. Return AIValidationResult
//
//        throw new UnsupportedOperationException("Implement with your chosen LLM provider");
//    }


//
//    public AIValidationResult analyzeScreenshots(String prompt, List<File> screenshots) {
//
//        try {
//            // 1. Convert images to Base64
//            List<Map<String, Object>> contentList = new ArrayList<>();
//
//            // Add text prompt
//            contentList.add(Map.of(
//                    "type", "text",
//                    "text", buildStrictPrompt(prompt)
//            ));
//
//            // Add images
//            for (File file : screenshots) {
//                byte[] fileContent = Files.readAllBytes(file.toPath());
//                String base64 = Base64.getEncoder().encodeToString(fileContent);
//
//                Map<String, Object> imageMap = new HashMap<>();
//                imageMap.put("type", "image_url");
//
//                Map<String, String> imageUrl = new HashMap<>();
//                imageUrl.put("url", "data:image/png;base64," + base64);
//
//                imageMap.put("image_url", imageUrl);
//
//                contentList.add(imageMap);
//            }
//
//            // 2. Build request
//            Map<String, Object> message = new HashMap<>();
//            message.put("role", "user");
//            message.put("content", contentList);
//
//            Map<String, Object> request = new HashMap<>();
//            request.put("model", "gpt-4o"); // IMPORTANT: use vision-capable model
//            request.put("messages", List.of(message));
//            request.put("temperature", 0);
//
//            // 3. Call LLM
//            String rawResponse = webClient.post()
//                    .uri(apiUrl)
//                    .header("Authorization", "Bearer " + apiKey)
//                    .bodyValue(request)
//                    .retrieve()
//                    .bodyToMono(JsonNode.class)
//                    .map(res -> res.get("choices").get(0).get("message").get("content").asText())
//                    .block();
//
//            // 4. Parse JSON safely
//            return parseAIResponse(rawResponse);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error analyzing screenshots", e);
//        }
//    }
}
