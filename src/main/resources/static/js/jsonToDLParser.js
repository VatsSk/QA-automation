export function generateDLScript(scenariosList) {

    let lines = [];

    scenariosList.forEach(s => {

        // ───── SCENARIO ─────
        lines.push(`@${s.type}`);

        // ───── NORMAL FIELDS ─────
        if (s.url) lines.push(`^url:${s.url}`);
        if (s.cssOpener) lines.push(`^cssSelector:${s.cssOpener}`);
        if (s.value) lines.push(`^value:${s.value}`);
        if (s.clickCss) lines.push(`^clickCss:${s.clickCss}`);
        if (s.csv) lines.push(`^csv:${s.csv}`);

        // ───── FILTER ─────
        if (s.type === 'FILTER_NAV' && s.filters?.length) {

            if (s.applyFilterBtn) {
                lines.push(`^applyBtnCss:${s.applyFilterBtn}`);
            }

            s.filters.forEach(f => {
                lines.push(`!FILTER`);

                if (f.querySelector) lines.push(`^querySelector:${f.querySelector}`);
                if (f.filterType) lines.push(`^filterType:${f.filterType}`);
                if (f.operation) lines.push(`^operation:${f.operation}`);
                if (f.value) lines.push(`^value:${f.value}`);
                if (f.valueSelector) lines.push(`^valueSelector:${f.valueSelector}`);
                if (f.logicalOperator) lines.push(`^logicalOperator:${f.logicalOperator}`);
            });
        }

        // ───── ASSERT ─────
        if (s.type === 'ASSERT' && s.assertions?.length) {

            s.assertions.forEach(a => {
                lines.push(`^type:${a.type}`);

                if (a.tableId) lines.push(`^tableId:${a.tableId}`);
                if (a.columnName) lines.push(`^columnName:${a.columnName}`);
                if (a.locator) lines.push(`^locator:${a.locator}`);
                if (a.expected) lines.push(`^expected:${a.expected}`);
                if (a.order) lines.push(`^order:${a.order}`);
                if (a.rowsBtn) lines.push(`^rowsBtn:${a.rowsBtn}`);
            });
        }

        // spacing between scenarios
        lines.push('');
    });

    return lines.join('\n');
}

