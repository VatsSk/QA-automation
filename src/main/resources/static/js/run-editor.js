/**
 * run-editor.js — Create / Edit Run
 *
 * ScenarioTypes: URL, MODAL, URL_NAV, MODAL_NAV, SEARCH_NAV
 * resultStatement: run-level field — NOT per-scenario.
 * Sent as query param ?resultStatement=... to /runner/run-auth at execution time.
 */

import * as api from './api.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav,
    showToast, esc, getUser } from './utils.js';

if (!requireAuth()) { /* redirect */ }
initTheme(); fillSidebar(); highlightNav();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);

const urlP      = new URLSearchParams(location.search);
const runId     = urlP.get('runId');
const projectId = urlP.get('projectId');
const moduleId  = urlP.get('moduleId');

document.getElementById('back-btn').addEventListener('click', () => history.back());
document.getElementById('page-title').textContent = runId ? 'Edit Run' : 'New Run';

// ── Scenario type → fields visible ───────────────────────────────────────
const TYPE_FIELDS = {
    URL:        ['url', 'value', 'statement', 'csv'],
    MODAL:      ['cssOpener', 'value', 'statement', 'csv'],
    URL_NAV:    ['url', 'value', 'statement', 'csv'],
    MODAL_NAV:  ['cssOpener', 'value', 'statement', 'csv'],
    SEARCH_NAV: ['url', 'value', 'statement', 'csv'],
};
const REQUIRED = {
    URL:        ['url', 'statement'],
    MODAL:      ['cssOpener', 'statement'],
    URL_NAV:    ['url', 'statement'],
    MODAL_NAV:  ['cssOpener', 'statement'],
    SEARCH_NAV: ['url', 'value', 'statement'],
};

let scenarios   = [];   // array of { id, type, url, cssOpener, value, statement, csv, manualTestCases }
let scCounter   = 0;

// ── Load existing run ────────────────────────────────────────────────────
if (runId) {
    (async () => {
        try {
            const run = await api.runs.get(runId);
            document.getElementById('f-runName').value         = run.runName        || '';
            document.getElementById('f-runType').value         = run.runType        || '';
            document.getElementById('f-createdBy').value       = run.createdBy      || '';
            document.getElementById('f-tags').value            = (run.tags||[]).join(', ');
            document.getElementById('f-resultStatement').value = run.resultStatement || '';

            scenarios = (run.scenariosList||[]).map(s => ({
                _key:           ++scCounter,
                id:             s.id,
                type:           s.type || 'URL',
                url:            s.url        || '',
                cssOpener:      s.cssOpener  || '',
                value:          s.value      || '',
                statement:      s.statement  || '',
                csv:            s.csv        || '',
                manualTestCases: s.manualTestCases || [],
            }));
            renderAll();
        } catch (e) { showToast('Failed to load run: ' + e.message, 'error'); }
    })();
} else {
    // Pre-fill createdBy from logged-in user
    const user = getUser() || {};
    document.getElementById('f-createdBy').value = user.username || '';
}

// ── Scenario list render ─────────────────────────────────────────────────
function renderAll() {
    const list  = document.getElementById('scenarios-list');
    const empty = document.getElementById('scenarios-empty');
    empty.style.display = scenarios.length ? 'none' : 'block';
    list.innerHTML = scenarios.map((s, i) => scenarioCard(s, i)).join('');

    // Wire all events inside each card
    scenarios.forEach((s, i) => {
        const card = list.querySelector(`[data-key="${s._key}"]`);
        if (!card) return;

        // Type select → show/hide fields
        const typeSel = card.querySelector('.sc-type');
        typeSel.addEventListener('change', () => {
            s.type = typeSel.value;
            updateFields(card, s.type);
        });
        updateFields(card, s.type);

        // Collapse toggle
        card.querySelector('.sc-header').addEventListener('click', e => {
            if (e.target.closest('button, select')) return;
            card.querySelector('.sc-body').classList.toggle('collapsed');
        });

        // Text fields
        card.querySelector('.sc-url')?.addEventListener('input', e => s.url = e.target.value);
        card.querySelector('.sc-css')?.addEventListener('input', e => s.cssOpener = e.target.value);
        card.querySelector('.sc-val')?.addEventListener('input', e => s.value = e.target.value);
        card.querySelector('.sc-stmt')?.addEventListener('input', e => s.statement = e.target.value);

        // CSV upload
        // CSV upload
        card.querySelector('.sc-csv-upload')?.addEventListener('change', async e => {
            const file = e.target.files[0];
            if (!file) return;

            try {
                if (!projectId || !moduleId) {
                    showToast('Missing projectId or moduleId in URL', 'error');
                    return;
                }

                const sequenceNo = i + 1; // current scenario position (1-based)

                showToast('Uploading CSV…', 'info', 1500);

                const res = await api.uploads.csv(file, projectId, moduleId, sequenceNo, runId || '');

                s.csv = res.path;
                card.querySelector('.sc-csv-path').textContent = res.path;

                showToast('CSV uploaded', 'success');
            } catch (err) {
                showToast('Upload failed: ' + err.message, 'error');
            }
        });

        // Move up/down
        card.querySelector('.sc-up')?.addEventListener('click', e => { e.stopPropagation(); moveScenario(i, -1); });
        card.querySelector('.sc-dn')?.addEventListener('click', e => { e.stopPropagation(); moveScenario(i,  1); });

        // Delete
        card.querySelector('.sc-del')?.addEventListener('click', e => {
            e.stopPropagation();
            if (scenarios.length > 1 && !confirm('Remove this scenario?')) return;
            scenarios.splice(i, 1);
            renderAll();
        });

        // Manual test cases
        card.querySelector('.add-tc-btn')?.addEventListener('click', () => {
            s.manualTestCases.push({ testcaseId: crypto.randomUUID(), name: '', expectedResult: '', actualResult: '', status: 'PENDING' });
            renderAll();
        });
        card.querySelectorAll('.tc-row').forEach((row, ti) => {
            row.querySelector('.tc-name')?.addEventListener('input',     e => s.manualTestCases[ti].name           = e.target.value);
            row.querySelector('.tc-expected')?.addEventListener('input', e => s.manualTestCases[ti].expectedResult  = e.target.value);
            row.querySelector('.tc-del')?.addEventListener('click', () => {
                s.manualTestCases.splice(ti, 1); renderAll();
            });
        });
    });
}

