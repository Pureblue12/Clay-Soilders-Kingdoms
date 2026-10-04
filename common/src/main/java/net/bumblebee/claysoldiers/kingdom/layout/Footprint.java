package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The ground a building covers: a rectangle of cells.
 *
 * @param id        name used in problem messages, e.g. {@code house_1}
 * @param role      the structure role (see {@code StructureMeta.ROLES}); {@code keep} marks the kingdom's hub
 * @param minX      west edge (inclusive)
 * @param minZ      north edge (inclusive)
 * @param sizeX     width (x)
 * @param sizeZ     depth (z)
 * @param needsRoad whether it must touch a road connected to the keep (false e.g. for wall towers)
 */
public record Footprint(String id, String role, int minX, int minZ, int sizeX, int sizeZ, boolean needsRoad) {
    public Footprint {
        if (sizeX < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Footprint " + id + " needs a size of at least 1x1");
        }
    }

    public Footprint(String id, String role, int minX, int minZ, int sizeX, int sizeZ) {
        this(id, role, minX, minZ, sizeX, sizeZ, true);
    }

    public int maxX() {
        return minX + sizeX - 1;
    }

    public int maxZ() {
        return minZ + sizeZ - 1;
    }

    public boolean contains(Cell cell) {
        return cell.x() >= minX && cell.x() <= maxX() && cell.z() >= minZ && cell.z() <= maxZ();
    }

    public boolean overlaps(Footprint other) {
        return minX <= other.maxX() && other.minX <= maxX() && minZ <= other.maxZ() && other.minZ <= maxZ();
    }

    /**
     * The cells right next to the footprint's sides (not its corners): where a road gives access.
     */
    public Set<Cell> edgeNeighbors() {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int x = minX; x <= maxX(); x++) {
            cells.add(new Cell(x, minZ - 1));
            cells.add(new Cell(x, maxZ() + 1));
        }
        for (int z = minZ; z <= maxZ(); z++) {
            cells.add(new Cell(minX - 1, z));
            cells.add(new Cell(maxX() + 1, z));
        }
        return cells;
    }
}
