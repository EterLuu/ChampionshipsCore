const packets = require('../packets');
const { gameContract, supportedMatrixGames } = require('../matrix');

const PROFILE_DEFINITIONS = {
  bingo: { kind: 'bingo-walk', cadenceMs: 250, stride: 0.25, grounded: true, interaction: 'swing', mechanism: 'task-progress' },
  tntrun: { kind: 'walk', cadenceMs: 80, stride: 0.1, sprint: true, jumpEvery: 12, interaction: 'swing', mechanism: 'floor-break' },
  parkourtag: { kind: 'walk', cadenceMs: 80, stride: 0.1, sprint: true, jumpEvery: 8, interaction: 'look', mechanism: 'tag-chase' },
  battlebox: { kind: 'look', cadenceMs: 180, yawStep: 7, sprint: true, jumpEvery: 16, interaction: 'combat', mechanism: 'weapon-combat' },
  lasershowdown: { kind: 'look', cadenceMs: 180, yawStep: 8, interaction: 'swing', mechanism: 'laser-shot' },
  skywars: { kind: 'walk', cadenceMs: 100, stride: 0.08, sprint: true, jumpEvery: 10, interaction: 'use-item', mechanism: 'resource-combat' },
  tgttos: { kind: 'walk', cadenceMs: 70, stride: 0.12, sprint: true, jumpEvery: 5, interaction: 'look', mechanism: 'parkour-finish' },
  dragoneggcarnival: { kind: 'walk', cadenceMs: 100, stride: 0.08, sprint: true, jumpEvery: 14, interaction: 'entity', mechanism: 'egg-objective' },
  parkourwarrior: { kind: 'walk', cadenceMs: 70, stride: 0.1, sprint: true, jumpEvery: 4, interaction: 'look', mechanism: 'checkpoint' },
  hotycodydusky: { kind: 'walk', cadenceMs: 100, stride: 0.08, sprint: true, jumpEvery: 10, interaction: 'look', mechanism: 'zone-objective' },
  buildmart: { kind: 'walk', cadenceMs: 120, stride: 0.06, interaction: 'build', mechanism: 'collect-place-submit' },
  dodgebolt: { kind: 'look', cadenceMs: 180, yawStep: 8, interaction: 'combat', mechanism: 'arrow-round' },
  acerace: { kind: 'walk', cadenceMs: 70, stride: 0.12, sprint: true, jumpEvery: 6, interaction: 'look', mechanism: 'checkpoint-race' },
  riptide: { kind: 'walk', cadenceMs: 70, stride: 0.12, sprint: true, jumpEvery: 8, interaction: 'use-item', mechanism: 'raft-stage' },
  frostbite: { kind: 'look', cadenceMs: 180, yawStep: 6, interaction: 'use-item', mechanism: 'freeze-cycle' },
  laserbox: { kind: 'look', cadenceMs: 180, yawStep: 8, interaction: 'swing', mechanism: 'two-target-laser' },
  sulfursoccer: { kind: 'walk', cadenceMs: 100, stride: 0.08, sprint: true, jumpEvery: 10, targetEntityTypes: ['sulfur_cube'], interaction: 'combat', mechanism: 'kick-pearl-score' },
};

// Keep game behavior data-driven.  The profile owns client actions; the matrix
// owns the evidence and lifecycle contract.  Combining them here gives callers
// one immutable object and makes drift fail in tests instead of at runtime.
const GAME_PROFILES = Object.freeze(Object.fromEntries(
  Object.entries(PROFILE_DEFINITIONS).map(([game, profile]) => {
    const contract = gameContract(game);
    return [game, Object.freeze({
      ...profile,
      game,
      actions: contract.actions,
      mechanism: profile.mechanism || contract.mechanism,
      contract,
    })];
  }),
));

if (Object.keys(GAME_PROFILES).some(game => !supportedMatrixGames().includes(game)))
  throw new Error('game action profile is missing from the executable test matrix');

function actionProfile(settings) {
  if (settings.action !== 'game') {
    if (settings.action === 'bingo-walk') return GAME_PROFILES.bingo;
    return { kind: settings.action, cadenceMs: 50, stride: 0.05, yawStep: 3 };
  }
  return GAME_PROFILES[String(settings.game || '').toLowerCase()] || {
    kind: 'walk', cadenceMs: 100, stride: 0.08, yawStep: 3,
  };
}

