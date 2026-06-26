package com.testingautomation.testautomation.repositories;

import com.testingautomation.testautomation.entities.Agent;
import com.testingautomation.testautomation.enums.AgentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AgentRepository extends MongoRepository<Agent, String> {
    Optional<Agent> findFirstByRegisteredByAndStatus(String registeredBy, AgentStatus status);
}
