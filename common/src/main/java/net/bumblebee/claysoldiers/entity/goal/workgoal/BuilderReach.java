package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The one rule for where a builder can place a blueprint block from, shared by the "is it reachable?"
 * check and the actual placing, so a builder is never handed a block it cannot place.
 * <p>
 * Builders have "long arms" (Fix B): a block can be placed when its center is less than {@link #HORIZONTAL_REACH}
 * blocks sideways from the builder's feet (up to 2 blocks away), from {@link #MAX_PLACE_DEPTH} below up to
 * {@link #MAX_PLACE_HEIGHT} above the feet. This includes everything the old rule (less than 2 blocks
 * straight-line) allowed.
 */
public final class BuilderReach {
    /** A block's center must be less than this far sideways from the builder's feet (so up to 2 blocks away). */
    public static final double HORIZONTAL_REACH = 2.5;
    /** How many blocks above its feet a builder can place a block (enough for all built-in blueprints). */
    public static final int MAX_PLACE_HEIGHT = 8;
    /** How many blocks below its feet a builder can place a block. */
    public static final int MAX_PLACE_DEPTH = 2;

    private BuilderReach() {
    }

    /**
     * Returns whether {@code builder} can place a block at {@code target} from where it stands now. Measured from the
     * center of the block it stands in, exactly like the end of a path is checked, so arriving at the end of an
     * accepted path always counts as "in reach".
     */
    public static boolean canPlaceFrom(Entity builder, BlockPos target) {
        return canPlaceFrom(Vec3.atBottomCenterOf(builder.blockPosition()), target);
    }

    /**
     * Returns whether a builder standing with its feet at {@code feet} can place a block at {@code target}.
     */
    public static boolean canPlaceFrom(Vec3 feet, BlockPos target) {
        double dx = target.getX() + 0.5 - feet.x;
        double dz = target.getZ() + 0.5 - feet.z;
        int dy = target.getY() - Mth.floor(feet.y);
        return dx * dx + dz * dz < HORIZONTAL_REACH * HORIZONTAL_REACH && dy >= -MAX_PLACE_DEPTH && dy <= MAX_PLACE_HEIGHT;
    }

    /**
     * Returns a path to a spot from which {@code builder} can place a block at {@code target}, or {@code null}.
     * <p>
     * First a path towards the block itself is tried (vanilla pathfinding stops at the first spot within N steps,
     * counting sideways and up/down steps together; the end is checked with {@link #canPlaceFrom}). If that fails,
     * e.g. for a block high above the ground, one path to the nearest standing spot within reach is tried.
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
        Reach reach = find(builder, target);
        if (reach.reachable()) {
            return reach;
        }
        DebugLog.event(DebugCategory.PATHING, builder, "can't place at %s: %s", target, reach.whyNot());
        if (explainDoors) {
            BlockPos door = closedDoorInTheWay(builder, target);
            if (door != null) {
                return new Reach(null, "closed door in the way at (" + door.toShortString() + ")");
            }
        }
        return reach;
    }

    private static Reach find(PathfinderMob builder, BlockPos target) {
        for (int accuracy = 2; accuracy >= 1; accuracy--) {
            Path path = builder.getNavigation().createPath(target, accuracy);
            if (path != null && path.canReach() && endsWithinReach(path, target)) {
                return new Reach(path, "");
            }
        }
        Set<BlockPos> spots = standingSpots(builder.level(), target);
        if (spots.isEmpty()) {
            return new Reach(null, "no standing spot within reach");
        }
        Path path = builder.getNavigation().createPath(spots, 0);
        if (path != null && path.canReach() && endsWithinReach(path, target)) {
            return new Reach(path, "");
        }
        return new Reach(null, path == null ? "no path at all" : "can't walk to a standing spot within reach");
    }

    /**
     * Spots a builder could stand on to place a block at {@code target}: an empty block (feet) with something solid
     * under it, in the columns within {@link #HORIZONTAL_REACH} around the target, between {@link #MAX_PLACE_DEPTH} above and
     * {@link #MAX_PLACE_HEIGHT} below the target. The target itself is excluded.
     */
    private static Set<BlockPos> standingSpots(Level level, BlockPos target) {
        Set<BlockPos> spots = new HashSet<>();
        int range = Mth.floor(HORIZONTAL_REACH);
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int below = -MAX_PLACE_DEPTH; below <= MAX_PLACE_HEIGHT; below++) {
                    BlockPos feet = target.offset(dx, -below, dz);
                    if (!feet.equals(target) && level.isLoaded(feet) && canStandAt(level, feet)
                            && canPlaceFrom(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5), target)) {
                        spots.add(feet);
                    }
                }
            }
        }
        return spots;
    }

    private static boolean canStandAt(Level level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState ground = level.getBlockState(feet.below());
        return feetState.getCollisionShape(level, feet).isEmpty() && feetState.getFluidState().isEmpty()
                && !ground.getCollisionShape(level, feet.below()).isEmpty();
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
            Path path = find(builder, target).path();
            if (path == null) {
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
