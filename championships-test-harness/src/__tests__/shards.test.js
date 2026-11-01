const assert = require('node:assert/strict');
const test = require('node:test');
const { shardPlans, aggregateShards } = require('../shards');

const settings = { count: 18, startIndex: 4, clientsPerProcess: 8, prefix: 'autobot' };
function reports(plans) {
  return plans.map(p => ({ count: p.count, activeAtFinish: p.count, connected: p.count,
    loggedIn: p.count, success: true, exitCode: 0,
    players: Array.from({ length: p.count }, (_, i) => ({
      name: `autobot${String(p.startIndex + i).padStart(2, '0')}`,
      loggedIn: true, activeAtFinish: true, worldReady: true })) }));
}
test('shards cover the exact requested seats including a partial process and nonzero offset', () => {
  const plans = shardPlans(settings);
  assert.deepEqual(plans, [{ startIndex: 4, count: 8 }, { startIndex: 12, count: 8 },
    { startIndex: 20, count: 2 }]);
  const total = aggregateShards(settings, plans, reports(plans), 'start');
  assert.equal(total.success, true);
  assert.equal(total.activeAtFinish, 18);
  assert.equal(total.players.length, 18);
});
test('missing reports, failed exits, disconnected seats and duplicate names cannot pass aggregation', () => {
  const plans = shardPlans(settings);
  for (const change of [r => { r[1] = undefined; }, r => { r[0].exitCode = 1; },
    r => { r[0].players[0].activeAtFinish = false; },
    r => { r[0].players[0].worldReady = false; },
    r => { r[0].players[0].deadAtFinish = true; },
    r => { r[0].players[0].name = r[0].players[1].name; }]) {
    const values = reports(plans);
    change(values);
    assert.equal(aggregateShards(settings, plans, values, 'start').success, false);
  }
});
