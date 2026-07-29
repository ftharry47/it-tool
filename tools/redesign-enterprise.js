const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
const html = fs.readFileSync(indexPath, 'utf8');

// Extract original selects
const locationMatch = html.match(/<select id="location"[\s\S]*?<\/select>/);
const issueTypeMatch = html.match(/<select id="issueType"[\s\S]*?<\/select>/);
let locationSelect = locationMatch ? locationMatch[0] : '<select id="location" class="form-input itsm-input itsm-select"><option value="">Select location</option></select>';
let issueTypeSelect = issueTypeMatch ? issueTypeMatch[0] : '<select id="issueType" class="form-input itsm-input itsm-select"><option value="">Select issue type</option></select>';

// Clean classes on selects
locationSelect = locationSelect.replace(/class="[^"]*"/g, 'class="form-input itsm-input itsm-select"');
issueTypeSelect = issueTypeSelect.replace(/class="[^"]*"/g, 'class="form-input itsm-input itsm-select"');

// Extract original style content
const styleMatch = html.match(/<style>([\s\S]*?)<\/style>/);
const originalStyle = styleMatch ? styleMatch[1] : '';

const overrideCss = `
/* Enterprise ITSM overrides */
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
html, body.itsm-body {
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif !important;
}
body.itsm-body {
  background: #F8FAFC !important;
  color: #1F2937 !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.itsm-toolbar {
  height: 72px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E7EB;
  position: sticky;
  top: 0;
  z-index: 40;
}
.itsm-toolbar-inner {
  max-width: 1280px;
  height: 100%;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.itsm-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.itsm-brand img {
  height: 32px;
  width: auto;
}
.itsm-brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.itsm-portal {
  font-family: 'Michroma', 'Bank Gothic', 'Eurostile Extended', 'Microgramma', sans-serif !important;
  font-size: 16px;
  font-weight: 400;
  color: #1F2937;
  letter-spacing: 0.015em;
  line-height: 1.2;
}
.itsm-org {
  font-size: 12px;
  color: #6B7280;
  line-height: 1.2;
}
.itsm-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.itsm-toolbar-btn {
  height: 36px;
  padding: 0 18px;
  border-radius: 6px;
  border: none;
  background: #2563EB;
  color: #FFFFFF;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}
.itsm-toolbar-btn:hover {
  background: #1D4ED8;
}
.itsm-toolbar-btn-secondary {
  height: 36px;
  padding: 0 18px;
  border-radius: 6px;
  border: 1px solid #D1D5DB;
  background: #FFFFFF;
  color: #374151;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}
.itsm-toolbar-btn-secondary:hover {
  background: #F3F4F6;
  border-color: #9CA3AF;
}
.itsm-toolbar-btn.active {
  box-shadow: 0 0 0 2px rgba(37,99,235,0.2);
}

.itsm-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px;
}

.itsm-container {
  background: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  padding: 32px;
}

.itsm-page-header {
  margin-bottom: 32px;
}
.itsm-caption {
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #64748B;
  margin-bottom: 8px;
}
.itsm-main-title {
  font-family: 'Michroma', 'Bank Gothic', 'Eurostile Extended', 'Microgramma', sans-serif !important;
  font-size: 26px;
  font-weight: 400;
  text-transform: uppercase;
  letter-spacing: 0.015em;
  color: #1F2937;
  margin: 0 0 8px 0;
  line-height: 1.2;
}
.itsm-desc {
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  color: #6B7280;
  margin: 0;
  line-height: 22px;
}
.itsm-phi {
  color: #C62828;
  font-weight: 600;
}

.itsm-section {
  margin-bottom: 32px;
}
.itsm-section:last-child {
  margin-bottom: 0;
}
.itsm-section-title {
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #6B7280;
  padding-bottom: 12px;
  margin-bottom: 20px;
  border-bottom: 1px solid #E5E7EB;
}

.itsm-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}
@media (min-width: 768px) {
  .itsm-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.itsm-field {
  margin-bottom: 20px;
}
.itsm-field:last-child {
  margin-bottom: 0;
}

.itsm-label {
  display: block;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}
.itsm-required {
  color: #C62828;
}

.itsm-input,
.itsm-select,
.itsm-textarea {
  width: 100%;
  height: 44px;
  border: 1px solid #D1D5DB !important;
  border-radius: 6px !important;
  background: #FFFFFF !important;
  color: #1F2937 !important;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  padding: 13px 14px !important;
  outline: none;
  transition: all 150ms;
  line-height: 1.25;
}
.itsm-textarea {
  height: 160px;
  padding: 16px !important;
  font-size: 15px;
  line-height: 26px;
  resize: none;
}
.itsm-input::placeholder,
.itsm-textarea::placeholder {
  color: #9CA3AF;
}
.itsm-input:focus,
.itsm-select:focus,
.itsm-textarea:focus {
  border-color: #2563EB !important;
  box-shadow: 0 0 0 3px rgba(37,99,235,0.12) !important;
}
.itsm-input.valid,
.itsm-select.valid,
.itsm-textarea.valid {
  border-color: #16A34A !important;
  background: #F0FDF4 !important;
}
.itsm-input.error,
.itsm-select.error,
.itsm-textarea.error {
  border-color: #DC2626 !important;
  background: #FEF2F2 !important;
}

.itsm-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='none' stroke='%236B7280' stroke-width='2' viewBox='0 0 24 24'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px !important;
}

.itsm-char-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  font-size: 12px;
  color: #6B7280;
}

.itsm-impact-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 12px;
}
@media (min-width: 640px) {
  .itsm-impact-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (min-width: 1024px) {
  .itsm-impact-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
.itsm-impact {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid #D1D5DB;
  border-radius: 6px;
  background: #FFFFFF;
  color: #374151;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
  text-align: left;
}
.itsm-impact:hover {
  background: #EFF6FF;
}
.itsm-impact.selected {
  border-color: #2563EB;
  background: #EFF6FF;
  color: #2563EB;
}
.itsm-impact svg {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.itsm-priority-bar {
  display: inline-flex;
  align-items: center;
  border: 1px solid #D1D5DB;
  border-radius: 6px;
  overflow: hidden;
  background: #FFFFFF;
}
.itsm-priority {
  height: 34px;
  padding: 0 18px;
  border: none;
  border-right: 1px solid #D1D5DB;
  background: #FFFFFF;
  color: #374151;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}
.itsm-priority:last-child {
  border-right: none;
}
.itsm-priority:hover {
  background: #F3F4F6;
}
.itsm-priority.selected {
  color: #FFFFFF;
}
.itsm-priority[data-value="P4"].selected { background: #6B7280; }
.itsm-priority[data-value="P3"].selected { background: #2563EB; }
.itsm-priority[data-value="P2"].selected { background: #F59E0B; }
.itsm-priority[data-value="P1"].selected { background: #C62828; }

.itsm-critical {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}
.itsm-cb-box {
  width: 18px;
  height: 18px;
  border: 1px solid #D1D5DB;
  border-radius: 4px;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 150ms;
  flex-shrink: 0;
}
.itsm-critical.checked .itsm-cb-box {
  background: #C62828;
  border-color: #C62828;
}
.itsm-cb-check {
  width: 12px;
  height: 12px;
  color: #FFFFFF;
}
.itsm-cb-label {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}
.itsm-cb-hint {
  font-size: 12px;
  color: #6B7280;
  margin-top: 2px;
}

.itsm-upload {
  border: 2px dashed #CBD5E1;
  border-radius: 8px;
  background: #F8FAFC;
  padding: 28px;
  text-align: center;
  cursor: pointer;
  transition: all 150ms;
}
.itsm-upload:hover,
.itsm-upload.dragover {
  background: #EFF6FF;
  border-color: #2563EB;
}
.itsm-upload-title {
  font-size: 14px;
  color: #64748B;
  margin: 8px 0 4px;
  font-weight: 500;
}
.itsm-upload-hint {
  font-size: 12px;
  color: #9CA3AF;
  margin: 0;
}
.itsm-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

#itsm-app .itsm-submit {
  width: 100%;
  height: 46px;
  background: #2563EB !important;
  color: #FFFFFF !important;
  border: none !important;
  border-radius: 8px !important;
  font-family: 'Inter', 'SF Pro Display', 'Segoe UI', sans-serif;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
  box-shadow: none !important;
  transform: none !important;
}
#itsm-app .itsm-submit:hover:not(:disabled) {
  background: #1D4ED8 !important;
  transform: translateY(-1px) !important;
}
#itsm-app .itsm-submit:disabled {
  background: #93C5FD !important;
  color: #FFFFFF !important;
  cursor: not-allowed;
}

.itsm-terms-line {
  text-align: center;
  font-size: 12px;
  color: #6B7280;
  margin-top: 16px;
}
.itsm-terms-link {
  color: #2563EB;
  background: none;
  border: none;
  padding: 0;
  font-size: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.itsm-terms-link:hover {
  color: #1D4ED8;
}

.itsm-track-row {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
@media (min-width: 640px) {
  .itsm-track-row {
    flex-direction: row;
  }
}
.itsm-track-input {
  flex: 1;
}
.itsm-track-btn {
  width: 100%;
}
@media (min-width: 640px) {
  .itsm-track-btn {
    width: auto;
    min-width: 140px;
  }
}

.itsm-msg {
  font-size: 12px;
  margin-top: 6px;
}
.itsm-msg.text-green-600 { color: #16A34A; }
.itsm-msg.text-red-600 { color: #DC2626; }

.itsm-help-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
  margin-top: 32px;
}
@media (min-width: 768px) {
  .itsm-help-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.itsm-help-card {
  background: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  padding: 24px;
}
.itsm-help-title {
  font-size: 14px;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 16px;
}
.itsm-help-item {
  padding: 12px 0;
  border-bottom: 1px solid #E5E7EB;
  cursor: pointer;
}
.itsm-help-item:last-child {
  border-bottom: none;
}
.itsm-help-q {
  font-size: 14px;
  color: #374151;
  margin: 0;
}
.itsm-help-a {
  font-size: 13px;
  color: #6B7280;
  line-height: 20px;
  margin: 8px 0 0;
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.25s ease;
}
.itsm-help-item.open .itsm-help-a {
  max-height: 200px;
}
.itsm-info-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.itsm-info-row:last-child {
  margin-bottom: 0;
}
.itsm-info-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #EFF6FF;
  color: #2563EB;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.itsm-info-icon svg {
  width: 18px;
  height: 18px;
}
.itsm-info-title {
  font-size: 14px;
  font-weight: 600;
  color: #1F2937;
  margin: 0 0 4px;
}
.itsm-info-text {
  font-size: 13px;
  color: #6B7280;
  line-height: 20px;
  margin: 0;
}

#itsm-app section {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
#itsm-app section.hidden {
  opacity: 0;
  transform: translateY(10px);
  pointer-events: none;
  position: absolute;
  width: 100%;
}

#autoSaveIndicator {
  display: none !important;
}
`;

