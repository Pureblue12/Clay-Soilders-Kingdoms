package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.commands.KingdomCommand;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The "Kingdom" dev book category: links to the real {@code /csr kingdom} commands (found, disband, book, status) and
 * dev-only stockpile tools for testing ({@code /csr dev kingdom stock_add|stock_remove|stock_show}), plus the keep's
 * (stage 4: {@code keep_materials} supplies it, {@code keep_now} looks after it at once). The stockpile
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
                "Found at once where your king stands (or sneak + right-click him with a gold block: he picks a spot and founds it there).", false));
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
        registry.addTool(new DevTool("kingdom_keep_materials", KINGDOM, Component.literal("Supply the keep"),
                "Adds everything the keep needs (its level from the tuning file, with scaffolding when it is tall) to the stockpile (dev).",
                List.of("kingdom", "keep_materials"), false, null, KingdomTools::keepMaterials));
        registry.addTool(new DevTool("kingdom_keep_now", KINGDOM, Component.literal("Look after the keep now"),
                "The kingdom looks after its keep at once: places it if it hasn't (else after kingdom.keep.plan_retry_ticks), stocks the chest, picks builders (dev).",
                List.of("kingdom", "keep_now"), false, null, KingdomTools::keepNow));
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

    private static int keepMaterials(CommandContext<CommandSourceStack> context) {
        Optional<Kingdom> kingdom = KingdomCommand.pick(context.getSource(), null);
        if (kingdom.isEmpty()) {
            return 0;
        }
        var server = context.getSource().getServer();
        int detail = KingdomKeep.keep(kingdom.get()).map(KingdomBuilding::level).orElse(Tuning.KINGDOM_KEEP_LEVEL.get());
        List<ItemStack> materials = KingdomKeep.materials(context.getSource().getLevel(), detail);
        if (materials.isEmpty()) {
            report(context, "The keep structure isn't loaded.", true);
            return 0;
        }
        int total = 0;
        for (ItemStack stack : materials) {
            Kingdoms.addResource(server, kingdom.get(), BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
            total += stack.getCount();
        }
        report(context, "Added the keep's materials (level " + detail + ", " + materials.size() + " kinds, " + total + " items) to the stockpile of "
                + Kingdoms.teamName(server, kingdom.get().team()) + ".", false);
        return 1;
    }

    private static int keepNow(CommandContext<CommandSourceStack> context) {
        Optional<Kingdom> kingdom = KingdomCommand.pick(context.getSource(), null);
        if (kingdom.isEmpty()) {
            return 0;
        }
        var server = context.getSource().getServer();
        var level = server.getLevel(kingdom.get().dimension());
        if (level == null) {
            report(context, "The kingdom's dimension isn't loaded.", true);
            return 0;
        }
        KingdomKeep.forget(kingdom.get());
        KingdomKeep.update(level, kingdom.get());
        KingdomKeep.describe(server, kingdom.get()).forEach(line -> report(context, "Keep: " + line, false));
        return 1;
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
