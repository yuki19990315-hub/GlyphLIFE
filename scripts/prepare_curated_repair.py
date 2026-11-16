#!/usr/bin/env python3
"""Convert a full-body concept image into a strict 64px/16-color sprite.

The visual review of the supplied full-body concept must happen BEFORE
conversion. This tool cannot infer or restore missing body parts on its own.
"""

import argparse
from pathlib import Path

from PIL import Image


def repair(source: Path, destination: Path) -> None:
    art = Image.open(source).convert("RGBA")
    alpha = art.getchannel("A")
    # Ghost-like transparent pixels around AI results must not enter the sprite.
    mask = alpha.point(lambda a: 255 if a >= 128 else 0)
    box = mask.getbbox()
    if not box:
        raise ValueError(f"Transparent image: {source}")
    art = art.crop(box)
    iw, ih = art.size
    width = max(1, round(iw * min(48 / iw, 48 / ih)))
    height = max(1, round(ih * min(48 / iw, 48 / ih)))
    art = art.resize((width, height), Image.Resampling.NEAREST)
    pixels = art.load()

    # Learn the 15 RGB clusters from actually opaque pixels only. The sixteenth
    # slot is reserved for alpha=0 and opaque output always uses alpha=255.
    visible = [(pixels[x, y][0:3]) for y in range(height)
               for x in range(width) if pixels[x, y][3] >= 128]
    if not visible:
        raise ValueError(f"No opaque pixels in resized image: {source}")
    sample = Image.new("RGB", (len(visible), 1))
    sample.putdata(visible)
    palette = sample.quantize(colors=15, method=Image.Quantize.MEDIANCUT,
                              dither=Image.Dither.NONE)
    colors = [tuple(palette.getpalette()[i * 3: i * 3 + 3])
              for i in range(len(palette.getcolors() or []))]
    if not colors:
        raise ValueError("Empty palette")
    out = Image.new("RGBA", (64, 64))
    dest = out.load()
    dx, dy = (64 - width) // 2, (64 - height) // 2
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a < 128:
                continue
            c = min(colors, key=lambda v: (v[0]-r)**2 +
                    (v[1]-g)**2 + (v[2]-b)**2)
            dest[dx+x, dy+y] = (*c, 255)
    bounds = out.getbbox()
    assert bounds is not None
    assert min(bounds[0], bounds[1], 64-bounds[2], 64-bounds[3]) >= 8
    assert len(out.getcolors(4097) or []) <= 16
    destination.parent.mkdir(parents=True, exist_ok=True)
    out.save(destination, optimize=True)
    print(f"{destination}: bounds={bounds}, colors={len(out.getcolors(4097) or [])}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    repair(args.source, args.destination)


if __name__ == "__main__":
    main()
