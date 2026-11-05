const crypto = require('node:crypto');

const PROVISION_PATH = '/api/internal/test/bots/provision';
const DISABLE_PATH = '/api/internal/test/bots/disable';

function required(value, name) {
  if (!value) throw new Error(`missing ${name} for cc-web Bot mode`);
  return value;
}

function provisionBots(settings, fetchImpl = globalThis.fetch) {
  const baseUrl = required(settings.webBaseUrl, 'CC_TEST_WEB_BASE_URL').replace(/\/$/, '');
  const keyId = required(settings.webKeyId, 'CC_TEST_WEB_KEY_ID');
  const secret = required(settings.webHmacSecret, 'CC_TEST_WEB_HMAC_SECRET');
  if (Buffer.byteLength(secret, 'utf8') < 32)
    throw new Error('CC_TEST_WEB_HMAC_SECRET must be at least 32 bytes');
  if (!/^https:\/\//i.test(baseUrl) && !settings.allowInsecureWebHttp)
    throw new Error('cc-web Bot mode requires HTTPS or CC_TEST_WEB_ALLOW_INSECURE_HTTP=true');
  if (!Array.isArray(settings.botAllowedCidrs) || settings.botAllowedCidrs.length === 0)
    throw new Error('CC_TEST_BOT_ALLOWED_CIDRS must contain at least one CIDR in cc-web Bot mode');

  const bots = [];
  for (let offset = 0; offset < settings.count; offset += 1) {
    const slot = settings.startIndex + offset;
    bots.push({
      slot,
      username: `${settings.prefix}${String(slot).padStart(2, '0')}`,
      password: crypto.randomBytes(18).toString('base64url'),
      allowedCidrs: settings.botAllowedCidrs,
      persistent: settings.botPersistent,
    });
  }
  if (bots.some(bot => bot.slot > 63)) throw new Error('cc-web Bot slots must be between 0 and 63');
  if (bots.some(bot => !/^[A-Za-z0-9_]{3,16}$/.test(bot.username)))
    throw new Error('cc-web Bot usernames must be 3-16 ASCII characters');
  return requestJson(settings, PROVISION_PATH, { bots }, fetchImpl).then(async result => {
    // cc-web commits credentials and publishes an outbox event before the
    // AuthMe/bridge consumers necessarily observe it. Give the normal sync
    // path a small settle window so the first login is not mistaken for a bad
    // password. Set to zero for a caller that already waits on sync health.
    const settleMs = Number(settings.webProvisionSettleMs || 0);
    if (settleMs > 0) await new Promise(resolve => setTimeout(resolve, settleMs));
    if (!result || !Array.isArray(result.bots) || result.bots.length !== bots.length)
      throw new Error('cc-web Bot provisioning returned an incomplete account list');
    const passwords = new Map(bots.map(bot => [bot.username.toLowerCase(), bot.password]));
    const credentials = {};
    for (const profile of result.bots) {
      if (!profile || typeof profile.username !== 'string')
        throw new Error('cc-web Bot provisioning returned an invalid account');
      const password = passwords.get(profile.username.toLowerCase());
      if (!password || credentials[profile.username])
        throw new Error('cc-web Bot provisioning returned an unexpected account');
      credentials[profile.username] = { password };
    }
    if (Object.keys(credentials).length !== bots.length)
      throw new Error('cc-web Bot provisioning returned an incomplete account list');
    return { credentials, uuidMode: result.uuidMode || null };
  });
}

function disableBots(settings, fetchImpl = globalThis.fetch) {
  const count = settings.count;
  const startIndex = settings.startIndex;
  const slots = Array.from({ length: count }, (_, index) => startIndex + index);
  return requestJson(settings, DISABLE_PATH, { slots }, fetchImpl).then(result => {
    if (!result || !Array.isArray(result.disabled))
      throw new Error('cc-web Bot disable returned an invalid response');
    return result;
  });
}

function requestJson(settings, requestPath, payload, fetchImpl) {
  const baseUrl = required(settings.webBaseUrl, 'CC_TEST_WEB_BASE_URL').replace(/\/$/, '');
  const keyId = required(settings.webKeyId, 'CC_TEST_WEB_KEY_ID');
  const secret = required(settings.webHmacSecret, 'CC_TEST_WEB_HMAC_SECRET');
  if (Buffer.byteLength(secret, 'utf8') < 32)
    throw new Error('CC_TEST_WEB_HMAC_SECRET must be at least 32 bytes');
  if (!/^https:\/\//i.test(baseUrl) && !settings.allowInsecureWebHttp)
    throw new Error('cc-web Bot mode requires HTTPS or CC_TEST_WEB_ALLOW_INSECURE_HTTP=true');
  const body = JSON.stringify(payload);
  const timestamp = String(Date.now());
  const requestId = crypto.randomUUID();
  const bodyHash = crypto.createHash('sha256').update(body).digest('hex');
  const signature = crypto.createHmac('sha256', secret)
    .update(`POST\n${requestPath}\n${timestamp}\n${requestId}\n${bodyHash}`)
    .digest('hex');
  return fetchImpl(`${baseUrl}${requestPath}`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      accept: 'application/json',
      'x-cc-key-id': keyId,
      'x-cc-timestamp': timestamp,
      'x-cc-request-id': requestId,
      'x-cc-signature': signature,
    },
    body,
    signal: AbortSignal.timeout(settings.webRequestTimeoutMs),
  }).then(async response => {
    if (!response.ok) throw new Error(`cc-web Bot request ${requestPath} returned HTTP ${response.status}`);
    return response.json();
  });
}

module.exports = { provisionBots, disableBots, requestJson };
