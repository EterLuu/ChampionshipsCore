const { Vec3 } = require('vec3');
const { GroundNavigator } = require('./navigation');
const { getAttributeValue } = require('@wp2508/prismarine-physics/lib/attribute');
const { Physics, PlayerState } = require('@wp2508/prismarine-physics');

// Return the first distance along a unit ray that crosses a box.
function rayHit(origin, direction, lower, upper, limit) {
  let near = 0, far = limit;
  for (const k of ['x', 'y', 'z']) {
    const d = direction[k];
    if (Math.abs(d) < 1e-9) {
      if (origin[k] < lower[k] || origin[k] > upper[k]) return Infinity;
      continue;
    }
    const a = (lower[k] - origin[k]) / d, b = (upper[k] - origin[k]) / d;
    near = Math.max(near, Math.min(a, b));
    far = Math.min(far, Math.max(a, b));
    if (near > far) return Infinity;
  }
  return near;
}

// Profiles choose actions at game-specific intervals. The wire client resolves their
// movement against the current dimension once per Minecraft tick, including idle ticks.
class ClientMovement {
  constructor(client, version) {
    this.client = client;
    this.version = version;
    const data = require('minecraft-data')(version);
    this.effectFields = new Map(['JumpBoost', 'SlowFalling', 'Levitation', 'DolphinsGrace', 'Speed', 'Slowness']
      .map(name => [data.effectsByName[name].id, name[0].toLowerCase() + name.slice(1)]));
    this.Chunk = require('@wp2508/prismarine-chunk')(version);
    this.physics = Physics(data, this);
    // EntityDimensions stores floats. A decimal 0.3 half-width lets the player's
    // box overlap a wall when the server rebuilds it from a reported foot position.
    this.physics.playerHalfWidth = Math.fround(data.entitiesByName.player.width) / 2;
    this.physics.playerHeight = Math.fround(data.entitiesByName.player.height);
    this.columns = new Map();
    this.navigator = new GroundNavigator(this);
    const defaults = data.loginPacket.dimensionCodec['minecraft:dimension_type'].entries;
    this.dimensions = defaults;
    client.on('registry_data', packet => {
      if (packet.id === 'minecraft:dimension_type') {
        this.dimensions = packet.entries.map(entry => entry.value ? entry : defaults.find(d => d.key === entry.key));
      }
    });
    this.changeWorld();
    client.on('login', packet => {
      this.entityId = packet?.entityId;
      this.changeWorld(packet?.worldState);
    });
    client.on('respawn', packet => {
      if (packet?.worldState?.name !== this.worldName || packet?.worldState?.dimension !== this.dimension)
        this.changeWorld(packet?.worldState);
      else {
        this.player = null;
        this.useHand = null;
        this.pendingAction = null;
        this.navigator.reset();
        if (!(packet?.copyMetadata & 2)) this.effects = {};
      }
    });
    client.on('start_configuration', () => { this.changeWorld(); this.dimensions = defaults; });
    client.on('end', () => this.changeWorld());
    client.on('map_chunk', p => this.columns.set(this.key(p.x, p.z), { bytes: p.chunkData }));
    client.on('chunk_batch_finished', () => client.write('chunk_batch_received', { chunksPerTick: 2 }));
    client.on('unload_chunk', p => this.columns.delete(this.key(p.chunkX, p.chunkZ)));
    client.on('block_change', p => this.updateBlock(p.location, p.type));
    client.on('multi_block_change', ({ chunkCoordinates: c, records }) => {
      if (!c) return;
      for (const v of records) {
        this.updateBlock(new Vec3(c.x * 16 + ((v >> 8) & 15), c.y * 16 + (v & 15),
          c.z * 16 + ((v >> 4) & 15)), v >>> 12);
      }
    });
    client.on('entity_update_attributes', p => {
      if (p.entityId !== this.entityId) return;
      for (const attr of p.properties) {
        if (attr.key === 'generic.movement_speed') this.attributes[this.physics.movementSpeedAttribute] = attr;
        if (attr.key === 'generic.gravity') this.physics.gravity = getAttributeValue(attr);
        if (attr.key === 'generic.step_height') this.physics.stepHeight = getAttributeValue(attr);
      }
    });
    client.on('entity_metadata', p => {
      if (p.entityId !== this.entityId) return;
      const flags = p.metadata.find(entry => entry.key === 8 && entry.type === 'byte');
      if (flags) this.useHand = (flags.value & 1) ? (flags.value & 2 ? 'off' : 'main') : null;
    });
    client.on('entity_effect', p => {
      if (p.entityId === this.entityId && this.effectFields.has(p.effectId))
        this.effects[p.effectId] = { amplifier: p.amplifier, duration: p.duration };
    });
    client.on('remove_entity_effect', p => {
      if (p.entityId === this.entityId) delete this.effects[p.effectId];
    });
    client.on('entity_velocity', p => {
      if (this.player && p.entityId === this.entityId && p.velocity)
        this.player.vel.set(p.velocity.x, p.velocity.y, p.velocity.z);
    });
    client.on('explosion', p => {
      if (this.player && p.playerKnockback) {
        const { x, y, z } = p.playerKnockback;
        this.player.vel.translate(x, y, z);
      }
    });
    client.on('abilities', p => {
      this.flying = (p.flags & 2) !== 0;
      this.flySpeed = p.flyingSpeed;
    });
  }

