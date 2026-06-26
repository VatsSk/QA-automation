package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.dto.requestDto.AgentRegistrationRequest;
import com.testingautomation.testautomation.dto.requestDto.RunCompleteRequest;
import com.testingautomation.testautomation.entities.Agent;
import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.enums.AgentStatus;
import com.testingautomation.testautomation.enums.RunStatus;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.repositories.AgentRepository;
import com.testingautomation.testautomation.repositories.runRepos.RunRepository;
import com.testingautomation.testautomation.services.s3Service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final RunRepository runRepository;
    private final StorageService storageService;

    public Agent register(AgentRegistrationRequest req) {
        Agent agent = agentRepository.findById(req.getAgentId())
                .orElse(new Agent());
        agent.setId(req.getAgentId());
        agent.setHostname(req.getHostname());
        agent.setOs(req.getOs());
        agent.setAgentVersion(req.getAgentVersion());
        agent.setRegisteredBy(req.getRegisteredBy());
        agent.setStatus(AgentStatus.ONLINE);
        agent.setLastSeen(Instant.now());
        if (agent.getRegisteredAt() == null) agent.setRegisteredAt(Instant.now());
        log.info("Agent registered: {}", req.getAgentId());
        return agentRepository.save(agent);
    }

    public void heartbeat(String agentId) {
        Agent agent = findAgentOrThrow(agentId);
        agent.setLastSeen(Instant.now());
        // keep BUSY if it has an assigned run
        if (agent.getStatus() != AgentStatus.BUSY) {
            agent.setStatus(AgentStatus.ONLINE);
        }
        agentRepository.save(agent);
    }

    /** Called by RunService.executeRun() to hand a run to an online agent. */
    public void assignRun(String runId, String createdBy) {
        Instant cutoff = Instant.now().minusSeconds(60);
        Agent agent = agentRepository.findAllByRegisteredByAndStatus(createdBy, AgentStatus.ONLINE)
                .stream()
                .filter(a -> a.getLastSeen() != null && a.getLastSeen().isAfter(cutoff))
                .findFirst()
                .orElseThrow(() -> new GlobalExceptionHandler.BadRequestException(
                        "No online QA Agent found for user: " + createdBy +
                        ". Please start the QA Agent on your machine."));
        
        agent.setAssignedRunId(runId);
        agent.setStatus(AgentStatus.BUSY);
        agentRepository.save(agent);
        log.info("Run {} assigned to agent {}", runId, agent.getId());
    }

    /** Polled by the agent every few seconds. */
    public Map<String, String> nextRun(String agentId) {
        Agent agent = findAgentOrThrow(agentId);
        Map<String, String> response = new HashMap<>();

        if (agent.getAssignedRunId() != null) {
            response.put("status", "READY");
            response.put("runId", agent.getAssignedRunId());
        } else {
            response.put("status", "IDLE");
        }
        return response;
    }

    /** Agent calls this once it starts executing a run (clears the assignment slot). */
    public void acknowledgeRun(String agentId) {
        Agent agent = findAgentOrThrow(agentId);
        agent.setAssignedRunId(null); // clear so the slot can receive another run later
        agentRepository.save(agent);
    }

    /** Agent uploads a screenshot for a run scenario. */
    public String uploadScreenshot(String runId, MultipartFile file) {
        String url = storageService.uploadScreenshot(file);
        // Append to first in-progress scenario's screenshots list
        Run run = findRunOrThrow(runId);
        if (run.getScenariosList() != null && !run.getScenariosList().isEmpty()) {
            run.getScenariosList().stream()
                    .filter(s -> s.getScenarioStatus() == RunStatus.RUNNING ||
                                 s.getScenarioStatus() == RunStatus.DRAFT)
                    .findFirst()
                    .ifPresent(s -> {
                        if (s.getScreenshots() == null) s.setScreenshots(new java.util.ArrayList<>());
                        s.getScreenshots().add(url);
                    });
            runRepository.save(run);
        }
        return url;
    }

    /** Agent calls this when execution is fully done. */
    public void completeRun(String agentId, String runId, RunCompleteRequest req) {
        Run run = findRunOrThrow(runId);
        run.setStatus(req.getStatus());
        run.setReason(req.getReason());
        run.setUpdatedAt(Instant.now());
        runRepository.save(run);

        // Free the agent
        Agent agent = findAgentOrThrow(agentId);
        agent.setStatus(AgentStatus.ONLINE);
        agent.setAssignedRunId(null);
        agentRepository.save(agent);

        log.info("Run {} completed by agent {} with status {}", runId, agentId, req.getStatus());
    }

    private Agent findAgentOrThrow(String agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Agent not found: " + agentId));
    }

    private Run findRunOrThrow(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Run not found: " + runId));
    }
}
