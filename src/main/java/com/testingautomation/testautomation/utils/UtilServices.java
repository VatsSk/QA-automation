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

            return "#"+realId;
        }

        // Not Select2 -> return original id
        return "#"+id;
    }
    public static WebElement unwrapFrameworkElement(WebDriver driver, WebElement element) {
        if (element == null) return null;

        String id = safe(element.getAttribute("id"));
        String cls = safe(element.getAttribute("class"));
        String tag = safe(element.getTagName()).toLowerCase();

        // If it's already a real control, keep it
        if (isRealControl(tag)) {
            return element;
        }

        // Select2 visible container -> hidden select
        if (id.startsWith("select2-") && id.endsWith("-container")) {
            String realId = id
                    .replaceFirst("^select2-", "")
                    .replaceFirst("-container$", "");
            try {
                WebElement found = driver.findElement(By.id(realId));
                if ("select".equalsIgnoreCase(found.getTagName())) {
                    return found;
                }
            } catch (Exception ignored) {}
        }

        // DataTables wrapper -> real table
        if (id.endsWith("_wrapper")) {
            String realId = id.substring(0, id.length() - "_wrapper".length());
            try {
                WebElement found = driver.findElement(By.id(realId));
                if ("table".equalsIgnoreCase(found.getTagName())) {
                    return found;
                }
            } catch (Exception ignored) {}
        }

        // Generic wrapper-like elements
        if (isWrapperLike(id, cls, tag)) {
            List<WebElement> candidates = element.findElements(By.cssSelector(
                    "select, input, textarea, button, a, table, [contenteditable='true']"
            ));

            for (WebElement c : candidates) {
                if (isRealControl(safe(c.getTagName()).toLowerCase())) {
                    return c;
                }
            }
        }

        return element;
    }

    private static boolean isWrapperLike(String id, String cls, String tag) {
        return id.endsWith("_wrapper")
                || id.contains("_container")
                || id.startsWith("select2-")
                || id.startsWith("cke_")
                || id.startsWith("mce_")
                || cls.contains("select2")
                || cls.contains("react-select")
                || cls.contains("ag-")
                || cls.contains("dataTables_wrapper")
                || cls.contains("ui-select")
                || cls.contains("datepicker")
                || tag.equals("div");
    }

    private static boolean isRealControl(String tag) {
        return "select".equals(tag)
                || "input".equals(tag)
                || "textarea".equals(tag)
                || "button".equals(tag)
                || "a".equals(tag)
                || "table".equals(tag);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
