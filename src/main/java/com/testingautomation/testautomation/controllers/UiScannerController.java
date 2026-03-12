package com.testingautomation.testautomation.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
//@RequestMapping("/scanner")
public class UiScannerController {

//    private final UiScannerService scannerService;
//
//    public UiScannerController(UiScannerService scannerService) {
//        this.scannerService = scannerService;
//    }
//
//    @GetMapping("/scan")
//    public List<FieldDescriptor> scan(@RequestParam String url) {
//        return scannerService.scanPage(url);
//    }
    @GetMapping("/")
    public String getHome(){
        return "index.html";
    }

    @GetMapping("/auth")
    public String getAuth(){
        return "Testforge.html";
    }
}