package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for the debug recorder ({@link DebugLog}). The recorder is global state, so every test runs in its
 * own batch (batches run one after another) and resets the recorder at the start and end.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class DebugLogGameTests {

    /**
     * A disabled category records nothing AND does no work: the lazy message and the arguments' toString are never
     * called. Once enabled, the same calls record.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_disabled")
    public static void disabledCategoryRecordsNothing(GameTestHelper helper) {
        DebugLog.reset();
        AtomicInteger work = new AtomicInteger();
        Object tripwire = new Object() {
            @Override
            public String toString() {
                work.incrementAndGet();
                return "tripwire";
            }
        };

        DebugLog.event(DebugCategory.TEAM, null, "arg %s", tripwire);
        DebugLog.event(DebugCategory.TEAM, null, () -> {
            work.incrementAndGet();
            return "lazy";
        });
        helper.assertTrue(DebugLog.size() == 0, "A disabled category recorded " + DebugLog.size() + " events");
        helper.assertTrue(work.get() == 0, "A disabled category still built its message (" + work.get() + " times)");

        DebugLog.setEnabled(DebugCategory.TEAM, true, helper.getLevel().getServer());
        DebugLog.event(DebugCategory.TEAM, null, "arg %s", tripwire);
        DebugLog.event(DebugCategory.TEAM, null, () -> {
            work.incrementAndGet();
            return "lazy";
        });
        helper.assertTrue(DebugLog.size() == 2, "An enabled category should record 2 events, got " + DebugLog.size());
        helper.assertTrue(work.get() == 2, "Messages should be built once each when enabled, got " + work.get());
        helper.assertTrue(DebugLog.contains(DebugCategory.TEAM, "arg tripwire"), "Formatted message missing");
        DebugLog.reset();
        helper.succeed();
    }

    /**
     * The ring buffer keeps only the newest N events, oldest first.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_ring")
    public static void ringBufferKeepsLastN(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setCapacity(10);
        DebugLog.setEnabled(DebugCategory.BUILD, true, helper.getLevel().getServer());
        for (int i = 0; i < 25; i++) {
            DebugLog.event(DebugCategory.BUILD, null, "event %s", i);
        }
        List<DebugEvent> events = DebugLog.events();
        helper.assertTrue(events.size() == 10, "Expected 10 events, got " + events.size());
        helper.assertTrue(events.getFirst().message().equals("event 15"), "Oldest kept should be event 15, got " + events.getFirst().message());
        helper.assertTrue(events.getLast().message().equals("event 24"), "Newest should be event 24, got " + events.getLast().message());
        DebugLog.reset();
        helper.succeed();
    }

    /**
     * Identical repeats from the same soldier collapse into one line with a count; a different message starts a new line.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_throttle")
    public static void throttlingCollapsesRepeats(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.BUILD, true, helper.getLevel().getServer());
        for (int i = 0; i < 5; i++) {
            DebugLog.event(DebugCategory.BUILD, null, "no reachable block available");
        }
        helper.assertTrue(DebugLog.size() == 1, "5 repeats should be 1 line, got " + DebugLog.size());
        helper.assertTrue(DebugLog.events().getFirst().count() == 5, "Count should be 5, got " + DebugLog.events().getFirst().count());
        helper.assertTrue(DebugLog.events().getFirst().toLine().contains("(x5"), "Line should show the count: " + DebugLog.events().getFirst().toLine());

        DebugLog.event(DebugCategory.BUILD, null, "got request");
        DebugLog.event(DebugCategory.BUILD, null, "no reachable block available");
        helper.assertTrue(DebugLog.size() == 3, "A different message in between should start new lines, got " + DebugLog.size());
        DebugLog.reset();
        helper.succeed();
    }

    /**
     * {@code /csr dev log start} turns on exactly the preset and clears the old log;
     * {@code /csr dev log stop} turns everything off and writes a file containing the recorded events.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_startstop")
    public static void startAndStopCommands(GameTestHelper helper) throws IOException {
        DebugLog.reset();
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);
        DebugLog.setEnabled(DebugCategory.TEAM, true, server);
        DebugLog.event(DebugCategory.TEAM, null, "old event before start");

        server.getCommands().performPrefixedCommand(source, "csr dev log start");
        helper.assertTrue(DebugLog.enabledCategories().equals(DebugCategory.PRESET),
                "Start should enable exactly " + DebugCategory.PRESET + ", got " + DebugLog.enabledCategories());
        helper.assertTrue(DebugLog.size() == 0, "Start should clear the old log, " + DebugLog.size() + " events left");

        DebugLog.event(DebugCategory.BUILD, null, "recorded while on");
        long before = System.currentTimeMillis() - 1000;
        server.getCommands().performPrefixedCommand(source, "csr dev log stop");
        helper.assertTrue(DebugLog.enabledCategories().isEmpty(), "Stop should turn everything off, on: " + DebugLog.enabledCategories());

        Path folder = server.getServerDirectory().resolve(DebugLog.FOLDER);
        Optional<Path> newest;
        try (Stream<Path> files = Files.list(folder)) {
            newest = files.filter(path -> path.getFileName().toString().startsWith("csr-debug-"))
                    .filter(path -> path.toFile().lastModified() >= before)
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
        }
        helper.assertTrue(newest.isPresent(), "Stop did not write a file in " + folder);
        String text = Files.readString(newest.get());
        helper.assertTrue(text.contains("recorded while on"), "Saved file is missing the recorded event: " + newest.get());
        helper.assertTrue(text.startsWith("# Clay Soldiers Remake debug log"), "Saved file has no header");
        DebugLog.reset();
        helper.succeed();
    }

    /**
     * Integration: runs the {@code build_test} scenario with only BUILD recording on, and passes once a
     * "placed" event has been recorded. Prints the first lines of the log to the console as a sample.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_scenario", timeoutTicks = 600)
    public static void buildScenarioRecordsBlockPlaced(GameTestHelper helper) {
        DebugLog.reset();
        ServerLevel level = helper.getLevel();
        DebugLog.setEnabled(DebugCategory.BUILD, true, level.getServer());
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "csr-recorder-tester"));
        BlockPos standAt = helper.absolutePos(new BlockPos(8, ClaySoldierGameTests.GROUND_Y, 2));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0f, 0f);
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                "csr dev scenario build_test");

        helper.succeedWhen(() -> {
            helper.assertTrue(DebugLog.contains(DebugCategory.BUILD, "placed "), "No 'placed' event recorded yet (" + DebugLog.size() + " events)");
            DebugLog.lastEvents(Integer.MAX_VALUE).stream().limit(15)
                    .forEach(event -> ClaySoldiersCommon.LOGGER.info("RECORDER SAMPLE {}", event.toLine()));
            DebugLog.reset();
        });
    }

    /**
     * The book's per-category on/off buttons are generated from the category list, so a new category gets them automatically.
     */
    @GameTest(template = PLATFORM, batch = "debuglog_buttons")
    public static void categoryButtonsComeFromCategories(GameTestHelper helper) {
        DevToolRegistry registry = DevToolRegistry.defaults();
        List<String> categories = DebugCategory.all().stream().map(DebugCategory::id).toList();
        for (String toolId : List.of("log_on", "log_off")) {
            DevTool tool = registry.tools().stream().filter(t -> t.id().equals(toolId)).findFirst().orElseThrow();
            List<String> buttons = registry.buttons(tool, helper.getLevel().registryAccess()).stream()
                    .map(button -> button.label().getString()).toList();
            helper.assertTrue(buttons.equals(categories), toolId + " buttons " + buttons + " don't match categories " + categories);
        }
        helper.succeed();
    }
}
