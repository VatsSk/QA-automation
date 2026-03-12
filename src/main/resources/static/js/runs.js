import * as api from './api.js';
import { load, save, reset, activeCount, toChips, QUICK } from './filters.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav,
    showToast, showSpinner, showEmpty, showErr, badge, esc, fmtDate, timeAgo,
    debounce, getUser } from './utils.js';

if (!requireAuth()) { /* redirect */ }
initTheme(); fillSidebar(); highlightNav();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);

// ── State ──────────────────────────────────────────────────────────────────
let projectId = null, moduleId = null;
let filters   = {};
let pollTimer = null;
let delTarget = null;

// Read projectId/moduleId from URL (when arriving from modules page)
const urlParams = new URLSearchParams(location.search);
const initProject = urlParams.get('projectId');
const initModule  = urlParams.get('moduleId');

// ── DOM ────────────────────────────────────────────────────────────────────
const projSel   = document.getElementById('proj-select');
const modSel    = document.getElementById('mod-select');
const newRunBtn = document.getElementById('new-run-btn');
const panel     = document.getElementById('filter-panel');
const toggleBtn = document.getElementById('filter-toggle');
const closeBtn  = document.getElementById('filter-close');
const badge_el  = document.getElementById('filter-badge');
const chipRow   = document.getElementById('chip-row');
const quickBar  = document.getElementById('quick-bar');
const fSearch   = document.getElementById('f-search');
const fCreatedBy= document.getElementById('f-createdby');
const fFrom     = document.getElementById('f-from');
const fTo       = document.getElementById('f-to');
const fReset    = document.getElementById('f-reset');
const sortSel   = document.getElementById('sort-sel');
const runsCount = document.getElementById('runs-count');
const container = document.getElementById('runs-container');
const pagination= document.getElementById('pagination');
const breadcrumb= document.getElementById('breadcrumb');

// ── Boot ───────────────────────────────────────────────────────────────────
(async () => {
    await loadProjects();
    renderQuickBar();
    wireFilterEvents();

    if (initProject) {
        projSel.value = initProject;
        await onProjectChange();
        if (initModule) {
            modSel.value = initModule;
            await onModuleChange();
        }
    }
})();

// ── Projects / modules selectors ───────────────────────────────────────────
async function loadProjects() {
    try {
        const list = await api.projects.list();
        projSel.innerHTML = '<option value="">Select project…</option>' +
            list.map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('');
    } catch (e) { showToast('Failed to load projects: ' + e.message, 'error'); }
}

projSel.addEventListener('change', onProjectChange);
modSel.addEventListener('change', onModuleChange);

async function onProjectChange() {
    projectId = projSel.value || null;
    moduleId  = null;
    modSel.innerHTML = '<option value="">Select module…</option>';
    newRunBtn.disabled = true;
    clearAll();
    breadcrumb.innerHTML = '';
    if (!projectId) return;

    try {
        const mods = await api.modules.list(projectId);
        modSel.innerHTML = '<option value="">Select module…</option>' +
            mods.map(m => `<option value="${esc(m.id)}">${esc(m.name)}</option>`).join('');
    } catch (e) { showToast('Failed to load modules: ' + e.message, 'error'); }
}

async function onModuleChange() {
    moduleId = modSel.value || null;
    newRunBtn.disabled = !moduleId;
    clearAll();
    if (!projectId || !moduleId) return;

    // Breadcrumb
    try {
        const [proj, mod] = await Promise.all([api.projects.get(projectId), api.modules.get(moduleId)]);
        breadcrumb.innerHTML = `<a href="projects.html">Projects</a> / <a href="modules.html?projectId=${esc(projectId)}">${esc(proj.name)}</a> / ${esc(mod.name)}`;
    } catch {}

    filters = load(projectId, moduleId);
    restoreUI();
    await loadMeta();
    fetchRuns(0);
}

newRunBtn.addEventListener('click', () => {
    location.href = `run-editor.html?projectId=${projectId}&moduleId=${moduleId}`;
});

// ── Filter meta ────────────────────────────────────────────────────────────
async function loadMeta() {
    try {
        const meta = await api.runs.filterMeta(projectId, moduleId);

        // createdBy dropdown
        fCreatedBy.innerHTML = '<option value="">All users</option>' +
            (meta.createdByUsers||[]).map(u => `<option value="${esc(u)}">${esc(u)}</option>`).join('');
        if (filters.createdBy) fCreatedBy.value = filters.createdBy;

        // tags checkboxes
        const tags = meta.availableTags || [];
        const fc = document.getElementById('f-tags');
        if (!tags.length) {
            fc.innerHTML = '<span style="font-size:12px;color:var(--text-muted)">No tags</span>';
        } else {
            fc.innerHTML = tags.map(t => `
        <label><input type="checkbox" name="tag" value="${esc(t)}"
          ${(filters.tags||[]).includes(t) ? 'checked' : ''} /> ${esc(t)}</label>
      `).join('');
            fc.querySelectorAll('input').forEach(cb => cb.addEventListener('change', onChange));
        }
    } catch {}
}

