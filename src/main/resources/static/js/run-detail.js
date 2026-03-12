/**
 * run-detail.js — Run detail page
 * Shows run summary, results, scenario timeline with status dots.
 * Clicking a scenario opens detail with test cases + screenshot gallery.
 * resultStatement displayed at run level (not inside scenarios).
 */

import * as api from './api.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav,
    showToast, badge, esc, fmtDate, timeAgo, showSpinner, showErr, getUser } from './utils.js';

if (!requireAuth()) { /* redirect */ }
initTheme(); fillSidebar(); highlightNav();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);
document.getElementById('back-btn').addEventListener('click', () => history.back());

const runId   = new URLSearchParams(location.search).get('runId');
let pollTimer = null;
let run       = null;

if (!runId) {
    document.getElementById('run-summary').innerHTML =
        `<div class="error-state"><div class="state-icon">⚠️</div><p>No runId in URL</p></div>`;
} else {
    load();
}

async function load() {
    try {
        run = await api.runs.get(runId);
        renderSummary(run);
        renderTimeline(run.scenariosList || []);
        loadResults();

        if (run.status === 'RUNNING') startPolling();
    } catch (e) {
        showErr(document.getElementById('run-summary'), 'Failed to load run: ' + e.message);
    }
}

// ── Summary ───────────────────────────────────────────────────────────────
function renderSummary(r) {
    document.getElementById('run-name-title').textContent = r.runName;
    document.getElementById('run-status-badge').innerHTML = badge(r.status);
    document.title = `${r.runName} — QA Manager`;

    document.getElementById('edit-btn').addEventListener('click', () =>
        location.href = `run-editor.html?runId=${r.id}`);
    document.getElementById('clone-btn').addEventListener('click', async () => {
        try {
            const c = await api.runs.clone(r.id);
            showToast(`Cloned as "${c.runName}"`, 'success');
            location.href = `run-editor.html?runId=${c.id}`;
        } catch (e) { showToast(e.message, 'error'); }
    });
    document.getElementById('run-btn').addEventListener('click', () => executeRun());

    if (r.status === 'RUNNING') {
        document.getElementById('run-btn').disabled = true;
        document.getElementById('run-btn').textContent = '⏳ Running…';
    }

    document.getElementById('run-summary').innerHTML = `
    <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:16px;">
      <div><div class="form-label">Run ID</div><div class="run-id">${esc(r.id)}</div></div>
      <div><div class="form-label">Run Type</div><div>${esc(r.runType||'—')}</div></div>
      <div><div class="form-label">Created By</div><div>${esc(r.createdBy||'—')}</div></div>
      <div><div class="form-label">Scenarios</div><div><strong>${r.scenarioCount||0}</strong></div></div>
      <div><div class="form-label">Created</div><div style="font-size:13px">${fmtDate(r.createdAt)}</div></div>
      <div><div class="form-label">Updated</div><div style="font-size:13px">${timeAgo(r.updatedAt)}</div></div>
    </div>
    ${(r.tags||[]).length ? `
      <div style="margin-top:14px;">
        <div class="form-label">Tags</div>
        <div>${r.tags.map(t=>`<span class="tag">${esc(t)}</span>`).join('')}</div>
      </div>` : ''}
    ${r.resultStatement ? `
      <div style="margin-top:14px;padding:12px 14px;background:var(--bg-surface-2);border-radius:var(--radius-sm);border:1px solid var(--border);">
        <div class="form-label" style="margin-bottom:4px;">Result Statement (sent as query param to runner)</div>
        <div style="font-size:14px;font-weight:500;">${esc(r.resultStatement)}</div>
      </div>` : ''}
  `;
}

