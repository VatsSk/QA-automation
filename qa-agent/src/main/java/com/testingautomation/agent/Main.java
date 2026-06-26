package com.testingautomation.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Properties props = loadProperties();

        String backendUrl    = props.getProperty("backend.url", "http://localhost:8088");
        int    pollMs        = Integer.parseInt(props.getProperty("agent.poll.interval.ms",      "3000"));
        int    heartbeatMs   = Integer.parseInt(props.getProperty("agent.heartbeat.interval.ms", "30000"));
        boolean headless     = Boolean.parseBoolean(props.getProperty("agent.headless", "false"));

        // agentId = stable UUID stored in properties, or auto-generate
        String agentId = props.getProperty("agent.id", UUID.randomUUID().toString());

        String registeredBy = System.getProperty("agent.user",
                props.getProperty("agent.user", "default"));

        log.info("QA Agent starting | id={} backend={}", agentId, backendUrl);

        AgentApiClient api = new AgentApiClient(backendUrl);
        api.register(agentId, registeredBy);

        HeartbeatService heartbeat = new HeartbeatService(api, agentId, heartbeatMs);
        heartbeat.start();

        ExecutionService executor = new ExecutionService(api, headless);
        AgentRunner runner = new AgentRunner(api, executor, agentId, pollMs);
        runner.run(); // blocks forever
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Main.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (in != null) props.load(in);
        }
        return props;
    }
}
