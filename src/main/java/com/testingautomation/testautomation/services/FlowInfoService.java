package com.testingautomation.testautomation.services;

import com.testingautomation.testautomation.entities.component.FlowInfo;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.repositories.flowRepos.FlowInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class FlowInfoService {

    @Autowired
    private FlowInfoRepository flowInfoRepository;

    public Optional<FlowInfo> getFlowInfoById(String id) {
        return flowInfoRepository.findById(id);
    }
}
