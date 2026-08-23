const $ = s => document.querySelector(s);
const api = async (path, options = {}) => {
  const res = await fetch(path, {credentials:'include', headers:{'Content-Type':'application/json', ...(options.headers||{})}, ...options});
  let data = {};
  try { data = await res.json(); } catch {}
  if (!res.ok) throw Object.assign(new Error(data.message || data.error || `HTTP ${res.status}`), {status:res.status, data});
  return data;
};
const esc = value => String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
const date = ts => new Date(ts * 1000).toLocaleString('ru-RU');
const roleLabel = r => ({admin:'Администратор',support:'Тех. Поддержка',user:'Игрок'}[r] || r);
const badge = r => `<span class="role-badge ${esc(r)}">${roleLabel(r)}</span>`;
const statusText = s => ({open:'Открыт',pending:'Ожидает',closed:'Закрыт'}[s] || s);
let me = null;

async function boot() {
  try {
    me = await api('/api/v1/me');
    if (!['support','admin'].includes(me.role)) throw Object.assign(new Error('Доступ только для сотрудников.'), {status:403});
    $('#staff-identity').innerHTML = `${esc(me.display_name)} ${badge(me.role)}`;
    $('#staff-panel').classList.remove('hidden');
    if (me.role === 'admin') {
      $('#admin-users').classList.remove('hidden');
      $('#admin-audit').classList.remove('hidden');
    }
    await Promise.all([
      loadStats(),
      loadTickets(),
      me.role === 'admin' ? loadUsers() : Promise.resolve(),
      me.role === 'admin' ? loadAudit() : Promise.resolve()
    ]);
  } catch (error) {
    const box = $('#staff-denied');
    box.textContent = error.status === 401 ? 'Сначала войди в GGO Account.' : error.message;
    box.classList.remove('hidden');
    $('#staff-identity').textContent = 'Нет доступа';
  }
}

async function loadStats() {
  const data = await api('/api/v1/staff/stats');
  const counts = data.tickets;
  $('#staff-stats').innerHTML = `<div><b>${counts.open||0}</b><span>Открыто</span></div><div><b>${counts.pending||0}</b><span>Ожидает</span></div><div><b>${counts.closed||0}</b><span>Закрыто</span></div>`;
}

async function loadTickets() {
  const filter = $('#ticket-filter').value;
  const data = await api(`/api/v1/staff/tickets${filter ? `?status=${encodeURIComponent(filter)}` : ''}`);
  const list = $('#staff-ticket-list');
  if (!data.tickets.length) { list.innerHTML = '<div class="empty-state">В этой очереди тикетов нет.</div>'; return; }
  list.innerHTML = data.tickets.map(renderTicket).join('');
}

function renderTicket(t) {
  const messages = t.messages.map(m => `<div class="ticket-message-row ${m.author.role==='user'?'player':'staff'}"><div class="ticket-author"><strong>${esc(m.author.display_name)}</strong>${m.author.role!=='user'?badge(m.author.role):''}<time>${date(m.created_at)}</time></div><div class="ticket-text">${esc(m.body).replace(/\n/g,'<br>')}</div></div>`).join('');
  return `<article class="ticket-card staff-ticket" data-ticket="${esc(t.id)}"><div class="ticket-card-head"><div><div class="ticket-meta"><span>#${esc(t.id)}</span><span>${esc(t.category)}</span><span class="ticket-status ${esc(t.status)}">${statusText(t.status)}</span></div><h3>${esc(t.subject)}</h3><div class="ticket-owner">${esc(t.owner.display_name)}</div></div><time>${date(t.updated_at)}</time></div><div class="ticket-thread">${messages}</div><div class="ticket-reply"><textarea maxlength="8000" placeholder="Ответ сотрудника"></textarea><button class="ghost-button" data-action="reply">Ответить</button><select data-action="status" class="compact-select"><option value="open" ${t.status==='open'?'selected':''}>Открыт</option><option value="pending" ${t.status==='pending'?'selected':''}>Ожидает</option><option value="closed" ${t.status==='closed'?'selected':''}>Закрыт</option></select></div></article>`;
}

