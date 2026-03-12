/**
 * api.js — Central API client
 * All calls to the Spring Boot backend go through here.
 */

const BASE = window.QA_API_BASE || 'http://localhost:8080';

function token() { return localStorage.getItem('qa_token') || ''; }

async function req(path, opts = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
        ...(opts.headers || {}),
    };
    const res = await fetch(BASE + path, { ...opts, headers });
    if (!res.ok) {
        let body;
        try { body = await res.json(); } catch { body = { message: res.statusText }; }
        const e = new Error(body.message || `HTTP ${res.status}`);
        e.status = res.status;
        e.details = body.details;
        throw e;
    }
    if (res.status === 204) return null;
    return res.json();
}

async function upload(path, formData) {
    const res = await fetch(BASE + path, {
        method: 'POST',
        headers: token() ? { Authorization: `Bearer ${token()}` } : {},
        body: formData,
    });
    if (!res.ok) {
        const b = await res.json().catch(() => ({ message: res.statusText }));
        throw new Error(b.message || `Upload failed ${res.status}`);
    }
    return res.json();
}

// ── Auth ──────────────────────────────────────────────────────────────────
export const auth = {
    login: (username, password) =>
        req('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
};

// ── Projects ──────────────────────────────────────────────────────────────
export const projects = {
    list:   (userId)    => req(`/api/projects${userId ? `?userId=${userId}` : ''}`),
    get:    (id)        => req(`/api/projects/${id}`),
    create: (data)      => req('/api/projects', { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data)  => req(`/api/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id)        => req(`/api/projects/${id}`, { method: 'DELETE' }),
};

// ── Modules ───────────────────────────────────────────────────────────────
export const modules = {
    list:   (projectId)        => req(`/api/projects/${projectId}/modules`),
    get:    (id)               => req(`/api/modules/${id}`),
    create: (projectId, data)  => req(`/api/projects/${projectId}/modules`, { method: 'POST', body: JSON.stringify(data) }),
    update: (id, data)         => req(`/api/modules/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id)               => req(`/api/modules/${id}`, { method: 'DELETE' }),
};

// ── Runs ──────────────────────────────────────────────────────────────────

/**
 * Build URLSearchParams from filter object.
 * - status / type: comma-separated strings
 * - tags:          array → repeated &tag= params
 * - nulls skipped
 */
function buildQS(f = {}) {
    const p = new URLSearchParams();
    const add = (k, v) => { if (v !== null && v !== undefined && v !== '') p.append(k, v); };
    add('status',    f.status);
    add('type',      f.type);
    add('createdBy', f.createdBy);
    add('search',    f.search);
    add('from',      f.from);
    add('to',        f.to);
    add('page',      f.page ?? 0);
    add('size',      f.size ?? 20);
    add('sort',      f.sort ?? '-createdAt');
    (f.tags || []).forEach(t => p.append('tag', t));
    return p.toString();
}

export const runs = {
    /**
     * Filtered, paginated run list.
     * filters: { status, type, createdBy, search, from, to, tags[], page, size, sort }
     */
    list:       (projectId, moduleId, filters = {}) =>
        req(`/api/projects/${projectId}/modules/${moduleId}/runs?${buildQS(filters)}`),

    filterMeta: (projectId, moduleId) =>
        req(`/api/runs/filters/meta?projectId=${projectId}&moduleId=${moduleId}`),

    get:        (id)       => req(`/api/runs/${id}`),
    results:    (id)       => req(`/api/runs/${id}/results`),

    create: (projectId, moduleId, data) =>
        req(`/api/projects/${projectId}/modules/${moduleId}/runs`, { method: 'POST', body: JSON.stringify(data) }),

    update: (id, data)  => req(`/api/runs/${id}`, { method: 'PUT',    body: JSON.stringify(data) }),
    delete: (id)        => req(`/api/runs/${id}`, { method: 'DELETE' }),
    clone:  (id)        => req(`/api/runs/${id}/clone`,   { method: 'POST' }),
    execute:(id)        => req(`/api/runs/${id}/execute`, { method: 'POST' }),
};

// ── Uploads ───────────────────────────────────────────────────────────────
export const uploads = {
    /**
     * @param {File}   file
     * @param {string} projectId
     * @param {string} moduleId
     * @param {number} sequenceNo  — 1-based scenario index
     * @param {string} [runId]     — optional, blank if run not saved yet
     */
    csv: (file, projectId, moduleId, sequenceNo, runId = '') => {
        const fd = new FormData();
        fd.append('file',       file);
        fd.append('projectId',  projectId);
        fd.append('moduleId',   moduleId);
        fd.append('runId',      runId);
        fd.append('sequenceNo', sequenceNo);
        return upload('/api/uploads/testcase', fd);
    },
    screenshot: (file) => {
        const fd = new FormData(); fd.append('file', file);
        return upload('/api/uploads/screenshot', fd);
    },
};