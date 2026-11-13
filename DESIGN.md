# Design

## Intent

A calm, futuristic command center that feels approachable rather than intimidating. The interface emphasizes what is available, what needs setup, and what stays under the user's control.

## Visual language

- Midnight navy backgrounds, layered blue-black surfaces, fine borders and restrained cyan/mint highlights.
- Original glowing U circuit emblem in `assets/ultron-logo.png`, adapted for the launcher, HUD, and interface.
- Locally bundled Manrope and Space Grotesk fonts; no network fonts.
- Orbital motifs suggest assistance and connection without asserting that the microphone is always listening.
- Wide screens use a left workspace navigation and a right status rail; phones use bottom navigation and responsive stacked cards.

## Navigation

Overview provides shortcuts and honest status. Chat handles local commands or a configured model. Agents selects an instruction profile and optional model override. Automations exposes individual user-initiated actions. Connections explains Android, model, HUD, screen and voice capabilities. Memory manages local notes. Settings configures a model, voice replies and permissions.

## Interaction rules

Permission dialogs are preceded by explanations. Messaging and sharing are drafts. The HUD is off until enabled. Screen snapshots are on demand, and the model never sees them unless the user manually types their contents. The tap flow warns about the countdown, permissions, blocked labels and remaining risks. Empty, disconnected and error states avoid implying a model is installed or an action has succeeded.
