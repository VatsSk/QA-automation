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
        System.out.println("AUTH controller calleld::");
        return "runs.html";
    }

    @GetMapping("/run-detail")
    public String getRunDetail(){
        return "run-detail.html";
    }

    @GetMapping("/run-editor")
    public String getRunEditor(){
        return "run-editor.html";
    }

    @GetMapping("/projects")
    public String getProjects(){
        return "projects.html";
    }

    @GetMapping("/modules")
    public String getModules(){
        return "modules.html";
    }
}
