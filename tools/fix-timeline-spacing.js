const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Timeline item spacing
html = html.replace(
  /return '<div class="flex gap-3"><div class="flex flex-col items-center"><div class="w-7 h-7 rounded-full ' \+ iconBg \+ ' flex items-center justify-center flex-shrink-0">' \+ iconClass \+ '<\/div>' \+ \(!isLast ? '<div class="w-0\.5 bg-gray-200 flex-1 my-1"><\/div>' : ''\) \+ '<\/div><div class="flex-1 pb-3 min-w-0"><p class="text-sm font-medium text-gray-900">' \+ action \+ '<\/p><p class="text-xs text-gray-500 mt-0\.5 break-words">' \+ \(notes \|\| fromTo\) \+ '<\/p><p class="text-xs text-gray-400 mt-0\.5">' \+ formatDate\(timestamp\) \+ ' • ' \+ performedBy \+ '<\/p><\/div><\/div>'/,
  'return \'<div class="flex gap-4"><div class="flex flex-col items-center"><div class="w-7 h-7 rounded-full \' + iconBg + \' flex items-center justify-center flex-shrink-0">\' + iconClass + \'</div>\' + (!isLast ? \'<div class="w-0.5 bg-gray-200 flex-1 my-2"></div>\' : \'\') + \'</div><div class="flex-1 pb-5 min-w-0"><p class="text-sm font-medium text-gray-900">\' + action + \'</p><p class="text-xs text-gray-500 mt-1 break-words">\' + (notes || fromTo) + \'</p><p class="text-xs text-gray-400 mt-1">\' + formatDate(timestamp) + \' • \' + performedBy + \'</p></div></div>\''
);

// 2. Empty timeline item spacing
html = html.replace(
  /timelineHtml = '<div class="flex gap-3"><div class="flex flex-col items-center"><div class="w-7 h-7 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0"><svg class="w-3\.5 h-3\.5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"><\/path><\/svg><\/div><\/div><div class="flex-1 min-w-0"><p class="text-sm font-medium text-gray-900">Ticket Created<\/p><p class="text-xs text-gray-500 mt-0\.5 break-words">' \+ shortDesc \+ '<\/p><p class="text-xs text-gray-400 mt-0\.5">' \+ formatDate\(createdDate\) \+ ' • ' \+ userName \+ '<\/p><\/div><\/div>'/,
  'timelineHtml = \'<div class="flex gap-4"><div class="flex flex-col items-center"><div class="w-7 h-7 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0"><svg class="w-3.5 h-3.5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg></div></div><div class="flex-1 min-w-0"><p class="text-sm font-medium text-gray-900">Ticket Created</p><p class="text-xs text-gray-500 mt-1 break-words">\' + shortDesc + \'</p><p class="text-xs text-gray-400 mt-1">\' + formatDate(createdDate) + \' • \' + userName + \'</p></div></div>\''
);

// 3. Activity card wrapper spacing
html = html.replace(
  /'<div class="bg-white rounded-lg border border-gray-200 shadow-sm mt-4"><div class="px-4 sm:px-6 py-3 border-b border-gray-200"><h3 class="text-sm font-semibold text-gray-900 flex items-center gap-2">/,
  '\'<div class="bg-white rounded-lg border border-gray-200 shadow-sm mt-6"><div class="px-4 sm:px-6 py-4 border-b border-gray-200"><h3 class="text-base font-semibold text-gray-900 flex items-center gap-2">'
);

html = html.replace(
  /<\/h3><\/div><div class="p-4 sm:p-6">' \+ timelineHtml \+ '<\/div><\/div>/,
  '</h3></div><div class="p-6 sm:p-8">\' + timelineHtml + \'</div></div>'
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Timeline spacing applied.');
