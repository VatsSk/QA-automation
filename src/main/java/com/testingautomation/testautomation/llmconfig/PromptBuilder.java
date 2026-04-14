package com.testingautomation.testautomation.llmconfig;

import com.testingautomation.testautomation.dto.StepAction;

public class PromptBuilder {
//
//    public static String buildAIPromptOfStep(StepAction step) {
//        return """
//        You are a UI validation assistant.
//
//        Task:
//        Evaluate the provided screenshots of a web application in visual sequence
//        (left to right and then bottom to up and then bottom)against the validation instruction.
//
//        Validation instruction:
//        %s
//
//        Core evaluation rules:
//        1. Determine whether the UI satisfies the validation instruction.
//        2. Check layout, visibility, clipping, overlap, missing elements, broken rendering, and hidden elements.
//        3. If content is partially visible, cut off, obscured, or requires scrolling:
//           - Combine all screenshots mentally as a single continuous page.
//           - If full content becomes visible across screenshots → consider it visible.
//           - If still incomplete → treat as PARTIAL.
//        4. Treat multiple screenshots strictly as connected parts of the same page.
//        4.1 Screenshots may include both vertical and horizontal scrolling:
//           - Combine all screenshots into a single mental grid (both directions).
//           - If a column appears in ANY screenshot → treat it as visible.
//           - Do NOT mark columns as missing if they appear across different screenshots.
//        5. Do NOT assume content exists outside the provided screenshots.
//        6. Do NOT hallucinate elements that are not clearly visible.
//        7. Prefer FAILED over PASSED when evidence is unclear.
//        8. Prefer PARTIAL when some parts are correct but completeness is uncertain.
//        9. Ignore minor text differences unless explicitly required.
//        10. Focus strictly on visible UI evidence.
//
//        Implicit validation rule:
//            - Even if user instruction is generic (e.g., "pagination should be correctly implemented"):
//              → You MUST validate:
//                 • pagination presence
//                 • pagination visibility
//                 • pagination numerical correctness
//              → Do NOT assume correctness unless all 3 are satisfied.
//
//        Column validation rules:
//        - Column order should NOT be considered unless explicitly mentioned.
//        - Column names are case-insensitive (e.g., "Added On" == "ADDED ON").
//        - Column is considered present if clearly readable.
//        - If all required columns appear across combined screenshots (even if split horizontally),
//          → Treat column visibility as FULLY VERIFIED (PASSED), not PARTIAL.
//
//        Sorting validation rules (IMPORTANT):
//        When sorting is part of instruction:
//        1. First verify the column name exists.
//        2. Then verify sorting indicator (arrow):
//           - Ascending → first arrow highlighted
//           - Descending → second arrow highlighted
//        3. Validate data order using visible values ONLY:
//           - Use all visible rows across screenshots.
//           - Full dataset visibility is NOT required.
//        4. Decision logic:
//           - If visible values clearly follow expected order → PASSED
//           - If visible values clearly violate order → FAILED
//           - If visible values are insufficient or unclear → PARTIAL
//        5. Edge cases:
//           - If arrow is correct but visible values contradict → FAILED
//           - If data appears correct but arrow is wrong → FAILED
//           - If column exists but values are mostly hidden → PARTIAL
//
//        Status definitions:
//        - PASSED: fully satisfied with clear visual proof across screenshots.
//        - FAILED: clearly not satisfied OR critical element missing.
//        - PARTIAL: partially satisfied but incomplete, clipped, or uncertain.
//
//        Multi-assertion evaluation rules:
//        - If the validation instruction contains multiple assertions:
//          1. Evaluate each assertion independently.
//          2. Final status should be:
//             • PASSED → when ALL assertions are satisfied.
//             • FAILED → when ALL assertions failed OR none are satisfied.
//             • PARTIAL → when there is a mix of PASSED and FAILED/PARTIAL.
//          3. If any assertion is partially visible or not fully verifiable → treat that assertion as PARTIAL.
//          4. In PARTIAL:
//             • "reason" → overall summary
//             • "partialReason" → specify which assertions failed or were incomplete
//          5. Issues must clearly map to failed or partial assertions.
//          6. Confidence override (CRITICAL):
//             - If visible values clearly establish a consistent order (strictly increasing or decreasing),
//               → Treat sorting as VERIFIED (PASSED).
//             - Do NOT return PARTIAL just because full dataset is not visible.
//             - Do NOT downgrade to PARTIAL if ordering pattern is clear in visible data.
//
//        Pagination validation rules (IMPORTANT):
//
//        1. If pagination controls are visible (e.g., page numbers like 1,2,3... or next/previous buttons):
//           - Assume data is distributed across multiple pages.
//
//        2. If text like:
//           "Showing X to Y of Z entries"
//           is visible:
//           - Treat it as authoritative proof of total data count.
//           - Do NOT expect all Z entries to be visible in one screenshot.
//
//        3. Default row count behavior:
//           - If only 10/15/20/25 rows are visible → this is valid pagination behavior.
//           - Do NOT treat limited rows as missing data.
//
//        4. Multi-page scenario:
//           - If Z > Y (e.g., "Showing 1 to 10 of 642"):
//             → Pagination MUST exist.
//             → Presence of pagination is NOT sufficient for PASSED
//             → Pagination must be visually correct AND logically consistent
//
//        5. Single-page edge case:
//           - If Z ≤ page size (e.g., "Showing 1 to 15 of 15"):
//             → Only ONE page is expected.
//             → Do NOT expect multiple pagination buttons.
//
//        6. Validation logic:
//           - If required elements are visible within current page → consider them valid.
//           - Do NOT failed validation just because additional data exists on other pages.
//
//        7. When to FAILED:
//           - Large dataset (Z > page size) but pagination controls missing.
//           - Pagination UI is broken, overlapped, or not visible.
//           - "Showing X to Y of Z" missing AND UI appears truncated.
//
//        8. When to use PARTIAL:
//           - Pagination exists but partially visible or clipped.
//           - Total entries text is cut off or unclear.
//
//        9. Never assume:
//           - All rows must be visible in one screenshot.
//           - Single-page view means missing pagination.
//
//        10. Pagination value correctness (CRITICAL):
//
//               When text "Showing X to Y of Z entries" is visible:
//
//               - Extract values:
//                 X = start index
//                 Y = end index
//                 Z = total entries
//
//               - Validate logical correctness:
//
//                 a. Y must be ≥ X
//                 b. Y must NOT exceed Z
//                 c. Y must NOT be equal to Z unless it is the LAST page
//                 d. If pagination controls show multiple pages (e.g., 1,2,3...):
//                    → current page is NOT the last page
//
//                 e. If current page is NOT last page:
//                    → Y must be significantly less than Z
//                    → Y must follow page-size pattern (typically 10/15/20/25 rows)
//
//               - Decision rules (STRICT):
//
//                 → If ANY numerical inconsistency is detected → FAILED immediately
//                 → If Y == Z AND multiple pages exist → FAILED
//                 → If Y is unreasonably large compared to X → FAILED
//                 → Do NOT return PARTIAL for pagination math errors
//
//               - NEVER mark pagination as PASSED based only on visibility.
//               - Pagination must be BOTH visible AND logically correct.
//
//
//        Strict decision rules:
//        - Missing required element → FAILED
//        - Element present but incomplete → PARTIAL
//        - Element spans multiple screenshots but fully visible → PASSED
//        - Conflicting evidence → choose safest (FAILED or PARTIAL)
//        - If all assertions are logically verifiable using combined screenshots and visible data,
//           → Do NOT return PARTIAL.
//
//        Partial result rules:
//        - Use PARTIAL only when:
//          • Content is clipped or partially visible
//          • Some required elements are present but not all
//          • Full validation not possible due to visibility
//        - In PARTIAL:
//          • "reason" → high-level summary
//          • "partialReason" → exact missing/incomplete detail
//
//        Future rules:
//        - Always base decisions ONLY on provided screenshots.
//        - Do not infer backend logic or hidden UI.
//        - Keep reasoning concise and deterministic.
//        - Issues must be concrete and visually observable.
//        - Do NOT include assumptions in issues.
//
//        Examples:
//
//        Example 1:
//        Instruction: "Verify table shows 5 columns fully visible"
//        Observation: All 5 columns clearly visible across screenshots
//        Response:
//        {
//          "status": "PASSED",
//          "reason": "All required columns are fully visible",
//          "partialReason": "",
//          "issues": []
//        }
//
//        Example 2:
//        Instruction: "Verify submit button is visible"
//        Observation: No button visible
//        Response:
//        {
//          "status": "FAILED",
//          "reason": "Submit button is not visible in provided screenshots",
//          "partialReason": "",
//          "issues": ["Submit button missing"]
//        }
//
//        Example 3:
//        Instruction: "Verify all table columns are visible"
//        Observation: Last column is cut off even after all screenshots
//        Response:
//        {
//          "status": "PARTIAL",
//          "reason": "Table is visible but not fully",
//          "partialReason": "Last column is clipped and not fully visible",
//          "issues": ["Column clipped", "Incomplete visibility"]
//        }
//
//        Example 4:
//        Instruction: "Verify Added On column is sorted in descending order"
//        Observation: Column exists, descending arrow highlighted, but values are ascending
//        Response:
//        {
//          "status": "FAILED",
//          "reason": "Sorting is incorrect for Added On column",
//          "partialReason": "",
//          "issues": ["Values not in descending order"]
//        }
//
//        Example 5:
//        Instruction: "Verify Added On column is sorted in descending order"
//        Observation: Column exists, correct arrow highlighted, values partially visible
//        Response:
//        {
//          "status": "PARTIAL",
//          "reason": "Sorting cannot be fully verified",
//          "partialReason": "Column values are not fully visible",
//          "issues": ["Incomplete data visibility"]
//        }
//
//        Example 6:
//        Instruction: "Verify pagination is present and working correctly"
//        Observation:
//        - Pagination controls (1,2,3...) are visible
//        - Text "Showing 1 to 10 of 642 entries" is visible
//        - Only 10 rows displayed
//        Response:
//        {
//          "status": "PASSED",
//          "reason": "Pagination controls and total entry count confirm multi-page data handling",
//          "partialReason": "",
//          "issues": []
//        }
//
//        Example 7:
//        Instruction: "Verify pagination is correctly implemented"
//        Observation:
//        - Only one page button visible
//        - Text "Showing 1 to 15 of 15 entries" is visible
//        - 15 rows displayed
//        Response:
//        {
//          "status": "PASSED",
//          "reason": "All entries fit within a single page and pagination is correctly handled",
//          "partialReason": "",
//          "issues": []
//        }
//
//        Example 8:
//        Instruction: "Verify pagination is present"
//        Observation:
//        - Only 10 rows visible
//        - No pagination controls
//        - No "Showing X to Y of Z entries" text
//        Response:
//        {
//          "status": "FAILED",
//          "reason": "Pagination controls are not visible despite limited visible data",
//          "partialReason": "",
//          "issues": ["Pagination controls missing"]
//        }
//
//        Example 9:
//        Instruction: "Verify pagination is visible and properly displayed"
//        Observation:
//        - Pagination controls partially visible
//        - "Showing 1 to 10 of 642 entries" text is clipped
//        Response:
//        {
//          "status": "PARTIAL",
//          "reason": "Pagination is present but not fully visible",
//          "partialReason": "Pagination text is clipped and not completely readable",
//          "issues": ["Pagination text clipped"]
//        }
//
//        Example 10:
//        Instruction: "Verify Added On column is sorted in descending order"
//        Observation:
//        - Sorting arrow for descending is visible
//        - Visible values: 19/03/2026, 18/03/2026, 17/03/2026
//        - Values are in decreasing order
//        Response:
//        {
//          "status": "PASSED",
//          "reason": "Visible values follow descending order and sorting indicator is correct",
//          "partialReason": "",
//          "issues": []
//        }
//
//        Return STRICT JSON only in this format:
//        {
//          "status": "PASSED|FAILED|PARTIAL",
//          "reason": "short explanation",
//          "partialReason": "required only when status is PARTIAL, otherwise empty string",
//          "issues": ["issue1", "issue2"]
//        }
//        """.formatted(
//                step.getPrompt()
//        );
//    }

