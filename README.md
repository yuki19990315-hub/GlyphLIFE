# GlyphLIFE v0.10 / Monster Quest v0.3

Two **independently installable Android apps** in one repository:

- **GlyphLIFE** (`app/`, `dev.glyphlife.app`): Nothing Phone (4a) Pro pet care, 13×13 Glyph animation editor, optional AI, fluid simulation, NFC ring mode switching and a companion gallery.
- **Monster Quest** (`monsterquest/`, `dev.glyphlife.monsterquest`): 4×4 / 5-room mobile RPG with random encounters, final-floor bosses, elemental combat, capture, and optional AI-created monster designs.

The apps exchange **versioned JSON v1** over Android's intent system. Both function on their own.

## Verified CI status vs. actual device status

| Capability | Status |
|---|---|
| GlyphLIFE SDK-free debug APK | GitHub Actions build + unit tests pass |
| Monster Quest debug APK | GitHub Actions build + unit tests pass |
| Official Nothing Glyph Matrix SDK 2.0 adapter | **Compilation passes** using the official SDK downloaded into an isolated CI job |
| NFC, accelerometer / tilt, real Glyph Matrix lighting | **Not tested on a physical Phone (4a) Pro** |
| Glyph Toy / Always-on Display service | Source and SDK compilation implemented; **real AOD display not tested** |
| Google Gemini cloud conversation and artwork generation | API code implemented; **network calls untested without a user's API key** |

This is an **experimental prototype**, not a published or production-tested release. A compiled app is not evidence that its hardware features function.

## GlyphLIFE features

- Physical 137-LED circular mask in a logical 13×13 grid; the unpopulated corners are disabled.
- 1–64-frame animation editor, 100–2000ms frame times, live playback, paint/erase/drag/fill, flip, rotate, invert, Undo/Redo.
- Photo-to-Glyph conversion, PNG sprite-sheet export, portable animation JSON import/export.
- Named artwork gallery (up to 30), per-pet drawings and chat history, up to 20 pets.
- Procedural pet species/egg generation, play/feed/rest, deterministic growth stages and 3-frame blink/hop sprites.
- Optional `gemini-3.5-flash-lite` chat and AI-generated binary 13×13 frames with a user-supplied API key kept only in RAM; no private key embedded in the APK.
- **Glyph Fluid Lab:** falling-water cellular automaton for 137 LED positions, adjustable simulation speed and gravity, accelerometer tilt direction (unverified on device), and transfer into Creator.
- **NFC ring (experimental):** enroll an NFC tag and cycle Pet → Creator → Fluid in the foreground. Only a SHA-256 hash of the UID is stored. NFC UIDs are not trustworthy IDs for access control.
- SDK-backed foreground Glyph display via `setAppMatrixFrame`, and a Phone (4a) Pro AOD Glyph Toy service with approximately one AOD frame per minute. These are **different** display mechanisms.

## Monster Quest features

- Five procedurally populated rooms with at least one navigable path to each encounter.
- Final-floor boss, elemental attacks, defensive stance, two healing items, capture mechanics and local save/restore.
- Original 13×13 pixel creature previews, including deterministic fallback portraits for offline play.
- Optional Gemini text/photo-to-monster generation (name, element, description, 13×13 design only); combat stats are determined by game rules, not by untrusted AI output.
- Import a pet from GlyphLIFE using Android's share/explicit intent, export quest rewards back. GlyphLIFE checks pet ID, reward limits and event IDs to prevent double application. The JSON is not cryptographically authenticated.

## Get the SDK-free APKs

Open [GitHub Actions](https://github.com/yuki19990315-hub/GlyphLIFE/actions), choose a **successful** run for the desired commit, and download:

- `GlyphLife-debug-stub`: full screen-side prototype but **does not control the rear LEDs**.
- `MonsterQuest-debug`: the separate RPG, requiring no Nothing SDK.

## Build a personal Nothing Phone (4a) Pro test APK

Official SDK: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

The SDK is proprietary and subject to Nothing's license, including distribution and commercial-use restrictions. It is **not committed, uploaded as a GitHub artifact, or bundled into the normal downloadable test APK**.

On a Windows development PC with Android Studio/JDK, run the helper after reviewing the SDK license:

```powershell
.\scripts\build_glyph_windows.ps1
# Optional: install to a connected phone with USB debugging
.\scripts\build_glyph_windows.ps1 -InstallToPhone
```

The script downloads the official SDK directly to your development PC, obtains Gradle 8.14 from the official distribution (SHA-256 checked), and produces a local debug APK. It has **not been tested on Windows**. See [Windows setup instructions](docs/WINDOWS_GLYPH_BUILD_JA.md).

The SDK requires **minSdk 33** in the SDK-enabled variant, whereas the SDK-free app targets minSdk 29. The official SDK 2.0 was used in CI to compile the Glyph adapter; device runtime behavior remains unverified.

## Design and docs

- [Product specification (JP)](docs/PRODUCT_SPEC_JA.md)
- [Roadmap (JP)](docs/ROADMAP_JA.md)
- [Architecture / JSON contract (JP)](docs/ARCHITECTURE_JA.md)
- [Monster Quest MVP (JP)](docs/MONSTER_QUEST_MVP_JA.md)
- [Boss combat (JP)](docs/BOSS_BATTLE_JA.md)
- [Fluid simulation (JP)](docs/GLYPH_FLUID_JA.md)
- [NFC ring design (JP)](docs/NFC_RING_JA.md)
- [Glyph SDK compatibility (JP)](docs/GLYPH_SDK_AUDIT_JA.md)
- [Dot. research (JP)](docs/DOT_REFERENCE_JA.md)
- [Current release status (JP)](docs/RELEASE_STATUS_JA.md)
- [Gemini AI privacy / key handling (JP)](docs/AI_PROVIDER_JA.md)
- [Data format](protocol/pet-schema-v1.json)

## Technology and license

Kotlin / Jetpack Compose, Android SDK 35, JDK 17, Gradle 8.14; local SharedPreferences/JSON storage for the MVP. Future larger-scale data storage can be migrated to Room. Original project source was written independently; [Dot.](https://github.com/kavastore/nothing-dot) was used as a feature reference, not copied.

No general license has been granted to the original project code. Nothing's proprietary SDK license is separate. Do not publish APKs with proprietary vendor binaries without permission.
