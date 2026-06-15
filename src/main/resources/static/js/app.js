/**
 * EMS Dashboard — Core Application Script
 * ─────────────────────────────────────────
 * Handles: Auth check → Role-based UI → Employee CRUD → Analytics
 *
 * Architecture:
 *   1. On load → checkAuth() → loadInitialData()
 *   2. All API calls use credentials:'same-origin' to send the session cookie
 *   3. Role-based UI: [data-admin-only] elements are hidden for USER role
 */

'use strict';

// ═══════════════════════════════════════════════════════════════
// STATE
// ═══════════════════════════════════════════════════════════════
const state = {
  user: null,          // { username, roles, isAdmin }
  page: 0,
  size: 10,
  sortBy: 'id',
  direction: 'asc',
  totalPages: 0,
  totalElements: 0,
  searchQuery: '',
  deletingId: null,    // ID queued for deletion
  analytics: []        // department analytics cache
};

// ═══════════════════════════════════════════════════════════════
// INIT
// ═══════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', async () => {
  const authed = await checkAuth();
  if (!authed) return; // checkAuth already redirected to login
  setupSearch();
  await Promise.all([loadEmployees(), loadAnalytics()]);
});

// ═══════════════════════════════════════════════════════════════
// AUTH
// ═══════════════════════════════════════════════════════════════

/** Verify session; redirects to login on failure. Returns true if authenticated. */
async function checkAuth() {
  try {
    const res = await fetch('/api/auth/me', { credentials: 'same-origin' });

    // Detect redirect to login page (res.redirected) or non-JSON 200 response
    // Spring Security without a custom entry point sends 302 → HTML with status 200,
    // making res.ok=true but the body is HTML, not JSON — this breaks the handshake.
    if (!res.ok || res.redirected) {
      window.location.replace('/login.html');
      return false;
    }

    // Guard against HTML responses masquerading as 200 OK
    const contentType = res.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      window.location.replace('/login.html');
      return false;
    }

    state.user = await res.json();
    renderUserInfo();
    applyRoleBasedUI();
    return true;
  } catch {
    window.location.replace('/login.html');
    return false;
  }
}

/** Populate sidebar username, avatar, and role badge. */
function renderUserInfo() {
  const { username, isAdmin } = state.user;
  document.getElementById('sidebarUsername').textContent = username;
  document.getElementById('userAvatar').textContent = username.charAt(0).toUpperCase();

  const badge = document.getElementById('roleBadge');
  if (isAdmin) {
    badge.textContent = 'Admin';
    badge.className = 'inline-block text-xs px-2 py-0.5 rounded-full font-medium mt-0.5 role-badge-admin';
  } else {
    badge.textContent = 'Read Only';
    badge.className = 'inline-block text-xs px-2 py-0.5 rounded-full font-medium mt-0.5 role-badge-user';
  }
}

/**
 * Show or hide elements marked [data-admin-only] based on current role.
 * Admin: all elements visible.
 * User: all [data-admin-only] elements hidden.
 */
function applyRoleBasedUI() {
  const display = state.user.isAdmin ? '' : 'none';
  document.querySelectorAll('[data-admin-only]').forEach(el => {
    el.style.display = display;
  });
}

// ═══════════════════════════════════════════════════════════════
// NAVIGATION
// ═══════════════════════════════════════════════════════════════

function showSection(name) {
  // Toggle sections
  document.getElementById('section-employees').classList.toggle('hidden', name !== 'employees');
  document.getElementById('section-analytics').classList.toggle('hidden', name !== 'analytics');

  // Toggle nav active states
  document.getElementById('nav-employees').classList.toggle('active', name === 'employees');
  document.getElementById('nav-analytics').classList.toggle('active', name === 'analytics');

  document.getElementById('nav-employees').className =
    'sidebar-link flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium cursor-pointer ' +
    (name === 'employees' ? 'active text-white' : 'text-slate-300 hover:text-white');
  document.getElementById('nav-analytics').className =
    'sidebar-link flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium cursor-pointer ' +
    (name === 'analytics' ? 'active text-white' : 'text-slate-300 hover:text-white');

  // Update header
  const titles = {
    employees: ['Employee Directory',   'Manage and view employee records'],
    analytics: ['Department Analytics', 'Headcount and salary insights by department']
  };
  document.getElementById('pageTitle').textContent    = titles[name][0];
  document.getElementById('pageSubtitle').textContent = titles[name][1];

  // Search bar only relevant on employees page
  document.getElementById('searchWrap').style.display = name === 'employees' ? '' : 'none';
  document.getElementById('addBtn').style.display = (name === 'employees' && state.user?.isAdmin) ? '' : 'none';

  // Load analytics on demand
  if (name === 'analytics') renderAnalyticsCards();
}

