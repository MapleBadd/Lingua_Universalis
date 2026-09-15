package com.linguauniversalis.menu;

import com.linguauniversalis.core.behavior.InventoryRules;
import com.linguauniversalis.core.gui.GirlInventoryLayout;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

/**
 * 魔物娘 GUI：**快捷栏 + 背包** + 玩家背包（设计 §6）。
 *
 * <p>打开方式：手持**初稿**右键**伙伴档**魔物娘（非伙伴不开放）。
 *
 * <p>槽位约定：{@code 0..快捷栏数-1} = 快捷栏（物种档案命名槽：主手/副手/附肢…），
 * 之后是背包；再往后 36 格是玩家背包（3 行 + 快捷栏行，原版口径）。
 *
 * <p>客户端由 {@link #fromNetwork} 从网络缓冲解出实体 id 与物种 id —— 物种 id 决定**槽位布局**，
 * 必须两端一致；实体暂时取不到时（极少数时序）用一个同尺寸的空容器兜底，避免两端槽位数不一致。
 */
public class GirlInventoryMenu extends AbstractContainerMenu {
    private final Container container;
    private final SpeciesProfile profile;
    @Nullable
    private final MonsterGirlEntity girl;

    /** 服务端：直接引用实体（容器就是她的快捷栏 + 背包）。 */
    public GirlInventoryMenu(int containerId, Inventory playerInventory, MonsterGirlEntity girl) {
        this(containerId, playerInventory, girl.profile(), girl.girlInventory(), girl);
    }

    /** 客户端：从缓冲解出实体 id 与物种 id。 */
    public static GirlInventoryMenu fromNetwork(int containerId, Inventory playerInventory,
            RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String speciesId = buf.readUtf();
        SpeciesProfile profile = SpeciesRegistry.contains(speciesId)
                ? SpeciesRegistry.get(speciesId)
                : SpeciesRegistry.all().iterator().next();
        MonsterGirlEntity girl = playerInventory.player.level().getEntity(entityId)
                instanceof MonsterGirlEntity resolved ? resolved : null;
        Container container = girl != null
                ? girl.girlInventory()
                : new SimpleContainer(Math.max(1, InventoryRules.totalSlots(profile)));
        return new GirlInventoryMenu(containerId, playerInventory, profile, container, girl);
    }

    /** 主构造：两端共用（布局只依赖物种档案）。 */
    public GirlInventoryMenu(int containerId, Inventory playerInventory, SpeciesProfile profile,
            Container container, @Nullable MonsterGirlEntity girl) {
        super(LURegistries.GIRL_INVENTORY_MENU.get(), containerId);
        this.profile = profile;
        this.container = container;
        this.girl = girl;

        int girlSlots = InventoryRules.totalSlots(profile);
        checkContainerSize(container, girlSlots);
        for (int i = 0; i < girlSlots; i++) {
            addSlot(new Slot(container, i,
                    GirlInventoryLayout.girlSlotX(profile, i),
                    GirlInventoryLayout.girlSlotY(profile, i)));
        }
        addStandardInventorySlots(playerInventory, GirlInventoryLayout.PLAYER_INV_X,
                GirlInventoryLayout.playerInventoryY(InventoryRules.backpackSlots(profile)));
    }

    /** 布局用档案（客户端屏幕按它算面板高度/标签位置）。 */
    public SpeciesProfile profile() {
        return profile;
    }

    @Nullable
    public MonsterGirlEntity girl() {
        return girl;
    }

    /** 她的容器（服务端逻辑要用时）。 */
    public Container container() {
        return container;
    }

    @Override
    public boolean stillValid(Player player) {
        // 她必须还在、还活着，且玩家没走远（8 格）——走远/她消失即关界面
        return girl != null && girl.isAlive() && !girl.isRemoved()
                && player.distanceToSqr(girl) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int girlSlots = InventoryRules.totalSlots(profile);
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < girlSlots) {
            // 她的快捷栏/背包 → 玩家背包
            if (!moveItemStackTo(stack, girlSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, girlSlots, false)) {
            // 玩家背包 → 她的快捷栏/背包
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
