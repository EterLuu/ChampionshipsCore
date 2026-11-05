/**
 * The executable part of docs/game-test-matrix.md.
 *
 * This file intentionally contains test intent rather than game rules.  A profile
 * consumes the action and evidence declarations, while Core/Worker remains the
 * authority for the resulting state and score.
 */

function deepFreeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value;
  Object.freeze(value);
  for (const child of Object.values(value)) deepFreeze(child);
  return value;
}

const phases = deepFreeze(['WAITING', 'VOTING', 'PREPARATION', 'COUNTDOWN', 'PROGRESS', 'END']);
const ACTION_TYPES = deepFreeze([
  'move', 'move-ground', 'sprint', 'jump', 'look', 'swing', 'use-item', 'attack',
  'entity-interact', 'block-dig', 'block-place', 'block-interact', 'submit', 'menu-select',
]);
const SERVER_EVIDENCE_TYPES = deepFreeze([
  'item', 'scoreboard', 'block-change', 'entity-spawn', 'entity-destroy',
]);

const SERVER_EVIDENCE = {
  bingo: ['item', 'scoreboard'], tntrun: ['block-change', 'scoreboard'],
  parkourtag: ['entity-destroy', 'scoreboard'], battlebox: ['entity-destroy', 'scoreboard'],
  lasershowdown: ['entity-destroy', 'scoreboard'], skywars: ['item', 'entity-destroy', 'scoreboard'],
  tgttos: ['scoreboard'], dragoneggcarnival: ['entity-spawn', 'scoreboard'],
  parkourwarrior: ['scoreboard'], hotycodydusky: ['entity-destroy', 'scoreboard'],
  buildmart: ['block-change', 'item', 'scoreboard'], dodgebolt: ['entity-destroy', 'scoreboard'],
  acerace: ['scoreboard'], riptide: ['entity-destroy', 'scoreboard'],
  frostbite: ['item', 'scoreboard'], laserbox: ['entity-destroy', 'scoreboard'],
  sulfursoccer: ['entity-spawn', 'scoreboard'],
};

