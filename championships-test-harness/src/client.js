function dialogNbt(values) {
  const value = {};
  for (const [key, item] of Object.entries(values)) value[key] = { type: 'string', value: item };
  return { type: 'compound', name: '', value };
}

// The protocol library handles configuration; the application owns teleport acknowledgements.
function attachClient(client, settings, index, result, state, timers = globalThis) {
  let actionTimer = null;
  let actionStartTimer = null;
  let position = null;
  let origin = null;
  let step = 0;
  const increment = key => {
    result[key] = (result[key] || 0) + 1;
    state[key] = (state[key] || 0) + 1;
  };
  const stopActions = () => {
    if (actionTimer != null) timers.clearInterval(actionTimer);
    if (actionStartTimer != null) timers.clearTimeout(actionStartTimer);
    actionTimer = null;
    actionStartTimer = null;
  };
  const resetWorld = () => {
    stopActions();
    position = null;
    origin = null;
    step = 0;
    state.worldReady = false;
  };
  const died = () => {
    if (!state.active || client.state !== 'play' || state.stopRequested) return;
    if (!state.dead) {
      increment('deaths');
      state.lastDeathAt = new Date().toISOString();
    }
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
    // Folia can deliver the world position before the destination region has finished its
    // first ticks. Let the server settle the cross-world teleport before sending movement.
    actionStartTimer = timers.setTimeout(() => {
      actionStartTimer = null;
      actionTimer = timers.setInterval(() => {
        if (client.state !== 'play' || position == null || state.dead || state.stopRequested) return;
        step += 1;
        if (settings.action === 'walk' || settings.action === 'bingo-walk') {
          // The generic walk profile is deliberately a movement smoke. bingo-walk keeps the
          // same bounded path but reports an on-ground movement suitable for WALK_ONE_CM tasks.
          const extent = settings.action === 'bingo-walk' ? 8 : 40;
          const phase = step % (extent * 2);
          const stride = settings.action === 'bingo-walk' ? 0.25 : 0.05;
          position.x = origin.x + (phase < extent ? phase : extent * 2 - phase) * stride;
          client.write('position', { x: position.x, y: position.y, z: position.z,
            flags: { onGround: settings.action === 'bingo-walk', hasHorizontalCollision: false } });
        } else if (settings.action === 'look' && step % 10 === 0) {
          client.write('look', { yaw: position.yaw + (index % 2 === 0 ? 1 : -1) * (step % 8) * 3,
            pitch: position.pitch, flags: { onGround: false, hasHorizontalCollision: false } });
        }
        if (settings.action === 'walk' || settings.action === 'bingo-walk'
          || (settings.action === 'look' && step % 10 === 0))
          result.actionPackets += 1;
        // Since 26.3 the server rejects a second position before a client tick-end boundary.
        client.write('tick_end', {});
      }, settings.action === 'bingo-walk' ? 250 : 50);
    }, 1000);
  };
  client.on('success', packet => { state.serverUuid = packet.uuid; });
  client.on('login', packet => {
    resetWorld();
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
    resetWorld();
  });
  client.on('start_configuration', resetWorld);
  client.on('position', packet => {
    const previous = position || { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
    const next = {};
    for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) {
      next[key] = packet[key] + (packet.flags?.[key] ? previous[key] : 0);
    }
    if (!Object.values(next).every(Number.isFinite)) throw new Error('invalid server position');
    position = next;
    origin = { ...next };
    step = 0;
    // Protocol 777 includes the resolved position/rotation in the confirmation itself.
    client.write('teleport_confirm', { teleportId: packet.teleportId, ...next });
    client.write('tick_end', {});
    result.teleportConfirmations += 1;
    state.teleportConfirmations = (state.teleportConfirmations || 0) + 1;
    if (!state.dead) {
      state.worldReady = true;
      startActions();
    }
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
  client.on('keep_alive', () => {
    state.keepAliveRequests = (state.keepAliveRequests || 0) + 1;
    state.lastKeepAliveAt = new Date().toISOString();
  });
  client.on('keepAliveTimeout', details => { state.keepAliveDiagnostics = details; });
  client.on('end', reason => {
    state.disconnectReason = reason;
    state.keepAliveDiagnostics = state.keepAliveDiagnostics || client._keepAliveDiagnostics || null;
    state.active = false;
    resetWorld();
  });
  return stopActions;
}

function successfulRun(states, result) {
  return states.length === result.count
    && states.every(state => state.loggedIn && state.active && state.worldReady && !state.dead)
    && result.fatalErrors.length === 0;
}

module.exports = { attachClient, successfulRun };