// ═══════════════════════════════════════════════════════════════
// EMPLOYEES — LOAD & RENDER
// ═══════════════════════════════════════════════════════════════

async function loadEmployees() {
  const { page, size, sortBy, direction } = state;
  const url = `/api/employees?page=${page}&size=${size}&sortBy=${sortBy}&direction=${direction}`;

  // Show spinner while loading
  document.getElementById('tableBody').innerHTML = `
    <tr><td colspan="8" class="px-4 py-12 text-center">
      <div class="flex flex-col items-center gap-3 text-gray-400">
        <div class="spinner"></div>
        <span class="text-sm">Loading employees…</span>
      </div>
    </td></tr>`;

  try {
    const res = await fetch(url, { credentials: 'same-origin' });

    // Redirect = session expired — go back to login
    if (res.redirected) {
      window.location.replace('/login.html');
      return;
    }

    // 401: session expired, redirect to login
    if (res.status === 401) {
      window.location.replace('/login.html');
      return;
    }

    // 403: authenticated but not authorized — show error in table, NOT a hanging spinner
    if (res.status === 403) {
      document.getElementById('tableBody').innerHTML = `
        <tr><td colspan="8" class="px-4 py-10 text-center text-sm text-amber-600">
          🔒 You don't have permission to view employee records.
        </td></tr>`;
      showToast('Access denied — read permission required', 'error');
      return;
    }

    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data = await res.json();
    state.totalPages    = data.totalPages;
    state.totalElements = data.totalElements;

    renderTable(data.content || []);
    renderPagination();
    updateStats(data);
  } catch (err) {
    document.getElementById('tableBody').innerHTML = `
      <tr><td colspan="8" class="px-4 py-10 text-center text-sm text-red-500">
        ⚠ Failed to load employees (${err.message}). Please refresh.
      </td></tr>`;
  }
}

