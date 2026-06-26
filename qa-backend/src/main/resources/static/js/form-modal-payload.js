// Additional updates for FORM_MODAL support
export function updateFormModalPayload() {
    
    // Update buildPayload function to include clickCss
    const originalBuildPayload = window.buildPayload;
    window.buildPayload = function() {
        console.log("All scenarios before payload:", JSON.stringify(scenarios, null, 2));

        // Debug ASSERT scenarios specifically
        const assertScenarios = scenarios.filter(s => s.type === 'ASSERT');
        console.log("ASSERT scenarios:", JSON.stringify(assertScenarios, null, 2));

        return {
            runName:         document.getElementById('f-runName').value.trim(),
            runType:         document.getElementById('f-runType').value.trim(),
            tags:            document.getElementById('f-tags').value
                .split(',').map(t => t.trim()).filter(Boolean),
            resultStatement: document.getElementById('f-resultStatement').value.trim(),
            createdBy:       user.id || user._id || '',

            scenariosList: scenarios.map((s, i) => ({
                id:              s.id    || undefined,
                type:            s.type,
                sequenceNo:      i + 1,
                url:             s.url         || null,
                cssOpener:       s.cssSelector || null,
                value:           s.value       || null,
                clickCss:        s.clickCss    || null, // Add clickCss field
                csv:             s.csv         || null,
                scenarioBasePath:s.scenarioBasePath || null,

                // 🔥 ADD THIS
                assertions: s.type === 'ASSERT' ? (s.assertions || []).map(a => ({
                    type: a.type || null,
                    locator: a.locator || null,
                    expected: a.expected || null,
                    tableId: a.tableId || null,
                    columnName: a.columnName || null,
                    order: a.order || null,
                    rowsBtn: a.rowsBtn || null
                })) : undefined,
            })),
        };
    };
}
