const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { attachClient, successfulRun } = require('../client');

const { loadGround } = require('./support/terrain');

function fixture(action = 'idle', overrides = {}) {
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
  attachClient(client, { action, password: 'ephemeral', ...overrides }, 0, result, state, timers);
  const position = (overrides, withTerrain = true) => {
    const packet = { teleportId: 7, x: 10, y: 64, z: -5, yaw: 90, pitch: 0, flags: {}, ...overrides };
    if (withTerrain) loadGround(client, packet.x, packet.z);
    client.emit('position', packet);
  };
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
    'teleport_confirm', 'tick_end', 'player_loaded', 'teleport_confirm', 'tick_end',
  ]);
  assert.equal(f.writes[3].data.teleportId, 8);
  assert.equal(f.writes[3].data.x, 12);
  assert.equal(f.writes[3].data.y, 65);
  assert.equal(f.writes[3].data.yaw, 95);
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
  assert.ok(f.writes.at(-2).data.x > 10 && f.writes.at(-2).data.x < 10.05);
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
  assert.ok(f.writes.at(-2).data.x > 1000 && f.writes.at(-2).data.x < 1000.05);
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

test('reconnect history records phase, identity and restored world evidence', () => {
  const f = fixture('game', { game: 'tntrun' });
  f.client.emit('success', { uuid: 'same-player' });
  f.client.emit('login', { entityId: 42 });
  f.position();
  f.client.emit('teams', { name: 'red', mode: 0, players: ['autobot00'] });
  f.client.emit('system_chat', { content: 'Game in progress' });
  f.client.emit('end', 'injected phase disconnect');
  f.client.emit('success', { uuid: 'same-player' });
  f.client.emit('login', { entityId: 43 });
  f.position({ teleportId: 8 });
  assert.deepEqual(f.state.reconnectHistory.map(item => item.phase), ['PROGRESS']);
  assert.equal(f.state.reconnectHistory[0].identityPreserved, true);
  assert.equal(f.state.reconnectHistory[0].worldReadyAfter, true);
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

test('death and dimension transfers preserve the observed match phase', () => {
  const f = fixture('game', { game: 'bingo', disconnectPhase: 'PROGRESS' });
  f.client.emit('login', { entityId: 42 });
  f.client.emit('death_combat_event', { playerId: 25 });
  f.client.emit('respawn');
  assert.equal(f.state.phase, 'WAITING');
  f.client.emit('action_bar', { text: '比赛开始倒计时' });
  f.client.emit('death_combat_event', { playerId: 42 });
  f.client.emit('respawn');
  assert.equal(f.state.phase, 'COUNTDOWN');
  assert.equal(f.result.injectedDisconnects || 0, 0);
  f.client.emit('set_title_subtitle', { text: { type: 'string', value: '卡片已发放' } });
  assert.equal(f.state.phase, 'PROGRESS');
  assert.equal(f.result.injectedDisconnects, 1);
  f.client.emit('system_chat', { content: '本场结束' });
  f.client.emit('respawn');
  assert.equal(f.state.phase, 'END');
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
    assert.equal(f.result.actionPackets, ({ walk: 100, 'bingo-walk': 20, look: 10, idle: 0 })[action]);
  }
});

test('airborne Bingo client waits for terrain, falls and lands on the received floor', () => {
  const f = fixture('bingo-walk');
  f.client.emit('login');
  f.position({ y: 68 }, false);
  loadGround(f.client, 128, 128);
  assert.equal(f.state.worldReady, false);
  assert.equal(f.writes.some(w => w.name === 'player_loaded'), false);
  loadGround(f.client, 10, -5);
  assert.equal(f.state.worldReady, true);
  assert.equal(f.writes.filter(w => w.name === 'player_loaded').length, 1);
  f.settle();
  const tick = [...f.callbacks][0];
  for (let i = 0; i < 40; i++) tick();
  const moves = f.writes.filter(w => w.name === 'position').map(w => w.data);
  assert.ok(moves.some(p => p.y < 68 && p.y > 64 && !p.flags.onGround));
  assert.ok(moves.every(p => p.y >= 64 && (!p.flags.onGround || p.y === 64)));
  assert.equal(moves.at(-1).flags.onGround, true);
  assert.equal(moves.at(-1).y, 64);
});

test('game resident profile follows server phase before emitting mechanism actions', () => {
  const f = fixture('game', { game: 'battlebox' });
  f.client.emit('login', { entityId: 1 });
  f.position();
  f.settle();
  const tick = [...f.callbacks][0];
  for (let index = 0; index < 20; index += 1) tick();
  assert.equal(f.result.actionPackets, 0); // waiting phase is observation-only
  f.client.emit('system_chat', { content: 'GO!' });
  for (let index = 0; index < 20; index += 1) tick();
  assert.ok(f.result.actionPackets > 1);
  assert.equal(f.state.phase, 'PROGRESS');
  assert.ok(f.state.mechanismPackets['weapon-combat'] || f.state.mechanismPackets['weapon-combat-search']);
});

test('scoreboard packets are captured with the phase for CI score assertions', () => {
  const f = fixture();
  f.client.emit('login');
  f.client.emit('scoreboard_score', { itemName: 'autobot00', scoreName: 'points', value: 12 });
  assert.equal(f.state.scorePackets, 1);
  assert.deepEqual(f.state.scores.points.autobot00, 12);
  assert.equal(f.state.scoreHistory[0].phase, 'WAITING');
});

test('server observations expose legal targets and scoreboard/team evidence', () => {
  const f = fixture('game', { game: 'buildmart' });
  f.client.emit('login', { entityId: 42 });
  f.client.emit('spawn_entity', { entityId: 99, type: 'display' });
  f.client.emit('block_change', { location: { x: 4, y: 64, z: -2 }, type: 1 });
  f.client.emit('scoreboard_objective', { name: 'points', mode: 0 });
  f.client.emit('teams', { name: 'red', mode: 0 });
  assert.deepEqual(f.state.targetBlock, { x: 4, y: 64, z: -2 });
  assert.equal(f.state.observations.entitySpawns, 1);
  assert.equal(f.state.observations.blockChanges, 1);
  assert.equal(f.state.observations.scoreboardObjectives, 1);
  assert.equal(f.state.observations.teamPackets, 1);
  assert.equal(f.state.scoreObjectives.points, 0);
  assert.equal(f.state.evidence.blocks[0].event, 'change');
  assert.equal(f.state.evidence.entities[0].event, 'spawn');
  assert.equal(f.state.evidence.windows.length, 0);
});

test('reconnect reports UUID, team and game mode history for identity assertions', () => {
  const f = fixture();
  f.client.emit('success', { uuid: '11111111-1111-4111-8111-111111111111' });
  f.client.emit('teams', { name: 'test01', mode: 0, players: ['autobot00'] });
  f.client.emit('game_state_change', { gameMode: 2 });
  f.client.emit('success', { uuid: '11111111-1111-4111-8111-111111111111' });
  assert.deepEqual(f.state.uuidHistory, ['11111111-1111-4111-8111-111111111111']);
  assert.deepEqual(f.state.teamHistory[0], { name: 'test01', mode: 0, players: ['autobot00'] });
  assert.deepEqual(f.state.gameModeHistory, [2]);
});

test('reconnect keeps the last game phase until the server sends a new phase signal', () => {
  const f = fixture('game', { game: 'tntrun' });
  f.client.emit('login', { entityId: 1 });
  f.client.emit('system_chat', { content: 'GO!' });
  assert.equal(f.state.phase, 'PROGRESS');
  f.client.emit('end', 'network drop');
  assert.equal(f.state.phase, 'PROGRESS');
});

test('recognizes the live verification room title when entering the joint after game start', () => {
  const f = fixture('game');
  f.client.emit('login', { entityId: 42 });
  f.client.emit('set_title_text', { text: '第三关 · 向右推动滑块到底，完全嵌合' });
  assert.equal(f.state.detectedGame, 'iamnotarobot');
  assert.equal(f.state.phase, 'PROGRESS');
});

test('resident auto detection switches games, phase gates attacks, and ignores chat naming a game', () => {
  const f = fixture('game');
  const tick = () => { for (const callback of f.callbacks) callback(); };
  f.client.emit('login', { entityId: 42 });
  f.position({});
  f.settle();
  f.client.emit('system_chat', { content: '烫手鳕鱼 • 开赛' });
  tick();
  assert.equal(f.state.detectedGame, null);
  assert.deepEqual(f.state.actionTypes, {});
  f.client.emit('set_title_text', { text: '烫手鳕鱼' });
  f.client.emit('set_title_subtitle', { text: '烫手鳕鱼 • 即将开始' });
  const data = require('minecraft-data')('26.3');
  const slot = name => ({ itemId: data.itemsByName[name].id, itemCount: 1 });
  f.client.emit('set_player_inventory', { slotId: 2, contents: slot('structure_void') });
  f.client.emit('set_player_inventory', { slotId: 4, contents: slot('sugar') });
  f.client.emit('spawn_entity', { entityId: 93, type: data.entitiesByName.player.id, x: 11, y: 64, z: -5 });
  f.client.emit('entity_equipment', { entityId: 93, equipments: [{ slot: 5, item: slot('leather_helmet') }] });
  for (let i = 0; i < 10; i++) tick();
  assert.ok(!f.state.actionTypes.attack);
  f.client.emit('action_bar', { text: '第 2 趟 • 鳕鱼抓人倒数 25' });
  tick(); tick(); tick();
  assert.equal(f.state.actionTypes.attack, 1);
  const count = f.state.mechanismPackets['fish-pass'];
  f.client.emit('set_slot', { windowId: 0, slot: 40, item: { itemCount: 0 } });
  for (let i = 0; i < 24; i++) tick();
  assert.equal(f.state.mechanismPackets['fish-pass'], count, 'eliminated player no longer acts');
  f.client.emit('action_bar', { text: '斗战方框 • 开赛' });
  tick(); tick(); tick(); tick();
  assert.equal(f.state.detectedGame, 'battlebox');
  assert.ok(f.state.mechanismPackets['weapon-combat']);
  f.client.emit('action_bar', { text: '未知比赛 • 开赛' });
  assert.equal(f.state.detectedGame, null);
});

test('entity aim tracks 26.3 spawn, deltas, path sync, relative teleport and despawn', () => {
  const f = fixture('game', { game: 'hotycodydusky' });
  f.client.emit('spawn_entity', { entityId: 3, type: 159, x: 10, y: 64, z: -4 });
  const e = f.state.entities.get(3);
  assert.equal(e.type, 'player');
  f.client.emit('entity_move_look', { entityId: 3, delta: { dX: 0.5, dY: -1, dZ: 2 } });
  assert.deepEqual([e.x, e.y, e.z], [10.5, 63, -2]);
  f.client.emit('sync_entity_position', { entityId: 3, position: { path: { steps: [
    { position: { x: 21, y: 70, z: 2 } }, { position: { x: 23, y: 69, z: 1 } },
  ] } } });
  f.client.emit('entity_teleport', { entityId: 3, flags: 1, x: -1, y: 80, z: 20 });
  assert.deepEqual([e.x, e.y, e.z], [22, 80, 20]);
  f.client.emit('entity_destroy', { entityIds: [3] });
  assert.equal(f.state.entities.size, 0);
});

test('same-world corrections keep the action clock running while the terrain is loaded', () => {
  const f = fixture('walk');
  f.client.emit('login');
  f.position();
  f.settle();
  const loop = [...f.callbacks][0];
  for (let id = 8; id < 20; id++) {
    f.position({ teleportId: id }, false);
    assert.equal([...f.callbacks][0], loop);
    assert.equal(f.delayed.size, 0);
    loop();
  }
  f.position({ x: 200 }, false);
  assert.equal(f.state.worldReady, false);
  assert.equal(f.callbacks.size, 0);
  loadGround(f.client, 200, -5);
  f.settle();
  assert.equal(f.callbacks.size, 1);
});
