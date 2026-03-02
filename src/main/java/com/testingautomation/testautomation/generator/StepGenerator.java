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





//
//package com.testingautomation.testautomation.generator;
//
//import com.testingautomation.testautomation.model.StepAction;
//import com.testingautomation.testautomation.model.TestCase;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//import java.util.*;
//import com.testingautomation.testautomation.model.FieldDescriptor;
//
//@Component
//public class StepGenerator {
//    private final Logger logger = LoggerFactory.getLogger(StepGenerator.class);
//
//    public List<StepAction> generateSteps(List<FieldDescriptor> fields, TestCase testCase) {
//        logger.info("Generating steps for testcase {}", testCase.getId());
//        List<StepAction> steps = new ArrayList<>();
//
//        // keywords used to auto-detect submit-like buttons when CSV doesn't explicitly ask for click
//        final List<String> submitKeywords = Arrays.asList("login", "sign in", "submit", "register", "create account", "continue", "next", "save");
//
//        for (FieldDescriptor f : fields) {
//            // choose locator (prefer css then xpath). If none, we'll try to build a text-based xpath fallback
//            String locatorType = (f.css != null && !f.css.isBlank()) ? "css" : "xpath";
//            String locator = (f.css != null && !f.css.isBlank()) ? f.css : f.xpath;
//
//            // determine matching CSV value (prefer id, then name, then visible text)
//            String value = null;
//            if (f.id != null) value = testCase.getValue(f.id);
//            if ((value == null || value.isBlank()) && f.name != null) value = testCase.getValue(f.name);
//            if ((value == null || value.isBlank()) && f.text != null) value = testCase.getValue(f.text);
//
//            // If no locator, try fallback xpath based on visible text (if available)
//            if ((locator == null || locator.isBlank())) {
//                if (f.text != null && !f.text.isBlank()) {
//                    locatorType = "xpath";
//                    locator = xpathContains(f.text);
//                    logger.debug("Using text-based fallback locator for field: {} -> {}", f, locator);
//                } else {
//                    logger.warn("Skipping field (no locator & no text): {}", f);
//                    continue; // can't act on element without locator or text
//                }
//            }
//
//            // Map tag/type -> action
//            if ("input".equalsIgnoreCase(f.tag)) {
//                // Only create TYPE action when CSV provided a non-empty value
//                if (value != null && !value.isBlank()) {
//                    String actionDescription = String.format("Type into input %s (id=%s)", locator, f.id);
//                    steps.add(new StepAction(StepAction.ActionType.TYPE, locatorType, locator, value, actionDescription));
//                } else {
//                    logger.debug("Skipping input {} (no test data)", f.id != null ? f.id : locator);
//                }
//
//            } else if ("select".equalsIgnoreCase(f.tag)) {
//                // If no CSV value, fallback to scanner-provided text (scanner sets text = first option for selects)
//                if ((value == null || value.isBlank()) && f.text != null && !f.text.isBlank()) {
//                    value = f.text;
//                }
//
//                // Only create SELECT action when a non-empty value is available (pick first from list)
//                if (value != null && !value.isBlank()) {
//                    // choose only the first value (CSV may be "English,Spanish")
//                    String chosen = Arrays.stream(value.split("[,|]"))
//                            .map(String::trim)
//                            .filter(s -> !s.isEmpty())
//                            .findFirst()
//                            .orElse(value);
//
//                    String actionDescription = String.format("Select '%s' in %s", chosen, locator);
//                    steps.add(new StepAction(StepAction.ActionType.SELECT, locatorType, locator, chosen, actionDescription));
//                } else {
//                    logger.debug("Skipping select {} (no test data and no scanner default)", f.id != null ? f.id : locator);
//                }
//
//            } else if ("button".equalsIgnoreCase(f.tag) || "a".equalsIgnoreCase(f.tag)) {
//                // For buttons/links: click only if it's a submit OR CSV explicitly asks for "click" OR the visible text is a known submit keyword
//                boolean isSubmit = f.type != null && "submit".equalsIgnoreCase(f.type);
//                boolean csvSaysClick = value != null && "click".equalsIgnoreCase(value.trim());
//                boolean textLooksLikeSubmit = false;
//                if (f.text != null && !f.text.isBlank()) {
//                    String t = f.text.trim().toLowerCase();
//                    for (String k : submitKeywords) {
//                        if (t.contains(k)) { textLooksLikeSubmit = true; break; }
//                    }
//                }
//
//                if (isSubmit || csvSaysClick || textLooksLikeSubmit) {
//                    String actionDescription = String.format("Click on %s (text=%s)", locator, f.text);
//                    steps.add(new StepAction(StepAction.ActionType.CLICK, locatorType, locator, null, actionDescription));
//                } else {
//                    logger.debug("Skipping click {} (not submit and no CSV click)", f.id != null ? f.id : locator);
//                }
//
//            } else if ("span".equalsIgnoreCase(f.tag)) {
//                // Only verify if CSV explicitly provides expected value
//                if (value != null && !value.isBlank()) {
//                    String actionDescription = String.format("Verify text in %s", locator);
//                    steps.add(new StepAction(
//                            StepAction.ActionType.VERIFY_TEXT,
//                            locatorType,
//                            locator,
//                            value,
//                            actionDescription
//                    ));
//                } else {
//                    logger.debug("Skipping span {} - no verification requested", f.text);
//                }
//            } else {
//                // fallback: light wait to keep execution stable when encountering unknown tags
//                steps.add(new StepAction(StepAction.ActionType.WAIT, "css", "body", "250", "Wait small moment"));
//            }
//        }
//
//        logger.debug("Generated {} steps for testcase {}", steps.size(), testCase.getId());
//        return steps;
//    }
//
//    // Build a safe xpath contains() expression for the provided text.
//    // Handles cases where text contains single or double quotes.
//    private static String xpathContains(String text) {
//        if (text == null) return "//*[normalize-space()]"; // very generic fallback (should not happen)
//        // trim to avoid whitespace noise
//        String t = text.trim();
//        // prefer double-quoted literal if it doesn't contain double quotes
//        if (!t.contains("\"")) {
//            return "//*[contains(normalize-space(.), \"" + escapeForXpath(t) + "\")]";
//        }
//        // else prefer single-quoted literal if it doesn't contain single quotes
//        if (!t.contains("'")) {
//            return "//*[contains(normalize-space(.), '" + escapeForXpath(t) + "')]";
//        }
//        // both quotes present: build concat('a','\"','b') style
//        String[] parts = t.split("\"", -1); // keep trailing empty parts
//        StringBuilder sb = new StringBuilder("//*[contains(normalize-space(.), concat(");
//        for (int i = 0; i < parts.length; i++) {
//            if (i > 0) sb.append(", '\"', ");
//            sb.append("'").append(escapeForXpath(parts[i])).append("'");
//        }
//        sb.append("))]");
//        return sb.toString();
//    }
//
//    // minimal escape for XPath single/double quote content (we already handle quote cases above,
//    // but keep simple replacements for control chars)
//    private static String escapeForXpath(String s) {
//        if (s == null) return "";
//        return s.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
//    }
//}