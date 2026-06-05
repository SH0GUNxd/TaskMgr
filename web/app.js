const API = '/api';
let tasks        = [];
let activeFilter = 'all';
let editingId    = null;
let pendingDelete = null;
let dragSrcIdx      = null;
let currentView     = 'list';
let kanbanDragId    = null;

// API helpers
async function apiFetch(path, opts = {}) {
  const r = await fetch(API + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts
  });
  if (!r.ok) {
    const err = await r.json().catch(() => ({ error: r.statusText }));
    throw new Error(err.error || r.statusText);
  }
  return r.status === 204 ? null : r.json();
}

// Load & render
async function load() {
  try {
    const [fetchedTasks, stats] = await Promise.all([
      apiFetch('/tasks'),
      apiFetch('/stats')
    ]);
    tasks = fetchedTasks;
    updateStats(stats);
    renderTasks();
  } catch (e) {
    toast('Server connection error', 'red');
  }
}

function updateStats(stats) {
  document.getElementById('stat-total').textContent = stats.total;
  document.getElementById('stat-todo').textContent  = stats.todo;
  document.getElementById('stat-doing').textContent = stats.doing;
  document.getElementById('stat-done').textContent  = stats.done;

  document.getElementById('nav-all').textContent    = stats.total;
  document.getElementById('nav-todo').textContent   = stats.todo;
  document.getElementById('nav-doing').textContent  = stats.doing;
  document.getElementById('nav-done').textContent   = stats.done;

  const overdue = tasks.filter(t => isOverdue(t)).length;
  document.getElementById('nav-overdue').textContent = overdue;

  const pct = stats.total ? Math.round(stats.done / stats.total * 100) : 0;
  document.getElementById('progress-fill').style.width = pct + '%';
  document.getElementById('progress-pct').textContent  = pct + '%';
}

function isOverdue(t) {
  return t.status !== 'DONE' && t.dueDate && t.dueDate < today();
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function filteredTasks() {
  const q = document.getElementById('search-input').value.toLowerCase().trim();
  return tasks.filter(t => {
    const matchFilter =
      activeFilter === 'all'     ? true :
      activeFilter === 'overdue' ? isOverdue(t) :
      t.status === activeFilter;
    const matchSearch = !q ||
      t.title.toLowerCase().includes(q) ||
      (t.description || '').toLowerCase().includes(q);
    return matchFilter && matchSearch;
  });
}

function renderTasks() {
  if (currentView === 'kanban') { renderKanban(); return; }
  const list = document.getElementById('task-list');
  const filtered = filteredTasks();

  if (!filtered.length) {
    list.innerHTML = `
      <div class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="18" rx="3"/>
          <path d="M9 12h6M12 9v6"/>
        </svg>
        <p>No tasks to display</p>
      </div>`;
    return;
  }

  list.innerHTML = filtered.map((t, idx) => {
    const overdue = isOverdue(t);
    const pill = overdue
      ? `<span class="pill pill-overdue">OVERDUE</span>`
      : `<span class="pill pill-${t.status.toLowerCase()}">${t.status}</span>`;
    const todayStr  = today();
    const daysUntil = t.dueDate ? Math.ceil((new Date(t.dueDate) - new Date(todayStr)) / 86400000) : 999;
    const dateClass = overdue ? 'task-date overdue' : daysUntil <= 3 ? 'task-date warning' : 'task-date';
    const priorityPill = t.priority ? `<span class="pill pill-${t.priority.toLowerCase()}">${t.priority}</span>` : '';
    const dateStr   = t.dueDate ? formatDate(t.dueDate) : '-';

    return `
    <div class="task-card" draggable="true"
         data-id="${t.id}" data-idx="${idx}"
         ondragstart="onDragStart(event,${idx})"
         ondragover="onDragOver(event)"
         ondragleave="onDragLeave(event)"
         ondrop="onDrop(event,${idx})"
         ondragend="onDragEnd(event)">
      <div class="drag-handle">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
          <circle cx="9" cy="5" r="1" fill="currentColor"/><circle cx="15" cy="5" r="1" fill="currentColor"/>
          <circle cx="9" cy="12" r="1" fill="currentColor"/><circle cx="15" cy="12" r="1" fill="currentColor"/>
          <circle cx="9" cy="19" r="1" fill="currentColor"/><circle cx="15" cy="19" r="1" fill="currentColor"/>
        </svg>
      </div>
      <div class="task-check ${t.status === 'DONE' ? 'done' : ''}" onclick="toggleDone(${t.id})">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
      </div>
      <div class="task-body">
        <div class="task-title ${t.status === 'DONE' ? 'done' : ''}">${escHtml(t.title)}</div>
        ${t.description ? `<div class="task-desc">${escHtml(t.description)}</div>` : ''}
        <div class="task-meta">
          ${pill}
          ${priorityPill}
          <span class="${dateClass}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            ${dateStr}
          </span>
        </div>
      </div>
      <div class="task-actions">
        <button class="icon-btn" onclick="openModal(${t.id})" title="Edit">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <button class="icon-btn delete" onclick="confirmDelete(${t.id})" title="Delete">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>
        </button>
      </div>
    </div>`;
  }).join('');
}

// Filter & nav
function setFilter(status, btn) {
  activeFilter = status;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  // Sync sidebar
  document.querySelectorAll('.nav-item').forEach(n => {
    n.classList.toggle('active', n.dataset.filter === status);
  });
  renderTasks();
}

function setNavFilter(status, item) {
  activeFilter = status;
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  item.classList.add('active');
  // Sync toolbar
  document.querySelectorAll('.filter-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.status === status);
  });
  renderTasks();
}

