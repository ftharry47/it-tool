const core = require('./core');
const tickets = require('./tickets');
const reports = require('./reports');
const utils = require('../utils');

module.exports = {
  ...core,
  ...tickets,
  ...reports,
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
  testEmailConfiguration: () => ({ success: true, message: 'Email configured via SMTP env vars' }),
  diagnoseSystem: core.getSystemStatus
};
