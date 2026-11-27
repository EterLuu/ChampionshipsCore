const { ClientMovement } = require('./movement');
const { VerificationPickaxe } = require('./profiles/verification-pickaxe');
const { actionProfile, announcedGame, runProfileTick } = require('./profiles');
const { packetText, PhaseTracker } = require('./phase');

function dialogNbt(values) {
  const value = {};
  for (const [key, item] of Object.entries(values)) value[key] = { type: 'string', value: item };
  return { type: 'compound', name: '', value };
}

function compactPacketValue(value, depth = 0) {
  if (depth > 4 || value == null) return value == null ? null : String(value);
  if (typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.slice(0, 8).map(item => compactPacketValue(item, depth + 1));
  const result = {};
  for (const key of Object.keys(value).slice(0, 16)) {
    if (key === 'components' || key === 'nbt' || key === 'data') continue;
    result[key] = compactPacketValue(value[key], depth + 1);
  }
  return result;
}

// The protocol library handles configuration; the application owns teleport acknowledgements.
function attachClient(client, settings, index, result, state, timers = globalThis) {
  const version = settings.version || '26.3';
  const data = require('minecraft-data')(version);
  const movement = new ClientMovement(client, version);
  const automatic = settings.action === 'game' && !settings.game;
  let profile = actionProfile(settings);
  let memory = {};
  let tick = 0;
  const itemName = item => item && item.itemCount !== 0
    ? data.items[item.itemId]?.name || item.name : null;
  let actionTimer = null;
  let actionStartTimer = null;
  let position = null;
  let origin = null;
  let loadingAcknowledged = false;
  let step = 0;
  let sequence = 0;
  const initialPhase = state.phase || 'WAITING';
  let phaseTracker = new PhaseTracker(profile.markers, initialPhase);
  state.phase = phaseTracker.phase;
  state.phaseTransitions = state.phaseTransitions || [];
  state.mechanismPackets = state.mechanismPackets || {};
  state.actionTypes = state.actionTypes || {};
  state.mechanismPhases = state.mechanismPhases || {};
  state.actionPhases = state.actionPhases || {};
  state.serverEvidence = state.serverEvidence || {};
  state.serverEvidencePhases = state.serverEvidencePhases || {};
  state.observations = state.observations || {
    entitySpawns: 0, entityDestroys: 0, blockChanges: 0, scoreboardObjectives: 0, teamPackets: 0,
  };
  state.uuidHistory = state.uuidHistory || [];
  state.reconnectHistory = state.reconnectHistory || [];
  state.teamHistory = state.teamHistory || [];
  state.gameModeHistory = state.gameModeHistory || [];
  state.scoreHistory = state.scoreHistory || [];
  state.scores = state.scores || {};
  state.evidence = state.evidence || {
    blocks: [], items: [], windows: [], entities: [], messages: [],
  };
  state.entities = new Map();
  state.blockChanges = state.blockChanges || 0;
  state.targetBlock = state.targetBlock || null;
  state.recentBlocks = state.recentBlocks || [];
  state.window = null;
  state.menuSent = false;
  state.hotbar = state.hotbar || new Array(9).fill(null);
  state.inventorySlots = state.inventorySlots || {};
  state.heldSlot = Number.isInteger(state.heldSlot) ? state.heldSlot : 0;
  state.injectedDisconnectPhases = state.injectedDisconnectPhases || new Set();
  const increment = key => {
    result[key] = (result[key] || 0) + 1;
    state[key] = (state[key] || 0) + 1;
  };
  const record = (key, value) => {
    if (key === 'mechanismPackets') {
      state.mechanismPackets[value] = (state.mechanismPackets[value] || 0) + 1;
      const phases = state.mechanismPhases[value] || (state.mechanismPhases[value] = {});
      const phase = state.phase || 'WAITING';
      phases[phase] = (phases[phase] || 0) + 1;
      return;
    }
    if (key === 'actionTypes') {
      state.actionTypes[value] = (state.actionTypes[value] || 0) + 1;
      const phases = state.actionPhases[value] || (state.actionPhases[value] = {});
      const phase = state.phase || 'WAITING';
      phases[phase] = (phases[phase] || 0) + 1;
      return;
    }
    state[key] = (state[key] || 0) + 1;
  };
  const evidence = (key, value) => {
    const list = state.evidence[key] || (state.evidence[key] = []);
    if (list.length < 32) list.push(compactPacketValue(value));
  };
  const serverEvidence = key => {
    state.serverEvidence[key] = (state.serverEvidence[key] || 0) + 1;
    const phases = state.serverEvidencePhases[key] || (state.serverEvidencePhases[key] = {});
    const phase = state.phase || 'WAITING';
    phases[phase] = (phases[phase] || 0) + 1;
  };
  const nextSequence = () => ++sequence;
  const entityLocation = entityId => {
    const entity = state.entities.get(entityId);
    if (!entity || ![entity.x, entity.y, entity.z].every(Number.isFinite)) return { x: 0, y: 0, z: 0 };
    return { x: entity.x, y: entity.y, z: entity.z };
  };
  const nearestEntity = (maxDistance = Infinity, allowedTypes = null) => {
    const candidates = [...state.entities.entries()]
      .filter(([entityId]) => entityId !== state.entityId)
      .filter(([, entity]) => !Array.isArray(allowedTypes) || allowedTypes.length === 0
        || allowedTypes.includes(String(entity.type ?? entity.entityType ?? '').toLowerCase()))
      .map(([entityId, entity]) => {
        const hasPosition = [entity.x, entity.y, entity.z].every(Number.isFinite);
        const distance = hasPosition && position
          ? Math.hypot(entity.x - position.x, entity.y - position.y, entity.z - position.z)
          : 0;
        return { entityId, distance, hasPosition };
      })
      // Once we know our own position, ignore entities the server has not
      // positioned yet: an unpositioned target cannot be aimed or reached.
      .filter(candidate => position == null || candidate.hasPosition)
      .filter(candidate => candidate.distance <= maxDistance)
      .sort((a, b) => a.distance - b.distance);
    return candidates.length ? candidates[0].entityId : null;
  };
  const selectHeldSlot = slot => {
    if (!Number.isInteger(slot) || slot < 0 || slot > 8) return false;
    if (slot === state.heldSlot) return true;
    client.write('held_item_slot', { slotId: slot });
    state.heldSlot = slot;
    return true;
  };
  const pickaxe = new VerificationPickaxe(movement, state, selectHeldSlot, nextSequence);
  const rememberBlock = location => {
    if (!location) return;
    state.recentBlocks.push({ x: location.x, y: location.y, z: location.z });
    if (state.recentBlocks.length > 16) state.recentBlocks.shift();
  };
  const stopActions = () => {
    if (actionTimer != null) timers.clearInterval(actionTimer);
    if (actionStartTimer != null) timers.clearTimeout(actionStartTimer);
    actionTimer = null;
    actionStartTimer = null;
  };
  const resetWorld = (preservePhase = false, disconnecting = false) => {
    stopActions();
    position = null;
    origin = null;
    loadingAcknowledged = false;
    step = 0;
    state.worldReady = false;
    state.voteSent = false;
    state.targetBlock = null;
    state.recentBlocks = [];
    state.entities.clear();
    state.hotbar = new Array(9).fill(null);
    state.inventorySlots = {};
    memory = {};
    if (automatic && !disconnecting && (!preservePhase || movement.worldName !== state.gameWorld)) {
      state.gameWorld = movement.worldName;
      state.detectedGame = null;
      profile = actionProfile(settings);
      phaseTracker = new PhaseTracker();
    } else if (automatic && state.detectedGame) {
      profile = actionProfile({ ...settings, game: state.detectedGame });
      phaseTracker = new PhaseTracker(profile.markers, phaseTracker.phase);
    }
    if (!preservePhase) phaseTracker.reset();
    state.phase = phaseTracker.phase;
  };
  const died = () => {
    if (!state.active || client.state !== 'play' || state.stopRequested) return;
    if (!state.dead) {
      increment('deaths');
      state.lastDeathAt = new Date().toISOString();
    }
    pickaxe.cancel();
    state.dead = true;
    state.worldReady = false;
    stopActions();
    // Health and combat-death packets can describe the same death. Request exactly once until
    // the server confirms respawn, and keep the old position only to resolve relative teleports.
    if (!state.respawnRequested) {
      state.respawnRequested = true;
      client.write('client_command', { actionId: 0 });
      client.write('tick_end', {});
      increment('respawnRequests');
    }
  };
  const startActions = () => {
    stopActions();
    let cadenceLeft = 50;
    // Folia can deliver the world position before the destination region has finished its
    // first ticks. Let the server settle the cross-world teleport before sending movement.
    actionStartTimer = timers.setTimeout(() => {
      actionStartTimer = null;
      actionTimer = timers.setInterval(() => {
        if (client.state !== 'play' || position == null || state.dead || state.stopRequested) return;
        const pending = movement.pendingAction;
        movement.pendingAction = null;
        pending?.();
        tick++;
        cadenceLeft -= 50;
        if (cadenceLeft <= 0) {
          cadenceLeft += profile.cadenceMs || 50;
          movement.beginDecision();
          step += 1;
          if (settings.action === 'game') {
            const actionContext = {
              client: movement,
              index,
              step,
              phase: state.phase,
              position: { ...position },
              origin,
              entityId: state.entityId,
              window: state.window,
              voteSent: state.voteSent,
              voteSlot: settings.voteSlot,
              submitSlot: settings.submitSlot,
              menuSlot: settings.menuSlot,
              menuSent: state.menuSent,
              targetBlock: state.targetBlock,
              recentBlocks: state.recentBlocks,
              entities: state.entities,
              memory, tick,
              pickaxe,
              walkingHeading: (angle, side) => movement.walkingHeading(angle, side),
              fleeFrom: (threats, bearing) => movement.navigator.escapeHeading(movement.player, threats, bearing, tick),
              navigate: (goal, mode) => movement.navigator.heading(movement.player, goal, mode, tick),
              afterMove: (fn, target) => {
                const decision = memory, slot = state.heldSlot, item = state.hotbar[slot], role = state.hotbar[4];
                movement.intent.afterMove = () => {
                  if (decision !== memory || role !== state.hotbar[4] || slot !== state.heldSlot
                    || state.hotbar[slot] !== item) return;
                  const hit = target && movement.crosshairIntersection(target, state.entities);
                  if (!target || hit) fn(hit);
                };
              },
              hotbar: state.hotbar,
              inventory: state.inventorySlots,
              heldSlot: state.heldSlot,
              selectHeldSlot,
              allowWorldMutation: settings.allowWorldMutation === true,
              nextSequence,
              nearestEntity,
              entityLocation,
              record,
            };
            const emitted = runProfileTick(profile, actionContext);
            if (actionContext.voteSent !== undefined) state.voteSent = actionContext.voteSent;
            if (actionContext.menuSent !== undefined) state.menuSent = actionContext.menuSent;
            result.actionPackets += emitted;
          } else if (profile.kind === 'walk' || profile.kind === 'bingo-walk') {
            // Both walk profiles use a bounded horizontal path.
            const extent = profile.kind === 'bingo-walk' ? 8 : 40;
            const phase = step % (extent * 2);
            const stride = profile.stride || 0.05;
            const x = origin.x + (phase < extent ? phase : extent * 2 - phase) * stride;
            movement.write('position', { x, y: position.y, z: position.z,
              flags: { onGround: profile.kind === 'bingo-walk', hasHorizontalCollision: false } });
          } else if (profile.kind === 'look' && step % 10 === 0) {
            movement.write('look', { yaw: position.yaw + (index % 2 === 0 ? 1 : -1) * (step % 8) * (profile.yawStep || 3),
              pitch: position.pitch, flags: { onGround: false, hasHorizontalCollision: false } });
          }
          if (settings.action !== 'game' && (profile.kind === 'walk' || profile.kind === 'bingo-walk'
            || (profile.kind === 'look' && step % 10 === 0))
          ) result.actionPackets += 1;
        }
        movement.tick(position, state.hotbar[state.heldSlot], state.inventorySlots[45]);
        // Since 26.3 the server rejects a second position before a client tick-end boundary.
        client.write('tick_end', {});
      }, 50);
    }, 1000);
  };
  client.on('success', packet => {
    state.serverUuid = packet.uuid;
    if (packet.uuid && !state.uuidHistory.includes(packet.uuid)) state.uuidHistory.push(packet.uuid);
    if (state.currentReconnect) {
      state.currentReconnect.uuidAfter = packet.uuid || null;
      state.currentReconnect.identityPreserved = !state.currentReconnect.uuidBefore
        || !packet.uuid || state.currentReconnect.uuidBefore === packet.uuid;
    }
  });
  function spawnEntity(packet, type) {
    if (!Number.isInteger(packet.entityId)) return;
    state.entities.set(packet.entityId, { ...packet, type });
    serverEvidence('entity-spawn');
    state.observations.entitySpawns += 1;
    evidence('entities', { event: 'spawn', entityId: packet.entityId, type });
  }
  client.on('spawn_entity', p => spawnEntity(p, data.entities[p.type]?.name || p.type));
  client.on('named_entity_spawn', p => spawnEntity(p, 'player'));
  client.on('entity_equipment', p => {
    if (p.entityId === movement.entityId) {
      const boots = p.equipments.find(e => e.slot === 2);
      if (boots) movement.leatherBoots = itemName(boots.item) === 'leather_boots';
    }
    const entity = state.entities.get(p.entityId);
    if (entity?.type !== 'player' && entity?.type !== 'mannequin') return;
    for (const { slot, item } of p.equipments) {
      if (slot === 5) entity.helmet = itemName(item);
      if (slot === 0) entity.hand = itemName(item);
    }
  });
  client.on('entity_metadata', p => {
    const entity = state.entities.get(p.entityId);
    if (!entity) return;
    for (const entry of p.metadata) {
      if ((automatic || profile.game === 'craftingfeast') && entry.key === 2) entity.customName = packetText(entry.value);
      if (entity.type === 'text_display' && entry.type === 'component') entity.text = packetText(entry.value);
      if (entry.key === 0) entity.flags = entry.value;
      if ((entity.type === 'item_display' || entity.type === 'item') && entry.type === 'item_stack')
        entity.displayItem = itemName(entry.value);
    }
  });
  client.on('entity_destroy', packet => {
    const entities = packet.entityIds || packet.entities || [];
    if (entities.length) serverEvidence('entity-destroy');
    state.observations.entityDestroys += entities.length;
    evidence('entities', { event: 'destroy', entityIds: entities.slice(0, 16) });
    for (const entityId of entities) state.entities.delete(entityId);
  });
  // Keep tracked entity positions current for aim/reach decisions.  These are
  // pure observation packets; the harness never derives a target of its own.
  const moveEntity = (packet, absolute) => {
    if (!Number.isInteger(packet.entityId)) return;
    const entity = state.entities.get(packet.entityId);
    if (!entity) return;
    const delta = packet.delta || packet;
    for (const [i, key] of ['x', 'y', 'z'].entries()) {
      if (absolute && Number.isFinite(packet[key])) {
        entity[key] = packet[key] + ((packet.flags & (1 << i)) ? entity[key] : 0);
      } else if (!absolute && Number.isFinite(delta['d' + key.toUpperCase()])) {
        entity[key] += delta['d' + key.toUpperCase()];
      }
    }
    if (Number.isFinite(packet.yaw)) entity.yaw = packet.yaw;
    if (Number.isFinite(packet.pitch)) entity.pitch = packet.pitch;
  };
  client.on('rel_entity_move', packet => moveEntity(packet, false));
  client.on('entity_move_look', packet => moveEntity(packet, false));
  client.on('entity_teleport', packet => moveEntity(packet, true));
  client.on('sync_entity_position', packet => {
    const path = packet.position.path;
    const end = path.endPosition || path.steps?.at(-1)?.position;
    if (end) moveEntity({ ...end, entityId: packet.entityId }, true);
  });
  client.on('block_change', packet => {
    const location = packet.location || packet.position;
    if (!location || !Number.isInteger(location.x) || !Number.isInteger(location.y) || !Number.isInteger(location.z)) return;
    // Keep one server-observed block as a legal target for BuildMart-style
    // profiles.  Never invent a coordinate when the server has not sent one.
    state.targetBlock = { x: location.x, y: location.y, z: location.z };
    rememberBlock(location);
    serverEvidence('block-change');
    state.blockChanges = (state.blockChanges || 0) + 1;
    state.observations.blockChanges += 1;
    evidence('blocks', { event: 'change', location, type: packet.type ?? packet.blockStateId });
  });
  client.on('multi_block_change', packet => {
    serverEvidence('block-change');
    state.observations.blockChanges += Array.isArray(packet.records) ? packet.records.length : 1;
    evidence('blocks', { event: 'multi-change', count: Array.isArray(packet.records) ? packet.records.length : 1 });
  });
  client.on('open_window', packet => {
    state.window = packet;
    evidence('windows', { event: 'open', windowId: packet.windowId, type: packet.type, title: packet.title });
    observe('open_window', packet);
  });
  client.on('close_window', () => { state.window = null; });
  const updateInventory = (slot, item) => {
    if (!Number.isInteger(slot)) return;
    const stack = item && item.itemCount !== 0 ? { ...item, name: itemName(item) } : null;
    state.inventorySlots[slot] = stack;
    if (slot === 8) movement.leatherBoots = stack?.name === 'leather_boots';
    if (slot >= 36 && slot <= 44) state.hotbar[slot - 36] = stack;
  };
  client.on('set_player_inventory', ({ slotId, contents }) => {
    if (slotId === 36) updateInventory(8, contents);
    if (slotId >= 0 && slotId < 36) {
      serverEvidence('item');
      updateInventory(slotId < 9 ? 36 + slotId : slotId, contents);
    }
  });
  client.on('held_item_slot', ({ slot }) => { state.heldSlot = slot; });
  client.on('set_slot', packet => {
    serverEvidence('item');
    if (state.window && packet.windowId === state.window.windowId && Number.isInteger(packet.stateId))
      state.window.stateId = packet.stateId;
    if (packet.windowId === 0) updateInventory(packet.slot, packet.item);
    evidence('items', { event: 'slot', windowId: packet.windowId, slot: packet.slot, item: packet.item });
  });
  client.on('window_items', packet => {
    serverEvidence('item');
    if (state.window && packet.windowId === state.window.windowId && Number.isInteger(packet.stateId))
      state.window.stateId = packet.stateId;
    if (packet.windowId === 0) {
      const slots = packet.items || packet.slots || [];
      slots.forEach((item, slot) => updateInventory(slot, item));
    }
    evidence('items', { event: 'window', windowId: packet.windowId, count: (packet.items || packet.slots || []).length });
  });
  client.on('scoreboard_score', packet => {
    if (typeof packet.scoreName !== 'string' || typeof packet.itemName !== 'string') return;
    const objective = state.scores[packet.scoreName] || (state.scores[packet.scoreName] = {});
    serverEvidence('scoreboard');
    objective[packet.itemName] = packet.value;
    state.scoreHistory.push({
      objective: packet.scoreName,
      entry: packet.itemName,
      value: packet.value,
      phase: state.phase,
      at: new Date().toISOString(),
    });
    increment('scorePackets');
  });
  client.on('scoreboard_objective', packet => {
    if (typeof packet.name === 'string' || typeof packet.objective === 'string') {
      serverEvidence('scoreboard');
      state.observations.scoreboardObjectives += 1;
      const objective = packet.name || packet.objective;
      state.scoreObjectives = state.scoreObjectives || {};
      state.scoreObjectives[objective] = packet.mode ?? packet.action ?? null;
    }
  });
  client.on('teams', packet => {
    state.observations.teamPackets += 1;
    if (state.teamHistory.length < 256) {
      state.teamHistory.push({
        name: packet.name || null,
        mode: packet.mode ?? packet.action ?? null,
        players: Array.isArray(packet.players) ? packet.players.slice(0, 64) : [],
      });
    }
    if (state.currentReconnect) {
      state.currentReconnect.teamPacketsAfter = Math.max(0,
        state.observations.teamPackets - state.currentReconnect.teamPacketsBefore);
    }
  });
  client.on('game_state_change', packet => {
    const mode = packet.gameMode ?? packet.gamemode ?? packet.mode;
    if (mode != null && state.gameModeHistory.length < 64) state.gameModeHistory.push(mode);
    if (state.currentReconnect) {
      state.currentReconnect.gameModePacketsAfter = Math.max(0,
        state.gameModeHistory.length - state.currentReconnect.gameModePacketsBefore);
    }
  });
  for (const packetName of ['system_chat', 'action_bar', 'set_title_text', 'set_title_subtitle', 'scoreboard_objective', 'scoreboard_score', 'teams']) {
    client.on(packetName, packet => {
      if (['system_chat', 'action_bar', 'set_title_text', 'set_title_subtitle'].includes(packetName))
        evidence('messages', { packet: packetName, text: packet.content ?? packet.text ?? packet.title ?? packet });
      observe(packetName, packet);
    });
  }
  client.on('login', packet => {
    if (state.loggedIn && state.pendingReconnect) {
      state.currentReconnect = {
        ...state.pendingReconnect,
        uuidAfter: state.serverUuid || null,
        identityPreserved: !state.pendingReconnect.uuidBefore
          || !state.serverUuid || state.pendingReconnect.uuidBefore === state.serverUuid,
        teamPacketsAfter: 0,
        gameModePacketsAfter: 0,
        worldReadyAfter: false,
      };
      state.reconnectHistory.push(state.currentReconnect);
      state.pendingReconnect = null;
    }
    resetWorld(state.loggedIn === true);
    state.dead = false;
    state.respawnRequested = false;
    state.entityId = packet?.entityId ?? null;
    state.active = true;
    increment('playLogins');
    if (!state.loggedIn) {
      state.loggedIn = true;
      result.loggedIn += 1;
    }
  });
  client.on('update_health', packet => { if (packet.health <= 0) died(); });
  client.on('death_combat_event', packet => {
    if (state.entityId != null && packet.playerId === state.entityId) died();
  });
  client.on('respawn', () => {
    if (state.dead) increment('deathRespawns');
    increment('respawnPackets');
    state.dead = false;
    state.respawnRequested = false;
    resetWorld(true);
  });
  client.on('start_configuration', () => resetWorld(false));
  const readyWhenTerrainArrives = () => {
    if (state.worldReady || state.dead || !position || !movement.column(position.x, position.z)) return;
    if (!loadingAcknowledged) {
      client.write('player_loaded', {});
      loadingAcknowledged = true;
    }
    state.worldReady = true;
    if (state.currentReconnect) state.currentReconnect.worldReadyAfter = true;
    if (actionTimer == null && actionStartTimer == null) startActions();
  };
  client.on('map_chunk', readyWhenTerrainArrives);
  client.on('player_rotation', packet => {
    if (position && movement.player) movement.rotate(position, packet);
  });
  client.on('position', packet => {
    const previous = position || { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
    const next = {};
    for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) {
      next[key] = packet[key] + (packet.flags?.[key] ? previous[key] : 0);
    }
    if (!Object.values(next).every(Number.isFinite)) throw new Error('invalid server position');
    position = next;
    movement.teleport(next, packet);
    if (profile.game !== 'hotycodydusky' || !origin || Math.hypot(previous.x - next.x, previous.z - next.z) > 8) {
      origin = { ...next };
      step = 0;
    }
    // Protocol 777 includes the resolved position/rotation in the confirmation itself.
    client.write('teleport_confirm', { teleportId: packet.teleportId, ...next });
    client.write('tick_end', {});
    result.teleportConfirmations += 1;
    state.teleportConfirmations = (state.teleportConfirmations || 0) + 1;
    state.worldReady = false;
    if (!movement.column(next.x, next.z)) stopActions();
    readyWhenTerrainArrives();
  });
  client.on('show_dialog', packet => {
    const text = JSON.stringify(packet.dialog || packet);
    const register = text.includes('prejoin-register/submit');
    if (!register && !text.includes('prejoin-login/submit')) return;
    client.write('custom_click_action', {
      id: register ? 'authme:prejoin-register/submit' : 'authme:prejoin-login/submit',
      nbt: dialogNbt(register
        ? { password: settings.password, confirm: settings.password }
        : { password: settings.password }),
    });
    result.authDialogSubmissions += 1;
    state.authDialogSubmissions = (state.authDialogSubmissions || 0) + 1;
  });
  client.on('ping', ({ id }) => { if (client.state === 'play') client.write('pong', { id }); });
  client.on('keep_alive', () => {
    state.keepAliveRequests = (state.keepAliveRequests || 0) + 1;
    state.lastKeepAliveAt = new Date().toISOString();
  });
  client.on('keepAliveTimeout', details => { state.keepAliveDiagnostics = details; });
  client.on('end', reason => {
    state.disconnectReason = reason;
    state.keepAliveDiagnostics = state.keepAliveDiagnostics || client._keepAliveDiagnostics || null;
    if (state.loggedIn && !state.stopRequested) {
      state.pendingReconnect = {
        phase: state.phase || 'WAITING',
        uuidBefore: state.serverUuid || null,
        teamPacketsBefore: state.observations.teamPackets,
        gameModePacketsBefore: state.gameModeHistory.length,
      };
    }
    state.active = false;
    // Keep the last observed game phase for a reconnect. The server may not repeat its
    // title/actionbar immediately after login, and resuming the wrong lobby actions is worse
    // than waiting for a fresh phase signal.
    resetWorld(true, true);
  });
  function observe(packetName, packet) {
    if (automatic && ['action_bar', 'set_title_text', 'set_title_subtitle'].includes(packetName)) {
      const game = announcedGame(packetName, packetText(packet));
      if (game !== undefined && game !== state.detectedGame) {
        profile = actionProfile({ ...settings, game });
        state.detectedGame = game;
        state.gameWorld = movement.worldName;
        phaseTracker = new PhaseTracker(profile.markers);
        memory = {};
      }
    }
    const transition = phaseTracker.observe(packetName, packet);
    state.phase = phaseTracker.phase;
    if (transition) {
      pickaxe.cancel();
      movement.navigator.reset();
      memory = {};
      state.phaseTransitions.push(transition);
      if (transition.to === 'VOTING') state.voteSent = false;
      if (transition.to === 'PREPARATION') state.menuSent = false;
      const expected = settings.disconnectPhases || (settings.disconnectPhase ? [String(settings.disconnectPhase).toUpperCase()] : []);
      if (expected.includes(transition.to) && !state.injectedDisconnectPhases.has(transition.to)) {
        state.injectedDisconnectPhases.add(transition.to);
        increment('injectedDisconnects');
        timers.setTimeout(() => {
          if (!state.stopRequested && state.active && typeof client.end === 'function') client.end('harness phase disconnect');
        }, settings.disconnectDelayMs || 0);
      }
    }
  }
  return stopActions;
}

function successfulRun(states, result) {
  return states.length === result.count
    && states.every(state => state.loggedIn && state.active && state.worldReady && !state.dead
      && (state.uuidHistory || []).length <= 1)
    && result.fatalErrors.length === 0;
}

module.exports = { attachClient, successfulRun };
