package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.UiElement;
import com.testingautomation.testautomation.scan.UiScannerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scanner")
public class UiScannerController {

    private final UiScannerService scannerService;

    public UiScannerController(UiScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @GetMapping("/scan")
    public List<FieldDescriptor> scan(@RequestParam String url) {
        return scannerService.scanPage(url);
    }
}