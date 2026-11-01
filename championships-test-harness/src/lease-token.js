const crypto = require('node:crypto');

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`missing ${name}`);
  return value;
}

function main() {
  const ttl = Number(process.argv[2] || 3600);
  if (!Number.isSafeInteger(ttl) || ttl < 1 || ttl > 86400) {
    throw new Error('ttl must be an integer between 1 and 86400 seconds');
  }
  const secret = required('CC_TEST_LEASE_SECRET');
  if (Buffer.byteLength(secret, 'utf8') < 32) throw new Error('lease secret must be at least 32 bytes');
  const serverId = required('CC_TEST_LEASE_SERVER_ID');
  const sessionId = required('CC_TEST_LEASE_SESSION_ID').toLowerCase();
  const generation = Number(required('CC_TEST_LEASE_GENERATION'));
  const username = required('CC_TEST_LEASE_USERNAME').toLowerCase();
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(serverId)) throw new Error('invalid server id');
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(sessionId))
    throw new Error('invalid session UUID');
  if (!Number.isSafeInteger(generation) || generation < 1) throw new Error('invalid generation');
  if (!/^[a-z0-9_]{1,16}$/.test(username)) throw new Error('invalid Minecraft username');
  const profileUuid = offlineUuid(username);
  const expiresAt = Math.floor(Date.now() / 1000) + ttl;
  const nonce = crypto.randomBytes(24).toString('base64url');
  const payload = Buffer.from([
    serverId, sessionId, generation, username, profileUuid, expiresAt, nonce,
  ].join('\n'), 'utf8');
  const signature = crypto.createHmac('sha256', secret).update(payload).digest('base64url');
  process.stdout.write(`v1.${payload.toString('base64url')}.${signature}\n`);
}

function offlineUuid(name) {
  const digest = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  digest[6] = (digest[6] & 0x0f) | 0x30;
  digest[8] = (digest[8] & 0x3f) | 0x80;
  const hex = digest.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

try {
  main();
} catch (error) {
  console.error(error.message);
  process.exitCode = 2;
}
