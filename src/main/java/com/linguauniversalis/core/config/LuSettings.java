package com.linguauniversalis.core.config;

import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * 本地可调设置（纯逻辑，无 MC 依赖）。文件不存在时写出一份带注释的默认文件。
 *
 * <p>路径 {@code config/lingua_universalis.properties}（dev: {@code run/config/...}）。
 * 文件每秒自动重读一次，改完存盘约 1 秒生效，不用重启游戏。
 *
 * <p>两层结构：全局键 + 物种覆盖键 {@code <物种id>.<键>}；
 * 优先级 物种 &gt; 全局 &gt; 代码默认。物种键在默认文件里是注释掉的，去掉行首 # 才生效。
 *
 * <p>可调组：{@code headLook.*}、{@code animation.speed.*} / {@code animation.mainTransitionTicks} /
 * {@code animation.freezePawTransition}、{@code model.renderScale}、
 * {@code body.width|height|eyeHeight}（不写则用物种档案值）。
 */
public final class LuSettings {
    // ---------------------------------------------------------------- 键名
    public static final String KEY_HEAD_LOOK_ENABLED = "headLook.enabled";
    public static final String KEY_HEAD_LOOK_YAW_SIGN = "headLook.yawSign";
    public static final String KEY_HEAD_LOOK_PITCH_SIGN = "headLook.pitchSign";
    public static final String KEY_HEAD_LOOK_YAW_AXIS = "headLook.yawAxis";
    public static final String KEY_HEAD_LOOK_PITCH_AXIS = "headLook.pitchAxis";
    public static final String KEY_HEAD_LOOK_FOLLOW_PITCH = "headLook.followPitch";
    public static final String KEY_LOOK_AT_NEARBY_PLAYER = "headLook.lookAtNearbyPlayer";
    public static final String KEY_HEAD_LOOK_MAX_YAW = "headLook.maxYawDeg";
    public static final String KEY_HEAD_LOOK_MAX_PITCH = "headLook.maxPitchDeg";
    public static final String KEY_MAIN_TRANSITION_TICKS = "animation.mainTransitionTicks";
    public static final String KEY_JUMP_TRANSITION_TICKS = "animation.jumpTransitionTicks";
    public static final String KEY_WALK_ANIMATION_SPEED = "animation.walkSpeed";
    public static final String KEY_FREEZE_PAW_TRANSITION = "animation.freezePawTransition";
    public static final String KEY_MODEL_RENDER_SCALE = "model.renderScale";
    public static final String KEY_BODY_WIDTH = "body.width";
    public static final String KEY_BODY_HEIGHT = "body.height";
    public static final String KEY_BODY_EYE_HEIGHT = "body.eyeHeight";
    /** 可选：慢速档（walk）倍率覆盖（不写则用物种档案值）。 */
    public static final String KEY_SPEED_WALK_SCALE = "speed.walkScale";
    /** 可选：最快档倍率覆盖（不写则用物种档案值）。 */
    public static final String KEY_SPEED_FAST_SCALE = "speed.fastScale";
    public static final String ANIMATION_SPEED_PREFIX = "animation.speed.";

    /** 会写进默认配置文件的动画名（与动画资源一致；冒烟测试对表）。 */
    public static final String[] TUNING_ANIMATION_NAMES = {
            "main_idle", "main_walk", "main_jump", "main_airborne", "main_eat", "main_sit",
            "main_playing1", "main_hiss1", "constant", "tail_swing", "pet",
            "random_blink1", "random_idle1",
    };

