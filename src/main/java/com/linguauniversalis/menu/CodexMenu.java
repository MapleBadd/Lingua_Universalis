package com.linguauniversalis.menu;

import com.linguauniversalis.core.encyclopedia.Encyclopedia;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 图鉴界面菜单（无槽位）：把"这个玩家已经解锁了哪些条目"从服务端带到客户端。
 *
 * <p>为什么必须走网络：{@code PlayerEncyclopedia} 在服务端（{@code codex/ServerCodex}），
 * 客户端没有这份数据；不传的话界面只能显示全 `？`。
 *
 * <p>载荷很小：**物种 id → 已解锁 Kind 的位掩码**（一个 int）。所有物种都会列出来，
 * 没记录的物种掩码为 0（界面显示 `？？？`），这样玩家能看到"还有几只没发现"。
 */
public class CodexMenu extends AbstractContainerMenu {
    /** 服务端模式用：方块坐标（用于 stillValid 判定）。 */
    private final BlockPos pos;
    /** 物种 id → Kind 位掩码（**可变**：菜单数据槽会增量更新它）。 */
    private final Map<String, Integer> known;
    /** 物种顺序（数据槽索引 → 物种 id），用于 setData 反查。 */
    private final java.util.List<String> speciesOrder = new java.util.ArrayList<>();
    /** 服务端构造：把该玩家的图鉴打包进来。 */
    public CodexMenu(int containerId, Inventory playerInventory, BlockPos pos, Map<String, Integer> known) {
        super(LURegistries.CODEX_MENU.get(), containerId);
        this.pos = pos;
        this.known = new LinkedHashMap<>(known);
        this.speciesOrder.addAll(known.keySet());
    }

    /** 客户端构造：从缓冲读掩码表。 */
    public static CodexMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        Map<String, Integer> known = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            known.put(buf.readUtf(), buf.readVarInt());
        }
        return new CodexMenu(containerId, playerInventory, pos, known);
    }

    /** 服务端：给玩家打开图鉴（把方块坐标与掩码表一起写进缓冲）。 */
    public static void open(ServerPlayer player, BlockPos pos, Map<String, Integer> known) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CodexMenu(containerId, inventory, pos, known),
                net.minecraft.network.chat.Component.translatable("container.lingua_universalis.codex")),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeVarInt(known.size());
                    known.forEach((species, mask) -> {
                        buffer.writeUtf(species);
                        buffer.writeVarInt(mask);
                    });
                });
    }

    /**
     * 菜单数据槽同步：服务端 {@code setData(index, mask)} → 客户端这个回调被触发。
     *
     * <p>这样图鉴的"解锁进度"能**增量刷新**（观测到新条目时界面即时变化，不用关掉重开），
     * 而且不用自定义网络包 —— 走原版容器数据槽。
     */
    @Override
    public void setData(int id, int value) {
        if (id >= 0 && id < this.speciesOrder.size()) {
            this.known.put(this.speciesOrder.get(id), value);
        }
    }

    /** 物种顺序（供服务端推送时对齐数据槽索引）。 */
    public java.util.List<String> speciesOrder() {
        return java.util.List.copyOf(this.speciesOrder);
    }

    /** 该物种已解锁的条目类型（客户端也调这个）。 */
    public Set<Encyclopedia.Kind> knownKinds(String speciesId) {
        Integer mask = this.known.get(speciesId);
        if (mask == null || mask == 0) {
            return EnumSet.noneOf(Encyclopedia.Kind.class);
        }
        Set<Encyclopedia.Kind> kinds = EnumSet.noneOf(Encyclopedia.Kind.class);
        for (Encyclopedia.Kind kind : Encyclopedia.Kind.values()) {
            if ((mask & (1 << kind.ordinal())) != 0) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** 是否已经解锁过任意条目（用于列表上的 ●/◐/○）。 */
    public int knownMask(String speciesId) {
        Integer mask = this.known.get(speciesId);
        return mask == null ? 0 : mask;
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 台子被拆/书被取走 → 菜单自动失效（避免服务端一直挂着一个无槽位菜单）
        return player.level().getBlockEntity(this.pos)
                instanceof com.linguauniversalis.block.ScriptoriumBlockEntity desk && desk.hasBook();
    }
}
