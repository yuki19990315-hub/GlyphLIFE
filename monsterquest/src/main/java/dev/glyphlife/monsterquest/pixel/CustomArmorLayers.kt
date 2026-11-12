package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Strict bounded pixel offsets, persisted per slot and independent of asset ID. */
data class ArmorOffset(
    val dx: Int = 0, val dy: Int = 0, val scalePercent: Int = 100
) {
    init {
        require(dx in -12..12 && dy in -12..12)
        require(scalePercent in 50..150)
    }
    fun moved(x: Int, y: Int): ArmorOffset =
        copy(dx = (dx + x).coerceIn(-12, 12),
            dy = (dy + y).coerceIn(-12, 12))
    fun scaled(percentDelta: Int): ArmorOffset =
        copy(scalePercent = (scalePercent + percentDelta).coerceIn(50, 150))
}

/**
 * Separately authored PNG components mounted on stable anatomy anchors.
 * The saved asset is a transparent 64x64 *part*, never a complete monster.
 * Stored UUIDs are safe identifiers and contain no absolute file paths.
 */
object CustomArmorLayers {
    fun validatedOffsets(offsets: Map<String, ArmorOffset>): Map<String, ArmorOffset> {
        require(offsets.size <= ArmorSlot.entries.size)
        require(offsets.keys.all { slot -> ArmorSlot.entries.any { it.name == slot } })
        return offsets
    }

    /** Same parent-side selection as referenced armor assets. */
    fun inheritOffsets(
        a: Map<String, ArmorOffset>,
        b: Map<String, ArmorOffset>
    ): Map<String, ArmorOffset> {
        val first = validatedOffsets(a)
        val second = validatedOffsets(b)
        return ArmorSlot.entries.mapNotNull { slot ->
            val owner = if (slot in setOf(
                ArmorSlot.HEAD, ArmorSlot.CORE, ArmorSlot.SHOULDER
            )) first else second
            owner[slot.name]?.let { slot.name to it }
        }.toMap()
    }

    fun validated(parts: Map<String, String>): Map<String, String> {
        require(parts.size <= ArmorSlot.entries.size) { "装着パーツが多すぎます" }
        return parts.entries.associate { (slot, id) ->
            require(ArmorSlot.entries.any { it.name == slot }) { "無効な装備位置: $slot" }
            require(id.length == 36 && runCatching {
                UUID.fromString(id).toString().equals(id, ignoreCase = true)
            }.getOrDefault(false)) { "無効な装備画像IDです" }
            slot to id.lowercase()
        }
    }

    /** First parent's head/core/shoulder, second parent's tail/back/wing. */
    fun inherit(a: Map<String, String>, b: Map<String, String>): Map<String, String> {
        val first = validated(a)
        val second = validated(b)
        return ArmorSlot.entries.mapNotNull { slot ->
            val owner = if (slot == ArmorSlot.HEAD || slot == ArmorSlot.CORE ||
                slot == ArmorSlot.SHOULDER) first else second
            owner[slot.name]?.let { slot.name to it }
        }.toMap()
    }

    private fun isMagenta(color: Int): Boolean {
        val r = (color ushr 16) and 255
        val g = (color ushr 8) and 255
        val b = color and 255
        return r >= 170 && b >= 170 && g <= 105 && r + b - 2 * g >= 270
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private fun opaqueBounds(sprite: PixelSprite): Bounds? {
        var x0 = sprite.width; var y0 = sprite.height
        var x1 = -1; var y1 = -1
        for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
            if ((sprite.pixel(x, y) ushr 24) < 128) continue
            x0 = min(x0, x); y0 = min(y0, y)
            x1 = max(x1, x); y1 = max(y1, y)
        }
        return if (x1 < 0) null else Bounds(x0, y0, x1, y1)
    }

    /**
     * Convert a transparent or chroma-key magenta image to an independent 64px
     * centered asset. Reject opaque full backgrounds: the user must review or
     * cut out the part instead of silently layering an entire photograph.
     */
    fun normalize(source: PixelSprite): PixelSprite {
        require(source.width in 16..256 && source.height in 16..256)
        val input = source.pixels()
        fun cleaned(color: Int): Int =
            if ((color ushr 24) < 128 || isMagenta(color)) 0
            else color or (0xff shl 24)
        val corners = intArrayOf(0, source.width - 1,
            (source.height - 1) * source.width, source.height * source.width - 1)
        require(corners.all { cleaned(input[it]) == 0 }) {
            "透過PNG、または四隅がマゼンタ色の画像を指定してください"
        }
        val keyed = PixelSprite.fromArgb(source.width, source.height,
            IntArray(input.size) { cleaned(input[it]) })
        val box = opaqueBounds(keyed) ?: error("装備画像が空です")
        val originalOpaque = keyed.opaqueCount()
        require(originalOpaque in 6..30000) { "装備画像の有効画素数が不正です" }
        // Keep natural aspect ratio; a wide wing is not squashed into a square.
        val size = 54
        val ratio = min(size.toFloat() / box.width, size.toFloat() / box.height)
        val w = max(1, (box.width * ratio).toInt())
        val h = max(1, (box.height * ratio).toInt())
        val output = IntArray(4096)
        val ox = (64 - w) / 2
        val oy = (64 - h) / 2
        for (y in 0 until h) for (x in 0 until w) {
            val sx = box.left + min(box.width - 1, x * box.width / w)
            val sy = box.top + min(box.height - 1, y * box.height / h)
            output[(oy + y) * 64 + (ox + x)] = keyed.pixel(sx, sy)
        }
        return PixelSprite.fromArgb(64, 64, output)
    }

