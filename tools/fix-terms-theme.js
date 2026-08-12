const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

const newTheme = `
    /* Relaxed terms modal theme matching the form page */
    #termsModal .card-header {
      background: #ffffff !important;
      border-bottom: 1px solid #E5E7EB;
      color: var(--primary-800) !important;
    }
    #termsModal .card-header h3 {
      color: var(--primary-800) !important;
    }
    #termsModal .card-header button {
      color: var(--primary-600);
      background: transparent;
    }
    #termsModal .card-header button:hover {
      background: var(--primary-50);
      color: var(--primary-800);
    }
    #termsModal .bg-gray-50 {
      background: #F8FAFC !important;
    }
`;

html = html.replace(
  /    \/\* Spacing polish \*\//,
  `${newTheme}\n\n    /* Spacing polish */`
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Terms modal theme updated.');
