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

/**
 * 魔物娘 GUI 屏幕（快捷栏 + 背包），贴图由美术提供。
 *
 * <p>布局全部来自 {@link GirlInventoryLayout}（坐标对着 {@code girl_inventory.png} 逐像素量过）：
 * <ul>
 *   <li><b>快捷栏</b>（第一行 9 格）：物种档案声明的槽位**从左往右**填 —— 主手 / 副手 / 附肢1 / 附肢2 …；</li>
 *   <li>用不到的格子画上"锁定栏位"图标，悬停提示"无法使用"；</li>
 *   <li>背包 3 行，超出该物种格数的格子同样显示锁定；</li>
 *   <li>鼠标悬停在可用槽位上时，显示该栏位名（主手 / 副手 / 附肢一 …）。</li>
 * </ul>
 */
public class GirlInventoryScreen extends AbstractContainerScreen<GirlInventoryMenu> {
    /** 面板贴图（176×220 内容，画布 256×256）。 */
    private static final Identifier PANEL_TEXTURE =
            Identifier.parse("lingua_universalis:textures/gui/girl_inventory.png");
    /** 锁定栏位图标（16×16）。 */
    private static final Identifier LOCKED_ICON =
            Identifier.parse("lingua_universalis:textures/gui/locked_slot.png");
    private static final int TEXTURE_SIZE = 256;
    /** 分区标签颜色（原版灰字）。 */
    private static final int LABEL_COLOR = 0xFF404040;
    /** 标题颜色（比标签略深）。 */
    private static final int TITLE_COLOR = 0xFF2B2B2B;

    public GirlInventoryScreen(GirlInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
                GirlInventoryLayout.PANEL_WIDTH,
                GirlInventoryLayout.imageHeight(InventoryRules.backpackSlots(menu.profile())));
        this.titleLabelY = GirlInventoryLayout.TITLE_Y;
        this.inventoryLabelY =
                GirlInventoryLayout.playerInventoryLabelY(InventoryRules.backpackSlots(menu.profile()));
    }

    /** 当前物种的快捷栏格数。 */
    private int hotbarSlots() {
        return InventoryRules.hotbarSlots(this.menu.profile());
    }

    /** 当前物种的背包格数。 */
    private int backpackSlots() {
        return InventoryRules.backpackSlots(this.menu.profile());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // 面板整图（美术给的就是固定 176×220）
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE, x, y,
                0.0F, 0.0F, GirlInventoryLayout.PANEL_WIDTH, GirlInventoryLayout.PANEL_HEIGHT,
                GirlInventoryLayout.PANEL_WIDTH, GirlInventoryLayout.PANEL_HEIGHT,
                TEXTURE_SIZE, TEXTURE_SIZE);

        // 用不上的快捷栏格子：画锁定图标
        for (int i = hotbarSlots(); i < GirlInventoryLayout.MAX_HOTBAR_SLOTS; i++) {
            drawLock(graphics, x + GirlInventoryLayout.hotbarCellX(i),
                    y + GirlInventoryLayout.hotbarCellY());
        }
        // 用不上的背包格子：同样画锁定图标（按 3 行铺满）
        int backpackCapacity = GirlInventoryLayout.SLOTS_PER_ROW * 3;
        for (int i = backpackSlots(); i < backpackCapacity; i++) {
            drawLock(graphics, x + GirlInventoryLayout.backpackCellX(i),
                    y + GirlInventoryLayout.backpackCellY(i));
        }
    }

    /** 在格子左上角画 16×16 的锁定图标。 */
    private void drawLock(GuiGraphicsExtractor graphics, int cellX, int cellY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOCKED_ICON, cellX + 1, cellY + 1,
                0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
    }

    /**
     * 标题与分区标签：**全部无阴影**（最后一个参数 false）。
     *
     * <p>不调 {@code super.extractLabels}：它会用带阴影的方式画标题，导致重影/发糊。
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // extractLabels 已被基类平移到面板原点，这里用面板内坐标
        graphics.text(this.font, this.title, GirlInventoryLayout.LABEL_X,
                GirlInventoryLayout.TITLE_Y, TITLE_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.lingua_universalis.hotbar"),
                GirlInventoryLayout.LABEL_X, GirlInventoryLayout.HOTBAR_LABEL_Y, LABEL_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.lingua_universalis.backpack"),
                GirlInventoryLayout.LABEL_X, GirlInventoryLayout.BACKPACK_LABEL_Y, LABEL_COLOR, false);
    }

    /**
     * 悬浮提示：可用快捷栏显示栏位名（主手/副手/附肢一…），锁定格子显示"无法使用"。
     *
     * <p>槽位本身（{@code Slot}）只有真实存在的格子，锁定格子没有对应 {@code Slot}，
     * 因此这里用鼠标坐标反查格位。
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cellX = mouseX - this.leftPos;
        int cellY = mouseY - this.topPos;

        // 快捷栏一行
        if (cellY >= GirlInventoryLayout.hotbarCellY()
                && cellY < GirlInventoryLayout.hotbarCellY() + GirlInventoryLayout.SLOT_SIZE) {
            for (int i = 0; i < GirlInventoryLayout.MAX_HOTBAR_SLOTS; i++) {
                int left = GirlInventoryLayout.hotbarCellX(i);
                if (cellX >= left && cellX < left + GirlInventoryLayout.SLOT_SIZE) {
                    if (i < hotbarSlots()) {
                        graphics.setTooltipForNextFrame(Component.translatable(
                                "girl.lingua_universalis.slot."
                                        + InventoryRules.slotName(this.menu.profile(), i)),
                                mouseX, mouseY);
                    } else {
                        graphics.setTooltipForNextFrame(Component.translatable(
                                "girl.lingua_universalis.slot.locked"), mouseX, mouseY);
                    }
                    return;
                }
            }
        }
        // 背包区域（超出格数的锁定格子）
        int backpackCapacity = GirlInventoryLayout.SLOTS_PER_ROW * 3;
        for (int i = backpackSlots(); i < backpackCapacity; i++) {
            int left = GirlInventoryLayout.backpackCellX(i);
            int top = GirlInventoryLayout.backpackCellY(i);
            if (cellX >= left && cellX < left + GirlInventoryLayout.SLOT_SIZE
                    && cellY >= top && cellY < top + GirlInventoryLayout.SLOT_SIZE) {
                graphics.setTooltipForNextFrame(Component.translatable(
                        "girl.lingua_universalis.slot.locked"), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
