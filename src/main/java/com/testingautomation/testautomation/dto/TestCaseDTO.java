package com.testingautomation.testautomation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestCaseDTO {
    private String testcaseId;
    private List<String> ssUrls;
    private Map<String,String> values;
    private String result;
    private String actual;
    private String expectedResult;


    public TestCaseDTO(String testcaseId ,Map<String, String> values) {
        this.testcaseId = testcaseId;
        this.values = values;
    }

    public String getValue(String key) { return values.get(key); }

}