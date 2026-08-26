const db = require('../db');
const utils = require('../utils');

function generateProjectId(data) {
  const d = data || db.readDb();
  let highest = 0;
  (d.projects || []).forEach(p => {
    const id = String(p.id || '');
    const match = id.match(/PRJ(\d+)/);
    if (match) {
      const n = parseInt(match[1], 10);
      if (!isNaN(n) && n > highest) highest = n;
    }
  });
  return 'PRJ' + String(highest + 1).padStart(5, '0');
}

function getProjects() {
  const d = db.readDb();
  return { success: true, projects: Array.isArray(d.projects) ? d.projects : [] };
}

function createProject(data) {
  if (!data || !data.name) return { success: false, error: 'Project name is required' };
  return db.withDb(d => {
    if (!Array.isArray(d.projects)) d.projects = [];
    const project = {
      id: data.id || generateProjectId(d),
      name: String(data.name).trim(),
      description: String(data.description || '').trim(),
      owner: String(data.owner || '').trim(),
      status: data.status || 'Active',
      createdAt: new Date().toISOString()
    };
    d.projects.push(project);
    return { success: true, project };
  });
}

function updateProject(id, data) {
  if (!id) return { success: false, error: 'Project ID is required' };
  return db.withDb(d => {
    const p = (d.projects || []).find(x => x.id === id);
    if (!p) return { success: false, error: 'Project not found' };
    if (data.name) p.name = String(data.name).trim();
    if (data.description !== undefined) p.description = String(data.description).trim();
    if (data.owner !== undefined) p.owner = String(data.owner).trim();
    if (data.status) p.status = data.status;
    return { success: true, project: p };
  });
}

function deleteProject(id) {
  if (!id) return { success: false, error: 'Project ID is required' };
  return db.withDb(d => {
    const idx = (d.projects || []).findIndex(x => x.id === id);
    if (idx === -1) return { success: false, error: 'Project not found' };
    d.projects.splice(idx, 1);
    (d.tickets || []).forEach(t => { if (t['Project'] === id) t['Project'] = ''; });
    return { success: true, message: 'Project deleted' };
  });
}

function getIssueClasses() {
  const d = db.readDb();
  return { success: true, issueClasses: Array.isArray(d.issueTypes) ? d.issueTypes : [] };
}

function setTicketProject(ticketId, projectId, updatedBy) {
  if (!ticketId) return { success: false, error: 'Ticket ID is required' };
  return db.withDb(d => {
    const t = utils.findTicket(d, ticketId);
    if (!t) return { success: false, error: 'Ticket not found' };
    const old = t['Project'] || '';
    const project = (d.projects || []).find(p => p.id === projectId);
    const name = project ? project.name : '';
    t['Project'] = projectId || '';
    t['Last Updated'] = new Date().toISOString();
    require('./tickets').logHistory(d, ticketId, 'Project Changed', old, t['Project'], updatedBy || 'Dashboard User', `Project changed by ${updatedBy || 'Dashboard User'} to ${name}`);
    return { success: true, ticketId, project: t['Project'] };
  });
}

function setTicketIssueClass(ticketId, issueClassId, updatedBy) {
  if (!ticketId) return { success: false, error: 'Ticket ID is required' };
  return db.withDb(d => {
    const t = utils.findTicket(d, ticketId);
    if (!t) return { success: false, error: 'Ticket not found' };
    const issueClass = (d.issueTypes || []).find(it => it.id === issueClassId || it.name === issueClassId);
    const old = t['Issue Class'] || '';
    const newValue = issueClass ? issueClass.name : (issueClassId || '');
    t['Issue Class'] = newValue;
    t['Last Updated'] = new Date().toISOString();
    require('./tickets').logHistory(d, ticketId, 'Issue Class Changed', old, t['Issue Class'], updatedBy || 'Dashboard User', `Issue class changed by ${updatedBy || 'Dashboard User'} to ${newValue}`);
    return { success: true, ticketId, issueClass: t['Issue Class'] };
  });
}

module.exports = {
  getProjects,
  createProject,
  updateProject,
  deleteProject,
  getIssueClasses,
  setTicketProject,
  setTicketIssueClass
};
