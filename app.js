const express = require('express');
const path = require('path');
const fs = require('fs');
const api = require('./src/handlers');
const db = require('./src/db');
const sla = require('./src/sla');
const config = require('./src/config');
const utils = require('./src/utils');
const { seedAdminUsers, seedL1Users, seedL2Users, seedViewerUsers } = require('./src/seed');
const workflow = require('./src/workflow');
const { hasPermission } = workflow;
const events = require('./src/events');

const log = (msg) => fs.appendFileSync('app.log', new Date().toISOString() + ' ' + msg + '\n');
log('app.js starting');
seedAdminUsers();
seedL1Users();
seedL2Users();
seedViewerUsers();
utils.migrateToIncidentNumbers();
workflow.ensureWorkflows();
process.on('uncaughtException', (e) => { log('uncaught: ' + e.message + '\n' + e.stack); console.error('uncaught:', e); process.exit(1); });
process.on('unhandledRejection', (e) => { log('unhandled: ' + e); console.error('unhandled:', e); });

const app = express();
const PORT = process.env.PORT || 3000;
const UPLOAD_DIR = process.env.UPLOAD_DIR || (process.env.WEBSITE_SITE_NAME ? '/home/site/data/uploads' : path.join(__dirname, 'public', 'uploads'));

if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.use(express.json({ limit: '15mb' }));
app.use('/uploads', express.static(UPLOAD_DIR));
app.use('/app', express.static(path.join(__dirname, 'public', 'app')));
app.use(express.static(path.join(__dirname, 'public'), { index: false }));

function disabledPage(title, message) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title}</title><style>body{font-family:Arial,sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#111827;color:#fff;text-align:center}</style></head><body><div><h1>${title}</h1><p>${message}</p></div></body></html>`;
}

function isSettingEnabled(name, envDefault) {
  try {
    const setting = utils.getSetting(name);
    return setting === null ? envDefault : !!setting;
  } catch (e) { return envDefault; }
}

const distPath = path.join(__dirname, 'client', 'dist');
const appPath = path.join(distPath, 'index.html');

app.use(express.static(distPath, { index: false }));

app.get('/', (req, res) => res.sendFile(appPath));

app.get('/form', (req, res) => res.sendFile(appPath));

app.get('/dashboard', (req, res) => res.sendFile(appPath));

app.get('/login', (req, res) => res.sendFile(appPath));

app.get('/settings', (req, res) => res.sendFile(appPath));

const publicPath = path.join(__dirname, 'public');
app.get('/admin', (req, res) => res.sendFile(path.join(publicPath, 'dashboard.html')));
app.get('/audit', (req, res) => res.sendFile(path.join(publicPath, 'audit.html')));
app.get('/kb', (req, res) => res.sendFile(path.join(publicPath, 'kb.html')));
app.get('/csat', (req, res) => res.sendFile(path.join(publicPath, 'csat.html')));
app.get('/import-export', (req, res) => res.sendFile(path.join(publicPath, 'import-export.html')));
app.get('/analytics', (req, res) => res.sendFile(path.join(publicPath, 'analytics.html')));
app.get('/cmdb', (req, res) => res.sendFile(path.join(publicPath, 'cmdb.html')));
app.get('/help-bot', (req, res) => res.sendFile(path.join(publicPath, 'help-bot.html')));
app.get('/live', (req, res) => res.sendFile(path.join(publicPath, 'live.html')));
app.get('/roles', (req, res) => res.sendFile(path.join(publicPath, 'roles.html')));

app.get('/api/events', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();
  const handler = (data) => res.write('data: ' + JSON.stringify(data) + '\n\n');
  events.events.on('activity', handler);
  req.on('close', () => events.events.off('activity', handler));
});

app.get('/status', (req, res) => {
  res.json(api.diagnoseSystem());
});

