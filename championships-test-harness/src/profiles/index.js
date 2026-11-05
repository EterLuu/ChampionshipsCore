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
    case 'move':
    case 'move-ground':
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
    // Preserve the profile's shorter jump cadence while exposing the action in
    // the matrix contract.
    const configured = typeof action === 'object' ? action : { type: action };
    if (configured.type === 'move' || configured.type === 'move-ground'
      || !phaseAllows(profile, phase, configured.type)) continue;
    const effective = configured.type === 'jump' && profile.jumpEvery > 0
      ? { ...configured, every: profile.jumpEvery }
      : configured;
    count += emitAction(effective, profile, ctx);
  }
  // Legacy profiles supplied by external scripts may still only define
  // `interaction`; retain that compatibility path while all built-in profiles
  // use the executable matrix actions above.
  if (!actions.length && profile.interaction) {
    const legacy = profile.interaction === 'combat' ? 'attack'
      : profile.interaction === 'entity' ? 'entity-interact'
        : profile.interaction === 'build' ? 'block-place' : profile.interaction;
    count += emitAction(legacy, profile, ctx);
  }
  return count;
}

module.exports = { GAME_PROFILES, actionProfile, supportedGameProfiles, runProfileTick };
