package com.testingautomation.testautomation.dto;

import lombok.Data;

import java.util.List;

@Data
public class AIValidationResult {

    public enum AssertStatus{
        PASSED,
        FAILED,
        PARTIAL
    }
    private AssertStatus status;
    private String reason;
    private String partialReason;
    private List<String> issues;

    @Override
    public String toString() {
        return "AIValidationResult{" +
                "status=" + status +
                ", reason='" + reason + '\'' +
                ", partialReason='" + partialReason + '\'' +
                ", issues=" + issues +
                '}';
    }
}