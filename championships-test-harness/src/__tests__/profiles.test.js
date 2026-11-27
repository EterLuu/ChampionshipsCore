const test = require('node:test');
const assert = require('node:assert/strict');
const { GAME_PROFILES, actionProfile, runProfileTick, supportedGameProfiles } = require('../profiles');

test('every matrix game has a data driven profile and mechanism contract', () => {
  assert.equal(supportedGameProfiles().length, 19);
  for (const profile of Object.values(GAME_PROFILES)) {
    assert.ok(profile.cadenceMs > 0);
    assert.ok(profile.mechanism);
    assert.ok(profile.interaction);
    assert.ok(profile.contract);
    assert.deepEqual(profile.actions, profile.contract.actions);
  }
});

test('voting profile clicks an observed window once', () => {
  const writes = [];
  const client = { write: (name, params) => writes.push({ name, params }) };
  const ctx = {
    client, index: 0, step: 1, phase: 'VOTING', window: { windowId: 2, stateId: 3 },
    voteSlot: 12, voteSent: false, position: { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 },
    origin: { x: 0, y: 0, z: 0 }, nextSequence: () => 1, nearestEntity: () => null,
    record: () => {},
  };
  runProfileTick(actionProfile({ action: 'game', game: 'tntrun' }), ctx);
  assert.equal(writes[0].name, 'window_click');
  assert.equal(writes[0].params.slot, 12);
});

test('block-interaction profiles use an observed preparation block', () => {
  const writes = [];
  const client = { write: (name, params) => writes.push({ name, params }) };
  const context = {
    client, index: 0, step: 20, phase: 'PREPARATION', window: { windowId: 4, stateId: 8 },
    menuSlot: 7, menuSent: false, position: { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 },
    origin: { x: 0, y: 0, z: 0 }, targetBlock: { x: 2, y: 64, z: 2 },
    nextSequence: () => 1, nearestEntity: () => null,
    record: () => {},
  };
  const profile = actionProfile({ action: 'game', game: 'battlebox' });
  assert.equal(runProfileTick(profile, context), 1);
  assert.equal(writes[0].name, 'block_place');
  assert.deepEqual(writes[0].params.hitResult.location, { x: 2, y: 64, z: 2 });
  assert.equal(runProfileTick(profile, context), 1);
  assert.equal(writes.filter(packet => packet.name === 'block_place').length, 2);
});

test('all game profiles emit protocol-valid progress actions', () => {
  const protocol = require('@wp2508/minecraft-protocol');
  const serializer = protocol.createSerializer({ state: 'play', isServer: false, version: '26.3' });
  for (const game of supportedGameProfiles()) {
    const writes = [];
    const client = { write: (name, params) => {
      serializer.createPacketBuffer({ name, params });
      writes.push(name);
    } };
    const profile = actionProfile({ action: 'game', game });
    const context = {
      client, index: 0, step: 20, phase: 'PROGRESS', entityId: 1,
      hotbar: [null, null, { name: 'structure_void' }, null, { name: 'barrier' }], entities: new Map(),
      tick: 20, memory: {}, walkingHeading: angle => ({ angle }), fleeFrom: (_, angle) => ({ angle }), navigate(goal) { return { angle: Math.atan2(goal.z - this.position.z, goal.x - this.position.x) }; },
      position: { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 }, origin: { x: 0, y: 64, z: 0 },
      nextSequence: () => 2, nearestEntity: () => 3, record: () => {},
    };
    if (game === 'iamnotarobot') {
      assert.equal(runProfileTick(profile, context), 0, 'world mutation is disabled');
      continue; // The scenario test supplies the round board, terrain and issued tool.
    }
    if (game === 'parkourtag') context.hotbar[0] = { name: 'feather' };
    if (game === 'dodgebolt') Object.assign(context, { hotbar: [{ name: 'bow' }],
      heldSlot: 0, inventory: { 36: { name: 'arrow' } }, entityLocation: () => context.position });
    context.heldSlot = 0;
    assert.ok(runProfileTick(profile, context) > 0, game);
    assert.ok(writes.length > 0, game);
  }
});

