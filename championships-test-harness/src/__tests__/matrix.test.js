const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { ACTION_TYPES, GAME_TEST_MATRIX, SERVER_EVIDENCE_TYPES, gameContract, supportedMatrixGames, validateMatrix } = require('../matrix');
const { supportedGameProfiles } = require('../profiles');

test('the executable matrix has one contract for every resident game profile', () => {
  assert.equal(validateMatrix().length, 0);
  assert.deepEqual(supportedGameProfiles().sort(), supportedMatrixGames().sort());
  for (const game of supportedMatrixGames()) {
    const contract = gameContract(game);
    assert.equal(contract.token, game);
    assert.ok(contract.actions.length > 0);
    assert.ok(contract.disconnect.includes('PROGRESS'));
    assert.ok(contract.checks.length > 0);
    assert.ok(contract.score.length > 0);
    assert.ok(contract.serverEvidence.length > 0);
    assert.deepEqual(contract.phaseActions.PROGRESS, contract.actions);
    assert.deepEqual(contract.reconnect.map(item => item.phase), contract.disconnect);
    assert.ok(contract.reconnect.every(item => item.preserve.includes('identity')));
  }
});

test('matrix contracts are immutable and reject unknown games', () => {
  assert.throws(() => gameContract('missing-game'), /unknown game matrix profile/);
  assert.equal(Object.isFrozen(GAME_TEST_MATRIX), true);
  assert.equal(Object.isFrozen(gameContract('tntrun').actions), true);
});

test('matrix action and evidence declarations use the shared vocabulary', () => {
  assert.ok(ACTION_TYPES.includes('block-interact'));
  assert.ok(SERVER_EVIDENCE_TYPES.includes('scoreboard'));
  assert.deepEqual(validateMatrix({ bad: {
    token: 'bad', mechanism: 'x', actions: ['typo'], disconnect: ['PROGRESS'],
    score: ['points'], serverEvidence: ['unknown'], checks: ['x'], phaseActions: {},
    reconnect: [{ phase: 'PROGRESS' }],
  }}), ['bad: unsupported action typo', 'bad: unsupported server evidence unknown']);
});

test('the documentation table names every executable game token', (t) => {
  const documentPath = path.join(__dirname, '../../../docs/game-test-matrix.md');
  // The production image intentionally contains only the harness.  The source
  // checkout runs this consistency check; the image test suite can still run
  // without copying the repository's documentation into /app.
  if (!fs.existsSync(documentPath)) return t.skip('matrix documentation is outside the harness image');
  const document = fs.readFileSync(documentPath, 'utf8');
  for (const game of supportedMatrixGames()) {
    const contract = gameContract(game);
    assert.match(document, new RegExp('\\\\| \\S+ \\| `' + contract.token + '` \\|'), game);
  }
});
