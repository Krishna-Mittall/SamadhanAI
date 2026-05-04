/* =======================================================
   submit.js — File Complaint Page (Multi-Photo)
   APIs used:
     POST /api/complaints/analyze   (per photo — AI + EXIF check)
     POST /api/complaints            (submit with photos[])
   ======================================================= */

// ─── STATE ───────────────────────────────────────────────
// selectedFiles = [{file, status, reason, aiData}]
// status: 'analyzing' | 'ok' | 'rejected'
let selectedFiles = [];
let gpsData       = null;
const MAX_PHOTOS  = 3;

// ─── INIT ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    prefillIfLoggedIn();
    bindInputListeners();
    updateProgress();
});

function prefillIfLoggedIn() {
    const user = Auth.getUser();
    if (!user) return;
    const n = document.getElementById('u-name');
    const e = document.getElementById('u-email');
    const p = document.getElementById('u-phone');
    if (n) n.value = user.name  || '';
    if (e) e.value = user.email || '';
    if (p) p.value = user.phone || '';
    checkReady();
}

function bindInputListeners() {
    ['u-name', 'u-email'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', checkReady);
    });
}

// ─── PROGRESS BAR ────────────────────────────────────────
function updateProgress() {
    const hasPhoto   = selectedFiles.some(f => f.status === 'ok');
    const hasGPS     = !!gpsData;
    const hasDetails = !!(
        document.getElementById('u-name')?.value.trim() &&
        document.getElementById('u-email')?.value.trim()
    );
    const step = !hasPhoto ? 1 : !hasGPS ? 2 : !hasDetails ? 3 : 4;

    for (let i = 1; i <= 4; i++) {
        const circle = document.getElementById('pc' + i);
        const label  = document.getElementById('pl' + i);
        if (!circle || !label) continue;
        if (i < step) {
            circle.className   = 'prog-circle done';
            circle.textContent = '✓';
            label.className    = 'prog-label';
        } else if (i === step) {
            circle.className   = 'prog-circle active';
            circle.textContent = String(i);
            label.className    = 'prog-label active';
        } else {
            circle.className   = 'prog-circle';
            circle.textContent = String(i);
            label.className    = 'prog-label';
        }
        const line = document.getElementById('pline' + i);
        if (line) line.className = 'prog-line' + (i < step ? ' done' : '');
    }
}

function checkReady() {
    const hasValidPhoto = selectedFiles.some(f => f.status === 'ok');
    const hasRejected   = selectedFiles.some(f => f.status === 'rejected');
    const hasAnalyzing  = selectedFiles.some(f => f.status === 'analyzing');
    const hasGPS        = !!gpsData;
    const hasName       = !!document.getElementById('u-name')?.value.trim();
    const hasEmail      = !!document.getElementById('u-email')?.value.trim();

    const ok = hasValidPhoto && !hasRejected && !hasAnalyzing && hasGPS && hasName && hasEmail;
    const btn = document.getElementById('submit-btn');
    if (btn) btn.disabled = !ok;

    const uploadZone = document.getElementById('upload-zone');
    if (uploadZone) {
        uploadZone.style.display = selectedFiles.length >= MAX_PHOTOS ? 'none' : 'block';
    }

    const countLabel = document.getElementById('photo-count-label');
    if (countLabel) {
        countLabel.textContent = selectedFiles.length > 0
            ? `${selectedFiles.length}/${MAX_PHOTOS} photos added`
            : '';
    }

    updateProgress();
}

// ─── FILE HANDLING ────────────────────────────────────────
function onDrop(e) {
    e.preventDefault();
    document.getElementById('upload-zone')?.classList.remove('over');
    const files = Array.from(e.dataTransfer?.files || []);
    files.forEach(f => processFile(f));
}

function onPhotoSelected(e) {
    const files = Array.from(e.target?.files || []);
    files.forEach(f => processFile(f));
    e.target.value = '';
}

