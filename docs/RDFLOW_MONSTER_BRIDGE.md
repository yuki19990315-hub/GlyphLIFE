# RD Flow 64 -> Monster Quest (optional desktop art pipeline)

**Status:** integration adapter for art assets, **not** an embedded Android AI model.
Existing Monster Quest `CaptureLab` already imports individual full-body images into a
captured creature. This bridge creates compatible 64×64 transparent PNG files.
The AI-generated creature remains editable via the existing species/fusion/evolution
metadata; the artwork is stored separately as native per-stage PNG.

## License boundary

Upstream source: https://github.com/Retro-Diffusion/rd-flow-64
Model: https://huggingface.co/Retro-Diffusion/rd-flow-64

Read the upstream **MODEL_LICENSE.md**, version 1.0 (2026-11-25), before use:
- Model weights and derivatives are non-commercial only; never ship them in the
  Android APK or a commercial image-generation service.
- Outputs may be used as game assets, including commercially, subject to law.
- Outputs cannot be used to train, fine-tune, or improve another AI model.
- Upstream code is MIT. Model license is **not** MIT.
- Text encoder is separately Apache-2.0 licensed.

No external API, account, or network connection is required after local weights
are downloaded. The **first** RD Flow execution downloads approximately 6.8GB.
RD Flow's upstream desktop implementation supports Windows, Linux and macOS.
We have **not** verified local AI inference on Android.

## Step 1: Install upstream RD Flow on PC

Use upstream README for your OS. Python 3.10–3.13 and around 8GB free disk space
are required.

```bash
git clone https://github.com/Retro-Diffusion/rd-flow-64
cd rd-flow-64
python -m venv .venv
# Activate .venv using the OS-specific instruction in upstream README
pip install torch
pip install -r requirements.txt
```

Install Pillow in the Python environment used by the bridge:
```bash
pip install Pillow
```

## Step 2: Generate 64px art and normalize into game quality

Write a **120–200 English word caption** describing one creature: anatomy from
head to tail, pose and facing, full-body composition, safe margins, outline,
shading and colors. Follow upstream `PROMPTING.md`. Include:
"The whole subject is visible with a clear margin on every side and nothing
cut off. The background is transparent."

Save it in `monster_caption.txt`. Then run from the GlyphLIFE repository root:
```bash
python tools/rdflow_monster_bridge.py \
  --rdflow-root /path/to/rd-flow-64 \
  --caption-file monster_caption.txt \
  --name thunder_wolf --stage 1 --seed 1001 --out rdflow-output
```

On Windows PowerShell, use a single line instead of the backslash continuation.

Alternative **without model installation**: generate a transparent 64×64 image
with the RD Flow web demo, manually save its PNG and run:
```bash
python tools/rdflow_monster_bridge.py --source-png wolf.png \
  --name thunder_wolf --stage 1 --out rdflow-output
```

The bridge:
1. accepts **only real 64×64** images; does not shrink 512px art;
2. normalizes alpha to transparent or opaque (no partial alpha);
3. fits the full body within a 2px safety margin using nearest-neighbor;
4. quantizes opaque pixels to **at most 15 colors plus transparency**;
5. saves an import-ready PNG and JSON metadata audit.

Review the appearance manually. Passing this audit does **not** guarantee a
recognizable monster or a valid action animation. The `reviewed_by_human` field
defaults to false; do not auto-register unreviewed art.

## Step 3: Import in Monster Quest

Open the game -> **仲間 / モンスター詳細** -> choose the intended monster ->
**全身画像を取り込む**. Choose `rdflow-output/thunder_wolf_s1_64.png`.

Import is per **monster ID and evolution stage**. Generate a separate
`--stage 2` or `--stage 3` image for later evolution designs.
The original PNG is retained on-device. Runtime rendering applies the
existing 16-color audit, armor compositing, animation and 13×13 Glyph
projection. Importing does **not** overwrite genes, fusion lineage, level,
skills, or old save files.

## Verification

```bash
python -m unittest tools/test_rdflow_monster_bridge.py
```

This test does **not** download the model and only verifies the image processing.
The bridge's source code and app UI are checked by GitHub CI; actual RD Flow
generation quality, GPU/CPU compatibility on a user's PC and Android device
interaction require separate checks.

## Important limitations

- No model weights, Qwen prompt reader or generated examples are committed.
- The tool doesn't generate animation sheets, per-frame skeletal motion,
  evolved appearances or DNA inheritance automatically.
- No in-app text-to-image UI: the existing Android app can import the
  generated file, but all AI generation remains on the desktop.
- Never use RD Flow outputs as training inputs for GlyphLIFE's AI system.