// ── Filter events ──────────────────────────────────────────────────────────
function wireFilterEvents() {
    toggleBtn.addEventListener('click', () => {
        panel.classList.toggle('hidden');
        toggleBtn.setAttribute('aria-expanded', !panel.classList.contains('hidden'));
    });
    closeBtn.addEventListener('click', () => { panel.classList.add('hidden'); });

    fSearch.addEventListener('input', debounce(onChange, 350));

    document.querySelectorAll('#f-status input, #f-type input').forEach(cb =>
        cb.addEventListener('change', onChange));

    fCreatedBy.addEventListener('change', onChange);
    fFrom.addEventListener('change', onChange);
    fTo.addEventListener('change', onChange);
    sortSel.addEventListener('change', onChange);
    fReset.addEventListener('click', () => {
        if (!projectId || !moduleId) return;
        filters = reset(projectId, moduleId);
        restoreUI();
        fetchRuns(0);
    });
}

function onChange() {
    if (!projectId || !moduleId) return;
    collectFilters();
    save(projectId, moduleId, filters);
    fetchRuns(0);
}

function collectFilters() {
    filters.status    = [...document.querySelectorAll('#f-status input:checked')].map(c=>c.value).join(',');
    filters.type      = [...document.querySelectorAll('#f-type input:checked')].map(c=>c.value).join(',');
    filters.tags      = [...document.querySelectorAll('#f-tags input:checked')].map(c=>c.value);
    filters.search    = fSearch.value.trim();
    filters.createdBy = fCreatedBy.value;
    filters.from      = fFrom.value ? new Date(fFrom.value).toISOString() : '';
    filters.to        = fTo.value   ? new Date(fTo.value).toISOString()   : '';
    filters.sort      = sortSel.value;
}

function restoreUI() {
    const statuses = (filters.status||'').split(',');
    const types    = (filters.type||'').split(',');
    document.querySelectorAll('#f-status input').forEach(cb => cb.checked = statuses.includes(cb.value));
    document.querySelectorAll('#f-type input').forEach(cb  => cb.checked = types.includes(cb.value));
    fSearch.value    = filters.search    || '';
    fCreatedBy.value = filters.createdBy || '';
    fFrom.value      = filters.from ? filters.from.slice(0,16) : '';
    fTo.value        = filters.to   ? filters.to.slice(0,16)   : '';
    sortSel.value    = filters.sort || '-createdAt';
    // restore tag checks
    document.querySelectorAll('#f-tags input').forEach(cb =>
        cb.checked = (filters.tags||[]).includes(cb.value));
}

// ── Quick filters ──────────────────────────────────────────────────────────
function renderQuickBar() {
    quickBar.innerHTML = QUICK.map(q =>
        `<button class="quick-chip" data-id="${q.id}">${q.label}</button>`
    ).join('');
    quickBar.querySelectorAll('.quick-chip').forEach(btn => {
        btn.addEventListener('click', () => {
            if (!projectId || !moduleId) return;
            if (btn.classList.contains('active')) {
                filters = reset(projectId, moduleId);
                quickBar.querySelectorAll('.quick-chip').forEach(b => b.classList.remove('active'));
            } else {
                const qf = QUICK.find(q => q.id === btn.dataset.id);
                filters = qf.apply(filters);
                quickBar.querySelectorAll('.quick-chip').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            }
            save(projectId, moduleId, filters);
            restoreUI();
            fetchRuns(0);
        });
    });
}

// ── Fetch & render runs ────────────────────────────────────────────────────
async function fetchRuns(page = 0) {
    filters.page = page;
    filters.size = 20;
    showSpinner(container);
    pagination.innerHTML = '';

    try {
        const data = await api.runs.list(projectId, moduleId, filters);
        renderCount(data);
        renderChips();
        renderBadge();
        if (!data.results?.length) {
            showEmpty(container, 'No runs match your filters');
        } else {
            renderTable(data.results);
        }
        renderPagination(data.page, data.totalPages);
    } catch (e) {
        showErr(container, e.message);
        showToast(e.message, 'error');
    }
}