function processFile(file) {
    hideError();

    if (!file.type.startsWith('image/')) {
        showError(`"${file.name}" is not an image file.`);
        return;
    }
    if (file.size > 10 * 1024 * 1024) {
        showError(`"${file.name}" is too large. Max size is 10MB.`);
        return;
    }
    if (selectedFiles.length >= MAX_PHOTOS) {
        showError(`Maximum ${MAX_PHOTOS} photos allowed.`);
        return;
    }

    const idx = selectedFiles.length;
    selectedFiles.push({ file, status: 'analyzing', reason: null, aiData: null });

    renderPhotoCard(idx);
    checkReady();
    analyzePhoto(file, idx);
}

function removePhoto(idx) {
    selectedFiles.splice(idx, 1);
    renderAllPhotoCards();
    checkReady();

    if (selectedFiles.length === 0) {
        setText('m-type', 'AI will detect');
        setText('m-dept', 'AI will assign');
    }
}

// ─── RENDER PHOTO CARDS ───────────────────────────────────
function renderAllPhotoCards() {
    const grid = document.getElementById('photos-grid');
    if (!grid) return;
    grid.innerHTML = '';
    selectedFiles.forEach((_, i) => renderPhotoCard(i));
}

function renderPhotoCard(idx) {
    const grid = document.getElementById('photos-grid');
    if (!grid) return;

    const item = selectedFiles[idx];
    let existingCard = document.getElementById('photo-card-' + idx);

    let statusHtml = '';
    if (item.status === 'analyzing') {
        statusHtml = `
            <div class="photo-status analyzing">
                <div class="spinner-sm"></div>
                <span>AI verifying...</span>
            </div>`;
    } else if (item.status === 'ok') {
        const ai = item.aiData;
        statusHtml = `
            <div class="photo-status ok">
                <span>✅ Verified</span>
                ${ai ? `<span class="photo-type">${formatType(ai.complaintType) || ''}</span>` : ''}
                ${ai ? `<span class="photo-conf">${Math.round((ai.confidence||0)*100)}% confidence</span>` : ''}
            </div>`;
    } else if (item.status === 'rejected') {
        statusHtml = `
            <div class="photo-status rejected">
                <span>❌ Rejected</span>
                <span class="photo-reason">${item.reason || 'Verification failed'}</span>
                <span style="font-size:.75rem;color:#b91c1c;margin-top:2px;">Remove this photo to submit</span>
            </div>`;
    }

    const cardHtml = `
        <div class="photo-card" id="photo-card-${idx}">
            <div class="photo-preview-wrap">
                <img id="photo-img-${idx}" src="" alt="Photo ${idx+1}" class="photo-thumb"/>
                <button class="photo-remove-btn" onclick="removePhoto(${idx})" title="Remove photo">✕</button>
                <span class="photo-num">#${idx+1}</span>
            </div>
            ${statusHtml}
        </div>`;

    if (existingCard) {
        existingCard.outerHTML = cardHtml;
    } else {
        grid.insertAdjacentHTML('beforeend', cardHtml);
    }

    const reader = new FileReader();
    reader.onload = e => {
        const img = document.getElementById('photo-img-' + idx);
        if (img) img.src = e.target.result;
    };
    reader.readAsDataURL(item.file);
}

// ─── AI ANALYSIS ──────────────────────────────────────────
async function analyzePhoto(file, idx) {
    try {
        const fd = new FormData();
        fd.append('photo', file);
        const data = await Api.upload('/api/complaints/analyze', fd);

        if (data.success && data.data) {
            const d = data.data;

            if (d.isRejected) {
                selectedFiles[idx].status = 'rejected';
                selectedFiles[idx].reason = d.rejectReason || 'Photo verification failed';
            } else {
                selectedFiles[idx].status = 'ok';
                selectedFiles[idx].aiData = d;

                if (idx === 0 || !selectedFiles.slice(0, idx).some(f => f.status === 'ok')) {
                    setText('m-type', formatType(d.complaintType) || 'AI will detect');
                    setText('m-dept', formatType(d.department)    || 'AI will assign');
                }
            }
        } else {
            selectedFiles[idx].status = 'rejected';
            selectedFiles[idx].reason = 'Could not analyze photo. Please try again.';
        }
    } catch (err) {
        selectedFiles[idx].status = 'rejected';
        selectedFiles[idx].reason = 'Analysis failed. Please check your connection.';
    }

    renderPhotoCard(idx);
    checkReady();
}

