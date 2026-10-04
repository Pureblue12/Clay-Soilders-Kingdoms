package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.commands.KingdomCommand;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The "Kingdom" dev book category: links to the real {@code /csr kingdom} commands (found, disband, book, status) and
 * dev-only stockpile tools for testing ({@code /csr dev kingdom stock_add|stock_remove|stock_show}). The stockpile
 * tools act on the kingdom you stand in, else the one of a team loyal to you.
 */
final class KingdomTools {
    static final String KINGDOM = "kingdom";
    private static final int DEFAULT_AMOUNT = 16;
    private static final List<String> SAMPLE_RESOURCES = List.of("minecraft:oak_log", "minecraft:wheat", "minecraft:cobblestone");

    private KingdomTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(KINGDOM, "Kingdom",
                "Found near your king (or sneak + right-click it with a gold block).", false));
        registry.addTool(DevTool.link("kingdom_found", KINGDOM, Component.literal("Found kingdom"),
                "Founds a kingdom around the nearest king of a team loyal to you.", "/csr kingdom found", null));
        registry.addTool(DevTool.link("kingdom_book", KINGDOM, Component.literal("Kingdom book"),
                "Gives the book of the kingdom you stand in (or your team's): status, stockpile, buildings, chronicle.", "/csr kingdom book", null));
        registry.addTool(DevTool.link("kingdom_status", KINGDOM, Component.literal("Status in chat"),
                "Prints the kingdom's status in chat.", "/csr kingdom status", null));
        registry.addTool(DevTool.link("kingdom_disband", KINGDOM, Component.literal("Disband kingdom"),
                "Disbands your team's kingdom. Nothing in the world changes.", "/csr kingdom disband", null));
        registry.addTool(new DevTool("kingdom_stock_add", KINGDOM, Component.literal("Stockpile +" + DEFAULT_AMOUNT),
                "Adds to the kingdom's stockpile (dev; optional amount after the resource).",
                List.of("kingdom", "stock_add"), false, resourceArgument(), context -> change(context, true)));
        registry.addTool(new DevTool("kingdom_stock_remove", KINGDOM, Component.literal("Stockpile -" + DEFAULT_AMOUNT),
                "Takes from the stockpile, all or nothing; shows the shortfall (dev).",
                List.of("kingdom", "stock_remove"), false, resourceArgument(), context -> change(context, false)));
        registry.addTool(new DevTool("kingdom_stock_show", KINGDOM, Component.literal("Show stockpile"),
                "Lists the kingdom's stockpile (dev).", List.of("kingdom", "stock_show"), false, null, KingdomTools::show));
    }

    private static DevTool.Argument resourceArgument() {
        return new DevTool.Argument("resource", ResourceLocationArgument.id(), null,
                access -> SAMPLE_RESOURCES.stream().map(id -> new DevTool.Choice(Component.literal(ResourceLocation.parse(id).getPath()), id)).toList())
                .withOptionalExtra("amount", IntegerArgumentType.integer(1, 1_000_000), null);
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        Optional<Kingdom> kingdom = KingdomCommand.pick(context.getSource(), null);
        if (kingdom.isEmpty()) {
            return 0;
        }
        ResourceLocation resource = ResourceLocationArgument.getId(context, "resource");
        int amount = DEFAULT_AMOUNT;
        try {
            amount = IntegerArgumentType.getInteger(context, "amount");
        } catch (IllegalArgumentException noAmount) {
            // default
        }
        var server = context.getSource().getServer();
        if (add) {
            Kingdoms.addResource(server, kingdom.get(), resource, amount);
            report(context, "Stockpile of " + Kingdoms.teamName(server, kingdom.get().team()) + ": +" + amount + " " + resource
                    + " (now " + kingdom.get().resource(resource) + ").", false);
            return 1;
        }
        Kingdom.Withdrawal withdrawal = Kingdoms.removeResource(server, kingdom.get(), resource, amount);
        report(context, withdrawal.done()
                ? "Stockpile of " + Kingdoms.teamName(server, kingdom.get().team()) + ": -" + amount + " " + resource + " (now " + kingdom.get().resource(resource) + ")."
                : "Not enough " + resource + ": short by " + withdrawal.shortfall() + " (has " + kingdom.get().resource(resource) + "). Nothing taken.",
                !withdrawal.done());
        return withdrawal.done() ? 1 : 0;
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        Optional<Kingdom> kingdom = KingdomCommand.pick(context.getSource(), null);
        if (kingdom.isEmpty()) {
            return 0;
        }
        var server = context.getSource().getServer();
        report(context, "Stockpile of " + Kingdoms.teamName(server, kingdom.get().team()) + ": "
                + (kingdom.get().stockpile().isEmpty() ? "empty" : kingdom.get().stockpile().toString()), false);
        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> context, String line, boolean problem) {
        Component message = problem ? Component.literal(line).withStyle(ChatFormatting.RED) : Component.literal(line);
        context.getSource().sendSuccess(() -> message, false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }
}
