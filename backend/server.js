const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const crypto = require('crypto');

const PORT = 18000;
const TORRENT_PORT = 18001;
const BASE = __dirname;
const HTML_PATH = path.join(BASE, 'templates', 'index.html');
const PARSE_WORKER = path.join(BASE, 'torrent_server');
const SESSION = path.join(BASE, 'torrent_server');
const DOWNLOAD_DIR = path.join(BASE, 'downloads');

if (!fs.existsSync(DOWNLOAD_DIR)) fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });

// Logging
function log(msg) {
  const line = `[${new Date().toISOString()}] ${msg}`;
  console.log(line);
  try { fs.appendFileSync(path.join(BASE, 'server.log'), line + '\n'); } catch(e) {}
}

process.on('uncaughtException', (err) => log('CRASH: ' + (err.stack || err.message)));
process.on('unhandledRejection', (reason) => log('REJECTION: ' + (reason?.message || reason)));

// ===== Torrent session HTTP client =====
async function tsApi(cmd, data = {}) {
  data.cmd = cmd;
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    const r = await fetch(`http://127.0.0.1:${TORRENT_PORT}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
      signal: controller.signal,
    });
    clearTimeout(timeout);
    return await r.json();
  } catch(e) {
    log('TS API error: ' + e.message);
    return null;
  }
}

async function tsGet() {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const r = await fetch(`http://127.0.0.1:${TORRENT_PORT}/`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);
    return await r.json();
  } catch(e) {
    return [];
  }
}

async function tsGetOne(ih) {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const r = await fetch(`http://127.0.0.1:${TORRENT_PORT}/${ih}`, {
      signal: controller.signal,
    });
    clearTimeout(timeout);
    return await r.json();
  } catch(e) {
    return null;
  }
}

// Start Python torrent session
function startSession() {
  const proc = spawn(SESSION, [], {
    stdio: ['ignore', 'pipe', 'pipe'],
    cwd: BASE,
  });
  proc.stderr.on('data', d => {
    d.toString().split('\n').filter(l => l.trim()).forEach(l => log('TS: ' + l.trim()));
  });
  proc.on('close', (code) => {
    log('TS exited: ' + code + ', restarting...');
    setTimeout(startSession, 3000);
  });
  proc.on('error', (e) => {
    log('TS error: ' + e.message);
    setTimeout(startSession, 3000);
  });
  global.tsProc = proc;
  log('Torrent session starting...');
}

startSession();

// ===== Parse tasks =====
const parseTasks = new Map();

function startParse(taskId, magnet) {
  const proc = spawn(PARSE_WORKER, ['--parse', magnet, DOWNLOAD_DIR], {
    timeout: 60000,
    stdio: ['ignore', 'pipe', 'pipe']
  });
  let stdout = '', stderr = '';
  proc.stdout.on('data', d => stdout += d.toString());
  proc.stderr.on('data', d => { const s = d.toString().trim(); if (s) log('Parse: ' + s); });

  const timeout = setTimeout(() => {
    try { proc.kill('SIGKILL'); } catch(e) {}
    parseTasks.set(taskId, { ts: Date.now(), status: 'error', error: '解析超时' });
  }, 65000);

  proc.on('close', code => {
    clearTimeout(timeout);
    if (code !== 0) {
      const err = stdout.trim() || stderr.trim() || `exit:${code}`;
      parseTasks.set(taskId, { ts: Date.now(), status: 'error', error: err });
      return;
    }
    try {
      const r = JSON.parse(stdout);
      parseTasks.set(taskId, { ts: Date.now(), status: r.success ? 'done' : 'error', result: r });
    } catch (e) {
      parseTasks.set(taskId, { ts: Date.now(), status: 'error', error: '输出异常: ' + e.message });
    }
  });
}

function getBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', chunk => data += chunk);
    req.on('end', () => { try { resolve(JSON.parse(data)); } catch(e) { resolve({}); } });
    req.on('error', () => resolve({}));
  });
}

