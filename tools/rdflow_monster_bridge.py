#!/usr/bin/env python3
"""RD Flow 64 -> Monster Quest sprite bridge.

This optional desktop tool does NOT bundle RD Flow model weights, run on Android,
upload art, or train on generated outputs. Use upstream RD Flow 64 separately.
Requires Pillow; use --source-png to process a pre-generated image offline.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from PIL import Image

SIZE = 64
MAX_OPAQUE = 15
MARGIN = 2

def normalize(source: Image.Image) -> tuple[Image.Image, dict]:
    """Strict 64px RGBA, one transparent colour + <=15 opaque colours."""
    if source.size != (SIZE, SIZE):
        raise ValueError("RD Flow sprite must be exactly 64x64; never rescale a larger image")
    rgba = source.convert("RGBA")
    # A generated half-transparent outline is not acceptable in pixel-native art.
    mask = rgba.getchannel("A").point(lambda alpha: 255 if alpha >= 128 else 0)
    bbox = mask.getbbox()
    if bbox is None:
        raise ValueError("Image is entirely transparent")
    # Preserve silhouette but move whole creature away from cropped edges.
    visible = rgba.crop(bbox)
    w, h = visible.size
    factor = min(1.0, (SIZE - 2 * MARGIN) / w, (SIZE - 2 * MARGIN) / h)
    nw, nh = max(1, round(w * factor)), max(1, round(h * factor))
    if (nw, nh) != (w, h):
        visible = visible.resize((nw, nh), Image.Resampling.NEAREST)
    foreground_mask = visible.getchannel("A").point(
        lambda alpha: 255 if alpha >= 128 else 0
    )
    pixels = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    x0, y0 = (SIZE - nw) // 2, (SIZE - nh) // 2
    # Quantize opaque pixels only, so transparent pixels never eat a palette slot.
    rgb = Image.new("RGB", (nw, nh), (0, 0, 0))
    rgb.paste(visible.convert("RGB"), (0, 0))
    indexed = rgb.quantize(colors=MAX_OPAQUE, method=Image.Quantize.MEDIANCUT,
                           dither=Image.Dither.NONE)
    quantized = indexed.convert("RGBA")
    quantized.putalpha(foreground_mask)
    pixels.paste(quantized, (x0, y0), quantized)
    # Replace any fully transparent RGB with zero; no partially transparent pixels.
    data = [
        (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
        for r, g, b, a in pixels.getdata()
    ]
    pixels.putdata(data)
    occupied = {(r, g, b) for r, g, b, a in data if a == 255}
    assert len(occupied) <= MAX_OPAQUE, "Opaque palette exceeds Monster Quest limit"
    assert any(a == 0 for _, _, _, a in data), "Transparency is required"
    assert all(a in (0, 255) for _, _, _, a in data), "Partial alpha detected"
    bounds = pixels.getchannel("A").getbbox()
    assert bounds and bounds[0] >= MARGIN and bounds[1] >= MARGIN
    assert bounds[2] <= SIZE - MARGIN and bounds[3] <= SIZE - MARGIN
    return pixels, {
        "native_resolution": [SIZE, SIZE],
        "opaque_colors": len(occupied),
        "total_colors": len(occupied) + 1,
        "margin_px": MARGIN,
        "source_bbox": list(bbox),
        "normalized_bbox": list(bounds),
        "pixel_art_filter": "nearest",
    }

def run_rdflow(root: Path, caption: str, seed: int, raw_dir: Path) -> Path:
    generate = root / "generate.py"
    if not generate.is_file():
        raise FileNotFoundError(f"RD Flow 64 generate.py not found: {generate}")
    raw_dir.mkdir(parents=True, exist_ok=True)
    before = set(raw_dir.glob("*.png"))
    cmd = [sys.executable, str(generate), caption, "--n", "1",
           "--seed", str(seed), "--scale", "1", "--out", str(raw_dir)]
    subprocess.run(cmd, cwd=str(root), check=True)
    created = set(raw_dir.glob("*.png")) - before
    if len(created) != 1:
        raise RuntimeError(f"Expected exactly 1 new generated PNG, found {len(created)}")
    return next(iter(created))

def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    source = p.add_mutually_exclusive_group(required=True)
    source.add_argument("--source-png", type=Path, help="Process an existing 64x64 PNG")
    source.add_argument("--rdflow-root", type=Path, help="Upstream rd-flow-64 checkout")
    p.add_argument("--caption-file", type=Path, help="Plain English full caption (120-200 words)")
    p.add_argument("--name", required=True, help="Creature asset name, e.g. thunder_wolf_s1")
    p.add_argument("--stage", type=int, choices=(1, 2, 3), default=1)
    p.add_argument("--seed", type=int, default=1001)
    p.add_argument("--out", type=Path, default=Path("rdflow-output"))
    args = p.parse_args(argv)
    safe = "".join(ch if ch.isascii() and (ch.isalnum() or ch in "-_")
                   else "_" for ch in args.name).strip("_")[:64]
    if not safe:
        p.error("--name must contain a Latin letter, number or underscore")
    caption = args.caption_file.read_text("utf-8").strip() if args.caption_file else ""
    if args.rdflow_root is not None and not caption:
        p.error("--rdflow-root requires --caption-file")
    args.out.mkdir(parents=True, exist_ok=True)
    source_file = (args.source_png if args.source_png is not None else
                   run_rdflow(args.rdflow_root, caption, args.seed, args.out / "_raw"))
    with Image.open(source_file) as img:
        art, audit = normalize(img)
    filename = f"{safe}_s{args.stage}_64.png"
    output = args.out / filename
    art.save(output, format="PNG", optimize=True)
    meta = {
        "generator": "Retro Diffusion RD Flow 64" if args.rdflow_root else "external PNG",
        "source": source_file.name,
        "model_license": "https://github.com/Retro-Diffusion/rd-flow-64/blob/main/MODEL_LICENSE.md",
        "art_use": "review output rights before redistribution; never use to train AI",
        "caption": caption,
        "seed": args.seed if args.rdflow_root else None,
        "name": safe,
        "stage": args.stage,
        "image": filename,
        "audit": audit,
        "reviewed_by_human": False,
    }
    output.with_suffix(".json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n",
                                          encoding="utf-8")
    print(f"OK: {output} | opaque colors {audit['opaque_colors']}/15; review before import")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
