const db = require('../db');
const utils = require('../utils');

function validateUser(employeeId, password) {
  if (!employeeId || !password) {
    return { success: false, message: 'Employee ID and Password are required' };
  }
  const data = db.readDb();
  const user = data.users.find(u => u.employeeId.toLowerCase() === String(employeeId).toLowerCase().trim());
  if (!user) return { success: false, message: 'Employee ID not found' };
  if (user.password !== password) return { success: false, message: 'Invalid password' };
  if (String(user.status).toLowerCase() === 'inactive') return { success: false, message: 'Your account is inactive. Please contact IT Admin.' };

  db.withDb(d => {
    const u = d.users.find(x => x.employeeId.toLowerCase() === String(employeeId).toLowerCase().trim());
    if (u) u.status = 'Online';
  });

  return {
    success: true,
    displayName: user.displayName,
    role: user.role,
    employeeId: user.employeeId,
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
  return {
    formEnabled: settings.FORM_ENABLED !== false && settings.FORM_ENABLED !== 'false',
    dashboardEnabled: settings.DASHBOARD_ENABLED !== false && settings.DASHBOARD_ENABLED !== 'false',
    autoAssign: utils.getAutoAssignSetting(),
    dryRun: require('../config').DRY_RUN,
    version: '7.9.0',
    settings,
    adminEmails: utils.getAdminEmails(d),
    dbPath: db.DB_PATH
  };
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
  getSystemStatus
};
