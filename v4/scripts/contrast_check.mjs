import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Read HSL tokens directly from index.css so the check stays in sync with the source.
const css = readFileSync(join(__dirname, '../src/main/frontend/src/index.css'), 'utf8');

function parseHsl(hsl) {
  const m = hsl.match(/(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%/);
  if (!m) throw new Error(`Cannot parse HSL: ${hsl}`);
  return { h: parseFloat(m[1]), s: parseFloat(m[2]) / 100, l: parseFloat(m[3]) / 100 };
}

function hslToRgb({ h, s, l }) {
  const k = (n) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return [f(0), f(8), f(4)].map((v) => Math.round(v * 255));
}

function relativeLuminance(rgb) {
  const toLinear = (c) => {
    c = c / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  const [r, g, b] = rgb.map(toLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
  const l1 = relativeLuminance(a) + 0.05;
  const l2 = relativeLuminance(b) + 0.05;
  return Math.max(l1, l2) / Math.min(l1, l2);
}

function tokenValue(name, isDark) {
  const block = isDark
    ? css.match(/\.dark\s*\{([^}]*)\}/)?.[1] ?? ''
    : css.match(/:root\s*\{([^}]*)\}/)?.[1] ?? '';
  const m = new RegExp(`\\b${name}:\\s*([^;]+);`).exec(block);
  if (!m) throw new Error(`Token not found: ${name}`);
  return hslToRgb(parseHsl(m[1].trim()));
}

const modes = [
  { name: 'light', dark: false },
  { name: 'dark', dark: true },
];

const checks = [
  { fg: 'foreground', bg: 'background', context: 'Body text' },
  { fg: 'primary-foreground', bg: 'primary', context: 'Primary button' },
  { fg: 'secondary-foreground', bg: 'secondary', context: 'Secondary button / status badge' },
  { fg: 'destructive-foreground', bg: 'destructive', context: 'Destructive button' },
  { fg: 'muted-foreground', bg: 'background', context: 'Muted/body-secondary text on page' },
  { fg: 'muted-foreground', bg: 'muted', context: 'Muted text on muted surface' },
  { fg: 'muted-foreground', bg: 'card', context: 'Muted text on card' },
  { fg: 'card-foreground', bg: 'card', context: 'Card text' },
  { fg: 'input', bg: 'background', context: 'Input border on background', large: true },
  { fg: 'foreground', bg: 'background', context: 'Input text on input background' },
  { fg: 'primary-foreground', bg: 'primary', context: 'Primary link on page', large: true },
  { fg: 'border', bg: 'background', context: 'Border on background', large: true },
];

let failures = [];
let passes = [];

console.log('\n=== WCAG 2.1 AA static contrast check ===\n');

for (const { name, dark } of modes) {
  console.log(`--- ${name} mode ---`);
  for (const check of checks) {
    const fg = tokenValue(check.fg, dark);
    const bg = tokenValue(check.bg, dark);
    const ratio = contrast(fg, bg);
    const threshold = check.large ? 3.0 : 4.5;
    const status = ratio >= threshold ? 'PASS' : 'FAIL';
    const target = check.large ? '3:1' : '4.5:1';
    const line = `  ${check.context.padEnd(40)} ${ratio.toFixed(2)}:1  (target ${target})  [${status}]`;
    console.log(line);
    if (ratio < threshold) {
      failures.push({ mode: name, ...check, ratio });
    } else {
      passes.push({ mode: name, ...check, ratio });
    }
  }
}

console.log('\n=== Summary ===');
console.log(`  Failures: ${failures.length}`);
console.log(`  Passes:   ${passes.length}`);

if (failures.length > 0) {
  console.log('\nFailing pairs:');
  for (const f of failures) {
    console.log(`  [${f.mode}] ${f.context}: ${f.ratio.toFixed(2)}:1 (fg=${f.fg}, bg=${f.bg})`);
  }
  process.exitCode = 1;
} else {
  console.log('\nAll checked color pairs meet WCAG 2.1 AA.');
  process.exitCode = 0;
}
