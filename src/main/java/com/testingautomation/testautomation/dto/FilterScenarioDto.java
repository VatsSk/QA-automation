package com.testingautomation.testautomation.dto;


import com.testingautomation.testautomation.enums.DataType;
import com.testingautomation.testautomation.enums.Operator;
import lombok.Data;

@Data
public class FilterScenarioDto {
    private String querySelector;
    private String columnName;
    private DataType filterType;
    private Operator operation;
    private String valueSelector;
    private String value;

}
