const { Vec3 } = require('vec3');

const CARDINAL = [[1, 0], [0, 1], [-1, 0], [0, -1]];
const DIAGONAL = [[1, 1], [-1, 1], [-1, -1], [1, -1]];
const HAZARDS = new Set(['lava', 'fire', 'soul_fire', 'powder_snow', 'cactus',
  'sweet_berry_bush', 'water', 'cobweb', 'magma_block', 'campfire', 'soul_campfire']);
const key = (x, y, z) => x + ',' + y + ',' + z;
const distance = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);

// One bounded search sees one snapshot of the loaded collision shapes. The next
// search reads live blocks again, including snow, footprints and chunk changes.
class Terrain {
  constructor(world) {
    this.world = world;
    this.blocks = new Map();
  }

  block(x, y, z) {
    const id = key(x, y, z);
    if (!this.blocks.has(id)) this.blocks.set(id, this.world.getBlock(new Vec3(x, y, z)));
    return this.blocks.get(id);
  }

  getBlock(pos) { return this.block(pos.x, pos.y, pos.z); }

  clear(x, z, feet, head) {
    for (let cx = Math.floor(x - 0.299); cx <= Math.floor(x + 0.299); cx++) {
      for (let cz = Math.floor(z - 0.299); cz <= Math.floor(z + 0.299); cz++) {
        for (let y = Math.floor(feet) - 1; y < head - 0.001; y++) {
          const block = this.block(cx, y, cz);
          if (!block || (HAZARDS.has(block.name) && y + 1 > feet)) return false;
          if (block.shapes.some(([x0, y0, z0, x1, y1, z1]) => y + y1 > feet + 0.001
            && y + y0 < head - 0.001 && cx + x1 > x - 0.299 && cx + x0 < x + 0.299
            && cz + z1 > z - 0.299 && cz + z0 < z + 0.299)) return false;
        }
      }
    }
    return true;
  }

  landing(x, z, fromY) {
    const cx = Math.floor(x), cz = Math.floor(z);
    for (let y = Math.floor(fromY + 1); y >= Math.floor(fromY - 3); y--) {
      const block = this.block(cx, y, cz);
      if (!block || HAZARDS.has(block.name)) return null;
      // Use a supported center and the actual top surface (slabs, stairs, snow).
      let top = -Infinity;
      for (const [x0, , z0, x1, y1, z1] of block.shapes) {
        if (x >= cx + x0 && x <= cx + x1 && z >= cz + z0 && z <= cz + z1)
          top = Math.max(top, y + y1);
      }
      if (Number.isFinite(top) && top <= fromY + 1.001 && top >= fromY - 2.001
        && this.clear(x, z, top, top + 1.8)) return { x, y: top, z };
    }
    return null;
  }

  edge(from, dx, dz, gap = false) {
    const span = gap ? 2 : 1;
    const to = this.landing(from.x + span * dx, from.z + span * dz, from.y);
    if (!to || (gap && to.y > from.y)) return null;
    let jump = gap || to.y - from.y > 0.6;
    if (jump && !gap && (!dx || !dz)) {
      // A stair rises one block between cell centers, in two walkable half steps.
      // Sample the tread just inside the approaching edge, including its headroom.
      const tread = this.landing(to.x - dx * 0.4, to.z - dz * 0.4, from.y);
      if (tread && tread.y > from.y && tread.y - from.y <= 0.6 && to.y - tread.y <= 0.6)
        jump = false;
    }
    const high = Math.max(from.y, to.y);
    const head = jump ? from.y + 1.25 + 1.8 : high + 1.8;
    const mx = from.x + dx * 0.5, mz = from.z + dz * 0.5;
    if (!this.clear(mx, mz, high, head) || !this.clear(to.x, to.z, high, head)) return null;
    if (jump && !this.clear(from.x, from.z, from.y, head)) return null;
    if (gap && (!this.clear(from.x + dx, from.z + dz, high, head)
      || !this.clear(to.x - dx * 0.5, to.z - dz * 0.5, high, head))) return null;
    return { ...to, jump, gap };
  }
}

