/* =======================================================
   dashboard.js — Public Dashboard Page
   APIs used:
     GET /api/dashboard/stats            → overall stats
     GET /api/dashboard/ward-stats       → [{ward, total, resolved}]
     GET /api/dashboard/department-stats → [{department, total, resolved, pending, ignored, avgDays}]
     GET /api/dashboard/ignored          → [{referenceId, complaintType, department, ward, createdAt, daysIgnored}]
   ======================================================= */

let wardChartInstance = null;
const esc = v => String(v ?? '').replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]
));

// ─── INIT ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    loadWards();
    loadDepts();
    loadIgnored();
});

// ─── STATS ───────────────────────────────────────────────
async function loadStats() {
    try {
        const data = await Api.get('/api/dashboard/stats');
        if (data.success && data.data) renderStats(data.data);
    } catch (e) {}
}

function renderStats(s) {
    setText('s-total', s.totalComplaints       ?? '0');
    setText('s-res',   s.resolvedComplaints     ?? '0');
    setText('s-prog',  s.inProgressComplaints   ?? '0');
    setText('s-pend',  s.pendingComplaints      ?? '0');
    setText('s-ign',   s.ignoredComplaints      ?? '0');

    setText('hl-ward', s.mostComplainedWard || '—');
    setText('hl-worst', s.worstPerformingDepartment || '—');
    setText('hl-type', formatType(s.mostCommonComplaintType) || '—');
}

// ─── WARD CHART ──────────────────────────────────────────
async function loadWards() {
    try {
        const data = await Api.get('/api/dashboard/ward-stats');
        if (data.success && data.data?.length) {
            renderWards(data.data);
        } else {
            showEmptyWard();
        }
    } catch (e) {
        showEmptyWard();
    }
}

function showEmptyWard() {
    hide('ward-loading');
    const box = document.getElementById('ward-chart-box');
    if (box) {
        box.style.display = 'flex';
        box.style.alignItems = 'center';
        box.style.justifyContent = 'center';
        box.innerHTML = '<div style="color:#64748b;font-size:.9rem;">No ward data yet.</div>';
    }
    setText('hl-ward', '—');
}

function renderWards(wards) {
    hide('ward-loading');
    const box = document.getElementById('ward-chart-box');
    if (box) box.style.display = 'block';

    setText('hl-ward', wards[0]?.wardName || '—');

    const top    = wards.slice(0, 10);
    const labels = top.map(w => w.wardName || 'Unknown');
    const totals = top.map(w => w.totalComplaints    || 0);
    const res    = top.map(w => w.resolvedComplaints || 0);

    if (wardChartInstance) wardChartInstance.destroy();

    const canvas = document.getElementById('ward-chart');
    if (!canvas) return;

    wardChartInstance = new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels,
            datasets: [
                {
                    label: 'Total Complaints',
                    data: totals,
                    backgroundColor: totals.map((_, i) =>
                        i === 0 ? 'rgba(220,38,38,.75)' : 'rgba(249,115,22,.6)'
                    ),
                    borderRadius: 5
                },
                {
                    label: 'Resolved',
                    data: res,
                    backgroundColor: 'rgba(22,163,74,.55)',
                    borderRadius: 5
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { labels: { font: { family: 'Nunito', size: 12 }, color: '#475569' } },
                tooltip: {
                    backgroundColor: '#fff', borderColor: '#e2e8f0', borderWidth: 1,
                    titleColor: '#1e293b', bodyColor: '#64748b'
                }
            },
            scales: {
                x: { ticks: { color: '#64748b', font: { family: 'Nunito', size: 11 } }, grid: { display: false } },
                y: { beginAtZero: true, ticks: { color: '#64748b', font: { family: 'Nunito', size: 11 }, stepSize: 1 }, grid: { color: 'rgba(0,0,0,.05)' } }
            }
        }
    });
}

// ─── DEPARTMENT TABLE ─────────────────────────────────────
async function loadDepts() {
    try {
        const data = await Api.get('/api/dashboard/dept-scores');
        if (data.success && data.data?.length) {
            renderDepts(data.data);
        } else {
            showEmptyDepts();
        }
    } catch (e) {
        showEmptyDepts();
    }
}

