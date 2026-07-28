const db = require('../db');
const utils = require('../utils');
const config = require('../config');

function validateUser(login, password) {
  if (!login || !password) {
    return { success: false, message: 'Email and Password are required' };
  }
  const input = String(login).toLowerCase().trim();
  const data = db.readDb();
  const user = data.users.find(u => {
    if (u.email && String(u.email).toLowerCase().trim() === input) return true;
    if (u.employeeId && u.employeeId.toLowerCase() === input) return true;
    return false;
  });
  if (!user) return { success: false, message: 'Email not found' };
  if (user.password !== password) return { success: false, message: 'Invalid password' };
  if (String(user.status).toLowerCase() === 'inactive') return { success: false, message: 'Your account is inactive. Please contact IT Admin.' };

  db.withDb(d => {
    const u = d.users.find(u2 => {
      if (u2.email && String(u2.email).toLowerCase().trim() === input) return true;
      return u2.employeeId && u2.employeeId.toLowerCase() === input;
    });
    if (u) u.status = 'Online';
  });

  return {
    success: true,
    displayName: user.displayName,
    role: user.role,
    employeeId: user.employeeId,
    email: user.email || user.employeeId,
    status: 'Online',
    message: 'Login successful'
  };
}

function getAllUsers() {
  const data = db.readDb();
  return data.users.map(u => ({
    employeeId: u.employeeId,
    name: u.displayName,
    displayName: u.displayName,
    role: u.role,
    status: u.status
  }));
}

function updateUserStatus(employeeId, newStatus) {
  if (!employeeId) return { success: false, error: 'Employee ID is required' };
  if (!newStatus) return { success: false, error: 'New status is required' };
  const normalized = utils.normalizeStatus(newStatus);
  const config = require('../config');
  if (!config.VALID_USER_STATUSES.includes(normalized)) {
    return { success: false, error: 'Invalid status' };
  }
  return db.withDb(d => {
    const user = d.users.find(u => u.employeeId.toLowerCase() === String(employeeId).toLowerCase());
    if (!user) return { success: false, error: 'User not found' };
    user.status = normalized;
    const staff = d.itStaff.find(s => s.name.toLowerCase() === user.displayName.toLowerCase());
    if (staff) staff.status = normalized;
    return { success: true, message: 'Status updated to ' + normalized, employeeId, newStatus: normalized };
  });
}

function updateITStaffStatus(staffName, newStatus) {
  db.withDb(d => {
    const staff = d.itStaff.find(s => s.name.toLowerCase() === String(staffName).toLowerCase().trim());
    if (staff) staff.status = newStatus;
  });
}

function lookupEmployee(email) {
  const result = { found: false, empId: '', name: '', vipLevel: 'Low' };
  if (!email || typeof email !== 'string' || String(email).trim() === '') return result;
  const search = String(email).toLowerCase().trim();
  const data = db.readDb();
  const row = data.directory.find(r => String(r.email).toLowerCase().trim() === search);
  if (row) {
    return {
      found: true,
      empId: String(row.employeeId || '').trim(),
      name: String(row.name || '').trim(),
      vipLevel: String(row.vipLevel || 'Low').trim()
    };
  }
  return result;
}

function lookupEmployeeSafe(email) {
  const r = lookupEmployee(email);
  return {
    found: r.found === true,
    empId: String(r.empId || ''),
    name: String(r.name || ''),
    vipLevel: String(r.vipLevel || 'Low')
  };
}

function toggleAutoAssign(enabled, updatedBy) {
  const result = db.withDb(d => utils.writeSetting(d, 'AUTO_ASSIGN', enabled ? 'true' : 'false', updatedBy));
  return result.success ? { success: true, enabled, message: 'Auto-assign ' + (enabled ? 'enabled' : 'disabled') } : result;
}

function setupSystem() {
  db.initDb();
  return { success: true, message: 'System initialized' };
}

function getSystemStatus() {
  const d = db.readDb();
  const settings = utils.getAllSettings();
  const dryRunSetting = utils.getSetting('DRY_RUN');
  return {
    formEnabled: settings.FORM_ENABLED !== false && settings.FORM_ENABLED !== 'false',
    dashboardEnabled: settings.DASHBOARD_ENABLED !== false && settings.DASHBOARD_ENABLED !== 'false',
    autoAssign: utils.getAutoAssignSetting(),
    dryRun: dryRunSetting !== null ? !!dryRunSetting : config.DRY_RUN,
    version: '8.1.0',
    settings,
    adminEmails: utils.getAdminEmails(d),
    dbPath: db.DB_PATH
  };
}

