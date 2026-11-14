package com.linguauniversalis.mixin;

import com.linguauniversalis.compat.CatGirlCompat;
import com.linguauniversalis.core.behavior.CatTraitRules;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让原版"幻翼怕猫"的逻辑也认"猫系魔物娘"。
 *
 * <p>原版实现在 {@code Phantom$PhantomSweepAttackGoal#canContinueToUse()}：
 * 每 20 tick 在幻翼碰撞箱 {@code inflate(16)} 范围内找 {@code Cat}，找到就让这次俯冲放弃。
 * 我们的魔物娘不是 {@code Cat}，所以在这处判定的**返回值**上补一条：
 * 原版本来就返回 false（已经被真猫吓住）→ 不动；否则若 16 格内有猫系魔物娘 → 也返回 false。
 *
 * <p><b>对原版行为零影响</b>：真猫的判定、hiss、计时全在我们的注入点之前跑完；
 * 我们只会在"原本会继续俯冲"的情况下多给一个"放弃"的理由。
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal")
public abstract class PhantomCatFearMixin {
    /**
     * 内部目标类持有外部 Phantom 的合成字段（{@code this$0}）。
     *
     * <p>只读、不加 {@code @Final}：即使哪天这个字段改名，注入点也会因为 {@code require = 0} 静默失效，
     * 而不是把游戏加载搞崩（原版行为保持原样）。
     */
    @Shadow
    private Phantom this$0;

    /** 我们自己的搜索节流（对应原版的 catSearchTick，20 tick）。 */
    @Unique
    private int linguaUniversalis$catGirlSearchTick;

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true, require = 0)
    private void linguaUniversalis$abortWhenCatGirlNear(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) {
            return; // 原版已经判定"被猫吓住"，不插手
        }
        Phantom phantom = this.this$0;
        if (phantom == null || phantom.level() == null) {
            return;
        }
        if (phantom.tickCount < this.linguaUniversalis$catGirlSearchTick) {
            return; // 与原版一致：20 tick 才重算一次
        }
        this.linguaUniversalis$catGirlSearchTick =
                phantom.tickCount + CatTraitRules.PHANTOM_CAT_SEARCH_INTERVAL_TICKS;
        if (CatGirlCompat.hissNearestCatGirl(phantom, CatTraitRules.PHANTOM_CAT_RADIUS_BLOCKS)) {
            // 猫系魔物娘嘶一声（对应原版幻翼靠近真猫时 Cat#hiss() 的效果）
            callback.setReturnValue(false); // 放弃这次俯冲（随后走原版 stop()：清目标 + 回 CIRCLE）
        }
    }
}
