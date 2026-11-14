package com.linguauniversalis.block;

import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 魔物床（当前只有"中魔物床"，未来会有小/大/超大）。
 *
 * <p>做法：**直接继承原版 {@link BedBlock}** —— 占两格、朝向、碰撞箱、放置/破坏/掉落、
 * 以及 {@link BedPart} 逻辑全部复用原版（所以"和原版床一样"是代码保证的，不是模仿）。
 * 外观也复用原版的红色床模型（方块状态指向 {@code minecraft:block/red_bed_*} 模型）。
 *
 * <p>本类只加两件事：
 * <ol>
 *   <li>把体型档挂在方块上（{@code monster_bed} 目前固定 = 中；未来按档位注册多个方块）；</li>
 *   <li>右键床边可以叫醒睡在这张床上的魔物娘（与原版"村民被叫醒"同一手感）。</li>
 * </ol>
 *
 * <p>占床规则：一张床只睡一只（在 {@link MonsterGirlEntity} 侧判定：别的个体已把这张床记为
 * 自己的睡处时，后来的会另找一张）。
 */
public class MonsterBedBlock extends BedBlock implements net.minecraft.world.level.block.EntityBlock {
    /**
     * 与父类 {@code BedBlock#codec()} 的返回类型保持一致（{@code MapCodec<BedBlock>}）——
     * 泛型不协变，写成 {@code MapCodec<MonsterBedBlock>} 会编译不过。
     */
    public static final MapCodec<BedBlock> CODEC = simpleCodec(properties -> new MonsterBedBlock(properties));

    /** 这张床适配的体型档（0=小 / 1=中 / 2=大 / 3=超大）。 */
    private final com.linguauniversalis.core.behavior.SleepRules.SizeClass sizeClass;

    public MonsterBedBlock(Properties properties) {
        this(com.linguauniversalis.core.behavior.SleepRules.SizeClass.MEDIUM, properties);
    }

    public MonsterBedBlock(com.linguauniversalis.core.behavior.SleepRules.SizeClass sizeClass, Properties properties) {
        // 红色床（占位外观）；原版床的两格/碰撞/朝向逻辑都在父类里
        super(DyeColor.RED, properties);
        this.sizeClass = sizeClass == null
                ? com.linguauniversalis.core.behavior.SleepRules.SizeClass.MEDIUM
                : sizeClass;
    }

    public com.linguauniversalis.core.behavior.SleepRules.SizeClass sizeClass() {
        return sizeClass;
    }

    /** 外观交给 GeckoLib 渲染器（方块本体不画模型）。 */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    /** 床的方块实体（GeckoLib 渲染用）。 */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos, BlockState state) {
        return new MonsterBedBlockEntity(pos, state);
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }

    /**
     * 右键：若这张床上睡着魔物娘 → 叫醒她；否则交回原版（玩家睡觉/其他行为）。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            MonsterGirlEntity sleeper = findSleepingGirl(level, pos);
            if (sleeper != null) {
                sleeper.wakeUp();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[Lingua Universalis] She woke up."));
                return InteractionResult.SUCCESS;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /** 找睡在这张床（含床头/床尾两格）上的魔物娘。 */
    private static MonsterGirlEntity findSleepingGirl(Level level, BlockPos pos) {
        List<MonsterGirlEntity> girls = level.getEntitiesOfClass(MonsterGirlEntity.class,
                new AABB(pos).inflate(2.5));
        for (MonsterGirlEntity girl : girls) {
            if (girl.isSleepingInBed() && girl.sleepPos() != null
                    && girl.sleepPos().distSqr(pos) <= 8.0) {
                return girl;
            }
        }
        return null;
    }
}
