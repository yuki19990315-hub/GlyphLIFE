package dev.glyphlife.monsterquest.data

import android.content.Context
import dev.glyphlife.monsterquest.model.*
import org.json.JSONArray
import org.json.JSONObject

/** A small local JSON save for the standalone Monster Quest MVP. */
class QuestStorage(context: Context) {
    private val prefs = context.getSharedPreferences("monsterquest_v41_clean", Context.MODE_PRIVATE)

    fun load(): QuestState {
        val raw = prefs.getString("session", null)
        if (raw == null) return QuestRules.start(Companion())
        return runCatching {
            val obj = JSONObject(raw)
            val p = obj.getJSONObject("companion")
            val pet = Companion(
                id = p.getString("id"), name = p.getString("name"),
                species = p.getString("species"), element = p.getString("element"),
                level = p.getInt("level"), glyph = p.getString("glyph"),
                importedFromGlyphLife = p.optBoolean("imported", false),
                extraSkillId = p.optString("extraSkillId",""),
                statSlots = readStatSlots(p.optJSONArray("statSlots"))
            ).let(TalentRules::awaken)
            val wallsArray = obj.getJSONArray("walls")
            val walls = (0 until wallsArray.length()).map { wallsArray.getInt(it) }.toSet()
            val capturesArray = obj.getJSONArray("captures")
            val captures = (0 until capturesArray.length()).map {
                TalentRules.awaken(wildFromJson(capturesArray.getJSONObject(it)))
            }
            QuestState(
                companion = pet,
                challenge = obj.optJSONObject("challenge")?.let(::readChallenge)
                    ?: ChallengeState(),
                bossRelics = obj.optJSONArray("bossRelics")?.let { arr ->
                    (0 until arr.length()).mapNotNull { index ->
                        runCatching {
                            val item=arr.getJSONObject(index)
                            val id=item.optString("id","")
                            val kind=item.optString("kind","")
                            if(id.isBlank() || RelicRules.spec(kind)==null) null
                            else BossRelic(id,kind,
                                item.optString("source","").take(80),
                                item.optInt("floor",1).coerceIn(1,999))
                        }.getOrNull()
                    }.distinctBy{it.id}.take(RelicRules.INVENTORY_LIMIT)
                } ?: emptyList(),
                relicEquipment = readRelicAssignments(obj),
                relicDryBosses = obj.optInt("relicDryBosses",0)
                    .coerceIn(0,RelicRules.PITY_LIMIT-1),
                companionXp = obj.optInt("companionXp",0).coerceIn(0,10000),
                attackUpgrade = obj.optInt("attackUpgrade",0).coerceIn(0,GrowthRules.UPGRADE_CAP),
                defenseUpgrade = obj.optInt("defenseUpgrade",0).coerceIn(0,GrowthRules.UPGRADE_CAP),
                bossWins = obj.optInt("bossWins",0).coerceIn(0,999999),
                achievementsClaimed = obj.optJSONArray("achievementsClaimed")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.toSet()
                        .intersect(GrowthRules.achievements.map { it.id }.toSet())
                } ?: emptySet(),
                room = obj.getInt("room").coerceIn(1, QuestRules.ROOM_COUNT),
                position = obj.getInt("position").coerceIn(0, 15),
                walls = walls,
                enemy = obj.optJSONObject("enemy")?.let(::wildFromJson),
                enemyHp = obj.optInt("enemyHp", 0).coerceAtLeast(0),
                playerHp = obj.getInt("playerHp").coerceIn(0, 200),
                potions = obj.optInt("potions", 2).coerceIn(0, 99),
                supportUsedInBattle = obj.optBoolean("supportUsedInBattle", false),
                inBattle = obj.optBoolean("inBattle", false),
                roomCleared = obj.optBoolean("roomCleared", false),
                completed = obj.optBoolean("completed", false),
                captures = captures.take(100),
                defeated = obj.optInt("defeated", 0).coerceIn(0, 999999),
                earnedXp = obj.optInt("earnedXp", 0).coerceIn(0, 100),
                earnedHappiness = obj.optInt("earnedHappiness", 0).coerceIn(0, 20),
                rewardEventId = obj.optString("rewardEventId").ifBlank { null },
                tapRun = obj.optJSONObject("tapRun")?.let(::readTapRun) ?: TapRun(),
                summonStones = obj.optInt("summonStones", 0).coerceIn(0, 999),
                captureOrbs = obj.optInt("captureOrbs", 3).coerceIn(0, 99),
                catalog = obj.optJSONArray("catalog")?.let { known ->
                    (0 until known.length()).mapNotNull { index ->
                        runCatching { wildFromJson(known.getJSONObject(index)) }.getOrNull()
                    }.takeLast(150)
                } ?: emptyList(),
                huntTargetId = obj.optString("huntTargetId","").take(120),
                partyIds = obj.optJSONArray("partyIds")?.let { party ->
                    (0 until party.length()).map { party.getString(it) }.distinct().take(3)
                }?.filter { id -> captures.any { it.id == id } } ?: emptyList(),
                message = obj.optString("message", "冒険しよう").take(200)
            )
        }.getOrElse { QuestRules.start(Companion()) }
    }

    fun save(s: QuestState) {
        val data = JSONObject()
            .put("companion", JSONObject()
                .put("id", s.companion.id).put("name", s.companion.name)
                .put("species", s.companion.species).put("element", s.companion.element)
                .put("level", s.companion.level).put("glyph", s.companion.glyph)
                .put("imported", s.companion.importedFromGlyphLife)
                .put("extraSkillId",s.companion.extraSkillId)
                .put("statSlots",writeStatSlots(s.companion.statSlots)))
            .put("challenge",writeChallenge(s.challenge))
            .put("bossRelics",JSONArray().apply {
                s.bossRelics.take(RelicRules.INVENTORY_LIMIT).forEach {
                    put(JSONObject().put("id",it.id).put("kind",it.kind)
                        .put("source",it.source).put("floor",it.floor))
                }
            })
            .put("relicEquipment",JSONObject(s.relicEquipment))
            .put("relicDryBosses",s.relicDryBosses)
            .put("companionXp",s.companionXp)
            .put("attackUpgrade",s.attackUpgrade).put("defenseUpgrade",s.defenseUpgrade)
            .put("bossWins",s.bossWins)
            .put("achievementsClaimed",JSONArray().apply {
                s.achievementsClaimed.sorted().forEach { put(it) }
            })
            .put("room", s.room).put("position", s.position)
            .put("walls", JSONArray().apply { s.walls.forEach { put(it) } })
            .put("enemy", s.enemy?.let(::wildToJson))
            .put("enemyHp", s.enemyHp).put("playerHp", s.playerHp)
            .put("potions", s.potions)
            .put("supportUsedInBattle", s.supportUsedInBattle)
            .put("inBattle", s.inBattle).put("roomCleared", s.roomCleared)
            .put("completed", s.completed).put("defeated", s.defeated)
            .put("earnedXp", s.earnedXp).put("earnedHappiness", s.earnedHappiness)
            .put("rewardEventId", s.rewardEventId ?: "")
            .put("tapRun", writeTapRun(s.tapRun))
            .put("summonStones", s.summonStones)
            .put("captureOrbs", s.captureOrbs)
            .put("huntTargetId",s.huntTargetId)
            .put("catalog", JSONArray().apply {
                s.catalog.takeLast(150).forEach { put(wildToJson(it)) }
            })
            .put("partyIds", JSONArray().apply { s.partyIds.take(3).forEach { put(it) } })
            .put("message", s.message)
            .put("captures", JSONArray().apply { s.captures.takeLast(100).forEach { put(wildToJson(it)) } })
        // apply() makes the new state available immediately and writes it
        // asynchronously; synchronous commit on EVERY rapid combat tap caused
        // frame stalls and missed input on slower Android flash storage.
        prefs.edit().putString("session", data.toString()).apply()
    }

    private fun readCooldowns(value:JSONObject?):Map<String,Int> {
        if(value==null)return emptyMap()
        return value.keys().asSequence().take(8)
            .mapNotNull { key ->
                if(key.isBlank() || key.length>90) null
                else key to value.optInt(key,0).coerceIn(0,30)
            }.filter { it.second>0 }.toMap()
    }

    /** Ignore orphaned or duplicate gear references; loading a damaged bag
     * must not grant copies of the same unique equipment. */
    private fun readRelicAssignments(obj:JSONObject):Map<String,String> {
        val items=obj.optJSONArray("bossRelics") ?: return emptyMap()
        val known=(0 until items.length()).mapNotNull {
            items.optJSONObject(it)?.let {entry ->
                if(RelicRules.spec(entry.optString("kind",""))!=null)
                    entry.optString("id","") else null
            }
        }.filter{it.isNotBlank()}.take(RelicRules.INVENTORY_LIMIT).toSet()
        val assignment=obj.optJSONObject("relicEquipment") ?: return emptyMap()
        val captures=obj.optJSONArray("captures")
        val petId=obj.optJSONObject("companion")?.optString("id","").orEmpty()
        val owners=mutableSetOf(petId)
        if(captures!=null) for(i in 0 until captures.length())
            owners.add(captures.optJSONObject(i)?.optString("id","").orEmpty())
        val found=mutableSetOf<String>()
        return assignment.keys().asSequence().take(130)
            .mapNotNull {owner ->
                val id=assignment.optString(owner,"")
                if(owner !in owners || id !in known || id in found) null
                else {found+=id;owner to id}
            }.toMap()
    }

    private fun writeChallenge(c:ChallengeState):JSONObject=JSONObject()
        .put("mode",c.mode.name)
        .put("towerFloor",c.towerFloor).put("towerBest",c.towerBest)
        .put("towerClears",c.towerClears)
        .put("towerMilestones",JSONArray().apply {
            c.towerMilestones.sorted().forEach{put(it)}
        })
        .put("arenaRating",c.arenaRating)
        .put("arenaBestRating",c.arenaBestRating)
        .put("arenaWins",c.arenaWins).put("arenaLosses",c.arenaLosses)
        .put("arenaStreak",c.arenaStreak).put("arenaWave",c.arenaWave)
        .put("opponent",c.opponent?.let(::wildToJson))
        .put("opponentHp",c.opponentHp).put("playerHp",c.playerHp)
        .put("attackTimerMs",c.attackTimerMs)
        .put("guardCooldownMs",c.guardCooldownMs)
        .put("guardActive",c.guardActive).put("combo",c.combo)
        .put("skillCooldowns",JSONObject(c.skillCooldowns))
        .put("guardHits",c.guardHits).put("venomCharges",c.venomCharges)
        .put("message",c.message)

    private fun readChallenge(o:JSONObject):ChallengeState {
        val mode=runCatching {
            ChallengeMode.valueOf(o.optString("mode","NONE"))
        }.getOrDefault(ChallengeMode.NONE)
        val enemy=o.optJSONObject("opponent")?.let {
            runCatching { wildFromJson(it) }.getOrNull()
        }
        val hp=o.optInt("playerHp",100).coerceIn(0,200)
        val enemyHp=o.optInt("opponentHp",0).coerceIn(0,100000)
        val safeMode=if(enemy==null || hp<=0 || enemyHp<=0)
            ChallengeMode.NONE else mode
        return ChallengeState(
            mode=safeMode,
            towerFloor=o.optInt("towerFloor",1)
                .coerceIn(1,ChallengeRules.TOWER_LAST_FLOOR),
            towerBest=o.optInt("towerBest",0)
                .coerceIn(0,ChallengeRules.TOWER_LAST_FLOOR),
            towerClears=o.optInt("towerClears",0).coerceIn(0,999999),
            towerMilestones=o.optJSONArray("towerMilestones")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }
                    .filter { it in listOf(5,10,15,20) }.toSet()
            } ?: emptySet(),
            arenaRating=o.optInt("arenaRating",1000).coerceIn(900,3000),
            arenaBestRating=o.optInt("arenaBestRating",1000)
                .coerceIn(1000,3000),
            arenaWins=o.optInt("arenaWins",0).coerceIn(0,999999),
            arenaLosses=o.optInt("arenaLosses",0).coerceIn(0,999999),
            arenaStreak=o.optInt("arenaStreak",0).coerceIn(0,999999),
            arenaWave=o.optInt("arenaWave",1)
                .coerceIn(1,ChallengeRules.ARENA_WAVES),
            opponent=if(safeMode!=ChallengeMode.NONE)enemy else null,
            opponentHp=if(safeMode!=ChallengeMode.NONE)enemyHp else 0,
            playerHp=if(hp>0)hp else 100,
            attackTimerMs=o.optInt("attackTimerMs",3000).coerceIn(1,5000),
            guardCooldownMs=o.optInt("guardCooldownMs",0)
                .coerceIn(0,ChallengeRules.GUARD_COOLDOWN_MS),
            guardActive=o.optBoolean("guardActive",false),
            skillCooldowns=readCooldowns(o.optJSONObject("skillCooldowns")),
            guardHits=o.optInt("guardHits",0).coerceIn(0,1),
            venomCharges=o.optInt("venomCharges",0).coerceIn(0,2),
            combo=o.optInt("combo",0).coerceIn(0,999999),
            message=o.optString("message","塔とアリーナに挑戦しよう！").take(180)
        )
    }

    private fun writeTapRun(r:TapRun):JSONObject=JSONObject()
        .put("floor",r.floor).put("steps",r.steps).put("phase",r.phase.name)
        .put("enemy",r.enemy?.let(::wildToJson)).put("enemyHp",r.enemyHp)
        .put("combo",r.combo).put("bossCountdown",r.bossCountdown)
        .put("attackTimerMs",r.attackTimerMs)
        .put("skillCooldowns",JSONObject(r.skillCooldowns))
        .put("guardHits",r.guardHits).put("venomCharges",r.venomCharges)
        .put("bossTarget",r.bossTarget).put("lane",r.lane)
        .put("weakpointTaps",r.weakpointTaps)
        .put("bestFloor",r.bestFloor).put("deaths",r.deaths)
        .put("distance",r.distance).put("gold",r.gold).put("message",r.message)

    private fun readTapRun(o:JSONObject):TapRun {
        val phase=runCatching { TapPhase.valueOf(o.optString("phase","WALK")) }
            .getOrDefault(TapPhase.WALK)
        val foe=o.optJSONObject("enemy")?.let { runCatching { wildFromJson(it) }.getOrNull() }
        val safePhase=if(phase in listOf(TapPhase.FIGHT,TapPhase.BOSS) && foe==null)
            TapPhase.WALK else phase
        return TapRun(
            floor=o.optInt("floor",1).coerceIn(1,TapAdventure.MAX_FLOOR),
            steps=o.optInt("steps",0).coerceIn(0,TapAdventure.STEPS_PER_FLOOR),
            phase=safePhase,enemy=foe,enemyHp=o.optInt("enemyHp",0).coerceIn(0,20000),
            combo=o.optInt("combo",0).coerceIn(0,100000),
            bossCountdown=o.optInt("bossCountdown",3).coerceIn(1,3),
            attackTimerMs=o.optInt("attackTimerMs",2800).coerceIn(1,5500),
            skillCooldowns=readCooldowns(o.optJSONObject("skillCooldowns")),
            guardHits=o.optInt("guardHits",0).coerceIn(0,1),
            venomCharges=o.optInt("venomCharges",0).coerceIn(0,2),
            bossTarget=if(o.optInt("bossTarget",-1)==1)1 else -1,
            lane=o.optInt("lane",0).coerceIn(-1,1),
            weakpointTaps=o.optInt("weakpointTaps",0).coerceIn(0,3),
            bestFloor=o.optInt("bestFloor",1).coerceIn(1,TapAdventure.MAX_FLOOR),
            deaths=o.optInt("deaths",0).coerceIn(0,99999),
            distance=o.optInt("distance",0).coerceIn(0,1_000_000),
            gold=o.optInt("gold",0).coerceIn(0,999999),
            message=o.optString("message","タップで前進！").take(180)
        )
    }

    private fun writeStatSlots(slots:List<StatSlot>):JSONArray=JSONArray().apply {
        slots.take(3).forEach {
            put(JSONObject().put("kind",it.kind).put("value",it.value)
                .put("rarity",it.rarity))
        }
    }
    private fun readStatSlots(items:JSONArray?):List<StatSlot> {
        if(items==null)return emptyList()
        return (0 until minOf(items.length(),3)).mapNotNull { index ->
            val slot=items.optJSONObject(index) ?: return@mapNotNull null
            val kind=slot.optString("kind","")
            val amount=slot.optInt("value",0)
            val rarity=slot.optInt("rarity",0)
            if(kind !in TalentRules.statKinds || amount !in 1..60 ||
                rarity !in 1..3)null
            else StatSlot(kind,amount,rarity)
        }
    }

    private fun wildToJson(w: WildMonster) = JSONObject()
        .put("id", w.id).put("species", w.species).put("element", w.element)
        .put("maxHp", w.maxHp).put("attack", w.attack)
        .put("description", w.description).put("pixels", w.pixels)
        .put("isBoss", w.isBoss)
        .put("bodyType", w.bodyType).put("stage", w.stage)
        .put("level",w.level).put("xp",w.xp).put("victories",w.victories)
        .put("extraSkillId",w.extraSkillId)
        .put("statSlots",writeStatSlots(w.statSlots))
        .put("lineage", w.lineage)
        .put("motifPrimary", w.motifPrimary).put("motifSecondary", w.motifSecondary)
        .put("designSeed", w.designSeed)
        .put("artFamily", w.artFamily)
        .put("artSourceId", w.artSourceId)
        .put("armorParts", JSONObject(w.armorParts))
        .put("customArmorParts", JSONObject(w.customArmorParts))
        .put("customArmorOffsets", JSONObject().apply {
            w.customArmorOffsets.forEach { (slot, offset) ->
                put(slot, JSONObject()
                    .put("dx", offset.dx).put("dy", offset.dy)
                    .put("scale", offset.scalePercent))
            }
        })

    private fun wildFromJson(o: JSONObject): WildMonster = WildMonster(
        id = o.getString("id"), species = o.getString("species"),
        element = o.getString("element"), maxHp = o.getInt("maxHp"),
        attack = o.getInt("attack"),
        description = o.optString("description", "").take(160),
        pixels = o.optString("pixels", "").take(169),
        isBoss = o.optBoolean("isBoss", false),
        bodyType = o.optString("bodyType", "").take(32),
        stage = o.optInt("stage", 1).coerceIn(1, 3),
        level = o.optInt("level",1).coerceIn(1,GrowthRules.LEVEL_CAP),
        xp = o.optInt("xp",0).coerceIn(0,10000),
        victories = o.optInt("victories",0).coerceIn(0,100000),
        extraSkillId = o.optString("extraSkillId","").take(32),
        statSlots = readStatSlots(o.optJSONArray("statSlots")),
        lineage = o.optString("lineage", "").take(130),
        motifPrimary = o.optString("motifPrimary", "").take(60),
        motifSecondary = o.optString("motifSecondary", "").take(60),
        designSeed = o.optString("designSeed", "").take(64),
        artFamily = o.optString("artFamily", "").take(32)
            .takeIf { it == MonsterArtFamilies.RAIZEL ||
                it == MonsterArtFamilies.LUNA }.orEmpty(),
        armorParts = readArmorParts(o.optJSONObject("armorParts")),
        customArmorParts = readCustomLayers(o.optJSONObject("customArmorParts")),
        customArmorOffsets = readCustomOffsets(o.optJSONObject("customArmorOffsets")),
        artSourceId = o.optString("artSourceId","").take(64)
            .takeIf { id -> id.isBlank() || runCatching {
                java.util.UUID.fromString(id)
            }.isSuccess }.orEmpty()
    )

    private fun readArmorParts(value: JSONObject?): Map<String, String> {
        if(value == null) return emptyMap()
        return runCatching {
            val allowed = dev.glyphlife.monsterquest.pixel.ArmorSlot.entries
            val names = value.keys().asSequence().toList()
            dev.glyphlife.monsterquest.pixel.MonsterArmor.validated(
                names.filter { name -> allowed.any { it.name == name } }
                    .associateWith { name -> value.getString(name) }
            )
        }.getOrDefault(emptyMap())
    }

    private fun readCustomLayers(value: JSONObject?): Map<String, String> {
        if(value == null) return emptyMap()
        return runCatching {
            val result = value.keys().asSequence()
                .filter { key -> dev.glyphlife.monsterquest.pixel.ArmorSlot.entries
                    .any { it.name == key } }
                .associateWith { key -> value.getString(key) }
            dev.glyphlife.monsterquest.pixel.CustomArmorLayers.validated(result)
        }.getOrDefault(emptyMap())
    }
    private fun readCustomOffsets(
        value: JSONObject?
    ): Map<String, dev.glyphlife.monsterquest.pixel.ArmorOffset> {
        if (value == null) return emptyMap()
        return dev.glyphlife.monsterquest.pixel.ArmorSlot.entries.mapNotNull { slot ->
            val pos = value.optJSONObject(slot.name) ?: return@mapNotNull null
            val offset = runCatching {
                dev.glyphlife.monsterquest.pixel.ArmorOffset(
                    pos.getInt("dx"), pos.getInt("dy"), pos.optInt("scale", 100))
            }.getOrNull() ?: return@mapNotNull null
            slot.name to offset
        }.toMap()
    }

}
