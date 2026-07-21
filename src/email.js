const nodemailer = require('nodemailer');
const config = require('./config');
const db = require('./db');
const utils = require('./utils');

function getTransporter() {
  const host = process.env.SMTP_HOST;
  const port = process.env.SMTP_PORT;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (!host || !port || !user || !pass) {
    return null;
  }

  return nodemailer.createTransport({
    host,
    port: Number(port),
    secure: Number(port) === 465,
    auth: { user, pass }
  });
}

function isDryRun() {
  const setting = utils.getSetting('DRY_RUN');
  if (setting !== null) return !!setting;
  return config.DRY_RUN || process.env.DRY_RUN === 'true';
}

function getAdminEmails() {
  const settings = db.readDb().settings;
  const get = key => {
    const s = settings[key];
    return s ? s.value : config.ADMIN_EMAILS[key.replace('_EMAIL', '').replace('_ADMIN', '')] || '';
  };
  return {
    primary: get('PRIMARY_ADMIN_EMAIL') || config.ADMIN_EMAILS.PRIMARY,
    secondary: get('SECONDARY_ADMIN_EMAIL') || config.ADMIN_EMAILS.SECONDARY,
    tertiary: get('TERTIARY_ADMIN_EMAIL') || config.ADMIN_EMAILS.TERTIARY,
    escalation: get('ESCALATION_EMAIL') || config.ADMIN_EMAILS.ESCALATION,
    critical: get('CRITICAL_EMAIL') || config.ADMIN_EMAILS.CRITICAL
  };
}

async function sendEmail(to, subject, body, cc) {
  if (isDryRun()) {
    console.log('DRY_RUN: Email skipped to', to);
    console.log('Subject:', subject);
    return true;
  }

  const recipients = Array.isArray(to) ? to.filter(e => e && String(e).trim() !== '') : [to].filter(e => e && String(e).trim() !== '');
  if (recipients.length === 0) {
    console.log('No valid email recipients');
    return false;
  }

  const transporter = getTransporter();
  if (!transporter) {
    console.log('No SMTP configured; email not sent:', subject);
    return false;
  }

  const mailOptions = {
    from: process.env.EMAIL_FROM || config.ADMIN_EMAILS.PRIMARY,
    to: recipients.join(','),
    subject,
    text: body,
    html: `<div style="font-family:Verdana,Arial,sans-serif;font-size:14px;line-height:1.6;color:#333333;">${body.replace(/\n/g, '<br>')}</div>`
  };

  if (cc && cc.length > 0) {
    const validCC = cc.filter(e => e && String(e).trim() !== '');
    if (validCC.length > 0) mailOptions.cc = validCC.join(',');
  }

  try {
    await transporter.sendMail(mailOptions);
    console.log('Email sent to:', recipients.join(', '));
    return true;
  } catch (e) {
    console.error('Error sending email:', e.message);
    return false;
  }
}

function formatDate(date) {
  try {
    return new Date(date).toLocaleString('en-US');
  } catch (e) {
    return String(date);
  }
}

async function sendTicketSubmittedEmail(ticketId, timestamp, userName, formData, vipLevel, issueType, impactArea, criticalFlag) {
  const subject = 'Ticket Submitted: ' + ticketId + ' - ' + (formData.shortDescription || '').substring(0, 50);
  let body = 'Dear ' + userName + ',\n\n';
  body += 'Thank you for submitting your IT support request.\n\n';
  body += 'Ticket Details:\n';
  body += '--------------------------------------------------\n';
  body += 'Ticket ID: ' + ticketId + '\n';
  body += 'Submitted: ' + formatDate(timestamp) + '\n';
  body += 'Location: ' + formData.location + '\n';
  body += 'Phone: ' + formData.phone + '\n';
  if (formData.temporaryEmail) body += 'Temporary Email: ' + formData.temporaryEmail + '\n';
  if (issueType) body += 'Issue Type: ' + issueType + '\n';
  if (impactArea) body += 'Impact Area: ' + impactArea + '\n';
  if (criticalFlag) body += 'Note: Marked as Critical\n';
  body += '\nDescription:\n' + formData.shortDescription + '\n';
  if (formData.additionalDescription) body += '\nAdditional Details:\n' + formData.additionalDescription + '\n';
  body += '--------------------------------------------------\n\n';
  body += 'An IT staff member will be assigned shortly.\n\n';
  body += 'Best regards,\nIT Support Team\n' + config.ADMIN_EMAILS.PRIMARY;

  return sendEmail([formData.email], subject, body);
}