function supportedGameProfiles() {
  return Object.keys(GAME_PROFILES);
}

function recordMechanism(ctx, key) {
  ctx.record('mechanismPackets', key);
}

function phaseAllows(profile, phase, action) {
  const allowed = profile.contract?.phaseActions?.[phase];
  return !allowed || allowed.includes(action);
}

const ACTION_INTERVALS = Object.freeze({
  look: 10,
  sprint: 20,
  jump: 12,
  'use-item': 20,
  attack: 20,
  'entity-interact': 20,
  swing: 10,
  'block-dig': 20,
  'block-place': 20,
  'block-interact': 20,
  submit: 20,
});

function blockTarget(ctx) {
  const target = ctx.targetBlock;
  if (!target || !Number.isInteger(target.x) || !Number.isInteger(target.y) || !Number.isInteger(target.z)) return null;
  return target;
}

// ---------------------------------------------------------------------------
// Geometry and aim helpers.  Movement is bounded client input; aim and reach
// checks use only coordinates the server already told the client about, so the
// harness never invents targets.
// ---------------------------------------------------------------------------

function locationOf(ctx, entityId) {
  if (typeof ctx.entityLocation !== 'function' || !Number.isInteger(entityId)) return null;
  const location = ctx.entityLocation(entityId);
  if (!location || ![location.x, location.y, location.z].every(Number.isFinite)) return null;
  return location;
}

function distanceTo(ctx, location) {
  return Math.hypot(
    location.x - ctx.position.x,
    location.y - ctx.position.y,
    location.z - ctx.position.z);
}

function inReach(ctx, entityId, reach = 4.5) {
  const location = locationOf(ctx, entityId);
  return location != null && distanceTo(ctx, location) <= reach;
}

// Prefer the nearest server-observed block the player can actually reach; fall
// back to the single target block when the server has only sent one update.
function nearestObservedBlock(ctx, reach = 4.5) {
  const blocks = Array.isArray(ctx.recentBlocks) ? ctx.recentBlocks : [];
  let best = null;
  let bestDistance = Infinity;
  for (const block of blocks) {
    if (![block.x, block.y, block.z].every(Number.isFinite)) continue;
    const distance = distanceTo(ctx, block);
    if (distance <= reach && distance < bestDistance) {
      best = block;
      bestDistance = distance;
    }
  }
  return best;
}

const EYE_HEIGHT = 1.62;

function aimAt(ctx, location) {
  const dx = location.x - ctx.position.x;
  const dy = location.y + EYE_HEIGHT - (ctx.position.y + EYE_HEIGHT);
  const dz = location.z - ctx.position.z;
  const yaw = Math.atan2(-dx, dz) * 180 / Math.PI;
  const pitch = Math.max(-90, Math.min(90, -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI));
  ctx.position.yaw = yaw;
  ctx.position.pitch = pitch;
  packets.look(ctx.client, yaw, pitch, true);
  ctx.record('actionTypes', 'look');
}

function lookAround(ctx, profile, mechanism) {
  const yaw = (ctx.position.yaw || 0) + (ctx.index % 2 ? -1 : 1) * (profile.yawStep || 3);
  ctx.position.yaw = yaw;
  packets.look(ctx.client, yaw, ctx.position.pitch || 0, true);
  ctx.record('actionTypes', 'look');
  if (mechanism) recordMechanism(ctx, mechanism);
}

function doSprint(ctx) {
  if (ctx.entityId == null) return;
  packets.sprint(ctx.client, ctx.entityId, true);
  ctx.record('actionTypes', 'sprint');
}

function doJump(ctx) {
  packets.input(ctx.client, { forward: true, sprint: true, jump: true });
  ctx.record('actionTypes', 'jump');
}

function doUse(ctx, mechanism) {
  packets.useItem(ctx.client, ctx.nextSequence());
  ctx.record('actionTypes', 'use-item');
  recordMechanism(ctx, mechanism);
}

function doSwing(ctx, mechanism) {
  packets.swing(ctx.client, ctx.nextSequence());
  ctx.record('actionTypes', 'swing');
  recordMechanism(ctx, mechanism);
}

