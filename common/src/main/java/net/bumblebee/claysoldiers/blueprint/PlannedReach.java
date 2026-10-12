package net.bumblebee.claysoldiers.blueprint;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Implemented by a builder's reach test that can use a build plan's standing spots (D80): whether the builder can walk to
 * one of {@code spots} (or, failing that, anywhere it can place {@code target} from).
 */
public interface PlannedReach {
    boolean canReach(BlockPos target, List<BlockPos> spots);
}