// ── Results summary ───────────────────────────────────────────────────────
async function loadResults() {
    try {
        const res = await api.runs.results(runId);
        const body = document.getElementById('results-body');

        const counts = res.scenarioStatusCounts || {};
        const total  = res.totalScenarios || 0;

        body.innerHTML = `
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px;margin-bottom:20px;">
        <div class="stat-card" style="padding:14px;"><div class="stat-value" style="font-size:24px">${total}</div><div class="stat-label">Total</div></div>
        ${Object.entries(counts).map(([s, n]) => `
          <div class="stat-card" style="padding:14px;">
            <div class="stat-value" style="font-size:24px">${n}</div>
            <div class="stat-label">${s}</div>
          </div>`).join('')}
      </div>
      ${res.allResultCsvs?.length ? `
        <div style="margin-bottom:16px;">
          <div class="form-label">Result CSVs</div>
          ${res.allResultCsvs.map(p => `<div><a href="${esc(p)}" target="_blank" class="btn btn-secondary btn-sm" style="margin:3px 3px 3px 0">📥 Download CSV</a></div>`).join('')}
        </div>` : ''}
      ${res.allScreenshots?.length ? `
        <div>
          <div class="form-label" style="margin-bottom:8px;">All Screenshots (${res.allScreenshots.length})</div>
          <div class="screenshot-grid">
            ${res.allScreenshots.map(p => `
              <div class="screenshot-thumb" data-src="${esc(p)}" tabindex="0" role="button" aria-label="View screenshot">
                <img src="${esc(p)}" alt="Screenshot" loading="lazy" onerror="this.parentElement.innerHTML='<div style=padding:20px;text-align:center;font-size:12px;color:var(--text-muted)>No preview</div>'" />
              </div>`).join('')}
          </div>
        </div>` : ''}
    `;

        // Screenshot lightbox
        body.querySelectorAll('.screenshot-thumb').forEach(el => {
            el.addEventListener('click', () => openLightbox(el.dataset.src));
            el.addEventListener('keydown', e => { if (e.key === 'Enter') openLightbox(el.dataset.src); });
        });
    } catch {}
}

// ── Timeline ──────────────────────────────────────────────────────────────
function renderTimeline(scenarios) {
    document.getElementById('scenario-count').textContent = `${scenarios.length} scenario${scenarios.length!==1?'s':''}`;
    const tl = document.getElementById('timeline');
    if (!scenarios.length) {
        tl.innerHTML = '<p style="color:var(--text-muted);font-size:14px;">No scenarios in this run.</p>';
        return;
    }
    tl.innerHTML = scenarios.map((s, i) => {
        const statusCls = (s.status||'PENDING').toLowerCase();
        return `
      <div class="tl-item">
        <div class="tl-dot ${statusCls}" title="${esc(s.status||'PENDING')}">
          ${statusCls==='passed'?'✓':statusCls==='failed'?'✕':i+1}
        </div>
        <div class="tl-content" data-idx="${i}" tabindex="0" role="button" aria-label="View scenario ${i+1}">
          <div class="tl-title">
            <span style="font-size:11px;color:var(--text-muted);margin-right:6px;">#${(s.sequenceNo||i+1)}</span>
            <span class="tag" style="margin-right:6px;">${esc(s.type)}</span>
            ${esc(s.statement || s.url || s.cssOpener || 'No description')}
          </div>
          <div class="tl-meta">
            ${badge(s.status||'PENDING')}
            ${s.actionPerformedAt ? `<span style="margin-left:8px;">${timeAgo(s.actionPerformedAt)}</span>` : ''}
            ${s.screenshots?.length ? `<span style="margin-left:8px;">📸 ${s.screenshots.length}</span>` : ''}
            ${s.resultCsv ? `<span style="margin-left:8px;">📄 CSV</span>` : ''}
          </div>
        </div>
      </div>`;
    }).join('');

    tl.querySelectorAll('.tl-content').forEach(el => {
        const open = () => openScenarioModal(run.scenariosList[+el.dataset.idx]);
        el.addEventListener('click', open);
        el.addEventListener('keydown', e => { if (e.key === 'Enter') open(); });
    });
}

