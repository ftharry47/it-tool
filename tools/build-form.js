const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
const html = fs.readFileSync(indexPath, 'utf8');

// Extract existing <select> tags so we don't copy long option lists
const locationMatch = html.match(/<select id="location"[\s\S]*?<\/select>/);
const issueTypeMatch = html.match(/<select id="issueType"[\s\S]*?<\/select>/);
let locationSelect = locationMatch ? locationMatch[0] : '<select id="location" name="location" required class="form-input itsm-input w-full pr-10 bg-white"><option value="">Select location</option></select>';
let issueTypeSelect = issueTypeMatch ? issueTypeMatch[0] : '<select id="issueType" name="issueType" class="form-input itsm-input w-full bg-white"><option value="">Select issue type</option></select>';

// Replace old select classes with new styling
function cleanSelectClasses(tag) {
  return tag
    .replace(/class="[^"]*form-input[^"]*"/g, 'class="form-input itsm-input w-full pr-10 bg-white"')
    .replace(/class="[^"]*"/g, (m) => m.includes('itsm-input') ? m : m.replace('class="', 'class="form-input itsm-input ').replace(/\s{2,}/g, ' '));
}
locationSelect = cleanSelectClasses(locationSelect);
issueTypeSelect = cleanSelectClasses(issueTypeSelect);

const icon = (name) => `<i data-lucide="${name}" class="w-[18px] h-[18px]"></i>`;

