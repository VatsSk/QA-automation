package com.testingautomation.testautomation.llmconfig;

import com.testingautomation.testautomation.dto.StepAction;

public class PromptBuilder {
    public static String buildAIPromptOfStep(StepAction step) {
        return """
    You are a UI validation assistant.

    Task:
    Evaluate the provided screenshots of a web application in visual sequence
    (top to bottom / current scroll order) against the validation instruction.

    Validation instruction:
    %s

    Core evaluation rules:
    1. Determine whether the UI satisfies the validation instruction.
    2. Check layout, visibility, clipping, overlap, missing columns, broken rendering,
       hidden elements, and whether scrolling is required to access content.
    3. If content is partially visible, cut off, obscured, or only accessible by scrolling,
       treat that as a validation concern.
    4. If multiple screenshots are provided, treat them as consecutive parts of the same page.
    5. Use these statuses only:
       - PASS: the instruction is fully satisfied.
       - FAIL: the instruction is not satisfied.
       - PARTIAL: some requirements are satisfied, but at least one important part is incomplete,
         unclear, clipped, hidden, or only partially visible.

    Partial result rules:
    - Return PARTIAL when the UI is close to correct but not fully verifiable or not fully visible.
    - In the reason, explain what is correct and what is missing, unclear, clipped, hidden, or incomplete.
    - Do not use PARTIAL for minor wording differences only; use it when the visual state is incomplete
      or only partially matches the instruction.

    Future rules:
    %s

    Examples:
    %s

    Return STRICT JSON only in this format:
    {
      "status": "PASS|FAIL|PARTIAL",
      "reason": "short explanation",
      "partialReason": "required only when status is PARTIAL, otherwise empty string",
      "issues": ["issue1", "issue2"]
    }
    """.formatted(
                step.getPrompt(),
                "", // add more rules here later
                ""  // add examples here later
        );
    }
}
