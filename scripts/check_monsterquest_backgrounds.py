#!/usr/bin/env python3
"""Validate 10×4 pixel-art backgrounds and render scroll/crop QA contact sheet.

Used for repeatable asset inspection before Android UI screenshot testing.
PNG output is a diagnostic, not a substitute for an emulator/device test.
"""

from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "monsterquest/src/main/res/drawable-nodpi"
PREVIEW = ROOT / "docs/MONSTER_QUEST_BACKGROUND_QA.png"
ZONES = ("meadow", "forest", "cave", "ruins", "volcano",
         "ice", "coast", "wasteland", "dark", "boss")
SPEED = (.18, .48, 1.0, 1.25)
LAYER_NAMES = ("far", "mid", "ground", "front")


def strip_window(image, offset):
    offset = int(offset) % image.width
    if offset <= image.width - 320:
        return image.crop((offset, 0, offset + 320, 180))
    # World-space wrap-around; verifies that stage background can loop forever.
    tail = image.crop((offset, 0, image.width, 180))
    head = image.crop((0, 0, 320 - tail.width, 180))
    result = Image.new("RGBA", (320, 180))
    result.paste(tail, (0, 0))
    result.paste(head, (tail.width, 0))
    return result


def combined_layers(images, world):
    result = Image.new("RGBA", (320, 180))
    for depth, image in enumerate(images):
        result = Image.alpha_composite(result,
            strip_window(image, world * SPEED[depth]))
    return result.convert("RGB")


def main():
    canvas = Image.new("RGB", (1024, 10 * 228 + 30), "#0b1626")
    draw = ImageDraw.Draw(canvas)
    reports = []
    for i, zone in enumerate(ZONES):
        sources = []
        palette = set()
        for layer in LAYER_NAMES:
            path = ART / f"mq_bg_{zone}_{layer}.png"
            assert path.exists(), f"Missing {path}"
            with Image.open(path) as file:
                assert file.format == "PNG", path
                assert file.mode == "RGBA", path
                assert file.size == (640, 180), (path, file.size)
                image = file.copy()
            if layer == "far":
                assert image.getextrema()[3] == (255, 255), "Sky has holes"
            else:
                assert image.getextrema()[3][0] == 0, (zone, layer)
            if layer == "ground":
                assert image.getpixel((100, 120))[3] == 0, "Floor hides monsters"
            if layer == "front":
                assert all(image.getpixel((x, 95))[3] == 0
                           for x in range(640)), "Front obscures bullet hell"
            pixels = (image.get_flattened_data() if hasattr(image,"get_flattened_data")
                      else image.getdata())
            palette.update(pixel[:3] for pixel in pixels if pixel[3])
            sources.append(image)
        assert len(palette) <= 64, (zone, len(palette))
        for j, world in enumerate((0, 160, 400)):
            image = combined_layers(sources, world)
            canvas.paste(image, (16 + j * 337, 32 + i * 228))
        draw.text((16, 12 + i * 228),
                  f"{i+1:02d} {zone} | {len(palette)} colors | 16px tiles | 4 layers",
                  fill="#f0cf8f")
        reports.append(f"{zone}: {len(palette)} colors; 4/4 PNG layers PASS")
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(PREVIEW, optimize=True)
    for row in reports:
        print(row)
    print(f"QA CONTACT SHEET: {PREVIEW}")


if __name__ == "__main__":
    main()
