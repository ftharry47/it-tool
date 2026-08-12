const base = process.env.VERIFY_BASE || 'http://localhost:3000';
const results = [];

function post(fn, body) {
  return fetch(`${base}/api/${fn}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ args: body })
  }).then(r => r.json());
}

function get(path) {
  return fetch(`${base}${path}`).then(r => ({ ok: r.ok, status: r.status }));
}

function ok(label, condition, detail = '') {
  const status = condition ? 'PASS' : 'FAIL';
  results.push({ label, status, detail });
  console.log(`[${status}] ${label}${detail ? ' - ' + detail : ''}`);
}

(async () => {
  console.log('--- Pages ---');
  const root = await get('/');
  const dash = await get('/dashboard');
  const st = await get('/status');
  ok('GET / form page', root.ok, root.status);
  ok('GET /dashboard page', dash.ok, dash.status);
  ok('GET /status endpoint', st.ok, st.status);

  console.log('\n--- System ---');
  const sys = await post('diagnoseSystem', []);
  ok('diagnoseSystem', sys.formEnabled !== undefined, `form=${sys.formEnabled}, dashboard=${sys.dashboardEnabled}, v=${sys.version}`);

  console.log('\n--- Submit & Track ---');
  const submit = await post('submitTicket', [{
    name: 'Azure Redeploy Test',
    email: 'azure@alignedcardio.com',
    phone: '(555) 987-6543',
    location: 'JRC - Discovery',
    shortDescription: 'Pre-deployment verification ticket',
    issueType: 'Hardware',
    workMode: 'User Productivity',
    priority: 'Low',
    criticalFlag: true,
    attachments: []
  }]);
  ok('submitTicket', submit.success, submit.ticketId);

  const ticketId = submit.ticketId;
  if (ticketId) {
    const track = await post('getTicketByIdForTracking', [ticketId]);
    ok('getTicketByIdForTracking', track.success, track.ticket && track.ticket['Ticket ID'] + ' / ' + track.ticket['Status']);

    console.log('\n--- Ticket Updates ---');
    ok('addTicketNote', (await post('addTicketNote', [ticketId, 'Verification note', 'System Tester', 'General'])).success);
    ok('getTicketNotes', (await post('getTicketNotes', [ticketId])).length > 0);
    ok('updateIssueType', (await post('updateIssueType', [ticketId, 'Network'])).success);
    ok('updateImpactArea', (await post('updateImpactArea', [ticketId, 'Clinical Operations'])).success);
    ok('updatePhoneNumber', (await post('updatePhoneNumber', [ticketId, '(555) 111-2222'])).success);
    ok('updateLocation', (await post('updateLocation', [ticketId, 'JRC - Research'])).success);
    ok('getTicketTimeline', (await post('getTicketTimeline', [ticketId])).length > 0);

    console.log('\n--- Dashboard & Reports ---');
    const dashboard = await post('getDashboardData', [false]);
    ok('getDashboardData', dashboard.lastUpdated !== undefined, `tickets=${dashboard.tickets.length}, staff=${dashboard.itStaff.length}, stats=${Object.keys(dashboard.stats).length}`);

    const monthly = await post('generateReport', ['monthly']);
    ok('generateReport monthly', monthly.success, `records=${monthly.recordCount}`);
    const allTickets = await post('generateReport', ['tickets']);
    ok('generateReport tickets', allTickets.success, `records=${allTickets.recordCount}`);

    console.log('\n--- Settings ---');
    const settings = await post('getAllSettings', []);
    ok('getAllSettings', settings.FORM_ENABLED !== undefined);
    ok('getAdminEmails', (await post('getAdminEmails', [])).primary !== undefined);
    ok('getAutoAssignSetting', (await post('getAutoAssignSetting', [])) !== undefined);
  }

  const passed = results.filter(r => r.status === 'PASS').length;
  const failed = results.filter(r => r.status === 'FAIL').length;
  console.log(`\n=== Result: ${passed} passed, ${failed} failed ===`);
  if (failed > 0) process.exit(1);
})();
