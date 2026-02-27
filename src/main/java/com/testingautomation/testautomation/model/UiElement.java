package com.testingautomation.testautomation.model;

public class UiElement {
    public String tag;
    public String type;
    public String id;
    public String name;
    public String text;
    public String css;
    public String xpath;

    @Override
    public String toString() {
        return "UiElement{" +
                "tag='" + tag + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", text='" + text + '\'' +
                ", css='" + css + '\'' +
                ", xpath='" + xpath + '\'' +
                '}';
    }
}