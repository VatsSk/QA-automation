package com.testingautomation.testautomation.dto.requestDto;

import lombok.Data;

@Data
public class AgentRegistrationRequest {
    private String agentId;
    private String hostname;
    private String os;
    private String agentVersion;
    private String registeredBy; // username
}
