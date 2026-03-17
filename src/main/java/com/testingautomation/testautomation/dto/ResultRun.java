package com.testingautomation.testautomation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResultRun {
    String status;
    List<String> screenshots;

    public ResultRun(String status, List<String> screenshots) {
        this.status = status;
        this.screenshots = screenshots;
    }
}