function normalizeNameFromEmail(email) {
  if (!email) return 'Unknown';
  let local = String(email).split('@')[0];
  // Azure AD B2B guest format: user_domain.com#EXT#@tenant
  local = local.replace(/#EXT#.*$/, '');
  // Insert spaces between camelCase words
  local = local.replace(/([a-z])([A-Z])/g, '$1 $2');
  // Split on . _ and -
  local = local.replace(/[._-]/g, ' ');
  // Title case and trim
  return local
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map(w => w[0].toUpperCase() + w.slice(1))
    .join(' ')
    .trim() || 'Unknown';
}

function nextEmployeeId(directory) {
  let max = 0;
  for (const row of directory) {
    const n = parseInt(String(row.employeeId || '').replace(/\D/g, ''), 10);
    if (!isNaN(n) && n > max) max = n;
  }
  return 'EMP' + String(max + 1).padStart(3, '0');
}

function addDirectory(entry) {
  if (!entry || (!entry.email && typeof entry !== 'string')) {
    return { success: false, error: 'Email is required' };
  }
  const email = typeof entry === 'string' ? entry.trim() : String(entry.email || '').trim();
  if (!email) return { success: false, error: 'Email is required' };
  const name = typeof entry === 'object' && entry.name ? String(entry.name).trim() : normalizeNameFromEmail(email);
  const vipLevel = (typeof entry === 'object' && entry.vipLevel ? String(entry.vipLevel) : 'Low');

  return db.withDb(d => {
    const exists = d.directory.find(r => String(r.email).toLowerCase().trim() === email.toLowerCase());
    if (exists) return { success: false, skipped: true, email, message: 'Email already exists' };
    const employeeId = nextEmployeeId(d.directory);
    d.directory.push({ employeeId, name, email, vipLevel });
    return { success: true, email, employeeId, name };
  });
}

function bulkImportDirectory(entries, token) {
  if (!Array.isArray(entries)) {
    return { success: false, error: 'entries must be an array' };
  }
  const expected = process.env.BULK_IMPORT_TOKEN;
  if (expected && token !== expected) {
    return { success: false, error: 'Invalid or missing bulk import token' };
  }
  let added = 0;
  let skipped = 0;
  const results = [];
  for (const item of entries) {
    const r = addDirectory(item);
    if (r.success) added++;
    else if (r.skipped) skipped++;
    results.push(r);
  }
  return { success: true, added, skipped, total: entries.length };
}

function addUser(user) {
  if (!user || !user.email || !user.password || !user.role) {
    return { success: false, error: 'Email, password and role are required' };
  }
  const email = String(user.email).toLowerCase().trim();
  const password = String(user.password).trim();
  const role = String(user.role).trim();
  const status = user.status ? String(user.status).trim() : 'Active';
  const displayName = user.displayName ? String(user.displayName).trim() : normalizeNameFromEmail(email);
  const employeeId = user.employeeId ? String(user.employeeId).trim() : email.split('@')[0];
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return { success: false, error: 'Invalid email format' };
  }
  return db.withDb(d => {
    const exists = d.users.find(u =>
      (u.email && u.email.toLowerCase() === email) ||
      (u.employeeId && u.employeeId.toLowerCase() === employeeId.toLowerCase())
    );
    if (exists) return { success: false, error: 'User already exists' };
    d.users.push({ email, employeeId, displayName, password, role, status });
    return { success: true, email, employeeId, displayName, role, status };
  });
}

function removeUser(employeeId) {
  if (!employeeId) return { success: false, error: 'Employee ID or email is required' };
  const search = String(employeeId).toLowerCase().trim();
  return db.withDb(d => {
    const idx = d.users.findIndex(u =>
      (u.employeeId && u.employeeId.toLowerCase() === search) ||
      (u.email && u.email.toLowerCase() === search)
    );
    if (idx === -1) return { success: false, error: 'User not found' };
    const removed = d.users.splice(idx, 1)[0];
    return { success: true, employeeId: removed.employeeId, email: removed.email, displayName: removed.displayName };
  });
}

module.exports = {
  validateUser,
  getAllUsers,
  updateUserStatus,
  updateITStaffStatus,
  lookupEmployee,
  lookupEmployeeSafe,
  toggleAutoAssign,
  setupSystem,
  getSystemStatus,
  addDirectory,
  bulkImportDirectory,
  addUser,
  removeUser
};
