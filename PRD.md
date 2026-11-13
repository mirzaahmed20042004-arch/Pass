# Product requirements

## Product

Ultron Ai is a user-controlled Android assistant prototype, designed for quick conversations and everyday device actions with clear permission boundaries.

## Implemented scope

1. An original conceptual logo, responsive command-center UI, and navigation for overview, chat, agents, automations, connections, memory and settings.
2. Local notes, simple local commands, chat history, per-profile model choices, and an optional Ollama-compatible HTTP connection.
3. Press-to-talk and spoken replies through Android's installed speech services, plus an opt-in foreground conversation loop.
4. User-initiated Android app opening, web search, SMS draft and system share sheet.
5. Optional floating overlay HUD, on-demand accessible-label snapshots, and one user-confirmed, exact-label tap with a countdown and conservative restrictions.
6. An optional Python gateway, Android source/build scripts, and automated non-device tests.

## Acceptance boundaries

- No model, speech service, social integration, or unlimited third-party service is promised as bundled or free forever.
- No automatic sending/posting, unrestricted cross-app operation, continuous screen recording, invisible background listening, remote code execution, or automatic permission grant.
- Accessibility is opt-in and can reveal sensitive non-password text; taps are conservative but cannot guarantee safety. Users remain responsible for reviewing actions.
- Android 5.0+ is the configured minimum, with a current System WebView; exact functionality depends on the OS, installed services, and target app. Physical-device testing remains required.
- Local data is private to the app sandbox but not application-level encrypted; users can clear it. Sending model prompts or using Android providers can transmit data off-device.
