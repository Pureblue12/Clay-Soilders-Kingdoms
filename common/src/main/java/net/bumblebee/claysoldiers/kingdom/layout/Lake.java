package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whether water is a real lake and not a puddle (the owner's ask, 2026-10-17: "a check that makes sure it's like actually a
 * lake and not just like a random puddle"). Pure: reads the land through {@link Water}.
 * <p>
 * The lake is the water reached from a starting column through its four neighbours with the same surface height. Only
 * still water (source blocks) counts toward its size: a stream running down a slope is mostly flowing water. A river or
 * the sea passes like a lake.
 */
public final class Lake {
    private Lake() {
    }

    /** The water as the check sees it. */
    public interface Water {
        /** Whether the column is loaded (known); an unknown column is never water or land. */
        boolean known(int x, int z);

        /** The height of the water's top block in the column, or {@link #NONE} when its top isn't water. */
        int surface(int x, int z);

        /** How many water blocks stand in the column, from its top down (0 when its top isn't water). */
        int depth(int x, int z);

        /** Whether the water at the column's top is still (a source block). */
        boolean still(int x, int z);
    }

    public static final int NONE = Integer.MIN_VALUE;

    /**
     * What the check found.
     *
     * @param size     still water columns of the lake, counted up to the limit
     * @param surface  the height of its top
     * @param unknown  whether the lake runs into columns that aren't loaded (its size may be larger)
     * @param edge     the lake's columns that touch something other than its water, up to the limit (the plan view)
     */
    public record Measure(int size, int surface, boolean unknown, List<Cell> edge) {
        public boolean isLake(int minSize) {
            return size >= minSize;
        }

        public String describe(int minSize) {
            return isLake(minSize) ? "a lake (at least " + size + " still water columns)"
                    : "a puddle or pond: " + size + " still water columns, needs " + minSize + (unknown ? " (part of it isn't loaded)" : "");
        }
    }

    /**
     * Measures the water connected to ({@code x}, {@code z}); stops once {@code limit} columns of it were looked at (a big
     * lake doesn't need counting to its end). An empty measure when the column isn't water.
     */
    public static Measure measure(Water water, int x, int z, int limit) {
        int surface = water.known(x, z) ? water.surface(x, z) : NONE;
        if (surface == NONE) {
            return new Measure(0, NONE, !water.known(x, z), List.of());
        }
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> open = new ArrayDeque<>();
        List<Cell> edge = new ArrayList<>();
        Cell start = new Cell(x, z);
        seen.add(start);
        open.add(start);
        int size = 0;
        boolean unknown = false;
        while (!open.isEmpty() && seen.size() <= limit) {
            Cell cell = open.poll();
            if (water.still(cell.x(), cell.z())) {
                size++;
            }
            boolean atEdge = false;
            for (Cell next : cell.neighbors()) {
                if (!water.known(next.x(), next.z())) {
                    unknown = true;
                    atEdge = true;
                    continue;
                }
                if (water.surface(next.x(), next.z()) != surface) {
                    atEdge = true;
                    continue;
                }
                if (seen.add(next)) {
                    open.add(next);
                }
            }
            if (atEdge) {
                edge.add(cell);
            }
        }
        return new Measure(size, surface, unknown, edge);
    }
}
