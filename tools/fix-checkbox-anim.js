const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Make critical checkbox slightly bigger and match issue type select height
html = html
  .replace(/\.itsm-critical \{\s*display: flex;\s*align-items: center;\s*justify-content: center;\s*gap: 10px;\s*width: 100%;\s*min-height: 44px;\s*padding: 8px 16px;/, '.itsm-critical {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 10px;\n  width: 100%;\n  min-height: 46px;\n  padding: 12px 16px;')
  .replace(/\.itsm-cb-box \{\s*width: 18px;\s*height: 18px;/, '.itsm-cb-box {\n  width: 20px;\n  height: 20px;')
  .replace(/\.itsm-cb-label \{\s*font-size: 14px;\s*font-weight: 600;/, '.itsm-cb-label {\n  font-size: 15px;\n  font-weight: 600;');

// 2. Remove the remaining success-checkmark animation block and its keyframes
html = html.replace(
  /\.success-checkmark \{\s*animation: checkmarkPop 0\.5s cubic-bezier\(0\.175, 0\.885, 0\.32, 1\.275\) both;\s*\}\s*\.success-checkmark path \{\s*stroke-dasharray: 24;\s*stroke-dashoffset: 24;\s*animation: checkmarkDraw 0\.45s ease-out 0\.2s forwards;\s*\}\s*@keyframes checkmarkPop \{\s*from \{ opacity: 0; transform: scale\(0\.5\); \}\s*to \{ opacity: 1; transform: scale\(1\); \}\s*\}\s*@keyframes checkmarkDraw \{\s*to \{ stroke-dashoffset: 0; \}\s*\}/,
  '.success-checkmark {\n  /* No animation */\n}\n.success-checkmark path {\n  /* No draw animation */\n}'
);

// 3. Remove validation-icon path draw animation
html = html.replace(
  /\.validation-icon path \{\s*stroke-dasharray: 22;\s*stroke-dashoffset: 22;\s*animation: drawCheck 0\.3s ease-out forwards;\s*\}\s*@keyframes drawCheck \{\s*to \{ stroke-dashoffset: 0; \}\s*\}/,
  '.validation-icon path {\n  /* No draw animation */\n}'
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Checkbox and tick animation fixes applied.');