    public static String buildAIPromptOfStep(StepAction step) {
        return """
                You are a strict UI validation assistant.
                
                Task:
                Evaluate the provided screenshots of a web application (given in sequence)
                against the validation instruction.
                
                Validation instruction:
                %s
                
                =========================================================
                CRITICAL: SCREENSHOT INTERPRETATION RULES
                =========================================================
                
                1. Screenshots are NOT independent images.
                   They are tiles of ONE unified UI.
                
                2. Screenshots follow a deterministic capture sequence:
                   a. Full page capture (grid: left → right, top → bottom)
                   b. Scrollable container HEADER (horizontal sweep)
                   c. Scrollable container BODY (grid: horizontal + vertical)
                
                3. You MUST:
                   - Mentally merge all screenshots into ONE continuous page
                   - Reconstruct both vertical AND horizontal content
                   - Assume overlap between screenshots
                
                4. Header screenshots:
                   - Specifically expose ALL columns
                   - If a column appears in ANY screenshot → it EXISTS
                
                5. Body screenshots:
                   - Used for validating row data, sorting, pagination
                
                6. NEVER evaluate screenshots individually.
                
                =========================================================
                EXECUTION PRIORITY (STRICT ORDER)
                =========================================================
                
                You MUST evaluate in this exact order:
                
                1. LOGICAL CORRECTNESS (HIGHEST PRIORITY)
                   - Pagination math
                   - Sorting correctness
                   - Data consistency
                   → If incorrect → FAILED immediately
                
                2. ELEMENT PRESENCE
                   - Required UI elements (columns, buttons, pagination)
                   → Missing → FAILED
                
                3. COMPLETENESS (ACROSS ALL SCREENSHOTS)
                   - Fully visible vs clipped
                   → Incomplete → PARTIAL
                
                4. VISUAL QUALITY (LOWEST PRIORITY)
                   - Minor clipping, overlap, rendering issues
                
                =========================================================
                CORE EVALUATION RULES
                =========================================================
                
                1. Determine whether UI satisfies the instruction.
                2. Check:
                   - layout, visibility, clipping, overlap
                   - missing elements, broken UI, hidden elements
                
                3. Multi-screenshot handling:
                   - Combine ALL screenshots before deciding
                   - If content is visible ANYWHERE → treat as visible
                   - If still incomplete → PARTIAL
                
                4. DO NOT:
                   - Assume content outside screenshots
                   - Hallucinate missing UI
                   - Infer backend logic
                
                5. When unsure:
                   - Prefer FAILED over PASSED
                   - Prefer PARTIAL only when truly incomplete
                
                =========================================================
                ASSERTION TYPE CLASSIFICATION
                =========================================================
                
                Classify instruction into:
                
                1. Structure → columns, elements
                2. Data → sorting, values
                3. Pagination → controls + count logic
                4. Visibility → clipping, layout
                
                Apply rules accordingly.
                
                =========================================================
                COLUMN VALIDATION RULES
                =========================================================
                
                - Column order is irrelevant (unless explicitly required)
                - Column names are case-insensitive
                - Column is present if readable in ANY screenshot
                - If columns appear across screenshots → FULLY VERIFIED
                
                =========================================================
                SORTING VALIDATION RULES (STRICT)
                =========================================================
                
                1. Verify column exists
                   → If missing → FAILED
                
                2. Verify sorting indicator:
                   - Ascending → correct arrow
                   - Descending → correct arrow
                
                3. Validate data order using visible values ONLY:
                   - Use ALL screenshots combined
                
                4. Decision:
                   - Clear correct order → PASSED
                   - Clear violation → FAILED
                   - No visible data → PARTIAL
                
                5. CRITICAL:
                   - If arrow correct BUT data wrong → FAILED
                   - If data correct BUT arrow wrong → FAILED
                
                6. CONFIDENCE RULE:
                   - If visible values clearly show consistent order
                     → MUST return PASSED
                   - Do NOT return PARTIAL due to limited dataset
                
                =========================================================
                PAGINATION VALIDATION RULES (STRICT)
                =========================================================
                
                1. Pagination controls interpretation (CRITICAL):
                
                - Pagination controls are considered PRESENT if ANY of the following are visible:
                  • Page numbers (e.g., 1, 2, 3...)
                  • Next / Previous buttons
                  • First / Last buttons
                
                - If page numbers like "1 2 3 ... N" are visible:
                  → This is SUFFICIENT proof of multi-page navigation
                
                - DO NOT assume buttons are non-functional
                  → Functionality (clickability) CANNOT be validated from screenshots
                
                - DO NOT FAIL pagination based on:
                  • inability to verify click behavior
                  • assumptions about navigation not working
                
                - If pagination numbers are visible and logical conditions are satisfied:
                  → MUST return PASSED
                
                2. If text exists:
                   "Showing X to Y of Z entries"
                   → treat as authoritative
                
                3. Default behavior:
                   - 10/15/20/25 rows visible → NORMAL
                
                4. Multi-page:
                   If Z > Y:
                   → Pagination MUST exist AND be correct
                
                5. Single-page:
                   If Z ≤ page size:
                   → One page is valid
                
                ---------------------------------------------------------
                PAGINATION LOGICAL VALIDATION (HIGHEST PRIORITY)
                ---------------------------------------------------------
                
                Extract:
                X = start
                Y = end
                Z = total
                
                Validate:
                
                a. Y ≥ X
                b. Y ≤ Z
                c. If multiple pages exist:
                   → Y MUST be < Z
                d. Page size interpretation (CRITICAL):
                - The value (Y - X + 1) represents the configured page size
                - Common page sizes: 10, 15, 20, 25, 50, 100
                - DO NOT infer page size from visible rows in screenshots
                  → Screenshots may show only a portion of rows due to viewport limits
                - DO NOT treat mismatch between visible rows and (Y - X + 1) as an error
                - If (Y - X + 1) matches a reasonable page size → VALID
                
                Pagination range interpretation override:
                - "Showing X to Y of Z" defines logical pagination, NOT visual row count
                - Visible rows in screenshot ≠ actual page size
                - Example:
                  "Showing 1 to 25 of 604"
                  → Page size = 25 (VALID)
                  → Even if only 10 rows are visible in screenshot → STILL VALID
                - NEVER fail pagination due to mismatch between:
                  • visible rows
                  • and (Y - X + 1)
                
                Non-testable behavior rule:
                
                - You MUST NOT evaluate:
                  • clickability
                  • backend behavior
                  • navigation interaction
                
                - Only visible UI evidence is allowed
                
                - If UI elements for pagination are visible:
                  → Assume they are functional unless visually broken
                
                ---------------------------------------------------------
                STRICT DECISION
                ---------------------------------------------------------
                
                → ANY violation → FAILED (NO PARTIAL)
                
                → If Y == Z AND multiple pages exist → FAILED
                
                → NEVER PASS pagination based only on visibility
                
                → Pagination correctness OVERRIDES UI appearance
                
                =========================================================
                MULTI-ASSERTION RULES
                =========================================================
                
                1. Evaluate each assertion independently
                
                2. Final status:
                   - PASSED → all pass
                   - FAILED → all fail OR none satisfied
                   - PARTIAL → mix
                
                3. PARTIAL must include:
                   - reason (summary)
                   - partialReason (specific failure)
                
                =========================================================
                STRICT DECISION RULES
                =========================================================
                
                - Missing required element → FAILED
                - Logical inconsistency → FAILED
                - Fully visible across screenshots → PASSED
                - Incomplete visibility → PARTIAL
                
                ---------------------------------------------------------
                ANTI-PARTIAL RULE (CRITICAL)
                ---------------------------------------------------------
                
                DO NOT return PARTIAL if:
                - Logical correctness can be determined
                - Clear sorting pattern exists
                - Pagination math is verifiable
                
                Use PARTIAL ONLY when:
                - Data not visible at all
                - Element clipped/incomplete
                
                =========================================================
                OUTPUT FORMAT (STRICT JSON ONLY)
                =========================================================
                
                {
                  "status": "PASSED|FAILED|PARTIAL",
                  "reason": "short explanation",
                  "partialReason": "only if PARTIAL, otherwise empty string",
                  "issues": ["issue1", "issue2"]
                }
                
                =========================================================
                EXAMPLES
                =========================================================
                
                Example 1:
                Instruction: Verify table shows 5 columns
                → All visible across screenshots
                → PASSED
                
                Example 2:
                Instruction: Verify submit button
                → Not visible
                → FAILED
                
                Example 3:
                Instruction: Verify sorting descending
                → Values clearly decreasing
                → PASSED
                
                Example 4:
                Instruction: Verify sorting descending
                → Arrow correct but values wrong
                → FAILED
                
                Example 5:
                Instruction: Verify pagination
                → "Showing 1 to 10 of 642" + controls visible
                → PASSED
                
                Example 6:
                Instruction: Verify pagination
                → Y > Z OR Y == Z but multiple pages
                → FAILED
                
                =========================================================
                FINAL INSTRUCTIONS
                =========================================================
                
                - Always combine screenshots
                - Always follow priority order
                - Always prefer correctness over assumptions
                - Be deterministic and strict
                """.formatted(step.getPrompt());
    }


}
