const test = require('node:test');
const assert = require('node:assert/strict');
const { GAME_PROFILES, actionProfile, runProfileTick, supportedGameProfiles } = require('../profiles');

test('every matrix game has a data driven profile and mechanism contract', () => {
  assert.equal(supportedGameProfiles().length, 17);
  for (const profile of Object.values(GAME_PROFILES)) {
    assert.ok(profile.cadenceMs > 0);
    assert.ok(profile.mechanism);
    assert.ok(profile.interaction);
    assert.ok(profile.contract);
    assert.deepEqual(profile.actions, profile.contract.actions);
  }
});

test('game profile emits phase appropriate actions and records mechanism packets', () => {
  const writes = [];
  const records = [];
  const client = { write: (name, params) => writes.push({ name, params }) };
  const ctx = {
    client,
    index: 0,
    step: 20,
    phase: 'PROGRESS',
    position: { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 },
    origin: { x: 0, y: 64, z: 0 },
    entityId: 1,
    nextSequence: () => 4,
    nearestEntity: () => 7,
    entityLocation: () => ({ x: 0, y: 64, z: 0 }),
    allowWorldMutation: true,
    record: (kind, value) => records.push([kind, value]),
  };
  const count = runProfileTick(actionProfile({ action: 'game', game: 'battlebox' }), ctx);
  assert.ok(count > 0);
  assert.ok(writes.some(packet => packet.name === 'attack'));
  assert.ok(records.some(([, value]) => value === 'weapon-combat'));
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
      position: { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 }, origin: { x: 0, y: 64, z: 0 },
      nextSequence: () => 2, nearestEntity: () => 3, record: () => {},
    };
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

test('entity actions can restrict targets to the game mechanism entity type', () => {
  const profile = actionProfile({ action: 'game', game: 'sulfursoccer' });
  const writes = [];
  const client = { write: (name, params) => writes.push({ name, params }) };
  let types;
  runProfileTick(profile, {
    client, index: 0, step: 20, phase: 'PROGRESS', entityId: 1,
    position: { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 }, origin: { x: 0, y: 64, z: 0 },
    nextSequence: () => 1,
    nearestEntity: (_distance, allowed) => { types = allowed; return 2; },
    entityLocation: () => ({ x: 0, y: 64, z: 0 }), record: () => {},
  });
  assert.deepEqual(types, ['sulfur_cube']);
  assert.ok(writes.some(write => write.name === 'attack'));
});
