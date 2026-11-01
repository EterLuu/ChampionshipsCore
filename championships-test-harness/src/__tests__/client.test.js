const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { attachClient, successfulRun } = require('../client');

function fixture(action = 'idle') {
  const client = new EventEmitter();
  client.state = 'play';
  const writes = [];
  client.write = (name, data) => writes.push({ name, data });
  const callbacks = new Set();
  const delayed = new Set();
  const timers = {
    setInterval(callback) { callbacks.add(callback); return callback; },
    clearInterval(callback) { callbacks.delete(callback); },
    setTimeout(callback) { delayed.add(callback); return callback; },
    clearTimeout(callback) { delayed.delete(callback); },
  };
  const state = { active: false, loggedIn: false, stopRequested: false };
  const result = { count: 1, loggedIn: 0, teleportConfirmations: 0,
    authDialogSubmissions: 0, actionPackets: 0, fatalErrors: [] };
  attachClient(client, { action, password: 'ephemeral' }, 0, result, state, timers);
  const position = overrides => client.emit('position', {
    teleportId: 7, x: 10, y: 64, z: -5, yaw: 90, pitch: 0, flags: {}, ...overrides,
  });
  const settle = () => {
    const callback = delayed.values().next().value;
    if (callback) { delayed.delete(callback); callback(); }
  };
  return { client, writes, callbacks, delayed, state, result, position, settle };
}

test('server teleports are confirmed without echoing a movement packet', () => {
  const f = fixture();
  f.client.emit('login');
  f.position();
  f.position({ teleportId: 8, x: 2, y: 1, z: 4, yaw: 5, pitch: -3,
    flags: { x: true, y: true, z: true, yaw: true, pitch: true } });
  assert.deepEqual(f.writes.map(write => write.name), [
    'teleport_confirm', 'tick_end', 'teleport_confirm', 'tick_end',
  ]);
  assert.equal(f.writes[2].data.teleportId, 8);
  assert.equal(f.writes[2].data.x, 12);
  assert.equal(f.writes[2].data.y, 65);
  assert.equal(f.writes[2].data.yaw, 95);
  assert.equal(f.result.teleportConfirmations, 2);
  assert.equal(f.callbacks.size, 0);
  assert.equal(f.delayed.size, 1);
});

test('protocol 777 teleport confirmation serializes finite coordinates and rotations', () => {
  const protocol = require('@wp2508/minecraft-protocol');
  const serializer = protocol.createSerializer({ state: 'play', isServer: false, version: '26.3' });
  const deserializer = protocol.createDeserializer({ state: 'play', isServer: true, version: '26.3' });
  const f = fixture();
  f.client.emit('login');
  f.position();
  const bytes = serializer.createPacketBuffer({ name: f.writes[0].name, params: f.writes[0].data });
  const decoded = deserializer.parsePacketBuffer(bytes).data;
  assert.equal(decoded.name, 'teleport_confirm');
  for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) {
    assert.ok(Number.isFinite(decoded.params[key]), key);
    assert.equal(decoded.params[key], f.writes[0].data[key]);
  }
});

test('cross-server login/respawn stops old actions until a fresh teleport arrives', () => {
  const f = fixture('walk');
  f.client.emit('login');
  assert.equal(f.callbacks.size, 0);
  f.position();
  f.settle();
  const tick = [...f.callbacks][0];
  tick();
  assert.equal(f.writes.at(-2).data.x, 10.05);
  assert.equal(f.writes.at(-1).name, 'tick_end');
  f.position();
  f.settle();
  assert.equal(f.callbacks.size, 1);
  f.client.emit('respawn');
  assert.equal(f.callbacks.size, 0);
  f.position({ x: 200 });
  f.client.emit('login');
  assert.equal(f.callbacks.size, 0);
  assert.equal(f.result.loggedIn, 1);
  f.position({ x: 1000 });
  f.settle();
  [...f.callbacks][0]();
  assert.equal(f.writes.at(-2).data.x, 1000.05);
  f.client.emit('end');
  assert.equal(f.callbacks.size, 0);
  assert.equal(f.state.active, false);
});

test('a previously logged-in client disconnected without an error cannot pass', () => {
  const f = fixture();
  f.client.emit('login');
  assert.equal(successfulRun([f.state], f.result), false);
  f.position();
  f.settle();
  assert.equal(successfulRun([f.state], f.result), true);
  f.client.emit('end');
  assert.equal(f.result.loggedIn, 1);
  assert.equal(successfulRun([f.state], f.result), false);
});

