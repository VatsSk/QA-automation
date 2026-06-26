package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.FilterScenarioDto;
import com.testingautomation.testautomation.enums.DataType;
import com.testingautomation.testautomation.enums.Operator;
import com.testingautomation.testautomation.utils.TextExtractor;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.*;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;


public final class TableFilterExpression {

    private static final Logger log = LoggerFactory.getLogger(TableFilterExpression.class);

    private final String scenarioPrefix;
    private final Table table;
    private final List<List<PreparedCondition>> orGroups;
    private final String debugExpression;

    public TableFilterExpression(String scenarioPrefix,
                                  Table table,
                                  List<List<PreparedCondition>> orGroups,
                                  String debugExpression) {
        this.scenarioPrefix = scenarioPrefix;
        this.table = table;
        this.orGroups = orGroups;
        this.debugExpression = debugExpression;
    }

    public static TableFilterExpression compile(Table table, List<FilterScenarioDto> scenarios) {
        return compile("FILTER", table, scenarios);
    }

    public static TableFilterExpression compile(String scenarioPrefix,
                                                Table extractedTable,
                                                List<FilterScenarioDto> filterScenarios) {
        if (extractedTable == null) {
            throw new IllegalArgumentException("Table cannot be null");
        }

        if (scenarioPrefix == null || scenarioPrefix.trim().isEmpty()) {
            scenarioPrefix = "FILTER";
        }

        if (filterScenarios == null || filterScenarios.isEmpty()) {
            log.info("[{}] No filter scenarios provided. Compiled expression will be empty.", scenarioPrefix);
            return new TableFilterExpression(scenarioPrefix, extractedTable, new ArrayList<>(), "");
        }

        List<List<PreparedCondition>> groups = new ArrayList<>();
        List<PreparedCondition> currentAndGroup = new ArrayList<>();

        for (int i = 0; i < filterScenarios.size(); i++) {
            FilterScenarioDto filterScenario = filterScenarios.get(i);
            PreparedCondition condition = PreparedCondition.from(extractedTable, filterScenario, scenarioPrefix, i + 1);

            currentAndGroup.add(condition);

            String connector = normalizeLogicalOperator(filterScenario.getLogicalOperator());

            // logicalOperator belongs to current scenario and tells how it connects to the next one
            if ("OR".equals(connector)) {
                groups.add(currentAndGroup);
                currentAndGroup = new ArrayList<>();
            } else if ("AND".equals(connector)) {
                // keep adding to same AND group
            } else {
                throw new IllegalArgumentException(
                        "Unsupported logical operator '" + connector + "' at scenario index " + i
                );
            }
        }

        if (!currentAndGroup.isEmpty()) {
            groups.add(currentAndGroup);
        }

        String debugExpression = buildDebugExpression(groups);

        log.info("[{}] Compiled table filter expression: {}", scenarioPrefix, debugExpression);
        log.debug("[{}] OR groups: {}", scenarioPrefix, groups.size());

        return new TableFilterExpression(scenarioPrefix, extractedTable, groups, debugExpression);
    }

    public boolean matchesRow(int rowIndex) {
        if (orGroups.isEmpty()) {
            log.debug("[{}] No groups present. Row {} treated as MATCH.", scenarioPrefix, rowIndex);
            return true;
        }

        log.debug("[{}] Evaluating row {} ...", scenarioPrefix, rowIndex);

        for (int groupIndex = 0; groupIndex < orGroups.size(); groupIndex++) {
            List<PreparedCondition> andGroup = orGroups.get(groupIndex);
            boolean groupResult = true;

            log.debug("[{}]  Group {} start", scenarioPrefix, groupIndex + 1);

            for (int conditionIndex = 0; conditionIndex < andGroup.size(); conditionIndex++) {
                PreparedCondition condition = andGroup.get(conditionIndex);
                boolean conditionResult = condition.matches(rowIndex, scenarioPrefix, groupIndex + 1, conditionIndex + 1);

                if (!conditionResult) {
                    groupResult = false;
                    log.debug("[{}]  Group {} failed at condition {}", scenarioPrefix, groupIndex + 1, conditionIndex + 1);
                    break;
                }
            }

            log.debug("[{}]  Group {} result: {}", scenarioPrefix, groupIndex + 1, groupResult);

            if (groupResult) {
                log.debug("[{}] Row {} MATCHED by group {}", scenarioPrefix, rowIndex, groupIndex + 1);
                return true;
            }
        }

        log.debug("[{}] Row {} did NOT match expression.", scenarioPrefix, rowIndex);
        return false;
    }

