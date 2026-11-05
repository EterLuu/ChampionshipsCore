const test = require('node:test');
const assert = require('node:assert/strict');
const { runGameScenario } = require('../scenarios');
const { supportedGameProfiles } = require('../profiles');
const { validateReport } = require('../validation');

test('every game profile completes the CI phase/action/reconnect/score scenario', () => {
  for (const game of supportedGameProfiles()) {
    const report = runGameScenario(game);
    assert.equal(report.loggedIn, true, game);
    assert.equal(report.reconnectObserved, true, game);
    assert.ok(report.phaseNames.includes('VOTING'), game);
    assert.ok(report.phaseNames.includes('PREPARATION'), game);
    assert.ok(report.phaseNames.includes('COUNTDOWN'), game);
    assert.ok(report.phaseNames.includes('PROGRESS'), game);
    assert.ok(report.phaseNames.includes('END'), game);
    assert.ok(report.actionsBeforeDisconnect > 0, game);
    assert.ok(report.actionsAfterReconnect > 0, game);
    assert.ok(report.writes.some(packet => packet.name === 'window_click'), game);
    assert.ok(report.scoreHistory.some(score => score.objective === 'points'), game);
    assert.ok(Object.keys(report.mechanismPackets).length > 0, game);
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
    });
    assert.equal(validation.passed, true, `${game}: ${validation.errors.join('; ')}`);
  }
});