    // ---------------------------------------------------------------- 默认值
    public static final boolean DEFAULT_HEAD_LOOK_ENABLED = true;
    public static final double DEFAULT_HEAD_LOOK_YAW_SIGN = -1.0;
    public static final double DEFAULT_HEAD_LOOK_PITCH_SIGN = -1.0;
    public static final String DEFAULT_HEAD_LOOK_YAW_AXIS = "z";
    public static final String DEFAULT_HEAD_LOOK_PITCH_AXIS = "x";
    public static final boolean DEFAULT_HEAD_LOOK_FOLLOW_PITCH = true;
    public static final boolean DEFAULT_LOOK_AT_NEARBY_PLAYER = true;
    public static final double DEFAULT_HEAD_LOOK_MAX_YAW = 75.0;
    public static final double DEFAULT_HEAD_LOOK_MAX_PITCH = 60.0;
    /** 主要动画自动过渡时长（tick）：越大越柔和；0 = 不做过渡。 */
    public static final int DEFAULT_MAIN_TRANSITION_TICKS = 8;
    /**
     * 切到 main_jump 时的过渡时长（tick）。默认 0 = 硬切：
     * 跳跃动画本身只有 0.42 秒，若用跟主过渡一样长的时长，整段跳跃会被过渡稀释掉（看起来"跳跃动画没生效"）。
     */
    public static final int DEFAULT_JUMP_TRANSITION_TICKS = 0;
    public static final boolean DEFAULT_FREEZE_PAW_TRANSITION = true;
    public static final double DEFAULT_MODEL_RENDER_SCALE = 1.0;
    /** 全局动画倍速默认全部 1.0（要加速请在配置里逐条或按物种改）。 */
    public static final double DEFAULT_ANIMATION_SPEED = 1.0;
    public static final double DEFAULT_WALK_ANIMATION_SPEED = 1.0;
    public static final double DEFAULT_BODY_WIDTH = 0.6;
    public static final double DEFAULT_BODY_HEIGHT = 1.8;
    public static final double DEFAULT_BODY_EYE_HEIGHT = 1.62;

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final List<String> COMMENTS = new ArrayList<>();
    /** 可选键（不在 DEFAULTS 里，写进文件才生效）。 */
    private static final Set<String> OPTIONAL_KEYS =
            Set.of(KEY_BODY_WIDTH, KEY_BODY_HEIGHT, KEY_BODY_EYE_HEIGHT,
                    KEY_SPEED_WALK_SCALE, KEY_SPEED_FAST_SCALE);

