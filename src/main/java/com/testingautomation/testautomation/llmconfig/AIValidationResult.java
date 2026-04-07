package com.testingautomation.testautomation.llmconfig;

import com.testingautomation.testautomation.model.RunStatus;
import jdk.jshell.Snippet;
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
    private List<String> issues;

    @Override
    public String toString() {
        return "AIValidationResult{" +
                "status=" + status +
                ", reason='" + reason + '\'' +
                ", issues=" + issues +
                '}';
    }
}