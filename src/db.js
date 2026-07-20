const fs = require('fs');
const path = require('path');
const os = require('os');

const DB_DIR = process.env.DB_DIR || path.join(process.env.LOCALAPPDATA || os.tmpdir(), 'it-tool');
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
    users: [
      { employeeId: 'admin', password: 'admin123', displayName: 'IT Administrator', role: 'Admin', status: 'Online' },
      { employeeId: 'user1', password: 'password1', displayName: 'John Smith', role: 'L1', status: 'Online' },
      { employeeId: 'user2', password: 'password2', displayName: 'Jane Doe', role: 'L2', status: 'Online' },
      { employeeId: 'user3', password: 'password3', displayName: 'Mike Johnson', role: 'L3', status: 'Online' }
    ],
    itStaff: [
      { name: 'John Smith', email: 'john@example.com', level: 'L1', status: 'Online' },
      { name: 'Jane Doe', email: 'jane@example.com', level: 'L2', status: 'Online' },
      { name: 'Mike Johnson', email: 'mike@example.com', level: 'L3', status: 'Online' }
    ],
    directory: [
      { employeeId: 'EMP001', name: 'Alice Brown', email: 'alice@example.com', vipLevel: 'High' },
      { employeeId: 'EMP002', name: 'Bob Wilson', email: 'bob@example.com', vipLevel: 'Middle' },
      { employeeId: 'EMP003', name: 'Carol Davis', email: 'carol@example.com', vipLevel: 'Low' }
    ],
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

  const now = new Date();
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const twoDaysAgo = new Date(now.getTime() - 48 * 60 * 60 * 1000);

  db.tickets = [
    buildTicket(now, 'SSPTKT-001', 'EMP001', 'Alice Brown', 'alice@example.com', '555-0101', 'Cath Lab', 'Login issue', 'User Productivity', 'Cannot access patient records', 'Error message appears when trying to open charts', 'Open', 'Pending', 'High', true, '', '', 'L1', '', '', now, '', ''),
    buildTicket(yesterday, 'SSPTKT-002', 'EMP002', 'Bob Wilson', 'bob@example.com', '555-0102', 'Echo Lab', 'Laptop / desktop issue', 'User Productivity', 'Computer running slow', 'Takes 5 minutes to boot up', 'In Progress', 'High', 'Middle', false, 'John Smith', yesterday, 'L1', '', '', yesterday, '', ''),
    buildTicket(twoDaysAgo, 'SSPTKT-003', 'EMP003', 'Carol Davis', 'carol@example.com', '555-0103', 'Administration', 'Printer / scanner', 'Service Request', 'Printer not working', 'Paper jam error', 'Resolved', 'Medium', 'Low', false, 'Jane Doe', twoDaysAgo, 'L1', '', '', now, 'Jane Doe', now)
  ];

  saveDb(db);
  return db;
}

function buildTicket(created, ticketId, empId, name, email, phone, location, issueType, impactArea, shortDesc, additionalDesc, status, priority, vipLevel, criticalFlag, assignedTo, assignedDate, escalationLevel, escalatedTo, escalationDate, lastUpdated, resolvedBy, resolvedDate) {
  return {
    'Created Date': created.toISOString(),
    'Ticket ID': ticketId,
    'Employee ID': empId,
    'Name': name,
    'Email Address': email,
    'Phone Number': phone,
    'Location': location,
    'Issue Type': issueType,
    'Impact Area': impactArea,
    'Short Description': shortDesc,
    'Additional Description': additionalDesc,
    'Status': status,
    'Priority': priority,
    'VIP Level': vipLevel,
    'Critical Flag': criticalFlag ? 'true' : 'false',
    'Assigned To': assignedTo,
    'Assigned Date': assignedDate ? assignedDate.toISOString() : '',
    'Escalation Level': escalationLevel,
    'Escalated To': escalatedTo,
    'Escalation Date': escalationDate ? escalationDate.toISOString() : '',
    'Last Updated': lastUpdated.toISOString(),
    'Resolved By': resolvedBy,
    'Resolved Date': resolvedDate ? resolvedDate.toISOString() : ''
  };
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
