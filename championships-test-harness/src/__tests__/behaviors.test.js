const test = require('node:test');
const assert = require('node:assert/strict');
const { actionProfile, runProfileTick } = require('../profiles');

function harness(game, options = {}) {
  const writes = [];
  const records = [];
  let sequence = 0;
  const ctx = {
    client: { write: (name, params) => writes.push({ name, params }) },
    index: 0,
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
  const result = harness('dodgebolt', { nearestEntity: () => 7, entityLocation: near });
  assert.ok(result.names.includes('use_item'), 'charges/releases the bow');
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
  assert.ok(result.names.includes('position'), 'moves');
  assert.ok(result.names.includes('entity_action'), 'sprints');
  assert.ok(result.names.includes('player_input'), 'jumps on cadence');
  assert.ok(result.mechanisms.includes('floor-break'));
});

test('parkourtag chases a distant runner and tags at close range', () => {
  const chase = harness('parkourtag', { nearestEntity: () => 7, entityLocation: far });
  assert.ok(chase.names.includes('position_look'), 'moves toward the target');
  const tag = harness('parkourtag', { nearestEntity: () => 7, entityLocation: near });
  assert.ok(tag.names.includes('use_item'), 'uses the chaser tool at close range');
});
