package net.bumblebee.claysoldiers.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Scans terrain columns from the world into a {@link TerrainScan}. Stage 2 skeleton: not implemented yet.
 */
public final class TerrainScanner {
    private TerrainScanner() {
    }

    public static TerrainScan scan(ServerLevel level, BlockPos center, int radius) {
        return TerrainScan.around(center.getX(), center.getZ(), radius);
    }
}
