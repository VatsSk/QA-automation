/**
 * api.js — ALL HTTP calls to the Spring Boot backend live here.
 *
 * Base URL: window.QA_API_BASE  OR  'http://localhost:8088'
 * Auth    : Bearer token stored in localStorage key 'qa_token'
 *
 * Groups:  auth | projects | modules | runs | uploads
 */

//const BASE = window.QA_API_BASE || 'http://localhost:8088';
 const BASE = window.QA_API_BASE || 'http://3.7.136.248:8088';

function getToken() {
    return localStorage.getItem('qa_token') || '';
}

// ── Core JSON request ──────────────────────────────────────────────────────
async function request(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    const tok = getToken();
    if (tok) headers['Authorization'] = `Bearer ${tok}`;

    const opts = { method, headers };
    if (body !== undefined) opts.body = JSON.stringify(body);

    const res = await fetch(BASE + path, opts);
    if (!res.ok) {
        let err;
        try { err = await res.json(); } catch { err = { message: res.statusText }; }
        const e = new Error(err.message || `HTTP ${res.status}`);
        e.status = res.status;
        throw e;
    }
    if (res.status === 204) return null;
    const text = await res.text();
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

// ── Multipart file upload ──────────────────────────────────────────────────
async function uploadFile(path, formData) {
    const headers = {};
    const tok = getToken();
    if (tok) headers['Authorization'] = `Bearer ${tok}`;

    const res = await fetch(BASE + path, { method: 'POST', headers, body: formData });
    if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }));
        throw new Error(err.message || `Upload failed (HTTP ${res.status})`);
    }
    return res.json();
}

// ── Auth ───────────────────────────────────────────────────────────────────
export const auth = {
    login: (username, password) =>
        request('POST', '/api/auth/login', { username, password }),
};

// ── Users ──────────────────────────────────────────────────────────────────
export const users = {
    getExtensionId: (username) => request('GET', `/api/users/${username}/extension`),
    updateExtensionId: (username, extensionId) => request('PUT', `/api/users/${username}/extension`, { extensionId }),
};

// ── Projects ───────────────────────────────────────────────────────────────
export const projects = {
    list:   (username) => request('GET',    `/api/projects${username ? `?createdBy=${username}` : ''}`),
    get:    (id)     => request('GET',    `/api/projects/${id}`),
    create: (data)   => request('POST',   '/api/projects', data),
    update: (id, d)  => request('PUT',    `/api/projects/${id}`, d),
    delete: (id)     => request('DELETE', `/api/projects/${id}`),
};

// ── Modules ────────────────────────────────────────────────────────────────
export const modules = {
    list:   (projectId)      => request('GET',    `/api/projects/${projectId}/modules`),
    get:    (id)             => request('GET',    `/api/modules/${id}`),
    create: (projectId, d)   => request('POST',   `/api/projects/${projectId}/modules`, d),
    update: (id, d)          => request('PUT',    `/api/modules/${id}`, d),
    delete: (id)             => request('DELETE', `/api/modules/${id}`),
};

// ── Runs ───────────────────────────────────────────────────────────────────

// Build query string from a filter object  { status, search, page, size, sort }
function toQS(f = {}) {
    const p = new URLSearchParams();
    if (f.status) p.set('status', f.status);
    if (f.search) p.set('search', f.search);
    p.set('page', f.page  ?? 0);
    p.set('size', f.size  ?? 20);
    p.set('sort', f.sort  ?? '-createdAt');
    (f.tags || []).forEach(t => p.append('tag', t));
    return p.toString();
}

