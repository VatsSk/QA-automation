package com.testingautomation.testautomation.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotItemResponse {
    private String fileName;
    private String url;
}