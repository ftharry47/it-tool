const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Revert spacing to the moderate layout and reduce page width
html = html
  .replace(/\.itsm-page \{\s*max-width: 1280px;\s*margin: 0 auto;\s*padding: 20px;/, '.itsm-page {\n  max-width: 960px;\n  margin: 0 auto;\n  padding: 24px;')
  .replace(/\.itsm-container \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 20px;/, '.itsm-container {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 24px;')
  .replace(/\.itsm-page-header \{\s*margin-bottom: 16px;/, '.itsm-page-header {\n  margin-bottom: 24px;')
  .replace(/\.itsm-section \{\s*margin-bottom: 16px;/, '.itsm-section {\n  margin-bottom: 24px;')
  .replace(/padding-bottom: 8px;\s*margin-bottom: 12px;/, 'padding-bottom: 10px;\n  margin-bottom: 16px;')
  .replace(/\.itsm-field \{\s*margin-bottom: 12px;/, '.itsm-field {\n  margin-bottom: 16px;')
  .replace(/\.itsm-textarea \{\s*height: 120px;/, '.itsm-textarea {\n  height: 140px;')
  .replace(/\.itsm-upload \{\s*border: 2px dashed #CBD5E1;\s*border-radius: 8px;\s*background: #F8FAFC;\s*padding: 16px;/, '.itsm-upload {\n  border: 2px dashed #CBD5E1;\n  border-radius: 8px;\n  background: #F8FAFC;\n  padding: 20px;')
  .replace(/\.itsm-impact \{\s*height: 44px;\s*display: flex;/, '.itsm-impact {\n  height: 48px;\n  display: flex;')
  .replace(/\.itsm-impact-grid \{\s*display: grid;\s*grid-template-columns: repeat\(1, 1fr\);\s*gap: 8px;/, '.itsm-impact-grid {\n  display: grid;\n  grid-template-columns: repeat(1, 1fr);\n  gap: 12px;')
  .replace(/\.itsm-info-row \{\s*display: flex;\s*align-items: flex-start;\s*gap: 12px;\s*margin-bottom: 8px;/, '.itsm-info-row {\n  display: flex;\n  align-items: flex-start;\n  gap: 12px;\n  margin-bottom: 12px;')
  .replace(/\.itsm-help-card \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 16px;/, '.itsm-help-card {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 20px;')
  .replace(/\.itsm-help-item \{\s*padding: 8px 0;/, '.itsm-help-item {\n  padding: 10px 0;');

// 2. Replace Priority in issue-type grid with Mark as Critical
const oldCriticalPattern = /            <div class="itsm-field" style="margin-top:20px;">\s*<label id="criticalCheckbox" class="critical-checkbox itsm-critical" onclick="toggleCritical\(\)">\s*<div class="itsm-cb-box" id="criticalCheckIcon">\s*<svg class="itsm-cb-check hidden" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"><\/path><\/svg>\s*<\/div>\s*<div>\s*<div class="itsm-cb-label">Mark as Critical<\/div>\s*<div class="itsm-cb-hint">Requires immediate attention<\/div>\s*<\/div>\s*<\/label>\s*<input type="hidden" id="criticalFlag" name="criticalFlag" value="false">\s*<\/div>/;

const criticalMatch = html.match(oldCriticalPattern);
if (!criticalMatch) {
  console.error('Critical block not found');
  process.exit(1);
}

let criticalForGrid = criticalMatch[0]
  .replace(/^            /, '              ')
  .replace(/style="margin-top:20px;"/, '');

const priorityPattern = /              <div class="itsm-field">\s*<label class="itsm-label">Priority<\/label>\s*<div class="itsm-priority-bar">[\s\S]*?<\/div>\s*<input type="hidden" id="priority" name="priority" value="P4">\s*<\/div>/;

html = html.replace(priorityPattern, criticalForGrid);
html = html.replace(oldCriticalPattern, '\n            <input type="hidden" id="priority" name="priority" value="P4">');

// 3. Style Mark as Critical like a button
const criticalButtonCss = `.itsm-critical {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 8px 16px;
  border: 1px solid #D1D5DB;
  border-radius: 6px;
  background: #FFFFFF;
  color: #374151;
  cursor: pointer;
  user-select: none;
  transition: all 150ms;
  box-sizing: border-box;
}
.itsm-critical:hover {
  background: #F3F4F6;
}
.itsm-critical.checked {
  border-color: #C62828;
  background: #FEF2F2;
  color: #C62828;
}
.itsm-cb-hint {
  display: none;
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
}

`;

html = html.replace(
  /\.itsm-critical \{\s*display: inline-flex;\s*align-items: center;\s*gap: 10px;\s*cursor: pointer;\s*user-select: none;\s*\}[\s\S]*?\.itsm-cb-label \{\s*font-size: 14px;\s*font-weight: 600;\s*color: #374151;\s*\}\s*\.itsm-cb-hint \{[\s\S]*?\}\s*/,
  criticalButtonCss
);

// 4. Track row: always row, larger button
const trackCss = `.itsm-track-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}
.itsm-track-input {
  flex: 1 1 220px;
}
.itsm-track-btn {
  flex: 0 0 160px;
  width: auto !important;
  min-width: 160px !important;
  height: 44px !important;
}
`;

html = html.replace(
  /\.itsm-track-row \{\s*display: flex;\s*flex-direction: column;\s*align-items: center;\s*gap: 16px;\s*\}\s*@media \(min-width: 640px\) \{\s*\.itsm-track-row \{\s*flex-direction: row;\s*\}\s*\}\s*\.itsm-track-btn \{\s*height: 44px !important;\s*\}/,
  trackCss
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Iteration fixes applied.');