function renderCount(data) {
    const n = activeCount(filters);
    runsCount.textContent = n
        ? `${data.totalCount} run${data.totalCount!==1?'s':''} matching filters`
        : `${data.totalCount} run${data.totalCount!==1?'s':''}`;
}

function renderBadge() {
    const n = activeCount(filters);
    badge_el.textContent = n;
    badge_el.style.display = n ? 'inline-flex' : 'none';
}

function renderChips() {
    const chips = toChips(filters);
    if (!chips.length) { chipRow.innerHTML = ''; return; }
    chipRow.innerHTML = chips.map(c => `
    <span class="filter-chip">
      ${esc(c.label)}
      <button class="chip-x" data-key="${esc(c.key)}" data-val="${esc(c.val||'')}" aria-label="Remove">×</button>
    </span>`).join('');
    chipRow.querySelectorAll('.chip-x').forEach(btn => {
        btn.addEventListener('click', () => {
            const k = btn.dataset.key, v = btn.dataset.val;
            if (k === 'tag') {
                filters.tags = (filters.tags||[]).filter(t => t !== v);
                const cb = document.querySelector(`#f-tags input[value="${v}"]`);
                if (cb) cb.checked = false;
            } else {
                filters[k] = '';
                if (k === 'from') fFrom.value = '';
                if (k === 'to')   fTo.value   = '';
                // uncheck corresponding status/type box
                const cb = document.querySelector(`#f-status input[value="${v}"], #f-type input[value="${v}"]`);
                if (cb) cb.checked = false;
            }
            save(projectId, moduleId, filters);
            restoreUI();
            fetchRuns(0);
        });
    });
}

