import http from 'node:http';
import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdir, stat, copyFile } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC = path.join(ROOT, 'public');
const RUNTIME = path.join(ROOT, 'runtime');
const PORT = Number(process.env.PORT || 4173);
const IDLE_MS = Number(process.env.AGENT_IDLE_MS || 180_000);

const definitions = [
  { id: 'system-analyst', name: 'System Analyst', kicker: 'Discovery & specification', script: 'system-analyst.sh', output: 'output-agent-sa.json', upstream: [] },
  { id: 'uiux-designer', name: 'UI/UX Designer', kicker: 'Experience & interface', script: 'uiux-designer.sh', output: 'output-agent-uiux.json', upstream: ['system-analyst'] },
  { id: 'fullstack-developer', name: 'Fullstack Developer', kicker: 'Build & verification', script: 'fullstack-developer.sh', output: 'output-agent-fullstack.json', upstream: ['system-analyst', 'uiux-designer'] }
];

const states = new Map(definitions.map((agent) => [agent.id, {
  ...agent, status: 'idle', pid: null, startedAt: null, finishedAt: null,
  lastActivityAt: null, exitCode: null, message: 'Siap menerima pekerjaan', process: null,
  idleTimer: null, prompt: '', mode: agent.upstream.length ? 'combined' : 'prompt'
}]));
const clients = new Set();

await mkdir(RUNTIME, { recursive: true });

function publicState(state) {
  const { process, idleTimer, script, ...safe } = state;
  return safe;
}

function snapshot() {
  return { idleTimeoutMs: IDLE_MS, agents: [...states.values()].map(publicState) };
}

function broadcast() {
  const payload = `data: ${JSON.stringify(snapshot())}\n\n`;
  for (const response of clients) response.write(payload);
}

async function readBody(request) {
  let raw = '';
  for await (const chunk of request) {
    raw += chunk;
    if (raw.length > 1_000_000) throw new Error('Payload terlalu besar');
  }
  if (!raw) return {};
  return JSON.parse(raw);
}

function json(response, status, value) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  response.end(JSON.stringify(value));
}

async function fileExists(file) {
  try { await stat(file); return true; } catch { return false; }
}

async function composePrompt(agent, prompt, mode) {
  const chunks = [];
  if (mode !== 'upstream') chunks.push(`INPUT PROMPT USER:\n${prompt.trim() || '(tidak ada prompt tambahan)'}`);
  if (mode !== 'prompt') {
    if (!agent.upstream.length) chunks.push('Tidak ada output agent sebelumnya untuk role ini.');
    for (const upstreamId of agent.upstream) {
      const upstream = states.get(upstreamId);
      const upstreamPath = path.join(ROOT, upstream.output);
      if (!await fileExists(upstreamPath)) throw new Error(`Output ${upstream.name} belum tersedia: ${upstream.output}`);
      const content = await readFile(upstreamPath, 'utf8');
      chunks.push(`OUTPUT ${upstream.name.toUpperCase()} (${upstream.output}):\n${content}`);
    }
  }
  return chunks.join('\n\n---\n\n');
}

function resetIdleTimer(agent) {
  clearTimeout(agent.idleTimer);
  agent.lastActivityAt = new Date().toISOString();
  agent.idleTimer = setTimeout(() => stopAgent(agent, 'Dihentikan otomatis: tidak ada aktivitas selama 3 menit'), IDLE_MS);
}

function stopAgent(agent, reason = 'Dihentikan oleh pengguna') {
  if (!agent.process) return false;
  agent.message = reason;
  agent.status = 'stopping';
  agent.process.kill('SIGTERM');
  setTimeout(() => agent.process?.kill('SIGKILL'), 5_000).unref();
  broadcast();
  return true;
}

