const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { fork } = require('node:child_process');
const { gameContract } = require('./matrix');

function shardPlans(settings) {
  const plans = [];
  for (let offset = 0; offset < settings.count; offset += settings.clientsPerProcess) {
    plans.push({ startIndex: settings.startIndex + offset,
      count: Math.min(settings.clientsPerProcess, settings.count - offset) });
  }
  return plans;
}

function aggregateShards(settings, plans, reports, startedAt) {
  const total = { startedAt, finishedAt: new Date().toISOString(), host: settings.host,
    port: settings.port, count: settings.count, processCount: plans.length,
    connected: 0, loggedIn: 0, ended: 0, attempted: 0, reconnects: 0,
    deaths: 0, respawnRequests: 0, deathRespawns: 0, respawnPackets: 0, playLogins: 0,
    actionPackets: 0, teleportConfirmations: 0, authDialogSubmissions: 0, scorePackets: 0, injectedDisconnects: 0,
    mechanismPackets: {}, mechanismPhases: {}, phaseTransitions: [], scoreHistory: [], reconnectHistory: [], validation: { errors: [] },
    actionTypes: {}, actionPhases: {}, serverEvidence: {}, serverEvidencePhases: {}, matrix: settings.action === 'game' && settings.game ? gameContract(settings.game) : null,
    observations: { entitySpawns: 0, entityDestroys: 0, blockChanges: 0, scoreboardObjectives: 0, teamPackets: 0 },
    scoreObjectives: {},
    evidence: { blocks: [], items: [], windows: [], entities: [], messages: [] },
    activeAtFinish: 0, errors: [], fatalErrors: [], players: [], shards: [] };
  total.configuredHoldMs = settings.holdMs;
  for (let i = 0; i < plans.length; i += 1) {
    const report = reports[i];
    const plan = plans[i];
    const expected = Array.from({ length: plan.count }, (_, n) =>
      `${settings.prefix}${String(plan.startIndex + n).padStart(2, '0')}`);
    const players = report?.players || [];
    const actual = players.map(p => p.name);
    const exactRoster = actual.length === expected.length
      && new Set(actual).size === expected.length && expected.every(name => actual.includes(name));
    const passed = report?.exitCode === 0 && report.success === true
      && report.count === plan.count && exactRoster
      && players.every(p => p.loggedIn && p.activeAtFinish && p.worldReady && !p.deadAtFinish);
    if (!passed) total.fatalErrors.push({ shard: i, message: report?.workerError || 'worker failed or incomplete roster' });
    for (const key of ['connected', 'loggedIn', 'ended', 'attempted', 'reconnects',
      'actionPackets', 'teleportConfirmations', 'authDialogSubmissions', 'scorePackets', 'injectedDisconnects', 'activeAtFinish',
      'deaths', 'respawnRequests', 'deathRespawns', 'respawnPackets', 'playLogins'])
      total[key] += Number(report?.[key]) || 0;
    total.errors.push(...(report?.errors || []));
    total.fatalErrors.push(...(report?.fatalErrors || []));
    total.validation.errors.push(...(report?.validation?.errors || []).map(message => `shard ${i}: ${message}`));
    total.players.push(...players);
    for (const player of players) {
      total.phaseTransitions.push(...(player.phaseTransitions || []).map(transition => ({
        ...transition, player: player.name,
      })));
      total.scoreHistory.push(...(player.scoreHistory || []).map(score => ({
        ...score, player: player.name,
      })));
      total.reconnectHistory.push(...(player.reconnectHistory || []).map(reconnect => ({
        ...reconnect, player: player.name,
      })));
      for (const [key, value] of Object.entries(player.mechanismPackets || {}))
        total.mechanismPackets[key] = (total.mechanismPackets[key] || 0) + Number(value || 0);
      for (const [key, phases] of Object.entries(player.mechanismPhases || {})) {
        const target = total.mechanismPhases[key] || (total.mechanismPhases[key] = {});
        for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
      }
      for (const [key, value] of Object.entries(player.actionTypes || {}))
        total.actionTypes[key] = (total.actionTypes[key] || 0) + Number(value || 0);
      for (const [key, phases] of Object.entries(player.actionPhases || {})) {
        const target = total.actionPhases[key] || (total.actionPhases[key] = {});
        for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
      }
      for (const [key, value] of Object.entries(player.serverEvidence || {}))
        total.serverEvidence[key] = (total.serverEvidence[key] || 0) + Number(value || 0);
      for (const [key, phases] of Object.entries(player.serverEvidencePhases || {})) {
        const target = total.serverEvidencePhases[key] || (total.serverEvidencePhases[key] = {});
        for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
      }
      for (const [key, value] of Object.entries(player.observations || {}))
        total.observations[key] = (total.observations[key] || 0) + Number(value || 0);
      for (const [key, value] of Object.entries(player.scoreObjectives || {}))
        total.scoreObjectives[key] = value;
      for (const key of ['blocks', 'items', 'windows', 'entities', 'messages']) {
        const samples = player.evidence?.[key] || [];
        if (total.evidence[key].length < 64)
          total.evidence[key].push(...samples.slice(0, 64 - total.evidence[key].length));
      }
    }
    total.shards.push({ ...plan, success: passed, exitCode: report?.exitCode,
      windowEndReason: report?.windowEndReason, windowElapsedMs: report?.windowElapsedMs,
      eventLoop: report?.eventLoop, workerError: report?.workerError });
  }
  total.success = total.fatalErrors.length === 0 && total.activeAtFinish === settings.count;
  total.validation.passed = total.validation.errors.length === 0;
  return total;
}

