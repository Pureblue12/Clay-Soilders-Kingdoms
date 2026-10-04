package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The kingdom wall as the cells it covers. Gates count as wall (they close). Closed means: you can't walk from the
 * inside to the outside without crossing a wall cell, stepping only north, south, east or west (mobs can't squeeze
 * through two blocks that only touch at a corner).
 */
public final class WallLoop {
    private final Set<Cell> cells;

    private WallLoop(Set<Cell> cells) {
        this.cells = Collections.unmodifiableSet(cells);
    }

    public static WallLoop ofCells(Collection<Cell> cells) {
        return new WallLoop(new LinkedHashSet<>(cells));
    }

    /**
     * A wall along a closed polygon through {@code corners} (the last corner connects back to the first), each side a
     * 4-connected line of cells.
     */
    public static WallLoop fromCorners(List<Cell> corners) {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int i = 0; i < corners.size(); i++) {
            cells.addAll(RoadGraph.line(corners.get(i), corners.get((i + 1) % corners.size())));
        }
        return new WallLoop(cells);
    }

    /** This wall with one cell missing (a gap). */
    public WallLoop without(Cell cell) {
        Set<Cell> remaining = new LinkedHashSet<>(cells);
        remaining.remove(cell);
        return new WallLoop(remaining);
    }

    public Set<Cell> cells() {
        return cells;
    }
}
