package com.linguauniversalis.event;

import com.linguauniversalis.codex.ServerCodex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 图鉴数据的持久化钩子。
 *
 * <p>为什么需要它：{@link ServerCodex} 里的图鉴是<b>内存缓存</b>（按玩家 UUID 索引），
 * 关掉世界就没了 —— 表现就是"解锁了、重进又清空"。这里把缓存
 * **编码进玩家的 {@code player.getPersistentData()}**（原版实体 NBT 的 {@code data} 段，随存档保存），
 * 进服时读回、退服/关服/每 30 秒写一次。
 *
 * <p>编码格式见 {@code core/encyclopedia/EncyclopediaPersistence}（二进制 → Base64 字符串）。
 */
public final class CodexPersistenceHandler {
    private CodexPersistenceHandler() {
    }

    /** 周期性写盘的间隔（tick）：30 秒。 */
    private static final int FLUSH_INTERVAL_TICKS = 600;
    private static int tickCounter;
    /** 图鉴增量同步的节拍计数。 */
    private static int openTickCounter;
    /** 图鉴增量同步间隔（tick）。 */
    private static final int OPEN_SYNC_INTERVAL_TICKS = 20;

    /** 玩家 NBT 里的"新手礼包已发"标记。 */
    private static final String TAG_STARTER_KIT = "LUStarterKit";

    /** 服务器启动：按**当前存档**读入图鉴（图鉴不再跟随玩家跨存档）。 */
    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ServerCodex.onServerStarted(event.getServer());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCodex.load(player);
            com.linguauniversalis.core.companion.CompanionRegistry.load(player);
            giveStarterKit(player);
        }
    }

    /**
     * 首次进入世界时发一套新手物品：**初稿 1 本 + 玩法手记（成书）1 本**。
     *
     * <p>只在第一次发（标记写进玩家 NBT 的 {@code LUStarterKit}），之后进退不再重复。
     * 背包满时直接掉在脚边，不会丢。
     */
    private static void giveStarterKit(ServerPlayer player) {
        var data = player.getPersistentData();
        if (data.getBooleanOr(TAG_STARTER_KIT, false)) {
            return;
        }
        data.putBoolean(TAG_STARTER_KIT, true);

        dropOrGive(player, new net.minecraft.world.item.ItemStack(
                com.linguauniversalis.registry.LURegistries.FIRST_DRAFT.get()));
        dropOrGive(player, com.linguauniversalis.util.GuideBook.create());

        player.containerMenu.broadcastChanges();
    }

    /** 塞进背包；塞不下就掉在脚边。 */
    private static void dropOrGive(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCodex.save(player);
            com.linguauniversalis.core.companion.CompanionRegistry.save(player);
        }
    }

    /** 死亡重生：把图鉴带到新的玩家实体上（原版会换一个 ServerPlayer 实例）。 */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer fresh
                && event.getOriginal() instanceof ServerPlayer original) {
            ServerCodex.save(original);
            ServerCodex.load(fresh);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        // 图鉴打开时的**增量同步**：每 20 tick 把解锁掩码推进菜单数据槽（走原版容器数据同步）
        if (++openTickCounter >= OPEN_SYNC_INTERVAL_TICKS) {
            openTickCounter = 0;
            pushOpenCodex(event.getServer());
        }
        if (++tickCounter < FLUSH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        ServerCodex.saveAll(event.getServer());
        com.linguauniversalis.core.companion.CompanionRegistry.saveAll(event.getServer());
    }

    /** 把每位玩家当前打开的图鉴菜单的解锁掩码推给客户端。 */
    public static void pushOpenCodex(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof com.linguauniversalis.menu.CodexMenu menu) {
                pushMasks(player, menu);
            }
        }
    }

    /** 把该玩家的解锁掩码逐个写进菜单数据槽。 */
    public static void pushMasks(ServerPlayer player, com.linguauniversalis.menu.CodexMenu menu) {
        com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia book =
                ServerCodex.of(player.getStringUUID());
        java.util.List<String> order = menu.speciesOrder();
        for (int i = 0; i < order.size(); i++) {
            String species = order.get(i);
            int mask = 0;
            for (com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind kind
                    : com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.values()) {
                if (book.isUnlocked(species, kind)) {
                    mask |= 1 << kind.ordinal();
                }
            }
            menu.setData(i, mask);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ServerCodex.flushFile();
        ServerCodex.saveAll(event.getServer());
        com.linguauniversalis.core.companion.CompanionRegistry.saveAll(event.getServer());
    }
}
