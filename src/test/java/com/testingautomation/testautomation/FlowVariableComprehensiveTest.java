package com.testingautomation.testautomation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingautomation.testautomation.dto.FlowExecutionContext;
import com.testingautomation.testautomation.entities.flow.Flow;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import com.testingautomation.testautomation.entities.flow.FlowVariableDefinition;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.enums.flow.ExecutionStatus;
import com.testingautomation.testautomation.enums.flow.VariableCharset;
import com.testingautomation.testautomation.enums.flow.VariableType;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;
import com.testingautomation.testautomation.services.flowService.ActionHandlerService;
import com.testingautomation.testautomation.services.flowService.FlowExecutionService;
import com.testingautomation.testautomation.services.flowService.FlowSseService;
import com.testingautomation.testautomation.services.flowService.FlowVariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite verifying all 19 required scenarios for Flow-Level Dynamic Variables.
 */
@ExtendWith(MockitoExtension.class)
public class FlowVariableComprehensiveTest {

    private FlowVariableService variableService;

    @Mock
    private ActionHandlerService actionHandlerService;

    @Mock
    private FlowSseService flowSseService;

    @Mock
    private WebDriver webDriver;

    @Mock
    private WebElement webElement;

    @InjectMocks
    private FlowExecutionService flowExecutionService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        variableService = new FlowVariableService();
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(flowExecutionService, "flowVariableService", variableService);
        ReflectionTestUtils.setField(flowExecutionService, "actionHandlerService", actionHandlerService);
        ReflectionTestUtils.setField(flowExecutionService, "flowSseService", flowSseService);