async function startAgent(agent, prompt, mode) {
  if (agent.process) throw new Error(`${agent.name} sedang berjalan`);
  if (!['prompt', 'combined', 'upstream'].includes(mode)) throw new Error('Mode prompt tidak valid');
  const content = await composePrompt(agent, prompt, mode);
  const promptPath = path.join(RUNTIME, `${agent.id}.prompt.txt`);
  const logPath = path.join(RUNTIME, `${agent.id}.log`);
  const historyDirectory = path.join(ROOT, 'outputs', agent.id);
  const runId = new Date().toISOString().replace(/[:.]/g, '-');
  const outputPath = path.join(historyDirectory, `${runId}.json`);
  const latestPath = path.join(historyDirectory, 'latest.json');
  const compatibilityPath = path.join(ROOT, agent.output);
  await mkdir(historyDirectory, { recursive: true });
  await writeFile(promptPath, content, 'utf8');
  await writeFile(logPath, `[${new Date().toISOString()}] Memulai ${agent.name}\nMode: ${mode}\n\n`, 'utf8');

  const child = spawn('bash', [path.join(ROOT, 'agents', agent.script)], {
    cwd: ROOT,
    env: { ...process.env, AGENT_ROOT: ROOT, AGENT_PROMPT_FILE: promptPath, AGENT_OUTPUT_FILE: outputPath }
  });
  agent.process = child;
  agent.pid = child.pid;
  agent.status = 'running';
  agent.startedAt = new Date().toISOString();
  agent.finishedAt = null;
  agent.exitCode = null;
  agent.prompt = prompt;
  agent.mode = mode;
  agent.historyFile = path.relative(ROOT, outputPath);
  agent.message = 'Codex CLI sedang bekerja';

  const append = async (source, chunk) => {
    resetIdleTimer(agent);
    await writeFile(logPath, `[${new Date().toISOString()}] ${source}\n${chunk.toString()}\n`, { flag: 'a' }).catch(() => {});
    broadcast();
  };
  child.stdout.on('data', (chunk) => append('stdout', chunk));
  child.stderr.on('data', (chunk) => append('stderr', chunk));
  child.on('error', (error) => append('process-error', error.message));
  child.on('close', async (code, signal) => {
    clearTimeout(agent.idleTimer);
    agent.process = null;
    agent.idleTimer = null;
    agent.pid = null;
    agent.exitCode = code;
    agent.finishedAt = new Date().toISOString();
    if (code === 0 && await fileExists(outputPath)) {
      try {
        await copyFile(outputPath, latestPath);
        await copyFile(outputPath, compatibilityPath);
        agent.status = 'completed';
        agent.message = `Pekerjaan selesai · tersimpan di ${path.relative(ROOT, outputPath)}`;
      } catch (error) {
        agent.status = 'failed';
        agent.message = `Output history dibuat, tetapi sinkronisasi latest gagal: ${error.message}`;
      }
    } else {
      agent.status = signal ? 'stopped' : 'failed';
      agent.message = agent.message || `Proses berhenti dengan kode ${code}`;
    }
    await writeFile(logPath, `\n[${new Date().toISOString()}] Selesai — status=${agent.status}, code=${code}, signal=${signal || '-'}\n`, { flag: 'a' }).catch(() => {});
    broadcast();
  });
  resetIdleTimer(agent);
  broadcast();
}

const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml' };
async function serveStatic(request, response) {
  const pathname = new URL(request.url, `http://${request.headers.host}`).pathname;
  const relative = pathname === '/' ? 'index.html' : pathname.slice(1);
  const resolved = path.resolve(PUBLIC, relative);
  if (!resolved.startsWith(`${PUBLIC}${path.sep}`) && resolved !== path.join(PUBLIC, 'index.html')) return json(response, 403, { error: 'Forbidden' });
  if (!await fileExists(resolved)) return json(response, 404, { error: 'Not found' });
  response.writeHead(200, { 'content-type': mime[path.extname(resolved)] || 'application/octet-stream' });
  createReadStream(resolved).pipe(response);
}

const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (request.method === 'GET' && url.pathname === '/api/state') return json(response, 200, snapshot());
    if (request.method === 'GET' && url.pathname === '/api/events') {
      response.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
      clients.add(response); response.write(`data: ${JSON.stringify(snapshot())}\n\n`);
      request.on('close', () => clients.delete(response)); return;
    }
    const match = url.pathname.match(/^\/api\/agents\/([a-z-]+)\/(start|stop|log|output)$/);
    if (match) {
      const agent = states.get(match[1]);
      if (!agent) return json(response, 404, { error: 'Agent tidak ditemukan' });
      const action = match[2];
      if (request.method === 'POST' && action === 'start') {
        const body = await readBody(request);
        await startAgent(agent, String(body.prompt || ''), String(body.mode || 'prompt'));
        return json(response, 202, publicState(agent));
      }
      if (request.method === 'POST' && action === 'stop') return json(response, stopAgent(agent) ? 200 : 409, { ok: Boolean(agent.process), state: publicState(agent) });
      if (request.method === 'GET' && action === 'log') {
        const file = path.join(RUNTIME, `${agent.id}.log`);
        return json(response, 200, { content: await fileExists(file) ? await readFile(file, 'utf8') : 'Belum ada log.' });
      }
      if (request.method === 'GET' && action === 'output') {
        const file = path.join(ROOT, agent.output);
        return json(response, 200, { content: await fileExists(file) ? await readFile(file, 'utf8') : 'Belum ada output.' });
      }
    }
    if (url.pathname.startsWith('/api/')) return json(response, 404, { error: 'Endpoint tidak ditemukan' });
    await serveStatic(request, response);
  } catch (error) {
    json(response, 400, { error: error.message || 'Terjadi kesalahan' });
  }
});

server.listen(PORT, '127.0.0.1', () => console.log(`Agent Kanban aktif di http://127.0.0.1:${PORT}`));
process.on('SIGINT', () => { for (const agent of states.values()) stopAgent(agent, 'Server dihentikan'); server.close(() => process.exit(0)); });
