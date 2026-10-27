package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.entities.flow.FlowVariableDefinition;
import com.testingautomation.testautomation.enums.flow.VariableCharset;
import com.testingautomation.testautomation.enums.flow.VariableType;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates variable definitions (at save time) and generates runtime values
 * (at execution start).
 *
 * <p><strong>Generation contract:</strong>
 * <ul>
 *   <li>{@code length} is the number of randomly generated characters.</li>
 *   <li>The final value is: {@code prefix + generatedPortion + suffix}.</li>
 *   <li>All values are represented as Strings so leading zeros are preserved.</li>
 *   <li>A cryptographically strong {@link SecureRandom} is used.</li>
 * </ul>
 *
 * <p><strong>Uniqueness note:</strong>
 * Fresh random generation gives a low probability of collision across runs but
 * does not guarantee global uniqueness. Within a single execution every reference
 * to the same variable resolves to the same value (the map is initialized once
 * and shared read-only across all steps).
 * </p>
 */
@Service
public class FlowVariableService {

    private static final Logger logger = LoggerFactory.getLogger(FlowVariableService.class);

    /** Canonical variable name pattern: must start with a letter. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String LETTER_CHARS       = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_CHARS        = "0123456789";

    private static final int MAX_GENERATED_LENGTH = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Validation ──────────────────────────────────────────────────────────────

    /**
     * Validates all variable definitions in the map that will be stored on a Flow.
     *
     * <p>Rules enforced:
     * <ol>
     *   <li>Variable names must match {@code [A-Za-z][A-Za-z0-9_]*} after trimming.</li>
     *   <li>Variable names must be unique within the flow, ignoring case.</li>
     *   <li>No null definitions.</li>
     *   <li>Type must be non-null and a known {@link VariableType}.</li>
     *   <li>Charset must be non-null and a known {@link VariableCharset}.</li>
     *   <li>Length must be between 1 and {@value #MAX_GENERATED_LENGTH}.</li>
     *   <li>For NUMBER type, charset must be DIGITS.</li>
     * </ol>
     *
     * @param variables the variable map from the incoming Flow (may be null or empty)
     * @throws GlobalExceptionHandler.BadRequestException on any validation failure
     */
    public void validateDefinitions(Map<String, FlowVariableDefinition> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }

        // Detect case-insensitive duplicate names across the map keys
        Map<String, String> lowerToCanonical = new LinkedHashMap<>();
        for (String name : variables.keySet()) {
            if (name == null || name.trim().isEmpty()) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable name must not be blank.");
            }
            String trimmed = name.trim();
            if (!NAME_PATTERN.matcher(trimmed).matches()) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable name [" + trimmed + "] is invalid. " +
                        "Names must match [A-Za-z][A-Za-z0-9_]* (start with a letter, " +
                        "followed by letters, digits, or underscores).");
            }
            String lower = trimmed.toLowerCase();
            if (lowerToCanonical.containsKey(lower)) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Duplicate flow variable name detected: [" + trimmed + "] conflicts with [" +
                        lowerToCanonical.get(lower) + "] (names are case-insensitive within a flow).");
            }
            lowerToCanonical.put(lower, trimmed);
        }

        // Validate each definition
        for (Map.Entry<String, FlowVariableDefinition> entry : variables.entrySet()) {
            String name = entry.getKey().trim();
            FlowVariableDefinition def = entry.getValue();

            if (def == null) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] has a null definition.");
            }
            if (def.getType() == null) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] is missing 'type'. Must be STRING or NUMBER.");
            }
            if (def.getCharset() == null) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] is missing 'charset'. " +
                        "Must be ALPHANUMERIC, LETTERS, or DIGITS.");
            }
            if (def.getLength() == null || def.getLength() < 1) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] has an invalid length [" + def.getLength() + "]. " +
                        "Length must be >= 1.");
            }
            if (def.getLength() > MAX_GENERATED_LENGTH) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] length [" + def.getLength() + "] exceeds the " +
                        "maximum allowed length of " + MAX_GENERATED_LENGTH + ".");
            }
            if (def.getType() == VariableType.NUMBER && def.getCharset() != VariableCharset.DIGITS) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable [" + name + "] has type NUMBER but charset [" + def.getCharset() + "]. " +
                        "NUMBER variables must use charset DIGITS.");
            }
        }
    }

    // ── Runtime generation ──────────────────────────────────────────────────────

    /**
     * Generates one runtime value for every configured variable definition.
     *
     * <p>This method must be called exactly once per new execution. The returned
     * map is then stored in the {@code FlowExecutionContext} and shared (read-only)
     * across all steps of that execution.</p>
     *
     * <p>For a flow with no variables this returns an empty, unmodifiable map.</p>
     *
     * @param variables the variable definitions from the persisted Flow (never modified)
     * @param flowId    used only for logging
     * @return an unmodifiable map of variable name → generated String value
     */
    public Map<String, String> generateRuntimeValues(Map<String, FlowVariableDefinition> variables,
                                                     String flowId) {
        if (variables == null || variables.isEmpty()) {
            logger.debug("Flow [{}] has no variable definitions; skipping generation.", flowId);
            return Collections.emptyMap();
        }

        Map<String, String> runtimeValues = new HashMap<>();
        for (Map.Entry<String, FlowVariableDefinition> entry : variables.entrySet()) {
            String name = entry.getKey();
            FlowVariableDefinition def = entry.getValue();
            String generated = generateValue(def, name, flowId);
            runtimeValues.put(name, generated);
            logger.info("Flow [{}]: generated variable [{}] = [{}]", flowId, name, generated);
        }
        return Collections.unmodifiableMap(runtimeValues);
    }

    /**
     * Generates a single value for the given definition.
     * The value is: {@code prefix + randomPortion + suffix}.
     */
    private String generateValue(FlowVariableDefinition def, String varName, String flowId) {
        String alphabet = resolveAlphabet(def.getCharset());
        StringBuilder sb = new StringBuilder(def.getLength());
        for (int i = 0; i < def.getLength(); i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        String prefix = def.getPrefix() != null ? def.getPrefix() : "";
        String suffix = def.getSuffix() != null ? def.getSuffix() : "";
        return prefix + sb + suffix;
    }

    private String resolveAlphabet(VariableCharset charset) {
        return switch (charset) {
            case ALPHANUMERIC -> ALPHANUMERIC_CHARS;
            case LETTERS      -> LETTER_CHARS;
            case DIGITS       -> DIGIT_CHARS;
        };
    }

    // ── Reference validation ────────────────────────────────────────────────────

    /**
     * Pattern that matches {@code #name} references where name starts with a letter
     * and is followed by letters, digits, or underscores.
     *
     * <p>This pattern intentionally does NOT match CSS id selectors alone because
     * resolution is only applied to designated data-value fields, never to
     * selector/locator fields.</p>
     */
    public static final Pattern VARIABLE_REF_PATTERN =
            Pattern.compile("#([A-Za-z][A-Za-z0-9_]*)");

    /**
     * Validates that every {@code #variableName} token found in the given step
     * field values is actually defined in the flow's variable map.
     *
     * <p>This check runs before Selenium execution begins. It uses case-sensitive
     * matching against the canonical stored names and provides a helpful error
     * when the casing is wrong.</p>
     *
     * @param stepValueFields a list of candidate field values to scan (may include nulls)
     * @param variables       the flow's persisted variable definitions
     * @param stepName        step name for error messages
     * @param stepOrder       step order for error messages
     * @param flowId          flow ID for error messages
     * @throws GlobalExceptionHandler.FlowExecutionException if an undefined reference is found
     */
    public void validateStepReferences(List<String> stepValueFields,
                                       Map<String, FlowVariableDefinition> variables,
                                       String stepName,
                                       Integer stepOrder,
                                       String flowId) {
        if (stepValueFields == null) return;
        Map<String, FlowVariableDefinition> safeVars = variables != null ? variables : Collections.emptyMap();

        for (String fieldValue : stepValueFields) {
            if (fieldValue == null || !fieldValue.contains("#")) continue;
            java.util.regex.Matcher m = VARIABLE_REF_PATTERN.matcher(fieldValue);
            while (m.find()) {
                String refName = m.group(1);
                if (!safeVars.containsKey(refName)) {
                    // Check if it's a case-mismatch
                    String suggestion = safeVars.keySet().stream()
                            .filter(k -> k.equalsIgnoreCase(refName))
                            .findFirst()
                            .orElse(null);
                    String detail = suggestion != null
                            ? " Did you mean [" + suggestion + "]? Variable names are case-sensitive."
                            : " No variable named [" + refName + "] is defined in this flow.";
                    throw new GlobalExceptionHandler.FlowExecutionException(
                            stepOrder, stepName, null,
                            "Undefined variable [#" + refName + "] in step [" + stepName +
                            "] of flow [" + flowId + "]." + detail,
                            "Undefined variable reference [#" + refName + "]. Define it in the flow's variables map before running.",
                            null
                    );
                }
            }
        }
    }

    // ── Resolution ──────────────────────────────────────────────────────────────

    /**
     * Resolves all {@code #variableName} tokens in a string using the runtime map.
     *
     * <p>Returns the original string if:
     * <ul>
     *   <li>the input is null</li>
     *   <li>there are no {@code #name} patterns</li>
     *   <li>the runtime map is null or empty</li>
     * </ul>
     *
     * @param value          the raw string that may contain variable references
     * @param runtimeValues  map of variable name → generated value for this execution
     * @return the resolved string with all known references replaced
     */
    public String resolveValue(String value, Map<String, String> runtimeValues) {
        if (value == null || !value.contains("#") || runtimeValues == null || runtimeValues.isEmpty()) {
            return value;
        }
        java.util.regex.Matcher m = VARIABLE_REF_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String replacement = runtimeValues.get(name);
            if (replacement != null) {
                // Escape $ and \ in replacement to prevent regex interpretation
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } else {
                // Leave unknown references as-is (they were validated earlier)
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