/** Render employee rows, applying client-side search filter. */
function renderTable(employees) {
  const q = state.searchQuery.toLowerCase();
  const filtered = q
    ? employees.filter(e =>
        [e.fullName, e.email, e.department, e.designation]
          .some(v => v && v.toLowerCase().includes(q)))
    : employees;

  if (!filtered.length) {
    document.getElementById('tableBody').innerHTML = `
      <tr><td colspan="8" class="px-4 py-12 text-center text-sm text-gray-400">
        No employees found${q ? ` matching "${q}"` : ''}.
      </td></tr>`;
    return;
  }

  const isAdmin = state.user?.isAdmin;
  document.getElementById('tableBody').innerHTML = filtered.map(e => `
    <tr class="table-row border-b border-gray-100 transition-colors">
      <td class="px-4 py-3 text-xs text-gray-400 font-mono">#${e.id}</td>
      <td class="px-4 py-3">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
            ${(e.firstName?.[0] || '?').toUpperCase()}
          </div>
          <div>
            <div class="font-medium text-gray-900 text-sm">${esc(e.fullName || `${e.firstName} ${e.lastName}`)}</div>
          </div>
        </div>
      </td>
      <td class="px-4 py-3 text-sm text-gray-600">${esc(e.email)}</td>
      <td class="px-4 py-3">
        <span class="inline-block bg-indigo-50 text-indigo-700 text-xs px-2.5 py-1 rounded-full font-medium">
          ${esc(e.department)}
        </span>
      </td>
      <td class="px-4 py-3 text-sm text-gray-600">${esc(e.designation)}</td>
      <td class="px-4 py-3 text-right text-sm font-medium text-gray-900">
        ₹${Number(e.salary).toLocaleString('en-IN')}
      </td>
      <td class="px-4 py-3 text-sm text-gray-500">${formatDate(e.dateOfJoining)}</td>
      ${isAdmin ? `
      <td class="px-4 py-3 text-center">
        <div class="flex items-center justify-center gap-1.5">
          <button onclick="openModal(${e.id})"
            class="p-1.5 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-all" title="Edit">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </button>
          <button onclick="openDeleteModal(${e.id})"
            class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-all" title="Delete">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </td>` : '<td></td>'}
    </tr>
  `).join('');
}

function updateStats(data) {
  document.getElementById('statTotal').textContent = data.totalElements ?? '—';
}

// ═══════════════════════════════════════════════════════════════
// PAGINATION
// ═══════════════════════════════════════════════════════════════

function renderPagination() {
  const { page, totalPages, totalElements, size } = state;
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to   = Math.min((page + 1) * size, totalElements);

  document.getElementById('paginationInfo').textContent =
    totalElements === 0 ? 'No records' : `Showing ${from}–${to} of ${totalElements} employees`;

  document.getElementById('prevBtn').disabled = page === 0;
  document.getElementById('nextBtn').disabled = page >= totalPages - 1;

  // Page number pills (show max 5 around current)
  const pages = [];
  const start = Math.max(0, page - 2);
  const end   = Math.min(totalPages - 1, start + 4);
  for (let i = start; i <= end; i++) pages.push(i);

  document.getElementById('pageNumbers').innerHTML = pages.map(i => `
    <button onclick="goToPage(${i})"
      class="w-7 h-7 text-xs rounded-lg font-medium transition-all ${i === page
        ? 'bg-indigo-600 text-white shadow-sm'
        : 'border border-gray-200 hover:bg-white text-gray-600'}">
      ${i + 1}
    </button>
  `).join('');
}

function changePage(delta) { goToPage(state.page + delta); }
function goToPage(p) {
  if (p < 0 || p >= state.totalPages) return;
  state.page = p;
  loadEmployees();
}

// ═══════════════════════════════════════════════════════════════
// SORTING
// ═══════════════════════════════════════════════════════════════

function sortBy(col) {
  if (state.sortBy === col) {
    state.direction = state.direction === 'asc' ? 'desc' : 'asc';
  } else {
    state.sortBy = col;
    state.direction = 'asc';
  }
  state.page = 0;

  // Update sort button styles
  document.querySelectorAll('.sort-btn').forEach(btn => {
    btn.classList.remove('asc', 'desc');
    if (btn.dataset.col === col) btn.classList.add(state.direction);
  });

  loadEmployees();
}

// ═══════════════════════════════════════════════════════════════
// SEARCH
// ═══════════════════════════════════════════════════════════════

function setupSearch() {
  let debounceTimer;
  document.getElementById('searchInput').addEventListener('input', e => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      state.searchQuery = e.target.value.trim();
      state.page = 0;
      loadEmployees();
    }, 300);
  });
}

// ═══════════════════════════════════════════════════════════════
// MODAL — ADD / EDIT
// ═══════════════════════════════════════════════════════════════

async function openModal(id = null) {
  clearModalForm();
  document.getElementById('editId').value = id || '';

  if (id) {
    document.getElementById('modalTitle').textContent   = 'Edit Employee';
    document.getElementById('modalSubtitle').textContent = `Updating record #${id}`;
    document.getElementById('saveBtn').textContent = 'Update Employee';

    try {
      const res = await fetch(`/api/employees/${id}`, { credentials: 'same-origin' });
      const emp = await res.json();
      document.getElementById('fFirstName').value   = emp.firstName   || '';
      document.getElementById('fLastName').value    = emp.lastName    || '';
      document.getElementById('fEmail').value       = emp.email       || '';
      document.getElementById('fDepartment').value  = emp.department  || '';
      document.getElementById('fDesignation').value = emp.designation || '';
      document.getElementById('fSalary').value      = emp.salary      || '';
      document.getElementById('fDoj').value         = emp.dateOfJoining || '';
    } catch {
      showToast('Failed to load employee data', 'error');
      return;
    }
  } else {
    document.getElementById('modalTitle').textContent    = 'Add Employee';
    document.getElementById('modalSubtitle').textContent = 'Fill in the employee details below';
    document.getElementById('saveBtn').textContent = 'Save Employee';
  }

  showModal('employeeModal');
}

