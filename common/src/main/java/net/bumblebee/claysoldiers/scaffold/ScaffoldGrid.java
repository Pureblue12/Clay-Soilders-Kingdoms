package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;

/**
 * The scaffold planner's model of the build site (pure data): which cells of the region are solid, which of those a
 * soldier may stand on, and where soldiers can walk from the region's edge. It follows the same rules as soldiers do:
 * a standing spot is an empty cell with an empty cell above and a floor below; soldiers hop up 1 block, walk level,
 * and drop down at most {@link #MAX_DROP}; placing uses {@link BuilderReach#canPlaceFrom(Vec3, BlockPos)}.
 */
final class ScaffoldGrid {
    /** How far soldiers drop down when walking off an edge (vanilla mobs' default safe fall distance). */
    static final int MAX_DROP = 3;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final BoundingBox region;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet solid;
    /** Solid cells soldiers may stand on (structure blocks don't count when the planner ignores them as floors). */
    private final BitSet floor;
    /** Scaffolding column cells: not solid, soldiers can be inside them and climb up and down (approach e). */
    private final BitSet climbable;

    ScaffoldGrid(BoundingBox region) {
        this.region = region;
        sizeX = region.getXSpan();
        sizeY = region.getYSpan();
        sizeZ = region.getZSpan();
        solid = new BitSet(sizeX * sizeY * sizeZ);
        floor = new BitSet(sizeX * sizeY * sizeZ);
        climbable = new BitSet(sizeX * sizeY * sizeZ);
    }

    BoundingBox region() {
        return region;
    }

    boolean inside(int x, int y, int z) {
        return x >= region.minX() && x <= region.maxX() && y >= region.minY() && y <= region.maxY() && z >= region.minZ() && z <= region.maxZ();
    }

    boolean inside(BlockPos pos) {
        return inside(pos.getX(), pos.getY(), pos.getZ());
    }

    private int index(int x, int y, int z) {
        return ((x - region.minX()) * sizeY + (y - region.minY())) * sizeZ + (z - region.minZ());
    }

    boolean solid(int x, int y, int z) {
        return inside(x, y, z) && solid.get(index(x, y, z));
    }

