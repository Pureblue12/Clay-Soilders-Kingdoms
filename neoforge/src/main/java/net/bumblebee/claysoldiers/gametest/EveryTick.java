package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a long test's check once per test tick, for tests with very long timeouts (the structure builds: keep L3's is
 * several hundred thousand ticks).
 * <p>
 * Why not {@link GameTestHelper#onEachTick}: vanilla schedules one entry per tick up to the timeout and walks every
 * remaining entry on every tick, and {@link GameTestHelper#succeedWhen} throws an exception (with its message built) on
 * every tick until it passes. A flight recording of keep L3 (structure builds run 38939248627) spent 85% of the server
 * thread there and 14% ticking the world. This runs from the server tick instead, with nothing scheduled ahead.
 * <p>
 * A task that throws fails the test: the exception is handed to the test framework on the next test tick, so it fails
 * exactly as if the task had run inside the framework. The task stops when it threw, when {@link Handle#stop()} is called,
 * or when the test stops ticking (finished or timed out).
 */
@EventBusSubscriber(modid = ClaySoldiersCommon.MOD_ID)
public final class EveryTick {
    /** Server ticks without a new test tick after which a test counts as over. */
    private static final int STALE_SERVER_TICKS = 20;
    private static final List<Handle> ACTIVE = new ArrayList<>();

    private EveryTick() {
    }

    /** Runs {@code task} once on every later tick of the test {@code helper} belongs to. */
    public static Handle run(GameTestHelper helper, Runnable task) {
        Handle handle = new Handle(helper, task);
        ACTIVE.add(handle);
        return handle;
    }

    /** A running per-tick task. */
    public static final class Handle {
        private final GameTestHelper helper;
        private final Runnable task;
        private long lastTick;
        private int staleServerTicks;
        private boolean stopped;

        private Handle(GameTestHelper helper, Runnable task) {
            this.helper = helper;
            this.task = task;
            this.lastTick = helper.getTick();
        }

        /** Stops the task; it doesn't run again. */
        public void stop() {
            stopped = true;
        }

        /** Runs the task if the test ticked since the last run; returns false when the task is over. */
        private boolean tick() {
            if (stopped) {
                return false;
            }
            long tick = helper.getTick();
            if (tick == lastTick) {
                return ++staleServerTicks < STALE_SERVER_TICKS;
            }
            lastTick = tick;
            staleServerTicks = 0;
            try {
                task.run();
            } catch (RuntimeException e) {
                helper.runAfterDelay(1, () -> {
                    throw e;
                });
                return false;
            }
            return !stopped;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // A copy: a task may start another one.
        for (Handle handle : List.copyOf(ACTIVE)) {
            if (!handle.tick()) {
                ACTIVE.remove(handle);
            }
        }
    }
}
