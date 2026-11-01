const net = require('node:net');

const TERMINAL_STATES = new Set(['COMPLETED', 'FAILED', 'CLOSED']);

function stripFormatting(value) {
  return String(value || '')
    .replace(/§[0-9a-fk-orx]/gi, '')
    .replace(/\u001b\[[0-?]*[ -/]*[@-~]/g, '');
}

function parseStatus(reply) {
  const text = stripFormatting(reply);
  if (text.includes('当前没有测试会话')) {
    return { text, present: false, state: 'NONE', bound: 0, transientTeams: 0, eventRound: 0, standings: 0 };
  }
  const value = (pattern, fallback = null) => {
    const match = text.match(pattern);
    return match ? match[1] : fallback;
  };
  const state = value(/状态=([^|\s]+)/i);
  const bound = Number(value(/已绑定=(\d+)/i, '0'));
  const transientTeams = Number(value(/transient teams=(\d+)/i, '0'));
  const eventRound = Number(value(/event round=(\d+)/i, '0'));
  const standings = Number(value(/standings=(\d+)/i, '0'));
  return {
    text,
    present: state != null,
    state: state ? state.toUpperCase() : 'UNKNOWN',
    bound: Number.isFinite(bound) ? bound : 0,
    transientTeams: Number.isFinite(transientTeams) ? transientTeams : 0,
    eventRound: Number.isFinite(eventRound) ? eventRound : 0,
    standings: Number.isFinite(standings) ? standings : 0,
  };
}

function requireToken(value, name) {
  if (!value || !/^[A-Za-z0-9_.:-]+$/.test(value))
    throw new Error(`${name} must contain only letters, numbers, dot, underscore, colon or hyphen`);
  return value;
}

function delay(ms, timers = globalThis) {
  return new Promise(resolve => timers.setTimeout(resolve, ms));
}

class RconClient {
  constructor({ host, port, password, timeoutMs = 10000, netImpl = net }) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.timeoutMs = timeoutMs;
    this.netImpl = netImpl;
    this.nextId = 1;
  }

  execute(command) {
    if (!command || typeof command !== 'string') throw new Error('RCON command must not be blank');
    const authId = this.nextId++;
    const commandId = this.nextId++;
    return new Promise((resolve, reject) => {
      const socket = this.netImpl.createConnection({ host: this.host, port: this.port });
      let buffer = Buffer.alloc(0);
      let authenticated = false;
      let settled = false;
      let timer = null;
      const finish = (error, result) => {
        if (settled) return;
        settled = true;
        if (timer != null) clearTimeout(timer);
        socket.removeAllListeners();
        socket.destroy();
        if (error) reject(error);
        else resolve(result);
      };
      const timeout = () => finish(new Error(`RCON command timed out after ${this.timeoutMs} ms`));
      timer = setTimeout(timeout, this.timeoutMs);
      const send = (id, type, body) => socket.write(encodePacket(id, type, body));
      const handlePacket = packet => {
        const id = packet.readInt32LE(4);
        const type = packet.readInt32LE(8);
        const body = packet.subarray(12, packet.length - 2).toString('utf8');
        if (id === -1) return finish(new Error('RCON authentication rejected'));
        if (!authenticated) {
          if (id !== authId || type !== 2) return finish(new Error('unexpected RCON authentication response'));
          authenticated = true;
          send(commandId, 2, command);
          return;
        }
        if (id === commandId && (type === 0 || type === 2)) finish(null, body);
      };
      socket.on('connect', () => send(authId, 3, this.password));
      socket.on('data', chunk => {
        buffer = Buffer.concat([buffer, chunk]);
        while (buffer.length >= 4) {
          const size = buffer.readInt32LE(0);
          if (size < 10 || size > 1024 * 1024) return finish(new Error('invalid RCON packet length'));
          if (buffer.length < size + 4) return;
          const packet = buffer.subarray(0, size + 4);
          buffer = buffer.subarray(size + 4);
          handlePacket(packet);
          if (settled) return;
        }
      });
      socket.on('error', error => finish(error));
      socket.on('close', () => {
        if (!settled) finish(new Error('RCON connection closed before a response'));
      });
    });
  }
}