async function runShards(settings, filename) {
  const startedAt = new Date().toISOString();
  const plans = shardPlans(settings);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cc-test-workers-'));
  const children = new Set();
  const stop = signal => { for (const child of children) child.kill(signal); };
  let interrupted = false;
  const interrupt = () => { interrupted = true; stop('SIGTERM'); };
  process.once('SIGTERM', interrupt);
  process.once('SIGINT', interrupt);
  try {
    const reports = await Promise.all(plans.map((plan, i) => new Promise(resolve => {
      setTimeout(() => {
        if (interrupted) { resolve({ workerError: 'coordinator interrupted' }); return; }
        const reportPath = path.join(directory, `worker-${i}.json`);
        const child = fork(filename, ['--worker'], { env: { ...process.env,
          CC_TEST_COUNT: String(plan.count), CC_TEST_START_INDEX: String(plan.startIndex),
          CC_TEST_INTERVAL_MS: String(settings.intervalMs * plans.length),
          CC_TEST_CORE_AUTO_SESSION: 'false',
          CC_TEST_WEB_DISABLE_ON_FINISH: 'false',
          CC_TEST_BOT_CREDENTIALS: settings.credentials ? JSON.stringify(settings.credentials) : '',
          CC_TEST_REPORT: reportPath }, stdio: ['ignore', 'ignore', 'pipe', 'ipc'] });
        children.add(child);
        let stderr = '';
        child.stderr.on('data', chunk => { stderr = (stderr + chunk.toString()).slice(-8192); });
        child.once('error', error => { resolve({ workerError: error.message }); });
        child.once('exit', (exitCode, signal) => {
          children.delete(child);
          try { resolve({ ...JSON.parse(fs.readFileSync(reportPath, 'utf8')), exitCode }); }
          catch { resolve({ exitCode, workerError: stderr || `worker exited without report: ${signal || exitCode}` }); }
        });
      }, i * settings.intervalMs);
    })));
    return { ...aggregateShards(settings, plans, reports, startedAt), workerReports: directory };
  } finally {
    process.removeListener('SIGTERM', interrupt);
    process.removeListener('SIGINT', interrupt);
    stop('SIGTERM');
  }
}

module.exports = { shardPlans, aggregateShards, runShards };
