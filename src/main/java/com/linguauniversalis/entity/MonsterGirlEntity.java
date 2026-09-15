package com.linguauniversalis.entity;

import com.linguauniversalis.block.CompanionChestBlockEntity;
import com.linguauniversalis.core.behavior.CommandRules;
import com.linguauniversalis.core.behavior.CommandRules.CommandMode;
import com.linguauniversalis.codex.ServerCodex;
import com.linguauniversalis.core.behavior.HostilityRules;
import com.linguauniversalis.core.interaction.PersistentDailyLimiter;
import com.linguauniversalis.core.rule.DormancyClock;
import com.linguauniversalis.core.rule.HungerRules;
import com.linguauniversalis.core.rule.InteractionRules;
import com.linguauniversalis.core.rule.KnockdownRules;
import com.linguauniversalis.core.rule.LowMoodRecoveryTracker;
import com.linguauniversalis.core.rule.MoodActivityClock;
import com.linguauniversalis.core.rule.RelationshipRules;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.core.state.GirlStatePersistence;
import com.linguauniversalis.core.state.MonsterGirlState;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Base64;
import java.util.UUID;

/**
 * 通用魔物娘实体（Phase 2/4）。
 *
 * <p>承载 {@link MonsterGirlState} 并以 {@link GirlStatePersistence}（Base64 字符串）
 * 接入 26.2 流式存档；按物种档案设属性与基础 AI；提供互动实体接入：
 * <ul>
 *   <li>Shift+右键：命令循环 跟随/待机/游荡（仅伙伴绑定玩家，好感≥100，非低落/休眠）；</li>
 *   <li>持食物右键：投喂（+好感，每日一次；回饱食）；</li>
 *   <li>空手右键：友善+ 摸头（+好感+心情，每日一次）；休眠时绑玩家摸头可唤醒；</li>
 *   <li>急救箱/送礼/誓约等道具行为在物品阶段接入（占位）。</li>
 * </ul>
 */
