/* =======================================================
   profile.js — User Profile Page
   APIs used:
     GET /api/user/profile         → profile + stats
     PUT /api/user/profile         → {name, phone}
     PUT /api/user/change-password → {currentPassword, newPassword}
     GET /api/user/complaints      → user's complaint list
   ======================================================= */

// ─── INIT ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    Auth.requireLogin();
    loadProfile();
    loadMyComplaints();
});

const esc = v => String(v ?? '').replace(/[&<>"']/g, ch => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]
));

// ─── TAB SWITCHING ───────────────────────────────────────
// ✅ Single definition here — duplicate removed from profile.html
function switchTab(name, btn) {
    document.querySelectorAll('.tab-pane').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById('tab-' + name)?.classList.add('active');
    btn.classList.add('active');
}

// ─── LOAD PROFILE ────────────────────────────────────────
async function loadProfile() {
    // Show cached data immediately so page doesn't look blank
    const cached = Auth.getUser();
    if (cached) fillProfile(cached);

    // Then fetch fresh data from server
    try {
        const data = await Api.get('/api/user/profile');
        if (data.success && data.data) {
            fillProfile(data.data);
            Auth.setSession(Auth.getToken(), data.data);
        }
    } catch (e) {
        // If fetch fails, cached data is still shown — no blank page
    }
}

