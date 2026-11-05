const test = require('node:test');
const assert = require('node:assert/strict');
const protocol = require('@wp2508/minecraft-protocol');
const packets = require('../packets');

test('common action helpers serialize on protocol 26.3', () => {
  const serializer = protocol.createSerializer({ state: 'play', isServer: false, version: '26.3' });
  const writes = [];
  const client = { write: (name, params) => {
    writes.push({ name, params });
    serializer.createPacketBuffer({ name, params });
  } };
  packets.position(client, { x: 1, y: 64, z: 2 });
  packets.positionLook(client, { x: 1, y: 64, z: 2, yaw: 90, pitch: 0 });
  packets.look(client, 90, 0);
  packets.input(client, { forward: true, jump: true });
  packets.sprint(client, 1, true);
  packets.swing(client, 1);
  packets.useItem(client, 2);
  packets.attack(client, 3);
  packets.useEntity(client, 3);
  packets.selectHotbar(client, 0);
  packets.dig(client, { x: 1, y: 64, z: 2 }, 3);
  packets.place(client, { x: 1, y: 64, z: 2 }, 4);
  packets.interactBlock(client, { x: 1, y: 64, z: 2 }, 5);
  packets.clickWindow(client, { windowId: 2, stateId: 3 }, 4);
  assert.deepEqual(writes.find(write => write.name === 'punch').params, {});
  assert.equal(writes.filter(write => write.name === 'use_item').length, 1);
  assert.equal(writes.filter(write => write.name === 'block_place').length, 2);
});
