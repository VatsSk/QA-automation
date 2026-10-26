package com.testingautomation.testautomation;

import com.testingautomation.testautomation.entities.flow.FlowVariableDefinition;
import com.testingautomation.testautomation.enums.flow.VariableCharset;
import com.testingautomation.testautomation.enums.flow.VariableType;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.flowService.FlowVariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for variable resolution in step fields and step-level reference validation.
 */
class FlowVariableResolutionTest {

    private FlowVariableService service;

    @BeforeEach
    void setUp() {
        service = new FlowVariableService();
    }

    // ── Step field resolution ─────────────────────────────────────────────────

    @Test
    @DisplayName("resolveValue replaces reference in step value field")
    void resolveValue_inValueField_replacesCorrectly() {
        Map<String, String> runtime = Map.of("username", "alice42");
        // The regex [A-Za-z][A-Za-z0-9_]* includes underscores, so delimit with non-identifier chars
        String resolved = service.resolveValue("user #username form", runtime);
        assertEquals("user alice42 form", resolved);
    }

    @Test
    @DisplayName("resolveValue replaces reference in expectedValue field")
    void resolveValue_inExpectedValueField_replacesCorrectly() {
        Map<String, String> runtime = Map.of("orderNum", "00012345");
        String resolved = service.resolveValue("Order #orderNum confirmed", runtime);
        assertEquals("Order 00012345 confirmed", resolved);
    }

    @Test
    @DisplayName("resolveValue replaces reference in url field")
    void resolveValue_inUrlField_replacesCorrectly() {
        Map<String, String> runtime = Map.of("userId", "u99");
        String resolved = service.resolveValue("https://example.com/users/#userId/profile", runtime);
        assertEquals("https://example.com/users/u99/profile", resolved);
    }

    @Test
    @DisplayName("resolveValue replaces reference in message field")
    void resolveValue_inMessageField_replacesCorrectly() {
        Map<String, String> runtime = Map.of("code", "ABC123");
        String resolved = service.resolveValue("Your code is #code", runtime);
        assertEquals("Your code is ABC123", resolved);
    }

    @Test
    @DisplayName("resolveValue does not touch selector-like values when called on data fields")
    void resolveValue_selectorStyleInput_noResolution() {
        // Even if passed through by mistake, unknown refs are left as-is
        Map<String, String> runtime = Map.of("known", "val");
        String result = service.resolveValue("#someElement .child", runtime);
        // "someElement" is not in runtime, so left as-is
        assertEquals("#someElement .child", result);
    }

    @Test
    @DisplayName("resolveValue with prefix-only variable produces correct output")
    void resolveValue_prefixOnlyVariable_producesCorrectOutput() {
        // Simulate a generated value that already has prefix baked in
        Map<String, String> runtime = Map.of("orderId", "ORD-123456");
        String resolved = service.resolveValue("Order: #orderId", runtime);
        assertEquals("Order: ORD-123456", resolved);
    }

    @Test
    @DisplayName("resolveValue preserves leading zeros in number variables")
    void resolveValue_numberWithLeadingZeros_preserved() {
        Map<String, String> runtime = Map.of("pin", "007890");
        String resolved = service.resolveValue("PIN=#pin", runtime);
        assertEquals("PIN=007890", resolved);
    }

    @Test
    @DisplayName("resolveValue with null value returns null")
    void resolveValue_nullValue_returnsNull() {
        Map<String, String> runtime = Map.of("x", "val");
        assertNull(service.resolveValue(null, runtime));
    }

    @Test
    @DisplayName("resolveValue with no variable references returns original string")
    void resolveValue_noReferences_returnsOriginal() {
        Map<String, String> runtime = Map.of("x", "val");
        assertEquals("static text only", service.resolveValue("static text only", runtime));
    }

    @Test
    @DisplayName("resolveValue replaces all occurrences of the same variable")
    void resolveValue_sameVariableMultipleTimes_allReplaced() {
        Map<String, String> runtime = Map.of("token", "abc");
        String resolved = service.resolveValue("#token and #token again", runtime);
        assertEquals("abc and abc again", resolved);
    }

    // ── Step reference validation ─────────────────────────────────────────────

    @Test
    @DisplayName("validateStepReferences passes when all references are defined")
    void validateStepReferences_allDefined_noException() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("username", new FlowVariableDefinition(VariableType.STRING, 8, VariableCharset.ALPHANUMERIC, null, null));
        vars.put("orderId", new FlowVariableDefinition(VariableType.NUMBER, 6, VariableCharset.DIGITS, null, null));

