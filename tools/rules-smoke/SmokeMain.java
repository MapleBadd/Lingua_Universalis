import com.linguauniversalis.core.interaction.DailyInteractionTracker;
import com.linguauniversalis.core.rule.DormancyClock;
import com.linguauniversalis.core.rule.InteractionRules;
import com.linguauniversalis.core.rule.MoodActivityClock;
import com.linguauniversalis.core.rule.RelationshipRules;
import com.linguauniversalis.core.state.MonsterGirlState;
import com.linguauniversalis.core.species.SpeciesRegistry;

public class SmokeMain {
    static int fails = 0;

    static void check(boolean cond, String msg) {
        if (!cond) {
            fails++;
            System.out.println("FAIL: " + msg);
        } else {
            System.out.println("ok: " + msg);
        }
    }

    public static void main(String[] args) {
        com.linguauniversalis.core.taxonomy.Taxonomy.registerDefaults();
        com.linguauniversalis.core.behavior.TaxonTemplates.registerDefaults();
        com.linguauniversalis.core.behavior.OrdoTemplates.registerDefaults();
        com.linguauniversalis.core.species.BuiltinSpecies.registerAll();
        com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.registerDefaults();
        check(SpeciesRegistry.contains("arakne") && SpeciesRegistry.contains("nekomata"), "species registered");
        check("nekomata".equals(SpeciesRegistry.get("nekomata").id()), "nekomata profile id");

        DailyInteractionTracker tracker = new DailyInteractionTracker();
        MonsterGirlState s = new MonsterGirlState();
        String player = "p-1", girl = "g-1";
        long day = 1000;

        InteractionRules.feed(s, SpeciesRegistry.get("nekomata"), true, 5, player, girl, day, tracker, true);
        check(s.affection() == 1, "feed +1 (aff=1), got " + s.affection());
        InteractionRules.feed(s, SpeciesRegistry.get("nekomata"), true, 5, player, girl, day, tracker, true);
        check(s.affection() == 1, "feed capped once/day (aff=1)");
        InteractionRules.feed(s, SpeciesRegistry.get("nekomata"), true, 5, player, girl, day + 1, tracker, true);
        check(s.affection() == 2, "next day feed works (aff=2)");

        s.addAffection(-1);
        boolean reverted = RelationshipRules.enforceCoherence(s);
        check(!reverted && s.boundPlayerUuid() == null, "no bound yet -> nothing to revert");

        // 野生认主路径：未绑定的野生个体被投喂 → 好感 +1（养成起点），好感不足 10 不绑定
        MonsterGirlState wildFeed = new MonsterGirlState();
        check(InteractionRules.feed(wildFeed, SpeciesRegistry.get("arakne"), false, 4,
                        player, "g-wild", day, tracker, false).applied()
                        && wildFeed.affection() == 1 && wildFeed.boundPlayerUuid() == null,
                "wild feed gives +1 affection without instant bond (arakne)");
        // 陌生人第二次同一天喂 → 当日上限拒绝
        check(!InteractionRules.feed(wildFeed, SpeciesRegistry.get("arakne"), false, 4,
                        player, "g-wild", day, tracker, false).applied()
                        && wildFeed.affection() == 1,
                "wild feed capped once/day");
        // 喂到好感 9 → 次日再喂 +1 → 跨过 10 → 自动绑定喂食者为友善对象
        wildFeed.setAffection(9);
        InteractionRules.Outcome bondOut = InteractionRules.feed(
                wildFeed, SpeciesRegistry.get("arakne"), false, 4,
                player, "g-wild", day + 1, tracker, false);
        check(bondOut.applied() && wildFeed.affection() == 10
                        && player.equals(wildFeed.boundPlayerUuid())
                        && "friendly".equals(wildFeed.stageId()),
                "wild feed crossing 10 auto-bonds feeder as friendly owner");

        for (int i = 0; i < 9; i++) {
            s.addAffection(1);
        }
        s.setBoundPlayerUuid(player);
        check(s.affection() == 10 && !RelationshipRules.enforceCoherence(s), "friendly at 10, no revert");

        s.addAffection(-1);
        reverted = RelationshipRules.enforceCoherence(s);
        check(reverted && s.boundPlayerUuid() == null && s.affection() == 9, "hit 10->9 reverts to wild");

        s.setAffection(100);
        RelationshipRules.enforceCoherence(s);
        check(s.isCompanion() && s.companionUnlocked(), "companion unlocked at 100");
        s.addAffection(-200);
        check(s.isCompanion(), "companion never reverts (still companion at low aff)");

        s.setAffection(99);
        check(!RelationshipRules.canCommand(s), "no commands below 100");
        s.setAffection(150);
        check(RelationshipRules.canCommand(s), "commands at 150");

        s.setAffection(200);
        check(RelationshipRules.tryVow(s), "vow applied");
        s.addAffection(-50);
        check(s.affection() == 200 && s.vowed(), "vow locks affection at 200");

        DormancyClock clock = new DormancyClock();
        MonsterGirlState d = new MonsterGirlState();
        d.setCompanionUnlocked(true);
        d.setAffection(0);
        d.setMood(0);
        long t = 1_000_000L;
        clock.update(d, false, t);
        DormancyClock.Event ev = clock.update(d, false, t + 24L * 3600 * 1000);
        check(ev == DormancyClock.Event.ENTERED_DORMANT && d.dormant(), "dormant entered after 24h");
        clock.update(d, false, t + 48L * 3600 * 1000);
        check(d.dormant(), "still dormant");
        check(clock.wakeByBoundPlayer(d, true), "woken by bound player");

        MoodActivityClock mac = new MoodActivityClock();
        for (int m = 0; m < 60; m++) {
            mac.recordSample(7, 0, 0);
        }
        check(mac.endOfDay(7) == 0, "1st inactive day: no loss (streak=1)");
        for (int dd = 8; dd <= 9; dd++) {
            for (int m = 0; m < 60; m++) {
                mac.recordSample(dd, 0, 0);
            }
            mac.endOfDay(dd);
        }
        int loss = 0;
        for (int m = 0; m < 60; m++) {
            mac.recordSample(10, 0, 0);
        }
        loss = mac.endOfDay(10);
        check(loss == 1, "4th inactive day (streak>=3) mood -1, got " + loss);

        MoodActivityClock mac2 = new MoodActivityClock();
        for (int m = 0; m < 60; m++) {
            mac2.recordSample(7, m, m);
        }
        check(mac2.endOfDay(7) == 0 && mac2.inactiveStreakDays() == 0, "active day resets streak");

        // 战败 / 急救（设计 §5）
        MonsterGirlState k = new MonsterGirlState();
        check(!com.linguauniversalis.core.rule.KnockdownRules.shouldKnockdown(k), "wild girl dies, no knockdown");
        k.setAffection(50);
        k.setBoundPlayerUuid(player);
        check(com.linguauniversalis.core.rule.KnockdownRules.shouldKnockdown(k), "bonded girl knockdown eligible");
        com.linguauniversalis.core.rule.KnockdownRules.beginKnockdown(k);
        check(k.downed() && k.isDownedImmune(), "knockdown entered with lock");
        check(!com.linguauniversalis.core.rule.KnockdownRules.canUseMedkit(k), "medkit blocked during lock");
        check(com.linguauniversalis.core.rule.KnockdownRules.applyMedkit(k, 40f) == -1f, "medkit not consumed during lock");
        for (int tt = 0; tt < 60 * 20; tt++) {
            com.linguauniversalis.core.rule.KnockdownRules.tickKnockdown(k);
        }
        check(k.isDownedVulnerable() && !k.isDownedImmune(), "lock ended, vulnerable");
        check(com.linguauniversalis.core.rule.KnockdownRules.isLethalWhileDowned(k), "attack while vulnerable kills");
        float healed = com.linguauniversalis.core.rule.KnockdownRules.applyMedkit(k, 40f);
        check(!k.downed() && healed == 4f, "medkit heals 10% (4) and ends knockdown");
        check(k.satiety() == 20, "satiety already max stays 20 (medkit +6 capped)");

        // 图鉴知识解锁（设计 §10 + ObservationRules）
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia book =
                new com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia();
        check(!book.hasSighted("nekomata"), "not sighted yet");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onSighting(book, "nekomata");
        check(book.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.SIGHT)
                && book.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.BASIC_INFO),
                "sighting unlocks sight+basic");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onZoomObserve(book, "nekomata");
        check(book.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.TAXONOMY)
                && book.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.HOSTILITY),
                "zoom unlocks taxonomy+hostility");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onLikedInteraction(book, "nekomata");
        check(book.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.PREFERENCES), "liked interaction unlocks preference");
        check(book.sightedCount() == 1, "one species sighted");
        check(!book.isUnlocked("arakne", com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.SIGHT),
                "arakne not sighted");
        check(SpeciesRegistry.get("nekomata").isLikedFood("minecraft:cod"), "cod is liked by nekomata");
        check(!SpeciesRegistry.get("nekomata").isLikedFood("minecraft:beef"), "beef not liked by nekomata");

        // 猫又礼物表（设计 §12：基础 25%×4 不含绿宝石）
        com.linguauniversalis.core.loot.WeightedGiftTable gifts =
                com.linguauniversalis.core.loot.WeightedGiftTable.nekomataGiftTable();
        java.util.Random rnd = new java.util.Random(42);
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < 40000; i++) {
            counts.merge(gifts.roll(rnd), 1, Integer::sum);
        }
        check(counts.size() == 4, "gift table has 4 distinct results");
        check(!counts.containsKey("minecraft:emerald"), "no emerald in base table");
        int floor = 40000 / 4 - 2000; // 25% ±5%
        for (String item : new String[]{"minecraft:rotten_flesh", "minecraft:bone",
                "minecraft:iron_ingot", "minecraft:gunpowder"}) {
            check(counts.get(item) != null && counts.get(item) > floor,
                    "gift item roughly 25%: " + item + "=" + counts.get(item));
        }

        // 命令权 / 低落不听指挥 / 游荡范围（设计 §2 §3 §7）
        com.linguauniversalis.core.behavior.CommandRules.CommandMode mode =
                com.linguauniversalis.core.behavior.CommandRules.CommandMode.WANDER;
        check("wander".equals(mode.englishId), "command mode english id");
        MonsterGirlState c = new MonsterGirlState();
        c.setAffection(100);
        c.setBoundPlayerUuid(player);
        check(com.linguauniversalis.core.behavior.CommandRules.canCommand(c, player), "companion+100 can command");
        check(!com.linguauniversalis.core.behavior.CommandRules.canCommand(c, "stranger"), "stranger cannot command");
        c.setMood(10);
        check(!com.linguauniversalis.core.behavior.CommandRules.canCommand(c, player), "depressed refuses commands");
        check(com.linguauniversalis.core.behavior.CommandRules.isInsubordinate(c), "insubordinate flag while depressed");
        c.setMood(50);
        c.setDormant(true);
        check(!com.linguauniversalis.core.behavior.CommandRules.canCommand(c, player), "dormant refuses commands");

        com.linguauniversalis.core.behavior.WanderAnchor anchor = new com.linguauniversalis.core.behavior.WanderAnchor(0, 0);
        check(anchor.inWanderArea(0, 0), "center in wander area");
        check(anchor.inWanderArea(4 * 16 + 15, -4 * 16), "edge chunk (4) in wander area");
        check(!anchor.inWanderArea(5 * 16, 0), "chunk 5 out of wander area");
        com.linguauniversalis.core.behavior.WanderAnchor anchor2 = new com.linguauniversalis.core.behavior.WanderAnchor(33, -20);
        check(anchor2.inWanderArea(33 + 4 * 16, -20 - 4 * 16), "relative edge in");
        check(!anchor2.inWanderArea(33 + 5 * 16, -20), "relative chunk 5 out");

        // 低落击杀回心情（设计 §3 模板）
        com.linguauniversalis.core.rule.LowMoodRecoveryTracker rec = new com.linguauniversalis.core.rule.LowMoodRecoveryTracker();
        MonsterGirlState d2 = new MonsterGirlState();
        d2.setMood(10);
        long t0 = 5_000_000L;
        rec.onKill(t0);
        check(rec.update(d2, t0 + 60_000L), "mood recovered to 30 after kill within window");
        check(d2.mood() == 30, "mood equals 30");
        d2.setMood(10);
        rec.onKill(t0);
        check(!rec.update(d2, t0 + 121_000L) && d2.mood() == 10, "recovery window expired");

        // 状态快照（HUD/百晓镜同步用）
        MonsterGirlState s2 = new MonsterGirlState();
        s2.setAffection(150);
        s2.setBoundPlayerUuid(player);
        s2.setCompanionUnlocked(true);
        s2.setMood(55);
        s2.setSatiety(12);
        com.linguauniversalis.core.state.GirlStateSnapshot snap =
                com.linguauniversalis.core.state.GirlStateSnapshot.from(s2);
        check("companion".equals(snap.stageId()) && snap.affection() == 150 && snap.mood() == 55
                && snap.satiety() == 12 && snap.boundPlayerUuid().equals(player), "snapshot fields match");

        // 领地巡游·巢心守卫（阿拉克涅，设计 §11）
        com.linguauniversalis.core.behavior.NestGuard nest = new com.linguauniversalis.core.behavior.NestGuard(0, 64, 0);
        check(nest.inTerritory(23, 0) && !nest.inTerritory(25, 0), "territory horizontal 24");
        check(nest.inThreatSphere(8, 64, 8), "inside threat sphere (dist<12)");
        check(!nest.inThreatSphere(9, 64, 9), "outside threat sphere (dist>12)"); // ~12.7
        check(nest.shouldActivelyAttackPlayer(false, false), "wild attacks stranger in threat");
        check(!nest.shouldActivelyAttackPlayer(false, true), "friendly spares bound player");
        check(!nest.shouldActivelyAttackPlayer(true, true), "companion never actively attacks");
        check(!nest.shouldActivelyAttackPlayer(true, false), "companion passive to strangers");
        check(nest.shouldAttackOnNestTouch(true, false), "companion redline vs stranger");
        check(!nest.shouldAttackOnNestTouch(true, true), "companion self exempt from redline");
        check(com.linguauniversalis.core.behavior.NestGuard.updateEnrage(false, true, false, true, true) == false,
                "companion never enrages from nest loss");
        check(com.linguauniversalis.core.behavior.NestGuard.updateEnrage(false, false, false, true, false),
                "wild enrages when nest destroyed");
        check(!com.linguauniversalis.core.behavior.NestGuard.updateEnrage(true, false, true, true, false),
                "enrage clears when nest exists & chunk loaded");
        check(com.linguauniversalis.core.behavior.NestGuard.updateEnrage(false, false, true, false, true),
                "enrage when outside territory & nest chunk unloaded");
        // 无巢狂暴开关（刷怪蛋生成个体 = false，避免出生即狂暴）
        check(com.linguauniversalis.core.behavior.NestGuard.updateEnrage(false, false, true, false, true, true),
                "wild rages when no nest (rageWithoutNest default true)");
        check(!com.linguauniversalis.core.behavior.NestGuard.updateEnrage(false, false, false, false, true, true),
                "egg-born does not rage when no nest (rageWithoutNest=false)");
        check(!com.linguauniversalis.core.behavior.NestGuard.updateEnrage(true, true, true, false, false, false),
                "companion never rages even with rageWithoutNest=true");
        // 领地巡游纲模板：ragesWithoutNest 默认 true，物种 flag 可覆盖
        com.linguauniversalis.core.species.SpeciesProfile territorialNoRage =
                com.linguauniversalis.core.species.SpeciesProfile.builder("tnr")
                        .name("不狂暴领地种", "tnr")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CRUSTACEA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.TERRITORIALIS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.ORDO,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PALPI)
                        .templateFlag(
                                com.linguauniversalis.core.behavior.TemplateKeys.ORDO_RAGE_WITHOUT_NEST, false)
                        .build();
        check(com.linguauniversalis.core.behavior.OrdoTemplates.of(
                        com.linguauniversalis.core.species.SpeciesRegistry.get("arakne"))
                        .ragesWithoutNest(com.linguauniversalis.core.species.SpeciesRegistry.get("arakne")),
                "territorialis ragesWithoutNest default true (arakne)");
        check(!com.linguauniversalis.core.behavior.OrdoTemplates.of(territorialNoRage)
                        .ragesWithoutNest(territorialNoRage),
                "species templateFlag can disable rageWithoutNest");
        // 同种族敌意·物种变量：阿拉克涅巢穴威胁内攻击同种族 = true、狂暴也攻击同种族 = true
        check(com.linguauniversalis.core.behavior.OrdoTemplates.of(
                        com.linguauniversalis.core.species.SpeciesRegistry.get("arakne"))
                        .nestThreatAttacksOwnKind(
                                com.linguauniversalis.core.species.SpeciesRegistry.get("arakne")),
                "arakne nest-threat attacks own kind (variable true)");
        check(com.linguauniversalis.core.behavior.OrdoTemplates.of(
                        com.linguauniversalis.core.species.SpeciesRegistry.get("arakne"))
                        .enragedAttacksOwnKind(
                                com.linguauniversalis.core.species.SpeciesRegistry.get("arakne")),
                "arakne enraged attacks own kind (variable true)");
        // 物种级可覆盖为 false（温和物种：同族不打同族）
        com.linguauniversalis.core.species.SpeciesProfile gentleOwnKind =
                com.linguauniversalis.core.species.SpeciesProfile.builder("gk")
                        .name("温和同族种", "gk")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.TERRITORIALIS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PLUMAE)
                        .templateFlag(
                                com.linguauniversalis.core.behavior.TemplateKeys.ORDO_NEST_THREAT_ATTACKS_OWN_KIND,
                                false)
                        .templateFlag(
                                com.linguauniversalis.core.behavior.TemplateKeys.ORDO_ENRAGED_ATTACKS_OWN_KIND,
                                false)
                        .build();
        check(!com.linguauniversalis.core.behavior.OrdoTemplates.of(gentleOwnKind)
                        .nestThreatAttacksOwnKind(gentleOwnKind)
                        && !com.linguauniversalis.core.behavior.OrdoTemplates.of(gentleOwnKind)
                        .enragedAttacksOwnKind(gentleOwnKind),
                "species templateFlag can disable own-kind hostility (both false)");

        // 饱食/进食与自给（设计 §4）
        MonsterGirlState h1 = new MonsterGirlState();
        check(!com.linguauniversalis.core.rule.HungerRules.isStarving(h1), "full satiety not starving");
        check(!com.linguauniversalis.core.rule.HungerRules.needsForage(h1), "full satiety no forage need");
        h1.setSatiety(9);
        check(com.linguauniversalis.core.rule.HungerRules.needsForage(h1), "satiety<10 needs forage");
        check(com.linguauniversalis.core.rule.HungerRules.canSelfForage(h1), "wild can self-forage");
        check(com.linguauniversalis.core.rule.HungerRules.canForageFromNest(h1,
                com.linguauniversalis.core.species.SpeciesProfile.SpawnType.NATURAL, false), "natural conjures without nest");
        check(!com.linguauniversalis.core.rule.HungerRules.canForageFromNest(h1,
                com.linguauniversalis.core.species.SpeciesProfile.SpawnType.FIXED, false), "fixed needs nest present");
        check(com.linguauniversalis.core.rule.HungerRules.canForageFromNest(h1,
                com.linguauniversalis.core.species.SpeciesProfile.SpawnType.FIXED, true), "fixed forages with nest");
        MonsterGirlState h2 = new MonsterGirlState();
        h2.setAffection(150);
        h2.setCompanionUnlocked(true);
        h2.setSatiety(5);
        check(!com.linguauniversalis.core.rule.HungerRules.canSelfForage(h2), "companion cannot self-forage");
        MonsterGirlState h3 = new MonsterGirlState();
        h3.setSatiety(0);
        h3.setDowned(true);
        h3.setDownedLockTicks(100);
        check(!com.linguauniversalis.core.rule.HungerRules.starvationDamageActive(h3), "starvation blocked during lock");
        h3.setDownedLockTicks(0);
        check(com.linguauniversalis.core.rule.HungerRules.starvationDamageActive(h3), "starvation active after lock at satiety 0");

        // 物种档案完整性（数据完整性守卫）
        boolean profileOk = true;
        for (com.linguauniversalis.core.species.SpeciesProfile profile
                : com.linguauniversalis.core.species.SpeciesRegistry.all()) {
            if (profile.hotbarSlotNames().isEmpty()
                    || !profile.hotbarSlotNames().contains("main_hand")
                    || !profile.hotbarSlotNames().contains("off_hand")
                    || profile.backpackSize() < 0
                    || !com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.contains(profile.moodLowTemplate())) {
                profileOk = false;
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String slot : profile.hotbarSlotNames()) {
                if (!seen.add(slot)) {
                    profileOk = false;
                }
            }
        }
        check(profileOk, "all profiles structurally valid (slots unique, main/off present, mood template known)");
        com.linguauniversalis.core.species.SpeciesProfile arakne = SpeciesRegistry.get("arakne");
        com.linguauniversalis.core.species.SpeciesProfile neko = SpeciesRegistry.get("nekomata");
        check(arakne.hotbarSlotNames().size() == 4 && arakne.backpackSize() == 9, "arakne slots 4+9");
        check(neko.hotbarSlotNames().size() == 2 && neko.backpackSize() == 8, "nekomata slots 2+8");
        check(arakne.isLikedFood("minecraft:beef") && neko.isLikedFood("minecraft:cod"), "liked foods present");
        check(com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.UMBRA.element().equals("umbra")
                && com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.ORDO.element().equals("ordo"),
                "element tags umbra/ordo");

        // 战斗面板数值（阿拉克涅/猫又，设计 §11/§12）
        check(arakne.meleeDamage() == 15f && arakne.rangedDamage() == 0f, "arakne melee 15");
        check(neko.meleeDamage() == 5f && neko.rangedDamage() == 10f
                && neko.undeadDamageMultiplier() == 5f
                && neko.undeadDamageReduction() == 0.5f, "nekomata combat values");

        // GirlState 二进制编解码往返
        MonsterGirlState p1 = new MonsterGirlState();
        MonsterGirlState back1 = com.linguauniversalis.core.state.GirlStatePersistence.decode(
                com.linguauniversalis.core.state.GirlStatePersistence.encode(p1));
        check(back1.affection() == 0 && back1.mood() == 70 && back1.satiety() == 20
                && back1.boundPlayerUuid() == null, "default state roundtrip");
        MonsterGirlState p2 = new MonsterGirlState();
        p2.setAffection(187);
        p2.setMood(3);
        p2.setSatiety(9);
        p2.setSynergy(42);
        p2.setBoundPlayerUuid("01234567-89ab-cdef-0123-456789abcdef");
        p2.setCompanionUnlocked(true);
        p2.setVowed(true);
        p2.setDormant(true);
        p2.setDowned(true);
        p2.setDownedLockTicks(1234L);
        MonsterGirlState back2 = com.linguauniversalis.core.state.GirlStatePersistence.decode(
                com.linguauniversalis.core.state.GirlStatePersistence.encode(p2));
        check(back2.affection() == 200 && back2.vowed(), "vowed locks affection on decode");
        check(back2.mood() == 3 && back2.satiety() == 9 && back2.synergy() == 42, "values survive roundtrip");
        check("01234567-89ab-cdef-0123-456789abcdef".equals(back2.boundPlayerUuid()), "uuid survives roundtrip");
        check(back2.companionUnlocked() && back2.dormant() && back2.downed()
                && back2.downedLockTicks() == 1234L, "flags survive roundtrip");

        // 图鉴持久化编解码往返
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia book2 =
                new com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia();
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia bookEmpty =
                com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.decode(
                        com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.encode(book2));
        check(bookEmpty.sightedCount() == 0, "empty encyclopedia roundtrip");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onSighting(book2, "arakne");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onZoomObserve(book2, "arakne");
        com.linguauniversalis.core.encyclopedia.ObservationRules.onLikedInteraction(book2, "nekomata");
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia bookBack =
                com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.decode(
                        com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.encode(book2));
        check(bookBack.sightedCount() == 2, "two species roundtrip");
        check(bookBack.isUnlocked("arakne",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.TAXONOMY)
                && bookBack.isUnlocked("nekomata",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.PREFERENCES)
                && !bookBack.isUnlocked("arakne",
                com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.PREFERENCES),
                "kind bitmask survives roundtrip per species");
        check(java.util.Objects.equals(
                java.util.List.copyOf(book2.sightedSpecies()),
                java.util.List.copyOf(bookBack.sightedSpecies())), "species insertion order preserved");

        // 可持久化每日限额器（重进存档不重置当天奖励）
        com.linguauniversalis.core.interaction.PersistentDailyLimiter lim =
                new com.linguauniversalis.core.interaction.PersistentDailyLimiter();
        check(lim.tryGain("feed", player, girl, 500), "limiter first gain day 500");
        check(!lim.tryGain("feed", player, girl, 500), "limiter blocks same day");
        check(lim.tryGain("feed", player, girl, 501), "limiter allows next day");
        check(lim.tryGain("pet", player, girl, 501), "limiter separate per behavior");
        String enc = lim.snapshotEncoded(501);
        check(!enc.isEmpty(), "limiter snapshot non-empty");
        com.linguauniversalis.core.interaction.PersistentDailyLimiter lim2 =
                new com.linguauniversalis.core.interaction.PersistentDailyLimiter();
        lim2.fromEncoded(enc);
        check(!lim2.tryGain("feed", player, girl, 501), "restored limiter still blocks same day (feed)");
        check(lim2.tryGain("feed", player, girl, 502), "restored limiter allows newer day");
        String old = lim.snapshotEncoded(1000); // 仅保留 day>=999
        com.linguauniversalis.core.interaction.PersistentDailyLimiter lim3 =
                new com.linguauniversalis.core.interaction.PersistentDailyLimiter();
        lim3.fromEncoded(old);
        check(lim3.tryGain("feed", player, girl, 500), "pruned old entries expire");

        // 敌意/目标选择骨架（设计 §11/§12/§13）
        com.linguauniversalis.core.behavior.HostilityRules.TargetKind playerKind =
                com.linguauniversalis.core.behavior.HostilityRules.TargetKind.PLAYER;
        com.linguauniversalis.core.behavior.HostilityRules.TargetKind undeadKind =
                com.linguauniversalis.core.behavior.HostilityRules.TargetKind.UNDEAD;
        com.linguauniversalis.core.behavior.HostilityRules.TargetKind mobKind =
                com.linguauniversalis.core.behavior.HostilityRules.TargetKind.OTHER_MOB;
        MonsterGirlState wild = new MonsterGirlState(); // 野生：无绑定
        // 阿拉克涅（领地巡游纲）：守巢——巢心威胁范围（12格）内的【一切生物】都是目标
        // （玩家/亡灵/普通生物一视同仁，仅豁免绑玩家）；范围外一概不主动攻击
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, playerKind, 100.0, true, false, false, false),
                "wild arakne guards: player inside threat (10<12) attacked");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, playerKind, 400.0, false, false, false, false),
                "wild arakne ignores player outside threat (20>12)");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, mobKind, 100.0, true, false, false, false),
                "wild arakne guards: ordinary mob inside threat attacked");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, mobKind, 400.0, false, false, false, false),
                "wild arakne ignores ordinary mob outside threat");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, undeadKind, 100.0, true, false, false, false),
                "wild arakne guards: undead inside threat attacked");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", wild, undeadKind, 400.0, false, false, false, false),
                "wild arakne ignores undead outside threat");
        // 阿拉克涅：友善豁免绑玩家，仍打陌生人
        MonsterGirlState friendly = new MonsterGirlState();
        friendly.setAffection(50);
        friendly.setBoundPlayerUuid(player);
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", friendly, playerKind, 100.0, true, true, false, false),
                "friendly arakne spares bound player in threat");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", friendly, playerKind, 100.0, true, false, false, false),
                "friendly arakne still attacks stranger in threat");
        // 伙伴：永不主动攻击玩家；被非绑定者招惹自卫反击
        MonsterGirlState companion = new MonsterGirlState();
        companion.setAffection(150);
        companion.setCompanionUnlocked(true);
        companion.setBoundPlayerUuid(player);
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", companion, playerKind, 100.0, true, false, false, false),
                "companion never proactively attacks any player");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", companion, playerKind, 100.0, true, true, true, false),
                "companion does not retaliate bound player");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", companion, playerKind, 100.0, true, false, true, false),
                "companion retaliates stranger when provoked");
        // 猫又：不主动攻击玩家（拟态），猎杀亡灵；被招惹反击玩家
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", wild, playerKind, 100.0, true, false, false, false),
                "wild nekomata passive to players (mimeticus)");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", wild, undeadKind, 200.0, false, false, false, false),
                "wild nekomata hunts undead in 16 block radius");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", wild, undeadKind, 900.0, false, false, false, false),
                "nekomata ignores undead beyond hunt radius (30>16)");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", wild, playerKind, 100.0, true, false, true, false),
                "nekomata retaliates player when provoked");
        // 伙伴猫又：不主动猎亡灵（伙伴后失效），不反击绑玩家
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", companion, undeadKind, 100.0, false, false, false, false),
                "companion nekomata stops undead hunt");
        // 低落·随机攻击模板：只打近战范围内非玩家生物
        MonsterGirlState depressed = new MonsterGirlState();
        depressed.setMood(5);
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", depressed, playerKind, 4.0, true, false, false, false),
                "depressed ignores players");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", depressed, mobKind, 4.0, true, false, false, false),
                "depressed melee-attacks non-player mob nearby");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", depressed, mobKind, 400.0, false, false, false, false),
                "depressed does not chase far mobs (no target selection beyond melee)");
        // 倒地/休眠不战斗
        MonsterGirlState downed = new MonsterGirlState();
        downed.setDowned(true);
        downed.setDownedLockTicks(1);
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "arakne", downed, playerKind, 4.0, true, false, false, false),
                "downed never fights");
        MonsterGirlState dormant = new MonsterGirlState();
        dormant.setDormant(true);
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                "nekomata", dormant, undeadKind, 4.0, true, false, false, false),
                "dormant never fights");

        // 敌意威胁以巢心为圆心（领地巡游）：巢心近 → 打；巢心远 → 不打（即使本体近）
        MonsterGirlState wildNest = new MonsterGirlState();
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, playerKind, 100.0, 400.0, true, false, false, false),
                "arakne guards: player near nest attacked (nest 10, self 20)");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, playerKind, 400.0, 100.0, true, false, false, false),
                "arakne ignores player far from nest even if near self");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, mobKind, 100.0, 400.0, true, false, false, false),
                "arakne guards: ordinary mob near nest attacked even if self far");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, undeadKind, 100.0, 400.0, true, false, false, false),
                "arakne guards: undead near nest attacked even if self far");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, mobKind, 400.0, 100.0, true, false, false, false),
                "arakne ignores ordinary mob far from nest even if near self");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, playerKind,
                        com.linguauniversalis.core.behavior.HostilityRules.NO_NEST, 100.0,
                        true, false, false, false),
                "nestless arakne does not guard anything from self center");
        // 被惹反击不受距离限制：巢外远距离攻击她 → 仍反击（狂暴目标保留逻辑在 AI 层）
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, playerKind, 400.0, 400.0, false, false, true, false),
                "arakne retaliates player provoked from outside nest threat");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, mobKind, 400.0, 400.0, false, false, true, false),
                "arakne retaliates mob provoked from outside nest threat");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, playerKind, 400.0, 400.0, false, true, true, false),
                "arakne never retaliates bound owner");
        // 狂暴个体：即便离巢远也攻击（enraged 分支）；同物种豁免在 AI 层（实体同类过滤）
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                        "arakne", wildNest, mobKind, 400.0, 400.0, true, false, false, true),
                "enraged arakne attacks anything regardless of nest distance");

        // 模块化：另一领地巡游纲物种复用模板，但用不同数值（领地 40 / 威胁 5）
        com.linguauniversalis.core.species.SpeciesProfile.Builder builder =
                com.linguauniversalis.core.species.SpeciesProfile.builder("t2")
                        .name("领地种二", "t2")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CRUSTACEA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.TERRITORIALIS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.ORDO,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PALPI)
                        .templateParam(
                                com.linguauniversalis.core.behavior.OrdoTemplates.KEY_TERRITORY_HORIZONTAL, 40.0)
                        .templateParam(com.linguauniversalis.core.behavior.OrdoTemplates.KEY_THREAT_RADIUS, 5.0);
        com.linguauniversalis.core.species.SpeciesProfile t2 = builder.build();
        check(com.linguauniversalis.core.behavior.OrdoTemplates.of(t2)
                        .guardsTerritoryAgainstPlayers(),
                "territorial template reused by species t2 (guards players)");
        check(com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                t2, new MonsterGirlState(), playerKind, 16.0, true, false, false, false),
                "t2 threat 5: player at 4 (16 sq) attacked");
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                t2, new MonsterGirlState(), playerKind, 36.0, false, false, false, false),
                "t2 threat 5: player at 6 (36 sq) out of range");
        // 纲模板默认未注册时回落中性（新纲物种安全默认：不主动攻击）
        com.linguauniversalis.core.species.SpeciesProfile nomad =
                com.linguauniversalis.core.species.SpeciesProfile.builder("n2")
                        .name("流浪种", "n2")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.NOMADICUS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PLUMAE)
                        .build();
        check(!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                nomad, new MonsterGirlState(), playerKind, 4.0, true, false, false, false),
                "unregistered ordo falls back to neutral (no proactive attack)");

        // 分类学模块化：可增删类目（模拟附属模组注册新科）
        com.linguauniversalis.core.taxonomy.Taxonomy.Taxon customSectio =
                com.linguauniversalis.core.taxonomy.Taxonomy.taxon(
                        com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO,
                        "customia", "sectio customia", "自定义科");
        int sectioBefore = com.linguauniversalis.core.taxonomy.Taxonomy.count(
                com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO);
        com.linguauniversalis.core.taxonomy.Taxonomy.register(customSectio);
        check(com.linguauniversalis.core.taxonomy.Taxonomy.contains(
                        com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO, "customia")
                && com.linguauniversalis.core.taxonomy.Taxonomy.count(
                        com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO) == sectioBefore + 1,
                "taxonomy category registered (addon extensible)");
        check(com.linguauniversalis.core.taxonomy.Taxonomy.unregister(
                        com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO, "customia"),
                "taxonomy category removed");
        check(!com.linguauniversalis.core.taxonomy.Taxonomy.contains(
                        com.linguauniversalis.core.taxonomy.Taxonomy.Rank.SECTIO, "customia"),
                "taxonomy category gone after removal");

        // 模板键目录与纲模板键同源（防漂移）
        check(com.linguauniversalis.core.behavior.OrdoTemplates.KEY_TERRITORY_HORIZONTAL
                        .equals(com.linguauniversalis.core.behavior.TemplateKeys.ORDO_TERRITORY_HORIZONTAL)
                && com.linguauniversalis.core.behavior.OrdoTemplates.KEY_THREAT_RADIUS
                        .equals(com.linguauniversalis.core.behavior.TemplateKeys.ORDO_THREAT_RADIUS)
                && com.linguauniversalis.core.behavior.OrdoTemplates.KEY_UNDEAD_HUNT_RADIUS
                        .equals(com.linguauniversalis.core.behavior.TemplateKeys.ORDO_UNDEAD_HUNT_RADIUS),
                "ordo template keys are TemplateKeys (single source)");

        // 通用阶元默认特性：界(脊索=跳跃2/自动上阶)，科(绒=流血/闪避，触肢=附肢槽)
        com.linguauniversalis.core.species.SpeciesProfile chordPellis =
                com.linguauniversalis.core.species.SpeciesProfile.builder("cp")
                        .name("脊绒测试", "cp")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.NOMADICUS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PELLIS)
                        .build();
        check(chordPellis.featureNumber(com.linguauniversalis.core.behavior.TemplateKeys.KINGDOM_JUMP_SCALE, 1.0) == 2.0,
                "chordata default jump scale 2.0 via featureNumber");
        check(chordPellis.featureFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_KINGDOM_AUTO_STEP_UP, false),
                "chordata auto step-up default true");
        check(chordPellis.featureFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SECTIO_BLEED, false),
                "pellis bleed default true");
        check(chordPellis.featureFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SECTIO_PROJECTILE_DODGE, false),
                "pellis projectile-dodge default true");
        check(!chordPellis.featureFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_KINGDOM_CRIT_IMMUNE, false),
                "chordata does not inherit amorpha crit-immunity");

        // 物种级 flag 覆盖模板默认
        com.linguauniversalis.core.species.SpeciesProfile noBleed =
                com.linguauniversalis.core.species.SpeciesProfile.builder("nb")
                        .name("无流血", "nb")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.NOMADICUS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PELLIS)
                        .templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SECTIO_BLEED, false)
                        .build();
        check(!noBleed.featureFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SECTIO_BLEED, true),
                "species flag overrides template default (bleed off)");

        // 物种专属战斗能力参数（设计 §11/§12；参数 0 = 无该能力）
        com.linguauniversalis.core.species.SpeciesProfile arakneAb =
                SpeciesRegistry.get("arakne");
        com.linguauniversalis.core.species.SpeciesProfile nekoAb =
                SpeciesRegistry.get("nekomata");
        check(com.linguauniversalis.core.behavior.SpeciesAbilities.webCooldownTicks(arakneAb) == 100L
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.silkCooldownTicks(arakneAb) == 300L
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.poisonTicks(arakneAb) == 160,
                "arakne abilities: web 5s / silk 15s / poison 8s");
        check(com.linguauniversalis.core.behavior.SpeciesAbilities.webCooldownTicks(nekoAb) == 0L
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.silkCooldownTicks(nekoAb) == 0L,
                "nekomata has no web/silk abilities (params 0)");
        check(com.linguauniversalis.core.behavior.SpeciesAbilities.bleedRefreshTicks(nekoAb) == 200L
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltCooldownTicks(nekoAb) == 200L
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltMinRange(nekoAb) == 8.0
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.dodgeCooldownTicks(nekoAb) == 100L,
                "nekomata abilities: bleed 10s / shadow bolt 10s@8+ / dodge 5s");
        // 能力冷却计时
        com.linguauniversalis.core.behavior.SpeciesAbilities.Cooldowns cd =
                new com.linguauniversalis.core.behavior.SpeciesAbilities.Cooldowns();
        check(cd.ready("web", 1000L, 100L), "ability ready before first use");
        cd.use("web", 1000L);
        check(!cd.ready("web", 1050L, 100L) && cd.ready("web", 1100L, 100L),
                "ability cooldown gates until elapsed");
        check(!cd.ready("web", 2000L, 0L), "param 0 means ability absent (never ready)");
        check(cd.remaining("web", 1050L, 100L) == 50L, "ability remaining cooldown");
        // 流血叠加与周期结算（层级 +1 无上限；命中刷新；每间隔结算一次等于层级的伤害）
        com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed bleed =
                new com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed();
        check(bleed.onHit(1000L, 200L) == 1, "bleed level 1 on first hit");
        check(bleed.onHit(1010L, 200L) == 2, "bleed stacks level 2 on re-hit (no cap)");
        check(!bleed.expired(1200L), "bleed refreshed by re-hit (not expired)");
        check(bleed.pollDamage(1001L, 40L) == 2 && bleed.pollDamage(1002L, 40L) == 0,
                "bleed deals level damage once per interval");
        check(bleed.pollDamage(1041L, 40L) == 2, "bleed next interval damage");
        check(bleed.expired(2000L), "bleed expires after refresh window");
        bleed.clear();
        check(bleed.level() == 0 && bleed.expired(2000L), "bleed cleared on target switch");

        // 能力弹道（蛛网/蛛丝/暗影箭）：伤害种类与 id 往返
        check(com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.byId("web")
                        == com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.WEB
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.byId("silk")
                        == com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SILK
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.byId("shadow")
                        == com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SHADOW
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.byId("nope") == null,
                "bolt kinds round-trip by id (web/silk/shadow)");
        check(!com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.WEB.dealsProjectileDamage()
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SILK.dealsProjectileDamage()
                        && !com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SHADOW.dealsProjectileDamage(),
                "magic bolt (shadow) is NOT projectile damage; silk is; web deals none");
        check(com.linguauniversalis.core.behavior.SpeciesAbilities.boltDamage(
                        arakneAb, com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.WEB) == 0f
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.boltDamage(
                        arakneAb, com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SILK) == 10f
                        && com.linguauniversalis.core.behavior.SpeciesAbilities.boltDamage(
                        nekoAb, com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SHADOW) == 10f,
                "bolt damage per kind (web 0 / silk 10 / shadow 10)");
        // 弹道寿命上限（射空/射向天空也必须自然消失，不会无限飞行）
        boolean boltLifeBounded = true;
        for (com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind boltKind
                : com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.values()) {
            int life = com.linguauniversalis.core.behavior.SpeciesAbilities.boltMaxLifeTicks(boltKind);
            if (life <= 0 || life > 200) {
                boltLifeBounded = false;
            }
        }
        check(boltLifeBounded, "every bolt kind has a bounded life (<= 200 ticks) so misses despawn");

        // 巢穴方块·物种变量：有巢/无巢显式声明，且"无巢穴"不被当作方块 id
        check(arakneAb.hasNest() && "cubile_araneae".equals(arakneAb.nestBlockId()),
                "arakne declares nest block cubile_araneae");
        check(!nekoAb.hasNest()
                        && com.linguauniversalis.core.species.SpeciesProfile.NEST_NONE.equals(nekoAb.nestBlockId()),
                "nekomata explicitly declares NO nest (variable = none)");
        com.linguauniversalis.core.species.SpeciesProfile undeclaredNest =
                com.linguauniversalis.core.species.SpeciesProfile.builder("un")
                        .name("未声明巢穴", "un")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.TERRITORIALIS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PELLIS)
                        .build();
        check(!undeclaredNest.hasNest(), "species without nest declaration has no nest logic");
        check(!com.linguauniversalis.core.species.SpeciesProfile.builder("nn")
                        .name("哨兵当巢", "nn")
                        .taxonomy(com.linguauniversalis.core.taxonomy.Taxonomy.Domain.MATERIAL,
                                com.linguauniversalis.core.taxonomy.Taxonomy.Kingdom.CHORDATA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.MagicClassis.EXOSPIRA,
                                com.linguauniversalis.core.taxonomy.Taxonomy.SocialOrdo.TERRITORIALIS,
                                com.linguauniversalis.core.taxonomy.Taxonomy.ElementFamilia.LUX,
                                com.linguauniversalis.core.taxonomy.Taxonomy.FormaSectio.PELLIS)
                        .nestBlock(com.linguauniversalis.core.species.SpeciesProfile.NEST_NONE)
                        .build()
                        .hasNest(),
                "NEST_NONE passed as a block id still means no nest");

        // 伪装形态规则（设计 §12 猫又）：物种变量 + 现形触发 + 静默恢复 + 伙伴档失效
        check(com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(nekoAb)
                        && !com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(arakneAb),
                "disguise is a species variable (nekomata yes / arakne no)");
        check(com.linguauniversalis.core.behavior.DisguiseRules.revertTicks(nekoAb) == 1200L,
                "nekomata disguise revert after 60s of no activity");
        check(com.linguauniversalis.core.behavior.DisguiseRules.reveals(
                        com.linguauniversalis.core.behavior.DisguiseRules.Trigger.PET)
                        && com.linguauniversalis.core.behavior.DisguiseRules.reveals(
                        com.linguauniversalis.core.behavior.DisguiseRules.Trigger.DAMAGED)
                        && com.linguauniversalis.core.behavior.DisguiseRules.reveals(
                        com.linguauniversalis.core.behavior.DisguiseRules.Trigger.ATTACK),
                "empty-hand click / damaged / attacking all reveal the human form");
        check(com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(false)
                        && !com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(true),
                "disguise habit ends at companion stage");
        check(!com.linguauniversalis.core.behavior.DisguiseRules.shouldRevert(1000L, 500L, 1200L)
                        && com.linguauniversalis.core.behavior.DisguiseRules.shouldRevert(1700L, 500L, 1200L)
                        && !com.linguauniversalis.core.behavior.DisguiseRules.shouldRevert(1700L, -1L, 1200L),
                "revert to disguise only after the quiet window elapses");

        // 巢穴结构几何规则（程序化生成的深色橡木巨树 + 蛛丝巢；设计 §11）
        check(com.linguauniversalis.core.worldgen.NestShapeRules.TRUNK_HEIGHT == 15
                        && com.linguauniversalis.core.worldgen.NestShapeRules.TOTAL_HEIGHT == 25,
                "nest structure spec: trunk 15 / total 25");
        check(com.linguauniversalis.core.worldgen.NestShapeRules.trunkRadius(0)
                        > com.linguauniversalis.core.worldgen.NestShapeRules.trunkRadius(14),
                "trunk tapers from bottom to top (wider at base)");
        check(com.linguauniversalis.core.worldgen.NestShapeRules.inTrunk(0.0, 0.0, 0)
                        && !com.linguauniversalis.core.worldgen.NestShapeRules.inTrunk(4.0, 0.0, 0),
                "trunk cross-section membership");
        // 巢壁球壳：中心是空的，半径附近是壁
        check(!com.linguauniversalis.core.worldgen.NestShapeRules.isNestWall(0.0, 0.0, 0.0, 0.5),
                "nest is hollow at the centre");
        check(com.linguauniversalis.core.worldgen.NestShapeRules.isNestWall(4.8, 0.0, 0.0, 0.5)
                        || com.linguauniversalis.core.worldgen.NestShapeRules.isNestWall(4.2, 0.0, 0.0, 0.5),
                "nest wall near the shell radius");
        // 出入口锥：面积占比 20% → cos 阈值 0.6；开口方向内、反方向外
        check(Math.abs(com.linguauniversalis.core.worldgen.NestShapeRules.entranceCosThreshold() - 0.6) < 1.0E-9,
                "entrance cap area fraction 20% (cos threshold 0.6)");
        check(com.linguauniversalis.core.worldgen.NestShapeRules.inEntranceOpening(5.0, 0.0, 0.0, 1.0, 0.0)
                        && !com.linguauniversalis.core.worldgen.NestShapeRules.inEntranceOpening(-5.0, 0.0, 0.0, 1.0, 0.0),
                "entrance opening is on one side only");
        // 巢心在入口反方向、靠近巢壁
        int[] heart = com.linguauniversalis.core.worldgen.NestShapeRules.heartOffset(1.0, 0.0);
        check(heart[0] < 0 && heart[2] == 0 && Math.abs(heart[0]) >= 3,
                "nest heart sits opposite the entrance, near the wall");
        // 蜘蛛网密度：近巢心更密，超出最大距离为 0
        check(com.linguauniversalis.core.worldgen.NestShapeRules.cobwebDensity(0.5)
                        > com.linguauniversalis.core.worldgen.NestShapeRules.cobwebDensity(6.0)
                        && com.linguauniversalis.core.worldgen.NestShapeRules.cobwebDensity(9.0) == 0.0,
                "cobweb density falls off from the nest heart (zero beyond max)");
        // 通路：巢心→出入口 连线在通路内，偏离则不在
        check(com.linguauniversalis.core.worldgen.NestShapeRules.inHeartCorridor(0.0, 16.0, 0.0,
                        4.0, 16.0, 0.0, -5.0, 16.0, 0.0)
                        && !com.linguauniversalis.core.worldgen.NestShapeRules.inHeartCorridor(0.0, 16.0, 3.0,
                        4.0, 16.0, 0.0, -5.0, 16.0, 0.0),
                "clear corridor from nest heart to entrance (heart stays clickable)");
        // 螺旋楼梯：凹陷位于螺旋角附近（外表面一层），反方向不在凹陷内
        int grooveY = 10;
        double grooveAngle = com.linguauniversalis.core.worldgen.NestShapeRules.spiralAngle(grooveY);
        double grooveRadius = com.linguauniversalis.core.worldgen.NestShapeRules.trunkRadius(grooveY) * 0.95;
        double onX = Math.cos(grooveAngle) * grooveRadius;
        double onZ = Math.sin(grooveAngle) * grooveRadius;
        double offX = Math.cos(grooveAngle + Math.PI) * grooveRadius;
        double offZ = Math.sin(grooveAngle + Math.PI) * grooveRadius;
        check(com.linguauniversalis.core.worldgen.NestShapeRules.spiralAngle(11)
                        > com.linguauniversalis.core.worldgen.NestShapeRules.spiralAngle(10),
                "spiral staircase advances with height");
        check(com.linguauniversalis.core.worldgen.NestShapeRules.onSpiralGroove(onX, onZ, grooveY)
                        && !com.linguauniversalis.core.worldgen.NestShapeRules.onSpiralGroove(offX, offZ, grooveY),
                "spiral groove is carved only along the helix on the trunk surface");
        // 树冠避开巢穴体积
        check(com.linguauniversalis.core.worldgen.NestShapeRules.skipLeafAt(0.0, 0.0, 0.0)
                        && !com.linguauniversalis.core.worldgen.NestShapeRules.skipLeafAt(0.0, 0.0, 12.0),
                "canopy leaves are skipped inside the nest volume but placed outside");

        // 猫又社交习性（设计 §12）：偷鱼 / 村民绿宝石 / 睡醒赠礼 / 陪睡 / 亡灵视野
        check(com.linguauniversalis.core.behavior.SocialHabitRules.stealsFish(nekoAb)
                        && com.linguauniversalis.core.behavior.SocialHabitRules.stealsEmeralds(nekoAb)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.stealsFish(arakneAb),
                "fish/emerald theft are species variables (nekomata yes / arakne no)");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.fishStealIntervalTicks(nekoAb) == 6000L,
                "fish theft interval is 5 minutes");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(9)
                        && com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(35)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(0)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(8)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(36)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(40),
                "only backpack slots 9-35 are stealable (hotbar/hands/armor excluded)");
        check(!com.linguauniversalis.core.behavior.SocialHabitRules.canStealFish(
                        false, true, 10_000L, -1L, 6000L)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.canStealFish(
                        true, false, 10_000L, -1L, 6000L)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.canStealFish(
                        true, true, 10_000L, 5_000L, 6000L)
                        && com.linguauniversalis.core.behavior.SocialHabitRules.canStealFish(
                        true, true, 11_000L, 5_000L, 6000L),
                "fish theft needs wander mode + nearby player + elapsed interval");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.villageStealHappens(0.49)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.villageStealHappens(0.51),
                "village emerald theft is 50% per day");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.emeraldAmount(0.0) == 1
                        && com.linguauniversalis.core.behavior.SocialHabitRules.emeraldAmount(0.5) == 2
                        && com.linguauniversalis.core.behavior.SocialHabitRules.emeraldAmount(0.999) == 3,
                "village theft yields 1-3 emeralds");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMood(nekoAb),
                "umbra familia grants the undead-sight mood habit by default");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMoodTrigger(true, 10L, 9L)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMoodTrigger(true, 10L, 10L)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMoodTrigger(false, 11L, 9L),
                "undead-sight mood once per day and only when actually seen");
        com.linguauniversalis.core.loot.WeightedGiftTable nekoGifts =
                com.linguauniversalis.core.loot.WeightedGiftTable.nekomataGiftTable();
        check(com.linguauniversalis.core.loot.WeightedGiftTable.EMERALD.equals(
                        com.linguauniversalis.core.behavior.SocialHabitRules.pickGift(
                                true, nekoGifts, new java.util.Random(1L))),
                "armed emerald forces the emerald gift");
        java.util.Set<String> rolled = new java.util.HashSet<>();
        java.util.Random giftRandom = new java.util.Random(7L);
        for (int i = 0; i < 64; i++) {
            rolled.add(com.linguauniversalis.core.behavior.SocialHabitRules.pickGift(
                    false, nekoGifts, giftRandom));
        }
        check(rolled.size() >= 3 && nekoGifts.entries().size() == 4,
                "gift table has 4 entries and rolls across them");
        check(com.linguauniversalis.core.behavior.SocialHabitRules.shouldSleepAsCat(
                        true, false, true, 4.0, 8.0)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.shouldSleepAsCat(
                        false, false, true, 4.0, 8.0)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.shouldSleepAsCat(
                        true, true, true, 4.0, 8.0)
                        && !com.linguauniversalis.core.behavior.SocialHabitRules.shouldSleepAsCat(
                        true, false, true, 100.0, 8.0),
                "cat-form co-sleep requires companion + not standing by + sleeping owner nearby");

        // 动画规则（通用约定：main 互斥+过渡 / 混合项 / 随机每秒 3% 同系列互斥 / 空中优先于走跑 / 跳跃→空中）
        check(com.linguauniversalis.core.anim.AnimationRules.MAIN_TRANSITION_TICKS > 0,
                "main animations transition instead of hard-cutting");
        check(com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, false, false, false, false)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_IDLE)
                        && com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, false, false, false, true)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_WALK),
                "main: idle when still / walk when moving");
        check(com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, true, false, false, true)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_AIRBORNE)
                        && com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, true, true, false, true)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_AIRBORNE)
                        && com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, true, false, true, true)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_AIRBORNE),
                "airborne outranks walk / eat / sit while falling");
        check(com.linguauniversalis.core.anim.AnimationRules.mainAnimation(true, true, false, false, true)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_JUMP)
                        && com.linguauniversalis.core.anim.AnimationRules.mainAnimation(true, false, false, false, false)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_JUMP),
                "jump animation wins while it is playing");
        check(com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, false, true, false, false)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_EAT)
                        && com.linguauniversalis.core.anim.AnimationRules.mainAnimation(false, false, false, true, false)
                        .equals(com.linguauniversalis.core.anim.AnimationRules.MAIN_SIT),
                "eat / sit main states");
        check(!com.linguauniversalis.core.anim.AnimationRules.isAirborne(true, false, false)
                        && !com.linguauniversalis.core.anim.AnimationRules.isAirborne(false, true, false)
                        && !com.linguauniversalis.core.anim.AnimationRules.isAirborne(false, false, true)
                        && com.linguauniversalis.core.anim.AnimationRules.isAirborne(false, false, false),
                "airborne excludes ground / water / flight");
        check("blink".equals(com.linguauniversalis.core.anim.AnimationRules.randomSeriesOf("random_blink1"))
                        && "blink".equals(com.linguauniversalis.core.anim.AnimationRules.randomSeriesOf("random_blink"))
                        && "idle".equals(com.linguauniversalis.core.anim.AnimationRules.randomSeriesOf("random_idle2")),
                "random series groups by prefix ignoring trailing digits");
        // 随机系列：每秒 3% 投掷一次；命中后同系列播完前不再触发；不同系列可并行
        com.linguauniversalis.core.anim.AnimationRules.RandomSeries series =
                new com.linguauniversalis.core.anim.AnimationRules.RandomSeries("random_blink", 8);
        check(!series.poll(0L, 0.5)
                        && !series.poll(10L, 0.01)
                        && series.poll(20L, 0.01)
                        && series.isPlaying(20L)
                        && series.poll(24L, 0.99)
                        && !series.poll(29L, 0.01)
                        && series.poll(40L, 0.01),
                "random series: 3%/sec roll, blocked while playing, re-rolls after");
        com.linguauniversalis.core.anim.AnimationRules.RandomSeries otherSeries =
                new com.linguauniversalis.core.anim.AnimationRules.RandomSeries("random_idle1", 5);
        check(otherSeries.poll(20L, 0.01) && series.isPlaying(20L) && otherSeries.isPlaying(20L),
                "different random series may play simultaneously");

        // 未注册类目/未设置键回落 fallback
        check(chordPellis.featureNumber(com.linguauniversalis.core.behavior.TemplateKeys.ORDO_TERRITORY_HORIZONTAL, 9.0) == 9.0,
                "unset feature falls back to given default (nomadicus has no territory)");

        // ---------------------------------------------------------------- 自然生成（设计 §12）
        com.linguauniversalis.core.species.SpeciesProfile nekoForSpawn = SpeciesRegistry.get("nekomata");
        com.linguauniversalis.core.species.SpeciesProfile araForSpawn = SpeciesRegistry.get("arakne");
        check(com.linguauniversalis.core.behavior.SpawnRules
                        .villageCatConversionChance(nekoForSpawn) == 0.5,
                "nekomata declares 50% village-cat conversion");
        check(!com.linguauniversalis.core.behavior.SpawnRules.convertsFromCats(araForSpawn),
                "arakne does not participate in cat conversion (unset variable = 0)");
        check("minecraft:cats_spawn_in".equals(
                        com.linguauniversalis.core.behavior.SpawnRules.CAT_SPAWN_STRUCTURE_TAG),
                "cat conversion keys off the vanilla cats_spawn_in structure tag");
        check(com.linguauniversalis.core.behavior.SpawnRules
                        .convertsFromCat(nekoForSpawn, true, true, 0.49)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .convertsFromCat(nekoForSpawn, true, true, 0.5)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .convertsFromCat(nekoForSpawn, false, true, 0.0)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .convertsFromCat(nekoForSpawn, true, false, 0.0),
                "conversion needs natural spawn + cat-spawn structure + roll under chance");
        check(com.linguauniversalis.core.behavior.SpawnRules
                        .despawnsNaturally(false, false)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .despawnsNaturally(true, false)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .despawnsNaturally(false, true),
                "wild girl despawns only before any affection and while unbound");
        check(com.linguauniversalis.core.behavior.SpawnRules
                        .shouldBecomePersistent(true, false, false)
                        && com.linguauniversalis.core.behavior.SpawnRules
                        .shouldBecomePersistent(false, true, false)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .shouldBecomePersistent(true, false, true)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .shouldBecomePersistent(false, false, false),
                "persistence flips on after first affection / bonding, idempotently");
        check(!com.linguauniversalis.core.behavior.SpawnRules.everAffectionedNow(0, false)
                        && com.linguauniversalis.core.behavior.SpawnRules.everAffectionedNow(1, false)
                        && com.linguauniversalis.core.behavior.SpawnRules.everAffectionedNow(0, true),
                "ever-affectioned flag is sticky once set");
        check(com.linguauniversalis.core.behavior.SpawnRules
                        .isNaturalWorldSpawnFlag(true, false)
                        && com.linguauniversalis.core.behavior.SpawnRules
                        .isNaturalWorldSpawnFlag(false, true)
                        && !com.linguauniversalis.core.behavior.SpawnRules
                        .isNaturalWorldSpawnFlag(false, false),
                "only world/natural spawn reasons count as natural spawning");

        // ---------------------------------------------------------------- 猫形伪装表现（设计 §12）
        check(com.linguauniversalis.core.anim.CatFormRules.useSittingPose(false, true)
                        && !com.linguauniversalis.core.anim.CatFormRules.useSittingPose(true, true)
                        && !com.linguauniversalis.core.anim.CatFormRules.useSittingPose(false, false)
                        && !com.linguauniversalis.core.anim.CatFormRules.useSittingPose(true, false),
                "cat form sits only when standing by (never while dormant)");
        check(com.linguauniversalis.core.anim.CatFormRules.useLiePose(true, false)
                        && com.linguauniversalis.core.anim.CatFormRules.useLiePose(false, true)
                        && com.linguauniversalis.core.anim.CatFormRules.useLiePose(true, true)
                        && !com.linguauniversalis.core.anim.CatFormRules.useLiePose(false, false),
                "cat form lies down when dormant or sleeping with owner");
        check(com.linguauniversalis.core.anim.CatFormRules.LIE_RISE == 0.15F
                        && com.linguauniversalis.core.anim.CatFormRules.LIE_FALL == 0.22F
                        && com.linguauniversalis.core.anim.CatFormRules.LIE_TAIL_RISE == 0.08F
                        && com.linguauniversalis.core.anim.CatFormRules.LIE_TAIL_FALL == 0.13F
                        && com.linguauniversalis.core.anim.CatFormRules.RELAX_RISE == 0.10F
                        && com.linguauniversalis.core.anim.CatFormRules.RELAX_FALL == 0.13F,
                "cat pose transition steps match vanilla Cat constants");
        check(com.linguauniversalis.core.anim.CatFormRules.stepLieAmount(0.0F, true) == 0.15F
                        && com.linguauniversalis.core.anim.CatFormRules.stepLieAmount(0.95F, true) == 1.0F
                        && com.linguauniversalis.core.anim.CatFormRules.stepLieAmount(0.1F, false) == 0.0F
                        && com.linguauniversalis.core.anim.CatFormRules.stepLieTailAmount(0.5F, true) == 0.58F
                        && com.linguauniversalis.core.anim.CatFormRules.stepRelaxAmount(0.5F, false) == 0.37F,
                "cat pose amounts ease toward the target and clamp to [0,1]");
        check("minecraft:textures/entity/cat/cat_all_black.png".equals(
                        com.linguauniversalis.core.anim.CatFormRules.CAT_FORM_TEXTURE),
                "cat form uses the vanilla all-black cat texture");

        // ---------------------------------------------------------------- 动画名 ↔ 资源文件对表
        // 防"改了动画名/资源改名后静默失效"：逐一确认 AnimationRules 里请求的名字都存在于动画资源里。
        java.nio.file.Path animFile = java.nio.file.Path.of(
                "src/main/resources/assets/lingua_universalis/geckolib/animations/nekomata.animation.json");
        String animJson = null;
        try {
            if (java.nio.file.Files.isRegularFile(animFile)) {
                animJson = new String(java.nio.file.Files.readAllBytes(animFile),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (java.io.IOException readFailed) {
            animJson = null;
        }
        if (animJson != null) {
            StringBuilder missing = new StringBuilder();
            for (String name : com.linguauniversalis.core.anim.AnimationRules.REQUIRED_ANIMATIONS) {
                if (!animJson.contains("\"" + name + "\"")) {
                    missing.append(' ').append(name);
                }
            }
            check(missing.length() == 0,
                    "every requested animation exists in nekomata.animation.json"
                            + (missing.length() == 0 ? "" : " (missing:" + missing + " )"));
        } else {
            System.out.println("skip: animation asset not found (run smoke from repo root to enable the name check)");
        }

        // ---------------------------------------------------------------- 头部跟随视角（纯规则）
        check(com.linguauniversalis.core.anim.HeadLookRules.wrapDegrees(190.0) == -170.0
                        && com.linguauniversalis.core.anim.HeadLookRules.wrapDegrees(-190.0) == 170.0
                        && com.linguauniversalis.core.anim.HeadLookRules.wrapDegrees(30.0) == 30.0,
                "angle wrapping takes the shortest arc");
        check(com.linguauniversalis.core.anim.HeadLookRules.lerpDegrees(350.0, 10.0, 0.5) == 360.0
                        && com.linguauniversalis.core.anim.HeadLookRules.lerpDegrees(10.0, 20.0, 0.25) == 12.5,
                "partial-tick angle interpolation crosses ±180 without twitching");
        check(com.linguauniversalis.core.anim.HeadLookRules.netHeadYawDeg(10.0, 20.0) == -10.0
                        && com.linguauniversalis.core.anim.HeadLookRules.netHeadYawDeg(100.0, 0.0) == 75.0
                        && com.linguauniversalis.core.anim.HeadLookRules.netHeadYawDeg(0.0, 90.0) == -75.0,
                "net head yaw = head - body, clamped to ±75°");
        check(com.linguauniversalis.core.anim.HeadLookRules.headPitchDeg(30.0) == 30.0
                        && com.linguauniversalis.core.anim.HeadLookRules.headPitchDeg(100.0) == 60.0
                        && com.linguauniversalis.core.anim.HeadLookRules.headPitchDeg(-100.0) == -60.0,
                "head pitch clamped to ±60°");
        check(Math.abs(com.linguauniversalis.core.anim.HeadLookRules.toRadians(180.0) - (float) Math.PI) < 1.0e-6,
                "degrees convert to radians for bone rotation");

        // ---------------------------------------------------------------- 模型骨骼名对表
        java.nio.file.Path geoFile = java.nio.file.Path.of(
                "src/main/resources/assets/lingua_universalis/geckolib/models/nekomata.geo.json");
        String geoJson = null;
        try {
            if (java.nio.file.Files.isRegularFile(geoFile)) {
                geoJson = new String(java.nio.file.Files.readAllBytes(geoFile),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (java.io.IOException readFailed) {
            geoJson = null;
        }
        if (geoJson != null) {
            check(geoJson.contains("\"AllHead\""),
                    "model has the AllHead bone that the look-follow rotation drives");
        } else {
            System.out.println("skip: model asset not found (run smoke from repo root to enable the bone check)");
        }

        // ---------------------------------------------------------------- 体型（碰撞箱/视线高度）
        com.linguauniversalis.core.species.BodyRules.Size nekoSize =
                com.linguauniversalis.core.species.BodyRules.of(nekoForSpawn);
        check(Math.abs(nekoSize.width() - 0.6f) < 1.0e-6 && Math.abs(nekoSize.height() - 1.5f) < 1.0e-6,
                "nekomata hitbox is 0.6 x 1.5 (crawling pose)");
        check(Math.abs(nekoSize.eyeHeight() - 2.1548f) < 1.0e-4,
                "nekomata eye height follows the ViewLocator pivot (34.47642/16)");
        com.linguauniversalis.core.species.BodyRules.Size scaledSize =
                com.linguauniversalis.core.species.BodyRules.of(nekoForSpawn, 0.5);
        check(Math.abs(scaledSize.eyeHeight() - 1.0774f) < 1.0e-4
                        && Math.abs(scaledSize.height() - 1.5f) < 1.0e-6,
                "render scale moves the eye with the model but not the hitbox");
        com.linguauniversalis.core.species.BodyRules.Size araSize =
                com.linguauniversalis.core.species.BodyRules.of(araForSpawn);
        check(Math.abs(araSize.width() - 0.6f) < 1.0e-6 && Math.abs(araSize.height() - 1.8f) < 1.0e-6,
                "species without body variables fall back to the vanilla humanoid box");

        // ---------------------------------------------------------------- 本地可调设置
        com.linguauniversalis.core.config.LuSettings settings =
                com.linguauniversalis.core.config.LuSettings.get();
        settings.useDefaults();
        check(settings.headLookEnabled()
                        && settings.headLookYawSign() == -1.0
                        && settings.headLookPitchSign() == -1.0
                        && "z".equals(settings.headLookYawAxis())
                        && "x".equals(settings.headLookPitchAxis())
                        && settings.headLookMaxYawDeg() == 75.0
                        && settings.mainTransitionTicks() == 8
                        && settings.freezePawTransition()
                        && settings.jumpTransitionTicks() == 0
                        && com.linguauniversalis.core.anim.AnimationRules.JUMP_ANIM_TICKS >= 10
                        && settings.headLookFollowPitch()
                        && settings.lookAtNearbyPlayer()
                        && settings.modelRenderScale() == 1.0,
                "settings defaults are sane");
        java.nio.file.Path settingsFile = java.nio.file.Path.of("build/smoke-settings.properties");
        try {
            java.nio.file.Files.createDirectories(settingsFile.getParent());
            java.nio.file.Files.write(settingsFile, java.util.List.of(
                    "headLook.yawSign=1",
                    "headLook.maxYawDeg=not-a-number",
                    "animation.mainTransitionTicks=-3",
                    "animation.speed.main_walk=3.5",
                    "nekomata.animation.speed.main_walk=4",
                    "nekomata.headLook.yawAxis=y",
                    "nekomata.body.width=0.9",
                    "speed.walkScale=1.2",
                    "nekomata.speed.fastScale=1.8",
                    "model.renderScale=0"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            // 写不出来就跳过这项检查
        }
        com.linguauniversalis.core.config.LuSettings parsed =
                com.linguauniversalis.core.config.LuSettings.get();
        boolean settingsFileReady = java.nio.file.Files.isRegularFile(settingsFile);
        if (settingsFileReady) {
            // 独立读一份（不影响单例），验证解析/兜底行为
            parsed = com.linguauniversalis.core.config.LuSettings.read(settingsFile);
            check(parsed != null && parsed.headLookYawSign() == 1.0
                            && parsed.headLookMaxYawDeg() == 75.0
                            && parsed.mainTransitionTicks() == 0
                            && parsed.modelRenderScale() == 1.0,
                    "settings parse values, clamp bad input and keep defaults");
        } else {
            System.out.println("skip: could not write the settings fixture");
        }
        // 移速倍率可热改：物种覆盖 > 全局；没写时返回 -1（= 用物种档案里的值）
        if (settingsFileReady) {
            check(parsed.speedFastScale("nekomata") == 1.8
                            && parsed.speedWalkScale("nekomata") == 1.2
                            && parsed.speedWalkScale("arakne") == 1.2
                            && parsed.speedFastScale("arakne") == -1.0,
                    "speed scale overrides: species beats global, unset falls back to the profile");
        }

        // ---------------------------------------------------------------- 跳跃/悬空判定
        check(com.linguauniversalis.core.anim.AnimationRules.isActiveJump(false, false, false, 0.42, false)
                        && com.linguauniversalis.core.anim.AnimationRules.isActiveJump(false, false, false, 0.0, true)
                        && !com.linguauniversalis.core.anim.AnimationRules.isActiveJump(false, false, false, -0.4, false)
                        && !com.linguauniversalis.core.anim.AnimationRules.isActiveJump(true, false, false, 0.5, true)
                        && !com.linguauniversalis.core.anim.AnimationRules.isActiveJump(false, true, false, 0.5, true),
                "jump animation triggers on upward motion (not only on AI jump requests)");
        check(!com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(true, 0.0, 0)
                        && !com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(true, 0.0, 3)
                        && com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(true, 0.0, 4)
                        && com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(true, 0.9, 1)
                        && !com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(false, 5.0, 99),
                "airborne needs to settle a few ticks (no airborne reset right after joining)");

        // ---------------------------------------------------------------- 走路动画倍速 && 移速倍率
        check(parsed != null && parsed.animationSpeed("main_walk") == 3.5
                        && parsed.animationSpeed("main_idle") == 1.0,
                "per-animation speed override wins, others stay 1x");
        check(settings.walkAnimationSpeed() == 1.0 && settings.animationSpeed("main_jump") == 1.0,
                "global animation speeds all default to 1.0");
        // 生成的默认配置必须给"动画资源里每一个动画"都写出可调键（防止新增动画后忘了加键）
        if (animJson != null) {
            java.nio.file.Path generated = java.nio.file.Path.of("build/smoke-generated.properties");
            try {
                java.nio.file.Files.deleteIfExists(generated);
            } catch (java.io.IOException ignored) {
                // 忽略
            }
            com.linguauniversalis.core.config.LuSettings.read(generated);
            String generatedText = null;
            try {
                if (java.nio.file.Files.isRegularFile(generated)) {
                    generatedText = new String(java.nio.file.Files.readAllBytes(generated),
                            java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (java.io.IOException ignored) {
                generatedText = null;
            }
            if (generatedText != null) {
                StringBuilder missingKeys = new StringBuilder();
                for (String name : com.linguauniversalis.core.config.LuSettings.TUNING_ANIMATION_NAMES) {
                    if (!generatedText.contains("animation.speed." + name + "=")) {
                        missingKeys.append(' ').append(name);
                    }
                }
                check(missingKeys.length() == 0 && generatedText.contains("animation.walkSpeed=") == false,
                        "generated settings list every animation speed key"
                                + (missingKeys.length() == 0 ? "" : " (missing:" + missingKeys + " )"));
                // 物种段要写出移速倍率现值（方便直接去掉行首 # 微调）
                check(generatedText.contains("nekomata.speed.walkScale=1")
                                && generatedText.contains("nekomata.speed.fastScale=1.7"),
                        "generated settings show per-species speed scales");
            } else {
                System.out.println("skip: could not generate the settings fixture");
            }
        }

        check(Math.abs(nekoForSpawn.templateParam(
                        com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_MOVE_SPEED_SCALE, 1.0) - 1.0) < 1.0e-6
                        && Math.abs(araForSpawn.templateParam(
                        com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_MOVE_SPEED_SCALE, 1.0) - 1.0) < 1.0e-6,
                "species move-speed scale defaults to 1.0 (nekomata reverted to base speed)");
        check(Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.fastSpeedScale(nekoForSpawn) - 1.7) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.walkSpeedScale(nekoForSpawn) - 1.0) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.fastSpeedScale(araForSpawn) - 1.0) < 1.0e-6,
                "nekomata fastest = 1.7x walk (about 1.3x player sprint); other species stay 1.0");
        check(com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 64.1)
                        && !com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 64.0)
                        && com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(true, false, 0.0)
                        && !com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, false, 999.0),
                "two-speed rule: chase beyond 8 blocks or attacking uses the fastest speed");
        // 滞回：已在最快档时，一路追到"跟随最低距离"（6 格）才降回 walk
        // （8 格加速 → 6 格减速，中间是滞回带，避免玩家在 8 格上下走动导致反复切档）
        check(com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 64.1, false)
                        && !com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 64.0, false)
                        && com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 63.9, true)
                        && com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 36.1, true)
                        && !com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(false, true, 36.0, true)
                        && com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(true, false, 1.0, true),
                "chase keeps the fastest speed all the way in to the 6-block follow distance");
        check(Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.FOLLOW_STOP_DISTANCE - 6.0) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.FOLLOW_STOP_DIST_SQ
                                - 36.0) < 1.0e-6
                        && com.linguauniversalis.core.behavior.MovementSpeedRules.FOLLOW_FAST_DISTANCE
                                > com.linguauniversalis.core.behavior.MovementSpeedRules.FOLLOW_STOP_DISTANCE,
                "follow stop distance is 6 blocks (fast 8 -> walk 6, shared with the stop check)");
        // 档位倍率选择：配置值 > 0 优先，否则回落物种档案值
        check(Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                        true, -1.0, -1.0, nekoForSpawn) - 1.7) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                        false, -1.0, -1.0, nekoForSpawn) - 1.0) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                        true, 0.0, 1.9, nekoForSpawn) - 1.9) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                        true, 0.0, -1.0, nekoForSpawn) - 1.7) < 1.0e-6
                        && Math.abs(com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                        true, 0.0, 1.9, araForSpawn) - 1.9) < 1.0e-6,
                "configured speed scale wins, 0/negative falls back to the species profile value");

        // ---------------------------------------------------------------- 物种覆盖层（per-species tuning）
        check(parsed != null
                        && parsed.animationSpeed("nekomata", "main_walk") == 4.0
                        && parsed.animationSpeed(null, "main_walk") == 3.5
                        && parsed.headLookYawAxis("nekomata").equals("y")
                        && parsed.headLookYawAxis("arakne").equals("z")
                        && parsed.bodyWidth("nekomata") == 0.9
                        && parsed.bodyWidth("arakne") <= 0,
                "per-species keys override globals; other species keep globals/defaults");
        com.linguauniversalis.core.species.BodyRules.Size overridden =
                com.linguauniversalis.core.species.BodyRules.of(nekoForSpawn, 1.0, 0.9, 1.2, 1.1);
        check(Math.abs(overridden.width() - 0.9f) < 1.0e-6
                        && Math.abs(overridden.height() - 1.2f) < 1.0e-6
                        && Math.abs(overridden.eyeHeight() - 1.1f) < 1.0e-6,
                "config body overrides replace the profile values");

        // ---------------------------------------------------------------- 快捷栏 / 背包（设计 §6）
        com.linguauniversalis.core.species.SpeciesProfile nekoInv = SpeciesRegistry.get("nekomata");
        com.linguauniversalis.core.species.SpeciesProfile araInv = SpeciesRegistry.get("arakne");
        check(com.linguauniversalis.core.behavior.InventoryRules.hotbarSlots(nekoInv) == 2
                        && com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(nekoInv) == 8
                        && com.linguauniversalis.core.behavior.InventoryRules.totalSlots(nekoInv) == 10
                        && com.linguauniversalis.core.behavior.InventoryRules.hotbarSlots(araInv) == 4
                        && com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(araInv) == 9
                        && com.linguauniversalis.core.behavior.InventoryRules.totalSlots(araInv) == 13,
                "cat girl = 2 hotbar + 8 pack, arakne = 4 hotbar (main/off/appendage1-2) + 9 pack");
        check(com.linguauniversalis.core.behavior.InventoryRules.isHotbarSlot(nekoInv, 0)
                        && com.linguauniversalis.core.behavior.InventoryRules.isHotbarSlot(nekoInv, 1)
                        && !com.linguauniversalis.core.behavior.InventoryRules.isHotbarSlot(nekoInv, 2)
                        && com.linguauniversalis.core.behavior.InventoryRules.isBackpackSlot(nekoInv, 2)
                        && com.linguauniversalis.core.behavior.InventoryRules.isBackpackSlot(nekoInv, 9)
                        && !com.linguauniversalis.core.behavior.InventoryRules.isBackpackSlot(nekoInv, 10)
                        && !com.linguauniversalis.core.behavior.InventoryRules.isBackpackSlot(nekoInv, -1),
                "slot indexes split into hotbar then backpack, out-of-range reads are false");
        check("main_hand".equals(com.linguauniversalis.core.behavior.InventoryRules
                        .slotName(nekoInv, com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(nekoInv)))
                        && "off_hand".equals(com.linguauniversalis.core.behavior.InventoryRules
                        .slotName(nekoInv, com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(nekoInv)))
                        && "backpack".equals(com.linguauniversalis.core.behavior.InventoryRules.slotName(nekoInv, 5))
                        && com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(nekoInv) == 0
                        && com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(nekoInv) == 1
                        && "appendage2".equals(com.linguauniversalis.core.behavior.InventoryRules
                        .slotName(araInv, com.linguauniversalis.core.behavior.InventoryRules
                                .indexOfHotbarSlot(araInv, "appendage2"))),
                "hotbar slots keep their profile names (main/off hand, appendages)");
        // 档案写错也不能把 GUI 撑爆：背包格数夹在 0..27（借用猫又的完整阶元满足档案校验）
        com.linguauniversalis.core.species.SpeciesProfile hugeBackpack =
                com.linguauniversalis.core.species.SpeciesProfile.builder("smoke_huge")
                        .name("测试", "Testus")
                        .taxonomy(nekoInv.domain(), nekoInv.kingdom(), nekoInv.magicClassis(),
                                nekoInv.socialOrdo(), nekoInv.familia(), nekoInv.sectio())
                        .backpack(99).build();
        com.linguauniversalis.core.species.SpeciesProfile noBackpack =
                com.linguauniversalis.core.species.SpeciesProfile.builder("smoke_none")
                        .name("测试", "Testus")
                        .taxonomy(nekoInv.domain(), nekoInv.kingdom(), nekoInv.magicClassis(),
                                nekoInv.socialOrdo(), nekoInv.familia(), nekoInv.sectio())
                        .backpack(0).build();
        check(com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(hugeBackpack)
                        == com.linguauniversalis.core.behavior.InventoryRules.MAX_BACKPACK_SLOTS
                        && com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(noBackpack) == 0
                        && com.linguauniversalis.core.behavior.InventoryRules.totalSlots(noBackpack) == 0,
                "backpack size is clamped to 0..27");

        // ---------------------------------------------------------------- GUI 布局（漏斗占位面板）
        check(com.linguauniversalis.core.gui.GirlInventoryLayout.backpackRows(0) == 0
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackRows(8) == 1
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackRows(9) == 1
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackRows(10) == 2
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackRows(27) == 3,
                "pack rows: 8/9 slots = one row, 27 slots = three rows");
        check(com.linguauniversalis.core.gui.GirlInventoryLayout.hotbarSlotX(2, 0) == 70
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.hotbarSlotX(2, 1) == 88
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.hotbarSlotX(4, 0) == 52
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.hotbarSlotX(4, 3) == 106,
                "hotbar row is centred (2 slots = main/off hand, 4 = arakne)");
        check(com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotX(8, 0) == 16
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotX(9, 0) == 7
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotX(10, 9) == 79
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotX(10, 1) == 25
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotY(0)
                                == com.linguauniversalis.core.gui.GirlInventoryLayout.BACKPACK_Y
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.backpackSlotY(9)
                                == com.linguauniversalis.core.gui.GirlInventoryLayout.BACKPACK_Y + 18,
                "pack rows are centred per row and stack downward");
        check(com.linguauniversalis.core.gui.GirlInventoryLayout.imageHeight(8) == 192
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.imageHeight(9) == 192
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.imageHeight(27) == 228
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.panelMiddleHeight(
                                com.linguauniversalis.core.gui.GirlInventoryLayout.imageHeight(8)) == 167
                        && com.linguauniversalis.core.gui.GirlInventoryLayout.playerInventoryY(8)
                                == com.linguauniversalis.core.gui.GirlInventoryLayout.playerInventoryLabelY(8) + 12,
                "panel height follows the pack rows (top 18 + stretch + bottom 7 pieces)");
        // 槽位不重叠、且都在面板内（她的槽位 + 玩家 36 格）
        boolean uniqueAndInside = true;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < com.linguauniversalis.core.behavior.InventoryRules.totalSlots(nekoInv); i++) {
            int sx = com.linguauniversalis.core.gui.GirlInventoryLayout.girlSlotX(nekoInv, i);
            int sy = com.linguauniversalis.core.gui.GirlInventoryLayout.girlSlotY(nekoInv, i);
            uniqueAndInside &= seen.add(sx + "," + sy)
                    && sx >= 1 && sx + 18 <= 175 && sy >= 1
                    && sy + 18 <= com.linguauniversalis.core.gui.GirlInventoryLayout.imageHeight(
                            com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(nekoInv)) - 7;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = 8 + col * 18;
                int sy = com.linguauniversalis.core.gui.GirlInventoryLayout.playerInventoryY(8) + row * 18;
                uniqueAndInside &= seen.add(sx + "," + sy) && sx + 18 <= 175;
            }
        }
        for (int col = 0; col < 9; col++) {
            uniqueAndInside &= seen.add((8 + col * 18) + ","
                    + (com.linguauniversalis.core.gui.GirlInventoryLayout.playerInventoryY(8) + 58));
        }
        check(uniqueAndInside, "every slot sits inside the panel and no two slots overlap");

        // ---------------------------------------------------------------- 物品栏行为（拾取/进食/武器/伙伴宝箱）
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldPickUp(false, 50, true)
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldPickUp(true, 3, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldPickUp(true, 50, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldPickUp(false, 3, false),
                "pickup: not hungry = no food, full inventory = nothing");
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldLiftToBackpack(0L, 100L, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldLiftToBackpack(0L, 99L, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldLiftToBackpack(0L, 100L, false)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldLiftToBackpack(-1L, 999L, true),
                "picked-up item is lifted into the backpack after 5s (only with room)");
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.isHungry(11)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.isHungry(12)
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEW_TICKS == 32,
                "she eats only below the satiety threshold, chewing like vanilla (32t)");
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWeaponUpgrade(7.0, 5.0)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWeaponUpgrade(4.0, 5.0)
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWornOut(90, 100)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWornOut(89, 100)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWornOut(0, 0),
                "weapon: only better panel damage is equipped, and it is put away at <=10% durability");
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldDepositToChest(true, false)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldDepositToChest(true, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldDepositToChest(false, false),
                "companions store into a chest only when the backpack is full");
        // 关键：决策与执行必须同一套过滤 —— "背包满了但里面全是喜爱食物"时不该往箱子跑
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldVisitChest(true, false)
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldVisitChest(false, true)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldVisitChest(false, false)
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEST_IDLE_COOLDOWN_TICKS > 0,
                "only walk to the chest when there is something to take OR something depositable");
        // 存取必须走到箱子 2 格内（搜索半径仍是 8 格）
        check(com.linguauniversalis.core.behavior.InventoryBehaviorRules.withinChestReach(4.0)
                        && !com.linguauniversalis.core.behavior.InventoryBehaviorRules.withinChestReach(4.01)
                        && Math.abs(com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEST_USE_RADIUS_BLOCKS
                                - 2.0) < 1.0e-9
                        && Math.abs(com.linguauniversalis.core.behavior.InventoryBehaviorRules
                                .CHEST_SEARCH_RADIUS_BLOCKS - 8.0) < 1.0e-9
                        && com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEST_SEARCH_RADIUS_BLOCKS
                                > com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEST_USE_RADIUS_BLOCKS,
                "chest: search within 8 blocks but only deposit/take within 2 blocks");
        check(com.linguauniversalis.core.behavior.InventoryRules.backpackSlots(nekoInv) == 8
                        && com.linguauniversalis.core.behavior.InventoryRules.hotbarSlots(nekoInv) == 2,
                "cat girl keeps 2 hotbar slots + 8 backpack slots for these behaviors");

        System.out.println(fails == 0 ? "ALL_PASS" : ("FAILURES=" + fails));
        if (fails > 0) {
            System.exit(1);
        }
    }
}
