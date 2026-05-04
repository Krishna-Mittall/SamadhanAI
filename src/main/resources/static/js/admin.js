/* =======================================================
   admin.js — Admin Panel Page
   APIs used:
     GET    /api/admin/stats
     GET    /api/admin/users
     PUT    /api/admin/users/{id}/toggle
     DELETE /api/admin/users/{id}
     GET    /api/admin/complaints
     PUT    /api/complaints/{refId}/status
   ======================================================= */

// ─── STATE ───────────────────────────────────────────────
let allUsers      = [];
let allComplaints = [];
let currentRefId  = '';

// ─── INIT ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    Auth.requireLogin();
    const user = Auth.getUser();
    if (!user || user.role !== 'ADMIN') {
        showToast('🚫 Admin access required.');
        setTimeout(() => location.href = 'profile.html', 1500);
        return;
    }
    const urlRef = new URLSearchParams(location.search).get('ref');
    if (urlRef) {
        switchTab('complaints', document.querySelector('[onclick*="complaints"]'));
    }
    loadStats();
    loadUsers();
    loadComplaints();
});

// ─── TAB SWITCHING ───────────────────────────────────────
function switchTab(name, btn) {
    document.querySelectorAll('.tab-pane').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById('tab-' + name)?.classList.add('active');
    if (btn) btn.classList.add('active');
}

// ─── STATS ───────────────────────────────────────────────
async function loadStats() {
    // ✅ FIX: /api/admin/stats only has totalUsers + totalComplaints
    // resolved + ignored come from /api/dashboard/stats
    try {
        const data = await Api.get('/api/admin/stats');
        if (data.success && data.data) {
            setText('as-users', data.data.totalUsers      ?? '—');
            setText('as-total', data.data.totalComplaints ?? '—');
        }
    } catch (e) {}

    // ✅ FIX: fetch resolved + ignored from dashboard API
    try {
        const dash = await Api.get('/api/dashboard/stats');
        if (dash.success && dash.data) {
            setText('as-res', dash.data.resolvedComplaints ?? '—');
            setText('as-ign', dash.data.ignoredComplaints  ?? '—');
        }
    } catch (e) {}
}

// ─── USERS ───────────────────────────────────────────────
async function loadUsers() {
    try {
        const data = await Api.get('/api/admin/users');
        hide('users-loading');
        show('users-table-wrap');
        allUsers = data.success ? (data.data || []) : [];
        renderUsers(allUsers);
    } catch (e) {
        const el = document.getElementById('users-loading');
        if (el) el.innerHTML = `<div style="color:#dc2626;">❌ Failed to load users. <a href="#" onclick="loadUsers()" style="color:#2563eb;">Retry</a></div>`;
    }
}

function renderUsers(list) {
    setText('users-count', list.length + ' user' + (list.length !== 1 ? 's' : ''));
    const tbody = document.getElementById('users-body');
    if (!tbody) return;

    if (!list.length) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:2rem;color:#64748b;">No users found.</td></tr>`;
        return;
    }

    tbody.innerHTML = list.map(u => {
        const initial  = (u.name || '?')[0].toUpperCase();
        // ✅ FIXED: u.enabled (not u.active)
        const isActive = u.enabled !== false;
        return `<tr>
      <td><div style="display:flex;align-items:center;gap:.7rem;"><div class="mini-av">${initial}</div><div style="font-weight:700;">${u.name || '—'}</div></div></td>
      <td style="color:#64748b;font-size:.82rem;">${u.email}</td>
      <td style="text-align:center;font-weight:700;">${u.totalComplaints || 0}</td>
      <td style="font-size:.8rem;color:#64748b;">${formatDate(u.createdAt)}</td>
      <td><span class="s-badge s-${u.role || 'USER'}">${u.role || 'USER'}</span></td>
      <td><span class="s-badge s-${isActive ? 'ACTIVE' : 'DISABLED'}">${isActive ? 'Active' : 'Disabled'}</span></td>
      <td><div style="display:flex;gap:.4rem;flex-wrap:wrap;">
        <button class="btn btn-${isActive ? 'red-soft' : 'green-soft'} btn-sm" onclick="toggleUser(${u.id}, ${isActive})">${isActive ? 'Disable' : 'Enable'}</button>
        <button class="btn btn-red-soft btn-sm" onclick="deleteUser(${u.id}, '${(u.name || '').replace(/'/g, '')}')">Delete</button>
      </div></td>
    </tr>`;
    }).join('');
}

function filterUsers() {
    const q    = document.getElementById('user-search')?.value.toLowerCase() || '';
    const role = document.getElementById('user-role-f')?.value || '';
    renderUsers(allUsers.filter(u =>
        (!q || (u.name || '').toLowerCase().includes(q) || u.email.toLowerCase().includes(q)) &&
        (!role || u.role === role)
    ));
}

async function toggleUser(id, isCurrentlyActive) {
    if (!confirm(`${isCurrentlyActive ? 'Disable' : 'Enable'} this user?`)) return;
    try {
        const data = await Api.put('/api/admin/users/' + id + '/toggle', {});
        if (data.success) {
            showToast('✅ User ' + (isCurrentlyActive ? 'disabled' : 'enabled') + '!');
            // ✅ FIXED: u.enabled
            const u = allUsers.find(u => u.id === id);
            if (u) u.enabled = !isCurrentlyActive;
            renderUsers(allUsers);
        } else {
            showToast('❌ ' + (data.message || 'Failed.'));
        }
    } catch (e) { showToast('❌ Something went wrong.'); }
}

