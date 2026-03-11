package com.testingautomation.testautomation.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.logging.Logger;

@Data
public class ScenarioDescriptor {
    Logger logger = Logger.getLogger(ScenarioDescriptor.class.getName());

    public enum Type { URL, MODAL,NAV_URL,NAV_MODAL,NAV_SEARCH }

    private Type type;
    private String id;        // friendly id for logs
    private String url;       // used for URL scenarios
    private String openerCss; // used for NAV MODAL
    private MultipartFile csvFile;
    private String value;// CSV file uploaded

    public ScenarioDescriptor(Type type,
                              String id,
                              String url,
                              MultipartFile csvFile) {

        this.type = type;
        this.id = id;
        this.url = url;
        this.csvFile = csvFile;
    }
    // new overloaded constructor (for NAV_MODAL that needs openerCss)
    public ScenarioDescriptor(Type type,
                              String id,
                              String url,
                              String openerCss,
                              MultipartFile csvFile) {
        this.type = type;
        this.id = id;
        this.url = url;
        this.openerCss = openerCss;
        this.csvFile = csvFile;
    }

    public ScenarioDescriptor(Type type,
                              String id,
                              String url,
                              String openerCss,
                              MultipartFile csvFile,String value) {
        this.type = type;
        this.id = id;
        this.url = url;
        this.openerCss = openerCss;
        this.csvFile = csvFile;
        this.value=value;
    }

    public MultipartFile getCsvFile() {
        if(csvFile == null) {
            logger.info("Csv file not loaded");
            return null;
        }
        return csvFile;
    }
}