package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Scaffolding in the world: plans a job when builders can't reach the rest of a structure, hands out scaffold
 * requests, tears scaffold down, and cleans up interrupted builds. Stage 3 skeleton: not implemented yet.
 */
public final class Scaffolds {
    private Scaffolds() {
    }

    /** Plans the easel's scaffold now (if its structure needs one) and returns the job. */
    public static Optional<ScaffoldJob> ensureJob(ServerLevel level, EaselBlockEntity easel) {
        return Optional.empty();
    }

    /** Places the next planned scaffold block at once, as a builder would (dev tools and tests). */
    public static boolean placeNext(ServerLevel level, BlockPos easel) {
        return false;
    }

    /** Removes the easel's scaffold at once (blocks a player changed are kept and reported) and forgets the job. */
    public static int cleanup(ServerLevel level, BlockPos easel, String why) {
        return 0;
    }

    /** Cleans up jobs whose easel is gone or has no plan any more (runs every {@code scaffold.sweep_ticks}). */
    public static void sweep(MinecraftServer server) {
    }
}
