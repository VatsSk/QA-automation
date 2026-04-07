package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.llmconfig.LLMServices;
import com.testingautomation.testautomation.services.RunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/runner")
public class RunController {
    private final Logger logger = LoggerFactory.getLogger(RunController.class);
    private final RunService runService;
    private final LLMServices llmServices;

    public RunController(RunService runService, LLMServices llmServices) {
        this.runService = runService;
        this.llmServices = llmServices;
    }

    @PostMapping(value = "/runs/{id}/execute")
    public ResponseEntity<?> receiveTests(
            @PathVariable("id") String runId) {
        return ResponseEntity.ok(runService.executeRun(runId));
    }

    @PostMapping("/ask")
    public ResponseEntity<String> ask(@RequestBody String prompt) {
        String response = llmServices.callLLM(prompt);
        return ResponseEntity.ok(response);
    }



}