    boolean solid(BlockPos pos) {
        return solid(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Makes a cell solid; {@code standOn} says whether soldiers may stand on it.
     */
    void set(BlockPos pos, boolean standOn) {
        if (inside(pos)) {
            int i = index(pos.getX(), pos.getY(), pos.getZ());
            solid.set(i);
            floor.set(i, standOn);
        }
    }

    void clear(BlockPos pos) {
        if (inside(pos)) {
            int i = index(pos.getX(), pos.getY(), pos.getZ());
            solid.clear(i);
            floor.clear(i);
            climbable.clear(i);
        }
    }

    /** Makes a cell a scaffolding column cell (climbable, not solid). */
    void setClimbable(BlockPos pos) {
        if (inside(pos)) {
            climbable.set(index(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    boolean climbable(int x, int y, int z) {
        return inside(x, y, z) && climbable.get(index(x, y, z));
    }

    /** A column cell above the column's base: builders are only ever inside it while climbing. */
    boolean insideColumn(int x, int y, int z) {
        return climbable(x, y, z) && climbable(x, y - 1, z);
    }

    /** Whether something (solid or a scaffolding column) is in the cell already. */
    boolean occupied(BlockPos pos) {
        return solid(pos) || climbable(pos.getX(), pos.getY(), pos.getZ());
    }

    void setAll(Collection<BlockPos> cells, boolean standOn) {
        cells.forEach(cell -> set(cell, standOn));
    }

    /** Whether a soldier can stand with its feet in this cell. */
    boolean standable(int x, int y, int z) {
        if (climbable(x, y, z)) {
            return true;
        }
        if (!inside(x, y, z) || y - 1 < region.minY() || solid(x, y, z) || solid(x, y + 1, z)) {
            return false;
        }
        return floor.get(index(x, y - 1, z));
    }

    /**
     * Every standing spot soldiers can walk to from the region's edge (where they arrive from outside).
     */
    BitSet reachable() {
        return reachable(null, 0);
    }

    /**
     * Every standing spot soldiers can walk to: from {@code home} (the standing spots within 2 blocks of it) or, when
     * null, from the region's edge. With {@code maxPath} > 0, only spots whose shortest path is shorter than that
     * (walked length, like vanilla's path limit: 1 per level step, about 1.4 per hop up, more for drops).
     */
    BitSet reachable(@Nullable BlockPos home, int maxPath) {
        return reachable(home, maxPath, maxPath, null);
    }

    /**
     * Like {@link #reachable(BlockPos, int)}, with a longer limit for walking to a scaffolding column: the result has the
     * spots shorter than {@code maxPath} plus the column cells whose base is shorter than {@code columnPath} (climbing
     * doesn't count, D75). With {@code walk}, every spot shorter than {@code columnPath} is added to it (where a column's
     * base could stand).
     */
    BitSet reachable(@Nullable BlockPos home, int maxPath, int columnPath, @Nullable BitSet walk) {
        int cells = sizeX * sizeY * sizeZ;
        BitSet seen = new BitSet(cells);
        float[] best = new float[cells];
        java.util.Arrays.fill(best, Float.MAX_VALUE);
        java.util.PriorityQueue<float[]> queue = new java.util.PriorityQueue<>(java.util.Comparator.comparingDouble(e -> e[3]));
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                boolean start = home == null
                        ? x == region.minX() || x == region.maxX() || z == region.minZ() || z == region.maxZ()
                        : Math.abs(x - home.getX()) <= 2 && Math.abs(z - home.getZ()) <= 2;
                if (!start) {
                    continue;
                }
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    if ((home == null || Math.abs(y - home.getY()) <= 1) && standable(x, y, z)) {
                        best[index(x, y, z)] = 0;
                        queue.add(new float[]{x, y, z, 0});
                    }
                }
            }
        }
        float limit = maxPath > 0 && columnPath > 0 ? Math.max(maxPath, columnPath) : Float.MAX_VALUE;
        while (!queue.isEmpty()) {
            float[] at = queue.poll();
            int x = (int) at[0];
            int y = (int) at[1];
            int z = (int) at[2];
            int i = index(x, y, z);
            if (seen.get(i) || at[3] > best[i]) {
                continue;
            }
            seen.set(i);
            if (climbable(x, y, z)) {
                // Climbing inside a column doesn't count toward the path limit: it isn't a vanilla path.
                if (climbable(x, y + 1, z)) {
                    relax(queue, best, x, y + 1, z, at[3], limit);
                }
                if (climbable(x, y - 1, z)) {
                    relax(queue, best, x, y - 1, z, at[3], limit);
                }
                if (insideColumn(x, y, z)) {
                    continue; // builders climb inside a column and come back down; they never step off it (ScaffoldClimber)
                }
            }
            for (int[] d : DIRECTIONS) {
                int nx = x + d[0];
                int nz = z + d[1];
                // and never step into one above its base (its base they enter like any cell)
                if (!insideColumn(nx, y + 1, nz)) {
                    relax(queue, best, nx, y + 1, nz, at[3] + 1.4142f, limit); // hop up
                }
                if (!insideColumn(nx, y, nz)) {
                    relax(queue, best, nx, y, nz, at[3] + 1, limit);
                }
                if (!solid(nx, y, nz) && !solid(nx, y + 1, nz)) {
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        if (standable(nx, y - drop, nz)) {
                            relax(queue, best, nx, y - drop, nz, at[3] + (float) Math.sqrt(1 + drop * drop), limit);
                            break;
                        }
                        if (solid(nx, y - drop, nz)) {
                            break;
                        }
                    }
                }
            }
        }
        if (walk != null) {
            walk.or(seen);
        }
        if (maxPath <= 0 || columnPath <= maxPath) {
            return seen;
        }
        BitSet work = new BitSet(cells);
        for (int i = seen.nextSetBit(0); i >= 0; i = seen.nextSetBit(i + 1)) {
            if (best[i] < maxPath || climbable.get(i)) {
                work.set(i);
            }
        }
        return work;
    }

    private void relax(java.util.PriorityQueue<float[]> queue, float[] best, int x, int y, int z, float distance, float limit) {
        if (distance < limit && standable(x, y, z)) {
            int i = index(x, y, z);
            if (distance < best[i]) {
                best[i] = distance;
                queue.add(new float[]{x, y, z, distance});
            }
        }
    }

    boolean isReachable(BitSet reachable, int x, int y, int z) {
        return inside(x, y, z) && reachable.get(index(x, y, z));
    }

    /**
     * Whether a builder can work on {@code target} (place it, or remove it) from some reachable standing spot. The spot
     * can't be the target itself.
     */
    boolean canWorkOn(BitSet reachable, BlockPos target) {
        int range = (int) Math.floor(BuilderReach.HORIZONTAL_REACH);
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int feet = target.getY() - BuilderReach.MAX_PLACE_HEIGHT; feet <= target.getY() + BuilderReach.MAX_PLACE_DEPTH; feet++) {
                    int x = target.getX() + dx;
                    int z = target.getZ() + dz;
                    if ((dx != 0 || dz != 0 || feet != target.getY()) && isReachable(reachable, x, feet, z)
                            && BuilderReach.canPlaceFrom(new Vec3(x + 0.5, feet, z + 0.5), target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The lowest reachable standing spot's y in a column, or {@link Integer#MIN_VALUE}. */
    int lowestReachable(BitSet reachable, int x, int z) {
        for (int y = region.minY(); y <= region.maxY(); y++) {
            if (isReachable(reachable, x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
