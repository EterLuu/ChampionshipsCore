/**
 * Small, protocol-version-local helpers for client actions.
 * Profiles call these helpers instead of constructing packet payloads directly.
 * Every helper returns the packet name so tests can inspect an action plan without
 * opening a network connection.
 */

function write(client, name, params = {}) {
  client.write(name, params);
  return { name, params };
}

function movementFlags(onGround = false, hasHorizontalCollision = false) {
  return { onGround, hasHorizontalCollision };
}

function position(client, point, onGround = false) {
  return write(client, 'position', {
    x: point.x,
    y: point.y,
    z: point.z,
    flags: movementFlags(onGround, false),
  });
}

function positionLook(client, point, onGround = false) {
  return write(client, 'position_look', {
    x: point.x,
    y: point.y,
    z: point.z,
    yaw: point.yaw,
    pitch: point.pitch,
    flags: movementFlags(onGround, false),
  });
}

function look(client, yaw, pitch = 0, onGround = false) {
  return write(client, 'look', {
    yaw,
    pitch,
    flags: movementFlags(onGround, false),
  });
}

function input(client, inputs) {
  return write(client, 'player_input', { inputs });
}

function sprint(client, entityId, enabled = true) {
  return write(client, 'entity_action', {
    entityId,
    actionId: enabled ? 'start_sprinting' : 'stop_sprinting',
    jumpBoost: 0,
  });
}

function swing(client, sequence = 0) {
  // Java protocol 26.3 names the client-to-server arm swing packet `punch`.
  // `use_item` is a right-click interaction and can trigger gameplay, so keep
  // the two inputs distinct for profiles that only need a hand swing.
  return write(client, 'punch');
}

function useItem(client, sequence, hand = 0, rotation = { x: 0, y: 0 }) {
  return write(client, 'use_item', { hand, sequence, rotation });
}

function attack(client, entityId) {
  if (!Number.isInteger(entityId)) return null;
  return write(client, 'attack', { entityId });
}

function useEntity(client, entityId, location = { x: 0, y: 0, z: 0 }, hand = 0) {
  if (!Number.isInteger(entityId)) return null;
  return write(client, 'use_entity', {
    target: entityId,
    hand,
    location,
    sneaking: false,
  });
}

function selectHotbar(client, slot) {
  if (!Number.isInteger(slot) || slot < 0 || slot > 8) return null;
  return write(client, 'held_item_slot', { slotId: slot });
}

function dig(client, location, sequence, face = 1, status = 0) {
  return write(client, 'block_dig', { status, location, face, sequence });
}

function place(client, location, sequence, direction = 1, hand = 0) {
  return write(client, 'block_place', {
    hand,
    hitResult: {
      location,
      direction,
      cursorX: 0.5,
      cursorY: 0.5,
      cursorZ: 0.5,
      insideBlock: false,
      worldBorderHit: false,
    },
    sequence,
  });
}

function interactBlock(client, location, sequence, direction = 1, hand = 0) {
  return place(client, location, sequence, direction, hand);
}

function clickWindow(client, window, slot, button = 0) {
  if (!window || !Number.isInteger(window.windowId) || !Number.isInteger(slot)) return null;
  return write(client, 'window_click', {
    windowId: window.windowId,
    stateId: Number.isInteger(window.stateId) ? window.stateId : 0,
    slot,
    mouseButton: button,
    mode: 0,
    changedSlots: [],
    cursorItem: null,
  });
}

module.exports = {
  write,
  movementFlags,
  position,
  positionLook,
  look,
  input,
  sprint,
  swing,
  useItem,
  attack,
  useEntity,
  selectHotbar,
  dig,
  place,
  interactBlock,
  clickWindow,
};
