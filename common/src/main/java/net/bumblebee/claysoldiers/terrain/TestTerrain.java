package net.bumblebee.claysoldiers.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds the test terrains for the terrain spike (GameTests and {@code /csr dev terrain make}): a square of grass
 * around a center column, shaped as a slope, hill, one-sided cliff, lake edge, forest, lava pool or plateau. The
 * shapes' sizes are test fixtures, not balancing numbers, so they are not in the tuning file.
 * <p>
 * Every shape is built on a flat base whose top is the center's y; dirt goes down to 7 below it and everything up to
 * 12 above it is cleared, so the result doesn't depend on what was there. Fluids are contained by the base (the lake's
 * surface is level with the ground around it).
 */
public final class TestTerrain {
    /** Lowest block of the base, below the center's y. */
    public static final int DEPTH = 7;
    /** Highest cleared block, above the center's y. */
    public static final int CLEARANCE = 12;
    /** How far the shapes reach from the center (27 by 27 columns). */
    public static final int RADIUS = 13;

    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    private TestTerrain() {
    }

    public enum Shape implements StringRepresentable {
        /** Rises 1 block every 2 blocks going east (50 %), from 7 below the center to 6 above. */
        SLOPE("slope"),
        /** A diamond-shaped hill 9 high at the center, 1 up per block in every direction (100 %). */
        HILL("hill"),
        /** Flat, with everything from the center eastward 6 blocks higher. */
        CLIFF("cliff"),
        /** Land in the west; east of the center a lake: 1 deep for 2 blocks, then 4 deep. */
        LAKE_EDGE("lake_edge"),
        /** Oak trees every 4 blocks in the east half; the west half is a clearing. */
        FOREST("forest"),
        /** A 3 by 3 lava pool at the center. */
        LAVA_POOL("lava_pool"),
        /** A 13 by 13 flat top 4 blocks high with vertical sides. */
        PLATEAU("plateau");

        private final String id;

        Shape(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static Shape byId(String id) {
            for (Shape shape : values()) {
                if (shape.id.equals(id)) {
                    return shape;
                }
            }
            throw new IllegalArgumentException("unknown terrain shape " + id);
        }
    }

    /**
     * The blocks a terrain of {@code radius} around {@code center} changes.
     */
    public static BoundingBox box(BlockPos center, int radius) {
        return new BoundingBox(center.getX() - radius, center.getY() - DEPTH, center.getZ() - radius,
                center.getX() + radius, center.getY() + CLEARANCE, center.getZ() + radius);
    }

    /**
     * Builds {@code shape} in {@link #box}. Doesn't keep an undo snapshot (the dev command does).
     *
     * @param center the center column, at the height of the base's top block
     */
    public static BoundingBox build(ServerLevel level, BlockPos center, Shape shape, int radius) {
        int base = center.getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int ground = groundHeight(shape, dx, dz, base, radius);
                boolean lake = shape == Shape.LAKE_EDGE && ground < base;
                boolean lava = shape == Shape.LAVA_POOL && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                for (int y = base - DEPTH; y <= base + CLEARANCE; y++) {
                    BlockState state;
                    if (y < ground) {
                        state = DIRT;
                    } else if (y == ground) {
                        state = lava ? LAVA : lake ? DIRT : GRASS;
                    } else if (lake && y <= base) {
                        state = WATER;
                    } else {
                        state = AIR;
                    }
                    set(level, pos.set(center.getX() + dx, y, center.getZ() + dz), state);
                }
            }
        }
        if (shape == Shape.FOREST) {
            for (int dx = 0; dx <= radius - 2; dx++) {
                for (int dz = -(radius - 2); dz <= radius - 2; dz++) {
                    if (Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0) {
                        tree(level, center.offset(dx, 0, dz), pos);
                    }
                }
            }
        }
        return box(center, radius);
    }

    /**
     * The ground height of a shape's column (the lake's bed under its water).
     */
    static int groundHeight(Shape shape, int dx, int dz, int base, int radius) {
        return switch (shape) {
            case SLOPE -> base + Math.floorDiv(dx, 2);
            case HILL -> base + Math.max(0, 9 - (Math.abs(dx) + Math.abs(dz)));
            case CLIFF -> dx < 0 ? base : base + 6;
            case PLATEAU -> Math.abs(dx) <= 6 && Math.abs(dz) <= 6 ? base + 4 : base;
            case LAKE_EDGE -> dx >= 1 && dx <= radius - 1 && Math.abs(dz) <= radius - 1 ? (dx >= 3 ? base - 4 : base - 1) : base;
            case FOREST, LAVA_POOL -> base;
        };
    }

    /** A small oak: 4 logs, two 3 by 3 layers of leaves around the top and one leaf on top. */
    private static void tree(ServerLevel level, BlockPos ground, BlockPos.MutableBlockPos pos) {
        for (int y = 1; y <= 4; y++) {
            set(level, pos.set(ground.getX(), ground.getY() + y, ground.getZ()), LOG);
        }
        for (int y = 4; y <= 5; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (y == 4 && dx == 0 && dz == 0) {
                        continue;
                    }
                    set(level, pos.set(ground.getX() + dx, ground.getY() + y, ground.getZ() + dz), LEAVES);
                }
            }
        }
        set(level, pos.set(ground.getX(), ground.getY() + 6, ground.getZ()), LEAVES);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
