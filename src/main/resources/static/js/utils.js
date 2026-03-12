/**
 * utils.js — Shared UI utilities
 */

// ── Debounce ──────────────────────────────────────────────────────────────
export function debounce(fn, ms = 300) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

// ── Theme ─────────────────────────────────────────────────────────────────
export function initTheme() {
    const saved = localStorage.getItem('qa_theme') || 'light';
    document.documentElement.setAttribute('data-theme', saved);
    updateThemeBtn(saved);
}
export function toggleTheme() {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('qa_theme', next);
    updateThemeBtn(next);
}
function updateThemeBtn(theme) {
    const btn = document.getElementById('theme-btn');
    if (btn) btn.textContent = theme === 'dark' ? '☀️' : '🌙';
}

// ── Toast ─────────────────────────────────────────────────────────────────
let _toastEl;
function getContainer() {
    if (!_toastEl) {
        _toastEl = document.createElement('div');
        _toastEl.className = 'toast-container';
        document.body.appendChild(_toastEl);
    }
    return _toastEl;
}
export function showToast(msg, type = 'info', ms = 4000) {
    const c = getContainer();
    const t = document.createElement('div');
    t.className = `toast toast-${type}`;
    t.setAttribute('role', 'alert');
    const icons = { success: '✓', error: '✕', info: 'ℹ', warning: '⚠' };
    t.innerHTML = `
    <span class="toast-icon">${icons[type] || 'ℹ'}</span>
    <span class="toast-msg">${esc(msg)}</span>
    <button class="toast-close" aria-label="Dismiss">×</button>`;
    t.querySelector('.toast-close').onclick = () => dismiss(t);
    c.appendChild(t);
    requestAnimationFrame(() => t.classList.add('show'));
    const timer = setTimeout(() => dismiss(t), ms);
    t.onmouseenter = () => clearTimeout(timer);
    function dismiss(el) {
        el.classList.remove('show');
        el.addEventListener('transitionend', () => el.remove(), { once: true });
    }
}

// ── Formatting ────────────────────────────────────────────────────────────
export function fmtDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString(undefined, { year:'numeric', month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' });
}
export function fmtDateOnly(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString(undefined, { year:'numeric', month:'short', day:'numeric' });
}
export function timeAgo(iso) {
    if (!iso) return '—';
    const s = Math.floor((Date.now() - new Date(iso)) / 1000);
    if (s < 60)    return `${s}s ago`;
    if (s < 3600)  return `${Math.floor(s/60)}m ago`;
    if (s < 86400) return `${Math.floor(s/3600)}h ago`;
    return `${Math.floor(s/86400)}d ago`;
}

// ── Status badge HTML ─────────────────────────────────────────────────────
export function badge(status) {
    const map = {
        DRAFT:   'draft',   RUNNING: 'running',
        PASSED:  'passed',  FAILED:  'failed',
        PARTIAL: 'partial', PENDING: 'pending',
    };
    const cls = map[status] || 'pending';
    return `<span class="badge badge-${cls}">${esc(status || '—')}</span>`;
}

// ── HTML escape ───────────────────────────────────────────────────────────
export function esc(s) {
    if (s == null) return '';
    return String(s)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

// ── States ────────────────────────────────────────────────────────────────
export function showSpinner(el) {
    el.innerHTML = `<div class="spinner-wrap"><div class="spinner"></div></div>`;
}
export function showEmpty(el, msg = 'No results found') {
    el.innerHTML = `<div class="empty-state"><div class="state-icon">🔍</div><p>${esc(msg)}</p></div>`;
}
export function showErr(el, msg = 'Something went wrong') {
    el.innerHTML = `<div class="error-state"><div class="state-icon">⚠️</div><p>${esc(msg)}</p></div>`;
}

// ── Auth ──────────────────────────────────────────────────────────────────
export function requireAuth() {
    if (!localStorage.getItem('qa_token') || !localStorage.getItem('qa_user')) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}
export function getUser() {
    try { return JSON.parse(localStorage.getItem('qa_user') || 'null'); } catch { return null; }
}
export function logout() {
    localStorage.removeItem('qa_token');
    localStorage.removeItem('qa_user');
    window.location.href = '/login.html';
}

// ── Sidebar: highlight active link ───────────────────────────────────────
export function highlightNav() {
    const page = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.sidebar-nav a').forEach(a => {
        a.classList.toggle('active', a.getAttribute('href') === page);
    });
}

// ── Fill sidebar user info ────────────────────────────────────────────────
export function fillSidebar() {
    const u = getUser() || {};
    const el = document.getElementById('sb-username');
    const rl = document.getElementById('sb-role');
    if (el) el.textContent = u.username || '—';
    if (rl) rl.textContent = u.role     || '—';
}