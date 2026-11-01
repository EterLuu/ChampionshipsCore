const fs = require('node:fs');
const path = require('node:path');
const protocol = require('@wp2508/minecraft-protocol');
const { attachClient, successfulRun } = require('./client');
const { runShards } = require('./shards');
const { waitForWindow } = require('./window');
const { provisionBots, disableBots } = require('./web-bots');
const { CoreTestControl } = require('./core-control');
const { monitorEventLoopDelay } = require('node:perf_hooks');

const DEFAULTS = Object.freeze({
  mode: 'window',
  host: '127.0.0.1',
  port: 25565,
  version: '26.3',
  viewDistance: 2,
  count: 64,
  clientsPerProcess: 8,
  startIndex: 0,
  prefix: 'autobot',
  intervalMs: 2200,
  holdMs: 600000,
  reconnectAttempts: 3,
  reconnectDelayMs: 3000,
  password: 'TestPass123',
  action: 'idle',
  identityMode: 'offline',
  webRequestTimeoutMs: 10000,
  botPersistent: true,
  coreSessionMode: 'single',
  coreProfile: 'idle',
  coreStatusIntervalMs: 1000,
  coreTimeoutMs: 180000,
  coreRconTimeoutMs: 10000,
});

function help() {
  console.log(`Championships Core protocol harness\n\nEnvironment:\n  CC_TEST_HOST, CC_TEST_PORT, CC_TEST_VERSION, CC_TEST_VIEW_DISTANCE\n  CC_TEST_COUNT, CC_TEST_START_INDEX, CC_TEST_PREFIX, CC_TEST_INTERVAL_MS, CC_TEST_HOLD_MS\n  CC_TEST_CLIENTS_PER_PROCESS=8\n  CC_TEST_RECONNECT_ATTEMPTS, CC_TEST_RECONNECT_DELAY_MS\n  CC_TEST_MODE=window|resident (default: window)\n  CC_TEST_ACTION=idle|look|walk (bingo-walk is a legacy compatibility profile)\n  CC_TEST_IDENTITY_MODE=offline|cc-web\n  CC_TEST_WEB_BASE_URL, CC_TEST_WEB_KEY_ID, CC_TEST_WEB_HMAC_SECRET\n  CC_TEST_BOT_ALLOWED_CIDRS (required for cc-web mode)\n  CC_TEST_WEB_DISABLE_ON_FINISH=true (optional; disables the provisioned slots)\n  CC_TEST_REPORT=/tmp/.../bots.json\n  CC_TEST_STOP_FILE=/tmp/.../finished\n  CC_TEST_CORE_AUTO_SESSION=true
  CC_TEST_CORE_RCON_HOST, CC_TEST_CORE_RCON_PORT, CC_TEST_CORE_RCON_PASSWORD
  CC_TEST_CORE_SESSION_MODE=single|event
  CC_TEST_CORE_GAME, CC_TEST_CORE_MAP, CC_TEST_CORE_PROFILE
  CC_TEST_CORE_EVENT_PROFILE, CC_TEST_CORE_DIFFICULTY, CC_TEST_CORE_REMIX
  CC_TEST_CORE_AUTO_RUN=true, CC_TEST_CORE_AUTO_STOP=true
  CC_TEST_CORE_STATUS_INTERVAL_MS, CC_TEST_CORE_TIMEOUT_MS
\nresident mode keeps idle Bot clients connected until the absolute stop file is created.\nIt is valid only with CC_TEST_ACTION=idle and a new CC_TEST_STOP_FILE. The Bot\nhandles proxy/AuthMe/Core login, server teleports, respawn and keep-alive; Core\ncontinues to own teams, game rules, scoring and the normal match lifecycle.\n\nCore should obtain leases with "/cc game test lease web" after creating the session.\n`);
}

function numberEnv(name, fallback, minimum) {
  const value = process.env[name];
  if (value == null || value === '') return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < minimum) throw new Error(`invalid ${name}: ${value}`);
  return parsed;
}