function doInteract(ctx, entityId, mechanism) {
  packets.useEntity(ctx.client, entityId, locationOf(ctx, entityId) || { x: 0, y: 0, z: 0 });
  ctx.record('actionTypes', 'entity-interact');
  recordMechanism(ctx, mechanism);
}

function doAttack(ctx, entityId, mechanism) {
  const location = locationOf(ctx, entityId);
  if (location) aimAt(ctx, location);
  packets.attack(ctx.client, entityId);
  ctx.record('actionTypes', 'attack');
  recordMechanism(ctx, mechanism);
}

function itemLabel(item) {
  if (!item) return '';
  return String(item.name || item.itemId || item.itemType || item.type || '');
}

// Select a real hotbar slot before a mechanism action.  Falls back to the first
// occupied slot when the server item id is not descriptive.
function selectWeapon(ctx) {
  if (!Array.isArray(ctx.hotbar) || typeof ctx.selectHeldSlot !== 'function') return false;
  let slot = ctx.hotbar.findIndex(item => /sword|axe|bow|trident|pickaxe|shovel|hoe|snowball|egg/i.test(itemLabel(item)));
  if (slot < 0) slot = ctx.hotbar.findIndex(Boolean);
  if (slot < 0) return false;
  if (slot === ctx.heldSlot) return true;
  return ctx.selectHeldSlot(slot) === true;
}

function walkPattern(ctx, profile, mechanism) {
  const base = ctx.origin || ctx.position;
  const extent = profile.kind === 'bingo-walk' ? 8 : 40;
  const phaseStep = (ctx.step || 0) % (extent * 2);
  const stride = profile.stride || 0.08;
  ctx.position.x = base.x + (phaseStep < extent ? phaseStep : extent * 2 - phaseStep) * stride;
  packets.position(ctx.client, ctx.position, profile.grounded === true);
  ctx.record('actionTypes', profile.kind === 'bingo-walk' ? 'move-ground' : 'move');
  recordMechanism(ctx, mechanism || profile.mechanism);
}

function stepToward(ctx, location, profile, speed = 0.2) {
  const dx = location.x - ctx.position.x;
  const dz = location.z - ctx.position.z;
  const length = Math.hypot(dx, dz) || 1;
  const nextX = ctx.position.x + (dx / length) * speed;
  const nextZ = ctx.position.z + (dz / length) * speed;
  const yaw = Math.atan2(-dx, dz) * 180 / Math.PI;
  ctx.position.x = nextX;
  ctx.position.z = nextZ;
  ctx.position.yaw = yaw;
  packets.positionLook(ctx.client, {
    x: nextX, y: ctx.position.y, z: nextZ, yaw, pitch: ctx.position.pitch || 0,
  }, true);
  ctx.record('actionTypes', 'move');
  recordMechanism(ctx, profile.mechanism);
}

// ---------------------------------------------------------------------------
// Per-game progress behaviors.  Each strategy uses server-observed entities,
// blocks, inventory and windows instead of firing every action blindly.
// ---------------------------------------------------------------------------

function runner(ctx, profile) {
  walkPattern(ctx, profile);
  if (profile.actions.includes('sprint')) doSprint(ctx);
  if (profile.actions.includes('jump') && (!profile.jumpEvery || (ctx.step || 0) % profile.jumpEvery === 0))
    doJump(ctx);
  if (profile.actions.includes('look') && (ctx.step || 0) % 4 === 0) lookAround(ctx, profile, null);
  if (profile.actions.includes('swing')
    && (ctx.step || 0) % (profile.jumpEvery ? profile.jumpEvery * 2 : 20) === 0) doSwing(ctx, profile.mechanism);
  return 1;
}

function laserFighter(ctx, profile) {
  const target = ctx.nearestEntity(20);
  const location = locationOf(ctx, target);
  if (location) aimAt(ctx, location);
  doSwing(ctx, profile.mechanism);
  if (profile.actions.includes('use-item') && (ctx.step || 0) % 2 === 0) doUse(ctx, profile.mechanism);
  return 1;
}

