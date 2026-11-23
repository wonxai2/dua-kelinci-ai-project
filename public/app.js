const board = document.querySelector('#board');
const viewer = document.querySelector('#viewer');
const viewerTitle = document.querySelector('#viewer-title');
const viewerKicker = document.querySelector('#viewer-kicker');
const viewerContent = document.querySelector('#viewer-content');
const toast = document.querySelector('#toast');
const accents = ['#c8f243', '#4c63f0', '#ff7a45'];
let state = { agents: [] };

function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, (character) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[character]); }
function isActive(status) { return status === 'running' || status === 'stopping'; }
function modeOptions(agent) {
  const hasUpstream = agent.upstream.length > 0;
  return [
    ['prompt', 'Hanya input prompt'],
    ['combined', 'Prompt + output sebelumnya'],
    ['upstream', 'Hanya output sebelumnya']
  ].map(([value,label]) => `<option value="${value}" ${agent.mode===value?'selected':''} ${!hasUpstream&&value!=='prompt'?'disabled':''}>${label}</option>`).join('');
}
function render() {
  board.innerHTML = state.agents.map((agent,index) => `
    <article class="agent-card" style="--accent:${accents[index]}">
      <div class="card-stripe"></div><div class="card-body">
        <div class="card-top"><span class="role-id">AGENT / 0${index+1}</span><span class="status ${agent.status}">${escapeHtml(agent.status)}</span></div>
        <h3 class="agent-name">${escapeHtml(agent.name)}</h3><p class="kicker">${escapeHtml(agent.kicker)}</p>
        <label for="prompt-${agent.id}">Input prompt</label>
        <textarea id="prompt-${agent.id}" placeholder="Tulis instruksi khusus untuk agent ini...">${escapeHtml(agent.prompt || '')}</textarea>
        <label class="mode-label" for="mode-${agent.id}">Sumber konteks</label><div class="select-wrap"><select id="mode-${agent.id}">${modeOptions(agent)}</select></div>
        <p class="message">${escapeHtml(agent.message)}${agent.pid ? ` · PID ${agent.pid}` : ''}</p>
        <div class="card-actions">
          <button class="button primary ${isActive(agent.status)?'stop':''}" data-action="${isActive(agent.status)?'stop':'start'}" data-id="${agent.id}">${isActive(agent.status)?'Stop agent':'Start agent'}</button>
          <button class="button" data-action="log" data-id="${agent.id}">Lihat log</button>
          <button class="button" data-action="output" data-id="${agent.id}">Lihat output</button>
        </div>
      </div>
    </article>`).join('');
}
function notify(message) { toast.textContent = message; toast.classList.add('show'); clearTimeout(notify.timer); notify.timer = setTimeout(() => toast.classList.remove('show'), 3200); }
async function api(url, options) { const response = await fetch(url, options); const data = await response.json(); if (!response.ok) throw new Error(data.error || 'Permintaan gagal'); return data; }
async function refresh() { state = await api('/api/state'); render(); }
async function showViewer(id, type) {
  const agent = state.agents.find((item) => item.id === id); const data = await api(`/api/agents/${id}/${type}`);
  viewerKicker.textContent = `${agent.name} / ${type}`; viewerTitle.textContent = type === 'log' ? 'Live activity log' : agent.output;
  viewerContent.textContent = data.content; viewer.showModal(); viewerContent.scrollTop = viewerContent.scrollHeight;
  if (type === 'log') { clearInterval(showViewer.timer); showViewer.timer = setInterval(async () => { if (!viewer.open) return clearInterval(showViewer.timer); const next = await api(`/api/agents/${id}/log`); viewerContent.textContent = next.content; viewerContent.scrollTop = viewerContent.scrollHeight; }, 1500); }
}
board.addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-action]'); if (!button) return;
  const { action,id } = button.dataset;
  try {
    if (action === 'start') { button.disabled = true; const prompt = document.querySelector(`#prompt-${id}`).value; const mode = document.querySelector(`#mode-${id}`).value; await api(`/api/agents/${id}/start`, { method:'POST', headers:{'content-type':'application/json'}, body:JSON.stringify({ prompt, mode }) }); notify('Agent berhasil dimulai'); }
    else if (action === 'stop') { await api(`/api/agents/${id}/stop`, { method:'POST' }); notify('Perintah stop dikirim'); }
    else await showViewer(id, action);
  } catch (error) { notify(error.message); } finally { await refresh().catch(() => {}); }
});
document.querySelector('#refresh').addEventListener('click', () => refresh().then(() => notify('Status diperbarui')).catch((error) => notify(error.message)));
document.querySelector('#close-viewer').addEventListener('click', () => viewer.close());
viewer.addEventListener('close', () => clearInterval(showViewer.timer));

refresh().catch((error) => notify(error.message));
const events = new EventSource('/api/events');
events.onmessage = (event) => { state = JSON.parse(event.data); render(); };
events.onerror = () => notify('Koneksi realtime terputus; mencoba menyambung ulang');
