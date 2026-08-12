const nodemailer = require('nodemailer');
const config = require('./config');
const db = require('./db');
const utils = require('./utils');

const EXCLUDED_NEW_TICKET_NOTIFICATIONS = ['dinesh.manoharan@alignedcardio.com'];

function getTransporter() {
  const host = process.env.SMTP_HOST;
  const port = process.env.SMTP_PORT;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (!host || !port || !user || !pass) {
    return null;
  }

  const isMicrosoft365 = host === 'smtp.office365.com' || host === 'smtp-mail.outlook.com' || host === 'outlook.office365.com';
  const portNum = Number(port);

  const transport = {
    host,
    port: portNum,
    secure: portNum === 465,
    requireTLS: portNum === 587,
    auth: { user, pass }
  };

  if (isMicrosoft365 && portNum === 587) {
    transport.tls = { ciphers: 'SSLv3', minVersion: 'TLSv1.2' };
  }

  return nodemailer.createTransport(transport);
}

function isGraphConfigured() {
  return process.env.EMAIL_MODE === 'graph' || !!(process.env.GRAPH_CLIENT_ID && process.env.GRAPH_CLIENT_SECRET && process.env.GRAPH_TENANT_ID && process.env.GRAPH_SENDER_USER);
}

function getGraphSender() {
  return process.env.GRAPH_SENDER_USER || process.env.EMAIL_FROM || process.env.SMTP_USER || config.ADMIN_EMAILS.PRIMARY;
}

async function getGraphAccessToken() {
  const tenant = process.env.GRAPH_TENANT_ID;
  const clientId = process.env.GRAPH_CLIENT_ID;
  const clientSecret = process.env.GRAPH_CLIENT_SECRET;
  const tokenUrl = `https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token`;
  const body = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    scope: 'https://graph.microsoft.com/.default',
    grant_type: 'client_credentials'
  });
  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });
  const data = await response.json();
  if (!response.ok) {
    const err = new Error(data.error_description || data.error || 'Graph token request failed');
    err.code = data.error;
    throw err;
  }
  return data.access_token;
}

function formatHtmlBody(text) {
  return `<div style="font-family:Verdana,Arial,sans-serif;font-size:14px;line-height:1.6;color:#333333;">${text.replace(/\n/g, '<br>')}</div>`;
}

async function sendGraphEmail(to, subject, body, cc) {
  const sender = getGraphSender();
  if (!sender) throw new Error('Graph sender not configured');
  const token = await getGraphAccessToken();
  const url = `https://graph.microsoft.com/v1.0/users/${encodeURIComponent(sender)}/sendMail`;
  const toRecipients = to.filter(e => e && String(e).trim() !== '').map(addr => ({ emailAddress: { address: String(addr).trim() } }));
  const ccRecipients = (cc || []).filter(e => e && String(e).trim() !== '').map(addr => ({ emailAddress: { address: String(addr).trim() } }));
  if (toRecipients.length === 0) throw new Error('No valid email recipients');
  const message = {
    subject,
    body: { contentType: 'HTML', content: formatHtmlBody(body) },
    toRecipients,
    ccRecipients
  };
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, saveToSentItems: true })
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const err = new Error(data.error?.message || 'Graph sendMail failed');
    err.code = data.error?.code;
    throw err;
  }
  return true;
}

async function verifyGraph() {
  if (!isGraphConfigured()) {
    return { success: false, error: 'Graph API not configured; set GRAPH_CLIENT_ID, GRAPH_CLIENT_SECRET, GRAPH_TENANT_ID and GRAPH_SENDER_USER' };
  }
  try {
    const token = await getGraphAccessToken();
    const sender = getGraphSender();
    const url = `https://graph.microsoft.com/v1.0/users/${encodeURIComponent(sender)}`;
    const response = await fetch(url, { headers: { 'Authorization': `Bearer ${token}` } });
    const data = await response.json().catch(() => ({}));
    if (response.ok) {
      return { success: true, message: 'Graph API connection verified', sender: data.userPrincipalName || sender, displayName: data.displayName, tenant: process.env.GRAPH_TENANT_ID };
    }
    if (response.status === 403) {
      return { success: true, message: 'Graph API token valid, but user read permission not granted; Mail.Send may still work', sender, tenant: process.env.GRAPH_TENANT_ID };
    }
    return { success: false, error: data.error?.message || `Graph API check failed with status ${response.status}`, code: data.error?.code, tenant: process.env.GRAPH_TENANT_ID };
  } catch (e) {
    return { success: false, error: e.message, code: e.code };
  }
}

