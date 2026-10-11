package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Where a builder was, and on which route, in the seconds before it fell. Town hall builders kept falling 6 blocks at
 * one spot until they died (structure build run 38129993219), and nothing in the recorder said where they walked off.
 * While DAMAGE is recorded, the build goal records every working builder's position each tick (the last
 * {@link #TICKS}) and the path it follows; fall damage writes them as one DAMAGE event "fall trace: ...". Memory only,
 * weak keys: a soldier that leaves the level takes its trace with it.
 */
public final class FallTrace {
    /** How many ticks of positions are kept (3 seconds). */
    static final int TICKS = 60;
    private static final Map<Entity, FallTrace> TRACES = Collections.synchronizedMap(new WeakHashMap<>());

    private record Step(long tick, double x, double y, double z, boolean onGround, String status, int node, int nodes) {
        String text() {
            return String.format(Locale.ROOT, "t=%d (%.2f, %.2f, %.2f)%s %s%s", tick, x, y, z, onGround ? "" : " in the air", status,
                    nodes > 0 ? " node " + node + "/" + nodes : "");
        }
    }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    @Nullable
    private Path path;

    private FallTrace() {
    }

    /** Records where {@code soldier} is now and the path its navigation follows; does nothing unless DAMAGE is recorded. */
    public static void record(AbstractClaySoldierEntity soldier, String status) {
        if (!DebugLog.isOn(DebugCategory.DAMAGE)) {
            return;
        }
        FallTrace trace = TRACES.computeIfAbsent(soldier, s -> new FallTrace());
        Path current = soldier.getNavigation().getPath();
        if (current != null) {
            trace.path = current;
        }
        trace.steps.addLast(new Step(soldier.level().getGameTime(), soldier.getX(), soldier.getY(), soldier.getZ(), soldier.onGround(), status,
                current == null ? 0 : current.getNextNodeIndex(), current == null ? 0 : current.getNodeCount()));
        while (trace.steps.size() > TICKS) {
            trace.steps.removeFirst();
        }
    }

    /** Writes the trace of {@code soldier} as a DAMAGE event (nothing when there is none). */
    public static void report(AbstractClaySoldierEntity soldier) {
        FallTrace trace = TRACES.get(soldier);
        if (trace == null || trace.steps.isEmpty()) {
            return;
        }
        DebugLog.event(DebugCategory.DAMAGE, soldier, () -> {
            String positions = trace.steps.stream().map(Step::text).collect(Collectors.joining("; "));
            String route = trace.path == null ? "no path" : "path of " + trace.path.getNodeCount() + " nodes: " + nodes(trace.path);
            return "fall trace (last " + trace.steps.size() + " ticks): " + positions + " | " + route;
        });
        trace.steps.clear();
    }

    private static String nodes(Path path) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            text.append(i > 0 ? "; " : "").append(node.x).append(", ").append(node.y).append(", ").append(node.z).append(' ').append(node.type);
        }
        return text.toString();
    }
}
