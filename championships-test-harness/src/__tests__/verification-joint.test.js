const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { Vec3 } = require('vec3');
const data = require('minecraft-data')('26.3');
const Block = require('prismarine-block')('26.3');
const { loadGround } = require('./support/terrain');
const { ClientMovement } = require('../movement');
const { VerificationPickaxe } = require('../profiles/verification-pickaxe');

function rig(start = { x: 5.5, z: 58.5 }) {
  const client = new EventEmitter(), digs = [];
  const origin = new Vec3(256, 63, 0);
  const pos = { x: origin.x + start.x, y: 64, z: start.z, yaw: 0, pitch: 0 };
  client.write = (name, packet) => {
    if (name === 'block_dig') digs.push({ ...packet, feet: { ...pos } });
  };
  const movement = new ClientMovement(client, '26.3');
  client.emit('login', { entityId: 42 });
  for (let x = -32; x <= 32; x += 16)
    for (let z = 32; z < 96; z += 16) loadGround(client, origin.x + x, z);
  const set = (x, y, z, name, properties) => {
    const id = data.blocksByName[name].id;
    const state = properties ? Block.fromProperties(id, properties).stateId : data.blocks[id].defaultState;
    movement.updateBlock(origin.offset(x, y, z), state);
  };
  for (let x = -6; x <= 4; x++) for (let y = 2; y <= 8; y++) for (let z = 60; z <= 66; z++)
    set(x, y, z, x < -4 ? 'iron_block' : x < -1 ? 'light_gray_concrete' : 'gray_concrete');
  for (let x = -17; x < -7; x++) for (let z = 58; z <= 59; z++) {
    const y = Math.min(8, x + 18);
    for (let h = 1; h < y; h++) set(x, h, z, 'polished_andesite');
    if (x <= -10) set(x, y, z, 'polished_deepslate_stairs',
      { facing: 'east', half: 'bottom', shape: 'straight', waterlogged: false });
    else set(x, y, z, 'yellow_concrete');
  }
  for (let x = -12; x <= -8; x++) for (let z = 61; z <= 65; z++) set(x, 8, z, 'yellow_concrete');
  for (const x of [-9, -8]) set(x, 8, 60, 'yellow_concrete');
  for (const z of [61, 62]) set(-7, 8, z, 'polished_andesite');
  // One required top cell remains beyond a two-block-deep pre-cut.
  for (const x of [3, 4]) set(x, 8, 63, 'air');
  for (const x of [16, 17, 18]) set(x, 8, 63, 'white_concrete');
  movement.teleport(pos, {});
  const board = { type: 'text_display', text: '第 2/3 轮', x: origin.x + 9.5, y: origin.y + 13.775, z: 71.95 };
  const pick = { name: 'iron_pickaxe', itemId: data.itemsByName.iron_pickaxe.id, components: [
    { type: 'custom_data', data: { value: { PublicBukkitValues: { value: {
      'championshipscore:robot_prop': { type: 'string', value: 'JOINT_PICK/test' },
    } } } } },
  ] };
  const state = { entities: new Map([[10, board]]), heldSlot: 0, hotbar: [pick] };
  let seq = 0, tick = 0;
  const miner = new VerificationPickaxe(movement, state, slot => { state.heldSlot = slot; }, () => ++seq);
  const step = () => {
    movement.beginDecision();
    miner.workJoint(pos, 0, ++tick);
    movement.tick(pos);
  };
  return { client, movement, miner, pos, set, step, board, state, digs, origin };
}

for (const [name, x, z] of [
  ['the front apron', 5.5, 58.5],
  ['the entrance', 9.5, 41.5],
  ['the far side of the other forklift', 34.5, 78.5],
]) {
  test('climbs the front stairs to excavate a roof target from ' + name, () => {
    const { pos, step, digs, origin, set } = rig({ x, z });
    const trace = [];
    for (let i = 0; i < 600 && !digs.some(d => d.status === 3); i++) {
      step();
      trace.push({ x: pos.x - origin.x, y: pos.y - origin.y, z: pos.z });
    }
    assert.ok(trace.some(p => p.z >= 58 && p.z < 60 && p.x < -10 && p.y >= 4), 'used front stairs');
    assert.ok(trace.some(p => p.y >= 9 && p.z >= 60 && p.z < 67), 'crossed onto roof');
    const completed = digs.filter(d => d.status === 3);
    assert.equal(completed.length, 1);
    assert.deepEqual(completed[0].location, origin.offset(2, 8, 63));
    assert.ok(completed[0].feet.y >= origin.y + 9);
    set(2, 8, 63, 'air');
    const n = digs.length;
    for (let i = 0; i < 40; i++) step();
    assert.equal(digs.length, n, 'server-confirmed air is skipped');
  });
}

test('a reachable gray face can be mined from the ground and the testing board cancels a dig', () => {
  const { board, pos, set, step, digs, origin } = rig();
  set(18, 2, 60, 'white_concrete');
  for (let i = 0; i < 40 && !digs.length; i++) step();
  assert.equal(digs[0]?.status, 0);
  assert.equal(pos.y, origin.y + 1);
  assert.deepEqual(digs[0].location, origin.offset(4, 2, 60));
  board.text = '验证中…';
  for (let i = 0; i < 50; i++) step();
  assert.deepEqual(digs.map(d => d.status), [0, 2]);
});

test('abandons the stair route when another player removes the assigned block', () => {
  const { pos, origin, step, set, digs } = rig();
  for (let i = 0; i < 250 && pos.y < origin.y + 4; i++) step();
  assert.ok(pos.y >= origin.y + 4 && pos.y < origin.y + 9);
  set(2, 8, 63, 'air');
  for (let i = 0; i < 100; i++) step();
  assert.ok(pos.y < origin.y + 9);
  assert.deepEqual(digs, []);
});

for (const corrected of [false, true]) {
  test('reacquires the front stairs after a fall with ' + (corrected ? 'a server correction' : 'normal movement'), () => {
    const { client, movement, miner, origin, pos, digs } = rig();
    const target = origin.offset(2, 8, 63);
    assert.ok(miner.start(target, 'JOINT', origin));
    function step() {
      movement.beginDecision();
      miner.tick(pos);
      movement.tick(pos);
    }
    for (let t = 0; t < 480 && pos.y < origin.y + 5; t++) step();
    assert.ok(pos.y >= origin.y + 5);
    // Knock the player sideways, clear of the stair. Let normal gravity land it.
    movement.player.vel.z = -1;
    let landed = false, climbedAgain = false;
    for (let t = 0; t < 650 && !digs.some(d => d.status === 3); t++) {
      step();
      if (!landed && movement.player.onGround && pos.y < origin.y + 2) {
        landed = true;
        if (corrected) {
          client.emit('position', { ...pos });
          movement.teleport(pos, {});
        }
      }
      if (landed && pos.z >= 58 && pos.z < 60 && pos.y >= origin.y + 5) climbedAgain = true;
    }
    assert.ok(landed && climbedAgain, 'landed on the apron and climbed again');
    assert.deepEqual(digs.filter(d => d.status === 3).map(d => d.location), [target]);
  });
}
