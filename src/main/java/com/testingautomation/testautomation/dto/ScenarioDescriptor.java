package com.testingautomation.testautomation.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.logging.Logger;

@Data
public class ScenarioDescriptor {

    public enum Type { URL, MODAL,NAV_URL,NAV_MODAL,NAV_SEARCH,FORM_MODAL }

    private Type type;
    private String id;        // friendly id for logs
    private String url;       // used for URL scenarios
    private String openerCss; // used for NAV MODAL
    private MultipartFile csvFile;
    private String isClick;
    private String clickCss;
    private String value;// CSV file uploaded
    private String resultCsvPath;
    private List<String> ssPaths;
    private String csvUrl;
    private int sno;

    public ScenarioDescriptor(Type type,
                              String id,
                              String url,
                              MultipartFile csvFile) {

        this.type = type;
        this.id = id;
        this.url = url;
        this.csvFile = csvFile;
    }

    public ScenarioDescriptor(Type type, String id, String url, String openerCss, MultipartFile csvFile, String isClick, String clickCss, String value) {
        this.type = type;
        this.id = id;
        this.url = url;
        this.openerCss = openerCss;
        this.csvFile = csvFile;
        this.isClick = isClick;
        this.clickCss = clickCss;
        this.value = value;
    }

    public MultipartFile getCsvFile() {
        if(csvFile == null) {
            return null;
        }
        return csvFile;
    }
}