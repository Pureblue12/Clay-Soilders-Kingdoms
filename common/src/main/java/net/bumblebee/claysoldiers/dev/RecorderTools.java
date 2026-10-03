package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The "Recorder" dev tools: {@code /csr dev log ...} and {@code /csr dev snapshot}, controlling {@link DebugLog}.
 * Category on/off buttons are generated from {@link DebugCategory#all()}, so new categories get buttons automatically.
 */
final class RecorderTools {
    static final String RECORDER = "recorder";
    static final String RECORDER_ADVANCED = "recorder_advanced";
    private static final int NEARBY_RADIUS = 32;
    private static final int DEFAULT_SHOW = 20;

    private RecorderTools() {
    }

    static void register(DevToolRegistry registry) {
        // Main page: the three buttons you need, each with its description printed under it.
        registry.addCategory(new DevCategory(RECORDER, "Recorder",
                "Check Status to see if it's on.", false, true));
        registry.addTool(new DevTool("log_start", RECORDER, Component.literal("Start recording"),
                "Turns on " + ids(DebugCategory.PRESET) + ".",
                List.of("log", "start"), false, null, RecorderTools::start));
        registry.addTool(new DevTool("log_stop", RECORDER, Component.literal("Stop + save"),
                "Turns it off, saves a file.",
                List.of("log", "stop"), false, null, RecorderTools::stop));
        registry.addTool(new DevTool("log_status", RECORDER, Component.literal("Status (is it on?)"),
                "Shows on/off, events, focus.",
                List.of("log", "status"), false, null, context -> status(context, registry)));

        // Advanced page: categories, focus, show/save/clear, log size, snapshot.
        registry.addCategory(new DevCategory(RECORDER_ADVANCED, "Recorder (advanced)",
                "Categories (hover a name to see what it records): " + ids(DebugCategory.all()) + ".", false));
        registry.addTool(new DevTool("log_on", RECORDER_ADVANCED, Component.literal("Turn on"),
                "Turns one category on without changing the others.",
                List.of("log", "on"), false, categoryArgument(), context -> setCategory(context, true)));
        registry.addTool(new DevTool("log_off", RECORDER_ADVANCED, Component.literal("Turn off"),
                "Turns one category off without changing the others.",
                List.of("log", "off"), false, categoryArgument(), context -> setCategory(context, false)));

        registry.addTool(new DevTool("log_focus", RECORDER_ADVANCED, Component.literal("Focus nearest soldier"),
                "Records only the clay soldier nearest to you.",
                List.of("log", "focus"), false, null, RecorderTools::focus));
        registry.addTool(new DevTool("log_unfocus", RECORDER_ADVANCED, Component.literal("Clear focus"),
                "Records all soldiers again.",
                List.of("log", "unfocus"), false, null, RecorderTools::unfocus));

        registry.addTool(new DevTool("log_show", RECORDER_ADVANCED, Component.literal("Show in chat"),
                "Prints the last events in chat, oldest first.",
                List.of("log", "show"), false,
                new DevTool.Argument("count", IntegerArgumentType.integer(1, 500), null,
                        access -> List.of(new DevTool.Choice(Component.literal("Last " + DEFAULT_SHOW + " events"), String.valueOf(DEFAULT_SHOW))),
                        true),
                RecorderTools::show));
        registry.addTool(new DevTool("log_save", RECORDER_ADVANCED, Component.literal("Save log now"),
                "Saves the log to a file without stopping.",
                List.of("log", "save"), false, null, RecorderTools::save));
        registry.addTool(new DevTool("log_clear", RECORDER_ADVANCED, Component.literal("Clear log"),
                "Deletes all recorded events from memory (saved files stay).",
                List.of("log", "clear"), false, null, RecorderTools::clear));
        registry.addTool(new DevTool("log_capacity", RECORDER_ADVANCED, Component.literal("Log size"),
                "How many events are kept in memory (oldest are dropped).",
                List.of("log", "capacity"), false,
                new DevTool.Argument("events", IntegerArgumentType.integer(100, 100_000), null,
                        access -> List.of(new DevTool.Choice(Component.literal("1000"), "1000"),
                                new DevTool.Choice(Component.literal(DebugLog.DEFAULT_CAPACITY + " (default)"), String.valueOf(DebugLog.DEFAULT_CAPACITY)),
                                new DevTool.Choice(Component.literal("20000"), "20000"))),
                RecorderTools::capacity));

        registry.addTool(new DevTool("snapshot", RECORDER_ADVANCED, Component.literal("Snapshot soldiers near me"),
                "One line per soldier within " + NEARBY_RADIUS + " blocks (job, status, carried item, position, health, goal, team), in chat and a file.",
                List.of("snapshot"), false, null, RecorderTools::snapshot));
    }

    private static DevTool.Argument categoryArgument() {
        return new DevTool.Argument("category", StringArgumentType.word(),
                (context, builder) -> SharedSuggestionProvider.suggest(DebugCategory.all().stream().map(DebugCategory::id), builder),
                access -> DebugCategory.all().stream()
                        .map(category -> new DevTool.Choice(Component.literal(category.id()), category.id(), category.id() + ": " + category.description()))
                        .toList());
    }

    // Actions. Each prints one line with the result and the recorder state, and logs it with [CSR-DEBUG].

    private static int start(CommandContext<CommandSourceStack> context) {
        DebugLog.start(context.getSource().getServer());
        report(context, "Recording is now ON (" + ids(DebugLog.enabledCategories()) + ") · old log cleared · all soldiers");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        int events = DebugLog.size();
        String duration = DebugLog.recordingDuration();
        try {
            Path file = DebugLog.stop(context.getSource().getServer());
            report(context, "Recording is now OFF · saved %d events (recorded for %s) to %s".formatted(events, duration, file.toAbsolutePath()));
            return 1;
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the debug log", e);
            fail(context, "Recording is now OFF, but saving %d events failed: %s".formatted(events, e.getMessage()));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context, DevToolRegistry registry) {
        String book = registry.fingerprint(context.getSource().registryAccess());
        report(context, "%s · log size %d · current book #%s (compare with page 1 of your book)"
                .formatted(DebugLog.stateLine(), DebugLog.capacity(), book));
        return DebugLog.enabledCategories().size();
    }

    private static int setCategory(CommandContext<CommandSourceStack> context, boolean enabled) {
        String id = StringArgumentType.getString(context, "category");
        Optional<DebugCategory> category = DebugCategory.byId(id);
        if (category.isEmpty()) {
            fail(context, "Unknown category %s (known: %s) · %s".formatted(id, ids(DebugCategory.all()), DebugLog.stateLine()));
            return 0;
        }
        DebugLog.setEnabled(category.get(), enabled, context.getSource().getServer());
        report(context, category.get().id() + " is now " + (enabled ? "ON" : "OFF") + " · " + DebugLog.stateLine());
        return 1;
    }

    private static int focus(CommandContext<CommandSourceStack> context) {
        List<AbstractClaySoldierEntity> nearby = soldiersNear(context.getSource());
        if (nearby.isEmpty()) {
            fail(context, "No clay soldier within %d blocks, focus unchanged · %s".formatted(NEARBY_RADIUS, DebugLog.stateLine()));
            return 0;
        }
        DebugLog.focus(nearby.getFirst());
        report(context, "Focus is now " + DebugLog.focusName() + " · " + DebugLog.stateLine());
        return 1;
    }

    private static int unfocus(CommandContext<CommandSourceStack> context) {
        DebugLog.focus(null);
        report(context, "Focus cleared · " + DebugLog.stateLine());
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        int count = DEFAULT_SHOW;
        try {
            count = IntegerArgumentType.getInteger(context, "count");
        } catch (IllegalArgumentException missing) {
            // "/csr dev log show" without a number
        }
        List<DebugEvent> events = DebugLog.lastEvents(count);
        String text = events.isEmpty() ? "(no events recorded)"
                : events.stream().map(DebugEvent::toLine).collect(Collectors.joining("\n"));
        context.getSource().sendSuccess(() -> Component.literal("Last %d events (oldest first) · %s\n%s"
                .formatted(events.size(), DebugLog.stateLine(), text)), false);
        return events.size();
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        try {
            Path file = DebugLog.save(context.getSource().getServer());
            report(context, "Saved %d events to %s · %s".formatted(DebugLog.size(), file.toAbsolutePath(), DebugLog.stateLine()));
            return 1;
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the debug log", e);
            fail(context, "Saving failed: " + e.getMessage() + " · " + DebugLog.stateLine());
            return 0;
        }
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        DebugLog.clear();
        report(context, "Log cleared · " + DebugLog.stateLine());
        return 1;
    }

    private static int capacity(CommandContext<CommandSourceStack> context) {
        DebugLog.setCapacity(IntegerArgumentType.getInteger(context, "events"));
        report(context, "The log now keeps the last %d events · %s".formatted(DebugLog.capacity(), DebugLog.stateLine()));
        return 1;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) {
        List<AbstractClaySoldierEntity> nearby = soldiersNear(context.getSource());
        List<String> lines = DebugLog.snapshot(nearby);
        String saved;
        try {
            saved = "saved to " + DebugLog.writeFile(context.getSource().getServer(), "csr-snapshot", DebugLog.snapshotText(nearby)).toAbsolutePath();
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the snapshot", e);
            saved = "saving failed: " + e.getMessage();
        }
        report(context, "Snapshot of %d soldiers within %d blocks · %s".formatted(nearby.size(), NEARBY_RADIUS, saved));
        if (!lines.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        }
        return nearby.size();
    }

    /**
     * Sends a one-line result to the command source and writes it to the game log with a [CSR-DEBUG] prefix.
     */
    private static void report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }

    private static void fail(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendFailure(Component.literal(line));
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }

    // Helpers

    /**
     * Clay soldiers within {@link #NEARBY_RADIUS} blocks of the command source, nearest first.
     */
    static List<AbstractClaySoldierEntity> soldiersNear(CommandSourceStack source) {
        Vec3 center = source.getPosition();
        return source.getLevel().getEntitiesOfClass(AbstractClaySoldierEntity.class, new AABB(center, center).inflate(NEARBY_RADIUS))
                .stream().sorted(Comparator.comparingDouble(soldier -> soldier.distanceToSqr(center))).toList();
    }

    private static String ids(List<DebugCategory> categories) {
        return categories.stream().map(DebugCategory::id).collect(Collectors.joining(", "));
    }
}
