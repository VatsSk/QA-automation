package com.testingautomation.testautomation.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Verify {
    private String cssSelector;
    private String expectedResult;
    private boolean status;
    private String message;
    private String actual;

    public boolean getStatus() {
        return this.status;
    }
}
