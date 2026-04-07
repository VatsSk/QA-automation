package com.testingautomation.testautomation.llmconfig;

import java.util.List;

public class AIValidationResult {
    private boolean passed;
    private String reason;
    private List<String> issues;

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    @Override
    public String toString() {
        return "AIValidationResult{" +
                "passed=" + passed +
                ", reason='" + reason + '\'' +
                ", issues=" + issues +
                '}';
    }
}