(function () {
  'use strict';
  var C = window.UltronCore;
  var $ = function (selector) { return document.querySelector(selector); };
  var $$ = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var e = C.escapeHtml;
  var KEY = 'ultron-ai-state-v1';
  var defaults = function () { return { notes: [], messages: [], selectedAgent: 'assistant', settings: { url: '', model: '', token: '', voiceReplies: false, agentModels: {} } }; };
  function loadState() {
    var value = defaults();
    try {
      var loaded = JSON.parse(localStorage.getItem(KEY) || '{}');
      if (Array.isArray(loaded.notes)) value.notes = loaded.notes.filter(function (n) { return n && typeof n.text === 'string'; }).slice(0, 200);
      if (Array.isArray(loaded.messages)) value.messages = loaded.messages.filter(function (m) { return m && typeof m.text === 'string' && (m.role === 'user' || m.role === 'assistant'); }).slice(-100);
      if (typeof loaded.selectedAgent === 'string') value.selectedAgent = C.getAgent(loaded.selectedAgent).id;
      if (loaded.settings && typeof loaded.settings === 'object') {
        ['url', 'model', 'token'].forEach(function (key) { if (typeof loaded.settings[key] === 'string') value.settings[key] = loaded.settings[key]; });
        value.settings.voiceReplies = !!loaded.settings.voiceReplies;
        if (loaded.settings.agentModels && typeof loaded.settings.agentModels === 'object') value.settings.agentModels = loaded.settings.agentModels;
      }
    } catch (ignored) { /* Start with an empty local state. */ }
    return value;
  }
  var state = loadState();
  var currentPage = 'home';
  var capabilities = { native: !!window.UltronAndroid, overlayAllowed: false, hudRunning: false, accessibilityEnabled: false, voiceAvailable: false };
  var connectionVerified = false;
  var busy = false;
  var liveVoice = false;
  var pendingVoiceReply = false;
  var toastTimer;
  var modelRequest = 0;
  var testRequest = 0;
  var modalReturnFocus = null;
  var names = { home: 'OVERVIEW', chat: 'AI CHAT', agents: 'AGENTS', automations: 'AUTOMATIONS', connections: 'CONNECTIONS', memory: 'MEMORY', settings: 'SETTINGS' };
  function icon(name, extra) { return '<svg class="icon ' + (extra || '') + '" aria-hidden="true"><use href="#i-' + name + '"></use></svg>'; }
  function persist() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); return true; }
    catch (err) { toast('Could not save locally. Check device storage.'); return false; }
  }
  function toast(message) {
    var el = $('#toast'); el.textContent = message; el.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { el.classList.remove('show'); }, 3600);
  }
  function hasNative() { return !!window.UltronAndroid; }
  function callNative(method, arg) {
    if (!hasNative() || typeof window.UltronAndroid[method] !== 'function') {
      toast('This action is available in the Android app.'); return false;
    }
    try {
      if (arguments.length > 1) window.UltronAndroid[method](arg);
      else window.UltronAndroid[method]();
      return true;
    } catch (err) { toast('This action could not be started on this device.'); return false; }
  }
  function navigate(page) {
    if (!names[page]) page = 'home';
    currentPage = page;
    $$('.page').forEach(function (section) { var active = section.dataset.page === page; section.hidden = !active; section.classList.toggle('active', active); });
    $$('[data-nav]').forEach(function (button) { if (button.classList.contains('nav-item') || button.parentNode.classList.contains('bottom-nav')) button.classList.toggle('active', button.dataset.nav === page); });
    $('.workspace-layout').classList.toggle('is-secondary', page !== 'home');
    $('#current-crumb').textContent = names[page];
    if (page === 'chat') renderChat();
    if (page === 'memory') renderMemory();
    if (page === 'agents') renderAgents();
    if (page === 'settings') renderSettings();
    window.scrollTo(0, 0);
  }
  function readableDate(value) {
    try { return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }); }
    catch (err) { return ''; }
  }
  function renderHome() {
    var last = state.messages.slice().reverse().filter(function (m) { return m.role === 'user'; })[0];
    var container = $('#home-recent');
    if (!last) {
      container.innerHTML = '<div class="empty-recent"><span class="empty-icon">' + icon('chat') + '</span><span><strong>A fresh start looks good on you.</strong><small>Your conversations will find a home here.</small></span></div>';
    } else {
      container.innerHTML = '<div class="recent-item"><div class="chat-avatar"><img src="images/logo.webp" alt=""></div><span><strong>' + e(last.text) + '</strong><small>' + e(readableDate(last.created)) + ' · Saved on this device</small></span><button class="round-arrow" data-nav="chat" aria-label="Continue conversation">' + icon('arrow') + '</button></div>';
    }
  }
  function renderStatuses() {
    var configured = !!(state.settings.url && state.settings.model);
    $('#top-model-status').textContent = connectionVerified ? 'Model connected' : configured ? 'Model configured' : 'Connect a model';
    $('#top-model-dot').classList.toggle('off', !connectionVerified);
    $('#rail-model-status').textContent = connectionVerified ? 'CONNECTED' : configured ? 'CONFIGURED' : 'NOT CONNECTED';
    $('#connection-model').textContent = connectionVerified ? 'CONNECTED' : configured ? 'CONFIGURED' : 'NOT CONNECTED';
    $('#rail-device-status').textContent = hasNative() ? 'AVAILABLE' : 'ANDROID APP';
    $('#connection-device').textContent = hasNative() ? 'AVAILABLE' : 'ANDROID APP';
    $('#connection-accessibility').textContent = capabilities.accessibilityEnabled ? 'ENABLED' : 'OFF';
    $('#connection-hud').textContent = capabilities.hudRunning ? 'ON' : 'OFF';
    $('#connection-voice').textContent = hasNative() ? (capabilities.voiceAvailable ? 'ON REQUEST' : 'UNAVAILABLE') : 'ANDROID APP';
    $('#hud-state').textContent = capabilities.hudRunning ? 'ON · LONG-PRESS TO READ' : 'OFF BY DEFAULT';
    $('#hud-dot').classList.toggle('muted', !capabilities.hudRunning);
    $('#settings-hud').innerHTML = (capabilities.hudRunning ? 'On' : 'Set up') + ' ' + icon('chevron');
    $('#settings-accessibility').innerHTML = (capabilities.accessibilityEnabled ? 'Enabled' : 'Set up') + ' ' + icon('chevron');
    $('#chat-connection-label').textContent = connectionVerified ? 'Model connected' : configured ? 'Model configured · not verified' : 'On-device commands available';
  }
  function refreshCapabilities() {
    if (hasNative()) {
      try {
        var data = JSON.parse(window.UltronAndroid.getCapabilities());
        Object.keys(data).forEach(function (k) { capabilities[k] = data[k]; });
      } catch (ignored) { /* Device may still be starting. */ }
    }
    renderStatuses();
  }
  function renderSettings() {
    $('#model-url').value = state.settings.url;
    $('#model-name').value = state.settings.model;
    $('#model-token').value = state.settings.token;
    $('#voice-replies').checked = state.settings.voiceReplies;
    $('#model-test-result').textContent = '';
    renderStatuses();
  }
  function renderAgents() {
    $('#agent-grid').innerHTML = C.agents.map(function (agent) {
      var selected = agent.id === state.selectedAgent;
      var model = state.settings.agentModels[agent.id] || state.settings.model || 'Not configured';
      return '<article class="agent-card"><div class="agent-card-head"><span class="quick-icon ' + agent.color + '">' + icon(agent.icon) + '</span><span class="agent-tag ' + (selected ? 'selected' : '') + '">' + (selected ? 'SELECTED' : 'PROFILE') + '</span></div><h3>' + e(agent.name) + '</h3><p>' + e(agent.description) + '</p><div class="agent-model">MODEL &nbsp; <strong>' + e(model) + '</strong></div><div class="agent-buttons"><button class="button ' + (selected ? 'subtle' : 'primary') + ' small" data-agent-use="' + agent.id + '">' + (selected ? 'Open chat' : 'Use agent') + ' ' + icon('arrow') + '</button><button class="button subtle small" data-agent-model="' + agent.id + '">Choose model</button></div></article>';
    }).join('');
  }
  function renderMemory() {
    var query = ($('#memory-search').value || '').toLowerCase().trim();
    var notes = state.notes.filter(function (n) { return n.text.toLowerCase().indexOf(query) !== -1; });
    $('#memory-count').textContent = state.notes.length + (state.notes.length === 1 ? ' saved memory' : ' saved memories');
    if (!notes.length) {
      $('#memory-grid').innerHTML = '<div class="memory-empty"><span class="empty-icon">' + icon('memory') + '</span><strong>' + (query ? 'No memories found.' : 'A little room for your thoughts.') + '</strong><p>' + (query ? 'Try another search.' : 'Save a note and it will be right here when you need it.') + '</p>' + (query ? '' : '<button class="button subtle small" data-action="note">' + icon('plus') + ' Add a memory</button>') + '</div>';
      return;
    }
    $('#memory-grid').innerHTML = notes.map(function (note) { return '<article class="memory-card"><div class="memory-card-top">' + icon('memory') + '<button class="memory-delete" data-delete-note="' + e(note.id) + '" aria-label="Delete memory">' + icon('trash') + '</button></div><p>' + e(note.text) + '</p><small>' + e(readableDate(note.created)) + ' · Stored locally</small></article>'; }).join('');
  }
  function renderChat() {
    var agent = C.getAgent(state.selectedAgent);
    $('#chat-agent-name').textContent = agent.name;
    var stream = $('#chat-stream');
    if (!state.messages.length) {
      stream.innerHTML = '<div class="chat-welcome"><img src="images/logo.webp" alt=""><h2>Good things start here.</h2><p>Ask a question, save a thought, or try a command. I\'m here when you need me.</p></div>';
    } else {
      stream.innerHTML = state.messages.map(function (m) {
        return '<div class="chat-message ' + e(m.role) + '">' + (m.role === 'assistant' ? '<div class="message-logo"><img src="images/logo.webp" alt=""></div>' : '') + '<div class="message-content">' + e(m.text) + '</div></div>';
      }).join('');
    }
    if (busy) stream.insertAdjacentHTML('beforeend', '<div class="typing-indicator" id="typing">✦ &nbsp; Thinking with your model…</div>');
    $('#chat-form button[type=submit]').disabled = busy;
    stream.scrollTop = stream.scrollHeight;
    renderStatuses();
  }
  function addMessage(role, text, modelContext) {
    state.messages.push({ role: role, text: String(text).slice(0, 12000), created: Date.now(), modelContext: !!modelContext });
    state.messages = state.messages.slice(-100);
    persist(); renderHome(); renderChat();
  }
  function addNote(text) {
    text = String(text || '').trim();
    if (!text) { toast('Add a little text first.'); return false; }
    if (state.notes.length >= 200) { toast('Memory is full. Delete a note before adding another.'); return false; }
    state.notes.unshift({ id: Date.now().toString(36) + Math.random().toString(36).slice(2, 8), text: text.slice(0, 3000), created: Date.now() });
    if (!persist()) { state.notes.shift(); return false; }
    renderMemory(); return true;
  }
  function answer(text, fromVoice, modelContext) {
    addMessage('assistant', text, modelContext);
    if ((fromVoice || state.settings.voiceReplies) && hasNative()) callNative('speak', String(text).slice(0, 3500));
    else if (fromVoice && liveVoice) liveVoice = false;
  }
  function sendMessage(raw, fromVoice) {
    var text = String(raw || '').trim();
    if (!text || busy) return;
    text = text.slice(0, 4000);
    var cmd = C.parseCommand(text);
    addMessage('user', text, cmd.type === 'model');
    if (cmd.type === 'help') {
      answer('I can save notes, open installed apps, search the web, prepare a message, share a draft, and help with visible screen labels after you enable accessibility. Try “remember buy milk” or “search the web for astronomy”.\n\nFor open-ended conversation or coding help, connect an Ollama model in Settings. I won’t send a message or make a sensitive tap for you.', fromVoice, false);
    } else if (cmd.type === 'note') {
      answer(addNote(cmd.value) ? 'Saved to your local memories. You can find or delete it in Memory.' : 'I couldn’t save that note.', fromVoice, false);
    } else if (cmd.type === 'open') {
      liveVoice = false;
      answer(hasNative() ? 'I’ll look for “' + cmd.value + '” among your installed apps.' : 'Opening installed apps is available in the Android app.', fromVoice, false);
      if (hasNative()) callNative('openApp', cmd.value);
    } else if (cmd.type === 'search') {
      liveVoice = false;
      answer(hasNative() ? 'I’ll open your browser to search for “' + cmd.value + '”.' : 'Web search through your phone is available in the Android app.', fromVoice, false);
      if (hasNative()) callNative('searchWeb', cmd.value);
    } else if (cmd.type === 'message') {
      liveVoice = false;
      answer('Let’s make a draft. Your messaging app will open so you can review and send it yourself.', fromVoice, false);
      showMessage();
    } else if (cmd.type === 'screen') {
      liveVoice = false;
      answer('To read accessible labels on another app, enable screen assistance and long-press the floating HUD while that app is visible.', fromVoice, false);
      showScreen();
    } else if (cmd.type === 'tap') {
      liveVoice = false;
      answer('I can try one supervised tap after you confirm and switch to the target app. Some sensitive labels are blocked, but you must verify the target yourself.', fromVoice, false);
      showTap(cmd.value);
    } else {
      var endpoint = C.validateEndpoint(state.settings.url);
      var agent = C.getAgent(state.selectedAgent);
      var model = String(state.settings.agentModels[agent.id] || state.settings.model || '').trim();
      if (!endpoint.ok || !model) {
        answer('For an open-ended answer, first connect a model in Settings. Ultron doesn’t include a model by default. You can still use local commands like “remember …”, “open …”, or “search the web for …”.', fromVoice, false);
        return;
      }
      if (!hasNative()) {
        answer('The browser preview cannot connect to your model. Install the Android app to use your configured server.', fromVoice, false);
        return;
      }
      var messages = [{ role: 'system', content: agent.prompt }];
      state.messages.filter(function (m) { return m.modelContext; }).slice(-8).forEach(function (m) { messages.push({ role: m.role, content: m.text.slice(0, 4000) }); });
      busy = true; pendingVoiceReply = !!fromVoice; renderChat();
      var id = ++modelRequest;
      var request = { id: id, url: endpoint.value, model: model, token: state.settings.token, messages: messages };
      if (!callNative('chatWithModel', JSON.stringify(request))) { busy = false; pendingVoiceReply = false; renderChat(); }
    }
  }
  function openModal(kicker, title, description, body, actions) {
    modalReturnFocus = document.activeElement;
    $('#modal-root').innerHTML = '<div class="modal-overlay" data-modal-overlay><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div class="modal-head"><div><div class="modal-kicker">' + e(kicker) + '</div><h2 id="modal-title">' + e(title) + '</h2></div><button class="modal-close" data-modal-close aria-label="Close">' + icon('close') + '</button></div>' + (description ? '<p class="modal-description">' + e(description) + '</p>' : '') + (body || '') + (actions ? '<div class="modal-actions">' + actions + '</div>' : '') + '</section></div>';
    var focus = $('#modal-root input:not([type=checkbox]), #modal-root textarea, #modal-root .modal-close');
    if (focus) setTimeout(function () { focus.focus(); }, 30);
  }
  function closeModal() { $('#modal-root').innerHTML = ''; if (modalReturnFocus && document.contains(modalReturnFocus)) modalReturnFocus.focus(); modalReturnFocus = null; }
  function showNote() {
    openModal('A LITTLE SOMETHING TO KEEP', 'Save a thought.', 'Your memory will stay in this app on this device.', '<form class="modal-form" id="note-form"><label>Your note<textarea id="note-text" maxlength="3000" required placeholder="A thought, a reminder, an idea..."></textarea></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Save memory ' + icon('arrow') + '</button></div></form>');
  }
  function showSearch() {
    openModal('FIND WHAT YOU NEED', 'Search the web.', 'Your search will open in your phone’s browser.', '<form class="modal-form" id="search-form"><label>What are you looking for?<input id="search-query" required maxlength="1000" placeholder="Search for anything"></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Open browser ' + icon('arrow') + '</button></div></form>');
  }
  function showOpenApp() {
    openModal('GET THERE FASTER', 'Open an app.', 'Enter the name of an app installed on your Android phone.', '<form class="modal-form" id="open-form"><label>App name<input id="app-name" required maxlength="100" placeholder="e.g. Camera"></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Open app ' + icon('arrow') + '</button></div></form>');
  }
  function showMessage() {
    openModal('A NOTE TO SOMEONE', 'Draft a message.', 'Your messaging app opens with a draft. You review the recipient and send it yourself.', '<form class="modal-form" id="message-form"><label>Phone number <span class="optional">OPTIONAL</span><input id="message-recipient" type="tel" maxlength="40" placeholder="Include country code if needed"><small>Leave blank to choose a recipient in your messaging app.</small></label><label>Message<textarea id="message-body" required maxlength="2000" placeholder="Write your message here..."></textarea></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Open message draft ' + icon('arrow') + '</button></div></form>');
  }
  function showShare() {
    openModal('SEND IT YOUR WAY', 'Share a draft.', 'Choose an installed app using Android’s share sheet. Ultron never posts for you.', '<form class="modal-form" id="share-form"><label>Your text<textarea id="share-text" required maxlength="3000" placeholder="Write something to share..."></textarea></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Choose an app ' + icon('arrow') + '</button></div></form>');
  }
  function showHud() {
    var status = capabilities.hudRunning ? 'The HUD is on. Tap it to return to Ultron, or long-press it to read accessible labels from the app currently on screen.' : 'A small floating button can appear above your other apps. Tap it to return to Ultron; long-press it to read visible accessible labels.';
    var buttons = capabilities.hudRunning ? '<button class="button subtle" data-action="stop-hud">Turn off HUD</button>' : '<button class="button primary" data-action="start-hud">Enable floating HUD ' + icon('arrow') + '</button>';
    openModal('YOUR ASSISTANT, WITHIN REACH', 'Meet your HUD.', status, '<div class="modal-note">' + icon('shield') + '<span>Android will ask for “display over other apps” and may ask for notification permission to show a stop control. The HUD is optional and can be turned off at any time. Screen reading also requires separate accessibility permission.</span></div><p class="modal-status">Status: ' + (capabilities.hudRunning ? 'On' : capabilities.overlayAllowed ? 'Permission granted · HUD off' : 'Off · permission not granted') + '</p>', buttons);
  }
  function showScreen() {
    openModal('SCREEN ASSISTANCE', 'A little more context.', 'Ultron can read text and content descriptions exposed by the currently visible app—not a live video or screenshot.', '<div class="modal-note">' + icon('shield') + '<span>Accessibility access is optional. Password fields are skipped, but other visible text can be sensitive. A one-time snapshot is saved in local chat and is not automatically sent to your model.</span></div><p class="modal-status">1. Enable Ultron in Android Accessibility settings.<br>2. Turn on the floating HUD.<br>3. In another app, long-press the HUD to capture visible labels.<br><br>Status: ' + (capabilities.accessibilityEnabled ? 'Accessibility enabled' : 'Accessibility not enabled') + '</p>', '<button class="button subtle" data-action="hud">Set up HUD</button><button class="button primary" data-action="accessibility-settings">Open settings ' + icon('arrow') + '</button>');
  }
  function showTap(value) {
    openModal('ONE ACTION AT A TIME', 'Tap a visible button.', 'After you confirm, switch to the target app. Ultron will try one exact label match after 5 seconds.', '<div class="modal-note">' + icon('shield') + '<span>Requires accessibility access. Some sensitive labels (including send, pay, buy, delete, install, and confirm) are blocked, but this cannot identify every risky control. Open the correct app and avoid sensitive actions. Ambiguous or hidden targets are not tapped.</span></div><form class="modal-form" id="tap-form"><label>Exact visible button label<input id="tap-label" required maxlength="80" value="' + e(value || '') + '" placeholder="e.g. Open menu"></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Confirm &amp; start countdown</button></div></form>');
  }
  function showVoice() {
    var body = '<div class="voice-circle">' + icon('mic') + '</div><p class="voice-copy">Speak when Android’s voice prompt appears.</p><label class="setting-toggle voice-switch"><span><strong>Continuous conversation</strong><small>Listen again after each spoken reply while Ultron is open.</small></span><input type="checkbox" id="live-voice" ' + (liveVoice ? 'checked' : '') + '><span class="switch" aria-hidden="true"></span></label><div class="modal-note">' + icon('shield') + '<span>Voice requires your microphone permission and a speech service on your phone. That service may send audio to its provider. Nothing listens in the background.</span></div>';
    var buttons = (liveVoice ? '<button class="button subtle" data-action="stop-voice">Stop conversation</button>' : '') + '<button class="button primary" data-action="start-voice">Start listening ' + icon('mic') + '</button>';
    openModal('A MORE NATURAL HELLO', 'Talk to Ultron.', 'A little conversation can go a long way.', body, buttons);
  }
  function showMore() {
    openModal('EXPLORE YOUR SPACE', 'A little more.', 'Everything else is right here.', '<div class="modal-link-list"><button data-nav="automations">Automations ' + icon('chevron') + '</button><button data-nav="connections">Connections ' + icon('chevron') + '</button><button data-nav="settings">Settings ' + icon('chevron') + '</button></div>');
  }
  function showAgentModel(id) {
    var agent = C.getAgent(id); var current = state.settings.agentModels[agent.id] || '';
    openModal('A MIND OF ITS OWN', 'Choose a model.', 'Set a model for ' + agent.name + ', or leave this blank to use your default model.', '<form class="modal-form" id="agent-model-form" data-agent="' + e(agent.id) + '"><label>Installed model name<input id="agent-model-name" maxlength="120" value="' + e(current) + '" placeholder="Use default model"><small>The model must already be installed on your configured server.</small></label><div class="modal-actions"><button type="button" class="button subtle" data-modal-close>Cancel</button><button class="button primary" type="submit">Save model</button></div></form>');
  }
  function confirmClear(kind) {
    if (kind === 'chat') openModal('A FRESH START', 'Clear this conversation?', 'This removes your local chat history. Your memories and model settings will stay.', '', '<button class="button subtle" data-modal-close>Cancel</button><button class="button primary" data-confirm="chat">Clear chat</button>');
    else openModal('YOUR DATA, YOUR CHOICE', 'Clear local app data?', 'This removes conversations, notes, model settings, and the stored token from this app. Android permissions must be disabled separately in system settings.', '', '<button class="button subtle" data-modal-close>Cancel</button><button class="button primary" data-confirm="data">Clear everything</button>');
  }
  function handleAction(action) {
    if (action === 'voice') showVoice();
    else if (action === 'search') showSearch();
    else if (action === 'message') showMessage();
    else if (action === 'note') showNote();
    else if (action === 'open-app') showOpenApp();
    else if (action === 'share') showShare();
    else if (action === 'screen') showScreen();
    else if (action === 'hud') { refreshCapabilities(); showHud(); }
    else if (action === 'tap') showTap('');
    else if (action === 'more') showMore();
    else if (action === 'clear-chat') confirmClear('chat');
    else if (action === 'clear-data') confirmClear('data');
    else if (action === 'start-hud') { if (callNative('startHud')) { closeModal(); toast('Follow Android’s instructions to enable the HUD.'); } }
    else if (action === 'stop-hud') { if (callNative('stopHud')) { closeModal(); setTimeout(refreshCapabilities, 300); toast('Floating HUD turned off.'); } }
    else if (action === 'accessibility-settings') { if (callNative('openAccessibilitySettings')) closeModal(); }
    else if (action === 'start-voice') {
      if (!hasNative()) { toast('Voice is available in the Android app.'); return; }
      liveVoice = !!$('#live-voice').checked; closeModal(); navigate('chat');
      if (!callNative('startVoice')) liveVoice = false;
      else if (liveVoice) toast('Conversation on. Open the mic to stop.');
    }
    else if (action === 'stop-voice') { liveVoice = false; callNative('stopVoice'); closeModal(); toast('Conversation stopped.'); }
    else if (action === 'test-model') testConnection();
  }
  function testConnection() {
    var endpoint = C.validateEndpoint($('#model-url').value);
    var result = $('#model-test-result'); result.classList.remove('error');
    if (!endpoint.ok) { result.textContent = endpoint.error; result.classList.add('error'); return; }
    if (!hasNative()) { result.textContent = 'Connection testing is available in the Android app.'; result.classList.add('error'); return; }
    result.textContent = 'Checking server…';
    var id = ++testRequest;
    callNative('testModel', JSON.stringify({ id: id, url: endpoint.value, token: $('#model-token').value, model: $('#model-name').value.trim() }));
  }
  document.addEventListener('click', function (event) {
    var target = event.target.closest('button, [data-modal-overlay]');
    if (!target) return;
    if (target.hasAttribute('data-modal-overlay') && event.target === target) { closeModal(); return; }
    if (target.hasAttribute('data-modal-close')) { closeModal(); return; }
    if (target.dataset.nav) { closeModal(); navigate(target.dataset.nav); return; }
    if (target.dataset.action) { handleAction(target.dataset.action); return; }
    if (target.dataset.suggestion) { $('#chat-input').value = target.dataset.suggestion; $('#chat-input').focus(); return; }
    if (target.dataset.agentUse) { state.selectedAgent = C.getAgent(target.dataset.agentUse).id; persist(); navigate('chat'); toast(C.getAgent(state.selectedAgent).name + ' selected.'); return; }
    if (target.dataset.agentModel) { showAgentModel(target.dataset.agentModel); return; }
    if (target.dataset.deleteNote) {
      var id = target.dataset.deleteNote;
      openModal('LET IT GO', 'Delete this memory?', 'This note will be removed from your device.', '', '<button class="button subtle" data-modal-close>Cancel</button><button class="button primary" data-confirm-note="' + e(id) + '">Delete memory</button>'); return;
    }
    if (target.dataset.confirmNote) { state.notes = state.notes.filter(function (n) { return n.id !== target.dataset.confirmNote; }); persist(); closeModal(); renderMemory(); toast('Memory deleted.'); return; }
    if (target.dataset.confirm === 'chat') { modelRequest++; busy = false; pendingVoiceReply = false; if (liveVoice && hasNative()) callNative('stopVoice'); liveVoice = false; var oldMessages = state.messages; state.messages = []; if (!persist()) { state.messages = oldMessages; renderChat(); return; } closeModal(); renderChat(); renderHome(); toast('Conversation cleared.'); return; }
    if (target.dataset.confirm === 'data') { try { localStorage.removeItem(KEY); } catch (err) { toast('Could not clear local app data.'); return; } modelRequest++; testRequest++; busy = false; pendingVoiceReply = false; liveVoice = false; if (hasNative()) { callNative('stopVoice'); callNative('stopHud'); } state = defaults(); connectionVerified = false; closeModal(); renderSettings(); renderHome(); renderChat(); renderMemory(); renderAgents(); refreshCapabilities(); toast('Local app data cleared.'); }
  });
  document.addEventListener('submit', function (event) {
    var form = event.target; event.preventDefault();
    if (form.id === 'chat-form') { var input = $('#chat-input'); var text = input.value; input.value = ''; input.style.height = 'auto'; sendMessage(text, false); }
    else if (form.id === 'note-form') { if (addNote($('#note-text').value)) { closeModal(); toast('Saved to your memories.'); } }
    else if (form.id === 'search-form') { var query = $('#search-query').value.trim(); if (query && callNative('searchWeb', query)) closeModal(); }
    else if (form.id === 'open-form') { var app = $('#app-name').value.trim(); if (app && callNative('openApp', app)) closeModal(); }
    else if (form.id === 'message-form') { var recipient = $('#message-recipient').value.trim(); var body = $('#message-body').value.trim(); if (body && callNative('draftMessage', JSON.stringify({ recipient: recipient, body: body }))) closeModal(); }
    else if (form.id === 'share-form') { var share = $('#share-text').value.trim(); if (share && callNative('shareText', share)) closeModal(); }
    else if (form.id === 'tap-form') { var label = $('#tap-label').value.trim(); if (label && callNative('scheduleTap', label)) { closeModal(); toast('Switch to the target app now.'); } }
    else if (form.id === 'agent-model-form') { var id = C.getAgent(form.dataset.agent).id; var model = $('#agent-model-name').value.trim(); if (model) state.settings.agentModels[id] = model; else delete state.settings.agentModels[id]; persist(); closeModal(); renderAgents(); toast('Agent model saved.'); }
    else if (form.id === 'model-form') {
      var url = $('#model-url').value.trim(); var modelName = $('#model-name').value.trim(); var token = $('#model-token').value;
      if (!url && !modelName && !token) { state.settings.url = ''; state.settings.model = ''; state.settings.token = ''; connectionVerified = false; persist(); renderStatuses(); toast('Model connection removed.'); return; }
      var endpoint = C.validateEndpoint(url);
      if (!endpoint.ok) { $('#model-test-result').textContent = endpoint.error; $('#model-test-result').classList.add('error'); return; }
      if (!modelName) { $('#model-test-result').textContent = 'Enter an installed model name.'; $('#model-test-result').classList.add('error'); return; }
      state.settings.url = endpoint.value; state.settings.model = modelName; state.settings.token = token; connectionVerified = false; persist(); renderStatuses(); $('#model-test-result').textContent = ''; toast('Connection saved on this device.');
    }
  });
  $('#voice-replies').addEventListener('change', function () { state.settings.voiceReplies = this.checked; persist(); });
  $('#memory-search').addEventListener('input', renderMemory);
  $('#chat-input').addEventListener('input', function () { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 110) + 'px'; });
  $('#chat-input').addEventListener('keydown', function (event) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); var form = $('#chat-form'); if (form.requestSubmit) form.requestSubmit(); else form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); } });
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && $('#modal-root').firstChild) closeModal(); });
  document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') refreshCapabilities(); });

  window.UltronApp = {
    onBackPressed: function () { if ($('#modal-root').firstChild) { closeModal(); return true; } if (currentPage !== 'home') { navigate('home'); return true; } return false; },
    onCapabilities: function (json) { try { var data = typeof json === 'string' ? JSON.parse(json) : json; Object.keys(data).forEach(function (k) { capabilities[k] = data[k]; }); renderStatuses(); } catch (ignored) {} },
    onVoiceResult: function (text) { if (!text) { liveVoice = false; toast('No speech was recognized.'); return; } navigate('chat'); sendMessage(text, true); },
    onVoiceError: function (message) { liveVoice = false; toast(message || 'Voice recognition stopped.'); },
    onSpeechDone: function () { if (liveVoice && document.visibilityState === 'visible') setTimeout(function () { if (liveVoice && document.visibilityState === 'visible') callNative('startVoice'); }, 500); },
    onActionResult: function (message) { toast(message); },
    onScreenSnapshot: function (text) { liveVoice = false; navigate('chat'); addMessage('assistant', 'Visible screen labels (captured locally):\n\n' + String(text || 'No readable labels found.'), false); toast('Screen labels captured locally.'); },
    onModelResponse: function (id, json) {
      if (Number(id) !== modelRequest) return;
      busy = false; var voice = pendingVoiceReply; pendingVoiceReply = false;
      try { var result = typeof json === 'string' ? JSON.parse(json) : json; if (result.ok && result.text) { connectionVerified = true; answer(result.text, voice, true); } else { liveVoice = false; answer('I couldn’t get a response from your model. ' + (result.error || 'Check your server and model name in Settings.'), voice, false); } }
      catch (err) { liveVoice = false; answer('I couldn’t read the response from your model.', voice, false); }
      renderStatuses();
    },
    onModelTest: function (id, json) {
      if (Number(id) !== testRequest) return;
      var result = $('#model-test-result');
      try { var data = typeof json === 'string' ? JSON.parse(json) : json; result.classList.toggle('error', !data.ok); result.textContent = data.ok ? (data.modelFound === false ? 'Server reached. This model was not found.' : 'Server reached' + (data.modelFound ? ' · model found.' : '.')) : (data.error || 'Could not reach server.'); connectionVerified = !!data.ok && data.modelFound !== false && $('#model-url').value.trim().replace(/\/+$/, '') === state.settings.url && $('#model-name').value.trim() === state.settings.model; renderStatuses(); }
      catch (err) { result.classList.add('error'); result.textContent = 'Could not read server response.'; }
    }
  };
  var now = new Date();
  $('#greeting').innerHTML = e(C.timeGreeting(now.getHours())) + '<span class="greeting-spark"> ✳</span>';
  try { $('#today-date').textContent = now.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' }); } catch (ignored) {}
  renderHome(); renderAgents(); renderMemory(); renderChat(); renderSettings(); refreshCapabilities();
}());
