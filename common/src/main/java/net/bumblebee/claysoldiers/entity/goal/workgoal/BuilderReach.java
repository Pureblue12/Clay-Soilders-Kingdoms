package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The one rule for where a builder can place a blueprint block from, shared by the "is it reachable?"
 * check and the actual placing, so a builder is never handed a block it cannot place.
 */
public final class BuilderReach {
    /**
     * A block can be placed when its center is less than this far (straight line) from the builder's feet.
     */
    public static final double PLACE_DISTANCE = 2.0;

    private BuilderReach() {
    }

    /**
     * Returns whether a builder standing with its feet at {@code feet} can place a block at {@code target}.
     */
    public static boolean canPlaceFrom(Vec3 feet, BlockPos target) {
        return target.closerToCenterThan(feet, PLACE_DISTANCE);
    }

    /**
     * Returns a path to a spot from which {@code builder} can place a block at {@code target}, or {@code null}.
     * <p>
     * Vanilla pathfinding stops at the first spot within N steps (counting sideways and up/down steps together)
     * of the target, which can still be too far to place from (e.g. 2 blocks straight sideways or 2 below).
     * So the path's end is checked with {@link #canPlaceFrom}; if it fails, a path ending within 1 step is tried,
     * which is always close enough.
     */
    @Nullable
    public static Path pathToPlace(PathfinderMob builder, BlockPos target) {
        for (int accuracy = 2; accuracy >= 1; accuracy--) {
            Path path = builder.getNavigation().createPath(target, accuracy);
            if (path != null && path.canReach() && endsWithinReach(path, target)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Returns whether {@code builder} can walk somewhere it can place a block at {@code target} from.
     */
    public static boolean canReachToPlace(PathfinderMob builder, BlockPos target) {
        return pathToPlace(builder, target) != null;
    }

    private static boolean endsWithinReach(Path path, BlockPos target) {
        Node end = path.getEndNode();
        return end != null && canPlaceFrom(new Vec3(end.x + 0.5, end.y, end.z + 0.5), target);
    }
}