test('game profiles map their mechanism to the server event input', () => {
  assert.ok(actionProfile({ action: 'game', game: 'lasershowdown' }).actions.includes('swing'));
  assert.ok(actionProfile({ action: 'game', game: 'laserbox' }).actions.includes('swing'));
  assert.ok(actionProfile({ action: 'game', game: 'sulfursoccer' }).actions.includes('attack'));
  assert.ok(!actionProfile({ action: 'game', game: 'dodgebolt' }).actions.includes('attack'));
  assert.ok(!actionProfile({ action: 'game', game: 'riptide' }).actions.includes('use-item'));
});

function harness(game, options = {}) {
  const writes = [];
  const records = [];
  let sequence = 0;
  const ctx = {
    client: { write: (name, params) => writes.push({ name, params }) },
    index: 0, memory: options.memory || {}, tick: options.tick ?? 20,
    entities: options.entities || new Map(), inventory: options.inventory || {}, walkingHeading: angle => ({ angle }), fleeFrom: (_, angle) => ({ angle }), navigate(goal) { return { angle: Math.atan2(goal.z - this.position.z, goal.x - this.position.x) }; },
    step: options.step ?? 20,
    phase: options.phase || 'PROGRESS',
    position: { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 },
    origin: { x: 0, y: 64, z: 0 },
    entityId: 1,
    nextSequence: () => sequence++,
    nearestEntity: options.nearestEntity || (() => null),
    entityLocation: options.entityLocation,
    targetBlock: options.targetBlock,
    recentBlocks: options.recentBlocks,
    allowWorldMutation: options.allowWorldMutation === true,
    hotbar: options.hotbar,
    heldSlot: options.heldSlot ?? 0,
    selectHeldSlot: slot => { writes.push({ name: 'held_item_slot', params: { slotId: slot } }); return true; },
    record: (kind, value) => records.push([kind, value]),
  };
  const emitted = runProfileTick(actionProfile({ action: 'game', game }), ctx);
  return {
    emitted,
    writes,
    names: writes.map(write => write.name),
    mechanisms: records.filter(([kind]) => kind === 'mechanismPackets').map(([, value]) => value),
  };
}

const WEAPON_HOTBAR = [
  { name: 'stone' }, { name: 'diamond_sword' }, null, null, null, null, null, null, null,
];
const near = () => ({ x: 1, y: 64, z: 0 });
const far = () => ({ x: 20, y: 64, z: 0 });

test('battlebox equips, aims and attacks only when the target is in melee reach', () => {
  const close = harness('battlebox', {
    nearestEntity: () => 7, entityLocation: near, hotbar: WEAPON_HOTBAR,
  });
  assert.ok(close.names.includes('held_item_slot'), 'selects a real weapon slot');
  assert.equal(close.writes.find(write => write.name === 'held_item_slot').params.slotId, 1);
  assert.ok(close.names.includes('look'), 'aims at the target');
  assert.ok(close.names.includes('attack'), 'attacks in reach');
  assert.ok(close.mechanisms.includes('weapon-combat'));

  const distant = harness('battlebox', {
    nearestEntity: () => 7, entityLocation: far, hotbar: WEAPON_HOTBAR,
  });
  assert.ok(!distant.names.includes('attack'), 'does not swing at an out-of-reach target');
  assert.ok(distant.names.includes('look'), 'still tracks the far target');
  assert.ok(distant.names.includes('entity_action'), 'sprints toward the fight');
});

test('battlebox still works when the server has not sent an inventory', () => {
  const result = harness('battlebox', { nearestEntity: () => 7, entityLocation: near });
  assert.ok(result.names.includes('attack'));
  assert.ok(!result.names.includes('held_item_slot'));
});

test('dodgebolt draws the bow and never sends a melee attack', () => {
  const memory = {};
  const options = { nearestEntity: () => 7, entityLocation: near, memory,
    hotbar: [{ name: 'bow' }], inventory: { 37: { name: 'arrow' } } };
  const result = harness('dodgebolt', options);
  assert.ok(result.names.includes('use_item'), 'begins drawing');
  assert.ok(!harness('dodgebolt', { ...options, tick: 30 }).names.includes('block_dig'));
  const shot = harness('dodgebolt', { ...options, tick: 40 }).writes.find(p => p.name === 'block_dig');
  assert.equal(shot.params.status, 6, 'releases the charged bow');
  assert.ok(result.names.includes('look'), 'aims before shooting');
  assert.ok(!result.names.includes('attack'), 'dodgebolt has no melee attack');
});

