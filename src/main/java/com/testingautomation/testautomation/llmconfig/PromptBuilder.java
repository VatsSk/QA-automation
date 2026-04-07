package com.testingautomation.testautomation.llmconfig;

import com.testingautomation.testautomation.dto.StepAction;

public class PromptBuilder {
    public static String buildAIPromptOfStep(StepAction step) {
        return """
        You are a UI validation assistant.
        
        Analyze the provided screenshots of a web application in sequence (top to bottom / current scroll order).

        User validation instruction:
        %s

        Your task:
        1. Determine whether the UI satisfies the validation instruction.
        2. Check layout, visibility, clipping, overlap, missing columns, broken rendering, hidden elements, and whether scrolling is required to access content.
        3. If content appears partially visible or cut off, mention it clearly.
        4. If multiple screenshots are provided, treat them as consecutive parts of the same page.

        Return STRICT JSON only in this format:
        {
          "passed": true/false,
          "reason": "short explanation",
          "issues": ["issue1", "issue2"]
        }
        """.formatted(step.getPrompt());
    }
}
