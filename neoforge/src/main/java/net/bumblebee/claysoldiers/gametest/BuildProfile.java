package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Where a structure build's time went, from the BUILD events the stall report records anyway (structure builds with
 * {@code CSR_STRUCTURE_BUILD_PROFILE} set): when the easel finishes, one "CSR build profile" line counts the blocks handed
 * out by the easel (each a trip to the chest), the blocks a builder carrying a stack went on to (D79), the stacks taken
 * back, the searches that found nothing (a builder waiting 20 to 40 ticks), the give-ups, the build plan's fallbacks, when
 * the plan ran out (every block left comes from the whole-list search: blocks needing scaffold, blocks given up), when
 * the first scaffold went up and when the structure was done (then its scaffold comes down).
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

    /** Writes the profile line of a build that has just finished, {@code ticks} after it started. */
    static void write(String name, long ticks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        COUNTED.keySet().forEach(key -> counts.put(key, 0));
        int events = 0;
        long firstTick = -1;
        String planRanOut = "never";
        String firstScaffold = "never";
        String structureDone = "never";
        for (DebugEvent event : DebugLog.events()) {
            events++;
            if (firstTick < 0) {
                firstTick = event.tick();
            }
            if (planRanOut.equals("never") && event.message().startsWith("plan: nothing of wave") && event.message().contains(" or later ")) {
                // From here on blocks come from the whole-list search: what the plan left out (scaffold) or gave up.
                planRanOut = "at " + (event.tick() - firstTick) + " ticks with " + counts.get("placed") + " placed";
            }
            if (firstScaffold.equals("never") && event.message().startsWith("placed scaffold at ")) {
                firstScaffold = "at " + (event.tick() - firstTick) + " ticks with " + counts.get("placed") + " placed";
            }
            if (structureDone.equals("never") && event.message().startsWith("structure at easel ") && event.message().contains(" finished: ")) {
                structureDone = "at " + (event.tick() - firstTick) + " ticks"; // then the scaffold comes down
            }
            for (Map.Entry<String, Predicate<String>> counted : COUNTED.entrySet()) {
                if (counted.getValue().test(event.message())) {
                    counts.merge(counted.getKey(), event.count(), Integer::sum);
                }
            }
        }
        StringBuilder line = new StringBuilder().append(ticks).append(" ticks");
        counts.forEach((key, count) -> line.append(", ").append(key).append(' ').append(count));
        line.append(", plan ran out ").append(planRanOut).append(", first scaffold ").append(firstScaffold)
                .append(", structure done ").append(structureDone);
        line.append(" (").append(events).append(" events").append(events >= CAPACITY ? ", buffer full: counts low" : "").append(')');
        ClaySoldiersCommon.LOGGER.info("CSR build profile {}: {}", name, line);
    }
}
