const db = require('./db');
const config = require('./config');
const email = require('./email');
const sms = require('./sms');
const audit = require('./audit');

function getSLAConfig(priority) {
  return config.SLA_THRESHOLDS[priority] || config.SLA_THRESHOLDS.Pending || { response: 24, resolution: 72 };
}

function addHours(date, hours) {
  const d = new Date(date);
  d.setTime(d.getTime() + hours * 60 * 60 * 1000);
  return d.toISOString();
}

function getTicketStartTime(ticket) {
  const created = ticket['Created Date'];
  const assigned = ticket['Assigned Date'];
  const start = created || new Date().toISOString();
  if (assigned) {
    try {
      const createdDate = new Date(created);
      const assignedDate = new Date(assigned);
      if (!isNaN(assignedDate.getTime()) && assignedDate > createdDate) return assigned;
    } catch (e) {}
  }
  return start;
}

function calculateSLADueDates(ticket) {
  const priority = ticket['Priority'] || 'Pending';
  const sla = getSLAConfig(priority);
  const startTime = getTicketStartTime(ticket);
  return {
    responseDue: addHours(startTime, sla.response),
    resolutionDue: addHours(startTime, sla.resolution)
  };
}

function computeSLAStatus(ticket) {
  const status = String(ticket['Status'] || 'Open');
  if (status === 'Resolved' || status === 'Closed') return { status: 'Resolved', breached: false, breachTime: '' };
  if (ticket['SLA Breached'] === true || ticket['SLA Breached'] === 'true') {
    return { status: 'Breached', breached: true, breachTime: ticket['SLA Breach Time'] || new Date().toISOString() };
  }
  const now = new Date();
  const resolutionDue = ticket['Resolution Due'];
  const responseDue = ticket['Response Due'];
  if (resolutionDue) {
    try {
      const rd = new Date(resolutionDue);
      if (!isNaN(rd.getTime()) && now > rd) {
        return { status: 'Breached', breached: true, breachTime: now.toISOString() };
      }
    } catch (e) {}
  }
  if (responseDue) {
    try {
      const resD = new Date(responseDue);
      if (!isNaN(resD.getTime()) && now > resD) {
        return { status: 'At Risk', breached: false, breachTime: '' };
      }
    } catch (e) {}
  }
  if (resolutionDue) {
    try {
      const rd = new Date(resolutionDue);
      if (!isNaN(rd.getTime())) {
        const total = rd.getTime() - new Date(getTicketStartTime(ticket)).getTime();
        const elapsed = now.getTime() - new Date(getTicketStartTime(ticket)).getTime();
        if (total > 0 && elapsed / total > 0.8) {
          return { status: 'At Risk', breached: false, breachTime: '' };
        }
      }
    } catch (e) {}
  }
  return { status: 'On Track', breached: false, breachTime: '' };
}

function updateTicketSLAFields(ticket) {
  const dueDates = calculateSLADueDates(ticket);
  ticket['Response Due'] = dueDates.responseDue;
  ticket['Resolution Due'] = dueDates.resolutionDue;
  const statusInfo = computeSLAStatus(ticket);
  ticket['SLA Status'] = statusInfo.status;
  if (statusInfo.breached) {
    ticket['SLA Breached'] = 'true';
    if (!ticket['SLA Breach Time']) ticket['SLA Breach Time'] = statusInfo.breachTime;
  } else {
    ticket['SLA Breached'] = 'false';
    ticket['SLA Breach Time'] = '';
  }
  return statusInfo;
}

function processSLABreaches(d, options = {}) {
  const now = new Date();
  const notifications = options.notify !== false;
  const tickets = (d.tickets || []).filter(t => {
    const status = String(t['Status'] || '').toLowerCase();
    return status !== 'resolved' && status !== 'closed';
  });
  const results = [];
  for (const t of tickets) {
    const before = { breached: t['SLA Breached'], status: t['SLA Status'] };
    const statusInfo = updateTicketSLAFields(t);
    if (statusInfo.breached && String(before.breached) !== 'true') {
      t['Last Updated'] = now.toISOString();
      results.push({ ticketId: t['Ticket ID'], priority: t['Priority'], status: 'Breached', resolutionDue: t['Resolution Due'] });
      if (notifications) {
        email.sendSLABreachEmail(t['Ticket ID'], t).catch(() => {});
        sms.sendTicketEscalatedSms(t, 'SLA Manager', 'SLA Breach').catch(() => {});
      }
      if (typeof audit.appendAuditEntry === 'function') {
        audit.appendAuditEntry(d, { action: 'SLA_BREACH', targetType: 'Ticket', targetId: t['Ticket ID'], performedBy: 'SLA Engine', before: before.status, after: 'Breached', details: 'Resolution due: ' + (t['Resolution Due'] || '') });
      }
    }
  }
  return results;
}

function checkSLABreaches() {
  return db.withDb(d => {
    const breached = processSLABreaches(d);
    return { success: true, breached, count: breached.length };
  });
}

function getSLAStats(d) {
  d = d || db.readDb();
  const tickets = Array.isArray(d.tickets) ? d.tickets : [];
  const active = tickets.filter(t => {
    const status = String(t['Status'] || '').toLowerCase();
    return status !== 'resolved' && status !== 'closed';
  });
  let onTrack = 0, atRisk = 0, breached = 0;
  for (const t of active) {
    updateTicketSLAFields(t);
    const status = t['SLA Status'];
    if (status === 'Breached') breached += 1;
    else if (status === 'At Risk') atRisk += 1;
    else onTrack += 1;
  }
  return { onTrack, atRisk, breached, total: active.length };
}

module.exports = {
  getSLAConfig,
  calculateSLADueDates,
  computeSLAStatus,
  updateTicketSLAFields,
  processSLABreaches,
  checkSLABreaches,
  getSLAStats
};