function config() {
  return {
    mode: process.env.CC_TEST_MODE || DEFAULTS.mode,
    host: process.env.CC_TEST_HOST || DEFAULTS.host,
    port: numberEnv('CC_TEST_PORT', DEFAULTS.port, 1),
    version: process.env.CC_TEST_VERSION || DEFAULTS.version,
    viewDistance: numberEnv('CC_TEST_VIEW_DISTANCE', DEFAULTS.viewDistance, 2),
    count: numberEnv('CC_TEST_COUNT', DEFAULTS.count, 1),
    clientsPerProcess: numberEnv('CC_TEST_CLIENTS_PER_PROCESS', DEFAULTS.clientsPerProcess, 1),
    startIndex: numberEnv('CC_TEST_START_INDEX', DEFAULTS.startIndex, 0),
    prefix: process.env.CC_TEST_PREFIX || DEFAULTS.prefix,
    intervalMs: numberEnv('CC_TEST_INTERVAL_MS', DEFAULTS.intervalMs, 0),
    holdMs: numberEnv('CC_TEST_HOLD_MS', DEFAULTS.holdMs, 1000),
    reconnectAttempts: numberEnv('CC_TEST_RECONNECT_ATTEMPTS', DEFAULTS.reconnectAttempts, 0),
    reconnectDelayMs: numberEnv('CC_TEST_RECONNECT_DELAY_MS', DEFAULTS.reconnectDelayMs, 0),
    password: process.env.CC_TEST_PASSWORD || DEFAULTS.password,
    action: process.env.CC_TEST_ACTION || DEFAULTS.action,
    identityMode: process.env.CC_TEST_IDENTITY_MODE || DEFAULTS.identityMode,
    webBaseUrl: process.env.CC_TEST_WEB_BASE_URL || null,
    webKeyId: process.env.CC_TEST_WEB_KEY_ID || null,
    webHmacSecret: process.env.CC_TEST_WEB_HMAC_SECRET || null,
    allowInsecureWebHttp: process.env.CC_TEST_WEB_ALLOW_INSECURE_HTTP === 'true',
    botAllowedCidrs: (process.env.CC_TEST_BOT_ALLOWED_CIDRS || '')
      .split(',').map(value => value.trim()).filter(Boolean),
    botPersistent: process.env.CC_TEST_BOT_PERSISTENT !== 'false',
    webDisableOnFinish: process.env.CC_TEST_WEB_DISABLE_ON_FINISH === 'true',
    webRequestTimeoutMs: numberEnv('CC_TEST_WEB_REQUEST_TIMEOUT_MS', DEFAULTS.webRequestTimeoutMs, 1000),
    credentials: parseCredentials(process.env.CC_TEST_BOT_CREDENTIALS),
    report: process.env.CC_TEST_REPORT || null,
    stopFile: process.env.CC_TEST_STOP_FILE || null,
    coreAutoSession: process.env.CC_TEST_CORE_AUTO_SESSION === 'true',
    coreSessionMode: process.env.CC_TEST_CORE_SESSION_MODE || DEFAULTS.coreSessionMode,
    coreGame: process.env.CC_TEST_CORE_GAME || null,
    coreMap: process.env.CC_TEST_CORE_MAP || null,
    coreProfile: process.env.CC_TEST_CORE_PROFILE || DEFAULTS.coreProfile,
    coreEventProfile: process.env.CC_TEST_CORE_EVENT_PROFILE || null,
    coreDifficulty: process.env.CC_TEST_CORE_DIFFICULTY || null,
    coreRemix: process.env.CC_TEST_CORE_REMIX || null,
    coreAutoRun: process.env.CC_TEST_CORE_AUTO_RUN !== 'false',
    coreAutoStop: process.env.CC_TEST_CORE_AUTO_STOP !== 'false',
    coreStatusIntervalMs: numberEnv('CC_TEST_CORE_STATUS_INTERVAL_MS', DEFAULTS.coreStatusIntervalMs, 100),
    coreTimeoutMs: numberEnv('CC_TEST_CORE_TIMEOUT_MS', DEFAULTS.coreTimeoutMs, 1000),
    coreRcon: {
      host: process.env.CC_TEST_CORE_RCON_HOST || null,
      port: numberEnv('CC_TEST_CORE_RCON_PORT', 25575, 1),
      password: process.env.CC_TEST_CORE_RCON_PASSWORD || null,
      timeoutMs: numberEnv('CC_TEST_CORE_RCON_TIMEOUT_MS', DEFAULTS.coreRconTimeoutMs, 1000),
    },
  };
}

function parseCredentials(value) {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    throw new Error('invalid CC_TEST_BOT_CREDENTIALS');
  }
}