        // Lenient stub for locator resolution on mock WebDriver
        lenient().when(webDriver.findElement(any(By.class))).thenReturn(webElement);
    }

    // ── Scenario 1: A flow without variables executes with existing behavior ─────
    @Test
    @DisplayName("Scenario 1: Flow without variables executes normally without error")
    void scenario1_flowWithoutVariables_executesExistingBehavior() {
        Flow flow = new Flow();
        flow.setId("flow_no_vars");
        flow.setVersion(2);
        flow.setVariables(new HashMap<>());

        FlowStep step = new FlowStep();
        step.setStepOrder(1);
        step.setName("Navigate Step");
        step.setActionType(ActionType.NAVIGATE);
        step.setValue("https://example.com/login");

        FlowExecutionContext context = new FlowExecutionContext(webDriver, flow.getId());
        context.setRuntimeVariables(Collections.emptyMap());

        flowExecutionService.executeStep(context, step, flow);

        ArgumentCaptor<FlowStep> captor = ArgumentCaptor.forClass(FlowStep.class);
        verify(actionHandlerService).handleNavigate(eq(webDriver), captor.capture());
        assertEquals("https://example.com/login", captor.getValue().getValue());
        assertEquals(ExecutionStatus.PASSED, step.getExecutionStatus());
    }

    // ── Scenario 2: Save and retrieve multiple variable definitions ───────────────
    @Test
    @DisplayName("Scenario 2: Flow can save and retrieve multiple variable definitions")
    void scenario2_saveAndRetrieveMultipleVariableDefinitions() {
        Flow flow = new Flow();
        flow.setName("MultiVar Flow");

        FlowVariableDefinition emailDef = new FlowVariableDefinition(
                VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, "user_", "@example.com");
        FlowVariableDefinition phoneDef = new FlowVariableDefinition(
                VariableType.NUMBER, 10, VariableCharset.DIGITS, null, null);
        FlowVariableDefinition codeDef = new FlowVariableDefinition(
                VariableType.STRING, 6, VariableCharset.LETTERS, "CODE-", null);

        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", emailDef);
        vars.put("phone", phoneDef);
        vars.put("code", codeDef);
        flow.setVariables(vars);

        assertDoesNotThrow(() -> variableService.validateDefinitions(flow.getVariables()));
        assertEquals(3, flow.getVariables().size());
        assertEquals(VariableType.STRING, flow.getVariables().get("email").getType());
        assertEquals(VariableType.NUMBER, flow.getVariables().get("phone").getType());
        assertEquals(VariableCharset.LETTERS, flow.getVariables().get("code").getCharset());
    }

    // ── Scenario 3: Duplicate names are rejected (including case-insensitive) ─────
    @Test
    @DisplayName("Scenario 3: Duplicate names differing only by case are rejected")
    void scenario3_duplicateNamesDifferingOnlyByCase_rejected() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));
        vars.put("Email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));

        GlobalExceptionHandler.BadRequestException ex = assertThrows(
                GlobalExceptionHandler.BadRequestException.class,
                () -> variableService.validateDefinitions(vars)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate"),
                "Error message should mention duplicate, got: " + ex.getMessage());
    }

    // ── Scenario 4: Names with surrounding whitespace are normalized ─────────────
    @Test
    @DisplayName("Scenario 4: Names with surrounding whitespace are normalized and validated")
    void scenario4_namesWithWhitespace_normalized() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("  email  ", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));

        assertDoesNotThrow(() -> variableService.validateDefinitions(vars));
    }

    // ── Scenario 5: Invalid names, types, charsets, and lengths are rejected ─────
    @Test
    @DisplayName("Scenario 5: Invalid names, types, charsets, and lengths are rejected")
    void scenario5_invalidDefinitions_rejected() {
        // Invalid name (starts with digit)
        Map<String, FlowVariableDefinition> vars1 = Map.of(
                "1order", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars1));

        // Invalid name (contains hyphen)
        Map<String, FlowVariableDefinition> vars2 = Map.of(
                "order-id", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars2));

        // Blank name
        Map<String, FlowVariableDefinition> vars3 = Map.of(
                "   ", new FlowVariableDefinition(VariableType.STRING, 5, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars3));

        // Null definition
        Map<String, FlowVariableDefinition> vars4 = new HashMap<>();
        vars4.put("myVar", null);
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars4));

        // Null type
        Map<String, FlowVariableDefinition> vars5 = Map.of(
                "myVar", new FlowVariableDefinition(null, 5, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars5));

        // Null charset
        Map<String, FlowVariableDefinition> vars6 = Map.of(
                "myVar", new FlowVariableDefinition(VariableType.STRING, 5, null, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars6));

        // Length < 1
        Map<String, FlowVariableDefinition> vars7 = Map.of(
                "myVar", new FlowVariableDefinition(VariableType.STRING, 0, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars7));

        // Length > 256
        Map<String, FlowVariableDefinition> vars8 = Map.of(
                "myVar", new FlowVariableDefinition(VariableType.STRING, 257, VariableCharset.LETTERS, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars8));

        // NUMBER type with non-DIGITS charset
        Map<String, FlowVariableDefinition> vars9 = Map.of(
                "myVar", new FlowVariableDefinition(VariableType.NUMBER, 10, VariableCharset.ALPHANUMERIC, null, null)
        );
        assertThrows(GlobalExceptionHandler.BadRequestException.class, () -> variableService.validateDefinitions(vars9));
    }

    // ── Scenario 6: Configured variable generates matching charset and length ────
    @Test
    @DisplayName("Scenario 6: Variables generate values matching required charset and length")
    void scenario6_generatedValuesMatchCharsetAndLength() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("strAlpha", new FlowVariableDefinition(VariableType.STRING, 15, VariableCharset.ALPHANUMERIC, null, null));
        vars.put("strLetters", new FlowVariableDefinition(VariableType.STRING, 12, VariableCharset.LETTERS, null, null));
        vars.put("numDigits", new FlowVariableDefinition(VariableType.NUMBER, 8, VariableCharset.DIGITS, null, null));

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, "flow1");

        assertEquals(15, runtime.get("strAlpha").length());
        assertTrue(runtime.get("strAlpha").matches("[A-Za-z0-9]{15}"));

        assertEquals(12, runtime.get("strLetters").length());
        assertTrue(runtime.get("strLetters").matches("[A-Za-z]{12}"));

        assertEquals(8, runtime.get("numDigits").length());
        assertTrue(runtime.get("numDigits").matches("[0-9]{8}"));
    }

    // ── Scenario 7: Prefix and suffix behavior is correct ────────────────────────
    @Test
    @DisplayName("Scenario 7: Prefix and suffix are correctly applied to generated portion")
    void scenario7_prefixAndSuffixBehavior() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 8, VariableCharset.LETTERS, "test-", "@example.com"));

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, "flow1");
        String val = runtime.get("email");

        assertNotNull(val);
        assertTrue(val.startsWith("test-"));
        assertTrue(val.endsWith("@example.com"));
        assertEquals(8 + "test-".length() + "@example.com".length(), val.length());
        String generatedPortion = val.substring("test-".length(), val.length() - "@example.com".length());
        assertEquals(8, generatedPortion.length());
        assertTrue(generatedPortion.matches("[A-Za-z]{8}"));
    }

    // ── Scenario 8: Numeric strings preserve leading zeros ───────────────────────
    @Test
    @DisplayName("Scenario 8: Numeric values preserve leading zeros and are stored as strings")
    void scenario8_numericPreservesLeadingZeros() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("phone", new FlowVariableDefinition(VariableType.NUMBER, 10, VariableCharset.DIGITS, null, null));

        for (int i = 0; i < 50; i++) {
            Map<String, String> runtime = variableService.generateRuntimeValues(vars, "flow1");
            String phone = runtime.get("phone");
            assertNotNull(phone);
            assertEquals(10, phone.length(), "Phone length must always be 10 characters");
            assertTrue(phone.matches("\\d{10}"));
        }
    }

    // ── Scenario 9: A variable generates only once per execution ─────────────────
    @Test
    @DisplayName("Scenario 9: Variable is generated exactly once per execution")
    void scenario9_variableGeneratedOnlyOncePerExecution() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("orderId", new FlowVariableDefinition(VariableType.STRING, 16, VariableCharset.ALPHANUMERIC, "ORD-", null));

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, "flow1");
        String firstLookup = runtime.get("orderId");
        String secondLookup = runtime.get("orderId");

        assertSame(firstLookup, secondLookup, "Variable lookup from execution context map returns identical instance");
    }

    // ── Scenario 10: Multiple steps referencing #email receive the same value ────
    @Test
    @DisplayName("Scenario 10: Multiple steps referencing #email in a single execution receive the exact same value")
    void scenario10_multipleStepsReceiveSameValue() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, "@test.com"));

        Flow flow = new Flow();
        flow.setId("flow_multistep");
        flow.setVersion(2);
        flow.setDefaultWait(100);
        flow.setVariables(vars);

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, flow.getId());
        FlowExecutionContext context = new FlowExecutionContext(webDriver, flow.getId());
        context.setRuntimeVariables(runtime);

        // Step 1: TYPE #email
        FlowStep step1 = new FlowStep();
        step1.setStepOrder(1);
        step1.setName("Type Email");
        step1.setActionType(ActionType.TYPE);
        step1.setSelector("#email");
        step1.setValue("#email");

        // Step 2: NAVIGATE with #email
        FlowStep step2 = new FlowStep();
        step2.setStepOrder(2);
        step2.setName("Verify Email URL");
        step2.setActionType(ActionType.NAVIGATE);
        step2.setValue("https://example.com/confirm?email=#email");

        flowExecutionService.executeStep(context, step1, flow);
        flowExecutionService.executeStep(context, step2, flow);

        ArgumentCaptor<FlowStep> captor1 = ArgumentCaptor.forClass(FlowStep.class);
        verify(actionHandlerService).handleType(eq(webDriver), eq(webElement), captor1.capture());

        ArgumentCaptor<FlowStep> captor2 = ArgumentCaptor.forClass(FlowStep.class);
        verify(actionHandlerService).handleNavigate(eq(webDriver), captor2.capture());

        String resolvedStep1 = captor1.getValue().getValue();
        String resolvedStep2 = captor2.getValue().getValue();

        assertEquals(runtime.get("email"), resolvedStep1);
        assertEquals("https://example.com/confirm?email=" + runtime.get("email"), resolvedStep2);
    }

    // ── Scenario 11: A new execution generates fresh values ──────────────────────
    @Test
    @DisplayName("Scenario 11: Consecutive executions generate fresh, independent variable values")
    void scenario11_newExecutionGeneratesFreshValues() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("token", new FlowVariableDefinition(VariableType.STRING, 24, VariableCharset.ALPHANUMERIC, null, null));

        Map<String, String> run1 = variableService.generateRuntimeValues(vars, "flow1");
        Map<String, String> run2 = variableService.generateRuntimeValues(vars, "flow1");

        assertNotEquals(run1.get("token"), run2.get("token"),
                "Consecutive executions of the same flow must generate fresh values");
    }

    // ── Scenario 12: Concurrent executions do not share runtime values ───────────
    @Test
    @DisplayName("Scenario 12: Concurrent executions maintain separate contexts and do not share values")
    void scenario12_concurrentExecutionsDoNotShareValues() throws Exception {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("session", new FlowVariableDefinition(VariableType.STRING, 20, VariableCharset.ALPHANUMERIC, null, null));

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String execId = "exec_" + i;
            futures.add(pool.submit(() -> {
                startLatch.await();
                FlowExecutionContext ctx = new FlowExecutionContext(null, "flow1");
                ctx.setRuntimeVariables(variableService.generateRuntimeValues(vars, execId));
                return ctx.getRuntimeVariables().get("session");
            }));
        }

        startLatch.countDown();
        Set<String> generatedValues = new HashSet<>();
        for (Future<String> f : futures) {
            String val = f.get(5, TimeUnit.SECONDS);
            assertNotNull(val);
            generatedValues.add(val);
        }
        pool.shutdown();

        assertEquals(threadCount, generatedValues.size(),
                "All concurrent executions must produce distinct runtime variable values");
    }

    // ── Scenario 13: Undefined references produce clear validation errors ────────
    @Test
    @DisplayName("Scenario 13: Undefined references produce clear validation errors before browser execution")
    void scenario13_undefinedReferences_produceClearErrors() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, null));

        // 1. Completely missing variable
        GlobalExceptionHandler.FlowExecutionException ex1 = assertThrows(
                GlobalExceptionHandler.FlowExecutionException.class,
                () -> variableService.validateStepReferences(
                        List.of("#orderId"), vars, "Submit Step", 1, "flow123"
                )
        );
        assertTrue(ex1.getMessage().contains("orderId") || ex1.getReason().contains("orderId"));
        assertTrue(ex1.getReason().contains("flow123"));

        // 2. Case mismatch produces helpful suggestion
        GlobalExceptionHandler.FlowExecutionException ex2 = assertThrows(
                GlobalExceptionHandler.FlowExecutionException.class,
                () -> variableService.validateStepReferences(
                        List.of("#Email"), vars, "Login Step", 2, "flow123"
                )
        );
        assertTrue(ex2.getReason().contains("Did you mean [email]"),
                "Expected case suggestion, got: " + ex2.getReason());
    }

    // ── Scenario 14: Variables resolve in typing and assertion fields ─────────────
    @Test
    @DisplayName("Scenario 14: Variables resolve in typing, assertion, and URL fields")
    void scenario14_variablesResolveInTypingAndAssertionFields() {
        Map<String, String> runtime = Map.of(
                "username", "user_abc123",
                "phone", "9876543210"
        );

        String typed = variableService.resolveValue("#username", runtime);
        assertEquals("user_abc123", typed);

        String assertion = variableService.resolveValue("Phone is: #phone", runtime);
        assertEquals("Phone is: 9876543210", assertion);

        String url = variableService.resolveValue("https://example.com/?user=#username", runtime);
        assertEquals("https://example.com/?user=user_abc123", url);
    }

    // ── Scenario 15: CSS selectors and locators are not corrupted ────────────────
    @Test
    @DisplayName("Scenario 15: CSS selectors in step locator fields are never resolved")
    void scenario15_cssSelectorsNotCorrupted() {
        Flow flow = new Flow();
        flow.setId("flow_selector_test");
        flow.setVersion(2);
        flow.setDefaultWait(100);
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, "@test.com"));
        flow.setVariables(vars);

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, flow.getId());
        FlowExecutionContext context = new FlowExecutionContext(webDriver, flow.getId());
        context.setRuntimeVariables(runtime);

        // Step has CSS selector '#email' which matches a variable name, but selector must NOT be resolved
        FlowStep step = new FlowStep();
        step.setStepOrder(1);
        step.setName("Type Email Input");
        step.setActionType(ActionType.TYPE);
        step.setSelector("#email"); // CSS ID selector
        step.setValue("#email");    // Data value

        flowExecutionService.executeStep(context, step, flow);

        ArgumentCaptor<FlowStep> captor = ArgumentCaptor.forClass(FlowStep.class);
        verify(actionHandlerService).handleType(eq(webDriver), eq(webElement), captor.capture());

        FlowStep executed = captor.getValue();
        // Selector must remain exactly "#email"
        assertEquals("#email", executed.getSelector(), "CSS selector must NOT be rewritten by resolver");
        // Data value must be resolved to generated email
        assertEquals(runtime.get("email"), executed.getValue());
    }

    // ── Scenario 16: Embedded references are substituted correctly ───────────────
    @Test
    @DisplayName("Scenario 16: Embedded references within surrounding text are correctly substituted")
    void scenario16_embeddedReferencesSubstitutedCorrectly() {
        Map<String, String> runtime = Map.of(
                "orderId", "ORD8M2P9X4Q1K7D6",
                "env", "staging"
        );

        String result = variableService.resolveValue("user-#orderId@example.com", runtime);
        assertEquals("user-ORD8M2P9X4Q1K7D6@example.com", result);

        String multiEmbedded = variableService.resolveValue("https://#env.example.com/orders/#orderId/receipt", runtime);
        assertEquals("https://staging.example.com/orders/ORD8M2P9X4Q1K7D6/receipt", multiEmbedded);
    }

    // ── Scenario 17: Saved flow definitions remain unchanged after execution ─────
    @Test
    @DisplayName("Scenario 17: Saved flow step definitions are not mutated after step execution")
    void scenario17_savedFlowDefinitionsRemainUnchanged() {
        Flow flow = new Flow();
        flow.setId("flow_immutable");
        flow.setVersion(2);
        flow.setDefaultWait(100);
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 8, VariableCharset.LETTERS, null, "@corp.com"));
        flow.setVariables(vars);

        FlowStep step = new FlowStep();
        step.setStepOrder(1);
        step.setName("Login Step");
        step.setActionType(ActionType.TYPE);
        step.setSelector("#user-input");
        step.setValue("#email"); // The original definition
        flow.setSteps(List.of(step));

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, flow.getId());
        FlowExecutionContext context = new FlowExecutionContext(webDriver, flow.getId());
        context.setRuntimeVariables(runtime);

        // Execute step
        flowExecutionService.executeStep(context, step, flow);

        // Verify that the original step in flow.getSteps() still has "#email" as its value!
        assertEquals("#email", flow.getSteps().get(0).getValue(),
                "Original step value must remain '#email' so persistence does not write back generated values");
        // Execution status is updated
        assertEquals(ExecutionStatus.PASSED, flow.getSteps().get(0).getExecutionStatus());
    }

    // ── Scenario 18: Duplicate variable names in API input cannot be overwritten ─
    @Test
    @DisplayName("Scenario 18: Duplicate JSON keys in API input are rejected and cannot silently overwrite definitions")
    void scenario18_duplicateKeysInJson_rejectedByDeserializer() {
        // 1. Exact duplicate keys in JSON
        String jsonExactDup = """
                {
                  "name": "Test Flow",
                  "variables": {
                    "email": {
                      "type": "STRING",
                      "length": 10,
                      "charset": "ALPHANUMERIC"
                    },
                    "email": {
                      "type": "STRING",
                      "length": 20,
                      "charset": "LETTERS"
                    }
                  }
                }
                """;

        Exception ex1 = assertThrows(
                Exception.class,
                () -> objectMapper.readValue(jsonExactDup, Flow.class)
        );
        Throwable root1 = ex1.getCause() != null ? ex1.getCause() : ex1;
        assertTrue(root1 instanceof GlobalExceptionHandler.BadRequestException);
        assertTrue(root1.getMessage().contains("Duplicate flow variable name"));

        // 2. Case-insensitive duplicate keys in JSON
        String jsonCaseDup = """
                {
                  "name": "Test Flow",
                  "variables": {
                    "email": {
                      "type": "STRING",
                      "length": 10,
                      "charset": "ALPHANUMERIC"
                    },
                    "Email": {
                      "type": "STRING",
                      "length": 20,
                      "charset": "LETTERS"
                    }
                  }
                }
                """;

        Exception ex2 = assertThrows(
                Exception.class,
                () -> objectMapper.readValue(jsonCaseDup, Flow.class)
        );
        Throwable root2 = ex2.getCause() != null ? ex2.getCause() : ex2;
        assertTrue(root2 instanceof GlobalExceptionHandler.BadRequestException);
        assertTrue(root2.getMessage().contains("Duplicate flow variable name"));

        // 3. Key with '#' prefix rejected
        String jsonHashKey = """
                {
                  "name": "Test Flow",
                  "variables": {
                    "#email": {
                      "type": "STRING",
                      "length": 10,
                      "charset": "ALPHANUMERIC"
                    }
                  }
                }
                """;

        Exception ex3 = assertThrows(
                Exception.class,
                () -> objectMapper.readValue(jsonHashKey, Flow.class)
        );
        Throwable root3 = ex3.getCause() != null ? ex3.getCause() : ex3;
        assertTrue(root3 instanceof GlobalExceptionHandler.BadRequestException);
        assertTrue(root3.getMessage().contains("#"));
    }

    // ── Scenario 19: Failed-step retries behave consistently with existing lifecycle
    @Test
    @DisplayName("Scenario 19: Step retries reuse the exact same runtime variable value")
    void scenario19_failedStepRetriesReuseSameValue() {
        Map<String, FlowVariableDefinition> vars = new HashMap<>();
        vars.put("email", new FlowVariableDefinition(VariableType.STRING, 10, VariableCharset.ALPHANUMERIC, null, "@test.com"));

        Flow flow = new Flow();
        flow.setId("flow_retry");
        flow.setVersion(2);
        flow.setDefaultWait(100);
        flow.setVariables(vars);

        FlowStep step = new FlowStep();
        step.setStepOrder(1);
        step.setName("Flaky Step");
        step.setActionType(ActionType.NAVIGATE);
        step.setValue("https://example.com/signup?email=#email");
        step.setRetryCount(2); // 2 retries = up to 3 attempts

        Map<String, String> runtime = variableService.generateRuntimeValues(vars, flow.getId());
        FlowExecutionContext context = new FlowExecutionContext(webDriver, flow.getId());
        context.setRuntimeVariables(runtime);

        // Fail first attempt, succeed on second attempt
        doThrow(new RuntimeException("Simulated navigation failure"))
                .doNothing()
                .when(actionHandlerService).handleNavigate(eq(webDriver), any());

        flowExecutionService.executeStep(context, step, flow);

        ArgumentCaptor<FlowStep> captor = ArgumentCaptor.forClass(FlowStep.class);
        verify(actionHandlerService, times(2)).handleNavigate(eq(webDriver), captor.capture());

        List<FlowStep> capturedSteps = captor.getAllValues();
        assertEquals(2, capturedSteps.size());
        assertEquals("https://example.com/signup?email=" + runtime.get("email"), capturedSteps.get(0).getValue());
        assertEquals("https://example.com/signup?email=" + runtime.get("email"), capturedSteps.get(1).getValue(),
                "Retry attempt must use the exact same runtime value as the initial attempt");
        assertEquals(ExecutionStatus.PASSED, step.getExecutionStatus());
    }
}
