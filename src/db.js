const fs = require('fs');
const path = require('path');
const os = require('os');

function getDefaultDbDir() {
  if (process.env.DB_DIR) return process.env.DB_DIR;
  // Azure App Service Linux persistent path
  if (process.env.WEBSITE_SITE_NAME) return '/home/site/data/it-tool';
  if (process.env.LOCALAPPDATA) return path.join(process.env.LOCALAPPDATA, 'it-tool');
  return path.join(os.tmpdir(), 'it-tool');
}

const DB_DIR = getDefaultDbDir();
const DB_PATH = process.env.DB_PATH || path.join(DB_DIR, 'db.json');

function ensureDir() {
  fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
}

function loadDb() {
  if (!fs.existsSync(DB_PATH)) {
    return initDb();
  }
  try {
    const raw = fs.readFileSync(DB_PATH, 'utf8');
    return JSON.parse(raw);
  } catch (e) {
    console.error('Error loading DB, reinitializing:', e.message);
    return initDb();
  }
}

function saveDb(db) {
  ensureDir();
  // Keep a backup before each write so a bad upload/sync can be recovered
  if (fs.existsSync(DB_PATH)) {
    try { fs.copyFileSync(DB_PATH, DB_PATH + '.bak'); } catch (e) {}
  }
  const tmp = DB_PATH + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
  try {
    fs.renameSync(tmp, DB_PATH);
  } catch (e) {
    fs.copyFileSync(tmp, DB_PATH);
    fs.unlinkSync(tmp);
  }
}

function initDb() {
  const db = {
    tickets: [],
    users: [],
    itStaff: [],
    directory: [],
    history: [],
    notes: [],
    settings: {
      AUTO_ASSIGN: { value: 'false', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      DRY_RUN: { value: 'false', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      FORM_ENABLED: { value: 'true', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      DASHBOARD_ENABLED: { value: 'true', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      PRIMARY_ADMIN_EMAIL: { value: 'rick.barlow@alignedcardio.com', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      SECONDARY_ADMIN_EMAIL: { value: 'srihari.thangavel@alignedcardio.com', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      TERTIARY_ADMIN_EMAIL: { value: 'monapuri.pranay@alignedcardio.com', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      ESCALATION_EMAIL: { value: 'rick.barlow@alignedcardio.com', lastUpdated: new Date().toISOString(), updatedBy: 'System' },
      CRITICAL_EMAIL: { value: 'rick.barlow@alignedcardio.com', lastUpdated: new Date().toISOString(), updatedBy: 'System' }
    }
  };

  saveDb(db);
  return db;
}


function withDb(fn) {
  const db = loadDb();
  const result = fn(db);
  saveDb(db);
  return result;
}

function readDb() {
  return loadDb();
}

module.exports = { loadDb, saveDb, initDb, withDb, readDb, DB_PATH };