function jsonResp(res, data, code = 200) {
  try {
    const body = Buffer.from(JSON.stringify(data), 'utf-8');
    res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': body.length, 'Access-Control-Allow-Origin': '*' });
    res.end(body);
  } catch(e) {}
}

function serveHtml(res) {
  fs.readFile(HTML_PATH, (err, data) => {
    if (err) return jsonResp(res, { error: 'not found' }, 404);
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Length': data.length });
    res.end(data);
  });
}

function serveFile(req, res, filePath) {
  fs.stat(filePath, (err, stat) => {
    if (err) return jsonResp(res, { error: 'not found' }, 404);
    const fileSize = stat.size;
    const range = reqRange(req);
    if (range) {
      const start = range[0];
      const end = range[1] || fileSize - 1;
      const chunkSize = end - start + 1;
      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${fileSize}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunkSize,
        'Content-Type': 'video/mp4',
        'Access-Control-Allow-Origin': '*',
      });
      fs.createReadStream(filePath, { start, end }).pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': fileSize,
        'Content-Type': 'application/octet-stream',
        'Accept-Ranges': 'bytes',
        'Access-Control-Allow-Origin': '*',
      });
      fs.createReadStream(filePath).pipe(res);
    }
  });
}

function reqRange(req) {
  const range = req.headers && req.headers['range'];
  if (!range) return null;
  const parts = range.replace(/bytes=/, '').split('-').map(Number);
  return [parts[0], parts[1]];
}