// Toggle done
async function toggleDone(id) {
  const t = tasks.find(t => t.id === id);
  if (!t) return;
  const newStatus = t.status === 'DONE' ? 'TODO' : 'DONE';
  try {
    await apiFetch('/tasks/' + id, {
      method: 'PUT',
      body: JSON.stringify({ status: newStatus })
    });
    toast(newStatus === 'DONE' ? 'Task completed ✓' : 'Task reopened', newStatus === 'DONE' ? 'green' : 'accent');
    await load();
  } catch (e) { toast(e.message, 'red'); }
}

// Modal
function openModal(id = null) {
  editingId = id;
  const overlay = document.getElementById('modal-overlay');
  document.getElementById('modal-title').textContent = id ? 'Edit Task' : 'New Task';

  if (id) {
    const t = tasks.find(t => t.id === id);
    document.getElementById('f-title').value  = t.title;
    document.getElementById('f-desc').value   = t.description || '';
    document.getElementById('f-date').value   = t.dueDate || '';
    document.getElementById('f-status').value   = t.status;
    document.getElementById('f-priority').value = t.priority || 'MEDIUM';
  } else {
    document.getElementById('f-title').value  = '';
    document.getElementById('f-desc').value   = '';
    document.getElementById('f-date').value   = tomorrow();
    document.getElementById('f-status').value   = 'TODO';
    document.getElementById('f-priority').value = 'MEDIUM';
  }
  overlay.classList.add('open');
  setTimeout(() => document.getElementById('f-title').focus(), 150);
}

function closeModal() {
  document.getElementById('modal-overlay').classList.remove('open');
  editingId = null;
}

function closeModalOnOverlay(e) {
  if (e.target === document.getElementById('modal-overlay')) closeModal();
}

async function submitTask() {
  const title  = document.getElementById('f-title').value.trim();
  const desc   = document.getElementById('f-desc').value.trim();
  const date   = document.getElementById('f-date').value;
  const status = document.getElementById('f-status').value;

  if (!title) {
    document.getElementById('f-title').focus();
    toast('Title is required', 'red');
    return;
  }

  const priority = document.getElementById('f-priority').value;
  const body = JSON.stringify({ title, description: desc, dueDate: date, status, priority });

  try {
    if (editingId) {
      await apiFetch('/tasks/' + editingId, { method: 'PUT', body });
      toast('Task updated', 'accent');
    } else {
      await apiFetch('/tasks', { method: 'POST', body });
      toast('Task created', 'green');
    }
    closeModal();
    await load();
  } catch (e) { toast(e.message, 'red'); }
}

// Delete confirm
function confirmDelete(id) {
  pendingDelete = id;
  const t = tasks.find(t => t.id === id);
  document.getElementById('confirm-msg').textContent =
    `Delete "${t ? t.title : id}"? This action cannot be undone.`;
  document.getElementById('confirm-overlay').classList.add('open');
}

function closeConfirm() {
  document.getElementById('confirm-overlay').classList.remove('open');
  pendingDelete = null;
}

function closeConfirmOnOverlay(e) {
  if (e.target === document.getElementById('confirm-overlay')) closeConfirm();
}

async function confirmAction() {
  if (!pendingDelete) return;
  try {
    await apiFetch('/tasks/' + pendingDelete, { method: 'DELETE' });
    toast('Task deleted', 'red');
    closeConfirm();
    await load();
  } catch (e) { toast(e.message, 'red'); }
}

// Drag & drop
function onDragStart(e, idx) {
  dragSrcIdx = idx;
  e.currentTarget.classList.add('dragging');
  e.dataTransfer.effectAllowed = 'move';
}
function onDragOver(e) {
  e.preventDefault();
  e.currentTarget.classList.add('drag-over');
}
function onDragLeave(e) {
  e.currentTarget.classList.remove('drag-over');
}
function onDrop(e, idx) {
  e.preventDefault();
  e.currentTarget.classList.remove('drag-over');
  if (dragSrcIdx === null || dragSrcIdx === idx) return;
  const filtered = filteredTasks();
  const src = filtered[dragSrcIdx];
  const dst = filtered[idx];
  // Swap in the full tasks array
  const si = tasks.indexOf(src);
  const di = tasks.indexOf(dst);
  [tasks[si], tasks[di]] = [tasks[di], tasks[si]];
  renderTasks();
}
function onDragEnd(e) {
  e.currentTarget.classList.remove('dragging');
  dragSrcIdx = null;
}