function clearModalForm() {
  ['fFirstName','fLastName','fEmail','fDepartment','fDesignation','fSalary','fDoj']
    .forEach(id => document.getElementById(id).value = '');
  document.getElementById('modalError').classList.add('hidden');
}

/**
 * Phase 1 — Validate the form, then:
 *   - For EDIT (PUT): open the confirmation modal.
 *   - For ADD  (POST): proceed directly to executeSave() (no destructive risk).
 */
function saveEmployee() {
  const errEl  = document.getElementById('modalError');
  const payload = {
    firstName:     document.getElementById('fFirstName').value.trim(),
    lastName:      document.getElementById('fLastName').value.trim(),
    email:         document.getElementById('fEmail').value.trim(),
    department:    document.getElementById('fDepartment').value.trim(),
    designation:   document.getElementById('fDesignation').value.trim(),
    salary:        parseFloat(document.getElementById('fSalary').value),
    dateOfJoining: document.getElementById('fDoj').value,
  };

  // Client-side validation
  if (!payload.firstName || !payload.lastName || !payload.email ||
      !payload.department || !payload.designation || !payload.salary || !payload.dateOfJoining) {
    errEl.textContent = 'All fields are required.';
    errEl.classList.remove('hidden');
    return;
  }
  errEl.classList.add('hidden');

  const id = document.getElementById('editId').value;
  if (id) {
    // EDIT path — show confirmation dialog before mutating the record
    openSaveConfirmModal();
  } else {
    // ADD path — no existing data at risk, execute immediately
    executeSave();
  }
}

/** Phase 2 — Fires the actual PUT / POST request after confirmation (or directly for adds). */
async function executeSave() {
  closeSaveConfirmModal();

  const id  = document.getElementById('editId').value;
  const btn = document.getElementById('saveBtn');

  const payload = {
    firstName:     document.getElementById('fFirstName').value.trim(),
    lastName:      document.getElementById('fLastName').value.trim(),
    email:         document.getElementById('fEmail').value.trim(),
    department:    document.getElementById('fDepartment').value.trim(),
    designation:   document.getElementById('fDesignation').value.trim(),
    salary:        parseFloat(document.getElementById('fSalary').value),
    dateOfJoining: document.getElementById('fDoj').value,
  };

  btn.textContent = 'Saving…';
  btn.disabled = true;

  try {
    const url    = id ? `/api/employees/${id}` : '/api/employees';
    const method = id ? 'PUT' : 'POST';

    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(payload)
    });

    if (res.status === 403) {
      showModalError('You do not have permission to perform this action.');
      return;
    }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      if (err.fieldErrors) {
        const msgs = Object.values(err.fieldErrors).join(', ');
        showModalError(msgs);
      } else {
        showModalError(err.message || 'An error occurred. Please try again.');
      }
      return;
    }

    closeModal();
    showToast(id ? 'Employee updated successfully' : 'Employee added successfully', 'success');
    await loadEmployees();
    await loadAnalytics();
  } catch {
    showModalError('Network error. Please check your connection.');
  } finally {
    btn.textContent = id ? 'Update Employee' : 'Save Employee';
    btn.disabled = false;
  }
}

function showModalError(msg) {
  const el = document.getElementById('modalError');
  el.textContent = msg;
  el.classList.remove('hidden');
}

// ─── Save Confirmation helpers ───────────────────────────────────

/** Opens the save-confirmation dialog (sits above the edit modal at z-[60]). */
function openSaveConfirmModal() {
  const btn = document.getElementById('saveConfirmOkBtn');
  btn.textContent = 'Confirm';
  btn.disabled = false;
  showModal('saveConfirmModal');
}

/**
 * Closes the save-confirmation dialog and returns focus to the edit form.
 * The form's field values are intentionally preserved so the admin can
 * continue editing without losing their changes.
 */
function closeSaveConfirmModal() { hideModal('saveConfirmModal'); }

// ═══════════════════════════════════════════════════════════════
// MODAL — DELETE
// ═══════════════════════════════════════════════════════════════

function openDeleteModal(id) {
  state.deletingId = id;
  showModal('deleteModal');
}