function scenarioCard(s, i) {
    const types = ['URL','MODAL','URL_NAV','MODAL_NAV','SEARCH_NAV'];
    return `
    <div class="scenario-card" data-key="${s._key}">
      <div class="sc-header scenario-header">
        <span class="scenario-seq">#${i+1}</span>
        <select class="sc-type form-control" style="width:140px;font-size:13px;" aria-label="Scenario type">
          ${types.map(t => `<option value="${t}" ${s.type===t?'selected':''}>${t}</option>`).join('')}
        </select>
        <span class="scenario-title">${esc(s.statement||'New scenario')}</span>
        <button type="button" class="sc-up  btn btn-ghost btn-sm btn-icon" title="Move up"   ${i===0?'disabled':''}>↑</button>
        <button type="button" class="sc-dn  btn btn-ghost btn-sm btn-icon" title="Move down" ${i===scenarios.length-1?'disabled':''}>↓</button>
        <button type="button" class="sc-del btn btn-ghost btn-sm btn-icon" title="Remove">🗑️</button>
      </div>
      <div class="sc-body scenario-body">
        <div class="sc-field-url form-group span-full" style="display:none">
          <label class="form-label">URL <span class="req">*</span></label>
          <input type="text" class="sc-url form-control" value="${esc(s.url)}" placeholder="https://example.com/page" />
        </div>
        <div class="sc-field-css form-group" style="display:none">
          <label class="form-label">CSS Opener <span class="req">*</span></label>
          <input type="text" class="sc-css form-control" value="${esc(s.cssOpener)}" placeholder="#modal-btn" />
        </div>
        <div class="sc-field-val form-group" style="display:none">
          <label class="form-label">Value</label>
          <input type="text" class="sc-val form-control" value="${esc(s.value)}" placeholder="Input value" />
        </div>
        <div class="sc-field-stmt form-group span-full" style="display:none">
          <label class="form-label">Statement <span class="req">*</span></label>
          <input type="text" class="sc-stmt form-control" value="${esc(s.statement)}" placeholder="Describe what this scenario does" />
        </div>
        <div class="sc-field-csv form-group span-full" style="display:none">
          <label class="form-label">Test Case CSV</label>
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
            <input type="file" class="sc-csv-upload" accept=".csv,.xlsx" style="display:none" id="csv-${s._key}" />
            <label for="csv-${s._key}" class="btn btn-secondary btn-sm" style="cursor:pointer;">📎 Upload CSV</label>
            <span class="sc-csv-path" style="font-size:12px;color:var(--text-muted)">${esc(s.csv||'No file uploaded')}</span>
          </div>
        </div>

        <!-- Manual test cases -->
        <div class="span-full" style="margin-top:8px;">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <span style="font-size:12px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:.4px;">Manual Test Cases</span>
            <button type="button" class="add-tc-btn btn btn-ghost btn-sm">+ Add Test Case</button>
          </div>
          ${s.manualTestCases.length ? `
            <table class="table tc-table" style="font-size:12px;">
              <thead><tr><th>#</th><th>Name</th><th>Expected Result</th><th></th></tr></thead>
              <tbody>
                ${s.manualTestCases.map((tc, ti) => `
                  <tr class="tc-row">
                    <td>${ti+1}</td>
                    <td><input type="text" class="tc-name form-control" value="${esc(tc.name)}" placeholder="TC name" /></td>
                    <td><input type="text" class="tc-expected form-control" value="${esc(tc.expectedResult)}" placeholder="Expected" /></td>
                    <td><button type="button" class="tc-del btn btn-ghost btn-sm btn-icon">🗑️</button></td>
                  </tr>`).join('')}
              </tbody>
            </table>` : '<p style="font-size:12px;color:var(--text-muted)">No manual test cases. Click + Add Test Case.</p>'}
        </div>
      </div>
    </div>`;
}

