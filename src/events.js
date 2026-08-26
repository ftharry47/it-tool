const { EventEmitter } = require('events');

const events = new EventEmitter();

function emitActivity(data) {
  events.emit('activity', data);
}

function emitSystem(data) {
  events.emit('system', data);
}

module.exports = { events, emitActivity, emitSystem };
