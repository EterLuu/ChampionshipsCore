const test = require('node:test');
const assert = require('node:assert/strict');
const { waitForWindow } = require('../window');

function fixture() {
  let deadline, poll, present = false;
  const cleared = [];
  const timers = {
    setTimeout(callback) { deadline = callback; return 'deadline'; },
    setInterval(callback) { poll = callback; return 'poll'; },
    clearTimeout(id) { cleared.push(id); },
    clearInterval(id) { cleared.push(id); },
  };
  return { timers, cleared, exists: () => present, present: () => { present = true; },
    deadline: () => deadline(), poll: () => poll() };
}

test('local completion preserves a separate deadline and clears both timers', async () => {
  const f = fixture();
  const result = waitForWindow(10000, '/tmp/finish', f.timers, f.exists);
  f.present(); f.poll();
  assert.equal(await result, 'control-file');
  assert.deepEqual(f.cleared, ['deadline', 'poll']);
});

test('missing control completion is a deadline, while ordinary hold remains duration', async () => {
  for (const path of [null, '/tmp/finish']) {
    const f = fixture();
    const result = waitForWindow(10000, path, f.timers, f.exists);
    f.deadline();
    assert.equal(await result, path ? 'deadline' : 'duration');
  }
});

test('stale local files cannot finish a new run successfully', async () => {
  const f = fixture(); f.present();
  await assert.rejects(waitForWindow(10000, '/tmp/finish', f.timers, f.exists), /already exists/);
  assert.deepEqual(f.cleared, []);
});

test('an indefinite window requires a control file', async () => {
  const f = fixture();
  await assert.rejects(waitForWindow(Infinity, null, f.timers, f.exists), /indefinite window requires a stop file/);
  assert.deepEqual(f.cleared, []);
});
