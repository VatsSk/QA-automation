package com.testingautomation.testautomation.entities;

import com.testingautomation.testautomation.enums.AgentStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "agents")
public class Agent {

    @Id
    private String id;          // agentId sent by the agent

    private String hostname;
    private String os;
    private String agentVersion;
    private String registeredBy; // username who owns this agent

    private AgentStatus status;
    private Instant lastSeen;
    private Instant registeredAt;

    private String assignedRunId; // null when idle
}
