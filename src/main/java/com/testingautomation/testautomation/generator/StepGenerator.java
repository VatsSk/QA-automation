package com.testingautomation.testautomation.generator;

import com.testingautomation.testautomation.model.StepAction;
import com.testingautomation.testautomation.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import com.testingautomation.testautomation.model.FieldDescriptor;

@Component
public class StepGenerator {
    private final Logger logger = LoggerFactory.getLogger(StepGenerator.class);

    public List<StepAction> generateSteps(List<FieldDescriptor> fields, TestCase testCase) {
        logger.info("Generating steps for testcase {}", testCase.getId());
        List<StepAction> steps = new ArrayList<>();

        for (FieldDescriptor f : fields) {
            String locatorType = (f.css != null && !f.css.isBlank()) ? "css" : "xpath";
            String locator = (f.css != null && !f.css.isBlank()) ? f.css : f.xpath;

            // determine matching CSV value (prefer id, then name, then visible text)
            String value = null;
            if (f.id != null) value = testCase.getValue(f.id);
            if ((value == null || value.isBlank()) && f.name != null) value = testCase.getValue(f.name);
            if ((value == null || value.isBlank()) && f.text != null) value = testCase.getValue(f.text);

            // Map tag/type -> action
            if ("input".equalsIgnoreCase(f.tag)) {
                // Only create TYPE action when CSV provided a non-empty value
                if (value != null && !value.isBlank()) {
                    String actionDescription = String.format("Type into input %s (id=%s)", locator, f.id);
                    steps.add(new StepAction(StepAction.ActionType.TYPE, locatorType, locator, value, actionDescription));
                } else {
                    logger.debug("Skipping input {} (no test data)", f.id != null ? f.id : locator);
                }

            } else if ("select".equalsIgnoreCase(f.tag)) {
                // Only create SELECT action when CSV provided a non-empty value
                if (value != null && !value.isBlank()) {
                    String actionDescription = String.format("Select value in %s", locator);
                    steps.add(new StepAction(StepAction.ActionType.SELECT, locatorType, locator, value, actionDescription));
                } else {
                    logger.debug("Skipping select {} (no test data)", f.id != null ? f.id : locator);
                }

            } else if ("button".equalsIgnoreCase(f.tag) || "a".equalsIgnoreCase(f.tag)) {
                // For buttons/links: click only if it's a submit OR CSV explicitly asks for "click"
                boolean isSubmit = f.type != null && "submit".equalsIgnoreCase(f.type);
                boolean csvSaysClick = value != null && "click".equalsIgnoreCase(value.trim());

                if (isSubmit || csvSaysClick) {
                    String actionDescription = String.format("Click on %s (text=%s)", locator, f.text);
                    steps.add(new StepAction(StepAction.ActionType.CLICK, locatorType, locator, null, actionDescription));
                } else {
                    logger.debug("Skipping click {} (not submit and no CSV click)", f.id != null ? f.id : locator);
                }

            }else if ("span".equalsIgnoreCase(f.tag)) {

                // Only verify if CSV explicitly provides expected value
                if (value != null && !value.isBlank()) {
                    String actionDescription = String.format("Verify text in %s", locator);
                    steps.add(new StepAction(
                            StepAction.ActionType.VERIFY_TEXT,
                            locatorType,
                            locator,
                            value,
                            actionDescription
                    ));
                } else {
                    logger.debug("Skipping span {} - no verification requested", f.text);
                }
            } else {
                // fallback: light wait to keep execution stable when encountering unknown tags
                steps.add(new StepAction(StepAction.ActionType.WAIT, "css", "body", "250", "Wait small moment"));
            }
        }

        logger.debug("Generated {} steps for testcase {}", steps.size(), testCase.getId());
        return steps;
    }
}