package com.testingautomation.testautomation.utils;
import com.testingautomation.testautomation.enums.DataType;
import com.testingautomation.testautomation.enums.Operator;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class TableColumnValidator {

    // =========================
    // MAIN METHOD (single value)
    // =========================
    public static boolean allRowsMatchInColumn(
            Table table,
            String columnName,
            Operator operator,
            String expectedValue,
            DataType dataType
    ) {
        Column<?> column = table.column(columnName);

        if (table.rowCount() == 0) {
            return true; // choose your framework rule
        }

//        if (operator == Operator.DATE_RANGE) {
//            throw new IllegalArgumentException("Use allRowsInRange() for DATE_RANGE operator");
//        }

        for (int i = 0; i < table.rowCount(); i++) {
            Object raw = column.get(i);
            String cell = raw == null ? "" : raw.toString().trim();

            if (isMissing(cell)) {
                return false; // if any row missing => fail
            }

            boolean matched = switch (dataType) {
                case TEXT -> matchString(cell, operator, expectedValue);
                case NUMBER -> matchNumber(cell, operator, expectedValue);
                case DATE -> matchDate(cell, operator, expectedValue);
                case DATE_TIME -> matchDateTime(cell, operator, expectedValue);
            };

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    // =========================
    // RANGE METHOD (DATE / DATETIME)
    // =========================
    public static boolean allRowsInRange(
            Table table,
            String columnName,
            String fromValue,
            String toValue,
            DataType dataType
    ) {
        Column<?> column = table.column(columnName);

        if (table.rowCount() == 0) {
            return false;
        }

        if (dataType != DataType.DATE && dataType != DataType.DATE_TIME) {
            throw new IllegalArgumentException("allRowsInRange() supports only DATE or DATETIME");
        }

        for (int i = 0; i < table.rowCount(); i++) {
            Object raw = column.get(i);
            String cell = raw == null ? "" : raw.toString().trim();

            if (isMissing(cell)) {
                return false;
            }

            boolean matched = switch (dataType) {
                case DATE -> isDateInRange(cell, fromValue, toValue);
                case DATE_TIME -> isDateTimeInRange(cell, fromValue, toValue);
                default -> false;
            };

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    // =========================
    // STRING MATCH
    // =========================
    private static boolean matchString(String cell, Operator operator, String expectedValue) {
        String target = expectedValue == null ? "" : expectedValue.trim();

        return switch (operator) {
            case EQUALS -> cell.equals(target);
            case NOT_EQUALS -> !cell.equals(target);
            case CONTAINS -> cell.contains(target);
            case STARTS_WITH -> cell.startsWith(target);
            default -> throw new IllegalArgumentException("Operator " + operator + " not supported for STRING");
        };
    }

    // =========================
    // NUMBER MATCH (DOUBLE ONLY)
    // =========================
    private static boolean matchNumber(String cell, Operator operator, String expectedValue) {
        Double a = parseDoubleSafe(cell);
        Double b = parseDoubleSafe(expectedValue);

        if (a == null || b == null) {
            return false;
        }

        return switch (operator) {
            case EQUALS -> Double.compare(a, b) == 0;
            case NOT_EQUALS -> Double.compare(a, b) != 0;
            case GREATER_THAN -> a > b;
            case LESS_THAN -> a < b;
            default -> throw new IllegalArgumentException("Operator " + operator + " not supported for NUMBER");
        };
    }

    // =========================
    // DATE MATCH
    // =========================
    private static boolean matchDate(String cell, Operator operator, String expectedValue) {
        LocalDate a = parseLocalDate(cell);
        LocalDate b = parseLocalDate(expectedValue);

        if (a == null || b == null) {
            return false;
        }

        return switch (operator) {
            case EQUALS -> a.isEqual(b);
            case NOT_EQUALS -> !a.isEqual(b);
            case GREATER_THAN -> a.isAfter(b);
            case LESS_THAN -> a.isBefore(b);
            default -> throw new IllegalArgumentException("Operator " + operator + " not supported for DATE");
        };
    }

    // =========================
    // DATETIME MATCH
    // =========================
    private static boolean matchDateTime(String cell, Operator operator, String expectedValue) {
        LocalDateTime a = parseLocalDateTime(cell);
        LocalDateTime b = parseLocalDateTime(expectedValue);

        if (a == null || b == null) {
            return false;
        }

        return switch (operator) {
            case EQUALS -> a.isEqual(b);
            case NOT_EQUALS -> !a.isEqual(b);
            case GREATER_THAN -> a.isAfter(b);
            case LESS_THAN -> a.isBefore(b);
            default -> throw new IllegalArgumentException("Operator " + operator + " not supported for DATETIME");
        };
    }

    // =========================
    // DATE RANGE (inclusive)
    // =========================
    private static boolean isDateInRange(String cell, String fromValue, String toValue) {
        LocalDate value = parseLocalDate(cell);
        LocalDate from = parseLocalDate(fromValue);
        LocalDate to = parseLocalDate(toValue);

        if (value == null || from == null || to == null) {
            return false;
        }

        return (value.isEqual(from) || value.isAfter(from))
                && (value.isEqual(to) || value.isBefore(to));
    }

    // =========================
    // DATETIME RANGE (inclusive)
    // =========================
    private static boolean isDateTimeInRange(String cell, String fromValue, String toValue) {
        LocalDateTime value = parseLocalDateTime(cell);
        LocalDateTime from = parseLocalDateTime(fromValue);
        LocalDateTime to = parseLocalDateTime(toValue);

        if (value == null || from == null || to == null) {
            return false;
        }

        return (value.isEqual(from) || value.isAfter(from))
                && (value.isEqual(to) || value.isBefore(to));
    }

    // =========================
    // HELPERS
    // =========================
    private static boolean isMissing(String value) {
        return value == null
                || value.isBlank()
                || value.equals("--")
                || value.equalsIgnoreCase("n/a")
                || value.equalsIgnoreCase("null");
    }

    private static Double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(
                    value.trim()
                            .replace(",", "")
                            .replace("%", "")
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd MMMM yyyy")
    );

    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    );

    private static LocalDate parseLocalDate(String value) {
        String v = value.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        String v = value.trim();

        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
