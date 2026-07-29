const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, '..', 'public', 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');

// 1. Center the processing modal timeline
html = html
  .replace(/\.processing-timeline \{\s*position: relative;\s*z-index: 1;\s*list-style: none;\s*padding: 0;\s*margin: 0;\s*display: flex;\s*flex-direction: column;\s*gap: 0\.875rem;/, '.processing-timeline {\n  position: relative;\n  z-index: 1;\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 0.875rem;')
  .replace(/\.processing-stage \{\s*display: flex;\s*align-items: center;\s*gap: 1rem;/, '.processing-stage {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 1rem;')
  .replace(/\.processing-stage\.active \{\s*color: rgba\(255, 255, 255, 0\.95\);\s*transform: translateX\(4px\);/, '.processing-stage.active {\n  color: rgba(255, 255, 255, 0.95);\n  transform: translateX(0);');

// 2. Fix success modal next-steps alignment
html = html
  .replace(/\.next-steps-card \{\s*margin-top: 1\.25rem;\s*padding: 1rem;/, '.next-steps-card {\n  margin-top: 1.25rem;\n  padding: 1rem;\n  text-align: left;\n  max-width: 320px;\n  margin-left: auto;\n  margin-right: auto;')
  .replace(/\.next-steps-title \{\s*font-size: 0\.875rem;\s*font-weight: 600;\s*color: var\(--primary-800\);\s*margin-bottom: 0\.75rem;\s*display: flex;\s*align-items: center;\s*gap: 0\.5rem;/, '.next-steps-title {\n  font-size: 0.875rem;\n  font-weight: 600;\n  color: var(--primary-800);\n  margin-bottom: 0.75rem;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 0.5rem;')
  .replace(/\.next-step-item \{\s*display: flex;\s*align-items: flex-start;\s*gap: 0\.75rem;\s*padding: 0\.35rem 0;\s*font-size: 0\.875rem;/, '.next-step-item {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 0.75rem;\n  padding: 0.35rem 0;\n  font-size: 0.875rem;');

// 3. Add status-connector animation to track status bars
html = html.replace(
  "'<div class=\"w-4 sm:w-8 h-0.5 ' + (index < currentStepIndex ? 'bg-green-500' : 'bg-gray-200') + '\"></div>'",
  "'<div class=\"w-4 sm:w-8 h-0.5 status-connector ' + (index < currentStepIndex ? 'bg-green-500' : 'bg-gray-200') + '\"></div>'"
);

html = html.replace(
  /\.status-connector \{\s*transition: background-color 0\.3s ease;\s*\}/,
  `.status-connector {
  transition: background-color 0.4s ease, transform 0.4s ease;
  transform-origin: left;
  border-radius: 9999px;
}
.status-connector.bg-green-500 {
  animation: connectorGrow 0.5s ease-out forwards;
}
@keyframes connectorGrow {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}`
);

// 4. Remove green checkmark animation from success modal
html = html.replace(
  /\.success-checkmark \{\s*animation: checkmarkPop 0\.5s ease-out forwards;\s*\}\s*@keyframes checkmarkPop \{\s*0% \{ transform: scale\(0\); opacity: 0; \}\s*50% \{ transform: scale\(1\.2\); \}\s*100% \{ transform: scale\(1\); opacity: 1; \}\s*\}\s*\.success-checkmark svg \{\s*animation: drawCheck 0\.3s ease-out 0\.3s forwards;\s*stroke-dasharray: 30;\s*stroke-dashoffset: 30;\s*\}\s*@keyframes drawCheck \{\s*to \{ stroke-dashoffset: 0; \}\s*\}/,
  '.success-checkmark {\n  /* No animation */\n}\n.success-checkmark svg {\n  /* No draw animation */\n}'
);

// Also remove second occurrence / validation spinner animation
html = html.replace(
  /\.validation-spinner \{\s*animation: validationSpin 0\.8s linear infinite;\s*\}\s*@keyframes validationSpin \{\s*from \{ transform: rotate\(0deg\); \}\s*to \{ transform: rotate\(360deg\); \}\s*\}/,
  '.validation-spinner {\n  animation: none;\n}'
);

fs.writeFileSync(indexPath, html, 'utf8');
console.log('Modal and animation fixes applied.');
