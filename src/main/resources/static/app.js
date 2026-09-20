'use strict';

const state = {
  projects: [],
  tasks: [],
  projectsById: new Map()
};

const el = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (res.status === 204) {
    return null;
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const errors = body.fieldErrors ? Object.values(body.fieldErrors).join('; ') : body.message;
    throw new Error(errors || (res.status + ' ' + res.statusText));
  }
  return body;
}

function text(node, value) {
  node.textContent = value;
}

function badgeNode(value) {
  const span = document.createElement('span');
  span.className = 'badge ' + String(value).replace(/[^a-zA-Z0-9_-]/g, '');
  text(span, value);
  return span;
}

function renderMeta() {
  api('/api/meta').then((m) => {
    const meta = el('meta');
    text(meta, 'release ' + m.release + ' · Java ' + m.javaVersion);
  }).catch(() => {});
}

function renderProjects() {
  const list = el('projectList');
  list.textContent = '';
  state.projects.forEach((p) => {
    const li = document.createElement('li');
    li.className = 'card';

    const title = document.createElement('strong');
    text(title, p.name);
    const describe = document.createElement('p');
    text(describe, p.description);
    const status = badgeNode(p.status);

    const actions = document.createElement('div');
    actions.className = 'actions';
    const editBtn = document.createElement('button');
    text(editBtn, 'Edit');
    editBtn.type = 'button';
    editBtn.addEventListener('click', () => editProject(p));
    const delBtn = document.createElement('button');
    text(delBtn, 'Delete');
    delBtn.type = 'button';
    delBtn.className = 'danger';
    delBtn.addEventListener('click', () => deleteProject(p));
    actions.append(editBtn, delBtn);

    li.append(title, describe, status, actions);
    list.append(li);
  });
}

function renderTasks() {
  const list = el('taskList');
  list.textContent = '';
  state.tasks.forEach((t) => {
    const li = document.createElement('li');
    li.className = 'card';

    const title = document.createElement('strong');
    text(title, t.title);
    const describe = document.createElement('p');
    text(describe, t.description);
    const line = document.createElement('div');
    spanInto(line, t.projectName || ('project #' + t.projectId));
    line.append(badgeNode(t.status), badgeNode(t.priority));

    const actions = document.createElement('div');
    actions.className = 'actions';
    const editBtn = document.createElement('button');
    text(editBtn, 'Edit');
    editBtn.type = 'button';
    editBtn.addEventListener('click', () => editTask(t));
    const delBtn = document.createElement('button');
    text(delBtn, 'Delete');
    delBtn.type = 'button';
    delBtn.className = 'danger';
    delBtn.addEventListener('click', () => deleteTask(t));
    actions.append(editBtn, delBtn);

    li.append(title, describe, line, actions);
    list.append(li);
  });
}

function spanInto(parent, value) {
  const span = document.createElement('span');
  text(span, value);
  parent.append(span);
}

async function loadProjects() {
  const q = el('projectSearch').value.trim();
  const status = el('projectStatusFilter').value;
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  if (status) params.set('status', status);
  const query = params.toString() ? '?' + params.toString() : '';
  state.projects = await api('/api/projects' + query);
  state.projectsById = new Map(state.projects.map((p) => [p.id, p]));
  renderProjects();
}

async function loadTasks() {
  const q = el('taskSearch').value.trim();
  const status = el('taskStatusFilter').value;
  const priority = el('taskPriorityFilter').value;
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  if (status) params.set('status', status);
  if (priority) params.set('priority', priority);
  const query = params.toString() ? '?' + params.toString() : '';
  state.tasks = await api('/api/tasks' + query);
  renderTasks();
}

async function refresh() {
  await Promise.all([loadProjects(), loadTasks()]);
}

function projectOptions(selectedId) {
  const name = 'projectId';
  const select = document.createElement('select');
  select.name = name;
  select.required = true;
  state.projects.forEach((p) => {
    const opt = document.createElement('option');
    opt.value = String(p.id);
    text(opt, p.name);
    if (p.id === selectedId) opt.selected = true;
    select.append(opt);
  });
  return select;
}

function textInput(name, value, required, maxLength) {
  const input = document.createElement('input');
  input.name = name;
  input.value = value == null ? '' : String(value);
  if (required) input.required = true;
  if (maxLength) input.maxLength = maxLength;
  return input;
}

function textArea(name, value, maxLength) {
  const area = document.createElement('textarea');
  area.name = name;
  area.value = value == null ? '' : String(value);
  if (maxLength) area.maxLength = maxLength;
  return area;
}

