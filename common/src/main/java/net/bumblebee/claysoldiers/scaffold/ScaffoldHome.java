package net.bumblebee.claysoldiers.scaffold;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by a builder's reachability test when it asks an easel for work: where the builder comes from (its
 * storage). The scaffold planner measures walking distances from there, since builders fetch every block from it.
 */
public interface ScaffoldHome {
    @Nullable
    BlockPos scaffoldHome();
}
