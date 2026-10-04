package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.List;

/**
 * Checks a {@link LayoutPlan}: overlapping footprints, buildings without access to the keep's road network, gaps in
 * the wall. Pure data, no world access; the kingdom planner (stage 4+) will run it before building.
 */
public final class LayoutChecker {
    private LayoutChecker() {
    }

    /**
     * One problem with a layout.
     *
     * @param kind    what is wrong
     * @param message readable description naming the buildings or cells
     * @param where   the cells involved
     */
    public record Problem(Kind kind, String message, List<Cell> where) {
    }

    public enum Kind {
        OVERLAP, NO_ROAD_ACCESS, WALL_GAP
    }

    public static List<Problem> check(LayoutPlan plan) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }
}
