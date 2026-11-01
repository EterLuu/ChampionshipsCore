const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { fork } = require('node:child_process');

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
    actionPackets: 0, teleportConfirmations: 0, authDialogSubmissions: 0,
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
      'actionPackets', 'teleportConfirmations', 'authDialogSubmissions', 'activeAtFinish',
      'deaths', 'respawnRequests', 'deathRespawns', 'respawnPackets', 'playLogins'])
      total[key] += Number(report?.[key]) || 0;
    total.errors.push(...(report?.errors || []));
    total.fatalErrors.push(...(report?.fatalErrors || []));
    total.players.push(...players);
    total.shards.push({ ...plan, success: passed, exitCode: report?.exitCode,
      windowEndReason: report?.windowEndReason, windowElapsedMs: report?.windowElapsedMs,
      eventLoop: report?.eventLoop, workerError: report?.workerError });
  }
  total.success = total.fatalErrors.length === 0 && total.activeAtFinish === settings.count;
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
