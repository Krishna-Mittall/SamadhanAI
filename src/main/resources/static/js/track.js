/* =======================================================
   track.js — Track Complaint Page
   APIs used:
     GET  /api/complaints/{referenceId}           → complaint details
     PUT  /api/complaints/{referenceId}/edit      → {complaintType, description}
     PUT  /api/complaints/{referenceId}/status    → admin: {status, resolutionNote}
     POST /api/complaints/{referenceId}/send      → resend dept email
     PUT  /api/complaints/{referenceId}/resolve   → citizen resolves
   ======================================================= */

// ─── STATE ───────────────────────────────────────────────
let currentComplaint = null;

// ─── INIT ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const urlRef = new URLSearchParams(location.search).get('ref');
    if (urlRef) {
        const inp = document.getElementById('ref-inp');
        if (inp) inp.value = urlRef;
        doTrack();
    }

    const inp = document.getElementById('ref-inp');
    if (inp) inp.addEventListener('keydown', e => { if (e.key === 'Enter') doTrack(); });
});

// ─── SEARCH ──────────────────────────────────────────────
async function doTrack() {
    const inp = document.getElementById('ref-inp');
    const ref = inp?.value.trim().toUpperCase();
    if (!ref) { showToast('⚠️ Please enter a Reference ID'); return; }

    const btn = document.getElementById('search-btn');
    if (btn) { btn.disabled = true; btn.textContent = '⏳...'; }

    hide('not-found');
    hide('comp-section');

    try {
        const data = await Api.get('/api/complaints/' + encodeURIComponent(ref));

        if (data.success && data.data) {
            currentComplaint = data.data;
            renderComplaint(currentComplaint);
            show('comp-section');
            document.getElementById('comp-section')?.scrollIntoView({ behavior: 'smooth' });
        } else {
            show('not-found');
        }
    } catch (e) {
        show('not-found');
    }

    if (btn) { btn.disabled = false; btn.textContent = '🔍 Search'; }
}

// ─── RENDER COMPLAINT ─────────────────────────────────────
const statusConfig = {
    PENDING:     { label: '⏳ Pending',     explain: 'Your complaint is registered and waiting for department action.' },
    IN_PROGRESS: { label: '🔄 In Progress', explain: 'The department has acknowledged and started working on your complaint.' },
    RESOLVED:    { label: '✅ Resolved',    explain: 'Great news! Your complaint has been resolved by the department.' },
    REJECTED:    { label: '❌ Rejected',    explain: 'Your complaint was rejected. See resolution note for the reason.' }
};

function renderComplaint(c) {
    const sc = statusConfig[c.status] || statusConfig.PENDING;

    const badge = document.getElementById('status-badge');
    if (badge) {
        badge.textContent = sc.label;
        badge.className   = 'status-badge s-' + (c.status || 'PENDING');
    }
    setText('status-explain', sc.explain);

    setText('i-ref',  c.referenceId || '—');
    setText('i-type', formatType(c.complaintType) || '—');
    setText('i-dept', formatType(c.assignedDepartment) || '—');
    setText('i-ward', c.ward || '—');
    setText('i-loc',  c.fullAddress || c.city || '—');
    setText('i-date', formatDate(c.createdAt));

    const img = document.getElementById('comp-img');
    if (img && c.photoPath) {
        img.src = '/uploads/' + c.photoPath;
        img.style.display = 'block';
        img.onclick = () => window.open('/uploads/' + c.photoPath, '_blank');
    }

    if (c.resolutionNote) {
        const rn = document.getElementById('res-note');
        if (rn) {
            rn.style.display = 'flex';
            setText('res-note-txt', 'Resolution Note: ' + c.resolutionNote);
        }
    }

    const en  = document.getElementById('email-notice');
    const ent = document.getElementById('email-notice-txt');
    if (en && ent) {
        if (c.emailSent) {
            en.className = 'notice n-green';
            ent.textContent = '✅ Complaint email sent to department. You were CC\'d at ' + (c.userEmail || 'your email') + '.';
        } else {
            en.className = 'notice n-blue';
            ent.textContent = 'Email not sent yet. Click "Re-send Email" below to notify the department.';
        }
    }

    renderResolvedByCard(c);
    renderTimeline(c);
    renderActionBtns(c);

    const editable = c.status === 'PENDING' || c.status === 'IN_PROGRESS';
    const editCard = document.getElementById('edit-card');
    if (editCard) editCard.style.display = editable ? 'block' : 'none';

    if (editable) {
        const et = document.getElementById('edit-type');
        const ed = document.getElementById('edit-desc');
        if (et) et.value = c.complaintType    || 'OTHER';
        if (ed) ed.value = c.userDescription  || '';
    }
}

