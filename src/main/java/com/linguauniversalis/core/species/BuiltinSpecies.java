package com.linguauniversalis.core.species;

import com.linguauniversalis.core.species.SpeciesProfile.SpawnType;
import com.linguauniversalis.core.species.SpeciesProfile.SpeedTier;
import com.linguauniversalis.core.taxonomy.Taxonomy;

/**
 * 内置物种档案（当前为设计文档中的两只物种）。
 *
 * <p>数值来源：《万象牧语-设计汇总.md》§11（阿拉克涅）、§12（猫又）。
 * 尚未在文档定稿的数值（如阿拉克涅最大生命/速度）以 TODO 标注占位。
 */
public final class BuiltinSpecies {
    private BuiltinSpecies() {
    }

    /** 需要在注册任何实体之前调用（模组 common setup）。 */
    public static void registerAll() {
        registerArakne();
        registerNekomata();
    }

    /**
     * 阿拉克涅 arakne —— 物质域/外壳界/外生息门/领地巡游纲/规序目/触肢科。
     * 固定刷新型，绑定巢穴方块（蜘蛛巢心）；快捷栏：主手/副手/附肢1/附肢2 + 背包 9。
     *
     * <p>社会习性使用「领地巡游纲」模板（{@link com.linguauniversalis.core.behavior.OrdoTemplates#TERRITORIALIS}），
     * 并显式给出自己的领地/威胁数值（与模板默认一致，显式写出以示范"档案覆盖模板参数"）。
     */
    private static void registerArakne() {
        SpeciesRegistry.register(SpeciesProfile.builder("arakne")
                .name("阿拉克涅", "arakne")
                .taxonomy(Taxonomy.Domain.MATERIAL, Taxonomy.Kingdom.CRUSTACEA,
                        Taxonomy.MagicClassis.EXOSPIRA, Taxonomy.SocialOrdo.TERRITORIALIS,
                        Taxonomy.ElementFamilia.ORDO, Taxonomy.FormaSectio.PALPI)
                .nestBlock("cubile_araneae") // 【物种变量】巢穴方块 = 蜘蛛巢心
                .hotbar("main_hand", "off_hand", "appendage1", "appendage2")
                .backpack(9)
                // 领地巡游纲模板参数（物种数值覆盖模板默认值）
                .templateParam(com.linguauniversalis.core.behavior.OrdoTemplates.KEY_TERRITORY_HORIZONTAL, 24.0)
                .templateParam(com.linguauniversalis.core.behavior.OrdoTemplates.KEY_THREAT_RADIUS, 12.0)
                // 同种族敌意·物种变量（阿拉克涅习性别）：巢穴威胁半径内攻击同种族 = true；
                // 狂暴也攻击同种族 = true（守巢/狂暴都一视同仁，含同族魔物娘）。
                .templateFlag(
                        com.linguauniversalis.core.behavior.TemplateKeys.ORDO_NEST_THREAT_ATTACKS_OWN_KIND, true)
                .templateFlag(
                        com.linguauniversalis.core.behavior.TemplateKeys.ORDO_ENRAGED_ATTACKS_OWN_KIND, true)
                // 专属战斗能力（设计 §11）：5 秒/次蛛网（减速2+挖掘疲劳2，持续 5 秒）；
                // 15 秒/次蛛丝拉拽（命中 10 伤并把目标拉向自己）；近战毒牙带中毒2（8 秒）
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_WEB_COOLDOWN_TICKS, 100.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_WEB_EFFECT_TICKS, 100.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_WEB_AMPLIFIER, 1.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_WEB_RANGE, 12.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SILK_COOLDOWN_TICKS, 300.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SILK_DAMAGE, 10.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SILK_RANGE, 12.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_POISON_TICKS, 160.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_POISON_AMPLIFIER, 1.0)
                // TODO(档案定稿)：阿拉克涅最大生命与速度档在文档中未最终给出，先占位。
                .stats(60f, SpeedTier.FAST)
                .armor(6, 2) // 外壳界默认模板：自带护甲/韧性（数值占位，按模板可覆盖）
                .likeFood("minecraft:beef", "minecraft:porkchop", "minecraft:chicken",
                        "minecraft:rabbit", "minecraft:mutton")
                .combat(15f, 0f, 1f, 0f)
                .build());
    }

    /**
     * 猫又 nekomata —— 物质域/脊索界/外生息门/拟态同化纲/幽暗目/绒科。
     * 自然刷新型；快捷栏：主手/副手 + 背包 8；生命 40，速度"快速"。
     *
     * <p>社会习性使用「拟态同化纲」模板（{@link com.linguauniversalis.core.behavior.OrdoTemplates#MIMETICUS}），
     * 并开启档案级"野生猎亡灵"半径。
     */
    private static void registerNekomata() {
        SpeciesRegistry.register(SpeciesProfile.builder("nekomata")
                .name("猫又", "nekomata")
                .taxonomy(Taxonomy.Domain.MATERIAL, Taxonomy.Kingdom.CHORDATA,
                        Taxonomy.MagicClassis.EXOSPIRA, Taxonomy.SocialOrdo.MIMETICUS,
                        Taxonomy.ElementFamilia.UMBRA, Taxonomy.FormaSectio.PELLIS)
                .spawn(SpawnType.NATURAL)
                // 【物种变量】巢穴方块：猫又设计阶段即无巢穴 → 显式声明"无巢穴"
                // （关闭认领/守巢/领地返回/无巢狂暴/巢穴增益等一切巢穴逻辑）
                .nestBlockNone()
                .hotbar("main_hand", "off_hand")
                .backpack(8)
                // 拟态同化纲模板参数：野生猎亡灵半径（0=不猎）
                .templateParam(com.linguauniversalis.core.behavior.OrdoTemplates.KEY_UNDEAD_HUNT_RADIUS, 16.0)
                // 专属战斗能力（设计 §12）：绒科流血（命中刷新 10 秒、层级+1，无上限，
                // 每 2 秒造成等于层级的物理伤害）；暗影箭（8 格外每 10 秒一发、10 伤、魔法伤害）；
                // 绒科闪避（周期性免疫弹射物 + 侧/后位移）
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_BLEED_REFRESH_TICKS, 200.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_BLEED_TICK_INTERVAL_TICKS, 40.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SHADOW_BOLT_COOLDOWN_TICKS, 200.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SHADOW_BOLT_DAMAGE, 10.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_SHADOW_BOLT_MIN_RANGE, 8.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_DODGE_COOLDOWN_TICKS, 100.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.ABILITY_DODGE_INVULN_TICKS, 10.0)
                // 伪装形态（设计 §12）：野生/友善默认以双尾黑猫出现；空手右键/受伤/主动攻击现人形；
                // 投喂不现形；人形后 60 秒无事恢复猫形；成为伙伴后伪装习性失效
                .templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_DISGUISE, true)
                .templateParam(
                        com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_DISGUISE_REVERT_TICKS, 1200.0)
                // 社交习性（设计 §12）：偷鱼（5 分钟/次，仅游荡状态，只偷背包里的鱼）；
                // 每天 50% 偷村民绿宝石 1–3 颗；偷窃成功后在玩家睡醒时赠送礼物；
                // 亡灵视野内每天一次 +1 心情（由幽暗目模板默认提供）
                .templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_STEALS_FISH, true)
                .templateParam(
                        com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_FISH_STEAL_INTERVAL_TICKS, 6000.0)
                .templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_STEALS_EMERALDS, true)
                // 自然生成（设计 §12）：原版在村庄/沼泽小屋自然刷出猫时，50% 换成野生猫又；
                // 获得过一次好感后不再被自然刷新掉（见 MonsterGirlEntity.everAffectioned）
                .templateParam(
                        com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_VILLAGE_CAT_CONVERSION, 0.5)
                // 体型：碰撞箱 0.6 × 1.5（爬行姿态，宽按建模方要求改回 0.6）；
                // 视线高度 = 模型 ViewLocator 骨骼枢轴 Y（34.47642）÷ 16 = 2.1548 格
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_BODY_WIDTH, 0.6)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_BODY_HEIGHT, 1.5)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_EYE_HEIGHT, 2.1548)
                // 移动速度：两档 —— walk 倍率（所有物种默认 1.0）+ 最快倍率。
                // 最快档标定：玩家疾跑 = 1.3 × 玩家走路；这里要"玩家疾跑的 1.3 倍"
                //   ⇒ 相对走路 ≈ 1.3 × 1.3 ≈ 1.7（0.25 × 1.7 = 0.425 格/tick ≈ 8.5 格/秒）
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_WALK_SPEED_SCALE, 1.0)
                .templateParam(com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_FAST_SPEED_SCALE, 1.7)
                .stats(40f, SpeedTier.FAST)
                .likeFood("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish")
                .combat(5f, 10f, 5f, 0.5f)
                .build());
    }
}
