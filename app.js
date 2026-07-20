const express = require('express');
const path = require('path');
const fs = require('fs');
const api = require('./src/handlers');
const config = require('./src/config');

const log = (msg) => fs.appendFileSync('app.log', new Date().toISOString() + ' ' + msg + '\n');
log('app.js starting');
process.on('uncaughtException', (e) => { log('uncaught: ' + e.message + '\n' + e.stack); console.error('uncaught:', e); process.exit(1); });
process.on('unhandledRejection', (e) => { log('unhandled: ' + e); console.error('unhandled:', e); });

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

function disabledPage(title, message) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title}</title><style>body{font-family:Arial,sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#111827;color:#fff;text-align:center}</style></head><body><div><h1>${title}</h1><p>${message}</p></div></body></html>`;
}

app.get('/', (req, res) => {
  if (!config.FORM_ENABLED) return res.status(503).send(disabledPage('IT Support Unavailable', 'The support form is currently disabled.'));
  const formPath = path.join(__dirname, 'public', 'index.html');
  if (fs.existsSync(formPath)) return res.sendFile(formPath);
  res.redirect('/dashboard');
});

app.get('/form', (req, res) => res.redirect('/'));

app.get('/dashboard', (req, res) => {
  if (!config.DASHBOARD_ENABLED) return res.status(503).send(disabledPage('Dashboard Unavailable', 'The dashboard is currently disabled.'));
  res.sendFile(path.join(__dirname, 'public', 'dashboard.html'));
});

app.get('/status', (req, res) => {
  res.json(api.diagnoseSystem());
});

app.post('/api/:fn', (req, res) => {
  const fn = req.params.fn;
  if (typeof api[fn] !== 'function') {
    return res.status(404).json({ success: false, error: 'Function not found: ' + fn });
  }
  const args = req.body && Array.isArray(req.body.args) ? req.body.args : [];
  try {
    const result = api[fn](...args);
    if (result && typeof result.then === 'function') {
      result.then(r => res.json(r)).catch(e => res.status(500).json({ success: false, error: e.message }));
    } else {
      res.json(result);
    }
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

const server = app.listen(PORT, () => {
  log('listening on port ' + PORT);
  console.log('IT Support Portal running at http://localhost:' + PORT);
});
server.on('error', (e) => { log('listen error: ' + e.message); console.error('listen error:', e); });