    public boolean matchesAllRows() {
        if (table.rowCount() == 0) {
            log.info("[{}] Table has 0 rows. Treating as PASS.", scenarioPrefix);
            return true;
        }

        for (int rowIndex = 0; rowIndex < table.rowCount(); rowIndex++) {
            if (!matchesRow(rowIndex)) {
                log.info("[{}] Table filter failed on row {}. Expression: {}", scenarioPrefix, rowIndex, debugExpression);
                return false;
            }
        }

        log.info("[{}] All {} rows matched successfully.", scenarioPrefix, table.rowCount());
        return true;
    }

    public void assertAllRowsMatch() {
        if (!matchesAllRows()) {
            throw new IllegalStateException(
                    "Table filter assertion failed. Expression: " + debugExpression
            );
        }
    }

    public String getDebugExpression() {
        return debugExpression;
    }

    private static String normalizeLogicalOperator(String logicalOperator) {
        if (logicalOperator == null || logicalOperator.trim().isEmpty()) {
            return "AND";
        }
        return "OR";
    }

    private static String buildDebugExpression(List<List<PreparedCondition>> groups) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }

            sb.append("(");
            List<PreparedCondition> group = groups.get(i);

            for (int j = 0; j < group.size(); j++) {
                if (j > 0) {
                    sb.append(" AND ");
                }
                sb.append(group.get(j).describe());
            }

            sb.append(")");
        }

        return sb.toString();
    }

    private static boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
                || "-".equals(trimmed)
                || "null".equalsIgnoreCase(trimmed)
                || "na".equalsIgnoreCase(trimmed)
                || "n/a".equalsIgnoreCase(trimmed);
    }

    private static boolean compareText(String actual, String operatorName, String expected) {
        String a = actual == null ? "" : actual.trim();
        String e = expected == null ? "" : expected.trim();

        switch (operatorName) {
            case "EQUALS":
                return a.equalsIgnoreCase(e);

            case "NOT_EQUALS":
                return !a.equalsIgnoreCase(e);

            case "CONTAINS":
                return a.toLowerCase(Locale.ROOT).contains(e.toLowerCase(Locale.ROOT));

            case "STARTS_WITH":
                return a.toLowerCase(Locale.ROOT).startsWith(e.toLowerCase(Locale.ROOT));

            case "ENDS_WITH":
                return a.toLowerCase(Locale.ROOT).endsWith(e.toLowerCase(Locale.ROOT));

            case "IN":
                return containsTextInList(a, e);

            case "NOT_IN":
                return !containsTextInList(a, e);

            default:
                throw new IllegalArgumentException("Unsupported text operator: " + operatorName);
        }
    }

    private static boolean containsTextInList(String actual, String expectedCsv) {
        List<String> values = splitExpectedValues(expectedCsv);
        for (String value : values) {
            if (actual.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareNumber(String actual, String operatorName, String expected) {
        if ("IN".equals(operatorName) || "NOT_IN".equals(operatorName)) {
            List<String> values = splitExpectedValues(expected);
            double a = parseDouble(actual);

            boolean found = false;
            for (String candidate : values) {
                if (Double.compare(a, parseDouble(candidate)) == 0) {
                    found = true;
                    break;
                }
            }
            return "IN".equals(operatorName) ? found : !found;
        }

        double a = parseDouble(actual);
        double e = parseDouble(expected);

        switch (operatorName) {
            case "EQUALS":
                return Double.compare(a, e) == 0;

            case "NOT_EQUALS":
                return Double.compare(a, e) != 0;

            case "GREATER_THAN":
                return a > e;

            case "GREATER_THAN_OR_EQUAL":
                return a >= e;

            case "LESS_THAN":
                return a < e;

            case "LESS_THAN_OR_EQUAL":
                return a <= e;

            default:
                throw new IllegalArgumentException("Unsupported number operator: " + operatorName);
        }
    }

    private static boolean compareDate(String actual, String operatorName, String expected) {
        if ("DATE_RANGE".equals(operatorName) || "BETWEEN".equals(operatorName)) {
            Range<LocalDate> range = parseDateRange(expected);
            LocalDate a = parseFlexibleDate(actual);

            return !a.isBefore(range.start) && !a.isAfter(range.end);
        }

        if ("IN".equals(operatorName) || "NOT_IN".equals(operatorName)) {
            List<String> values = splitExpectedValues(expected);
            LocalDate a = parseFlexibleDate(actual);

            boolean found = false;
            for (String candidate : values) {
                if (a.isEqual(parseFlexibleDate(candidate))) {
                    found = true;
                    break;
                }
            }
            return "IN".equals(operatorName) ? found : !found;
        }

        LocalDate a = parseFlexibleDate(actual);
        LocalDate e = parseFlexibleDate(expected);

        switch (operatorName) {
            case "EQUALS":
                return a.isEqual(e);

            case "NOT_EQUALS":
                return !a.isEqual(e);

            case "GREATER_THAN":
                return a.isAfter(e);

            case "GREATER_THAN_OR_EQUAL":
                return a.isAfter(e) || a.isEqual(e);

            case "LESS_THAN":
                return a.isBefore(e);

            case "LESS_THAN_OR_EQUAL":
                return a.isBefore(e) || a.isEqual(e);

            default:
                throw new IllegalArgumentException("Unsupported date operator: " + operatorName);
        }
    }

    private static boolean compareDateTime(String actual, String operatorName, String expected) {
        if ("DATE_RANGE".equals(operatorName) || "BETWEEN".equals(operatorName)) {
            Range<LocalDateTime> range = parseDateTimeRange(expected);
            LocalDateTime a = parseFlexibleDateTime(actual);

            return !a.isBefore(range.start) && !a.isAfter(range.end);
        }

        if ("IN".equals(operatorName) || "NOT_IN".equals(operatorName)) {
            List<String> values = splitExpectedValues(expected);
            LocalDateTime a = parseFlexibleDateTime(actual);

            boolean found = false;
            for (String candidate : values) {
                if (a.isEqual(parseFlexibleDateTime(candidate))) {
                    found = true;
                    break;
                }
            }
            return "IN".equals(operatorName) ? found : !found;
        }

        LocalDateTime a = parseFlexibleDateTime(actual);
        LocalDateTime e = parseFlexibleDateTime(expected);

        switch (operatorName) {
            case "EQUALS":
                return a.isEqual(e);

            case "NOT_EQUALS":
                return !a.isEqual(e);

            case "GREATER_THAN":
                return a.isAfter(e);

            case "GREATER_THAN_OR_EQUAL":
                return a.isAfter(e) || a.isEqual(e);

            case "LESS_THAN":
                return a.isBefore(e);

            case "LESS_THAN_OR_EQUAL":
                return a.isBefore(e) || a.isEqual(e);

            default:
                throw new IllegalArgumentException("Unsupported datetime operator: " + operatorName);
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse number: " + value, e);
        }
    }

    private static List<String> splitExpectedValues(String expected) {
        if (expected == null || expected.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] parts = expected.split("\\s*,\\s*");
        return Arrays.asList(parts);
    }

    private static Range<LocalDate> parseDateRange(String expected) {
        String[] bounds = splitRangeBounds(expected);
        LocalDate start = parseFlexibleDate(bounds[0]);
        LocalDate end = parseFlexibleDate(bounds[1]);

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        return new Range<>(start, end);
    }

    private static Range<LocalDateTime> parseDateTimeRange(String expected) {
        String[] bounds = splitRangeBounds(expected);
        LocalDateTime start = parseFlexibleDateTime(bounds[0]);
        LocalDateTime end = parseFlexibleDateTime(bounds[1]);

        if (start.isAfter(end)) {
            LocalDateTime tmp = start;
            start = end;
            end = tmp;
        }

        return new Range<>(start, end);
    }

    private static String[] splitRangeBounds(String expected) {
        if (expected == null || expected.trim().isEmpty()) {
            throw new IllegalArgumentException("DATE_RANGE/BETWEEN expected value cannot be empty");
        }

        String cleaned = expected.trim();
        String[] parts;

        if (cleaned.contains("|")) {
            parts = cleaned.split("\\|");
        } else if (cleaned.contains(",")) {
            parts = cleaned.split(",");
        } else {
            throw new IllegalArgumentException(
                    "DATE_RANGE/BETWEEN expected value must contain '|' or ',' separator. Given: " + expected
            );
        }

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "DATE_RANGE/BETWEEN must have exactly 2 boundaries. Given: " + expected
            );
        }

        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private static LocalDate parseFlexibleDate(String value) {
        String v = value.trim();

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
        );

        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
            }
        }

        // fallback: parse datetime and reduce to date
        try {
            return parseFlexibleDateTime(v).toLocalDate();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse date: " + value, e);
        }
    }

    private static LocalDateTime parseFlexibleDateTime(String value) {
        String v = value.trim();

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
        );

        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDateTime.parse(v, f);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return parseFlexibleDate(v).atStartOfDay();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse datetime: " + value, e);
        }
    }

    private static final class Range<T> {
        private final T start;
        private final T end;

        private Range(T start, T end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class PreparedCondition {
        private final String columnName;
        private final Column<?> column;
        private final Operator operator;
        private final DataType dataType;
        private final String expectedValue;

        private PreparedCondition(String columnName,
                                  Column<?> column,
                                  Operator operator,
                                  DataType dataType,
                                  String expectedValue) {
            this.columnName = columnName;
            this.column = column;
            this.operator = operator;
            this.dataType = dataType;
            this.expectedValue = expectedValue;
        }

        static PreparedCondition from(Table extractedTable,
                                      FilterScenarioDto filterScenario,
                                      String scenarioPrefix,
                                      int scenarioNumber) {
            if (filterScenario == null) {
                throw new IllegalArgumentException("Scenario cannot be null");
            }

            String columnName = filterScenario.getColumnName()==null||filterScenario.getColumnName().trim().isEmpty()?
                                TextExtractor.extractColumnName(filterScenario.getQuerySelector()):
                    filterScenario.getColumnName();


            if (columnName == null || columnName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Column name cannot be empty for scenario #" + scenarioNumber);
            }

            Operator operator = filterScenario.getOperation();
            if (operator == null) {
                throw new IllegalArgumentException("Operator cannot be null for column: " + columnName);
            }

            DataType dataType = filterScenario.getFilterType();
            if (dataType == null) {
                throw new IllegalArgumentException("DataType cannot be null for column: " + columnName);
            }

            String expectedValue = filterScenario.getValue() == null ? "" : filterScenario.getValue().trim();

            Column<?> column;
            try {
                column = extractedTable.column(columnName);
            } catch (Exception e) {
                throw new IllegalArgumentException("Column not found in table: " + columnName, e);
            }

            PreparedCondition condition = new PreparedCondition(columnName, column, operator, dataType, expectedValue);

            log.debug("[{}] Prepared condition #{} -> {}", scenarioPrefix, scenarioNumber, condition.describe());
            return condition;
        }

        boolean matches(int rowIndex, String prefix, int groupNumber, int conditionNumber) {
            Object raw = column.get(rowIndex);
            String cell = raw == null ? "" : raw.toString().trim();

            if (isMissing(cell)) {
                log.debug("[{}] row={} group={} condition={} column='{}' value=MISSING -> false",
                        prefix, rowIndex, groupNumber, conditionNumber, columnName);
                return false;
            }

            String operatorName = operator.name().toUpperCase(Locale.ROOT);
            boolean result;

            switch (dataType) {
                case TEXT:
                    result = compareText(cell, operatorName, expectedValue);
                    break;

                case NUMBER:
                    result = compareNumber(cell, operatorName, expectedValue);
                    break;

                case DATE:
                    result = compareDate(cell, operatorName, expectedValue);
                    break;

                case DATE_TIME:
                    result = compareDateTime(cell, operatorName, expectedValue);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported data type: " + dataType);
            }

            log.debug("[{}] row={} group={} condition={} column='{}' actual='{}' operator={} type={} expected='{}' => {}",
                    prefix,
                    rowIndex,
                    groupNumber,
                    conditionNumber,
                    columnName,
                    cell,
                    operatorName,
                    dataType,
                    expectedValue,
                    result);

            return result;
        }

        String describe() {
            String op = operator.name().toLowerCase(Locale.ROOT);
            return columnName + "." + op + "(" + expectedValue + ")";
        }
    }
}