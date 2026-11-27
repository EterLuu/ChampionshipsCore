const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { ClientMovement } = require('../movement');
const { loadGround } = require('./support/terrain');

function fixture() {
  const client = new EventEmitter(), writes = [];
  client.write = (name, data) => writes.push({ name, data });
  const movement = new ClientMovement(client, '26.3');
  client.emit('login', { entityId: 42 });
  loadGround(client, 8, 8);
  const pos = { x: 8.5, y: 64, z: 8.5, yaw: 0, pitch: 0 };
  movement.teleport(pos, {});
  return { movement, client, writes, pos };
}

test('holding forward simulates acceleration and sends just the changed input and rotation', () => {
  const { movement, pos, writes } = fixture();
  const held = () => {
    movement.beginDecision();
    movement.write('position_look', { x: pos.x, z: pos.z + 0.25, yaw: 0, pitch: 0 });
    movement.tick(pos);
  };
  held();
  assert.ok(pos.z > 8.5 && pos.z < 8.75);
  held(); held();
  const inputs = writes.filter(p => p.name === 'player_input');
  assert.equal(inputs.length, 1);
  assert.equal(inputs[0].data.inputs.forward, true);
  assert.ok(writes.every(p => !['look', 'position_look'].includes(p.name)));
  movement.beginDecision();
  movement.tick(pos);
  assert.equal(writes.filter(p => p.name === 'player_input').at(-1).data.inputs.forward, false);
});

test('click ray stops at the first player or terrain and forgets a destroyed target', () => {
  const { movement, client } = fixture();
  const target = { type: 'player', x: 8.5, y: 64, z: 11, entityId: 7 };
  const entities = new Map([[7, target]]);
  const hit = movement.crosshairIntersection(target, entities);
  assert.ok(Math.abs(hit.z + 0.3) < 1e-8 && Math.abs(hit.y - 1.62) < 1e-8);
  assert.equal(hit.x, 0);
  entities.set(8, { ...target, z: 10, entityId: 8 });
  assert.equal(movement.crosshairIntersection(target, entities), null);
  entities.delete(8);
  client.emit('block_change', { location: { x: 8, y: 65, z: 10 },
    type: require('minecraft-data')('26.3').blocksByName.stone.defaultState });
  assert.equal(movement.crosshairIntersection(target, entities), null);
  entities.delete(7);
  assert.equal(movement.crosshairIntersection(target, entities), null);
});

test('server-authorized flight responds to movement keys without falling while hovering', () => {
  const { movement, client, pos } = fixture();
  pos.y = 70;
  movement.teleport(pos, {});
  client.emit('abilities', { flags: 6, flyingSpeed: 0.05 });
  movement.write('position_look', { ...pos, z: pos.z + 1 });
  movement.tick(pos);
  assert.ok(pos.z > 8.5);
  assert.equal(pos.y, 70);
  movement.beginDecision();
  client.emit('abilities', { flags: 0, flyingSpeed: 0.05 });
  movement.tick(pos);
  movement.tick(pos);
  assert.ok(pos.y < 70);
});

function walkCourse(setup, goal, ticks = 160, spawn = null) {
  const { movement, pos } = fixture();
  const blocks = require('minecraft-data')('26.3').blocksByName;
  const set = (x, y, z, name) => movement.updateBlock({ x, y, z }, blocks[name].defaultState);
  setup(set);
  if (spawn) {
    Object.assign(pos, spawn);
    movement.teleport(pos, {});
  }
  const trace = [];
  for (let tick = 1; tick <= ticks; tick++) {
    const way = movement.navigator.heading(movement.player, goal, 'test', tick);
    movement.beginDecision();
    if (way) {
      movement.write('position_look', { x: pos.x + Math.cos(way.angle), z: pos.z + Math.sin(way.angle),
        yaw: way.angle * 180 / Math.PI - 90, pitch: 0 });
      movement.write('player_input', { inputs: { jump: way.jump } });
    }
    movement.tick(pos);
    trace.push({ ...pos, jump: way?.jump === true });
  }
  return trace;
}

test('a runner exits a U-shaped dead end before approaching the goal behind it', () => {
  const trace = walkCourse(set => {
    for (const y of [64, 65, 66]) {
      for (let z = 6; z <= 10; z++) set(10, y, z, 'stone');
      for (let x = 6; x < 10; x++) for (const z of [6, 10]) set(x, y, z, 'stone');
    }
  }, { x: 12.5, y: 64, z: 8.5 }, 240);
  assert.ok(trace.some(p => p.x < 6), 'must first move away from the goal to leave the enclosure');
  assert.ok(trace.some(p => Math.hypot(p.x - 12.5, p.z - 8.5) < 0.7));
  assert.ok(trace.every(p => !p.jump));
});

