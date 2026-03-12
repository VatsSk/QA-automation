package com.testingautomation.testautomation.dto;

import java.util.List;
import java.util.Map;

public class TestCase {
    private String id;
    private List<String> ssUrls;
    private Map<String,String> values;
    private String result;
    private String expectedResult;

    @Override
    public String toString() {
        return "TestCase{" +
                "id='" + id + '\'' +
                ", url='" + ssUrls + '\'' +
                ", values=" + values +
                ", result='" + result + '\'' +
                ", expectedResult='" + expectedResult + '\'' +
                '}';
    }

    public TestCase(String id ,Map<String, String> values) {
        this.id = id;
        this.values = values;
    }
    public Map<String,String> getValues() {
        return values;
    }
    public String getValue(String key) { return values.get(key); }
    public String getId() { return id; }
    public List<String> getUrls() { return ssUrls; }
    public void setUrls(List<String> ssUrls){
        this.ssUrls=ssUrls;
    }
    public String getResult(){return result;}
    public void setResult(String result){
        this.result=result;
    }
    public String getExpectedResult(){return expectedResult;}
    public void setExpectedResult(String expectedResult){this.expectedResult=expectedResult;}
}