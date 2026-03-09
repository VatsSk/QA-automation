package com.testingautomation.testautomation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDescriptor {
    public String tag;
    public String type;
    public String id;
    public String name;
    public String text;
    public String css;
    public String xpath;
    public String dataTarget;

    @Override
    public String toString() {
        return "FieldDescriptor{" +
                "tag='" + tag + '\'' +
                ", type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", text='" + text + '\'' +
                ", css='" + css + '\'' +
                ", xpath='" + xpath + '\'' +
                ", dataTarget='" + dataTarget + '\'' +
                '}';
    }


    // getters/setters or keep public fields for brevity
}