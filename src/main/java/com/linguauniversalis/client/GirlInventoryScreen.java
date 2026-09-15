package com.linguauniversalis.client;

import com.linguauniversalis.core.behavior.InventoryRules;
import com.linguauniversalis.core.gui.GirlInventoryLayout;
import com.linguauniversalis.menu.GirlInventoryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 魔物娘 GUI 屏幕（快捷栏 + 背包）。
 *
 * <p><b>贴图暂时复用原版漏斗</b>（{@code textures/gui/container/hopper.png}，256×256 画布、
 * 内容 176×133），按"上片段原尺寸 + 中段 1px 灰底纵向拉伸 + 下片段原尺寸"拼接成任意高度的
 * 原版风格面板；每个槽位的底图取该贴图里**现成的 18×18 槽位**（源点 43,19，即原版漏斗第一个槽），
 * 逐个铺到我们自己算出来的槽位坐标上。
 *
 * <p>这样槽位格子与原版完全一致，而面板是任意高度的占位 —— 等美术给出正式 GUI 贴图时，
 * 只需把本类里的贴图 id 与三个片段参数换掉即可（布局数学在
 * {@link GirlInventoryLayout}，与贴图无关）。
 */
public class GirlInventoryScreen extends AbstractContainerScreen<GirlInventoryMenu> {
    /** 占位贴图：原版漏斗 GUI。 */
    private static final Identifier PANEL_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/hopper.png");
    /** 画布尺寸（该贴图是 256×256，内容只占左上 176×133）。 */
    private static final int TEXTURE_SIZE = 256;

    /** 面板上片段：源（0,0）176×18，原尺寸贴。 */
    private static final int PANEL_TOP_V = 0;
    /** 面板中段：源第 17 行（整行纯灰底 + 左右边框），纵向拉伸。 */
    private static final int PANEL_MIDDLE_V = 17;
    /** 面板下片段：源（0,126）176×7，含下边框与投影，原尺寸贴。 */
    private static final int PANEL_BOTTOM_V = 126;

    /** 槽位底图（原版漏斗第一个槽的 18×18 贴图，含上/左边框与右下亮边）。 */
    private static final int SLOT_TILE_U = 43;
    private static final int SLOT_TILE_V = 19;

    /** 分区标签颜色（原版灰字）。 */
    private static final int LABEL_COLOR = 0xFF404040;

    public GirlInventoryScreen(GirlInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
                GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.imageHeight(InventoryRules.backpackSlots(menu.profile())));
        this.titleLabelY = GirlInventoryLayout.TITLE_Y;
        this.inventoryLabelY =
                GirlInventoryLayout.playerInventoryLabelY(InventoryRules.backpackSlots(menu.profile()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        int middleHeight = GirlInventoryLayout.panelMiddleHeight(this.imageHeight);

        // 面板 = 上片段（原尺寸）+ 中段（1px 灰底拉伸）+ 下片段（原尺寸，含边框与投影）
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE, x, y,
                0.0F, (float) PANEL_TOP_V, GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.PANEL_TOP_HEIGHT, GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.PANEL_TOP_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE, x, y + GirlInventoryLayout.PANEL_TOP_HEIGHT,
                0.0F, (float) PANEL_MIDDLE_V, GirlInventoryLayout.PANEL_WIDTH, 1,
                GirlInventoryLayout.PANEL_WIDTH, middleHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE,
                x, y + GirlInventoryLayout.PANEL_TOP_HEIGHT + middleHeight,
                0.0F, (float) PANEL_BOTTOM_V, GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.PANEL_BOTTOM_HEIGHT, GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.PANEL_BOTTOM_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        // 槽位底图：所有槽位（她的快捷栏 + 背包 + 玩家背包）都铺同一块原版槽位贴图
        for (Slot slot : this.menu.slots) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE,
                    x + slot.x - 1, y + slot.y - 1,
                    (float) SLOT_TILE_U, (float) SLOT_TILE_V,
                    GirlInventoryLayout.SLOT_SIZE, GirlInventoryLayout.SLOT_SIZE,
                    GirlInventoryLayout.SLOT_SIZE, GirlInventoryLayout.SLOT_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // 注意：extractLabels 已经被基类平移到面板原点（extractContents 里 pushMatrix+translate），
        // 所以这里用**面板内坐标**（不能加 leftPos/topPos，否则会被偏移两次）。
        graphics.text(this.font, Component.translatable("gui.lingua_universalis.hotbar"),
                GirlInventoryLayout.LABEL_X,
                GirlInventoryLayout.HOTBAR_LABEL_Y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.lingua_universalis.backpack"),
                GirlInventoryLayout.LABEL_X,
                GirlInventoryLayout.BACKPACK_LABEL_Y, LABEL_COLOR);
    }
}