const GAME_BEHAVIORS = {
  bingo(ctx, profile) {
    const mob = ctx.nearestEntity(6);
    const block = nearestObservedBlock(ctx) || blockTarget(ctx);
    if (mob != null && inReach(ctx, mob, 4.5)) {
      doInteract(ctx, mob, 'task-progress');
    } else if (ctx.allowWorldMutation && block) {
      packets.dig(ctx.client, block, ctx.nextSequence());
      ctx.record('actionTypes', 'block-dig');
      recordMechanism(ctx, 'task-progress');
    } else {
      walkPattern(ctx, profile);
    }
    if ((ctx.step || 0) % 8 === 0) doUse(ctx, 'task-progress');
    return 1;
  },

  tntrun(ctx, profile) { return runner(ctx, profile); },
  tgttos(ctx, profile) { return runner(ctx, profile); },
  parkourwarrior(ctx, profile) { return runner(ctx, profile); },
  hotycodydusky(ctx, profile) { return runner(ctx, profile); },
  acerace(ctx, profile) { return runner(ctx, profile); },
  riptide(ctx, profile) { return runner(ctx, profile); },

  parkourtag(ctx, profile) {
    const target = ctx.nearestEntity(24);
    const location = locationOf(ctx, target);
    if (target != null && location) {
      aimAt(ctx, location);
      if (distanceTo(ctx, location) > 3.5) stepToward(ctx, location, profile, 0.3);
      else doUse(ctx, 'tag-chase');
    } else {
      runner(ctx, profile);
    }
    return 1;
  },

  battlebox(ctx, profile) {
    const target = ctx.nearestEntity(6);
    const location = locationOf(ctx, target);
    if (target != null && location && distanceTo(ctx, location) <= 4.5) {
      selectWeapon(ctx);
      doAttack(ctx, target, 'weapon-combat');
      if ((ctx.step || 0) % 3 === 0) doUse(ctx, 'weapon-combat');
      return 1;
    }
    // Nothing in melee range: equip the kit, keep the target in view and close in.
    selectWeapon(ctx);
    if (location) {
      aimAt(ctx, location);
      recordMechanism(ctx, 'weapon-combat');
    } else {
      lookAround(ctx, profile, 'weapon-combat');
    }
    if (profile.actions.includes('sprint')) doSprint(ctx);
    if (profile.actions.includes('jump') && profile.jumpEvery && (ctx.step || 0) % profile.jumpEvery === 0)
      doJump(ctx);
    return 1;
  },

  lasershowdown(ctx, profile) { return laserFighter(ctx, profile); },
  laserbox(ctx, profile) { return laserFighter(ctx, profile); },

  skywars(ctx, profile) {
    const target = ctx.nearestEntity(16);
    const location = locationOf(ctx, target);
    if (target != null && location && distanceTo(ctx, location) <= 4.5) {
      selectWeapon(ctx);
      doAttack(ctx, target, 'resource-combat');
    } else if (location) {
      selectWeapon(ctx);
      stepToward(ctx, location, profile, 0.25);
    } else {
      walkPattern(ctx, profile);
    }
    if ((ctx.step || 0) % 6 === 0) doUse(ctx, 'resource-combat');
    return 1;
  },

  dragoneggcarnival(ctx, profile) {
    const target = ctx.nearestEntity(24);
    const location = locationOf(ctx, target);
    if (target != null && location && distanceTo(ctx, location) <= 4.5) {
      doInteract(ctx, target, 'egg-objective');
    } else if (location) {
      stepToward(ctx, location, profile, 0.3);
    } else {
      walkPattern(ctx, profile);
    }
    return 1;
  },

  buildmart(ctx, profile) {
    const target = nearestObservedBlock(ctx) || blockTarget(ctx);
    if (ctx.allowWorldMutation && target) {
      // Alternate harvesting the observed material block and placing at the plot.
      if ((ctx.step || 0) % 2 === 0) {
        packets.dig(ctx.client, target, ctx.nextSequence());
        ctx.record('actionTypes', 'block-dig');
      } else {
        packets.place(ctx.client, target, ctx.nextSequence());
        ctx.record('actionTypes', 'block-place');
      }
      recordMechanism(ctx, 'collect-place-submit');
      return 1;
    }
    walkPattern(ctx, profile);
    if (profile.actions.includes('look') && (ctx.step || 0) % 4 === 0) lookAround(ctx, profile, null);
    return 1;
  },

  dodgebolt(ctx, profile) {
    const target = ctx.nearestEntity(20);
    const location = locationOf(ctx, target);
    if (location) aimAt(ctx, location);
    // Bow semantics: right-click to draw, right-click again to release.
    doUse(ctx, 'arrow-round');
    return 1;
  },

  frostbite(ctx, profile) {
    const target = ctx.nearestEntity(16);
    const location = locationOf(ctx, target);
    if (location) aimAt(ctx, location);
    else lookAround(ctx, profile, null);
    doUse(ctx, 'freeze-cycle');
    return 1;
  },

  sulfursoccer(ctx, profile) {
    const target = ctx.nearestEntity(6, profile.targetEntityTypes);
    const location = locationOf(ctx, target);
    if (target != null && location && distanceTo(ctx, location) <= 4.5) {
      doAttack(ctx, target, 'kick-pearl-score');
      if ((ctx.step || 0) % 4 === 0) doUse(ctx, 'kick-pearl-score');
    } else if (location) {
      stepToward(ctx, location, profile, 0.3);
    } else {
      walkPattern(ctx, profile);
      if ((ctx.step || 0) % 6 === 0) doUse(ctx, 'kick-pearl-score');
    }
    return 1;
  },
};

