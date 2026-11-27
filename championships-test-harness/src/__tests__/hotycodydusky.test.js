const test = require('node:test');
const assert = require('node:assert/strict');
const { playHotyCodyDusky } = require('../profiles/hotycodydusky');
const ctxFor = (skill = 'barrier') => {
  const writes = [];
  const ctx = {
    writes, client: { write(name, data) { writes.push({ name, data }); } },
    hotbar: [null, null, { name: 'structure_void' }, null, { name: skill }], heldSlot: 0,
    entities: new Map(), index: 0, entityId: 1, tick: 0, memory: {},
    origin: { x: 4, y: 64, z: 4 }, position: { x: 4, y: 64, z: 4, yaw: 0, pitch: 0 },
    selectHeldSlot(slot) { this.heldSlot = slot; }, fleeFrom: (_, angle) => ({ angle }), navigate(goal) { return { angle: Math.atan2(goal.z - this.position.z, goal.x - this.position.x) }; },
    afterMove: action => action({ x: -0.5, y: 0.65, z: 0 }), nextSequence: () => 1, record() {},
  };
  return ctx;
};
function entity(ctx, id, type, x, props) {
  ctx.entities.set(id, { entityId: id, type, x, y: 64, z: 4, ...props });
}
function step(ctx, ticks = 2) {
  ctx.tick += ticks;
  ctx.writes.length = 0;
  playHotyCodyDusky(ctx);
  return ctx.writes;
}
test('runner flees the cod head, and a holder can be fooled by a closer mannequin', () => {
  const ctx = ctxFor();
  entity(ctx, 2, 'player', 4.5, { helmet: 'player_head' });
  entity(ctx, 3, 'player', 6.5, { helmet: 'leather_helmet' });
  entity(ctx, 4, 'mannequin', 5, { helmet: 'leather_helmet' });
  entity(ctx, 5, 'player', 4.3, { helmet: 'leather_helmet', flags: 0x20 });
  const walk = step(ctx).find(p => p.name === 'position_look');
  assert.ok(walk.data.x < 4);
  assert.ok(!ctx.writes.some(p => p.name === 'attack'));
  ctx.hotbar[4] = { name: 'sugar' };
  assert.equal(step(ctx).find(p => p.name === 'attack').data.entityId, 4);
  ctx.entities.delete(4);
  assert.equal(step(ctx, 10).find(p => p.name === 'attack').data.entityId, 3);
});
test('pickup clicks the paired loot hitbox with a relative hit point and respects a worn vest', () => {
  const ctx = ctxFor();
  entity(ctx, 2, 'interaction', 4.1);
  entity(ctx, 3, 'interaction', 6, { y: 64.8 });
  entity(ctx, 4, 'item_display', 6, { y: 65.25, displayItem: 'compass' });
  const click = step(ctx).find(p => p.name === 'use_entity');
  assert.equal(click.data.target, 3);
  assert.deepEqual(click.data.location, { x: -0.5, y: 0.65, z: 0 });
  ctx.hotbar[0] = { name: 'paper' };
  ctx.hotbar[1] = { name: 'trident' };
  assert.ok(!step(ctx, 20).some(p => p.name === 'use_entity' || p.name === 'use_item'));
  ctx.hotbar[1] = null;
  assert.equal(step(ctx).find(p => p.name === 'use_entity').data.target, 3);
});
test('holder skill survives a pass-back on cooldown and a charged trident releases once', () => {
  const ctx = ctxFor('sugar');
  ctx.hotbar[1] = { name: 'trident' };
  entity(ctx, 3, 'player', 16, { helmet: 'leather_helmet' });
  step(ctx);
  assert.equal(ctx.heldSlot, 4);
  ctx.hotbar[4] = { name: 'barrier' };
  step(ctx);
  ctx.hotbar[4] = { name: 'sugar' };
  assert.ok(step(ctx).some(p => p.name === 'use_item'));
  assert.equal(ctx.heldSlot, 1);
  assert.ok(!step(ctx, 10).some(p => p.name === 'block_dig'));
  assert.equal(step(ctx, 4).find(p => p.name === 'block_dig').data.status, 6);
  assert.ok(!step(ctx).some(p => p.name === 'block_dig'));
});

test('beyond ten blocks a runner continues evading and can collect loot along a safe route', () => {
  const ctx = ctxFor();
  entity(ctx, 2, 'player', 18, { helmet: 'player_head' });
  entity(ctx, 3, 'player', 22, { helmet: 'player_head' });
  let holders;
  ctx.fleeFrom = (threats, angle) => { holders = threats.map(e => e.entityId); return { angle }; };
  assert.ok(step(ctx).find(p => p.name === 'position_look').data.x < 4);
  assert.deepEqual(holders, [2, 3]);
  entity(ctx, 6, 'interaction', 0, { y: 64.8 });
  entity(ctx, 7, 'item_display', 0, { y: 65.25, displayItem: 'compass' });
  let goal;
  ctx.navigate = e => { goal = e; return { angle: Math.PI }; };
  step(ctx);
  assert.equal(goal.entityId, 6);
  ctx.entities.get(6).x = ctx.entities.get(7).x = 9;
  goal = null;
  step(ctx);
  assert.equal(goal, null, 'leaves bait that would draw it back toward a holder');
});

test('a holder keeps chasing the same runner across small distance changes', () => {
  const ctx = ctxFor('sugar');
  entity(ctx, 2, 'player', 16, { helmet: 'leather_helmet' });
  entity(ctx, 3, 'player', 18, { helmet: 'leather_helmet' });
  step(ctx); assert.equal(ctx.memory.pursued, 2);
  ctx.entities.get(3).x = 15.5;
  step(ctx); assert.equal(ctx.memory.pursued, 2);
  ctx.entities.get(3).x = 10;
  step(ctx); assert.equal(ctx.memory.pursued, 3);
});
