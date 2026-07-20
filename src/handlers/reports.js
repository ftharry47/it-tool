const db = require('../db');
const utils = require('../utils');
const config = require('../config');

function getTeamPerformance(filters) {
  try {
    const tickets = utils.getAllTickets();
    const staff = require('./tickets').getITStaffList();
    if (!staff.length) return [];
    const performance = {};
    staff.forEach(s => {
      performance[s.name] = {
        name: s.name, level: s.level || 'L1', status: s.status || 'Online',
        ticketsHandled: 0, resolved: 0, openTickets: 0, withinSla: 0, breachedSla: 0, totalResolutionHours: 0
      };
    });

    tickets.forEach(t => {
      const assignedTo = String(t['Assigned To'] || '').trim();
      if (!assignedTo || !performance[assignedTo]) return;
      const status = String(t['Status'] || 'Open').toLowerCase();
      const priority = String(t['Priority'] || 'Pending').trim();
      const created = t['Created Date'] ? new Date(t['Created Date']) : null;
      const resolved = t['Resolved Date'] ? new Date(t['Resolved Date']) : null;
      const p = performance[assignedTo];
      p.ticketsHandled++;
      if (status === 'resolved') {
        p.resolved++;
        if (resolved && created) {
          const hours = (resolved - created) / (1000 * 60 * 60);
          p.totalResolutionHours += hours;
          const threshold = config.SLA_THRESHOLDS[priority] || config.SLA_THRESHOLDS['Pending'];
          if (hours <= threshold.resolution) p.withinSla++; else p.breachedSla++;
        }
      } else {
        p.openTickets++;
      }
    });

    const result = [];
    for (const key in performance) {
      if (!performance.hasOwnProperty(key)) continue;
      const m = performance[key];
      const total = m.withinSla + m.breachedSla;
      const slaCompliance = total > 0 ? Math.round((m.withinSla / total) * 100) : 100;
      const avgHours = m.resolved > 0 ? m.totalResolutionHours / m.resolved : 0;
      let avgDisplay = '--';
      if (avgHours > 0) {
        if (avgHours < 1) avgDisplay = Math.round(avgHours * 60) + 'm';
        else if (avgHours < 24) avgDisplay = avgHours.toFixed(1) + 'h';
        else avgDisplay = (avgHours / 24).toFixed(1) + 'd';
      }
      result.push({ name: m.name, level: m.level, status: m.status, ticketsHandled: m.ticketsHandled, resolved: m.resolved, openTickets: m.openTickets, slaCompliance, avgResolutionTime: avgDisplay });
    }
    result.sort((a, b) => b.ticketsHandled - a.ticketsHandled);
    return result;
  } catch (e) {
    return [];
  }
}

function getSlaSummary(filters) {
  const tickets = utils.getAllTickets();
  const today = new Date(); today.setHours(0, 0, 0, 0);
  let resolvedToday = 0, breachedTickets = 0, withinSlaTickets = 0, totalResponseHours = 0, ticketsWithResponse = 0;
  tickets.forEach(t => {
    const priority = String(t['Priority'] || 'Pending').trim();
    const created = t['Created Date'] ? new Date(t['Created Date']) : null;
    const assigned = t['Assigned Date'] ? new Date(t['Assigned Date']) : null;
    const resolved = t['Resolved Date'] ? new Date(t['Resolved Date']) : null;
    if (resolved) { const r = new Date(resolved); r.setHours(0, 0, 0, 0); if (r.getTime() === today.getTime()) resolvedToday++; }
    if (created && assigned) { totalResponseHours += (assigned - created) / (1000 * 60 * 60); ticketsWithResponse++; }
    if (resolved && created) {
      const hours = (resolved - created) / (1000 * 60 * 60);
      const threshold = config.SLA_THRESHOLDS[priority] || config.SLA_THRESHOLDS['Pending'];
      if (hours <= threshold.resolution) withinSlaTickets++; else breachedTickets++;
    }
  });
  const avgResponseHours = ticketsWithResponse > 0 ? Math.round((totalResponseHours / ticketsWithResponse) * 10) / 10 : 0;
  const totalProcessed = withinSlaTickets + breachedTickets;
  const slaCompliance = totalProcessed > 0 ? Math.round((withinSlaTickets / totalProcessed) * 100) : 100;
  let avgDisplay = '--';
  if (avgResponseHours > 0) {
    if (avgResponseHours < 1) avgDisplay = Math.round(avgResponseHours * 60) + 'm';
    else if (avgResponseHours < 24) avgDisplay = avgResponseHours.toFixed(1) + 'h';
    else avgDisplay = (avgResponseHours / 24).toFixed(1) + 'd';
  }
  return { slaCompliance, avgResponseTime: avgDisplay, breachedTickets, resolvedToday, totalTickets: tickets.length };
}

function generateTicketsReport(tickets) {
  return tickets.map(t => [
    t['Ticket ID'] || '',
    t['Created Date'] ? utils.formatDate(t['Created Date']) : '',
    t['Name'] || '',
    t['Email Address'] || '',
    t['Phone Number'] || '',
    t['Location'] || '',
    t['Issue Type'] || '',
    t['Impact Area'] || '',
    t['Short Description'] || '',
    t['Status'] || '',
    t['Priority'] || 'Pending',
    t['VIP Level'] || '',
    t['Critical Flag'] === 'true' ? 'Yes' : 'No',
    t['Assigned To'] || '',
    t['Escalation Level'] || '',
    t['Resolved By'] || '',
    t['Resolved Date'] ? utils.formatDate(t['Resolved Date']) : ''
  ]);
}

