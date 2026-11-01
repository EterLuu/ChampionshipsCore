const fs = require('node:fs');

// A local coordinator can finish a server-observed window without disconnecting clients first.
// The deadline remains a failure when a control file was requested but never delivered.
function waitForWindow(durationMs, stopFile, timers = globalThis, exists = fs.existsSync) {
  if (stopFile && exists(stopFile)) return Promise.reject(new Error('stop file already exists'));
  return new Promise((resolve, reject) => {
    let poll;
    const finish = (reason, error) => {
      if (deadline != null) timers.clearTimeout(deadline);
      if (poll != null) timers.clearInterval(poll);
      if (error) reject(error);
      else resolve(reason);
    };
    const deadline = Number.isFinite(durationMs)
      ? timers.setTimeout(() => finish(stopFile ? 'deadline' : 'duration'), durationMs)
      : null;
    if (stopFile) poll = timers.setInterval(() => {
      try { if (exists(stopFile)) finish('control-file'); }
      catch (error) { finish(null, error); }
    }, 100);
    if (deadline == null && !stopFile) finish(null, new Error('an indefinite window requires a stop file'));
  });
}

module.exports = { waitForWindow };