export const runs = {

    executeQueue: (runIds) => request('POST', '/api/runs/execute-queue', runIds),

    //all runs of the user
    bucketRunList: (username) => request('GET',`/api/runlist/${username}`),

    // Paginated list filtered by project + module
    list:    (projectId, moduleId, filters) =>
        request('GET', `/api/projects/${projectId}/modules/${moduleId}/runs?${toQS(filters)}`),

    get:     (id)      => request('GET',    `/api/runs/${id}`),
    results: (id)      => request('GET',    `/api/runs/${id}/results`),

    create:  (projectId, moduleId, data) =>
        request('POST', `/api/projects/${projectId}/modules/${moduleId}/runs`, data),

    update:  (id, d)   => request('PUT',    `/api/runs/${id}`, d),
    delete:  (id)      => request('DELETE', `/api/runs/${id}`),
    clone:   (id)      => request('POST',   `/api/runs/${id}/clone`),
    execute: (id)      => request('POST',   `/api/runs/${id}/execute`),

    getScenarioResultCsv: (s3Path) =>
        request('GET', `/api/runs/scenario/result?s3Path=${encodeURIComponent(s3Path)}`),
    // ✅ NEW: Get screenshots for a selected column + value from scenario result CSV
    // Example:
    // /api/runs/{runId}/scenarios/{sequenceNo}/screenshots?column=testcaseId&value=TC_001
    getScenarioScreenshots: (prefix) =>
        request(
            'GET',
            `/scenario-screenshots?prefix=${encodeURIComponent(prefix)}`
        ),
};

// ── Flows ──────────────────────────────────────────────────────────────────
export const flows = {
    list:    (projectId, moduleId) =>
        request('GET', `/api/flows/${projectId}/${moduleId}`),
    get:     (id)      => request('GET',    `/api/flows/${id}`),
    update:  (id, d)   => request('PUT',    `/api/flows/${id}`, d),
    remove:  (id)      => request('DELETE', `/api/flows/${id}`),
    clone:   (id)      => request('POST',   `/api/flows/${id}/clone`),
    execute: (id, environmentId) => {
        const qs = environmentId ? `?environmentId=${encodeURIComponent(environmentId)}` : '';
        return request('POST', `/api/flows/${id}/run${qs}`);
    },
    executeQueue: (flowIds, environmentId) => {
        const qs = environmentId ? `?environmentId=${encodeURIComponent(environmentId)}` : '';
        return request('POST', `/api/flows/execute-queue${qs}`, flowIds);
    },
    executeModule: (moduleId, environmentId) => {
        const qs = environmentId ? `?environmentId=${encodeURIComponent(environmentId)}` : '';
        return request('POST', `/api/flows/execute-module/${moduleId}${qs}`);
    },
    executeProject: (projectId, environmentId) => {
        const qs = environmentId ? `?environmentId=${encodeURIComponent(environmentId)}` : '';
        return request('POST', `/api/flows/execute-project/${projectId}${qs}`);
    },
    stop:        (id)        => request('POST', `/api/flows/${id}/stop`),
    stopQueue:   ()          => request('POST', `/api/flows/stop-queue`),
    stopModule:  (moduleId)  => request('POST', `/api/flows/stop-module/${moduleId}`),
    stopProject: (projectId) => request('POST', `/api/flows/stop-project/${projectId}`),
};

// ── Environments ───────────────────────────────────────────────────────────
export const environments = {
    list:   (projectId)      => request('GET',    `/api/projects/${projectId}/environments`),
    create: (projectId, d)   => request('POST',   `/api/projects/${projectId}/environments`, d),
    update: (projectId, id, d) => request('PUT',  `/api/projects/${projectId}/environments/${id}`, d),
    delete: (projectId, id)  => request('DELETE', `/api/projects/${projectId}/environments/${id}`),
};

// ── Files / S3 access ──────────────────────────────────────────────────────
export const files = {
    /**
     * Convert an S3 key into a browser-accessible URL (usually pre-signed URL)
     * Backend should return: { url: "https://..." }
     */
    presign: (key) =>
        request('GET', `/api/files/presign?key=${encodeURIComponent(key)}`),
};
// ── Uploads ────────────────────────────────────────────────────────────────
export const uploads = {
    /**
     * Upload a test-case CSV or XLSX file.
     * S3 key: {projectId}/{moduleId}/{runId}/{sequenceNo}/testcase.{ext}
     * Pass runId = '' if the run has not been saved yet.
     */
    csv(file, projectId, moduleId, sequenceNo, runId = '') {
        const fd = new FormData();
        fd.append('file',       file);
        fd.append('projectId',  projectId);
        fd.append('moduleId',   moduleId);
        fd.append('runId',      runId);
        fd.append('sequenceNo', String(sequenceNo));
        return uploadFile('/api/uploads/testcase', fd);
    },
    projectLoginCsv(file, projectName='') {
        const fd = new FormData();
        fd.append('file', file);
        fd.append('projectName', projectName);

        return uploadFile('/api/uploads/project-login', fd);
    }
};
