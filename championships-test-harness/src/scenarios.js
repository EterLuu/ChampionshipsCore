const { EventEmitter } = require('node:events');
const { attachClient } = require('./client');
const { actionProfile } = require('./profiles');

function fakeTimers() {
  const intervals = new Set();
  const timeouts = new Set();
  return {
    setInterval(callback) { intervals.add(callback); return callback; },
    clearInterval(callback) { intervals.delete(callback); },
    setTimeout(callback) { timeouts.add(callback); return callback; },
    clearTimeout(callback) { timeouts.delete(callback); },
    start() {
      for (const callback of [...timeouts]) {
        timeouts.delete(callback);
        callback();
      }
    },
    tick(count = 1) {
      for (let index = 0; index < count; index += 1)
        for (const callback of [...intervals]) callback();
    },
  };
}

function fakeClient() {
  const client = new EventEmitter();
  client.state = 'play';
  client.writes = [];
  client.write = (name, params) => client.writes.push({ name, params });
  client.end = reason => client.emit('end', reason);
  return client;
}

function phasePacket(text) {
  return { content: text };
}

function runGameScenario(game) {
  const timers = fakeTimers();
  const result = { count: 1, loggedIn: 0, actionPackets: 0, fatalErrors: [] };
  const state = { active: false, loggedIn: false, stopRequested: false };
  const settings = { action: 'game', game, password: 'scenario', reconnectAttempts: 1, allowWorldMutation: true };
  let client = fakeClient();
  attachClient(client, settings, 0, result, state, timers);
  client.emit('login', { entityId: 1 });
  client.emit('position', { teleportId: 1, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, flags: {} });
  timers.start();

  client.emit('system_chat', phasePacket('Map voting starts now'));
  client.emit('open_window', { windowId: 2, title: { text: 'Vote for map' } });
  timers.tick(20);
  client.emit('system_chat', phasePacket('Preparation phase'));
  client.emit('action_bar', { text: 'Starting in 3' });
  client.emit('set_title_text', { text: 'GO!' });
  client.emit('entity_spawn', { entityId: 99, type: 'objective' });
  client.emit('entity_destroy', { entityIds: [98] });
  client.emit('block_change', { location: { x: 1, y: 63, z: 1 }, type: 1 });
  client.emit('set_slot', { windowId: 2, slot: 0, stateId: 4, item: { itemCount: 1 } });
  timers.tick(24);
  const actionsBeforeDisconnect = client.writes.length;
  const writesBeforeDisconnect = client.writes.slice();
  const phaseBeforeDisconnect = state.phase;
  client.end('scenario disconnect');

  client = fakeClient();
  attachClient(client, settings, 0, result, state, timers);
  client.emit('login', { entityId: 2 });
  client.emit('position', { teleportId: 2, x: 4, y: 64, z: 0, yaw: 0, pitch: 0, flags: {} });
  timers.start();
  timers.tick(24);
  client.emit('system_chat', phasePacket('Team 1 won; results complete'));
  client.emit('scoreboard_score', { itemName: 'autobot00', scoreName: 'points', value: 10 });

  const phaseNames = new Set(state.phaseTransitions.flatMap(transition => [transition.from, transition.to]));
  return {
    game,
    phase: state.phase,
    phaseNames: [...phaseNames],
    transitions: state.phaseTransitions,
    actionsBeforeDisconnect,
    actionsAfterReconnect: client.writes.length,
    writes: writesBeforeDisconnect.concat(client.writes),
    mechanismPackets: state.mechanismPackets,
    serverEvidence: state.serverEvidence,
    serverEvidencePhases: state.serverEvidencePhases,
    actionTypes: state.actionTypes,
    scoreHistory: state.scoreHistory,
    scorePackets: state.scorePackets || 0,
    matrix: actionProfile({ action: 'game', game }).contract,
    reconnectObserved: state.playLogins >= 2 && phaseBeforeDisconnect === 'PROGRESS',
    loggedIn: state.loggedIn,
  };
}

module.exports = { fakeTimers, fakeClient, runGameScenario };
