# Glyph Life v0.2 — Pixel animation × Pet × Monster Quest bridge

An Android app for Nothing Phone (4a) Pro combining a local pet, a 13×13 Glyph Matrix sprite/animation editor, and a versioned data bridge for a future, **separate** Monster Quest game.

## Features now in source

- 13×13 **multi-frame animation** (up to 64 frames), 100–2000 ms per frame, playback, frame duplicate/delete/add and autosave.
- Pixel editor with paint/erase/flood-fill, mirror, rotate, invert, clear and undo/redo (history in current session).
- Local image-to-pixel import using Floyd–Steinberg dithering; PNG to Pictures/GlyphLife; animation JSON import/export.
- Pet basic leveling, energy, happiness, randomized egg hatching, and local **rule-based** chat (NOT a real AI model).
- Pet snapshot JSON export for a future **separate Monster Quest** app.
- Local quest-result JSON import with pet ID check, reward limits, and consumed event IDs. This is offline, not tamper-proof.
- Optional Nothing SDK adapter for **foreground/live** Glyph Matrix output. The stub APK cannot access rear LEDs.

## Verified / not verified

- Android debug APK + unit tests: see GitHub Actions for each commit (do not infer success from a prior commit).
- On-device Glyph output: **NOT VERIFIED**; vendor SDK binary excluded by licensing.
- AI-based conversations and AI sprite generation: **NOT IMPLEMENTED**. No cloud provider or secrets bundled.
- AOD Glyph Toy, Monster Quest game, secure inter-app synchronization: **NOT IMPLEMENTED**.

## Getting an APK

Open [GitHub Actions](https://github.com/yuki19990315-hub/GlyphLIFE/actions), select the successful **Android MVP Build** run for the desired commit, then download `GlyphLife-debug-stub` from **Artifacts**. It is a debug APK for the pet/editor; its matrix hardware calls are disabled. The build step uses Android SDK 35, JDK 17 and Gradle 8.14.

## Nothing SDK

Official: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

Read its SDK license and place the AAR locally at `app/libs/glyph-matrix-sdk-2.0.aar`. That creates a hardware-capable build of the adapter, but still requires real-device testing; **do not upload or redistribute the proprietary binary**. Android manifest has the SDK permission. Phone (4a) Pro uses a 13×13 matrix and does not support Glyph Touch. Foreground display is distinct from the separate AOD Glyph Toy service.

## Source layout

- `app/src/main/java`: UI, immutable animation tools, pet and quest-result logic, storage, image dithering and PNG export.
- `app/src/stub`: safe No-op Glyph implementation, used by GitHub Actions.
- `app/src/glyph`: optional, unverified real-device Glyph SDK adapter.
- `protocol/`: pet snapshot and quest-result examples.
- `docs/`: Japanese specs, build guidance and roadmap.
- `scripts/check_protocol.py`: offline payload validation.

Source license: not assigned. Third-party Nothing SDK terms are separate.

This is original code inspired by publicly described editor capabilities. No source from the Dot. app is copied.
