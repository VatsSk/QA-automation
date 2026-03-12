/**
 * filters.js — Filter state management
 * Persists per project+module in localStorage.
 */

const VER = 'v1';
const key = (p, m) => `qa_filters_${VER}_${p}_${m}`;

export const DEFAULTS = {
    status: '', type: '', createdBy: '',
    search: '', from: '', to: '',
    tags: [], sort: '-createdAt',
    page: 0, size: 20,
};

export function load(projectId, moduleId) {
    try {
        const raw = localStorage.getItem(key(projectId, moduleId));
        return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : { ...DEFAULTS };
    } catch { return { ...DEFAULTS }; }
}

export function save(projectId, moduleId, f) {
    try { localStorage.setItem(key(projectId, moduleId), JSON.stringify(f)); } catch {}
}

export function reset(projectId, moduleId) {
    localStorage.removeItem(key(projectId, moduleId));
    return { ...DEFAULTS };
}

export function activeCount(f) {
    let n = 0;
    if (f.status)    n++;
    if (f.type)      n++;
    if (f.createdBy) n++;
    if (f.search)    n++;
    if (f.from || f.to) n++;
    if (f.tags && f.tags.length) n++;
    return n;
}

export function toChips(f) {
    const chips = [];
    const fmt = iso => iso ? new Date(iso).toLocaleDateString() : '';
    if (f.status)    chips.push({ label: `Status: ${f.status}`,      key: 'status' });
    if (f.type)      chips.push({ label: `Type: ${f.type}`,          key: 'type' });
    if (f.createdBy) chips.push({ label: `By: ${f.createdBy}`,       key: 'createdBy' });
    if (f.search)    chips.push({ label: `"${f.search}"`,            key: 'search' });
    if (f.from)      chips.push({ label: `From ${fmt(f.from)}`,      key: 'from' });
    if (f.to)        chips.push({ label: `To ${fmt(f.to)}`,          key: 'to' });
    (f.tags || []).forEach(t => chips.push({ label: `#${t}`, key: 'tag', val: t }));
    return chips;
}

export const QUICK = [
    { id: 'last24h',  label: '⏱ Last 24h',    apply: f => ({ ...f, from: new Date(Date.now() - 86400000).toISOString(), to: '' }) },
    { id: 'last7d',   label: '📅 Last 7d',     apply: f => ({ ...f, from: new Date(Date.now() - 7*86400000).toISOString(), to: '' }) },
    { id: 'failed',   label: '❌ Failed only',  apply: f => ({ ...f, status: 'FAILED' }) },
    { id: 'passed',   label: '✅ Passed only',  apply: f => ({ ...f, status: 'PASSED' }) },
    { id: 'drafts',   label: '📝 Drafts',       apply: f => ({ ...f, status: 'DRAFT'  }) },
];