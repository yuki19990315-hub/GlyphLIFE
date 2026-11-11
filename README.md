# Glyph Life v0.7 — two Android apps: Glyph Life + Monster Quest

Android app for Nothing Phone (4a) Pro. It combines virtual pet care, a 13×13 pixel-art/animation editor, optional Gemini AI, and JSON exchange with a **future separate** Monster Quest game.

## Implemented features
- Circular 13×13 editor with a **137-LED physical mask**: the 32 unwired corners are not editable.
- 13×13 animation timeline: 1–64 frames, 100–2000 ms/frame, looping playback and auto-save.
- Draw, erase, flood fill, 90° rotate, mirror, invert, clear, duplicate/add/delete and in-session undo/redo.
- Convert imported photos to Glyph pixels (Floyd–Steinberg), export PNG, import/export animation JSON.
- Pet: play/feed/rest, level/happiness/energy, up to **20 saved pets**, persistent active selection, per-pet sprite and per-pet chat history, randomized hatch, offline rule-based fallback.
- Original offline pet sprite generator (5 species, blink/hop 3-frame animation, growth stages at Lv5 and Lv12). A confirmation protects existing hand-drawn art from overwrite.
- **Optional Gemini AI chat and text-to-13×13 animation generation** (Google API key entered by the user; not embedded or persisted, actual API requests NOT YET TESTED). Model `gemini-3.5-flash-lite`. Only sends on explicit user action; provider terms/rates apply.
- Monster Quest pet export; import individual quest-result JSON with basic validation and duplicate event ID protection.
- Foreground Glyph SDK adapter that sends frames for real-device live playback if user locally installs official SDK AAR.
- AOD Glyph Toy Service included in SDK builds: shows one saved animation frame per OS AOD tick (about once a minute), not fast playback. AOD is disabled in the SDK-free APK. SDK-free debug APK uses a stub; real-device output is **NOT YET VERIFIED**.

## Build & tests
Open [GitHub Actions](https://github.com/yuki19990315-hub/GlyphLIFE/actions). On a successful run for a particular commit, download the `GlyphLife-debug-stub` artifact. This APK supports all screen features and BYOK AI but **does not control the rear Glyph LEDs**. Built with Android SDK 35, JDK 17, Gradle 8.14. Run locally with `gradle :app:testDebugUnitTest :app:assembleDebug`.

## Optional hardware SDK
The proprietary Nothing Glyph Matrix SDK **is not included**. Read https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit and its license. To build the real SDK adapter, place `glyph-matrix-sdk-2.0.aar` in `app/libs/` on your own development machine, then rebuild and validate on a Phone (4a) Pro. No hardware success can be claimed without that step. AOD Toy is separate and currently NOT implemented.

## Optional AI (bring your own key)
Google Gemini REST API, model `gemini-3.5-flash-lite`. Create an API key in Google AI Studio, review Google's terms/free-tier eligibility and costs, and type it into the Pet or Creator screen. It is kept in memory only, not saved on disk. Chat sends your recent messages and pet profile; pixel generation sends your requested subject. If there is no key, offline pet replies and the manual editor still work. API availability depends on your account/region; generation can fail and existing data stays intact. See [AI provider notes](docs/AI_PROVIDER_JA.md).

## Source
- `app/src/main` Kotlin/Jetpack Compose app: pet, pixel editor, network client, JSON storage, photo conversion.
- `app/src/stub` Glyph no-op (build/test without vendor SDK).
- `app/src/glyph` official SDK adapter (requires manual AAR).
- `protocol/` JSON v1 examples and schemas.
- `docs/` specifications, validation limits, roadmap, Dot. research.

Original project code, **not copied** from Dot. Reference: https://github.com/kavastore/nothing-dot

**Not yet implemented or verified:** AOD Glyph Toy on real hardware (source included), native on-device generative model, real-world Gemini network test, real-device LED test, Monster Quest game, secure inter-app sync, evolving species models. App code currently has no open-source license.

## Separate Monster Quest Android APK (MVP)

This repository also contains **`:monsterquest`**, a second independently installable Android app (`dev.glyphlife.monsterquest`). It includes a 4×4 five-room procedural exploration game, basic turn-based encounters, elemental magic, monster capture, and offline save data. Enemy encounters work offline using procedural rules. An optional BYOK Gemini feature can generate monster names, descriptions and 13×13 pixel portraits from text or a user-selected photo; live API behavior is not yet verified.

For two-way app linking: open Glyph Life → 連携 → ペットデータを共有 → select Monster Quest; after the adventure, Monster Quest → 報酬をGlyph Lifeへ送る → select Glyph Life. Both apps accept `ACTION_SEND/application/json` payloads and validate the v1 contract. Duplicate reward event IDs are refused by Glyph Life.

GitHub Actions builds **two APKs**: `GlyphLife-debug-stub` and `MonsterQuest-debug`. Current run must show success before installing. Monster Quest needs no Glyph SDK.

Monster Quest details: [docs/MONSTER_QUEST_MVP_JA.md](docs/MONSTER_QUEST_MVP_JA.md). Hardware Glyph display still requires the optional SDK AAR and a real-device test.

### Optional Monster Quest Gemini generation

In Monster Quest, provide your own Gemini API key to generate a custom monster from text or a photo. The key is held in memory only, never committed or persisted. If a photo is selected, the app downsamples it locally and explicitly sends a JPEG to Google's Gemini API. The AI only designs monster identity, description and a monochrome 13×13 portrait; game stats are derived from fixed rules to avoid out-of-range AI-generated values. No model is available offline, and Google's pricing/free-tier/privacy conditions apply. Actual network requests are **not tested** without a key. See [Monster Quest spec](docs/MONSTER_QUEST_MVP_JA.md).

## Glyph Fluid Lab (v0.9)

A dedicated foreground-only 13×13 cellular-fluid screen uses the actual 137-LED circular mask. It can simulate gravity in four directions, add droplets, change playback speed and copy a frame to Glyph Creator. The screen runs completely offline and can optionally stream frames to the Glyph Matrix on a vendor-SDK-enabled local build. The SDK-free GitHub Actions APK only displays it on the phone screen. See [Fluid Lab implementation notes](docs/GLYPH_FLUID_JA.md).

## NFC Ring Mode Switch (v0.10)

Glyph Life can optionally enroll an NFC ring and use it to cycle the **Pet → Creator → Fluid** screen/matrix display while the app is foregrounded. Enrollment stores a SHA-256 fingerprint of the tag UID locally; no UID or NFC content is uploaded. NFC UID is spoofable or randomized on some tags, so this is **not authentication** and must not protect sensitive data. Without the Nothing SDK AAR, the ring can switch the visible screen, but cannot control hardware Glyph LEDs. Real NFC detection and Glyph output remain untested on a Phone (4a) Pro. See [NFC instructions](docs/NFC_RING_JA.md).

## New in the current development branch

- Monster Quest final-floor bosses, guarding, limited healing items, and elemental pixel-art portraits.
- Direct app-to-app JSON transfer with share-sheet fallback and delayed AI reply isolation per pet.
- Fluid Lab motion driven by the phone accelerometer (optional, device test pending).
- NFC ring enrollment for foreground-only mode cycling: Pet / Creator / Fluid (requires a stable NFC tag UID).
- A vendor-SDK compile-only GitHub Actions job downloads the official Nothing AAR temporarily and **does not upload or distribute the SDK or an SDK-enabled APK**. Always inspect the latest CI run rather than assuming success.