const server = http.createServer(async (req, res) => {

  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  // Parse URL
  const _url = new URL(req.url, 'http://' + (req.headers.host || 'localhost'));
  const pathname = _url.pathname;

  // Serve webtorrent locally
  if ((req.method === 'GET' || req.method === 'HEAD') && pathname === '/webtorrent.min.js') {
    try {
      const d = require('fs').readFileSync(require('path').join(__dirname, 'templates', 'webtorrent.min.js'));
      res.writeHead(200, {'Content-Type':'application/javascript','Content-Length':d.length,'Access-Control-Allow-Origin':'*'});
      res.end(d);
      return;
    } catch(e) { console.log('WT error:', e.message); }
  }

  try {
    log('Request: ' + req.method + ' ' + pathname);

    // GET / => HTML
    if (req.method === 'GET' && pathname === '/') {
      return serveHtml(res);
    }

    // GET /api/torrents
    if (req.method === 'GET' && pathname === '/api/torrents') {
      const list = await tsGet() || [];
      return jsonResp(res, list);
    }

    // GET /api/stats
    if (req.method === 'GET' && pathname === '/api/stats') {
      const list = await tsGet() || [];
      let total = list.length;
      let active = list.filter(t => t.state === 'downloading' || t.state === 'downloading_metadata').length;
      let dl = list.reduce((s, t) => s + (t.download_rate || 0), 0);
      let ul = list.reduce((s, t) => s + (t.upload_rate || 0), 0);
      return jsonResp(res, { total_torrents: total, active_downloads: active, total_download_rate: fmt(dl)+'/s', total_upload_rate: fmt(ul)+'/s' });
    }

    // GET /api/magnet/parse/:task_id
    const parseMatch = pathname.match(/^\/api\/magnet\/parse\/([a-f0-9]+)$/);
    if (req.method === 'GET' && parseMatch) {
      const t = parseTasks.get(parseMatch[1]);
      if (!t) return jsonResp(res, { success: false, error: '不存在' }, 404);
      if (t.status === 'parsing') return jsonResp(res, { success: false, status: 'parsing' }, 202);
      if (t.status === 'error') {
        parseTasks.delete(parseMatch[1]);
        return jsonResp(res, { success: false, error: t.error }, 408);
      }
      const r = t.result;
      parseTasks.delete(parseMatch[1]);
      return jsonResp(res, { success: true, name: r.name, total_size_str: fmt(r.total_size_str ? r.total_size_str : r.total_size), info_hash: r.info_hash, files: (r.files || []).map(f => ({...f, size_str: fmt(f.size)})) });
    }

    // POST /api/magnet/parse (via running C++ session)
    if (req.method === 'POST' && pathname === '/api/magnet/parse') {
      const body = await getBody(req);
      const magnet = (body.magnet || '').trim();
      if (!magnet.startsWith('magnet:')) return jsonResp(res, { success: false, error: '无效磁力链接' }, 400);
      
      try {
        // Use the running C++ session's /parse endpoint
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 35000);
        const r = await fetch(`http://127.0.0.1:${TORRENT_PORT}/parse`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ magnet }),
          signal: controller.signal,
        });
        clearTimeout(timeout);
        const data = await r.json();
        
        if (data.error) {
          return jsonResp(res, { success: false, error: data.error }, 408);
        }
        
        return jsonResp(res, {
          success: true,
          name: data.name,
          total_size_str: data.total_size_str || fmt(data.total_size || 0),
          info_hash: data.info_hash,
          files: (data.files || []).map(f => ({...f, size_str: fmt(f.size)}))
        });
      } catch(e) {
        return jsonResp(res, { success: false, error: '解析超时: ' + e.message }, 408);
      }
    }

    // POST /api/magnet/download    // POST /api/magnet/download
    if (req.method === 'POST' && pathname === '/api/magnet/download') {
      const body = await getBody(req);
      const magnet = (body.magnet || '').trim();
      if (!magnet.startsWith('magnet:')) return jsonResp(res, { success: false, error: '无效磁力链接' }, 400);
      const r = await tsApi('add', { magnet, selected_files: body.selectedFiles });
      return jsonResp(res, r || { success: false, error: '连接torrent会话失败' });
    }

    // POST /api/magnet/play
    if (req.method === 'POST' && pathname === '/api/magnet/play') {
      const body = await getBody(req);
      const magnet = (body.magnet || '').trim();
      const fi = body.file_index;
      if (!magnet.startsWith('magnet:') || fi === undefined) return jsonResp(res, { success: false, error: '参数错误' }, 400);
      const reHash = /xt=urn:btih:([a-f0-9]+)/i;
      const m = magnet.match(reHash);
      if (!m) return jsonResp(res, { success: false, error: '无效磁力' }, 400);
      const ih = m[1].toLowerCase();

      // Add if not exists
      const existing = await tsGetOne(ih);
      if (!existing || existing.error) {
        await tsApi('add', { magnet, selected_files: [fi] });
      }

      // Wait for file to appear (up to 120s)
      let waited = 0;
      let found = false;
      while (waited < 120) {
        const t = await tsGetOne(ih);
        if (t && t.files && t.files[fi]) {
          const fpath = path.join(DOWNLOAD_DIR, t.files[fi].path);
          try {
            if (fs.existsSync(fpath) && fs.statSync(fpath).size > 512 * 1024) {
              found = true;
              break;
            }
          } catch(e) {}
        }
        await new Promise(r => setTimeout(r, 1000));
        waited++;
      }
      if (found) {
        const t = await tsGetOne(ih);
        const fpath = path.join(DOWNLOAD_DIR, t.files[fi].path);
        log(`Play ready: ${ih}/${fi} (${(fs.statSync(fpath).size/1024/1024).toFixed(1)}MB)`);
        return jsonResp(res, { success: true, stream_url: `/api/stream/${ih}/${fi}` });
      } else {
        return jsonResp(res, { success: true, stream_url: `/api/stream/${ih}/${fi}?wait=1` });
      }
    }

    // GET /api/stream/:hash/:index
    const streamMatch = pathname.match(/^\/api\/stream\/([a-f0-9]+)\/(\d+)$/);
    if (req.method === 'GET' && streamMatch) {
      const ih = streamMatch[1];
      const fi = parseInt(streamMatch[2]);
      const t = await tsGetOne(ih);
      if (!t || !t.files || !t.files[fi]) return jsonResp(res, { error: 'not found' }, 404);
      const fpath = path.join(DOWNLOAD_DIR, t.files[fi].path);
      let waited = 0;
      while (!fs.existsSync(fpath) && waited < 120) {
        await new Promise(r => setTimeout(r, 2000));
        waited += 2;
      }
      if (fs.existsSync(fpath) && fs.statSync(fpath).size > 0) {
        return serveFile(req, res, fpath);
      }
      return jsonResp(res, { error: 'file not ready' }, 404);
    }

    // POST /api/torrents (add torrent - for Android compatibility)
    if (req.method === 'POST' && pathname === '/api/torrents') {
      const body = await getBody(req);
      const magnet = (body.magnet || '').trim();
      if (!magnet.startsWith('magnet:')) return jsonResp(res, { success: false, error: '无效磁力链接' }, 400);
      const r = await tsApi('add', { magnet, selected_files: body.selectedFiles });
      return jsonResp(res, r || { success: false, error: '连接torrent会话失败' });
    }

    // Torrent operations
    const tMatch = pathname.match(/^\/api\/torrents\/([a-f0-9]+)(?:\/(\w+))?$/);
    if (tMatch) {
      const ih = tMatch[1];
      const action = tMatch[2];
      if (req.method === 'GET' && !action) {
        const t = await tsGetOne(ih);
        return jsonResp(res, t || { error: 'not found' }, t ? 200 : 404);
      }
      if (req.method === 'POST' && action === 'pause') {
        await tsApi('pause', { info_hash: ih });
        return jsonResp(res, { success: true });
      }
      if (req.method === 'POST' && action === 'resume') {
        await tsApi('resume', { info_hash: ih });
        return jsonResp(res, { success: true });
      }
      if (req.method === 'DELETE' && !action) {
        const delFiles = url.searchParams.get('delete_files') === 'true';
        await tsApi('remove', { info_hash: ih, delete_files: delFiles });
        return jsonResp(res, { success: true });
      }
    }

    // GET /api/download/:hash/:index (download file)
    const dlMatch = pathname.match(/^\/api\/download\/([a-f0-9]+)\/(\d+)$/);
    if (req.method === 'GET' && dlMatch) {
      const ih = dlMatch[1];
      const fi = parseInt(dlMatch[2]);
      const t = await tsGetOne(ih);
      if (!t || !t.files || !t.files[fi]) return jsonResp(res, { error: 'not found' }, 404);
      const fpath = path.join(DOWNLOAD_DIR, t.files[fi].path);
      let waited = 0;
      while (!fs.existsSync(fpath) && waited < 60) {
        await new Promise(r => setTimeout(r, 2000));
        waited += 2;
      }
      if (fs.existsSync(fpath) && fs.statSync(fpath).size > 0) {
        const stat = fs.statSync(fpath);
        const fname = path.basename(fpath);
        res.writeHead(200, {
          'Content-Type': 'application/octet-stream',
          'Content-Length': stat.size,
          'Content-Disposition': 'attachment; filename="' + fname + '"',
          'Access-Control-Allow-Origin': '*'
        });
        const stream = fs.createReadStream(fpath);
        stream.pipe(res);
        return;
      }
      return jsonResp(res, { error: 'file not ready' }, 404);
    }

    // GET /bowenApp.tar.gz
    if (req.method === 'GET' && pathname === '/bowenApp.tar.gz') {
      const tarpath = '/tmp/bowenApp.tar.gz';
      if (fs.existsSync(tarpath)) {
        const data = fs.readFileSync(tarpath);
        res.writeHead(200, { 'Content-Type': 'application/gzip', 'Content-Length': data.length, 'Access-Control-Allow-Origin': '*' });
        res.end(data);
        return;
      }
    }



    jsonResp(res, { error: 'not found' }, 404);

  } catch (e) {
    log('Handler: ' + (e.stack || e.message));
    try { jsonResp(res, { error: '服务器错误' }, 500); } catch(_) {}
  }
});

server.timeout = 60000;
server.keepAliveTimeout = 5000;

server.listen(PORT, '0.0.0.0', () => {
  log('✅ Server on http://0.0.0.0:' + PORT);
});

function fmt(sz) {
  if (typeof sz === 'string') return sz;
  for (const u of ['B','KB','MB','GB','TB']) {
    if (sz < 1024) return sz.toFixed(1) + ' ' + u;
    sz /= 1024;
  }
  return sz.toFixed(1) + ' PB';
}
