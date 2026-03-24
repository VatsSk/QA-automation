// Add these functions to run-editor.html after the renderDataSection function
import { ASSERT_TYPES, SORT_ORDER_OPTIONS } from './config.js';
import { esc } from './utils.js';

function ensureAssertions(scenario) {
    if (!scenario.assertions) {
        scenario.assertions = [];
    }
    return scenario.assertions;
}
// ── Assertions section (for ASSERT type scenarios) ─────────────────────
export function renderAssertionsSection(idx, s) {
    const assertions = ensureAssertions(s);
    return `
    <div class="sc-section">Assertions</div>
    <div class="assertions-container" id="assertions-${idx}">
        ${assertions.map((assertion, ai) => renderAssertion(idx, ai, assertion)).join('')}
        ${assertions.length === 0 ? '<div style="padding:20px;text-align:center;color:var(--tx2);border:1px dashed var(--bd);border-radius:var(--rs);margin-bottom:12px">No assertions added yet</div>' : ''}
    </div>
    <button class="btn btn-ghost btn-sm" onclick="addAssertion(${idx})" style="margin-top:8px">
        + Add Assertion
    </button>`;
}

function renderAssertion(scIdx, assertionIdx, assertion) {
    const assertType = assertion.assertType || '';
    const assertConfig = ASSERT_TYPES[assertType] || { fields: [], required: [] };
    
    return `
    <div class="assertion-card" id="assertion-${scIdx}-${assertionIdx}" style="border:1px solid var(--bd);border-radius:var(--rs);padding:12px;margin-bottom:12px;background:var(--sur2)">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
            <div style="display:flex;gap:8px;align-items:center">
                <label class="lbl" style="margin:0">Assertion Type:</label>
                <select class="inp" style="width:180px" onchange="updateAssertionType(${scIdx}, ${assertionIdx}, this.value)">
                    <option value="">Select type...</option>
                    ${Object.entries(ASSERT_TYPES).map(([key, config]) => 
                        `<option value="${key}" ${assertType === key ? 'selected' : ''}>${config.label}</option>`
                    ).join('')}
                </select>
            </div>
            <button class="btn btn-danger btn-xs" onclick="removeAssertion(${scIdx}, ${assertionIdx})" style="padding:4px 8px;font-size:11px">Remove</button>
        </div>
        
        ${assertType ? renderAssertionFields(scIdx, assertionIdx, assertion, assertConfig) : ''}
    </div>`;
}

function renderAssertionFields(scIdx, assertionIdx, assertion, assertConfig) {
    let fieldsHtml = '';
    
    assertConfig.fields.forEach(field => {
        let fieldHtml = '';
        let value = assertion[field] || '';
        
        switch(field) {
            case 'cssSelector':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">CSS Selector ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <input class="inp inp-mono" 
                        value="${esc(value)}"
                        placeholder=".element, #id"
                        oninput="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                </div>`;
                break;
                
            case 'expectedValue':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">Expected Value ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <input class="inp" 
                        value="${esc(value)}"
                        placeholder="Expected text or value"
                        oninput="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                </div>`;
                break;
                
            case 'tableSelector':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">Table Selector ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <input class="inp inp-mono" 
                        value="${esc(value)}"
                        placeholder="table selector"
                        oninput="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                </div>`;
                break;
                
            case 'columnName':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">Column Name ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <input class="inp" 
                        value="${esc(value)}"
                        placeholder="column name"
                        oninput="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                </div>`;
                break;
                
            case 'order':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">Sort Order ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <select class="inp" 
                        onchange="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                        <option value="">Select order...</option>
                        ${SORT_ORDER_OPTIONS.map(opt => 
                            `<option value="${opt.value}" ${value === opt.value ? 'selected' : ''}>${opt.label}</option>`
                        ).join('')}
                    </select>
                </div>`;
                break;
                
            case 'btnSelector':
                fieldHtml = `
                <div class="field" style="margin:8px 0">
                    <label class="lbl">Button Selector ${assertConfig.required.includes(field) ? '*' : ''}</label>
                    <input class="inp inp-mono" 
                        value="${esc(value)}"
                        placeholder="button selector"
                        oninput="updateAssertionField(${scIdx}, ${assertionIdx}, '${field}', this.value)">
                </div>`;
                break;
        }
        
        fieldsHtml += fieldHtml;
    });
    
    return fieldsHtml;
}
