package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.enums.flow.FlowStatus;
import com.testingautomation.testautomation.repositories.flowRepos.FlowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FlowService {

    @Autowired
    private FlowRepository flowRepository;

    public Flow saveFlow(Flow flow) {
        if (flow.getId() == null) {
            flow.setCreatedAt(Instant.now());
        }
        flow.setUpdatedAt(Instant.now());
        return flowRepository.save(flow);
    }

    public Flow saveAsDraft(Flow flow) {
        flow.setStatus(FlowStatus.DRAFT);
        return saveFlow(flow);
    }

    public List<Flow> getFlowsByModule(String projectId, String moduleId) {
        return flowRepository.findByProjectIdAndModuleId(projectId, moduleId);
    }

    public Optional<Flow> getFlowById(String id) {
        return flowRepository.findById(id);
    }

    public void deleteFlow(String id) {
        flowRepository.deleteById(id);
    }
}
