package com.linguauniversalis.command;

import com.linguauniversalis.core.ModConstants;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * 开发者调试命令 {@code /lubond}（后续可移除或收进权限）：
 * <pre>
 *   /lubond &lt;entity&gt; info
 *   /lubond &lt;entity&gt; bond            -- 绑定为执行者伙伴（好感150，解锁伙伴）
 *   /lubond &lt;entity&gt; aff|mood|satiety|synergy &lt;0..上限&gt;
 * </pre>
 */
public final class LUCommands {
    private LUCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // TODO 正式版：改为权限检查（26.2 用 PermissionSet，暂以开发命令无门槛处理）
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lubond");
        root.then(Commands.literal("selftest").executes(LUCommands::selftest));
        var targets = Commands.argument("target", EntityArgument.entities());
        root.then(targets
                .then(Commands.literal("info").executes(LUCommands::info))
                .then(Commands.literal("bond").executes(LUCommands::bond))
                .then(intStat("aff", ModConstants.AFFECTION_MAX))
                .then(intStat("mood", ModConstants.MOOD_MAX))
                .then(intStat("satiety", ModConstants.SATIETY_MAX))
                .then(intStat("synergy", ModConstants.SYNERGY_MAX)));
        dispatcher.register(root);
        // 开发辅助：手持带快照的命缕右键命令 /lurevive → 按快照复活
        dispatcher.register(Commands.literal("lurevive").executes(LUCommands::revive));
    }

    private static int selftest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long day = src.getLevel().getOverworldClockTime() / ModConstants.DAY_TICKS;
        src.sendSystemMessage(Component.literal(
                "[lubond] selftest OK | species=" + com.linguauniversalis.core.species.SpeciesRegistry.all().size()
                        + " moodTemplates=" + com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.ids().size()
                        + " day=" + day
                        + " entity=" + com.linguauniversalis.registry.LUEntities.MONSTER_GIRL.get()
                        .getDescriptionId()));
        try {
            ServerPlayer player = src.getPlayerOrException();
            src.sendSystemMessage(Component.literal(
                    "[lubond] " + com.linguauniversalis.codex.ServerCodex.describe(player.getStringUUID())));
        } catch (CommandSyntaxException ignored) {
            // 控制台执行则跳过玩家图鉴概览
        }
        return 1;
    }

    private static int revive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!(player.level() instanceof ServerLevel server)) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != LURegistries.FATUM_FILUM.get() || !held.has(LURegistries.GIRL_SNAPSHOT.get())) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "[lurevive] hold a Fatum Filum that carries a snapshot"));
            return 0;
        }
        String snapshot = held.get(LURegistries.GIRL_SNAPSHOT.get());
        if (!Boolean.TRUE.equals(held.get(LURegistries.FILUM_CHARGED.get()))) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "[lurevive] not charged yet - strike it with lightning first"));
            return 0;
        }
        MonsterGirlEntity girl = new MonsterGirlEntity(LUEntities.MONSTER_GIRL.get(), server);
        if (!girl.applySnapshot(snapshot)) {
            ctx.getSource().sendSystemMessage(Component.literal("[lurevive] malformed snapshot"));
            return 0;
        }
        girl.setPos(player.getX(), player.getY() + 1, player.getZ());
        girl.setYRot(player.getYRot());
        girl.setXRot(0f);
        if (girl.getAttribute(Attributes.MAX_HEALTH) != null) {
            girl.getAttribute(Attributes.MAX_HEALTH).setBaseValue(girl.profile().maxHealth());
            girl.setHealth(girl.getMaxHealth());
        }
        server.addFreshEntity(girl);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[lurevive] revived "
                + girl.speciesId() + " stage=" + girl.girlState().stageId()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intStat(String name, int max) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(0, max))
                        .executes(ctx -> setStat(ctx, name)));
    }

    private static MonsterGirlEntity girlOf(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        for (Entity entity : EntityArgument.getEntities(ctx, "target")) {
            if (entity instanceof MonsterGirlEntity girl) {
                return girl;
            }
        }
        ctx.getSource().sendSystemMessage(Component.literal("[lubond] no monster girl in selection"));
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MonsterGirlEntity girl = girlOf(ctx);
        if (girl == null) {
            return 0;
        }
        var st = girl.girlState();
        ctx.getSource().sendSystemMessage(Component.literal(
                "[lubond] species=" + girl.speciesId() + " stage=" + st.stageId()
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
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[lubond] bound to " + player.getName().getString() + " (stage=companion, aff=150)"), true);
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
            }
        }
        RelationshipRules.enforceCoherence(st);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[lubond] set " + stat + "=" + value + " (stage=" + st.stageId() + ")"), true);
        return 1;
    }
}