async function loadUsers() {
  const q = $('#user-search').value.trim();
  const data = await api(`/api/v1/admin/users${q ? `?q=${encodeURIComponent(q)}` : ''}`);
  $('#user-list').innerHTML = data.users.map(u => `<div class="user-role-row" data-user="${esc(u.id)}"><div><strong>${esc(u.display_name)}</strong>${badge(u.role)}</div><select class="compact-select" data-role><option value="user" ${u.role==='user'?'selected':''}>Игрок</option><option value="support" ${u.role==='support'?'selected':''}>Тех. Поддержка</option><option value="admin" ${u.role==='admin'?'selected':''}>Администратор</option></select></div>`).join('');
}

async function loadAudit() {
  const data = await api('/api/v1/admin/audit');
  const list = $('#audit-list');
  const events = data.events || data.audit || [];
  if (!events.length) { list.innerHTML = '<div class="empty-state">Критичных административных событий пока нет.</div>'; return; }
  list.innerHTML = events.map(event => {
    const actor = event.actor_display_name || event.actor || event.actor_user_id || 'system';
    const target = event.target_display_name || event.target || event.target_user_id || '—';
    const details = event.details || event.metadata || {};
    const detailsText = typeof details === 'string' ? details : JSON.stringify(details);
    return `<article class="ticket-card"><div class="ticket-card-head"><div><div class="ticket-meta"><span>${esc(event.event_type || event.action || 'security')}</span><span>actor: ${esc(actor)}</span><span>target: ${esc(target)}</span></div><h3>${esc(event.summary || event.event_type || event.action || 'Security event')}</h3></div><time>${event.created_at ? date(event.created_at) : ''}</time></div><div class="ticket-text">${esc(detailsText)}</div></article>`;
  }).join('');
}

$('#ticket-filter').addEventListener('change', () => loadTickets().catch(showError));
$('#staff-ticket-list').addEventListener('click', async e => {
  const button = e.target.closest('button[data-action="reply"]'); if (!button) return;
  const card = button.closest('[data-ticket]'); const area = card.querySelector('textarea'); if (!area.value.trim()) return;
  button.disabled = true;
  try { await api(`/api/v1/support/tickets/${card.dataset.ticket}/messages`, {method:'POST', body:JSON.stringify({body:area.value})}); await Promise.all([loadTickets(), loadStats()]); }
  catch (error) { showError(error); } finally { button.disabled = false; }
});
$('#staff-ticket-list').addEventListener('change', async e => {
  const select = e.target.closest('select[data-action="status"]'); if (!select) return;
  const card = select.closest('[data-ticket]'); select.disabled = true;
  try { await api(`/api/v1/staff/tickets/${card.dataset.ticket}/status`, {method:'PUT', body:JSON.stringify({status:select.value})}); await Promise.all([loadTickets(), loadStats()]); }
  catch (error) { showError(error); } finally { select.disabled = false; }
});
let timer = null;
$('#user-search').addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => loadUsers().catch(showError), 250); });
$('#user-list').addEventListener('change', async e => {
  const select = e.target.closest('select[data-role]'); if (!select) return;
  const row = select.closest('[data-user]'); select.disabled = true;
  try { await api(`/api/v1/admin/users/${row.dataset.user}/role`, {method:'PUT', body:JSON.stringify({role:select.value})}); await Promise.all([loadUsers(), loadAudit()]); }
  catch (error) { showError(error); await loadUsers(); } finally { select.disabled = false; }
});
$('#audit-refresh').addEventListener('click', () => loadAudit().catch(showError));
function showError(error) {
  const box = $('#staff-denied');
  box.textContent = error.data?.error === 'owner_role_locked' ? 'Роль владельца проекта защищена и не может быть снята.' : error.message;
  box.classList.remove('hidden'); setTimeout(() => box.classList.add('hidden'), 5000);
}
boot();
