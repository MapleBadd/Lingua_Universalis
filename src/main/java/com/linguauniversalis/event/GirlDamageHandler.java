package com.linguauniversalis.event;

import com.linguauniversalis.core.rule.KnockdownRules;
import com.linguauniversalis.core.rule.RelationshipRules;
import com.linguauniversalis.core.state.MonsterGirlState;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 战败/倒地伤害处理（设计汇总 §5）：
 * <ul>
 *   <li>玩家攻击绑定的魔物娘：每次命中好感 -1（誓约锁定无效由规则保证）；</li>
 *   <li>非野生魔物娘受到致命伤害 → 倒地：锁血 60 秒（期间免疫伤害、清状态），保持 ≥1 HP；</li>
 *   <li>锁血期间其它伤害一律拦截；锁血结束后再次被攻击 → 正常致死。</li>
 * </ul>
 */
public final class GirlDamageHandler {
    private GirlDamageHandler() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof MonsterGirlEntity girl)) {
            return;
        }
        MonsterGirlState st = girl.girlState();
        DamageSource source = event.getSource();

        // 锁血保护：拦截所有伤害
        if (st.isDownedImmune()) {
            event.setAmount(0f);
            return;
        }
        // 弹射物闪避（绒科）：判定按<b>原版弹射物伤害类型标签</b>——
        // 本模组的魔法弹道（暗影箭）使用魔法伤害类型，不属于弹射物，因此不触发闪避、
        // 也不受弹射物保护附魔影响；原版箭/雪球等仍会触发闪避。
        if (girl.level() instanceof ServerLevel srvLevel
                && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            long now = srvLevel.getGameTime();
            if (girl.isDodgeInvulnerable(now) || girl.tryDodgeProjectile(srvLevel, now)) {
                event.setAmount(0f);
                return;
            }
        }
        // 记录受击时间（自愈冷却用）
        if (event.getAmount() > 0f && girl.level() instanceof ServerLevel srv) {
            girl.markDamaged(srv.getGameTime());
        }
        // 物种减伤：受到亡灵伤害按档案减免（如猫又 -50%）
        if (event.getAmount() > 0f && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && MonsterGirlEntity.isUndeadEntity(attacker)) {
            float reduction = girl.profile().undeadDamageReduction();
            if (reduction > 0f) {
                event.setAmount(event.getAmount() * (1f - reduction));
            }
        }
        // 被招惹记忆：真实受击后记录攻击者（用于反击目标选择；绑玩家攻击者仍受规则豁免）
        if (event.getAmount() > 0f && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && attacker != girl && girl.level() instanceof ServerLevel srv) {
            girl.markProvoked(attacker, srv.getGameTime());
        }
        // 倒地脆弱期：不拦截（再次被攻击 → 正常死亡），无额外好感惩罚
        if (st.downed()) {
            return;
        }

        if (source.getEntity() instanceof Player) {
            RelationshipRules.onPlayerAttack(st);
        }
        if (event.getAmount() <= 0f) {
            return;
        }
        boolean lethal = (girl.getHealth() - event.getAmount()) <= 0.001f;
        if (lethal && KnockdownRules.shouldKnockdown(st)) {
            KnockdownRules.beginKnockdown(st);
            event.setAmount(0f);
            girl.setHealth(1f); // 锁血 ≥1
            girl.removeAllEffects();
            if (girl.isInWater()) {
                girl.setNoGravity(true); // 浮于水面
            }
            // TODO: 26.2 火焰清理 API 待查；锁血期间火焰伤害已被拦截
        }
    }
}
