# Glyph Life v0.5 — Glyph animation × AI Pet × Monster Quest link

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
