/* Pure, dependency-free helpers shared by the Android WebView and tests. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.UltronCore = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var agents = [
    { id: 'assistant', name: 'Ultron Assistant', subtitle: 'Your everyday co-pilot', description: 'A thoughtful all-rounder for questions, planning, and the little things.', icon: 'spark', color: 'cyan', prompt: 'You are Ultron, a helpful, honest personal assistant. Be concise, warm and practical. Do not claim to have opened apps, searched the web, read the screen, sent messages or performed actions unless the user explicitly gives you results. You have no tools in this conversation. Ask a question when essential.' },
    { id: 'code', name: 'Code Studio', subtitle: 'Build & debug', description: 'Think through code, untangle bugs, and bring your next idea to life.', icon: 'code', color: 'violet', prompt: 'You are Ultron Code Studio, a careful programming assistant. Provide correct, practical code and explain assumptions. You cannot execute code, access a repository, or build an APK from this chat; do not claim that you did. Ask for the relevant details when needed.' },
    { id: 'research', name: 'Research Guide', subtitle: 'Explore & learn', description: 'Break down a question, compare ideas, and find a clearer path forward.', icon: 'globe', color: 'blue', prompt: 'You are Ultron Research Guide. Explain clearly, distinguish facts from uncertainty, and never fabricate sources. You do not have web browsing access. Suggest search terms or ask the user for source material when current information is needed.' },
    { id: 'creative', name: 'Creative Partner', subtitle: 'Write & create', description: 'Find the right words, brainstorm new ideas, and make something yours.', icon: 'pen', color: 'amber', prompt: 'You are Ultron Creative Partner. Help brainstorm and draft useful, original content. Match the user’s requested voice and audience. You cannot post to social media or send messages; do not claim that you did.' }
  ];
  function parseCommand(text) {
    var value = String(text || '').trim();
    var match;
    if (/^(?:help|what can you do\??)$/i.test(value)) return { type: 'help' };
    if ((match = value.match(/^(?:remember|save (?:a )?note|note)\s+(.+)$/i))) return { type: 'note', value: match[1].trim() };
    if ((match = value.match(/^(?:search (?:the )?web for|search (?:the )?web|search for|google)\s+(.+)$/i))) return { type: 'search', value: match[1].trim() };
    if ((match = value.match(/^open\s+(.+)$/i))) return { type: 'open', value: match[1].trim() };
    if (/^(?:read|inspect) (?:the |my )?screen$/i.test(value)) return { type: 'screen' };
    if ((match = value.match(/^(?:tap|click)\s+(.+)$/i))) return { type: 'tap', value: match[1].trim() };
    if (/^(?:draft (?:a )?message|send (?:a )?message|message someone)$/i.test(value)) return { type: 'message' };
    return { type: 'model' };
  }
  function validateEndpoint(input) {
    var raw = String(input || '').trim();
    if (!raw) return { ok: false, error: 'Enter a server URL.' };
    try {
      var url = new URL(raw);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') return { ok: false, error: 'Use an http:// or https:// server URL.' };
      if (url.username || url.password || url.search || url.hash) return { ok: false, error: 'Remove credentials, query parameters, and fragments from the URL.' };
      return { ok: true, value: url.href.replace(/\/+$/, '') };
    } catch (e) { return { ok: false, error: 'Enter a valid server URL, including http:// or https://.' }; }
  }
  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; });
  }
  function timeGreeting(hour) {
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }
  function getAgent(id) { return agents.filter(function (a) { return a.id === id; })[0] || agents[0]; }
  return { agents: agents, parseCommand: parseCommand, validateEndpoint: validateEndpoint, escapeHtml: escapeHtml, timeGreeting: timeGreeting, getAgent: getAgent };
}));
