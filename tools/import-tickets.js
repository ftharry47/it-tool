const fs = require('fs');
const path = require('path');

// Use the project root as base so we can load src/ files regardless of cwd
const root = path.resolve(__dirname, '..');
const db = require(path.join(root, 'src', 'db'));
const utils = require(path.join(root, 'src', 'utils'));
const config = require(path.join(root, 'src', 'config'));

const inputFile = process.argv[2] || path.join(__dirname, 'old-tickets.json');

if (!fs.existsSync(inputFile)) {
  console.error('Import file not found:', inputFile);
  console.error('Usage: node tools/import-tickets.js <path-to-file>');
  console.error('Supported formats: .json (array) or .csv');
  process.exit(1);
}

const fieldAliases = {
  'Created Date': ['created date', 'createddate', 'created_date', 'created', 'date created', 'date_created'],
  'Ticket ID': ['ticket id', 'ticketid', 'ticket_id', 'ticketid'],
  'Name': ['name', 'requester', 'submitter', 'reporter'],
  'Email Address': ['email address', 'emailaddress', 'email_address', 'email', 'e-mail'],
  'Phone Number': ['phone number', 'phonenumber', 'phone_number', 'phone'],
  'Location': ['location', 'site'],
  'Issue Type': ['issue type', 'issuetype', 'issue_type', 'issue', 'category'],
  'Impact Area': ['impact area', 'impactarea', 'impact_area', 'work mode', 'workmode', 'work_mode', 'impact'],
  'Short Description': ['short description', 'shortdescription', 'short_description', 'shortdesc', 'subject', 'title'],
  'Attachments': ['attachments', 'files', 'attachment'],
  'Status': ['status', 'ticket status', 'ticket_status'],
  'Priority': ['priority'],
  'Critical Flag': ['critical flag', 'criticalflag', 'critical_flag', 'critical'],
  'Assigned To': ['assigned to', 'assignedto', 'assigned_to', 'assignee'],
  'Assigned Date': ['assigned date', 'assigneddate', 'assigned_date'],
  'Escalation Level': ['escalation level', 'escalationlevel', 'escalation_level', 'escalation'],
  'Escalated To': ['escalated to', 'escalatedto', 'escalated_to'],
  'Escalation Date': ['escalation date', 'escalationdate', 'escalation_date'],
  'Last Updated': ['last updated', 'lastupdated', 'last_updated', 'updated'],
  'Resolved By': ['resolved by', 'resolvedby', 'resolved_by'],
  'Resolved Date': ['resolved date', 'resolveddate', 'resolved_date']
};

function canonicalKey(rawKey) {
  if (!rawKey) return '';
  const normalized = String(rawKey).toLowerCase().trim();
  for (const [canonical, aliases] of Object.entries(fieldAliases)) {
    if (canonical.toLowerCase() === normalized || aliases.includes(normalized)) {
      return canonical;
    }
  }
  return rawKey; // keep unknown keys as-is
}

// Only fields actually used by the current form and dashboard
const AVAILABLE_FIELDS = [
  'Created Date', 'Ticket ID', 'Name', 'Email Address', 'Phone Number',
  'Location', 'Issue Type', 'Impact Area', 'Short Description',
  'Status', 'Priority', 'Critical Flag', 'Assigned To', 'Assigned Date',
  'Escalation Level', 'Escalated To', 'Escalation Date', 'Last Updated',
  'Resolved By', 'Resolved Date', 'Attachments'
];

function tokenize(s) {
  if (!s) return [];
  return String(s).toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
}

function scoreMatch(raw, option) {
  const rawTok = tokenize(raw);
  const optTok = tokenize(option);
  if (!rawTok.length || !optTok.length) return 0;
  const rawSet = new Set(rawTok);
  let score = 0;
  for (const t of optTok) if (rawSet.has(t)) score++;
  const rawStr = String(raw).toLowerCase().trim();
  const optStr = String(option).toLowerCase().trim();
  if (rawStr === optStr) score += 100;
  else if (optStr.includes(rawStr)) score += 10;
  else if (rawStr.includes(optStr)) score += 10;
  return score;
}

function normalizeLocation(raw) {
  if (!raw) return '';
  const r = String(raw).trim();
  if (!config.locations || !config.locations.length) return r;
  // Exact match first
  const exact = config.locations.find(l => l.toLowerCase().trim() === r.toLowerCase());
  if (exact) return exact;
  let best = null, bestScore = -1;
  for (const loc of config.locations) {
    const sc = scoreMatch(r, loc);
    if (sc > bestScore) {
      bestScore = sc;
      best = loc;
    }
  }
  return best && bestScore > 0 ? best : r;
}

function parseDate(v) {
  if (!v) return '';
  const d = new Date(v);
  if (!isNaN(d.getTime())) return d.toISOString();
  return String(v);
}