function generateTeamReport() {
  return getTeamPerformance({}).map(p => [
    p.name || '', p.level || '', p.status || 'Active', p.ticketsHandled || 0, p.resolved || 0, p.openTickets || 0, p.slaCompliance || 0, p.avgResolutionTime || '--'
  ]);
}

function generateSLAReport(tickets) {
  return tickets.map(t => {
    const created = t['Created Date'] ? new Date(t['Created Date']) : null;
    const assigned = t['Assigned Date'] ? new Date(t['Assigned Date']) : null;
    const resolved = t['Resolved Date'] ? new Date(t['Resolved Date']) : null;
    const priority = t['Priority'] || 'Pending';
    const threshold = config.SLA_THRESHOLDS[priority] || config.SLA_THRESHOLDS['Pending'];
    let responseTime = '--', resolutionTime = '--', slaStatus = 'Pending';
    if (created && assigned) responseTime = ((assigned - created) / (1000 * 60 * 60)).toFixed(2);
    if (created && resolved) {
      const hours = (resolved - created) / (1000 * 60 * 60);
      resolutionTime = hours.toFixed(2);
      slaStatus = hours <= threshold.resolution ? 'Within SLA' : 'Breached';
    }
    return [t['Ticket ID'] || '', created ? utils.formatDate(created) : '', priority, t['Status'] || '', t['Assigned To'] || 'Unassigned', responseTime, resolutionTime, slaStatus, threshold.resolution];
  });
}

function generateMonthlyReport(tickets) {
  const now = new Date();
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const monthly = tickets.filter(t => t['Created Date'] && new Date(t['Created Date']) >= startOfMonth);
  const stats = require('./tickets').getDashboardStats(monthly);
  return [
    ['Total Tickets This Month', monthly.length, ''],
    ['Open Tickets', stats.open, ''],
    ['In Progress', stats.inProgress, ''],
    ['Resolved', stats.resolved, ''],
    ['High Priority Tickets', stats.highPriority, 'Critical + High'],
    ['Pending Priority', stats.pending, 'Awaiting assignment'],
    ['Marked as Critical', stats.markedCritical, 'User-marked'],
    ['Unassigned Tickets', stats.unassigned, '']
  ];
}

function generateNotesReport() {
  const data = db.readDb();
  return data.notes.map(n => {
    let ts = n.timestamp;
    try { ts = utils.formatDate(new Date(ts)); } catch (e) {}
    return [n.ticketId || '', ts, n.noteType || '', n.addedBy || '', n.note || ''];
  });
}

function generateReport(reportType) {
  try {
    const tickets = utils.getAllTickets();
    const now = new Date();
    let reportData, headers, reportTitle;
    switch (reportType) {
      case 'tickets':
        reportData = generateTicketsReport(tickets);
        headers = ['Ticket ID', 'Created Date', 'Name', 'Email', 'Phone', 'Location', 'Issue Type', 'Impact Area', 'Description', 'Status', 'Priority', 'VIP Level', 'Marked Critical', 'Assigned To', 'Escalation Level', 'Resolved By', 'Resolved Date'];
        reportTitle = 'All_Tickets_Report';
        break;
      case 'team':
        reportData = generateTeamReport();
        headers = ['Staff Name', 'Level', 'Status', 'Tickets Handled', 'Resolved', 'Open', 'SLA Compliance %', 'Avg Resolution Time'];
        reportTitle = 'Team_Performance_Report';
        break;
      case 'sla':
        reportData = generateSLAReport(tickets);
        headers = ['Ticket ID', 'Created Date', 'Priority', 'Status', 'Assigned To', 'Response Time (hrs)', 'Resolution Time (hrs)', 'SLA Status', 'SLA Threshold (hrs)'];
        reportTitle = 'SLA_Compliance_Report';
        break;
      case 'monthly':
        reportData = generateMonthlyReport(tickets);
        headers = ['Metric', 'Value', 'Details'];
        reportTitle = 'Monthly_Summary_Report';
        break;
      case 'notes':
        reportData = generateNotesReport();
        headers = ['Ticket ID', 'Timestamp', 'Note Type', 'Added By', 'Note'];
        reportTitle = 'Ticket_Notes_Report';
        break;
      default:
        return { success: false, error: 'Invalid report type' };
    }
    const csvContent = utils.generateCSVContent(headers, reportData);
    const dateStr = now.getFullYear() + ('0' + (now.getMonth() + 1)).slice(-2) + ('0' + now.getDate()).slice(-2) + '_' + ('0' + now.getHours()).slice(-2) + ('0' + now.getMinutes()).slice(-2) + ('0' + now.getSeconds()).slice(-2);
    const fileName = reportTitle + '_' + dateStr + '.csv';
    return { success: true, fileName, csvContent, recordCount: reportData.length, reportType, generatedAt: now.toISOString() };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

module.exports = { getTeamPerformance, getSlaSummary, generateReport };
