package com.testingautomation.testautomation.model;

import lombok.Data;

@Data
public class StepAction {
    public enum ActionType { TYPE, CLICK, SELECT, VERIFY_TEXT, WAIT }
    private ActionType type;
    private String locatorType; // css / xpath / id

    @Override
    public String toString() {
        return "StepAction{" +
                "type=" + type +
                ", locatorType='" + locatorType + '\'' +
                ", locator='" + locator + '\'' +
                ", payload='" + payload + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

    private String locator;     // actual locator value
    private String payload;     // text to type / text to select / expected text
    private String description;

    // constructor + getters/setters
    public StepAction(ActionType type, String locatorType, String locator, String payload, String description) {
        this.type = type; this.locatorType = locatorType; this.locator = locator; this.payload = payload; this.description = description;
    }

    // getters...
}