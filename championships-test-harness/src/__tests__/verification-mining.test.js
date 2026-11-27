const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { Vec3 } = require('vec3');
const data = require('minecraft-data')('26.3');
const Block = require('prismarine-block')('26.3');
const { VerificationPickaxe } = require('../profiles/verification-pickaxe');

function course(room, range = 2) {
  const block = name => Block.fromStateId(data.blocksByName[name].defaultState);
  const pos = new Vec3(range, 81, 0);
  const pick = { name: 'iron_pickaxe', itemId: data.itemsByName.iron_pickaxe.id, components: [
    { type: 'custom_data', data: { value: { PublicBukkitValues: { value: {
      'championshipscore:robot_prop': { type: 'string', value: room + '_PICK/session' },
    } } } } },
  ] };
  const state = { heldSlot: 0, hotbar: [null, pick] };
  const writes = [], cells = new Map([[pos.toString(), block(room === 'JOINT' ? 'gray_concrete' : 'bricks')]]);
  const client = new EventEmitter();
  client.write = (name, packet) => writes.push({ name, packet });
  const movement = { client,
    player: { pos: new Vec3(.5, 81, .5), onGround: true }, keys: {}, worldName: 'robot_verification',
    getBlock: p => cells.get(p.toString()) ?? block('air'),
    write: (_, hit) => { movement.look = hit; },
  };
  let sequence = 0;
  const miner = new VerificationPickaxe(movement, state, slot => {
    writes.push({ name: 'held_item_slot' }); state.heldSlot = slot;
  }, () => ++sequence);
  const tick = () => miner.tick(movement.look ?? { yaw: 0, pitch: 0 });
  return { miner, pos, tick, writes, cells, movement, state, block };
}

for (const room of ['WAREHOUSE', 'JOINT']) {
  test(room + ' holds the issued pick across repeated breaks and faces the visible side', () => {
    const { miner, pos, tick, writes } = course(room);
    for (let n = 0; n < 2; n++) {
      assert.ok(miner.start(pos, room));
      for (let t = 0; t < 60; t++) tick();
    }
    assert.equal(writes.filter(p => p.name === 'held_item_slot').length, 1);
    const digs = writes.filter(p => p.name === 'block_dig');
    assert.deepEqual(digs.map(d => [d.packet.status, d.packet.face, d.packet.sequence]),
      [[0, 4, 1], [3, 4, 2], [0, 4, 3], [3, 4, 4]]);
    assert.ok(writes.filter(p => p.name === 'punch').length > 4);
  });
}
test('joint uses the issued reach, respects obstruction and aborts if the tool changes', () => {
  const { miner, pos, tick, writes, cells, block, state } = course('JOINT', 6);
  assert.equal(miner.start(pos, 'WAREHOUSE'), false);
  assert.ok(miner.start(pos, 'JOINT'));
  tick();
  tick();
  state.heldSlot = 0;
  tick();
  assert.deepEqual(writes.filter(p => p.name === 'block_dig').map(p => p.packet.status), [0, 2]);
  cells.set(new Vec3(3, 82, 0).toString(), block('stone'));
  cells.set(new Vec3(3, 81, 0).toString(), block('stone'));
  writes.length = 0;
  assert.ok(miner.start(pos, 'JOINT'));
  tick();
  tick();
  assert.ok(!writes.some(p => p.name === 'block_dig'));
});

test('a world change discards pending mining and its original coordinates', () => {
  const { miner, pos, tick, writes, movement } = course('JOINT');
  assert.ok(miner.start(pos, 'JOINT'));
  tick();
  tick();
  assert.ok(miner.mining.active);
  movement.client.emit('respawn');
  const previous = writes.length;
  for (let t = 0; t < 30; t++) tick();
  assert.equal(writes.length, previous);
});
