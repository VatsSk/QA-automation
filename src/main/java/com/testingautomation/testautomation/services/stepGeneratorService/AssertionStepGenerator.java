package com.testingautomation.testautomation.services.stepGeneratorService;

import com.testingautomation.testautomation.dto.AssertionDto;
import com.testingautomation.testautomation.dto.StepAction;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.enums.AssertionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssertionStepGenerator {

    public List<StepAction> generateAssertionSteps(List<AssertionDto> assertions) {

        List<StepAction> steps = new ArrayList<>();

        if (assertions == null || assertions.isEmpty()) {
            return steps;
        }

        for (AssertionDto a : assertions) {

            StepAction.ActionType actionType = mapToActionType(a.getType());

            String description = buildDescription(a);
            StepAction step = new StepAction(
                    actionType,                 //type
                    null,                      //locationType
                    a.getLocator(),           //locator
                    a.getExpected(),         // expected goes as value(payload)
                    description,            //description
                    a.getTableId(),        //tableId
                    a.getColumnName(),    //column name
                    a.getRowsBtn(),      //rowsBtn
                    a.getPrompt(),      //prompt
                    a.getOrder(),      //order
                    a                 //assertionDto
            );

            steps.add(step);
        }

        return steps;
    }

    /**
     * Map AssertionType → StepAction.ActionType
     */
//    private StepAction.ActionType mapToActionType(AssertionType type) {
//
//        return switch (type) {
//            case ASSERT_VISIBLE -> StepAction.ActionType.ASSERT_VISIBLE;
//            case ASSERT_NOT_VISIBLE -> StepAction.ActionType.ASSERT_NOT_VISIBLE;
//            case ASSERT_ELEMENT_PRESENT -> StepAction.ActionType.ASSERT_ELEMENT_PRESENT;
//            case ASSERT_TEXT_EQUALS -> StepAction.ActionType.ASSERT_TEXT_EQUALS;
//            case ASSERT_TEXT_CONTAINS -> StepAction.ActionType.ASSERT_TEXT_CONTAINS;
//            case ASSERT_COLUMN_PRESENT -> StepAction.ActionType.ASSERT_COLUMN_PRESENT;
//            case ASSERT_SORT_ORDER -> StepAction.ActionType.ASSERT_SORT_ORDER;
//            case ASSERT_COUNT -> StepAction.ActionType.ASSERT_COUNT;
//            case ASSERT_API_CALLED -> StepAction.ActionType.ASSERT_API_CALLED;
//            case ASSERT_ATTRIBUTE -> StepAction.ActionType.ASSERT_ATTRIBUTE;
//        };

//    }
    private StepAction.ActionType mapToActionType(AssertionType type) {

        switch (type) {
            case ASSERT_VISIBLE:
                return StepAction.ActionType.ASSERT_VISIBLE;

            case ASSERT_NOT_VISIBLE:
                return StepAction.ActionType.ASSERT_NOT_VISIBLE;

            case ASSERT_ELEMENT_PRESENT:
                return StepAction.ActionType.ASSERT_ELEMENT_PRESENT;

            case ASSERT_TEXT_EQUALS:
                return StepAction.ActionType.ASSERT_TEXT_EQUALS;

            case ASSERT_TEXT_CONTAINS:
                return StepAction.ActionType.ASSERT_TEXT_CONTAINS;

            case ASSERT_COLUMN_PRESENT:
                return StepAction.ActionType.ASSERT_COLUMN_PRESENT;

            case ASSERT_SORT_ORDER:
                return StepAction.ActionType.ASSERT_SORT_ORDER;

            case ASSERT_COUNT:
                return StepAction.ActionType.ASSERT_COUNT;

            case ASSERT_API_CALLED:
                return StepAction.ActionType.ASSERT_API_CALLED;

            case ASSERT_ATTRIBUTE:
                return StepAction.ActionType.ASSERT_ATTRIBUTE;

            case ASSERT_AI:
                return StepAction.ActionType.ASSERT_AI;
            case ASSERT_FILTER:
                return StepAction.ActionType.ASSERT_FILTER;

            default:
                throw new GlobalExceptionHandler.BadRequestException(
                        "Invalid configuration - Unknown assertion type: " + type
                );
        }
    }

    /**
     * Human readable description (very useful in logs)
     */
    private String buildDescription(AssertionDto a) {

        switch (a.getType()) {

            case ASSERT_VISIBLE:
                return "Assert element visible: " + a.getLocator();

            case ASSERT_NOT_VISIBLE:
                return "Assert element NOT visible: " + a.getLocator();

            case ASSERT_ELEMENT_PRESENT:
                return "Assert element present: " + a.getLocator();

            case ASSERT_TEXT_EQUALS:
                return "Assert text equals: " + a.getExpected();

            case ASSERT_TEXT_CONTAINS:
                return "Assert text contains: " + a.getExpected();

            case ASSERT_COLUMN_PRESENT:
                return "Assert column present: " + a.getColumnName();

            case ASSERT_SORT_ORDER:
                return "Assert sorting: " + a.getExpected();

            case ASSERT_COUNT:
                return "Assert count = " + a.getExpected();

            case ASSERT_API_CALLED:
                return "Assert API called: " + a.getColumnName();

            case ASSERT_ATTRIBUTE:
                return "Assert attribute";

            case ASSERT_AI:
                return "Assert via Prompt: "+a.getPrompt();

            default:
                return "Unknown assertion";
        }
    }
}