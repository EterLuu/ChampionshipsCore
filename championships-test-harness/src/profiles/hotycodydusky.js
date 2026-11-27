const packets = require('../packets');

// These are the visible item materials, including the horn's custom item model.
// Paper sends a paper token in the original slot while a vest is being worn.
const SKILLS = ['sugar', 'phantom_membrane', 'ink_sac', 'blue_dye', 'blaze_rod', 'rabbit_hide'];
const TOOLS = new Set(['red_mushroom', 'chorus_fruit', 'ender_pearl', 'music_disc_cat', 'trident',
  'leather_chestplate', 'trial_key', 'compass', 'lingering_potion', 'honey_bottle', 'wind_charge',
  'armor_stand', 'clock', 'totem_of_undying']);

function targets(ctx, holder, hasSpace) {
  let opponent = null, pursued = null, pickup = null;
  const threats = [];
  const displays = new Map();
  const hitboxes = [];
  for (const e of ctx.entities.values()) {
    if (e.entityId === ctx.entityId || ![e.x, e.y, e.z].every(Number.isFinite)) continue;
    if (e.type === 'player' || e.type === 'mannequin') {
      if (holder && ctx.memory.skipChase?.get(e.entityId) > ctx.tick) continue;
      if (e.helmet !== (holder ? 'leather_helmet' : 'player_head')) continue;
      if ((e.flags & 0x20) && !(e.flags & 0x40)) continue;
      const distance = Math.hypot(e.x - ctx.position.x, e.y - ctx.position.y, e.z - ctx.position.z);
      if (distance >= 64) continue;
      if (!holder) threats.push(e);
      if (holder && e.entityId === ctx.memory.pursued) pursued = { e, distance };
      if (distance < (opponent?.distance ?? Infinity)) opponent = { e, distance };
    } else if (hasSpace && e.type === 'item_display' && TOOLS.has(e.displayItem)) {
      displays.set(e.x + ',' + e.z, e.y);
    } else if (hasSpace && e.type === 'interaction') {
      hitboxes.push(e);
    }
  }
  for (const e of hitboxes) {
    const y = displays.get(e.x + ',' + e.z);
    if (y == null || Math.abs(y - e.y - 0.45) > 0.08 || ctx.memory.skipLoot?.get(e.entityId) > ctx.tick) continue;
    const distance = Math.hypot(e.x - ctx.position.x, e.y - ctx.position.y - 1, e.z - ctx.position.z);
    if (distance < (pickup?.distance ?? 64)) pickup = { e, distance };
  }
  if (pursued && pursued.distance <= opponent.distance + 2) opponent = pursued;
  ctx.memory.pursued = holder ? opponent?.e.entityId : null;
  return { opponent, pickup, threats };
}

function travel(ctx, goal, flee, holder, threats) {
  const { position: pos, origin, index, tick, memory } = ctx;
  const side = index % 2 ? -1 : 1;
  // Stagger destinations around the spawn so bots split up even when all nine
  // receive exactly the same spawn coordinates and no entities are in sight.
  const bearing = index * 2.4 + (Math.floor(tick / 120) + (memory.roamTurns || 0)) * 1.8 * side;
  const mode = flee ? 'escape' : goal ? 'seek-' + goal.entityId : 'roam';
  let way;
  if (flee) {
    const dx = pos.x - goal.x, dz = pos.z - goal.z;
    const angle = Math.hypot(dx, dz) < 0.08 ? bearing : Math.atan2(dz, dx) + 0.35 * side;
    way = ctx.fleeFrom(threats, angle);
  } else {
    const dest = goal || { x: origin.x + 24 * Math.cos(bearing), y: pos.y, z: origin.z + 24 * Math.sin(bearing) };
    way = ctx.navigate(dest, mode);
  }
  if (!way) {
    if (mode !== memory.idleMode) memory.idleSince = tick;
    memory.idleMode = mode;
    if (tick - memory.idleSince >= 16) {
      if (goal?.type === 'interaction') memory.skipLoot.set(goal.entityId, tick + 80);
      else if (holder && goal) memory.skipChase.set(goal.entityId, tick + 40);
      else memory.roamTurns = (memory.roamTurns || 0) + 1;
      memory.idleSince = tick;
    }
    return;
  }
  memory.idleMode = null;
  const stride = holder ? 0.33 : 0.25;
  const yaw = way.angle * 180 / Math.PI - 90;
  pos.yaw = yaw;
  packets.positionLook(ctx.client, { ...pos, x: pos.x + stride * Math.cos(way.angle),
    z: pos.z + stride * Math.sin(way.angle) }, true);
  ctx.record('actionTypes', 'move');
  const sprint = way.sprint !== false;
  packets.sprint(ctx.client, ctx.entityId, sprint);
  if (sprint && !memory.sprinting) ctx.record('actionTypes', 'sprint');
  memory.sprinting = sprint;
  if (way.jump) {
    packets.input(ctx.client, { forward: true, sprint, jump: true });
    ctx.record('actionTypes', 'jump');
  }
}

