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
    /** 同步：当前变形形态 id（空串 = 本体形态；见 {@code core/species/DisguiseRegistry}）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_DISGUISE_FORM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** 同步：战斗模式 id（被动/主动；头顶状态牌与哨子切换用）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_COMBAT_MODE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** 同步：是否倒地（战败）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_DOWNED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /**
     * 同步：是否应播"悬空"动画（<b>服务端权威</b>）。
     *
     * <p>为什么必须同步而不是客户端自己算：非玩家实体的 {@code onGround} 客户端只在收到移动包时才更新，
     * 刚进游戏/静止不动的个体会一直是 false ⇒ 客户端"以为她在空中"，
     * 于是刚进游戏前几秒播悬浮空动画（这就是那个 bug）。
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_AIRBORNE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：是否正在睡觉（床或巢穴处）；动画据此播 main_sleep。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_SLEEPING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：是否伙伴档（只有伙伴才显示头顶状态牌）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_COMPANION =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 同步：绑定的玩家 UUID（空串 = 未绑定）。状态牌"只有主人看得到"靠它判定。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_OWNER_UUID =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** 同步：倒地锁血剩余秒数（0 = 锁血结束 = 濒死）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_DOWNED_SECONDS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** 同步：注视目标实体 id（-1 = 无）——渲染端据此让头部跟随目标。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_LOOK_TARGET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** 同步：当前命令模式 id（猫形坐姿等表现用）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_OBEY_MODE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /**
     * 同步：主手<b>可视物品</b>（客户端渲染在 {@code RightPaw} 下的定位组上）。
     *
     * <p>她的主手/副手是背包里的命名槽位（{@code main_hand} / {@code off_hand}，
     * 见 {@code core/behavior/InventoryRules}），不属于原版装备槽位体系，客户端无从得知；
     * 因此由服务端每 tick 把这两格同步成"渲染输入"（值不变时数据监视器不发包）。
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<ItemStack> DATA_MAIN_HAND_ITEM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    /** 同步：副手可视物品（客户端渲染在 {@code LeftPaw} 下的定位组上）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<ItemStack> DATA_OFF_HAND_ITEM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MonsterGirlEntity.class, net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);

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

    // ---------------------------------------------------------------- 巢心（领地巡游）
    /** 领地/威胁圆心的巢心方块坐标（null = 尚无巢）。 */
    private BlockPos nestPos;
    /** 是否"无巢狂暴"（领地巡游模板默认 true；刷怪蛋生成个体 = false，见 finalizeSpawn）。 */
    private boolean rageWithoutNest = true;
    /** 当前狂暴状态（每 tick 由 NestGuard 依据巢心评估）。 */
    private boolean enraged;
    /** 巢心认领扫描节流（无巢时周期性找附近巢心方块）。 */
    private int nestClaimTicks;
    /** 外生息·巢穴增益下次刷新 tick。 */
    private long nestBuffNextTick;
    /** 巢穴增益刷新间隔（tick，4 秒）与持续时长（tick，6 秒，略长于刷新间隔避免闪烁）。 */
    private static final long NEST_BUFF_REFRESH_TICKS = 80L;
    private static final int NEST_BUFF_DURATION_TICKS = 120;

    /** 连续离地 tick 数（客户端也维护；用于 airborne 动画的"稳定"判定）。 */
    private int airborneTicks;
    /** 服务端算好的"是否播悬空动画"（每 tick 更新后同步给客户端）。 */
    private boolean airborneVisual;

    /** 主控制器引用（构造后赋值；供其判定回调里动态改 {@code main_walk} 播放倍速）。 */
    private com.geckolib.animation.AnimationController<MonsterGirlEntity> mainController;


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
            // 悬空判定用服务端同步值：客户端自己的 onGround 在"刚进游戏 / 静止不动"时是脏的
            boolean airborne = girl.isAirborneVisual();
            boolean eating = girl.isEatAnimActive();
            // 睡觉：休眠 / 战败倒地 / 睡在床上 —— 一律先播 main_sleep 顶着
            boolean sleeping = girl.isDormantVisual() || girl.isDownedVisual() || girl.isSleepingVisual();
            boolean sitting = !sleeping && girl.isStandingByVisual();
            boolean moving = test.isMoving();
            String name = com.linguauniversalis.core.anim.AnimationRules.mainAnimation(
                    jumpAnim, airborne, eating, sleeping, sitting, moving);
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
        // 优先级：睡眠 > 尾巴 > 其他主要动画。
        // 睡着（含休眠/战败）时这一层改播 main_sleep —— 与主层同一套静态睡姿，
        // 于是尾巴被"写成睡姿并保持不动"，而不是继续摆（返回 STOP 停不住已在播的 loop）。
        registrar.add(toggledLoopController("tail",
                com.linguauniversalis.core.anim.AnimationRules.BLEND_TAIL_SWING,
                com.linguauniversalis.core.anim.AnimationRules.MAIN_SLEEP,
                girl -> com.linguauniversalis.core.anim.AnimationRules.shouldPlayTailSwing(
                        girl.isSleepingVisual() || girl.isDormantVisual() || girl.isDownedVisual())));

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

    /**
 * 两段常驻混合动画控制器：条件成立播 A，否则播 B。
 *
 * <p>为什么不用 {@code PlayState.STOP} 表示"这层不参与"：GeckoLib 里对已经在播的 loop 返回 STOP
 * 并不能可靠地把它停掉（实测睡眠时尾巴仍在摆）。改成"条件不成立时改播另一段动画"，
 * 由动画本身把骨骼写成想要的数值 —— 结果是确定的。
 */
