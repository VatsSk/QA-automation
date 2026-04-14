package com.testingautomation.testautomation.tableSaw;


import com.testingautomation.testautomation.dto.StepAction;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import java.util.*;

@Slf4j
@Service
public class TableSawService {

    public Table extractDataTableToTablesaw(WebDriver driver, StepAction step) {
        String tableLocator = step.getTableId();
        log.info("Extracting DataTable using locator: {}", tableLocator);

        // 1) Headers from DataTables cloned scroll header
        List<WebElement> headerElements = driver.findElements(By.cssSelector(".dataTables_scrollHead th"));

        List<String> headers = headerElements.stream()
                .map(this::extractCellText)
                .map(this::sanitizeHeader)
                .filter(h -> h != null && !h.isBlank())
                .collect(Collectors.toList());

        log.info("Extracted headers: {}", headers);

        if (headers.isEmpty()) {
            throw new GlobalExceptionHandler.BadRequestException(
                    "No headers found in DataTables scroll header for locator: " + tableLocator
            );
        }

        headers = makeHeadersUnique(headers);

        // 2) Prepare column data map
        Map<String, List<String>> columnData = new LinkedHashMap<>();
        for (String header : headers) {
            columnData.put(header, new ArrayList<>());
        }

        // 3) Rows from actual table body
        List<WebElement> rows = driver.findElements(By.cssSelector(tableLocator + " tbody tr"));
        log.info("Total body rows found: {}", rows.size());

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            WebElement row = rows.get(rowIndex);
            List<WebElement> cells = row.findElements(By.cssSelector("td, th"));

            List<String> rowValues = cells.stream()
                    .map(this::extractCellText)
                    .collect(Collectors.toList());

            // Normalize row size
            if (rowValues.size() < headers.size()) {
                while (rowValues.size() < headers.size()) {
                    rowValues.add("");
                }
            } else if (rowValues.size() > headers.size()) {
                rowValues = rowValues.subList(0, headers.size());
            }

            log.debug("Row {} values: {}", rowIndex + 1, rowValues);

            for (int i = 0; i < headers.size(); i++) {
                columnData.get(headers.get(i)).add(rowValues.get(i));
            }
        }

        // 4) Build Tablesaw table
        Table tsTable = Table.create("UI_DataTable");

        for (String header : headers) {
            tsTable.addColumns(StringColumn.create(header, columnData.get(header)));
        }

        log.info("Tablesaw DataTable created. Rows: {}, Columns: {}",
                tsTable.rowCount(), tsTable.columnCount());

        log.info("Column names: {}", tsTable.columnNames());

        return tsTable;
    }
    private String extractCellText(WebElement element) {
        String text = "";

        try {
            // Common case: header text inside div/span
            text = element.getText().trim();
        } catch (Exception e) {
            log.debug("Failed to read text directly from element", e);
        }

        if (text == null || text.isBlank()) {
            try {
                text = element.getAttribute("innerText");
                if (text != null) text = text.trim();
            } catch (Exception ignored) {}
        }

        if (text == null || text.isBlank()) {
            try {
                text = element.getAttribute("textContent");
                if (text != null) text = text.trim();
            } catch (Exception ignored) {}
        }

        if (text == null || text.isBlank()) {
            try {
                text = element.getAttribute("aria-label");
                if (text != null) text = text.trim();
            } catch (Exception ignored) {}
        }

        return text != null ? text.replace("\n", " ").replaceAll("\\s+", " ").trim() : "";
    }
    private String sanitizeHeader(String header) {
        if (header == null) return "";

        header = header.trim();

        // Remove sort suffix from aria labels like "Name: activate to sort column ascending"
        if (header.contains(":")) {
            header = header.split(":")[0].trim();
        }

        return header;
    }
    private List<String> makeHeadersUnique(List<String> headers) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> uniqueHeaders = new ArrayList<>();

        for (String header : headers) {
            String base = (header == null || header.isBlank()) ? "Column" : header.trim();

            int count = counts.getOrDefault(base.toLowerCase(), 0) + 1;
            counts.put(base.toLowerCase(), count);

            if (count == 1) {
                uniqueHeaders.add(base);
            } else {
                uniqueHeaders.add(base + "_" + count);
            }
        }

        return uniqueHeaders;
    }
}
