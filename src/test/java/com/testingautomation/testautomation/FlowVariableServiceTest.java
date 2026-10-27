package com.testingautomation.testautomation;

import com.testingautomation.testautomation.entities.flow.FlowVariableDefinition;
import com.testingautomation.testautomation.enums.flow.VariableCharset;
import com.testingautomation.testautomation.enums.flow.VariableType;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.flowService.FlowVariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FlowVariableService covering validation, generation, and resolution.
 */
class FlowVariableServiceTest {

    private FlowVariableService service;

    @BeforeEach
    void setUp() {
        service = new FlowVariableService();
    }

    // ── Validation tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("null or empty variable map passes validation without error")
    void validateDefinitions_nullOrEmptyMap_noException() {
        assertDoesNotThrow(() -> service.validateDefinitions(null));
        assertDoesNotThrow(() -> service.validateDefinitions(Collections.emptyMap()));
    }

    @Test
    @DisplayName("valid STRING variable with ALPHANUMERIC charset passes validation")
    void validateDefinitions_validStringVariable_passes() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));
        assertDoesNotThrow(() -> service.validateDefinitions(vars));
    }

    @Test
    @DisplayName("valid NUMBER variable with DIGITS charset passes validation")
    void validateDefinitions_validNumberVariable_passes() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("orderId", new FlowVariableDefinition(VariableType.NUMBER, 8, VariableCharset.DIGITS, "ORD-", null));
        assertDoesNotThrow(() -> service.validateDefinitions(vars));
    }

    @Test
    @DisplayName("variable name starting with digit fails validation")
    void validateDefinitions_nameStartingWithDigit_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("1invalid", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.LETTERS, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("invalid") || ex.getMessage().contains("1invalid"),
                "Expected message about invalid name, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("variable name with hyphen fails validation")
    void validateDefinitions_nameWithHyphen_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("my-var", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.LETTERS, null, null));
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> service.validateDefinitions(vars));
    }

    @Test
    @DisplayName("null type in definition fails validation")
    void validateDefinitions_nullType_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(null, 5, VariableCharset.ALPHANUMERIC, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("type"), "Expected message about missing type, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("null charset in definition fails validation")
    void validateDefinitions_nullCharset_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, 5, null, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("charset"), "Expected message about missing charset, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("length of zero fails validation")
    void validateDefinitions_zeroLength_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, 0, VariableCharset.ALPHANUMERIC, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("length") || ex.getMessage().contains("Length"),
                "Expected message about invalid length, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("null length fails validation")
    void validateDefinitions_nullLength_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, null, VariableCharset.ALPHANUMERIC, null, null));
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> service.validateDefinitions(vars));
    }

    @Test
    @DisplayName("length exceeding 256 fails validation")
    void validateDefinitions_tooLargeLength_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, 257, VariableCharset.ALPHANUMERIC, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("256") || ex.getMessage().contains("maximum"),
                "Expected message about max length, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("NUMBER type with ALPHANUMERIC charset fails validation")
    void validateDefinitions_numberTypeWithAlphanumericCharset_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myNum", new FlowVariableDefinition(VariableType.NUMBER, 5, VariableCharset.ALPHANUMERIC, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("NUMBER") || ex.getMessage().contains("DIGITS"),
                "Expected message about NUMBER+DIGITS constraint, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("NUMBER type with LETTERS charset fails validation")
    void validateDefinitions_numberTypeWithLettersCharset_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myNum", new FlowVariableDefinition(VariableType.NUMBER, 5, VariableCharset.LETTERS, null, null));
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> service.validateDefinitions(vars));
    }

    @Test
    @DisplayName("case-insensitive duplicate names fail validation")
    void validateDefinitions_caseInsensitiveDuplicate_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null));
        vars.put("MYVAR", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null));
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate"),
                "Expected duplicate error message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("null definition value fails validation")
    void validateDefinitions_nullDefinition_throwsBadRequest() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("myVar", null);
        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> service.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().contains("null"),
                "Expected null definition error, got: " + ex.getMessage());
    }

    // ── Generation tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generates correct length random value")
    void generateRuntimeValues_correctLength() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("token", new FlowVariableDefinition(VariableType.STRING, 12, VariableCharset.ALPHANUMERIC, null, null));
        Map<String, String> result = service.generateRuntimeValues(vars, "flow1");
        assertNotNull(result.get("token"));
        assertEquals(12, result.get("token").length());
    }

    @Test
    @DisplayName("prefix and suffix are included in generated value")
    void generateRuntimeValues_prefixSuffixIncluded() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("orderId", new FlowVariableDefinition(VariableType.NUMBER, 6, VariableCharset.DIGITS, "ORD-", "-END"));
        Map<String, String> result = service.generateRuntimeValues(vars, "flow1");
        String val = result.get("orderId");
        assertNotNull(val);
        assertTrue(val.startsWith("ORD-"), "Value should start with prefix: " + val);
        assertTrue(val.endsWith("-END"), "Value should end with suffix: " + val);
        assertEquals(6 + 4 + 4, val.length()); // prefix(4) + generated(6) + suffix(4)
    }

    @Test
    @DisplayName("DIGITS charset produces only digit characters in random portion")
    void generateRuntimeValues_digitsCharset_onlyDigits() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("pin", new FlowVariableDefinition(VariableType.NUMBER, 10, VariableCharset.DIGITS, null, null));
        Map<String, String> result = service.generateRuntimeValues(vars, "flow1");
        String val = result.get("pin");
        assertNotNull(val);
        assertTrue(val.matches("\\d{10}"), "Expected only digits, got: " + val);
    }

    @Test
    @DisplayName("LETTERS charset produces only letter characters in random portion")
    void generateRuntimeValues_lettersCharset_onlyLetters() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("code", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.LETTERS, null, null));
        Map<String, String> result = service.generateRuntimeValues(vars, "flow1");
        String val = result.get("code");
        assertNotNull(val);
        assertTrue(val.matches("[A-Za-z]{10}"), "Expected only letters, got: " + val);
    }

    @Test
    @DisplayName("empty variables map returns empty unmodifiable map")
    void generateRuntimeValues_emptyVars_returnsEmpty() {
        Map<String, String> result = service.generateRuntimeValues(Collections.emptyMap(), "flow1");
        assertTrue(result.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.put("x", "y"),
                "Result map should be unmodifiable");
    }

    @Test
    @DisplayName("null variables map returns empty unmodifiable map")
    void generateRuntimeValues_nullVars_returnsEmpty() {
        Map<String, String> result = service.generateRuntimeValues(null, "flow1");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("returned runtime map is unmodifiable")
    void generateRuntimeValues_returnedMapIsUnmodifiable() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("x", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.ALPHANUMERIC, null, null));
        Map<String, String> result = service.generateRuntimeValues(vars, "flow1");
        assertThrows(UnsupportedOperationException.class, () -> result.put("y", "z"),
                "Result map should be unmodifiable");
    }

    // ── Resolution tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveValue replaces known variable reference")
    void resolveValue_knownReference_replaced() {
        Map<String, String> runtime = new HashMap<>();
        runtime.put("username", "testUser42");
        String result = service.resolveValue("Hello #username!", runtime);
        assertEquals("Hello testUser42!", result);
    }

    @Test
    @DisplayName("resolveValue leaves null input as null")
    void resolveValue_nullInput_returnsNull() {
        Map<String, String> runtime = Map.of("x", "val");
        assertNull(service.resolveValue(null, runtime));
    }

    @Test
    @DisplayName("resolveValue returns original if no # in input")
    void resolveValue_noHash_returnsOriginal() {
        Map<String, String> runtime = Map.of("x", "val");
        assertEquals("hello world", service.resolveValue("hello world", runtime));
    }

    @Test
    @DisplayName("resolveValue leaves unknown reference as-is")
    void resolveValue_unknownReference_leftAsIs() {
        Map<String, String> runtime = new HashMap<>();
        runtime.put("known", "value");
        String result = service.resolveValue("data #unknown more", runtime);
        assertEquals("data #unknown more", result);
    }

    @Test
    @DisplayName("resolveValue handles multiple references in same string")
    void resolveValue_multipleReferences_allReplaced() {
        Map<String, String> runtime = new HashMap<>();
        runtime.put("first", "John");
        runtime.put("last", "Doe");
        String result = service.resolveValue("#first #last", runtime);
        assertEquals("John Doe", result);
    }

    @Test
    @DisplayName("resolveValue with empty runtime map returns original value")
    void resolveValue_emptyRuntimeMap_returnsOriginal() {
        String result = service.resolveValue("#username", Collections.emptyMap());
        assertEquals("#username", result);
    }

    @Test
    @DisplayName("resolveValue with replacement containing $ and backslash does not corrupt output")
    void resolveValue_replacementWithSpecialRegexChars_handledCorrectly() {
        Map<String, String> runtime = new HashMap<>();
        runtime.put("val", "$100.00 \\n");
        String result = service.resolveValue("Price: #val", runtime);
        assertEquals("Price: $100.00 \\n", result);
    }

    // ── VARIABLE_REF_PATTERN tests ────────────────────────────────────────────

    @Test
    @DisplayName("VARIABLE_REF_PATTERN matches valid variable references")
    void variableRefPattern_validRefs_matched() {
        Pattern p = FlowVariableService.VARIABLE_REF_PATTERN;
        assertTrue(p.matcher("#myVar").find());
        assertTrue(p.matcher("#Var123").find());
        assertTrue(p.matcher("#a_b_c").find());
    }

    @Test
    @DisplayName("VARIABLE_REF_PATTERN does not match number-starting names")
    void variableRefPattern_invalidRef_notMatched() {
        Pattern p = FlowVariableService.VARIABLE_REF_PATTERN;
        assertFalse(p.matcher("#1bad").find());
        // CSS id selector — no letter after #
        assertFalse(p.matcher("#123").find());
    }
}
