#!/usr/bin/env node

/**
 * Quick smoke test for the IT Support Portal.
 * Finds the server on the first available port and checks
 * key pages, APIs, and the auto-assignment flow.
 */

const http = require('http');
const path = require('path');

const PORTS = [3000, 55000, 55001, 56000, 57000, 58000, 59000];

function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let body = '';
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body }));
    }).on('error', reject);
  });
}

function httpPostJson(url, data) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(data);
    const req = http.request(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, (res) => {
      let body = '';
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

async function findPort() {
  for (const port of PORTS) {
    try {
      const { status } = await httpGet(`http://localhost:${port}/`);
      if (status === 200) return port;
    } catch {
      // try next port
    }
  }
  throw new Error('Could not find a running IT Support Portal instance on the expected ports.');
}

async function run() {
  const port = await findPort();
  const base = `http://localhost:${port}`;
  console.log(`Smoke testing ${base}...\n`);

  const pages = [
    '/', '/admin', '/analytics', '/cmdb', '/import-export',
    '/live', '/roles', '/kb', '/help-bot', '/audit', '/csat',
    '/offline.html', '/manifest.json', '/sw.js'
  ];

  let failures = 0;

  for (const page of pages) {
    try {
      const { status } = await httpGet(`${base}${page}`);
      if (status === 200) {
        console.log(`  [OK] ${page}`);
      } else {
        console.log(`  [FAIL] ${page} -> HTTP ${status}`);
        failures++;
      }
    } catch (e) {
      console.log(`  [FAIL] ${page} -> ${e.message}`);
      failures++;
    }
  }

  // Check API
  try {
    const { status: statusStatus } = await httpGet(`${base}/status`);
    console.log(`  [${statusStatus === 200 ? 'OK' : 'FAIL'}] /status`);
    if (statusStatus !== 200) failures++;
  } catch (e) {
    console.log(`  [FAIL] /status -> ${e.message}`);
    failures++;
  }

  // Check auto-assign toggle API
  try {
    const { status: toggleStatus } = await httpPostJson(`${base}/api/toggleAutoAssign`, { args: [true, 'SmokeTest'] });
    console.log(`  [${toggleStatus === 200 ? 'OK' : 'FAIL'}] POST /api/toggleAutoAssign`);
    if (toggleStatus !== 200) failures++;
  } catch (e) {
    console.log(`  [FAIL] POST /api/toggleAutoAssign -> ${e.message}`);
    failures++;
  }

  // Create a test ticket
  try {
    const { status: submitStatus, body: submitBody } = await httpPostJson(`${base}/api/submitTicket`, {
      args: [{ email: 'smoke@test.local', phone: '555-0100', location: 'Smoke Lab', shortDescription: 'Smoke test ticket' }]
    });
    const ticket = JSON.parse(submitBody);
    if (submitStatus === 200 && ticket.success) {
      console.log(`  [OK] POST /api/submitTicket -> ticketId: ${ticket.ticketId}${ticket.assignedTo ? `, assigned to ${ticket.assignedTo}` : ''}`);
    } else {
      console.log(`  [FAIL] POST /api/submitTicket -> HTTP ${submitStatus}`);
      failures++;
    }
  } catch (e) {
    console.log(`  [FAIL] POST /api/submitTicket -> ${e.message}`);
    failures++;
  }

  console.log(`\n${failures === 0 ? 'All smoke tests passed.' : `${failures} failure(s).`}`);
  process.exit(failures === 0 ? 0 : 1);
}

if (require.main === module) {
  run().catch((e) => {
    console.error('Smoke test error:', e.message);
    process.exit(1);
  });
}