function encodePacket(id, type, body) {
  const payload = Buffer.from(String(body), 'utf8');
  const packet = Buffer.alloc(payload.length + 14);
  packet.writeInt32LE(payload.length + 10, 0);
  packet.writeInt32LE(id, 4);
  packet.writeInt32LE(type, 8);
  payload.copy(packet, 12);
  packet.writeInt16LE(0, packet.length - 2);
  return packet;
}

class CoreTestControl {
  constructor(settings, client = null, timers = globalThis) {
    this.settings = settings;
    this.client = client || new RconClient(settings.coreRcon);
    this.timers = timers;
  }

  execute(command) {
    return this.client.execute(command);
  }

  start() {
    const s = this.settings;
    const command = commandForStart(s);
    return this.execute(command).then(reply => {
      if (/测试会话操作失败|error|failed/i.test(stripFormatting(reply)))
        throw new Error(`Core did not create a test session: ${stripFormatting(reply)}`);
      return reply;
    });
  }

  leaseWeb() {
    return this.execute('cc game test lease web').then(reply => {
      if (/测试会话操作失败|error|failed/i.test(stripFormatting(reply)))
        throw new Error(`Core did not register web leases: ${stripFormatting(reply)}`);
      return reply;
    });
  }

  status() {
    return this.execute('cc game test status').then(parseStatus);
  }

  run() {
    return this.execute('cc game test run').then(reply => {
      if (/测试会话操作失败|测试游戏未启动|error|failed/i.test(stripFormatting(reply)))
        throw new Error(`Core did not start the test game: ${stripFormatting(reply)}`);
      return reply;
    });
  }

  async stop() {
    const reply = await this.execute('cc game test stop --confirm');
    if (/测试会话操作失败|error|failed/i.test(stripFormatting(reply)))
      throw new Error(`Core did not stop the test session: ${stripFormatting(reply)}`);
    return this.waitFor(status => !status.present, {
      timeoutMs: this.settings.coreTimeoutMs || 180000,
      intervalMs: this.settings.coreStatusIntervalMs || 1000,
      description: 'Core test session cleanup',
    });
  }

  async waitForReady({ timeoutMs, intervalMs, expectedSeats = 64, expectedTeams = 16 }) {
    return this.waitFor(status => status.state === 'READY'
      && status.bound >= expectedSeats && status.transientTeams >= expectedTeams,
    { timeoutMs, intervalMs, description: 'Core test session READY' });
  }

  async waitForTerminal({ timeoutMs, intervalMs }) {
    return this.waitFor(status => TERMINAL_STATES.has(status.state),
      { timeoutMs, intervalMs, description: 'Core test session terminal state' });
  }

  async waitFor(predicate, { timeoutMs, intervalMs, description }) {
    const started = Date.now();
    let last = null;
    while (Date.now() - started <= timeoutMs) {
      last = await this.status();
      if (predicate(last)) return last;
      if (last.state === 'FAILED') throw new Error(`Core test session failed: ${last.text}`);
      await delay(intervalMs, this.timers);
    }
    throw new Error(`${description} timed out after ${timeoutMs} ms; last status: ${last?.text || 'unavailable'}`);
  }
}

function commandForStart(settings) {
  const args = settings.coreSessionMode === 'event'
    ? ['cc', 'game', 'test', 'event', 'start', requireToken(settings.coreEventProfile, 'CC_TEST_CORE_EVENT_PROFILE')]
    : ['cc', 'game', 'test', 'single', 'start', requireToken(settings.coreGame, 'CC_TEST_CORE_GAME'),
      requireToken(settings.coreMap, 'CC_TEST_CORE_MAP'), requireToken(settings.coreProfile, 'CC_TEST_CORE_PROFILE')];
  if (settings.coreDifficulty) args.push(requireToken(settings.coreDifficulty, 'CC_TEST_CORE_DIFFICULTY'));
  if (settings.coreRemix) args.push(requireToken(settings.coreRemix, 'CC_TEST_CORE_REMIX'));
  return args.join(' ');
}

module.exports = {
  CoreTestControl,
  RconClient,
  encodePacket,
  parseStatus,
  stripFormatting,
  commandForStart,
  TERMINAL_STATES,
};
