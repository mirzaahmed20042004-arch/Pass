/* Full UI interaction test, including a mocked native Android bridge. */
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { chromium } = require('playwright-core');
const chromiumBundle = require('@sparticuz/chromium');
const root = path.resolve(__dirname, '../app/src/main/assets/www');
const mime = {'.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.webp': 'image/webp'};
const server = http.createServer((request, response) => {
  let pathname = decodeURIComponent(new URL(request.url, 'http://test').pathname);
  const file = path.join(root, pathname === '/' ? 'index.html' : pathname);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) { response.writeHead(404); response.end(); return; }
  response.writeHead(200, {'Content-Type': mime[path.extname(file)] || 'application/octet-stream'});
  fs.createReadStream(file).pipe(response);
});
async function run() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  // The bundled browser ships its NSS libraries; use them on minimal Linux images.
  const binDir = path.join(path.dirname(require.resolve('@sparticuz/chromium')), '..', 'bin');
  const libDir = await chromiumBundle.inflate(path.join(binDir, 'al2023.tar.br'));
  process.env.LD_LIBRARY_PATH = path.join(libDir, 'lib') + (process.env.LD_LIBRARY_PATH ? ':' + process.env.LD_LIBRARY_PATH : '');
  const executablePath = await chromiumBundle.default.executablePath();
  const args = chromiumBundle.default.args.filter(x => x !== '--disable-web-security' && x !== '--allow-running-insecure-content');
  const browser = await chromium.launch({executablePath, args, headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 950}});
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.addInitScript(() => {
    window.nativeCalls = [];
    const caps = {native: true, overlayAllowed: false, hudRunning: false, accessibilityEnabled: false, voiceAvailable: true};
    function record(method, arg) { window.nativeCalls.push({method, arg}); }
    window.UltronAndroid = {
      getCapabilities: () => JSON.stringify(caps),
      startHud: () => { record('startHud'); caps.overlayAllowed = caps.hudRunning = true; setTimeout(() => window.UltronApp.onCapabilities(JSON.stringify(caps)), 20); },
      stopHud: () => { record('stopHud'); caps.hudRunning = false; },
      openAccessibilitySettings: () => record('openAccessibilitySettings'),
      startVoice: () => { record('startVoice'); setTimeout(() => window.UltronApp.onVoiceResult('remember voice idea'), 20); },
      stopVoice: () => record('stopVoice'),
      speak: value => record('speak', value),
      openApp: value => record('openApp', value),
      searchWeb: value => record('searchWeb', value),
      draftMessage: value => record('draftMessage', JSON.parse(value)),
      shareText: value => record('shareText', value),
      scheduleTap: value => record('scheduleTap', value),
      testModel: value => { const req = JSON.parse(value); record('testModel', req); setTimeout(() => window.UltronApp.onModelTest(req.id, JSON.stringify({ok: true, modelFound: true})), 20); },
      chatWithModel: value => { const req = JSON.parse(value); record('chatWithModel', req); setTimeout(() => window.UltronApp.onModelResponse(req.id, JSON.stringify({ok: true, text: '<b>Answer from model</b>'})), 20); }
    };
  });
  await page.goto(`http://127.0.0.1:${server.address().port}/`, {waitUntil: 'networkidle'});
  const clickNav = async name => page.locator(`.side-nav [data-nav="${name}"]`).click();
  assert.equal(await page.title(), 'Ultron Ai — your personal intelligence');
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);

  // Notes are persisted, searched, safely escaped, and deleted.
  await page.locator('#page-home [data-action=note]').click();
  await page.locator('#note-text').fill('<img src=x onerror="window.xssFired=1"> remember this');
  await page.locator('#note-form button[type=submit]').click();
  await clickNav('memory');
  assert.equal(await page.locator('.memory-card').count(), 1);
  assert.match(await page.locator('.memory-card p').innerText(), /<img src=x/);
  assert.equal(await page.locator('.memory-card img').count(), 0);
  assert.equal(await page.evaluate(() => window.xssFired), undefined);
  await page.reload({waitUntil: 'networkidle'}); await clickNav('memory');
  assert.equal(await page.locator('.memory-card').count(), 1);
  await page.locator('#memory-search').fill('not present'); assert.match(await page.locator('#memory-grid').innerText(), /No memories found/);
  await page.locator('#memory-search').fill(''); await page.locator('[data-delete-note]').click(); await page.locator('[data-confirm-note]').click();
  assert.equal(await page.locator('.memory-card').count(), 0);

  // Safe local commands and explicit Android intents.
  await clickNav('chat'); await page.locator('#chat-input').fill('remember buy milk'); await page.locator('#chat-form button[type=submit]').click();
  assert.match(await page.locator('#chat-stream').innerText(), /Saved to your local memories/);
  await clickNav('automations'); await page.locator('#page-automations [data-action=search]').click();
  await page.locator('#search-query').fill('space telescopes'); await page.locator('#search-form button[type=submit]').click();
  await page.locator('#page-automations [data-action=message]').click();
  await page.locator('#message-recipient').fill('+1 555 123 4567'); await page.locator('#message-body').fill('Hello there'); await page.locator('#message-form button[type=submit]').click();
  const calls1 = await page.evaluate(() => window.nativeCalls);
  assert.ok(calls1.some(x => x.method === 'searchWeb' && x.arg === 'space telescopes'));
  assert.ok(calls1.some(x => x.method === 'draftMessage' && x.arg.body === 'Hello there'));
  assert.ok(!calls1.some(x => /sendSMS|postMessage/.test(x.method)));

  // An optional, per-agent model is passed to the native client. Local screen snapshots never enter model context.
  await clickNav('agents'); await page.locator('[data-agent-model=code]').click(); await page.locator('#agent-model-name').fill('codellama:7b'); await page.locator('#agent-model-form button[type=submit]').click();
  await page.locator('[data-agent-use=code]').click();
  await page.evaluate(() => window.UltronApp.onScreenSnapshot('PRIVATE SCREEN LABEL'));
  await clickNav('settings');
  await page.locator('#model-url').fill('http://192.168.1.10:11434'); await page.locator('#model-name').fill('qwen2.5:3b'); await page.locator('#model-token').fill('example-secret-token');
  await page.locator('#model-form button[type=submit]').click(); await page.locator('[data-action=test-model]').click();
  await page.waitForFunction(() => document.querySelector('#model-test-result').textContent.includes('model found'));
  assert.equal(await page.locator('#top-model-status').innerText(), 'Model connected');
  await clickNav('chat'); await page.locator('#chat-input').fill('Explain this Java bug'); await page.locator('#chat-form button[type=submit]').click();
  await page.waitForFunction(() => document.querySelector('#chat-stream').textContent.includes('Answer from model'));
  assert.equal(await page.locator('.message-content b').count(), 0);
  const modelCalls = (await page.evaluate(() => window.nativeCalls)).filter(x => x.method === 'chatWithModel');
  const sent = modelCalls[modelCalls.length - 1].arg;
  assert.equal(sent.model, 'codellama:7b'); assert.equal(sent.token, 'example-secret-token');
  assert.ok(!JSON.stringify(sent.messages).includes('PRIVATE SCREEN LABEL')); assert.ok(!JSON.stringify(sent.messages).includes('buy milk'));
  assert.match(sent.messages[0].content, /cannot execute/i);

  // HUD, accessibility and one-shot voice are initiated only by the user's buttons.
  await clickNav('connections'); await page.locator('#page-connections [data-action=hud]').click(); await page.locator('[data-action=start-hud]').click();
  await page.waitForFunction(() => document.querySelector('#connection-hud').textContent === 'ON');
  await page.locator('#page-connections [data-action=screen]').click(); await page.locator('[data-action=accessibility-settings]').click();
  await page.locator('#page-connections [data-action=voice]').click(); await page.locator('[data-action=start-voice]').click();
  await page.waitForFunction(() => document.querySelector('#chat-stream').textContent.includes('voice idea'));
  assert.ok((await page.evaluate(() => window.nativeCalls)).some(x => x.method === 'speak'));

  // Mobile layout has no horizontal overflow and all navigation remains available.
  await page.setViewportSize({width: 390, height: 844});
  await page.locator('.bottom-nav [data-nav=home]').click();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.locator('.bottom-nav [data-action=more]').click(); await page.locator('.modal [data-nav=connections]').click();
  assert.equal(await page.locator('#page-connections').isVisible(), true);
  assert.deepEqual(pageErrors, []);
  console.log('UI tests passed: notes, chat, safe intents, model privacy, permissions, voice, and mobile navigation.');
  await browser.close();
}
run().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => server.close());