async function deleteUser(id, name) {
    if (!confirm(`Permanently delete user "${name}"?\n\nThis CANNOT be undone.`)) return;
    try {
        const data = await Api.delete('/api/admin/users/' + id);
        if (data.success) {
            showToast('✅ User deleted.');
            allUsers = allUsers.filter(u => u.id !== id);
            renderUsers(allUsers);
            loadStats();
        } else {
            showToast('❌ ' + (data.message || 'Delete failed.'));
        }
    } catch (e) { showToast('❌ Something went wrong.'); }
}

// ─── COMPLAINTS ───────────────────────────────────────────
async function loadComplaints() {
    try {
        const data = await Api.get('/api/admin/complaints');
        hide('comp-loading');
        show('comp-table-wrap');
        allComplaints = data.success ? (data.data || []) : [];
        renderComplaints(allComplaints);

        const urlRef = new URLSearchParams(location.search).get('ref');
        if (urlRef) {
            const found = allComplaints.find(c => c.referenceId === urlRef);
            if (found) openStatusModal(found.referenceId, found.status);
        }
    } catch (e) {
        const el = document.getElementById('comp-loading');
        if (el) el.innerHTML = `<div style="color:#dc2626;">❌ Failed to load complaints. <a href="#" onclick="loadComplaints()" style="color:#2563eb;">Retry</a></div>`;
    }
}

function renderComplaints(list) {
    setText('comp-count', list.length + ' complaint' + (list.length !== 1 ? 's' : ''));
    const tbody = document.getElementById('comp-body');
    if (!tbody) return;

    if (!list.length) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:2rem;color:#64748b;">No complaints found.</td></tr>`;
        return;
    }

    tbody.innerHTML = list.map(c => {
        // ✅ NEW: resolvedBy badge
        const resolvedByHtml = c.resolvedBy ? (() => {
            const map = {
                ADMIN:            '<span style="font-size:.72rem;font-weight:700;color:#1e40af;">🛡️ Admin</span>',
                USER:             '<span style="font-size:.72rem;font-weight:700;color:#16a34a;">👤 Citizen</span>',
                DEPARTMENT_EMAIL: '<span style="font-size:.72rem;font-weight:700;color:#d97706;">📧 Dept Email</span>'
            };
            return map[c.resolvedBy] || '—';
        })() : '—';

        return `
    <tr>
      <td><span style="font-weight:800;font-size:.85rem;">${c.referenceId}</span></td>
      <td style="font-size:.82rem;">${formatType(c.complaintType) || '—'}</td>
      <td style="font-size:.82rem;">${formatType(c.assignedDepartment) || '—'}</td>
      <td style="font-size:.82rem;color:#64748b;">${c.ward || '—'}</td>
      <td style="font-size:.8rem;">${c.userName || 'Anonymous'}</td>
      <td style="font-size:.78rem;color:#64748b;">${formatDate(c.createdAt)}</td>
      <td>${statusBadgeHtml(c.status)}</td>
      <td>${resolvedByHtml}</td>
      <td><div style="display:flex;gap:.4rem;flex-wrap:wrap;">
        <button class="btn btn-orange-soft btn-sm" onclick="openStatusModal('${c.referenceId}', '${c.status}')">Update</button>
        <a href="track.html?ref=${c.referenceId}" class="btn btn-blue-soft btn-sm" target="_blank">View</a>
      </div></td>
    </tr>`;
    }).join('');
}

function filterComplaints() {
    const q    = (document.getElementById('comp-search')?.value  || '').toLowerCase();
    const st   = document.getElementById('comp-status-f')?.value || '';
    // ✅ FIXED: c.assignedDepartment (not c.department)
    const dept = document.getElementById('comp-dept-f')?.value   || '';
    renderComplaints(allComplaints.filter(c =>
        (!q ||
            (c.referenceId        || '').toLowerCase().includes(q) ||
            (c.complaintType      || '').toLowerCase().includes(q) ||
            (c.ward               || '').toLowerCase().includes(q) ||
            (c.userName           || '').toLowerCase().includes(q)
        ) &&
        (!st   || c.status             === st) &&
        (!dept || c.assignedDepartment === dept)
    ));
}

// ─── STATUS MODAL ─────────────────────────────────────────
function openStatusModal(refId, currentStatus) {
    currentRefId = refId;
    setText('modal-ref-txt', 'Complaint: ' + refId);
    const sel = document.getElementById('new-status');
    if (sel) sel.value = currentStatus || 'PENDING';
    const note = document.getElementById('res-note');
    if (note) note.value = '';
    document.getElementById('status-modal')?.classList.add('open');
    const btn = document.querySelector('#status-modal .btn-orange');
    if (btn) { btn.disabled = false; btn.textContent = '✅ Update'; }
}

function closeStatusModal() {
    document.getElementById('status-modal')?.classList.remove('open');
}

async function confirmStatus() {
    const status = document.getElementById('new-status')?.value;
    const note   = document.getElementById('res-note')?.value.trim();
    const btn    = document.querySelector('#status-modal .btn-orange');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Updating...'; }
    try {
        const data = await Api.put('/api/complaints/' + currentRefId + '/status', {
            status,
            resolutionNote: note
        });
        if (data.success) {
            closeStatusModal();
            showToast(`✅ Status → ${formatStatus(status)}! Citizen notified via email.`);
            const c = allComplaints.find(c => c.referenceId === currentRefId);
            if (c) c.status = status;
            renderComplaints(allComplaints);
            loadStats();
            loadComplaints();
        } else {
            showToast('❌ ' + (data.message || 'Update failed.'));
            if (btn) { btn.disabled = false; btn.textContent = '✅ Update'; }
        }
    } catch (e) {
        showToast('❌ Something went wrong.');
        if (btn) { btn.disabled = false; btn.textContent = '✅ Update'; }
    }
}

// ─── LOGOUT ──────────────────────────────────────────────
function doLogout() { Auth.logout(); }