// ---------------------------------------------------------------------------
// Low-level action emitter used by the non-progress phases (voting, preparation
// kit selection and block interaction) and as a legacy fallback.
// ---------------------------------------------------------------------------

function emitAction(action, profile, ctx) {
  const type = typeof action === 'string' ? action : action.type;
  const every = Number.isInteger(action?.every) ? action.every : ACTION_INTERVALS[type] || 20;
  if (ctx.step % every !== 0) return 0;
  const emitted = () => { ctx.record('actionTypes', type); return 1; };
  const mechanism = typeof action === 'object' && action.mechanism ? action.mechanism : profile.mechanism;
  const point = ctx.position;
  switch (type) {
    case 'look':
      packets.look(ctx.client, point.yaw + (ctx.index % 2 ? -1 : 1) * (profile.yawStep || 3), point.pitch);
      recordMechanism(ctx, mechanism);
      return emitted();
    case 'sprint':
      if (ctx.entityId == null) return 0;
      packets.sprint(ctx.client, ctx.entityId, true);
      recordMechanism(ctx, 'sprint');
      return emitted();
    case 'jump':
      packets.input(ctx.client, { forward: true, sprint: profile.sprint === true, jump: true });
      recordMechanism(ctx, 'jump');
      return emitted();
    case 'swing':
      packets.swing(ctx.client, ctx.nextSequence());
      recordMechanism(ctx, mechanism);
      return emitted();
    case 'use-item':
      packets.useItem(ctx.client, ctx.nextSequence());
      recordMechanism(ctx, mechanism);
      return emitted();
    case 'attack': {
      const target = ctx.nearestEntity(6, profile.targetEntityTypes);
      if (target != null) packets.attack(ctx.client, target);
      else packets.swing(ctx.client, ctx.nextSequence());
      recordMechanism(ctx, `${mechanism}${target != null ? '' : '-search'}`);
      return emitted();
    }
    case 'entity-interact': {
      const target = ctx.nearestEntity(6, profile.targetEntityTypes);
      if (target == null) return 0;
      packets.useEntity(ctx.client, target, ctx.entityLocation?.(target));
      recordMechanism(ctx, mechanism);
      return emitted();
    }
    case 'block-dig': {
      if (!ctx.allowWorldMutation) return 0;
      const target = blockTarget(ctx);
      if (!target) return 0;
      packets.dig(ctx.client, target, ctx.nextSequence());
      recordMechanism(ctx, mechanism);
      return emitted();
    }
    case 'block-place': {
      if (!ctx.allowWorldMutation) return 0;
      const target = blockTarget(ctx);
      if (!target) return 0;
      packets.place(ctx.client, target, ctx.nextSequence());
      recordMechanism(ctx, mechanism);
      return emitted();
    }
    case 'block-interact': {
      const target = blockTarget(ctx);
      if (!target) return 0;
      packets.interactBlock(ctx.client, target, ctx.nextSequence());
      recordMechanism(ctx, mechanism);
      return emitted();
    }
    case 'submit': {
      if (ctx.window && packets.clickWindow(ctx.client, ctx.window, Number.isInteger(ctx.submitSlot) ? ctx.submitSlot : 0)) {
        recordMechanism(ctx, mechanism);
        return emitted();
      }
      const target = ctx.nearestEntity(6);
      if (target != null) {
        packets.useEntity(ctx.client, target, ctx.entityLocation?.(target));
        recordMechanism(ctx, mechanism);
        return emitted();
      }
      return 0;
    }
    case 'menu-select':
      if (!ctx.window || ctx.menuSent) return 0;
      if (packets.clickWindow(ctx.client, ctx.window, Number.isInteger(ctx.menuSlot) ? ctx.menuSlot : 0)) {
        ctx.menuSent = true;
        recordMechanism(ctx, 'menu-select');
        return emitted();
      }
      return 0;
    default:
      return 0;
  }
}

