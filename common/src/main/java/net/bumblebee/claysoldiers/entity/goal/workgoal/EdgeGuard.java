package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps a working soldier from stepping off an edge with no floor within a safe drop below, the way vanilla keeps a
 * sneaking player on a block (D70). Builders walk along narrow ledges of what they build: a tiny soldier hopping up a
 * step next to a drop overshoots, or cuts a corner past its path, and falls. The gatehouse's builders all died
 * falling 9 blocks down the portcullis slot beside the walkway they had built (structure build run 38149380878, fall
 * traces: the soldier left its path mid-hop at the slot's edge). The soldiers' paths never drop more than
 * {@link #SAFE_DROP} blocks, so no planned step is stopped.
 */
public final class EdgeGuard {
    /** The deepest drop a soldier's path plans (vanilla's 3), plus a little for the floor's top. */
    public static final double SAFE_DROP = 3.5;
    /** Rounding room for a fall of exactly {@link #SAFE_DROP}, which a working soldier takes without damage (D74). */
    public static final double FALL_MARGIN = 0.01;
    private static final double STEP = 0.05;
    /** Ticks after a stop during which a soldier counts as held at an edge ({@link #heldRecently}). */
    private static final int HELD_TICKS = 20;
    /** Game time each soldier was last stopped at an edge (server side; forgotten with the soldier). */
    private static final Map<AbstractClaySoldierEntity, Long> LAST_HELD = new WeakHashMap<>();

    private EdgeGuard() {
    }

    /** The movement with every horizontal part removed that would carry {@code soldier} over a deadly drop. */
    public static Vec3 backOff(AbstractClaySoldierEntity soldier, Vec3 movement, MoverType type) {
        if (type != MoverType.SELF || movement.x == 0 && movement.z == 0 || soldier.isInWater() || deadly(soldier, 0, 0)) {
            return movement; // already over a drop (falling, knocked off): don't hold it in the air
        }
        double x = movement.x;
        double z = movement.z;
        while (x != 0 && deadly(soldier, x, 0)) {
            x = towardZero(x);
        }
        while (z != 0 && deadly(soldier, 0, z)) {
            z = towardZero(z);
        }
        while (x != 0 && z != 0 && deadly(soldier, x, z)) {
            x = towardZero(x);
            z = towardZero(z);
        }
        if (x == movement.x && z == movement.z) {
            return movement;
        }
        LAST_HELD.put(soldier, soldier.level().getGameTime());
        DebugLog.event(DebugCategory.PATHING, soldier, "edge guard: stopped at the edge of a drop at %s", soldier.blockPosition());
        return new Vec3(x, movement.y, z);
    }

    /**
     * Whether the guard stopped {@code soldier} within the last {@link #HELD_TICKS} ticks. A builder standing on the
     * edge of a wall top it built, over a cell with a deeper drop below, gets a path that starts with that drop (vanilla
     * starts the path in the soldier's own cell): the guard stops every step, and the path never ends (structure build
     * run 38156523957: noble house L1, farm L1, hospital L1, all 3 builders on one cell for up to 13,800 ticks). Such a
     * builder is stranded ({@link Stranded}).
     */
    static boolean heldRecently(AbstractClaySoldierEntity soldier) {
        Long last = LAST_HELD.get(soldier);
        return last != null && soldier.level().getGameTime() - last <= HELD_TICKS;
    }

    /** Whether the soldier moved by (dx, dz) would have nothing below it within {@link #SAFE_DROP}. */
    private static boolean deadly(AbstractClaySoldierEntity soldier, double dx, double dz) {
        AABB box = soldier.getBoundingBox().move(dx, 0, dz);
        return soldier.level().noCollision(soldier, box.expandTowards(0, -SAFE_DROP, 0));
    }

    private static double towardZero(double value) {
        if (Math.abs(value) <= STEP) {
            return 0;
        }
        return value - Math.copySign(STEP, value);
    }
}
