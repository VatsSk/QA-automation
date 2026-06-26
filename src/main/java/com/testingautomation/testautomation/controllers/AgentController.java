package com.testingautomation.testautomation.controllers;

import com.testingautomation.testautomation.dto.requestDto.AgentRegistrationRequest;
import com.testingautomation.testautomation.dto.requestDto.RunCompleteRequest;
import com.testingautomation.testautomation.dto.requestDto.RunLogRequest;
import com.testingautomation.testautomation.entities.Agent;
import com.testingautomation.testautomation.services.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** Agent registers itself on startup. */
    @PostMapping("/register")
    public ResponseEntity<Agent> register(@RequestBody AgentRegistrationRequest req) {
        return ResponseEntity.ok(agentService.register(req));
    }

    /** Agent calls every 30 s to stay ONLINE. */
    @PostMapping("/{agentId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String agentId) {
        agentService.heartbeat(agentId);
        return ResponseEntity.ok().build();
    }

    /**
     * Agent polls every 3 s.
     * Response: {"status":"IDLE"} or {"status":"READY","runId":"..."}
     */
    @GetMapping("/{agentId}/next-run")
    public ResponseEntity<Map<String, String>> nextRun(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.nextRun(agentId));
    }

    /** Agent calls this after it has picked up the run (clears the assignment slot). */
    @PostMapping("/{agentId}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable String agentId) {
        agentService.acknowledgeRun(agentId);
        return ResponseEntity.ok().build();
    }

    /** Agent uploads a screenshot during execution. */
    @PostMapping("/runs/{runId}/screenshot")
    public ResponseEntity<Map<String, String>> uploadScreenshot(
            @PathVariable String runId,
            @RequestParam("file") MultipartFile file) {
        String url = agentService.uploadScreenshot(runId, file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** Agent posts a step log (fire-and-forget; stored in backend logs / future DB). */
    @PostMapping("/runs/{runId}/log")
    public ResponseEntity<Void> log(
            @PathVariable String runId,
            @RequestBody RunLogRequest req) {
        log.info("[RUN:{}] step={} status={} msg={}", runId, req.getStep(), req.getStatus(), req.getMessage());
        return ResponseEntity.ok().build();
    }

    /** Agent reports final result. */
    @PostMapping("/{agentId}/runs/{runId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable String agentId,
            @PathVariable String runId,
            @RequestBody RunCompleteRequest req) {
        agentService.completeRun(agentId, runId, req);
        return ResponseEntity.ok().build();
    }
}
