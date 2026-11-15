const test = require('node:test');
const assert = require('node:assert/strict');
const { runGameScenario } = require('./support/game-scenario');
const { supportedGameProfiles } = require('../profiles');
const { validateReport } = require('../validation');

for (const game of supportedGameProfiles()) {
  test(`${game}: client reacts to synthetic phases, actions, reconnect and score packets`, () => {
    const report = runGameScenario(game);
    assert.equal(report.loggedIn, true, game);
    assert.equal(report.reconnectObserved, true, game);
    assert.ok(report.actionsBeforeDisconnect > 0, game);
    assert.ok(report.actionsAfterReconnect > 0, game);
    assert.ok(report.writes.some(packet => packet.name === 'window_click'), game);
    const validation = validateReport({
      matrix: report.matrix,
      players: [{ phase: report.phase, phaseTransitions: report.transitions }],
      mechanismPackets: report.mechanismPackets,
      actionTypes: report.actionTypes,
      scorePackets: report.scorePackets,
      scoreHistory: report.scoreHistory,
      serverEvidence: report.serverEvidence,
    }, {
      expectedPhases: 'WAITING,VOTING,PREPARATION,COUNTDOWN,PROGRESS,END',
      expectedServerEvidence: report.matrix.serverEvidence.join(','),
      expectedScoreObjective: 'points',
    });
    assert.equal(validation.passed, true, `${game}: ${validation.errors.join('; ')}`);
  });
}