function selectField(name, value, options) {
  const select = document.createElement('select');
  select.name = name;
  const blank = document.createElement('option');
  blank.value = '';
  text(blank, 'Default');
  select.append(blank);
  options.forEach((o) => {
    const opt = document.createElement('option');
    opt.value = o;
    text(opt, o);
    if (o === value) opt.selected = true;
    select.append(opt);
  });
  return select;
}

function label(textValue, control) {
  const lab = document.createElement('label');
  const name = document.createElement('span');
  text(name, textValue);
  lab.append(name, control);
  return lab;
}

let dialogMode = null;

function openDialog(title, fields, initialMode) {
  dialogMode = initialMode;
  text(el('dialogTitle'), title);
  const container = el('dialogFields');
  container.textContent = '';
  fields.forEach((f) => container.append(f));
  const error = el('dialogError');
  error.classList.add('hidden');
  el('dialogForm').setAttribute('method', 'dialog');
  el('dialog').showModal();
}

el('dialogForm').addEventListener('submit', (e) => {
  e.preventDefault();
  submitDialog();
  closeAndReset(e);
});

function closeAndReset(e) {
  const dialog = el('dialog');
  if (dialog.open) {
    e.preventDefault();
    dialog.close();
  }
}

async function submitDialog() {
  const data = new FormData(el('dialogForm'));
  const body = Object.fromEntries(data.entries());
  try {
    if (dialogMode === 'create-project') {
      await api('/api/projects', { method: 'POST', body: JSON.stringify(body) });
    } else if (dialogMode === 'edit-project') {
      await api('/api/projects/' + body.id, { method: 'PATCH', body: JSON.stringify(stripBlank(body)) });
    } else if (dialogMode === 'create-task') {
      await api('/api/tasks', { method: 'POST', body: JSON.stringify(body) });
    } else if (dialogMode === 'edit-task') {
      await api('/api/tasks/' + body.id, { method: 'PATCH', body: JSON.stringify(stripBlank(body)) });
    }
    await refresh();
  } catch (err) {
    const error = el('dialogError');
    text(error, err.message);
    error.classList.remove('hidden');
    throw err;
  }
}

function stripBlank(obj) {
  const out = {};
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== '') out[k] = v;
    else if (k === 'id') out[k] = v;
  });
  return out;
}

function newProject() {
  openDialog('New project', [
    label('Name', textInput('name', '', true, 100)),
    label('Description', textArea('description', '', 1000)),
    label('Status', selectField('status', '', ['active', 'archived']))
  ], 'create-project');
}

function editProject(p) {
  openDialog('Edit project', [
    hiddenField('id', p.id),
    label('Name', textInput('name', p.name, true, 100)),
    label('Description', textArea('description', p.description, 1000)),
    label('Status', selectField('status', p.status, ['active', 'archived']))
  ], 'edit-project');
}

function hiddenField(name, value) {
  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = name;
  input.value = String(value);
  return input;
}

function newTask() {
  const projectId = state.projects.length ? state.projects[0].id : null;
  openDialog('New task', [
    label('Project', projectOptions(projectId)),
    label('Title', textInput('title', '', true, 200)),
    label('Description', textArea('description', '', 1000)),
    label('Status', selectField('status', '', ['todo', 'in_progress', 'done'])),
    label('Priority', selectField('priority', '', ['low', 'medium', 'high']))
  ], 'create-task');
}

function editTask(t) {
  openDialog('Edit task', [
    hiddenField('id', t.id),
    label('Title', textInput('title', t.title, true, 200)),
    label('Description', textArea('description', t.description, 1000)),
    label('Status', selectField('status', t.status, ['todo', 'in_progress', 'done'])),
    label('Priority', selectField('priority', t.priority, ['low', 'medium', 'high']))
  ], 'edit-task');
}

async function deleteProject(p) {
  if (!window.confirm('Delete project "' + p.name + '" and its tasks?')) return;
  try {
    await api('/api/projects/' + p.id, { method: 'DELETE' });
    await refresh();
  } catch (err) {
    window.alert(err.message);
  }
}

async function deleteTask(t) {
  if (!window.confirm('Delete task "' + t.title + '"?')) return;
  try {
    await api('/api/tasks/' + t.id, { method: 'DELETE' });
    await refresh();
  } catch (err) {
    window.alert(err.message);
  }
}

el('newProjectBtn').addEventListener('click', newProject);
el('newTaskBtn').addEventListener('click', newTask);
el('dialogCancel').addEventListener('click', () => el('dialog').close());

['projectSearch', 'projectStatusFilter'].forEach((id) => {
  el(id).addEventListener('input', loadProjects);
});
['taskSearch', 'taskStatusFilter', 'taskPriorityFilter'].forEach((id) => {
  el(id).addEventListener('input', loadTasks);
});

renderMeta();
refresh();