// ─── GPS ─────────────────────────────────────────────────
function getGPS() {
    const statusEl = document.getElementById('gps-status');
    if (statusEl) { statusEl.style.color = '#0369a1'; statusEl.textContent = '📡 Getting your location...'; }

    if (!navigator.geolocation) {
        if (statusEl) { statusEl.style.color = '#dc2626'; statusEl.textContent = '❌ Geolocation not supported.'; }
        return;
    }

    navigator.geolocation.getCurrentPosition(
        async pos => {
            const lat = pos.coords.latitude.toFixed(6);
            const lng = pos.coords.longitude.toFixed(6);

            const latEl = document.getElementById('lat');
            const lngEl = document.getElementById('lng');
            if (latEl) latEl.value = lat;
            if (lngEl) lngEl.value = lng;

            gpsData = { lat, lng };
            if (statusEl) { statusEl.style.color = '#16a34a'; statusEl.textContent = `✅ Location found: ${lat}, ${lng}`; }

            try {
                const res  = await fetch(
                    `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json`,
                    { headers: { 'Accept-Language': 'en' } }
                );
                const info = await res.json();
                const addr = info.address || {};
                const ward = addr.suburb || addr.neighbourhood || addr.village || addr.county || '';
                const city = addr.city   || addr.town || addr.state_district || addr.state || '';
                const wardEl = document.getElementById('ward');
                const cityEl = document.getElementById('city');
                if (wardEl) wardEl.value = ward;
                if (cityEl) cityEl.value = city;
                if (statusEl && (ward || city)) {
                    statusEl.textContent = `✅ Location: ${[ward, city].filter(Boolean).join(', ')}`;
                }
            } catch (e) {}

            checkReady();
        },
        err => {
            const msgs = {
                1: '❌ Permission denied. Please allow location access.',
                2: '❌ Position unavailable. Check your GPS signal.',
                3: '❌ Request timed out. Please try again.'
            };
            if (statusEl) { statusEl.style.color = '#dc2626'; statusEl.textContent = msgs[err.code] || '❌ Could not get location.'; }
        },
        { enableHighAccuracy: true, timeout: 12000 }
    );
}

// ─── MODAL ───────────────────────────────────────────────
function openModal() {
    hideError();
    const name  = document.getElementById('u-name')?.value.trim();
    const email = document.getElementById('u-email')?.value.trim();
    const phone = document.getElementById('u-phone')?.value.trim();
    const desc  = document.getElementById('u-desc')?.value.trim();

    if (!desc || desc.length < 10) {
        showError('Please write at least 10 characters describing the problem.');
        return;
    }

    const validPhotos = selectedFiles.filter(f => f.status === 'ok');

    if (validPhotos.length === 0)                              { showError('Please upload at least one verified photo.'); return; }
    if (selectedFiles.some(f => f.status === 'rejected'))      { showError('Please remove rejected photos before submitting.'); return; }
    if (selectedFiles.some(f => f.status === 'analyzing'))     { showError('Please wait for all photos to finish verification.'); return; }
    if (!gpsData)                                              { showError('Please click "Get My Location" to detect GPS.'); return; }
    if (!name || !/^[A-Za-z\s]{2,50}$/.test(name))            { showError('Please enter a valid full name.'); return; }
    if (!email)                                                { showError('Please enter your email address.'); return; }
    if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) { showError('Please enter a valid email address.'); return; }
    if (phone && !/^[6-9][0-9]{9}$/.test(phone))              { showError('Please enter a valid 10-digit mobile number.'); return; }

    const ward = document.getElementById('ward')?.value || '—';
    const city = document.getElementById('city')?.value || '—';
    setText('m-loc',    [ward, city].filter(s => s !== '—').join(', ') || '—');
    setText('m-name',   name);
    setText('m-email',  email);
    setText('m-photos', `${validPhotos.length} photo${validPhotos.length > 1 ? 's' : ''} verified ✅`);
    setText('m-perm',   document.getElementById('email-dept')?.checked ? '✅ Yes — email will be sent' : '❌ No');

    const modal = document.getElementById('modal');
    if (modal) modal.classList.add('open');
}

