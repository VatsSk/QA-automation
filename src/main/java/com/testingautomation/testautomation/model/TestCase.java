package com.testingautomation.testautomation.model;

import java.util.Map;

public class TestCase {
    private String id;
    private String url;
    private Map<String,String> values;
    private String result;

    @Override
    public String toString() {
        return "TestCase{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", values=" + values +
                ", result='" + result + '\'' +
                '}';
    }

    public TestCase(String id, String url, Map<String, String> values) {
        this.id = id;
        this.url = url;
        this.values = values;
    }

    public String getValue(String key) { return values.get(key); }
    public String getId() { return id; }
    public String getUrl() { return url; }
    public String getResult(){return result;}
    public void setResult(String result){
        this.result=result;
    }
}