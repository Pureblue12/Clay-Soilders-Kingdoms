package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
}
