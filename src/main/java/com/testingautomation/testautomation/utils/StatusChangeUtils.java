package com.testingautomation.testautomation.utils;

import com.testingautomation.testautomation.dto.TestCaseDTO;
import com.testingautomation.testautomation.entities.Scenario;
import com.testingautomation.testautomation.enums.RunStatus;

public class StatusChangeUtils {
    public static void testCaseResultSetter(boolean verifyStatus, TestCaseDTO resultTestCase){
        if (verifyStatus) {
            resultTestCase.getValues().put("FinalVerificationStatus","Passed");
            resultTestCase.setActual("Passed");
            if ("Passed".equals(resultTestCase.getExpectedResult())) {
                resultTestCase.setResult("Passed");
            } else if ("Failed".equals(resultTestCase.getExpectedResult())) {
                resultTestCase.setResult("Failed");
            }
        } else {
            resultTestCase.getValues().put("FinalVerificationStatus","Failed");
            resultTestCase.setActual("Failed");
            if ("Failed".equals(resultTestCase.getExpectedResult())) {
                resultTestCase.setResult("Passed");
            } else if("Passed".equals(resultTestCase.getExpectedResult())) {
                resultTestCase.setResult("Failed");

            }
        }
    }
    public static void initialVerificationStatus(boolean verifyStatus, TestCaseDTO resultTestCase){
        if (verifyStatus) {
           resultTestCase.getValues().put("initialVerificationStatus","Passed");
        } else {
            resultTestCase.getValues().put("initialVerificationStatus","Failed");
            resultTestCase.setActual("Initial verification Failed");
            resultTestCase.setResult("Failed");
        }
    }

    public static void scenarioStatusSetter(TestCaseDTO resultTestCase, Scenario currScenario){
        if("Failed".equalsIgnoreCase(resultTestCase.getResult())){
            if(currScenario.getScenarioStatus()== RunStatus.DRAFT){
                currScenario.setScenarioStatus(RunStatus.FAILED);
            }else if(currScenario.getScenarioStatus()==RunStatus.PASSED){
                currScenario.setScenarioStatus(RunStatus.PARTIAL);
            }
        }else{
            if(currScenario.getScenarioStatus()==RunStatus.DRAFT){
                currScenario.setScenarioStatus(RunStatus.PASSED);
            }else if(currScenario.getScenarioStatus()==RunStatus.FAILED){
                currScenario.setScenarioStatus(RunStatus.PARTIAL);
            }
        }
    }
}
