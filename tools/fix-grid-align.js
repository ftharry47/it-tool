const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Make issue type select 46px to match critical checkbox
html = html.replace(
  /\.itsm-select \{\s*appearance: none;/,
  '.itsm-select {\n  height: 46px !important;\n  appearance: none;'
);

// 2. Remove the flex-column trick from grid fields and zero out margin for alignment
html = html.replace(
  /\.itsm-grid > \.itsm-field \{\s*display: flex;\s*flex-direction: column;\s*\}\s*\.itsm-grid > \.itsm-field > \.itsm-critical \{\s*flex: 1;\s*\}/,
  '.itsm-grid > .itsm-field {\n  margin-bottom: 0;\n}'
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Grid alignment applied.');
