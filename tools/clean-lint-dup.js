const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// Remove empty success-checkmark rulesets
html = html.replace(
  /\.success-checkmark \{\s*\/\* No animation \*\/\s*\}\s*\.success-checkmark svg \{\s*\/\* No draw animation \*\/\s*\}/g,
  ''
);

// Remove duplicate terms modal theme block
const themePattern = /\n\n    \/\* Relaxed terms modal theme matching the form page \*\/\n    #termsModal \.card-header \{\s*background: #ffffff !important;\s*border-bottom: 1px solid #E5E7EB;\s*color: var\(--primary-800\) !important;\s*\}\s*#termsModal \.card-header h3 \{\s*color: var\(--primary-800\) !important;\s*\}\s*#termsModal \.card-header button \{\s*color: var\(--primary-600\);\s*background: transparent;\s*\}\s*#termsModal \.card-header button:hover \{\s*background: var\(--primary-50\);\s*color: var\(--primary-800\);\s*\}\s*#termsModal \.bg-gray-50 \{\s*background: #F8FAFC !important;\s*\}/;

// Keep the first occurrence, remove the second
const firstMatch = html.match(themePattern);
if (firstMatch) {
  const start = firstMatch.index;
  const afterFirst = html.slice(start + firstMatch[0].length);
  const secondMatch = afterFirst.match(themePattern);
  if (secondMatch) {
    const secondStart = start + firstMatch[0].length + secondMatch.index;
    html = html.slice(0, secondStart) + html.slice(secondStart + secondMatch[0].length);
  }
}

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Lint cleanup and duplicate removal applied.');
