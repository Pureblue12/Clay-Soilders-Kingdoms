package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The kingdom's roads: straight segments between points (radial roads may be diagonal). Each segment becomes a
 * 4-connected line of cells (a diagonal road becomes a staircase), so walking along a road never needs a diagonal step.
 */
public final class RoadGraph {
    private final List<Cell[]> segments = new ArrayList<>();
    private final Set<Cell> cells = new LinkedHashSet<>();

    public RoadGraph road(Cell from, Cell to) {
        segments.add(new Cell[]{from, to});
        cells.addAll(line(from, to));
        return this;
    }

    public List<Cell[]> segments() {
        return Collections.unmodifiableList(segments);
    }

    /** Every road cell. */
    public Set<Cell> cells() {
        return Collections.unmodifiableSet(cells);
    }

    /**
     * A 4-connected line from {@code from} to {@code to}: steps along x or z, never both at once.
     */
    static List<Cell> line(Cell from, Cell to) {
        List<Cell> line = new ArrayList<>();
        int dx = Math.abs(to.x() - from.x());
        int dz = Math.abs(to.z() - from.z());
        int stepX = Integer.signum(to.x() - from.x());
        int stepZ = Integer.signum(to.z() - from.z());
        int x = from.x();
        int z = from.z();
        line.add(new Cell(x, z));
        int doneX = 0;
        int doneZ = 0;
        while (doneX < dx || doneZ < dz) {
            // Take the step whose next half-way point comes first along the line.
            if ((1 + 2L * doneX) * dz < (1 + 2L * doneZ) * dx) {
                x += stepX;
                doneX++;
            } else {
                z += stepZ;
                doneZ++;
            }
            line.add(new Cell(x, z));
        }
        return line;
    }
}
