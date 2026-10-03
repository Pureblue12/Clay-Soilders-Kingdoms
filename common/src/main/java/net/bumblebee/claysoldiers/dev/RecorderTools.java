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
    private static final int NEARBY_RADIUS = 32;
    private static final int DEFAULT_SHOW = 20;

    private RecorderTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(RECORDER, "Recorder",
                "A book can't show on/off: use Status to check.", false));

        registry.addTool(new DevTool("log_start", RECORDER, Component.literal("Start recording"),
                "Turns on " + ids(DebugCategory.PRESET) + " for all soldiers and clears the old log.",
                List.of("log", "start"), false, null, RecorderTools::start));
        registry.addTool(new DevTool("log_stop", RECORDER, Component.literal("Stop + save to file"),
                "Turns all categories off and saves the log to a file in debuglogs/.",
                List.of("log", "stop"), false, null, RecorderTools::stop));
        registry.addTool(new DevTool("log_status", RECORDER, Component.literal("Status (is it on?)"),
                "Shows whether recording is on, which categories, how many events and the focus.",
                List.of("log", "status"), false, null, RecorderTools::status));

        registry.addTool(new DevTool("log_on", RECORDER, Component.literal("Turn on"),
                "Turns one category on without changing the others.",
                List.of("log", "on"), false, categoryArgument(), context -> setCategory(context, true)));
        registry.addTool(new DevTool("log_off", RECORDER, Component.literal("Turn off"),
                "Turns one category off without changing the others.",
                List.of("log", "off"), false, categoryArgument(), context -> setCategory(context, false)));

        registry.addTool(new DevTool("log_focus", RECORDER, Component.literal("Focus nearest soldier"),
                "Records only the clay soldier nearest to you.",
                List.of("log", "focus"), false, null, RecorderTools::focus));
        registry.addTool(new DevTool("log_unfocus", RECORDER, Component.literal("Clear focus"),
                "Records all soldiers again.",
                List.of("log", "unfocus"), false, null, RecorderTools::unfocus));

        registry.addTool(new DevTool("log_show", RECORDER, Component.literal("Show in chat"),
                "Prints the last events in chat, oldest first.",
                List.of("log", "show"), false,
                new DevTool.Argument("count", IntegerArgumentType.integer(1, 500), null,
                        access -> List.of(new DevTool.Choice(Component.literal("Last " + DEFAULT_SHOW + " events"), String.valueOf(DEFAULT_SHOW))),
                        true),
                RecorderTools::show));
        registry.addTool(new DevTool("log_save", RECORDER, Component.literal("Save log now"),
                "Saves the log to a file without stopping.",
                List.of("log", "save"), false, null, RecorderTools::save));
        registry.addTool(new DevTool("log_clear", RECORDER, Component.literal("Clear log"),
                "Deletes all recorded events from memory (saved files stay).",
                List.of("log", "clear"), false, null, RecorderTools::clear));
        registry.addTool(new DevTool("log_capacity", RECORDER, Component.literal("Log size"),
                "How many events are kept in memory (oldest are dropped).",
                List.of("log", "capacity"), false,
                new DevTool.Argument("events", IntegerArgumentType.integer(100, 100_000), null,
                        access -> List.of(new DevTool.Choice(Component.literal("1000"), "1000"),
                                new DevTool.Choice(Component.literal(DebugLog.DEFAULT_CAPACITY + " (default)"), String.valueOf(DebugLog.DEFAULT_CAPACITY)),
                                new DevTool.Choice(Component.literal("20000"), "20000"))),
                RecorderTools::capacity));

        registry.addTool(new DevTool("snapshot", RECORDER, Component.literal("Snapshot soldiers near me"),
                "One line per soldier within " + NEARBY_RADIUS + " blocks (job, status, carried item, position, health, goal, team), in chat and a file.",
                List.of("snapshot"), false, null, RecorderTools::snapshot));
    }

    private static DevTool.Argument categoryArgument() {
        return new DevTool.Argument("category", StringArgumentType.word(),
                (context, builder) -> SharedSuggestionProvider.suggest(DebugCategory.all().stream().map(DebugCategory::id), builder),
                access -> DebugCategory.all().stream()
                        .map(category -> new DevTool.Choice(Component.literal(category.id()), category.id()))
                        .toList());
    }

    // Actions

    private static int start(CommandContext<CommandSourceStack> context) {
        DebugLog.start(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Recording started: " + ids(DebugLog.enabledCategories())
                + " (all soldiers, log cleared)."), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        int events = DebugLog.size();
        try {
            Path file = DebugLog.stop(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Recording stopped. Saved " + events + " events to " + file.toAbsolutePath()), false);
            return 1;
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the debug log", e);
            context.getSource().sendFailure(Component.literal("Recording stopped, but saving failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        List<DebugCategory> on = DebugLog.enabledCategories();
        String focus = DebugLog.focusName().isEmpty() ? "all soldiers" : DebugLog.focusName();
        context.getSource().sendSuccess(() -> Component.literal("Recorder: %s | categories on: %s | events: %d of %d | focus: %s"
                .formatted(on.isEmpty() ? "OFF" : "ON", on.isEmpty() ? "none" : ids(on), DebugLog.size(), DebugLog.capacity(), focus)), false);
        return on.size();
    }

    private static int setCategory(CommandContext<CommandSourceStack> context, boolean enabled) {
        String id = StringArgumentType.getString(context, "category");
        Optional<DebugCategory> category = DebugCategory.byId(id);
        if (category.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown category " + id + ". Known: " + ids(DebugCategory.all())));
            return 0;
        }
        DebugLog.setEnabled(category.get(), enabled, context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(category.get().id() + " is now " + (enabled ? "ON" : "OFF")
                + ". Categories on: " + (DebugLog.enabledCategories().isEmpty() ? "none" : ids(DebugLog.enabledCategories()))), false);
        return 1;
    }

    private static int focus(CommandContext<CommandSourceStack> context) {
        List<AbstractClaySoldierEntity> nearby = soldiersNear(context.getSource());
        if (nearby.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No clay soldier within " + NEARBY_RADIUS + " blocks."));
            return 0;
        }
        DebugLog.focus(nearby.getFirst());
        context.getSource().sendSuccess(() -> Component.literal("Recording only " + DebugLog.focusName() + "."), false);
        return 1;
    }

    private static int unfocus(CommandContext<CommandSourceStack> context) {
        DebugLog.focus(null);
        context.getSource().sendSuccess(() -> Component.literal("Recording all soldiers."), false);
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
        context.getSource().sendSuccess(() -> Component.literal("Last " + events.size() + " events (oldest first):\n" + text), false);
        return events.size();
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        try {
            Path file = DebugLog.save(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Saved " + DebugLog.size() + " events to " + file.toAbsolutePath()), false);
            return 1;
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the debug log", e);
            context.getSource().sendFailure(Component.literal("Saving failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        DebugLog.clear();
        context.getSource().sendSuccess(() -> Component.literal("Debug log cleared."), false);
        return 1;
    }

    private static int capacity(CommandContext<CommandSourceStack> context) {
        DebugLog.setCapacity(IntegerArgumentType.getInteger(context, "events"));
        context.getSource().sendSuccess(() -> Component.literal("The log now keeps the last " + DebugLog.capacity() + " events."), false);
        return 1;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) {
        List<AbstractClaySoldierEntity> nearby = soldiersNear(context.getSource());
        List<String> lines = DebugLog.snapshot(nearby);
        context.getSource().sendSuccess(() -> Component.literal("Snapshot of " + nearby.size() + " soldiers:\n" + String.join("\n", lines)), false);
        try {
            Path file = DebugLog.writeFile(context.getSource().getServer(), "csr-snapshot", DebugLog.snapshotText(nearby));
            context.getSource().sendSuccess(() -> Component.literal("Saved to " + file.toAbsolutePath()), false);
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("Could not save the snapshot", e);
            context.getSource().sendFailure(Component.literal("Saving the snapshot failed: " + e.getMessage()));
        }
        return nearby.size();
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
