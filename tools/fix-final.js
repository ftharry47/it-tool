const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Remove green circle from validation success icon and use a green checkmark
html = html.replace(
  /var validIcon = '<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" fill="#22c55e"><\/circle><path stroke="white" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"><\/path><\/svg>';/,
  "var validIcon = '<svg class=\"w-5 h-5\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#22c55e\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M5 13l4 4L19 7\"></path></svg>';"
);

// 2. Add footer CSS before the autoSaveIndicator rule
const footerCss = `
.itsm-footer {
  border-top: 1px solid #E5E7EB;
  background: #FFFFFF;
  padding: 16px 24px;
  margin-top: 24px;
}
.itsm-footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6B7280;
}
.itsm-footer-inner p {
  margin: 0;
  line-height: 1.4;
}
@media (min-width: 640px) {
  .itsm-footer-inner {
    flex-direction: row;
    justify-content: space-between;
  }
}

`;
html = html.replace('#autoSaveIndicator {', footerCss + '#autoSaveIndicator {');

// 3. Reduce form spacing further
html = html
  .replace(/\.itsm-page \{\s*max-width: 1280px;\s*margin: 0 auto;\s*padding: 24px;/, '.itsm-page {\n  max-width: 1280px;\n  margin: 0 auto;\n  padding: 20px;')
  .replace(/\.itsm-container \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 24px;/, '.itsm-container {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 20px;')
  .replace(/\.itsm-page-header \{\s*margin-bottom: 24px;/, '.itsm-page-header {\n  margin-bottom: 16px;')
  .replace(/\.itsm-section \{\s*margin-bottom: 24px;/, '.itsm-section {\n  margin-bottom: 16px;')
  .replace(/padding-bottom: 10px;\s*margin-bottom: 16px;/, 'padding-bottom: 8px;\n  margin-bottom: 12px;')
  .replace(/\.itsm-field \{\s*margin-bottom: 16px;/, '.itsm-field {\n  margin-bottom: 12px;')
  .replace(/\.itsm-textarea \{\s*height: 140px;/, '.itsm-textarea {\n  height: 120px;')
  .replace(/\.itsm-upload \{\s*border: 2px dashed #CBD5E1;\s*border-radius: 8px;\s*background: #F8FAFC;\s*padding: 20px;/, '.itsm-upload {\n  border: 2px dashed #CBD5E1;\n  border-radius: 8px;\n  background: #F8FAFC;\n  padding: 16px;')
  .replace(/\.itsm-impact \{\s*height: 48px;\s*display: flex;/, '.itsm-impact {\n  height: 44px;\n  display: flex;')
  .replace(/\.itsm-impact-grid \{\s*display: grid;\s*grid-template-columns: repeat\(1, 1fr\);\s*gap: 12px;/, '.itsm-impact-grid {\n  display: grid;\n  grid-template-columns: repeat(1, 1fr);\n  gap: 8px;')
  .replace(/\.itsm-priority \{\s*flex: 1 1 auto;\s*min-width: 70px;\s*height: 34px;\s*padding: 0 14px;/, '.itsm-priority {\n  flex: 1 1 auto;\n  min-width: 60px;\n  height: 34px;\n  padding: 0 10px;')
  .replace(/\.itsm-help-card \{\s*background: #FFFFFF;\s*border: 1px solid #E5E7EB;\s*border-radius: 6px;\s*padding: 20px;/, '.itsm-help-card {\n  background: #FFFFFF;\n  border: 1px solid #E5E7EB;\n  border-radius: 6px;\n  padding: 16px;')
  .replace(/\.itsm-help-item \{\s*padding: 10px 0;/, '.itsm-help-item {\n  padding: 8px 0;')
  .replace(/\.itsm-info-row \{\s*display: flex;\s*align-items: flex-start;\s*gap: 12px;\s*margin-bottom: 12px;/, '.itsm-info-row {\n  display: flex;\n  align-items: flex-start;\n  gap: 12px;\n  margin-bottom: 8px;');

// 4. Remove the entire help grid to reduce page length
const helpGridPattern = /\s*<div class="itsm-help-grid">[\s\S]*?<\/div>\s*<\/section>/;
html = html.replace(helpGridPattern, '\n    </section>');

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Final fixes applied.');