function aim(ctx, e, height) {
  const dx = e.x - ctx.position.x, dz = e.z - ctx.position.z;
  const dy = e.y + height - ctx.position.y - 1.62;
  ctx.position.yaw = Math.atan2(-dx, dz) * 180 / Math.PI;
  ctx.position.pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
  packets.look(ctx.client, ctx.position.yaw, ctx.position.pitch);
  ctx.record('actionTypes', 'look');
}

function click(ctx, target, attack, mechanism) {
  ctx.afterMove(hit => {
    if (attack) packets.attack(ctx.client, target.entityId);
    else packets.useEntity(ctx.client, target.entityId, hit);
    ctx.record('actionTypes', attack ? 'attack' : 'entity-interact');
    ctx.record('mechanismPackets', mechanism);
  }, target);
}

function use(ctx, slot, mechanism, cycle = false) {
  if (ctx.heldSlot !== slot) ctx.selectHeldSlot(slot);
  // Flush aim before the interaction. Both the use packet and the server's
  // last player rotation matter for thrown items.
  if (cycle) packets.input(ctx.client, { shift: true });
  ctx.afterMove(() => {
    packets.useItem(ctx.client, ctx.nextSequence(), 0, { x: ctx.position.yaw, y: ctx.position.pitch });
    ctx.record('actionTypes', 'use-item');
    ctx.record('mechanismPackets', mechanism);
  });
}

function canUseTool(name, holder, target, hotbar) {
  switch (name) {
    case 'trial_key': return target?.e.type === 'player' && target.distance <= 3;
    case 'trident': return holder && target && target.distance < 24;
    case 'honey_bottle': case 'clock': return holder;
    case 'armor_stand': case 'lingering_potion': case 'chorus_fruit':
      return !holder && target?.distance < 14;
    case 'ender_pearl': case 'wind_charge': return target?.distance > 3;
    case 'leather_chestplate': return !hotbar.slice(0, 2).some(stack => stack?.name === 'paper');
    default: return TOOLS.has(name);
  }
}

function tryTool(ctx, holder, target) {
  const { memory, tick, hotbar } = ctx;
  for (let offset = 0; offset < 2; offset++) {
    const slot = ((memory.lastTool ?? 1) + 1 + offset) % 2;
    const name = hotbar[slot]?.name;
    if (tick < (memory.toolReady[name] ?? 0) || !canUseTool(name, holder, target, hotbar)) continue;
    if (name === 'trial_key' || name === 'trident') aim(ctx, target.e, 1.2);
    if (name === 'trial_key') {
      if (ctx.heldSlot !== slot) ctx.selectHeldSlot(slot);
      click(ctx, target.e, false, 'fish-tool-' + name);
    } else {
      use(ctx, slot, 'fish-tool-' + name);
    }
    const hold = name === 'trident' ? 14 : name === 'honey_bottle' ? 36 : name === 'red_mushroom' ? 14 : 4;
    memory.busyUntil = tick + hold;
    memory.charging = name === 'trident' ? slot : null;
    memory.toolReady[name] = tick + (name === 'music_disc_cat' ? 240 : name === 'compass' ? 42 : Math.max(20, hold));
    memory.lastTool = slot;
    return true;
  }
  return false;
}

