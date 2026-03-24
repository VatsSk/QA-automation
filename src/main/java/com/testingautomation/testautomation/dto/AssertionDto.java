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

    //     Locator strategy: css / xpath (optional for some assertions)
    private String locatorType;

    //     Locator value: #id / .class / xpath
    //cssLocator
    private String locator;

    //      Expected value (depends on type)
//      Examples:
//      "true", "desc", "Verified", "10"
    private String expected;

    /**
     * Optional value (context-specific)
     * Examples:
     * column name ("FR Status")
     * API endpoint ("/members/list")
     */
    private String value;
    private String tableId;
    private String rowsBtn;
    private String rangeId;
}

