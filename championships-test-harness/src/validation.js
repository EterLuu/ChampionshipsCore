function list(value) {
  return String(value || '').split(',').map(item => item.trim().toUpperCase()).filter(Boolean);
}

function mergePhaseMaps(players, field) {
  const merged = {};
  for (const player of players || []) {
    for (const [action, phases] of Object.entries(player[field] || {})) {
      const target = merged[action] || (merged[action] = {});
      for (const [phase, count] of Object.entries(phases || {}))
        target[phase] = (target[phase] || 0) + Number(count || 0);
    }
  }
  return merged;
}

function validateReport(report, settings = {}) {
  const expectedPhases = list(settings.expectedPhases);
  const matrix = report.matrix || null;
  const progressRequired = expectedPhases.includes('PROGRESS');
  const expectedMechanisms = String(settings.expectedMechanisms || (progressRequired && matrix ? matrix.mechanism : ''))
    .split(',').map(item => item.trim()).filter(Boolean);
  const expectedActions = String(settings.expectedActions || (progressRequired && matrix ? (matrix.signatureActions || matrix.actions).join(',') : ''))
    .split(',').map(item => item.trim()).filter(Boolean);
  const expectedObjective = settings.expectedScoreObjective || null;
  const expectedServerEvidence = String(settings.expectedServerEvidence || '')
    .split(',').map(item => item.trim().toLowerCase()).filter(Boolean);
  const expectedReconnectPhases = list(settings.expectedReconnectPhases);
  const scoreRequired = expectedPhases.includes('END') && matrix;
  const expectedScorePackets = settings.expectedScorePackets == null
    ? (scoreRequired ? 1 : null)
    : Number(settings.expectedScorePackets);
  const phaseSet = new Set((report.players || []).flatMap(player => [
    player.phase || 'WAITING', ...(player.phaseTransitions || []).flatMap(transition => [transition.from, transition.to]),
  ]));
  const mechanisms = Object.keys(report.mechanismPackets || {});
  const actions = Object.keys(report.actionTypes || {});
  const serverEvidence = Object.keys(report.serverEvidence || {});
  const serverEvidencePhases = report.serverEvidencePhases || mergePhaseMaps(report.players, 'serverEvidencePhases');
  const mechanismPhases = report.mechanismPhases || mergePhaseMaps(report.players, 'mechanismPhases');
  const actionPhases = report.actionPhases || mergePhaseMaps(report.players, 'actionPhases');
  const errors = [];
  for (const phase of expectedPhases) if (!phaseSet.has(phase)) errors.push(`missing phase ${phase}`);
  for (const mechanism of expectedMechanisms) {
    if (!mechanisms.includes(mechanism)) errors.push(`missing mechanism ${mechanism}`);
    else if (progressRequired && mechanismPhases[mechanism] && !mechanismPhases[mechanism].PROGRESS)
      errors.push(`missing mechanism ${mechanism} in phase PROGRESS`);
  }
  for (const action of expectedActions) {
    if (!actions.includes(action)) errors.push(`missing action ${action}`);
    else if (progressRequired && actionPhases[action] && !actionPhases[action].PROGRESS)
      errors.push(`missing action ${action} in phase PROGRESS`);
  }
  if (progressRequired) {
    for (const evidence of expectedServerEvidence)
      if (!serverEvidence.includes(evidence)) errors.push(`missing server evidence ${evidence}`);
      else if (serverEvidencePhases[evidence] && !serverEvidencePhases[evidence].PROGRESS)
        errors.push(`missing server evidence ${evidence} in phase PROGRESS`);
  }
  if (settings.expectedReconnects != null) {
    const expected = Number(settings.expectedReconnects);
    if (Number(report.reconnects || 0) < expected) errors.push(`expected at least ${expected} reconnect(s)`);
  }
  if (expectedReconnectPhases.length) {
    const reconnects = (report.players || []).flatMap(player => player.reconnectHistory || []);
    for (const phase of expectedReconnectPhases) {
      const match = reconnects.find(reconnect => reconnect.phase === phase);
      if (!match) {
        errors.push(`missing reconnect at phase ${phase}`);
        continue;
      }
      if (match.identityPreserved === false) errors.push(`reconnect changed identity at phase ${phase}`);
      if (!match.worldReadyAfter) errors.push(`reconnect did not restore world at phase ${phase}`);
    }
  }
  if (expectedScorePackets != null && Number(report.scorePackets || 0) < expectedScorePackets)
    errors.push(`expected at least ${expectedScorePackets} scoreboard packet(s)`);
  if (scoreRequired && !(report.scoreHistory || []).some(score => score.phase === 'END'))
    errors.push('missing scoreboard evidence in phase END');
  if (expectedObjective && !(report.scoreHistory || []).some(score => score.objective === expectedObjective))
    errors.push(`missing scoreboard objective ${expectedObjective}`);
  return {
    expectedPhases,
    expectedMechanisms,
    expectedActions,
    expectedReconnects: settings.expectedReconnects == null ? null : Number(settings.expectedReconnects),
    expectedReconnectPhases,
    expectedScoreObjective: expectedObjective,
    expectedScorePackets,
    observedPhases: [...phaseSet],
    observedMechanisms: mechanisms,
    observedActions: actions,
    observedMechanismPhases: mechanismPhases,
    observedActionPhases: actionPhases,
    expectedServerEvidence,
    observedServerEvidence: serverEvidence,
    observedServerEvidencePhases: serverEvidencePhases,
    scorePackets: Number(report.scorePackets || 0),
    errors,
    passed: errors.length === 0,
  };
}

module.exports = { list, validateReport };
