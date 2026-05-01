import { TYPES, REQUIRED } from './config.js';

export function validateDL(dlText) {
    const lines = dlText.split('\n');

    let errors = [];
    let currentType = null;
    let fieldsSeen = new Set();
    let lineNo = 0;

    for (let rawLine of lines) {
        lineNo++;
        const line = rawLine.trim();

        if (!line) continue;

        // Scenario
        if (line.startsWith('@')) {
            currentType = line.substring(1).trim();
            fieldsSeen.clear();

            if (!TYPES[currentType]) {
                errors.push(`Line ${lineNo}: Invalid scenario type ${currentType}`);
            }
        }

        // Field
        else if (line.startsWith('^')) {
            if (!currentType) {
                errors.push(`Line ${lineNo}: Field without scenario`);
                continue;
            }

            const [key] = line.substring(1).split(':');

            fieldsSeen.add(key.trim());
        }
    }

    // 🔥 Required field validation
    if (currentType && REQUIRED[currentType]) {
        REQUIRED[currentType].forEach(req => {
            if (!fieldsSeen.has(req)) {
                errors.push(`Missing required field "${req}" for ${currentType}`);
            }
        });
    }

    return errors;
}


export function parseDLToPayload(dlText) {

    const lines = dlText.split('\n');

    let scenarios = [];
    let currentScenario = null;
    let currentBlock = null;
    let currentAssertion = null; // 🔥 important

    for (let index = 0; index < lines.length; index++) {

        let line = lines[index].trim();
        if (!line) continue;

        // ─────────────────────────────
        // 🧩 SCENARIO (@)
        // ─────────────────────────────
        if (line.startsWith('@')) {

            const type = line.substring(1).trim();

            if (!TYPES[type]) {
                throw new Error(`Invalid scenario type: ${type}`);
            }

            currentBlock = null;
            currentAssertion = null;

            if (type === 'ASSERT') {
                currentScenario = {
                    type: 'ASSERT',
                    assertions: []
                };
            } else {
                currentScenario = {
                    type,
                    ...Object.fromEntries(
                        TYPES[type].fields.map(f => [f, ''])
                    ),
                    filters: []
                };
            }

            scenarios.push(currentScenario);
        }

            // ─────────────────────────────
            // 📦 BLOCK (!FILTER)
        // ─────────────────────────────
        else if (line.startsWith('!')) {

            const block = line.substring(1).trim();
            currentBlock = block;

            if (block === 'FILTER') {
                currentScenario.filters.push({});
            }
        }

            // ─────────────────────────────
            // 🔑 FIELD (^key:value)
        // ─────────────────────────────
        else if (line.startsWith('^')) {

            const [key, ...rest] = line.substring(1).split(':');
            const field = key.trim();
            const value = rest.join(':').trim();

            if (!field) continue;

            // ───────── FILTER BLOCK ─────────
            if (currentBlock === 'FILTER') {

                const f = currentScenario.filters[currentScenario.filters.length - 1];

                // 🔥 Normalize keys (important fix)
                const map = {
                    querySelector: 'querySelector',
                    filterType: 'filterType',
                    operation: 'operation',
                    value: 'value',
                    valueSelector: 'valueSelector',
                    logicalOperator: 'logicalOperator'
                };

                f[map[field] || field] = value;
                continue;
            }

            // ───────── ASSERT ─────────
            if (currentScenario.type === 'ASSERT') {

                // 🔥 NEW ASSERT START
                if (field === 'type') {
                    currentAssertion = { type: value };
                    currentScenario.assertions.push(currentAssertion);
                    continue;
                }

                // 🔥 Attach to CURRENT assertion
                if (!currentAssertion) {
                    console.warn("⚠️ Field before assertion type. Skipping:", field);
                    continue;
                }

                currentAssertion[field] = value;
                continue;
            }

            // ───────── NORMAL SCENARIO ─────────
            currentScenario[field] = value;
        }
    }

    // ─────────────────────────────
    // 🔄 FINAL MAPPING
    // ─────────────────────────────
    return {
        scenariosList: scenarios.map((s, i) => ({

            type: s.type,
            sequenceNo: i + 1,

            url: s.url || null,
            cssOpener: s.cssSelector || null,
            value: s.value || null,
            clickCss: s.clickCss || null,
            csv: s.csv || null,

            // ✅ ASSERTS
            assertions: s.type === 'ASSERT'
                ? (s.assertions || []).map(a => ({
                    type: a.type || null,
                    locator: a.locator || null,
                    expected: a.expected || null,
                    tableId: a.tableId || null,
                    columnName: a.columnName || null,
                    order: a.order || null,
                    rowsBtn: a.rowsBtn || null,
                    prompt: a.prompt || null
                }))
                : undefined,

            // ✅ FILTERS
            filters: s.filters?.length
                ? s.filters.map(f => ({
                    querySelector: f.querySelector || null,
                    filterType: f.filterType || null,
                    operation: f.operation || null,
                    value: f.value || null,
                    valueSelector: f.valueSelector || null,
                    logicalOperator: f.logicalOperator || null
                }))
                : undefined,

            applyFilterBtn: s.applyBtnCss || undefined
        }))
    };
}

