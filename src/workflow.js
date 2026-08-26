const db = require('./db');
const config = require('./config');
const sla = require('./sla');

const DEFAULT_WORKFLOW_ID = 'itsm-default';

const DEFAULT_WORKFLOW = {
  id: DEFAULT_WORKFLOW_ID,
  name: 'Default ITSM Workflow',
  description: 'Open → In Progress → Resolved with On Hold and Differ as intermediate states.',
  states: ['Open', 'In Progress', 'On Hold', 'Differ', 'Resolved'],
  initial: 'Open',
  transitions: [
    { id: 't1', from: 'Open', to: 'In Progress', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Start work' },
    { id: 't2', from: 'In Progress', to: 'On Hold', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Place on hold' },
    { id: 't3', from: 'On Hold', to: 'In Progress', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Resume work' },
    { id: 't4', from: 'In Progress', to: 'Differ', allowedRoles: ['Admin', 'L2', 'L3'], conditions: [], name: 'Differ/Waiting' },
    { id: 't5', from: 'Differ', to: 'In Progress', allowedRoles: ['Admin', 'L2', 'L3'], conditions: [], name: 'Resume from differ' },
    { id: 't6', from: 'Open', to: 'Resolved', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Resolve without work' },
    { id: 't7', from: 'In Progress', to: 'Resolved', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Resolve' },
    { id: 't8', from: 'On Hold', to: 'Resolved', allowedRoles: ['Admin', 'L2', 'L3'], conditions: [], name: 'Resolve from hold' },
    { id: 't9', from: 'Differ', to: 'Resolved', allowedRoles: ['Admin', 'L2', 'L3'], conditions: [], name: 'Resolve from differ' },
    { id: 't10', from: 'Resolved', to: 'Open', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Reopen' },
    { id: 't11', from: 'Resolved', to: 'In Progress', allowedRoles: ['Admin', 'L1', 'L2', 'L3'], conditions: [], name: 'Reopen to in progress' }
  ]
};

function ensureWorkflows() {
  return db.withDb(d => {
    if (!Array.isArray(d.workflows)) d.workflows = [];
    if (!d.workflows.find(w => w.id === DEFAULT_WORKFLOW_ID)) {
      d.workflows.push(DEFAULT_WORKFLOW);
    }
    if (!Array.isArray(d.cmdb)) d.cmdb = [];
    if (!d.cmdbTypes) d.cmdbTypes = ['Workstation', 'Server', 'Network', 'Application', 'Service', 'Peripheral'];
    if (!Array.isArray(d.ticketCIs)) d.ticketCIs = [];
    if (!Array.isArray(d.projects)) d.projects = [];
    if (d.projects.length === 0) {
      d.projects.push(
        { id: 'PRJ00001', name: 'IT Support', description: 'General IT support and service requests', owner: 'IT', status: 'Active', createdAt: new Date().toISOString() },
        { id: 'PRJ00002', name: 'Infrastructure', description: 'Servers, network and cloud operations', owner: 'IT', status: 'Active', createdAt: new Date().toISOString() },
        { id: 'PRJ00003', name: 'Software Development', description: 'Internal applications and integrations', owner: 'Engineering', status: 'Active', createdAt: new Date().toISOString() }
      );
    }
    if (!Array.isArray(d.issueTypes)) {
      d.issueTypes = [
        { id: 'incident', name: 'Incident', workflowId: DEFAULT_WORKFLOW_ID, icon: 'alert-triangle' },
        { id: 'problem', name: 'Problem', workflowId: DEFAULT_WORKFLOW_ID, icon: 'search' },
        { id: 'bug', name: 'Bug', workflowId: DEFAULT_WORKFLOW_ID, icon: 'bug' },
        { id: 'story', name: 'Story', workflowId: DEFAULT_WORKFLOW_ID, icon: 'book-open' },
        { id: 'epic', name: 'Epic', workflowId: DEFAULT_WORKFLOW_ID, icon: 'layers' }
      ];
    }
    if (!Array.isArray(d.permissions)) {
      d.permissions = [
        { role: 'Viewer', resources: ['tickets.read', 'tickets.track'] },
        { role: 'L1', resources: ['tickets.read', 'tickets.update.status', 'tickets.update.assign', 'tickets.update.note', 'cmdb.read'] },
        { role: 'L2', resources: ['tickets.*', 'cmdb.*', 'workflows.read'] },
        { role: 'L3', resources: ['tickets.*', 'cmdb.*', 'workflows.*', 'users.*', 'settings.*'] },
        { role: 'Admin', resources: ['*'] }
      ];
    }
    return { success: true, message: 'Workflows and CMDB collections ensured' };
  });
}

function getWorkflows() {
  const d = db.readDb();
  return Array.isArray(d.workflows) ? d.workflows : [];
}

function getWorkflowById(id) {
  const d = db.readDb();
  return (d.workflows || []).find(w => w.id === id) || DEFAULT_WORKFLOW;
}

function getWorkflowForTicket(ticket) {
  const d = db.readDb();
  const issueType = ticket && (ticket.issueTypeId || ticket['Issue Type']);
  const mapped = (d.issueTypes || []).find(it => it.name === issueType || it.id === issueType);
  if (mapped && mapped.workflowId) return getWorkflowById(mapped.workflowId);
  return getWorkflowById(DEFAULT_WORKFLOW_ID);
}

function getAllowedTransitions(ticket, role) {
  const workflow = getWorkflowForTicket(ticket);
  const current = ticket['Status'] || ticket.status || workflow.initial;
  return workflow.transitions.filter(t => t.from === current && roleAllowed(t.allowedRoles, role));
}

function roleAllowed(allowedRoles, role) {
  if (!allowedRoles || allowedRoles.length === 0) return true;
  if (!role) return false;
  return allowedRoles.includes(role);
}

function canTransition(ticket, newStatus, role) {
  const allowed = getAllowedTransitions(ticket, role);
  return allowed.some(t => t.to === newStatus);
}

function evaluateConditions(ticket, transition, context) {
  if (!transition.conditions || transition.conditions.length === 0) return { ok: true };
  for (const cond of transition.conditions) {
    if (cond.type === 'assigned') {
      const assigned = ticket['Assigned To'] || ticket.assignedTo;
      if (!assigned) return { ok: false, reason: 'Ticket must be assigned before this transition' };
    }
    if (cond.type === 'note') {
      const notes = (db.readDb().notes || []).filter(n => n.ticketId === (ticket['Ticket ID'] || ticket.id));
      if (notes.length === 0) return { ok: false, reason: 'A note is required before this transition' };
    }
    if (cond.type === 'field' && cond.field) {
      const val = ticket[cond.field] || '';
      if (!val) return { ok: false, reason: cond.message || `Field ${cond.field} is required` };
    }
  }
  return { ok: true };
}

function transitionTicket(ticketId, newStatus, role, performedBy, context = {}) {
  if (!ticketId) return { success: false, error: 'Ticket ID is required' };
  if (!newStatus) return { success: false, error: 'New status is required' };

  const utils = require('./utils');
  return db.withDb(d => {
    const t = utils.findTicket(d, ticketId);
    if (!t) return { success: false, error: 'Ticket not found' };
    if (!canTransition(t, newStatus, role)) {
      return { success: false, error: `Transition to ${newStatus} is not allowed for role ${role} from state ${t['Status']}` };
    }
    const workflow = getWorkflowForTicket(t);
    const transition = workflow.transitions.find(tr => tr.from === t['Status'] && tr.to === newStatus && roleAllowed(tr.allowedRoles, role));
    if (transition) {
      const cond = evaluateConditions(t, transition, context);
      if (!cond.ok) return { success: false, error: cond.reason };
    }

    const now = new Date().toISOString();
    const oldStatus = t['Status'] || 'Open';
    t['Status'] = newStatus;
    t['Last Updated'] = now;
    if (newStatus === 'Resolved') {
      t['Resolved By'] = performedBy || t['Assigned To'] || 'IT Support';
      t['Resolved Date'] = now;
    } else if (oldStatus === 'Resolved') {
      t['Resolved By'] = '';
      t['Resolved Date'] = '';
    }
    sla.updateTicketSLAFields(t);

    const history = require('./handlers/tickets').logHistory;
    if (typeof history === 'function') {
      history(d, ticketId, oldStatus === 'Resolved' ? 'Reopened' : 'Status Changed', oldStatus, newStatus, performedBy || 'Dashboard User', `Transitioned via workflow by ${performedBy || 'Dashboard User'}`);
    } else {
      d.history.push({
        timestamp: now,
        ticketId: String(ticketId),
        action: oldStatus === 'Resolved' ? 'Reopened' : 'Status Changed',
        from: oldStatus,
        to: newStatus,
        performedBy: performedBy || 'Dashboard User',
        notes: `Transitioned via workflow by ${performedBy || 'Dashboard User'}`
      });
    }

    return { success: true, ticketId, oldStatus, newStatus, message: `Status changed to ${newStatus}`, updatedDate: now };
  });
}

function getIssueTypes() {
  const d = db.readDb();
  return Array.isArray(d.issueTypes) ? d.issueTypes : [];
}

function getAllRolePermissions() {
  const d = db.readDb();
  return { success: true, permissions: d.permissions || [] };
}

function getRolePermissions(role) {
  const d = db.readDb();
  const perms = (d.permissions || []).find(p => p.role === role);
  return perms ? perms.resources : [];
}

function setRolePermissions(role, resources) {
  if (!role || !Array.isArray(resources)) return { success: false, error: 'Role and resources array are required' };
  return db.withDb(d => {
    if (!Array.isArray(d.permissions)) d.permissions = [];
    const idx = d.permissions.findIndex(p => p.role === role);
    const entry = { role, resources: resources.map(String) };
    if (idx >= 0) d.permissions[idx] = entry;
    else d.permissions.push(entry);
    return { success: true, permissions: d.permissions };
  });
}

function hasPermission(role, resource) {
  const resources = getRolePermissions(role);
  if (resources.includes('*')) return true;
  if (resources.includes(resource)) return true;
  const wildcard = resources.find(r => r.endsWith('.*') && resource.indexOf(r.replace('.*', '')) === 0);
  return !!wildcard;
}

module.exports = {
  DEFAULT_WORKFLOW,
  ensureWorkflows,
  getWorkflows,
  getWorkflowById,
  getWorkflowForTicket,
  getAllowedTransitions,
  canTransition,
  transitionTicket,
  getIssueTypes,
  getAllRolePermissions,
  getRolePermissions,
  setRolePermissions,
  hasPermission
};
