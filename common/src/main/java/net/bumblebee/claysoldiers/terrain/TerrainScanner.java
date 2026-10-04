package net.bumblebee.claysoldiers.terrain;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Scans terrain columns from the world into a {@link TerrainScan}. Cheap: per column one heightmap read
 * ({@code WORLD_SURFACE}, the highest non-air block), then a walk down through trees, plants and fluids to the ground
 * (usually 1 to 8 blocks). Only chunks that are already loaded are read; columns in other chunks stay unknown, and
 * nothing is loaded or generated.
 */
public final class TerrainScanner {
    /** Most blocks walked down from a column's highest block looking for ground (taller trees count as ground). */
    private static final int MAX_WALK = 64;

    private TerrainScanner() {
    }

    /**
     * Scans the square of {@code 2 * radius + 1} columns around {@code center} (its y doesn't matter).
     */
    public static TerrainScan scan(ServerLevel level, BlockPos center, int radius) {
        long start = System.nanoTime();
        TerrainScan scan = TerrainScan.around(center.getX(), center.getZ(), radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = scan.minX() >> 4; chunkX <= scan.maxX() >> 4; chunkX++) {
            for (int chunkZ = scan.minZ() >> 4; chunkZ <= scan.maxZ() >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int fromX = Math.max(scan.minX(), chunkX << 4);
                int toX = Math.min(scan.maxX(), (chunkX << 4) + 15);
                int fromZ = Math.max(scan.minZ(), chunkZ << 4);
                int toZ = Math.min(scan.maxZ(), (chunkZ << 4) + 15);
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        scanColumn(level, chunk, scan, x, z, pos);
                    }
                }
            }
        }
        LevelChunk centerChunk = level.getChunkSource().getChunkNow(center.getX() >> 4, center.getZ() >> 4);
        if (centerChunk != null) {
            int y = scan.known(center.getX(), center.getZ()) ? scan.ground(center.getX(), center.getZ()) : center.getY();
            scan.setBiome(kind(centerChunk.getNoiseBiome(QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(y), QuartPos.fromBlock(center.getZ()))));
        }
        scan.setCostNanos(System.nanoTime() - start);
        DebugLog.event(DebugCategory.TERRAIN, null, () -> "scanned %d columns around %s (%d loaded, biome %s) in %.2f ms".formatted(
                scan.size() * scan.size(), center.toShortString(), scan.knownColumns(), scan.biome(), scan.costNanos() / 1_000_000.0));
        return scan;
    }

    private static void scanColumn(ServerLevel level, LevelChunk chunk, TerrainScan scan, int x, int z, BlockPos.MutableBlockPos pos) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        int minY = level.getMinBuildHeight();
        int water = 0;
        int treeBlocks = 0;
        int vegetation = 0;
        boolean lava = false;
        int y = top;
        BlockState ground = null;
        for (; y >= minY && y > top - MAX_WALK; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                continue;
            }
            FluidState fluid = state.getFluidState();
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                treeBlocks++;
                vegetation++;
            } else if (!fluid.isEmpty() && (state.getBlock() instanceof LiquidBlock || !state.blocksMotion())) {
                if (fluid.is(FluidTags.LAVA)) {
                    lava = true;
                } else {
                    water++;
                }
            } else if (!state.blocksMotion()) {
                vegetation++;
            } else {
                ground = state;
                break;
            }
        }
        scan.setColumn(x, z, y, water, lava, treeBlocks, vegetation, top,
                ground == null ? null : BuiltInRegistries.BLOCK.getKey(ground.getBlock()));
    }

    static BiomeKind kind(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return BiomeKind.WET;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN)) {
            return BiomeKind.MOUNTAIN;
        }
        return BiomeKind.NORMAL;
    }
}
