package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
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
        return check(builder, target, false).path();
    }

    /**
     * Result of {@link #check}: a path to a spot the block can be placed from, or why there is none.
     *
     * @param path   the path, or {@code null} when unreachable
     * @param whyNot short reason when unreachable (e.g. "no path gets close enough", "closed door in the way at (x, y, z)")
     */
    public record Reach(@Nullable Path path, String whyNot) {
        public boolean reachable() {
            return path != null;
        }
    }

    /**
     * Like {@link #pathToPlace}, but also says why a block is unreachable.
     *
     * @param explainDoors when unreachable, also try a path as if the builder could open doors, to detect a closed door
     *                     in the way. This costs one extra path search, so only use it while debugging.
     */
    public static Reach check(PathfinderMob builder, BlockPos target, boolean explainDoors) {
        String whyWithin2 = "";
        String whyWithin1 = "";
        for (int accuracy = 2; accuracy >= 1; accuracy--) {
            Path path = builder.getNavigation().createPath(target, accuracy);
            if (path != null && path.canReach() && endsWithinReach(path, target)) {
                return new Reach(path, "");
            }
            String why = path == null ? "no path at all" : !path.canReach() ? "no path gets close enough" : "path ends too far away to place from";
            if (accuracy == 2) {
                whyWithin2 = why;
            } else {
                whyWithin1 = why;
            }
        }
        DebugLog.event(DebugCategory.PATHING, builder, "can't place at %s: within 2 steps: %s; within 1 step: %s", target, whyWithin2, whyWithin1);
        String why = whyWithin1;
        if (explainDoors) {
            BlockPos door = closedDoorInTheWay(builder, target);
            if (door != null) {
                why = "closed door in the way at (" + door.toShortString() + ")";
            }
        }
        return new Reach(null, why);
    }

    /**
     * If the block would be reachable when the builder could open doors, returns the first door on that path.
     * The navigation's door setting is restored afterwards, so this doesn't change behavior.
     */
    @Nullable
    private static BlockPos closedDoorInTheWay(PathfinderMob builder, BlockPos target) {
        NodeEvaluator evaluator = builder.getNavigation().getNodeEvaluator();
        boolean couldOpen = evaluator.canOpenDoors();
        evaluator.setCanOpenDoors(true);
        try {
            Path path = builder.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach() || !endsWithinReach(path, target)) {
                return null;
            }
            for (int i = 0; i < path.getNodeCount(); i++) {
                BlockPos pos = path.getNode(i).asBlockPos();
                if (builder.level().getBlockState(pos).getBlock() instanceof DoorBlock) {
                    return pos;
                }
            }
            return null;
        } finally {
            evaluator.setCanOpenDoors(couldOpen);
        }
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