const PERMISSIONS = {
  updateTicketStatus: 'tickets.update.status',
  assignTicket: 'tickets.update.assign',
  escalateTicket: 'tickets.update.escalate',
  updateTicketPriority: 'tickets.update.priority',
  updateIssueType: 'tickets.update.fields',
  updateImpactArea: 'tickets.update.fields',
  updatePhoneNumber: 'tickets.update.fields',
  updateLocation: 'tickets.update.fields',
  addTicketNote: 'tickets.update.note',
  deleteTicketNote: 'tickets.update.note',
  createCMDBItem: 'cmdb.*',
  updateCMDBItem: 'cmdb.*',
  deleteCMDBItem: 'cmdb.*',
  linkCMDBItemToTicket: 'cmdb.*',
  unlinkCMDBItemFromTicket: 'cmdb.*',
  addUser: 'users.*',
  removeUser: 'users.*',
  updateUserStatus: 'users.*',
  setSetting: 'settings.*',
  toggleAutoAssign: 'settings.*',
  getAllRolePermissions: 'settings.read',
  setRolePermissions: 'settings.*',
  createProject: 'workflows.*',
  updateProject: 'workflows.*',
  deleteProject: 'workflows.*',
  setTicketProject: 'tickets.update.fields',
  setTicketIssueClass: 'tickets.update.fields'
};

const VIEWER_ALLOWED = new Set([
  'validateUser', 'getDashboardData', 'getAllUsers', 'getAllSettings', 'getSetting',
  'getAutoAssignSetting', 'getAdminEmails', 'getTicketByIdForTracking', 'getTicketNotes',
  'getTicketTimeline', 'getAllTickets', 'generateReport', 'lookupEmployee', 'lookupEmployeeSafe',
  'getSystemStatus', 'setupSystem', 'diagnoseSystem',
  'getWorkflows', 'getWorkflowById', 'getIssueTypes', 'getAllowedTransitions', 'getRolePermissions', 'checkPermission',
  'getCMDBItems', 'getCMDBItemById', 'getCMDBTypes', 'getTicketCIs',
  'getProjects', 'getIssueClasses',
  'getWorkflows', 'getWorkflowById'
]);

app.post('/api/:fn', (req, res) => {
  const fn = req.params.fn;
  if (typeof api[fn] !== 'function') {
    return res.status(404).json({ success: false, error: 'Function not found: ' + fn });
  }
  const user = req.body && req.body.user;
  if (user && String(user.role).toLowerCase() === 'viewer' && !VIEWER_ALLOWED.has(fn)) {
    return res.status(403).json({ success: false, error: 'Viewers are not allowed to perform this action.' });
  }
  if (user && PERMISSIONS[fn] && !hasPermission(user.role, PERMISSIONS[fn])) {
    return res.status(403).json({ success: false, error: `Role ${user.role} is not authorized for ${fn}.` });
  }
  const args = req.body && Array.isArray(req.body.args) ? req.body.args : [];
  try {
    const result = api[fn](...args, user);
    if (result && typeof result.then === 'function') {
      result.then(r => res.json(r)).catch(e => res.status(500).json({ success: false, error: e.message }));
    } else {
      res.json(result);
    }
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('*', (req, res) => res.sendFile(appPath));

const preferredPort = process.env.PORT || 3000;
const fallbackPorts = [preferredPort, 55000, 55001, 56000, 57000, 58000, 59000];

function tryListen(index) {
  if (index >= fallbackPorts.length) {
    const last = app.listen(0, () => {
      const actualPort = last.address().port;
      log('listening on fallback port ' + actualPort);
      console.log('IT Support Portal running at http://localhost:' + actualPort);
    });
    last.on('error', (e) => { log('listen error: ' + e.message); console.error('listen error:', e); process.exit(1); });
    return;
  }
  const port = fallbackPorts[index];
  const server = app.listen(port, () => {
    const actualPort = server.address().port;
    log('listening on port ' + actualPort);
    console.log('IT Support Portal running at http://localhost:' + actualPort);
    try { sla.checkSLABreaches(); } catch (e) { log('initial SLA check error: ' + e.message); }
    setInterval(() => {
      try { sla.checkSLABreaches(); } catch (e) { log('scheduled SLA check error: ' + e.message); }
    }, 15 * 60 * 1000);
  });
  server.on('error', (e) => {
    log('listen error on port ' + port + ': ' + e.message);
    if (e.code === 'EADDRINUSE') return tryListen(index + 1);
    console.error('listen error:', e);
    process.exit(1);
  });
}

tryListen(0);