function showEmptyDepts() {
    hide('dept-loading');
    const wrap = document.getElementById('dept-table-wrap');
    if (wrap) wrap.style.display = 'block';
    const tbody = document.getElementById('dept-body');
    if (tbody) tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:2rem;color:#64748b;">No department data yet.</td></tr>`;
    setText('hl-worst', '—');
    setText('hl-type',  '—');
}

function renderDepts(depts) {
    hide('dept-loading');
    const wrap = document.getElementById('dept-table-wrap');
    if (wrap) wrap.style.display = 'block';

    const sorted = [...depts].sort((a, b) => {
        const rA = a.totalAssigned ? a.resolved / a.totalAssigned : 1;
        const rB = b.totalAssigned ? b.resolved / b.totalAssigned : 1;
        return rA - rB;
    });

    setText('hl-worst', formatType(sorted[0]?.departmentName) || '—');
    // setText('hl-type',  formatType(sorted[sorted.length - 1]?.topComplaintType) || '—');

    const tbody = document.getElementById('dept-body');
    if (!tbody) return;

    tbody.innerHTML = sorted.map(d => {
        const rate = d.totalAssigned ? Math.round((d.resolved / d.totalAssigned) * 100) : 0;
        const grade    = rate >= 80 ? 'A' : rate >= 60 ? 'B' : rate >= 40 ? 'C' : 'D';
        const barColor = rate >= 60 ? '#16a34a' : rate >= 40 ? '#d97706' : '#dc2626';
        const safeDepartment = esc(formatType(d.departmentName));

        return `<tr>
      <td><strong>${safeDepartment}</strong></td>
      <td>${d.totalAssigned    || 0}</td>
      <td style="color:#16a34a;font-weight:700;">${d.resolved || 0}</td>
      <td style="color:#f97316;font-weight:700;">${d.pending  || 0}</td>
      <td style="color:#dc2626;font-weight:700;">${d.ignored  || 0}</td>
      <td>
        <div style="display:flex;align-items:center;gap:.6rem;">
          <div style="flex:1;height:8px;background:#e2e8f0;border-radius:4px;overflow:hidden;min-width:60px;">
            <div style="width:${rate}%;height:100%;background:${barColor};border-radius:4px;transition:width .4s;"></div>
          </div>
          <span style="font-weight:800;color:${barColor};min-width:36px;">${rate}%</span>
        </div>
      </td>
      <td>${d.avgResolutionDays != null ? d.avgResolutionDays + ' days' : '—'}</td>
      <td><span class="grade g-${grade}">${grade}</span></td>
    </tr>`;
    }).join('');
}

// ─── IGNORED COMPLAINTS ───────────────────────────────────
async function loadIgnored() {
    try {
        const data = await Api.get('/api/dashboard/ignored');
        renderIgnored(data.success ? (data.data || []) : []);
    } catch (e) {
        renderIgnored([]);
    }
}

function renderIgnored(list) {
    hide('ign-loading');
    const el = document.getElementById('ign-list');
    if (!el) return;

    if (!list.length) {
        el.innerHTML = `
      <div style="text-align:center;padding:2.5rem;color:#64748b;">
        🎉 <strong>No ignored complaints!</strong><br/>
        <span style="font-size:.85rem;">All complaints received government attention within 30 days.</span>
      </div>`;
        return;
    }

    el.innerHTML = list.map(c => {
        const safeRefId = esc(c.referenceId || '');
        const safeWard = esc(c.ward || '—');
        const days = c.daysIgnored ||
            Math.floor((Date.now() - new Date(c.submittedAt)) / (1000 * 60 * 60 * 24));
        return `
      <div class="ign-item">
        <div class="ign-top">
          <div>
            <div class="ign-ref">${safeRefId}</div>
            <div class="ign-meta">
              🔖 ${formatType(c.complaintType)} &nbsp;·&nbsp;
              🏛️ ${formatType(c.departmentName)} &nbsp;·&nbsp;
              🗺️ ${safeWard}
            </div>
            <div class="ign-warn">⚠️ Auto-reminder sent to department (Feature 8 — every 15 days)</div>
          </div>
          <div style="display:flex;flex-direction:column;align-items:flex-end;gap:.5rem;">
            <span class="days-chip">${days} days ignored</span>
            <a href="track.html?ref=${encodeURIComponent(c.referenceId || '')}" class="btn btn-blue-soft btn-sm">View →</a>
          </div>
        </div>
      </div>`;
    }).join('');
}