async function sendNewTicketNotificationToIT(ticketId, timestamp, employeeLookup, userName, formData, priority, vipLevel, issueType, impactArea, criticalFlag) {
  let urgencyTag = '';
  if (criticalFlag) urgencyTag = ' [MARKED CRITICAL]';
  else if (impactArea === 'System Outage') urgencyTag = ' [SYSTEM OUTAGE]';
  else if (impactArea === 'Security / Access') urgencyTag = ' [SECURITY]';

  const subject = 'NEW IT Ticket [VIP:' + vipLevel + ']' + urgencyTag + ': ' + ticketId;
  let body = 'A new IT ticket has been submitted.\n\n';
  body += 'Ticket Information:\n';
  body += '--------------------------------------------------\n';
  body += 'Ticket ID: ' + ticketId + '\n';
  body += 'Priority: ' + priority + ' (Awaiting assignment)\n';
  body += 'VIP Level: ' + vipLevel + '\n';
  if (criticalFlag) body += 'User Marked as: CRITICAL\n';
  if (impactArea) body += 'Impact Area: ' + impactArea + '\n';
  body += 'Submitted: ' + formatDate(timestamp) + '\n\n';
  body += 'Employee Details:\n';
  body += 'Employee ID: ' + (employeeLookup.empId || 'Not in directory') + '\n';
  body += 'Name: ' + userName + '\n';
  body += 'Email: ' + formData.email + '\n';
  if (formData.temporaryEmail) body += 'Temporary Email: ' + formData.temporaryEmail + '\n';
  body += 'Phone: ' + formData.phone + '\n';
  body += 'Location: ' + formData.location + '\n';
  body += '\nIssue Details:\n';
  if (issueType) body += 'Issue Type: ' + issueType + '\n';
  body += 'Description:\n' + formData.shortDescription + '\n';
  if (formData.additionalDescription) body += '\nAdditional Details:\n' + formData.additionalDescription + '\n';
  body += '--------------------------------------------------\n\n';
  body += 'Please assign this ticket and set the appropriate priority.\n\n';
  body += 'IT Support System';

  const adminEmails = getAdminEmails();
  const recipients = [adminEmails.primary, adminEmails.secondary, adminEmails.tertiary].filter(e => e && e.trim() !== '');
  if (criticalFlag && adminEmails.critical && recipients.indexOf(adminEmails.critical) === -1) recipients.push(adminEmails.critical);
  const uniqueRecipients = [];
  recipients.forEach(e => { if (uniqueRecipients.indexOf(e) === -1) uniqueRecipients.push(e); });

  return sendEmail(uniqueRecipients, subject, body);
}

async function sendTicketAssignedEmailToUser(ticketId, userName, assignedTo, ticket) {
  const shortDesc = ticket['Short Description'] || '';
  const userEmail = ticket['Email Address'];
  if (!userEmail) return;
  const subject = 'Ticket Assigned: ' + ticketId + ' - Now with ' + assignedTo;
  const body = 'Dear ' + userName + ',\n\nYour IT support ticket has been assigned.\n\nAssignment Details:\n--------------------------------------------------\nTicket ID: ' + ticketId + '\nDescription: ' + shortDesc + '\nAssigned To: ' + assignedTo + '\nStatus: In Progress\n--------------------------------------------------\n\n' + assignedTo + ' will work on your request.\n\nBest regards,\nIT Support Team';
  return sendEmail([userEmail], subject, body);
}

