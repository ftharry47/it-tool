const core = require('./core');
const tickets = require('./tickets');
const reports = require('./reports');
const workflow = require('./workflow');
const cmdb = require('./cmdb');
const projects = require('./projects');
const utils = require('../utils');
const email = require('../email');
const audit = require('../audit');
const importExport = require('../import-export');
const analytics = require('../analytics');

module.exports = {
  ...core,
  ...tickets,
  ...reports,
  ...workflow,
  ...cmdb,
  ...projects,
  getAllSettings: utils.getAllSettings,
  getSetting: utils.getSetting,
  setSetting: utils.setSetting,
  getAutoAssignSetting: utils.getAutoAssignSetting,
  getAdminEmails: d => utils.getAdminEmails(d || require('../db').readDb()),
  testSetup: core.setupSystem,
  testGetSpreadsheet: () => ({ success: true, name: 'JSON DB', id: 'json-db' }),
  testGetAllTickets: utils.getAllTickets,
  testGetDashboardData: tickets.getDashboardData,
  testValidateUser: core.validateUser,
  testLookupEmployee: core.lookupEmployee,
  testLookupEmployeeSafe: core.lookupEmployeeSafe,
  testSubmitTicket: tickets.submitTicket,
  testUpdatePriority: tickets.updateTicketPriority,
  testAssignWithPriority: tickets.assignTicket,
  testGenerateReport: reports.generateReport,
  testAddNote: tickets.addTicketNote,
  testGetNotes: tickets.getTicketNotes,
  testGetTimeline: tickets.getTicketTimeline,
  testUpdateIssueType: tickets.updateIssueType,
  testUpdateImpactArea: tickets.updateImpactArea,
  testUpdatePhoneNumber: tickets.updatePhoneNumber,
  testUpdateLocation: tickets.updateLocation,
  recordSatisfaction: tickets.recordSatisfaction,
  testEmailConfiguration: () => email.verifyEmail(),
  verifySMTP: () => email.verifySMTP(),
  verifyGraph: () => email.verifyGraph(),
  verifyEmail: () => email.verifyEmail(),
  diagnoseSystem: core.getSystemStatus,
  addDirectory: core.addDirectory,
  bulkImportDirectory: core.bulkImportDirectory,
  getAuditLogs: audit.getAuditLogs,
  checkSLABreaches: () => require('../sla').checkSLABreaches(),
  getSLAStats: () => require('../sla').getSLAStats(),
  exportTickets: importExport.exportTickets,
  exportUsers: importExport.exportUsers,
  importTickets: importExport.importTickets,
  importUsers: importExport.importUsers,
  getAnalytics: analytics.getAnalytics
};
