package com.linguauniversalis.command;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.companion.CompanionRegistry;
import com.linguauniversalis.core.rule.RelationshipRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LUEntities;
import com.linguauniversalis.registry.LURegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 统一命令根 {@code /lu}：
 *
 * <pre>
 *   /lu selftest                                 -- 自检（所有人）
 *   /lu check                                    -- 查看自己的图鉴进度 / 已伙伴 / 已誓约（所有人）
 *   /lu bond &lt;实体&gt; info|bond                    -- 查看 / 绑定为伙伴（管理员）
 *   /lu bond &lt;实体&gt; aff|mood|satiety|synergy &lt;值&gt;
 *   /lu codex unlock|clear [玩家]                 -- 图鉴调试（管理员，默认自己）
 *   /lu revive                                   -- 手持充能命缕，按快照复活（管理员）
 * </pre>
 *
 * <p>权限口径：{@code bond / codex / revive} 需要 op 等级 2（{@code LEVEL_GAMEMASTERS}）；
 * {@code selftest / check} 任何人可用。
 */
public final class LUCommands {
    private LUCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lu");

        // ---- 无门槛 ----
        root.then(Commands.literal("help").executes(LUCommands::help));
        // 所有人可用：发一本玩法介绍成书
        root.then(Commands.literal("guide").executes(LUCommands::guide));
        root.then(Commands.literal("selftest").executes(LUCommands::selftest));
        root.then(Commands.literal("check").executes(LUCommands::check));
        // 管理员：查看**别人**的图鉴/伙伴状态
        root.then(Commands.literal("opcheck")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(LUCommands::opcheck)));

        // ---- 管理员：bond ----
        var targets = Commands.argument("target", EntityArgument.entities());
        root.then(Commands.literal("bond")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(targets
                        .then(Commands.literal("info").executes(LUCommands::info))
                        .then(Commands.literal("bond").executes(LUCommands::bond))
                        .then(intStat("aff", ModConstants.AFFECTION_MAX))
                        .then(intStat("mood", ModConstants.MOOD_MAX))
                        .then(intStat("satiety", ModConstants.SATIETY_MAX))
                        .then(intStat("synergy", ModConstants.SYNERGY_MAX))));

        // ---- 管理员：codex ----
        root.then(Commands.literal("codex")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("unlock")
                        .executes(ctx -> codexUnlock(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> codexUnlock(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> codexClear(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> codexClear(ctx, EntityArgument.getPlayer(ctx, "player")))))
                // 跨存档交换：导出/导入到全局目录（仅管理员）
                .then(Commands.literal("export").executes(LUCommands::codexExport))
                .then(Commands.literal("import").executes(LUCommands::codexImport)));

        // ---- 管理员：revive ----
        // 管理员：数据包加载结果（物种档案）
        root.then(Commands.literal("species")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(LUCommands::speciesList)));

        root.then(Commands.literal("revive")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(LUCommands::revive));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intStat(String name, int max) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(0, max))
                        .executes(ctx -> setStat(ctx, name)));
    }

    // ------------------------------------------------------------------ 无门槛
    private static int selftest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long day = src.getLevel().getOverworldClockTime() / ModConstants.DAY_TICKS;
        src.sendSystemMessage(Component.literal(
                "[lu] selftest OK | species=" + com.linguauniversalis.core.species.SpeciesRegistry.all().size()
                        + " moodTemplates=" + com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.ids().size()
                        + " day=" + day
                        + " entity=" + LUEntities.MONSTER_GIRL.get().getDescriptionId()));
        try {
            ServerPlayer player = src.getPlayerOrException();
            src.sendSystemMessage(Component.literal(
                    "[lu] " + com.linguauniversalis.codex.ServerCodex.describe(player.getStringUUID())));
        } catch (CommandSyntaxException ignored) {
            // 控制台执行则跳过玩家图鉴概览
        }
        return 1;
    }

    /**
     * {@code /lu help}：列出所有命令与用法（任何人可用）。
     */
    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String[] lines = {
                tr("command.lingua_universalis.help.title"),
                tr("command.lingua_universalis.help.help"),
                tr("command.lingua_universalis.help.guide"),
                tr("command.lingua_universalis.help.check"),
                tr("command.lingua_universalis.help.selftest"),
                tr("command.lingua_universalis.help.admin"),
                tr("command.lingua_universalis.help.opcheck"),
                tr("command.lingua_universalis.help.bond_info"),
                tr("command.lingua_universalis.help.bond_bond"),
                tr("command.lingua_universalis.help.bond_set"),
                tr("command.lingua_universalis.help.codex_unlock"),
                tr("command.lingua_universalis.help.codex_clear"),
                tr("command.lingua_universalis.help.codex_transfer"),
                tr("command.lingua_universalis.help.species_list"),
                tr("command.lingua_universalis.help.revive"),
        };
        for (String line : lines) {
            src.sendSystemMessage(Component.literal(line));
        }
        return 1;
    }


    /** 命令帮助里的短文本：直接取语言文件（服务端也要能显示当前语言）。 */
    private static String tr(String key) {
        return net.minecraft.network.chat.Component.translatable(key).getString();
    }

    /** {@code /lu codex export}：把执行者（或指定玩家）的图鉴导出到全局目录，供其它存档导入。 */
    private static int codexExport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.translatable("command.lingua_universalis.codex.export.no_player"));
            return 0;
        }
        var path = com.linguauniversalis.codex.ServerCodex.exportToGlobal(player.getStringUUID());
        if (path == null) {
            src.sendFailure(Component.translatable("command.lingua_universalis.codex.export.failed"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.lingua_universalis.codex.export.ok", path.toString()), false);
        return 1;
    }

    /** {@code /lu codex import}：从全局目录把图鉴导入**当前存档**（覆盖该玩家在本存档的图鉴）。 */
    private static int codexImport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.translatable("command.lingua_universalis.codex.import.no_player"));
            return 0;
        }
        if (!com.linguauniversalis.codex.ServerCodex.importFromGlobal(player.getStringUUID())) {
            src.sendFailure(Component.translatable("command.lingua_universalis.codex.import.failed"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.lingua_universalis.codex.import.ok"), false);
        return 1;
    }

    /** {@code /lu species list}：列出物种总数、数据包加载结果与问题清单。 */
    private static int speciesList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSystemMessage(Component.translatable("command.lingua_universalis.species.total", com.linguauniversalis.core.species.SpeciesRegistry.all().size()));
        var loaded = com.linguauniversalis.event.SpeciesDataLoader.loadedIds();
        src.sendSystemMessage(Component.literal(
                Component.translatable("command.lingua_universalis.species.pack", loaded.size(), String.join(", ", loaded)).getString()));
        var problems = com.linguauniversalis.event.SpeciesDataLoader.problems();
        if (problems.isEmpty()) {
            src.sendSystemMessage(Component.translatable("command.lingua_universalis.species.no_issues"));
        } else {
            for (String problem : problems) {
                src.sendSystemMessage(Component.literal("[lu] ! " + problem));
            }
        }
        return loaded.size();
    }

    /** {@code /lu guide}：把玩法介绍成书发给执行者（无权限要求）。 */
    private static int guide(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.translatable("command.lingua_universalis.guide.no_player"));
            return 0;
        }
        ItemStack book = com.linguauniversalis.util.GuideBook.create();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        src.sendSuccess(() -> Component.translatable("command.lingua_universalis.guide.ok", com.linguauniversalis.util.GuideBook.TITLE), false);
        return 1;
    }


    /** {@code /lu species list}：列出物种总数、数据包加载结果与问题清单。 */
    private static int opcheck(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ctx.getSource().sendSystemMessage(Component.literal(
                "[lu] === " + target.getName().getString() + " ==="));
        return checkFor(ctx.getSource(), target);
    }
    /**
     * {@code /lu check}：查看自己的图鉴进度、已伙伴的魔物娘、已誓约（契约）的魔物娘。
     *
     * <p>打印前先做一次世界同步（把当前已加载、绑定到自己的实体并进登记表），保证清单不会漏。
     */
    private static int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return checkFor(ctx.getSource(), ctx.getSource().getPlayerOrException());
    }

    /** check 的实际实现（自己 / 别人共用）。 */
    private static int checkFor(CommandSourceStack src, ServerPlayer player) {
        int scanned = CompanionRegistry.syncFromWorld(player, src.getServer());
        CompanionRegistry.save(player);

        List<CompanionRegistry.Entry> entries = CompanionRegistry.list(player.getStringUUID());
        long vowed = entries.stream().filter(CompanionRegistry.Entry::vowed).count();
        src.sendSystemMessage(Component.translatable("command.lingua_universalis.check.codex", com.linguauniversalis.codex.ServerCodex.describe(player.getStringUUID())));
        src.sendSystemMessage(Component.translatable("command.lingua_universalis.check.companions", entries.size(), vowed, scanned));
        if (entries.isEmpty()) {
            src.sendSystemMessage(Component.literal(
                    net.minecraft.network.chat.Component.translatable("command.lingua_universalis.check.none").getString()));
            return 0;
        }
        for (CompanionRegistry.Entry entry : entries) {
            src.sendSystemMessage(Component.literal(
                    "[lu]   " + (entry.vowed() ? "\u2605 " : "\u00B7 ") + entry.name()
                            + " [" + entry.speciesId() + "] " + entry.entityUuid()));
        }
        return entries.size();
    }

    // ------------------------------------------------------------------ 图鉴
    private static int codexUnlock(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit)
            throws CommandSyntaxException {
        ServerPlayer target = explicit != null ? explicit : ctx.getSource().getPlayerOrException();
        var book = com.linguauniversalis.codex.ServerCodex.of(target.getStringUUID());
        int speciesCount = 0;
        for (com.linguauniversalis.core.species.SpeciesProfile profile
                : com.linguauniversalis.core.species.SpeciesRegistry.all()) {
            for (com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind kind
                    : com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind.values()) {
                book.add(profile.id(), kind);
            }
            speciesCount++;
        }
        com.linguauniversalis.codex.ServerCodex.save(target);
        ctx.getSource().sendSystemMessage(Component.literal(
                "[lu] unlocked all codex entries for " + target.getName().getString()
                        + " (" + speciesCount + " species) - reopen the codex"));
        return speciesCount;
    }

    private static int codexClear(CommandContext<CommandSourceStack> ctx, ServerPlayer explicit)
            throws CommandSyntaxException {
        ServerPlayer target = explicit != null ? explicit : ctx.getSource().getPlayerOrException();
        com.linguauniversalis.codex.ServerCodex.clear(target.getStringUUID());
        com.linguauniversalis.codex.ServerCodex.save(target);
        ctx.getSource().sendSystemMessage(Component.literal(
                "[lu] codex cleared for " + target.getName().getString()));
        return 1;
    }

    // ------------------------------------------------------------------ 复活
    private static int revive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!(player.level() instanceof ServerLevel server)) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != LURegistries.FATUM_FILUM.get() || !held.has(LURegistries.GIRL_SNAPSHOT.get())) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "[lu] hold a Fatum Filum that carries a snapshot"));
            return 0;
        }
        String snapshot = held.get(LURegistries.GIRL_SNAPSHOT.get());
        if (!Boolean.TRUE.equals(held.get(LURegistries.FILUM_CHARGED.get()))) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "[lu] not charged yet - strike it with lightning first"));
            return 0;
        }
        MonsterGirlEntity girl = new MonsterGirlEntity(LUEntities.MONSTER_GIRL.get(), server);
        if (!girl.applySnapshot(snapshot)) {
            ctx.getSource().sendSystemMessage(Component.literal("[lu] malformed snapshot"));
            return 0;
        }
        girl.snapTo(player.getX(), player.getY() + 0.5D, player.getZ(), player.getYRot(), 0.0F);
        server.addFreshEntity(girl);
        held.shrink(1);
        ctx.getSource().sendSystemMessage(Component.literal("[lu] revived " + girl.speciesId()));
        return 1;
    }

    // ------------------------------------------------------------------ 伙伴
    private static MonsterGirlEntity girlOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var entities = EntityArgument.getEntities(ctx, "target");
        for (var entity : entities) {
            if (entity instanceof MonsterGirlEntity girl) {
                return girl;
            }
        }
        ctx.getSource().sendSystemMessage(Component.literal("[lu] target is not a monster girl"));
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MonsterGirlEntity girl = girlOf(ctx);
        if (girl == null) {
            return 0;
        }
        var st = girl.girlState();
        ctx.getSource().sendSystemMessage(Component.literal(
                "[lu] species=" + girl.speciesId() + " stage=" + st.stageId()
                        + " aff=" + st.affection() + " mood=" + st.mood()
                        + " sat=" + st.satiety() + " syn=" + st.synergy()
                        + " bound=" + st.boundPlayerUuid() + " obey=" + girl.obeyModeId()
                        + " dormant=" + st.dormant() + " hp=" + girl.getHealth() + "/" + girl.getMaxHealth()));
        return 1;
    }

    private static int bond(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MonsterGirlEntity girl = girlOf(ctx);
        if (girl == null) {
            return 0;
        }
        girl.girlState().setBoundPlayerUuid(player.getStringUUID());
        girl.girlState().setAffection(150);
        girl.girlState().setCompanionUnlocked(true);
        RelationshipRules.enforceCoherence(girl.girlState());
        CompanionRegistry.register(player.getStringUUID(), girl);
        CompanionRegistry.save(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[lu] bound to " + player.getName().getString() + " (stage=companion, aff=150)"), true);
        return 1;
    }

    private static int setStat(CommandContext<CommandSourceStack> ctx, String stat) throws CommandSyntaxException {
        MonsterGirlEntity girl = girlOf(ctx);
        if (girl == null) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        var st = girl.girlState();
        switch (stat) {
            case "aff" -> st.setAffection(value);
            case "mood" -> st.setMood(value);
            case "satiety" -> st.setSatiety(value);
            case "synergy" -> st.setSynergy(value);
            default -> {
                return 0;
            }
        }
        ctx.getSource().sendSystemMessage(Component.literal("[lu] " + stat + " = " + value));
        return 1;
    }
}
