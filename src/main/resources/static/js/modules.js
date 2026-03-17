import * as api from './api.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav,
    showToast, showSpinner, showEmpty, showErr, esc, fmtDate, timeAgo, getUser } from './utils.js';

if (!requireAuth()) { /* redirect */ }
initTheme(); fillSidebar(); highlightNav();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);

const params    = new URLSearchParams(location.search);
const projectId = params.get('projectId');
let editingId   = null;
let allModules  = [];

// ── Init ──────────────────────────────────────────────────────────────────
document.getElementById('new-module-btn').addEventListener('click', () => openModal());
document.getElementById('modal-close-btn').addEventListener('click', closeModal);
document.getElementById('modal-cancel-btn').addEventListener('click', closeModal);
document.getElementById('module-form').addEventListener('submit', handleSubmit);
document.getElementById('search-input').addEventListener('input', () => render(allModules));
document.getElementById('module-modal').addEventListener('click', e => {
    if (e.target.id === 'module-modal') closeModal();
});

(async () => {
    if (!projectId) {
        showErr(document.getElementById('modules-container'), 'No project selected. Go to <a href="projects.html">Projects</a>.');
        return;
    }
    try {
        const proj = await api.projects.get(projectId);
        document.getElementById('page-title').textContent    = proj.name;
        document.getElementById('section-title').textContent = proj.name + ' — Modules';
        document.getElementById('breadcrumb').innerHTML =
            `<a href="projects.html">Projects</a> / ${esc(proj.name)}`;
        document.getElementById('new-module-btn').disabled = false;
    } catch (e) {
        showToast('Could not load project: ' + e.message, 'error');
    }
    await load();
})();

// ── Load ──────────────────────────────────────────────────────────────────
async function load() {
    showSpinner(document.getElementById('modules-container'));
    try {
        allModules = await api.modules.list(projectId);
        render(allModules);
    } catch (e) {
        showErr(document.getElementById('modules-container'), e.message);
        showToast(e.message, 'error');
    }
}

function render(list) {
    const q = (document.getElementById('search-input').value || '').toLowerCase();
    const filtered = q
        ? list.filter(m => m.name.toLowerCase().includes(q) || (m.description||'').toLowerCase().includes(q))
        : list;

    document.getElementById('module-count').textContent =
        `${filtered.length} module${filtered.length !== 1 ? 's' : ''}`;

    const container = document.getElementById('modules-container');
    if (!filtered.length) {
        showEmpty(container, q ? 'No modules match your search' : 'No modules yet. Create one to get started.');
        return;
    }

    container.innerHTML = `
    <div class="table-wrap">
      <table class="table" aria-label="Modules">
        <thead>
          <tr>
            <th>Module</th>
            <th>Description</th>
            <th>Created By</th>
            <th>Updated</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          ${filtered.map(m => `
            <tr>
              <td>
                <div class="run-name">${esc(m.name)}</div>
                <div class="run-id">${esc(m.id)}</div>
              </td>
              <td style="color:var(--text-secondary);font-size:13px;max-width:260px;">${esc(m.description||'—')}</td>
              <td>${esc(m.createdBy||'—')}</td>
              <td>
                <div style="font-size:12px;">${fmtDate(m.createdAt)}</div>
                <div style="font-size:11px;color:var(--text-muted);">${timeAgo(m.updatedAt)}</div>
              </td>
              <td>
                <div class="row-actions">
                  <a href="runs.html?projectId=${esc(projectId)}&moduleId=${esc(m.id)}"
                     class="btn btn-primary btn-sm">▶ Runs</a>
                  <button class="btn btn-ghost btn-sm btn-icon action-edit"
                          data-id="${esc(m.id)}" title="Edit">✏️</button>
                  <button class="btn btn-ghost btn-sm btn-icon action-delete"
                          data-id="${esc(m.id)}" data-name="${esc(m.name)}" title="Delete">🗑️</button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>`;

    container.querySelectorAll('.action-edit').forEach(b =>
        b.addEventListener('click', () => openModal(b.dataset.id)));
    container.querySelectorAll('.action-delete').forEach(b =>
        b.addEventListener('click', () => confirmDelete(b.dataset.id, b.dataset.name)));
}

// ── Modal ─────────────────────────────────────────────────────────────────
async function openModal(id = null) {
    editingId = id;
    document.getElementById('module-form').reset();
    document.getElementById('modal-title').textContent = id ? 'Edit Module' : 'New Module';
    if (id) {
        try {
            const m = await api.modules.get(id);
            document.getElementById('field-name').value        = m.name        || '';
            document.getElementById('field-description').value = m.description || '';
        } catch (e) { showToast('Could not load module: ' + e.message, 'error'); return; }
    }
    document.getElementById('module-modal').style.display = 'flex';
    document.getElementById('field-name').focus();
}
function closeModal() {
    document.getElementById('module-modal').style.display = 'none';
    editingId = null;
}

// ── Submit ────────────────────────────────────────────────────────────────
async function handleSubmit(e) {
    e.preventDefault();
    const user = getUser() || {};
    const data = {
        name:        document.getElementById('field-name').value.trim(),
        description: document.getElementById('field-description').value.trim(),
        createdBy:   user.userId || user.username,
    };
    if (!data.name) { showToast('Module name is required', 'warning'); return; }

    const btn = document.getElementById('modal-save-btn');
    btn.disabled = true; btn.textContent = 'Saving…';
    try {
        if (editingId) {
            const updated = await api.modules.update(editingId, data);
            allModules = allModules.map(m => m.id === editingId ? updated : m);
            showToast('Module updated', 'success');
        } else {
            const created = await api.modules.create(projectId, data);
            allModules = [created, ...allModules];
            showToast('Module created', 'success');
        }
        closeModal(); render(allModules);
    } catch (e) {
        showToast('Save failed: ' + e.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Save';
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
function confirmDelete(id, name) {
    if (!confirm(`Delete module "${name}"?\nAll runs inside it will also be deleted.`)) return;
    api.modules.delete(id)
        .then(() => {
            allModules = allModules.filter(m => m.id !== id);
            render(allModules); showToast(`Deleted "${name}"`, 'success');
        })
        .catch(e => showToast('Delete failed: ' + e.message, 'error'));
}