        List<String> fields = Arrays.asList("#username", "Order #orderId complete", null, "static");
        assertDoesNotThrow(() -> service.validateStepReferences(fields, vars, "step1", 1, "flow1"));
    }

    @Test
    @DisplayName("validateStepReferences throws when undefined variable reference found")
    void validateStepReferences_undefinedReference_throwsFlowExecutionException() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("defined", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null));

        List<String> fields = Arrays.asList("value with #undefined ref");
        GlobalExceptionHandler.FlowExecutionException ex = assertThrows(
                GlobalExceptionHandler.FlowExecutionException.class,
                () -> service.validateStepReferences(fields, vars, "myStep", 3, "flow42")
        );
        assertTrue(ex.getMessage().contains("undefined") || ex.getMessage().contains("Undefined"),
                "Expected undefined reference message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("validateStepReferences provides case-mismatch suggestion in error")
    void validateStepReferences_caseMismatch_suggestionInError() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("Username", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null));

        // Reference uses lowercase but variable is stored as "Username"
        List<String> fields = Arrays.asList("#username");
        GlobalExceptionHandler.FlowExecutionException ex = assertThrows(
                GlobalExceptionHandler.FlowExecutionException.class,
                () -> service.validateStepReferences(fields, vars, "loginStep", 1, "flow1")
        );
        // The suggestion detail is in getReason() (the first message parameter)
        assertTrue(ex.getReason().contains("Username") || ex.getReason().contains("Did you mean"),
                "Expected suggestion about 'Username' in reason, got: " + ex.getReason());
    }

    @Test
    @DisplayName("validateStepReferences with null field list does not throw")
    void validateStepReferences_nullFieldList_noException() {
        Map<String, FlowVariableDefinition> vars = Map.of(
                "x", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null)
        );
        assertDoesNotThrow(() -> service.validateStepReferences(null, vars, "step", 1, "flow"));
    }

    @Test
    @DisplayName("validateStepReferences with null fields in list does not throw")
    void validateStepReferences_nullFieldsInList_noException() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        List<String> fields = Arrays.asList(null, null, null);
        assertDoesNotThrow(() -> service.validateStepReferences(fields, vars, "step", 1, "flow"));
    }

    @Test
    @DisplayName("validateStepReferences with empty/no-hash fields does not throw")
    void validateStepReferences_noHashInFields_noException() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        List<String> fields = Arrays.asList("just text", "more text", "");
        assertDoesNotThrow(() -> service.validateStepReferences(fields, vars, "step", 1, "flow"));
    }

    @Test
    @DisplayName("validateStepReferences with null vars map treats all refs as undefined")
    void validateStepReferences_nullVarsMap_anyRefThrows() {
        List<String> fields = Arrays.asList("#someVar");
        GlobalExceptionHandler.FlowExecutionException ex = assertThrows(
                GlobalExceptionHandler.FlowExecutionException.class,
                () -> service.validateStepReferences(fields, null, "step", 1, "flow")
        );
        assertNotNull(ex);
    }

    // ── End-to-end flow: generate then resolve ────────────────────────────────

    @Test
    @DisplayName("generated values can be resolved in step fields end-to-end")
    void endToEnd_generateThenResolve() {
        Map<String, FlowVariableDefinition> defs = new HashMap<>();
        defs.put("email_prefix", new FlowVariableDefinition(VariableType.STRING, 8, VariableCharset.LETTERS, null, null));
        defs.put("pin", new FlowVariableDefinition(VariableType.NUMBER, 4, VariableCharset.DIGITS, null, null));

        Map<String, String> runtime = service.generateRuntimeValues(defs, "testFlow");

        // Verify all variables generated
        assertEquals(2, runtime.size());
        assertNotNull(runtime.get("email_prefix"));
        assertNotNull(runtime.get("pin"));

        // Verify structure
        assertTrue(runtime.get("email_prefix").matches("[A-Za-z]{8}"),
                "email_prefix should be 8 letters: " + runtime.get("email_prefix"));
        assertTrue(runtime.get("pin").matches("\\d{4}"),
                "pin should be 4 digits: " + runtime.get("pin"));

        // Resolve in step field
        String stepValue = "#email_prefix@example.com";
        String resolved = service.resolveValue(stepValue, runtime);
        assertTrue(resolved.endsWith("@example.com"), "Resolved value should end with @example.com: " + resolved);
        assertEquals(8 + "@example.com".length(), resolved.length());

        // Resolve pin in expected value field
        String expectedField = "Your PIN: #pin";
        String resolvedExpected = service.resolveValue(expectedField, runtime);
        assertTrue(resolvedExpected.startsWith("Your PIN: "), "Should start with 'Your PIN: '");
        assertTrue(resolvedExpected.matches("Your PIN: \\d{4}"), "PIN should be 4 digits in: " + resolvedExpected);
    }

    @Test
    @DisplayName("runtime map is consistent: same variable resolves to same value in all fields")
    void endToEnd_sameVariableSameValueAcrossFields() {
        Map<String, FlowVariableDefinition> defs = new HashMap<>();
        defs.put("token", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));

        Map<String, String> runtime = service.generateRuntimeValues(defs, "testFlow");
        String generatedToken = runtime.get("token");

        String field1 = service.resolveValue("Token=#token", runtime);
        String field2 = service.resolveValue("Verify #token here", runtime);

        assertEquals("Token=" + generatedToken, field1);
        assertEquals("Verify " + generatedToken + " here", field2);
    }
}
