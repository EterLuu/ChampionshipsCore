const { Vec3 } = require('vec3');
const { visibleFace } = require('../mining');

// JointPlaza coordinates, relative to the lane. The status display is attached
// to the board at (9.5, 13.775, 71.95), including on copied lanes.
function readyJoint(entities, feet) {
  for (const board of entities.values()) {
    if (board.type !== 'text_display' || !/^第\s*[123]\s*\/\s*3\s*轮$/.test(board.text)) continue;
    const origin = new Vec3(Math.floor(board.x) - 9, Math.floor(board.y) - 13, Math.floor(board.z) - 71);
    const p = feet.minus(origin);
    if (p.x >= -18 && p.x < 37 && p.z >= 39 && p.z < 82 && p.y >= 1 && p.y < 15)
      return { origin, round: board.text };
  }
  return null;
}

// The tool has seven blocks of interaction reach. JointRig also checks the
// distance to the block center before accepting a break at an exposed corner.
function jointFace(world, eye, target) {
  if (eye.distanceSquared(target.offset(.5, .5, .5)) > 7.5 ** 2) return null;
  return visibleFace(world, eye, target, 7);
}

function jointApproach(target, origin) {
  const p = target.minus(origin);
  if (p.x < -1 || p.x > 4 || p.y < 2 || p.y > 8 || p.z < 60 || p.z > 66) return null;
  return {
    origin,
    canStand(node) {
      const x = node.x - origin.x, y = node.y - origin.y, z = node.z - origin.z;
      if (x < -18 || x >= 37 || z < 39 || z >= 82 || y < 1 || y >= 14) return false;
      // Both cabs have stair access from the entrance forecourt.
      if (z >= 60 && z < 67 && y < 9)
        return x < -17 || x >= 35 || (x >= -1 && x < 19);
      return true;
    },
    canMine(terrain, node) {
      return !!jointFace(terrain, new Vec3(node.x, node.y + 1.62, node.z), target);
    },
  };
}

function grayTargets(world, origin, feet, index) {
  const targets = [];
  for (let z = 60; z <= 66; z++) for (let y = 2; y <= 8; y++) {
    // Work from the open face into each column. Only an overlapping white
    // block at full travel calls for another excavation.
    for (let x = 4; x >= -1; x--) {
      const pos = origin.offset(x, y, z);
      const gray = world.getBlock(pos);
      if (gray?.name === 'air') continue;
      if (gray?.name === 'gray_concrete' && world.getBlock(pos.offset(14, 0, 0))?.name === 'white_concrete')
        targets.push(pos);
      break;
    }
  }
  const score = p => p.distanceSquared(feet) + (Math.floor((p.z - origin.z - 60) / 2) === index % 4 ? 0 : 64);
  return targets.sort((a, b) => score(a) - score(b));
}

module.exports = { readyJoint, jointFace, jointApproach, grayTargets };