public class MonsterGirlEntity extends PathfinderMob
        implements com.geckolib.animatable.GeoEntity {
    public static final String DEFAULT_SPECIES = "arakne";
    private static final String TAG_SPECIES = "LUSpecies";
    private static final String TAG_STATE = "LUState";
    private static final String TAG_OBEY = "LUObeyMode";
    private static final String TAG_LIMITER = "LUDailyLimit";

    private MonsterGirlState girlState = new MonsterGirlState();
    private String speciesId = DEFAULT_SPECIES;
    private String obeyModeId = CommandMode.FOLLOW.englishId;

    // 每日互动上限跟踪（随实体存档持久化：行为×玩家 → 最后奖励游戏日）
    private final PersistentDailyLimiter interactionTracker = new PersistentDailyLimiter();
    private final DormancyClock dormancyClock = new DormancyClock();

    // tick 状态（不持久化；跨会话后重新累计）
    private final MoodActivityClock activityClock = new MoodActivityClock();
    private long satietyDecayTicks;
    private long starvationTicks;
    private long regenTicks;
    /** 最近一次受击 tick（服务端；-1=从未受伤），用于自愈冷却。 */
    private long lastDamageTick = -1L;

    public void markDamaged(long serverTick) {
        this.lastDamageTick = serverTick;
        // 受伤 → 现出人形（伪装失效；设计 §12）
        revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger.DAMAGED, serverTick);
    }
    private long activitySampleTicks;
    private long dormancySecondTicks;
    private int lastSettledDay = -1;
    private boolean satietyLowSeenThisDay;
    private long lastFriendlyGameDay = -1;
    /** 每玩家最后交互 tick：所有右键交互（摸头/投喂/送礼/急救/百晓镜/命令等）共用入口去抖，
     *  吞掉引擎对同一次点按的重复分发（双手探测/重复调用）。 */
    private final java.util.Map<String, Long> lastInteractTick = new java.util.HashMap<>();
    /** 随机散步目标（跟随/待机/倒地时动态移除，避免与命令行为抢寻路）。 */
    private WaterAvoidingRandomStrollGoal strollGoal;
    private boolean strollGoalEnabled;
    /** 跟随模式重算节流（服务端；每 10 tick 评估一次寻路/传送）。 */
    private int followEvalTicks;
    /**
     * 与主人 ≤6 格（"跟随最低距离"）：不再移动、移速也降回 walk 档。
     *
     * <p>距离常量与移速档位共用同一个来源（{@link com.linguauniversalis.core.behavior.MovementSpeedRules#FOLLOW_STOP_DISTANCE}）——
     * 保证"追到跟随最低距离才减速"和"到这儿就停步"是同一个距离。
     */
    private static final double FOLLOW_STOP_DIST_SQ =
            com.linguauniversalis.core.behavior.MovementSpeedRules.FOLLOW_STOP_DIST_SQ;
    /** 与主人 >32 格：就近传送（原 64 格，太远，收近）。 */
    private static final double FOLLOW_TELEPORT_DIST_SQ = 1024.0;
    /** 与主人 ≤12 格：每 tick 持续看向主人（保证头部平滑跟随，不“看一眼就回正”）。 */
    private static final double FOLLOW_LOOK_DIST_SQ = 144.0;

    // ---------------------------------------------------------------- 敌意/近战（Phase 4 物种专属行为）
    /** 当前敌意目标（服务端；由敌意规则每 N tick 重算）。 */
    private LivingEntity combatTarget;
    /** 敌意目标重算节流计数。 */
    private long hostileScanTicks;
    /** 近战攻击剩余冷却（tick）。 */
    private long meleeCooldownTicks;
    /** 最近攻击过她的生物（反击记忆：uuid + 游戏 tick）。 */
    private String provokedByUuid;
    private long provokedTick = -1L;
    /** 低落·随机攻击模板：击杀回心情计时器。 */
    private final LowMoodRecoveryTracker lowMoodRecovery = new LowMoodRecoveryTracker();

    // ---------------------------------------------------------------- 物种专属战斗能力（设计 §11/§12）
    /** 能力冷却计时（键为能力 id：web / silk / shadow_bolt / dodge）。 */
    private final com.linguauniversalis.core.behavior.SpeciesAbilities.Cooldowns abilityCooldowns =
            new com.linguauniversalis.core.behavior.SpeciesAbilities.Cooldowns();
    /** 能力 id（冷却计时键）。 */
    private static final String ABILITY_WEB = "web";
    private static final String ABILITY_SILK = "silk";
    private static final String ABILITY_SHADOW_BOLT = "shadow_bolt";
    private static final String ABILITY_DODGE = "dodge";
    /** 流血状态（按受害者 UUID；命中叠加层级，周期性结算伤害）。 */
    private final java.util.Map<String, com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed> bleedOn =
            new java.util.HashMap<>();
    /** 弹射物闪避免疫窗口截止 tick（-1 = 无效）。 */
    private long dodgeInvulnUntilTick = -1L;

    // ---------------------------------------------------------------- 伪装形态（设计 §12 猫又）
    /** 同步标记：true = 伪装形态（猫形，客户端据此换模型/贴图）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_CAT_FORM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 最近一次现出人形的 tick（-1 = 从未）。 */
    private long lastRevealTick = -1L;

    // ---------------------------------------------------------------- 客户端表现用同步状态
    // 说明：动画控制器与渲染只在客户端运行，而休眠/命令/进食/跳跃计时都只存在于服务端；
    // 因此把这些"表现输入"用同步数据广播（数据监视器只在变化时发包，开销可忽略）。
    /** 同步：是否休眠（人形 main_sit、猫形趴姿）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_DORMANT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：是否以猫形陪玩家睡觉。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_SLEEPING_WITH_OWNER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：跳跃动画窗口是否激活（人形 main_jump）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_ANIM_JUMP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：进食动画窗口是否激活（人形 main_eat）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_ANIM_EAT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：当前命令模式 id（猫形坐姿等表现用）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_OBEY_MODE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

    // ---------------------------------------------------------------- 社交习性（设计 §12 猫又）
    /** 上次偷鱼 tick（-1 = 从未）。 */
    private long lastFishStealTick = -1L;
    /** 上次偷村民绿宝石的游戏日（-1 = 从未）。 */
    private long lastEmeraldStealDay = -1L;
    /** 上次"亡灵视野 +心情"的游戏日（-1 = 从未）。 */
    private long lastUndeadMoodDay = -1L;
    /** 是否有待赠送的礼物（偷窃成功后置位，玩家睡醒时送出）。 */
    private boolean pendingGift;
    /** 是否持有绿宝石礼物标记（当天偷到过村民绿宝石 → 下次礼物必为绿宝石）。 */
    private boolean emeraldGiftArmed;
    /** 绑玩家上一 tick 是否在睡觉（用于检测"睡醒"边沿）。 */
    private boolean ownerWasSleeping;
    /** 当前是否正以猫形态陪睡（醒后恢复人形）。 */
    private boolean sleepingAsCat;
    /** 叼走的食物预定"吃掉"的 tick（-1 = 无）。 */
    private long stealEatAtTick = -1L;
    /** 偷到的绿宝石累计（她的背包占位计数，Phase 3 接物品栏）。 */
    private int stolenEmeralds;
    /**
     * 是否"获得过一次好感"（粘性标记，一旦置位不再清除；设计 §12「获得过一次好感后不再被刷新」）。
     *
     * <p>置位后该个体转为持久个体，不会被自然刷新（despawn）掉，防止养成中断。
     */
    private boolean everAffectioned;

    // ---------------------------------------------------------------- 巢心（领地巡游纲）
    /** 领地/威胁圆心的巢心方块坐标（null = 尚无巢）。 */
    private BlockPos nestPos;
    /** 是否"无巢狂暴"（领地巡游纲模板默认 true；刷怪蛋生成个体 = false，见 finalizeSpawn）。 */
    private boolean rageWithoutNest = true;
    /** 当前狂暴状态（每 tick 由 NestGuard 依据巢心评估）。 */
    private boolean enraged;
    /** 巢心认领扫描节流（无巢时周期性找附近巢心方块）。 */
    private int nestClaimTicks;
    /** 外生息门·巢穴增益下次刷新 tick。 */
    private long nestBuffNextTick;
    /** 巢穴增益刷新间隔（tick，4 秒）与持续时长（tick，6 秒，略长于刷新间隔避免闪烁）。 */
    private static final long NEST_BUFF_REFRESH_TICKS = 80L;
    private static final int NEST_BUFF_DURATION_TICKS = 120;

    /** 连续离地 tick 数（客户端也维护；用于 airborne 动画的"稳定"判定）。 */
    private int airborneTicks;

    /** 主控制器引用（构造后赋值；供其判定回调里动态改 {@code main_walk} 播放倍速）。 */
    private com.geckolib.animation.AnimationController<MonsterGirlEntity> mainController;


    /** 当前注视目标（服务端维护，同步给客户端用于头部跟随的偏航/俯仰来源）。 */
    private int syncLookTargetId = -1;

    // ---------------------------------------------------------------- 客户端表现用同步状态（注视目标）
    /** 同步：当前注视的实体 id（-1 = 无）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_LOOK_TARGET_ID =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** 客户端表现：当前注视目标实体 id（-1 = 无）。 */
    public int lookTargetIdVisual() {
        return this.getEntityData().get(DATA_LOOK_TARGET_ID);
    }

    public MonsterGirlEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // 会捡地上的东西（捡什么由 wantsToPickUp 决定：不饿不捡食物、满了不捡）
        this.setCanPickUpLoot(true);
    }

    /** 按物种创建：生成后立即写入对应档案（供 arakne/nekomata 等专有实体类型使用）。 */
    public static MonsterGirlEntity ofSpecies(EntityType<? extends PathfinderMob> type, Level level, String speciesId) {
        MonsterGirlEntity girl = new MonsterGirlEntity(type, level);
        girl.setSpeciesId(speciesId);
        return girl;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
    }

    // ------------------------------------------------------------------ 动画（GeckoLib；规则见 core/anim/AnimationRules）
    /** GeckoLib 实例缓存（每实体一份动画状态）。 */
    private final com.geckolib.animatable.instance.AnimatableInstanceCache geoCache =
            com.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    /** 主动跳跃动画结束 tick（-1 = 未在播放）；播完自动转 main_airborne（规则 5）。 */
    private long animJumpUntilTick = -1L;
    /** 进食动画结束 tick（-1 = 未在播放）。 */
    private long animEatUntilTick = -1L;
    /** 随机眨眼系列（每秒 3%）；同系列播放期间不重复触发（规则 4）。 */
    private final com.linguauniversalis.core.anim.AnimationRules.RandomSeries blinkSeries =
            new com.linguauniversalis.core.anim.AnimationRules.RandomSeries(
                    com.linguauniversalis.core.anim.AnimationRules.RANDOM_BLINK, 8);
    /** 随机小动作系列（每秒 3%）。 */
    private final com.linguauniversalis.core.anim.AnimationRules.RandomSeries idleSeries =
            new com.linguauniversalis.core.anim.AnimationRules.RandomSeries(
                    com.linguauniversalis.core.anim.AnimationRules.RANDOM_IDLE, 5);

    @Override
    public com.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 触发一次主跳跃动画（仅"主动跳跃"；播完交给 main_airborne）。 */
    public void playJumpAnimation() {
        animJumpUntilTick = this.tickCount + com.linguauniversalis.core.anim.AnimationRules.JUMP_ANIM_TICKS;
    }

    /** 触发一次进食动画（投喂/偷吃时调用）。 */
    public void playEatAnimation(int ticks) {
        animEatUntilTick = this.tickCount + Math.max(10, ticks);
    }

    /** 触发一次摸头混合动画（pet 控制器的一次性动画）。 */
    public void playPetAnimation() {
        this.triggerAnim("pet", "pet");
    }

    /**
     * 注册动画控制器。
     *
     * <p><b>顺序即优先级</b>：GeckoLib 的 {@code applyAnimationControllers} 按注册顺序把各控制器的
     * 骨骼快照写进同一份 {@code BoneSnapshots}，<b>后写覆盖先写</b>（同骨骼同属性）。
     * 因此这里按"底 → 上"排列：{@code constant}（细节底噪）→ {@code main}（主体动作）
     * → {@code tail}/{@code pet}/{@code random_*}（叠加表现，优先级最高）。
     *
     * <p>注意：若把 {@code constant} 放在 {@code main} 之后，它会用自己那点幅度覆盖
     * {@code main} 对同一骨骼的旋转——资源里的 {@code constant} 恰好也动 {@code LeftArm/RightArm}，
     * 会导致"两条手臂不随主要动画摆动"。
     *
     * <ul>
     *   <li>{@code constant}：常驻细节（眉毛/眼皮/眼点/Shrink 缩放）；</li>
     *   <li>{@code main}：主要动画（main_ 前缀，互斥 + 过渡）；</li>
     *   <li>{@code tail}：尾巴常驻摆动；</li>
     *   <li>{@code pet}：可触发的一次性混合动画（摸头触发）；</li>
     *   <li>{@code random_blink} / {@code random_idle}：随机混合动画（每秒 3%，同系列互斥）。</li>
     * </ul>
     */
    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar registrar) {
        // 底层：constant（一直在播；只贡献主要动画没有的那些骨骼/属性）
        registrar.add(loopController("constant", com.linguauniversalis.core.anim.AnimationRules.BLEND_CONSTANT));

        // 主要动画：同一时间只播一个，切换走 GeckoLib 过渡（放在 constant 之后 → 手臂等骨骼以它为准）
        // 过渡时长可在 config/lingua_universalis.properties 调整（0 = 不做过渡）
        ControllerRef mainRef = new ControllerRef();
        this.mainController = new com.geckolib.animation.AnimationController<MonsterGirlEntity>(
                "main", com.linguauniversalis.core.config.LuSettings.get()
                        .mainTransitionTicks(this.speciesId), test -> {
            MonsterGirlEntity girl = test.animatable();
            boolean jumpAnim = girl.isJumpAnimActive();
            boolean airborne = com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(
                    com.linguauniversalis.core.anim.AnimationRules.isAirborne(
                            girl.onGround(), girl.isInWater() || girl.isInLava(), girl.isFallFlying()),
                    girl.fallDistance, girl.airborneTicks);
            boolean eating = girl.isEatAnimActive();
            boolean sitting = girl.isDormantVisual();
            boolean moving = test.isMoving();
            String name = com.linguauniversalis.core.anim.AnimationRules.mainAnimation(
                    jumpAnim, airborne, eating, sitting, moving);
            // 过渡时长按目标动画选：切到 main_jump 用短过渡（默认 0 = 硬切），否则用主过渡时长。
            // 跳跃动画只有 0.42 秒，长过渡会把整段跳跃稀释掉（表现为"跳跃动画没生效"）。
            if (mainRef.controller != null) {
                int wantedTicks = jumpAnim
                        ? com.linguauniversalis.core.config.LuSettings.get().jumpTransitionTicks(girl.speciesId)
                        : com.linguauniversalis.core.config.LuSettings.get().mainTransitionTicks(girl.speciesId);
                if (wantedTicks != girl.appliedTransitionTicks) {
                    girl.appliedTransitionTicks = wantedTicks;
                    mainRef.controller.setTransitionTicks(wantedTicks);
                }
            }
            // 动画播放倍速：逐动画/逐物种可配（animation.speed.<动画名>，可按物种覆盖）
            mainRef.applySpeed(girl, name);
            return test.setAndContinue(girl.rawFor(name));
        });
        mainRef.controller = this.mainController;
        registrar.add(this.mainController);

        // 尾巴混合动画（持续播放；未来按心情/状态切换不同 tail_ 动画）
        registrar.add(loopController("tail", com.linguauniversalis.core.anim.AnimationRules.BLEND_TAIL_SWING));

        // 随机混合动画：两系列各自独立计时（每秒 3%，同系列互斥）
        registrar.add(randomController("random_blink", blinkSeries));
        registrar.add(randomController("random_idle", idleSeries));

        // 摸头：一次性混合动画，由实体 {@code triggerAnim("pet", "pet")} 触发；
        // 过渡 0 tick 让这段 0.17 秒的短动画不被稀释；放在最后 = 优先级最高。
        // 注意：**不能**调用 receiveTriggeredAnimations() —— 那表示"我自己在判定回调里接管触发动画"，
        // 而我们的回调恒返回 STOP，等于把触发动画直接掐掉（这正是摸头一直不生效的原因）。
        ControllerRef petRef = new ControllerRef();
        com.geckolib.animation.AnimationController<MonsterGirlEntity> petController =
                new com.geckolib.animation.AnimationController<MonsterGirlEntity>(
                        "pet", 0, test -> {
                    petRef.applySpeed(test.animatable(), com.linguauniversalis.core.anim.AnimationRules.BLEND_PET);
                    return com.geckolib.animation.object.PlayState.STOP;
                })
                        .triggerableAnim("pet", com.geckolib.animation.RawAnimation.begin()
                                .thenPlay(com.linguauniversalis.core.anim.AnimationRules.BLEND_PET));
        petRef.controller = petController;
        registrar.add(petController);
    }

    /** 已应用到主控制器的过渡时长（只在变化时写，避免打断正在进行的过渡）。 */
    private int appliedTransitionTicks = -1;

    /** 控制器自持引用：在它自己的判定回调里设置它自己的播放倍速。 */
    private static final class ControllerRef {
        private com.geckolib.animation.AnimationController<MonsterGirlEntity> controller;

        private void applySpeed(MonsterGirlEntity girl, String animationName) {
            if (controller == null) {
                return;
            }
            controller.setAnimationSpeed(com.linguauniversalis.core.config.LuSettings.get()
                    .animationSpeed(girl.speciesId, animationName));
        }
    }

    /** 常驻循环混合动画控制器（播放倍速同样可配，且作用在它自己身上）。 */
    private static com.geckolib.animation.AnimationController<MonsterGirlEntity> loopController(
            String controllerName, String animationName) {
        ControllerRef ref = new ControllerRef();
        com.geckolib.animation.AnimationController<MonsterGirlEntity> controller =
                new com.geckolib.animation.AnimationController<MonsterGirlEntity>(
                        controllerName, test -> {
                    ref.applySpeed(test.animatable(), animationName);
                    return test.setAndContinue(
                            com.geckolib.animation.RawAnimation.begin().thenLoop(animationName));
                });
        ref.controller = controller;
        return controller;
    }

    /** 随机混合动画控制器：命中时播一遍，否则不干扰当前姿态。 */
    private static com.geckolib.animation.AnimationController<MonsterGirlEntity> randomController(
            String controllerName, com.linguauniversalis.core.anim.AnimationRules.RandomSeries series) {
        ControllerRef ref = new ControllerRef();
        com.geckolib.animation.AnimationController<MonsterGirlEntity> controller =
                new com.geckolib.animation.AnimationController<MonsterGirlEntity>(
                        controllerName, 2, test -> {
                    MonsterGirlEntity girl = test.animatable();
                    boolean playing = series.poll(girl.tickCount, girl.getRandom().nextDouble());
                    if (!playing) {
                        return com.geckolib.animation.object.PlayState.STOP;
                    }
                    ref.applySpeed(girl, series.animation());
                    return test.setAndContinue(com.geckolib.animation.RawAnimation.begin()
                            .thenPlay(series.animation()));
                });
        ref.controller = controller;
        return controller;
    }

    /** 按动画名取缓存的 RawAnimation（循环动画 thenLoop；跳跃为播放并停在末帧）。 */
    private com.geckolib.animation.RawAnimation rawFor(String name) {
        com.geckolib.animation.RawAnimation cached = rawAnimCache.get(name);
        if (cached != null) {
            return cached;
        }
        com.geckolib.animation.RawAnimation raw =
                com.linguauniversalis.core.anim.AnimationRules.MAIN_JUMP.equals(name)
                        ? com.geckolib.animation.RawAnimation.begin().thenPlayAndHold(name)
                        : com.geckolib.animation.RawAnimation.begin().thenLoop(name);
        rawAnimCache.put(name, raw);
        return raw;
    }

    private final java.util.Map<String, com.geckolib.animation.RawAnimation> rawAnimCache =
            new java.util.HashMap<>();

    // ------------------------------------------------------------------ 伪装形态（设计 §12）
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CAT_FORM, false);
        builder.define(DATA_DORMANT, false);
        builder.define(DATA_SLEEPING_WITH_OWNER, false);
        builder.define(DATA_ANIM_JUMP, false);
        builder.define(DATA_ANIM_EAT, false);
        builder.define(DATA_OBEY_MODE, CommandMode.FOLLOW.englishId);
        builder.define(DATA_LOOK_TARGET_ID, -1);
    }

    // ---------------------------------------------------------------- 客户端表现读取
    /** 客户端表现：是否休眠。 */
    public boolean isDormantVisual() {
        return this.getEntityData().get(DATA_DORMANT);
    }

    /** 客户端表现：是否以猫形陪玩家睡觉。 */
    public boolean isSleepingWithOwnerVisual() {
        return this.getEntityData().get(DATA_SLEEPING_WITH_OWNER);
    }

    /** 客户端表现：是否处于待机命令。 */
    public boolean isStandingByVisual() {
        return CommandMode.STANDBY.englishId.equals(this.getEntityData().get(DATA_OBEY_MODE));
    }

    /** 跳跃动画窗口是否激活（客户端/服务端一致）。 */
    public boolean isJumpAnimActive() {
        return this.getEntityData().get(DATA_ANIM_JUMP);
    }

    /** 进食动画窗口是否激活（客户端/服务端一致）。 */
    public boolean isEatAnimActive() {
        return this.getEntityData().get(DATA_ANIM_EAT);
    }

    /** 服务端：把"表现输入"广播到客户端（仅变化时发包）。 */
    private void syncVisualState() {
        net.minecraft.network.syncher.SynchedEntityData data = this.getEntityData();
        data.set(DATA_DORMANT, girlState.dormant());
        data.set(DATA_SLEEPING_WITH_OWNER, sleepingAsCat);
        data.set(DATA_ANIM_JUMP, animJumpUntilTick >= 0 && this.tickCount < animJumpUntilTick);
        data.set(DATA_ANIM_EAT, animEatUntilTick >= 0 && this.tickCount < animEatUntilTick);
        data.set(DATA_OBEY_MODE, obeyModeId == null ? CommandMode.FOLLOW.englishId : obeyModeId);
        data.set(DATA_LOOK_TARGET_ID, syncLookTargetId);
    }

    /** 当前是否为伪装形态（猫形）。 */
    public boolean isCatForm() {
        return this.getEntityData().get(DATA_CAT_FORM);
    }

    /** 切换伪装形态（会同步客户端并刷新碰撞箱尺寸）。 */
    public void setCatForm(boolean catForm) {
        if (this.getEntityData().get(DATA_CAT_FORM) == catForm) {
            return;
        }
        this.getEntityData().set(DATA_CAT_FORM, catForm);
        this.refreshDimensions();
    }

    /**
     * 现出人形（伪装失效）：空手右键 / 受伤 / 主动攻击三选一触发。
     * 非伪装物种或伙伴档（伪装习性失效）不处理。
     */
    public void revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger trigger, long nowTick) {
        if (!com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())
                || !com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(girlState.isCompanion())
                || !com.linguauniversalis.core.behavior.DisguiseRules.reveals(trigger)) {
            return;
        }
        this.lastRevealTick = nowTick;
        setCatForm(false);
    }

    /**
     * 碰撞箱：猫形用矮小尺寸；其余按<b>物种变量</b>（
     * {@link com.linguauniversalis.core.behavior.TemplateKeys#SPECIES_BODY_WIDTH} /
     * {@code SPECIES_BODY_HEIGHT} / {@code SPECIES_EYE_HEIGHT}）取值，
     * 未声明时回落 {@link com.linguauniversalis.core.species.BodyRules} 的默认（0.6 × 1.8、眼高 1.62）。
     *
     * <p>猫又按设计是爬行姿态：0.9 × 1.5，视线高度取模型 {@code ViewLocator} 骨骼枢轴（34.47642 ÷ 16 = 2.1548）。
     */
    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        if (isCatForm()) {
            return net.minecraft.world.entity.EntityDimensions.scalable(0.6f, 0.7f).withEyeHeight(0.5f);
        }
        com.linguauniversalis.core.species.BodyRules.Size size =
                com.linguauniversalis.core.species.BodyRules.of(profile(),
                        com.linguauniversalis.core.config.LuSettings.get().modelRenderScale(speciesId),
                        com.linguauniversalis.core.config.LuSettings.get().bodyWidth(speciesId),
                        com.linguauniversalis.core.config.LuSettings.get().bodyHeight(speciesId),
                        com.linguauniversalis.core.config.LuSettings.get().bodyEyeHeight(speciesId));
        return net.minecraft.world.entity.EntityDimensions.scalable(size.width(), size.height())
                .withEyeHeight(size.eyeHeight());
    }

    /** 伪装形态 tick：人形静默超时 → 恢复伪装（猫形）。 */
    private void tickDisguise(ServerLevel server) {
        if (!com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())
                || !com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(girlState.isCompanion())) {
            return;
        }
        if (isCatForm() || lastRevealTick < 0) {
            return;
        }
        long revert = com.linguauniversalis.core.behavior.DisguiseRules.revertTicks(profile());
        if (com.linguauniversalis.core.behavior.DisguiseRules.shouldRevert(
                server.getGameTime(), lastRevealTick, revert)) {
            setCatForm(true);
        }
    }

    // ------------------------------------------------------------------ state 访问
    public MonsterGirlState girlState() {
        return girlState;
    }

    public String speciesId() {
        return speciesId;
    }

    public void setSpeciesId(String speciesId) {
        if (SpeciesRegistry.contains(speciesId)) {
            this.speciesId = speciesId;
            applySpeciesMovementSpeed();
        }
    }

    public SpeciesProfile profile() {
        return SpeciesRegistry.get(speciesId);
    }

    /** 模组自身工具类物品（命缕/命帛/百晓镜/初稿/礼物盒/急救箱/誓约协议书/调试道具）不作为普通礼物。 */
    private boolean isLUTool(ItemStack stack) {
        return stack.getItem() == LURegistries.FIRST_DRAFT.get()
                || stack.getItem() == LURegistries.FATUM_FILUM.get()
                || stack.getItem() == LURegistries.FATUM_PANNUS.get()
                || stack.getItem() == LURegistries.SPECULUM_SCIENTIAE.get()
                || stack.getItem() == LURegistries.PRESENT_CASE.get()
                || stack.getItem() == LURegistries.FIRST_AID_KIT.get()
                || stack.getItem() == LURegistries.PAPER_OF_VOW.get()
                || stack.getItem() == LURegistries.DEBUG_GIRL_REMOVER.get()
                || stack.getItem() == LURegistries.DEBUG_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get();
    }

    public String obeyModeId() {
        return obeyModeId;
    }

    /**
     * 生成个体快照（species|obey|Base64(GirlState)），供命缕/复活使用。
     *
     * <p>命缕记录的是"个体档案"，不包含战败（倒地）这一过程姿态：快照前先清除倒地与锁血，
     * 保证经绽放之刺/誓约轮回复活后魔物娘是站立状态而非延续死前的倒地姿态。
     */
    public String girlSnapshot() {
        MonsterGirlState archived = copyOf(girlState);
        archived.setDowned(false); // 同时清掉 downedLockTicks（见 MonsterGirlState#setDowned）
        return speciesId + "\u0001" + obeyModeId + "\u0001"
                + Base64.getEncoder().encodeToString(GirlStatePersistence.encode(archived));
    }

    /** 复制一份个体状态（不共享引用），供快照存档前的清洗使用。 */
    private static MonsterGirlState copyOf(MonsterGirlState src) {
        return GirlStatePersistence.decode(GirlStatePersistence.encode(src));
    }

    /** 从快照恢复个体档案；返回是否成功。复活路径统一兜底：不继承倒地/锁血状态。 */
    public boolean applySnapshot(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        String[] parts = snapshot.split("\u0001", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            setSpeciesId(parts[0]);
            obeyModeId = parts[1];
            this.girlState = GirlStatePersistence.decode(Base64.getDecoder().decode(parts[2]));
            this.girlState.setDowned(false); // 兜底：旧命缕若含倒地标记也不带入复活
            return true;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 生成
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        if (getAttribute(Attributes.MAX_HEALTH) != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(profile().maxHealth());
        }
        this.setHealth(this.getMaxHealth());
        // 领地巡游纲"无巢狂暴"：模板默认 true；刷怪蛋生成的个体 = false（避免出生即狂暴）
        this.rageWithoutNest = !isEggSpawnReason(reason)
                && com.linguauniversalis.core.behavior.OrdoTemplates.of(profile())
                        .ragesWithoutNest(profile());
        applySpeciesMovementSpeed();
        // 伪装形态：有伪装习性的物种在野生/友善档默认以伪装形态出现（伙伴档失效）
        if (com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())
                && com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(girlState.isCompanion())) {
            setCatForm(true);
        }
        return data;
    }

    private static boolean isEggSpawnReason(EntitySpawnReason reason) {
        return reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.DISPENSER;
    }

    /**
     * 按当前档位（walk / 最快）把移速写进属性。
     *
     * <p>档位：攻击敌人 → 最快；跟随且离绑玩家 &gt; 8 格 → 最快；其余（游荡 / 野生无敌对 / 跟得够近）→ walk。
     * 基础值仍是 {@link #createAttributes()} 的 0.25；幂等，且**只在真的变档时才调用**
     * （见 {@link #tickMovementSpeed()}）。
     */
    public void applySpeciesMovementSpeed() {
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
                this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean fast = computeFastSpeed(this.fastSpeedActive);
        this.fastSpeedActive = fast;
        double scale = com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                fast,
                com.linguauniversalis.core.config.LuSettings.get().speedWalkScale(speciesId),
                com.linguauniversalis.core.config.LuSettings.get().speedFastScale(speciesId),
                profile());
        this.speedScaleApplied = scale;
        speed.setBaseValue(BASE_MOVEMENT_SPEED * scale);
    }

    /**
     * 每 tick 重算档位并（仅在变化时）写属性。
     *
     * <p>为什么必须每 tick 算：属性只在 {@code finalizeSpawn}/{@code setSpeciesId} 里写过一次，
     * 而那时候既没有敌人也没绑定玩家 ⇒ 永远是 walk 档，fast 永远不生效。
     *
     * <p>为什么不用重新寻路：{@code MoveControl#tick} 每 tick 都做
     * {@code setSpeed(speedModifier × 属性值)}，所以改属性下一 tick 就生效；
     * 配置热改（{@code speed.walkScale}/{@code speed.fastScale}）也因此自动跟上。
     */
    private void tickMovementSpeed() {
        boolean fast = computeFastSpeed(this.fastSpeedActive);
        double scale = com.linguauniversalis.core.behavior.MovementSpeedRules.speedScale(
                fast,
                com.linguauniversalis.core.config.LuSettings.get().speedWalkScale(speciesId),
                com.linguauniversalis.core.config.LuSettings.get().speedFastScale(speciesId),
                profile());
        if (fast == this.fastSpeedActive && Math.abs(scale - this.speedScaleApplied) < 1.0e-6) {
            return; // 没变档就不重写属性（避免每 tick 触发属性同步包）
        }
        applySpeciesMovementSpeed();
    }

    /** 上一次写进属性的移速倍率（-1 = 还没写过）。 */
    private double speedScaleApplied = -1.0;
    /** 当前是否处于最快档（用于跟随 8 格判据的滞回）。 */
    private boolean fastSpeedActive;

    /** 当前是否处于"最快速度"档（服务端判定；规则见 {@link com.linguauniversalis.core.behavior.MovementSpeedRules}）。 */
    private boolean computeFastSpeed(boolean currentlyFast) {
        boolean hasTarget = hasCombatTarget();
        boolean following = false;
        double distSq = Double.MAX_VALUE;
        if (com.linguauniversalis.core.behavior.CommandRules.CommandMode.FOLLOW.englishId.equals(obeyModeId)
                && this.level() instanceof ServerLevel server) {
            String ownerUuid = girlState.boundPlayerUuid();
            if (ownerUuid != null
                    && com.linguauniversalis.core.behavior.CommandRules.canCommand(girlState, ownerUuid)) {
                ServerPlayer owner = resolveOwner(server, ownerUuid);
                if (owner != null && owner.level() == this.level() && !owner.isRemoved() && owner.isAlive()) {
                    following = true;
                    distSq = this.distanceToSqr(owner);
                }
            }
        }
        return com.linguauniversalis.core.behavior.MovementSpeedRules.useFastSpeed(
                hasTarget, following, distSq, currentlyFast);
    }

    /**
     * 是否有正在交战的敌人。
     *
     * <p><b>必须读本模组自己的 {@link #combatTarget}，不能读原版 {@code getTarget()}</b>——
     * 本实体从不调用 {@code setTarget()}（索敌全在 {@code tickCombat} 里做），
     * 所以 {@code getTarget()} 恒为 null，用它判断等于永远"没敌人"。
     */
    public boolean hasCombatTarget() {
        return combatTarget != null && combatTarget.isAlive() && !combatTarget.isRemoved()
                && combatTarget.level() == this.level();
    }

    /** 通用基础移动速度（各物种再乘 {@code species.moveSpeedScale}）。 */
    public static final double BASE_MOVEMENT_SPEED = 0.25;

    // ------------------------------------------------------------------ 快捷栏 / 背包（设计 §6）
    /** 她的快捷栏 + 背包（连续容器：0..快捷栏数-1 = 快捷栏，其后 = 背包）；按物种档案格数惰性创建。 */
    private GirlInventory girlInventory;
    /** 换容量时装不下的物品，下一 tick 掉在脚下（正常情况下不会发生：物种只在出生/读档时确定）。 */
    private final java.util.List<ItemStack> inventoryOverflow = new java.util.ArrayList<>();

    /**
     * 快捷栏 + 背包容器。
     *
     * <p>格数由**物种档案**决定（{@link com.linguauniversalis.core.behavior.InventoryRules}：
     * 猫又 = 主手/副手 + 背包 8；阿拉克涅 = 主手/副手/附肢1/附肢2 + 背包 9），
     * 因此读档时先确定物种、再取容器即可拿到正确格数。
     */
    public GirlInventory girlInventory() {
        int wanted = Math.max(1, com.linguauniversalis.core.behavior.InventoryRules.totalSlots(profile()));
        if (this.girlInventory == null) {
            this.girlInventory = new GirlInventory(wanted);
        } else if (this.girlInventory.getContainerSize() != wanted) {
            this.girlInventory = this.girlInventory.resized(wanted, this.inventoryOverflow);
        }
        return this.girlInventory;
    }

    /** 下一 tick 把换容量溢出的物品掉在脚下。 */
    private void tickInventoryOverflow(ServerLevel server) {
        if (this.inventoryOverflow.isEmpty()) {
            return;
        }
        for (ItemStack stack : this.inventoryOverflow) {
            this.spawnAtLocation(server, stack);
        }
        this.inventoryOverflow.clear();
    }

    // ------------------------------------------------------------------ 物品栏行为（设计 §6）
    /** 快捷栏每格"被占用的起始 tick"（-1 = 空）：任何来源放进快捷栏的东西都在 5 秒后进背包。 */
    private final long[] hotbarFilledSince = new long[16];
    /** 进食冷却 / 咀嚼计时。 */
    private int eatCooldownTicks;
    private int chewTicks;
    /** 伙伴宝箱检查节流。 */
    private int chestCheckTicks;
    /** 正在跑腿去的箱子（null = 没在跑腿）。 */
    private net.minecraft.core.BlockPos chestErrandPos;
    /** 跑腿时限（tick）。 */
    private long chestErrandUntilTick = -1L;
    /** 跑腿途中下次重下寻路的 tick。 */
    private long chestErrandRepathTick = -1L;
    /** 白跑一趟之后的冷却截止 tick（-1 = 不在冷却）。 */
    private long chestErrandCooldownUntil = -1L;

    private int hotbarSlotCount() {
        return com.linguauniversalis.core.behavior.InventoryRules.hotbarSlots(profile());
    }

    private int totalSlotCount() {
        return com.linguauniversalis.core.behavior.InventoryRules.totalSlots(profile());
    }

    /** 背包区（不含快捷栏）是否还有空位。 */
    private boolean backpackHasSpace() {
        int hotbar = hotbarSlotCount();
        return girlInventory().hasFreeSlot(hotbar, totalSlotCount());
    }

    /** 不饿不捡食物、满了不捡（拾取门的规则见 InventoryBehaviorRules）。 */
    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        boolean isFood = stack.has(net.minecraft.core.component.DataComponents.FOOD);
        return com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldPickUp(
                isFood, girlState.satiety(), girlInventory().hasFreeSlot());
    }

    /**
     * 捡起物品：先进**快捷栏（手上）**，再顺位进背包（{@code addIntoRange} 按索引顺序填，
     * 而索引 0 起就是快捷栏）。掉在地上的那一份按剩余数量处理。
     */
    @Override
    protected void pickUpItem(ServerLevel level, net.minecraft.world.entity.item.ItemEntity entity) {
        ItemStack ground = entity.getItem();
        if (ground.isEmpty()) {
            return;
        }
        ItemStack leftover = girlInventory().addIntoRange(ground.copy(), 0, totalSlotCount());
        int moved = ground.getCount() - leftover.getCount();
        if (moved <= 0) {
            return;
        }
        if (leftover.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(leftover);
        }
        this.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F,
                ((this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    /** 维护快捷栏每格的占用计时（任何来源放进去的都算，不只她捡的）。 */
    private void tickHotbarTimestamps(ServerLevel server) {
        int hotbar = Math.min(hotbarSlotCount(), hotbarFilledSince.length);
        long now = server.getGameTime();
        for (int i = 0; i < hotbar; i++) {
            if (girlInventory().getItem(i).isEmpty()) {
                hotbarFilledSince[i] = -1L;
            } else if (hotbarFilledSince[i] < 0L) {
                hotbarFilledSince[i] = now;
            }
        }
    }

    /**
     * 快捷栏里的东西待够 {@link com.linguauniversalis.core.behavior.InventoryRules#LIFT_TO_BACKPACK_DELAY_TICKS}
     * tick 且有地方放 → 挪进背包（设计 §6"尽量保持快捷栏空闲"）。**不区分它是捡来的还是别人放进来的**，
     * 所以 GUI 里往她快捷栏塞的东西也会自己进背包。
     *
     * <p>两个例外：<b>交战中主手不动</b>（那是 {@link #tickWeapon} 管的武器位）；
     * <b>正在副手咀嚼时不动副手</b>（吃东西的位置）。
     */
    private void tickLiftToBackpack(ServerLevel server) {
        int hotbar = hotbarSlotCount();
        int total = totalSlotCount();
        if (hotbar <= 0 || hotbar >= total) {
            return;
        }
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        int offHand = com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(profile());
        boolean fighting = hasCombatTarget();
        long now = server.getGameTime();
        boolean room = girlInventory().hasFreeSlot(hotbar, total);
        for (int i = 0; i < Math.min(hotbar, hotbarFilledSince.length); i++) {
            if (i == main && fighting) {
                continue; // 打起来了：武器留在手上
            }
            if (i == offHand && chewTicks > 0) {
                continue; // 正在副手吃东西
            }
            if (hotbarFilledSince[i] < 0L) {
                continue;
            }
            if (!com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldLiftToBackpack(
                    hotbarFilledSince[i], now, room)) {
                continue;
            }
            if (girlInventory().moveIntoRange(i, hotbar, total)) {
                hotbarFilledSince[i] = -1L;
            }
        }
    }

    /**
     * 饿了就自己找吃的：**优先快捷栏**，其次从背包搬到副手槽；食物一律放**副手**食用
     * （设计 §6"食物一律放副手食用"）。咀嚼 {@link com.linguauniversalis.core.behavior.InventoryBehaviorRules#CHEW_TICKS}
     * tick 后按食物的营养值回饱食度。
     */
    private void tickEat(ServerLevel server) {
        int offHand = com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(profile());
        if (chewTicks > 0) {
            chewTicks--;
            if (chewTicks == 0) {
                finishEating(server, offHand);
            }
            return;
        }
        if (eatCooldownTicks > 0) {
            eatCooldownTicks--;
            return;
        }
        if (!com.linguauniversalis.core.behavior.InventoryBehaviorRules.isHungry(girlState.satiety())) {
            return;
        }
        int hotbar = hotbarSlotCount();
        int total = totalSlotCount();
        int slot = findFood(0, hotbar, offHand);
        if (slot < 0) {
            slot = findFood(hotbar, total, offHand);
            if (slot >= 0 && offHand >= 0) {
                // 背包里的食物 → 搬到副手槽（副手槽有非食物就先塞回背包）
                ItemStack occupied = girlInventory().getItem(offHand);
                if (!occupied.isEmpty() && !occupied.has(net.minecraft.core.component.DataComponents.FOOD)
                        && girlInventory().hasFreeSlot(hotbar, total)) {
                    girlInventory().moveIntoRange(offHand, hotbar, total);
                }
                if (girlInventory().getItem(offHand).isEmpty()) {
                    girlInventory().swapSlots(slot, offHand);
                }
                slot = offHand;
            }
        }
        if (slot < 0) {
            return; // 身上没有食物（伙伴宝箱那边会补货）
        }
        if (offHand >= 0 && slot != offHand) {
            girlInventory().swapSlots(slot, offHand);
        }
        chewTicks = com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEW_TICKS;
        eatCooldownTicks = com.linguauniversalis.core.behavior.InventoryBehaviorRules.EAT_COOLDOWN_TICKS;
        playEatAnimation(com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEW_TICKS);
    }

    /** 找食物（跳过副手槽自己，避免把正在吃的算进去）。 */
    private int findFood(int from, int to, int skipSlot) {
        for (int i = from; i < to; i++) {
            if (i == skipSlot) {
                continue;
            }
            if (girlInventory().getItem(i).has(net.minecraft.core.component.DataComponents.FOOD)) {
                return i;
            }
        }
        return -1;
    }

    /** 咀嚼结束：吃掉副手那份食物，按营养值回饱食度。 */
    private void finishEating(ServerLevel server, int offHand) {
        if (offHand < 0) {
            return;
        }
        ItemStack stack = girlInventory().getItem(offHand);
        net.minecraft.world.food.FoodProperties food =
                stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null) {
            return;
        }
        girlState.addSatiety(Math.max(1, food.nutrition()));
        this.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
        stack.shrink(1);
        if (stack.isEmpty()) {
            girlInventory().setItem(offHand, ItemStack.EMPTY);
        }
        girlInventory().setChanged();
    }

    /**
     * 武器：发现敌人时从背包/快捷栏挑**面板伤害高于自身近战**的武器拿到主手；
     * 手上的武器**耐久 ≤10%** 就放回背包换别的（没有就徒手）。
     */
    private void tickWeapon(ServerLevel server) {
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        if (main < 0) {
            return;
        }
        int hotbar = hotbarSlotCount();
        int total = totalSlotCount();
        ItemStack equipped = girlInventory().getItem(main);
        if (!equipped.isEmpty() && com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWornOut(
                equipped.getDamageValue(), equipped.getMaxDamage())) {
            if (girlInventory().moveIntoRange(main, hotbar, total)) {
                equipped = ItemStack.EMPTY;
            }
        }
        if (!hasCombatTarget() || !equipped.isEmpty()) {
            return;
        }
        double bare = profile().meleeDamage();
        int best = -1;
        double bestDamage = bare;
        for (int i = 0; i < total; i++) {
            if (i == main) {
                continue;
            }
            ItemStack candidate = girlInventory().getItem(i);
            if (candidate.isEmpty()) {
                continue;
            }
            double damage = panelAttackDamage(candidate);
            if (com.linguauniversalis.core.behavior.InventoryBehaviorRules.isWeaponUpgrade(damage, bestDamage)) {
                bestDamage = damage;
                best = i;
            }
        }
        if (best >= 0) {
            girlInventory().swapSlots(best, main);
        }
    }

    /** 物品的"面板伤害"（= 原版 1 点基础 + 该物品的攻击力修饰符）。 */
    private static double panelAttackDamage(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                .compute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 1.0,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    /** 当前近战伤害 = 自身近战与手上武器面板伤害取大者。 */
    private float meleeDamageWithWeapon() {
        float bare = profile().meleeDamage();
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        if (main < 0) {
            return bare;
        }
        ItemStack weapon = girlInventory().getItem(main);
        if (weapon.isEmpty()) {
            return bare;
        }
        return (float) Math.max(bare, panelAttackDamage(weapon));
    }

    /**
     * 与**伙伴宝箱**的联动（设计 §6"伙伴存入伙伴宝箱，就近存放、多只共用"）：
     * 饿且身上没吃的 → 去箱子取一份食物；背包满了（或快捷栏有闲着的东西）→ 去箱子存进去。
     *
     * <p><b>存取必须走到箱子 2 格内</b>（{@code CHEST_USE_RADIUS_BLOCKS}）：不够近就先"跑腿"——
     * 给自己一段时限走过去（期间跟随/游荡让路，见 {@link #isOnChestErrand}）。
     */
    private void tickChestStorage(ServerLevel server) {
        long now = server.getGameTime();
        // 1) 正在跑腿：继续靠近，到了就办事，超时就放弃
        if (chestErrandPos != null) {
            if (now > chestErrandUntilTick) {
                clearChestErrand();
                return;
            }
            if (!(server.getBlockEntity(chestErrandPos) instanceof CompanionChestBlockEntity chest)) {
                clearChestErrand();
                return;
            }
            if (withinChestReach(chest)) {
                if (!doChestBusiness(server, chest)) {
                    // 白跑一趟（箱子满了 / 其实没东西可放）→ 冷却一会儿，别来回跑
                    chestErrandCooldownUntil = now + com.linguauniversalis.core.behavior.InventoryBehaviorRules
                            .CHEST_IDLE_COOLDOWN_TICKS;
                }
                clearChestErrand();
            } else if (now >= chestErrandRepathTick) {
                BlockPos pos = chest.getBlockPos();
                this.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                chestErrandRepathTick = now + com.linguauniversalis.core.behavior.InventoryBehaviorRules
                        .CHEST_ERRAND_REPATH_TICKS;
            }
            return;
        }
        // 2) 定期看看要不要去箱子那儿
        if (++chestCheckTicks < com.linguauniversalis.core.behavior.InventoryBehaviorRules
                .CHEST_CHECK_INTERVAL_TICKS) {
            return;
        }
        chestCheckTicks = 0;
        CompanionChestBlockEntity chest = findNearbyCompanionChest(server);
        if (chest == null) {
            return;
        }
        if (withinChestReach(chest)) {
            // 已经在跟前：随时可以办（不设冷却，省得东西来了却要等）
            if (doChestBusiness(server, chest)) {
                chestErrandCooldownUntil = -1L;
            }
            return;
        }
        if (now < chestErrandCooldownUntil) {
            return; // 刚白跑过一趟，先别急着再去
        }
        // 太远 → 只在"真的有事情要做"时才跑这一趟（避免空跑造成的来回走动）
        boolean wantsFood = com.linguauniversalis.core.behavior.InventoryBehaviorRules
                .isHungry(girlState.satiety()) && !hasFoodInInventory();
        if (!com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldVisitChest(
                wantsFood, hasDepositableItems())) {
            return;
        }
        chestErrandPos = chest.getBlockPos();
        chestErrandUntilTick = now + com.linguauniversalis.core.behavior.InventoryBehaviorRules
                .CHEST_ERRAND_TIMEOUT_TICKS;
        chestErrandRepathTick = now;
    }

    /**
     * 有没有**真的存得进去**的东西。
     *
     * <p>过滤条件必须与 {@link #depositSlotInto} 完全一致：伙伴档、主手（战斗中的武器）与
     * 正在咀嚼的副手除外、**喜爱食物留着**、背包只有在满的时候才清。
     * 少判一条就会出现"决策要去、执行却不放"的来回跑（踩过一次）。
     */
    private boolean hasDepositableItems() {
        if (!girlState.isCompanion()) {
            return false;
        }
        int hotbar = hotbarSlotCount();
        int total = totalSlotCount();
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        int offHand = com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(profile());
        for (int i = 0; i < hotbar; i++) {
            if (i == main || (i == offHand && chewTicks > 0)) {
                continue;
            }
            ItemStack stack = girlInventory().getItem(i);
            if (!stack.isEmpty() && !isKeptItem(stack)) {
                return true; // 快捷栏里闲着的都能存
            }
        }
        if (backpackHasSpace()) {
            return false; // 背包没满就不清背包
        }
        for (int i = hotbar; i < total; i++) {
            ItemStack stack = girlInventory().getItem(i);
            if (!stack.isEmpty() && !isKeptItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /** 到箱子跟前要做的事：饿且没吃的就取一份；伙伴档就把东西存进去。返回 true = 确实做成了事。 */
    private boolean doChestBusiness(ServerLevel server, CompanionChestBlockEntity chest) {
        boolean didSomething = false;
        // 1) 取食：饿且身上没食物 → 从箱子里拿一份
        if (com.linguauniversalis.core.behavior.InventoryBehaviorRules.isHungry(girlState.satiety())
                && !hasFoodInInventory()) {
            ItemStack food = chest.takeOne(stack -> stack.has(net.minecraft.core.component.DataComponents.FOOD));
            if (food != null && !food.isEmpty()) {
                ItemStack leftover = girlInventory().addIntoRange(food, 0, totalSlotCount());
                if (!leftover.isEmpty()) {
                    chest.insert(leftover);
                }
                didSomething = true;
            }
        }
        if (!girlState.isCompanion()) {
            return didSomething; // 野生/友善不往箱子里存（那是"伙伴"的待遇）
        }
        // 2) 存东西：快捷栏里闲着的（主手/副手除外）总是收走；背包只有在满了才清
        int hotbar = hotbarSlotCount();
        int total = totalSlotCount();
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        int offHand = com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(profile());
        for (int i = 0; i < hotbar; i++) {
            if (i == main || (i == offHand && chewTicks > 0)) {
                continue; // 手上拿着的武器 / 正在吃的食物不往里塞
            }
            DepositResult result = depositSlotInto(chest, i);
            if (result == DepositResult.DEPOSITED) {
                didSomething = true;
            } else if (result == DepositResult.CHEST_FULL) {
                return didSomething;
            }
        }
        if (!com.linguauniversalis.core.behavior.InventoryBehaviorRules.shouldDepositToChest(
                girlState.isCompanion(), backpackHasSpace())) {
            return didSomething;
        }
        for (int i = hotbar; i < total; i++) {
            DepositResult result = depositSlotInto(chest, i);
            if (result == DepositResult.DEPOSITED) {
                didSomething = true;
            } else if (result == DepositResult.CHEST_FULL) {
                return didSomething;
            }
        }
        return didSomething;
    }

    /** 存一格的结果。 */
    private enum DepositResult {
        /** 确实存进去了一部分。 */
        DEPOSITED,
        /** 这格没东西可存（空的 / 她舍不得放的喜爱食物）。 */
        NOTHING_TO_DO,
        /** 箱子塞不下了。 */
        CHEST_FULL
    }

    /** 把某一格存进箱子（返回结果，供"这次有没有做成事"判断）。 */
    private DepositResult depositSlotInto(CompanionChestBlockEntity chest, int slot) {
        ItemStack stack = girlInventory().getItem(slot);
        if (stack.isEmpty() || isKeptItem(stack)) {
            return DepositResult.NOTHING_TO_DO;
        }
        ItemStack leftover = chest.insert(stack.copy());
        if (leftover.getCount() == stack.getCount()) {
            return DepositResult.CHEST_FULL; // 一点都塞不进
        }
        girlInventory().setItem(slot, leftover.isEmpty() ? ItemStack.EMPTY : leftover);
        return DepositResult.DEPOSITED;
    }

    /** 是否正在给箱子跑腿（跟随/游荡都要让路，免得跟寻路打架）。 */
    public boolean isOnChestErrand() {
        return chestErrandPos != null;
    }

    private void clearChestErrand() {
        chestErrandPos = null;
        chestErrandUntilTick = -1L;
        chestErrandRepathTick = -1L;
    }

    /** 是否已经在"存取距离"内（2 格）。 */
    private boolean withinChestReach(CompanionChestBlockEntity chest) {
        BlockPos pos = chest.getBlockPos();
        double dx = this.getX() - (pos.getX() + 0.5);
        double dy = this.getY() - (pos.getY() + 0.5);
        double dz = this.getZ() - (pos.getZ() + 0.5);
        return com.linguauniversalis.core.behavior.InventoryBehaviorRules
                .withinChestReach(dx * dx + dy * dy + dz * dz);
    }

    /** 她自己会留着的东西：喜爱食物（设计 §6"捡喜爱物"）。 */
    private boolean isKeptItem(ItemStack stack) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString();
        return profile().isLikedFood(itemId);
    }

    private boolean hasFoodInInventory() {
        for (int i = 0; i < totalSlotCount(); i++) {
            if (girlInventory().getItem(i).has(net.minecraft.core.component.DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    /** 8 格内最近的伙伴宝箱（就近存放）。 */
    private CompanionChestBlockEntity findNearbyCompanionChest(ServerLevel server) {
        int reach = (int) Math.ceil(
                com.linguauniversalis.core.behavior.InventoryBehaviorRules.CHEST_SEARCH_RADIUS_BLOCKS);
        BlockPos origin = this.blockPosition();
        CompanionChestBlockEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!server.getBlockState(candidate).is(LURegistries.CHEST_OF_COMPANIONS.get())) {
                        continue;
                    }
                    if (!(server.getBlockEntity(candidate) instanceof CompanionChestBlockEntity chest)) {
                        continue;
                    }
                    double distSq = candidate.distSqr(origin);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = chest;
                    }
                }
            }
        }
        return best;
    }

    /**
     * 手持初稿右键她：打开她的 GUI（快捷栏 + 背包）。
     *
     * <p>规则：<b>只有主手持初稿才生效</b>（副手不启用初稿的任何功能）。
     */
    public void openInventoryFor(ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (containerId, playerInventory, ignored) ->
                                new com.linguauniversalis.menu.GirlInventoryMenu(containerId, playerInventory, this),
                        net.minecraft.network.chat.Component.translatable(
                                "gui.lingua_universalis.girl_inventory", this.getDisplayName())),
                buffer -> {
                    buffer.writeVarInt(this.getId());
                    buffer.writeUtf(this.speciesId);
                });
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.strollGoal = new WaterAvoidingRandomStrollGoal(this, 1.0);
        this.strollGoalEnabled = true;
        this.goalSelector.addGoal(7, this.strollGoal);
    }

    /**
     * 随机散步目标按命令/状态启停（服务端每 tick 调用）。
     *
     * <p>关闭散步的情形：倒地、休眠、待机命令、"跟随且具备命令权"（此时由 tickFollow
     * 完全接管移动，避免与随机散步抢寻路导致原地打转）；其余（野生/友善/游荡命令）保持散步。
     */
    private void updateStrollGoal() {
        boolean wantStroll = !girlState.downed() && !girlState.dormant();
        if (wantStroll) {
            if (hasCombatTarget()) {
                wantStroll = false; // 交战中：由 tickCombat 的追击寻路接管，别让随机散步抢路径
            } else if (isOnChestErrand()) {
                wantStroll = false; // 去伙伴宝箱跑腿中：别让随机散步抢路径
            } else if (CommandMode.STANDBY.englishId.equals(obeyModeId)) {
                wantStroll = false;
            } else if (CommandMode.FOLLOW.englishId.equals(obeyModeId)
                    && girlState.boundPlayerUuid() != null
                    && CommandRules.canCommand(girlState, girlState.boundPlayerUuid())) {
                wantStroll = false; // 命令权内跟随：由 tickFollow 驱动，不走随机散步
            }
        }
        if (wantStroll == this.strollGoalEnabled || this.strollGoal == null) {
            return;
        }
        if (wantStroll) {
            this.goalSelector.addGoal(7, this.strollGoal);
        } else {
            this.goalSelector.removeGoal(this.strollGoal);
            this.getNavigation().stop(); // 打断刚起步的随机路径
        }
        this.strollGoalEnabled = wantStroll;
    }

    // ------------------------------------------------------------------ tick（战败/饱食/心情/休眠）
    @Override
    public void tick() {
        super.tick();
        // 连续离地 tick 计数（客户端也要算：动画控制器只在客户端跑）——
        // 用于过滤"刚进游戏那两 tick 还没落地"导致的 airborne 动画重置
        if (this.onGround()) {
            this.airborneTicks = 0;
        } else if (this.airborneTicks < Short.MAX_VALUE) {
            this.airborneTicks++;
        }
        // 配置文件每秒自动重读一次（调动画倍速等数值不用重启游戏）
        com.linguauniversalis.core.config.LuSettings.get().pollForChanges();
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        int day = (int) (server.getOverworldClockTime() / com.linguauniversalis.core.ModConstants.DAY_TICKS);

        // 已绑定（友善+）的魔物娘不再被自然刷新掉；"获得过一次好感"的个体同理（设计 §12 防养成中断）
        if (com.linguauniversalis.core.behavior.SpawnRules.everAffectionedNow(
                girlState.affection(), everAffectioned)) {
            this.everAffectioned = true;
        }
        if (com.linguauniversalis.core.behavior.SpawnRules.shouldBecomePersistent(
                everAffectioned, girlState.boundPlayerUuid() != null, isPersistenceRequired())) {
            this.setPersistenceRequired();
        }

        // 随机散步目标按命令/状态启停：跟随（有命令权）、待机、倒地、休眠时关闭
        updateStrollGoal();

        // 命令模式差异：待机 = 停步站定（不寻路/不横向移动）
        if (CommandMode.STANDBY.englishId.equals(obeyModeId)) {
            this.getNavigation().stop();
            net.minecraft.world.phys.Vec3 vel = this.getDeltaMovement();
            this.setDeltaMovement(0.0, vel.y, 0.0);
        }

        // 倒地：静止（可浮水）、推进锁血计时；饱食等自然衰减暂停
        if (girlState.downed()) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.isInWater() ? 0.05 : 0.0, 0.0);
            KnockdownRules.tickKnockdown(girlState);
        }

        // 命令模式差异：跟随 = 向绑玩家靠拢/就近传送（仅伙伴命令权生效）
        tickFollow(server);

        // 动画：主动跳跃（离地且向上运动 / AI 请求）→ 播一遍 main_jump，之后交给 main_airborne
        if (com.linguauniversalis.core.anim.AnimationRules.isActiveJump(
                this.onGround(), this.isInWater() || this.isInLava(), this.isFallFlying(),
                this.getDeltaMovement().y, this.isJumping())
                && (animJumpUntilTick < 0 || this.tickCount >= animJumpUntilTick)) {
            playJumpAnimation();
        }

        // 伪装形态：人形"无事"超过阈值 → 恢复猫形（伙伴档伪装习性失效）
        tickDisguise(server);

        // 社交习性（猫又）：偷鱼 / 偷村民绿宝石 / 睡醒赠礼 / 猫形陪睡 / 亡灵视野心情
        tickSocialHabits(server);

        // 待机看向：每 tick 面向身旁的绑玩家（连续驱动 LookControl，避免“看一眼就回正”）
        tickLookAtOwner(server);

        // 物种专属敌意：目标选择 + 近战攻击（低落随机攻击模板也在此驱动）
        tickCombat(server);

        // 移动速度档：每 tick 重算 walk / 最快（攻击或跟随且 >8 格 → 最快），变了才写属性
        tickMovementSpeed();

        // 换容量溢出的物品掉在脚下（正常不会发生：物种只在出生/读档确定）
        tickInventoryOverflow(server);

        // 物品栏行为（设计 §6）：手上捡的东西 5 秒后入包、饿了找吃的、拿武器、与伙伴宝箱存取
        tickHotbarTimestamps(server);
        tickLiftToBackpack(server);
        tickEat(server);
        tickWeapon(server);
        tickChestStorage(server);

        // 日切结算：活动不足扣心情、饱食过低当天扣好感与心情
        if (lastSettledDay != day) {
            if (lastSettledDay >= 0) {
                int activityLoss = activityClock.endOfDay(lastSettledDay);
                if (activityLoss > 0) {
                    girlState.addMood(-activityLoss);
                }
                if (satietyLowSeenThisDay) {
                    RelationshipRules.onSatietyLowDaily(girlState);
                    girlState.addMood(-1);
                }
            }
            lastSettledDay = day;
            satietyLowSeenThisDay = false;
        }
        if (girlState.isSatietyLow()) {
            satietyLowSeenThisDay = true;
        }

        // 饱食自然下降（休眠/倒地时冻结）
        if (!girlState.dormant() && !girlState.downed()) {
            satietyDecayTicks++;
            if (satietyDecayTicks >= com.linguauniversalis.core.ModConstants.SATIETY_DECAY_TICKS_PER_POINT) {
                satietyDecayTicks = 0;
                girlState.addSatiety(-1);
            }
        }

        // 饱食自愈：非倒地/非休眠/饱食>0/血量不满/受伤5s冷却内不生效/有攻击目标时不生效
        long nowTick = server.getGameTime();
        boolean regenAllowed = !girlState.downed() && !girlState.dormant()
                && girlState.satiety() > 0 && this.getHealth() < this.getMaxHealth()
                && (lastDamageTick < 0 || nowTick - lastDamageTick >= 100)
                && !hasCombatTarget();
        if (regenAllowed) {
            regenTicks++;
            if (regenTicks >= com.linguauniversalis.core.ModConstants.REGEN_CONSUME_EVERY_TICKS) {
                regenTicks = 0;
                girlState.addSatiety(-1);
                this.heal(this.getMaxHealth() * com.linguauniversalis.core.ModConstants.REGEN_HEAL_FRACTION);
            }
        } else {
            regenTicks = 0;
        }

        // 饥饿伤害（饱食≤0；倒地锁血期由伤害事件免疫，锁血结束仍可能饿死）
        if (HungerRules.starvationDamageActive(girlState)) {
            starvationTicks++;
            if (starvationTicks >= com.linguauniversalis.core.ModConstants.STARVATION_DAMAGE_EVERY_TICKS) {
                starvationTicks = 0;
                if (!girlState.isDownedImmune()) {
                    this.hurtServer(server, this.damageSources().starve(), 1.0f);
                }
            }
        } else {
            starvationTicks = 0;
        }

        // 心情"活动不足"采样：每分钟记录坐标
        if (!girlState.downed()) {
            activitySampleTicks++;
            if (activitySampleTicks >= com.linguauniversalis.core.ModConstants.ACTIVITY_SAMPLE_INTERVAL_TICKS) {
                activitySampleTicks = 0;
                activityClock.recordSample(day, this.blockPosition().getX(), this.blockPosition().getZ());
            }
        }

        // 休眠：伙伴双0 + 24h 无友好互动（每 20 tick 驱动一次时钟）
        if (!girlState.downed()) {
            dormancySecondTicks++;
            if (dormancySecondTicks >= 20) {
                dormancySecondTicks = 0;
                long nowMs = server.getGameTime() * 50L;
                dormancyClock.update(girlState, lastFriendlyGameDay == day, nowMs);
            }
        }

        // 表现输入同步（休眠/陪睡/动画窗口/命令模式）→ 客户端动画与猫形姿态
        syncVisualState();
    }

    // ------------------------------------------------------------------ 跟随（伙伴命令权）
    /**
     * "跟随 follow"命令的执行体：向绑玩家靠拢；距离过远或寻路受阻时就近传送。
     *
     * <p>生效前提（与 Shift+右键命令一致）：命令模式为 follow、绑玩家本人、伙伴档且好感 ≥100、
     * 非低落/休眠/倒地。其余情况保持当前 AI（待机=停步、游荡=散步）。
     */
    private void tickFollow(ServerLevel server) {
        if (isOnChestErrand()) {
            return; // 正去伙伴宝箱存取（短暂跑腿），这段时间由宝箱逻辑驱动移动
        }
        if (!CommandMode.FOLLOW.englishId.equals(obeyModeId)) {
            return;
        }
        if (girlState.downed() || girlState.dormant() || girlState.isDepressed()) {
            return; // 倒地/休眠/低落不受指挥
        }
        String ownerUuid = girlState.boundPlayerUuid();
        if (ownerUuid == null) {
            return; // 未绑定（野生/友善未绑定）：无跟随对象
        }
        if (!CommandRules.canCommand(girlState, ownerUuid)) {
            return; // 命令权不足（非伙伴或好感 <100）
        }
        ServerPlayer owner = resolveOwner(server, ownerUuid);
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != this.level()) {
            // 主人离线/死亡/在其他维度：停下等待（打断残留寻路），不传送
            if (this.getNavigation().isInProgress()) {
                this.getNavigation().stop();
            }
            return;
        }

        // 节流：每 10 tick 评估一次寻路/传送
        followEvalTicks++;
        if (followEvalTicks % 10 != 0) {
            return;
        }

        double distSq = this.distanceToSqr(owner);
        if (distSq <= FOLLOW_STOP_DIST_SQ) {
            // 已在主人身旁（≤6 格）：停步（面向由 tickLookAtOwner 每 tick 驱动）
            if (this.getNavigation().isInProgress()) {
                this.getNavigation().stop();
            }
            return;
        }

        // 过远（>32 格）→ 就近传送；传送失败则退化为寻路
        if (distSq > FOLLOW_TELEPORT_DIST_SQ && tryTeleportBeside(owner)) {
            return;
        }

        // 6~32 格：寻路靠近主人
        this.getNavigation().moveTo(owner, 1.0);
    }

    // ------------------------------------------------------------------ 待机面向（绑玩家）
    /**
     * 每 tick 让魔物娘看向身旁的绑玩家（若有且未倒地/休眠/低落）。
     *
     * <p>MC 的 LookControl 每 tick 都会把"想看的目标"清零，只有本 tick 再次 setLookAt 才会
     * 继续转视角——因此必须在每 tick 驱动，否则就会出现"快速看一眼随即回正"的抽搐。
     */
    /** 非绑定玩家也会被注视的半径（格；可用配置关闭）。 */
    private static final double NEARBY_PLAYER_LOOK_RANGE = 8.0;

    /**
     * 注视目标：绑定玩家优先（12 格内），否则看最近的玩家（8 格内，可配置关闭）。
     *
     * <p>用 {@code setLookAt(实体, ...)} 会同时驱动<b>偏航与俯仰</b>，所以头部跟随视角的
     * 俯仰分量也由此获得来源（渲染侧只负责把它叠加到 AllHead 的旋转上）。
     */
    private void tickLookAtOwner(ServerLevel server) {
        this.syncLookTargetId = -1;
        if (girlState.downed() || girlState.dormant() || girlState.isDepressed()) {
            return;
        }
        String ownerUuid = girlState.boundPlayerUuid();
        if (ownerUuid != null) {
            ServerPlayer owner = resolveOwner(server, ownerUuid);
            if (owner != null && !owner.isRemoved() && owner.isAlive() && owner.level() == this.level()
                    && this.distanceToSqr(owner) <= FOLLOW_LOOK_DIST_SQ) {
                this.getLookControl().setLookAt(owner, 20.0F, 40.0F);
                this.syncLookTargetId = owner.getId();
                return;
            }
        }
        if (!com.linguauniversalis.core.config.LuSettings.get().lookAtNearbyPlayer()) {
            return;
        }
        net.minecraft.world.entity.player.Player nearby =
                server.getNearestPlayer(this, NEARBY_PLAYER_LOOK_RANGE);
        if (nearby != null && nearby.isAlive() && !nearby.isSpectator()) {
            this.getLookControl().setLookAt(nearby, 20.0F, 40.0F);
            this.syncLookTargetId = nearby.getId();
        }
    }

    /** 按 UUID 解析在线 ServerPlayer；解析失败返回 null。 */
    private ServerPlayer resolveOwner(ServerLevel server, String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return null;
        }
        try {
            return server.getServer().getPlayerList().getPlayer(UUID.fromString(ownerUuid));
        } catch (IllegalArgumentException badUuid) {
            return null;
        }
    }

    /** 尝试在主人身旁 2~5 格内寻找可站立的落点并传送过去；返回是否成功。 */
    private boolean tryTeleportBeside(ServerPlayer owner) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double spread = 2.0 + this.random.nextDouble() * 3.0; // 2~5 格
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            int bx = owner.getBlockX() + (int) Math.round(Math.cos(angle) * spread);
            int bz = owner.getBlockZ() + (int) Math.round(Math.sin(angle) * spread);
            int by = owner.getBlockY();
            for (int dy = 0; dy >= -4; dy--) { // 从主人脚下往下找可站方块
                BlockPos spot = new BlockPos(bx, by + dy, bz);
                if (isStandableSpot(spot)) {
                    this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    this.getNavigation().stop();
                    this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
                    return true;
                }
            }
        }
        return false;
    }

    /** 落点是否可站：脚下有实体方块、自身包围盒无碰撞。 */
    private boolean isStandableSpot(BlockPos spot) {
        BlockState below = this.level().getBlockState(spot.below());
        VoxelShape shape = below.getCollisionShape(this.level(), spot.below());
        if (below.isAir() || shape.isEmpty()) {
            return false;
        }
        AABB box = this.getBoundingBox()
                .move(spot.getX() + 0.5 - this.getX(), spot.getY() - this.getY(), spot.getZ() + 0.5 - this.getZ());
        return this.level().noCollision(box);
    }

    // ------------------------------------------------------------------ 敌意/近战（Phase 4 物种专属行为）
    /** 实体是否为亡灵（用于猫又亡灵仇恨与伤害修正）。 */
    public static boolean isUndeadEntity(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton
                || entity instanceof WitherBoss || entity instanceof Phantom;
    }

    /**
     * 记录一次"被该生物攻击"（受击反击窗口内它可被反击），并<b>立即将其设为反击目标</b>
     * （即使攻击者位于巢心威胁范围之外——被惹即应敌视；完整"追击直至脱离视线"逻辑后续再接入）。
     */
    public void markProvoked(LivingEntity attacker, long gameTick) {
        this.provokedByUuid = attacker.getStringUUID();
        this.provokedTick = gameTick;
        if (this.level() instanceof ServerLevel server
                && attacker.isAlive() && !attacker.isRemoved() && attacker.level() == this.level()) {
            // 创造/旁观玩家不会激怒（同原版默认）
            if (attacker instanceof Player p && (p.isSpectator() || p.getAbilities().instabuild)) {
                return;
            }
            boolean boundOwner = attacker instanceof Player
                    && attacker.getStringUUID().equals(girlState.boundPlayerUuid());
            if (boundOwner) {
                return; // 友善/伙伴的绑玩家：只掉好感，不反击
            }
            this.combatTarget = attacker;
        }
    }

    /** 当前反击目标是否仍在被惹记忆窗口内（用于保留跨扫描框的反击目标）。 */
    private boolean isProvokedTargetStillValid(LivingEntity target, long nowTick) {
        return target != null && target.isAlive() && !target.isRemoved()
                && target.level() == this.level()
                && target.getStringUUID().equals(provokedByUuid)
                && provokedTick >= 0
                && (nowTick - provokedTick) <= com.linguauniversalis.core.ModConstants.PROVOKED_MEMORY_TICKS;
    }

    /** 低落模板击杀回调（击杀回心情窗口由此驱动）。 */
    public void lowMoodOnKill() {
        if (this.level() instanceof ServerLevel server) {
            this.lowMoodRecovery.onKill(server.getGameTime() * 50L);
        }
    }

    // ------------------------------------------------------------------ 巢心（领地巡游纲核心）
    /**
     * 是否属于需要巢心做圆心/狂暴判定的领地守卫纲。
     * 以<b>物种巢穴变量</b>（{@link SpeciesProfile#hasNest()}）为总开关：
     * 没有巢穴方块的物种（如猫又）永远不进入任何巢穴逻辑。
     */
    private boolean isNestTerritorial() {
        return profile().hasNest()
                && com.linguauniversalis.core.behavior.OrdoTemplates.of(profile())
                .guardsTerritoryAgainstPlayers();
    }

    /** 巢心方块存档存在性判定（方块 = 档案巢穴变量指定，如阿拉克涅 cubile_araneae）。 */
    private boolean isNestBlock(BlockPos pos) {
        if (!profile().hasNest()) {
            return false; // 无巢穴物种：任何方块都不算巢穴
        }
        String nestId = profile().nestBlockId();
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.parse(
                com.linguauniversalis.registry.LUConstants.MODID + ":" + nestId);
        net.minecraft.world.level.block.Block nest = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getValue(key);
        return nest != null && this.level().getBlockState(pos).getBlock() == nest;
    }

    /** 每 tick 巢心状态：认领附近巢心 → 评估狂暴/失效 → 被带离领地时尝试返回。 */
    private void tickNestState(ServerLevel server) {
        if (!isNestTerritorial()) {
            nestPos = null;
            enraged = false;
            return;
        }
        if (girlState.downed() || girlState.dormant()) {
            return;
        }

        // 尚无巢：周期性在附近（水平半径 8）寻找巢心方块并认领
        if (nestPos == null) {
            nestClaimTicks++;
            if (nestClaimTicks % 80 == 0) {
                nestPos = findNestNearby(8);
            }
        }

        boolean nestExists = nestPos != null && isNestBlock(nestPos);
        boolean nestChunkLoaded = nestPos == null || server.isLoaded(nestPos);
        boolean outsideTerritory = false;
        if (nestPos != null) {
            com.linguauniversalis.core.behavior.NestGuard guard =
                    com.linguauniversalis.core.behavior.NestGuard.fromProfile(
                            profile(), nestPos.getX(), nestPos.getY(), nestPos.getZ());
            outsideTerritory = !guard.inTerritory(this.getX(), this.getZ());
        }

        // 狂暴状态推进（领地巡游纲：伙伴永不狂暴；无巢狂暴按 rageWithoutNest 开关）
        boolean companion = girlState.isCompanion();
        this.enraged = com.linguauniversalis.core.behavior.NestGuard.updateEnrage(
                this.enraged, companion, this.rageWithoutNest,
                nestExists, nestChunkLoaded, outsideTerritory);

        // 外生息门·巢穴附近增益（设计 §13：生命恢复/力量/速度/抗性提升）
        tickNestBuff(server);

        // 被带离领地且未狂暴 → 尝试返回巢心（不打断命令/战斗/低落）
        if (this.enraged || nestPos == null || outsideTerritory == false) {
            return;
        }
        boolean commanded = CommandRules.canCommand(girlState,
                girlState.boundPlayerUuid() == null ? "" : girlState.boundPlayerUuid());
        if (commanded || combatTarget != null || girlState.isDepressed()) {
            return;
        }
        if (this.getNavigation().isDone()) {
            this.getNavigation().moveTo(nestPos.getX() + 0.5, nestPos.getY(), nestPos.getZ() + 0.5, 1.0);
        }
    }

    /**
     * 外生息门·巢穴附近增益（设计 §13）：在领地范围内周期性获得
     * 生命恢复/力量/速度/抗性提升；离开领地或没有巢心则不再续期。
     */
    private void tickNestBuff(ServerLevel server) {
        // 巢穴变量总开关：无巢穴物种（如猫又）即便属于外生息门也不获得"巢穴附近增益"
        if (!profile().hasNest()) {
            return;
        }
        if (!profile().featureFlag(
                com.linguauniversalis.core.behavior.TemplateKeys.FLAG_CLASSIS_NEST_BUFF, false)) {
            return;
        }
        if (nestPos == null || girlState.downed() || girlState.dormant()) {
            return;
        }
        double territory = com.linguauniversalis.core.behavior.OrdoTemplates.of(profile())
                .effectiveTerritory(profile());
        double dx = this.getX() - (nestPos.getX() + 0.5);
        double dz = this.getZ() - (nestPos.getZ() + 0.5);
        if (dx * dx + dz * dz > territory * territory) {
            return; // 离开领地：增益自然到期
        }
        long now = server.getGameTime();
        if (nestBuffNextTick > now) {
            return;
        }
        nestBuffNextTick = now + NEST_BUFF_REFRESH_TICKS;
        int duration = NEST_BUFF_DURATION_TICKS;
        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, duration, 0), this);
        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.STRENGTH, duration, 0), this);
        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SPEED, duration, 0), this);
        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, duration, 0), this);
    }

    // ------------------------------------------------------------------ 社交习性（设计 §12 猫又）
    /**
     * 猫又社交习性 tick：偷鱼、偷村民绿宝石、睡醒赠礼、猫形陪睡、亡灵视野心情。
     * 全部由物种变量/模板默认驱动（非猫又物种自动跳过，见 {@code SocialHabitRules}）。
     */
    private void tickSocialHabits(ServerLevel server) {
        long now = server.getGameTime();
        long day = this.level().getOverworldClockTime() / com.linguauniversalis.core.ModConstants.DAY_TICKS;
        if (girlState.downed() || girlState.dormant()) {
            return;
        }
        stealFish(server, now);
        tickStolenSnack(server, now);
        stealVillagerEmeralds(server, day);
        tickUndeadSightMood(day);
        tickOwnerSleep(server, now, day);
    }

    /** 偷鱼：仅游荡状态、5 分钟一次，只偷玩家背包（9–35 槽）里的鱼；偷后跑远吃掉、不囤积。 */
    private void stealFish(ServerLevel server, long now) {
        if (!com.linguauniversalis.core.behavior.SocialHabitRules.stealsFish(profile())) {
            return;
        }
        boolean wanderMode = obeyModeId == null
                || CommandMode.WANDER.englishId.equals(obeyModeId)
                || !CommandRules.canCommand(girlState, girlState.boundPlayerUuid() == null
                        ? "" : girlState.boundPlayerUuid());
        if (!wanderMode) {
            return;
        }
        long interval = com.linguauniversalis.core.behavior.SocialHabitRules
                .fishStealIntervalTicks(profile());
        double range = com.linguauniversalis.core.behavior.SocialHabitRules.FISH_STEAL_RANGE;
        java.util.List<net.minecraft.world.entity.player.Player> players =
                server.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                        this.getBoundingBox().inflate(range),
                        p -> p.isAlive() && !p.isSpectator());
        if (players.isEmpty()) {
            return;
        }
        if (!com.linguauniversalis.core.behavior.SocialHabitRules.canStealFish(
                true, true, now, lastFishStealTick, interval)) {
            return;
        }
        for (net.minecraft.world.entity.player.Player victim : players) {
            net.minecraft.world.item.ItemStack fish = takeFishFromBackpack(victim);
            if (fish.isEmpty()) {
                continue;
            }
            lastFishStealTick = now;
            pendingGift = true; // 偷窃成功 → 下一次玩家睡醒时赠礼
            tell(victim, "She sneaked a " + fish.getHoverName().getString() + " out of your bag!");
            fleeAndEat(server, victim, fish);
            return;
        }
    }

    /** 从玩家<b>背包</b>（9–35 槽，不含快捷栏/主副手/护甲）取出一条鱼；取不到返回空。 */
    private net.minecraft.world.item.ItemStack takeFishFromBackpack(
            net.minecraft.world.entity.player.Player victim) {
        net.minecraft.world.entity.player.Inventory inventory = victim.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!com.linguauniversalis.core.behavior.SocialHabitRules.isStealableSlot(slot)) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(net.minecraft.tags.ItemTags.FISHES)) {
                continue;
            }
            net.minecraft.world.item.ItemStack stolen = stack.copyWithCount(1);
            stack.shrink(1);
            return stolen; // 不囤积：直接"叼走"，随后在远处吃掉
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 偷到后跑开约 8 格（离开玩家视线方向）再"吃掉"（不囤积）。 */
    private void fleeAndEat(ServerLevel server, net.minecraft.world.entity.player.Player victim,
                            net.minecraft.world.item.ItemStack stolen) {
        net.minecraft.world.phys.Vec3 away = this.position().subtract(victim.position());
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(away.x, 0.0, away.z);
        net.minecraft.world.phys.Vec3 dir = flat.lengthSqr() < 1.0E-4
                ? new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0)
                : flat.normalize();
        double distance = com.linguauniversalis.core.behavior.SocialHabitRules.FLEE_DISTANCE;
        BlockPos fleeTo = BlockPos.containing(this.getX() + dir.x * distance, this.getY(),
                this.getZ() + dir.z * distance);
        this.getNavigation().moveTo(fleeTo.getX() + 0.5, fleeTo.getY(), fleeTo.getZ() + 0.5, 1.2);
        stealEatAtTick = server.getGameTime() + 100L; // 约 5 秒后吃掉
    }

    /** 到点后把叼走的食物"吃掉"（食物从不入她的背包 → 不囤积）。 */
    private void tickStolenSnack(ServerLevel server, long now) {
        if (stealEatAtTick < 0 || now < stealEatAtTick) {
            return;
        }
        stealEatAtTick = -1L;
        this.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 1.0f, 1.0f);
        playEatAnimation(14); // 偷吃 → main_eat 动画
    }

    /** 每天 50% 偷村民绿宝石 1–3 颗（绿宝石入"她的背包"占位：记录数量，Phase 3 接物品栏）。 */
    private void stealVillagerEmeralds(ServerLevel server, long day) {
        if (!com.linguauniversalis.core.behavior.SocialHabitRules.stealsEmeralds(profile())
                || lastEmeraldStealDay == day) {
            return;
        }
        double range = com.linguauniversalis.core.behavior.SocialHabitRules.VILLAGER_STEAL_RANGE;
        java.util.List<net.minecraft.world.entity.npc.villager.Villager> villagers =
                server.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        this.getBoundingBox().inflate(range), net.minecraft.world.entity.Entity::isAlive);
        if (villagers.isEmpty()) {
            return;
        }
        lastEmeraldStealDay = day;
        if (!com.linguauniversalis.core.behavior.SocialHabitRules.villageStealHappens(this.random.nextDouble())) {
            return;
        }
        int amount = com.linguauniversalis.core.behavior.SocialHabitRules.emeraldAmount(this.random.nextDouble());
        stolenEmeralds += amount;
        pendingGift = true;
        emeraldGiftArmed = true;
        // 偷窃属隐蔽行为：不向玩家通报（村民绿宝石进"她的背包"占位计数，Phase 3 接物品栏）
    }

    /** 亡灵视野：视野内出现亡灵生物每天一次 +1 心情（幽暗目默认开启）。 */
    private void tickUndeadSightMood(long day) {
        if (!com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMood(profile())) {
            return;
        }
        double range = com.linguauniversalis.core.behavior.SocialHabitRules.UNDEAD_SIGHT_RANGE;
        boolean visible = !this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(range),
                e -> e != this && e.isAlive() && isUndeadEntity(e) && this.hasLineOfSight(e)).isEmpty();
        if (com.linguauniversalis.core.behavior.SocialHabitRules.undeadSightMoodTrigger(
                visible, day, lastUndeadMoodDay)) {
            lastUndeadMoodDay = day;
            girlState.addMood(com.linguauniversalis.core.behavior.SocialHabitRules.UNDEAD_SIGHT_MOOD_GAIN);
        }
    }

    /** 绑玩家睡眠：伙伴以猫形态陪睡；玩家睡醒时赠送礼物（偷窃成功后）。 */
    private void tickOwnerSleep(ServerLevel server, long now, long day) {
        String ownerUuid = girlState.boundPlayerUuid();
        ServerPlayer owner = ownerUuid == null ? null : resolveOwner(server, ownerUuid);
        boolean sleeping = owner != null && owner.isSleeping();
        boolean justWoke = ownerWasSleeping && !sleeping;
        ownerWasSleeping = sleeping;

        // 猫形陪睡（伙伴、非待机、玩家在附近睡觉）
        if (com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())) {
            boolean standingBy = CommandMode.STANDBY.englishId.equals(obeyModeId);
            double distSq = owner == null ? Double.MAX_VALUE : this.distanceToSqr(owner);
            boolean sleepAsCat = owner != null && owner.isAlive()
                    && com.linguauniversalis.core.behavior.SocialHabitRules.shouldSleepAsCat(
                    girlState.isCompanion(), standingBy, sleeping, distSq,
                    com.linguauniversalis.core.behavior.SocialHabitRules.SLEEP_AS_CAT_RANGE);
            if (sleepAsCat) {
                setCatForm(true);
                sleepingAsCat = true;
            } else if (sleepingAsCat) {
                sleepingAsCat = false;
                if (owner != null) {
                    setCatForm(false); // 醒后恢复人形
                }
            }
        }

        // 睡醒赠礼：仅伙伴、且此前有成功偷窃
        if (justWoke && owner != null && girlState.isCompanion() && pendingGift) {
            String giftId = com.linguauniversalis.core.behavior.SocialHabitRules.pickGift(
                    emeraldGiftArmed,
                    com.linguauniversalis.core.loot.WeightedGiftTable.nekomataGiftTable(),
                    new java.util.Random(this.random.nextLong()));
            net.minecraft.world.item.Item giftItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(net.minecraft.resources.Identifier.parse(giftId));
            if (giftItem != null) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(giftItem);
                owner.getInventory().add(stack);
                tell(owner, "She left you a gift: " + stack.getHoverName().getString());
            }
            pendingGift = false;
            if (emeraldGiftArmed) {
                emeraldGiftArmed = false; // 绿宝石礼物一次性（需再偷才能再送）
                if (stolenEmeralds > 0) {
                    stolenEmeralds--;
                }
            }
        }
    }

    /** 在水平半径内找最近的一个巢心方块并返回坐标；找不到返回 null。 */
    private BlockPos findNestNearby(int radius) {
        int bx = this.getBlockX();
        int bz = this.getBlockZ();
        int by = this.getBlockY();
        BlockPos best = null;
        long bestDist = Long.MAX_VALUE;
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!isNestBlock(pos)) {
                        continue;
                    }
                    long d = (long) dx * dx + (long) dz * dz + (long) dy * dy;
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** 供外部（结构生成/命令）指定巢心坐标；无巢穴物种（如猫又）一律忽略。 */
    public void claimNest(BlockPos pos) {
        if (pos != null && profile().hasNest() && isNestBlock(pos)) {
            this.nestPos = pos.immutable();
        }
    }

    public BlockPos nestPos() {
        return nestPos;
    }

    public boolean isEnraged() {
        return enraged;
    }

    private void tickCombat(ServerLevel server) {
        // 巢心状态评估：认领/失效/狂暴（领地巡游纲）
        tickNestState(server);
        if (girlState.downed() || girlState.dormant()) {
            combatTarget = null;
            return;
        }
        long now = server.getGameTime();
        // 低落：击杀后窗口内把心情抬回 30
        if (girlState.isDepressed()) {
            lowMoodRecovery.update(girlState, now * 50L);
        }

        if (meleeCooldownTicks > 0) {
            meleeCooldownTicks--;
        }

        // 清除失效目标
        if (combatTarget != null && (!combatTarget.isAlive() || combatTarget.isRemoved()
                || combatTarget.level() != this.level())) {
            combatTarget = null;
        }

        // 目标重扫（10 tick 一次）：被惹反击目标在记忆窗口内不因重扫被丢弃（即使已在扫描框外）
        hostileScanTicks++;
        boolean rescanned = false;
        if (hostileScanTicks % com.linguauniversalis.core.ModConstants.HOSTILE_SCAN_EVERY_TICKS == 0) {
            rescanned = true;
            if (!isProvokedTargetStillValid(combatTarget, now)) {
                boolean commanded = CommandRules.canCommand(girlState,
                        girlState.boundPlayerUuid() == null ? "" : girlState.boundPlayerUuid());
                combatTarget = pickCombatTarget(server, now, !commanded);
            }
        }

        if (combatTarget == null) {
            tickBleed(server, now); // 目标消失后流血仍继续结算
            return;
        }

        // 物种专属能力：蛛网 / 蛛丝拉拽 / 暗影箭（按物种参数，冷却驱动）
        tickAbilities(server, now);
        tickBleed(server, now);

        double meleeSq = com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS
                * com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS;
        double dSq = this.distanceToSqr(combatTarget);
        boolean inMelee = dSq <= meleeSq;

        // 待机命令：站定不追击，仅近战自卫
        boolean standingBy = CommandMode.STANDBY.englishId.equals(obeyModeId);

        if (inMelee) {
            if (meleeCooldownTicks <= 0) {
                doMeleeAttack(server, combatTarget, now);
            }
            this.getLookControl().setLookAt(combatTarget, 20.0F, 40.0F);
        } else if (!girlState.isDepressed() && !standingBy) {
            // 低落模板不追远敌（只打近战范围内生物）；待机不追击
            this.getNavigation().moveTo(combatTarget, 1.0);
        } else if (rescanned && combatTarget != null) {
            // 低落/待机且目标已超出近战：放弃目标，改为继续随机游荡/站定
            combatTarget = null;
            this.getNavigation().stop();
        }
    }

    /** 在扫描半径内挑选满足敌意规则的最近目标；返回 null 表示无可攻击者。 */
    private LivingEntity pickCombatTarget(ServerLevel server, long now, boolean mayHunt) {
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        double scanRadius = com.linguauniversalis.core.behavior.HostilityRules.maxScanRadius(profile());
        // 威胁中心：有巢心则以巢心为圆心（领地巡游纲）；狂暴/无巢时以自身为中心大范围索敌
        BlockPos center = enraged ? null : nestPos;
        double extra = enraged ? 16.0 : scanRadius;
        AABB box = (center != null
                ? new AABB(center).inflate(extra + 1.0)
                : this.getBoundingBox().inflate(extra + 1.0));
        for (net.minecraft.world.entity.Entity candidate
                : this.level().getEntities(this, box,
                e -> e instanceof LivingEntity le && le.isAlive() && le != this)) {
            LivingEntity target = (LivingEntity) candidate;
            // 创造/旁观玩家不作为敌意目标（同原版默认）
            if (target instanceof Player p && (p.isSpectator() || p.getAbilities().instabuild)) {
                continue;
            }
            com.linguauniversalis.core.behavior.HostilityRules.TargetKind kind = classify(target);
            double selfSq = this.distanceToSqr(target);
            double nestSq = com.linguauniversalis.core.behavior.HostilityRules.NO_NEST;
            if (nestPos != null) {
                nestSq = distSqToNest(target);
            }
            boolean inMelee = selfSq <= com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS
                    * com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS;
            boolean boundOwner = kind == com.linguauniversalis.core.behavior.HostilityRules.TargetKind.PLAYER
                    && target.getStringUUID().equals(girlState.boundPlayerUuid());
            boolean provoked = target.getStringUUID().equals(provokedByUuid)
                    && provokedTick >= 0
                    && (now - provokedTick) <= com.linguauniversalis.core.ModConstants.PROVOKED_MEMORY_TICKS;

            // 同种族敌意按物种变量过滤（仅限主动索敌；被惹反击不受此豁免）
            // 场景 A：狂暴时是否攻击同种族；场景 B：巢穴威胁半径内是否攻击同种族。
            if (target instanceof MonsterGirlEntity other && speciesId.equals(other.speciesId())) {
                com.linguauniversalis.core.behavior.OrdoTemplates.OrdoTemplate ordo =
                        com.linguauniversalis.core.behavior.OrdoTemplates.of(profile());
                if (!provoked) {
                    if (enraged && !ordo.enragedAttacksOwnKind(profile())) {
                        continue; // 本物种狂暴不打同族（变量=false）
                    }
                    if (!enraged && !girlState.isDepressed() && nestPos != null
                            && nestSq <= nestThreatSq()
                            && !ordo.nestThreatAttacksOwnKind(profile())) {
                        continue; // 本物种守巢威胁内不打同族（变量=false）
                    }
                }
            }
            // 命令权内（伙伴待命/跟随中）不允许主动狩猎，仅保留反击目标
            boolean commanded = CommandRules.canCommand(girlState,
                    girlState.boundPlayerUuid() == null ? "" : girlState.boundPlayerUuid());
            if (commanded && !provoked) {
                continue;
            }
            if (!mayHunt && !provoked) {
                continue;
            }
            if (!com.linguauniversalis.core.behavior.HostilityRules.shouldTarget(
                    profile(), girlState, kind, nestSq, selfSq, inMelee, boundOwner, provoked, enraged)) {
                continue;
            }
            if (selfSq < bestDistSq) {
                bestDistSq = selfSq;
                best = target;
            }
        }
        return best;
    }

    /** 目标到巢心（方块中心）的距离平方。 */
    private double distSqToNest(LivingEntity target) {
        double dx = target.getX() - (nestPos.getX() + 0.5);
        double dy = target.getY() - (nestPos.getY() + 0.5);
        double dz = target.getZ() - (nestPos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    /** 巢穴威胁半径平方（同 HostilityRules 守卫判定用的 ordo.effectiveThreat）。 */
    private double nestThreatSq() {
        double threat = com.linguauniversalis.core.behavior.OrdoTemplates.of(profile())
                .effectiveThreat(profile());
        return threat * threat;
    }

    private com.linguauniversalis.core.behavior.HostilityRules.TargetKind classify(LivingEntity target) {
        if (target instanceof Player) {
            return com.linguauniversalis.core.behavior.HostilityRules.TargetKind.PLAYER;
        }
        if (isUndeadEntity(target)) {
            return com.linguauniversalis.core.behavior.HostilityRules.TargetKind.UNDEAD;
        }
        return com.linguauniversalis.core.behavior.HostilityRules.TargetKind.OTHER_MOB;
    }

    /** 近战攻击：基础近战伤害 × 物种数值；对亡灵按亡灵倍率修正。 */
    private void doMeleeAttack(ServerLevel server, LivingEntity target, long nowTick) {
        // 手上拿了武器就用武器的面板伤害（规则见 InventoryBehaviorRules）
        float damage = meleeDamageWithWeapon();
        if (damage <= 0f) {
            return;
        }
        if (isUndeadEntity(target) && profile().undeadDamageMultiplier() > 1f) {
            damage *= profile().undeadDamageMultiplier();
        }
        this.swing(InteractionHand.MAIN_HAND);
        // 武器磨损（耐久 ≤10% 时由 tickWeapon 放回背包换别的）
        int main = com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile());
        if (main >= 0) {
            ItemStack weapon = girlInventory().getItem(main);
            if (!weapon.isEmpty() && weapon.isDamageableItem()) {
                weapon.hurtAndBreak(1, this, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }
        // 主动攻击 → 现出人形（伪装失效；设计 §12）
        revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger.ATTACK, nowTick);
        target.hurtServer(server, this.damageSources().mobAttack(this), damage);
        meleeCooldownTicks = com.linguauniversalis.core.ModConstants.MELEE_COOLDOWN_TICKS;
        // 近战附毒（阿拉克涅毒牙：中毒 II / 8 秒）
        int poisonTicks = com.linguauniversalis.core.behavior.SpeciesAbilities.poisonTicks(profile());
        if (poisonTicks > 0 && target.isAlive()) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.POISON,
                    poisonTicks,
                    com.linguauniversalis.core.behavior.SpeciesAbilities.poisonAmplifier(profile())), this);
        }
        // 近战附流血（猫又绒科：命中刷新 10 秒并等级 +1，无上限）
        long bleedRefresh = com.linguauniversalis.core.behavior.SpeciesAbilities.bleedRefreshTicks(profile());
        if (bleedRefresh > 0 && target.isAlive()) {
            bleedOn.computeIfAbsent(target.getStringUUID(),
                            k -> new com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed())
                    .onHit(nowTick, bleedRefresh);
        }
        // 低落模板：击杀 → 记一次击杀（随后在低落 tick 中把心情抬回 30）
        if (girlState.isDepressed() && !target.isAlive() && !target.isRemoved()) {
            lowMoodRecovery.onKill(nowTick * 50L);
        }
    }

    // ------------------------------------------------------------------ 物种专属能力（设计 §11/§12）
    /**
     * 冷却驱动的物种能力：蛛网（减速+挖掘疲劳）、蛛丝拉拽（伤害+拉向自己）、暗影箭（远程魔法伤害）。
     * 参数为 0 的能力自动跳过（该物种没有该能力），因此新物种无需改此处代码。
     */
    private void tickAbilities(ServerLevel server, long now) {
        LivingEntity target = combatTarget;
        if (target == null || !target.isAlive()) {
            return;
        }
        double distSq = this.distanceToSqr(target);
        boolean inMelee = distSq <= com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS
                * com.linguauniversalis.core.ModConstants.MELEE_REACH_BLOCKS;

        // 发射弹道属"主动攻击" → 现出人形（伪装失效；设计 §12）
        boolean willFire = false;

        // 蛛网：发射蛛网弹道（命中后减速 + 挖掘疲劳；阿拉克涅 5 秒/次）
        long webCd = com.linguauniversalis.core.behavior.SpeciesAbilities.webCooldownTicks(profile());
        double webRange = com.linguauniversalis.core.behavior.SpeciesAbilities.webRange(profile());
        if (webCd > 0 && distSq <= webRange * webRange
                && abilityCooldowns.ready(ABILITY_WEB, now, webCd)) {
            abilityCooldowns.use(ABILITY_WEB, now);
            willFire = true;
            com.linguauniversalis.entity.LuBoltEntity.shootAt(server, this, target,
                    com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.WEB, 0f);
        }

        // 蛛丝拉拽：发射蛛丝弹道（命中造成物理弹道伤害并把目标拉向自己；近战距离内不浪费）
        long silkCd = com.linguauniversalis.core.behavior.SpeciesAbilities.silkCooldownTicks(profile());
        double silkRange = com.linguauniversalis.core.behavior.SpeciesAbilities.silkRange(profile());
        if (silkCd > 0 && !inMelee && distSq <= silkRange * silkRange
                && abilityCooldowns.ready(ABILITY_SILK, now, silkCd)) {
            abilityCooldowns.use(ABILITY_SILK, now);
            willFire = true;
            float silkDamage = com.linguauniversalis.core.behavior.SpeciesAbilities.silkDamage(profile());
            if (isUndeadEntity(target) && profile().undeadDamageMultiplier() > 1f) {
                silkDamage *= profile().undeadDamageMultiplier(); // 对亡灵全部攻击 ×倍率
            }
            com.linguauniversalis.entity.LuBoltEntity.shootAt(server, this, target,
                    com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SILK, silkDamage);
        }

        // 暗影箭：发射暗影弹道（命中造成魔法伤害，不按弹射物结算；猫又 10 秒/次）
        long boltCd = com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltCooldownTicks(profile());
        double boltMinRange = com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltMinRange(profile());
        if (boltCd > 0 && distSq >= boltMinRange * boltMinRange
                && abilityCooldowns.ready(ABILITY_SHADOW_BOLT, now, boltCd)) {
            abilityCooldowns.use(ABILITY_SHADOW_BOLT, now);
            willFire = true;
            float boltDamage = com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltDamage(profile());
            if (isUndeadEntity(target) && profile().undeadDamageMultiplier() > 1f) {
                boltDamage *= profile().undeadDamageMultiplier(); // 对亡灵全部攻击 ×倍率
            }
            com.linguauniversalis.entity.LuBoltEntity.shootAt(server, this, target,
                    com.linguauniversalis.core.behavior.SpeciesAbilities.BoltKind.SHADOW, boltDamage);
        }

        if (willFire) {
            revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger.ATTACK, now);
        }
    }

    /** 流血结算：每间隔造成一次等于层级的物理伤害；过期或目标消失则清理。 */
    private void tickBleed(ServerLevel server, long now) {
        if (bleedOn.isEmpty()) {
            return;
        }
        long interval = com.linguauniversalis.core.behavior.SpeciesAbilities.bleedTickIntervalTicks(profile());
        java.util.Iterator<java.util.Map.Entry<String,
                com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed>> it = bleedOn.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String,
                    com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed> e = it.next();
            com.linguauniversalis.core.behavior.SpeciesAbilities.Bleed bleed = e.getValue();
            if (bleed.expired(now)) {
                it.remove();
                continue;
            }
            net.minecraft.world.entity.Entity victim = server.getEntity(
                    java.util.UUID.fromString(e.getKey()));
            if (!(victim instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            int damage = bleed.pollDamage(now, interval);
            if (damage > 0) {
                living.hurtServer(server, this.damageSources().mobAttack(this), damage);
            }
        }
    }

    /**
     * 弹射物闪避（猫又绒科）：冷却就绪时免疫该次弹射物伤害并侧/后位移。
     *
     * @return true = 已闪避（调用方应取消该次伤害）
     */
    public boolean tryDodgeProjectile(ServerLevel server, long now) {
        long dodgeCd = com.linguauniversalis.core.behavior.SpeciesAbilities.dodgeCooldownTicks(profile());
        if (dodgeCd <= 0 || !abilityCooldowns.ready(ABILITY_DODGE, now, dodgeCd)) {
            return false;
        }
        abilityCooldowns.use(ABILITY_DODGE, now);
        dodgeInvulnUntilTick = now + com.linguauniversalis.core.behavior.SpeciesAbilities.dodgeInvulnTicks(profile());
        // 侧/后位移：沿自身朝向的侧后方施加冲量
        net.minecraft.world.phys.Vec3 look = this.getLookAngle();
        net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-look.z, 0.0, look.x);
        boolean left = this.getRandom().nextBoolean();
        net.minecraft.world.phys.Vec3 dash = side.scale(left ? 1.0 : -1.0)
                .add(look.scale(-0.6))
                .normalize()
                .scale(0.7);
        this.setDeltaMovement(dash.x, 0.25, dash.z);
        this.hurtMarked = true;
        return true;
    }

    /** 是否处于弹射物闪避免疫窗口内。 */
    public boolean isDodgeInvulnerable(long now) {
        return dodgeInvulnUntilTick >= 0 && now <= dodgeInvulnUntilTick;
    }


    // ------------------------------------------------------------------ 互动（摸头/投喂/命令/唤醒）
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            // 客户端镜像：26.2 对一次右键会先主手再副手各发一次 ServerboundInteractPacket，
            // 仅当 mobInteract 返回 Success 类结果（SUCCESS/CONSUME）才停止；原版 mob 的做法是
            // 在"本次点击会被本模组处理"时返回 CONSUME，从而只发一个交互包（见 Animal.mobInteract）。
            // 这里镜像服务端分支（只看手/潜行/物品，不依赖仅在服务端可靠的档位状态）：
            // 只要这次点按属于本模组会接管的交互，就返回 CONSUME，阻止引擎继续试副手/物品。
            return clientSideInteract(player, hand);
        }
        long day = this.level().getOverworldClockTime() / com.linguauniversalis.core.ModConstants.DAY_TICKS;
        String actor = player.getStringUUID();
        boolean bonded = actor.equals(girlState.boundPlayerUuid());
        ItemStack stack = player.getItemInHand(hand);
        boolean sneak = player.isShiftKeyDown();
        long nowTick = ((ServerLevel) this.level()).getGameTime();

        // 通用右键交互去抖兜底（每玩家 × 本个体）：极少数路径若仍被重复分发，
        // 0.1s 窗口内只放行一次；高频正常点击不受影响（窗口仅 2 tick）。
        if (!allowInteract(actor, nowTick)) {
            return InteractionResult.SUCCESS;
        }

        // Shift+右键（空手）：命令循环（仅伙伴 + 绑玩家 + 好感≥100 + 非低落/休眠）
        // 防重与所有右键行为一致：客户端 CONSUME（原生机制）保证一次点按只发一个包，
        // 服务端 allowInteract 0.1s 兜底；每次点按即切换一次，无额外 0.5s 窗口。
        if (sneak && stack.isEmpty() && CommandRules.canCommand(girlState, actor)) {
            cycleCommandMode();
            tell(player, "Command -> " + obeyModeId + " | stage=" + girlState.stageId()
                    + " aff=" + girlState.affection() + " mood=" + girlState.mood());
            return InteractionResult.SUCCESS;
        }

        // 初稿：打开魔物娘 GUI（快捷栏 + 背包）。设计 §6：**仅伙伴档开放**，
        // 且绑定过的个体只允许绑玩家打开（别人拿初稿翻不了她的包）。
        // 另：只有**主手**持初稿才启用（副手不启用初稿的任何功能）。
        if (stack.getItem() == LURegistries.FIRST_DRAFT.get()) {
            if (!com.linguauniversalis.item.FirstDraftItem.isEnabledInHand(hand)) {
                return InteractionResult.PASS;
            }
            if (!girlState.isCompanion()) {
                tell(player, "Only a companion opens her pack (bond her first).");
                return InteractionResult.SUCCESS;
            }
            String bound = girlState.boundPlayerUuid();
            if (bound != null && !bound.isEmpty() && !bound.equals(actor)) {
                tell(player, "She only opens her pack for her own companion.");
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                openInventoryFor(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }

        // 百晓镜：记录目击与观测条目，显示个体状态摘要
        if (stack.getItem() == LURegistries.SPECULUM_SCIENTIAE.get()) {
            ServerCodex.sight(actor, speciesId);
            ServerCodex.zoomObserve(actor, speciesId);
            var st = girlState;
            String boundText = "none";
            String ownerUuid = st.boundPlayerUuid();
            if (ownerUuid != null && !ownerUuid.isEmpty()) {
                ServerPlayer owner = resolveOwner((ServerLevel) this.level(), ownerUuid);
                boundText = owner != null
                        ? owner.getName().getString()
                        : "offline:" + (ownerUuid.length() > 8 ? ownerUuid.substring(0, 8) : ownerUuid);
            }
            tell(player, "[Observe] " + profile().zhName() + " (" + profile().latinName() + ")"
                    + " | stage=" + st.stageId() + " aff=" + st.affection()
                    + " mood=" + st.mood() + " sat=" + st.satiety()
                    + " hp=" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth())
                    + " | bound=" + boundText
                    + " | sighted=" + ServerCodex.sightedCount(actor));
            return InteractionResult.SUCCESS;
        }

        // 礼物盒：已封装 → 送礼（喜爱 +2/日）；未封装 → 提示先打包
        if (stack.getItem() == LURegistries.PRESENT_CASE.get()) {
            if (!stack.has(LURegistries.PRESENT_CONTENT.get())) {
                tell(player, "Pack the case first: hold it + gift in other hand, then right-click air.");
                return InteractionResult.SUCCESS;
            }
            if (!bonded || !(girlState.isCompanion()
                    || girlState.affection() >= com.linguauniversalis.core.ModConstants.FRIENDLY_MIN)) {
                tell(player, "Only her friend can gift her (she must be friendly+).");
                return InteractionResult.SUCCESS;
            }
            String itemId = stack.get(LURegistries.PRESENT_CONTENT.get());
            boolean favorite = profile().isLikedFood(itemId);
            InteractionRules.Outcome out = InteractionRules.gift(girlState, favorite,
                    actor, this.getStringUUID(), day, interactionTracker, true);
            if (out.applied()) {
                lastFriendlyGameDay = day;
                stack.shrink(1); // 盒子连同内容送出
                RelationshipRules.enforceCoherence(girlState);
                if (favorite) {
                    ServerCodex.likedInteraction(actor, speciesId);
                }
                tell(player, "Gift box +" + out.affection() + " aff -> " + girlState.stageId()
                        + " aff=" + girlState.affection() + (favorite ? " (she loves it!)" : ""));
                return InteractionResult.SUCCESS;
            }
            tell(player, "Gift reward already used today.");
            return InteractionResult.SUCCESS;
        }

        // 急救箱：仅对"倒地且锁血结束"的魔物娘有效
        if (stack.getItem() == LURegistries.FIRST_AID_KIT.get()) {
            if (KnockdownRules.canUseMedkit(girlState)) {
                float healed = KnockdownRules.applyMedkit(girlState, this.getMaxHealth());
                this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + healed));
                this.setNoGravity(false);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                tell(player, "First aid: +" + healed + " HP, she recovered.");
            } else {
                tell(player, girlState.downed()
                        ? "She is still under knockdown protection, wait a moment."
                        : "She is not downed.");
            }
            return InteractionResult.SUCCESS;
        }

        // 调试：移除工具 —— 手持右键瞬间移除该魔物娘（创造调试用）
        if (stack.getItem() == LURegistries.DEBUG_GIRL_REMOVER.get()) {
            this.discard();
            tell(player, "Debug: monster girl removed.");
            return InteractionResult.SUCCESS;
        }

        // 调试：好感道具 —— 手持右键一次 +10（好感之实）或 +1（好感之实·一），不设每日上限
        if (stack.getItem() == LURegistries.DEBUG_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get()) {
            int debugGain = stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get() ? 1 : 10;
            if (!bonded && girlState.boundPlayerUuid() != null) {
                tell(player, "She is bound to someone else; debug affection refused.");
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            int before = girlState.affection();
            girlState.addAffection(debugGain);
            lastFriendlyGameDay = day; // 视为"当天有互动"，避免冷落/休眠误判
            RelationshipRules.enforceCoherence(girlState);
            // 越 10 → 绑定为友善；越 100 → 永久伙伴（enforceCoherence 负责解锁）
            if (girlState.affection() >= com.linguauniversalis.core.ModConstants.FRIENDLY_MIN
                    && girlState.boundPlayerUuid() == null) {
                girlState.setBoundPlayerUuid(actor);
            }
            tell(player, "Debug affection: +" + (girlState.affection() - before) + " aff -> "
                    + girlState.stageId() + " aff=" + girlState.affection());
            return InteractionResult.SUCCESS;
        }

        // 送礼（潜行+右键 非食物、非模组工具物品；绑定且友善+ 生效；喜爱物 +2/日）
        if (sneak && !stack.isEmpty() && !stack.has(DataComponents.FOOD) && !isLUTool(stack)) {
            if (bonded && (girlState.isCompanion()
                    || girlState.affection() >= com.linguauniversalis.core.ModConstants.FRIENDLY_MIN)) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                boolean favorite = profile().isLikedFood(itemId);
                InteractionRules.Outcome out = InteractionRules.gift(girlState, favorite,
                        actor, this.getStringUUID(), day, interactionTracker, true);
                if (out.applied()) {
                    lastFriendlyGameDay = day;
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    RelationshipRules.enforceCoherence(girlState);
                    if (favorite) {
                        ServerCodex.likedInteraction(actor, speciesId);
                    }
                    tell(player, "Gift +" + out.affection() + " aff -> "
                            + girlState.stageId() + " aff=" + girlState.affection()
                            + (favorite ? " (she likes it!)" : ""));
                    return InteractionResult.SUCCESS;
                }
                tell(player, "Gift reward already used today (once/day).");
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // 持食物右键：投喂（吃不吃都 +1 好感/日，饿时回饱食）
        boolean isFood = stack.has(DataComponents.FOOD);
        if (isFood) {
            boolean favorite = profile().isLikedFood(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            FoodProperties food = stack.get(DataComponents.FOOD);
            int nutrition = food == null ? 0 : food.nutrition();
            InteractionRules.Outcome out = InteractionRules.feed(girlState, profile(), favorite, nutrition,
                    actor, this.getStringUUID(), day, interactionTracker, bonded);
            if (out.applied()) {
                lastFriendlyGameDay = day;
                int before = girlState.affection() - out.affection();
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                playEatAnimation(20); // 投喂 → main_eat 动画
                RelationshipRules.enforceCoherence(girlState);
                // 认主/升友善：好感首次跨过友善阈值且现在绑定的是这位玩家（含野生投喂自然认主）
                if (actor.equals(girlState.boundPlayerUuid()) && before < com.linguauniversalis.core.ModConstants.FRIENDLY_MIN
                        && girlState.affection() >= com.linguauniversalis.core.ModConstants.FRIENDLY_MIN) {
                    tell(player, "She now sees you as a friend! (friendly stage)");
                }
                if (RelationshipRules.tryPromoteToCompanion(girlState, actor.equals(girlState.boundPlayerUuid()) ? actor : null)) {
                    tell(player, "She became your Companion! stage=" + girlState.stageId());
                }
                if (favorite) {
                    ServerCodex.likedInteraction(actor, speciesId); // 图鉴解锁“喜好”
                }
                tell(player, "Feed +" + out.affection() + " aff (satiety +" + out.satiety()
                        + ") -> " + girlState.stageId() + " aff=" + girlState.affection());
                return InteractionResult.SUCCESS;
            }
            tell(player, "Already fed today (once/day).");
            return InteractionResult.SUCCESS;
        }

        // 空手右键（主、副手都必须为空）：唤醒休眠（绑玩家）或摸头（友善+）
        // 副手有物品时摸头不触发：若主手物品交互 PASS 而引擎改试副手，副手为空会再进一次
        if (!sneak && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            // 空手右键 → 现出人形（与摸头同一手势；设计 §12 猫又伪装）
            revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger.PET, nowTick);
            // 摸头动画（一次性混合动画；由 GeckoLib 触发并同步给客户端）
            playPetAnimation();
            if (bonded && girlState.dormant() && dormancyClock.wakeByBoundPlayer(girlState, true)) {
                tell(player, "You woke her up!");
                return InteractionResult.SUCCESS;
            }
            InteractionRules.Outcome out = InteractionRules.pet(girlState, actor, this.getStringUUID(),
                    day, interactionTracker, bonded);
            if (out.applied()) {
                lastFriendlyGameDay = day;
                tell(player, "Pet +" + out.affection() + " aff, +" + out.mood() + " mood -> "
                        + girlState.stageId() + " aff=" + girlState.affection() + " mood=" + girlState.mood());
                return InteractionResult.SUCCESS;
            }
            if (girlState.affection() < com.linguauniversalis.core.ModConstants.FRIENDLY_MIN
                    && !girlState.isCompanion()) {
                return InteractionResult.PASS; // 野生：不触发
            }
            tell(player, "Pet reward already used today.");
            return InteractionResult.SUCCESS;
        }

        // TODO 物品阶段：命帛/礼物盒/急救箱/誓约协议书/初稿/百晓镜 的右键行为
        return super.mobInteract(player, hand);
    }

    private void cycleCommandMode() {
        CommandMode[] modes = CommandMode.values();
        int idx = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].englishId.equals(obeyModeId)) {
                idx = i;
                break;
            }
        }
        obeyModeId = modes[(idx + 1) % modes.length].englishId;
    }

    /**
     * 客户端侧 mobInteract 镜像：只回答"这次点按是否由本模组接管"。
     * <p>26.2 客户端一次右键会对主手、副手各发一个 {@code ServerboundInteractPacket}，
     * 只有 mobInteract 返回 Success 类结果（SUCCESS / CONSUME）才停止尝试下一只手。
     * 原版 mob（如 {@code Animal.mobInteract}）在客户端返回 CONSUME 表示"这里会处理"，
     * 从而一次点按只发一个包。本模组据此镜像服务端分支：命中的手势返回 CONSUME，
     * 其余（原版物品交互等）返回 PASS 交给原版。
     * <p>注意：此处<b>不执行任何状态修改</b>，且不能读取仅在服务端可靠的档位/心情，
     * 因此对依赖档位的分支只按手势保守认领 —— 若服务端最终不消费（如野生摸头），
     * 只多一个空包、无副作用；若这里漏认领才会造成"一次点按两个包"的连点问题。
     */
    private InteractionResult clientSideInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean sneak = player.isShiftKeyDown();
        // 1) 投喂：持食物在手即由本模组处理（服务端 feed 分支不限档位，永远消费该手）
        if (stack.has(DataComponents.FOOD)) {
            return InteractionResult.CONSUME;
        }
        // 2) 本模组手持工具（初稿/百晓镜/礼物盒/急救箱/调试件）：右键一律由本模组接管
        Item it = stack.getItem();
        if (it == LURegistries.FIRST_DRAFT.get()) {
            // 初稿只在主手生效：副手不认领这次点按，让引擎继续按原版流程试主手
            return hand == InteractionHand.MAIN_HAND
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }
        if (it == LURegistries.SPECULUM_SCIENTIAE.get()
                || it == LURegistries.PRESENT_CASE.get()
                || it == LURegistries.FIRST_AID_KIT.get()
                || it == LURegistries.DEBUG_GIRL_REMOVER.get()
                || it == LURegistries.DEBUG_AFFECTION.get()
                || it == LURegistries.DEBUG_AFFECTION_1.get()) {
            return InteractionResult.CONSUME;
        }
        if (sneak) {
            // 3) Shift+右键：空手 → 命令循环手势；持非食物非工具物品 → 送礼手势。
            //    能否真正生效取决于服务端档位（canCommand/bonded），这里只认领手势，
            //    服务端判定失败时单包 PASS，无副作用。
            if (stack.isEmpty()) {
                return InteractionResult.CONSUME;
            }
            if (!stack.has(DataComponents.FOOD) && !isLUTool(stack)) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        // 4) 空手摸头/唤醒：两手皆空才属于本模组手势（任一手有物品则让引擎继续试那一手）
        if (stack.isEmpty() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            // 摸头动画同时在客户端本地触发一次：手感即时，且不依赖服务端触发包（GeckoLib 服务端
            // triggerAnim 会发包给跟踪中的客户端，单机/联机都该到，但客户端本地触发更稳更快）。
            // 这里不做任何状态修改，服务端仍会按档位/每日上限独立结算好感。
            playPetAnimation();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 通用右键交互去抖兜底（每玩家 × 本个体）：极少数路径若仍被重复分发，
     * 在窗口内只放行第一次，其余吞掉。主防重靠客户端 CONSUME（原生机制）。
     */
    private boolean allowInteract(String actorUuid, long nowTick) {
        Long last = lastInteractTick.get(actorUuid);
        if (last != null
                && nowTick - last < com.linguauniversalis.core.ModConstants.INTERACT_DEBOUNCE_TICKS) {
            return false;
        }
        lastInteractTick.put(actorUuid, nowTick);
        return true;
    }

    private void tell(Player player, String text) {
        player.sendSystemMessage(Component.literal("[Lingua Universalis] " + text));
    }

    // ------------------------------------------------------------------ 持久化（26.2 流式）
    private static final String TAG_HAS_NEST = "LUHasNest";
    private static final String TAG_NEST_X = "LUNestX";
    private static final String TAG_NEST_Y = "LUNestY";
    private static final String TAG_NEST_Z = "LUNestZ";
    private static final String TAG_RAGE_WITHOUT_NEST = "LURageWithoutNest";
    private static final String TAG_CAT_FORM = "LUCatForm";
    private static final String TAG_PENDING_GIFT = "LUPendingGift";
    private static final String TAG_EMERALD_GIFT = "LUEmeraldGift";
    private static final String TAG_STOLEN_EMERALDS = "LUStolenEmeralds";
    private static final String TAG_LAST_EMERALD_DAY = "LULastEmeraldDay";
    private static final String TAG_LAST_UNDEAD_MOOD_DAY = "LULastUndeadMoodDay";
    private static final String TAG_EVER_AFFECTIONED = "LUEverAffectioned";

    @Override
    public void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString(TAG_SPECIES, speciesId);
        out.putString(TAG_OBEY, obeyModeId);
        long currentDay = this.level().getOverworldClockTime() / com.linguauniversalis.core.ModConstants.DAY_TICKS;
        out.putString(TAG_LIMITER, interactionTracker.snapshotEncoded(currentDay));
        out.putString(TAG_STATE, Base64.getEncoder().encodeToString(GirlStatePersistence.encode(girlState)));
        out.putBoolean(TAG_HAS_NEST, nestPos != null);
        if (nestPos != null) {
            out.putInt(TAG_NEST_X, nestPos.getX());
            out.putInt(TAG_NEST_Y, nestPos.getY());
            out.putInt(TAG_NEST_Z, nestPos.getZ());
        }
        out.putBoolean(TAG_RAGE_WITHOUT_NEST, rageWithoutNest);
        out.putBoolean(TAG_CAT_FORM, isCatForm());
        out.putBoolean(TAG_PENDING_GIFT, pendingGift);
        out.putBoolean(TAG_EMERALD_GIFT, emeraldGiftArmed);
        out.putInt(TAG_STOLEN_EMERALDS, stolenEmeralds);
        out.putLong(TAG_LAST_EMERALD_DAY, lastEmeraldStealDay);
        out.putLong(TAG_LAST_UNDEAD_MOOD_DAY, lastUndeadMoodDay);
        out.putBoolean(TAG_EVER_AFFECTIONED, everAffectioned);
        // 快捷栏 + 背包（槽位索引 + 物品；格数由物种档案决定）
        girlInventory().save(out);
    }

    @Override
    public void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String loadedSpecies = in.getStringOr(TAG_SPECIES, DEFAULT_SPECIES);
        setSpeciesId(loadedSpecies);
        obeyModeId = in.getStringOr(TAG_OBEY, CommandMode.FOLLOW.englishId);
        interactionTracker.fromEncoded(in.getStringOr(TAG_LIMITER, ""));
        String stateB64 = in.getStringOr(TAG_STATE, "");
        if (!stateB64.isEmpty()) {
            try {
                this.girlState = GirlStatePersistence.decode(Base64.getDecoder().decode(stateB64));
            } catch (IllegalArgumentException malformed) {
                this.girlState = new MonsterGirlState();
            }
        }
        // 巢心仅对"有巢穴方块"的物种有效：无巢穴物种忽略存档中的巢心数据（防止旧档/改档残留）
        if (in.getBooleanOr(TAG_HAS_NEST, false) && profile().hasNest()) {
            int x = in.getIntOr(TAG_NEST_X, 0);
            int y = in.getIntOr(TAG_NEST_Y, 0);
            int z = in.getIntOr(TAG_NEST_Z, 0);
            this.nestPos = new BlockPos(x, y, z);
        } else {
            this.nestPos = null;
        }
        this.rageWithoutNest = in.getBooleanOr(TAG_RAGE_WITHOUT_NEST, true);
        // 伪装形态持久化（无伪装习性的物种一律人形；伙伴的"猫形陪睡"也属于该形态）
        boolean loadedCat = in.getBooleanOr(TAG_CAT_FORM, false)
                && com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile());
        this.getEntityData().set(DATA_CAT_FORM, loadedCat);
        this.pendingGift = in.getBooleanOr(TAG_PENDING_GIFT, false);
        this.emeraldGiftArmed = in.getBooleanOr(TAG_EMERALD_GIFT, false);
        this.stolenEmeralds = in.getIntOr(TAG_STOLEN_EMERALDS, 0);
        this.lastEmeraldStealDay = in.getLongOr(TAG_LAST_EMERALD_DAY, -1L);
        this.lastUndeadMoodDay = in.getLongOr(TAG_LAST_UNDEAD_MOOD_DAY, -1L);
        // 旧档无此标记时按当前好感兜底推断（好感 > 0 视为已获得过好感）
        this.everAffectioned = in.getBooleanOr(TAG_EVER_AFFECTIONED, girlState.affection() > 0);
        // 快捷栏 + 背包：必须在物种确定之后读（容器格数取自物种档案）
        girlInventory().load(in);
    }
}
