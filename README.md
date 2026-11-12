# GlyphLIFE v0.14 / Monster Quest v2.6

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

## New creative features (v0.12 / Monster Quest v0.4)

- **Glyph Effects Studio:** Generate seven editable 13×13 animations fully offline: rain, twinkling stars, heartbeat, fire, ripples, radar sweep, and meteors. Preview without overwriting the working drawing; load into Creator after confirmation or directly save to the gallery.
- **Glyph clock + battery Toy:** A two-line 24-hour clock plus five-LED battery gauge. Choose Pet vs Clock for the AOD Glyph Toy in the Link screen. Time and battery are read locally; AOD updates are OS-controlled (~1/minute), while the foreground preview can update more often. [Details](docs/CLOCK_TOY_JA.md).
- **Monster Fusion Laboratory:** Consume two distinct captured monsters to create a new stronger hybrid with inherited pixel art, elemental rules and bounded stats. An explicit confirmation prevents accidental consumption.
- **Monster Support Attack:** The most recently captured companion can attack once per encounter, with an elemental bonus and enemy retaliation. Support-use state is preserved in local saves.
- [Detailed implementation notes](docs/CREATIVE_FEATURES_JA.md)

All new features require physical-device verification beyond the Android build and unit tests. The standard CI APK still does **not** control Glyph hardware.

## Glyph Snake mini-game (v0.13)

The **Playground** tab now offers **Fluid** or **Snake**. Snake is an entirely offline 137-pixel mini-game with food, wall/self-collision, high-score saving and adjustable speed. With the licensed Nothing SDK installed for local testing, its frames can stream to the rear Glyph Matrix while the app is foregrounded. The last 64 frames of play can be sent to Creator or saved in the artwork gallery as an editable replay. Details: [Glyph Snake spec](docs/GLYPH_SNAKE_JA.md).

## Monster design overhaul (v0.5)

Monster Quest now keeps **two different visual assets**: a tiny 13×13 monochrome Glyph sprite for hardware display and an optional **high-resolution full-body illustration** for battles and the collection. A new advanced art direction prompt emphasizes original, formidable creatures with detailed anatomy, layered armor, wing/horn silhouettes, multicolor elemental accents and professional shading instead of cute blobs. Players may choose a dark-fantasy, mech, dragon or celestial style.

