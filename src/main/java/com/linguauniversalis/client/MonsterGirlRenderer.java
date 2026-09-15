package com.linguauniversalis.client;

import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * 占位渲染器（26.2 状态式渲染管线）：人形复用原版"玩家"模型 + 村民贴图；
 * <b>伪装形态（猫形）复用原版猫模型 + 原版全黑猫贴图</b>（设计 §12 猫又）。
 * 正式模型/动画（GeckoLib）就绪后替换本渲染器。
 */
public class MonsterGirlRenderer
        extends MobRenderer<MonsterGirlEntity, MonsterGirlRenderState, MonsterGirlFormModel> {

    /** 人形占位贴图（村民）。 */
    private static final Identifier HUMAN_TEXTURE =
            Identifier.parse("minecraft:textures/entity/villager/villager.png");
    /** 伪装形态占位贴图（原版全黑猫）。 */
    private static final Identifier CAT_TEXTURE =
            Identifier.parse("minecraft:textures/entity/cat/cat_all_black.png");

    public MonsterGirlRenderer(EntityRendererProvider.Context context) {
        super(context, new MonsterGirlFormModel(context), 0.5f);
    }

    @Override
    public MonsterGirlRenderState createRenderState() {
        return new MonsterGirlRenderState();
    }

    @Override
    public void extractRenderState(MonsterGirlEntity entity, MonsterGirlRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.catForm = entity.isCatForm();
        state.shadowRadius = state.catForm ? 0.3f : 0.5f; // 猫形影子更小
    }

    @Override
    public Identifier getTextureLocation(MonsterGirlRenderState state) {
        return state.catForm ? CAT_TEXTURE : HUMAN_TEXTURE;
    }
}