// Keyboard shortcuts
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') { closeModal(); closeConfirm(); }
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    if (document.getElementById('modal-overlay').classList.contains('open')) submitTask();
  }
  if (e.key === 'n' && !e.ctrlKey && !e.metaKey &&
      document.activeElement.tagName !== 'INPUT' &&
      document.activeElement.tagName !== 'TEXTAREA') {
    openModal();
  }
});

// Toast
function toast(msg, type = 'accent') {
  const c = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = 'toast';
  el.innerHTML = `<div class="toast-dot ${type}"></div>${escHtml(msg)}`;
  c.appendChild(el);
  setTimeout(() => {
    el.classList.add('out');
    el.addEventListener('animationend', () => el.remove());
  }, 3000);
}

// Utils
function escHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatDate(iso) {
  if (!iso) return '-';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

function tomorrow() {
  const d = new Date(); d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
}

// Export CSV
function exportCsv() {
  const a = document.createElement('a');
  a.href     = '/api/export';
  a.download = 'tasks.csv';
  a.click();
  toast('CSV export downloaded', 'green');
}

// View toggle
function setView(view) {
  currentView = view;
  document.getElementById('task-list').style.display    = view === 'list'   ? 'flex'  : 'none';
  document.getElementById('kanban-board').style.display = view === 'kanban' ? 'grid'  : 'none';
  document.getElementById('btn-list').classList.toggle('active',   view === 'list');
  document.getElementById('btn-kanban').classList.toggle('active', view === 'kanban');
  // Filters don't apply in kanban (all tasks shown by status column)
  document.querySelector('.filter-group').style.opacity = view === 'kanban' ? '.4' : '1';
  document.querySelector('.filter-group').style.pointerEvents = view === 'kanban' ? 'none' : '';
  renderTasks();
}

// Kanban render
function renderKanban() {
  const q = document.getElementById('search-input').value.toLowerCase().trim();
  const statuses = ['TODO', 'DOING', 'DONE'];

  for (const status of statuses) {
    const col   = document.getElementById('kcards-' + status);
    const count = document.getElementById('kcnt-' + status);
    const filtered = tasks.filter(t => {
      const matchStatus = t.status === status;
      const matchSearch = !q || t.title.toLowerCase().includes(q) || (t.description||'').toLowerCase().includes(q);
      return matchStatus && matchSearch;
    });

    count.textContent = filtered.length;

    if (!filtered.length) {
      col.innerHTML = '<div class="kanban-empty">No tasks</div>';
      continue;
    }

    col.innerHTML = filtered.map(t => {
      const overdue   = isOverdue(t);
      const dateClass = overdue ? 'kanban-card-date overdue' : 'kanban-card-date';
      const dateStr   = t.dueDate ? formatDate(t.dueDate) : '-';
      return `
      <div class="kanban-card" draggable="true"
           data-id="${t.id}"
           ondragstart="onKanbanCardDragStart(event,${t.id})"
           ondragend="onKanbanCardDragEnd(event)">
        <div class="kanban-card-title ${t.status === 'DONE' ? 'done' : ''}">${escHtml(t.title)}</div>
        <div class="kanban-card-meta">
          <span class="${dateClass}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            ${dateStr}
          </span>
          <div class="kanban-card-actions">
            <button class="icon-btn" onclick="openModal(${t.id})" title="Edit">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            <button class="icon-btn delete" onclick="confirmDelete(${t.id})" title="Delete">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M9 6V4h6v2"/></svg>
            </button>
          </div>
        </div>
      </div>`;
    }).join('');
  }
}

// Kanban drag & drop (between columns = change status)
function onKanbanCardDragStart(e, id) {
  kanbanDragId = id;
  e.currentTarget.classList.add('dragging');
  e.dataTransfer.effectAllowed = 'move';
}

function onKanbanCardDragEnd(e) {
  e.currentTarget.classList.remove('dragging');
}

function onKanbanColDragOver(e, status) {
  e.preventDefault();
  document.getElementById('col-' + status).classList.add('drag-over');
}

function onKanbanColDragLeave(e) {
  e.currentTarget.classList.remove('drag-over');
}

async function onKanbanColDrop(e, newStatus) {
  e.preventDefault();
  document.getElementById('col-' + newStatus).classList.remove('drag-over');
  if (!kanbanDragId) return;

  const t = tasks.find(t => t.id === kanbanDragId);
  if (!t || t.status === newStatus) { kanbanDragId = null; return; }

  try {
    await apiFetch('/tasks/' + kanbanDragId, {
      method: 'PUT',
      body: JSON.stringify({ status: newStatus })
    });
    toast('Moved to ' + newStatus, 'accent');
    await load();
  } catch (err) {
    toast(err.message, 'red');
  }
  kanbanDragId = null;
}

// Boot
load();