#!/usr/bin/env node

/**
 * Build the React client without relying on the broken `npm` wrapper.
 * The project root has stale `npm` / `npm.cmd` / `npm.ps1` wrappers that
 * reference a missing `node_modules/npm` package, so this script invokes
 * Vite directly from the client's installed dependencies.
 */

const { spawn } = require('child_process');
const path = require('path');

const viteBin = path.join(__dirname, 'client', 'node_modules', 'vite', 'bin', 'vite.js');
const clientDir = path.join(__dirname, 'client');

const proc = spawn(process.execPath, [viteBin, 'build'], {
  cwd: clientDir,
  stdio: 'inherit'
});

proc.on('close', (code) => {
  process.exit(code || 0);
});
