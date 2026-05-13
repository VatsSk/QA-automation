package com.testingautomation.testautomation.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextExtractor {

    public static String extractColumnName(String columnSelector) {

        System.out.println("Extracting column name from selector: " + columnSelector);

        if (columnSelector == null || columnSelector.trim().isEmpty()) {
            System.out.println("Selector is null or empty.");
            return null;
        }

        String selector = columnSelector.trim();

        try {

            // Case 1: Attribute selector like [for="updated"]
            Pattern attributePattern = Pattern.compile("\\[(.*?)=['\"]?(.*?)['\"]?\\]");
            Matcher attributeMatcher = attributePattern.matcher(selector);

            if (attributeMatcher.find()) {
                String extracted = attributeMatcher.group(2);

                if (extracted != null && !extracted.trim().isEmpty()) {
                    System.out.println("Extracted from attribute selector: " + extracted.trim());
                    return extracted.trim();
                }
            }

            // Case 2: #id selector
            if (selector.startsWith("#")) {
                String extracted = selector.substring(1).trim();
                System.out.println("Extracted from id selector: " + extracted);
                return extracted;
            }

            // Case 3: .class selector
            if (selector.startsWith(".")) {
                String extracted = selector.substring(1).trim();
                System.out.println("Extracted from class selector: " + extracted);
                return extracted;
            }

            // Case 4: Direct tag or text
            System.out.println("Using direct selector value: " + selector);
            return selector;

        } catch (Exception e) {
            System.out.println("Failed to extract column name from selector.");
            e.printStackTrace();
            return null;
        }
    }
}