async function sendTicketAssignedEmailToStaff(ticketId, assignedTo, assignedBy, ticket) {
  const staff = db.readDb().itStaff.find(s => s.name === assignedTo);
  if (!staff || !staff.email) return;

  const userName = ticket['Name'] || '';
  const userEmail = ticket['Email Address'] || '';
  const userPhone = ticket['Phone Number'] || '';
  const location = ticket['Location'] || '';
  const issueType = ticket['Issue Type'] || '';
  const impactArea = ticket['Impact Area'] || '';
  const shortDesc = ticket['Short Description'] || '';
  const additionalDesc = ticket['Additional Description'] || '';
  const priority = ticket['Priority'] || 'Pending';
  const vipLevel = ticket['VIP Level'] || '';
  const criticalFlag = ticket['Critical Flag'] === 'true';

  let body = 'Hello ' + assignedTo + ',\n\n';
  body += 'You have been assigned a ticket by ' + assignedBy + '.\n\n';
  body += 'Ticket Details:\n--------------------------------------------------\n';
  body += 'Ticket ID: ' + ticketId + '\n';
  body += 'Priority: ' + priority + ' (VIP Level: ' + vipLevel + ')\n';
  if (criticalFlag) body += 'User Marked as: CRITICAL\n';
  if (impactArea) body += 'Impact Area: ' + impactArea + '\n';
  body += '\nUser Information:\n';
  body += 'Name: ' + userName + '\n';
  body += 'Email: ' + userEmail + '\n';
  body += 'Phone: ' + userPhone + '\n';
  body += 'Location: ' + location + '\n';
  body += '\nIssue:\n';
  if (issueType) body += 'Issue Type: ' + issueType + '\n';
  body += 'Description: ' + shortDesc + '\n';
  if (additionalDesc) body += 'Additional Details: ' + additionalDesc + '\n';
  body += '--------------------------------------------------\n\n';
  body += 'Please review and work on this ticket.\n\nIT Support System';

  const subject = 'Ticket Assigned to You [' + priority + ']: ' + ticketId;
  return sendEmail([staff.email], subject, body);
}

async function sendTicketEscalatedEmailToUser(ticketId, userName, escalateTo, escalationLevel, reason, ticket) {
  const shortDesc = ticket['Short Description'] || '';
  const userEmail = ticket['Email Address'];
  if (!userEmail) return;
  const subject = 'Ticket Escalated: ' + ticketId + ' - Now with ' + escalationLevel + ' Support';
  const body = 'Dear ' + userName + ',\n\nYour IT ticket has been escalated for faster resolution.\n\nEscalation Details:\n--------------------------------------------------\nTicket ID: ' + ticketId + '\nDescription: ' + shortDesc + '\nEscalation Level: ' + escalationLevel + '\nAssigned To: ' + escalateTo + '\n' + (reason ? 'Reason: ' + reason : '') + '\n--------------------------------------------------\n\n' + escalateTo + ' from ' + escalationLevel + ' will handle your request.\n\nBest regards,\nIT Support Team';
  return sendEmail([userEmail], subject, body);
}

async function sendTicketEscalatedEmailToStaff(ticketId, escalateTo, escalationLevel, escalatedBy, reason, ticket) {
  const staff = db.readDb().itStaff.find(s => s.name === escalateTo);
  if (!staff || !staff.email) return;

  const userName = ticket['Name'] || '';
  const userEmail = ticket['Email Address'] || '';
  const userPhone = ticket['Phone Number'] || '';
  const location = ticket['Location'] || '';
  const issueType = ticket['Issue Type'] || '';
  const impactArea = ticket['Impact Area'] || '';
  const shortDesc = ticket['Short Description'] || '';
  const additionalDesc = ticket['Additional Description'] || '';
  const priority = ticket['Priority'] || 'Pending';
  const vipLevel = ticket['VIP Level'] || '';
  const criticalFlag = ticket['Critical Flag'] === 'true';

  let body = 'Hello ' + escalateTo + ',\n\n';
  body += 'A ticket has been escalated to you by ' + escalatedBy + '.\n\n';
  body += 'Escalation Information:\n--------------------------------------------------\n';
  body += 'Ticket ID: ' + ticketId + '\n';
  body += 'Priority: ' + priority + ' (VIP Level: ' + vipLevel + ')\n';
  body += 'Escalation Level: ' + escalationLevel + '\n';
  if (criticalFlag) body += 'User Marked as: CRITICAL\n';
  if (reason) body += 'Escalation Reason: ' + reason + '\n';
  if (impactArea) body += 'Impact Area: ' + impactArea + '\n';
  body += '\nUser Information:\n';
  body += 'Name: ' + userName + '\n';
  body += 'Email: ' + userEmail + '\n';
  body += 'Phone: ' + userPhone + '\n';
  body += 'Location: ' + location + '\n';
  body += '\nIssue:\n';
  if (issueType) body += 'Issue Type: ' + issueType + '\n';
  body += 'Description: ' + shortDesc + '\n';
  if (additionalDesc) body += 'Additional Details: ' + additionalDesc + '\n';
  body += '--------------------------------------------------\n\n';
  body += 'Please prioritize this ticket.\n\nIT Support System';

  const subject = 'ESCALATED Ticket [' + escalationLevel + '] [' + priority + ']: ' + ticketId;
  return sendEmail([staff.email], subject, body);
}

