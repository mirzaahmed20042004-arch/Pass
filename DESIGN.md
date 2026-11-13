# Design System & Brand Identity — NoFilter Ai (`DESIGN.md`)

## 1. Conceptual Logo & Iconography
The **NoFilter Ai** visual identity centers on a custom conceptual emblem (`assets-src/logo_pick.png` & `docs/logo.png`):
- **Metaphor:** A luminous **camera aperture ring** (symbolizing unfiltered clarity, vision, and optical OCR) encircling an intricate **neural network brain** (symbolizing multi-expert reasoning and persistent memory) set against a deep cosmic navy starfield (`#0B0F1A`).
- **Adaptive & Legacy Icons (`tools/make_icons.py`):**
  - **Adaptive Icon (`API 26+`):** `@color/ic_launcher_background` (`#0B0F1A`) paired with `@drawable/ic_launcher_foreground` (`432×432` transparent canvas with the cropped neural-aperture emblem centered cleanly within the `66dp` Android safe zone, wordmark excluded so launcher masks never clip text).
  - **Legacy Mipmaps (`mdpi` 48px through `xxxhdpi` 192px):** Anti-aliased Lanczos downscales with a `22%` rounded-square alpha mask (`ic_launcher.png` and `ic_launcher_round.png`).

---

## 2. Color System (`res/values/colors.xml` & `themes.xml`)
All screens bind colors through custom theme attributes (`?attr/nfBackground`, `?attr/nfSurface`, `?attr/nfSurface2`, `?attr/nfText`, `?attr/nfMuted`, `?attr/nfBorder`, `?attr/nfAccent`, `?attr/nfAccent2`) so switching between Dark and Light modes updates the entire UI seamlessly.

| Semantic Token | Theme Attribute | Dark Mode (`Theme.NoFilter`) | Light Mode (`Theme.NoFilter.Light`) | Role |
| :--- | :--- | :--- | :--- | :--- |
| **Background** | `?attr/nfBackground` | `#0B0F1A` (Deep Navy) | `#F6F8FC` (Cool Alabaster) | Screen canvas & status/nav bars |
| **Surface** | `?attr/nfSurface` | `#121829` (Midnight Slate) | `#FFFFFF` (Pure White) | Top app bars, cards, sheets |
| **Surface 2** | `?attr/nfSurface2` | `#1A2238` (Elevated Indigo) | `#EEF2FA` (Soft Ice) | AI message bubbles, input pills |
| **Primary Text** | `?attr/nfText` | `#E9EDF9` (Crisp Starlight) | `#0F172A` (Deep Ink) | Body copy & headings |
| **Muted Text** | `?attr/nfMuted` | `#8E9BB8` (Muted Steel) | `#64748B` (Slate Gray) | Timestamps, expert badges, hints |
| **Border** | `?attr/nfBorder` | `#25304D` (Subtle Navy Line) | `#CBD5E1` (Hairline Gray) | Dividers & input strokes |
| **Primary Accent** | `?attr/nfAccent` | `#8B5CF6` (Electric Violet) | `#7C3AED` (Royal Violet) | Primary buttons, FAB, focus |
| **Secondary Accent** | `?attr/nfAccent2` | `#22D3EE` (Neon Cyan) | `#0891B2` (Deep Cyan) | Links, inline code, active web icon |
| **User Bubble** | Gradient | `#7C3AED` → `#2563EB` | `#7C3AED` → `#2563EB` | Horizontal violet-to-blue gradient |
| **Code Fence** | `codeBg` | `#0A0E1A` | `#0A0E1A` | Monospace code blocks |

---

## 3. Typography & Markdown Rendering (`util/Markdown.kt`)
- **Scalable Chat Typography:** Users can scale chat message text smoothly from **85% to 130%** in Settings (`Prefs.fontScale`), defaulting to `15sp` (`100%`).
- **Native Spannable Markdown:** Instead of spawning heavy WebViews per chat bubble, `Markdown.kt` renders rich formatting natively into Android `SpannableStringBuilder`:
  - `#`, `##`, `###` headings with proportional `RelativeSizeSpan` (`1.30×`, `1.18×`, `1.08×`) and bold weight
  - Fenced ` ```code``` ` blocks with monospace typeface and dark code surface background
  - Inline `` `code` `` highlighted in cyan (`nfAccent2`)
  - `**bold**`, `*italic*`, `~~strikethrough~~`, `> blockquotes`, bullet lists (`•`), and clickable `[title](url)` links (`LinkMovementMethod`).

---

## 4. Screen-by-Screen UX Flows
1. **Splash Screen (`activity_splash.xml`):** Centered icon, bold `34sp` title, tagline, and violet indeterminate progress indicator (`1.3s`).
2. **Home / Conversations (`activity_main.xml`):** Clean single-row header with app emblem + 5 icon actions (*Search, OCR Studio, Memory Vault, Personas, Settings*), collapsible instant-filter bar, conversation list with last-message preview + relative timestamp (`now`, `5m`, `2h`, `Nov 13`), empty-state illustration, and bottom-right `+` FAB.
3. **Chat Screen (`activity_chat.xml`):**
   - **Header:** Back button, tappable chat title (opens *Chat Tools*: Starter prompts, Rename, Share Markdown, View sources), active persona + web/offline status subtitle, live Globe toggle (`🌐`), Sources button (`🔗`), and Persona switcher (`🎭`).
   - **Message Stream:** Bottom-anchored `ListView`, streaming typewriter updates (`32 chars / 14ms`), live status bar (*"Checking Wikipedia…"*, *"Reading 3 sources…"*, *"Combining 4 expert models…"*), and expert provenance footer (`🌐web 🧠knowledge 🧮math  now`).
   - **Composer Bar:** Image-to-OCR attach button (`📷`), multi-line rounded input pill, voice dictation button (`🎤`), and violet send button (`➤`).
4. **OCR Studio (`activity_ocr.xml`):** Large image preview frame, *Gallery* and *Camera* buttons, primary *Extract Text* CTA, horizontal percentage progress bar, live Tesseract status readout, selectable extracted-text card, and *Copy* / *Share* actions.
5. **Memory Vault & Personas Studio (`activity_list_simple.xml`):** Unified minimal list layout with `+` creation dialogs, emoji badges, recall hit counts (`used 3×`), and default-persona checkmarks.
