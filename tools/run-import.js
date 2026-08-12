const path = require('path');

// Use the same DB path the app uses. Allow DB_PATH to be set externally.
if (!process.env.DB_PATH) {
  process.env.DB_PATH = 'C:\\Users\\SriHariThangavel\\AppData\\Local\\it-tool\\db.json';
}

const db = require('../src/db');

db.withDb(d => {
  const before = d.tickets.length;
  d.tickets = [];
  d.history = [];
  d.notes = [];
  console.log('Cleared', before, 'tickets from', db.DB_PATH);
});

// Import the legacy CSV (toolsusers.csv)
process.argv[2] = path.join(__dirname, 'toolsusers.csv');
require('./import-tickets');