// A* over walkable surfaces. Return the best reachable prefix for distant
// goals so chase/escape does not wait for map-wide searches or unloaded chunks.
function route(terrain, pos, goal, avoid, exploring, approach) {
  // Position packets give the center of the player, but at a ledge the
  // supporting block can be under either foot, in an adjacent column.
  let start = null, startCost = Infinity;
  for (let x = Math.floor(pos.x - 0.299); x <= Math.floor(pos.x + 0.299); x++) {
    for (let z = Math.floor(pos.z - 0.299); z <= Math.floor(pos.z + 0.299); z++) {
      const stand = terrain.landing(x + 0.5, z + 0.5, pos.y);
      if (!stand || Math.abs(stand.y - pos.y) > 0.6) continue;
      // Prefer the same surface. A stair can have two different heights
      // within one block, connected by the normal half-block step.
      const cost = distance(stand, pos) + (Math.abs(stand.y - pos.y) > 0.06 ? 1 : 0);
      const feet = Math.max(stand.y, pos.y);
      if (cost < startCost && terrain.clear((pos.x + stand.x) / 2, (pos.z + stand.z) / 2, feet, feet + 1.8)) {
        start = stand;
        startCost = cost;
      }
    }
  }
  if (!start) return [];
  const estimate = node => distance(node, goal) + 0.7 * Math.abs(node.y - goal.y);
  const open = [], costs = new Map();
  start.cost = 0;
  start.h = estimate(start);
  start.rank = start.h;
  open.push(start);
  costs.set(key(start.x, start.y, start.z), 0);
  let best = start, detour = start, reached = false;
  // A mining stance can be on the far side of a staircase. Search the bounded
  // room once per foothold, instead of repeatedly returning a ground-level prefix.
  const visited = approach ? new Set() : null;
  for (let expanded = 0; open.length && (visited ? visited.size < 4096 : expanded < 144); expanded++) {
    let min = 0;
    for (let i = 1; i < open.length; i++) if (open[i].rank < open[min].rank) min = i;
    const node = open[min];
    open[min] = open[open.length - 1];
    open.pop();
    const id = key(node.x, node.y, node.z);
    if (node.cost !== costs.get(id) || visited?.has(id)) continue;
    visited?.add(id);
    if (node.h < best.h) best = node;
    if (exploring && distance(start, node) >= 4 && (detour === start || node.h < detour.h)) detour = node;
    if (approach ? approach.canMine(terrain, node)
      : distance(node, goal) < 0.72 && Math.abs(node.y - goal.y) <= 1.25) {
      best = node;
      reached = true;
      break;
    }
    const neighbors = [], level = new Set();
    for (const [dx, dz] of CARDINAL) {
      const next = terrain.edge(node, dx, dz) || terrain.edge(node, dx, dz, true);
      if (!next) continue;
      neighbors.push(next);
      if (!next.gap && next.y === node.y) level.add(dx + ',' + dz);
    }
    for (const [dx, dz] of DIAGONAL) {
      if (!level.has(dx + ',0') || !level.has('0,' + dz)) continue;
      const next = terrain.edge(node, dx, dz);
      if (next && next.y === node.y) neighbors.push(next);
    }
    for (const next of neighbors) {
      const id = key(next.x, next.y, next.z);
      if (avoid.has(id) || visited?.has(id) || (approach && !approach.canStand(next))) continue;
      const cost = node.cost + distance(node, next) + (next.gap ? 1.2 : next.jump ? 0.6 : 0) + 0.2 * Math.abs(next.y - node.y);
      if (cost >= (costs.get(id) ?? Infinity)) continue;
      costs.set(id, cost);
      next.cost = cost;
      next.h = estimate(next);
      // Try positions on the current level before taking the long stair route.
      next.rank = cost + 1.25 * next.h + (approach ? 5 * Math.max(0, next.y - start.y) : 0);
      next.parent = node;
      open.push(next);
    }
  }
  if (approach && !reached) return [];
  if (!approach && best === start && distance(start, goal) > 3) best = detour;
  const path = [];
  while (best.parent) { path.push(best); best = best.parent; }
  if ((approach && !path.length) || Math.floor(start.x) !== Math.floor(pos.x) || Math.floor(start.z) !== Math.floor(pos.z)) path.push(start);
  return path.reverse();
}