function parseCSV(text) {
  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    const next = text[i + 1];
    if (c === '"') {
      if (inQuotes && next === '"') {
        cell += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (c === ',' && !inQuotes) {
      row.push(cell);
      cell = '';
    } else if ((c === '\n' || c === '\r') && !inQuotes) {
      if (cell !== '' || row.length > 0) {
        row.push(cell);
        rows.push(row);
      }
      row = [];
      cell = '';
      if (c === '\r' && next === '\n') i++;
    } else {
      cell += c;
    }
  }
  if (cell !== '' || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

function loadRecords() {
  const ext = path.extname(inputFile).toLowerCase();
  const text = fs.readFileSync(inputFile, 'utf8');
  if (ext === '.json') {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed : (parsed.tickets || []);
  }
  if (ext === '.csv' || ext === '.tsv') {
    const delim = ext === '.tsv' ? '\t' : ',';
    const rows = ext === '.tsv'
      ? text.split(/\r?\n/).filter(l => l.trim() !== '').map(l => l.split('\t'))
      : parseCSV(text);
    if (rows.length < 2) throw new Error('CSV appears empty');
    const headers = rows[0].map(h => canonicalKey(h.trim()));
    return rows.slice(1).map(cells => {
      const obj = {};
      headers.forEach((h, i) => {
        if (h) obj[h] = cells[i] !== undefined ? cells[i].trim() : '';
      });
      return obj;
    });
  }
  throw new Error('Unsupported file format. Use .json, .csv, or .tsv');
}

function normalizeRecord(raw) {
  const t = {};
  // First pass: canonicalize known keys
  for (const [key, value] of Object.entries(raw)) {
    const ckey = canonicalKey(key);
    if (ckey) t[ckey] = value;
  }
  // Second pass: ensure defaults
  const now = new Date().toISOString();
  const required = {
    'Created Date': parseDate(t['Created Date']) || now,
    'Last Updated': parseDate(t['Last Updated']) || now,
    'Status': t['Status'] && String(t['Status']).trim() !== '' ? String(t['Status']).trim() : 'Open',
    'Priority': config.PRIORITY_MAP[t['Priority']] || t['Priority'] || 'Pending',
    'Critical Flag': (t['Critical Flag'] === true || String(t['Critical Flag']).toLowerCase() === 'true' || String(t['Critical Flag']).toLowerCase() === 'yes') ? 'true' : 'false',
    'Escalation Level': t['Escalation Level'] || 'L1',
    'Assigned To': t['Assigned To'] || '',
    'Assigned Date': parseDate(t['Assigned Date']) || '',
    'Escalated To': t['Escalated To'] || '',
    'Escalation Date': parseDate(t['Escalation Date']) || '',
    'Resolved By': t['Resolved By'] || '',
    'Resolved Date': parseDate(t['Resolved Date']) || '',
    'Attachments': Array.isArray(t['Attachments']) ? JSON.stringify(t['Attachments']) : (t['Attachments'] || ''),
    'Name': (t['Name'] || 'Unknown').trim(),
    'Email Address': (t['Email Address'] || '').trim().toLowerCase(),
    'Phone Number': t['Phone Number'] || '',
    'Location': normalizeLocation(t['Location']),
    'Issue Type': t['Issue Type'] || 'General IT query',
    'Impact Area': t['Impact Area'] || 'User Productivity',
    'Short Description': (t['Short Description'] || 'No description').trim()
  };
  // Normalize and apply defaults for all fields
  for (const [k, v] of Object.entries(required)) {
    t[k] = v;
  }
  // Normalize location regardless of whether a value was present
  t['Location'] = normalizeLocation(t['Location']);
  if (t['Critical Flag'] === 'true' && t['Priority'] === 'Pending') {
    t['Priority'] = 'Critical';
  }
  // Keep only fields used by the current form/dashboard
  const filtered = {};
  for (const f of AVAILABLE_FIELDS) {
    filtered[f] = t[f] !== undefined ? t[f] : '';
  }
  return filtered;
}

function getNextTicketId(usedIds, maxNum) {
  let n = maxNum + 1;
  let id;
  do {
    id = 'SSPTKT-' + String(n).padStart(3, '0');
    n++;
  } while (usedIds.has(id));
  usedIds.add(id);
  return id;
}

function main() {
  let records;
  try {
    records = loadRecords();
  } catch (e) {
    console.error('Error reading import file:', e.message);
    process.exit(1);
  }

  let added = 0;
  let failed = 0;
  let skipped = 0;
  const idMap = [];

  function isBlankRecord(raw) {
    return Object.values(raw).every(v => !v || String(v).trim() === '');
  }

  db.withDb(d => {
    const usedIds = new Set((d.tickets || []).map(t => String(t['Ticket ID'] || '').trim()));
    let maxNum = 0;
    usedIds.forEach(id => {
      const m = id.match(/^SSPTKT-(\d+)$/i);
      if (m) maxNum = Math.max(maxNum, parseInt(m[1], 10));
    });

    for (const raw of records) {
      try {
        if (isBlankRecord(raw)) { skipped++; continue; }
        const row = normalizeRecord(raw);
        let ticketId = String(row['Ticket ID'] || '').trim();

        if (ticketId && !usedIds.has(ticketId)) {
          usedIds.add(ticketId);
          const m = ticketId.match(/^SSPTKT-(\d+)$/i);
          if (m) maxNum = Math.max(maxNum, parseInt(m[1], 10));
        } else {
          const newId = getNextTicketId(usedIds, maxNum);
          if (ticketId) idMap.push({ original: ticketId, assigned: newId, reason: 'duplicate ID' });
          ticketId = newId;
          maxNum = parseInt(ticketId.split('-')[1], 10);
        }
        row['Ticket ID'] = ticketId;

        d.tickets.push(row);

        d.history.push({
          timestamp: new Date().toISOString(),
          ticketId: ticketId,
          action: 'Imported',
          from: '',
          to: row['Status'],
          performedBy: 'System Import',
          notes: `Imported from ${path.basename(inputFile)}`
        });

        added++;
      } catch (e) {
        console.error('Row failed:', e.message, JSON.stringify(raw));
        failed++;
      }
    }
  });

  console.log(`Import complete. Added: ${added}, skipped: ${skipped}, failed: ${failed}`);
  if (idMap.length > 0) {
    console.log('Ticket ID remapping (old -> new):');
    idMap.slice(0, 10).forEach(m => console.log(`  ${m.original} -> ${m.assigned} (${m.reason})`));
    if (idMap.length > 10) console.log(`  ... and ${idMap.length - 10} more`);
  }
}

main();
