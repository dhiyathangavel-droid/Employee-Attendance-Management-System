// Cleaned and fixed: single DOMContentLoaded handler, guards for missing DOM elements,
// auto-creates table + tbody if missing, removed duplicated block.

document.addEventListener('DOMContentLoaded', () => {
  // DOM refs (resolved after DOM is ready)
  const dateInput = document.querySelector('.controls-row input[type="date"]');
  const searchInput = document.querySelector('.controls-row input[type="text"]');
  const deptSelect = document.querySelector('.controls-row select');
  const addBtn = document.querySelector('header .btn-primary');
  const exportBtn = document.querySelector('header .btn-ghost');
  let tbody = document.querySelector('table tbody');
  const statEls = document.querySelectorAll('aside .summary .stat h3');
  const recentList = document.querySelector('.list-scroll');
  const container = document.querySelector('.container');

  // Ensure a table and tbody exist so script won't abort on minimal index.html
  (function ensureTbody() {
    let table = document.querySelector('table');
    if (!table && container) {
      table = document.createElement('table');
      table.setAttribute('aria-label', 'attendance table');
      table.innerHTML = `
        <thead>
          <tr>
            <th>Employee</th><th>Date</th><th>Status</th><th class="text-right">Notes</th>
          </tr>
        </thead>
      `;
      container.insertBefore(table, container.firstChild);
    }

    if (!table) {
      console.warn('app.js: no container/table found in DOM — some UI features will be unavailable.');
      tbody = null;
      return;
    }

    tbody = table.querySelector('tbody');
    if (!tbody) {
      tbody = document.createElement('tbody');
      table.appendChild(tbody);
    }
  })();

  // In-memory model
  const data = [];

  // Helpers
  const toISO = (d) => {
    if (!d) return '';
    const dt = new Date(d);
    if (Number.isNaN(dt.getTime())) return '';
    return dt.toISOString().slice(0, 10);
  };

  const initialsFrom = (name = '') =>
    name
      .split(' ')
      .map((s) => (s ? s[0] : ''))
      .slice(0, 2)
      .join('')
      .toUpperCase();

  const makeId = () => {
    try {
      if (window.crypto && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    } catch { /* ignore */ }
    return Date.now().toString();
  };

  // Load existing rows from DOM into data array (if index.html has sample rows)
  function loadInitial() {
    if (!tbody) return;
    tbody.querySelectorAll('tr').forEach((tr) => {
      const cols = tr.querySelectorAll('td');
      if (cols.length < 4) return;
      const nameEl = cols[0].querySelector('div > div') || cols[0];
      const deptEl = cols[0].querySelector('.tiny');
      const name = nameEl.textContent.trim();
      const dept = deptEl ? deptEl.textContent.trim() : '';
      const date = cols[1].textContent.trim();
      const status = cols[2].textContent.trim();
      const notes = cols[3].textContent.trim();
      data.push({
        id: makeId(),
        name,
        dept,
        date: toISO(date),
        status,
        notes,
        initials: initialsFrom(name),
      });
    });
  }

  // Render helpers
  function renderTable(rows) {
    if (!tbody) return;
    tbody.innerHTML = '';
    const frag = document.createDocumentFragment();
    rows.forEach((r) => {
      const tr = document.createElement('tr');

      const tdName = document.createElement('td');
      tdName.innerHTML = `
        <div class="employee-row">
          <div class="avatar">${escapeHtml(r.initials)}</div>
          <div>
            <div>${escapeHtml(r.name)}</div>
            <div class="tiny">${escapeHtml(r.dept)}</div>
          </div>
        </div>
      `;

      const tdDate = document.createElement('td');
      tdDate.textContent = r.date || '';

      const tdStatus = document.createElement('td');
      const span = document.createElement('span');
      span.textContent = r.status || '';
      span.className = (r.status || '').toLowerCase().includes('present') ? 'status-present' : 'status-absent';
      tdStatus.appendChild(span);

      const tdNotes = document.createElement('td');
      tdNotes.className = 'text-right tiny';
      tdNotes.textContent = r.notes || '';

      tr.appendChild(tdName);
      tr.appendChild(tdDate);
      tr.appendChild(tdStatus);
      tr.appendChild(tdNotes);

      frag.appendChild(tr);
    });
    tbody.appendChild(frag);
  }

  function renderSummary() {
    const totalEmployees = new Set(data.map((d) => d.name)).size;
    const present = data.filter((d) => (d.status || '').toLowerCase().includes('present')).length;
    const absent = data.filter((d) => (d.status || '').toLowerCase().includes('absent')).length;

    if (statEls[0]) statEls[0].textContent = String(totalEmployees);
    if (statEls[1]) statEls[1].textContent = String(present);
    if (statEls[2]) statEls[2].textContent = String(absent);
  }

  function addRecentActivity(msg) {
    if (!recentList) return;
    const div = document.createElement('div');
    div.className = 'muted mb-8';
    div.textContent = msg;
    recentList.insertBefore(div, recentList.firstChild);
  }

  // Filtering
  function applyFilters() {
    const date = dateInput ? toISO(dateInput.value) : '';
    const query = searchInput ? (searchInput.value || '').trim().toLowerCase() : '';
    const dept = deptSelect ? deptSelect.value : '';

    const filtered = data.filter((d) => {
      if (date && d.date !== date) return false;
      if (dept && dept !== 'All Departments' && d.dept !== dept) return false;
      if (query) {
        const hay = `${d.name} ${d.dept} ${d.notes} ${d.status}`.toLowerCase();
        if (!hay.includes(query)) return false;
      }
      return true;
    });

    renderTable(filtered);
  }

  // Add attendance (simple prompts)
  function addAttendance() {
    const name = (prompt('Employee name') || '').trim();
    if (!name) return alert('Name is required.');

    const dept = (prompt('Department (e.g. Engineering)') || '').trim() || 'General';
    const dateRaw = prompt('Date (YYYY-MM-DD)') || new Date().toISOString().slice(0, 10);
    const date = toISO(dateRaw);
    const status = (prompt('Status (Present / Absent)') || 'Present').trim();
    const notes = (prompt('Notes (optional)') || '').trim();

    const rec = {
      id: makeId(),
      name,
      dept,
      date,
      status,
      notes,
      initials: initialsFrom(name),
    };
    data.unshift(rec);
    applyFilters();
    renderSummary();
    addRecentActivity(`${rec.name} added ${rec.date} — ${rec.status}`);
  }

  // Export CSV of currently visible rows
  function exportCSV() {
    const date = dateInput ? toISO(dateInput.value) : '';
    const query = searchInput ? (searchInput.value || '').trim().toLowerCase() : '';
    const dept = deptSelect ? deptSelect.value : '';

    const rows = data.filter((d) => {
      if (date && d.date !== date) return false;
      if (dept && dept !== 'All Departments' && d.dept !== dept) return false;
      if (query) {
        const hay = `${d.name} ${d.dept} ${d.notes} ${d.status}`.toLowerCase();
        if (!hay.includes(query)) return false;
      }
      return true;
    });

    if (!rows.length) return alert('No records to export.');

    const csv = [
      ['Name', 'Department', 'Date', 'Status', 'Notes'],
      ...rows.map((r) => [r.name, r.dept, r.date, r.status, r.notes]),
    ]
      .map((r) => r.map(csvEscape).join(','))
      .join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'attendance-export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  // Utilities
  function csvEscape(v) {
    if (v == null) return '';
    const s = String(v);
    if (s.includes('"') || s.includes(',') || s.includes('\n')) return `"${s.replace(/"/g, '""')}"`;
    return s;
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  // Event wiring
  function wire() {
    if (dateInput) dateInput.addEventListener('change', applyFilters);
    if (searchInput) searchInput.addEventListener('input', debounce(applyFilters, 200));
    if (deptSelect) deptSelect.addEventListener('change', applyFilters);
    if (addBtn) addBtn.addEventListener('click', addAttendance);
    if (exportBtn) exportBtn.addEventListener('click', exportCSV);
  }

  // Small debounce
  function debounce(fn, ms) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...args), ms);
    };
  }

  // Init
  loadInitial();
  renderSummary();
  applyFilters();
  wire();
});