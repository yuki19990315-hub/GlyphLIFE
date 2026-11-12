package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Six independent overlay slots attached to an existing 64x64 body. */
enum class ArmorSlot(val label: String, val minStage: Int) {
    HEAD("頭部", 1),
    CORE("胸コア", 1),
    TAIL("尾・先端", 1),
    SHOULDER("肩", 2),
    BACK("背中", 2),
    WING("翼・追加肢", 3)
}

enum class ArmorStyle(val label: String) {
    PLATE("重装甲"), SPIKE("刃・棘"), CRYSTAL("結晶"), RUNE("紋章")
}

/**
 * A validated plan contains one style per slot. The game owns pixels and
 * stats. AI is permitted to pick only these named slots/styles, never arbitrary
 * code, image links, game stats, or uncontrolled coordinates.
 */
object MonsterArmor {
    const val MAX_PARTS = 6

    fun isValid(plan: Map<String, String>): Boolean =
        plan.size <= MAX_PARTS && plan.all { (slot, style) ->
            ArmorSlot.entries.any { it.name == slot } &&
                ArmorStyle.entries.any { it.name == style }
        }

    fun validated(plan: Map<String, String>): Map<String, String> {
        require(isValid(plan)) { "装備スロットまたはパーツ名が不正です" }
        return ArmorSlot.entries.mapNotNull { slot ->
            plan[slot.name]?.let { slot.name to it }
        }.toMap()
    }

    fun parseSlot(name: String): ArmorSlot? =
        ArmorSlot.entries.firstOrNull { it.name == name }

    fun suggestion(element: String, body: MonsterBodyType): Map<String, String> {
        val elementStyle = when (element) {
            "火", "雷" -> ArmorStyle.SPIKE
            "水", "光" -> ArmorStyle.CRYSTAL
            "草", "闇" -> ArmorStyle.RUNE
            else -> ArmorStyle.PLATE
        }
        val wing = if (body in setOf(MonsterBodyType.DRAGON, MonsterBodyType.AVIAN))
            ArmorStyle.CRYSTAL else ArmorStyle.PLATE
        return mapOf(
            ArmorSlot.HEAD.name to elementStyle.name,
            ArmorSlot.CORE.name to ArmorStyle.CRYSTAL.name,
            ArmorSlot.TAIL.name to elementStyle.name,
            ArmorSlot.SHOULDER.name to ArmorStyle.PLATE.name,
            ArmorSlot.BACK.name to ArmorStyle.SPIKE.name,
            ArmorSlot.WING.name to wing.name
        )
    }

    /** Validated references to immutable transparent 64px attachment files. */
    fun validatedLayerRefs(refs: Map<String, String>): Map<String, String> {
        require(refs.size <= MAX_PARTS) { "装備画像が多すぎます" }
        return refs.map { (key, value) ->
            require(ArmorSlot.entries.any { it.name == key }) { "未知の装備画像スロットです" }
            require(runCatching { java.util.UUID.fromString(value) }.isSuccess) {
                "装備画像の参照IDが不正です"
            }
            key to value
        }.toMap()
    }

    /** Custom image layers follow the same parental slot inheritance as styles. */
    fun inheritLayerRefs(
        first: Map<String, String>, second: Map<String, String>
    ): Map<String, String> {
        val a = validatedLayerRefs(first)
        val b = validatedLayerRefs(second)
        return ArmorSlot.entries.mapNotNull { slot ->
            val owner = if (slot in setOf(
                ArmorSlot.HEAD, ArmorSlot.CORE, ArmorSlot.SHOULDER
            )) a else b
            owner[slot.name]?.let { slot.name to it }
        }.toMap()
    }

    /** Head/core/shoulder from first parent, tail/back/wings from second. */
    fun inherit(first: Map<String, String>, second: Map<String, String>): Map<String, String> {
        val a = validated(first)
        val b = validated(second)
        return ArmorSlot.entries.mapNotNull { slot ->
            val primary = slot in setOf(ArmorSlot.HEAD, ArmorSlot.CORE, ArmorSlot.SHOULDER)
            (if (primary) a else b)[slot.name]?.let { slot.name to it }
        }.toMap()
    }

