const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const {
  CoreTestControl,
  RconClient,
  commandForStart,
  encodePacket,
  parseStatus,
} = require('../core-control');

test('RCON packets authenticate, execute one command and parse a response', async () => {
  class Socket extends EventEmitter {
    write(packet) {
      const id = packet.readInt32LE(4);
      const type = packet.readInt32LE(8);
      process.nextTick(() => {
        if (type === 3) this.emit('data', encodePacket(id, 2, ''));
        else {
          const response = encodePacket(id, 0, 'ok');
          this.emit('data', response.subarray(0, 7));
          this.emit('data', response.subarray(7));
          this.emit('close');
        }
      });
    }
    destroy() {}
  }
  const client = new RconClient({
    host: '127.0.0.1', port: 25575, password: 'secret', timeoutMs: 1000,
    netImpl: { createConnection() {
      const socket = new Socket();
      process.nextTick(() => socket.emit('connect'));
      return socket;
    } },
  });
  assert.equal(await client.execute('cc game test status'), 'ok');
});

test('Core command construction keeps single and event syntax aligned with Core', () => {
  assert.equal(commandForStart({
    coreSessionMode: 'single', coreGame: 'tntrun', coreMap: 'astra', coreProfile: 'idle',
  }), 'cc game test single start tntrun astra idle');
  assert.equal(commandForStart({
    coreSessionMode: 'event', coreEventProfile: 'smoke', coreDifficulty: 'HARD', coreRemix: 'NETHER',
  }), 'cc game test event start smoke HARD NETHER');
  assert.throws(() => commandForStart({
    coreSessionMode: 'single', coreGame: 'tntrun;stop', coreMap: 'astra', coreProfile: 'idle',
  }), /CC_TEST_CORE_GAME/);
});

test('Core status parser recognizes READY, terminal and no-session replies', () => {
  const ready = parseStatus('测试会话 abc | 模式=SINGLE_GAME | 状态=READY | 已绑定=64 | transient teams=16 | event round=0 | standings=0');
  assert.deepEqual(ready, {
    text: '测试会话 abc | 模式=SINGLE_GAME | 状态=READY | 已绑定=64 | transient teams=16 | event round=0 | standings=0',
    present: true, state: 'READY', bound: 64, transientTeams: 16, eventRound: 0, standings: 0,
  });
  assert.equal(parseStatus('当前没有测试会话').present, false);
  assert.equal(parseStatus('状态=COMPLETED').state, 'COMPLETED');
});

test('Core control rejects unsuccessful command feedback', async () => {
  const commands = [];
  const control = new CoreTestControl({
    coreSessionMode: 'single', coreGame: 'tntrun', coreMap: 'astra', coreProfile: 'idle',
  }, { execute: async command => { commands.push(command); return '测试游戏未启动：roster incomplete'; } });
  await assert.rejects(control.run(), /did not start/);
});

test('async Core commands accept an empty RCON acknowledgement', async () => {
  const commands = [];
  const control = new CoreTestControl({
    coreSessionMode: 'single', coreGame: 'tntrun', coreMap: 'astra', coreProfile: 'idle',
  }, { execute: async command => { commands.push(command); return ''; } });
  await control.leaseWeb();
  await control.run();
  assert.deepEqual(commands, ['cc game test lease web', 'cc game test run']);
});