function renderTable(runList) {
    container.innerHTML = `
    <div class="table-wrap">
      <table class="table" aria-label="Runs list">
        <thead>
          <tr>
            <th>Run</th>
            <th>Status</th>
            <th>Type</th>
            <th style="text-align:center">Scenarios</th>
            <th>Created By</th>
            <th>Tags</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          ${runList.map(r => `
            <tr data-rid="${esc(r.id)}">
              <td>
                <div class="run-name">${esc(r.runName)}</div>
                <div class="run-id">${esc(r.id)}</div>
              </td>
              <td>${badge(r.status)}</td>
              <td style="font-size:12px;color:var(--text-muted)">${esc(r.runType||'—')}</td>
              <td style="text-align:center">${r.scenarioCount??0}</td>
              <td>${esc(r.createdBy||'—')}</td>
              <td>${(r.tags||[]).map(t=>`<span class="tag">${esc(t)}</span>`).join('')||'—'}</td>
              <td>
                <div style="font-size:12px">${fmtDate(r.createdAt)}</div>
                <div style="font-size:11px;color:var(--text-muted)">${timeAgo(r.updatedAt)}</div>
              </td>
              <td>
                <div class="row-actions">
                  <button class="btn btn-ghost btn-sm btn-icon act-view"
                          data-id="${esc(r.id)}" title="View" aria-label="View run">👁️</button>
                  <button class="btn btn-ghost btn-sm btn-icon act-edit"
                          data-id="${esc(r.id)}" title="Edit" aria-label="Edit run"
                          ${r.status==='RUNNING'?'disabled':''}>✏️</button>
                  <button class="btn btn-ghost btn-sm btn-icon act-clone"
                          data-id="${esc(r.id)}" title="Clone" aria-label="Clone run">📋</button>
                  <button class="btn btn-primary btn-sm act-run"
                          data-id="${esc(r.id)}" data-name="${esc(r.runName)}"
                          title="Run Now" aria-label="Execute run"
                          ${r.status==='RUNNING'?'disabled':''}>
                    ${r.status==='RUNNING'?'⏳':'▶'}
                  </button>
                  <button class="btn btn-ghost btn-sm btn-icon act-del"
                          data-id="${esc(r.id)}" data-name="${esc(r.runName)}"
                          title="Delete" aria-label="Delete run"
                          ${r.status==='RUNNING'?'disabled':''}>🗑️</button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>`;

    container.querySelectorAll('.act-view').forEach(b =>
        b.addEventListener('click', () => location.href = `run-detail.html?runId=${b.dataset.id}`));
    container.querySelectorAll('.act-edit').forEach(b =>
        b.addEventListener('click', () => location.href = `run-editor.html?runId=${b.dataset.id}`));
    container.querySelectorAll('.act-clone').forEach(b =>
        b.addEventListener('click', () => cloneRun(b.dataset.id)));
    container.querySelectorAll('.act-run').forEach(b =>
        b.addEventListener('click', () => executeRun(b.dataset.id, b.dataset.name, b)));
    container.querySelectorAll('.act-del').forEach(b =>
        b.addEventListener('click', () => openDelModal(b.dataset.id, b.dataset.name)));
}

// ── Pagination ─────────────────────────────────────────────────────────────
function renderPagination(cur, total) {
    if (total <= 1) { pagination.innerHTML = ''; return; }

    const max = 7;
    let pages = [];
    if (total <= max) {
        pages = Array.from({length: total}, (_,i) => i);
    } else {
        pages = [0];
        if (cur > 2) pages.push('…');
        for (let i = Math.max(1, cur-1); i <= Math.min(total-2, cur+1); i++) pages.push(i);
        if (cur < total-3) pages.push('…');
        pages.push(total-1);
    }

    pagination.innerHTML = `
    <button class="pg-btn" id="pg-prev" ${cur===0?'disabled':''} aria-label="Previous">‹</button>
    ${pages.map(p => p==='…'
        ? `<span class="pg-ellipsis">…</span>`
        : `<button class="pg-btn ${p===cur?'active':''}" data-p="${p}" aria-label="Page ${p+1}" aria-current="${p===cur?'page':false}">${p+1}</button>`
    ).join('')}
    <button class="pg-btn" id="pg-next" ${cur>=total-1?'disabled':''} aria-label="Next">›</button>`;

    document.getElementById('pg-prev')?.addEventListener('click', () => fetchRuns(cur-1));
    document.getElementById('pg-next')?.addEventListener('click', () => fetchRuns(cur+1));
    pagination.querySelectorAll('[data-p]').forEach(b =>
        b.addEventListener('click', () => fetchRuns(Number(b.dataset.p))));
}

// ── Actions ────────────────────────────────────────────────────────────────
async function cloneRun(id) {
    try {
        showToast('Cloning…', 'info', 1500);
        const c = await api.runs.clone(id);
        showToast(`Cloned as "${c.runName}"`, 'success');
        location.href = `run-editor.html?runId=${c.id}`;
    } catch (e) { showToast('Clone failed: ' + e.message, 'error'); }
}

async function executeRun(id, name, btn) {
    btn.disabled = true; btn.textContent = '⏳';
    showToast(`Starting "${name}"…`, 'info', 2000);
    try {
        await api.runs.execute(id);
        showToast('Run started — watching for updates…', 'info', 3000);
        clearInterval(pollTimer);
        pollTimer = setInterval(async () => {
            try {
                const r = await api.runs.get(id);
                updateRowBadge(id, r.status);
                if (r.status !== 'RUNNING') {
                    clearInterval(pollTimer);
                    const t = r.status==='PASSED' ? 'success' : r.status==='FAILED' ? 'error' : 'info';
                    showToast(`"${r.runName}" → ${r.status}`, t);
                    fetchRuns(filters.page||0);
                }
            } catch { clearInterval(pollTimer); }
        }, 3000);
    } catch (e) {
        btn.disabled = false; btn.textContent = '▶';
        showToast('Execute failed: ' + e.message, 'error');
    }
}

function updateRowBadge(id, status) {
    const row = container.querySelector(`tr[data-rid="${id}"]`);
    if (row) row.cells[1].innerHTML = badge(status);
}

// ── Delete modal ───────────────────────────────────────────────────────────
function openDelModal(id, name) {
    delTarget = id;
    document.getElementById('del-name').textContent = name;
    document.getElementById('del-modal').style.display = 'flex';
}
function closeDelModal() {
    document.getElementById('del-modal').style.display = 'none';
    delTarget = null;
}
document.getElementById('del-close').addEventListener('click', closeDelModal);
document.getElementById('del-cancel').addEventListener('click', closeDelModal);
document.getElementById('del-modal').addEventListener('click', e => {
    if (e.target.id === 'del-modal') closeDelModal();
});
document.getElementById('del-confirm').addEventListener('click', async () => {
    if (!delTarget) return;
    closeDelModal();
    try {
        await api.runs.delete(delTarget);
        showToast('Run deleted', 'success');
        fetchRuns(filters.page||0);
    } catch (e) { showToast('Delete failed: ' + e.message, 'error'); }
    delTarget = null;
});

// ── Helpers ────────────────────────────────────────────────────────────────
function clearAll() {
    container.innerHTML = `<div class="empty-state"><div class="state-icon">📂</div><p>Select a project and module to view runs</p></div>`;
    pagination.innerHTML = '';
    runsCount.textContent = '—';
    chipRow.innerHTML = '';
    badge_el.style.display = 'none';
}