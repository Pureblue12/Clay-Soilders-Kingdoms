package net.bumblebee.claysoldiers.kingdom.layout;

/**
 * A rectangle of columns on the kingdom map (inclusive edges).
 */
public record Rect(int minX, int maxX, int minZ, int maxZ) {
    public Rect {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("Empty rectangle " + minX + ".." + maxX + ", " + minZ + ".." + maxZ);
        }
    }

    /** The rectangle with corners ({@code x1}, {@code z1}) and ({@code x2}, {@code z2}) in any order. */
    public static Rect of(int x1, int z1, int x2, int z2) {
        return new Rect(Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    public int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(Cell cell) {
        return contains(cell.x(), cell.z());
    }

    /** Whether the two rectangles overlap or come closer than {@code gap} free columns. */
    public boolean overlaps(Rect other, int gap) {
        return minX <= other.maxX + gap && maxX >= other.minX - gap && minZ <= other.maxZ + gap && maxZ >= other.minZ - gap;
    }

    public Rect grow(int by) {
        return new Rect(minX - by, maxX + by, minZ - by, maxZ + by);
    }

    /** The smallest rectangle holding both. */
    public Rect union(Rect other) {
        return new Rect(Math.min(minX, other.minX), Math.max(maxX, other.maxX), Math.min(minZ, other.minZ), Math.max(maxZ, other.maxZ));
    }

    /** The edge coordinate on {@code side}: z for north and south, x for east and west. */
    public int edge(Side side) {
        return switch (side) {
            case NORTH -> minZ;
            case SOUTH -> maxZ;
            case WEST -> minX;
            case EAST -> maxX;
        };
    }

    /** The farthest distance from ({@code x}, {@code z}) to any corner. */
    public double reach(int x, int z) {
        double dx = Math.max(Math.abs(minX - x), Math.abs(maxX - x));
        double dz = Math.max(Math.abs(minZ - z), Math.abs(maxZ - z));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public Footprint footprint(String id, String role, boolean needsRoad) {
        return new Footprint(id, role, minX, minZ, sizeX(), sizeZ(), needsRoad);
    }

    @Override
    public String toString() {
        return "(" + minX + ", " + minZ + ") to (" + maxX + ", " + maxZ + ")";
    }
}
