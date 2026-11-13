# Engineering and safety rules

1. Do not claim that an unconfigured model, unavailable Android feature, or unperformed action is active or complete.
2. Require explicit, contextual permission and user initiation for microphone, HUD, accessibility snapshots and taps. Never silently enable Android settings.
3. Do not send SMS, post content, initiate payments, or run model-suggested device actions automatically. Use reviewable Android drafts or share sheets.
4. Accessibility must not record events or read continuously. Skip password nodes; do not automatically send snapshots to a model. Tap at most one unique exact label per explicit request, and block system screens and common sensitive labels. Recognize that label filtering is imperfect.
5. Keep bundled UI local, escape untrusted content, avoid remote pages in the bridge-enabled WebView, and do not introduce analytics or background uploads.
6. Treat model servers and speech services as external trust boundaries. Disclose cleartext HTTP, unencrypted local token storage, model availability and provider privacy.
7. Keep model weights and generated build caches out of Git. The single small signed APK is intentionally tracked because the requested deliverable is a direct GitHub download.
8. Do not commit signing keys, credentials, user data, or real conversation contents.
9. Test source, model gateway, UI, APK signing and package metadata; distinguish mocked browser tests from actual Android-device testing.
