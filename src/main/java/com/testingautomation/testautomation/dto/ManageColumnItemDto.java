package com.testingautomation.testautomation.dto;

import com.testingautomation.testautomation.enums.ManageColumnAction;
import lombok.Data;

@Data
public class ManageColumnItemDto {
    private String columnName;

    private String extractedName;

    //SHOW,HIDE
    private ManageColumnAction action;

    //Optional
    private Integer position;
}
