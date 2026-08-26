const config = require('./config');
const utils = require('./utils');

const SMS_BODY_LIMIT = 1600;

function isDryRun() {
  const setting = utils.getSetting('DRY_RUN');
  if (setting !== null) return !!setting;
  return config.DRY_RUN || process.env.DRY_RUN === 'true';
}

function getSetting(name, fallback) {
  const v = utils.getSetting(name);
  if (v === null || v === undefined || v === '') return fallback;
  return v;
}

function isEnabled() {
  const v = getSetting('SMS_ENABLED', 'false');
  return v === true || String(v).toLowerCase() === 'true';
}

function isCriticalOnly() {
  const v = getSetting('SMS_CRITICAL_ONLY', 'false');
  return v === true || String(v).toLowerCase() === 'true';
}

function normalizePhone(phone) {
  if (!phone) return '';
  const p = String(phone).trim().replace(/\s/g, '');
  if (p.startsWith('+')) return p;
  const digits = p.replace(/\D/g, '');
  if (digits.length === 10) return '+1' + digits;
  if (digits.length > 0) return '+' + digits;
  return p;
}

function getTwilioClient() {
  const sid = getSetting('TWILIO_SID', process.env.TWILIO_SID);
  const token = getSetting('TWILIO_TOKEN', process.env.TWILIO_TOKEN);
  if (!sid || !token) return null;
  try {
    const twilio = require('twilio');
    return twilio(sid, token);
  } catch (e) {
    console.warn('Twilio package not installed; SMS will be logged only. Run: npm install twilio');
    return null;
  }
}

function truncate(body) {
  if (!body) return '';
  if (body.length <= SMS_BODY_LIMIT) return body;
  return body.substring(0, SMS_BODY_LIMIT - 3) + '...';
}

async function sendSms(to, body) {
  if (!to) return { success: true, skipped: true, reason: 'No phone number' };
  const phone = normalizePhone(to);
  const message = truncate(body);

  if (!isEnabled()) {
    return { success: true, skipped: true, reason: 'SMS not enabled' };
  }

  if (isDryRun()) {
    console.log('DRY_RUN: SMS to', phone);
    console.log('Body:', message);
    return { success: true, dryRun: true };
  }

  const client = getTwilioClient();
  const from = getSetting('TWILIO_PHONE_NUMBER', process.env.TWILIO_PHONE_NUMBER);

  if (!client || !from) {
    console.log('SMS not configured (provider/twilio/from missing). Would send to', phone, ':', message);
    return { success: true, logged: true, reason: 'SMS provider not configured' };
  }

  try {
    const result = await client.messages.create({ from, to: phone, body: message });
    console.log('SMS sent:', result.sid, 'to', phone);
    return { success: true, sid: result.sid };
  } catch (e) {
    console.error('SMS send failed:', e.message);
    return { success: false, error: e.message };
  }
}

function shouldSendSmsForTicket(ticket) {
  if (!isEnabled()) return false;
  if (isCriticalOnly()) {
    return ticket['Critical Flag'] === 'true' || ticket['Critical Flag'] === true || ticket['Priority'] === 'Critical';
  }
  return true;
}

async function sendTicketSubmittedSms(ticket) {
  if (!shouldSendSmsForTicket(ticket)) return { success: true, skipped: true, reason: 'Not critical or SMS not enabled' };
  const body = 'Your IT ticket ' + ticket['Ticket ID'] + ' has been submitted. ' + (ticket['Short Description'] || '').substring(0, 80) + '. You will be updated when it is assigned.';
  return sendSms(ticket['Phone Number'], body);
}

async function sendTicketAssignedSms(ticket, assignedTo) {
  if (!shouldSendSmsForTicket(ticket)) return { success: true, skipped: true, reason: 'Not critical or SMS not enabled' };
  const body = 'Your IT ticket ' + ticket['Ticket ID'] + ' has been assigned to ' + assignedTo + '. ' + (ticket['Short Description'] || '').substring(0, 80);
  return sendSms(ticket['Phone Number'], body);
}

async function sendTicketStatusChangedSms(ticket, oldStatus, newStatus) {
  if (!shouldSendSmsForTicket(ticket)) return { success: true, skipped: true, reason: 'Not critical or SMS not enabled' };
  const body = 'Update on ' + ticket['Ticket ID'] + ': status changed from ' + oldStatus + ' to ' + newStatus + '. ' + (ticket['Short Description'] || '').substring(0, 80);
  return sendSms(ticket['Phone Number'], body);
}

async function sendTicketEscalatedSms(ticket, escalateTo, escalationLevel) {
  if (!shouldSendSmsForTicket(ticket)) return { success: true, skipped: true, reason: 'Not critical or SMS not enabled' };
  const body = 'Your IT ticket ' + ticket['Ticket ID'] + ' has been escalated to ' + escalationLevel + ' (' + escalateTo + '). We are working to resolve it.';
  return sendSms(ticket['Phone Number'], body);
}

function getSmsConfig() {
  return {
    enabled: isEnabled(),
    provider: getSetting('SMS_PROVIDER', process.env.SMS_PROVIDER || 'twilio'),
    from: getSetting('TWILIO_PHONE_NUMBER', process.env.TWILIO_PHONE_NUMBER) ? 'configured' : '',
    criticalOnly: isCriticalOnly()
  };
}

module.exports = {
  sendSms,
  sendTicketSubmittedSms,
  sendTicketAssignedSms,
  sendTicketStatusChangedSms,
  sendTicketEscalatedSms,
  getSmsConfig,
  isEnabled
};