    private data class Mount(val centerX: Int, val centerY: Int, val w: Int, val h: Int)
    private fun mount(slot: ArmorSlot, body: MonsterBodyType): Mount = when (slot) {
        ArmorSlot.HEAD -> when (body) {
            MonsterBodyType.BEAST, MonsterBodyType.DRAGON, MonsterBodyType.SERPENT ->
                Mount(23, 13, 24, 16)
            MonsterBodyType.AQUATIC -> Mount(20, 24, 20, 13)
            MonsterBodyType.AVIAN -> Mount(31, 15, 19, 16)
            else -> Mount(32, 13, 20, 16)
        }
        ArmorSlot.CORE -> Mount(32, 35, 17, 16)
        ArmorSlot.TAIL -> Mount(53, 43, 20, 18)
        ArmorSlot.SHOULDER -> Mount(18, 29, 19, 17)
        ArmorSlot.BACK -> Mount(35, 22, 28, 22)
        ArmorSlot.WING -> Mount(47, 25, 30, 35)
    }

    private fun place(
        sprite: PixelSprite, slot: ArmorSlot, body: MonsterBodyType,
        offset: ArmorOffset
    ): PixelSprite {
        val bounds = opaqueBounds(sprite) ?: return PixelSprite.fromArgb(64, 64, IntArray(4096))
        val target = mount(slot, body)
        val fit = min(target.w.toFloat() / bounds.width, target.h.toFloat() / bounds.height)
        val ratio = fit * offset.scalePercent / 100f
        val w = max(1, (bounds.width * ratio).toInt())
        val h = max(1, (bounds.height * ratio).toInt())
        val left = target.centerX + offset.dx - w / 2
        val top = target.centerY + offset.dy - h / 2
        val output = IntArray(4096)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = left + x; val dy = top + y
            if (dx !in 0..63 || dy !in 0..63) continue
            val sx = bounds.left + min(bounds.width - 1, x * bounds.width / w)
            val sy = bounds.top + min(bounds.height - 1, y * bounds.height / h)
            output[dy * 64 + dx] = sprite.pixel(sx, sy)
        }
        return PixelSprite.fromArgb(64, 64, output)
    }

    /**
     * External part pixels are down-mapped to the monster's existing palette,
     * retaining the <=16-color invariant and keeping the base art unchanged.
     * Back and wing layers render BEHIND the existing creature.
     */
    fun compose(
        base: PixelSprite, wild: WildMonster,
        findAsset: (String) -> PixelSprite?
    ): PixelSprite {
        require(base.width == 64 && base.height == 64)
        val identifiers = validated(wild.customArmorParts)
        if (identifiers.isEmpty()) return base
        val offsets = validatedOffsets(wild.customArmorOffsets)
        val colors = base.colors().toList()
        if (colors.isEmpty()) return base
        fun colorDistance(a: Int, b: Int): Int {
            val r = ((a ushr 16) and 255) - ((b ushr 16) and 255)
            val g = ((a ushr 8) and 255) - ((b ushr 8) and 255)
            val bb = (a and 255) - (b and 255)
            return r * r + g * g + bb * bb
        }
        val body = MonsterDesign.bodyType(wild)
        val placed = ArmorSlot.entries.mapNotNull { slot ->
            if (slot.minStage > wild.stage.coerceIn(1, 3)) return@mapNotNull null
            val id = identifiers[slot.name] ?: return@mapNotNull null
            val image = findAsset(id) ?: return@mapNotNull null
            if (image.width != 64 || image.height != 64) return@mapNotNull null
            slot to place(image, slot, body,
                offsets[slot.name] ?: ArmorOffset())
        }
        if (placed.isEmpty()) return base
        val result = base.pixels()
        fun paint(layer: PixelSprite, behind: Boolean) {
            val bytes = layer.pixels()
            for (i in bytes.indices) {
                if (bytes[i] ushr 24 < 128 || (behind && result[i] ushr 24 >= 128)) continue
                result[i] = colors.minBy { colorDistance(it, bytes[i]) }
            }
        }
        placed.filter { it.first == ArmorSlot.WING || it.first == ArmorSlot.BACK }
            .forEach { paint(it.second, true) }
        placed.filter { it.first != ArmorSlot.WING && it.first != ArmorSlot.BACK }
            .forEach { paint(it.second, false) }
        return PixelSprite.fromArgb(64, 64, result)
    }
}
