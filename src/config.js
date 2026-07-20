module.exports = {
  // Admin email configuration
  ADMIN_EMAILS: {
    PRIMARY: process.env.PRIMARY_ADMIN_EMAIL || 'rick.barlow@alignedcardio.com',
    SECONDARY: process.env.SECONDARY_ADMIN_EMAIL || 'srihari.thangavel@alignedcardio.com',
    TERTIARY: process.env.TERTIARY_ADMIN_EMAIL || 'monapuri.pranay@alignedcardio.com',
    ESCALATION: process.env.ESCALATION_EMAIL || 'rick.barlow@alignedcardio.com',
    CRITICAL: process.env.CRITICAL_EMAIL || 'rick.barlow@alignedcardio.com'
  },

  IT_TO() {
    return [
      this.ADMIN_EMAILS.PRIMARY,
      this.ADMIN_EMAILS.SECONDARY,
      this.ADMIN_EMAILS.TERTIARY
    ].filter(email => email && email.trim() !== '');
  },

  FORM_ENABLED: process.env.FORM_ENABLED !== 'false',
  DASHBOARD_ENABLED: process.env.DASHBOARD_ENABLED !== 'false',
  DRY_RUN: process.env.DRY_RUN === 'true',

  HEADERS: [
    'Created Date', 'Ticket ID', 'Employee ID', 'Name', 'Email Address',
    'Phone Number', 'Location', 'Issue Type', 'Impact Area',
    'Short Description', 'Additional Description',
    'Status', 'Priority', 'VIP Level', 'Critical Flag', 'Assigned To', 'Assigned Date', 'Escalation Level',
    'Escalated To', 'Escalation Date', 'Last Updated', 'Resolved By', 'Resolved Date'
  ],

  HISTORY_HEADERS: ['Timestamp', 'Ticket ID', 'Action', 'From', 'To', 'Performed By', 'Notes'],
  USERS_HEADERS: ['Employee ID', 'Password', 'Display Name', 'Role', 'Status'],
  IT_STAFF_HEADERS: ['Name', 'Email', 'Level', 'Status'],
  DIRECTORY_HEADERS: ['Employee ID', 'Name', 'Email', 'VIP Level'],
  SETTINGS_HEADERS: ['Setting Name', 'Value', 'Last Updated', 'Updated By'],
  NOTES_HEADERS: ['Timestamp', 'Ticket ID', 'Note', 'Added By', 'Note Type'],

  COLUMNS: {
    CREATED_DATE: 1, TICKET_ID: 2, EMPLOYEE_ID: 3, NAME: 4, EMAIL: 5, PHONE: 6, LOCATION: 7,
    ISSUE_TYPE: 8, IMPACT_AREA: 9, SHORT_DESCRIPTION: 10, ADDITIONAL_DESCRIPTION: 11,
    STATUS: 12, PRIORITY: 13, VIP_LEVEL: 14, CRITICAL_FLAG: 15, ASSIGNED_TO: 16, ASSIGNED_DATE: 17,
    ESCALATION_LEVEL: 18, ESCALATED_TO: 19, ESCALATION_DATE: 20, LAST_UPDATED: 21, RESOLVED_BY: 22, RESOLVED_DATE: 23
  },

  VIP_PRIORITY_MAP: { High: 'Critical', Middle: 'High', Low: 'Medium' },
  PRIORITY_MAP: { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low', Critical: 'Critical', High: 'High', Medium: 'Medium', Low: 'Low', Pending: 'Pending' },
  VALID_PRIORITIES: ['Pending', 'Low', 'Medium', 'High', 'Critical'],
  VALID_USER_STATUSES: ['Online', 'Offline', 'Break', 'In Meeting', 'Active', 'Inactive'],
  NOTE_TYPES: ['Work Done', 'Update', 'Investigation', 'Solution', 'Follow-up', 'General'],

  SLA_THRESHOLDS: {
    Critical: { response: 1, resolution: 4 },
    High: { response: 4, resolution: 24 },
    Medium: { response: 8, resolution: 48 },
    Low: { response: 24, resolution: 72 },
    Pending: { response: 24, resolution: 72 }
  },

  get locations() {
    return ['ACP - Corporate', 'ASC - Colonial Heights', 'CH - Williamsburg', 'HRA - Greenville', 'JRC - Ashlake', 'JRC - Colonial Heights', 'JRC - Discovery', 'JRC - Discovery - AIC', 'JRC - Emporia', 'JRC - Franklin', 'JRC - Lawrenceville', 'NCC - Stafford', 'NCC - Woodbridge', 'PCC - Potomac', 'Solid State Practice', 'SWVC - Salem'];
  },

  get issueTypes() {
    return [
      'Login / Sign-in issue', 'Password reset / MFA issue', 'Mailbox / Outlook access', 'Teams / OneDrive / SharePoint access',
      'Laptop / desktop issue', 'Monitor / keyboard / mouse', 'Printer / scanner', 'Hardware replacement',
      'Application not working', 'Application access request', 'Installation / upgrade', 'License issue',
      'No internet', 'VPN issue', 'Wi-Fi connectivity', 'Network slowness',
      'Email not working', 'Outlook / Gmail issue', 'Teams / Zoom issue', 'Calendar problem',
      'Add Ring Central User', 'Call Routing/IVR Issue', 'Poor Call Quality', 'Other Ring Central Issue',
      'Phishing / suspicious email', 'Malware concern', 'Data access issue',
      'New user setup', 'New software request', 'Access change', 'Equipment request',
      'General IT query', 'Not listed above'
    ];
  },

  impactAreas: ['System Outage', 'User Productivity', 'Security / Access', 'Service Request'],
  vipLevels: ['High', 'Middle', 'Low'],
  statuses: ['Open', 'In Progress', 'Resolved'],
  priorities: ['Pending', 'Low', 'Medium', 'High', 'Critical'],
  escalationLevels: ['L1', 'L2', 'L3']
};
