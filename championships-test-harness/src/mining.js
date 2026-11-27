// Normal left-click mining for full cube blocks in the verification course.
const { Vec3 } = require('vec3');
const packets = require('./packets');
const { rayHit } = require('./movement');

const faces = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

function unobstructed(world, eye, point, target) {
  const line = point.minus(eye);
  const distance = line.norm();
  const dir = line.scaled(1 / distance);
  const min = eye.min(point).floored(), max = eye.max(point).floored();
  for (let x = min.x; x <= max.x; x++) {
    for (let y = min.y; y <= max.y; y++) {
      for (let z = min.z; z <= max.z; z++) {
        const cell = new Vec3(x, y, z);
        if (cell.equals(target)) continue;
        const block = world.getBlock(cell);
        const shapes = block ? block.shapes : [[0, 0, 0, 1, 1, 1]];
        if (shapes.some(([x0, y0, z0, x1, y1, z1]) =>
          rayHit(eye, dir, cell.offset(x0, y0, z0), cell.offset(x1, y1, z1), distance) < distance - 1e-6)) return false;
      }
    }
  }
  return true;
}

function visibleFace(world, eye, pos, reach) {
  const center = pos.offset(.5, .5, .5);
  if (center.distanceTo(eye) > reach + Math.sqrt(3) / 2) return null;
  for (const inset of [0, .44]) {
    let closest = null;
    for (const [face, [x, y, z]] of faces.entries()) {
      const normal = new Vec3(x, y, z);
      const middle = center.plus(normal.scaled(.5));
      if (middle.minus(eye).dot(normal) >= 0) continue;
      const offsets = inset ? [-inset, 0, inset] : [0];
      for (const u of offsets) for (const v of offsets) {
        const point = middle.offset(x ? 0 : u, y ? 0 : x ? u : v, z ? 0 : v);
        const line = point.minus(eye), distance = line.norm();
        if (distance > reach || distance >= (closest?.distance ?? Infinity) || !unobstructed(world, eye, point, pos)) continue;
        closest = { face, distance, point, yaw: -Math.atan2(line.x, line.z) * 180 / Math.PI,
          pitch: -Math.atan2(line.y, Math.hypot(line.x, line.z)) * 180 / Math.PI };
      }
    }
    if (closest) return closest;
  }
  return null;
}

class BlockMining {
  constructor(client, nextSequence) {
    this.client = client;
    this.nextSequence = nextSequence;
    this.active = null;
  }

  cancel() {
    const job = this.active;
    if (job) packets.dig(this.client, job.pos, 0, job.face, 2);
    this.active = null;
  }

  // Call once per client tick after the selected tool and look have been synchronized.
  tick(pos, face, durationMs, tool, stateId) {
    let job = this.active;
    if (job && (!job.pos.equals(pos) || job.tool !== tool || job.stateId !== stateId)) {
      this.cancel();
      job = null;
    }
    if (!Number.isFinite(durationMs) || durationMs < 0) {
      this.cancel();
      return false;
    }
    if (!job) {
      packets.dig(this.client, pos, this.nextSequence(), face, 0);
      this.active = { pos, face, progress: 0, tool, stateId };
    } else {
      if (job.face !== face) {
        packets.dig(this.client, pos, 0, face, 1);
        job.face = face;
      }
      job.progress += 50 / durationMs;
    }
    const finished = durationMs === 0 || (job && job.progress >= 1 - 1e-9);
    if (finished) {
      if (job) packets.dig(this.client, pos, this.nextSequence(), face, 3);
      this.active = null;
    }
    packets.swing(this.client);
    return !!finished;
  }
}

module.exports = { BlockMining, visibleFace };
