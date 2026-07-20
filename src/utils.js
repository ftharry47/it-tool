const config = require('./config');
const db = require('./db');

function readSetting(d, name) {
  const s = d.settings[name];
  if (!s) return null;
  let v = s.value;
  if (v === 'true') return true;
  if (v === 'false') return false;
  return v;
}

function writeSetting(d, name, value, updatedBy) {
  d.settings[name] = {
    value: String(value),
    lastUpdated: new Date().toISOString(),
    updatedBy: updatedBy || 'System'
  };
  return { success: true, message: 'Setting updated' };
}

function getAdminEmails(d) {
  const settings = d.settings;
  const get = key => (settings[key] && settings[key].value) || config.ADMIN_EMAILS[key.replace('_EMAIL', '').replace('_ADMIN', '')] || '';
  return {
    primary: (settings.PRIMARY_ADMIN_EMAIL && settings.PRIMARY_ADMIN_EMAIL.value) || config.ADMIN_EMAILS.PRIMARY,
    secondary: (settings.SECONDARY_ADMIN_EMAIL && settings.SECONDARY_ADMIN_EMAIL.value) || config.ADMIN_EMAILS.SECONDARY,
    tertiary: (settings.TERTIARY_ADMIN_EMAIL && settings.TERTIARY_ADMIN_EMAIL.value) || config.ADMIN_EMAILS.TERTIARY,
    escalation: (settings.ESCALATION_EMAIL && settings.ESCALATION_EMAIL.value) || config.ADMIN_EMAILS.ESCALATION,
    critical: (settings.CRITICAL_EMAIL && settings.CRITICAL_EMAIL.value) || config.ADMIN_EMAILS.CRITICAL
  };
}

function getAllSettings() {
  const result = {};
  const d = db.readDb();
  Object.keys(d.settings).forEach(k => { result[k] = readSetting(d, k); });
  return result;
}

function getSetting(name) {
  return readSetting(db.readDb(), name);
}

function setSetting(name, value, updatedBy) {
  return db.withDb(d => writeSetting(d, name, value, updatedBy));
}

function getAutoAssignSetting() {
  return getSetting('AUTO_ASSIGN') === true || getSetting('AUTO_ASSIGN') === 'true';
}

function isValidEmail(email) {
  if (!email) return false;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email).trim());
}

function normalizeStatus(status) {
  const s = String(status).toLowerCase().trim();
  if (s === 'online' || s === 'active') return 'Online';
  if (s === 'offline' || s === 'inactive') return 'Offline';
  if (s === 'break' || s === 'on break') return 'Break';
  if (s === 'in meeting' || s === 'meeting') return 'In Meeting';
  return status;
}

function findTicket(d, ticketId) {
  return d.tickets.find(t => String(t['Ticket ID']).trim() === String(ticketId).trim());
}

function generateTicketId() {
  const d = db.readDb();
  let highest = 0;
  d.tickets.forEach(t => {
    const id = String(t['Ticket ID']);
    if (id.indexOf('SSPTKT-') === 0) {
      const num = parseInt(id.split('-')[1]);
      if (!isNaN(num) && num > highest) highest = num;
    }
  });
  let newNum = (highest + 1).toString();
  while (newNum.length < 3) newNum = '0' + newNum;
  return 'SSPTKT-' + newNum;
}

function formatDate(date) {
  try {
    return new Date(date).toLocaleString('en-US', { hour12: true });
  } catch (e) {
    return String(date);
  }
}

function ticketValue(ticket, key) {
  if (!ticket) return '';
  return ticket[key] !== undefined && ticket[key] !== null ? String(ticket[key]) : '';
}

function cloneTicket(t) {
  const ticket = {};
  config.HEADERS.forEach(h => {
    if (!h) return;
    let v = t[h];
    if (v === undefined || v === null) v = '';
    if (h.toLowerCase().indexOf('date') !== -1 && v) {
      try {
        const d = new Date(v);
        v = !isNaN(d.getTime()) ? d.toISOString() : String(v);
      } catch (e) { v = String(v); }
    } else {
      v = String(v);
    }
    ticket[h] = v;
  });
  return ticket;
}

function getAllTickets() {
  const d = db.readDb();
  const tickets = d.tickets.map(cloneTicket);
  tickets.sort((a, b) => {
    try { return new Date(b['Created Date'] || 0) - new Date(a['Created Date'] || 0); } catch (e) { return 0; }
  });
  return tickets;
}

function escapeCSVField(field) {
  if (field === null || field === undefined) return '';
  const str = String(field);
  if (str.indexOf(',') !== -1 || str.indexOf('"') !== -1 || str.indexOf('\n') !== -1 || str.indexOf('\r') !== -1) {
    return '"' + str.replace(/"/g, '""') + '"';
  }
  return str;
}

function generateCSVContent(headers, data) {
  let csv = headers.map(h => escapeCSVField(h)).join(',') + '\n';
  data.forEach(row => { csv += row.map(c => escapeCSVField(c)).join(',') + '\n'; });
  return csv;
}

module.exports = {
  readSetting, writeSetting, getAdminEmails, getAllSettings, getSetting, setSetting,
  getAutoAssignSetting, isValidEmail, normalizeStatus, findTicket, generateTicketId,
  formatDate, ticketValue, cloneTicket, getAllTickets, escapeCSVField, generateCSVContent
};
