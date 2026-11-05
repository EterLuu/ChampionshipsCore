const test = require('node:test');
const assert = require('node:assert/strict');
const { validateSettings, applyMatrixExpectations } = require('../cli');

function settings(overrides = {}) {
  return {
    mode: 'window', action: 'idle', count: 1, startIndex: 0, clientsPerProcess: 1,
    stopFile: null, ...overrides,
  };
}

test('resident mode requires idle or a game action and a new absolute stop file', () => {
  assert.throws(() => validateSettings(settings({ mode: 'resident' }), () => false), /requires CC_TEST_STOP_FILE/);
  assert.throws(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop', action: 'walk' }), () => false), /requires CC_TEST_ACTION=idle/);
  assert.doesNotThrow(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop' }), () => false));
  assert.doesNotThrow(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop', action: 'game', game: 'tntrun' }), () => false));
  assert.throws(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop', action: 'game' }), () => false), /requires CC_TEST_GAME/);
});

test('window mode retains the existing action profiles and stop-file checks', () => {
  for (const action of ['idle', 'look', 'walk', 'bingo-walk'])
    assert.doesNotThrow(() => validateSettings(settings({ action }), () => false));
  assert.doesNotThrow(() => validateSettings(settings({ action: 'game', game: 'tntrun' }), () => false));
  assert.throws(() => validateSettings(settings({ stopFile: 'relative.finished' }), () => false), /absolute path/);
  assert.throws(() => validateSettings(settings({ stopFile: '/tmp/old.finished' }), () => true), /does not already exist/);
});

test('CI disconnect injection accepts only known game phases', () => {
  assert.doesNotThrow(() => validateSettings(settings({ disconnectPhase: 'progress' }), () => false));
  assert.doesNotThrow(() => validateSettings(settings({ disconnectPhases: ['PREPARATION', 'PROGRESS'] }), () => false));
  assert.throws(() => validateSettings(settings({ disconnectPhase: 'playing' }), () => false), /unsupported disconnect phase/);
});

test('matrix expectations derive reconnect phases from injected disconnect phases', () => {
  const settingsValue = settings({
    coreAutoSession: true, action: 'game', game: 'tntrun',
    disconnectPhases: ['COUNTDOWN', 'PROGRESS'],
  });
  applyMatrixExpectations(settingsValue);
  assert.equal(settingsValue.expectedReconnectPhases, 'COUNTDOWN,PROGRESS');
  assert.equal(settingsValue.expectedReconnects, 2);
});

test('Core auto session accepts a game profile while retaining the default idle mode', () => {
  const base = settings({
    mode: 'resident', action: 'game', game: 'tntrun', stopFile: '/tmp/stop',
    identityMode: 'cc-web', coreAutoSession: true, count: 64, startIndex: 0,
    prefix: 'autobot', clientsPerProcess: 8, coreSessionMode: 'single',
    coreGame: 'tntrun', coreMap: 'astra', coreRcon: { host: 'core', password: 'secret' },
  });
  assert.doesNotThrow(() => validateSettings(base, () => false));
});

test('game mode rejects a token without a matrix profile', () => {
  assert.throws(() => validateSettings(settings({ action: 'game', game: 'unknown-game' }), () => false), /unsupported CC_TEST_GAME/);
});

test('window action slots are bounded and resident manual mode cannot mutate blocks', () => {
  assert.doesNotThrow(() => validateSettings(settings({ voteSlot: 12, submitSlot: 20 }), () => false));
  assert.throws(() => validateSettings(settings({ voteSlot: 54 }), () => false), /window action slots/);
  assert.throws(() => validateSettings(settings({ mode: 'resident', action: 'idle', stopFile: '/tmp/stop', allowWorldMutation: true }), () => false), /requires a window or Core automatic session/);
});

test('Core automatic sessions inherit matrix evidence expectations', () => {
  const settingsValue = settings({ coreAutoSession: true, action: 'game', game: 'buildmart' });
  applyMatrixExpectations(settingsValue);
  assert.equal(settingsValue.expectedPhases, 'WAITING,PROGRESS,END');
  assert.equal(settingsValue.expectedMechanisms, 'collect-place-submit');
  assert.match(settingsValue.expectedActions, /block-dig/);
  assert.equal(settingsValue.expectedScorePackets, 1);
  assert.equal(settingsValue.expectedServerEvidence, 'block-change,item,scoreboard');
});
