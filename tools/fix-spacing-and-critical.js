const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Improve terms section spacing
const oldTermsCss = /\.terms-content \{ display: flex; flex-direction: column; gap: 0\.5rem; \}\s*\.terms-updated \{ font-size: 0\.75rem; color: #94a3b8; margin-bottom: 0\.25rem; \}\s*\.terms-list \{ display: flex; flex-direction: column; \}\s*\.terms-item \{ display: flex; gap: 1rem; padding: 1rem 1\.25rem; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 0\.75rem; margin-bottom: 0\.75rem; box-shadow: 0 1px 2px rgba\(0,0,0,0\.04\); transition: transform 0\.2s ease, box-shadow 0\.2s ease; \}\s*\.terms-item:hover \{ transform: translateY\(-1px\); box-shadow: 0 4px 12px rgba\(0,0,0,0\.06\); \}\s*\.terms-item:last-child \{ margin-bottom: 0; \}\s*\.terms-number \{ width: 2rem; height: 2rem; border-radius: 50%; background: var\(--primary-100\); color: var\(--primary-700\); display: flex; align-items: center; justify-content: center; font-size: 0\.875rem; font-weight: 700; flex-shrink: 0; \}\s*\.terms-item h4 \{ color: #1e293b; font-size: 0\.95rem; font-weight: 600; margin: 0 0 0\.35rem 0; \}\s*\.terms-item p, \.terms-item ul \{ color: #475569; font-size: 0\.875rem; line-height: 1\.5; margin: 0; \}\s*\.terms-item ul \{ list-style: none; padding-left: 0; \}\s*\.terms-item ul li \{ position: relative; padding-left: 1\.25rem; margin-bottom: 0\.35rem; \}\s*\.terms-item ul li::before \{ content: '•'; position: absolute; left: 0\.25rem; color: var\(--primary-500\); font-weight: 700; \}\s*\.terms-item ul li:last-child \{ margin-bottom: 0; \}/;

const newTermsCss = `.terms-content { display: flex; flex-direction: column; gap: 1rem; padding: 0.5rem; }
    .terms-updated { font-size: 0.75rem; color: #94a3b8; margin-bottom: 0.5rem; }
    .terms-list { display: flex; flex-direction: column; gap: 1rem; }
    .terms-item { display: flex; gap: 1.25rem; padding: 1.25rem 1.5rem; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 0.75rem; box-shadow: 0 1px 2px rgba(0,0,0,0.04); transition: transform 0.2s ease, box-shadow 0.2s ease; }
    .terms-item:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,0.06); }
    .terms-number { width: 2.25rem; height: 2.25rem; border-radius: 50%; background: var(--primary-100); color: var(--primary-700); display: flex; align-items: center; justify-content: center; font-size: 0.875rem; font-weight: 700; flex-shrink: 0; }
    .terms-item h4 { color: #1e293b; font-size: 1rem; font-weight: 600; margin: 0 0 0.5rem 0; }
    .terms-item p, .terms-item ul { color: #475569; font-size: 0.9rem; line-height: 1.6; margin: 0; }
    .terms-item ul { list-style: none; padding-left: 0; display: flex; flex-direction: column; gap: 0.5rem; }
    .terms-item ul li { position: relative; padding-left: 1.25rem; }
    .terms-item ul li::before { content: '•'; position: absolute; left: 0.25rem; color: var(--primary-500); font-weight: 700; }`;

html = html.replace(oldTermsCss, newTermsCss);

// 2. Fix critical checkbox alignment by adding a label and cleaning indentation
const criticalPattern = /              <div class="itsm-field" >\s*<label id="criticalCheckbox" class="critical-checkbox itsm-critical" onclick="toggleCritical\(\)">\s*<div class="itsm-cb-box" id="criticalCheckIcon">\s*<svg class="itsm-cb-check hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"><\/path><\/svg>\s*<\/div>\s*<div>\s*<div class="itsm-cb-label">Mark as Critical<\/div>\s*<div class="itsm-cb-hint">Requires immediate attention<\/div>\s*<\/div>\s*<\/label>\s*<input type="hidden" id="criticalFlag" name="criticalFlag" value="false">\s*<\/div>/;

const newCritical = `              <div class="itsm-field">
                <label class="itsm-label">Urgency</label>
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
              </div>`;

html = html.replace(criticalPattern, newCritical);

// 3. Make grid fields flex columns so the critical button fills the cell height
if (!html.includes('.itsm-grid > .itsm-field')) {
  html = html.replace(
    '.itsm-critical {\n  display: flex;',
    '.itsm-grid > .itsm-field {\n  display: flex;\n  flex-direction: column;\n}\n.itsm-grid > .itsm-field > .itsm-critical {\n  flex: 1;\n}\n\n.itsm-critical {\n  display: flex;'
  );
}

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Spacing and critical alignment applied.');
