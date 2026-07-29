const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Move priority field into the issue type grid
const priorityField = `              <div class="itsm-field">
                <label class="itsm-label">Priority</label>
                <div class="itsm-priority-bar">
                  <button type="button" class="priority-option itsm-priority selected" data-value="P4" onclick="selectPriority(this)">Low</button>
                  <button type="button" class="priority-option itsm-priority" data-value="P3" onclick="selectPriority(this)">Medium</button>
                  <button type="button" class="priority-option itsm-priority" data-value="P2" onclick="selectPriority(this)">High</button>
                  <button type="button" class="priority-option itsm-priority" data-value="P1" onclick="selectPriority(this)">Critical</button>
                </div>
                <input type="hidden" id="priority" name="priority" value="P4">
              </div>`;

html = html.replace('              <div></div>', priorityField);

// 2. Remove old priority field
const oldPriorityPattern = /            <div class="itsm-field" style="margin-top:20px;">\s*<label class="itsm-label">Priority<\/label>\s*<div class="itsm-priority-bar">[\s\S]*?<\/div>\s*<input type="hidden" id="priority" name="priority" value="P4">\s*<\/div>/;
html = html.replace(oldPriorityPattern, '');

// 3. Reduce vertical spacing (slightly)
html = html
  .replace(/\.itsm-page \{\s*max-width: 1280px;\s*margin: 0 auto;\s*padding: 32px;/, '.itsm-page {\n  max-width: 1280px;\n  margin: 0 auto;\n  padding: 24px;')
  .replace(/\.itsm-container \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 32px;/, '.itsm-container {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 24px;')
  .replace(/\.itsm-page-header \{\s*margin-bottom: 32px;/, '.itsm-page-header {\n  margin-bottom: 24px;')
  .replace(/\.itsm-section \{\s*margin-bottom: 32px;/, '.itsm-section {\n  margin-bottom: 24px;')
  .replace(/padding-bottom: 12px;\s*margin-bottom: 20px;/, 'padding-bottom: 10px;\n  margin-bottom: 16px;')
  .replace(/\.itsm-field \{\s*margin-bottom: 20px;/, '.itsm-field {\n  margin-bottom: 16px;')
  .replace(/\.itsm-textarea \{\s*height: 160px;/, '.itsm-textarea {\n  height: 140px;')
  .replace(/\.itsm-upload \{\s*border: 2px dashed #CBD5E1;\s*border-radius: 8px;\s*background: #F8FAFC;\s*padding: 28px;/, '.itsm-upload {\n  border: 2px dashed #CBD5E1;\n  border-radius: 8px;\n  background: #F8FAFC;\n  padding: 20px;')
  .replace(/\.itsm-help-card \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 24px;/, '.itsm-help-card {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 20px;')
  .replace(/\.itsm-help-item \{\s*padding: 12px 0;/, '.itsm-help-item {\n  padding: 10px 0;')
  .replace(/\.itsm-info-row \{\s*display: flex;\s*align-items: flex-start;\s*gap: 12px;\s*margin-bottom: 16px;/, '.itsm-info-row {\n  display: flex;\n  align-items: flex-start;\n  gap: 12px;\n  margin-bottom: 12px;');

// 4. Fix track input/button alignment
html = html
  .replace(/\.itsm-track-row \{\s*display: flex;\s*flex-direction: column;\s*gap: 16px;/, '.itsm-track-row {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 16px;')
  .replace(/@media \(min-width: 640px\) \{\s*\.itsm-track-row \{\s*flex-direction: row;\s*\}\s*\}/, '@media (min-width: 640px) {\n  .itsm-track-row {\n    flex-direction: row;\n  }\n}\n\n.itsm-track-btn {\n  height: 44px !important;\n}')
  .replace(/\.itsm-priority-bar \{\s*display: inline-flex;\s*align-items: center;/, '.itsm-priority-bar {\n  display: flex;\n  flex-wrap: wrap;\n  align-items: center;')
  .replace(/\.itsm-priority \{\s*height: 34px;\s*padding: 0 18px;/, '.itsm-priority {\n  flex: 1 1 auto;\n  min-width: 70px;\n  height: 34px;\n  padding: 0 14px;');

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Layout fixes applied.');
