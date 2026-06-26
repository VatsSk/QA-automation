package com.testingautomation.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final AgentApiClient api;
    private final String agentId;
    private final int intervalMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "heartbeat"); t.setDaemon(true); return t; });

    public HeartbeatService(AgentApiClient api, String agentId, int intervalMs) {
        this.api        = api;
        this.agentId    = agentId;
        this.intervalMs = intervalMs;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                api.heartbeat(agentId);
                log.debug("Heartbeat sent");
            } catch (Exception e) {
                log.warn("Heartbeat failed: {}", e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Heartbeat started every {}ms", intervalMs);
    }

    public void stop() { scheduler.shutdownNow(); }
}
