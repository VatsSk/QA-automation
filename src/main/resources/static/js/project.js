import * as api from './api.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav,
    showToast, showSpinner, showEmpty, showErr, esc, timeAgo, getUser } from './utils.js';

if (!requireAuth()) { /* redirect */ }
initTheme(); fillSidebar(); highlightNav();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);

let allProjects  = [];
let editingId    = null;

// ── Init ──────────────────────────────────────────────────────────────────
document.getElementById('new-project-btn').addEventListener('click', () => openModal());
document.getElementById('modal-close-btn').addEventListener('click', closeModal);
document.getElementById('modal-cancel-btn').addEventListener('click', closeModal);
document.getElementById('project-form').addEventListener('submit', handleSubmit);
document.getElementById('search-input').addEventListener('input', () => render(allProjects));
document.getElementById('project-modal').addEventListener('click', e => {
    if (e.target.id === 'project-modal') closeModal();
});

load();

// ── Load ──────────────────────────────────────────────────────────────────
async function load() {
    const grid = document.getElementById('projects-grid');
    showSpinner(grid);
    try {
        const user = getUser() || {};
        allProjects = await api.projects.list(user.userId);
        render(allProjects);
    } catch (e) {
        showErr(document.getElementById('projects-grid'), e.message);
        showToast(e.message, 'error');
    }
}

function render(list) {
    const q = (document.getElementById('search-input').value || '').toLowerCase();
    const filtered = q
        ? list.filter(p => p.name.toLowerCase().includes(q) ||
            (p.description||'').toLowerCase().includes(q) ||
            (p.tags||[]).some(t => t.toLowerCase().includes(q)))
        : list;

    document.getElementById('project-count').textContent =
        `${filtered.length} project${filtered.length !== 1 ? 's' : ''}`;

    const grid = document.getElementById('projects-grid');
    if (!filtered.length) {
        showEmpty(grid, q ? 'No projects match your search' : 'No projects yet. Create one to get started.');
        return;
    }

    grid.innerHTML = filtered.map(p => `
    <div class="card">
      <div class="card-header">
        <div style="min-width:0">
          <div class="proj-name">${esc(p.name)}</div>
          <div class="proj-meta">By ${esc(p.createdBy||'—')} · ${timeAgo(p.createdAt)}</div>
        </div>
        <div style="display:flex;gap:4px;flex-shrink:0">
          <button class="btn btn-ghost btn-sm btn-icon action-edit"   data-id="${esc(p.id)}" title="Edit">✏️</button>
          <button class="btn btn-ghost btn-sm btn-icon action-delete" data-id="${esc(p.id)}" data-name="${esc(p.name)}" title="Delete">🗑️</button>
        </div>
      </div>
      <div class="card-body">
        <p class="proj-desc">${esc(p.description||'No description')}</p>
        ${p.baseUrl ? `<div class="proj-url">🔗 <a href="${esc(p.baseUrl)}" target="_blank" rel="noopener">${esc(p.baseUrl)}</a></div>` : ''}
        <div class="proj-tags">${(p.tags||[]).map(t=>`<span class="tag">${esc(t)}</span>`).join('')}</div>
        <div class="proj-footer" style="margin-top:14px;">
          <a href="modules.html?projectId=${esc(p.id)}" class="btn btn-primary btn-sm">🗂️ Modules</a>
          <a href="runs.html?projectId=${esc(p.id)}"    class="btn btn-secondary btn-sm">▶ Runs</a>
        </div>
      </div>
    </div>
  `).join('');

    grid.querySelectorAll('.action-edit').forEach(b =>
        b.addEventListener('click', () => openModal(b.dataset.id)));
    grid.querySelectorAll('.action-delete').forEach(b =>
        b.addEventListener('click', () => confirmDelete(b.dataset.id, b.dataset.name)));
}

// ── Modal ─────────────────────────────────────────────────────────────────
async function openModal(id = null) {
    editingId = id;
    document.getElementById('project-form').reset();
    document.getElementById('modal-title').textContent = id ? 'Edit Project' : 'New Project';

    if (id) {
        try {
            const p = await api.projects.get(id);
            document.getElementById('field-name').value        = p.name        || '';
            document.getElementById('field-description').value = p.description || '';
            document.getElementById('field-baseUrl').value     = p.baseUrl     || '';
            document.getElementById('field-tags').value        = (p.tags||[]).join(', ');
        } catch (e) { showToast('Could not load project: ' + e.message, 'error'); return; }
    }

    document.getElementById('project-modal').style.display = 'flex';
    document.getElementById('field-name').focus();
}

function closeModal() {
    document.getElementById('project-modal').style.display = 'none';
    editingId = null;
}

// ── Submit ────────────────────────────────────────────────────────────────
async function handleSubmit(e) {
    e.preventDefault();
    const user = getUser() || {};
    const data = {
        name:        document.getElementById('field-name').value.trim(),
        description: document.getElementById('field-description').value.trim(),
        baseUrl:     document.getElementById('field-baseUrl').value.trim(),
        createdBy:   user.userId || user.username,
        tags:        document.getElementById('field-tags').value.split(',').map(t=>t.trim()).filter(Boolean),
    };
    if (!data.name) { showToast('Project name is required', 'warning'); return; }

    const btn = document.getElementById('modal-save-btn');
    btn.disabled = true; btn.textContent = 'Saving…';
    try {
        if (editingId) {
            const updated = await api.projects.update(editingId, data);
            allProjects = allProjects.map(p => p.id === editingId ? updated : p);
            showToast('Project updated', 'success');
        } else {
            const created = await api.projects.create(data);
            allProjects = [created, ...allProjects];
            showToast('Project created', 'success');
        }
        closeModal();
        render(allProjects);
    } catch (e) {
        showToast('Save failed: ' + e.message, 'error');
    } finally {
        btn.disabled = false; btn.textContent = 'Save';
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
function confirmDelete(id, name) {
    if (!confirm(`Delete "${name}"?\nAll modules and runs inside it will also be deleted.`)) return;
    api.projects.delete(id)
        .then(() => {
            allProjects = allProjects.filter(p => p.id !== id);
            render(allProjects);
            showToast(`Deleted "${name}"`, 'success');
        })
        .catch(e => showToast('Delete failed: ' + e.message, 'error'));
}