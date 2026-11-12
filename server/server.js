'use strict';

/**
 * Приём координат от приложения «GPS Поездки» и отдача последней точки
 * веб-странице с картой Яндекса.
 *
 * Без зависимостей: Node.js 20+, стандартная библиотека.
 * Переменные окружения:
 *   TRACK_TOKEN    — обязательный токен устройства (заголовок X-Token);
 *   VIEW_PASSWORD  — необязательный пароль на просмотр страницы и API;
 *   STATE_FILE     — путь к файлу состояния (переживает перезапуск);
 *   PUBLIC_DIR     — каталог со страницей (для запуска без Nginx);
 *   PORT           — порт (по умолчанию 3000).
 */

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 3000);
const TOKEN = (process.env.TRACK_TOKEN || '').trim();
const VIEW_PASSWORD = (process.env.VIEW_PASSWORD || '').trim();
const STATE_FILE = (process.env.STATE_FILE || '').trim();
const PUBLIC_DIR = (process.env.PUBLIC_DIR || '').trim();

const MAX_BODY = 8 * 1024;
const TRAIL_MAX = 300;
const FIX_MAX_AGE_MS = 24 * 60 * 60 * 1000;

if (!TOKEN) {
  console.error('TRACK_TOKEN не задан. Укажите его в server/.env — без токена сервер не запускается.');
  process.exit(1);
}

let state = { pos: null, trail: [] };
loadState();

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname === '/api/position' && req.method === 'POST') {
    handlePosition(req, res);
    return;
  }
  if (url.pathname === '/api/last' && req.method === 'GET') {
    handleLast(req, res);
    return;
  }
  if (url.pathname === '/api/health' && req.method === 'GET') {
    sendJson(res, 200, { ok: true, hasPosition: !!state.pos });
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    sendJson(res, 404, { ok: false, error: 'not found' });
    return;
  }

  if (PUBLIC_DIR) {
    serveStatic(req, res, url.pathname);
    return;
  }
  sendJson(res, 404, { ok: false, error: 'not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`GPS tracker API: http://0.0.0.0:${PORT}`);
});

for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    persist();
    process.exit(0);
  });
}

// ---------- API ----------

function handlePosition(req, res) {
  if (!safeEqual(req.headers['x-token'] || '', TOKEN)) {
    sendJson(res, 401, { ok: false, error: 'bad token' });
    return;
  }

  readBody(req, MAX_BODY)
    .then((text) => {
      let body;
      try {
        body = JSON.parse(text);
      } catch {
        sendJson(res, 400, { ok: false, error: 'bad json' });
        return;
      }
      const fix = normalizeFix(body);
      if (!fix) {
        sendJson(res, 400, { ok: false, error: 'bad coordinates' });
        return;
      }

      const pos = {
        lat: fix.lat,
        lon: fix.lon,
        speed_kmh: fix.speed,
        heading: fix.heading,
        ts: fix.ts,
        device: fix.device,
        received: Date.now(),
      };
      state.pos = pos;
      const lastPoint = state.trail[state.trail.length - 1];
      if (!lastPoint || lastPoint.lat !== pos.lat || lastPoint.lon !== pos.lon) {
        state.trail.push({ lat: pos.lat, lon: pos.lon, ts: pos.ts });
        if (state.trail.length > TRAIL_MAX) state.trail.splice(0, state.trail.length - TRAIL_MAX);
      }
      scheduleSave();

      res.writeHead(204, noStoreHeaders());
      res.end();
    })
    .catch((error) => {
      const code = error.message === 'too large' ? 413 : 400;
      sendJson(res, code, { ok: false, error: error.message });
    });
}

function handleLast(req, res) {
  if (!viewAllowed(req)) {
    sendJson(res, 401, { ok: false, error: 'password required' });
    return;
  }
  sendJson(res, 200, {
    ok: true,
    pos: state.pos,
    trail: state.trail,
    serverTime: Date.now(),
  });
}

// ---------- данные ----------

function normalizeFix(body) {
  if (!body || typeof body !== 'object') return null;

  const lat = Number(body.lat);
  const lon = Number(body.lon);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) return null;
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) return null;

  let speed = null;
  if (body.speed_kmh !== undefined && body.speed_kmh !== null) {
    const v = Number(body.speed_kmh);
    if (!Number.isFinite(v) || v < 0 || v > 500) return null;
    speed = Math.round(v * 10) / 10;
  }

  let heading = null;
  if (body.heading !== undefined && body.heading !== null) {
    const v = Number(body.heading);
    if (!Number.isFinite(v) || v < 0 || v > 360) return null;
    heading = Math.round(v);
  }

  return {
    lat,
    lon,
    speed,
    heading,
    ts: normalizeTs(body.ts),
    device: String(body.device || 'car').slice(0, 64),
  };
}

function normalizeTs(value) {
  const n = Number(value);
  const now = Date.now();
  if (!Number.isFinite(n) || n <= 0) return now;
  const ms = n < 1e12 ? n * 1000 : n;
  if (ms > now + 60 * 60 * 1000 || ms < now - FIX_MAX_AGE_MS) return now;
  return Math.round(ms);
}

// ---------- авторизация ----------

function viewAllowed(req) {
  if (!VIEW_PASSWORD) return true;
  return safeEqual(req.headers['x-view-password'] || '', VIEW_PASSWORD);
}

function safeEqual(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

// ---------- служебное ----------

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error('too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function sendJson(res, code, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    ...noStoreHeaders(),
  });
  res.end(body);
}

function noStoreHeaders() {
  return {
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  };
}

// ---------- файл состояния ----------

let saveTimer = null;

function scheduleSave() {
  if (!STATE_FILE || saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    persist();
  }, 2000);
}

function persist() {
  if (!STATE_FILE) return;
  try {
    fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
    const tmp = `${STATE_FILE}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(state));
    fs.renameSync(tmp, STATE_FILE);
  } catch (error) {
    console.error('Не удалось сохранить состояние:', error.message);
  }
}

function loadState() {
  if (!STATE_FILE) return;
  try {
    const raw = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    if (raw && typeof raw === 'object') {
      if (raw.pos && typeof raw.pos === 'object') state.pos = raw.pos;
      if (Array.isArray(raw.trail)) state.trail = raw.trail.slice(-TRAIL_MAX);
    }
    console.log(`Состояние загружено: позиция ${state.pos ? 'есть' : 'нет'}, точек ${state.trail.length}`);
  } catch {
    /* первый запуск */
  }
}

// ---------- статика (для запуска без Nginx) ----------

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function serveStatic(req, res, pathname) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405);
    res.end();
    return;
  }
  const root = path.resolve(PUBLIC_DIR);
  const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
  let file = path.resolve(root, relative);
  if (!file.startsWith(root)) {
    res.writeHead(403);
    res.end();
    return;
  }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    file = path.join(root, 'index.html');
  }
  try {
    const body = fs.readFileSync(file);
    const type = TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': body.length,
      'X-Content-Type-Options': 'nosniff',
      'Cache-Control': 'no-cache',
    });
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch (error) {
    res.writeHead(500);
    res.end('internal error');
  }
}