async function confirmDelete() {
  const id  = state.deletingId;
  const btn = document.getElementById('confirmDeleteBtn');
  btn.textContent = 'Deleting…';
  btn.disabled = true;

  try {
    const res = await fetch(`/api/employees/${id}`, {
      method: 'DELETE',
      credentials: 'same-origin'
    });

    if (res.status === 403) {
      closeDeleteModal();
      showToast('Access denied — Admins only', 'error');
      return;
    }
    if (!res.ok) throw new Error();

    closeDeleteModal();
    showToast('Employee deleted successfully', 'success');
    if (state.page > 0 && (state.totalElements - 1) <= state.page * state.size) {
      state.page--;
    }
    await loadEmployees();
    await loadAnalytics();
  } catch {
    showToast('Failed to delete employee', 'error');
  } finally {
    btn.textContent = 'Delete';
    btn.disabled = false;
  }
}

function closeDeleteModal() { hideModal('deleteModal'); state.deletingId = null; }

// ═══════════════════════════════════════════════════════════════
// ANALYTICS
// ═══════════════════════════════════════════════════════════════

async function loadAnalytics() {
  try {
    const res = await fetch('/api/employees/analytics/department', { credentials: 'same-origin' });
    if (!res.ok) return;
    state.analytics = await res.json();

    // Populate stats
    const allSalaries = state.analytics.reduce((sum, d) => sum + d.averageSalary * d.employeeCount, 0);
    const totalEmp    = state.analytics.reduce((sum, d) => sum + d.employeeCount, 0);
    const globalAvg   = totalEmp > 0 ? allSalaries / totalEmp : 0;

    document.getElementById('statDepts').textContent    = state.analytics.length;
    document.getElementById('statAvgSalary').textContent = '₹' + Math.round(globalAvg).toLocaleString('en-IN');
  } catch {}
}

function renderAnalyticsCards() {
  const grid = document.getElementById('analyticsGrid');
  if (!state.analytics.length) {
    grid.innerHTML = '<p class="col-span-full text-center text-sm text-gray-400 py-10">No analytics data available yet. Add some employees first.</p>';
    return;
  }

  grid.innerHTML = state.analytics.map((d, i) => `
    <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 dept-card-${i % 6}">
      <div class="text-base font-semibold text-gray-900 mb-4">${esc(d.department)}</div>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-500 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Headcount
          </span>
          <span class="text-sm font-bold text-gray-900">${d.employeeCount}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-500 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Avg. Salary
          </span>
          <span class="text-sm font-bold text-gray-900">₹${Number(d.averageSalary).toLocaleString('en-IN', {maximumFractionDigits: 0})}</span>
        </div>
      </div>
    </div>
  `).join('');
}

// ═══════════════════════════════════════════════════════════════
// TOAST NOTIFICATIONS
// ═══════════════════════════════════════════════════════════════

let toastTimer;
function showToast(msg, type = 'success') {
  clearTimeout(toastTimer);
  const toast   = document.getElementById('toast');
  const content = document.getElementById('toastContent');
  const icon    = document.getElementById('toastIcon');
  const msgEl   = document.getElementById('toastMsg');

  msgEl.textContent = msg;
  content.className = 'flex items-center gap-3 px-5 py-3.5 rounded-2xl shadow-xl text-sm font-medium text-white min-w-[220px] ' +
    (type === 'success' ? 'bg-emerald-600' : 'bg-red-600');

  icon.innerHTML = type === 'success'
    ? '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />'
    : '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />';

  toast.classList.remove('hidden');
  toastTimer = setTimeout(() => toast.classList.add('hidden'), 3500);
}

// ═══════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════

function showModal(id) {
  const el = document.getElementById(id);
  el.classList.remove('hidden');
  el.classList.add('flex');
}
function hideModal(id) {
  const el = document.getElementById(id);
  el.classList.add('hidden');
  el.classList.remove('flex');
}
function closeModal() { hideModal('employeeModal'); }

/** Escape HTML to prevent XSS */
function esc(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** Format ISO date to readable format */
function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

// Close modals on backdrop click
document.getElementById('employeeModal').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeModal();
});
document.getElementById('deleteModal').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeDeleteModal();
});
document.getElementById('saveConfirmModal').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeSaveConfirmModal();
});
