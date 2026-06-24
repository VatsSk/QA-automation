package com.testingautomation.testautomation.utils;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

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
    public static String normalizeCssSelectorForSelect2(String cssSelector) {

        if (cssSelector == null || cssSelector.isBlank()) {
            return cssSelector;
        }

        String raw = cssSelector.trim();

        // remove leading #
        String id = raw.startsWith("#")
                ? raw.substring(1)
                : raw;

        // Select2 pattern:
        // select2-{realId}-container
        if (id.startsWith("select2-") && id.endsWith("-container")) {

            String realId = id
                    .replaceFirst("^select2-", "")
                    .replaceFirst("-container$", "");

//            logger.info("Select2 detected. Converted [{}] -> [{}]",
//                    cssSelector,
//                    realId);

            return "#"+realId;
        }

        // Not Select2 -> return original id
        return "#"+id;
    }
}
