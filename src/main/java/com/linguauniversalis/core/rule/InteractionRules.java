package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.interaction.DailyLimiter;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 通用互动效果（纯规则，服务端调用；无 MC 依赖）。
 *
 * <p>口径（设计汇总 §7）：
 * <ul>
 *   <li>投喂 feed：好感 +1/日（无论吃不吃；饿时吃并回饱食）；</li>
 *   <li>送礼 gift：喜爱物好感 +2/日，非喜爱物 0（收下存放，存放由物品系统处理）；</li>
 *   <li>摸头 pet：友善+ → 好感 +1、心情 +10（每日各一次）；</li>
 *   <li>每日上限统计键 = 玩家 × 魔物娘 × 行为 × 游戏日；</li>
 *   <li>陌生人（已绑定的魔物娘被他人喂）投喂：只保当天好感不掉，不给好感数值
 *       （由调用方传入 {@code bondedActor} 判定）；</li>
 *   <li><b>野生（未绑定）个体投喂 = 认主动作</b>：喂食者 +1 好感/日，
 *       好感首次 ≥10 时自动绑定该喂食者为友善对象（养成起点）。</li>
 * </ul>
 */
public final class InteractionRules {
    private InteractionRules() {
    }

    /** 一次投喂的结果。 */
    public record Outcome(boolean applied, int affection, int mood, int satiety, String reason) {
        public static final Outcome NOT_APPLIED = new Outcome(false, 0, 0, 0, "");
    }

    /**
     * 投喂。是否喜爱/是否食物由调用方依据 {@link SpeciesProfile} 判定；
     * 食物回复的饱食量由调用方按食物属性提供（此处用参数传入）。
     *
     * <p>三种对象口径（决定好感归属）：
     * <ul>
     *   <li><b>野生（未绑定）</b>：投喂是养成起点 —— 喂食者 +1 好感/日并回饱食；
     *       好感首次 ≥友善阈值时，<b>自动绑定该喂食者为友善对象</b>（认主）。</li>
     *   <li><b>已绑定 → 绑玩家本人</b>（{@code bondedActor=true}）：好感 +1/日 + 回饱食。</li>
     *   <li><b>已绑定 → 他人</b>：陌生人喂食不给好感（保当天好感不掉/回心情由心情规则另计），
     *       当天记账一次防刷。</li>
     * </ul>
     */
    public static Outcome feed(MonsterGirlState state, SpeciesProfile profile,
                               boolean isFavorite, int foodSatiety,
                               String actorUuid, String girlUuid, long gameDay,
                               DailyLimiter tracker, boolean bondedActor) {
        // 野生（未绑定）：认主动作 —— 喂食者建立好感并回饱食。
        if (state.boundPlayerUuid() == null) {
            if (!tracker.tryGain("feed", actorUuid, girlUuid, gameDay)) {
                return Outcome.NOT_APPLIED;
            }
            int satietyRestored = Math.min(foodSatiety, Math.max(0, ModConstants.SATIETY_MAX - state.satiety()));
            state.addSatiety(satietyRestored);
            state.addAffection(ModConstants.AFFECTION_PER_FEED);
            // 好感首次跨过友善阈值 → 该喂食者成为她的友善对象（自然养成起点）。
            if (state.affection() >= ModConstants.FRIENDLY_MIN) {
                state.setBoundPlayerUuid(actorUuid);
            }
            return new Outcome(true, ModConstants.AFFECTION_PER_FEED, 0, satietyRestored, "feed_wild");
        }
        // 绑玩家本人投喂。
        if (bondedActor) {
            if (!tracker.tryGain("feed", actorUuid, girlUuid, gameDay)) {
                return Outcome.NOT_APPLIED;
            }
            state.addAffection(ModConstants.AFFECTION_PER_FEED);
            int satietyRestored = Math.min(foodSatiety, Math.max(0, ModConstants.SATIETY_MAX - state.satiety()));
            state.addSatiety(satietyRestored);
            return new Outcome(true, ModConstants.AFFECTION_PER_FEED, 0, satietyRestored, "feed");
        }
        // 陌生人（已有主的个体被他人喂）：只保当天好感不掉/回心情，不给好感。
        if (tracker.tryGain("feed_other", actorUuid, girlUuid, gameDay)) {
            return new Outcome(true, 0, 0, 0, "feed_other");
        }
        return Outcome.NOT_APPLIED;
    }

    /** 送礼。喜爱物 +2/日（非喜爱物 0 但收下存放）。 */
    public static Outcome gift(MonsterGirlState state, boolean isFavorite,
                               String actorUuid, String girlUuid, long gameDay,
                               DailyLimiter tracker, boolean bondedActor) {
        if (!bondedActor) {
            // 陌生人送礼不产生数值（可由物品系统另行处理存放）
            return Outcome.NOT_APPLIED;
        }
        if (!tracker.tryGain("gift", actorUuid, girlUuid, gameDay)) {
            return Outcome.NOT_APPLIED;
        }
        int gain = isFavorite ? ModConstants.AFFECTION_PER_GIFT : 0;
        state.addAffection(gain);
        return new Outcome(true, gain, 0, 0, isFavorite ? "gift_favorite" : "gift");
    }

    /** 摸头。仅友善+；好感 +1、心情 +10，每日各一次。 */
    public static Outcome pet(MonsterGirlState state,
                              String actorUuid, String girlUuid, long gameDay,
                              DailyLimiter tracker, boolean bondedActor) {
        boolean petAllowed = state.isCompanion() || state.affection() >= ModConstants.FRIENDLY_MIN;
        if (!petAllowed) {
            return Outcome.NOT_APPLIED; // 野生不可摸头
        }
        if (bondedActor) {
            if (!tracker.tryGain("pet", actorUuid, girlUuid, gameDay)) {
                return Outcome.NOT_APPLIED;
            }
            int affectionGain = state.vowed() ? 0 : ModConstants.AFFECTION_PER_PET;
            state.addAffection(affectionGain);
            state.addMood(ModConstants.PET_MOOD_GAIN);
            return new Outcome(true, affectionGain, ModConstants.PET_MOOD_GAIN, 0, "pet");
        }
        // 陌生人摸头：只回心情（也走每日上限，防止刷心情）
        if (!tracker.tryGain("pet_other", actorUuid, girlUuid, gameDay)) {
            return Outcome.NOT_APPLIED;
        }
        state.addMood(ModConstants.PET_MOOD_GAIN);
        return new Outcome(true, 0, ModConstants.PET_MOOD_GAIN, 0, "pet_other");
    }
}
