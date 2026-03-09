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

//        logger.info("fds : {}",fields);
        Set<String> handledDataTargets = new HashSet<>();

        logger.info("Length of field descriptiors :---> "+fields.size());
        logger.info("Field desc ----  {}",fields);
        for (FieldDescriptor f : fields) {
            String locatorType = null;
            String locator = null;

            if (f.css != null && !f.css.isBlank() && !f.css.equals("#")) {
                locatorType = "css";
                locator = f.css;
            }
            else if (f.xpath != null && !f.xpath.contains("\"\"")) {
                locatorType = "xpath";
                locator = f.xpath;
            }
            else if (f.dataTarget != null) {
                locatorType = "xpath";
                locator = "//*[@data-target='" + f.dataTarget + "'][1]";
            }


            // determine matching CSV value (prefer id, then name, then visible text)
            String value = null;
            if (f.id != null) value = testCase.getValue(f.id);
            if ((value == null || value.isBlank()) && f.name != null) value = testCase.getValue(f.name);
            if((value == null || value.isBlank()) && f.dataTarget != null) value = testCase.getValue(f.dataTarget);
            if ((value == null || value.isBlank()) && f.text != null) value = testCase.getValue(f.text);
           if(value==null || value.isBlank()){
               logger.info("Data not matched---------"+value);
               continue;
            }

            logger.info("CSV lookup for {} -> {}", f.dataTarget, value);
            // Map tag/type -> action
            if ("input".equalsIgnoreCase(f.tag)) {
                String inputType = f.type != null ? f.type.toLowerCase() : "";

                // common CSV truthy values that mean "check / click"
                boolean csvTruthy = value != null && (
                        value.trim().equalsIgnoreCase("click") ||
                                value.trim().equalsIgnoreCase("true") ||
                                value.trim().equalsIgnoreCase("checked") ||
                                value.trim().equals("1")
                );

                // Handle checkboxes
                if ("checkbox".equals(inputType)) {
                    if (csvTruthy) {
                        String actionDescription = String.format("Click checkbox %s (id=%s)", locator, f.id);
                        steps.add(new StepAction(StepAction.ActionType.CLICK, locatorType, locator, null, actionDescription));
                    } else {
                        logger.debug("Skipping checkbox {} (not requested to click)", f.id != null ? f.id : locator);
                    }

                    // Handle radios
                } else if ("radio".equals(inputType)) {
                    if (value != null && !value.isBlank()) {
                        // if value is a truthy click and element has id -> click id
                        if (csvTruthy && f.id != null && !f.id.isBlank()) {
                            String actionDescription = String.format("Click radio %s (id=%s)", locator, f.id);
                            steps.add(new StepAction(StepAction.ActionType.CLICK, locatorType, locator, null, actionDescription));
                        } else {
                            // assume CSV value is the radio option value; prefer name attribute for group
                            if (f.name != null && !f.name.isBlank()) {
                                // build a CSS locator to input[name='group'][value='value']
                                String radioLocatorCss = "input[name=\"" + f.name + "\"][value=\"" + value.replace("\"","\\\"") + "\"]";
                                String actionDescription = String.format("Select radio %s (name=%s value=%s)", radioLocatorCss, f.name, value);
                                steps.add(new StepAction(StepAction.ActionType.CLICK, "css", radioLocatorCss, null, actionDescription));
                            } else if (f.id != null) {
                                String actionDescription = String.format("Click radio %s (id=%s)", locator, f.id);
                                steps.add(new StepAction(StepAction.ActionType.CLICK, locatorType, locator, null, actionDescription));
                            } else {
                                // fallback: try matching by value via xpath built from provided xpath (best-effort)
                                String radioXPath = f.xpath != null ? (f.xpath + "/following::input[@type='radio' and @value='" + value + "']") : null;
                                if (radioXPath != null) {
                                    String actionDescription = String.format("Select radio by xpath %s (value=%s)", radioXPath, value);
                                    steps.add(new StepAction(StepAction.ActionType.CLICK, "xpath", radioXPath, null, actionDescription));
                                } else {
                                    logger.debug("Skipping radio {} - cannot construct locator for value {}", f.name, value);
                                }
                            }
                        }
                    } else {
                        logger.debug("Skipping radio {} - no CSV value", f.name != null ? f.name : locator);
                    }

                    // Handle file upload
                } else if ("file".equals(inputType)) {
                    if (value != null && !value.isBlank()) {
                        // put the file path in payload - executor will sendKeys() to file input
                        String actionDescription = String.format("Upload file into %s (id=%s)", locator, f.id);
                        steps.add(new StepAction(StepAction.ActionType.TYPE, locatorType, locator, value, actionDescription));
                    } else {
                        logger.debug("Skipping file input {} (no file path provided)", f.id != null ? f.id : locator);
                    }

                    // Other input types that accept text payloads
                } else if (Arrays.asList("text","password","email","tel","number","search","url","date","datetime-local","time","month","week","color","hidden").contains(inputType) || inputType == null) {
                    if (value != null && !value.isBlank()) {
                        String actionDescription = String.format("Type into input %s (id=%s)", locator, f.id);
                        steps.add(new StepAction(StepAction.ActionType.TYPE, locatorType, locator, value, actionDescription));
                    } else {
                        logger.debug("Skipping input {} (no test data)", f.id != null ? f.id : locator);
                    }

                } else {
                    // unknown input type -> fallback to TYPE if CSV provided
                    if (value != null && !value.isBlank()) {
                        String actionDescription = String.format("Type into input %s (id=%s)", locator, f.id);
                        steps.add(new StepAction(StepAction.ActionType.TYPE, locatorType, locator, value, actionDescription));
                    } else {
                        logger.debug("Skipping input {} (unknown type and no data)", f.id != null ? f.id : locator);
                    }
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

                // click ONLY if CSV explicitly says "click"
                boolean csvSaysClick =
                        value != null && value.trim().equalsIgnoreCase("click");

                if (csvSaysClick) {
                    if (f.dataTarget != null && !f.dataTarget.isBlank()) {

                        if (handledDataTargets.contains(f.dataTarget)) {
                            continue; // skip duplicates
                        }

                        handledDataTargets.add(f.dataTarget);
                    }

                    String actionDescription =
                            String.format("Click on %s (id=%s)", locator, f.id);

                    steps.add(new StepAction(
                            StepAction.ActionType.CLICK,
                            locatorType,
                            locator,
                            null,
                            actionDescription
                    ));

                } else {
                    logger.debug("Skipping click {} (CSV did not request click)",
                            f.id != null ? f.id : locator);
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
