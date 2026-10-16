package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.commands.KingdomCommand;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * The "Economy" dev book category (stage 5, {@link KingdomEconomy}): shortcuts to watch a kingdom's economy without
 * waiting hours. They act on the kingdom you stand in, else the nearest one.
 */
final class EconomyTools {
    static final String ECONOMY = "economy";

    private EconomyTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(ECONOMY, "Economy",
                "After its keep a kingdom builds a lumber camp, a wheat field and a cottage paid with its own wood and food; woodcutters and farmers gather.", false));
        registry.addTool(new DevTool("economy_now", ECONOMY, Component.literal("Look after it now"),
                "The kingdom looks after its keep and economy at once: plans the next building, stocks the chest, picks builders and workers (dev).",
                List.of("economy", "now"), false, null, EconomyTools::now));
        registry.addTool(new DevTool("economy_finish", ECONOMY, Component.literal("Finish the current building"),
                "Pastes the building the kingdom is building (the keep too) and counts it as built; a keep crowns the king (dev).",
                List.of("economy", "finish"), false, null, EconomyTools::finish));
        registry.addTool(new DevTool("economy_supply", ECONOMY, Component.literal("Supply the next building"),
                "Adds the logs and wheat the cottage still waits for to the stockpile (dev).",
                List.of("economy", "supply"), false, null, EconomyTools::supply));
        registry.addTool(new DevTool("economy_ripen", ECONOMY, Component.literal("Ripen the fields"),
                "Makes every crop in the kingdom's fields ripe (dev).",
                List.of("economy", "ripen"), false, null, EconomyTools::ripen));
    }

    private static int now(CommandContext<CommandSourceStack> context) {
        return withKingdom(context, (level, kingdom) -> {
            KingdomKeep.update(level, kingdom);
            KingdomEconomy.forget(kingdom);
            KingdomEconomy.update(level, kingdom);
            KingdomEconomy.describe(level.getServer(), kingdom).forEach(line -> report(context, "Economy: " + line, false));
        });
    }

    private static int finish(CommandContext<CommandSourceStack> context) {
        return withKingdom(context, (level, kingdom) -> report(context, KingdomEconomy.finishNow(level, kingdom), false));
    }

    private static int supply(CommandContext<CommandSourceStack> context) {
        return withKingdom(context, (level, kingdom) -> report(context, KingdomEconomy.supplyNext(level, kingdom), false));
    }

    private static int ripen(CommandContext<CommandSourceStack> context) {
        return withKingdom(context, (level, kingdom) -> report(context, "Ripened " + KingdomEconomy.ripen(level, kingdom) + " crops.", false));
    }

    private interface KingdomAction {
        void run(ServerLevel level, Kingdom kingdom);
    }

    private static int withKingdom(CommandContext<CommandSourceStack> context, KingdomAction action) {
        Optional<Kingdom> kingdom = KingdomCommand.pick(context.getSource(), null);
        if (kingdom.isEmpty()) {
            return 0;
        }
        ServerLevel level = context.getSource().getServer().getLevel(kingdom.get().dimension());
        if (level == null) {
            report(context, "The kingdom's dimension isn't loaded.", true);
            return 0;
        }
        action.run(level, kingdom.get());
        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> context, String line, boolean problem) {
        Component message = problem ? Component.literal(line).withStyle(ChatFormatting.RED) : Component.literal(line);
        context.getSource().sendSuccess(() -> message, false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }
}
