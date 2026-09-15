package com.linguauniversalis.entity;

import com.linguauniversalis.core.behavior.SpeciesAbilities;
import com.linguauniversalis.registry.LUEntities;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 魔物娘专属能力的<b>弹道</b>（蛛网 / 蛛丝拉拽 / 暗影箭）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>以投射物实体的形式飞行（视觉先复用原版箭：渲染器用原版箭贴图）；</li>
 *   <li><b>弹道 ≠ 原版弹射物</b>：命中结算按 {@link SpeciesAbilities.BoltKind#dealsProjectileDamage()} 区分 ——
 *       蛛丝按弹射物伤害（吃弹射物保护），<b>暗影箭按魔法伤害，不受弹射物保护附魔影响</b>，蛛网不造成伤害；</li>
 *   <li>不落地拾取（{@link Pickup#DISALLOWED}）、无重力直线飞行、超时自动消失。</li>
 * </ul>
 */
public class LuBoltEntity extends AbstractArrow {
    private static final String TAG_KIND = "LUBoltKind";
    private static final String TAG_DAMAGE = "LUBoltDamage";
    private static final String TAG_AGE = "LUBoltAge";
    /** 箭的贴图（视觉占位）。 */
    public static final Identifier ARROW_TEXTURE =
            Identifier.parse("minecraft:textures/entity/projectiles/arrow.png");

    private SpeciesAbilities.BoltKind kind = SpeciesAbilities.BoltKind.WEB;
    private float boltDamage;
    private int maxLifeTicks = 60;
    /** 已飞行 tick（持久化：区块重载后继续计时，保证射空的弹道最终一定消失）。 */
    private int ageTicks;
    /** 蛛网参数（施放时从发射者档案快照，避免命中时档案不可用）。 */
    private int webEffectTicks = 100;
    private int webAmplifier = 1;

    public LuBoltEntity(EntityType<? extends LuBoltEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /**
     * 由 {@code shooter} 向 {@code target} 发射一枚指定种类的弹道。
     *
     * @return 已加入世界的弹道；目标与发射者重合等无效情形返回 null
     */
    public static LuBoltEntity shootAt(ServerLevel level, LivingEntity shooter, LivingEntity target,
                                       SpeciesAbilities.BoltKind kind, float damage) {
        Vec3 from = shooter.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            return null;
        }
        LuBoltEntity bolt = new LuBoltEntity(LUEntities.LU_BOLT.get(), level);
        bolt.setOwner(shooter);
        bolt.kind = kind;
        bolt.boltDamage = damage;
        bolt.maxLifeTicks = SpeciesAbilities.boltMaxLifeTicks(kind);
        // 蛛网参数从发射者档案快照（箭在命中时可能已与发射者脱钩）
        if (shooter instanceof MonsterGirlEntity girl) {
            bolt.webEffectTicks = SpeciesAbilities.webEffectTicks(girl.profile());
            bolt.webAmplifier = SpeciesAbilities.webAmplifier(girl.profile());
        }
        bolt.setPos(from.x, from.y - 0.15, from.z);
        double speed = SpeciesAbilities.boltSpeed(kind);
        Vec3 v = dir.normalize().scale(speed);
        bolt.shoot(v.x, v.y, v.z, (float) speed, 0f);
        level.addFreshEntity(bolt);
        return bolt;
    }

    /** 该弹道种类。 */
    public SpeciesAbilities.BoltKind boltKind() {
        return kind;
    }

    /**
     * 弹道寿命推进与消失兜底。
     *
     * <p>射空（打偏/射向天空）时的消失保证：
     * <ol>
     *   <li><b>寿命上限</b>：{@code ageTicks} 超过该种类的 maxLifeTicks（3~4 秒）即 {@code discard()}；</li>
     *   <li><b>寿命持久化</b>：{@code ageTicks} 存档，区块卸载/重载后继续计数，不会"重新获得寿命"；</li>
     *   <li><b>世界边界兜底</b>：飞出世界上下界立即消失（射向天空也不会长期留存）；</li>
     *   <li>命中实体/方块即消失（见 onHitEntity / onHitBlock），且不可拾取。</li>
     * </ol>
     * 弹道不会强制加载区块，因此不会因远离玩家而持续占用 tick。
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        ageTicks++;
        if (ageTicks > maxLifeTicks || isOutsideWorldBounds()) {
            this.discard();
        }
    }

    /** 是否已飞出世界上下界（各留 32 格余量）。 */
    private boolean isOutsideWorldBounds() {
        double minY = this.level().getMinY() - 32.0;
        double maxY = this.level().getMaxY() + 32.0;
        return this.getY() < minY || this.getY() > maxY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        Entity owner = this.getOwner();
        if (hit == owner) {
            return; // 不命中发射者自己
        }
        if (!(this.level() instanceof ServerLevel server) || !(hit instanceof LivingEntity living)) {
            this.discard();
            return;
        }
        // 创造/旁观玩家不作目标（与索敌口径一致）
        if (living instanceof net.minecraft.world.entity.player.Player p
                && (p.isSpectator() || p.getAbilities().instabuild)) {
            this.discard();
            return;
        }
        switch (kind) {
            case WEB -> {
                // 蛛网：减速 + 挖掘疲劳（无伤害）
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, webEffectTicks, webAmplifier), this);
                living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, webEffectTicks, webAmplifier), this);
            }
            case SILK -> {
                // 蛛丝拉拽：物理弹道伤害（按弹射物结算）并把目标拉向发射者
                if (boltDamage > 0f) {
                    living.hurtServer(server, server.damageSources().arrow(this, owner), boltDamage);
                }
                if (owner instanceof LivingEntity shooter && owner.level() == this.level()) {
                    Vec3 pull = shooter.position().subtract(living.position());
                    Vec3 flat = new Vec3(pull.x, 0.0, pull.z);
                    if (flat.lengthSqr() > 1.0E-4) {
                        living.setDeltaMovement(flat.normalize().scale(0.85).add(0.0, 0.25, 0.0));
                        living.hurtMarked = true;
                    }
                }
            }
            case SHADOW -> {
                // 暗影箭：魔法伤害（不按弹射物结算 → 不受弹射物保护附魔影响）
                if (boltDamage > 0f) {
                    living.hurtServer(server, server.damageSources().indirectMagic(this, owner), boltDamage);
                }
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) {
        this.discard(); // 不插在方块上
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString(TAG_KIND, kind.id());
        out.putFloat(TAG_DAMAGE, boltDamage);
        out.putInt(TAG_AGE, ageTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        SpeciesAbilities.BoltKind parsed = SpeciesAbilities.BoltKind.byId(in.getStringOr(TAG_KIND, "web"));
        this.kind = parsed == null ? SpeciesAbilities.BoltKind.WEB : parsed;
        this.boltDamage = in.getFloatOr(TAG_DAMAGE, 0f);
        this.maxLifeTicks = SpeciesAbilities.boltMaxLifeTicks(this.kind);
        this.ageTicks = in.getIntOr(TAG_AGE, 0);
    }
}
