package net.neoforged.neoforge.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/** 桩：签名对齐 26.2 官方 RegisterCommandsEvent。 */
public class RegisterCommandsEvent {
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
