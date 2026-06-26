import { TYPES, REQUIRED,DATE_SELECTION_TYPES ,ASSERT_TYPES } from './config.js';

export function validateDL(dlText) {

    const lines = dlText.split('\n');

    let errors = [];
    let currentType = null;
    let fieldsSeen = new Set();
    let lineNo = 0;

    function validateScenario(type, fields, line) {

        if (!type || !REQUIRED[type]) return;

        REQUIRED[type].forEach(req => {

            if (!fields.has(req)) {
                errors.push(
                    `Line ${line}: Missing required field "${req}" for ${type}`
                );
            }
        });
    }

    for (let rawLine of lines) {

        lineNo++;

        const line = rawLine.trim();

        if (!line) continue;

        // ─────────────────────────────
        // SCENARIO
        // ─────────────────────────────
        if (line.startsWith('@')) {

            // 🔥 validate previous scenario
            validateScenario(currentType, fieldsSeen, lineNo);

            currentType = line.substring(1).trim();

            fieldsSeen = new Set();

            if (!TYPES[currentType]) {
                errors.push(`Line ${lineNo}: Invalid scenario type ${currentType}`);
            }
        }

            // ─────────────────────────────
            // FIELD
        // ─────────────────────────────
        else if (line.startsWith('^')) {

            if (!currentType) {
                errors.push(`Line ${lineNo}: Field without scenario`);
                continue;
            }

            const [key, ...rest] = line.substring(1).split(':');

            const field = key.trim();
            const value = rest.join(':').trim();

            // ─────────────────────────────
            // ASSERT VALIDATION
            // ─────────────────────────────
            if (currentType === 'ASSERT') {

                let assertType = null;

                const typeField = [...fieldsSeen]
                    .find(f => f.startsWith('__ASSERT_TYPE__:'));

                if (typeField) {
                    assertType =
                        typeField.replace('__ASSERT_TYPE__:', '');
                }

                // 🔥 ASSERT TYPE
                if (field === 'type') {

                    if (!ASSERT_TYPES[value]) {
                        errors.push(
                            `Line ${lineNo}: Invalid ASSERT type ${value}`
                        );
                    }

                    fieldsSeen.add(`__ASSERT_TYPE__:${value}`);
                }

                // 🔥 type must come first
                if (
                    field !== 'type' &&
                    !assertType
                ) {
                    errors.push(
                        `Line ${lineNo}: ASSERT type must be defined before "${field}"`
                    );
                }

                // 🔥 ASSERT FIELD VALIDATION
                if (
                    assertType &&
                    ASSERT_TYPES[assertType]
                ) {

                    const allowedFields =
                        ASSERT_TYPES[assertType].fields;

                    if (
                        field !== 'type' &&
                        !allowedFields.includes(field)
                    ) {
                        errors.push(
                            `Line ${lineNo}: Invalid field "${field}" for ASSERT type ${assertType}`
                        );
                    }
                }
            }

            // ─────────────────────────────
            // DATE RANGE VALIDATION
            // ─────────────────────────────
            if (currentType === 'DATE_RANGE_NAV') {

                let currentSelection = null;

                const selectionField = [...fieldsSeen]
                    .find(f => f.startsWith('__SELECTION__:'));

                if (selectionField) {
                    currentSelection =
                        selectionField.replace('__SELECTION__:', '');
                }

                // 🔥 selectionType
                if (field === 'selectionType') {
                    if (!DATE_SELECTION_TYPES.some(t => t.value === value)) {

                        errors.push(
                            `Line ${lineNo}: Invalid selectionType ${value}`
                        );
                    }
                    fieldsSeen.add(`__SELECTION__:${value}`);
                    currentSelection = value;
                }

                // 🔥 PRESET validation
                if (
                    currentSelection === 'PRESET' &&
                    ['startDate', 'endDate'].includes(field)
                ) {
                    errors.push(
                        `Line ${lineNo}: ${field} not allowed for PRESET`
                    );
                }

                // 🔥 CUSTOM validation
                if (
                    currentSelection === 'CUSTOM' &&
                    field === 'preset'
                ) {
                    errors.push(
                        `Line ${lineNo}: preset not allowed for CUSTOM`
                    );
                }
            }

            // 🔥 NORMAL FIELD TRACKING
            fieldsSeen.add(field);
        }
    }

    // 🔥 validate last scenario
    validateScenario(currentType, fieldsSeen, lineNo);

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
                    assertions: [],
                    filters: [],
                    columns: []
                };
            } else {
                currentScenario = {
                    type,
                    ...Object.fromEntries(
                        TYPES[type].fields.map(f => [f, ''])
                    ),
                    filters: [],
                    columns: []
                };
            }

            scenarios.push(currentScenario);
        }

            // ─────────────────────────────
            // 📦 BLOCK (!FILTER)
        // ─────────────────────────────
        else if (line.startsWith('!')) {

            const block = line.substring(1).trim().toUpperCase();
            currentBlock = null;
            currentBlock = block;

            if (block === 'FILTER') {
                currentScenario.filters.push({});
            }

            // 🔥 COLUMN BLOCK
            if (block === 'COLUMN') {

                if (!currentScenario.columns) {
                    currentScenario.columns = [];
                }

                currentScenario.columns.push({});
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
                if (!currentScenario.filters.length) {
                    console.warn("⚠️ FILTER field before !FILTER block");
                    continue;
                }
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
            // ───────── COLUMN BLOCK ─────────
            if (currentBlock === 'COLUMN') {
                if (!currentScenario.columns.length) {
                    console.warn("⚠️ COLUMN field before !COLUMN block");
                    continue;
                }

                const col =
                    currentScenario.columns[
                    currentScenario.columns.length - 1
                        ];

                const columnField = field === 'column' ? 'columnName' : field;

                col[columnField] =
                    field === 'position'
                        ? (isNaN(Number(value)) ? null : Number(value))
                        : value;

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

    // 🔄 FINAL MAPPING
    return {
        scenariosList: scenarios.map((s, i) => {

            const base = {
                type: s.type,
                sequenceNo: i + 1,

                url: s.url || null,
                cssOpener: s.cssSelector || null,
                value: s.value || null,
                clickCss: s.clickCss || null,
                csv: s.csv || null
            };

            // ─────────────────────────────
            // DATE RANGE NAV
            // ─────────────────────────────
            if (s.type === 'DATE_RANGE_NAV') {

                base.dateRangeNavDto = {
                    inputSelector: s.inputSelector || null,
                    selectionType: s.selectionType || null,
                    preset: s.preset || null,
                    startDate: s.startDate || null,
                    endDate: s.endDate || null,
                    applyButtonSelector:
                        s.applyButtonSelector || null,
                    calendarContainerSelector:
                        s.calendarContainerSelector || null,
                    dateFormat: s.dateFormat || null
                };
            }

            // ─────────────────────────────
            // MANAGE COLUMN NAV
            // ─────────────────────────────
            if (s.type === 'MANAGE_COL_NAV') {

                base.saveBtnCss =
                    s.saveBtnCss || null;

                base.columns =
                    s.columns || [];
            }

            // ─────────────────────────────
            // ASSERT
            // ─────────────────────────────
            if (s.type === 'ASSERT') {

                base.assertions =
                    (s.assertions || []).map(a => ({
                        type: a.type || null,
                        locator: a.locator || null,
                        expected: a.expected || null,
                        tableId: a.tableId || null,
                        columnName: a.columnName || null,
                        order: a.order || null,
                        rowsBtn: a.rowsBtn || null,
                        prompt: a.prompt || null
                    }));
            }

            // ─────────────────────────────
            // FILTER NAV
            // ─────────────────────────────
            if (s.filters?.length) {

                base.filters =
                    s.filters.map(f => ({
                        querySelector:
                            f.querySelector || null,
                        filterType:
                            f.filterType || null,
                        operation:
                            f.operation || null,
                        value:
                            f.value || null,
                        valueSelector:
                            f.valueSelector || null,
                        logicalOperator:
                            f.logicalOperator || null
                    }));

                base.applyFilterBtn =
                    s.applyBtnCss || undefined;
            }

            return base;
        })
    };
}
