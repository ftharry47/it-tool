const db = require('./db');
const events = require('./events');

function ensureAudit(d) {
  if (!Array.isArray(d.audit)) d.audit = [];
}

function appendAuditEntry(d, { action, targetType = '', targetId = '', performedBy = 'System', details = '', before = '', after = '' }) {
  ensureAudit(d);
  const entry = {
    timestamp: new Date().toISOString(),
    action,
    targetType,
    targetId: String(targetId || ''),
    performedBy,
    details,
    before: String(before != null ? before : ''),
    after: String(after != null ? after : '')
  };
  d.audit.push(entry);
  events.emitActivity(entry);
}

function logAudit({ action, targetType, targetId, performedBy, details, before, after }) {
  db.withDb(d => appendAuditEntry(d, { action, targetType, targetId, performedBy, details, before, after }));
}

function getAuditLogs(limit = 50, offset = 0, filters = {}) {
  const d = db.readDb();
  let logs = Array.isArray(d.audit) ? d.audit : [];
  if (filters.action) logs = logs.filter(l => l.action === filters.action);
  if (filters.targetType) logs = logs.filter(l => l.targetType === filters.targetType);
  if (filters.targetId) logs = logs.filter(l => String(l.targetId).toLowerCase() === String(filters.targetId).toLowerCase());
  if (filters.performedBy) logs = logs.filter(l => String(l.performedBy).toLowerCase().includes(String(filters.performedBy).toLowerCase()));
  logs.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
  const total = logs.length;
  const paginated = logs.slice(offset, offset + limit);
  return { success: true, logs: paginated, total, limit, offset };
}

module.exports = { appendAuditEntry, logAudit, getAuditLogs, ensureAudit };