    static {
        COMMENTS.add("# 万象牧语 可调项（不存在时自动生成；改完存盘约 1 秒生效，不用重启游戏）");
        COMMENTS.add("#");
        COMMENTS.add("# 两层结构：全局键 + 物种覆盖键，物种覆盖写法 = <物种id>.<键>，例如：");
        COMMENTS.add("#   nekomata.animation.speed.main_walk=3      （只改猫又的走路倍速）");
        COMMENTS.add("#   nekomata.headLook.yawAxis=z");
        COMMENTS.add("#   arakne.model.renderScale=0.9");
        COMMENTS.add("# 优先级：<物种id>.<键> > <键> > 代码默认值（物种永远优先）。");
        COMMENTS.add("# 下面物种段的键默认是注释掉的，去掉行首 # 才会生效。");
        COMMENTS.add("#");
        COMMENTS.add("# headLook.*：头部跟随视角（只影响 AllHead 的旋转，不影响位置/缩放）");
        COMMENTS.add("#   yawAxis / pitchAxis：作用到骨骼的哪根轴（x / y / z），按模型实际表现试");
        COMMENTS.add("#   yawSign / pitchSign：方向反了改成 -1（正负一）");
        COMMENTS.add("#   followPitch：false = 只跟随左右转头；lookAtNearbyPlayer：没绑定时也注视附近玩家");
        COMMENTS.add("#   maxYawDeg / maxPitchDeg：限幅（度）");
        COMMENTS.add("# animation.speed.<动画名>：每个动画的播放倍速（1.0 = 原速，默认全部 1.0）");
        COMMENTS.add("# animation.mainTransitionTicks：主要动画自动过渡时长（tick）；0 = 不做过渡，越大越柔和");
        COMMENTS.add("# animation.freezePawTransition：true = LeftPaw/RightPaw 不参与自动过渡");
        COMMENTS.add("#   （GeckoLib 过渡按关键帧数值线性插值、不做最短弧，脚掌会绕远路反向转 180 度以上）");
        COMMENTS.add("# model.renderScale：模型渲染缩放（1.0 = 原尺寸）");
        COMMENTS.add("# body.width / body.height / body.eyeHeight：碰撞箱与视线高度（格），");
        COMMENTS.add("#   不写则用物种档案里的值（猫又 = 0.6 / 1.5 / 2.1548）");
        COMMENTS.add("# speed.walkScale / speed.fastScale：移动速度倍率（最终速度 = 0.25 格/tick × 倍率），");
        COMMENTS.add("#   不写则用物种档案里的值（猫又 = 1.0 / 1.7），≤0 视为不写；");
        COMMENTS.add("#   跟随玩家超过 8 格、或正在攻击敌人时用 fast，其余用 walk");

        DEFAULTS.put(KEY_HEAD_LOOK_ENABLED, Boolean.toString(DEFAULT_HEAD_LOOK_ENABLED));
        DEFAULTS.put(KEY_HEAD_LOOK_YAW_SIGN, trim(DEFAULT_HEAD_LOOK_YAW_SIGN));
        DEFAULTS.put(KEY_HEAD_LOOK_PITCH_SIGN, trim(DEFAULT_HEAD_LOOK_PITCH_SIGN));
        DEFAULTS.put(KEY_HEAD_LOOK_YAW_AXIS, DEFAULT_HEAD_LOOK_YAW_AXIS);
        DEFAULTS.put(KEY_HEAD_LOOK_PITCH_AXIS, DEFAULT_HEAD_LOOK_PITCH_AXIS);
        DEFAULTS.put(KEY_HEAD_LOOK_FOLLOW_PITCH, Boolean.toString(DEFAULT_HEAD_LOOK_FOLLOW_PITCH));
        DEFAULTS.put(KEY_LOOK_AT_NEARBY_PLAYER, Boolean.toString(DEFAULT_LOOK_AT_NEARBY_PLAYER));
        DEFAULTS.put(KEY_HEAD_LOOK_MAX_YAW, trim(DEFAULT_HEAD_LOOK_MAX_YAW));
        DEFAULTS.put(KEY_HEAD_LOOK_MAX_PITCH, trim(DEFAULT_HEAD_LOOK_MAX_PITCH));
        DEFAULTS.put(KEY_MAIN_TRANSITION_TICKS, Integer.toString(DEFAULT_MAIN_TRANSITION_TICKS));
        DEFAULTS.put(KEY_JUMP_TRANSITION_TICKS, Integer.toString(DEFAULT_JUMP_TRANSITION_TICKS));
        DEFAULTS.put(KEY_FREEZE_PAW_TRANSITION, Boolean.toString(DEFAULT_FREEZE_PAW_TRANSITION));
        DEFAULTS.put(KEY_MODEL_RENDER_SCALE, trim(DEFAULT_MODEL_RENDER_SCALE));
        for (String animation : TUNING_ANIMATION_NAMES) {
            DEFAULTS.put(ANIMATION_SPEED_PREFIX + animation, trim(defaultSpeedOf(animation)));
        }
    }

    private final Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
    private final Map<String, Map<String, String>> speciesValues = new LinkedHashMap<>();
    private boolean loaded;
    private Path sourceFile;
    private long sourceModified = -1L;
    private int pollTicks;

    private LuSettings() {
    }

    private static final LuSettings INSTANCE = new LuSettings();

    public static LuSettings get() {
        return INSTANCE;
    }

    /** 独立读取一份（供测试/工具用）。 */
    public static LuSettings read(Path file) {
        LuSettings settings = new LuSettings();
        settings.loadIfNeeded(file);
        return settings;
    }

    /** 某动画的默认倍速（当前全部 1.0）。 */
    public static double defaultSpeedOf(String animationName) {
        return "main_walk".equals(animationName) ? DEFAULT_WALK_ANIMATION_SPEED : DEFAULT_ANIMATION_SPEED;
    }

    private static boolean isKnownKey(String key) {
        return DEFAULTS.containsKey(key) || OPTIONAL_KEYS.contains(key)
                || key.startsWith(ANIMATION_SPEED_PREFIX);
    }

    // ---------------------------------------------------------------- 加载
    public synchronized boolean loadIfNeeded(Path file) {
        this.sourceFile = file;
        if (loaded) {
            return true;
        }
        loaded = true;
        return readFile(file);
    }

    /** 每秒检查一次文件改动并重读（调数值不用重启游戏）。 */
    public synchronized void pollForChanges() {
        if (sourceFile == null || ++pollTicks < 20) {
            return;
        }
        pollTicks = 0;
        try {
            if (!Files.isRegularFile(sourceFile)) {
                return;
            }
            if (Files.getLastModifiedTime(sourceFile).toMillis() != sourceModified) {
                readFile(sourceFile);
            }
        } catch (IOException | RuntimeException ignored) {
            // 读不动就保持当前值
        }
    }

