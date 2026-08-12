const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// Increase track result card padding/spacing for more length
html = html.replace(
  '<div class="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden"><div class="px-4 sm:px-6 py-4 bg-gradient-to-r from-blue-600 to-indigo-600',
  '<div class="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden"><div class="px-4 sm:px-6 py-5 bg-gradient-to-r from-blue-600 to-indigo-600'
);

html = html.replace(
  '<div class="px-4 sm:px-6 py-3 bg-gray-50 border-b border-gray-100">\' + statusProgressHtml + \'</div><div class="p-4 sm:p-6">',
  '<div class="px-4 sm:px-6 py-4 bg-gray-50 border-b border-gray-100">\' + statusProgressHtml + \'</div><div class="p-6 sm:p-8">'
);

html = html.replace(
  '<div class="mt-4 pt-4 border-t border-gray-100"><p class="text-xs text-gray-500 mb-1">Description',
  '<div class="mt-6 pt-6 border-t border-gray-100"><p class="text-xs text-gray-500 mb-2">Description'
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Track result card length increased.');