class GroundNavigator {
  constructor(world) {
    this.world = world;
    this.reset();
  }

  reset() {
    this.path = [];
    this.goal = null;
    this.landing = null;
    this.mode = null;
    this.avoid = new Map();
    this.retryAt = 0;
    this.reviewAt = 0;
    this.previousEdge = null;
    this.previousTick = 0;
    this.lastTick = null;
    this.escapeReview = 0;
    this.stuckSince = 0;
    this.waypointDistance = Infinity;
  }

  reposition(from, to) {
    this.landing = null;
    if (!from || distance(from, to) > 3 || Math.abs(from.y - to.y) > 2) {
      this.reset();
      return;
    }
    const next = this.path[0];
    if (next && this.lastTick != null && (distance(from, to) > 0.1 || Math.abs(from.y - to.y) > 0.1)) {
      // The attempted step did not take us where the terrain model predicted.
      // Prefer a different foothold when the server places us back at the same edge.
      const id = key(next.x, next.y, next.z);
      if (this.previousEdge === id && this.lastTick - this.previousTick < 40)
        this.avoid.set(id, this.lastTick + 60);
      this.previousEdge = id;
      this.previousTick = this.lastTick;
    }
    this.path = [];
    this.retryAt = 0;
    this.waypointDistance = Infinity;
  }

  // Villager panic selects a land destination away from a hostile, then lets
  // the ground navigator follow it. The cod game can have multiple holders.
  escapeHeading(player, threats, bearing, tick) {
    const pos = player.pos;
    const safety = point => Math.min(...threats.map(e => distance(point, e)));
    if (this.mode === 'escape' && this.goal) {
      if ((!player.onGround && this.landing) || tick < this.escapeReview
        || (this.path.length && distance(pos, this.goal) > 1.2 && safety(this.goal) > safety(pos) + 2)) {
        return this.heading(player, this.goal, 'escape', tick);
      }
    }
    if (!player.onGround) return null;
    const terrain = new Terrain(this.world), candidates = [];
    // Sample a forward fan with different radii, including both sides of the threat.
    for (let i = 0; i < 10; i++) {
      const angle = bearing + (i - 4.5) * Math.PI / 9;
      const radius = 10 + (i * 3 + Math.floor(tick / 20)) % 7;
      const x = Math.floor(pos.x + radius * Math.cos(angle)) + 0.5;
      const z = Math.floor(pos.z + radius * Math.sin(angle)) + 0.5;
      for (const dy of [0, 3, -3, 6, -6]) {
        const stand = terrain.landing(x, z, pos.y + dy);
        if (stand) { candidates.push(stand); break; }
      }
    }
    candidates.sort((a, b) => safety(b) - safety(a));
    const fallback = { x: pos.x + 12 * Math.cos(bearing), y: pos.y, z: pos.z + 12 * Math.sin(bearing) };
    this.mode = 'escape';
    this.goal = fallback;
    this.path = [];
    for (const [id, expiry] of this.avoid) if (expiry <= tick) this.avoid.delete(id);
    let best = -Infinity;
    for (const candidate of candidates.length ? candidates.slice(0, 3) : [fallback]) {
      const path = route(terrain, pos, candidate, this.avoid, true);
      const end = path.at(-1);
      if (!end) continue;
      const rank = safety(end) - 0.15 * end.cost;
      if (rank > best) {
        this.goal = { x: end.x, y: end.y, z: end.z };
        this.path = path;
        best = rank;
      }
      if (distance(end, candidate) < 1 && safety(end) > safety(pos) + 3) break;
    }
    this.escapeReview = tick + 8;
    this.retryAt = this.path.length ? 0 : this.escapeReview;
    this.waypointDistance = Infinity;
    this.stuckSince = tick;
    return this.heading(player, this.goal, 'escape', tick);
  }