// ─── RESOLVED BY CARD ─────────────────────────────────────
function renderResolvedByCard(c) {
    const card = document.getElementById('resolved-by-card');
    if (!card) return;

    if (c.status !== 'RESOLVED' || !c.resolvedBy) {
        card.style.display = 'none';
        return;
    }

    const resolvedAtStr = c.resolvedAt ? formatDate(c.resolvedAt) : '';

    const config = {
        ADMIN: {
            icon:  '🛡️',
            title: 'Resolved by Admin',
            sub:   'An admin reviewed and marked this complaint as resolved.' +
                (resolvedAtStr ? ' · ' + resolvedAtStr : '')
        },
        USER: {
            icon:  '👤',
            title: 'Resolved by You',
            sub:   'You confirmed that this problem has been fixed.' +
                (resolvedAtStr ? ' · ' + resolvedAtStr : '')
        },
        DEPARTMENT_EMAIL: {
            icon:  '📧',
            title: 'Auto-Resolved via Department Email',
            sub:   'The department replied confirming resolution. System auto-updated.' +
                (resolvedAtStr ? ' · ' + resolvedAtStr : '')
        }
    };

    const info = config[c.resolvedBy] || config.ADMIN;

    setText('rb-icon',  info.icon);
    setText('rb-title', info.title);
    setText('rb-sub',   info.sub);
    card.style.display = 'flex';
}

// ─── TIMELINE ─────────────────────────────────────────────
function renderTimeline(c) {
    setText('tl-d1', formatDate(c.createdAt));

    const d2 = document.getElementById('tl-dot2');
    if (d2) {
        if (c.emailSent) {
            d2.className   = 'tl-dot done';
            d2.textContent = '✓';
            setText('tl-t2', '📧 Email Sent to Department');
            setText('tl-d2', formatDate(c.emailSentAt) || 'Sent');
        } else {
            d2.className   = 'tl-dot wait';
            d2.textContent = '📧';
            setText('tl-t2', '📧 Email to Department (Pending)');
            setText('tl-d2', 'Not sent yet');
        }
    }

    const d3 = document.getElementById('tl-dot3');
    if (d3) {
        if (c.status === 'RESOLVED') {
            d3.className   = 'tl-dot done';
            d3.textContent = '✓';
            setText('tl-t3', '✅ Complaint Resolved');
            setText('tl-d3', formatDate(c.resolvedAt || c.updatedAt) || 'Resolved');
        } else if (c.status === 'IN_PROGRESS') {
            d3.className   = 'tl-dot active';
            d3.textContent = '🔄';
            setText('tl-t3', '🔄 Department Working on It');
            setText('tl-d3', 'In progress...');
        } else if (c.status === 'REJECTED') {
            d3.className         = 'tl-dot';
            d3.style.background  = '#fef2f2';
            d3.style.borderColor = '#dc2626';
            d3.textContent       = '❌';
            setText('tl-t3', '❌ Complaint Rejected');
            setText('tl-d3', formatDate(c.updatedAt) || '—');
        } else {
            d3.className   = 'tl-dot wait';
            d3.textContent = '🏛️';
            setText('tl-t3', '🏛️ Department Action');
            setText('tl-d3', 'Waiting...');
        }
    }
}

