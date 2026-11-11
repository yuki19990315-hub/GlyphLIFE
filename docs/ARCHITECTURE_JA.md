# Glyph Life アーキテクチャ / 連携 v0.1

```text
Glyph Life (Kotlin + Compose)
 ├─ UI: PetScreen / CreatorScreen / LinkScreen
 ├─ Model: PetState / PixelArt (pure domain)
 ├─ Storage: PetRepository (SharedPreferences → Room later)
 ├─ GlyphBridge interface
 │    ├─ stub (SDK absent)
 │    └─ glyph (official Nothing SDK installed locally)
 └─ export: JSON v1 → Android ACTION_SEND

Monster Quest (future standalone Godot 2D)
 └─ JSON import → quest state → event log → verified result import in Glyph Life
```

## Data ownership
Glyph Life maintains canonical pet data with immutable UUID. Monster Quest reads a snapshot and may return quest result events. The initial export is one-way JSON sharing, **not** an implemented sync protocol.

## JSON v1
`schemaVersion=1`; `source=glyph-life`; `pet` contains UUID, name, species, element, personality, level, experience, happiness, energy. `glyph.width=13`; `glyph.height=13`; `glyph.pixels` is 169 bits as a row-major 0/1 string (top-left to bottom-right). See `protocol/pet-schema-v1.json`.

## Future two-way link: design requirements
- Explicit Android intents and ContentProvider/FileProvider for content-URI delivery; do not blindly trust inbound extras.
- Whitelist package identity and verify signing certificates where appropriate.
- Every quest result has eventId, petId, questSessionId, timestamp, bounded rewards.
- Maintain consumed eventIds, reject duplicates and invalid rewards; handle version migrations and conflict reconciliation.
- Offline/device-local code cannot prevent all client tampering; do not claim secure competitive anti-cheat.
- No automatic data transfer without consent.

## AI boundaries
Input: pet profile + selected memories + user message. Output: reply + optional structured intent. The rules engine validates proposed actions. Text-to-pixel generation outputs editable candidate frames. No private provider key inside a distributable APK.

## Glyph constraints
Phone (4a) Pro: 13×13; device identifier `Glyph.DEVICE_25111p`; Glyph Touch unsupported, AOD Toy only. Use the official SDK and verify the ROM/software minimum. Actual display behavior and SDK API remain unverified on device.