function fillProfile(u) {
    // Avatar — initials
    const initials = (u.name || '?')
        .split(' ')
        .map(w => w[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();
    setText('avatar', initials);

    // Profile info
    setText('p-name',  u.name  || '—');
    setText('p-email', u.email || '—');

    // Role badge
    const rb = document.getElementById('p-role');
    if (rb) {
        rb.textContent = u.role || 'USER';
        rb.className   = 'role-badge role-' + (u.role || 'USER');
    }

    // Admin banner
    if (u.role === 'ADMIN') {
        show('admin-banner');
    }

    // Prefill edit form
    const editName  = document.getElementById('edit-name');
    const editPhone = document.getElementById('edit-phone');
    if (editName)  editName.value  = u.name  || '';
    if (editPhone) editPhone.value = u.phone || '';

    // Stats
    setText('ms-total', u.totalComplaints     ?? '0');
    setText('ms-res',   u.resolvedComplaints   ?? '0');
    setText('ms-pend',  u.pendingComplaints    ?? '0');
    setText('ms-prog',  u.inProgressComplaints ?? '0');
}

// ─── LOAD MY COMPLAINTS ───────────────────────────────────
async function loadMyComplaints() {
    try {
        const data = await Api.get('/api/user/complaints');
        hide('comp-loading');
        show('comp-list');

        const list = data.success ? (data.data || []) : [];
        renderMyComplaints(list);
    } catch (e) {
        const el = document.getElementById('comp-loading');
        if (el) el.innerHTML = `
      <div style="color:#dc2626;">
        ❌ Could not load complaints.
        <a href="#" onclick="loadMyComplaints()" style="color:#2563eb;margin-left:.4rem;">Retry</a>
      </div>`;
    }
}

function renderMyComplaints(list) {
    const el = document.getElementById('comp-list');
    if (!el) return;

    if (!list.length) {
        el.innerHTML = `
      <div style="text-align:center;padding:2.5rem;color:#64748b;">
        <div style="font-size:3rem;margin-bottom:.6rem;">📋</div>
        <div style="font-weight:800;font-size:1rem;margin-bottom:.4rem;">No complaints yet</div>
        <div style="font-size:.86rem;">File your first complaint and make a difference!</div>
        <a href="submit.html"
           style="display:inline-flex;margin-top:1rem;padding:.65rem 1.5rem;
                  border-radius:10px;background:#f97316;color:#fff;
                  font-weight:700;font-size:.9rem;">
          📸 File a Complaint
        </a>
      </div>`;
        return;
    }

    el.innerHTML = list.map(c => {
        const refId = esc(c.referenceId || '');
        const ward = esc(c.ward || '');
        // resolvedBy badge
        const resolvedByMap = {
            ADMIN:            '🛡️ Admin resolved',
            USER:             '👤 You confirmed',
            DEPARTMENT_EMAIL: '📧 Auto-resolved'
        };
        const resolvedByBadge = (c.status === 'RESOLVED' && c.resolvedBy)
            ? `<span style="font-size:.72rem;font-weight:700;color:#15803d;
                            background:#f0fdf4;padding:.15rem .6rem;
                            border-radius:20px;border:1px solid #bbf7d0;">
                ${resolvedByMap[c.resolvedBy] || '✅ Resolved'}
               </span>`
            : '';

        return `
    <div class="comp-row">
      <div>
        <div class="comp-ref">${refId}</div>
        <div class="comp-meta">
          ${formatType(c.complaintType) || '—'} &nbsp;·&nbsp;
          ${ward} &nbsp;·&nbsp;
          ${formatDate(c.createdAt)}
        </div>
        ${resolvedByBadge}
      </div>
      <div style="display:flex;align-items:center;gap:.7rem;flex-wrap:wrap;">
        ${statusBadgeHtml(c.status)}
        <a href="track.html?ref=${encodeURIComponent(c.referenceId || '')}" class="btn btn-gray btn-sm">View →</a>
      </div>
    </div>`;
    }).join('');
}

// ─── SAVE PROFILE ─────────────────────────────────────────
async function saveProfile() {
    const name  = document.getElementById('edit-name')?.value.trim();
    const phone = document.getElementById('edit-phone')?.value.trim();

    hide('prof-ok');
    hide('prof-err');

    if (!name) {
        setText('prof-err-txt', 'Name cannot be empty.');
        show('prof-err');
        return;
    }

    try {
        const data = await Api.put('/api/user/profile', { name, phone });
        if (data.success) {
            show('prof-ok');
            // Update cached user
            const u = Auth.getUser() || {};
            u.name  = name;
            u.phone = phone;
            Auth.setSession(Auth.getToken(), u);
            // Update displayed name + avatar
            setText('p-name', name);
            const initials = name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
            setText('avatar', initials);
        } else {
            setText('prof-err-txt', data.message || 'Update failed. Please try again.');
            show('prof-err');
        }
    } catch (e) {
        setText('prof-err-txt', 'Something went wrong. Please try again.');
        show('prof-err');
    }
}

// ─── CHANGE PASSWORD ──────────────────────────────────────
async function changePw() {
    const oldPw  = document.getElementById('old-pw')?.value;
    const newPw  = document.getElementById('new-pw')?.value;
    const confPw = document.getElementById('conf-pw')?.value;

    hide('pw-ok');
    hide('pw-err');

    if (!oldPw || !newPw || !confPw) {
        setText('pw-err-txt', 'All three password fields are required.');
        show('pw-err');
        return;
    }
    // ✅ FIX: 6 — matches backend @Size(min=6) + placeholder "Min. 6 characters"
    if (newPw.length < 6) {
        setText('pw-err-txt', 'New password must be at least 6 characters.');
        show('pw-err');
        return;
    }
    if (newPw !== confPw) {
        setText('pw-err-txt', 'New passwords do not match.');
        show('pw-err');
        return;
    }

    try {
        const data = await Api.put('/api/user/change-password', {
            currentPassword: oldPw,  // matches ChangePasswordRequest.currentPassword
            newPassword:     newPw
        });
        if (data.success) {
            show('pw-ok');
            // Clear fields
            ['old-pw', 'new-pw', 'conf-pw'].forEach(id => {
                const el = document.getElementById(id);
                if (el) el.value = '';
            });
        } else {
            setText('pw-err-txt', data.message || 'Wrong current password. Please try again.');
            show('pw-err');
        }
    } catch (e) {
        setText('pw-err-txt', 'Something went wrong. Please try again.');
        show('pw-err');
    }
}

// ─── LOGOUT ──────────────────────────────────────────────
function doLogout() {
    Auth.logout();
}