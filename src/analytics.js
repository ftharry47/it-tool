const db = require('./db');
const utils = require('./utils');
const sla = require('./sla');
const config = require('./config');

function hoursBetween(from, to) {
  if (!from || !to) return null;
  try {
    const diff = new Date(to) - new Date(from);
    if (isNaN(diff)) return null;
    return Math.round((diff / (1000 * 60 * 60)) * 10) / 10;
  } catch (e) { return null; }
}

function dateBucket(iso) {
  if (!iso) return null;
  try {
    return new Date(iso).toISOString().slice(0, 10);
  } catch (e) { return null; }
}

function getAnalytics() {
  const d = db.readDb();
  const tickets = utils.getAllTickets();
  const now = new Date();

  const stats = {
    total: tickets.length,
    open: 0, inProgress: 0, onHold: 0, differ: 0, resolved: 0,
    slaOnTrack: 0, slaAtRisk: 0, slaBreached: 0,
    resolvedToday: 0, createdToday: 0,
    avgResolutionHours: 0,
    satisfactionAvg: 0,
    satisfactionCount: 0,
    byStatus: {},
    byPriority: {},
    byIssueType: {},
    byImpactArea: {},
    byAssignee: {},
    byLocation: {},
    topIssueTypes: [],
    topAssignees: [],
    resolutionTimes: [],
    createdTrend: {},
    resolvedTrend: {},
    last30Days: []
  };

  const buckets = new Map();
  for (let i = 29; i >= 0; i--) {
    const dt = new Date();
    dt.setDate(dt.getDate() - i);
    const key = dt.toISOString().slice(0, 10);
    buckets.set(key, { date: key, created: 0, resolved: 0 });
  }

  const resTimes = [];
  const satRatings = [];

  tickets.forEach(t => {
    sla.updateTicketSLAFields(t);
    const status = String(t['Status'] || 'Open');
    const priority = String(t['Priority'] || 'Pending');
    const issueType = String(t['Issue Type'] || 'Unknown');
    const impactArea = String(t['Impact Area'] || 'Unknown');
    const assignee = String(t['Assigned To'] || 'Unassigned');
    const location = String(t['Location'] || 'Unknown');
    const slaStatus = String(t['SLA Status'] || 'On Track');

    stats.byStatus[status] = (stats.byStatus[status] || 0) + 1;
    stats.byPriority[priority] = (stats.byPriority[priority] || 0) + 1;
    stats.byIssueType[issueType] = (stats.byIssueType[issueType] || 0) + 1;
    stats.byImpactArea[impactArea] = (stats.byImpactArea[impactArea] || 0) + 1;
    stats.byAssignee[assignee] = (stats.byAssignee[assignee] || 0) + 1;
    stats.byLocation[location] = (stats.byLocation[location] || 0) + 1;

    if (status.toLowerCase() === 'open') stats.open++;
    else if (status.toLowerCase() === 'in progress') stats.inProgress++;
    else if (status.toLowerCase() === 'on hold') stats.onHold++;
    else if (status.toLowerCase() === 'differ') stats.differ++;
    else if (status.toLowerCase() === 'resolved') stats.resolved++;

    if (slaStatus === 'Breached') stats.slaBreached++;
    else if (slaStatus === 'At Risk') stats.slaAtRisk++;
    else if (slaStatus !== 'Resolved') stats.slaOnTrack++;

    const createdDate = t['Created Date'];
    const resolvedDate = t['Resolved Date'];
    if (dateBucket(createdDate) === now.toISOString().slice(0, 10)) stats.createdToday++;
    if (dateBucket(resolvedDate) === now.toISOString().slice(0, 10)) stats.resolvedToday++;

    const cBucket = dateBucket(createdDate);
    if (cBucket && buckets.has(cBucket)) buckets.get(cBucket).created++;
    const rBucket = dateBucket(resolvedDate);
    if (rBucket && buckets.has(rBucket)) buckets.get(rBucket).resolved++;

    if (status.toLowerCase() === 'resolved' && createdDate && resolvedDate) {
      const hours = hoursBetween(createdDate, resolvedDate);
      if (hours !== null) resTimes.push(hours);
    }

    const rating = parseFloat(t['Satisfaction']);
    if (!isNaN(rating) && rating > 0) satRatings.push(rating);
  });

  if (resTimes.length) {
    const total = resTimes.reduce((a, b) => a + b, 0);
    stats.avgResolutionHours = Math.round((total / resTimes.length) * 10) / 10;
    stats.resolutionTimes = resTimes.slice(0, 100);
  }

  if (satRatings.length) {
    const total = satRatings.reduce((a, b) => a + b, 0);
    stats.satisfactionAvg = Math.round((total / satRatings.length) * 10) / 10;
    stats.satisfactionCount = satRatings.length;
  }

  stats.topIssueTypes = Object.entries(stats.byIssueType).sort((a, b) => b[1] - a[1]).slice(0, 10);
  stats.topAssignees = Object.entries(stats.byAssignee).filter(([k]) => k !== 'Unassigned').sort((a, b) => b[1] - a[1]).slice(0, 10);

  stats.last30Days = Array.from(buckets.values()).sort((a, b) => a.date.localeCompare(b.date));

  return { success: true, stats };
}

module.exports = { getAnalytics };
