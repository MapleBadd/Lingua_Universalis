package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.core.species.DisguiseForm;
import com.linguauniversalis.core.species.DisguiseRegistry;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.resources.Identifier;

/**
 * <b>通用物种模型</b>：一套代码服务所有用 GeckoLib 的魔物娘（取代"每个物种一个 xGeoModel"）。
 *
 * <p>资源命名约定（只认这一套，按物种 id 推导）：
 * <pre>
 *   geckolib/models/&lt;species&gt;.geo.json        → Identifier {@code lingua_universalis:<species>}
 *   geckolib/animations/&lt;species&gt;.animation.json → Identifier {@code lingua_universalis:<species>}
 *   textures/entity/&lt;species&gt;.png              → Identifier {@code lingua_universalis:textures/entity/<species>.png}
 * </pre>
 * GeckoLib 只扫描 {@code geckolib/models|animations} 且键是"剥掉目录+后缀的裸名字"，
 * 所以模型与动画的 Identifier 同名并不冲突（两者是不同的缓存）。
 *
 * <p><b>变形形态</b>：当前形态（{@link SpeciesRenderer#FORM_ID}）若是 {@code renderKind = gecko}，
 * 就用形态自带的模型/贴图/动画资源（见 {@link DisguiseForm}）—— 也就是"新物种加第二形态
 * 只需丢资源 + 登记形态"，不需要写任何 Java 渲染代码。
 */
public class SpeciesGeoModel extends GeoModel<MonsterGirlEntity> {
    private final String speciesId;
    private final Identifier model;
    private final Identifier animation;
    private final Identifier texture;

    public SpeciesGeoModel(String speciesId) {
        this.speciesId = speciesId;
        this.model = Identifier.parse("lingua_universalis:" + speciesId);
        this.animation = Identifier.parse("lingua_universalis:" + speciesId);
        this.texture = Identifier.parse("lingua_universalis:textures/entity/" + speciesId + ".png");
        this.probeModel = Identifier.parse(
                "lingua_universalis:geckolib/models/" + speciesId + ".geo.json");
        this.probeAnimation = Identifier.parse(
                "lingua_universalis:geckolib/animations/" + speciesId + ".animation.json");
    }

    public String speciesId() {
        return speciesId;
    }

    /** 回退用的占位资源（草方块模型 / 原版草方块贴图 / 空动画），由 PlaceholderGrassModel 复用。 */
    private static final Identifier FALLBACK_MODEL = Identifier.parse("lingua_universalis:placeholder_grass");
    private static final Identifier FALLBACK_TEXTURE =
            Identifier.parse("minecraft:textures/block/grass_block_side.png");
    private static final Identifier FALLBACK_ANIMATION = Identifier.parse("lingua_universalis:placeholder_grass");

    /**
     * 探测用的**真实文件路径**。
     *
     * <p>为什么不能直接拿 {@code model}/{@code animation} 去查：GeckoLib 的短 id
     * （{@code lingua_universalis:nekomata}）由它内部拼成 {@code geckolib/models/nekomata.geo.json}，
     * 而资源管理器只认完整路径。之前直接查短 id → 永远"不存在" → 误判成缺资源并回退成草方块。
     */
    private final Identifier probeModel;
    private final Identifier probeAnimation;

    /** 资源存在性缓存（路径 → 是否存在），避免每帧查资源管理器。 */
    private static final java.util.Map<Identifier, Boolean> EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 资源回退链：物种资源缺失时自动降级到占位资源，而不是渲染不出来/抛异常。
     *
     * <p>判定口径：{@code geckolib/models/<id>.geo.json} 等实际文件是否存在。
     * 缺失时打一条日志（每个资源只记一次），便于发现"美术资源漏放"。
     */
    /** 资源重载（换资源包 / 补上缺失贴图）时清空缓存，否则会一直按"缺失"走占位。 */
    public static void clearResourceCache() {
        EXISTS.clear();
    }

    private static boolean exists(Identifier id, String kind) {
        return EXISTS.computeIfAbsent(id, key -> {
            boolean present = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(key).isPresent();
            if (!present) {
                com.linguauniversalis.LinguaUniversalis.LOGGER.warn(
                        "[render] missing {} {} → falling back to placeholder", kind, key);
            }
            return present;
        });
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        DisguiseForm form = geckoForm(renderState);
        if (form != null && !form.modelPath().isEmpty()) {
            return namedResource(form.modelPath());
        }
        return exists(probeModel, "geo model") ? model : FALLBACK_MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        DisguiseForm form = geckoForm(renderState);
        if (form != null && !form.texturePath().isEmpty()) {
            return Identifier.parse(form.texturePath());
        }
        return exists(texture, "texture") ? texture : FALLBACK_TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MonsterGirlEntity animatable) {
        return exists(probeAnimation, "animation") ? animation : FALLBACK_ANIMATION;
    }

    /** 当前生效的 GeckoLib 变形形态（{@code renderKind = gecko}），没有则 null。 */
    private static DisguiseForm geckoForm(GeoRenderState renderState) {
        String formId = renderState.getOrDefaultGeckolibData(SpeciesRenderer.FORM_ID, "");
        DisguiseForm form = DisguiseRegistry.get(formId);
        return form != null && form.isGecko() ? form : null;
    }

    /** 形态里写的模型名 → GeckoLib Identifier（自动补命名空间）。 */
    private static Identifier namedResource(String path) {
        return path.indexOf(':') >= 0 ? Identifier.parse(path) : Identifier.parse("lingua_universalis:" + path);
    }
}
