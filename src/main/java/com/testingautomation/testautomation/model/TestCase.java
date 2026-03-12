package com.testingautomation.testautomation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Embedded inside Scenario — not a top-level Mongo collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    private String id;
    private String name;

    private String url;

    /** Flexible key-value input data map */
    private Map<String, Object> inputData;

    private Map<String,String> value;

    private String result;

    private String expectedResult;
    private String actualResult;

    private ScenarioStatus status;

    /** S3/MinIO paths to screenshots for this test case */
    private List<String> screenshotPaths;

    public TestCase(String id,String url,Map<String,String> values){
        this.id=id;
        this.url=url;
        this.value=value;
    }

    public String getValue(String id){
        return value.get(id);
    }
}