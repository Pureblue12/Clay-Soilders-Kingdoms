package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.tuning.TuningKey;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The "Tuning" dev tools: {@code /csr dev tuning reload} and {@code /csr dev tuning show <key>}. The show buttons are
 * generated from {@link Tuning#keys()}, so new keys get buttons automatically.
 */
final class TuningTools {
    static final String TUNING = "tuning";

    private TuningTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(TUNING, "Tuning",
                "Numbers from data/csr/tuning/tuning.json (docs/TUNING.md).", false));
        registry.addTool(new DevTool("tuning_reload", TUNING, Component.literal("Reload tuning file"),
                "Reads the tuning file again and lists any problems.",
                List.of("tuning", "reload"), false, null, TuningTools::reload));
        registry.addTool(new DevTool("tuning_show", TUNING, Component.literal("Show"),
                "Shows a tuning value, its default and allowed range.",
                List.of("tuning", "show"), false,
                new DevTool.Argument("key", StringArgumentType.word(),
                        (context, builder) -> SharedSuggestionProvider.suggest(Tuning.keys().stream().map(TuningKey::id), builder),
                        access -> Tuning.keys().stream()
                                .map(key -> new DevTool.Choice(Component.literal(key.id()), key.id(), key.description()))
                                .toList()),
                TuningTools::show));
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        Tuning.Values values = Tuning.reload(context.getSource().getServer().getResourceManager());
        report(context, "Tuning reloaded from %s: %d of %d keys set in the file, %d problem(s)."
                .formatted(Tuning.FILE, values.setInFile().size(), Tuning.keys().size(), values.problems().size()));
        values.problems().forEach(problem -> fail(context, "  " + problem));
        return values.problems().isEmpty() ? 1 : 0;
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "key");
        var key = Tuning.key(id);
        if (key.isEmpty()) {
            fail(context, "Unknown tuning key " + id + ". Known keys: " + Tuning.keys().stream().map(TuningKey::id).toList());
            return 0;
        }
        report(context, describe(key.get()));
        return 1;
    }

    private static <T> String describe(TuningKey<T> key) {
        boolean fromFile = Tuning.current().setInFile().contains(key.id());
        return "%s = %s (%s; default %s; allowed: %s) - %s".formatted(key.id(), key.format(key.get()),
                fromFile ? "from the file" : "default", key.format(key.defaultValue()), key.range(), key.description());
    }

    private static void report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }

    private static void fail(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.RED), false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }
}
