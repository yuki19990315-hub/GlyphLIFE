package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import java.util.UUID

/**
 * Free, fully local design sandbox. AI model weights, training data and network
 * access are not involved. The player chooses anatomy, element and stage.
 */
object MonsterPrototypeFactory {
    val elements = listOf("火","水","草","雷","光","闇","無")
    const val PREVIEW_ID = "00000000-0000-4000-8000-000000000001"

    fun preview(
        name:String, type:MonsterBodyType, element:String,stage:Int=1,
        variant:Int=0,
        armorParts: Map<String,String> = emptyMap()
    ):WildMonster {
        require(element in elements)
        require(stage in 1..3)
        require(variant in 0..63)
        val checkedArmor = dev.glyphlife.monsterquest.pixel.MonsterArmor.validated(armorParts)
        val defaultNames=mapOf(
            MonsterBodyType.BEAST to "獣", MonsterBodyType.DRAGON to "竜",
            MonsterBodyType.BIPED to "戦士", MonsterBodyType.GOLEM to "巨像",
            MonsterBodyType.SERPENT to "蛇", MonsterBodyType.AVIAN to "翼獣",
            MonsterBodyType.INSECTOID to "甲虫", MonsterBodyType.AQUATIC to "海獣",
            MonsterBodyType.MECHA to "機甲", MonsterBodyType.SPIRIT to "精霊"
        )
        val clean=name.trim().take(16).ifBlank {
            (defaultNames[type] ?: "魔獣") + "試作型"
        }
        val idea=WildMonster(
            id=PREVIEW_ID, species=clean, element=element,
            maxHp=30, attack=7, stage=stage, bodyType=type.name,
            motifPrimary=type.name.lowercase(),
            motifSecondary="original",
            designSeed="atelier-$variant",
            artFamily=if (clean == "ライゼル" && element == "雷" &&
                type == MonsterBodyType.BEAST) MonsterArtFamilies.RAIZEL else "",
            armorParts=checkedArmor
        )
        return idea.copy(pixels=GlyphProjector.project(MonsterDesign.sprite(idea)))
    }

    fun summon(design:WildMonster,room:Int):WildMonster {
        require(design.id==PREVIEW_ID)
        require(room in 1..QuestRules.ROOM_COUNT)
        val first=design.copy(
            id=UUID.randomUUID().toString(),
            stage=1,
            maxHp=18+room*7,
            attack=4+room,
            pixels=""
        )
        return first.copy(pixels=GlyphProjector.project(MonsterDesign.sprite(first)))
    }
}