// ── Scenario modal ────────────────────────────────────────────────────────
function openScenarioModal(s) {
    document.getElementById('sc-modal-title').textContent = `Scenario #${s.sequenceNo||''} — ${s.type}`;
    const body = document.getElementById('sc-modal-body');

    const tc = s.manualTestCases || [];
    body.innerHTML = `
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px;">
      <div><div class="form-label">Type</div><span class="tag">${esc(s.type)}</span></div>
      <div><div class="form-label">Status</div>${badge(s.status||'PENDING')}</div>
      ${s.url       ? `<div class="span-full" style="grid-column:1/-1"><div class="form-label">URL</div><div style="font-size:13px;word-break:break-all">${esc(s.url)}</div></div>` : ''}
      ${s.cssOpener ? `<div><div class="form-label">CSS Opener</div><code style="font-size:12px">${esc(s.cssOpener)}</code></div>` : ''}
      ${s.value     ? `<div><div class="form-label">Value</div><div>${esc(s.value)}</div></div>` : ''}
      ${s.statement ? `<div style="grid-column:1/-1"><div class="form-label">Statement</div><div>${esc(s.statement)}</div></div>` : ''}
    </div>

    ${s.csv ? `<div style="margin-bottom:12px;"><div class="form-label">Input CSV</div><a href="${esc(s.csv)}" target="_blank" class="btn btn-secondary btn-sm">📥 Download Input CSV</a></div>` : ''}
    ${s.resultCsv ? `<div style="margin-bottom:12px;"><div class="form-label">Result CSV</div><a href="${esc(s.resultCsv)}" target="_blank" class="btn btn-secondary btn-sm">📥 Download Result CSV</a></div>` : ''}

    ${tc.length ? `
      <div style="margin-bottom:16px;">
        <div class="form-label" style="margin-bottom:8px;">Manual Test Cases</div>
        <table class="table" style="font-size:12px;">
          <thead><tr><th>#</th><th>Name</th><th>Expected</th><th>Actual</th><th>Status</th></tr></thead>
          <tbody>
            ${tc.map((t, i) => `
              <tr>
                <td>${i+1}</td>
                <td>${esc(t.name)}</td>
                <td>${esc(t.expectedResult)}</td>
                <td>${esc(t.actualResult||'—')}</td>
                <td>${badge(t.status||'PENDING')}</td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>` : ''}

    ${s.screenshots?.length ? `
      <div>
        <div class="form-label" style="margin-bottom:8px;">Screenshots (${s.screenshots.length})</div>
        <div class="screenshot-grid">
          ${s.screenshots.map(p => `
            <div class="screenshot-thumb" data-src="${esc(p)}" tabindex="0" role="button" aria-label="View screenshot">
              <img src="${esc(p)}" alt="Screenshot" loading="lazy" onerror="this.parentElement.innerHTML='<div style=padding:12px;font-size:11px;color:var(--text-muted);text-align:center>No preview</div>'" />
            </div>`).join('')}
        </div>
      </div>` : ''}
  `;

    body.querySelectorAll('.screenshot-thumb').forEach(el => {
        el.addEventListener('click', () => openLightbox(el.dataset.src));
        el.addEventListener('keydown', e => { if (e.key==='Enter') openLightbox(el.dataset.src); });
    });

    document.getElementById('scenario-modal').style.display = 'flex';
}

document.getElementById('sc-modal-close').addEventListener('click', () => {
    document.getElementById('scenario-modal').style.display = 'none';
});
document.getElementById('scenario-modal').addEventListener('click', e => {
    if (e.target.id === 'scenario-modal') document.getElementById('scenario-modal').style.display = 'none';
});

// ── Lightbox ──────────────────────────────────────────────────────────────
function openLightbox(src) {
    document.getElementById('lb-img').src = src;
    document.getElementById('lightbox').style.display = 'flex';
}
document.getElementById('lb-close').addEventListener('click', () => {
    document.getElementById('lightbox').style.display = 'none';
});
document.getElementById('lightbox').addEventListener('click', e => {
    if (e.target.id === 'lightbox') document.getElementById('lightbox').style.display = 'none';
});

// ── Execute run ───────────────────────────────────────────────────────────
async function executeRun() {
    const btn = document.getElementById('run-btn');
    btn.disabled = true; btn.textContent = '⏳ Starting…';
    try {
        await api.runs.execute(runId);
        showToast('Run started', 'success');
        startPolling();
    } catch (e) {
        btn.disabled = false; btn.textContent = '▶ Run Now';
        showToast('Execute failed: ' + e.message, 'error');
    }
}

// ── Polling ───────────────────────────────────────────────────────────────
function startPolling() {
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
        try {
            run = await api.runs.get(runId);
            document.getElementById('run-status-badge').innerHTML = badge(run.status);
            renderTimeline(run.scenariosList || []);
            if (run.status !== 'RUNNING') {
                clearInterval(pollTimer);
                const t = run.status==='PASSED'?'success':run.status==='FAILED'?'error':'info';
                showToast(`Run ${run.status}`, t);
                document.getElementById('run-btn').disabled = false;
                document.getElementById('run-btn').textContent = '▶ Run Now';
                loadResults();
            }
        } catch { clearInterval(pollTimer); }
    }, 3000);
}