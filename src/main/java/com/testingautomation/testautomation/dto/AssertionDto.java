package com.testingautomation.testautomation.dto;


import com.testingautomation.testautomation.model.AssertionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssertionDto {

    private AssertionType type;

    //     Locator value: #id / .class / xpath
    //cssLocator
    private String locator;

    //      Expected value (depends on type)
//      Examples:
//      "true", "desc", "Verified",
    private String expected;
    private String columnName;

    private String tableId;
    private String rowsBtn;
    private String rangeId;
}

