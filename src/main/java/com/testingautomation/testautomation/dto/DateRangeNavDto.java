package com.testingautomation.testautomation.dto;

import com.testingautomation.testautomation.enums.DatePresetType;
import com.testingautomation.testautomation.enums.DateSelectionType;

public class DateRangeNavDto {
    private String inputSelector;
    private DateSelectionType selectionType;

    // preset mode
    private DatePresetType preset;

    // custom mode
    private String startDate;
    private String endDate;

    // optional
    private String startTime;
    private String endTime;

    private String applyButtonSelector;
    private String calendarContainerSelector;
    private String dateFormat;
}