async function verifyEmail() {
  if (isGraphConfigured()) return verifyGraph();
  return verifySMTP();
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

  const validCC = (cc || []).filter(e => e && String(e).trim() !== '');
  const htmlBody = formatHtmlBody(body);

  if (isGraphConfigured()) {
    try {
      await sendGraphEmail(recipients, subject, body, validCC);
      console.log('Email sent via Graph to:', recipients.join(', '));
      return true;
    } catch (e) {
      console.error('Error sending email via Graph:', e.message);
      return false;
    }
  }

  const transporter = getTransporter();
  if (!transporter) {
    console.log('No email transport configured; email not sent:', subject);
    return false;
  }

  const mailOptions = {
    from: process.env.EMAIL_FROM || process.env.SMTP_USER || config.ADMIN_EMAILS.PRIMARY,
    to: recipients.join(','),
    subject,
    text: body,
    html: htmlBody
  };

  if (validCC.length > 0) {
    mailOptions.cc = validCC.join(',');
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

async function sendTicketSubmittedEmail(ticketId, timestamp, userName, formData, issueType, impactArea, criticalFlag) {
  const subject = 'Ticket Submitted: ' + ticketId + ' - ' + (formData.shortDescription || '').substring(0, 50);
  let body = 'Dear ' + userName + ',\n\n';
  body += 'Thank you for submitting your IT support request.\n\n';
  body += 'Ticket Details:\n';
  body += '--------------------------------------------------\n';
  body += 'Incident Number: ' + ticketId + '\n';
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

async function sendNewTicketNotificationToIT(ticketId, timestamp, employeeLookup, userName, formData, priority, issueType, impactArea, criticalFlag) {
  let urgencyTag = '';
  if (criticalFlag) urgencyTag = ' [MARKED CRITICAL]';
  else if (impactArea === 'System Outage') urgencyTag = ' [SYSTEM OUTAGE]';
  else if (impactArea === 'Security / Access') urgencyTag = ' [SECURITY]';

  const subject = 'NEW IT Ticket' + urgencyTag + ': ' + ticketId;
  let body = 'A new IT ticket has been submitted.\n\n';
  body += 'Ticket Information:\n';
  body += '--------------------------------------------------\n';
  body += 'Incident Number: ' + ticketId + '\n';
  body += 'Priority: ' + priority + ' (Awaiting assignment)\n';
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

  const data = db.readDb();
  const l1Emails = (data.users || []).filter(u => {
    const level = String(u.supportLevel || u.role || '').trim();
    const email = String(u.email || '').toLowerCase().trim();
    return level === 'L1' && email && !EXCLUDED_NEW_TICKET_NOTIFICATIONS.includes(email);
  }).map(u => u.email);
  l1Emails.forEach(e => { if (recipients.indexOf(e) === -1) recipients.push(e); });

  const uniqueRecipients = [];
  recipients.forEach(e => { if (uniqueRecipients.indexOf(e) === -1) uniqueRecipients.push(e); });

  return sendEmail(uniqueRecipients, subject, body);
}

async function sendTicketAssignedEmailToUser(ticketId, userName, assignedTo, ticket) {
  const shortDesc = ticket['Short Description'] || '';
  const userEmail = ticket['Email Address'];
  if (!userEmail) return;
  const subject = 'Ticket Assigned: ' + ticketId + ' - Now with ' + assignedTo;
  const body = 'Dear ' + userName + ',\n\nYour IT support ticket has been assigned.\n\nAssignment Details:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nDescription: ' + shortDesc + '\nAssigned To: ' + assignedTo + '\nStatus: In Progress\n--------------------------------------------------\n\n' + assignedTo + ' will work on your request.\n\nBest regards,\nIT Support Team';
  return sendEmail([userEmail], subject, body);
}

async function sendTicketAssignedEmailToStaff(ticketId, assignedTo, assignedBy, ticket) {
  const data = db.readDb();
  let staff = (data.users || []).find(u => (u.displayName || '').toLowerCase() === String(assignedTo || '').toLowerCase());
  if (!staff) staff = (data.itStaff || []).find(s => s.name === assignedTo);
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
  const criticalFlag = ticket['Critical Flag'] === 'true';

  let body = 'Hello ' + assignedTo + ',\n\n';
  body += 'You have been assigned a ticket by ' + assignedBy + '.\n\n';
  body += 'Ticket Details:\n--------------------------------------------------\n';
  body += 'Incident Number: ' + ticketId + '\n';
  body += 'Priority: ' + priority + '\n';
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
  const body = 'Dear ' + userName + ',\n\nYour IT ticket has been escalated for faster resolution.\n\nEscalation Details:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nDescription: ' + shortDesc + '\nEscalation Level: ' + escalationLevel + '\nAssigned To: ' + escalateTo + '\n' + (reason ? 'Reason: ' + reason : '') + '\n--------------------------------------------------\n\n' + escalateTo + ' from ' + escalationLevel + ' will handle your request.\n\nBest regards,\nIT Support Team';
  return sendEmail([userEmail], subject, body);
}

async function sendTicketEscalatedEmailToStaff(ticketId, escalateTo, escalationLevel, escalatedBy, reason, ticket) {
  const data = db.readDb();
  let staff = (data.users || []).find(u => (u.displayName || '').toLowerCase() === String(escalateTo || '').toLowerCase());
  if (!staff) staff = (data.itStaff || []).find(s => s.name === escalateTo);
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
  const criticalFlag = ticket['Critical Flag'] === 'true';

  let body = 'Hello ' + escalateTo + ',\n\n';
  body += 'A ticket has been escalated to you by ' + escalatedBy + '.\n\n';
  body += 'Escalation Information:\n--------------------------------------------------\n';
  body += 'Incident Number: ' + ticketId + '\n';
  body += 'Priority: ' + priority + '\n';
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
  body += 'Incident Number: ' + ticketId + '\n';
  body += 'User: ' + userName + '\n';
  body += 'Priority: ' + priority + '\n';
  if (criticalFlag) body += 'User Marked as: CRITICAL\n';
  body += 'Escalated To: ' + escalateTo + ' (' + escalationLevel + ')\n';
  body += 'Reason: ' + (reason || 'Not specified') + '\n';
  body += 'Description: ' + shortDesc + '\n\n';
  body += 'Please monitor this ticket closely.\n\nIT Support System';
  return sendEmail([adminEmails.escalation], subject, body);
}

async function verifySMTP() {
  const transporter = getTransporter();
  if (!transporter) {
    return { success: false, error: 'SMTP not configured; set SMTP_HOST, SMTP_PORT, SMTP_USER and SMTP_PASS' };
  }
  try {
    await transporter.verify();
    return { success: true, message: 'SMTP connection verified', host: process.env.SMTP_HOST, port: process.env.SMTP_PORT, user: process.env.SMTP_USER };
  } catch (e) {
    return { success: false, error: e.message, code: e.code };
  }
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
      body = 'Dear ' + userName + ',\n\nYour IT ticket is now being worked on.\n\nStatus Update:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: In Progress\n' + (assignedTo ? 'Working On It: ' + assignedTo : '') + '\nUpdated: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nYou\'ll be notified when resolved.\n\nBest regards,\nIT Support Team';
      break;
    case 'Resolved':
      subject = 'Ticket Resolved: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been resolved!\n\nResolution Details:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nStatus: Resolved\nResolved By: ' + (resolvedBy || assignedTo || 'IT Support Team') + '\nResolved: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nIf you still have issues, please reply to this email or submit a new ticket.\n\nBest regards,\nIT Support Team';
      break;
    case 'Open':
      subject = 'Ticket Reopened: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been reopened.\n\nReopening Details:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: Open\nReopened: ' + formatDate(new Date()) + '\n--------------------------------------------------\n\nWe\'ll work on it again shortly.\n\nBest regards,\nIT Support Team';
      break;
    case 'On Hold':
      subject = 'Ticket On Hold: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been placed on hold.\n\nStatus Update:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: On Hold\n' + (assignedTo ? 'Assigned To: ' + assignedTo : '') + '\nUpdated: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nWe\'ll resume work when possible.\n\nBest regards,\nIT Support Team';
      break;
    case 'Differ':
      subject = 'Ticket Differ: ' + ticketId;
      body = 'Dear ' + userName + ',\n\nYour IT ticket has been moved to Differ status.\n\nStatus Update:\n--------------------------------------------------\nIncident Number: ' + ticketId + '\nPrevious Status: ' + oldStatus + '\nNew Status: Differ\n' + (assignedTo ? 'Assigned To: ' + assignedTo : '') + '\nUpdated: ' + formatDate(new Date()) + '\n\nDescription: ' + shortDesc + '\n--------------------------------------------------\n\nWe\'ll follow up when the issue can proceed.\n\nBest regards,\nIT Support Team';
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
  getAdminEmails,
  verifySMTP,
  verifyGraph,
  verifyEmail
};