test('uses a jump for a full block, walks up a bottom slab, and jumps a one-block gap', () => {
  for (const [surface, expectedJump, height] of [['stone', true, 65], ['stone_slab', false, 64.5], ['air', true, 64]]) {
    const trace = walkCourse(set => {
      for (let z = 0; z < 16; z++) {
        if (surface === 'air') set(10, 63, z, 'air');
        else for (let x = 10; x < 16; x++) set(x, 64, z, surface);
      }
    }, { x: 13.5, y: height, z: 8.5 });
    assert.equal(trace.some(p => p.jump), expectedJump, surface);
    assert.ok(trace.some(p => p.x > 12.8 && Math.abs(p.y - height) < 0.15), surface);
    assert.ok(trace.every(p => p.y >= 64), surface);
  }
});

test('a low ceiling, a wide pit and unloaded terrain cannot be selected as a shortcut', () => {
  const trace = walkCourse(set => {
    for (let z = 0; z < 16; z++) {
      for (let x = 10; x <= 12; x++) set(x, 63, z, 'air');
      set(9, 64, z, 'stone');
      set(9, 66, z, 'stone');
    }
  }, { x: 20.5, y: 64, z: 8.5 });
  assert.ok(trace.every(p => !p.jump && p.x < 9 && p.y === 64));
});

test('a player resting on the edge of a neighboring block can leave the spawn platform', () => {
  const trace = walkCourse(set => {
    for (let x = 7; x <= 9; x++) set(x, 64, 9, 'barrier');
  }, { x: 12.5, y: 64, z: 13.5 }, 160, { x: 8.57, y: 65, z: 8.9 });
  assert.ok(trace.some(p => Math.hypot(p.x - 12.5, p.z - 13.5) < 0.7));
});

test('a removed foothold invalidates a planned route before walking onto it', () => {
  const { movement, pos } = fixture();
  movement.tick(pos);
  movement.tick(pos);
  const nav = movement.navigator, player = movement.player;
  const goal = { x: 13.5, y: 64, z: 8.5 };
  assert.equal(nav.heading(player, goal, 'chase', 3).angle, 0);
  for (const x of [9, 10, 11]) movement.updateBlock({ x, y: 63, z: 8 }, 0);
  const turn = nav.heading(player, goal, 'chase', 4);
  assert.ok(turn && Math.abs(turn.angle) > 1 && !turn.jump);
});

test('repeated displacement at one attempted step makes the bot try a different foothold', () => {
  const { movement, pos } = fixture();
  movement.tick(pos);
  movement.tick(pos);
  const nav = movement.navigator, player = movement.player;
  const goal = { x: 13.5, y: 64, z: 8.5 }, from = { ...pos, x: pos.x + 0.4 };
  for (const tick of [4, 8]) {
    assert.equal(nav.heading(player, goal, 'chase', tick).angle, 0);
    nav.reposition(from, pos);
  }
  const alternative = nav.heading(player, goal, 'chase', 10);
  assert.ok(alternative && Math.abs(alternative.angle) > 0.5);
});

test('walking off the lower half of a stair does not need a jump or a teleport', () => {
  const trace = walkCourse(set => set(8, 64, 8, 'stone_stairs'),
    { x: 8.5, y: 64, z: 13.5 }, 160, { x: 8.5, y: 64.5, z: 8.84 });
  assert.ok(trace.some(p => p.z > 13 && p.y === 64));
  assert.ok(trace.every(p => !p.jump));
});

test('panic keeps a reachable escape destination that opens distance from both holders', () => {
  const { client, movement, pos } = fixture();
  for (const x of [-16, 0, 16]) for (const z of [-16, 0, 16]) loadGround(client, x, z);
  const stone = require('minecraft-data')('26.3').blocksByName.stone.defaultState;
  for (let z = 0; z <= 18; z++) for (let y = 64; y < 67; y++)
    movement.updateBlock({ x: 11, y, z }, stone);
  const threats = [{ x: 4, z: 8.5 }, { x: 6, z: 5 }];
  const separation = p => Math.min(...threats.map(t => Math.hypot(t.x - p.x, t.z - p.z)));
  movement.tick(pos);
  movement.tick(pos);
  const nav = movement.navigator;
  const first = nav.escapeHeading(movement.player, threats, 0, 3);
  assert.ok(first);
  const destination = { ...nav.goal };
  assert.ok(separation(destination) > separation(pos) + 3);
  assert.ok(nav.path.every(n => n.x !== 11.5 || n.z < 0 || n.z >= 19));
  threats[0].z += 0.5;
  nav.escapeHeading(movement.player, threats, 0, 5);
  assert.deepEqual(nav.goal, destination);
});