function runProfileTick(profile, ctx) {
  const phase = ctx.phase || 'WAITING';
  const step = ctx.step;
  const point = ctx.position;
  if (!point || phase === 'END') return 0;
  if (phase === 'VOTING') {
    if (phaseAllows(profile, phase, 'vote-window') && ctx.window && !ctx.voteSent
      && packets.clickWindow(ctx.client, ctx.window, Number.isInteger(ctx.voteSlot) ? ctx.voteSlot : 0)) {
      ctx.voteSent = true;
      recordMechanism(ctx, 'vote-click');
      return 1;
    }
    if (phaseAllows(profile, phase, 'look') && step % 10 === 0)
      packets.look(ctx.client, point.yaw + (ctx.index % 2 ? -1 : 1) * (profile.yawStep || 3), point.pitch);
    return phaseAllows(profile, phase, 'look') && step % 10 === 0 ? 1 : 0;
  }
  if (phase === 'WAITING' || phase === 'PREPARATION' || phase === 'COUNTDOWN') {
    if (phase === 'PREPARATION' && phaseAllows(profile, phase, 'menu-select')) {
      const selected = emitAction('menu-select', profile, ctx);
      if (selected) return selected;
    }
    if (phase === 'PREPARATION' && phaseAllows(profile, phase, 'block-interact')) {
      const interacted = emitAction('block-interact', profile, ctx);
      if (interacted) return interacted;
    }
    if (phaseAllows(profile, phase, 'look') && step % 20 === 0) {
      packets.look(ctx.client, point.yaw + (ctx.index % 2 ? -1 : 1) * (profile.yawStep || 2), point.pitch);
      return 1;
    }
    return 0;
  }
  // PROGRESS: defer to the game-specific strategy.  A profile without one keeps
  // the legacy data-driven loop so external scripts remain compatible.
  const behavior = GAME_BEHAVIORS[profile.game];
  if (behavior) return behavior(ctx, profile);
  let count = 0;
  const extent = profile.kind === 'bingo-walk' ? 8 : 40;
  if (profile.kind === 'walk' || profile.kind === 'bingo-walk') {
    const phaseStep = step % (extent * 2);
    const stride = profile.stride || 0.05;
    point.x = ctx.origin.x + (phaseStep < extent ? phaseStep : extent * 2 - phaseStep) * stride;
    packets.position(ctx.client, point, profile.grounded === true);
    ctx.record('actionTypes', profile.kind === 'bingo-walk' ? 'move-ground' : 'move');
    recordMechanism(ctx, profile.mechanism);
    count += 1;
  } else if (profile.kind === 'look' && !profile.actions?.includes('look') && step % 4 === 0) {
    point.yaw += (ctx.index % 2 === 0 ? 1 : -1) * (step % 8) * (profile.yawStep || 3);
    packets.look(ctx.client, point.yaw, point.pitch);
    ctx.record('actionTypes', 'look');
    count += 1;
  }
  const actions = profile.actions || [];
  for (const action of actions) {
    const configured = typeof action === 'object' ? action : { type: action };
    if (configured.type === 'move' || configured.type === 'move-ground'
      || !phaseAllows(profile, phase, configured.type)) continue;
    const effective = configured.type === 'jump' && profile.jumpEvery > 0
      ? { ...configured, every: profile.jumpEvery }
      : configured;
    count += emitAction(effective, profile, ctx);
  }
  if (!actions.length && profile.interaction) {
    const legacy = profile.interaction === 'combat' ? 'attack'
      : profile.interaction === 'entity' ? 'entity-interact'
        : profile.interaction === 'build' ? 'block-place' : profile.interaction;
    count += emitAction(legacy, profile, ctx);
  }
  return count;
}

module.exports = { GAME_PROFILES, actionProfile, supportedGameProfiles, runProfileTick };
