const data = require('minecraft-data')('26.3');
const { loadGround } = require('./terrain');
const { EventEmitter } = require('node:events');
const { attachClient } = require('../../client');
const { actionProfile } = require('../../profiles');

function fakeTimers() {
  const intervals = new Set();
  const timeouts = new Set();
  return {
    setInterval(callback) { intervals.add(callback); return callback; },
    clearInterval(callback) { intervals.delete(callback); },
    setTimeout(callback) { timeouts.add(callback); return callback; },
    clearTimeout(callback) { timeouts.delete(callback); },
    start() {
      for (const callback of [...timeouts]) {
        timeouts.delete(callback);
        callback();
      }
    },
    tick(count = 1) {
      for (let index = 0; index < count; index += 1)
        for (const callback of [...intervals]) callback();
    },
  };
}

function fakeClient() {
  const client = new EventEmitter();
  client.state = 'play';
  client.writes = [];
  client.write = (name, params) => client.writes.push({ name, params });
  client.end = reason => client.emit('end', reason);
  return client;
}

function phasePacket(text) {
  return { content: text };
}

// Synthetic packets exercise client reactions only; this fixture does not run
// a server or establish gameplay, protocol-version, or scoring correctness.
const ENTITY_TYPES = {
  sulfursoccer: 'sulfur_cube',
  battlebox: 'player',
  parkourtag: 'player',
  lasershowdown: 'player',
  laserbox: 'player',
  dodgebolt: 'player',
  skywars: 'player',
  frostbite: 'player',
  hotycodydusky: 'player',
  craftingfeast: 'villager',
};

function hotbarItems() {
  // Player inventory slots 36..44 are the hotbar.  Provide a weapon, a block
  // and a usable item so profiles can select real slots instead of guessing.
  const items = new Array(45).fill(null);
  items[36] = { itemCount: 1, itemId: data.itemsByName.diamond_sword.id };
  items[37] = { itemCount: 16, itemId: data.itemsByName.stone.id };
  items[38] = { itemCount: 1, itemId: data.itemsByName.bow.id };
  items[39] = { itemCount: 8, itemId: data.itemsByName.snowball.id };
  return items;
}

