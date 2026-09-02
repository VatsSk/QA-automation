package com.testingautomation.testautomation.services.flowService;

import com.testingautomation.testautomation.dto.FlowExecutionContext;
import com.testingautomation.testautomation.dto.FrameNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrameContextManagerTest {

    private FrameContextManager frameContextManager;

    @Mock
    private WebDriver driver;

    @Mock
    private WebDriver.TargetLocator targetLocator;

    private FlowExecutionContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Use a spy to bypass the actual Selenium WebDriverWait logic
        frameContextManager = spy(new FrameContextManager());
        doNothing().when(frameContextManager).switchIntoFrame(any(WebDriver.class), any(FrameNode.class), any(Duration.class));

        when(driver.switchTo()).thenReturn(targetLocator);
        context = new FlowExecutionContext(driver, "flow1");
    }

    @Test
    void test1_normalElement() {
        frameContextManager.ensureFrameContext(context, Collections.emptyList(), Duration.ofSeconds(1));
        
        verify(frameContextManager, never()).switchIntoFrame(any(), any(), any());
        verify(targetLocator, never()).defaultContent();
        assertTrue(context.getCurrentFramePath().isEmpty());
    }

    @Test
    void test2_oneIframe() {
        FrameNode frameA = new FrameNode("#frame", "css", 0, null, null);
        
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        
        verify(frameContextManager, times(1)).switchIntoFrame(driver, frameA, Duration.ofSeconds(1));
        assertEquals(1, context.getCurrentFramePath().size());
        assertEquals(frameA, context.getCurrentFramePath().get(0));
    }

    @Test
    void test3_consecutiveIframeActions() {
        FrameNode frameA = new FrameNode("#frame", "css", 0, null, null);
        
        // Step 1
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        verify(frameContextManager, times(1)).switchIntoFrame(driver, frameA, Duration.ofSeconds(1));
        
        // Step 2
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        // Should NOT switch again
        verify(frameContextManager, times(1)).switchIntoFrame(any(), any(), any());
    }

    @Test
    void test4_nestedIframe() {
        FrameNode frameA = new FrameNode("#outer", "css", 0, null, null);
        FrameNode frameB = new FrameNode("#inner", "css", 0, null, null);
        
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA, frameB), Duration.ofSeconds(1));
        
        InOrder inOrder = inOrder(frameContextManager);
        inOrder.verify(frameContextManager).switchIntoFrame(driver, frameA, Duration.ofSeconds(1));
        inOrder.verify(frameContextManager).switchIntoFrame(driver, frameB, Duration.ofSeconds(1));
        
        assertEquals(2, context.getCurrentFramePath().size());
    }

    @Test
    void test5_nestedToParent() {
        FrameNode frameA = new FrameNode("#outer", "css", 0, null, null);
        FrameNode frameB = new FrameNode("#inner", "css", 0, null, null);
        
        context.setCurrentFramePath(Arrays.asList(frameA, frameB));
        
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        
        verify(targetLocator, times(1)).parentFrame();
        verify(frameContextManager, never()).switchIntoFrame(any(), any(), any());
        assertEquals(1, context.getCurrentFramePath().size());
        assertEquals(frameA, context.getCurrentFramePath().get(0));
    }

    @Test
    void test6_iframeToMain() {
        FrameNode frameA = new FrameNode("#outer", "css", 0, null, null);
        context.setCurrentFramePath(Arrays.asList(frameA));
        
        frameContextManager.ensureFrameContext(context, Collections.emptyList(), Duration.ofSeconds(1));
        
        verify(targetLocator, times(1)).defaultContent();
        verify(frameContextManager, never()).switchIntoFrame(any(), any(), any());
        assertTrue(context.getCurrentFramePath().isEmpty());
    }

    @Test
    void test7_iframeAToIframeB() {
        FrameNode frameA = new FrameNode("#frameA", "css", 0, null, null);
        FrameNode frameB = new FrameNode("#frameB", "css", 0, null, null);
        
        context.setCurrentFramePath(Arrays.asList(frameA));
        
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameB), Duration.ofSeconds(1));
        
        verify(targetLocator, times(1)).defaultContent();
        verify(frameContextManager, times(1)).switchIntoFrame(driver, frameB, Duration.ofSeconds(1));
        assertEquals(1, context.getCurrentFramePath().size());
        assertEquals(frameB, context.getCurrentFramePath().get(0));
    }

    @Test
    void test10_urlNavigation() {
        FrameNode frameA = new FrameNode("#frameA", "css", 0, null, null);
        context.setCurrentFramePath(Arrays.asList(frameA));
        
        frameContextManager.invalidateContext(context);
        
        assertFalse(context.isFrameContextValid());
        assertTrue(context.getCurrentFramePath().isEmpty());
        
        // Next step should trigger defaultContent() and switch
        frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        
        verify(targetLocator, times(1)).defaultContent();
        verify(frameContextManager, times(1)).switchIntoFrame(driver, frameA, Duration.ofSeconds(1));
    }

    @Test
    void test12_missingFrame() {
        FrameNode frameA = new FrameNode("#bad-frame", "css", 0, null, null);
        
        doThrow(new RuntimeException("Selenium timeout")).when(frameContextManager).switchIntoFrame(any(), any(), any());
        
        Exception ex = assertThrows(RuntimeException.class, () -> {
            frameContextManager.ensureFrameContext(context, Arrays.asList(frameA), Duration.ofSeconds(1));
        });
        
        assertTrue(ex.getMessage().contains("FRAME_ACCESS_FAILED"));
        assertFalse(context.isFrameContextValid()); // context should be invalidated on failure
    }
}