test('health and own combat death request one real protocol respawn and stop old movement', () => {
  const f = fixture('walk');
  f.client.emit('login', { entityId: 42 });
  f.position();
  f.settle();
  f.client.emit('death_combat_event', { playerId: 12 });
  assert.equal(f.state.dead, false);
  f.client.emit('update_health', { health: 0 });
  f.client.emit('death_combat_event', { playerId: 42 });
  f.client.emit('update_health', { health: 0 });
  assert.equal(f.callbacks.size, 0);
  assert.equal(f.result.deaths, 1);
  assert.equal(f.result.respawnRequests, 1);
  assert.equal(successfulRun([f.state], f.result), false);
  const request = f.writes.find(w => w.name === 'client_command');
  const protocol = require('@wp2508/minecraft-protocol');
  const encoder = protocol.createSerializer({ state: 'play', isServer: false, version: '26.3' });
  const decoder = protocol.createDeserializer({ state: 'play', isServer: true, version: '26.3' });
  const decoded = decoder.parsePacketBuffer(encoder.createPacketBuffer({
    name: request.name, params: request.data,
  })).data;
  assert.equal(decoded.name, 'client_command');
  assert.equal(decoded.params.actionId, 0);
  // Even a teleport while dead must not restart movement or make the run pass.
  const at = f.writes.length;
  f.position({ x: 20 });
  assert.deepEqual(f.writes.slice(at).map(w => w.name), ['teleport_confirm', 'tick_end']);
  assert.equal(f.callbacks.size, 0);
  f.client.emit('respawn');
  assert.equal(f.result.deathRespawns, 1);
  assert.equal(successfulRun([f.state], f.result), false);
  f.position({ x: 300 });
  f.settle();
  assert.equal(successfulRun([f.state], f.result), true);
  f.client.emit('death_combat_event', { playerId: 42 });
  assert.equal(f.result.deaths, 2);
  assert.equal(f.result.respawnRequests, 2);
});

test('world switches and configuration are not completed death respawns or ready worlds', () => {
  const f = fixture();
  f.client.emit('login');
  f.position();
  f.client.emit('respawn');
  assert.equal(f.result.respawnPackets, 1);
  assert.equal(f.result.deathRespawns || 0, 0);
  f.position();
  f.client.emit('start_configuration');
  assert.equal(successfulRun([f.state], f.result), false);
  f.client.state = 'configuration';
  f.client.emit('update_health', { health: 0 });
  assert.equal(f.result.respawnRequests || 0, 0);
  f.client.state = 'play';
  f.client.emit('login', { entityId: 90 });
  f.position();
  f.state.stopRequested = true;
  f.client.emit('update_health', { health: 0 });
  assert.equal(f.result.respawnRequests || 0, 0);
});

test('unrelated dialogs are ignored and AuthMe payloads remain protocol NBT', () => {
  const f = fixture();
  f.client.emit('show_dialog', { dialog: 'unrelated:button' });
  assert.equal(f.writes.length, 0);
  f.client.emit('show_dialog', { dialog: 'authme:prejoin-login/submit' });
  assert.equal(f.writes[0].data.id, 'authme:prejoin-login/submit');
  assert.equal(f.writes[0].data.nbt.value.password.type, 'string');
  assert.equal(f.result.authDialogSubmissions, 1);
});

test('all profiles close each client tick, including idle and teleport echoes', () => {
  for (const action of ['idle', 'look', 'walk', 'bingo-walk']) {
    const f = fixture(action);
    f.client.emit('login');
    f.position();
    f.settle();
    const tick = [...f.callbacks][0];
    for (let index = 0; index < 100; index += 1) tick();
    let positionsInTick = 0;
    for (const write of f.writes) {
      if (write.name === 'tick_end') positionsInTick = 0;
      if (write.name === 'position') {
        positionsInTick += 1;
        assert.ok(positionsInTick <= 1, action);
      }
    }
    assert.equal(f.writes.at(-1).name, 'tick_end');
    assert.equal(f.result.actionPackets, ['walk', 'bingo-walk'].includes(action) ? 100 : action === 'look' ? 10 : 0);
  }
});

test('bingo-walk reports grounded movement for statistic objectives', () => {
  const f = fixture('bingo-walk');
  f.client.emit('login');
  f.client.emit('position', {x: 10, y: 64, z: 10, yaw: 0, pitch: 0, teleportId: 1, flags: {}});
  assert.equal(f.result.actionPackets, 0);
  f.settle();
  [...f.callbacks][0]();
  const movement = f.writes.find(write => write.name === 'position');
  assert.equal(movement.data.flags.onGround, true);
});
