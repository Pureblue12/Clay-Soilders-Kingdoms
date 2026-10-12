package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Where a structure build's time went, from the BUILD events the stall report records anyway (structure builds with
 * {@code CSR_STRUCTURE_BUILD_PROFILE} set): when the easel finishes, one "CSR build profile" line counts the blocks handed
 * out by the easel (each a trip to the chest), the blocks a builder carrying a stack went on to (D79), the stacks taken
 * back, the searches that found nothing (a builder waiting 20 to 40 ticks), the give-ups, and the build plan's fallbacks.
 * Changes nothing in the test's outcome.
 */
final class BuildProfile {
    /** Recorder capacity while profiling: big enough for a whole build's BUILD, PATHING and SCAFFOLD events. */
    static final int CAPACITY = 1_000_000;

    private static final Map<String, Predicate<String>> COUNTED = new LinkedHashMap<>();

    static {
        COUNTED.put("placed", message -> message.startsWith("placed "));
        COUNTED.put("handed out", message -> message.startsWith("got request: "));
        COUNTED.put("follow-ups", message -> message.startsWith("next ") && message.contains("(carrying "));
        COUNTED.put("stacks taken back", message -> message.startsWith("no more "));
        COUNTED.put("waits", message -> message.startsWith("no reachable block available"));
        COUNTED.put("waits for the wave's last blocks", message -> message.startsWith("request search: ")
                && message.contains("plan wave ") && message.endsWith("nothing to hand out") && !message.contains(" 0 already assigned"));
        COUNTED.put("plan fallbacks", message -> message.startsWith("plan: nothing of wave"));
        COUNTED.put("given up", message -> message.startsWith("gave up "));
        COUNTED.put("soldier in the way", message -> message.startsWith("soldier in the way"));
        COUNTED.put("could not place", message -> message.startsWith("could not place"));
    }

    private BuildProfile() {
    }

    static void watch(GameTestHelper helper, String name, EaselBlockEntity easel) {
        long start = helper.getTick();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || !easel.isFinished()) {
                return;
            }
            done[0] = true;
            Map<String, Integer> counts = new LinkedHashMap<>();
            COUNTED.keySet().forEach(key -> counts.put(key, 0));
            int events = 0;
            for (DebugEvent event : DebugLog.events()) {
                events++;
                for (Map.Entry<String, Predicate<String>> counted : COUNTED.entrySet()) {
                    if (counted.getValue().test(event.message())) {
                        counts.merge(counted.getKey(), event.count(), Integer::sum);
                    }
                }
            }
            StringBuilder line = new StringBuilder().append(helper.getTick() - start).append(" ticks");
            counts.forEach((key, count) -> line.append(", ").append(key).append(' ').append(count));
            line.append(" (").append(events).append(" events").append(events >= CAPACITY ? ", buffer full: counts low" : "").append(')');
            ClaySoldiersCommon.LOGGER.info("CSR build profile {}: {}", name, line);
        });
    }
}
