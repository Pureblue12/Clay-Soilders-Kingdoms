package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers the development-only {@code /csr dev} commands. All of them come from {@link DevToolRegistry#defaults()};
 * add new ones there, never directly to the dispatcher.
 * <p>
 * Only called when {@code ClaySoldiersCommon.PLATFORM.isDevEnv()} is true
 * (on NeoForge: {@code !FMLLoader.isProduction()}), so the commands never exist in a normal player install.
 */
public final class DevCommands {
    private DevCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DevToolRegistry.defaults().registerCommands(dispatcher);
    }
}