  changeWorld(world) {
    this.navigator.reset();
    this.columns.clear();
    this.player = null;
    this.attributes = {};
    this.effects = {};
    this.physics.gravity = 0.08;
    this.physics.stepHeight = 0.6;
    this.leatherBoots = false;
    this.useHand = null;
    this.fallDistance = 0;
    this.keys = {};
    this.sprinting = false;
    this.pendingAction = null;
    this.flying = false;
    this.flySpeed = 0.05;
    this.worldName = world?.name;
    this.dimension = world?.dimension;
    const type = this.dimensions[this.dimension ?? 0]?.value?.value;
    this.bounds = type && { minY: type.min_y.value, worldHeight: type.height.value };
  }

  key(x, z) { return x + ',' + z; }

  column(x, z) {
    const entry = this.columns.get(this.key(Math.floor(x / 16), Math.floor(z / 16)));
    if (!entry || !this.bounds) return null;
    if (!entry.chunk) {
      entry.chunk = new this.Chunk(this.bounds);
      entry.chunk.load(entry.bytes);
      delete entry.bytes;
    }
    return entry.chunk;
  }

  getBlock(point) {
    const pos = point.floored();
    const block = this.column(pos.x, pos.z)?.getBlock(new Vec3(pos.x & 15, pos.y, pos.z & 15));
    if (block) {
      block.position = pos;
      // PowderSnowBlock's collision shape depends on the entity and the direction of entry.
      if (block.name === 'powder_snow' && this.player) {
        if (this.fallDistance > 2.5) block.shapes = [[0, 0, 0, 1, 0.9, 1]];
        else if (this.leatherBoots && !this.keys.shift && this.player.pos.y >= pos.y + 1 - 1e-5)
          block.shapes = [[0, 0, 0, 1, 1, 1]];
      }
    }
    return block;
  }

  // Check the next couple of steps for a solid floor and headroom. Try a
  // diagonal or a turn along the wall when the straight approach is blocked.
  walkingHeading(angle, side) {
    const { pos, onGround } = this.player;
    if (!onGround) return { angle, jump: false };
    for (const offset of [0, 0.55, -0.55, 1.05, -1.05, 1.57, -1.57, 2.3, -2.3, Math.PI]) {
      const heading = angle + side * offset;
      let floor = Math.floor(pos.y + 0.01) - 1;
      let jump = false;
      let clear = true;
      for (const distance of [0.8, 1.6]) {
        const x = pos.x + Math.cos(heading) * distance, z = pos.z + Math.sin(heading) * distance;
        const solid = y => this.getBlock(new Vec3(x, y, z))?.shapes.length;
        let y = floor + 1;
        while (y >= floor - 2 && !solid(y)) y--;
        if (y < floor - 2 || solid(y + 1) || solid(y + 2)) { clear = false; break; }
        jump ||= y > floor;
        floor = y;
      }
      if (clear) return { angle: heading, jump };
    }
    return null;
  }

  crosshairIntersection(target, entities) {
    if (entities.get(target.entityId) !== target || !this.player) return null;
    const eye = this.player.pos.offset(0, this.keys.shift ? 1.27 : 1.62, 0);
    const yaw = this.lastYaw * Math.PI / 180, pitch = this.lastPitch * Math.PI / 180;
    const dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    const distance = e => {
      const hitbox = e.type === 'interaction';
      const r = hitbox ? 0.5 : 0.3;
      return rayHit(eye, dir, new Vec3(e.x - r, e.y, e.z - r),
        new Vec3(e.x + r, e.y + (hitbox ? 1 : 1.8), e.z + r), 3);
    };
    const reach = distance(target);
    if (!Number.isFinite(reach)) return null;
    for (const e of entities.values()) {
      if (e !== target && ['interaction', 'mannequin', 'player'].includes(e.type) && distance(e) < reach) return null;
    }
    const end = eye.plus(dir.scaled(reach));
    for (let x = Math.floor(Math.min(eye.x, end.x)); x <= Math.floor(Math.max(eye.x, end.x)); x++) {
      for (let y = Math.floor(Math.min(eye.y, end.y)); y <= Math.floor(Math.max(eye.y, end.y)); y++) {
        for (let z = Math.floor(Math.min(eye.z, end.z)); z <= Math.floor(Math.max(eye.z, end.z)); z++) {
          const pos = new Vec3(x, y, z), block = this.getBlock(pos);
          if (!block || block.shapes.some(([x0, y0, z0, x1, y1, z1]) =>
            rayHit(eye, dir, pos.offset(x0, y0, z0), pos.offset(x1, y1, z1), reach) < reach)) return null;
        }
      }
    }
    return end.minus(new Vec3(target.x, target.y, target.z));
  }

