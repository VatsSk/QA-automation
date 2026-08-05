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

        // ───── DATE RANGE NAV ─────
        if (s.type === 'DATE_RANGE_NAV') {

            const d = s.dateRangeNavDto || {};

            if (d.inputSelector) {
                lines.push(`^inputSelector:${d.inputSelector}`);
            }

            if (d.selectionType) {
                lines.push(`^selectionType:${d.selectionType}`);
            }

            // PRESET
            if (
                d.selectionType === 'PRESET' &&
                d.preset
            ) {
                lines.push(`^preset:${d.preset}`);
            }

            // CUSTOM
            if (d.selectionType === 'CUSTOM') {

                if (d.startDate) {
                    lines.push(`^startDate:${d.startDate}`);
                }

                if (d.endDate) {
                    lines.push(`^endDate:${d.endDate}`);
                }
            }

            if (d.applyButtonSelector) {
                lines.push(
                    `^applyButtonSelector:${d.applyButtonSelector}`
                );
            }

            if (d.calendarContainerSelector) {
                lines.push(
                    `^calendarContainerSelector:${d.calendarContainerSelector}`
                );
            }

            if (d.dateFormat) {
                lines.push(`^dateFormat:${d.dateFormat}`);
            }
        }
        // ───── MANAGE COLUMN NAV ─────
        if (s.type === 'MANAGE_COL_NAV') {

            if (s.saveBtnCss) {
                lines.push(`^saveBtnCss:${s.saveBtnCss}`);
            }

            if (s.columns?.length) {

                s.columns.forEach(col => {

                    lines.push(`!COLUMN`);

                    if (col.columnName) {
                        lines.push(
                            `^columnName:${col.columnName}`
                        );
                    }

                    if (col.action) {
                        lines.push(`^action:${col.action}`);
                    }

                    if (
                        col.position !== undefined &&
                        col.position !== null
                    ) {
                        lines.push(
                            `^position:${col.position}`
                        );
                    }
                });
            }
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

