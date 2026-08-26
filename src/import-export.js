const db = require('./db');
const utils = require('./utils');
const config = require('./config');
const sla = require('./sla');
const audit = require('./audit');
const core = require('./handlers/core');

function parseCSV(text) {
  const rows = [];
  let current = [];
  let field = '';
  let inQuote = false;
  const str = String(text).replace(/\r\n/g, '\n').replace(/\r/g, '\n') + '\n';
  for (let i = 0; i < str.length; i++) {
    const ch = str[i];
    const next = str[i + 1];
    if (ch === '"') {
      if (inQuote && next === '"') { field += '"'; i++; continue; }
      inQuote = !inQuote;
      continue;
    }
    if (ch === ',' && !inQuote) { current.push(field); field = ''; continue; }
    if (ch === '\n' && !inQuote) {
      current.push(field);
      if (current.some(f => f.trim() !== '')) rows.push(current);
      current = [];
      field = '';
      continue;
    }
    field += ch;
  }
  return rows;
}

function rowToObject(headers, row) {
  const obj = {};
  headers.forEach((h, i) => { obj[h] = (row[i] || '').trim(); });
  return obj;
}

function exportTickets(filters = {}) {
  const d = db.readDb();
  let tickets = utils.getAllTickets();
  if (filters.status) tickets = tickets.filter(t => String(t['Status'] || '').toLowerCase() === String(filters.status).toLowerCase());
  if (filters.priority) tickets = tickets.filter(t => String(t['Priority'] || '').toLowerCase() === String(filters.priority).toLowerCase());
  if (filters.issueType) tickets = tickets.filter(t => String(t['Issue Type'] || '').toLowerCase() === String(filters.issueType).toLowerCase());
  if (filters.location) tickets = tickets.filter(t => String(t['Location'] || '').toLowerCase().includes(String(filters.location).toLowerCase()));
  const rows = tickets.map(t => config.HEADERS.map(h => t[h] != null ? t[h] : ''));
  const csv = utils.generateCSVContent(config.HEADERS, rows);
  return { success: true, csv, count: tickets.length };
}

function exportUsers() {
  const d = db.readDb();
  const users = Array.isArray(d.directory) ? d.directory : [];
  const headers = ['Employee ID', 'Name', 'Email'];
  const rows = users.map(u => [u.employeeId || '', u.name || '', u.email || '']);
  const csv = utils.generateCSVContent(headers, rows);
  return { success: true, csv, count: users.length };
}

function importTickets(csvText, updatedBy) {
  const parsed = parseCSV(csvText);
  if (parsed.length < 2) return { success: false, error: 'CSV must have a header row and at least one data row' };
  const headers = parsed[0];
  const dataRows = parsed.slice(1);
  const imported = [];
  const skipped = [];
  const errors = [];

  db.withDb(d => {
    for (const raw of dataRows) {
      const row = rowToObject(headers, raw);
      const name = row['Name'] || row['name'];
      const email = row['Email Address'] || row['email'] || row['Email'];
      const shortDesc = row['Short Description'] || row['shortDescription'];
      if (!name || !email || !shortDesc) { errors.push({ row, error: 'Missing required fields (Name, Email Address, Short Description)' }); continue; }

      const ticketId = (row['Ticket ID'] || utils.generateTicketId(d)).trim();
      const existing = utils.findTicket(d, ticketId);
      if (existing) { skipped.push(ticketId); continue; }

      const now = new Date().toISOString();
      const created = row['Created Date'] ? new Date(row['Created Date']).toISOString() : now;
      const priority = row['Priority'] || 'Pending';
      const status = row['Status'] || 'Open';
      const t = {};
      config.HEADERS.forEach(h => { t[h] = ''; });
      t['Created Date'] = created;
      t['Ticket ID'] = ticketId;
      t['Employee ID'] = row['Employee ID'] || '';
      t['Name'] = name;
      t['Email Address'] = email;
      t['Phone Number'] = row['Phone Number'] || '';
      t['Location'] = row['Location'] || '';
      t['Issue Type'] = row['Issue Type'] || 'General IT query';
      t['Impact Area'] = row['Impact Area'] || 'User Productivity';
      t['Short Description'] = shortDesc;
      t['Additional Description'] = row['Additional Description'] || '';
      t['Attachments'] = row['Attachments'] || '';
      t['Status'] = config.statuses.includes(status) ? status : 'Open';
      t['Priority'] = config.VALID_PRIORITIES.includes(priority) ? priority : 'Pending';
      t['Critical Flag'] = String(t['Priority'] === 'Critical' || String(row['Critical Flag']).toLowerCase() === 'true');
      t['Assigned To'] = row['Assigned To'] || '';
      t['Assigned Date'] = row['Assigned Date'] || '';
      t['Escalation Level'] = row['Escalation Level'] || 'L1';
      t['Escalated To'] = row['Escalated To'] || '';
      t['Escalation Date'] = row['Escalation Date'] || '';
      t['Last Updated'] = now;
      t['Resolved By'] = '';
      t['Resolved Date'] = '';
      t['Project'] = row['Project'] || '';
      t['Issue Class'] = row['Issue Class'] || 'Incident';
      sla.updateTicketSLAFields(t);
      d.tickets.push(t);
      d.history.push({ timestamp: now, ticketId, action: 'Created', from: '', to: 'Open', performedBy: updatedBy || 'CSV Import', notes: 'Imported via CSV' });
      audit.appendAuditEntry(d, { action: 'TICKET_IMPORTED', targetType: 'Ticket', targetId: ticketId, performedBy: updatedBy || 'CSV Import', details: 'Imported via bulk CSV' });
      imported.push(ticketId);
    }
  });

  return { success: true, imported, skipped, errors, importedCount: imported.length, skippedCount: skipped.length, errorCount: errors.length };
}

function importUsers(csvText, token, updatedBy) {
  const parsed = parseCSV(csvText);
  if (parsed.length < 2) return { success: false, error: 'CSV must have a header row and at least one data row' };
  const headers = parsed[0].map(h => h.trim().toLowerCase());
  const dataRows = parsed.slice(1);
  const entries = [];
  for (const raw of dataRows) {
    const row = rowToObject(parsed[0], raw);
    const email = row['Email'] || row['email'] || row['Email Address'];
    const name = row['Name'] || row['name'];
    if (email) entries.push({ email: String(email).trim(), name: name || '' });
  }
  const result = core.bulkImportDirectory(entries, token);
  if (result.success) {
    db.withDb(d => {
      audit.appendAuditEntry(d, { action: 'USERS_IMPORTED', targetType: 'Directory', targetId: 'bulk', performedBy: updatedBy || 'CSV Import', details: 'Imported ' + (result.added || 0) + ' users' });
    });
  }
  return result;
}

module.exports = { exportTickets, exportUsers, importTickets, importUsers, parseCSV };
