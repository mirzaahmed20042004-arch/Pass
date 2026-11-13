# Tasks

## Completed in v0.1.0

- [x] Original conceptual logo and responsive local-first UI.
- [x] Android shell and named navigation pages.
- [x] Local notes, chat history and deterministic command parser.
- [x] Four instruction profiles, configurable Ollama-compatible model and optional gateway.
- [x] User-initiated Android intents for app opening, web search, SMS drafts and sharing.
- [x] Opt-in speech input and TTS; optional foreground conversation loop.
- [x] Opt-in draggable HUD and accessibility-based screen snapshot / supervised exact-label tap.
- [x] Android APK build and signature verification; Android 10 emulator launch/dashboard smoke test; automated parser, gateway and mocked-bridge UI tests.

## Important follow-up

- [ ] Test on real Android 5–latest devices and multiple manufacturers, with updated/older WebView versions and permission denial/revocation cases.
- [ ] Perform a dedicated security and accessibility review, particularly the accessibility service, overlay and gateway.
- [ ] Add encrypted secret storage and a local-data export/import flow.
- [ ] Add a properly authenticated, TLS-protected deployment option and rate limiting if the gateway must be exposed beyond a trusted LAN.
- [ ] Add explicit, authorized integrations for specific social/customer platforms if requested and permitted by those services.
- [ ] Evaluate actual on-device model inference and hardware requirements; models are not bundled today.
- [ ] Create a securely managed production signing key and a release process if distributing beyond prototype testing.