function validateSettings(settings, exists = fs.existsSync) {
  if (!['window', 'resident'].includes(settings.mode))
    throw new Error(`unsupported mode: ${settings.mode}`);
  if (!['idle', 'look', 'walk', 'bingo-walk'].includes(settings.action))
    throw new Error(`unsupported action: ${settings.action}`);
  if (settings.mode === 'resident' && settings.action !== 'idle')
    throw new Error('resident mode requires CC_TEST_ACTION=idle');
  if (settings.mode === 'resident' && !settings.stopFile)
    throw new Error('resident mode requires CC_TEST_STOP_FILE');
  if (settings.identityMode !== undefined && !['offline', 'cc-web'].includes(settings.identityMode))
    throw new Error(`unsupported identity mode: ${settings.identityMode}`);
  if (settings.stopFile && (!path.isAbsolute(settings.stopFile) || exists(settings.stopFile)))
    throw new Error('stop file must be an absolute path which does not already exist');
  if (![settings.count, settings.startIndex, settings.clientsPerProcess].every(Number.isSafeInteger))
    throw new Error('count, start index and clients per process must be integers');
  if (settings.coreAutoSession) {
    if (settings.mode !== 'resident') throw new Error('Core auto session requires CC_TEST_MODE=resident');
    if (settings.action !== 'idle') throw new Error('Core auto session requires CC_TEST_ACTION=idle');
    if (settings.identityMode !== 'cc-web') throw new Error('Core auto session requires CC_TEST_IDENTITY_MODE=cc-web');
    if (settings.count !== 64 || settings.startIndex !== 0 || settings.prefix !== 'autobot')
      throw new Error('Core auto session requires the default 64-seat autobot00..autobot63 roster');
    if (!['single', 'event'].includes(settings.coreSessionMode))
      throw new Error(`unsupported Core session mode: ${settings.coreSessionMode}`);
    if (settings.coreSessionMode === 'single' && (!settings.coreGame || !settings.coreMap))
      throw new Error('Core single session requires CC_TEST_CORE_GAME and CC_TEST_CORE_MAP');
    if (settings.coreSessionMode === 'event' && !settings.coreEventProfile)
      throw new Error('Core event session requires CC_TEST_CORE_EVENT_PROFILE');
    if (settings.coreRemix && !settings.coreDifficulty)
      throw new Error('CC_TEST_CORE_REMIX requires CC_TEST_CORE_DIFFICULTY');
    if (!settings.coreRcon.host || !settings.coreRcon.password)
      throw new Error('Core auto session requires CC_TEST_CORE_RCON_HOST and CC_TEST_CORE_RCON_PASSWORD');
    if (!settings.coreAutoRun && !settings.stopFile)
      throw new Error('Core auto session without auto-run requires an external stop file');
  }
}

function username(prefix, index) {
  return `${prefix}${String(index).padStart(2, '0')}`;
}

function createClient(settings, name, index, result, state, clients) {
  const credential = settings.credentials?.[name];
  const clientSettings = credential?.password
    ? { ...settings, password: credential.password }
    : settings;
  const client = protocol.createClient({
    host: settings.host,
    port: settings.port,
    username: name,
    version: settings.version,
    auth: 'offline',
    clientSettings: { viewDistance: settings.viewDistance },
  });
  client._testState = state;
  state.attempts += 1;
  result.attempted += 1;
  client.on('connect', () => { result.connected += 1; });
  attachClient(client, clientSettings, index, result, state);
  client.on('error', error => {
    state.lastError = error.message;
    result.errors.push({ name, message: error.message, attempt: state.attempts });
  });
  client.on('end', () => {
    result.ended += 1;
    if (!state.stopRequested && state.attempts <= settings.reconnectAttempts) {
      result.reconnects += 1;
      setTimeout(() => {
        if (!state.stopRequested) clients.push(createClient(settings, name, index, result, state, clients));
      }, settings.reconnectDelayMs);
    } else if (!state.stopRequested) {
      result.fatalErrors.push({ name, message: state.lastError || 'connection ended before hold window', attempts: state.attempts });
    }
  });
  return client;
}

