const test = require('node:test');
const assert = require('node:assert/strict');
const { provisionBots } = require('../web-bots');

test('cc-web provisioning sends passwords but never accepts UUID input', async () => {
  let request;
  const result = await provisionBots({
    count: 2,
    startIndex: 0,
    prefix: 'autobot',
    botAllowedCidrs: ['10.20.0.0/16'],
    botPersistent: true,
    webBaseUrl: 'http://127.0.0.1:3000',
    webKeyId: 'harness',
    webHmacSecret: 'test-secret-that-is-at-least-32-bytes-long',
    allowInsecureWebHttp: true,
    webRequestTimeoutMs: 1000,
  }, async (url, options) => {
    request = { url, options, body: JSON.parse(options.body) };
    return {
      ok: true,
      status: 200,
      json: async () => ({
        uuidMode: 'CUSTOM_UUID',
        bots: [{ username: 'autobot00' }, { username: 'autobot01' }],
      }),
    };
  });

  assert.equal(request.url, 'http://127.0.0.1:3000/api/internal/test/bots/provision');
  assert.equal(request.body.bots.length, 2);
  assert.equal(request.body.bots[0].slot, 0);
  assert.equal(request.body.bots[1].allowedCidrs[0], '10.20.0.0/16');
  assert.equal(request.body.bots[0].profileUuid, undefined);
  assert.equal(result.uuidMode, 'CUSTOM_UUID');
  assert.ok(result.credentials.autobot00.password);
  assert.notEqual(result.credentials.autobot00.password, result.credentials.autobot01.password);
});

test('cc-web provisioning rejects an incomplete account response', async () => {
  await assert.rejects(
    provisionBots({
      count: 1,
      startIndex: 0,
      prefix: 'autobot',
      botAllowedCidrs: ['10.20.0.0/16'],
      botPersistent: true,
      webBaseUrl: 'https://web.example.test',
      webKeyId: 'harness',
      webHmacSecret: 'test-secret-that-is-at-least-32-bytes-long',
      allowInsecureWebHttp: false,
      webRequestTimeoutMs: 1000,
    }, async () => ({ ok: true, status: 200, json: async () => ({ bots: [] }) })),
    /incomplete account list/,
  );
});