function playHotyCodyDusky(ctx) {
  const { hotbar, memory, tick } = ctx;
  const skill = hotbar[4]?.name;
  const holder = SKILLS.includes(skill);
  if (hotbar[2]?.name !== 'structure_void' || (!holder && skill !== 'barrier')) {
    delete memory.role;
    memory.charging = null;
    return 0;
  }
  if (memory.role !== holder) {
    memory.role = holder;
    memory.skillReady ??= tick;
    if (!holder && memory.charging != null) {
      if (ctx.heldSlot === memory.charging) ctx.selectHeldSlot(1 - memory.charging);
      memory.charging = null;
      memory.busyUntil = tick;
    }
    memory.skillChanges = 0;
    memory.toolReady ??= {};
  }
  memory.skipLoot ??= new Map();
  memory.skipChase ??= new Map();
  for (const skipped of [memory.skipLoot, memory.skipChase])
    for (const [id, until] of skipped) if (until <= tick || !ctx.entities.has(id)) skipped.delete(id);
  const space = !hotbar[0] || !hotbar[1];
  const { opponent, pickup, threats } = targets(ctx, holder, space);
  const flee = !holder && !!opponent;
  // Pick up a tool on a safe escape route, including when the carrier is
  // distant. Crossing back towards a nearby holder for loot is too risky.
  const safeLoot = flee && pickup && opponent.distance > 8 && pickup.distance <= 8
    && threats.every(e => Math.hypot(e.x - pickup.e.x, e.z - pickup.e.z)
      >= Math.hypot(e.x - ctx.position.x, e.z - ctx.position.z) - 1);
  const collect = pickup && (!flee || pickup.distance <= 2.7 || safeLoot)
    && (!holder || !opponent || pickup.distance < Math.min(6, opponent.distance));
  const goal = collect ? pickup.e : holder || flee ? opponent?.e : null;
  travel(ctx, goal, flee && !collect, holder, threats);
  ctx.record('mechanismPackets', collect ? 'fish-seek-loot' : flee ? 'fish-flee'
    : holder && opponent ? 'fish-chase' : 'fish-roam');

  if (tick < (memory.busyUntil ?? 0)) {
    if (memory.charging != null && opponent) aim(ctx, opponent.e, 1.2);
    return 1;
  }
  if (memory.charging != null) {
    if (ctx.heldSlot === memory.charging && hotbar[ctx.heldSlot]?.name === 'trident') {
      if (opponent) aim(ctx, opponent.e, 1.2);
      ctx.afterMove(() => packets.releaseItem(ctx.client, ctx.nextSequence()));
    }
    memory.charging = null;
    return 1;
  }
  if (collect && pickup.distance <= 2.8 && tick >= (memory.pickupReady ?? 0)) {
    aim(ctx, pickup.e, 0.5);
    click(ctx, pickup.e, false, 'fish-pickup');
    memory.pickupReady = tick + 8;
    return 1;
  }
  if (holder && opponent?.distance <= 2.9 && tick >= (memory.attackReady ?? 0)) {
    aim(ctx, opponent.e, 1.2);
    click(ctx, opponent.e, true, opponent.e.type === 'mannequin' ? 'fish-hit-decoy' : 'fish-pass');
    memory.attackReady = tick + 8;
    return 1;
  }
  if (holder && tick >= memory.skillReady) {
    const preferred = SKILLS[(ctx.index + (memory.casts || 0)) % SKILLS.length];
    const cycle = skill !== preferred && memory.skillChanges < SKILLS.length;
    use(ctx, 4, cycle ? 'fish-switch-skill' : 'fish-cast-' + skill, cycle);
    memory.skillReady = tick + (cycle ? 5 : 400);
    memory.skillChanges = cycle ? memory.skillChanges + 1 : 0;
    if (!cycle) memory.casts = (memory.casts || 0) + 1;
    return 1;
  }
  tryTool(ctx, holder, opponent);
  return 1;
}

module.exports = { playHotyCodyDusky };
