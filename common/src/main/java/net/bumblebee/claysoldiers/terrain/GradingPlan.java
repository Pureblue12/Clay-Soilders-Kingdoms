package net.bumblebee.claysoldiers.terrain;

import java.util.List;

/**
 * What a grading method would do to a site (pure data, from {@link GradingPlanner}): the chosen site, the level, the
 * work per column and the estimated block counts. Applied by {@link Terrain#grade}.
 *
 * @param centerX            the site's center (moved by {@link GradingMethod#searches() searching} methods)
 * @param level              the y of the level ground's top block (the floor for a foundation)
 * @param feasible           false when the method can't do it (over budget, too high, nothing found, not loaded)
 * @param reason             why not, or a short description of what it does
 * @param earthPlaced        estimated ground blocks placed (fill, floor, pillars)
 * @param earthRemoved       estimated ground blocks cut
 * @param vegetationRemoved  estimated tree and plant blocks removed
 * @param budget             the method's budget from the tuning file (0 when it has none)
 * @param work               what to do in each column
 */
public record GradingPlan(GradingMethod method, int centerX, int centerZ, int level, boolean feasible, String reason,
                          int earthPlaced, int earthRemoved, int vegetationRemoved, int budget, List<Work> work) {
    public GradingPlan {
        work = List.copyOf(work);
    }

    public static GradingPlan refused(GradingMethod method, int x, int z, String reason) {
        return new GradingPlan(method, x, z, 0, false, reason, 0, 0, 0, 0, List.of());
    }

    /** What the block fill is made of. */
    public enum Fill {
        /** Earth for cut and fill. */
        DIRT,
        /** The foundation's floor and pillars. */
        STONE
    }

    /** No work of this kind in a column. */
    public static final int NONE = Integer.MIN_VALUE;

    /**
     * The work in one column; a range is skipped when its from is above its to.
     *
     * @param clearFrom  lowest block to remove (to air)
     * @param treesOnly  remove only logs and leaves
     * @param fillFrom   lowest block to fill with {@code fill}
     * @param markerY    where to put a corner marker (on air only), or {@link #NONE}
     */
    public record Work(int x, int z, int clearFrom, int clearTo, boolean treesOnly, int fillFrom, int fillTo, Fill fill, int markerY) {
    }

    /** One line for chat and logs. */
    public String describe() {
        if (!feasible) {
            return method.getSerializedName() + " can't: " + reason;
        }
        return method.getSerializedName() + " at " + centerX + ", " + centerZ + ", level y=" + level + ": " + reason + "; about "
                + earthPlaced + " placed, " + earthRemoved + " cut, " + vegetationRemoved + " trees/plants removed"
                + (budget > 0 ? " (budget " + budget + ")" : "");
    }
}
