package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 环境粒子发射器规则（纯规则，无 MC 依赖）—— 让魔物娘周围按"物种变量"持续冒粒子。
 *
 * <p>为什么做成数据驱动：粒子的用途（猫又掉毛、阿拉克涅滴蛛丝、低落时冒黑雾、战败时冒烟……）
 * 会随设计与美术推进不断新增，写死在代码里每加一种都要改实体；这里把"什么粒子／多久一次／
 * 一次几颗／半径多大／什么时候放"全部交给物种变量（{@link TemplateKeys}），代码只负责执行。
 *
 * <p>物种变量（全部可选，不写 = 不放粒子）：
 * <pre>
 *   species.ambientParticle            粒子 id（如 minecraft:ash）
 *   species.ambientParticle.when       触发状态：always / dormant / downed / depressed / aggressive
 *   species.ambientParticle.interval   间隔 tick（默认 20 = 每秒一次）
 *   species.ambientParticle.count      每次颗数（默认 1）
 *   species.ambientParticle.radius     水平散布半径（格，默认 0.5）
 *   species.ambientParticle.yOffset    出生高度偏移（格，默认 1.0）
 * </pre>
 */
public final class AmbientParticleRules {
    private AmbientParticleRules() {
    }

    /** 粒子触发状态。 */
    public enum ParticleState {
        /** 一直放。 */
        ALWAYS("always"),
        /** 仅在休眠时。 */
        DORMANT("dormant"),
        /** 仅在倒地时。 */
        DOWNED("downed"),
        /** 仅在心情低落时。 */
        DEPRESSED("depressed"),
        /** 仅在主动战斗模式时。 */
        AGGRESSIVE("aggressive");

        private final String id;

        ParticleState(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 由 id 解析；未知/空 → {@link #ALWAYS}。 */
        public static ParticleState byId(String id) {
            if (id != null) {
                for (ParticleState state : values()) {
                    if (state.id.equalsIgnoreCase(id.trim())) {
                        return state;
                    }
                }
            }
            return ALWAYS;
        }
    }

    /** 一份粒子规格（由物种变量解析出来的执行参数）。 */
    public record Spec(String particleId, ParticleState when, int intervalTicks, int count,
                       double radius, double yOffset) {
    }

    public static final int DEFAULT_INTERVAL_TICKS = 20;
    public static final int DEFAULT_COUNT = 1;
    public static final double DEFAULT_RADIUS = 0.5;
    public static final double DEFAULT_Y_OFFSET = 1.0;

    /**
     * 解析物种的粒子规格；没有配置粒子时返回 {@code null}。
     *
     * @param profile 物种档案
     */
    public static Spec specOf(SpeciesProfile profile) {
        if (profile == null) {
            return null;
        }
        String particleId = profile.templateString(TemplateKeys.SPECIES_AMBIENT_PARTICLE, "");
        if (particleId == null || particleId.trim().isEmpty()) {
            return null;
        }
        return new Spec(particleId.trim(),
                ParticleState.byId(profile.templateString(TemplateKeys.SPECIES_AMBIENT_PARTICLE_WHEN, "always")),
                Math.max(1, (int) Math.round(profile.templateParam(
                        TemplateKeys.SPECIES_AMBIENT_PARTICLE_INTERVAL_TICKS, DEFAULT_INTERVAL_TICKS))),
                Math.max(1, (int) Math.round(profile.templateParam(
                        TemplateKeys.SPECIES_AMBIENT_PARTICLE_COUNT, DEFAULT_COUNT))),
                nonNegative(profile.templateParam(TemplateKeys.SPECIES_AMBIENT_PARTICLE_RADIUS, DEFAULT_RADIUS)),
                profile.templateParam(TemplateKeys.SPECIES_AMBIENT_PARTICLE_Y_OFFSET, DEFAULT_Y_OFFSET));
    }

    /** 当前状态是否满足该粒子的触发条件。 */
    public static boolean stateMatches(ParticleState when, boolean dormant, boolean downed,
                                       boolean depressed, boolean aggressive) {
        return switch (when == null ? ParticleState.ALWAYS : when) {
            case DORMANT -> dormant;
            case DOWNED -> downed;
            case DEPRESSED -> depressed;
            case AGGRESSIVE -> aggressive;
            case ALWAYS -> true;
        };
    }

    /** 是否到了该放粒子的 tick（按间隔取模，避免每只个体各自计数）。 */
    public static boolean dueAtTick(long gameTime, int tickCount, int intervalTicks) {
        if (intervalTicks <= 0) {
            return true;
        }
        long phase = Math.floorMod(gameTime + tickCount, intervalTicks);
        return phase == 0L;
    }

    private static double nonNegative(double value) {
        return value < 0 ? 0 : value;
    }
}