  heading(player, goal, mode, tick, approach) {
    this.lastTick = tick;
    const pos = player.pos;
    const waypoint = this.path[0];
    if (approach && waypoint && (waypoint.y - pos.y > 1.25 || distance(pos, waypoint) > 2.75)) {
      // Knocked off the planned surface. Replan from the actual landing, without
      // blacklisting the stair we will need to climb again.
      this.path = [];
      this.landing = null;
      this.waypointDistance = Infinity;
      this.retryAt = 0;
    }
    // Finish an airborne step even if the holder or nearest loot changes mid-jump.
    if (this.landing && !player.onGround) {
      const d = distance(pos, this.landing);
      if (d < 0.22) return null;
      return { angle: Math.atan2(this.landing.z - pos.z, this.landing.x - pos.x), jump: false, sprint: player.control.sprint };
    }
    this.landing = null;
    // A moving melee target can shift every tick. Follow the current route
    // until it moves meaningfully and the next chase review is due.
    const shifted = this.goal && (distance(goal, this.goal) >= 1.25 || Math.abs(goal.y - this.goal.y) > 1);
    if (!this.goal || mode !== this.mode || (shifted && (!this.path.length || tick >= this.reviewAt))) {
      this.path = [];
      this.retryAt = 0;
      this.goal = { ...goal };
      this.mode = mode;
      this.waypointDistance = Infinity;
    }
    while (this.path.length && distance(pos, this.path[0]) < (approach && this.path.length === 1 ? 0.12 : 0.43)
      && Math.abs(pos.y - this.path[0].y) < 0.61) {
      this.path.shift();
      this.waypointDistance = Infinity;
    }
    if (this.path.length && player.onGround) {
      const next = this.path[0], d = distance(pos, next);
      if (d < this.waypointDistance - 0.12) {
        this.waypointDistance = d;
        this.stuckSince = tick;
      } else if (tick - this.stuckSince >= 16) {
        this.avoid.set(key(next.x, next.y, next.z), tick + 50);
        this.path = [];
        this.retryAt = 0;
      }
      // Discard an edge if the live terrain changed since the plan was made.
      if (this.path.length) {
        const live = new Terrain(this.world).landing(next.x, next.z, next.y);
        if (!live || live.y !== next.y) { this.path = []; this.retryAt = 0; }
      }
    }
    if (!this.path.length && tick >= this.retryAt && player.onGround) {
      for (const [id, expiry] of this.avoid) if (tick >= expiry) this.avoid.delete(id);
      this.path = route(new Terrain(this.world), pos, goal, this.avoid, mode === 'escape' || mode === 'roam', approach);
      this.goal = { ...goal };
      this.reviewAt = tick + (distance(pos, goal) > 16 ? 10 : 5);
      this.retryAt = this.path.length ? 0 : tick + 8;
      this.waypointDistance = Infinity;
      this.stuckSince = tick;
    }
    // A sprint jump needs a run-up. Look ahead along a straight approach so
    // the player clears the lip instead of starting a jump against its face.
    const first = this.path[0], second = this.path[1];
    if (first && second?.jump && !first.jump && player.onGround && player.control.sprint) {
      const dx = second.x - first.x, dz = second.z - first.z;
      const along = player.vel.x * dx + player.vel.z * dz;
      const inLine = Math.abs((pos.x - first.x) * dz - (pos.z - first.z) * dx) < 0.25;
      if (inLine && along > 0.1 && distance(pos, second) < (second.gap ? 2.4 : 2.1))
        this.path.shift();
    }
    const next = this.path[0];
    if (!next) return null;
    const jump = !!(next.jump && player.onGround && (next.gap || next.y > pos.y + 0.6));
    if (jump) this.landing = next;
    const dx = next.x - pos.x, dz = next.z - pos.z;
    const following = this.path[1];
    let sprint = !!next.gap;
    if (following && !sprint) {
      const fx = following.x - next.x, fz = following.z - next.z;
      const straight = dx * fx + dz * fz > 0.6 * Math.hypot(dx, dz) * Math.hypot(fx, fz);
      sprint = straight && next.y >= pos.y - 0.6;
    }
    return { angle: Math.atan2(dz, dx), jump, sprint };
  }
}

module.exports = { GroundNavigator };
