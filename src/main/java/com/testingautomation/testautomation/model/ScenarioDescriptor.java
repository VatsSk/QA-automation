package com.testingautomation.testautomation.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.logging.Logger;

@Data
public class ScenarioDescriptor {
    Logger logger = Logger.getLogger(ScenarioDescriptor.class.getName());

    public enum Type { URL, MODAL }

    private Type type;
    private String id;        // friendly id for logs
    private String url;       // used for URL scenarios
    private String openerCss; // used for MODAL
    private MultipartFile csvFile; // CSV file uploaded

    public ScenarioDescriptor(Type type,
                              String id,
                              String url,
                              MultipartFile csvFile) {

        this.type = type;
        this.id = id;
        this.url = url;
        this.csvFile = csvFile;
    }

    public MultipartFile getCsvFile() {
        if(csvFile == null) {
            logger.info("Csv file not loaded");
            return null;
        }
        return csvFile;
    }
}