async function runClients(settings) {
  const eventLoop = monitorEventLoopDelay({ resolution: 20 });
  eventLoop.enable();
  const result = {
    startedAt: new Date().toISOString(),
    host: settings.host,
    port: settings.port,
    mode: settings.mode,
    action: settings.action,
    identityMode: settings.identityMode,
    uuidMode: settings.uuidMode || null,
    viewDistance: settings.viewDistance,
    count: settings.count,
    attempted: 0,
    connected: 0,
    loggedIn: 0,
    ended: 0,
    actionPackets: 0,
    teleportConfirmations: 0,
    authDialogSubmissions: 0,
    errors: [],
    fatalErrors: [],
    reconnects: 0,
    configuredHoldMs: settings.holdMs,
  };
  const clients = [];
  const states = [];
  for (let index = 0; index < settings.count; index += 1) {
    const state = { attempts: 0, loggedIn: false, active: false, stopRequested: false, lastError: null };
    states.push(state);
    setTimeout(
      () => {
        if (!state.stopRequested) {
          state.name = username(settings.prefix, settings.startIndex + index);
          clients.push(createClient(settings, state.name, index, result, state, clients));
        }
      },
      index * settings.intervalMs,
    );
  }
  const windowStarted = performance.now();
  try {
    result.windowEndReason = await waitForWindow(
      settings.mode === 'resident'
        ? Infinity
        : Math.max(0, settings.count - 1) * settings.intervalMs + settings.holdMs,
      settings.stopFile);
  } catch (error) {
    result.windowEndReason = 'error';
    result.fatalErrors.push({ message: error.message });
  }
  result.windowElapsedMs = performance.now() - windowStarted;
  if (result.windowEndReason === 'deadline')
    result.fatalErrors.push({ message: 'server-controlled window did not finish before deadline' });
  result.activeAtFinish = states.filter(state => state.active).length;
  result.success = successfulRun(states, result);
  result.players = states.map(state => ({
    name: state.name,
    serverUuid: state.serverUuid || null,
    attempts: state.attempts,
    loggedIn: state.loggedIn,
    activeAtFinish: state.active,
    worldReady: state.worldReady === true,
    deadAtFinish: state.dead === true,
    deaths: state.deaths || 0,
    respawnRequests: state.respawnRequests || 0,
    deathRespawns: state.deathRespawns || 0,
    respawnPackets: state.respawnPackets || 0,
    playLogins: state.playLogins || 0,
    teleportConfirmations: state.teleportConfirmations || 0,
    authDialogSubmissions: state.authDialogSubmissions || 0,
    keepAliveRequests: state.keepAliveRequests || 0,
    lastKeepAliveAt: state.lastKeepAliveAt || null,
    disconnectReason: state.disconnectReason || null,
    keepAliveDiagnostics: state.keepAliveDiagnostics || null,
  }));
  states.forEach(state => { state.stopRequested = true; });
  clients.forEach(client => { if (client._testState) client._testState.stopRequested = true; });
  for (const client of clients) client.end();
  result.finishedAt = new Date().toISOString();
  eventLoop.disable();
  result.eventLoop = { meanMs: Number(eventLoop.mean) / 1e6, maxMs: Number(eventLoop.max) / 1e6,
    p99Ms: Number(eventLoop.percentile(99)) / 1e6 };
  return result;
}

async function runBotProcesses(settings) {
  if (settings.count > settings.clientsPerProcess)
    return runShards(settings, __filename);
  return runClients(settings);
}

async function runWithCoreSession(settings) {
  const control = new CoreTestControl(settings);
  await control.start();
  await control.leaseWeb();
  const bots = runBotProcesses(settings);
  let terminal = null;
  try {
    await control.waitForReady({ timeoutMs: settings.coreTimeoutMs, intervalMs: settings.coreStatusIntervalMs });
    if (settings.coreAutoRun) {
      await control.run();
      terminal = await control.waitForTerminal({
        timeoutMs: settings.coreTimeoutMs,
        intervalMs: settings.coreStatusIntervalMs,
      });
      if (settings.stopFile) fs.writeFileSync(settings.stopFile, `${JSON.stringify({
        state: terminal.state,
        at: new Date().toISOString(),
      })}\n`, { flag: 'wx' });
    }
    return await bots;
  } catch (error) {
    if (settings.stopFile && !fs.existsSync(settings.stopFile)) {
      try { fs.writeFileSync(settings.stopFile, `${error.message}\n`, { flag: 'wx' }); } catch { /* preserve original error */ }
    }
    try { await bots; } catch { /* preserve the first lifecycle failure */ }
    throw error;
  } finally {
    if (settings.coreAutoStop) {
      try { await control.stop(); } catch (error) { if (!terminal) throw error; }
    }
    if (settings.webDisableOnFinish) await disableBots(settings);
  }
}

async function main() {
  if (process.argv.includes('--help')) { help(); return; }
  const settings = config();
  validateSettings(settings);
  if (settings.identityMode === 'cc-web' && !settings.credentials) {
    const provisioned = await provisionBots(settings);
    settings.credentials = provisioned.credentials;
    settings.uuidMode = provisioned.uuidMode;
  }
  if (!process.argv.includes('--worker') && settings.coreAutoSession) {
    const report = await runWithCoreSession(settings);
    writeReport(settings, report);
    if (!report.success) process.exitCode = 1;
    return;
  }
  if (!process.argv.includes('--worker') && settings.count > settings.clientsPerProcess) {
    const report = await runShards(settings, __filename);
    writeReport(settings, report);
    if (!report.success) process.exitCode = 1;
    return;
  }
  const result = await runClients(settings);
  writeReport(settings, result);
  if (!result.success) process.exitCode = 1;
}

function writeReport(settings, result) {
  if (settings.report) {
    fs.mkdirSync(path.dirname(settings.report), { recursive: true });
    fs.writeFileSync(settings.report, `${JSON.stringify(result, null, 2)}\n`);
  }
  console.log(JSON.stringify(result));
}

if (require.main === module) main().catch(error => { console.error(error.stack || error); process.exitCode = 2; });

module.exports = { config, validateSettings, runClients, runBotProcesses, runWithCoreSession };
