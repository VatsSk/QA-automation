package com.testingautomation.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentApiClient api;
    private final ExecutionService executor;
    private final String agentId;
    private final int pollMs;

    public AgentRunner(AgentApiClient api, ExecutionService executor,
                       String agentId, int pollMs) {
        this.api      = api;
        this.executor = executor;
        this.agentId  = agentId;
        this.pollMs   = pollMs;
    }

    /** Blocks forever, polling for work. */
    public void run() {
        log.info("AgentRunner polling every {}ms", pollMs);
        while (true) {
            try {
                String runId = api.getNextRun(agentId);

                if (runId != null) {
                    log.info("Run assigned: {}", runId);
                    api.acknowledgeRun(agentId); // clear assignment slot immediately

                    JsonNode runData = api.downloadRun(runId);
                    executor.execute(agentId, runId, runData);
                }

                Thread.sleep(pollMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("AgentRunner interrupted, stopping.");
                break;
            } catch (Exception e) {
                log.error("Polling error: {}", e.getMessage());
                sleepQuietly(pollMs);
            }
        }
    }

    private void sleepQuietly(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
