const test = require('node:test');
const assert = require('node:assert/strict');
const { PHASES, PhaseTracker, packetText } = require('../phase');

test('phase tracker translates server text and keeps an auditable transition history', () => {
  const tracker = new PhaseTracker();
  assert.equal(tracker.phase, PHASES.WAITING);
  assert.equal(tracker.observe('system_chat', { content: 'Map voting starts now' }).to, PHASES.VOTING);
  assert.equal(tracker.observe('system_chat', { content: 'Preparation phase' }).to, PHASES.PREPARATION);
  assert.equal(tracker.observe('action_bar', { text: 'Starting in 3' }).to, PHASES.COUNTDOWN);
  assert.equal(tracker.observe('set_title_text', { text: 'GO!' }).to, PHASES.PROGRESS);
  assert.equal(tracker.observe('system_chat', { content: 'Results: Team 1 won' }).to, PHASES.END);
  assert.equal(tracker.history.length, 5);
});

test('phase packet text ignores identity fields and handles nested components', () => {
  assert.equal(packetText({ sender: 'uuid', content: { text: 'waiting', extra: [{ text: 'room' }] } }), 'waiting room');
});

test('Core Chinese preparation and progress announcements stay in distinct phases', () => {
  const tracker = new PhaseTracker();
  assert.equal(tracker.observe('action_bar', { text: '匹配赛建 准备开工' }).to, PHASES.PREPARATION);
  assert.equal(tracker.observe('action_bar', { text: '准备将在3秒后开始' }).to, PHASES.COUNTDOWN);
  assert.equal(tracker.observe('set_title_text', { text: '开始建造' }).to, PHASES.PROGRESS);
  assert.equal(tracker.observe('system_chat', { content: '本场结束，积分结算中' }).to, PHASES.END);
});

test('countdown wording containing 开始 is not mistaken for progress', () => {
  const tracker = new PhaseTracker();
  assert.equal(tracker.observe('system_chat', { content: '比赛开始倒计时' }).to, PHASES.COUNTDOWN);
  assert.equal(tracker.observe('action_bar', { text: '准备结束，将在3秒后开始' }), null);
  assert.equal(tracker.phase, PHASES.COUNTDOWN);
});

test('Core preparation countdown messages are recognized without English markers', () => {
  const tracker = new PhaseTracker();
  assert.equal(tracker.observe('action_bar', { text: '匹配赛建 准备5' }).to, PHASES.COUNTDOWN);
  tracker.reset();
  assert.equal(tracker.observe('system_chat', { content: '场地已开始准备' }).to, PHASES.PREPARATION);
});

test('team and scoreboard packets cannot regress an active game to waiting', () => {
  const tracker = new PhaseTracker();
  assert.equal(tracker.observe('set_title_text', { text: 'GO!' }).to, PHASES.PROGRESS);
  assert.equal(tracker.observe('teams', { mode: 'join', players: ['autobot00'] }), null);
  assert.equal(tracker.phase, PHASES.PROGRESS);
  assert.equal(tracker.observe('scoreboard_score', { itemName: 'join', scoreName: 'points', value: 1 }), null);
  assert.equal(tracker.phase, PHASES.PROGRESS);
});
