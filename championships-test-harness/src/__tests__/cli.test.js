const test = require('node:test');
const assert = require('node:assert/strict');
const { validateSettings } = require('../cli');

function settings(overrides = {}) {
  return {
    mode: 'window', action: 'idle', count: 1, startIndex: 0, clientsPerProcess: 1,
    stopFile: null, ...overrides,
  };
}

test('resident mode requires idle and a new absolute stop file', () => {
  assert.throws(() => validateSettings(settings({ mode: 'resident' }), () => false), /requires CC_TEST_STOP_FILE/);
  assert.throws(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop', action: 'walk' }), () => false), /requires CC_TEST_ACTION=idle/);
  assert.doesNotThrow(() => validateSettings(settings({ mode: 'resident', stopFile: '/tmp/stop' }), () => false));
});

test('window mode retains the existing action profiles and stop-file checks', () => {
  for (const action of ['idle', 'look', 'walk', 'bingo-walk'])
    assert.doesNotThrow(() => validateSettings(settings({ action }), () => false));
  assert.throws(() => validateSettings(settings({ stopFile: 'relative.finished' }), () => false), /absolute path/);
  assert.throws(() => validateSettings(settings({ stopFile: '/tmp/old.finished' }), () => true), /does not already exist/);
});