const overrideCss = `
  @import url('https://fonts.googleapis.com/css2?family=Michroma&family=Inter:wght@400;500;600;700&display=swap');
  :root {
    --itsm-bg: #F5F7FA;
    --itsm-card: #FFFFFF;
    --itsm-primary: #2563EB;
    --itsm-success: #22C55E;
    --itsm-warning: #F59E0B;
    --itsm-danger: #EF4444;
    --itsm-text: #111827;
    --itsm-text2: #6B7280;
    --itsm-border: #E5E7EB;
    --itsm-radius: 16px;
  }
  * { font-family: 'Michroma', 'Inter', sans-serif !important; }
  body { background: var(--itsm-bg) !important; color: var(--itsm-text); }
  .itsm-container { max-width: 1200px; margin: 0 auto; padding: 24px; }
  .itsm-card { background: var(--itsm-card); border-radius: var(--itsm-radius); box-shadow: 0 2px 12px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.03); border: 1px solid var(--itsm-border); padding: 32px; }
  .itsm-section-label { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--itsm-text2); margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
  .itsm-label { font-size: 12px; font-weight: 600; color: var(--itsm-text); margin-bottom: 5px; display: block; }
  .itsm-required { color: var(--itsm-danger); }
  .itsm-input, .form-input.itsm-input, select.form-input.itsm-input, textarea.form-input.itsm-input {
    border-radius: 10px !important;
    border: 1px solid #E5E7EB !important;
    padding: 10px 12px !important;
    font-size: 14px !important;
    transition: all 0.2s ease !important;
    background-color: #fff !important;
    color: var(--itsm-text) !important;
  }
  .itsm-input::placeholder, .form-input.itsm-input::placeholder { color: #9CA3AF !important; }
  .itsm-input:hover, .form-input.itsm-input:hover { border-color: #CBD5E1 !important; }
  .itsm-input:focus, .form-input.itsm-input:focus, select.form-input.itsm-input:focus, textarea.form-input.itsm-input:focus {
    border-color: var(--itsm-primary) !important;
    box-shadow: 0 0 0 4px rgba(37,99,235,0.08) !important;
    outline: none !important;
  }
  .itsm-input.valid, .form-input.itsm-input.valid { border-color: var(--itsm-success) !important; background-color: #f0fdf4 !important; }
  .itsm-input.error, .form-input.itsm-input.error { border-color: var(--itsm-danger) !important; background-color: #fef2f2 !important; }
  .itsm-btn-primary, .btn-primary {
    background: linear-gradient(135deg, #3b82f6 0%, #2563EB 100%) !important;
    color: #fff !important;
    border-radius: 10px !important;
    padding: 12px 20px !important;
    font-weight: 600 !important;
    font-size: 14px !important;
    transition: all 0.2s ease !important;
    border: none !important;
  }
  .itsm-btn-primary:hover:not(:disabled), .btn-primary:hover:not(:disabled) { transform: translateY(-1px) !important; box-shadow: 0 8px 24px rgba(37,99,235,0.22) !important; }
  .itsm-btn-primary:disabled, .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
  .itsm-btn-secondary {
    background: #fff; border: 1px solid #E5E7EB; color: #374151; border-radius: 12px; padding: 10px 16px;
    font-weight: 500; font-size: 14px; transition: all 0.2s ease; cursor: pointer;
  }
  .itsm-btn-secondary:hover:not(:disabled) { border-color: var(--itsm-primary); color: var(--itsm-primary); }
  .itsm-btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
  .itsm-priority-pill {
    border-radius: 8px; padding: 7px 14px; font-size: 12px; font-weight: 600; border: 1px solid #E5E7EB;
    cursor: pointer; transition: all 0.2s ease; display: inline-flex; align-items: center; gap: 6px;
    background: #fff; color: #6B7280;
  }
  .itsm-priority-pill:hover { border-color: #CBD5E1; background: #F9FAFB; }
  .itsm-priority-pill[data-value="P4"] { border-left: 3px solid #22C55E; }
  .itsm-priority-pill[data-value="P3"] { border-left: 3px solid #3B82F6; }
  .itsm-priority-pill[data-value="P2"] { border-left: 3px solid #F59E0B; }
  .itsm-priority-pill[data-value="P1"] { border-left: 3px solid #EF4444; }
  .itsm-priority-pill.selected { background: #F3F4F6; color: var(--itsm-text); border-color: #D1D5DB; font-weight: 700; }
  .itsm-impact-card {
    border-radius: 12px; border: 1px solid #E5E7EB; padding: 16px; cursor: pointer; transition: all 0.2s ease;
    background: #fff; border-left: 4px solid transparent; display: flex; align-items: center; gap: 12px;
  }
  .itsm-impact-card:hover { border-color: #CBD5E1; transform: translateY(-1px); }
  .itsm-impact-card.selected { border-color: #2563EB; background: #eff6ff; box-shadow: 0 0 0 3px rgba(37,99,235,0.08); }
  .itsm-impact-card[data-value="System Outage"].selected { border-left-color: #EF4444; }
  .itsm-impact-card[data-value="User Productivity"].selected { border-left-color: #F59E0B; }
  .itsm-impact-card[data-value="Security / Access"].selected { border-left-color: #A855F7; }
  .itsm-impact-card[data-value="Service Request"].selected { border-left-color: #2563EB; }
  .itsm-upload-zone {
    border: 2px dashed #E5E7EB; border-radius: 12px; padding: 24px; text-align: center; cursor: pointer;
    transition: all 0.2s ease; background: #FAFBFC;
  }
  .itsm-upload-zone:hover, .itsm-upload-zone.dragover { border-color: var(--itsm-primary); background: #eff6ff; }
  .itsm-critical {
    border-radius: 12px; border: 1px solid #E5E7EB; padding: 14px 16px; cursor: pointer; transition: all 0.2s ease;
    display: flex; align-items: center; gap: 12px; background: #fff; max-width: 360px;
  }
  .itsm-critical.checked { border-color: var(--itsm-danger); background: #fef2f2; }
  .itsm-critical #criticalCheckIcon {
    width: 22px; height: 22px; border-radius: 6px; border: 2px solid #E5E7EB;
    display: flex; align-items: center; justify-content: center; transition: all 0.2s ease; background: #fff;
  }
  .itsm-critical.checked #criticalCheckIcon { background: var(--itsm-danger) !important; border-color: var(--itsm-danger) !important; }
  .itsm-tabs {
    display: inline-flex; background: #fff; border-radius: 12px; padding: 6px; border: 1px solid #E5E7EB; box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  }
  .itsm-tab {
    padding: 10px 20px; border-radius: 8px; font-size: 14px; font-weight: 600; color: var(--itsm-text2);
    transition: all 0.2s ease; border: none; background: transparent; cursor: pointer;
  }
  .itsm-tab.active { background: var(--itsm-primary); color: #fff; box-shadow: 0 2px 8px rgba(37,99,235,0.18); }
  .itsm-header { background: #fff; border-bottom: 1px solid #E5E7EB; }
  .itsm-title { font-size: 28px; font-weight: 700; letter-spacing: -0.01em; color: var(--itsm-text); line-height: 1.2; }
  .itsm-subtitle { font-size: 14px; color: var(--itsm-text2); margin-top: 6px; }
  .itsm-incident-bar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 32px; flex-wrap: wrap; }
  .itsm-incident-id { font-size: 13px; font-weight: 500; color: var(--itsm-text2); letter-spacing: 0.04em; }
  .itsm-badge { border-radius: 999px; padding: 6px 14px; font-size: 12px; font-weight: 700; }
  .itsm-info-pill { display: inline-flex; align-items: center; gap: 8px; background: #F3F4F6; border-radius: 8px; padding: 8px 12px; font-size: 14px; color: var(--itsm-text); }
  .itsm-gray-box { background: #F9FAFB; border-radius: 12px; padding: 20px; border: 1px solid #F3F4F6; }
  .itsm-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; background: #F3F4F6; color: var(--itsm-text2); flex-shrink: 0; }
  .itsm-icon svg, .itsm-icon i { width: 18px; height: 18px; }
  .validation-icon { position: absolute; right: 12px; top: 50%; transform: translateY(-50%); display: flex; align-items: center; justify-content: center; width: 20px; height: 20px; }
  @media (max-width: 768px) {
    .itsm-card { padding: 24px; }
    .itsm-title { font-size: 26px; }
    .itsm-incident-bar { flex-direction: column; }
  }
`;