function runGameScenario(game) {
  const timers = fakeTimers();
  const result = { count: 1, loggedIn: 0, actionPackets: 0, fatalErrors: [] };
  const state = { active: false, loggedIn: false, stopRequested: false };
  const settings = { action: 'game', game, password: 'scenario', reconnectAttempts: 1, allowWorldMutation: true };
  let client = fakeClient();
  attachClient(client, settings, 0, result, state, timers);
  client.emit('login', { entityId: 1 });
  for (const x of [-16, 0]) for (const z of [-16, 0]) loadGround(client, x, z);
  client.emit('position', { teleportId: 1, x: 0, y: 64, z: 0, yaw: 0, pitch: 0, flags: {} });
  timers.start();

  client.emit('system_chat', phasePacket('Map voting starts now'));
  client.emit('open_window', { windowId: 2, title: { text: 'Vote for map' } });
  timers.tick(20);
  client.emit('system_chat', phasePacket('Preparation phase'));
  client.emit('action_bar', { text: 'Starting in 3' });
  client.emit('set_title_text', { text: 'GO!' });
  client.emit('spawn_entity', { entityId: 99, type: data.entitiesByName[ENTITY_TYPES[game] || 'interaction'].id, x: 1, y: 64, z: 1 });
  client.emit('entity_destroy', { entityIds: [98] });
  client.emit('block_change', { location: { x: 1, y: 63, z: 1 }, type: 1 });
  client.emit('window_items', { windowId: 0, stateId: 4, items: hotbarItems() });
  client.emit('set_slot', { windowId: 0, slot: 36, stateId: 5, item: { itemCount: 1, itemId: data.itemsByName.diamond_sword.id } });
  const stack = name => ({ itemCount: 1, itemId: data.itemsByName[name].id });
  if (game === 'parkourtag') {
    client.emit('set_player_inventory', { slotId: 0, contents: stack('feather') });
    client.emit('entity_equipment', { entityId: 99, equipments: [{ slot: 0, item: stack('ender_eye') }] });
  }
  if (game === 'dodgebolt') {
    client.emit('set_player_inventory', { slotId: 0, contents: stack('bow') });
    client.emit('set_slot', { windowId: 0, slot: 10, item: stack('arrow') });
  }
  if (game === 'hotycodydusky') {
    client.emit('entity_equipment', { entityId: 99, equipments: [{ slot: 5, item: stack('leather_helmet') }] });
    client.emit('set_slot', { windowId: 0, slot: 36, item: { itemCount: 0 } });
    client.emit('set_player_inventory', { slotId: 2, contents: stack('structure_void') });
    client.emit('set_slot', { windowId: 0, slot: 40, item: stack('barrier') });
    for (const [entityId, type, y] of [[100, 'item_display', 65.25], [101, 'interaction', 64.8]])
      client.emit('spawn_entity', { entityId, type: data.entitiesByName[type].id, x: 1, y, z: 1 });
    client.emit('entity_metadata', { entityId: 100, metadata: [{ key: 24, type: 'item_stack', value: stack('compass') }] });
    timers.tick(3);
    client.emit('entity_destroy', { entityIds: [100, 101] });
    client.emit('set_player_inventory', { slotId: 4, contents: stack('sugar') });
    client.emit('entity_teleport', { entityId: 99, x: 5, y: 64, z: 1 });
    timers.tick(6);
    client.emit('entity_teleport', { entityId: 99, x: 1, y: 64, z: 1 });
  }
  if (game === 'iamnotarobot') {
    // Use a translated lane with its gray face and ready board near this fixture's spawn.
    const pick = stack('iron_pickaxe');
    pick.components = [{ type: 'custom_data', data: { value: { PublicBukkitValues: { value: {
      'championshipscore:robot_prop': { type: 'string', value: 'JOINT_PICK/scenario' },
    } } } } }];
    client.emit('set_player_inventory', { slotId: 0, contents: pick });
    client.emit('spawn_entity', { entityId: 101, type: data.entitiesByName.text_display.id, x: 5.5, y: 76.775, z: 12.95 });
    client.emit('entity_metadata', { entityId: 101, metadata: [{ type: 'component', value: { text: '第 1/3 轮' } }] });
    for (const [x, material] of [[0, 'gray_concrete'], [14, 'white_concrete']])
      client.emit('block_change', { location: { x, y: 65, z: 3 }, type: data.blocksByName[material].defaultState });
  }
  if (game === 'craftingfeast')
    client.emit('entity_metadata', { entityId: 99, metadata: [{ key: 2, value: { text: '村民 点餐 面包 5s' } }] });
  timers.tick(24);
  const actionsBeforeDisconnect = client.writes.length;
  const writesBeforeDisconnect = client.writes.slice();
  const phaseBeforeDisconnect = state.phase;
  client.end('scenario disconnect');

  client = fakeClient();
  attachClient(client, settings, 0, result, state, timers);
  client.emit('login', { entityId: 2 });
  for (const x of [-16, 0]) for (const z of [-16, 0]) loadGround(client, x, z);
  client.emit('position', { teleportId: 2, x: 4, y: 64, z: 0, yaw: 0, pitch: 0, flags: {} });
  timers.start();
  timers.tick(24);
  client.emit('system_chat', phasePacket('Team 1 won; results complete'));
  client.emit('scoreboard_score', { itemName: 'autobot00', scoreName: 'points', value: 10 });

  return {
    game,
    phase: state.phase,
    transitions: state.phaseTransitions,
    actionsBeforeDisconnect,
    actionsAfterReconnect: client.writes.length,
    writes: writesBeforeDisconnect.concat(client.writes),
    mechanismPackets: state.mechanismPackets,
    serverEvidence: state.serverEvidence,
    serverEvidencePhases: state.serverEvidencePhases,
    actionTypes: state.actionTypes,
    scoreHistory: state.scoreHistory,
    scorePackets: state.scorePackets || 0,
    matrix: actionProfile({ action: 'game', game }).contract,
    reconnectObserved: state.playLogins >= 2 && phaseBeforeDisconnect === 'PROGRESS',
    loggedIn: state.loggedIn,
  };
}

module.exports = { runGameScenario };
