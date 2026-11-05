const test = require('node:test');
const assert = require('node:assert/strict');
const { validateReport } = require('../validation');

test('report validation gates expected phases, mechanisms and reconnects', () => {
  const report = {
    reconnects: 1,
    mechanismPackets: { checkpoint: 2 },
    scorePackets: 4,
    scoreHistory: [{ objective: 'points', entry: 'autobot00', value: 4, phase: 'END' }],
    players: [{ phase: 'END', phaseTransitions: [{ from: 'WAITING', to: 'PROGRESS' }] }],
  };
  const result = validateReport(report, {
    expectedPhases: 'WAITING,PROGRESS,END', expectedMechanisms: 'checkpoint', expectedReconnects: 1,
    expectedScoreObjective: 'points', expectedScorePackets: 1,
  });
  assert.equal(result.passed, true);
  assert.equal(result.scorePackets, 4);
});

test('report validation checks the phase specific reconnect contract', () => {
  const result = validateReport({
    reconnects: 1,
    players: [{
      phase: 'PROGRESS',
      reconnectHistory: [{ phase: 'PROGRESS', identityPreserved: true, worldReadyAfter: true }],
    }],
  }, { expectedReconnects: 1, expectedReconnectPhases: 'PROGRESS' });
  assert.equal(result.passed, true);
  assert.deepEqual(result.expectedReconnectPhases, ['PROGRESS']);

  const failed = validateReport({
    reconnects: 1,
    players: [{ reconnectHistory: [{ phase: 'PROGRESS', identityPreserved: false, worldReadyAfter: false }] }],
  }, { expectedReconnects: 1, expectedReconnectPhases: 'PROGRESS' });
  assert.deepEqual(failed.errors, [
    'reconnect changed identity at phase PROGRESS',
    'reconnect did not restore world at phase PROGRESS',
  ]);
});

test('report validation explains missing evidence', () => {
  const result = validateReport({ players: [], mechanismPackets: {}, reconnects: 0 }, {
    expectedPhases: 'PROGRESS', expectedMechanisms: 'jump', expectedReconnects: 1,
    expectedScoreObjective: 'points', expectedScorePackets: 1,
  });
  assert.deepEqual(result.errors, [
    'missing phase PROGRESS', 'missing mechanism jump', 'expected at least 1 reconnect(s)',
    'expected at least 1 scoreboard packet(s)', 'missing scoreboard objective points',
  ]);
});

test('progress validation can require the profile action contract', () => {
  const result = validateReport({
    matrix: { mechanism: 'floor-break', actions: ['move', 'jump'] },
    players: [{ phase: 'PROGRESS', phaseTransitions: [] }],
    mechanismPackets: { 'floor-break': 1 },
    actionTypes: { move: 1 },
  }, { expectedPhases: 'PROGRESS' });
  assert.equal(result.passed, false);
  assert.deepEqual(result.errors, ['missing action jump']);
  assert.deepEqual(result.observedActions, ['move']);
});

test('progress validation rejects actions and mechanisms observed only in another phase', () => {
  const result = validateReport({
    matrix: { mechanism: 'floor-break', actions: ['move', 'jump'] },
    players: [{ phase: 'PROGRESS', phaseTransitions: [] }],
    mechanismPackets: { 'floor-break': 1 },
    mechanismPhases: { 'floor-break': { WAITING: 1 } },
    actionTypes: { move: 1, jump: 1 },
    actionPhases: { move: { PROGRESS: 1 }, jump: { COUNTDOWN: 1 } },
  }, { expectedPhases: 'PROGRESS' });
  assert.deepEqual(result.errors, [
    'missing mechanism floor-break in phase PROGRESS',
    'missing action jump in phase PROGRESS',
  ]);
});

test('matrix end-state validation requires at least one score observation', () => {
  const result = validateReport({
    matrix: { mechanism: 'checkpoint', actions: ['move'], score: ['points'] },
    players: [{ phase: 'END', phaseTransitions: [] }],
    mechanismPackets: { checkpoint: 1 },
    actionTypes: { move: 1 },
    scorePackets: 0,
  }, { expectedPhases: 'PROGRESS,END' });
  assert.equal(result.passed, false);
  assert.deepEqual(result.errors, [
    'missing phase PROGRESS',
    'expected at least 1 scoreboard packet(s)',
    'missing scoreboard evidence in phase END',
  ]);
});

test('score validation rejects lobby-only scoreboard packets', () => {
  const result = validateReport({
    matrix: { mechanism: 'checkpoint', actions: ['move'], score: ['points'] },
    players: [{ phase: 'END', phaseTransitions: [] }],
    mechanismPackets: { checkpoint: 1 }, actionTypes: { move: 1 },
    scorePackets: 1, scoreHistory: [{ objective: 'lobby', value: 1, phase: 'WAITING' }],
  }, { expectedPhases: 'PROGRESS,END' });
  assert.deepEqual(result.errors, ['missing phase PROGRESS', 'missing scoreboard evidence in phase END']);
});

test('progress validation requires declared server evidence separately from client intent', () => {
  const result = validateReport({
    matrix: { mechanism: 'floor-break', actions: ['move'], serverEvidence: ['block-change', 'scoreboard'] },
    players: [{ phase: 'PROGRESS', phaseTransitions: [] }],
    mechanismPackets: { 'floor-break': 1 }, actionTypes: { move: 1 },
    serverEvidence: { 'block-change': 1 },
  }, { expectedPhases: 'PROGRESS', expectedServerEvidence: 'block-change,scoreboard' });
  assert.deepEqual(result.errors, ['missing server evidence scoreboard']);
});

test('server evidence observed only in lobby cannot satisfy progress evidence', () => {
  const result = validateReport({
    matrix: { mechanism: 'floor-break', actions: ['move'], serverEvidence: ['block-change'] },
    players: [{ phase: 'PROGRESS', phaseTransitions: [] }],
    mechanismPackets: { 'floor-break': 1 }, actionTypes: { move: 1 },
    serverEvidence: { 'block-change': 1 },
    serverEvidencePhases: { 'block-change': { WAITING: 1 } },
  }, { expectedPhases: 'PROGRESS', expectedServerEvidence: 'block-change' });
  assert.deepEqual(result.errors, ['missing server evidence block-change in phase PROGRESS']);
});