test('a chasing bot follows its current route between target reviews and replans after a turn', () => {
  const { movement, pos } = fixture();
  movement.tick(pos); movement.tick(pos);
  const nav = movement.navigator, player = movement.player;
  const a = { x: 13.5, y: 64, z: 8.5 }, b = { x: 13.5, y: 64, z: 12.5 };
  nav.heading(player, a, 'seek-9', 2);
  nav.heading(player, b, 'seek-9', 4);
  assert.deepEqual(nav.goal, a);
  nav.heading(player, b, 'seek-9', 8);
  assert.deepEqual(nav.goal, b);
});

test('camera follows the short turn across north and item use takes the flushed rotation', () => {
  const { movement, writes, pos } = fixture();
  pos.yaw = 179;
  movement.teleport(pos, {});
  movement.write('look', { yaw: -179, pitch: 12 });
  movement.tick(pos);
  assert.equal(pos.yaw, 181);
  movement.rotate(pos, { yaw: 10, relativeYaw: true, pitch: -20, relativePitch: false });
  assert.equal(pos.yaw, 191);
  movement.write('use_item', { hand: 0, sequence: 1, rotation: { x: -179, y: 12 } });
  assert.deepEqual(writes.at(-1).data.rotation, { x: 191, y: -20 });
});

test('self jump effects survive position corrections and expire on client ticks', () => {
  const { client, movement, pos } = fixture();
  client.emit('entity_effect', { entityId: 42, effectId: 7, amplifier: 1, duration: 4 });
  movement.teleport(pos, {});
  movement.tick(pos);
  movement.tick(pos);
  assert.equal(movement.player.onGround, true);
  movement.write('player_input', { inputs: { jump: true } });
  movement.tick(pos);
  assert.ok(pos.y > 64.61 && pos.y < 64.63);
  movement.tick(pos);
  movement.tick(pos);
  assert.equal(movement.player.jumpBoost, 0);
  client.emit('entity_effect', { entityId: 41, effectId: 7, amplifier: 2, duration: -1 });
  movement.tick(pos);
  assert.equal(movement.player.jumpBoost, 0);
});

test('powder snow supports leather boots from above and lets a crouching player descend', () => {
  const { movement, pos } = fixture();
  const snow = require('minecraft-data')('26.3').blocksByName.powder_snow;
  movement.updateBlock({ x: 8, y: 64, z: 8 }, snow.defaultState);
  pos.y = 65;
  movement.teleport(pos, {});
  movement.leatherBoots = true;
  for (let i = 0; i < 4; i++) movement.tick(pos);
  assert.equal(pos.y, 65);
  assert.equal(movement.player.onGround, true);
  for (let i = 0; i < 3; i++) {
    movement.write('player_input', { inputs: { shift: true } });
    movement.tick(pos);
  }
  assert.ok(pos.y < 65);
});

test('using a held item scales movement and releasing it restores sprint', () => {
  const { client, movement, pos } = fixture();
  const flags = value => client.emit('entity_metadata', { entityId: 42, metadata: [{ key: 8, type: 'byte', value }] });
  const walk = () => {
    movement.write('position', { x: pos.x, y: pos.y, z: pos.z + 1 });
    movement.write('entity_action', { actionId: 'start_sprinting' });
    movement.tick(pos);
  };
  flags(1);
  walk();
  assert.equal(movement.player.control.forward, 0.2);
  assert.equal(movement.keys.sprint, false);
  flags(0);
  walk();
  assert.equal(movement.player.control.forward, 1);
  assert.equal(movement.keys.sprint, true);
});

test('position correction preserves ground and jump timing, and rotates retained velocity', () => {
  const { movement, pos } = fixture();
  movement.tick(pos);
  movement.tick(pos);
  assert.equal(movement.player.onGround, true);
  movement.player.jumpTicks = 4;
  movement.player.vel.set(0, 0, 1);
  movement.teleport({ ...pos, yaw: 90 }, { flags: { yawDelta: true, dx: true, dz: true } });
  assert.equal(movement.player.onGround, true);
  assert.equal(movement.player.jumpTicks, 4);
  assert.ok(Math.abs(movement.player.vel.x + 1) < 1e-8);
  assert.ok(Math.abs(movement.player.vel.z) < 1e-8);
});

test('wall contacts leave room for the full Java player box on both sides of a block face', () => {
  const stone = require('minecraft-data')('26.3').blocksByName.stone.defaultState;
  const radius = Math.fround(0.6) / 2;
  for (const side of [-1, 1]) {
    const { movement, pos } = fixture();
    const wall = side > 0 ? 10 : 6;
    for (const y of [64, 65]) movement.updateBlock({ x: wall, y, z: 8 }, stone);
    for (let i = 0; i < 35; i++) {
      movement.write('position_look', { x: pos.x + side, z: pos.z, yaw: -90 * side, pitch: 0 });
      movement.tick(pos);
    }
    const face = side > 0 ? wall : wall + 1;
    assert.equal(pos.x + side * radius, face);
  }
});
