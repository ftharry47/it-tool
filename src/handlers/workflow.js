const workflow = require('../workflow');
const db = require('../db');
const utils = require('../utils');

function getWorkflows() {
  return { success: true, workflows: workflow.getWorkflows() };
}

function getWorkflowById(id) {
  if (!id) return { success: false, error: 'Workflow ID is required' };
  const w = workflow.getWorkflowById(id);
  return { success: true, workflow: w };
}

function getIssueTypes() {
  return { success: true, issueTypes: workflow.getIssueTypes() };
}

function getAllowedTransitions(ticketId, role) {
  if (!ticketId) return { success: false, error: 'Ticket ID is required' };
  const d = db.readDb();
  const t = utils.findTicket(d, ticketId);
  if (!t) return { success: false, error: 'Ticket not found' };
  return { success: true, current: t['Status'], transitions: workflow.getAllowedTransitions(t, role || 'Viewer') };
}

function transitionIssue(ticketId, newStatus, user) {
  if (!ticketId || !newStatus || !user) return { success: false, error: 'Ticket ID, new status and user are required' };
  const role = user.role || 'Viewer';
  return workflow.transitionTicket(ticketId, newStatus, role, user.displayName || user.name || user.email);
}

function getAllRolePermissions() {
  return workflow.getAllRolePermissions();
}

function getRolePermissions(role) {
  if (!role) return { success: false, error: 'Role is required' };
  return { success: true, role, permissions: workflow.getRolePermissions(role) };
}

function setRolePermissions(role, resources) {
  return workflow.setRolePermissions(role, resources);
}

function checkPermission(role, resource) {
  if (!role || !resource) return { success: false, error: 'Role and resource are required' };
  return { success: true, allowed: workflow.hasPermission(role, resource) };
}

module.exports = {
  getWorkflows,
  getWorkflowById,
  getIssueTypes,
  getAllowedTransitions,
  transitionIssue,
  getAllRolePermissions,
  getRolePermissions,
  setRolePermissions,
  checkPermission
};
