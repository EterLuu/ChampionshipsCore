const { Vec3 } = require('vec3');
const { BlockMining, visibleFace } = require('../mining');
const { readyJoint, jointFace, jointApproach, grayTargets } = require('./verification-joint');

function prop(stack) {
  const data = stack?.components?.find(component => component.type === 'custom_data')?.data;
  return data?.value?.PublicBukkitValues?.value?.['championshipscore:robot_prop']?.value;
}

// The warehouse and the joint issue separate picks. Both can occupy any hotbar slot.
class VerificationPickaxe {
  constructor(movement, state, selectHeldSlot, nextSequence) {
    this.movement = movement;
    this.state = state;
    this.selectHeldSlot = selectHeldSlot;
    this.mining = new BlockMining(movement.client, nextSequence);
    this.target = null;
    this.clock = 0;
    this.retry = new Map();
    this.nextScan = 0;
    const reset = () => {
      this.retry.clear();
      this.nextScan = 0;
      this.board = null;
      this.mining.active = null;
      this.target = null;
    };
    for (const event of ['login', 'respawn', 'start_configuration', 'end'])
      movement.client.on(event, reset);
    movement.client.on('position', () => {
      // Movement applies the server correction and invalidates the old path.
      // Keep a joint assignment until its tool, lane or actual block changes.
      this.mining.active = null;
      if (this.target?.approach) {
        this.target.walking = true;
        this.target.noRouteSince = null;
      } else this.target = null;
    });
  }

  cancel() {
    this.mining.cancel();
    if (this.target?.walking) {
      this.movement.beginDecision();
      this.movement.navigator.reset();
    }
    this.target = null;
  }

  pickSlot(room) {
    return this.state.hotbar.findIndex(stack => stack?.name === 'iron_pickaxe' && prop(stack)?.startsWith(room + '_PICK/'));
  }

  start(position, room, origin) {
    this.cancel();
    if (room !== 'WAREHOUSE' && room !== 'JOINT') return false;
    const slot = this.pickSlot(room);
    if (slot < 0) return false;
    const pos = new Vec3(position.x, position.y, position.z);
    const block = this.movement.getBlock(pos);
    if (!block?.diggable || (room === 'JOINT' && block.name !== 'gray_concrete')) return false;
    const tool = prop(this.state.hotbar[slot]);
    this.target = { pos, slot, tool, stateId: block.stateId, reach: room === 'JOINT' ? 7 : 4.5,
      world: this.movement.worldName };
    if (room === 'JOINT' && this.movement.player) {
      const lane = origin ?? new Vec3(Math.round(pos.x / 256) * 256, 80, 0);
      this.target.approach = jointApproach(pos, lane);
    }
    if (this.state.heldSlot !== slot) this.selectHeldSlot(slot);
    return true;
  }

  // Automatic game profile. Explicit scenario drivers can still use start/tick
  // for an assigned block, including one that requires the forklift stairs.
  workJoint(position, index, tick) {
    const feet = this.movement.player?.pos;
    const board = feet && this.pickSlot('JOINT') >= 0 && readyJoint(this.state.entities, feet);
    if (!board || this.board?.round !== board.round || !this.board.origin.equals(board.origin)) {
      this.cancel();
      this.retry.clear();
      this.nextScan = 0;
      this.board = board;
    }
    if (!board) return;
    if (this.target && this.movement.getBlock(this.target.pos.offset(14, 0, 0))?.name !== 'white_concrete')
      this.cancel();
    if (!this.target) {
      if (tick < this.nextScan) return;
      this.nextScan = tick + 5;
      const eye = feet.offset(0, 1.62, 0);
      const pending = grayTargets(this.movement, board.origin, feet, index)
        .filter(p => tick >= (this.retry.get(p.toString()) ?? 0));
      const target = pending.find(p => jointFace(this.movement, eye, p)) ?? pending[0];
      if (!target || !this.start(target, 'JOINT', board.origin)) return;
      this.target.automatic = true;
    }
    const block = this.target.pos;
    const action = this.tick(position);
    // Wait for a server block update after a finish, or try another column if
    // its face or route is obstructed. Never mark a local prediction as air.
    if (!this.target) this.retry.set(block.toString(), tick + 16);
    return action;
  }

  tick(position) {
    this.clock++;
    const target = this.target;
    if (!target) return;
    const { movement, state } = this;
    const { pos, slot, tool, reach, stateId } = target;
    const block = movement.getBlock(pos);
    const stack = state.hotbar[slot];
    if (!movement.player || target.world !== movement.worldName || state.heldSlot !== slot ||
        prop(stack) !== tool || block?.stateId !== stateId) {
      this.cancel();
      return;
    }
    if (target.approach) {
      const p = movement.player.pos.minus(target.approach.origin);
      if (p.x < -19 || p.x >= 38 || p.z < 39 || p.z >= 82) { this.cancel(); return; }
    }
    const eye = movement.player.pos.offset(0, movement.keys.shift ? 1.27 : 1.62, 0);
    const hit = target.approach ? jointFace(movement, eye, pos) : visibleFace(movement, eye, pos, reach);
    if (!hit || (target.walking && !movement.player.onGround)) {
      this.mining.cancel();
      if (!target.approach) { this.cancel(); return; }
      target.walking = true;
      movement.beginDecision();
      movement.wantsSprint = false;
      const nav = movement.navigator;
      const way = nav.heading(movement.player, pos, 'joint:' + pos.toString(), this.clock, target.approach);
      if (!way) {
        if (movement.player.onGround && !nav.path.length) {
          target.noRouteSince ??= this.clock;
          // An automatic miner can switch columns after several failed replans.
          // An explicit assignment is retried until it is cancelled or becomes invalid.
          if (target.automatic && this.clock - target.noRouteSince >= 24) this.cancel();
        }
        return;
      }
      target.noRouteSince = null;
      const feet = movement.player.pos;
      movement.write('position_look', { x: feet.x + Math.cos(way.angle), z: feet.z + Math.sin(way.angle),
        yaw: way.angle * 180 / Math.PI - 90, pitch: 0 });
      movement.write('player_input', { inputs: { jump: way.jump } });
      return 'move';
    }
    if (target.walking) {
      movement.beginDecision();
      movement.navigator.reset();
      target.walking = false;
    }
    if (!movement.player.onGround && target.approach) return;
    movement.write('look', hit);
    const turn = ((position.yaw - hit.yaw) % 360 + 540) % 360 - 180;
    if (Math.abs(turn) > .1 || Math.abs(position.pitch - hit.pitch) > .1) {
      this.mining.cancel();
      return 'look';
    }
    const duration = block.digTime(stack.itemId, false, movement.player.isInWater, !movement.player.onGround);
    const started = !this.mining.active;
    const finished = this.mining.tick(pos, hit.face, duration, tool, stateId);
    if (finished) this.target = null;
    return finished || (started && this.mining.active) ? 'block-dig' : 'swing';
  }
}

module.exports = { VerificationPickaxe };
