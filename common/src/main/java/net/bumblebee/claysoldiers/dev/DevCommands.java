package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Development-only commands, registered under {@code /csr dev}.
 * <p>
 * Only registered when {@code ClaySoldiersCommon.PLATFORM.isDevEnv()} is true
 * (on NeoForge: {@code !FMLLoader.isProduction()}), so they never exist in a normal player install.
 */
public final class DevCommands {
    private DevCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ClaySoldiersCommon.MOD_ID)
                .then(Commands.literal("dev")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("scenario")
                                .then(Commands.literal("build_test").executes(c -> buildTest(c, true)))
                                .then(Commands.literal("build_test_empty").executes(c -> buildTest(c, false)))
                        )
                )
        );
    }

    private static int buildTest(CommandContext<CommandSourceStack> context, boolean stocked) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This scenario must be run by a player."));
            return 0;
        }

        DevScenarios.BuildTestResult result;
        try {
            result = DevScenarios.buildTest(source.getLevel(), player, player.blockPosition(), player.getDirection(), stocked);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        StringBuilder message = new StringBuilder("Scenario ").append(stocked ? "build_test" : "build_test_empty").append(" set up:\n");
        message.append("- Chest at ").append(result.chest().toShortString())
                .append(stocked ? " with " + result.chestItems() : " (empty)").append('\n');
        message.append("- Easel at ").append(result.easel().toShortString())
                .append(" with blueprint ").append(DevScenarios.SMALL_HOUSE).append(" (built on the far side of the easel)\n");
        message.append("- Team ").append(DevScenarios.RED_TEAM).append(" is loyal to ").append(player.getName().getString()).append('\n');
        message.append("- ").append(result.soldiers().size()).append(" soldiers:");
        result.soldiers().forEach(soldier -> message.append("\n    ").append(DevScenarios.describeWorker(soldier)));

        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return result.soldiers().size();
    }
}