const newBody = `<body class="min-h-screen flex flex-col" style="background:#F5F7FA;">

  <header class="itsm-header">
    <div class="itsm-container">
      <div class="flex items-center justify-between py-3">
        <div class="flex items-center gap-3">
          <img src="https://lh3.googleusercontent.com/d/1n9zWSCv4u-nq9KV5O4YHCbDr4xXBbfwa" alt="Aligned Cardio" class="h-8">
          <div class="border-l border-gray-200 pl-3">
            <h1 class="text-lg font-bold text-gray-900 tracking-wide">IT Support Portal</h1>
            <p class="text-xs text-gray-500">Aligned Cardio</p>
          </div>
        </div>
        <div class="flex items-center gap-4">
          <div id="autoSaveIndicator" class="auto-save-indicator opacity-0 flex items-center gap-1.5 text-xs text-gray-500">
            <svg class="w-3.5 h-3.5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <span class="hidden sm:inline">Draft saved</span>
          </div>
          <div class="itsm-tabs">
            <button onclick="showTab('submit')" id="tabSubmit" class="itsm-tab active">Submit</button>
            <button onclick="showTab('track')" id="tabTrack" class="itsm-tab">Track</button>
          </div>
        </div>
      </div>
    </div>
  </header>

  <main class="flex-1 w-full">
    <div class="itsm-container">

      <!-- Submit Form -->
      <section id="submitSection">
        <div class="itsm-card">
          <div class="itsm-incident-bar">
            <div>
              <p class="itsm-incident-id">NEW INCIDENT</p>
              <h2 class="itsm-title">Report an IT Issue</h2>
              <p class="itsm-subtitle">Provide details so the IT team can respond quickly. <span class="itsm-required font-medium">Do not include patient PHI.</span></p>
            </div>
            <div class="flex items-center gap-3">
              <span id="incidentStatusBadge" class="itsm-badge" style="background:#F3F4F6;color:#6B7280">Draft</span>
              <button type="button" class="itsm-btn-secondary" disabled>...</button>
            </div>
          </div>

          <form id="ticketForm">

            <!-- Requester Details -->
            <div class="mb-10">
              <div class="itsm-section-label">
                <span class="itsm-icon">${icon('user')}</span>
                Requester Details
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                <div>
                  <label class="itsm-label">Full Name</label>
                  <input type="text" id="name" name="name" class="form-input itsm-input w-full" placeholder="Enter your full name (optional)">
                </div>
                <div>
                  <label class="itsm-label">Email Address <span class="itsm-required">*</span></label>
                  <div class="relative">
                    <input type="email" id="email" name="email" required class="form-input itsm-input w-full pr-10" placeholder="you@company.com">
                    <span id="emailIcon" class="validation-icon hidden"></span>
                  </div>
                  <p id="emailMsg" class="text-xs mt-1 hidden"></p>
                </div>
                <div>
                  <label class="itsm-label">Phone Number <span class="itsm-required">*</span></label>
                  <div class="relative">
                    <input type="tel" id="phone" name="phone" required class="form-input itsm-input w-full pr-10" placeholder="(123) 456-7890">
                    <span id="phoneIcon" class="validation-icon hidden"></span>
                  </div>
                  <p id="phoneMsg" class="text-xs mt-1 hidden"></p>
                </div>
                <div>
                  <label class="itsm-label">Location <span class="itsm-required">*</span></label>
                  <div class="relative">
                    ${locationSelect}
                  </div>
                  <p id="locationMsg" class="text-xs mt-1 hidden"></p>
                </div>
              </div>
            </div>

            <!-- Incident Details -->
            <div class="mb-10">
              <div class="itsm-section-label">
                <span class="itsm-icon">${icon('alert-triangle')}</span>
                Incident Details
              </div>

              <div class="itsm-gray-box mb-6">
                <label class="itsm-label">Description <span class="itsm-required">*</span></label>
                <textarea id="shortDescription" name="shortDescription" rows="4" required maxlength="500" class="form-input itsm-input w-full resize-none" placeholder="Describe your IT issue in detail (no patient names or MRNs)..."></textarea>
                <div class="flex items-center justify-between mt-2">
                  <p class="text-xs text-gray-500"><span id="shortCharCount" class="transition-colors">0</span>/500</p>
                  <p class="text-xs text-gray-400">Minimum 10 characters</p>
                </div>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-5 mb-6">
                <div>
                  <label class="itsm-label">Issue Type</label>
                  ${issueTypeSelect}
                </div>
              </div>

              <div class="mb-6">
                <label class="itsm-label">Business Impact</label>
                <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                  <div class="impact-option itsm-impact-card" data-value="System Outage" onclick="selectImpact(this)">
                    <span class="itsm-icon bg-red-100 text-red-600">${icon('zap')}</span>
                    <div>
                      <p class="text-sm font-semibold text-gray-900">System Outage</p>
                      <p class="text-xs text-gray-500">App / server down</p>
                    </div>
                  </div>
                  <div class="impact-option itsm-impact-card" data-value="User Productivity" onclick="selectImpact(this)">
                    <span class="itsm-icon bg-amber-100 text-amber-600">${icon('clock')}</span>
                    <div>
                      <p class="text-sm font-semibold text-gray-900">User Productivity</p>
                      <p class="text-xs text-gray-500">Login / slow system</p>
                    </div>
                  </div>
                  <div class="impact-option itsm-impact-card" data-value="Security / Access" onclick="selectImpact(this)">
                    <span class="itsm-icon bg-purple-100 text-purple-600">${icon('shield')}</span>
                    <div>
                      <p class="text-sm font-semibold text-gray-900">Security / Access</p>
                      <p class="text-xs text-gray-500">Lock / permissions</p>
                    </div>
                  </div>
                  <div class="impact-option itsm-impact-card" data-value="Service Request" onclick="selectImpact(this)">
                    <span class="itsm-icon bg-blue-100 text-blue-600">${icon('clipboard-list')}</span>
                    <div>
                      <p class="text-sm font-semibold text-gray-900">Service Request</p>
                      <p class="text-xs text-gray-500">New / setup</p>
                    </div>
                  </div>
                </div>
                <input type="hidden" id="impactArea" name="impactArea" value="">
              </div>

              <div class="mb-6">
                <label class="itsm-label">Priority</label>
                <div class="flex flex-wrap gap-2">
                  <button type="button" class="priority-option itsm-priority-pill selected" data-value="P4" onclick="selectPriority(this)">Low</button>
                  <button type="button" class="priority-option itsm-priority-pill" data-value="P3" onclick="selectPriority(this)">Medium</button>
                  <button type="button" class="priority-option itsm-priority-pill" data-value="P2" onclick="selectPriority(this)">High</button>
                  <button type="button" class="priority-option itsm-priority-pill" data-value="P1" onclick="selectPriority(this)">Critical</button>
                </div>
                <input type="hidden" id="priority" name="priority" value="P4">
              </div>

              <div>
                <label id="criticalCheckbox" onclick="toggleCritical()" class="critical-checkbox itsm-critical">
                  <div id="criticalCheckIcon">
                    <svg class="w-3.5 h-3.5 text-white hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
                    </svg>
                  </div>
                  <div>
                    <p class="text-sm font-semibold text-gray-900">Mark as Critical</p>
                    <p class="text-xs text-gray-500">Requires immediate attention</p>
                  </div>
                </label>
                <input type="hidden" id="criticalFlag" name="criticalFlag" value="false">
              </div>
            </div>

            <!-- Attachments -->
            <div class="mb-10">
              <div class="itsm-section-label">
                <span class="itsm-icon">${icon('paperclip')}</span>
                Attachments
              </div>
              <div class="upload-zone itsm-upload-zone" id="attachmentUpload" onclick="document.getElementById('attachmentInput').click()">
                <input type="file" id="attachmentInput" class="upload-input" accept="image/*" multiple onchange="handleAttachments(this.files)">
                <svg class="w-8 h-8 mx-auto text-gray-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                </svg>
                <p class="text-sm text-gray-600 font-medium">Click or drag images here</p>
                <p class="text-xs text-gray-400 mt-1">JPEG, PNG, GIF • up to 3 images, auto-compressed</p>
              </div>
              <div id="attachmentPreview" class="upload-preview"></div>
              <p id="attachmentMsg" class="text-xs mt-1 hidden"></p>
            </div>

            <!-- Submit -->
            <div class="pt-6 border-t border-gray-100">
              <button type="submit" id="submitBtn" disabled class="btn-primary itsm-btn-primary w-full">Submit Incident</button>
              <p class="text-xs text-gray-400 text-center mt-3">
                By submitting, you agree to our
                <button type="button" onclick="openTermsModal()" class="text-blue-600 hover:underline">IT support terms and conditions</button>
              </p>
            </div>

          </form>
        </div>
      </section>

      <!-- Track Section -->
      <section id="trackSection" class="hidden">
        <div class="itsm-card">
          <div class="itsm-incident-bar">
            <div>
              <p class="itsm-incident-id">TRACK INCIDENT</p>
              <h2 class="itsm-title">Ticket Status</h2>
              <p class="itsm-subtitle">Enter your ticket ID to view real-time updates.</p>
            </div>
          </div>

          <div class="itsm-gray-box flex flex-col sm:flex-row gap-3">
            <input type="text" id="searchInput" class="form-input itsm-input flex-1" placeholder="e.g. SSPTKT-001">
            <button onclick="performSearch()" class="btn-primary itsm-btn-primary px-8">Search</button>
          </div>

          <div id="trackResult" class="hidden mt-6"></div>
          <div id="noResult" class="hidden mt-6 text-center py-8">
            <svg class="w-12 h-12 mx-auto text-gray-300 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <h3 class="text-base font-semibold text-gray-900">Ticket not found</h3>
            <p class="text-xs text-gray-500 mt-1">Please check your ticket ID and try again</p>
          </div>
        </div>
      </section>

    </div>
  </main>
`;

// Split original into parts
const bodyMatch = html.match(/<body[^>]*>/);
const mainMatch = html.match(/<\/main>/);
if (!bodyMatch || !mainMatch) throw new Error('Could not locate <body> or </main>');

const head = html.slice(0, bodyMatch.index);
const rest = html.slice(mainMatch.index + 7);

// Modify head: add override CSS inside existing <style> and add Lucide before </head>
const styleEnd = head.lastIndexOf('</style>');
if (styleEnd === -1) throw new Error('Could not locate </style> in head');
const headWithCss = head.slice(0, styleEnd) + '\n' + overrideCss + '\n' + head.slice(styleEnd);
const headWithLucide = headWithCss.replace('</head>', '  <script src="https://unpkg.com/lucide@latest/dist/umd/lucide.min.js"></script>\n</head>');

// Add Lucide init before closing body tag
const restWithLucide = rest.replace('</body>', '  <script>if (typeof lucide !== \'undefined\') lucide.createIcons();</script>\n</body>');

const newHtml = headWithLucide + newBody + restWithLucide;
fs.writeFileSync(indexPath, newHtml, 'utf8');
console.log('public/index.html redesign applied.');
