/**
 * utils.js — Shared helpers used by every page.
 * No page-specific logic here; just pure utility functions.
 */

// ── HTML escape ────────────────────────────────────────────────────────────
export function esc(s) {
    return String(s ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// ── Avatar initials ────────────────────────────────────────────────────────
export function initials(name) {
    return (name || '?').slice(0, 2).toUpperCase();
}

// ── Date helpers ───────────────────────────────────────────────────────────
export function fmtDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
        + ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

export function timeAgo(iso) {
    if (!iso) return '—';
    const s = Math.floor((Date.now() - new Date(iso)) / 1000);
    if (s < 60)    return `${s}s ago`;
    if (s < 3600)  return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
}

// ── Status badge ───────────────────────────────────────────────────────────
export function statusBadge(status) {
    const cls = (status || 'pending').toLowerCase();
    return `<span class="badge badge-${cls}">${esc(status || '—')}</span>`;
}

// ── Toast notifications ────────────────────────────────────────────────────
export function toast(msg, type = 'info', ms = 3800) {
    let container = document.getElementById('toasts');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toasts';
        document.body.appendChild(container);
    }
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.innerHTML = `<div class="toast-dot"></div><span>${esc(msg)}</span>`;
    container.appendChild(el);
    const dismiss = () => {
        el.style.opacity = '0';
        el.style.transform = 'translateX(12px)';
        el.style.transition = 'all .2s';
        setTimeout(() => el.remove(), 220);
    };
    el.onclick = dismiss;
    setTimeout(dismiss, ms);
}

// ── Modal ──────────────────────────────────────────────────────────────────
export function openModal(html) {
    let root = document.getElementById('modal-root');
    if (!root) {
        root = document.createElement('div');
        root.id = 'modal-root';
        document.body.appendChild(root);
    }
    root.innerHTML = `<div class="overlay" id="modal-backdrop">${html}</div>`;
    document.getElementById('modal-backdrop').addEventListener('click', e => {
        if (e.target.id === 'modal-backdrop') closeModal();
    });
}

export function closeModal() {
    const root = document.getElementById('modal-root');
    if (root) root.innerHTML = '';
}

// ── Auth helpers ───────────────────────────────────────────────────────────
export function requireLogin() {
    const user = localStorage.getItem('qa_user');
    const tok  = localStorage.getItem('qa_token');
    if (!user || !tok) {
        window.location.href = '/login.html';
        return null;
    }
    try { return JSON.parse(user); } catch { return null; }
}

export function getUser() {
    try { return JSON.parse(localStorage.getItem('qa_user') || 'null'); } catch { return null; }
}

export function logout() {
    localStorage.removeItem('qa_token');
    localStorage.removeItem('qa_user');
    window.location.href = '/login.html';
}

// ── Theme ──────────────────────────────────────────────────────────────────
export function initTheme() {
    const t = localStorage.getItem('qa_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', t);
    updateThemeBtns(t);
}

export function toggleTheme() {
    const cur  = document.documentElement.getAttribute('data-theme') || 'dark';
    const next = cur === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('qa_theme', next);
    updateThemeBtns(next);
}

function updateThemeBtns(theme) {
    document.querySelectorAll('.theme-btn').forEach(b => {
        b.textContent = theme === 'dark' ? '☀' : '☾';
    });
}

// ── Sidebar builder ────────────────────────────────────────────────────────
// Call once per page to inject the sidebar HTML and wire up tree expand/collapse.
// `activePage`: 'projects' | 'modules' | 'runs'  (for highlighting)
export async function buildSidebar(activePage, api) {
    const container = document.getElementById('sidebar');
    if (!container) return;

    const user = getUser() || {};
    const theme = localStorage.getItem('qa_theme') || 'dark';

    // Load projects for sidebar tree
    let projectList = [];
    try {
        projectList = await api.projects.list(user.id || user._id);
    } catch { /* sidebar tree stays empty */ }

    // Build tree rows
    const sbTree = projectList.map(p => {
        const pid  = p.id || p._id;
        const isActive = activePage === 'modules'
            && sessionStorage.getItem('qa_projectId') === pid;
        return `
      <div class="sb-item ${isActive ? 'active' : ''}" id="sbp-${pid}"
        data-pid="${pid}"
        onclick="toggleSbProject('${pid}', this)">
        <span style="font-size:13px;flex-shrink:0">📁</span>
        <span class="sb-name">${esc(p.name)}</span>
        <span class="sb-count"></span>
        <span class="sb-arrow">▶</span>
      </div>
      <div class="sb-children" id="sbchildren-${pid}" style="display:none"></div>`;
    }).join('');

    container.innerHTML = `
    <div class="sb-logo">
      <div class="mark">QA</div>
      <div>
        <div class="brand-name">QA Studio</div>
        <div class="brand-sub">Test Manager</div>
      </div>
      <button class="btn-icon mla theme-btn" onclick="toggleTheme()"
        title="Toggle theme">${theme === 'dark' ? '☀' : '☾'}</button>
    </div>
    <div class="sb-scroll">
      <div class="sb-section">Workspace</div>
      <div class="sb-item ${activePage === 'projects' ? 'active' : ''}"
        onclick="window.location.href='/projects.html'">
        <span>🏠</span><span class="sb-name">All Projects</span>
      </div>
      <div class="sb-section" style="margin-top:6px">Projects</div>
      ${sbTree || '<div style="font-size:12px;color:var(--tx3);padding:6px 14px">No projects yet</div>'}
    </div>
    <div class="sb-bottom">
      <div class="sb-user">
        <div class="sb-av">${initials(user.username)}</div>
        <div class="sb-uname">${esc(user.username || '—')}</div>
        <button class="btn-icon mla" onclick="logoutUser()" title="Logout">⏏</button>
      </div>
    </div>`;

    // Expose helpers to inline handlers
    window.logoutUser = logout;
    window.toggleTheme = toggleTheme;
    window.toggleSbProject = async (pid, el) => {
        const childContainer = document.getElementById(`sbchildren-${pid}`);
        if (!childContainer) return;
        const isOpen = childContainer.style.display !== 'none';
        if (isOpen) {
            childContainer.style.display = 'none';
            el.classList.remove('open');
        } else {
            el.classList.add('open');
            childContainer.style.display = 'block';
            if (!childContainer.dataset.loaded) {
                childContainer.dataset.loaded = '1';
                childContainer.innerHTML = `<div style="padding:6px 14px 6px 28px;font-size:11.5px;color:var(--tx3)">Loading…</div>`;
                try {
                    const mods = await api.modules.list(pid);
                    childContainer.innerHTML = mods.length
                        ? mods.map(m => {
                            const mid = m.id || m._id;
                            return `<div class="sb-child"
                  onclick="window.location.href='/runs.html?projectId=${pid}&moduleId=${mid}'">
                  <span style="font-size:12px;flex-shrink:0">🗂️</span>
                  <span class="sb-name">${esc(m.name)}</span>
                </div>`;
                        }).join('')
                        : `<div class="sb-child" style="color:var(--tx3);font-style:italic">No modules</div>`;
                } catch {
                    childContainer.innerHTML = `<div class="sb-child" style="color:var(--rd)">Failed to load</div>`;
                }
            }
        }
    };
}

// ── Pagination HTML ────────────────────────────────────────────────────────
export function paginationHTML(current, total, clickFn) {
    if (total <= 1) return '';
    const pages = [];
    if (total <= 7) {
        for (let i = 0; i < total; i++) pages.push(i);
    } else {
        pages.push(0, 1);
        if (current > 3) pages.push(-1);
        for (let i = Math.max(2, current - 1); i <= Math.min(total - 3, current + 1); i++) pages.push(i);
        if (current < total - 4) pages.push(-1);
        pages.push(total - 2, total - 1);
    }
    const btns = pages.map(p =>
        p === -1
            ? `<span style="padding:0 5px;color:var(--tx3)">…</span>`
            : `<button class="pag-btn ${p === current ? 'active' : ''}" onclick="${clickFn}(${p})">${p + 1}</button>`
    ).join('');
    return `<div class="pagination">
    <button class="pag-btn" onclick="${clickFn}(${current - 1})" ${current === 0 ? 'disabled' : ''}>‹</button>
    ${btns}
    <button class="pag-btn" onclick="${clickFn}(${current + 1})" ${current === total - 1 ? 'disabled' : ''}>›</button>
  </div>`;
}

// ── Lightbox ───────────────────────────────────────────────────────────────
export function showLightbox(src) {
    const lb = document.createElement('div');
    lb.className = 'lightbox';
    lb.onclick = () => lb.remove();
    lb.innerHTML = `<img src="${esc(src)}" alt="Screenshot">`;
    document.body.appendChild(lb);
}

// ── Debounce ───────────────────────────────────────────────────────────────
export function debounce(fn, ms = 300) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}