private static com.geckolib.animation.AnimationController<MonsterGirlEntity> toggledLoopController(
        String controllerName, String animationWhenOn, String animationWhenOff,
        java.util.function.Predicate<MonsterGirlEntity> on) {
    ControllerRef ref = new ControllerRef();
    com.geckolib.animation.AnimationController<MonsterGirlEntity> controller =
            new com.geckolib.animation.AnimationController<MonsterGirlEntity>(
                    controllerName, test -> {
                        MonsterGirlEntity girl = test.animatable();
                        String name = on.test(girl) ? animationWhenOn : animationWhenOff;
                        ref.applySpeed(girl, name);
                        return test.setAndContinue(
                                com.geckolib.animation.RawAnimation.begin().thenLoop(name));
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
        builder.define(DATA_DORMANT, false);
        builder.define(DATA_SLEEPING_WITH_OWNER, false);
        builder.define(DATA_ANIM_JUMP, false);
        builder.define(DATA_ANIM_EAT, false);
        builder.define(DATA_OBEY_MODE, CommandMode.FOLLOW.englishId);
        builder.define(DATA_LOOK_TARGET, -1);
        builder.define(DATA_DISGUISE_FORM, "");
        builder.define(DATA_COMBAT_MODE, com.linguauniversalis.core.behavior.CombatModeRules.DEFAULT_MODE.id());
        builder.define(DATA_AIRBORNE, false);
        builder.define(DATA_SLEEPING, false);
        builder.define(DATA_COMPANION, false);
        builder.define(DATA_OWNER_UUID, "");
        builder.define(DATA_DOWNED, false);
        builder.define(DATA_DOWNED_SECONDS, 0);
        builder.define(DATA_MAIN_HAND_ITEM, ItemStack.EMPTY);
        builder.define(DATA_OFF_HAND_ITEM, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 客户端表现读取
    /** 客户端表现：注视目标实体 id（-1 = 没有目标）。 */
    public int lookTargetIdVisual() {
        return this.getEntityData().get(DATA_LOOK_TARGET);
    }

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

    /** 客户端表现：主手可视物品（定位组渲染输入，只读）。 */
    public ItemStack mainHandVisual() {
        return this.getEntityData().get(DATA_MAIN_HAND_ITEM);
    }

    /** 客户端表现：副手可视物品（定位组渲染输入，只读）。 */
    public ItemStack offHandVisual() {
        return this.getEntityData().get(DATA_OFF_HAND_ITEM);
    }

    /**
     * 读取手上命名槽位的物品并<b>复制</b>一份用于同步。
     *
     * <p>为什么要复制：{@code ItemStack} 在 26.2 <b>没有覆写 equals</b>，数据监视器用的
     * {@code ObjectUtils.notEqual} 实际是<b>引用比较</b>。若把背包里那个实例直接塞进去，
     * 之后进食扣数量之类原地修改不会让"引用"变化，客户端永远收不到更新；
     * 复制一份则每次都是新实例，再由 {@link #setHandVisual} 按内容比较决定要不要真的写。
     */
    private ItemStack handVisualCopy(InteractionHand hand) {
        SpeciesProfile profile = profile();
        int index = hand == InteractionHand.MAIN_HAND
                ? com.linguauniversalis.core.behavior.InventoryRules.mainHandIndex(profile)
                : com.linguauniversalis.core.behavior.InventoryRules.offHandIndex(profile);
        GirlInventory inventory = girlInventory();
        if (index < 0 || index >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.getItem(index);
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * 写入手上物品的同步值：<b>先按内容比较</b>，真的变了才写。
     *
     * <p>{@code ItemStack} 没有覆写 equals，直接每 tick {@code data.set(新副本)} 会被数据监视器
     * 判定为"每 tick 都变了"（引用不同）→ 每 tick 发包。这里用 {@link ItemStack#matches} 比较
     * 物品/数量/组件，没变就跳过。
     */
    private void setHandVisual(net.minecraft.network.syncher.EntityDataAccessor<ItemStack> key, ItemStack current) {
        net.minecraft.network.syncher.SynchedEntityData data = this.getEntityData();
        if (ItemStack.matches(data.get(key), current)) {
            return;
        }
        data.set(key, current);
    }

    // ------------------------------------------------------------------ 睡眠（床 / 巢穴）
    /**
     * 睡眠 tick：决定"这个时段她该不该睡、睡哪儿"，并把她带过去/让她躺下。
     *
     * <p>口径（规则层 {@link com.linguauniversalis.core.behavior.SleepRules}）：
     * <ul>
     *   <li>夜行性物种<b>白天睡</b>，非夜行性夜里睡；</li>
     *   <li>正在跟随玩家 / 倒地 / 低落 / 休眠 时不进入睡眠流程；</li>
     *   <li>有巢穴 ↔ 巢心 3 格内就地睡；无巢穴的伙伴 ↔ 走到<b>尺寸匹配</b>的魔物床睡；
     *       无巢穴的野生/友善 ↔ 不睡（没有能睡的地方）。</li>
     * </ul>
     */
    private void tickSleep(ServerLevel server) {
        // 被命令跟随玩家时不睡（跟主人走是优先事项）
        boolean following = CommandMode.FOLLOW.englishId.equals(obeyModeId)
                && girlState.boundPlayerUuid() != null
                && CommandRules.canCommand(girlState, girlState.boundPlayerUuid());
        boolean sleepTime = com.linguauniversalis.core.behavior.SleepRules.isSleepTime(
                server.getOverworldClockTime(),
                com.linguauniversalis.core.behavior.SleepRules.nocturnal(profile()));
        // 玩家命令优先：待机（站定）与跟随都不该自己走去睡觉（散步/睡眠/跑腿都让位于命令）
        boolean commandedStill = CommandRules.blocksAutonomousMovement(obeyModeId);
        boolean blocked = girlState.downed() || girlState.dormant() || girlState.isDepressed() || following
                || commandedStill || hasCombatTarget() || isOnChestErrand();

        if (blocked || !sleepTime) {
            if (sleeping) {
                wakeUp();
            }
            return;
        }

        com.linguauniversalis.core.behavior.SleepRules.Intent intent =
                com.linguauniversalis.core.behavior.SleepRules.intent(
                        true, profile().hasNest(), girlState.isCompanion());
        switch (intent) {
            case NEST -> tickSleepAtNest(server);
            case BED -> tickSleepInBed(server);
            case NONE -> {
                if (sleeping) {
                    wakeUp();
                }
            }
        }
    }

    /** 有巢物种：走到巢心 3 格内就地躺下（不需要床）。 */
    private void tickSleepAtNest(ServerLevel server) {
        if (nestPos == null) {
            if (sleeping) {
                wakeUp();
            }
            return;
        }
        double distSq = distSqToNest(this.position());
        if (!com.linguauniversalis.core.behavior.SleepRules.canSleepAtNest(distSq)) {
            // 还没到巢心 3 格：解除睡眠、走过去
            if (sleeping) {
                wakeUp();
            }
            this.getNavigation().moveTo(nestPos.getX() + 0.5, nestPos.getY(), nestPos.getZ() + 0.5, 1.0);
            return;
        }
        if (sleeping) {
            // 就地睡着：什么都不做（不锁速度、不每 tick 停导航）——
            // 她自己不会再主动走（散步/跟随/战斗都在下面被睡眠挡住），但玩家可以把睡着的她推来推去。
            return;
        }
        fallAsleep(false, nestPos);
    }

    /** 无巢伙伴：找一张尺寸匹配的魔物床，走过去躺下（一张床只睡一只）。 */
    private void tickSleepInBed(ServerLevel server) {
        if (sleeping) {
            // 已睡着：同理不锁死（可被推动）；床被破坏就起床
            if (!isSleepingInBed()) {
                wakeUp();
            }
            return;
        }
        BlockPos bed = sleepPos;
        if (bed == null || !(this.level().getBlockState(bed).getBlock()
                instanceof com.linguauniversalis.block.MonsterBedBlock bedBlock)
                || !com.linguauniversalis.core.behavior.SleepRules.bedMatchesSize(
                        com.linguauniversalis.core.behavior.SleepRules.sizeOf(profile()), bedBlock.sizeClass())
                || isBedClaimedByOther(server, bed)) {
            if (bedSearchTicks-- > 0) {
                return;
            }
            bedSearchTicks = com.linguauniversalis.core.behavior.SleepRules.BED_SEARCH_INTERVAL_TICKS;
            bed = findFreeBed(server);
            this.sleepPos = bed;
            if (bed == null) {
                return; // 附近没有合适的床：继续游荡
            }
        }
        // 躺下位置取**两格交界的中点**（不是某一半的中心，否则会偏床头/床尾）
        net.minecraft.world.level.block.state.BlockState bedState = this.level().getBlockState(bed);
        net.minecraft.core.Direction bedFacing = bedState.hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? bedState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                : null;
        boolean headHalf = bedState.hasProperty(com.linguauniversalis.block.MonsterBedBlock.PART)
                && bedState.getValue(com.linguauniversalis.block.MonsterBedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
        double[] centerOffset = com.linguauniversalis.core.behavior.SleepRules.bedCenterOffset(
                headHalf, bedFacing == null ? 0 : bedFacing.getStepX(),
                bedFacing == null ? 0 : bedFacing.getStepZ());
        double bedCenterX = bed.getX() + 0.5 + centerOffset[0];
        double bedCenterZ = bed.getZ() + 0.5 + centerOffset[1];
        double distSq = this.distanceToSqr(bedCenterX, bed.getY(), bedCenterZ);
        if (com.linguauniversalis.core.behavior.SleepRules.canSleepOnBed(distSq)) {
            this.getNavigation().stop();
            // 走到床边后"躺下"：位置用原版 startSleeping 同一套落点（床面 + 0.6875 格），
            // 让脚落在床面上（接地）；<b>不改碰撞箱</b>、不用 Pose.SLEEPING（那是玩家那套 0.2 格趴姿）。
            if (!sleeping) {
                this.setPos(bedCenterX, bed.getY() + com.linguauniversalis.core.behavior.SleepRules.BED_SURFACE_Y_OFFSET,
                        bedCenterZ);
                // 朝向与床头一致（原版床边躺下也是这个朝向）
                if (bedFacing != null) {
                    this.setYRot(bedFacing.toYRot());
                    this.yHeadRot = bedFacing.toYRot();
                    this.yBodyRot = bedFacing.toYRot();
                }
            }
            fallAsleep(true, bed);
        } else {
            this.getNavigation().moveTo(bedCenterX, bed.getY(), bedCenterZ, 1.0);
        }
    }

    /** 进入睡眠（床 = inBed；巢穴 = 就地）。 */
    private void fallAsleep(boolean inBed, BlockPos pos) {
        if (sleeping && java.util.Objects.equals(sleepPos, pos)) {
            return;
        }
        this.sleeping = true;
        this.sleepPos = pos;
        this.getNavigation().stop();
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        syncVisualState();
    }

    /** 附近找一张"尺寸匹配且没被别的个体占"的魔物床。 */
    private BlockPos findFreeBed(ServerLevel server) {
        com.linguauniversalis.core.behavior.SleepRules.SizeClass size =
                com.linguauniversalis.core.behavior.SleepRules.sizeOf(profile());
        double radius = com.linguauniversalis.core.behavior.SleepRules.BED_SEARCH_RADIUS_BLOCKS;
        int r = (int) Math.ceil(radius);
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-r, -3, -r), origin.offset(r, 3, r))) {
            if (!(server.getBlockState(candidate).getBlock()
                    instanceof com.linguauniversalis.block.MonsterBedBlock bedBlock)) {
                continue;
            }
            if (!com.linguauniversalis.core.behavior.SleepRules.bedMatchesSize(size, bedBlock.sizeClass())) {
                continue;
            }
            double distSq = candidate.distSqr(origin);
            if (distSq >= bestSq) {
                continue;
            }
            BlockPos immutable = candidate.immutable();
            if (isBedClaimedByOther(server, immutable)) {
                continue;
            }
            bestSq = distSq;
            best = immutable;
        }
        return best;
    }

    /** 这张床是否已经被别的个体占了（一张床只睡一只）。 */
    private boolean isBedClaimedByOther(ServerLevel server, BlockPos bed) {
        for (MonsterGirlEntity other : server.getEntitiesOfClass(MonsterGirlEntity.class,
                new net.minecraft.world.phys.AABB(bed).inflate(4.0))) {
            if (other != this && other.sleepPos() != null && other.sleepPos().distSqr(bed) <= 8.0) {
                return true;
            }
        }
        return false;
    }

    /** 服务端：把"表现输入"广播到客户端（仅变化时发包）。 */
    private void syncVisualState() {
        net.minecraft.network.syncher.SynchedEntityData data = this.getEntityData();
        data.set(DATA_DORMANT, girlState.dormant());
        data.set(DATA_SLEEPING_WITH_OWNER, sleepingAsCat);
        data.set(DATA_ANIM_JUMP, animJumpUntilTick >= 0 && this.tickCount < animJumpUntilTick);
        data.set(DATA_ANIM_EAT, animEatUntilTick >= 0 && this.tickCount < animEatUntilTick);
        data.set(DATA_OBEY_MODE, obeyModeId == null ? CommandMode.FOLLOW.englishId : obeyModeId);
        // 睡眠与"只有主人看得到的状态牌"
        data.set(DATA_AIRBORNE, airborneVisual);
        data.set(DATA_SLEEPING, sleeping);
        data.set(DATA_COMPANION, girlState.isCompanion());
        data.set(DATA_OWNER_UUID, girlState.boundPlayerUuid() == null ? "" : girlState.boundPlayerUuid());
        // 状态牌输入：战斗模式 / 倒地 / 倒地倒计时（秒）
        data.set(DATA_COMBAT_MODE, combatMode.id());
        data.set(DATA_DOWNED, girlState.downed());
        data.set(DATA_DOWNED_SECONDS,
                com.linguauniversalis.core.behavior.StatusPlateRules.secondsFromTicks(girlState.downedLockTicks()));
        // 手上物品 → 客户端定位组渲染（本方法只在服务端状态机里调用，客户端不会覆盖这两个值）
        setHandVisual(DATA_MAIN_HAND_ITEM, handVisualCopy(InteractionHand.MAIN_HAND));
        setHandVisual(DATA_OFF_HAND_ITEM, handVisualCopy(InteractionHand.OFF_HAND));
    }

    /** 当前变形形态 id（空串 = 本体形态）。 */
    public String disguiseFormId() {
        return this.getEntityData().get(DATA_DISGUISE_FORM);
    }

    /** 当前形态定义（未变形/未登记 → null）。 */
    public com.linguauniversalis.core.species.DisguiseForm disguiseForm() {
        return com.linguauniversalis.core.species.DisguiseRegistry.get(disguiseFormId());
    }

    /** 当前是否处于变形形态（任意形态，不再限于"猫形"）。 */
    public boolean isDisguised() {
        return !disguiseFormId().isEmpty();
    }

    /** 兼容旧名：是否处于猫形伪装（= 处于 nekomata_cat 形态）。 */
    public boolean isCatForm() {
        return com.linguauniversalis.core.species.DisguiseRegistry.NEKOMATA_CAT.equals(disguiseFormId());
    }

    /** 切换到指定变形形态（空/未登记 = 回到本体形态），并刷新碰撞箱。 */
    public void setDisguiseForm(String formId) {
        String wanted = formId == null ? "" : formId.trim();
        if (!wanted.isEmpty() && !com.linguauniversalis.core.species.DisguiseRegistry.contains(wanted)) {
            wanted = ""; // 未登记的形态：宁可不变形，也不要画成空气
        }
        if (wanted.equals(disguiseFormId())) {
            return;
        }
        this.getEntityData().set(DATA_DISGUISE_FORM, wanted);
        this.refreshDimensions();
    }

    /** 进入/退出该物种登记的变形形态。 */
    public void setDisguised(boolean disguised) {
        com.linguauniversalis.core.species.DisguiseForm form =
                com.linguauniversalis.core.behavior.DisguiseRules.formOf(profile());
        setDisguiseForm(disguised && form != null ? form.id() : "");
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
        setDisguised(false);
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
        com.linguauniversalis.core.species.DisguiseForm form = disguiseForm();
        if (form != null) {
            // 变形形态的碰撞箱由形态定义给出（新物种加形态不用改这里）
            return net.minecraft.world.entity.EntityDimensions.scalable((float) form.width(), (float) form.height())
                    .withEyeHeight((float) form.eyeHeight());
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
        if (!com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())) {
            return;
        }
        if (!com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(girlState.isCompanion())) {
            // 伙伴档：伪装习性失效。若还停在猫形（例如在猫形状态下被誓约），
            // 直接恢复本体 —— 否则她会永远卡在猫形（revealHumanForm 对伙伴档不生效）。
            // 唯一例外：正陪玩家睡觉的猫形（醒来时才还原）。
            if (isDisguised() && !isSleepingWithOwnerVisual()) {
                setDisguised(false);
            }
            return;
        }
        if (isDisguised() || lastRevealTick < 0) {
            return;
        }
        long revert = com.linguauniversalis.core.behavior.DisguiseRules.revertTicks(profile());
        if (com.linguauniversalis.core.behavior.DisguiseRules.shouldRevert(
                server.getGameTime(), lastRevealTick, revert)) {
            setDisguised(true);
        }
    }

    // ------------------------------------------------------------------ 强制状态（调试棒）
    /** 被"状态切换棒"强制的状态（服务端权威，随存档保存）；NORMAL = 不强制。 */
    private com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState forcedState =
            com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState.NORMAL;

    /** 当前的强制状态。 */
    public com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState forcedState() {
        return forcedState;
    }

    /**
     * 设置强制状态（调试棒用）；返回切换后的状态。
     *
     * <p>立即生效一次，之后由 {@link #tickForcedState} 每 tick 重新施加 ⇒ 休眠时钟、锁血倒计时都改不动它。
     */
    public com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState setForcedState(
            com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState state) {
        this.forcedState = state == null
                ? com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState.NORMAL
                : state;
        applyForcedState();
        syncVisualState();
        return this.forcedState;
    }

    /** 把强制状态施加到实体上（一次；"强制正常/自然"都会清休眠与倒地）。 */
    private void applyForcedState() {
        switch (forcedState) {
            case DORMANT -> {
                if (girlState.downed()) {
                    girlState.setDowned(false);
                }
                wakeUp();
                girlState.setDormant(true);
            }
            case DOWNED -> {
                if (girlState.dormant()) {
                    girlState.setDormant(false);
                }
                wakeUp();
                setupForcedKnockdown();
            }
            case FORCED_NORMAL, NORMAL -> {
                // 强制正常 = 每 tick 清一次；自然 = 只在切换时清一次（之后交给自然逻辑）
                if (girlState.downed()) {
                    girlState.setDowned(false);
                }
                if (girlState.dormant()) {
                    girlState.setDormant(false);
                }
            }
        }
    }

    /** 强制战败：倒地 + 满额锁血 + 至少 1 血（免得被当成已死）。 */
    private void setupForcedKnockdown() {
        if (!girlState.downed()) {
            com.linguauniversalis.core.rule.KnockdownRules.beginKnockdown(girlState);
        }
        girlState.setDownedLockTicks(com.linguauniversalis.core.ModConstants.KNOCKDOWN_LOCK_TICKS);
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
        if (this.getHealth() < 1.0F) {
            this.setHealth(1.0F);
        }
    }

    /**
     * 每 tick 重新施加强制状态（"强制切换的状态会一直保持"）。
     *
     * <p>放在休眠时钟与锁血倒计时之后执行，所以自然流转改不动它。
     */
    private void tickForcedState(ServerLevel server) {
        if (forcedState.isNatural()) {
            return; // 自然档：不持续施加（切过来时已经清过一次）
        }
        applyForcedState();
    }

    // ------------------------------------------------------------------ 睡眠（床 / 巢穴）
    /** 当前是否在睡（服务端权威）。 */
    private boolean sleeping;
    /** 睡处坐标（床或巢穴旁的就地位置）；未睡为 null。 */
    private BlockPos sleepPos;
    /** 找床重算计时（tick）。 */
    private int bedSearchTicks;
    /** 上一 tick 的好感值（-1 = 尚未初始化）——用于"好感变化时冒爱心/愤怒粒子"。 */
    private int lastAffectionSeen = -1;
    /**
     * 待机锚点：下"待机"命令时记下她当时的位置。
     *
     * <p>设计汇总 §4「伙伴取食」：待命时饿了会离开待命点去伙伴宝箱取食**再回来** ——
     * 锚点就是"待命点"，取食/被推走之后她自己走回去重新坐下。
     */
    private BlockPos standbyAnchor;
    /** 待命回位的重算寻路计时（tick）。 */
    private long standbyRepathTick = -1L;

    /** 是否正在睡觉（服务端权威）。 */
    public boolean isSleeping() {
        return sleeping;
    }

    /** 客户端表现：是否应播悬空动画（服务端权威，见 {@link #DATA_AIRBORNE}）。 */
    public boolean isAirborneVisual() {
        return this.getEntityData().get(DATA_AIRBORNE);
    }

    /** 客户端表现：是否正在睡觉（动画用）。 */
    public boolean isSleepingVisual() {
        return this.getEntityData().get(DATA_SLEEPING);
    }

    /** 客户端表现：是否伙伴档（头顶状态牌只对伙伴显示）。 */
    public boolean isCompanionVisual() {
        return this.getEntityData().get(DATA_COMPANION);
    }

    /** 客户端表现：绑定玩家 UUID（空串 = 未绑定）。 */
    public String ownerUuidVisual() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    /** 是否睡在床上（区别于在巢穴旁就地睡）；床的右键叫醒用它判定。 */
    public boolean isSleepingInBed() {
        return sleeping && sleepPos != null && this.level() != null
                && this.level().getBlockState(sleepPos).getBlock() instanceof com.linguauniversalis.block.MonsterBedBlock;
    }

    /** 睡处坐标（未睡 → null）。 */
    public BlockPos sleepPos() {
        return sleepPos;
    }

    /** 起床（同时停止寻路）。 */
    public void wakeUp() {
        if (!sleeping) {
            return;
        }
        this.sleeping = false;
        this.sleepPos = null;
        this.getNavigation().stop();
        // 起床后立刻往前挪出床（原版村民起床是走开；这里至少别卡在床面中央）
        net.minecraft.core.Direction facing = this.getDirection();
        this.setPos(this.getX() + facing.getStepX() * 0.6, this.getY(), this.getZ() + facing.getStepZ() * 0.6);
        syncVisualState();
    }

    /**
     * 强制"脸朝自己该朝的方向"：头朝向拉回身体朝向、俯仰归零。
     *
     * <p>用于睡觉 / 休眠 / 战败三态 —— 这时候她不该盯着玩家，也不该被原版 {@code LookControl}
     * 带着去盯别的生物。每 tick 在 tick 末尾执行一次，覆盖掉 AI 期间产生的任何视线目标。
     */
    private void freezeFacing() {
        this.setYHeadRot(this.yBodyRot);
        this.setXRot(0.0F);
        this.yHeadRotO = this.yBodyRot;
        this.xRotO = 0.0F;
    }

    // ------------------------------------------------------------------ 战斗模式（被动/主动）
    /** 战斗模式（服务端权威；客户端读同步值画状态牌）。 */
    private com.linguauniversalis.core.behavior.CombatModeRules.CombatMode combatMode =
            com.linguauniversalis.core.behavior.CombatModeRules.DEFAULT_MODE;

    /** 当前战斗模式（服务端权威值）。 */
    public com.linguauniversalis.core.behavior.CombatModeRules.CombatMode combatMode() {
        return combatMode;
    }

    /** 客户端表现：当前战斗模式（读同步值）。 */
    public com.linguauniversalis.core.behavior.CombatModeRules.CombatMode combatModeVisual() {
        return com.linguauniversalis.core.behavior.CombatModeRules.CombatMode.byId(
                this.getEntityData().get(DATA_COMBAT_MODE));
    }

    /** 设置战斗模式（同步客户端）。被动 = 只挨打后反击；主动 = 帮主人打架。 */
    public void setCombatMode(com.linguauniversalis.core.behavior.CombatModeRules.CombatMode mode) {
        com.linguauniversalis.core.behavior.CombatModeRules.CombatMode wanted =
                mode == null ? com.linguauniversalis.core.behavior.CombatModeRules.DEFAULT_MODE : mode;
        if (wanted == combatMode) {
            return;
        }
        this.combatMode = wanted;
        this.getEntityData().set(DATA_COMBAT_MODE, wanted.id());
        if (wanted == com.linguauniversalis.core.behavior.CombatModeRules.CombatMode.PASSIVE
                && combatTarget != null
                && this.level() instanceof ServerLevel server
                && !isProvokedTargetStillValid(combatTarget, server.getGameTime())) {
            // 切回被动：立刻放弃"帮主人打"的目标（自己被打的目标仍按被惹记忆保留）
            combatTarget = null;
        }
    }

    /** 切换被动 ↔ 主动，返回切换后的模式。 */
    public com.linguauniversalis.core.behavior.CombatModeRules.CombatMode toggleCombatMode() {
        setCombatMode(com.linguauniversalis.core.behavior.CombatModeRules.toggle(combatMode));
        return combatMode;
    }

    /** 客户端表现：是否倒地（战败）。 */
    public boolean isDownedVisual() {
        return this.getEntityData().get(DATA_DOWNED);
    }

    /** 客户端表现：倒地锁血剩余秒数（0 = 锁血结束 = 濒死）。 */
    public int downedSecondsVisual() {
        return this.getEntityData().get(DATA_DOWNED_SECONDS);
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
                || stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get()
                || stack.getItem() == LURegistries.DEBUG_NEGATIVE_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_BIG_NEGATIVE_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_STATE_STICK.get()
                || stack.getItem() == LURegistries.DEBUG_FAMINE_FRUIT.get()
                || stack.getItem() == LURegistries.DEBUG_SATIETY_FRUIT.get();
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
        // 领地巡游"无巢狂暴"：模板默认 true；刷怪蛋生成的个体 = false（避免出生即狂暴）
        this.rageWithoutNest = !isEggSpawnReason(reason)
                && com.linguauniversalis.core.behavior.OrdoTemplates.of(profile())
                        .ragesWithoutNest(profile());
        applySpeciesMovementSpeed();
        // 伪装形态：有伪装习性的物种在野生/友善档默认以伪装形态出现（伙伴档失效）
        if (com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(profile())
                && com.linguauniversalis.core.behavior.DisguiseRules.disguisesAtStage(girlState.isCompanion())) {
            setDisguised(true);
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
    /**
     * 这趟跑腿是否为"饿了去取食"。
     *
     * <p>设计汇总 §4：<b>待命时饿了会离开待命点去最近的伙伴宝箱取食再回来</b> ——
     * 所以命令虽然压过"自主移动"，但"饿了取食"是它的<b>明确例外</b>。
     */
    private boolean chestErrandForFood;
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
            // 玩家命令优先：中途改成待机/跟随 → 放弃这趟；但"饿了去取食"是命令的明确例外
            // （设计汇总 §4：待命时饿了会离开待命点去伙伴宝箱取食）
            if (CommandRules.blocksAutonomousMovement(obeyModeId) && !chestErrandForFood) {
                clearChestErrand();
                this.getNavigation().stop();
                return;
            }
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
        // 玩家命令优先（待机站定 / 跟随主人）：只有"饿了去取食"这一种跑腿被允许
        // —— 设计汇总 §4「待命时饿了会离开待命点去最近的伙伴宝箱取食再回来」
        // —— 已经站在箱子跟前的"原地存取"在上面就返回了，不受这条影响
        if (CommandRules.blocksAutonomousMovement(obeyModeId) && !wantsFood) {
            return;
        }
        chestErrandForFood = wantsFood;
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
        chestErrandForFood = false;
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
                        this.guiTitle()),
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
        // 看人 / 四处张望：直接用原版宠物那两个目标（狼、猫同款），不再由实体代码强行死盯玩家
        this.goalSelector.addGoal(8, new com.linguauniversalis.entity.GirlLookGoals.LookAtPlayer(this));
        this.goalSelector.addGoal(9, new com.linguauniversalis.entity.GirlLookGoals.RandomLookAround(this));
    }

    /**
     * 现在是否该"看人/四处张望"。
     *
     * <p>倒地、休眠、低落三种状态下她不该盯着人看；其余时候由原版
     * {@code LookAtPlayerGoal} / {@code RandomLookAroundGoal} 自己决定（见 {@link com.linguauniversalis.entity.GirlLookGoals}）。
     */
    /** 已经被我们追加过"避让猫系魔物娘"目标的生物（弱引用，避免卸载后泄漏）。 */
    private final java.util.Map<net.minecraft.world.entity.Mob, Boolean> catAwareMobs = new java.util.WeakHashMap<>();
    /** 猫系特性扫描计时。 */
    private int catTraitScanTicks;
    /** {@code #lingua_universalis:scared_of_cats} —— 会被猫系魔物娘赶走的生物类型（数据包可扩展）。 */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> SCARED_OF_CATS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.Identifier.parse(
                            com.linguauniversalis.core.behavior.CatTraitRules.SCARED_OF_CATS_TAG));

    /**
     * 猫系特性：把附近的苦力怕 / 幻翼（以及数据包标进 {@code #lingua_universalis:scared_of_cats} 的生物）赶走。
     *
     * <p>做法是给这些生物**追加一个 {@code AvoidEntityGoal}**（避让"猫系魔物娘"），
     * 让逃跑成为它自己的 AI 行为（会寻路、绕障碍），而不是我们每 tick 推它。
     * 每个生物只加一次（弱引用表去重）。
     */
    private void tickCatTraits(ServerLevel server) {
        if (!com.linguauniversalis.core.behavior.CatTraitRules.hasCatTraits(profile())) {
            return;
        }
        if (++catTraitScanTicks < com.linguauniversalis.core.behavior.CatTraitRules.SCAN_INTERVAL_TICKS) {
            return;
        }
        catTraitScanTicks = 0;
        double radius = com.linguauniversalis.core.behavior.CatTraitRules.SCARE_RADIUS_BLOCKS;
        for (net.minecraft.world.entity.PathfinderMob mob : server.getEntitiesOfClass(
                net.minecraft.world.entity.PathfinderMob.class,
                this.getBoundingBox().inflate(radius),
                candidate -> candidate != this && candidate.isAlive()
                        && candidate.getType().builtInRegistryHolder().is(SCARED_OF_CATS)
                        // 苦力怕保持**原版完全不动**（任何额外目标都会干扰它"贴脸爆炸"的原版手感），
                        // 只在它的原版逻辑里识别猫系魔物娘这件事另行处理。
                        && !(candidate instanceof net.minecraft.world.entity.monster.Creeper))) {
            // 说明：这里**不再排除苦力怕**。原版苦力怕的"躲猫"由 Mixin 挂在它自己的
            // registerGoals 上，一旦原版内部结构变动（Mixin 静默失效），苦力怕就不怕猫又了；
            // 直接在这里一并挂目标可以保证"怕猫又"这件事一定成立（重复挂目标也无害）。
            if (catAwareMobs.containsKey(mob)) {
                continue;
            }
            mob.goalSelector.addGoal(com.linguauniversalis.core.behavior.CatTraitRules.AUGMENT_GOAL_PRIORITY,
                    new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(mob, MonsterGirlEntity.class,
                            (float) com.linguauniversalis.core.behavior.CatTraitRules.SCARE_RADIUS_BLOCKS,
                            com.linguauniversalis.core.behavior.CatTraitRules.FLEE_WALK_SPEED,
                            com.linguauniversalis.core.behavior.CatTraitRules.FLEE_SPRINT_SPEED,
                            entity -> entity instanceof MonsterGirlEntity girl
                                    && com.linguauniversalis.core.behavior.CatTraitRules.hasCatTraits(girl.profile())));
            catAwareMobs.put(mob, Boolean.TRUE);
        }
    }



    /**
     * 待命回位：待命点记在 {@link #standbyAnchor}，她因"饿了取食"或被推走之后自己走回去。
     *
     * <p>不打断的情形（这时不该回位）：倒地/休眠/睡觉/交战/原地进食/正在取食跑腿、
     * 或玩家已经换成别的命令（锚点会被清掉）。
     */
    private void tickStandbyReturn(ServerLevel server) {
        if (standbyAnchor == null) {
            return;
        }
        if (!CommandMode.STANDBY.englishId.equals(obeyModeId)) {
            this.standbyAnchor = null;
            return;
        }
        boolean blocked = girlState.downed() || girlState.dormant() || girlState.isDepressed() || sleeping
                || hasCombatTarget() || isOnChestErrand() || chewTicks > 0;
        double centerX = standbyAnchor.getX() + 0.5;
        double centerY = standbyAnchor.getY();
        double centerZ = standbyAnchor.getZ() + 0.5;
        if (!CommandRules.shouldReturnToStandby(true, blocked, this.distanceToSqr(centerX, centerY, centerZ))) {
            return; // 不需要回位（或在容差内 / 有更优先的事）
        }
        long now = server.getGameTime();
        if (now < standbyRepathTick) {
            return;
        }
        standbyRepathTick = now + 20L;
        this.getNavigation().moveTo(centerX, centerY, centerZ, 1.0);
    }

    /**
     * 好感变化反馈：**增加**冒爱心、**减少**冒村民愤怒云（服务端发包，周围玩家都看得到）。
     *
     * <p>规则层 {@link com.linguauniversalis.core.behavior.AffectionParticleRules}（纯规则，有断言）。
     * 与"环境粒子发射器"是两套：那个是按物种变量持续冒，这里是事件驱动的一次性爆发。
     */
    private void tickAffectionParticles(ServerLevel server) {
        int current = girlState.affection();
        if (lastAffectionSeen < 0) {
            lastAffectionSeen = current;
            return;
        }
        if (current == lastAffectionSeen) {
            return;
        }
        String particleId = com.linguauniversalis.core.behavior.AffectionParticleRules.particleFor(
                lastAffectionSeen, current);
        lastAffectionSeen = current;
        if (particleId == null) {
            return;
        }
        net.minecraft.core.particles.ParticleOptions options = resolveParticle(particleId);
        if (options == null) {
            return;
        }
        double r = com.linguauniversalis.core.behavior.AffectionParticleRules.BURST_RADIUS;
        server.sendParticles(options, this.getX(),
                this.getY() + com.linguauniversalis.core.behavior.AffectionParticleRules.BURST_Y_OFFSET,
                this.getZ(), com.linguauniversalis.core.behavior.AffectionParticleRules.BURST_COUNT,
                r, r, r, com.linguauniversalis.core.behavior.AffectionParticleRules.BURST_SPEED);
    }

    /**
     * 环境粒子发射器（客户端）：按物种变量（{@code species.ambientParticle*}）在周围冒粒子。
     *
     * <p>数据驱动：粒子 id / 触发状态 / 间隔 / 颗数 / 半径 / 高度全在物种档案里，代码只执行。
     * 用途举例：猫又掉毛、阿拉克涅滴蛛丝、心情低落冒黑雾、战败冒烟……
     */
    private void tickAmbientParticles() {
        com.linguauniversalis.core.behavior.AmbientParticleRules.Spec spec =
                com.linguauniversalis.core.behavior.AmbientParticleRules.specOf(profile());
        if (spec == null) {
            return;
        }
        boolean dormant = isDormantVisual();
        boolean downed = isDownedVisual();
        boolean depressed = girlState.isDepressed();
        boolean aggressive = combatModeVisual()
                == com.linguauniversalis.core.behavior.CombatModeRules.CombatMode.AGGRESSIVE;
        if (!com.linguauniversalis.core.behavior.AmbientParticleRules.stateMatches(
                spec.when(), dormant, downed, depressed, aggressive)) {
            return;
        }
        long gameTime = this.level().getGameTime();
        if (!com.linguauniversalis.core.behavior.AmbientParticleRules.dueAtTick(
                gameTime, this.tickCount, spec.intervalTicks())) {
            return;
        }
        net.minecraft.core.particles.ParticleOptions options = resolveParticle(spec.particleId());
        if (options == null) {
            return;
        }
        net.minecraft.util.RandomSource random = this.level().getRandom();
        for (int i = 0; i < spec.count(); i++) {
            double dx = (random.nextDouble() - 0.5) * 2.0 * spec.radius();
            double dz = (random.nextDouble() - 0.5) * 2.0 * spec.radius();
            double dy = spec.yOffset() + random.nextDouble() * 0.4 - 0.2;
            this.level().addParticle(options, this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                    0.0, 0.0, 0.0);
        }
    }

    /** 粒子 id → 粒子参数对象（未注册/不是可喷的粒子 → null，静默跳过）。 */
    private static net.minecraft.core.particles.ParticleOptions resolveParticle(String particleId) {
        net.minecraft.resources.Identifier id;
        try {
            id = net.minecraft.resources.Identifier.parse(particleId);
        } catch (RuntimeException malformed) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(id)
                .map(net.minecraft.core.Holder::value)
                .filter(type -> type instanceof net.minecraft.core.particles.ParticleOptions)
                .map(type -> (net.minecraft.core.particles.ParticleOptions) type)
                .orElse(null);
    }

    public boolean wantsToLookAround() {
        // 进食/战斗/睡眠/战败/低落时都不盯玩家（规则层见 AttentionRules，有断言）
        return com.linguauniversalis.core.behavior.AttentionRules.shouldLookAtPlayers(
                girlState.downed(), girlState.dormant(), girlState.isDepressed(),
                isEatAnimActive(), hasCombatTarget(), sleeping);
    }

    /** 随机张望的启停（战斗/进食/睡眠/倒地时不四处看，避免边打边甩头）。 */
    public boolean wantsToGlanceAround() {
        return com.linguauniversalis.core.behavior.AttentionRules.shouldGlanceAround(
                girlState.downed(), girlState.dormant(), isEatAnimActive(), hasCombatTarget(), sleeping);
    }

    /**
     * 随机散步目标按命令/状态启停（服务端每 tick 调用）。
     *
     * <p>关闭散步的情形：倒地、休眠、待机命令、"跟随且具备命令权"（此时由 tickFollow
     * 完全接管移动，避免与随机散步抢寻路导致原地打转）；其余（野生/友善/游荡命令）保持散步。
     */
    private void updateStrollGoal() {
        boolean wantStroll = !girlState.downed() && !girlState.dormant() && !sleeping;
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
        // 连续离地 tick 计数：只作为服务端判定"真的悬空"的稳定门槛使用
        // （客户端不再自己算悬空动画，改用同步的 DATA_AIRBORNE —— 见 isAirborneVisual）
        if (this.onGround()) {
            this.airborneTicks = 0;
        } else if (this.airborneTicks < Short.MAX_VALUE) {
            this.airborneTicks++;
        }
        // 注视目标同步（客户端据此让头部追随）：只同步"活着的攻击目标/被看的实体"
        if (!this.level().isClientSide()) {
            net.minecraft.world.entity.LivingEntity look = this.getTarget();
            int id = look != null && look.isAlive() ? look.getId() : -1;
            if (this.getEntityData().get(DATA_LOOK_TARGET) != id) {
                this.getEntityData().set(DATA_LOOK_TARGET, id);
            }
        }
        // 配置文件每秒自动重读一次（调动画倍速等数值不用重启游戏）
        com.linguauniversalis.core.config.LuSettings.get().pollForChanges();
        if (this.level().isClientSide()) {
            tickAmbientParticles();
            return;
        }
        // 悬空动画判定：服务端权威（稳定门槛见 AnimationRules.shouldShowAirborne），由 syncVisualState 同步
        this.airborneVisual = com.linguauniversalis.core.anim.AnimationRules.shouldShowAirborne(
                com.linguauniversalis.core.anim.AnimationRules.isAirborne(
                        this.onGround(), this.isInWater() || this.isInLava(), this.isFallFlying()),
                this.fallDistance, this.airborneTicks);
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
            // 只锁水平移动：垂直分量保留，重力照常 => 空中倒地会正常掉下来（水里仍是浮起）
            net.minecraft.world.phys.Vec3 downedVelocity = this.getDeltaMovement();
            this.setDeltaMovement(0.0, this.isInWater() ? 0.05 : downedVelocity.y, 0.0);
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
        tickSleep(server);

        // 社交习性（猫又）：偷鱼 / 偷村民绿宝石 / 睡醒赠礼 / 猫形陪睡 / 亡灵视野心情
        tickSocialHabits(server);

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

        // 强制状态（调试棒）：放在休眠时钟之后 ⇒ 一直保持
        tickForcedState(server);

        // 待命回位（取食/被推走后自己走回待命点）
        tickStandbyReturn(server);

        // 猫系特性：驱赶附近的苦力怕/幻翼（给它们追加避让目标，每生物只加一次）
        tickCatTraits(server);

        // 好感变化 → 爱心 / 村民愤怒粒子
        tickAffectionParticles(server);

        // 睡觉 / 休眠 / 战败：不主动面朝任何生物（原版 LookControl 可能被目标带着转头，这里拉回自身朝向）
        if (com.linguauniversalis.core.behavior.AttentionRules.suppressesFacing(
                girlState.downed(), girlState.dormant(), sleeping)) {
            freezeFacing();
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
        if (girlState.downed() || girlState.dormant() || girlState.isDepressed() || sleeping) {
            return; // 倒地/休眠/低落/睡觉不受指挥
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
            // 已在主人身旁（≤6 格）：停步（面向交给原版 LookAtPlayerGoal）
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
    /**
     * 是否是**同一个主人的另一只伙伴**（用于杜绝内讧）。
     *
     * <p>判定：对方也是魔物娘、也是伙伴、且绑定的玩家与自己是同一个。
     */
    // ------------------------------------------------------------------ 音效（猫又 = 原版猫）

    /** 是否是猫又（使用原版猫音效的物种）。 */
    private boolean usesCatSounds() {
        return "nekomata".equals(speciesId);
    }

    /**
     * 原版猫的一套声音（26.2 把猫音效收进了 {@code CatSoundVariant}，
     * 不再有 {@code SoundEvents.CAT_AMBIENT} 这种扁平字段）。
     */
    private static net.minecraft.world.entity.animal.feline.CatSoundVariant.CatSoundSet catSounds() {
        var variant = net.minecraft.sounds.SoundEvents.CAT_SOUNDS
                .get(net.minecraft.world.entity.animal.feline.CatSoundVariants.SoundSet.CLASSIC);
        return variant == null ? null : variant.adultSounds();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return usesCatSounds() && catSounds() != null ? catSounds().ambientSound().value() : null;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return usesCatSounds() && catSounds() != null
                ? catSounds().hurtSound().value() : super.getHurtSound(source);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return usesCatSounds() && catSounds() != null
                ? catSounds().deathSound().value() : super.getDeathSound();
    }

    /** 陪睡呼噜（进入猫形陪睡时放一次）。 */
    private void playPurrIfCat() {
        if (usesCatSounds() && catSounds() != null) {
            this.playSound(catSounds().purrSound().value(), 0.7F, 1.0F);
        }
    }

    /**
     * 猫系哈气（原版幻翼靠近真猫时会调 {@code Cat#hiss()}；这里给猫系魔物娘同一套表现）。
     */
    public void playHissIfCat() {
        if (usesCatSounds() && catSounds() != null) {
            this.playSound(catSounds().hissSound().value(), 0.8F, 1.0F);
        }
    }

    /**
     * 界面标题 = **该物种的名字**（语言文件优先，缺翻译回退档案里的中文名）。
     *
     * <p>标题不能写死，因为不同物种各有名字（猫又 / 阿拉克涅 / 数据包新增物种）。
     */
    public net.minecraft.network.chat.Component guiTitle() {
        String key = "species." + speciesId() + ".name";
        net.minecraft.network.chat.Component translated =
                net.minecraft.network.chat.Component.translatable(key);
        if (translated.getString().equals(key)) {
            return net.minecraft.network.chat.Component.literal(profile().zhName());
        }
        return translated;
    }

    public boolean isSameOwnerCompanion(net.minecraft.world.entity.Entity other) {
        return other instanceof MonsterGirlEntity otherGirl
                && otherGirl != this
                && girlState.isCompanion()
                && otherGirl.girlState().isCompanion()
                && java.util.Objects.equals(otherGirl.girlState().boundPlayerUuid(),
                        girlState.boundPlayerUuid());
    }

    public void markProvoked(LivingEntity attacker, long gameTick) {
        wakeUp(); // 挨打就醒（睡着的个体被攻击时立刻起床）
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
            // 同一个主人的其它伙伴打到自己（例如玩家横扫/误伤）：不当敌人，避免内讧
            if (isSameOwnerCompanion(attacker)) {
                this.provokedByUuid = "";
                this.provokedTick = -1;
                return;
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

    // ------------------------------------------------------------------ 巢心（领地巡游核心）
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

        // 狂暴状态推进（领地巡游：伙伴永不狂暴；无巢狂暴按 rageWithoutNest 开关）
        boolean companion = girlState.isCompanion();
        this.enraged = com.linguauniversalis.core.behavior.NestGuard.updateEnrage(
                this.enraged, companion, this.rageWithoutNest,
                nestExists, nestChunkLoaded, outsideTerritory);

        // 外生息·巢穴附近增益（设计 §13：生命恢复/力量/速度/抗性提升）
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
     * 外生息·巢穴附近增益（设计 §13）：在领地范围内周期性获得
     * 生命恢复/力量/速度/抗性提升；离开领地或没有巢心则不再续期。
     */
    private void tickNestBuff(ServerLevel server) {
        // 巢穴变量总开关：无巢穴物种（如猫又）即便属于外生息也不获得"巢穴附近增益"
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
        this.playSound(usesCatSounds() && catSounds() != null
                ? catSounds().eatSound().value()
                : net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
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

    /** 亡灵视野：视野内出现亡灵生物每天一次 +1 心情（幽暗默认开启）。 */
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
            // 走到玩家睡的床上再趴下：远处先赶路，只有贴到床边（≤1.5 格）才真的趴
            net.minecraft.core.BlockPos ownerBed = owner == null ? null
                    : owner.getSleepingPos().orElse(null);
            boolean atBed = false;
            if (sleepAsCat && ownerBed != null) {
                double bedCenterX = ownerBed.getX() + 0.5D;
                double bedCenterZ = ownerBed.getZ() + 0.5D;
                atBed = this.distanceToSqr(bedCenterX, ownerBed.getY(), bedCenterZ) <= 2.25D;
                if (!atBed) {
                    this.getNavigation().moveTo(bedCenterX, ownerBed.getY(), bedCenterZ, 1.0D);
                } else if (!sleepingAsCat) {
                    this.getNavigation().stop();
                }
            }
            if (sleepAsCat && atBed) {
                if (!sleepingAsCat) {
                    playPurrIfCat();
                }
                setDisguised(true);
                sleepingAsCat = true;
            } else if (sleepingAsCat && !sleepAsCat) {
                sleepingAsCat = false;
                if (owner != null) {
                    setDisguised(false); // 醒后恢复人形
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
        // 巢心状态评估：认领/失效/狂暴（领地巡游）
        tickNestState(server);
        if (girlState.downed() || girlState.dormant() || sleeping) {
            combatTarget = null;
            return; // 倒地/休眠/睡觉：不打也不索敌（挨打会被 markProvoked 叫醒）
        }
        // 主动模式防脱队：追出去太远就放弃目标、回主人身边（滞回：协助 32 格 / 回防 24 格）
        net.minecraft.world.entity.player.Player returnOwner = boundOwner(server);
        if (returnOwner != null && com.linguauniversalis.core.behavior.CombatModeRules.shouldReturnToOwner(
                combatMode, girlState.boundPlayerUuid() != null, combatTarget != null,
                this.distanceToSqr(returnOwner))) {
            combatTarget = null;
            this.getNavigation().moveTo(returnOwner, 1.0);
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

        // 主动模式：优先把"主人正在打的 / 打了主人的"生物设为目标（每 tick 读两个原版字段，开销极小）
        LivingEntity assistTarget = ownerAssistTarget(server);
        if (assistTarget != null && assistTarget != combatTarget) {
            combatTarget = assistTarget;
            revealHumanForm(com.linguauniversalis.core.behavior.DisguiseRules.Trigger.ATTACK, now);
        }

        // 目标重扫（10 tick 一次）：被惹反击/协助目标在记忆窗口内不因重扫被丢弃（即使已在扫描框外）
        hostileScanTicks++;
        boolean rescanned = false;
        if (hostileScanTicks % com.linguauniversalis.core.ModConstants.HOSTILE_SCAN_EVERY_TICKS == 0) {
            rescanned = true;
            if (!isProvokedTargetStillValid(combatTarget, now) && !isAssistWorthy(combatTarget)) {
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

    // ------------------------------------------------------------------ 主动模式：帮主人打架
    /** 绑定主人（在线才返回；不在线返回 null）。 */
    private net.minecraft.world.entity.player.Player boundOwner(ServerLevel server) {
        String uuid = girlState.boundPlayerUuid();
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            return server.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * 主动模式下"该帮主人打的"目标：优先<b>打了主人的人</b>，其次<b>主人正在打的人</b>。
     *
     * <p>读原版 {@code LivingEntity#getLastHurtByMob} / {@code getLastHurtMob}：玩家不需要额外记账，
     * 原版已经在挨打/出手时写好了这两个字段。
     */
    private LivingEntity ownerAssistTarget(ServerLevel server) {
        if (!com.linguauniversalis.core.behavior.CombatModeRules.assistsOwner(combatMode)
                || girlState.boundPlayerUuid() == null) {
            return null;
        }
        net.minecraft.world.entity.player.Player owner = boundOwner(server);
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }
        LivingEntity attackerOfOwner = owner.getLastHurtByMob();
        if (isAssistCandidate(attackerOfOwner, owner)) {
            return attackerOfOwner;
        }
        LivingEntity ownerTarget = owner.getLastHurtMob();
        if (isAssistCandidate(ownerTarget, owner)) {
            return ownerTarget;
        }
        return null;
    }

    /** 该生物是否"值得帮他打"（活着、不是主人/自己、在协助距离内、不是创造/旁观玩家）。 */
    private boolean isAssistCandidate(LivingEntity candidate, net.minecraft.world.entity.player.Player owner) {
        if (candidate == null || candidate == this || candidate == owner || !candidate.isAlive()
                || candidate.isRemoved() || candidate.level() != this.level()) {
            return false;
        }
        if (candidate instanceof net.minecraft.world.entity.player.Player player
                && (player.isSpectator() || player.getAbilities().instabuild)) {
            return false;
        }
        if (isSameOwnerCompanion(candidate)) {
            return false; // 同主人的伙伴互相不算协助对象
        }
        double range = com.linguauniversalis.core.ModConstants.ASSIST_RANGE_BLOCKS;
        return this.distanceToSqr(candidate) <= range * range;
    }

    /** 目标是否属于"主动模式协助对象"（用于重扫时不被敌意规则刷掉）。 */
    private boolean isAssistWorthy(LivingEntity candidate) {
        if (candidate == null
                || !com.linguauniversalis.core.behavior.CombatModeRules.assistsOwner(combatMode)
                || girlState.boundPlayerUuid() == null
                || !(this.level() instanceof ServerLevel server)) {
            return false;
        }
        net.minecraft.world.entity.player.Player owner = boundOwner(server);
        if (owner == null) {
            return false;
        }
        return isAssistCandidate(candidate, owner)
                && (candidate == owner.getLastHurtByMob() || candidate == owner.getLastHurtMob());
    }

    /** 在扫描半径内挑选满足敌意规则的最近目标；返回 null 表示无可攻击者。 */
    private LivingEntity pickCombatTarget(ServerLevel server, long now, boolean mayHunt) {
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        double scanRadius = com.linguauniversalis.core.behavior.HostilityRules.maxScanRadius(profile());
        // 威胁中心：有巢心则以巢心为圆心（领地巡游）；狂暴/无巢时以自身为中心大范围索敌
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
            // 同一个玩家的伙伴之间不许互相打（玩家误伤其中一只时不会引发内讧）
            if (girlState.isCompanion() && target instanceof MonsterGirlEntity otherGirl
                    && otherGirl.girlState().isCompanion()
                    && java.util.Objects.equals(otherGirl.girlState().boundPlayerUuid(),
                            girlState.boundPlayerUuid())) {
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
            // 协助目标（主动模式）：打了主人的 / 主人正在打的 —— 优先认，且不受敌意模板过滤
            boolean assisting = isAssistWorthy(target);
            if (assisting && selfSq < bestDistSq) {
                bestDistSq = selfSq;
                best = target;
                continue;
            }
            // 命令权内（伙伴待命/跟随中）不允许主动狩猎，仅保留反击/协助目标
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
        return distSqToNest(target.position());
    }

    /** 任意坐标到巢心（方块中心）的距离平方。 */
    private double distSqToNest(net.minecraft.world.phys.Vec3 position) {
        double dx = position.x - (nestPos.getX() + 0.5);
        double dy = position.y - (nestPos.getY() + 0.5);
        double dz = position.z - (nestPos.getZ() + 0.5);
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
        // 近战附流血（猫又绒：命中刷新 10 秒并等级 +1，无上限）
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
                if (!isSameOwnerCompanion(living)) {
                    living.hurtServer(server, this.damageSources().mobAttack(this), damage);
                }
            }
        }
    }

    /**
     * 弹射物闪避（猫又绒）：冷却就绪时免疫该次弹射物伤害并侧/后位移。
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
    /**
     * 右键互动入口。真正逻辑在 {@link #handleInteraction}；这里负责客户端手势分流，
     * 并在服务端把"被本模组接管的交互"补一次**玩家挥臂**（摸头 / 用任何道具都要有这个动作）。
     */
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
        InteractionResult result = handleInteraction(player, hand);
        // 玩家挥臂（"使用物品"的动作）：服务端 swing(true) 会把动画广播给所有跟踪客户端
        if (result != null && result.consumesAction()) {
            player.swing(hand, true);
        }
        return result;
    }

    /** 服务端互动逻辑本体（摸头/投喂/命令/礼物/初稿 GUI/百神镜/调试道具…）。 */
    private InteractionResult handleInteraction(Player player, InteractionHand hand) {
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

        // 命名牌：本模组禁用（她的头顶名牌由"状态牌"接管：被动/主动/休眠/战败倒计时/濒死）
        if (stack.is(net.minecraft.world.item.Items.NAME_TAG)) {
            tell(player, "Name tags do not work on her (status is shown on her name plate).");
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
            // 从玩家侧的伙伴登记表里一并抹掉（否则 /lu check 里还会留着这条记录）
            com.linguauniversalis.core.companion.CompanionRegistry.removeByEntity(this.getStringUUID());
            this.discard();
            tell(player, "Debug: monster girl removed.");
            return InteractionResult.SUCCESS;
        }

        // 调试：状态切换棒 —— 右键循环 正常 → 休眠 → 战败 → 正常；强制状态一直保持（随存档）
        if (stack.getItem() == LURegistries.DEBUG_STATE_STICK.get()) {
            com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState next =
                    com.linguauniversalis.core.behavior.ForcedStateRules.next(forcedState);
            setForcedState(next);
            tell(player, "Forced state -> " + next.id() + " (persists)");
            return InteractionResult.SUCCESS;
        }

        // 调试：好感道具 —— 大好感 +10 / 好感 +1 / 负好感 -1 / 大负好感 -10（不设每日上限；减好感最低到 0）
        if (stack.getItem() == LURegistries.DEBUG_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get()
                || stack.getItem() == LURegistries.DEBUG_NEGATIVE_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_BIG_NEGATIVE_AFFECTION.get()
                || stack.getItem() == LURegistries.DEBUG_STATE_STICK.get()) {
            int debugGain = stack.getItem() == LURegistries.DEBUG_AFFECTION.get() ? 10
                    : stack.getItem() == LURegistries.DEBUG_AFFECTION_1.get() ? 1
                    : stack.getItem() == LURegistries.DEBUG_BIG_NEGATIVE_AFFECTION.get() ? -10
                    : -1;
            if (!bonded && girlState.boundPlayerUuid() != null) {
                tell(player, "She is bound to someone else; debug affection refused.");
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            int before = girlState.affection();
            girlState.addAffection(debugGain); // setAffection 内部夹取 0..200 ⇒ 减好感不会减成负数
            if (debugGain > 0) {
                lastFriendlyGameDay = day; // 视为"当天有互动"，避免冷落/休眠误判（减好感不算互动）
            }
            RelationshipRules.enforceCoherence(girlState);
            // 越 10 → 绑定为友善；越 100 → 永久伙伴（enforceCoherence 负责解锁）
            if (girlState.affection() >= com.linguauniversalis.core.ModConstants.FRIENDLY_MIN
                    && girlState.boundPlayerUuid() == null) {
                girlState.setBoundPlayerUuid(actor);
            }
            int applied = girlState.affection() - before;
            tell(player, "Debug affection: " + (applied >= 0 ? "+" : "") + applied + " aff (" + before + " -> "
                    + girlState.affection() + ") -> " + girlState.stageId());
            return InteractionResult.SUCCESS;
        }

        // 调试：饱食度道具 —— 饥馑之实 -2 / 饱和之实 +2（不设每日上限，不影响好感）
        if (stack.getItem() == LURegistries.DEBUG_FAMINE_FRUIT.get()
                || stack.getItem() == LURegistries.DEBUG_SATIETY_FRUIT.get()) {
            if (!bonded && girlState.boundPlayerUuid() != null) {
                tell(player, "She is bound to someone else; debug satiety refused.");
                return InteractionResult.SUCCESS;
            }
            int delta = stack.getItem() == LURegistries.DEBUG_SATIETY_FRUIT.get() ? 2 : -2;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            int before = girlState.satiety();
            girlState.addSatiety(delta);
            tell(player, "Debug satiety: " + (delta > 0 ? "+" : "") + delta + " (" + before + " -> "
                    + girlState.satiety() + " / " + com.linguauniversalis.core.ModConstants.SATIETY_MAX + ")");
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

    /** 切换到待机时记下待命点；切走时清掉（回位逻辑见 {@link #tickStandbyReturn}）。 */
    private void updateStandbyAnchor() {
        if (CommandMode.STANDBY.englishId.equals(obeyModeId)) {
            this.standbyAnchor = this.blockPosition().immutable();
            this.standbyRepathTick = -1L;
        } else {
            this.standbyAnchor = null;
            this.standbyRepathTick = -1L;
        }
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
        updateStandbyAnchor();
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
        // 1.5) 命名牌：本模组禁用命名（她的头顶名牌由状态牌接管），客户端也认领这次点按
        if (stack.is(net.minecraft.world.item.Items.NAME_TAG)) {
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
                || it == LURegistries.DEBUG_AFFECTION_1.get()
                || it == LURegistries.DEBUG_NEGATIVE_AFFECTION.get()
                || it == LURegistries.DEBUG_BIG_NEGATIVE_AFFECTION.get()
                || it == LURegistries.DEBUG_STATE_STICK.get()
                || it == LURegistries.DEBUG_FAMINE_FRUIT.get()
                || it == LURegistries.DEBUG_SATIETY_FRUIT.get()) {
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
    /** 旧档的猫形布尔标记（只读，兼容用）。 */
    private static final String TAG_CAT_FORM = "LUCatForm";
    /** 变形形态 id（空串 = 本体形态）。 */
    private static final String TAG_DISGUISE_FORM = "LUCatFormId";
    /** 战斗模式 id（被动/主动）。 */
    private static final String TAG_COMBAT_MODE = "LUCombatMode";
    /** 调试棒强制状态 id（normal / dormant / downed）。 */
    private static final String TAG_FORCED_STATE = "LUForcedState";
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
        out.putString(TAG_DISGUISE_FORM, disguiseFormId()); // 变形形态（空串 = 本体）
        out.putString(TAG_COMBAT_MODE, combatMode.id());    // 战斗模式（被动/主动）
        out.putString(TAG_FORCED_STATE, forcedState.id()); // 调试棒的强制状态
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
        // 读档不恢复待命锚点：下次下"待机"命令时重新记（避免拿旧坐标乱走）
        this.standbyAnchor = null;
        this.standbyRepathTick = -1L;
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
        // 变形形态持久化：优先读形态 id；旧档只有布尔"猫形"标记 → 折算成该物种登记的形态
        String loadedForm = in.getStringOr(TAG_DISGUISE_FORM, "");
        if (loadedForm.isEmpty() && in.getBooleanOr(TAG_CAT_FORM, false)) {
            com.linguauniversalis.core.species.DisguiseForm legacy =
                    com.linguauniversalis.core.behavior.DisguiseRules.formOf(profile());
            loadedForm = legacy == null ? "" : legacy.id();
        }
        if (!loadedForm.isEmpty() && !com.linguauniversalis.core.species.DisguiseRegistry.contains(loadedForm)) {
            loadedForm = "";
        }
        this.getEntityData().set(DATA_DISGUISE_FORM, loadedForm);
        this.combatMode = com.linguauniversalis.core.behavior.CombatModeRules.CombatMode.byId(
                in.getStringOr(TAG_COMBAT_MODE, com.linguauniversalis.core.behavior.CombatModeRules
                        .DEFAULT_MODE.id()));
        this.getEntityData().set(DATA_COMBAT_MODE, combatMode.id());
        // 调试棒的强制状态（存在则以它为准，随后由 tick 持续保持）
        this.forcedState = com.linguauniversalis.core.behavior.ForcedStateRules.ForcedState.byId(
                in.getStringOr(TAG_FORCED_STATE, "normal"));
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
