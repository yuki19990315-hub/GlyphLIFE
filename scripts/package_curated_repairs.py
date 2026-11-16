#!/usr/bin/env python3
"""Pack audited full-body PNG masters as Android/JVM-safe 4-bit sprites.

Generated Kotlin is a reproducible cache; PNG files under art_sources are
editable masters. Run this script again when a source illustration changes.
"""

import base64
import zlib
from pathlib import Path

from PIL import Image, ImageDraw

from audit_curated_sprites import parse_art


ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "monsterquest/art_sources/repairs_v56"
KOTLIN = ROOT / (
    "monsterquest/src/main/java/dev/glyphlife/monsterquest/pixel/"
    "CuratedMonsterRepairs.kt"
)
PREVIEW = ROOT / "monsterquest/build/pixel-preview/curated-repaired-audit.png"
FAMILIES = (
    "beast", "dragon", "knight", "spirit", "golem",
    "eye", "bat", "archer", "sorcerer", "slime",
)


def code_entry(index: int, img: Image.Image) -> str:
    data = list(img.get_flattened_data())
    unique = [(0, 0, 0, 0)]
    for pixel in data:
        if pixel[3] == 0:
            continue
        if pixel[3] != 255:
            raise ValueError(f"sprite {index}: partial alpha")
        if pixel not in unique:
            unique.append(pixel)
    if len(unique) > 16:
        raise ValueError(f"sprite {index}: {len(unique)} colors")
    palette = {color: i for i, color in enumerate(unique)}
    indices = [palette[p] if p[3] else 0 for p in data]
    packed = bytes([(indices[i] << 4) | indices[i+1]
                    for i in range(0, len(indices), 2)])
    encoded = base64.b64encode(zlib.compress(packed, level=9)).decode("ascii")
    argb = ["0x%08XL.toInt()" %
            ((a << 24) | (r << 16) | (g << 8) | b)
            for r, g, b, a in unique]
    return (f"        {index} to Entry(intArrayOf({', '.join(argb)}),\n"
            f"            \"{encoded}\"),")


def main() -> None:
    originals = list(parse_art())
    entries = []
    tile = 258
    sheet = Image.new("RGB", (tile * 5, tile * 6), "#192632")
    painter = ImageDraw.Draw(sheet)
    repaired = 0
    preserved = 0
    for family, name in enumerate(FAMILIES):
        for stage in range(1, 4):
            index = family * 3 + stage - 1
            source = SPRITES / f"{name}_{stage}.png"
            sprite = Image.open(source).convert("RGBA") if source.is_file() else originals[index]
            if source.is_file():
                repaired += 1
                assert sprite.size == (64, 64)
                assert len(sprite.getcolors(4097) or []) <= 16
                b = sprite.getbbox()
                assert b is not None and min(b[0], b[1], 64-b[2], 64-b[3]) >= 8
                entries.append(code_entry(index, sprite))
            else:
                preserved += 1
            col, row = family % 5, (family // 5) * 3 + stage - 1
            x, y = col * tile, row * tile
            tile_image = Image.new("RGBA", (64, 64), "#243547")
            tile_image.alpha_composite(sprite)
            sheet.paste(tile_image.resize((224, 224), Image.Resampling.NEAREST)
                        .convert("RGB"), (x + 16, y + 4))
            painter.text((x + 8, y + 230), f"{name.upper()} stage {stage}",
                         fill="#e8e8df")
            painter.text((x + 8, y + 244),
                         "REPAIRED" if source.is_file() else "PRESERVED",
                         fill="#8fe3a5" if source.is_file() else "#bcbcbf")
    # Pure Kotlin/JVM decoding, including unit tests, cannot rely on Android
    # application context or asset manager to access the PNG masters.
    contents = """package dev.glyphlife.monsterquest.pixel

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/** v5.6 full-body replacement masters. Generated from art_sources/repairs_v56.
 * Do not hand-edit compressed blobs; edit source PNGs and re-run
 * scripts/package_curated_repairs.py.
 */
internal object CuratedMonsterRepairs {
    private data class Entry(val palette: IntArray, val data: String)
    private val encoded = mapOf(
""" + "\n".join(entries) + """
    )
    private val decoded: Map<Int, PixelSprite> by lazy {
        encoded.mapValues { (_, entry) ->
            val packed = InflaterInputStream(ByteArrayInputStream(
                Base64.getDecoder().decode(entry.data))).use { it.readBytes() }
            check(packed.size == 2048)
            PixelSprite.fromArgb(64, 64, IntArray(4096) { index ->
                val byte = packed[index / 2].toInt() and 255
                val slot = if (index % 2 == 0) byte ushr 4 else byte and 15
                entry.palette[slot]
            })
        }
    }

    fun find(index: Int): PixelSprite? = decoded[index]
    fun indexes(): Set<Int> = encoded.keys
}
"""
    KOTLIN.write_text(contents, encoding="utf-8")
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print(f"Rebuilt {repaired} replacements; preserved {preserved} originals")
    print(f"Kotlin: {KOTLIN}")
    print(f"Visual QA: {PREVIEW}")


if __name__ == "__main__":
    main()