    private boolean readFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                writeDefaults(file);
                sourceModified = Files.getLastModifiedTime(file).toMillis();
                return false;
            }
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
            values.clear();
            values.putAll(DEFAULTS);
            speciesValues.clear();
            for (String rawKey : properties.stringPropertyNames()) {
                String rawValue = properties.getProperty(rawKey, "").trim();
                if (rawValue.isEmpty()) {
                    continue;
                }
                String key = rawKey.trim();
                if (isKnownKey(key)) {
                    values.put(key, rawValue);
                    continue;
                }
                int dot = key.indexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String species = key.substring(0, dot);
                String rest = key.substring(dot + 1);
                if (isKnownKey(rest)) {
                    speciesValues.computeIfAbsent(species, unused -> new LinkedHashMap<>()).put(rest, rawValue);
                }
            }
            sourceModified = Files.getLastModifiedTime(file).toMillis();
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false; // 读不出来就用默认值，别影响启动
        }
    }

    /** 仅测试/工具用：不落盘，直接给一份默认值。 */
    public synchronized void useDefaults() {
        loaded = true;
        values.clear();
        values.putAll(DEFAULTS);
        speciesValues.clear();
    }

    private static void writeDefaults(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String comment : COMMENTS) {
            text.append(comment).append(System.lineSeparator());
        }
        text.append(System.lineSeparator()).append("# ---------- 全局默认 ----------").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
        }
        text.append(System.lineSeparator())
                .append("# ---------- 物种覆盖（默认注释，去掉行首 # 生效；优先级高于全局）----------")
                .append(System.lineSeparator());
        for (SpeciesProfile profile : SpeciesRegistry.all()) {
            String id = profile.id();
            text.append("# ").append(id).append(" ---- ").append(profile.zhName()).append(System.lineSeparator());
            for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
                text.append("# ").append(id).append('.').append(entry.getKey()).append('=')
                        .append(entry.getValue()).append(System.lineSeparator());
            }
            text.append("# ").append(id).append('.').append(KEY_BODY_WIDTH).append('=')
                    .append(trim(profile.templateParam(
                            com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_BODY_WIDTH,
                            DEFAULT_BODY_WIDTH))).append(System.lineSeparator());
            text.append("# ").append(id).append('.').append(KEY_BODY_HEIGHT).append('=')
                    .append(trim(profile.templateParam(
                            com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_BODY_HEIGHT,
                            DEFAULT_BODY_HEIGHT))).append(System.lineSeparator());
            text.append("# ").append(id).append('.').append(KEY_BODY_EYE_HEIGHT).append('=')
                    .append(trim(profile.templateParam(
                            com.linguauniversalis.core.behavior.TemplateKeys.SPECIES_EYE_HEIGHT,
                            DEFAULT_BODY_EYE_HEIGHT))).append(System.lineSeparator());
            text.append("# ").append(id).append('.').append(KEY_SPEED_WALK_SCALE).append('=')
                    .append(trim(com.linguauniversalis.core.behavior.MovementSpeedRules.walkSpeedScale(profile)))
                    .append(System.lineSeparator());
            text.append("# ").append(id).append('.').append(KEY_SPEED_FAST_SCALE).append('=')
                    .append(trim(com.linguauniversalis.core.behavior.MovementSpeedRules.fastSpeedScale(profile)))
                    .append(System.lineSeparator());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------- 读取工具
    private String raw(String speciesId, String key) {
        if (speciesId != null) {
            Map<String, String> perSpecies = speciesValues.get(speciesId);
            if (perSpecies != null) {
                String value = perSpecies.get(key);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        String global = values.get(key);
        return global == null || global.isBlank() ? null : global.trim();
    }

    private boolean bool(String speciesId, String key, boolean fallback) {
        String raw = raw(speciesId, key);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    private double number(String speciesId, String key, double fallback) {
        return parse(raw(speciesId, key), fallback);
    }

    private String axis(String speciesId, String key, String fallback) {
        String raw = raw(speciesId, key);
        if (raw == null) {
            return fallback;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        return value.equals("x") || value.equals("y") || value.equals("z") ? value : fallback;
    }

    private double sign(String speciesId, String key, double fallback) {
        double value = number(speciesId, key, fallback);
        return value == 0 ? fallback : (value < 0 ? -1.0 : 1.0);
    }

    private double positive(String speciesId, String key, double fallback) {
        double value = number(speciesId, key, fallback);
        return value <= 0 ? fallback : value;
    }

    private static Double positiveOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        double value = parse(raw, -1.0);
        return value > 0 ? value : null;
    }

    // ---------------------------------------------------------------- 头部跟随
    public boolean headLookEnabled(String speciesId) {
        return bool(speciesId, KEY_HEAD_LOOK_ENABLED, DEFAULT_HEAD_LOOK_ENABLED);
    }

    public String headLookYawAxis(String speciesId) {
        return axis(speciesId, KEY_HEAD_LOOK_YAW_AXIS, DEFAULT_HEAD_LOOK_YAW_AXIS);
    }

    public String headLookPitchAxis(String speciesId) {
        return axis(speciesId, KEY_HEAD_LOOK_PITCH_AXIS, DEFAULT_HEAD_LOOK_PITCH_AXIS);
    }

    public double headLookYawSign(String speciesId) {
        return sign(speciesId, KEY_HEAD_LOOK_YAW_SIGN, DEFAULT_HEAD_LOOK_YAW_SIGN);
    }

    public double headLookPitchSign(String speciesId) {
        return sign(speciesId, KEY_HEAD_LOOK_PITCH_SIGN, DEFAULT_HEAD_LOOK_PITCH_SIGN);
    }

    public boolean headLookFollowPitch(String speciesId) {
        return bool(speciesId, KEY_HEAD_LOOK_FOLLOW_PITCH, DEFAULT_HEAD_LOOK_FOLLOW_PITCH);
    }

    public boolean lookAtNearbyPlayer(String speciesId) {
        return bool(speciesId, KEY_LOOK_AT_NEARBY_PLAYER, DEFAULT_LOOK_AT_NEARBY_PLAYER);
    }

    public double headLookMaxYawDeg(String speciesId) {
        return positive(speciesId, KEY_HEAD_LOOK_MAX_YAW, DEFAULT_HEAD_LOOK_MAX_YAW);
    }

    public double headLookMaxPitchDeg(String speciesId) {
        return positive(speciesId, KEY_HEAD_LOOK_MAX_PITCH, DEFAULT_HEAD_LOOK_MAX_PITCH);
    }

    // ---------------------------------------------------------------- 动画
    public int mainTransitionTicks(String speciesId) {
        return Math.max(0, (int) Math.round(
                number(speciesId, KEY_MAIN_TRANSITION_TICKS, DEFAULT_MAIN_TRANSITION_TICKS)));
    }

    /** 切到 main_jump 时的过渡时长（默认 0 = 硬切，保证整段跳跃播完）。 */
    public int jumpTransitionTicks(String speciesId) {
        return Math.max(0, (int) Math.round(
                number(speciesId, KEY_JUMP_TRANSITION_TICKS, DEFAULT_JUMP_TRANSITION_TICKS)));
    }

    public boolean freezePawTransition(String speciesId) {
        return bool(speciesId, KEY_FREEZE_PAW_TRANSITION, DEFAULT_FREEZE_PAW_TRANSITION);
    }

    /**
     * 某动画的播放倍速。优先级（物种永远高于全局）：
     * 物种 animation.speed.名 &gt; 物种 animation.walkSpeed（仅 main_walk）
     * &gt; 全局 animation.speed.名 &gt; 全局 animation.walkSpeed &gt; 代码默认（1.0）。
     */
    public double animationSpeed(String speciesId, String animationName) {
        String key = ANIMATION_SPEED_PREFIX + animationName;
        Double speciesExplicit = positiveOrNull(raw(speciesId, key));
        if (speciesExplicit != null) {
            return speciesExplicit;
        }
        if ("main_walk".equals(animationName)) {
            Double speciesLegacy = positiveOrNull(raw(speciesId, KEY_WALK_ANIMATION_SPEED));
            if (speciesLegacy != null) {
                return speciesLegacy;
            }
        }
        Double globalExplicit = positiveOrNull(raw(null, key));
        if (globalExplicit != null) {
            return globalExplicit;
        }
        if ("main_walk".equals(animationName)) {
            Double globalLegacy = positiveOrNull(raw(null, KEY_WALK_ANIMATION_SPEED));
            if (globalLegacy != null) {
                return globalLegacy;
            }
        }
        return defaultSpeedOf(animationName);
    }

    // ---------------------------------------------------------------- 模型 / 体型
    public double modelRenderScale(String speciesId) {
        double value = number(speciesId, KEY_MODEL_RENDER_SCALE, DEFAULT_MODEL_RENDER_SCALE);
        return value <= 0 ? DEFAULT_MODEL_RENDER_SCALE : value;
    }

    /** 碰撞箱宽（格）；小于等于 0 表示未配置（用物种档案值）。 */
    public double bodyWidth(String speciesId) {
        return number(speciesId, KEY_BODY_WIDTH, -1.0);
    }

    /** 碰撞箱高（格）；小于等于 0 表示未配置。 */
    public double bodyHeight(String speciesId) {
        return number(speciesId, KEY_BODY_HEIGHT, -1.0);
    }

    /** 视线高度（格）；小于等于 0 表示未配置。 */
    public double bodyEyeHeight(String speciesId) {
        return number(speciesId, KEY_BODY_EYE_HEIGHT, -1.0);
    }

    /** walk 速度倍率覆盖；小于等于 0 表示未配置（用物种档案值）。 */
    public double speedWalkScale(String speciesId) {
        return number(speciesId, KEY_SPEED_WALK_SCALE, -1.0);
    }

    /** 最快速度倍率覆盖；小于等于 0 表示未配置（用物种档案值）。 */
    public double speedFastScale(String speciesId) {
        return number(speciesId, KEY_SPEED_FAST_SCALE, -1.0);
    }

    // ---------------------------------------------------------------- 全局重载
    public boolean headLookEnabled() {
        return headLookEnabled(null);
    }

    public String headLookYawAxis() {
        return headLookYawAxis(null);
    }

    public String headLookPitchAxis() {
        return headLookPitchAxis(null);
    }

    public double headLookYawSign() {
        return headLookYawSign(null);
    }

    public double headLookPitchSign() {
        return headLookPitchSign(null);
    }

    public boolean headLookFollowPitch() {
        return headLookFollowPitch(null);
    }

    public boolean lookAtNearbyPlayer() {
        return lookAtNearbyPlayer(null);
    }

    public double headLookMaxYawDeg() {
        return headLookMaxYawDeg(null);
    }

    public double headLookMaxPitchDeg() {
        return headLookMaxPitchDeg(null);
    }

    public int mainTransitionTicks() {
        return mainTransitionTicks(null);
    }

    public int jumpTransitionTicks() {
        return jumpTransitionTicks(null);
    }

    public boolean freezePawTransition() {
        return freezePawTransition(null);
    }

    public double animationSpeed(String animationName) {
        return animationSpeed(null, animationName);
    }

    public double walkAnimationSpeed() {
        return animationSpeed(null, "main_walk");
    }

    public double modelRenderScale() {
        return modelRenderScale(null);
    }

    /** 调试用：当前生效设置的摘要（便于日志/自检）。 */
    public synchronized String describe(String speciesId) {
        Map<String, String> summary = new TreeMap<>();
        summary.put("headLook", headLookEnabled(speciesId) + "/" + headLookYawAxis(speciesId)
                + "/" + headLookPitchAxis(speciesId)
                + " signs " + trim(headLookYawSign(speciesId)) + "," + trim(headLookPitchSign(speciesId))
                + " max " + trim(headLookMaxYawDeg(speciesId)) + "," + trim(headLookMaxPitchDeg(speciesId)));
        summary.put("transition", mainTransitionTicks(speciesId) + "t freezePaw=" + freezePawTransition(speciesId));
        summary.put("walkSpeed", trim(animationSpeed(speciesId, "main_walk")));
        summary.put("renderScale", trim(modelRenderScale(speciesId)));
        summary.put("speciesOverrides", String.valueOf(speciesValues.getOrDefault(speciesId, Map.of()).size()));
        return summary.toString();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static double parse(String raw, double fallback) {
        try {
            return raw == null ? fallback : Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
