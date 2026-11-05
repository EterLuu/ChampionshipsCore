const PHASES = Object.freeze({
  UNKNOWN: 'UNKNOWN',
  WAITING: 'WAITING',
  VOTING: 'VOTING',
  PREPARATION: 'PREPARATION',
  COUNTDOWN: 'COUNTDOWN',
  PROGRESS: 'PROGRESS',
  END: 'END',
});

const TEXT_PACKET_NAMES = new Set([
  'system_chat', 'action_bar', 'set_title_text', 'set_title_subtitle', 'open_window',
]);

const DEFAULT_MARKERS = Object.freeze({
  END: [/winner/i, /won the game/i, /finished/i, /complete/i, /results?/i, /结算/, /获胜/, /本场结束/, /比赛结束/, /游戏结束/, /已结束/],
  PROGRESS: [/^go!?$/i, /playing/i, /in progress/i, /round/i, /回合/, /比赛开始/, /游戏开始/, /开始生存/, /开始建造/, /开始挑战/, /开始探索/, /追击开始/, /开球/],
  COUNTDOWN: [/countdown/i, /starting in/i, /starts? in/i, /将在.{0,12}(秒|second)/i, /准备\s*[0-9]+(?:秒)?$/, /倒计时/, /倒数/],
  PREPARATION: [/preparation/i, /prepare players/i, /准备阶段/, /(?:开始|进入)准备/, /准备(入场|开战|开始|探索|建造|出发|挑战|开工)/, /确认(路线|赛道|分工|场地)/, /选择装备/, /出生岛就位/],
  VOTING: [/vote/i, /voting/i, /map selection/i, /投票/, /地图选择/],
  WAITING: [/waiting/i, /lobby/i, /queue/i, /join/i, /等待/, /大厅/, /排队/],
});

function textOf(value, output = []) {
  if (value == null) return output;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    output.push(String(value));
    return output;
  }
  if (Array.isArray(value)) {
    value.forEach(item => textOf(item, output));
    return output;
  }
  if (typeof value === 'object') {
    for (const [key, item] of Object.entries(value)) {
      if (['signature', 'salt', 'timestamp', 'uuid', 'sender'].includes(key)) continue;
      textOf(item, output);
    }
  }
  return output;
}

function packetText(packet) {
  return textOf(packet).join(' ').replace(/\s+/g, ' ').trim();
}

function roundOfText(text) {
  const match = String(text || '').match(/\bround\s*[-#: ]?\s*(\d+)\b|第\s*(\d+)\s*轮|回合\s*[-#: ]?\s*(\d+)/i);
  if (!match) return null;
  const value = Number(match[1] || match[2] || match[3]);
  return Number.isSafeInteger(value) ? value : null;
}

function compileMarkers(markers = {}) {
  const result = {};
  for (const phase of Object.keys(DEFAULT_MARKERS)) {
    const values = markers[phase] || DEFAULT_MARKERS[phase];
    result[phase] = values.map(value => value instanceof RegExp ? value : new RegExp(String(value), 'i'));
  }
  return result;
}

function phaseForText(text, markers) {
  if (!text) return null;
  // Countdown text often contains the word “开始”; classify it before the
  // progress markers so a resident Bot does not start moving early.
  for (const phase of [PHASES.END, PHASES.COUNTDOWN, PHASES.PROGRESS, PHASES.PREPARATION, PHASES.VOTING, PHASES.WAITING]) {
    if (markers[phase]?.some(marker => marker.test(text))) return phase;
  }
  return null;
}

class PhaseTracker {
  constructor(markers = {}, initial = PHASES.WAITING, initialRound = null) {
    this.markers = compileMarkers(markers);
    this.phase = initial;
    this.history = [];
    this.lastText = '';
    this.lastPacket = null;
    this.round = Number.isSafeInteger(initialRound) ? initialRound : null;
    this.roundHistory = [];
  }

  reset() {
    this.phase = PHASES.WAITING;
    this.history = [];
    this.lastText = '';
    this.lastPacket = null;
    this.round = null;
    this.roundHistory = [];
  }

  observe(packetName, packet) {
    const text = TEXT_PACKET_NAMES.has(packetName) ? packetText(packet) : '';
    const round = roundOfText(text);
    if (round != null && round !== this.round) {
      this.round = round;
      this.roundHistory.push(round);
    }
    const inferred = phaseForText(text, this.markers);
    const accepted = inferred && this.acceptTransition(inferred) ? inferred : null;
    const phase = accepted || this.phaseForPacket(packetName, packet) || this.phase;
    this.lastText = text || this.lastText;
    this.lastPacket = packetName;
    if (phase === this.phase) return null;
    const transition = {
      from: this.phase,
      to: phase,
      packet: packetName,
      text: text.slice(0, 240),
      at: new Date().toISOString(),
    };
    this.phase = phase;
    this.history.push(transition);
    return transition;
  }

  acceptTransition(next) {
    if (next === this.phase) return true;
    if (next === PHASES.WAITING) return this.phase === PHASES.UNKNOWN || this.phase === PHASES.WAITING;
    if (next === PHASES.VOTING) return this.phase === PHASES.UNKNOWN || this.phase === PHASES.WAITING || this.phase === PHASES.VOTING;
    if (next === PHASES.PREPARATION) return [PHASES.UNKNOWN, PHASES.WAITING, PHASES.VOTING, PHASES.PREPARATION, PHASES.END].includes(this.phase);
    if (next === PHASES.COUNTDOWN) return [PHASES.UNKNOWN, PHASES.WAITING, PHASES.VOTING, PHASES.PREPARATION, PHASES.COUNTDOWN, PHASES.END].includes(this.phase);
    if (next === PHASES.PROGRESS) return [PHASES.UNKNOWN, PHASES.WAITING, PHASES.VOTING, PHASES.PREPARATION, PHASES.COUNTDOWN, PHASES.PROGRESS, PHASES.END].includes(this.phase);
    return next === PHASES.END;
  }

  phaseForPacket(packetName, packet) {
    if (packetName === 'login' || packetName === 'open_window') {
      const text = packetText(packet);
      if (packetName === 'open_window' && /vote|map|投票|地图/i.test(text)) return PHASES.VOTING;
      if (packetName === 'login') return PHASES.WAITING;
    }
    if (packetName === 'death_combat_event' || packetName === 'respawn') return PHASES.PROGRESS;
    return null;
  }
}

module.exports = { PHASES, DEFAULT_MARKERS, textOf, packetText, phaseForText, PhaseTracker };
