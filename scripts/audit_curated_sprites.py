#!/usr/bin/env python3
"""Inspect the exact 30 embedded 64x64 curated masters before UI fitting.

This is a diagnostic contact sheet, not a gameplay sprite generator. A padded
bounding box alone cannot detect art that was clipped *before* being packed.
"""

import base64
import re
import zlib
from pathlib import Path

from PIL import Image, ImageDraw


SOURCE = Path(__file__).resolve().parents[1] / (
    "monsterquest/src/main/java/dev/glyphlife/monsterquest/pixel/CuratedMonsterArt.kt"
)
OUT = SOURCE.parents[7] / "build/pixel-preview/curated-original-audit.png"
FAMILIES = (
    "BEAST", "DRAGON", "BIPED", "SPIRIT", "GOLEM",
    "EYE", "BAT", "ARCHER", "SORCERER", "SLIME",
)


def parse_art():
    source = SOURCE.read_text()
    palettes_text = source.split("private val palettes = arrayOf(", 1)[1].split(
        "private val compressed", 1
    )[0]
    palettes = [
        [int(h, 16) for h in re.findall(r"0x([0-9A-Fa-f]{8})L", line)]
        for line in palettes_text.splitlines() if "intArrayOf(" in line
    ]
    compressed_text = source.split("private val compressed = arrayOf(", 1)[1].split(
        "private val stages", 1
    )[0]
    encoded = re.findall(r'"(eNr[^\"]+)"', compressed_text)
    assert len(palettes) == len(encoded) == 30
    for palette, value in zip(palettes, encoded):
        packed = zlib.decompress(base64.b64decode(value))
        assert len(packed) == 2048
        indices = [
            (packed[i // 2] >> (4 if i % 2 == 0 else 0)) & 15
            for i in range(4096)
        ]
        sprite = Image.new("RGBA", (64, 64))
        sprite.putdata([
            (palette[n] >> 16 & 255, palette[n] >> 8 & 255,
             palette[n] & 255, palette[n] >> 24 & 255)
            for n in indices
        ])
        yield sprite


def maximum_run(mask):
    best = current = 0
    for value in mask:
        current = current + 1 if value else 0
        best = max(best, current)
    return best


def main():
    tile = 294
    sheet = Image.new("RGB", (tile * 5, tile * 6), "#142331")
    drawer = ImageDraw.Draw(sheet)
    for index, sprite in enumerate(parse_art()):
        alpha = sprite.getchannel("A")
        bounds = alpha.getbbox()
        assert bounds is not None
        left, top, right_exclusive, bottom_exclusive = bounds
        right = right_exclusive - 1
        right_run = maximum_run([
            alpha.getpixel((right, y)) > 0
            for y in range(top, bottom_exclusive)
        ])
        left_run = maximum_run([
            alpha.getpixel((left, y)) > 0
            for y in range(top, bottom_exclusive)
        ])
        label = f"{FAMILIES[index // 3]}  stage {index % 3 + 1}"
        note = f"side vertical: L{left_run} / R{right_run}"
        col = (index // 3) % 5
        row = (index // 15) * 3 + index % 3
        x = col * tile
        y = row * tile
        backdrop = Image.new("RGBA", (64, 64), "#19222f")
        backdrop.alpha_composite(sprite)
        zoom = backdrop.resize((256, 256), Image.Resampling.NEAREST)
        sheet.paste(zoom.convert("RGB"), (x + 19, y + 7))
        drawer.text((x + 8, y + 265), label, fill="#f5d8a0")
        drawer.text((x + 8, y + 279), note,
                    fill="#ef8a83" if max(left_run, right_run) >= 12 else "#8bc5c5")
        print(f"{label:22} bbox={bounds} L={left_run:2} R={right_run:2}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
