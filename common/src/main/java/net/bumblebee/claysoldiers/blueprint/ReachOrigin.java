package net.bumblebee.claysoldiers.blueprint;

import net.minecraft.core.BlockPos;

/**
 * Implemented by a builder's reach test: where the builder stands while it tests. A plan's memory of blocks this builder
 * found out of reach ({@code build.unreachable_recheck_ticks}) only holds while it still stands there: from another
 * spot the same block may be reachable.
 */
public interface ReachOrigin {
    BlockPos reachOrigin();
}
