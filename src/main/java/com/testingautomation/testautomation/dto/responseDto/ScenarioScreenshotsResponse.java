package com.testingautomation.testautomation.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioScreenshotsResponse {
    private List<ScreenshotItemResponse> screenshots;
}