  updateBlock(pos, type) {
    if (pos && Number.isInteger(type))
      this.column(pos.x, pos.z)?.setBlockStateId(new Vec3(pos.x & 15, pos.y, pos.z & 15), type);
  }

  teleport(pos, packet) {
    this.navigator.reposition(this.player?.pos, pos);
    let previous = this.player?.vel;
    if (previous && packet.flags?.yawDelta) {
      // PositionMoveRotation.calculateAbsolute rotates retained velocity into the new view.
      const pitch = (this.lastPitch - pos.pitch) * Math.PI / 180;
      const yaw = (this.lastYaw - pos.yaw) * Math.PI / 180;
      const y = previous.y * Math.cos(pitch) + previous.z * Math.sin(pitch);
      const z = previous.z * Math.cos(pitch) - previous.y * Math.sin(pitch);
      previous = new Vec3(previous.x * Math.cos(yaw) + z * Math.sin(yaw), y,
        z * Math.cos(yaw) - previous.x * Math.sin(yaw));
    }
    const velocity = new Vec3(...['x', 'y', 'z'].map(k =>
      (packet['d' + k] || 0) + (packet.flags?.['d' + k] ? previous?.[k] || 0 : 0)));
    if (this.player) {
      // Vanilla corrects the existing LocalPlayer. Starting a fresh physics body here
      // drops ground contact and the jump timer, even for a position-only correction.
      this.player.pos.set(pos.x, pos.y, pos.z);
      this.player.vel = velocity;
    } else {
      this.fallDistance = 0;
      this.player = new PlayerState({
        version: this.version, inventory: { slots: [] }, jumpTicks: 0, fireworkRocketDuration: 0,
        entity: { position: new Vec3(pos.x, pos.y, pos.z), velocity, effects: this.effects,
          attributes: this.attributes, onGround: false },
      }, { forward: false, back: false, left: false, right: false, jump: false, sprint: false, sneak: false });
    }
    this.lastSent = this.player.pos.clone();
    this.lastYaw = Math.fround(pos.yaw);
    this.lastPitch = Math.fround(pos.pitch);
    this.lastCollision = !!this.player.isCollidedHorizontally;
    this.lastGround = !!this.player.onGround;
    this.destination = null;
    this.wantsSprint = false;
    this.pendingAction = null;
    this.silentTicks = 0;
    this.intent = {};
  }

  rotate(position, packet) {
    position.yaw = Math.fround(packet.yaw + (packet.relativeYaw ? position.yaw : 0));
    position.pitch = Math.fround(Math.max(-90, Math.min(90,
      packet.pitch + (packet.relativePitch ? position.pitch : 0))));
    this.lastYaw = position.yaw;
    this.lastPitch = position.pitch;
    delete this.intent.look;
    this.pendingAction = null;
    this.client.write('look', { yaw: position.yaw, pitch: position.pitch,
      flags: { onGround: false, hasHorizontalCollision: false } });
  }

  beginDecision() {
    this.destination = null;
  }

  write(name, data) {
    if (name === 'position' || name === 'position_look') {
      this.destination = { x: data.x - this.player.pos.x, z: data.z - this.player.pos.z };
    }
    if (name === 'look' || name === 'position_look') this.intent.look = data;
    if (name === 'look' || name === 'position' || name === 'position_look') return;
    if (name === 'player_input') { Object.assign(this.intent, data.inputs); return; }
    if (name === 'entity_action' && ['start_sprinting', 'stop_sprinting'].includes(data.actionId)) {
      this.wantsSprint = data.actionId === 'start_sprinting';
      return;
    }
    if (name === 'use_item' && this.player)
      data = { ...data, rotation: { x: this.lastYaw, y: this.lastPitch } };
    this.client.write(name, data);
  }