function closeModal() {
    const modal = document.getElementById('modal');
    if (modal) modal.classList.remove('open');
    const btn = document.getElementById('confirm-btn');
    // ✅ FIX: matches HTML button text "✅ Submit"
    if (btn) { btn.disabled = false; btn.textContent = '✅ Submit'; }
}

// ─── SUBMIT ──────────────────────────────────────────────
async function submitComplaint() {
    const btn = document.getElementById('confirm-btn');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Submitting...'; }

    try {
        const fd = new FormData();

        selectedFiles
            .filter(f => f.status === 'ok')
            .forEach(f => fd.append('photos', f.file));

        fd.append('latitude',             gpsData.lat);
        fd.append('longitude',            gpsData.lng);
        fd.append('userName',             document.getElementById('u-name')?.value.trim()  || '');
        fd.append('userEmail',            document.getElementById('u-email')?.value.trim() || '');
        fd.append('userPhone',            document.getElementById('u-phone')?.value.trim() || '');
        fd.append('userDescription',      document.getElementById('u-desc')?.value.trim()  || '');
        fd.append('sendEmailToDepartment', document.getElementById('email-dept')?.checked ? 'true' : 'false');
        // ✅ FIX: ward + city removed — backend gets these from lat/lng via LocationService

        const token = localStorage.getItem('sam_token');
        const url   = token
            ? '/api/complaints?_token=' + encodeURIComponent(token)
            : '/api/complaints';

        const data = await Api.upload(url, fd);

        if (data.success) {
            closeModal();
            showSuccess(data.data);
        } else {
            closeModal();
            showError(data.message || 'Submission failed. Please try again.');
            if (btn) { btn.disabled = false; btn.textContent = '✅ Submit'; }
        }
    } catch (e) {
        closeModal();
        showError(e?.message && e.message !== 'Failed to fetch'
            ? e.message
            : 'Network error. Please check your connection and try again.');
        if (btn) { btn.disabled = false; btn.textContent = '✅ Submit'; }
    }
}

// ─── SUCCESS ─────────────────────────────────────────────
function showSuccess(complaint) {
    hide('form-wrap');
    const sc = document.getElementById('success-card');
    if (sc) sc.style.display = 'block';

    const refId = complaint.referenceId || 'SAI-ERROR';
    setText('ref-id-show', refId);

    const emailNote = document.getElementById('email-sent-note');
    if (emailNote) {
        emailNote.textContent = complaint.emailSent
            ? '📧 A formal complaint email has been sent to the department. You have been CC\'d at your email.'
            : '📋 Complaint registered. You can send the department email from the Track page.';
    }

    const trackLink = document.getElementById('track-link');
    if (trackLink) trackLink.href = 'track.html?ref=' + encodeURIComponent(refId);

    const waLink = document.getElementById('wa-link');
    if (waLink) waLink.href = waShareLink(refId, 'PENDING');
}

// ─── ERROR ───────────────────────────────────────────────
function showError(msg) {
    const box = document.getElementById('err-box');
    const txt = document.getElementById('err-txt');
    if (txt) txt.textContent = msg;
    if (box) { box.style.display = 'flex'; box.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }
}

function hideError() { hide('err-box'); }