const newHead = `<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>IT Support Portal - Aligned Cardio</title>
  <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <link href="https://fonts.googleapis.com/css2?family=Michroma&display=swap" rel="stylesheet">
  <style>
${originalStyle}
${overrideCss}
  </style>
</head>`;

const newBody = `<body class="itsm-body">

  <header class="itsm-toolbar">
    <div class="itsm-toolbar-inner">
      <div class="itsm-brand">
        <img src="https://lh3.googleusercontent.com/d/1n9zWSCv4u-nq9KV5O4YHCbDr4xXBbfwa" alt="Aligned Cardio">
        <div class="itsm-brand-text">
          <div class="itsm-portal">IT Support Portal</div>
          <div class="itsm-org">Aligned Cardio</div>
        </div>
      </div>
      <div class="itsm-toolbar-actions">
        <button id="tabSubmit" class="itsm-toolbar-btn active" onclick="showTab('submit')">Submit</button>
        <button id="tabTrack" class="itsm-toolbar-btn-secondary" onclick="showTab('track')">Track Request</button>
      </div>
    </div>
  </header>

  <main id="itsm-app" class="itsm-page">

    <section id="submitSection">
      <div class="itsm-container">
        <div class="itsm-page-header">
          <div class="itsm-caption">NEW INCIDENT</div>
          <h1 class="itsm-main-title">Report an IT Issue</h1>
          <p class="itsm-desc">Provide details so the IT team can respond quickly. <span class="itsm-phi">Do not include patient PHI.</span></p>
        </div>

        <form id="ticketForm" class="itsm-form">

          <section class="itsm-section">
            <h2 class="itsm-section-title">Requester Details</h2>
            <div class="itsm-grid">
              <div class="itsm-field">
                <label class="itsm-label">Full Name</label>
                <input type="text" id="name" name="name" class="form-input itsm-input" placeholder="Enter your full name (optional)">
              </div>
              <div class="itsm-field">
                <label class="itsm-label">Email Address <span class="itsm-required">*</span></label>
                <div class="relative">
                  <input type="email" id="email" name="email" required class="form-input itsm-input" placeholder="you@company.com">
                  <span id="emailIcon" class="validation-icon hidden"></span>
                </div>
                <p id="emailMsg" class="itsm-msg hidden"></p>
              </div>
              <div class="itsm-field">
                <label class="itsm-label">Phone Number <span class="itsm-required">*</span></label>
                <div class="relative">
                  <input type="tel" id="phone" name="phone" required class="form-input itsm-input" placeholder="(123) 456-7890">
                  <span id="phoneIcon" class="validation-icon hidden"></span>
                </div>
                <p id="phoneMsg" class="itsm-msg hidden"></p>
              </div>
              <div class="itsm-field">
                <label class="itsm-label">Location <span class="itsm-required">*</span></label>
                ${locationSelect}
                <p id="locationMsg" class="itsm-msg hidden"></p>
              </div>
            </div>
          </section>

          <section class="itsm-section">
            <h2 class="itsm-section-title">Incident Details</h2>
            <div class="itsm-field">
              <label class="itsm-label">Description <span class="itsm-required">*</span></label>
              <textarea id="shortDescription" name="shortDescription" required maxlength="500" class="form-input itsm-input itsm-textarea" placeholder="Describe your IT issue in detail (no patient names or MRNs)"></textarea>
              <div class="itsm-char-bar"><span id="shortCharCount">0</span>/500</div>
            </div>
            <div class="itsm-grid" style="margin-top:20px;">
              <div class="itsm-field">
                <label class="itsm-label">Issue Type</label>
                ${issueTypeSelect}
              </div>
              <div></div>
            </div>
            <div class="itsm-field" style="margin-top:20px;">
              <label class="itsm-label">Business Impact</label>
              <div class="itsm-impact-grid">
                <button type="button" class="impact-option itsm-impact" data-value="System Outage" onclick="selectImpact(this)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
                  System Outage
                </button>
                <button type="button" class="impact-option itsm-impact" data-value="User Productivity" onclick="selectImpact(this)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                  User Productivity
                </button>
                <button type="button" class="impact-option itsm-impact" data-value="Security / Access" onclick="selectImpact(this)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
                  Security / Access
                </button>
                <button type="button" class="impact-option itsm-impact" data-value="Service Request" onclick="selectImpact(this)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 12h6m-6 4h6m2 5H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5.586a1 1 0 0 1 .707.293l5.414 5.414a1 1 0 0 1 .293.707V19a2 2 0 0 1-2 2z"></path></svg>
                  Service Request
                </button>
              </div>
              <input type="hidden" id="impactArea" name="impactArea" value="">
            </div>
            <div class="itsm-field" style="margin-top:20px;">
              <label class="itsm-label">Priority</label>
              <div class="itsm-priority-bar">
                <button type="button" class="priority-option itsm-priority selected" data-value="P4" onclick="selectPriority(this)">Low</button>
                <button type="button" class="priority-option itsm-priority" data-value="P3" onclick="selectPriority(this)">Medium</button>
                <button type="button" class="priority-option itsm-priority" data-value="P2" onclick="selectPriority(this)">High</button>
                <button type="button" class="priority-option itsm-priority" data-value="P1" onclick="selectPriority(this)">Critical</button>
              </div>
              <input type="hidden" id="priority" name="priority" value="P4">
            </div>
            <div class="itsm-field" style="margin-top:20px;">
              <label id="criticalCheckbox" class="critical-checkbox itsm-critical" onclick="toggleCritical()">
                <div class="itsm-cb-box" id="criticalCheckIcon">
                  <svg class="itsm-cb-check hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"></path></svg>
                </div>
                <div>
                  <div class="itsm-cb-label">Mark as Critical</div>
                  <div class="itsm-cb-hint">Requires immediate attention</div>
                </div>
              </label>
              <input type="hidden" id="criticalFlag" name="criticalFlag" value="false">
            </div>
          </section>

          <section class="itsm-section">
            <h2 class="itsm-section-title">Attachments</h2>
            <div class="upload-zone itsm-upload" id="attachmentUpload" onclick="document.getElementById('attachmentInput').click()">
              <input type="file" id="attachmentInput" class="upload-input" accept="image/*" multiple onchange="handleAttachments(this.files)">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#CBD5E1" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
              <p class="itsm-upload-title">Click or drag images here</p>
              <p class="itsm-upload-hint">JPEG, PNG, GIF • up to 3 images, auto-compressed</p>
            </div>
            <div id="attachmentPreview" class="itsm-preview"></div>
            <p id="attachmentMsg" class="itsm-msg hidden"></p>
          </section>

          <button type="submit" id="submitBtn" disabled class="btn-primary itsm-submit">Submit Incident</button>
          <p class="itsm-terms-line">By submitting, you agree to our <button type="button" onclick="openTermsModal()" class="itsm-terms-link">IT support terms and conditions</button>.</p>
        </form>
      </div>

      <div class="itsm-help-grid">
        <div class="itsm-help-card">
          <h3 class="itsm-help-title">Quick Help</h3>
          <div class="itsm-help-item" onclick="toggleFaq(0)">
            <p class="itsm-help-q">How do I reset my password?</p>
            <p class="itsm-help-a">Click "Forgot Password" on the login screen or submit a ticket with Issue Type "Password reset".</p>
          </div>
          <div class="itsm-help-item" onclick="toggleFaq(1)">
            <p class="itsm-help-q">My computer is running slow - what should I do?</p>
            <p class="itsm-help-a">Try restarting your computer first and close unnecessary applications. If it persists, submit a ticket.</p>
          </div>
          <div class="itsm-help-item" onclick="toggleFaq(2)">
            <p class="itsm-help-q">I can't connect to VPN - help?</p>
            <p class="itsm-help-a">Check your internet connection and restart the VPN client. Submit a ticket under "VPN issue" if needed.</p>
          </div>
          <div class="itsm-help-item" onclick="toggleFaq(3)">
            <p class="itsm-help-q">How do I report a security concern?</p>
            <p class="itsm-help-a">Report suspected breaches immediately. Select "Phishing / suspicious email" or "Malware concern".</p>
          </div>
        </div>
        <div class="itsm-help-card">
          <h3 class="itsm-help-title">Support</h3>
          <div class="itsm-info-row">
            <div class="itsm-info-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg></div>
            <div>
              <p class="itsm-info-title">Critical / System Outage?</p>
              <p class="itsm-info-text">For system outages or critical issues affecting multiple users, call the IT Helpdesk directly at <strong>(123) 456-7890</strong>.</p>
            </div>
          </div>
          <div class="itsm-info-row">
            <div class="itsm-info-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg></div>
            <div>
              <p class="itsm-info-title">HIPAA Reminder</p>
              <p class="itsm-info-text">Do not include patient names, MRNs, or other PHI in your ticket description. Use general terms.</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section id="trackSection" class="hidden">
      <div class="itsm-container">
        <div class="itsm-page-header">
          <div class="itsm-caption">TRACK INCIDENT</div>
          <h1 class="itsm-main-title">Ticket Status</h1>
          <p class="itsm-desc">Enter your ticket ID to view real-time updates.</p>
        </div>
        <div class="itsm-track-row">
          <input type="text" id="searchInput" class="form-input itsm-input itsm-track-input" placeholder="e.g. SSPTKT-001">
          <button onclick="performSearch()" id="searchBtn" class="btn-primary itsm-submit itsm-track-btn">Search</button>
        </div>
        <p class="itsm-upload-hint">Your ticket ID was sent to your email after submission</p>
        <div id="trackResult" class="hidden mt-6"></div>
        <div id="noResult" class="hidden mt-6 text-center py-8">
          <svg class="w-12 h-12 mx-auto text-gray-300 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
          <p class="text-sm font-medium text-gray-900">Ticket not found</p>
          <p class="text-xs text-gray-500 mt-1">Please check your ticket ID and try again</p>
        </div>
      </div>
    </section>
  </main>
`;

const modalsRestMatch = html.match(/<\/main>\s*([\s\S]*?)(?=<\/body>)/);
const modalsAndRest = modalsRestMatch ? modalsRestMatch[1] : '';

const newFooter = `  <footer class="itsm-footer">
    <div class="itsm-footer-inner">
      <p>&copy; 2025-2026 Srihari. All rights reserved.</p>
      <p>IT Support Portal v8.1.0 | Powered by Solid State Practice</p>
    </div>
  </footer>
`;

const modalsAndRestNew = modalsAndRest.replace(
  /<footer[\s\S]*?<\/footer>/,
  newFooter
);

const newHtml = html
  .replace(/<head>[\s\S]*?<\/head>/, newHead)
  .replace(/<body[^>]*>[\s\S]*?<\/body>/, newBody + modalsAndRestNew + '\n</body>');

fs.writeFileSync(indexPath, newHtml, 'utf8');
console.log('Enterprise ITSM redesign applied to public/index.html');