**Explicit opt-in paid service:** The battle screen has a separate button for high-resolution Gemini 3.1 Flash Image generation; this model has no free API tier (Google's 1K image output rate is about $0.067, plus input costs as of Nov 2026). An existing image can also be imported from the Android photo picker with **no additional Monster Quest image-generation API charge**. Images are stored in the app's private directory by monster ID and displayed in battles / the fusion collection; prior 13×13 art remains as the fallback. Real image API calls and output quality remain untested.

See [Monster design pipeline](docs/MONSTER_ARTWORK_JA.md).

## v2 native pixel-art monster pipeline

**Monster Quest is now designed around actual 64×64 RGBA color pixels as its master artwork**, rather than generating a 13×13 monochrome monster directly. Offline templates cover 10 anatomy types × 7 elements; imported/optional AI images are reduced to a maximum of 16 colors and stored as per-creature/per-stage PNG. The bestiary and battle UI show crisp nearest-neighbour 256×256 displays. PNG export is supported at 64 and 256 pixels.

The circular 137-LED Glyph 13×13 image is **derived only when requested**, can be previewed in Monster Quest, and can be sent to GlyphLIFE. The receiving app asks before replacing an existing drawing. Three-stage evolution preserves body identity; fusion inherits main anatomy plus a second-parent ornament. Older saved JPG art is preserved and converted for presentation. No scraped franchise training data or copyrighted sprites were used. This is an experimental procedural/AI-assisted pixel pipeline, not a professional artist-reviewed asset library.

See [Monster Design v2 specification](docs/MONSTER_DESIGN_V2_JA.md). Real device visual quality and the optional paid Gemini image request remain untested.

## Native 64px animations (v2.2)

Every monster's native color sprite generates a 4-frame idle animation and a 3-frame attack pose. The battle and collection views render the idle frames at 256x256 with nearest-neighbour interpolation. Captured or encountered monsters can export the 4-frame 256x64 PNG animation strip to `Pictures/MonsterQuest`. The visual QA GitHub Actions artifact includes a motion preview strip generated from the runtime art code, not unrelated mock-ups. This is a procedural placeholder animation; character-specific hand-animated effects remain future work.

### Animated Glyph export
The Monster Quest → GlyphLIFE transfer now carries **all four idle animation frames**, each independently downsampled from its 64×64 original to the 137-LED (logical 13×13) Glyph shape. The receiving GlyphLIFE application previews the action and asks permission before overwriting Creator's current animation. This is a source-preserving projection, not the master asset.

## Monster Design Studio v2.4 — Choose your actual creature design

- 32 procedural identity variants per anatomy/element/name, browsable in **four side-by-side native 64×64 thumbnails** per page. The selected design is shown as an animated 256×256 integer-scaled sprite and a **separate 13×13 Glyph conversion**. Any selected prototype can be exported directly as native 64×64 or nearest-neighbour 256×256 PNG without capturing it first.
- Design identity (`designSeed`) is independent of an encounter's random UUID. **The stage-1 design you preview is now exactly the one summoned into combat and preserved across saves.** Existing saves without `designSeed` retain their legacy rendering identity.
- The fusion laboratory now shows a **pixel-art preview of the future hybrid** and the Glyph derivation *before* consuming either parent. Confirmation and result use a stable visual seed based on the two parent IDs, so the actual result matches the preview.
- CI generates extra real sprite contact sheets: 10 anatomy types × 4 variants, parent-A/parent-B/result fusion, and projected 13×13 glyph portraits. These are uploaded in **MonsterQuest-PixelArt-Preview** after successful tests.
- This is procedural pixel art, not a pretrained or newly trained Pokémon/Digimon imitation model. Character-specific illustration quality and phone display still require visual review.

[Implementation and test scope](docs/MONSTER_DESIGN_V24_JA.md).

## Featured original 64px creature family (v2.5 pilot)

The new original electric quadruped **ライゼル** is a catchable ordinary encounter with **three visually distinct native 64×64 pixel-art evolution sprites**. The art was generated as an original concept, reviewed and converted into a small, 15-color indexed pixel pack; runtime loading is entirely offline. The Designer now has a quick entry for the fixed Raizel family and hides irrelevant random-variant choices. The 256×256 bestiary displays and 13×13 Glyph transfer derive from those 64px masters, as with other monsters. This is **one pilot family**, not an art-quality overhaul of every existing creature. [Scope and verification](docs/MONSTER_V25_FEATURED_JA.md).

## Monster Quest v2.6 — Modular armor over native pixel bodies

- Native 64×64 creature bodies now support an optional **6-slot armor overlay**: HEAD, CORE, TAIL (available from stage 1), SHOULDER and BACK (stage 2), and WING (stage 3).
- Each slot is **off** or one of four original, local pixel styles: PLATE, SPIKE, CRYSTAL, RUNE. Armor is composed only with colors already used by that monster's 64px art; even the featured 15-color native pixel creature remains within its palette. No AI/network is required for manual fitting or an element-based preset.
- The Monster Design Studio shows individual controls and **optional BYOK Gemini text-only design suggestions** to pick one of the safe styles in each fixed slot. Suggestions never generate executable code, arbitrary image coordinates or battle stats, and require an explicit user tap. Android device/API calls are not verified.
- Selected pieces are persisted in the monster's save metadata. Evolving unlocks the reserved slots. Fusion inherits first-parent head/core/shoulder and second-parent tail/back/wing. The same layered master is used for animation, 256px view, 64/256 PNG export and downstream 13×13 Glyph projection.
- Existing saves (with no armor parts) retain their original appearance. Saved user images receive optional overlays at render time without overwriting the original image.
- Preview artifacts include **09-base-armor-evolution.png** and **10-armor-4-styles.png**, generated from the actual rendering code. See [technical design](docs/MONSTER_ARMOR_V26_JA.md).
