/* =======================================================
   auth.js — SamadhanAI Shared Auth Utilities
   Include on EVERY page: <script src="js/auth.js"></script>
   ======================================================= */

// ─── CONFIG ─────────────────────────────────────────────
const API_BASE = '';   // '' = same origin (Spring Boot serves on same port)

// ─── TOKEN HELPERS ──────────────────────────────────────
const Auth = {

    getToken() {
        return localStorage.getItem('sam_token') || null;
    },

    getUser() {
        try {
            return JSON.parse(localStorage.getItem('sam_user') || 'null');
        } catch (e) {
            return null;
        }
    },

    isLoggedIn() {
        return !!this.getToken();
    },

    isAdmin() {
        const user = this.getUser();
        return user && user.role === 'ADMIN';
    },

    setSession(token, user) {
        localStorage.setItem('sam_token', token);
        localStorage.setItem('sam_user', JSON.stringify(user));
    },

    clearSession() {
        localStorage.removeItem('sam_token');
        localStorage.removeItem('sam_user');
    },

    // Returns headers object — always add to fetch calls
    headers(extra = {}) {
        const token = this.getToken();
        return {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': 'Bearer ' + token } : {}),
            ...extra
        };
    },

    // Headers without Content-Type (for FormData uploads)
    uploadHeaders() {
        const token = this.getToken();
        return token ? { 'Authorization': 'Bearer ' + token } : {};
    },

    // Redirect to login if not logged in
    requireLogin(redirectBack = true) {
        if (!this.isLoggedIn()) {
            const back = redirectBack ? '?next=' + encodeURIComponent(location.href) : '';
            location.href = 'login.html' + back;
        }
    },

    // Redirect to profile if already logged in (for login/register pages)
    redirectIfLoggedIn() {
        if (this.isLoggedIn()) {
            location.href = 'profile.html';
        }
    },

    logout() {
        this.clearSession();
        location.href = 'login.html';
    }
};

// ─── API HELPER ──────────────────────────────────────────
const Api = {

    async get(path) {
        const res = await fetch(API_BASE + path, {
            headers: Auth.headers()
        });
        return res.json();
    },

    async post(path, body) {
        const res = await fetch(API_BASE + path, {
            method: 'POST',
            headers: Auth.headers(),
            body: JSON.stringify(body)
        });
        return res.json();
    },

    async put(path, body) {
        const res = await fetch(API_BASE + path, {
            method: 'PUT',
            headers: Auth.headers(),
            body: JSON.stringify(body)
        });
        return res.json();
    },

    async delete(path) {
        const res = await fetch(API_BASE + path, {
            method: 'DELETE',
            headers: Auth.headers()
        });
        return res.json();
    },

    async upload(path, formData, method = 'POST') {
        const res = await fetch(API_BASE + path, {
            method,
            headers: Auth.uploadHeaders(),
            body: formData
        });
        // ✅ Safe JSON parse — empty body pe crash nahi hoga
        const text = await res.text();
        if (!text || text.trim() === '') {
            return { success: false, message: 'Server returned empty response. Please try again.' };
        }
        try {
            return JSON.parse(text);
        } catch (e) {
            return { success: false, message: 'Invalid server response. Please try again.' };
        }
    }
};

// ─── NAV AUTH — call on every page ──────────────────────
function initNav() {
    const user = Auth.getUser();
    const loginBtn    = document.getElementById('nav-login-btn');
    const profileBtn  = document.getElementById('nav-profile-btn');

    if (user && Auth.isLoggedIn()) {
        if (loginBtn)   loginBtn.style.display   = 'none';
        if (profileBtn) {
            profileBtn.style.display = 'inline-flex';
            profileBtn.textContent   = '👤 ' + (user.name?.split(' ')[0] || 'Profile');
        }
    } else {
        if (loginBtn)   loginBtn.style.display   = '';
        if (profileBtn) profileBtn.style.display = 'none';
    }
}

// ─── TOAST UTILITY ──────────────────────────────────────
function showToast(msg, duration = 3500) {
    let t = document.getElementById('toast');
    if (!t) {
        t = document.createElement('div');
        t.id = 'toast';
        t.style.cssText = `
      position:fixed;bottom:1.5rem;right:1.5rem;
      background:#1e293b;color:#fff;
      padding:.8rem 1.4rem;border-radius:12px;
      font-size:.88rem;font-weight:600;
      z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,.25);
      font-family:'Nunito',sans-serif;display:none;
    `;
        document.body.appendChild(t);
    }
    t.textContent = msg;
    t.style.display = 'block';
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.style.display = 'none', duration);
}

// ─── DOM HELPER ─────────────────────────────────────────
function setText(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = val;
}

function show(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'block';
}

function hide(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}


// ─── FORMAT HELPERS ─────────────────────────────────────
function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric'
    });
}

function formatStatus(status) {
    if (!status) return '—';
    return status.replace(/_/g, ' ');
}

function formatType(type) {
    if (!type) return '—';
    return type.replace(/_/g, ' ')
        .replace(/\b\w/g, c => c.toUpperCase());
}

function statusBadgeHtml(status) {
    const map = {
        PENDING:     '⏳ Pending',
        IN_PROGRESS: '🔄 In Progress',
        RESOLVED:    '✅ Resolved',
        REJECTED:    '❌ Rejected'
    };
    const label = map[status] || status;
    return `<span class="status-badge s-${status}">${label}</span>`;
}

// ─── PASSWORD TOGGLE ─────────────────────────────────────
function togglePw(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    input.type = input.type === 'password' ? 'text' : 'password';
    btn.textContent = input.type === 'password' ? '👁️' : '🙈';
}

// ─── PASSWORD STRENGTH ───────────────────────────────────
function checkPwStrength(pw) {
    const b1 = document.getElementById('pb1');
    const b2 = document.getElementById('pb2');
    const b3 = document.getElementById('pb3');
    const lbl = document.getElementById('pw-lbl');
    if (!b1) return;
    [b1, b2, b3].forEach(b => b.className = 'pw-bar');
    if (!pw) { if (lbl) lbl.textContent = ''; return; }
    const score =
        (pw.length >= 8            ? 1 : 0) +
        (/[A-Z]/.test(pw)          ? 1 : 0) +
        (/[0-9]/.test(pw)          ? 1 : 0) +
        (/[^A-Za-z0-9]/.test(pw)  ? 1 : 0);
    if (score <= 1) {
        b1.className = 'pw-bar weak';
        if (lbl) { lbl.style.color = '#dc2626'; lbl.textContent = 'Weak'; }
    } else if (score <= 3) {
        b1.className = b2.className = 'pw-bar medium';
        if (lbl) { lbl.style.color = '#f97316'; lbl.textContent = 'Medium'; }
    } else {
        b1.className = b2.className = b3.className = 'pw-bar strong';
        if (lbl) { lbl.style.color = '#16a34a'; lbl.textContent = 'Strong 💪'; }
    }
}

// ─── WHATSAPP SHARE ──────────────────────────────────────
function waShareLink(refId, status) {
    const url = `${location.origin}/track.html?ref=${encodeURIComponent(refId)}`;
    const msg = `🚨 Civic Complaint Filed!\nID: ${refId}\nStatus: ${formatStatus(status)}\nTrack: ${url}`;
    return `https://wa.me/?text=${encodeURIComponent(msg)}`;
}

// ─── AUTO INIT NAV on every page ────────────────────────
document.addEventListener('DOMContentLoaded', initNav);