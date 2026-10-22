package net.bumblebee.claysoldiers.terrain;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A scanned square of terrain columns (pure data, no world access): for each column its ground height, water depth,
 * lava, tree blocks, other vegetation, the highest block, the surface block and how far down a cave starts under it. Made by {@link TerrainScanner} from
 * the world, or by hand in tests. Coordinates are world x and z. Columns in unloaded chunks are unknown.
 * <p>
 * Ground = the top block that blocks movement and isn't part of a tree (logs and leaves) or a fluid; plants, snow
 * layers and fluids above it don't count. The surface the soldiers walk on is ground + water depth.
 */
public final class TerrainScan {
    private final int minX;
    private final int minZ;
    private final int size;
    private final boolean[] known;
    private final int[] ground;
    private final int[] water;
    private final boolean[] lava;
    private final int[] treeBlocks;
    private final int[] vegetation;
    private final int[] top;
    private final ResourceLocation[] surfaceBlock;
    private final int[] hollow;
    private BiomeKind biome = BiomeKind.NORMAL;
    private long costNanos;

    /**
     * A scan with every column unknown.
     */
    public TerrainScan(int minX, int minZ, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        int n = size * size;
        known = new boolean[n];
        ground = new int[n];
        water = new int[n];
        lava = new boolean[n];
        treeBlocks = new int[n];
        vegetation = new int[n];
        top = new int[n];
        surfaceBlock = new ResourceLocation[n];
        hollow = new int[n];
    }

    /**
     * An unknown scan covering {@code radius} columns around a center column.
     */
    public static TerrainScan around(int centerX, int centerZ, int radius) {
        return new TerrainScan(centerX - radius, centerZ - radius, 2 * radius + 1);
    }

    /**
     * A known, flat, dry, treeless scan at {@code height} (for tests).
     */
    public static TerrainScan flat(int centerX, int centerZ, int radius, int height) {
        TerrainScan scan = around(centerX, centerZ, radius);
        for (int x = scan.minX; x <= scan.maxX(); x++) {
            for (int z = scan.minZ; z <= scan.maxZ(); z++) {
                scan.setColumn(x, z, height, 0, false, 0, 0, height, null);
            }
        }
        return scan;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return minX + size - 1;
    }

    public int maxZ() {
        return minZ + size - 1;
    }

    /** Columns per side. */
    public int size() {
        return size;
    }

    public boolean contains(int x, int z) {
        return x >= minX && z >= minZ && x <= maxX() && z <= maxZ();
    }

    private int index(int x, int z) {
        if (!contains(x, z)) {
            throw new IndexOutOfBoundsException("column " + x + ", " + z + " is outside the scan " + minX + ", " + minZ + " to " + maxX() + ", " + maxZ());
        }
        return (x - minX) * size + (z - minZ);
    }

    public boolean known(int x, int z) {
        return known[index(x, z)];
    }

    /** The ground height (y of the top ground block). */
    public int ground(int x, int z) {
        return ground[index(x, z)];
    }

    /** Blocks of water above the ground (0 when dry). */
    public int waterDepth(int x, int z) {
        return water[index(x, z)];
    }

    /** The height soldiers walk on: the ground, or the top of the water. */
    public int surface(int x, int z) {
        int i = index(x, z);
        return ground[i] + water[i];
    }

    /** Whether lava lies on this column's ground. */
    public boolean lava(int x, int z) {
        return lava[index(x, z)];
    }

    /** Logs and leaves above the ground. */
    public int treeBlocks(int x, int z) {
        return treeBlocks[index(x, z)];
    }

    public boolean trees(int x, int z) {
        return treeBlocks[index(x, z)] > 0;
    }

    /** All non-air, non-fluid blocks above the ground (trees, plants, snow layers). */
    public int vegetation(int x, int z) {
        return vegetation[index(x, z)];
    }

    /** The highest non-air block of the column (the ground when nothing is above it). */
    public int top(int x, int z) {
        return top[index(x, z)];
    }

    @Nullable
    public ResourceLocation surfaceBlock(int x, int z) {
        return surfaceBlock[index(x, z)];
    }

    /**
     * How far below the ground the first open space starts (a cave, a hole, water under the ground), or 0 when the
     * ground is solid as far down as {@code terrain.cave_probe_depth} (stage 6: the scan used to read only the surface).
     */
    public int hollow(int x, int z) {
        return hollow[index(x, z)];
    }

    public void setHollow(int x, int z, int depth) {
        hollow[index(x, z)] = Math.max(0, depth);
    }

    public BiomeKind biome() {
        return biome;
    }

    public void setBiome(BiomeKind biome) {
        this.biome = biome;
    }

    /** How long the scan took, in nanoseconds (0 for hand-made scans). */
    public long costNanos() {
        return costNanos;
    }

    public void setCostNanos(long costNanos) {
        this.costNanos = costNanos;
    }

    public int knownColumns() {
        int count = 0;
        for (boolean k : known) {
            count += k ? 1 : 0;
        }
        return count;
    }

    public void setColumn(int x, int z, int ground, int waterDepth, boolean lava, int treeBlocks, int vegetation, int top,
                          @Nullable ResourceLocation surfaceBlock) {
        int i = index(x, z);
        known[i] = true;
        this.ground[i] = ground;
        water[i] = Math.max(0, waterDepth);
        this.lava[i] = lava;
        this.treeBlocks[i] = Math.max(0, treeBlocks);
        this.vegetation[i] = Math.max(this.treeBlocks[i], vegetation);
        this.top[i] = Math.max(ground, top);
        this.surfaceBlock[i] = surfaceBlock;
    }

    public void setUnknown(int x, int z) {
        known[index(x, z)] = false;
    }

    // Shortcuts for hand-made scans in tests.

    public void setGround(int x, int z, int height) {
        int i = index(x, z);
        setColumn(x, z, height, water[i], lava[i], treeBlocks[i], vegetation[i], height + Math.max(water[i], vegetation[i]), surfaceBlock[i]);
    }

    public void setWater(int x, int z, int depth) {
        int i = index(x, z);
        setColumn(x, z, ground[i], depth, lava[i], treeBlocks[i], vegetation[i], ground[i] + Math.max(depth, vegetation[i]), surfaceBlock[i]);
    }

    public void setLava(int x, int z, boolean lava) {
        int i = index(x, z);
        setColumn(x, z, ground[i], water[i], lava, treeBlocks[i], vegetation[i], top[i], surfaceBlock[i]);
    }

    public void setTrees(int x, int z, int blocks) {
        int i = index(x, z);
        setColumn(x, z, ground[i], water[i], lava[i], blocks, blocks, ground[i] + blocks, surfaceBlock[i]);
    }
}
