import { validateDL, parseDLToPayload } from './dlUtils.js';

export function buildPayload(mode, toast, user, scenarios,projectData) {

    console.log("mode "+ mode);
    const isDLMode =
        mode === 'dl' ||
        new URLSearchParams(window.location.search).get('editMode') === 'dl';

    if (isDLMode) {
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
        console.log("parsed : "+JSON.stringify(parsed))

        // ❌ Remove login-like scenarios from parsed list
        const filtered = parsed.scenariosList.filter(s =>
            !(s.type === 'URL' && s.url === projectData.loginUrl)
        );
        console.log("filtered : "+JSON.stringify(filtered))

        // 🔥 3. Inject LOGIN SCENARIO (FIRST POSITION)
        const loginScenario = buildLoginScenario(projectData);
        console.log("loginScenario : "+JSON.stringify(loginScenario))

        const finalScenarios = [
            loginScenario,
            ...filtered.map((s, i) => ({
                ...s,
                sequenceNo: i + 2 // shift because login is #1
            }))
        ];

        console.log("finalScenarios : "+JSON.stringify(finalScenarios))

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

            // MANAGE COLUMN
            columns: s.type === 'MANAGE_COL_NAV'
                ? (s.columns || []).map(c => ({
                    columnName: c.columnName || null,
                    action: c.action || null,
                    position: c.position ?? null
                }))
                : undefined,

            saveBtnCss: s.type === 'MANAGE_COL_NAV'
                ? s.saveBtnCss || null
                : undefined,

            applyFilterBtn: s.type === 'FILTER_NAV' ? s.applyBtnCss : undefined,
            dateRangeNavDto: s.type === 'DATE_RANGE_NAV'
                ? {
                    inputSelector: s.inputSelector || null,
                    calendarContainerSelector: s.calendarContainerSelector || null,
                    applyButtonSelector: s.applyButtonSelector || null,
                    selectionType: s.selectionType || null,
                    preset: s.preset || null,
                    startDate: s.startDate || null,
                    endDate: s.endDate || null,
                    dateFormat: s.dateFormat || null
                }
                : undefined,
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

