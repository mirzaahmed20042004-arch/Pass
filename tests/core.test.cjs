const test = require('node:test');
const assert = require('node:assert/strict');
const core = require('../app/src/main/assets/www/core.js');

test('local commands are explicit and do not hijack general questions', () => {
  assert.deepEqual(core.parseCommand('  Remember buy milk  '), {type: 'note', value: 'buy milk'});
  assert.deepEqual(core.parseCommand('search the web for dark matter'), {type: 'search', value: 'dark matter'});
  assert.deepEqual(core.parseCommand('open Camera'), {type: 'open', value: 'Camera'});
  assert.deepEqual(core.parseCommand('tap Open menu'), {type: 'tap', value: 'Open menu'});
  assert.equal(core.parseCommand('read my screen').type, 'screen');
  assert.equal(core.parseCommand('message someone').type, 'message');
  assert.equal(core.parseCommand('What is a note?').type, 'model');
  assert.equal(core.parseCommand('send a message to Alice').type, 'model');
});

test('only HTTP(S) endpoints without embedded credentials or query are accepted', () => {
  assert.deepEqual(core.validateEndpoint('http://192.168.1.5:11434/'), {ok: true, value: 'http://192.168.1.5:11434'});
  for (const input of ['', 'ftp://host', 'http://user:password@host', 'https://host/?token=secret', 'https://host/#x', 'not a url']) assert.equal(core.validateEndpoint(input).ok, false, input);
});

test('user content is escaped and agents are distinct profiles, not models', () => {
  assert.equal(core.escapeHtml('<img src="x" onerror=\'alert(1)\'>'), '&lt;img src=&quot;x&quot; onerror=&#39;alert(1)&#39;&gt;');
  assert.equal(core.agents.length, 4);
  assert.match(core.getAgent('code').prompt, /cannot execute/i);
  assert.equal(core.getAgent('missing').id, 'assistant');
});