async function sendCriticalEscalationNotification(ticketId, escalateTo, escalationLevel, reason, ticket) {
  const adminEmails = getAdminEmails();
  if (!adminEmails.escalation) return;
  const userName = ticket['Name'] || '';
  const shortDesc = ticket['Short Description'] || '';
  const priority = ticket['Priority'] || 'Pending';
  const criticalFlag = ticket['Critical Flag'] === 'true';
  const subject = '[CRITICAL ESCALATION] Ticket ' + ticketId + ' escalated to ' + escalationLevel;
  let body = 'A CRITICAL ticket has been escalated.\n\n';
  body += 'Ticket ID: ' + ticketId + '\n';
  body += 'User: ' + userName + '\n';
  body += 'Priority: ' + priority + '\n';
  if (criticalFlag) body += 'User Marked as: CRITICAL\n';
  body += 'Escalated To: ' + escalateTo + ' (' + escalationLevel + ')\n';
  body += 'Reason: ' + (reason || 'Not specified') + '\n';
  body += 'Description: ' + shortDesc + '\n\n';
  body += 'Please monitor this ticket closely.\n\nIT Support System';
  return sendEmail([adminEmails.escalation], subject, body);
}

async function sendStatusChangeEmail(ticketId, userName, userEmail, oldStatus, newStatus, ticketInfo) {
  const shortDesc = ticketInfo['Short Description'] || '';
  const assignedTo = ticketInfo['Assigned To'] || '';
  const resolvedBy = ticketInfo['Resolved By'] || '';
  let subject = '';
  let body = '';

  switch (newStatus) {
    case 'In Progress':
      subject = 'Ticket In Progress: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket is now being worked on.\n\nStatus Update:\n--------------------------------------------------\nTicket ID: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: In Progress\n' + (assignedTo ? 'Working On It: ' + assignedTo : '') + '\nUpdated: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nYou\'ll be notified when resolved.\n\nBest regards,\nIT Support Team';
      break;
    case 'Resolved':
      subject = 'Ticket Resolved: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been resolved!\n\nResolution Details:\n--------------------------------------------------\nTicket ID: ' + ticketId + '\nStatus: Resolved\nResolved By: ' + (resolvedBy || assignedTo || 'IT Support Team') + '\nResolved: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nIf you still have issues, please reply to this email or submit a new ticket.\n\nBest regards,\nIT Support Team';
      break;
    case 'Open':
      subject = 'Ticket Reopened: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been reopened.\n\nReopening Details:\n--------------------------------------------------\nTicket ID: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: Open\nReopened: ' + formatDate(new Date()) + '\n--------------------------------------------------\n\nWe\'ll work on it again shortly.\n\nBest regards,\nIT Support Team';
      break;
  }

  if (subject && body && userEmail) {
    return sendEmail([userEmail], subject, body);
  }
}

module.exports = {
  sendEmail,
  sendTicketSubmittedEmail,
  sendNewTicketNotificationToIT,
  sendTicketAssignedEmailToUser,
  sendTicketAssignedEmailToStaff,
  sendTicketEscalatedEmailToUser,
  sendTicketEscalatedEmailToStaff,
  sendCriticalEscalationNotification,
  sendStatusChangeEmail,
  getAdminEmails
};
