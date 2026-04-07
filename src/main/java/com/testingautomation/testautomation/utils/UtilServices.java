package com.testingautomation.testautomation.utils;

import org.springframework.stereotype.Service;

@Service
public class UtilServices {
    public String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        throw new RuntimeException("No valid JSON found in LLM response: " + response);
    }
}
