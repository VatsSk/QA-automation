package com.testingautomation.testautomation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StepAction {
    public enum ActionType { TYPE,
        CLICK,
        SELECT,
        VERIFY_TEXT,
        WAIT,
        ASSERT_VISIBLE,
        ASSERT_NOT_VISIBLE,
        ASSERT_ELEMENT_PRESENT,
        ASSERT_TEXT_EQUALS,
        ASSERT_TEXT_CONTAINS,
        ASSERT_COLUMN_PRESENT,
        ASSERT_SORT_ORDER,
        ASSERT_COUNT,
        ASSERT_API_CALLED,
        ASSERT_ATTRIBUTE,
        ASSERT_AI,
        ASSERT_FILTER,
        ASSERT_MANAGE_COLUMN,
        ASSERT_ROWS_COUNT
    }
    private ActionType type;
    private String locatorType; // css / xpath / id


    private String locator;     // actual locator value
    private String payload;     // text to type / text to select / expected text
    private String description;
    private String tableId;
    private String colName;
    private String rowsBtn;
    private String prompt;
//    private String rangeId;
    private String order;
    private AssertionDto assertion;

    // constructor + getters/setters
    public StepAction(ActionType type, String locatorType, String locator, String payload, String description) {
        this.type = type;
        this.locatorType = locatorType;
        this.locator = locator;
        this.payload = payload;
        this.description = description;
    }

    @Override
    public String toString() {
        return "StepAction{" +
                "type=" + type +
                ", locatorType='" + locatorType + '\'' +
                ", locator='" + locator + '\'' +
                ", payload='" + payload + '\'' +
                ", description='" + description + '\'' +
                ", tableId='" + tableId + '\'' +
                ", colName='" + colName + '\'' +
                ", rowsBtn='" + rowsBtn + '\'' +
                ", order='" + order + '\'' +
                '}';
    }
}