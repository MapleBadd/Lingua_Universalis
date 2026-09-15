package com.linguauniversalis.client;

import com.linguauniversalis.menu.CompanionChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 伙伴宝箱界面。
 *
 * <p>**贴图暂时复用原版大箱子**（{@code textures/gui/container/generic_54.png}，176×222）——
 * 尺寸与 54 格布局逐像素对齐，所以直接整张贴上去即可，槽位底图都在贴图里。
 */
public class CompanionChestScreen extends AbstractContainerScreen<CompanionChestMenu> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int TEXTURE_SIZE = 256;

    public CompanionChestScreen(CompanionChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 222);
        this.inventoryLabelY = 126;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos,
                0.0F, 0.0F, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
