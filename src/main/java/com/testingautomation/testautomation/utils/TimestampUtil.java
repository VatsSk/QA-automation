package com.testingautomation.testautomation.utils;

public class TimestampUtil {
    public static String generateTimestamp() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
    }
}