  tick(position, mainHand, offHand) {
    const { look, jump, shift, afterMove } = this.intent;
    this.intent = {};
    const player = this.player;
    if (look) {
      const turn = ((look.yaw - position.yaw) % 360 + 540) % 360 - 180;
      position.yaw = Math.fround(position.yaw + turn);
      position.pitch = Math.fround(Math.max(-90, Math.min(90, look.pitch)));
    }
    const yaw = position.yaw * Math.PI / 180;
    const point = this.destination;
    const dx = point?.x ?? 0, dz = point?.z ?? 0;
    const forward = dz * Math.cos(yaw) - dx * Math.sin(yaw);
    const right = -dx * Math.cos(yaw) - dz * Math.sin(yaw);
    const deadZone = Math.max(0.02, Math.hypot(dx, dz) * Math.sin(Math.PI / 8));
    const usedStack = this.useHand === 'off' ? offHand : mainHand;
    const useEffects = usedStack?.components?.find(c => c.type === 'use_effects')?.data;
    const inputScale = this.useHand ? useEffects?.speed_multiplier ?? 0.2 : 1;
    const canSprint = !this.useHand || useEffects?.can_sprint === true;
    const keys = { forward: forward > deadZone, backward: forward < -deadZone,
      right: right > deadZone, left: right < -deadZone, jump: !!jump,
      shift: !!shift, sprint: canSprint && this.wantsSprint && forward > deadZone && !shift };
    if (Object.keys(keys).some(k => keys[k] !== !!this.keys[k])) {
      this.client.write('player_input', { inputs: keys });
      this.keys = keys;
    }
    if (keys.sprint !== this.sprinting) {
      this.client.write('entity_action', { entityId: this.entityId,
        actionId: keys.sprint ? 'start_sprinting' : 'stop_sprinting', jumpBoost: 0 });
      this.sprinting = keys.sprint;
    }
    player.yaw = Math.PI - yaw;
    player.pitch = -position.pitch * Math.PI / 180;
    Object.assign(player.control, { forward: Number(keys.forward) * inputScale, back: Number(keys.backward) * inputScale,
      left: Number(keys.left) * inputScale, right: Number(keys.right) * inputScale, jump: keys.jump, sprint: keys.sprint, sneak: keys.shift });
    for (const [id, field] of this.effectFields) {
      const effect = this.effects[id];
      player[field] = effect ? effect.amplifier + 1 : 0;
      if (effect && effect.duration > 0 && --effect.duration === 0) delete this.effects[id];
    }
    const startY = player.pos.y;
    const dest = player.pos.plus(player.vel);
    const available = [-0.3, 0.3].every(x => [-0.3, 0.3].every(z =>
      this.column(player.pos.x + x, player.pos.z + z) && this.column(dest.x + x, dest.z + z)));
    if (available) {
      if (this.flying) {
        // Flight authorized by the server uses the same terrain collision step.
        // Vanilla preserves the incoming vertical velocity with 0.6 drag after travel.
        player.vel.y += (Number(keys.jump) - Number(keys.shift)) * this.flySpeed * 3;
        const vertical = player.vel.y, acceleration = this.physics.airborneAcceleration;
        player.control.jump = false;
        this.physics.airborneAcceleration = keys.sprint ? (2 / 1.3) * this.flySpeed : this.flySpeed;
        try { this.physics.simulatePlayer(player, this); }
        finally { this.physics.airborneAcceleration = acceleration; }
        player.vel.y = player.isCollidedVertically ? 0 : vertical * 0.6;
      } else this.physics.simulatePlayer(player, this);
    }
    if (player.onGround || player.isInWater || this.flying) this.fallDistance = 0;
    else this.fallDistance += Math.max(0, startY - player.pos.y);
    Object.assign(position, { x: player.pos.x, y: player.pos.y, z: player.pos.z });
    const flags = { onGround: !!player.onGround, hasHorizontalCollision: !!player.isCollidedHorizontally };
    this.silentTicks++;
    const moved = player.pos.distanceSquared(this.lastSent) > 4e-8 || this.silentTicks >= 20;
    const rotated = position.yaw !== this.lastYaw || position.pitch !== this.lastPitch;
    if (moved || rotated || flags.onGround !== this.lastGround || flags.hasHorizontalCollision !== this.lastCollision) {
      const packet = moved ? (rotated ? 'position_look' : 'position') : rotated ? 'look' : 'flying';
      this.client.write(packet, { ...position, flags });
      if (moved) { this.lastSent = player.pos.clone(); this.silentTicks = 0; }
      this.lastGround = flags.onGround;
      this.lastCollision = flags.hasHorizontalCollision;
      this.lastYaw = position.yaw;
      this.lastPitch = position.pitch;
    }
    this.pendingAction = afterMove;
  }
}

module.exports = { ClientMovement, rayHit };