function updateFields(card, type) {
    const visible = TYPE_FIELDS[type] || [];
    card.querySelector('.sc-field-url').style.display  = visible.includes('url')       ? '' : 'none';
    card.querySelector('.sc-field-css').style.display  = visible.includes('cssOpener') ? '' : 'none';
    card.querySelector('.sc-field-val').style.display  = visible.includes('value')     ? '' : 'none';
    card.querySelector('.sc-field-stmt').style.display = visible.includes('statement') ? '' : 'none';
    card.querySelector('.sc-field-csv').style.display  = visible.includes('csv')       ? '' : 'none';
}

function moveScenario(i, dir) {
    const j = i + dir;
    if (j < 0 || j >= scenarios.length) return;
    [scenarios[i], scenarios[j]] = [scenarios[j], scenarios[i]];
    renderAll();
}

// ── Add scenario button ──────────────────────────────────────────────────
document.getElementById('add-scenario-btn').addEventListener('click', () => {
    scenarios.push({
        _key: ++scCounter, type: 'URL',
        url: '', cssOpener: '', value: '', statement: '', csv: '',
        manualTestCases: [],
    });
    renderAll();
    // Scroll to new card
    const last = document.getElementById('scenarios-list').lastElementChild;
    last?.scrollIntoView({ behavior: 'smooth', block: 'start' });
});

// ── Validate ─────────────────────────────────────────────────────────────
function validate() {
    const name = document.getElementById('f-runName').value.trim();
    if (!name) { showToast('Run name is required', 'warning'); return false; }

    for (let i = 0; i < scenarios.length; i++) {
        const s = scenarios[i];
        const req = REQUIRED[s.type] || [];
        for (const f of req) {
            const val = s[f === 'cssOpener' ? 'cssOpener' : f];
            if (!val || !val.trim()) {
                showToast(`Scenario #${i+1} (${s.type}): "${f}" is required`, 'warning');
                return false;
            }
        }
    }
    return true;
}

// ── Build payload ────────────────────────────────────────────────────────
function buildPayload() {
    const user = getUser() || {};
    return {
        runName:         document.getElementById('f-runName').value.trim(),
        runType:         document.getElementById('f-runType').value.trim(),
        createdBy:       document.getElementById('f-createdBy').value.trim() || user.username,
        tags:            document.getElementById('f-tags').value.split(',').map(t=>t.trim()).filter(Boolean),
        resultStatement: document.getElementById('f-resultStatement').value.trim(),  // run-level, not scenario
        scenariosList:   scenarios.map((s, i) => ({
            id:             s.id || undefined,
            type:           s.type,
            sequenceNo:     i + 1,
            url:            s.url       || null,
            cssOpener:      s.cssOpener || null,
            value:          s.value     || null,
            statement:      s.statement || null,
            csv:            s.csv       || null,
            manualTestCases: s.manualTestCases,
        })),
    };
}

// ── Save draft ───────────────────────────────────────────────────────────
document.getElementById('save-draft-btn').addEventListener('click', async () => {
    if (!validate()) return;
    await doSave(false);
});

// ── Save & Run ───────────────────────────────────────────────────────────
document.getElementById('save-run-btn').addEventListener('click', async () => {
    if (!validate()) return;
    await doSave(true);
});

async function doSave(andRun) {
    const btn = andRun
        ? document.getElementById('save-run-btn')
        : document.getElementById('save-draft-btn');
    btn.disabled = true;
    btn.textContent = andRun ? 'Saving & Running…' : 'Saving…';

    try {
        let saved;
        const payload = buildPayload();

        if (runId) {
            saved = await api.runs.update(runId, payload);
        } else {
            if (!projectId || !moduleId) {
                showToast('Missing projectId or moduleId in URL', 'error');
                return;
            }
            saved = await api.runs.create(projectId, moduleId, payload);
        }

        showToast(andRun ? 'Saved — starting run…' : 'Run saved as draft', 'success');

        if (andRun) {
            try {
                await api.runs.execute(saved.id);
                showToast('Run started!', 'success');
            } catch (ex) {
                showToast('Saved but execute failed: ' + ex.message, 'warning');
            }
        }

        location.href = `run-detail.html?runId=${saved.id}`;
    } catch (e) {
        showToast('Save failed: ' + e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = andRun ? '▶ Save & Run' : '💾 Save Draft';
    }
}