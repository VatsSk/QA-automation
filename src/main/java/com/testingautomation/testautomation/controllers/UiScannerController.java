package com.testingautomation.testautomation.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
//@RequestMapping("/scanner")
public class UiScannerController {

    @GetMapping("/")
    public String getHome(){
        return "index.html";
    }

    @GetMapping("/auth")
    public String getAuth(){return "runs.html";}

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
