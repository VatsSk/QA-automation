import { validateDL, parseDLToPayload } from './dlUtils.js';

export function buildPayload(mode, toast, user, scenarios,projectData) {

    console.log("mode "+ mode);

    if (mode === 'dl') {
        const script = window.editor?.getValue();

        console.log("DL Script:", script);

        if (!script || !script.trim()) {
            toast('DL script cannot be empty', 'warning');
            return;
        }

        // 🔥 1. Validate
        const errors = validateDL(script);
        if (errors.length) {
            toast(errors[0], 'error');
            return;
        }

        // 🔥 2. Convert DL → scenarios
        const parsed = parseDLToPayload(script);

        // 🔥 3. Inject LOGIN SCENARIO (FIRST POSITION)
        const loginScenario = buildLoginScenario(projectData);

        const finalScenarios = [
            loginScenario,
            ...parsed.scenariosList.map((s, i) => ({
                ...s,
                sequenceNo: i + 2 // shift because login is #1
            }))
        ];

        // 🔥 4. Return SAME structure as manual mode
        return {
            runName: document.getElementById('f-runName').value.trim(),
            runType: document.getElementById('f-runType').value.trim(),
            tags: document.getElementById('f-tags').value
                .split(',').map(t => t.trim()).filter(Boolean),
            resultStatement: document.getElementById('f-resultStatement').value.trim(),
            createdBy: user.id ||  '',

            scenariosList: finalScenarios
        };
    }

    // ✅ Existing manual flow (unchanged)
    console.log("All scenarios before payload:", JSON.stringify(scenarios, null, 2));

    return {
        runName: document.getElementById('f-runName').value.trim(),
        runType: document.getElementById('f-runType').value.trim(),
        tags: document.getElementById('f-tags').value
            .split(',').map(t => t.trim()).filter(Boolean),
        resultStatement: document.getElementById('f-resultStatement').value.trim(),
        createdBy: user.id || '',

        scenariosList: scenarios.map((s, i) => ({
            id: s.id || undefined,
            type: s.type,
            sequenceNo: i + 1,
            url: s.url || null,
            cssOpener: s.cssSelector || null,
            value: s.value || null,
            clickCss: s.clickCss || null,
            csv: s.csv || null,
            scenarioBasePath: s.scenarioBasePath || null,

            assertions: s.type === 'ASSERT'
                ? (s.assertions || []).map(a => ({
                    type: a.type || null,
                    locator: a.locator || null,
                    expected: a.expected || null,
                    tableId: a.tableId || null,
                    columnName: a.columnName || null,
                    order: a.order || null,
                    rowsBtn: a.rowsBtn || null,
                    prompt: a.promptAi || null
                }))
                : undefined,

            filters: s.type === 'FILTER_NAV'
                ? (s.filters || []).map(f => {
                    let finalValue = f.value;

                    if (f.operation === 'RANGE') {
                        const start = f.value?.start;
                        const end = f.value?.end;
                        if (start && end) {
                            finalValue = formatRange(start, end, f.filterType);
                        }
                    }

                    return {
                        querySelector: f.querySelectorOfColName,
                        filterType: f.filterType,
                        operation: f.operation,
                        value: finalValue,
                        valueSelector: f.searchCssSelector || null,
                        logicalOperator: f.logicalOperatorSelector || null
                    };
                })
                : undefined,

            applyFilterBtn: s.type === 'FILTER_NAV' ? s.applyBtnCss : undefined,
        }))
    };
}

function buildLoginScenario(projectData) {

    if (!projectData || !projectData.loginUrl) {
        throw new Error('Project login URL not configured');
    }

    return {
        type: 'URL',
        sequenceNo: 1,

        url: projectData.loginUrl,
        cssOpener: null,
        value: null,
        clickCss: null,

        csv: projectData.loginCredS3Path || null,

        assertions: [],
        filters: []
    };
}