// ─── ACTION BUTTONS ───────────────────────────────────────
function renderActionBtns(c) {
    const ab = document.getElementById('action-btns');
    if (!ab) return;

    const currentUser = Auth.getUser();
    const isOwner     = currentUser && c.userId && currentUser.id === c.userId;
    const canResolve  = c.status === 'PENDING' || c.status === 'IN_PROGRESS';

    const waHref = waShareLink(c.referenceId, c.status);
    let html = `<a href="${waHref}" class="btn btn-wa btn-sm" target="_blank">📱 WhatsApp</a>`;

    // ✅ FIX: resend email — sirf logged-in owner ko dikhao
    if (!c.emailSent && isOwner) {
        html += `<button class="btn btn-soft-blue btn-sm" onclick="resendEmail()">📧 Re-send Email</button>`;
    }

    // Mark as Resolved — sirf owner ko, sirf PENDING/IN_PROGRESS mein
    if (isOwner && canResolve) {
        html += `<button class="btn btn-green btn-sm" onclick="openResolveModal()">✅ Mark Resolved</button>`;
    }

    // Admin — update status from track page
    if (Auth.isAdmin()) {
        html += `<button class="btn btn-orange-soft btn-sm" onclick="openAdminStatus()">🛡️ Update Status</button>`;
    }

    ab.innerHTML = html;
}

// ─── RESEND EMAIL ─────────────────────────────────────────
async function resendEmail() {
    if (!currentComplaint) return;
    showToast('⏳ Sending email...');
    try {
        const data = await Api.post('/api/complaints/' + currentComplaint.referenceId + '/send', {});
        if (data.success) {
            showToast('✅ Email sent to department!');
            currentComplaint.emailSent = true;
            renderComplaint(currentComplaint);
        } else {
            showToast('❌ Failed: ' + (data.message || 'Unknown error'));
        }
    } catch (e) {
        showToast('❌ Something went wrong. Try again.');
    }
}

// ─── EDIT COMPLAINT ───────────────────────────────────────
async function saveEdit() {
    if (!currentComplaint) return;
    const type = document.getElementById('edit-type')?.value;
    const desc = document.getElementById('edit-desc')?.value.trim();

    try {
        const data = await Api.put(
            '/api/complaints/' + currentComplaint.referenceId + '/edit',
            { complaintType: type, userDescription: desc }
        );
        if (data.success) {
            showToast('✅ Complaint updated!');
            currentComplaint.complaintType = type;
            currentComplaint.description   = desc;
            setText('i-type', formatType(type));
        } else {
            showToast('❌ ' + (data.message || 'Update failed.'));
        }
    } catch (e) {
        showToast('❌ Something went wrong.');
    }
}

// ─── ADMIN STATUS ─────────────────────────────────────────
function openAdminStatus() {
    if (!currentComplaint || !Auth.isAdmin()) return;
    location.href = 'admin.html?ref=' + encodeURIComponent(currentComplaint.referenceId);
}

// ─── CITIZEN RESOLVE ──────────────────────────────────────
function openResolveModal() {
    const note = document.getElementById('resolve-note');
    if (note) note.value = '';
    const btn = document.getElementById('confirm-resolve-btn');
    if (btn) { btn.disabled = false; btn.textContent = '✅ Yes, It\'s Fixed!'; }
    document.getElementById('resolve-modal')?.classList.add('open');
}

function closeResolveModal() {
    document.getElementById('resolve-modal')?.classList.remove('open');
}

async function confirmResolve() {
    if (!currentComplaint) return;

    const note = document.getElementById('resolve-note')?.value.trim();
    const btn  = document.getElementById('confirm-resolve-btn');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Submitting...'; }

    try {
        const data = await Api.put(
            '/api/complaints/' + currentComplaint.referenceId + '/resolve',
            { note: note || '' }
        );

        if (data.success) {
            closeResolveModal();
            showToast('✅ Complaint marked as resolved! Thank you for confirming.');
            currentComplaint = data.data;
            renderComplaint(currentComplaint);
        } else {
            showToast('❌ ' + (data.message || 'Could not resolve. Please try again.'));
            if (btn) { btn.disabled = false; btn.textContent = '✅ Yes, It\'s Fixed!'; }
        }
    } catch (e) {
        showToast('❌ Something went wrong. Please try again.');
        if (btn) { btn.disabled = false; btn.textContent = '✅ Yes, It\'s Fixed!'; }
    }
}