# Glyph Life — AI Pet × Glyph Creator

**Glyph Life** is an Android app combining an AI-pet concept and a 13×13 Glyph Matrix pixel editor for Nothing Phone (4a) Pro. A separate future game, **Monster Quest**, will exchange versioned pet data with it.

**Current status (2026-11-11): Android MVP source added; APK compilation and on-device Glyph output NOT YET VERIFIED.** This is a development prototype. AI chat, AI sprite generation, animations, evolution, Monster Quest game and bidirectional sync are not implemented.

## MVP features
- One default pet ("ピコ") with Play / Feed / Rest interactions, leveling and local persistence.
- 13×13 bitmap edit / erase / reset; local storage and pet sprite preview.
- Manual JSON v1 pet snapshot export through Android's share sheet.
- Optional adapter for Nothing's proprietary Glyph Matrix SDK, excluded from the repository.
- GitHub Actions stub (no Nothing SDK) debug APK build and unit tests.

## Project structure
- `app/` — Kotlin, Jetpack Compose, SharedPreferences, pet state, pixel grid, optional Glyph bridge.
- `docs/PRODUCT_SPEC_JA.md` — Japanese requirements and acceptance criteria.
- `docs/ARCHITECTURE_JA.md` — interfaces, storage and Monster Quest protocol.
- `docs/ROADMAP_JA.md` — phased plan and verified/unverified features.
- `docs/NOTHING_SDK_JA.md` — official SDK integration notes.
- `protocol/` — JSON Schema and sample; `scripts/check_protocol.py` — validator.

## Build
Use Android Studio / JDK 17 / Android SDK 35 with Gradle 8.14. The project does **not** currently bundle Gradle Wrapper. Run `gradle :app:testDebugUnitTest :app:assembleDebug` in an Android SDK environment. GitHub Actions is configured to run the same tests/build. Successful CI is not yet confirmed.

To attempt real hardware Glyph output, review Nothing's SDK license and manually put the compatible SDK AAR at `app/libs/glyph-matrix-sdk-2.0.aar`; the `src/glyph` adapter is still untested against hardware. Without it, the app uses `src/stub`, so editor/pet functionality remains usable but Glyph output is disabled.

## Data ownership
Glyph Life is the authoritative store for pet stats. Monster Quest will import a snapshot and later return validated quest events by unique IDs, preventing duplicate awards. Initial JSON export is **not** live sync.

## Security and licensing
Do not hard-code cloud AI secrets in the APK. Do not commit Android signing keys or Nothing SDK binaries. This repository's original source currently has no general open-source license. Vendor SDK terms are separate. Before commercial release, confirm vendor requirements.

Official SDK: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
Android Compose: https://developer.android.com/develop/ui/compose
