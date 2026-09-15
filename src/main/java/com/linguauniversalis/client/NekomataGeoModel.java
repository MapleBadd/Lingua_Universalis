package com.linguauniversalis.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.resources.Identifier;

/**
 * 猫又 GeckoLib 模型（模型/动画/贴图均来自 {@code 美术资源} 中的 GeckoLib 资源）。
 *
 * <p>资源路径（<b>GeckoLib 5.5.5 实测约定</b>，与文件路径不同！GeckoLib 只扫描这两个目录，
 * 并把路径前缀/后缀剥掉当缓存键）：
 * <ul>
 *   <li>模型文件 <b>必须</b>放 {@code assets/<ns>/geckolib/models/nekomata.geo.json}
 *       → 缓存键 {@code <ns>:nekomata}（剥掉 {@code geckolib/models/} 与 {@code .geo.json}）</li>
 *   <li>动画文件 <b>必须</b>放 {@code assets/<ns>/geckolib/animations/nekomata.animation.json}
 *       → 缓存键 {@code <ns>:nekomata}（剥掉 {@code geckolib/animations/} 与 {@code .animation.json}）</li>
 *   <li>贴图 {@code lingua_universalis:textures/entity/nekomata.png}（贴图走原版渲染管线，需<b>完整</b>路径含后缀）</li>
 * </ul>
 * 所以模型与动画的 Identifier <b>都是</b> {@code lingua_universalis:nekomata}（两者是不同的缓存，不冲突）。
 * 放错目录（例如旧的 {@code geo/}、{@code animations/}）会在日志里刷
 * {@code Loaded 0 models and 0 animations from resources} 与 {@code Unable to find model/animation}，
 * 实体渲染成紫黑色占位块。
 *
 * <p>伪装形态（猫形）：<b>当前不走 GeckoLib</b> —— 猫形由 {@link CatFormRenderer} 用原版猫模型、
 * 原版全黑猫贴图与原版猫动画渲染（猫形个体不会进入 GeckoLib 的 {@code submit}）。
 * 下面保留的猫形 geo 资源分支是<b>预留</b>：若日后改主意要做专属猫形 geo 模型，
 * 放入 {@code geo/nekomata_cat.geo.json} + {@code textures/entity/nekomata_cat.png}
 * 并让渲染器不再分派到 {@link CatFormRenderer} 即可启用。
 */
public class NekomataGeoModel extends GeoModel<MonsterGirlEntity> {
    /**
     * 人形资源键（见类注释：模型与动画的键都是 {@code nekomata}，二者是不同缓存）。
     *
     * <p>对应文件：{@code geckolib/models/nekomata.geo.json}、{@code geckolib/animations/nekomata.animation.json}。
     */
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:nekomata");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:nekomata");
    private static final Identifier TEXTURE = Identifier.parse("lingua_universalis:textures/entity/nekomata.png");

    /** 伪装（猫形）资源：<b>预留</b>（当前猫形走原版猫渲染，见类注释）。键约定同上。 */
    private static final Identifier CAT_MODEL = Identifier.parse("lingua_universalis:nekomata_cat");
    private static final Identifier CAT_TEXTURE =
            Identifier.parse("lingua_universalis:textures/entity/nekomata_cat.png");
    /** 猫形模型的真实文件路径（存在性探测用；键与文件路径不是一回事）。 */
    private static final Identifier CAT_MODEL_FILE =
            Identifier.parse("lingua_universalis:geckolib/models/nekomata_cat.geo.json");

    /** 渲染状态数据键：是否伪装（猫形）。 */
    public static final DataTicket<Boolean> CAT_FORM = DataTicket.create("lu_cat_form", Boolean.class);

    @Override
    public void addAdditionalStateData(MonsterGirlEntity animatable, Object relatedObject, GeoRenderState renderState) {
        renderState.addGeckolibData(CAT_FORM, animatable.isCatForm());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        if (isCatForm(renderState) && resourceExists(CAT_MODEL_FILE)) {
            return CAT_MODEL;
        }
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        if (isCatForm(renderState) && resourceExists(CAT_TEXTURE)) {
            return CAT_TEXTURE;
        }
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MonsterGirlEntity animatable) {
        return ANIMATION;
    }

    private static boolean isCatForm(GeoRenderState renderState) {
        return Boolean.TRUE.equals(renderState.getOrDefaultGeckolibData(CAT_FORM, Boolean.FALSE));
    }

    /**
     * 资源是否存在（缺少猫形资源时不影响人形渲染）。
     *
     * <p>注意传入的必须是<b>真实文件路径</b>（含后缀），不能直接用上面的 GeckoLib Identifier 约定
     * ——除贴图外，模型/动画的 Identifier 与文件路径并不相同。
     */
    private static boolean resourceExists(Identifier fileId) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return false;
        }
        return minecraft.getResourceManager().getResource(fileId).isPresent();
    }
}
