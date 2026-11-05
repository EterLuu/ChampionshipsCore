const fs = require('node:fs');
const path = require('node:path');
const protocol = require('@wp2508/minecraft-protocol');
const { attachClient, successfulRun } = require('./client');
const { runShards } = require('./shards');
const { waitForWindow } = require('./window');
const { provisionBots, disableBots } = require('./web-bots');
const { CoreTestControl } = require('./core-control');
const { monitorEventLoopDelay } = require('node:perf_hooks');
const { validateReport } = require('./validation');
const { gameContract } = require('./matrix');

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
  // AuthBridge polls cc-web's outbox on a roughly ten-second cadence.  Keep a
  // full poll interval plus margin between PROVISION and the first login.
  webProvisionSettleMs: 15000,
  botPersistent: true,
  coreSessionMode: 'single',
  coreProfile: 'idle',
  coreStatusIntervalMs: 1000,
  coreTimeoutMs: 180000,
  coreRconTimeoutMs: 10000,
  disconnectDelayMs: 250,
});

function help() {
  console.log(`Championships Core protocol harness\n\nEnvironment:\n  CC_TEST_HOST, CC_TEST_PORT, CC_TEST_VERSION, CC_TEST_VIEW_DISTANCE\n  CC_TEST_COUNT, CC_TEST_START_INDEX, CC_TEST_PREFIX, CC_TEST_INTERVAL_MS, CC_TEST_HOLD_MS\n  CC_TEST_CLIENTS_PER_PROCESS=8\n  CC_TEST_RECONNECT_ATTEMPTS, CC_TEST_RECONNECT_DELAY_MS\n  CC_TEST_MODE=window|resident (default: window)\n  CC_TEST_ACTION=idle|look|walk|game (bingo-walk is a legacy compatibility profile)\n  CC_TEST_VOTE_SLOT=0, CC_TEST_SUBMIT_SLOT=0 (window action slots)\n  CC_TEST_MENU_SLOT=0 (game equipment/weapon menu slot)\n  CC_TEST_ALLOW_WORLD_MUTATION=true (CI/window or Core automatic session only)\n  CC_TEST_GAME=<game-token> (required with CC_TEST_ACTION=game)\n  CC_TEST_IDENTITY_MODE=offline|cc-web\n  CC_TEST_WEB_BASE_URL, CC_TEST_WEB_KEY_ID, CC_TEST_WEB_HMAC_SECRET\n  CC_TEST_BOT_ALLOWED_CIDRS (required for cc-web mode)\n  CC_TEST_WEB_DISABLE_ON_FINISH=true (optional; disables the provisioned slots)\n  CC_TEST_REPORT=/tmp/.../bots.json\n  CC_TEST_STOP_FILE=/tmp/.../finished\n  CC_TEST_CORE_AUTO_SESSION=true
  CC_TEST_CORE_RCON_HOST, CC_TEST_CORE_RCON_PORT, CC_TEST_CORE_RCON_PASSWORD
  CC_TEST_CORE_SESSION_MODE=single|event
  CC_TEST_CORE_GAME, CC_TEST_CORE_MAP, CC_TEST_CORE_PROFILE
  CC_TEST_CORE_EVENT_PROFILE, CC_TEST_CORE_DIFFICULTY, CC_TEST_CORE_REMIX
  CC_TEST_CORE_AUTO_RUN=true, CC_TEST_CORE_AUTO_STOP=true
  CC_TEST_CORE_STATUS_INTERVAL_MS, CC_TEST_CORE_TIMEOUT_MS
  CC_TEST_DISCONNECT_PHASE=... or CC_TEST_DISCONNECT_PHASES=phase,phase (CI fault injection)
  CC_TEST_DISCONNECT_DELAY_MS=250
  CC_TEST_EXPECT_PHASES=WAITING,PROGRESS,END
  CC_TEST_EXPECT_MECHANISMS=checkpoint,weapon-combat
  CC_TEST_EXPECT_ACTIONS=move,jump,attack
  CC_TEST_EXPECT_SERVER_EVIDENCE=block-change,scoreboard
  CC_TEST_EXPECT_RECONNECTS=1
  CC_TEST_EXPECT_RECONNECT_PHASES=PREPARATION,PROGRESS,END
  CC_TEST_EXPECT_SCORE_OBJECTIVE=points
  CC_TEST_EXPECT_SCORE_PACKETS=1
\nresident mode keeps idle Bot clients connected until the absolute stop file is created.\nIt is valid with CC_TEST_ACTION=idle or game and a new CC_TEST_STOP_FILE. The Bot\nhandles proxy/AuthMe/Core login, server teleports, respawn and keep-alive; Core\ncontinues to own teams, game rules, scoring and the normal match lifecycle.\n\nCore should obtain leases with "/cc game test lease web" after creating the session.\n`);
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
    game: process.env.CC_TEST_GAME || null,
    voteSlot: numberEnv('CC_TEST_VOTE_SLOT', 0, 0),
    submitSlot: numberEnv('CC_TEST_SUBMIT_SLOT', 0, 0),
    menuSlot: numberEnv('CC_TEST_MENU_SLOT', 0, 0),
    allowWorldMutation: process.env.CC_TEST_ALLOW_WORLD_MUTATION === 'true',
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
    webProvisionSettleMs: numberEnv('CC_TEST_WEB_PROVISION_SETTLE_MS', DEFAULTS.webProvisionSettleMs, 0),
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
    disconnectPhase: process.env.CC_TEST_DISCONNECT_PHASE || null,
    disconnectPhases: (process.env.CC_TEST_DISCONNECT_PHASES || process.env.CC_TEST_DISCONNECT_PHASE || '')
      .split(',').map(value => value.trim().toUpperCase()).filter(Boolean),
    disconnectDelayMs: numberEnv('CC_TEST_DISCONNECT_DELAY_MS', DEFAULTS.disconnectDelayMs, 0),
    expectedPhases: process.env.CC_TEST_EXPECT_PHASES || null,
    expectedMechanisms: process.env.CC_TEST_EXPECT_MECHANISMS || null,
    expectedActions: process.env.CC_TEST_EXPECT_ACTIONS || null,
    expectedServerEvidence: process.env.CC_TEST_EXPECT_SERVER_EVIDENCE || null,
    expectedReconnects: process.env.CC_TEST_EXPECT_RECONNECTS == null ? null : numberEnv('CC_TEST_EXPECT_RECONNECTS', 0, 0),
    expectedReconnectPhases: process.env.CC_TEST_EXPECT_RECONNECT_PHASES || null,
    expectedScoreObjective: process.env.CC_TEST_EXPECT_SCORE_OBJECTIVE || null,
    expectedScorePackets: process.env.CC_TEST_EXPECT_SCORE_PACKETS == null ? null : numberEnv('CC_TEST_EXPECT_SCORE_PACKETS', 0, 0),
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
  if (!['idle', 'look', 'walk', 'game', 'bingo-walk'].includes(settings.action))
    throw new Error(`unsupported action: ${settings.action}`);
  if (settings.action === 'game' && !settings.game)
    throw new Error('CC_TEST_ACTION=game requires CC_TEST_GAME');
  if (settings.action === 'game') {
    try { gameContract(settings.game); }
    catch { throw new Error(`unsupported CC_TEST_GAME: ${settings.game}`); }
  }
  if (settings.mode === 'resident' && !['idle', 'game'].includes(settings.action))
    throw new Error('resident mode requires CC_TEST_ACTION=idle or game');
  if (settings.mode === 'resident' && !settings.stopFile)
    throw new Error('resident mode requires CC_TEST_STOP_FILE');
  const disconnectPhases = settings.disconnectPhases || (settings.disconnectPhase ? [settings.disconnectPhase.toUpperCase()] : []);
  if (disconnectPhases.some(phase => !['WAITING', 'VOTING', 'PREPARATION', 'COUNTDOWN', 'PROGRESS', 'END'].includes(phase)))
    throw new Error(`unsupported disconnect phase: ${disconnectPhases.find(phase => !['WAITING', 'VOTING', 'PREPARATION', 'COUNTDOWN', 'PROGRESS', 'END'].includes(phase))}`);
  if (settings.identityMode !== undefined && !['offline', 'cc-web'].includes(settings.identityMode))
    throw new Error(`unsupported identity mode: ${settings.identityMode}`);
  if (settings.stopFile && (!path.isAbsolute(settings.stopFile) || exists(settings.stopFile)))
    throw new Error('stop file must be an absolute path which does not already exist');
  if (![settings.count, settings.startIndex, settings.clientsPerProcess].every(Number.isSafeInteger))
    throw new Error('count, start index and clients per process must be integers');
  if ([settings.voteSlot, settings.submitSlot, settings.menuSlot].some(value => value != null
      && (!Number.isSafeInteger(value) || value > 53)))
    throw new Error('window action slots must be integers between 0 and 53');
  if (settings.allowWorldMutation && settings.mode === 'resident' && !settings.coreAutoSession)
    throw new Error('CC_TEST_ALLOW_WORLD_MUTATION requires a window or Core automatic session');
  if (settings.coreAutoSession) {
    if (settings.mode !== 'resident') throw new Error('Core auto session requires CC_TEST_MODE=resident');
    if (!['idle', 'game'].includes(settings.action))
      throw new Error('Core auto session requires CC_TEST_ACTION=idle or game');
    if (settings.action === 'game' && !settings.game && !settings.coreGame)
      throw new Error('Core auto session with game actions requires CC_TEST_GAME or CC_TEST_CORE_GAME');
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

function applyMatrixExpectations(settings) {
  if (!settings.coreAutoSession || settings.action !== 'game' || !settings.game) return settings;
  const contract = gameContract(settings.game);
  if (!settings.expectedPhases) settings.expectedPhases = 'WAITING,PROGRESS,END';
  if (!settings.expectedMechanisms) settings.expectedMechanisms = contract.mechanism;
  if (!settings.expectedActions) settings.expectedActions = contract.actions.join(',');
  if (!settings.expectedServerEvidence) settings.expectedServerEvidence = contract.serverEvidence.join(',');
  if (settings.expectedScorePackets == null) settings.expectedScorePackets = 1;
  if (!settings.expectedReconnectPhases && settings.disconnectPhases?.length)
    settings.expectedReconnectPhases = settings.disconnectPhases.join(',');
  if (settings.expectedReconnects == null && settings.expectedReconnectPhases)
    settings.expectedReconnects = settings.expectedReconnectPhases.split(',').filter(Boolean).length;
  return settings;
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
    scorePackets: 0,
    errors: [],
    fatalErrors: [],
    reconnects: 0,
    injectedDisconnects: 0,
    configuredHoldMs: settings.holdMs,
    matrix: settings.action === 'game' && settings.game ? gameContract(settings.game) : null,
    mechanismPackets: {},
    mechanismPhases: {},
    actionTypes: {},
    actionPhases: {},
    serverEvidence: {},
    serverEvidencePhases: {},
    observations: { entitySpawns: 0, entityDestroys: 0, blockChanges: 0, scoreboardObjectives: 0, teamPackets: 0 },
    scoreObjectives: {},
    evidence: { blocks: [], items: [], windows: [], entities: [], messages: [] },
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
  result.mechanismPackets = {};
  result.phaseTransitions = [];
  result.scoreHistory = [];
  for (const state of states) {
    result.phaseTransitions.push(...(state.phaseTransitions || []).map(transition => ({ ...transition, player: state.name })));
    result.scoreHistory.push(...(state.scoreHistory || []).map(score => ({ ...score, player: state.name })));
    for (const [key, value] of Object.entries(state.mechanismPackets || {}))
      result.mechanismPackets[key] = (result.mechanismPackets[key] || 0) + Number(value || 0);
    for (const [key, phases] of Object.entries(state.mechanismPhases || {})) {
      const target = result.mechanismPhases[key] || (result.mechanismPhases[key] = {});
      for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
    }
    for (const [key, value] of Object.entries(state.actionTypes || {}))
      result.actionTypes[key] = (result.actionTypes[key] || 0) + Number(value || 0);
    for (const [key, phases] of Object.entries(state.actionPhases || {})) {
      const target = result.actionPhases[key] || (result.actionPhases[key] = {});
      for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
    }
    for (const [key, value] of Object.entries(state.serverEvidence || {}))
      result.serverEvidence[key] = (result.serverEvidence[key] || 0) + Number(value || 0);
    for (const [key, phases] of Object.entries(state.serverEvidencePhases || {})) {
      const target = result.serverEvidencePhases[key] || (result.serverEvidencePhases[key] = {});
      for (const [phase, value] of Object.entries(phases)) target[phase] = (target[phase] || 0) + Number(value || 0);
    }
    for (const [key, value] of Object.entries(state.observations || {}))
      result.observations[key] = (result.observations[key] || 0) + Number(value || 0);
    for (const [key, value] of Object.entries(state.scoreObjectives || {}))
      result.scoreObjectives[key] = value;
    for (const key of ['blocks', 'items', 'windows', 'entities', 'messages']) {
      const samples = state.evidence?.[key] || [];
      if (result.evidence[key].length < 64)
        result.evidence[key].push(...samples.slice(0, 64 - result.evidence[key].length));
    }
  }
  result.players = states.map(state => ({
    name: state.name,
    serverUuid: state.serverUuid || null,
    uuidHistory: state.uuidHistory || [],
    reconnectHistory: state.reconnectHistory || [],
    teamHistory: state.teamHistory || [],
    gameModeHistory: state.gameModeHistory || [],
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
    phase: state.phase || 'WAITING',
    phaseTransitions: state.phaseTransitions || [],
    mechanismPackets: state.mechanismPackets || {},
    mechanismPhases: state.mechanismPhases || {},
    actionTypes: state.actionTypes || {},
    actionPhases: state.actionPhases || {},
    serverEvidence: state.serverEvidence || {},
    serverEvidencePhases: state.serverEvidencePhases || {},
    blockChanges: state.blockChanges || 0,
    observations: state.observations || {},
    scoreObjectives: state.scoreObjectives || {},
    evidence: state.evidence || {},
    injectedDisconnects: state.injectedDisconnects || 0,
    scorePackets: state.scorePackets || 0,
    scores: state.scores || {},
    scoreHistory: state.scoreHistory || [],
  }));
  result.validation = validateReport(result, settings);
  if (!result.validation.passed) result.fatalErrors.push(...result.validation.errors.map(message => ({ message })));
  for (const player of result.players) {
    if (player.uuidHistory.length > 1)
      result.fatalErrors.push({ name: player.name, message: `server UUID changed across reconnects: ${player.uuidHistory.join(', ')}` });
  }
  result.success = successfulRun(states, result);
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
  applyMatrixExpectations(settings);
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
    let report;
    try {
      report = await runShards(settings, __filename);
      writeReport(settings, report);
    } finally {
      if (settings.webDisableOnFinish && settings.identityMode === 'cc-web')
        await disableBots(settings);
    }
    if (!report.success) process.exitCode = 1;
    return;
  }
  let result;
  try {
    result = await runClients(settings);
    writeReport(settings, result);
  } finally {
    if (settings.webDisableOnFinish && settings.identityMode === 'cc-web')
      await disableBots(settings);
  }
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

module.exports = { config, validateSettings, applyMatrixExpectations, runClients, runBotProcesses, runWithCoreSession };