const MATRIX_DEFINITIONS = {
  bingo: {
    token: 'bingo', topology: 'remote-worker', mechanism: 'task-progress',
    actions: ['move-ground', 'use-item', 'entity-interact'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points'],
    checks: ['manifest-freeze', 'task-event', 'death-respawn', 'three-world-release'],
  },
  tntrun: {
    token: 'tntrun', topology: 'multi-team', mechanism: 'floor-break',
    actions: ['move', 'sprint', 'jump', 'swing'],
    disconnect: ['COUNTDOWN', 'PROGRESS', 'END'], score: ['points', 'placement'],
    checks: ['floor-removal', 'fall-elimination', 'spectator', 'second-round'],
  },
  parkourtag: {
    token: 'parkourtag', topology: 'paired', mechanism: 'tag-chase',
    actions: ['move', 'sprint', 'jump', 'look', 'use-item', 'block-interact'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'survival-time'],
    checks: ['chaser-assignment', 'tag-hit', 'round-reset'],
  },
  battlebox: {
    token: 'battlebox', topology: 'paired', mechanism: 'weapon-combat',
    actions: ['look', 'sprint', 'jump', 'attack', 'use-item', 'block-interact'],
    disconnect: ['COUNTDOWN', 'PROGRESS', 'END'], score: ['points', 'round-wins'],
    checks: ['weapon-use', 'damage', 'round-end', 'equipment-clear'],
  },
  lasershowdown: {
    token: 'lasershowdown', topology: 'multi-team', mechanism: 'laser-shot',
    actions: ['look', 'swing', 'use-item'],
    disconnect: ['PROGRESS', 'END'], score: ['points', 'eliminations'],
    checks: ['laser-fire', 'hit', 'death-protection', 'spectator'],
  },
  skywars: {
    token: 'skywars', topology: 'multi-team', mechanism: 'resource-combat',
    actions: ['move', 'sprint', 'jump', 'use-item', 'attack'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'eliminations'],
    checks: ['resource-pickup', 'combat', 'void-elimination', 'second-round'],
  },
  tgttos: {
    token: 'tgttos', topology: 'multi-team', mechanism: 'parkour-finish',
    actions: ['move', 'sprint', 'jump', 'look'],
    disconnect: ['COUNTDOWN', 'PROGRESS', 'END'], score: ['points', 'finish-order'],
    checks: ['finish-detection', 'teleport', 'round-map-reset'],
  },
  dragoneggcarnival: {
    token: 'dragoneggcarnival', topology: 'finale', mechanism: 'egg-objective',
    actions: ['move', 'sprint', 'jump', 'entity-interact'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'objective'],
    checks: ['qualified-teams', 'egg-pickup-submit', 'audience-recovery'],
  },
  parkourwarrior: {
    token: 'parkourwarrior', topology: 'partitioned', mechanism: 'checkpoint',
    actions: ['move', 'sprint', 'jump', 'look'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'finish-order'],
    checks: ['room-assignment', 'checkpoint', 'completion-order', 'room-cleanup'],
  },
  hotycodydusky: {
    token: 'hotycodydusky', topology: 'multi-team', mechanism: 'zone-objective',
    actions: ['move', 'sprint', 'jump', 'look'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'zone'],
    checks: ['zone-completion', 'elimination', 'round-state'],
  },
  buildmart: {
    token: 'buildmart', topology: 'multi-team', mechanism: 'collect-place-submit',
    actions: ['move', 'look', 'block-dig', 'block-place', 'entity-interact'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'orders'],
    checks: ['material-collect', 'blueprint-place', 'submit', 'independent-restock', 'second-round'],
  },
  dodgebolt: {
    token: 'dodgebolt', topology: 'finale-paired', mechanism: 'arrow-round',
    actions: ['look', 'use-item', 'menu-select'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'round-wins'],
    checks: ['qualified-teams', 'arrow-pickup', 'shoot', 'round-reset'],
  },
  acerace: {
    token: 'acerace', topology: 'multi-team', mechanism: 'checkpoint-race',
    actions: ['move', 'sprint', 'jump', 'look'],
    disconnect: ['COUNTDOWN', 'PROGRESS', 'END'], score: ['points', 'finish-order'],
    checks: ['checkpoint', 'ranking', 'respawn', 'route-reset'],
  },
  riptide: {
    token: 'riptide', topology: 'multi-team', mechanism: 'raft-stage',
    actions: ['move', 'sprint', 'jump'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'survival', 'top-three'],
    checks: ['five-stages', 'raft', 'water-elimination', 'round-state'],
  },
  frostbite: {
    token: 'frostbite', topology: 'multi-team', mechanism: 'freeze-cycle',
    actions: ['look', 'use-item'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'survival'],
    checks: ['freeze-unfreeze', 'equipment-clear', 'death-recovery'],
  },
  laserbox: {
    token: 'laserbox', topology: 'paired', mechanism: 'two-target-laser',
    actions: ['look', 'swing', 'use-item'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['points', 'eliminations'],
    checks: ['pairing', 'shoot', 'two-target-limit', 'round-reset'],
  },
  sulfursoccer: {
    token: 'sulfursoccer', topology: 'finale-or-paired', mechanism: 'kick-pearl-score',
    actions: ['move', 'sprint', 'jump', 'attack', 'use-item'],
    disconnect: ['PREPARATION', 'PROGRESS', 'END'], score: ['goals', 'winner'],
    checks: ['trial-kick', 'kickoff', 'pearl-cooldown', 'goal', 'penalty-reset'],
  },
};

function phaseActions(actions) {
  const preparation = ['observe', 'look'];
  if (actions.includes('menu-select')) preparation.push('menu-select');
  if (actions.includes('block-interact')) preparation.push('block-interact');
  return {
    WAITING: ['observe'],
    VOTING: ['observe', 'vote-window'],
    PREPARATION: preparation,
    COUNTDOWN: ['observe', 'look'],
    PROGRESS: actions,
    END: ['observe'],
  };
}

function reconnectExpectations(disconnect) {
  return disconnect.map(phase => ({
    phase,
    preserve: phase === 'END'
      ? ['identity', 'team', 'settled-score']
      : ['identity', 'team', 'world', 'game-mode', 'current-round'],
  }));
}

const GAME_TEST_MATRIX = deepFreeze(Object.fromEntries(
  Object.entries(MATRIX_DEFINITIONS).map(([game, contract]) => [game, {
    ...contract,
    serverEvidence: SERVER_EVIDENCE[game],
    phaseActions: phaseActions(contract.actions),
    reconnect: reconnectExpectations(contract.disconnect),
  }]),
));

function gameContract(game) {
  const key = String(game || '').toLowerCase();
  const contract = GAME_TEST_MATRIX[key];
  if (!contract) throw new Error(`unknown game matrix profile: ${game}`);
  return contract;
}

function supportedMatrixGames() {
  return Object.keys(GAME_TEST_MATRIX);
}

function validateMatrix(matrix = GAME_TEST_MATRIX) {
  const errors = [];
  for (const [game, contract] of Object.entries(matrix)) {
    if (contract.token !== game) errors.push(`${game}: token must match key`);
    if (!contract.mechanism) errors.push(`${game}: mechanism is required`);
    if (!Array.isArray(contract.actions) || contract.actions.length === 0) errors.push(`${game}: actions are required`);
    if (Array.isArray(contract.actions)) {
      for (const action of contract.actions)
        if (!ACTION_TYPES.includes(action)) errors.push(`${game}: unsupported action ${action}`);
    }
    if (!Array.isArray(contract.disconnect) || contract.disconnect.some(phase => !phases.includes(phase)))
      errors.push(`${game}: invalid disconnect phase`);
    if (!Array.isArray(contract.score) || contract.score.length === 0) errors.push(`${game}: score evidence is required`);
    if (!Array.isArray(contract.serverEvidence) || contract.serverEvidence.length === 0) errors.push(`${game}: server evidence is required`);
    if (Array.isArray(contract.serverEvidence)) {
      for (const evidence of contract.serverEvidence)
        if (!SERVER_EVIDENCE_TYPES.includes(evidence)) errors.push(`${game}: unsupported server evidence ${evidence}`);
    }
    if (!Array.isArray(contract.checks) || contract.checks.length === 0) errors.push(`${game}: mechanism checks are required`);
    if (!contract.phaseActions || !contract.reconnect
      || contract.reconnect.length !== contract.disconnect.length)
      errors.push(`${game}: phase/reconnect contract is required`);
  }
  return errors;
}

module.exports = { phases, ACTION_TYPES, SERVER_EVIDENCE_TYPES, GAME_TEST_MATRIX, gameContract, supportedMatrixGames, validateMatrix };
