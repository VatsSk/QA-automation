package com.testingautomation.testautomation.requestDto;

import lombok.Data;

@Data
public class TestConfigRequest {
    private String type;      // "URL" or "MODAL"
    private String id;
    private String url;
    private String openerCss; // Make sure your frontend JS sends this if needed!
    private String fileKey;// "file_0", "file_1"
    private String value;
}