test('buildmart digs and places only with world mutation enabled', () => {
  const targetBlock = { x: 1, y: 64, z: 1 };
  const dig = harness('buildmart', { targetBlock, allowWorldMutation: true, step: 20 });
  assert.ok(dig.names.includes('block_dig'), 'harvests the observed material block');
  const place = harness('buildmart', { targetBlock, allowWorldMutation: true, step: 21 });
  assert.ok(place.names.includes('block_place'), 'places a matching block');
  assert.ok(dig.mechanisms.includes('collect-place-submit'));

  const readOnly = harness('buildmart', { targetBlock, allowWorldMutation: false });
  assert.ok(!readOnly.names.includes('block_dig'));
  assert.ok(!readOnly.names.includes('block_place'), 'never mutates the world when not allowed');
  assert.ok(readOnly.names.includes('position'), 'falls back to moving');
});

test('buildmart harvests the nearest reachable observed block', () => {
  const result = harness('buildmart', {
    targetBlock: { x: 20, y: 64, z: 20 },
    recentBlocks: [{ x: 12, y: 64, z: 0 }, { x: 1, y: 64, z: 1 }],
    allowWorldMutation: true,
    step: 20,
  });
  const dig = result.writes.find(write => write.name === 'block_dig');
  assert.ok(dig, 'digs an observed block');
  assert.deepEqual(dig.params.location, { x: 1, y: 64, z: 1 }, 'picks the nearest block inside reach');
});

test('sulfursoccer only targets the sulfur cube and kicks it in reach', () => {
  let allowedTypes;
  const close = harness('sulfursoccer', {
    nearestEntity: (_distance, allowed) => { allowedTypes = allowed; return 9; },
    entityLocation: near,
  });
  assert.deepEqual(allowedTypes, ['sulfur_cube']);
  assert.ok(close.names.includes('attack'), 'kicks the ball');
  assert.ok(close.mechanisms.includes('kick-pearl-score'));

  const distant = harness('sulfursoccer', {
    nearestEntity: () => 9, entityLocation: far,
  });
  assert.ok(!distant.names.includes('attack'), 'does not kick from out of reach');
  assert.ok(distant.names.includes('position_look'), 'runs toward the ball');
});

test('laser games aim, fire with a swing and use the ability item', () => {
  for (const game of ['lasershowdown', 'laserbox']) {
    const result = harness(game, { nearestEntity: () => 7, entityLocation: near, step: 20 });
    assert.ok(result.names.includes('look'), game);
    assert.ok(result.names.includes('punch'), `${game} fires with a swing`);
    assert.ok(result.names.includes('use_item'), `${game} uses its ability item`);
    assert.ok(result.mechanisms.length > 0, game);
  }
});

test('bingo chooses entity interaction, block digging or roaming from server state', () => {
  const entity = harness('bingo', { nearestEntity: () => 9, entityLocation: near });
  assert.ok(entity.names.includes('use_entity'));

  const block = harness('bingo', {
    targetBlock: { x: 1, y: 64, z: 1 }, allowWorldMutation: true,
  });
  assert.ok(block.names.includes('block_dig'));

  const roam = harness('bingo', {});
  assert.ok(roam.names.includes('position'), 'roams when nothing is nearby');
  assert.ok(roam.mechanisms.includes('task-progress'));
});

test('movement games run, sprint and jump on their configured cadence', () => {
  const result = harness('tntrun', { step: 12 });
  assert.ok(result.names.includes('position_look'), 'moves');
  assert.ok(result.names.includes('entity_action'), 'sprints');
  assert.ok(result.names.includes('player_input'), 'jumps on cadence');
  assert.ok(result.mechanisms.includes('floor-break'));
});

test('parkourtag chases a distant runner and tags at close range', () => {
  const options = pos => ({ entities: new Map([[7, { entityId: 7, type: 'player', hand: 'ender_eye', ...pos() }]]),
    hotbar: [{ name: 'feather' }], nearestEntity: () => 7, entityLocation: pos });
  const chase = harness('parkourtag', options(far));
  assert.ok(chase.names.includes('position_look'), 'moves toward the target');
  const tag = harness('parkourtag', options(near));
  assert.ok(tag.names.includes('attack'), 'tags the runner at close range');
});
