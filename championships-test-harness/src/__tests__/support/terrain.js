const { Vec3 } = require('vec3');
const Column = require('@wp2508/prismarine-chunk')('26.3');
const flat = new Column();
const stone = require('minecraft-data')('26.3').blocksByName.stone.defaultState;
for (let x = 0; x < 16; x++) for (let z = 0; z < 16; z++) flat.setBlockStateId(new Vec3(x, 63, z), stone);
const flatBytes = flat.dump();
function loadGround(client, x, z) {
  client.emit('map_chunk', { x: Math.floor(x / 16), z: Math.floor(z / 16), chunkData: flatBytes });
}

module.exports = { loadGround };