    /**
     * Overlays parts using only colors ALREADY PRESENT in the base sprite.
     * This keeps even a 15-color authored sprite at <=16 colors after armor,
     * prevents palette flicker, and preserves transparent background.
     */
    fun compose(base: PixelSprite, wild: WildMonster): PixelSprite {
        require(base.width == 64 && base.height == 64)
        if (wild.armorParts.isEmpty()) return base
        val plan = validated(wild.armorParts)
        val available = plan.filterKeys { key ->
            ArmorSlot.valueOf(key).minStage <= wild.stage.coerceIn(1, 3)
        }
        if (available.isEmpty()) return base
        val palette = base.colors().sortedBy { color ->
            ((color ushr 16) and 255) * 30 +
                ((color ushr 8) and 255) * 59 + (color and 255) * 11
        }
        if (palette.isEmpty()) return base
        val outline = palette.first()
        val material = palette[palette.size / 2]
        val glow = palette.last()
        val pixels = base.pixels()
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0..63 && y in 0..63) pixels[y * 64 + x] = c
        }
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thick: Int = 1) {
            var x = x0
            var y = y0
            val dx = abs(x1 - x0)
            val dy = abs(y1 - y0)
            val sx = if (x0 < x1) 1 else -1
            val sy = if (y0 < y1) 1 else -1
            var error = dx - dy
            while (true) {
                for (oy in -(thick / 2)..(thick / 2))
                    for (ox in -(thick / 2)..(thick / 2)) set(x + ox, y + oy, color)
                if (x == x1 && y == y1) break
                val e2 = 2 * error
                if (e2 > -dy) { error -= dy; x += sx }
                if (e2 < dx) { error += dx; y += sy }
            }
        }
        fun diamond(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
            for (y in max(0, cy - ry)..min(63, cy + ry))
                for (x in max(0, cx - rx)..min(63, cx + rx))
                    if (abs(x - cx) * ry + abs(y - cy) * rx <= rx * ry)
                        set(x, y, color)
        }
        fun anchor(slot: ArmorSlot): Pair<Int, Int> {
            val body = MonsterDesign.bodyType(wild)
            return when (slot) {
                ArmorSlot.HEAD -> when (body) {
                    MonsterBodyType.BEAST, MonsterBodyType.DRAGON,
                    MonsterBodyType.SERPENT -> 23 to 17
                    MonsterBodyType.AQUATIC -> 20 to 25
                    MonsterBodyType.AVIAN -> 31 to 15
                    else -> 32 to 15
                }
                ArmorSlot.CORE -> when (body) {
                    MonsterBodyType.BEAST, MonsterBodyType.AQUATIC -> 33 to 35
                    else -> 32 to 34
                }
                ArmorSlot.TAIL -> when (body) {
                    MonsterBodyType.AVIAN -> 33 to 52
                    MonsterBodyType.BIPED, MonsterBodyType.GOLEM,
                    MonsterBodyType.MECHA, MonsterBodyType.SPIRIT -> 49 to 43
                    else -> 53 to 43
                }
                ArmorSlot.SHOULDER -> when (body) {
                    MonsterBodyType.BEAST, MonsterBodyType.SERPENT,
                    MonsterBodyType.AQUATIC -> 39 to 28
                    else -> 19 to 30
                }
                ArmorSlot.BACK -> 39 to 24
                ArmorSlot.WING -> if (body in setOf(MonsterBodyType.DRAGON,
                    MonsterBodyType.AVIAN)) 48 to 22 else 49 to 29
            }
        }
        for (slot in ArmorSlot.entries) {
            val style = available[slot.name]?.let(ArmorStyle::valueOf) ?: continue
            val (x, y) = anchor(slot)
            val outwardX = when (slot) {
                ArmorSlot.TAIL, ArmorSlot.WING, ArmorSlot.BACK -> 1
                ArmorSlot.SHOULDER -> -1
                else -> 0
            }
            val outwardY = if (slot in setOf(ArmorSlot.TAIL, ArmorSlot.CORE)) -1 else -1
            when (style) {
                ArmorStyle.PLATE -> {
                    diamond(x, y, 7, 5, outline)
                    diamond(x, y - 1, 5, 3, material)
                    line(x - 3, y - 2, x + 2, y - 2, glow)
                }
                ArmorStyle.SPIKE -> {
                    val tipX = x + outwardX * 11
                    val tipY = y - 12
                    line(x - 4, y + 2, tipX, tipY, outline, 5)
                    line(x - 3, y + 1, tipX, tipY + 2, material, 2)
                    set(tipX, tipY, glow)
                }
                ArmorStyle.CRYSTAL -> {
                    diamond(x, y, 5, 9, outline)
                    diamond(x, y - 2, 3, 6, material)
                    line(x, y - 7, x, y - 2, glow, 2)
                    line(x - 2, y + 2, x, y + 5, glow)
                }
                ArmorStyle.RUNE -> {
                    diamond(x, y, 7, 7, outline)
                    diamond(x, y, 5, 5, material)
                    line(x - 3, y, x + 3, y, glow)
                    line(x, y - 4, x, y + 4, glow)
                    set(x - 2, y - 2, outline)
                    set(x + 2, y + 2, outline)
                }
            }
        }
        return PixelSprite.fromArgb(64, 64, pixels)
    }
}
