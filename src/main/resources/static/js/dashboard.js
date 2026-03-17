import * as api from './api.js';
import { initTheme, toggleTheme, requireAuth, logout, fillSidebar, highlightNav, esc, timeAgo, showErr } from './utils.js';

if (!requireAuth()) { /* redirect handled */ }

initTheme();
fillSidebar();
highlightNav();

document.getElementById('theme-btn').addEventListener('click', toggleTheme);
document.getElementById('logout-btn').addEventListener('click', logout);

const user = JSON.parse(localStorage.getItem('qa_user') || '{}');
document.getElementById('welcome-name').textContent = user.username || '—';
document.getElementById('welcome-date').textContent = new Date().toLocaleDateString(undefined, { weekday:'long', year:'numeric', month:'long', day:'numeric' });

(async () => {
    try {
        const allProjects = await api.projects.list(user.userId);
        document.getElementById('stat-projects').textContent = allProjects.length;

        // Show up to 6 recent projects
        const recent = allProjects.slice(0, 6);
        const grid = document.getElementById('recent-projects');
        if (!recent.length) {
            grid.innerHTML = `<div class="empty-state"><div class="state-icon">📁</div><p>No projects yet. <a href="projects.html">Create one</a></p></div>`;
        } else {
            grid.innerHTML = recent.map(p => `
        <div class="card">
          <div class="card-header">
            <div>
              <div class="proj-name">${esc(p.name)}</div>
              <div class="proj-meta">By ${esc(p.createdBy || '—')} · ${timeAgo(p.createdAt)}</div>
            </div>
          </div>
          <div class="card-body">
            <p class="proj-desc">${esc(p.description || 'No description')}</p>
            <div class="proj-tags" style="margin-bottom:14px;">
              ${(p.tags||[]).map(t=>`<span class="tag">${esc(t)}</span>`).join('')}
            </div>
            <div class="proj-footer">
              <a href="modules.html?projectId=${esc(p.id)}" class="btn btn-primary btn-sm">🗂️ Modules</a>
              <a href="runs.html?projectId=${esc(p.id)}"    class="btn btn-secondary btn-sm">▶ Runs</a>
            </div>
          </div>
        </div>
      `).join('');
        }

        // Aggregate run stats across all projects (sample from first few modules)
        let totalRuns = 0, passed = 0, failed = 0, running = 0;
        for (const proj of allProjects.slice(0, 4)) {
            try {
                const mods = await api.modules.list(proj.id);
                for (const mod of mods.slice(0, 2)) {
                    const res = await api.runs.list(proj.id, mod.id, { size: 100 });
                    totalRuns += res.totalCount || 0;
                    (res.results || []).forEach(r => {
                        if (r.status === 'PASSED')  passed++;
                        if (r.status === 'FAILED')  failed++;
                        if (r.status === 'RUNNING') running++;
                    });
                }
            } catch {}
        }
        document.getElementById('stat-runs').textContent    = totalRuns;
        document.getElementById('stat-passed').textContent  = passed;
        document.getElementById('stat-failed').textContent  = failed;
        document.getElementById('stat-running').textContent = running;

    } catch (e) {
        showErr(document.getElementById('recent-projects'), 'Failed to load dashboard: ' + e.message);
    }
})();