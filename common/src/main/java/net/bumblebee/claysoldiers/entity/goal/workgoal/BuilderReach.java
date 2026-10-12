package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.ChainedPath;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one rule for where a builder can place a blueprint block from, shared by the "is it reachable?"
 * check and the actual placing, so a builder is never handed a block it cannot place.
 * <p>
 * Builders have "long arms" (Fix B): a block can be placed when its center is less than {@link #HORIZONTAL_REACH}
 * blocks sideways from the builder's feet (up to 2 blocks away), from {@link #MAX_PLACE_DEPTH} below up to
 * {@link #MAX_PLACE_HEIGHT} above the feet, but never the block it stands in. This includes everything the old rule
 * (less than 2 blocks straight-line) allowed.
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
        return canPlaceFrom(feet, target, 0);
    }

    /**
     * Like {@link #canPlaceFrom(Vec3, BlockPos)}, reaching {@code extraHeight} more above the feet: a builder inside a
     * scaffolding column whose block no column reaches the usual way stretches up (D78).
     */
    public static boolean canPlaceFrom(Vec3 feet, BlockPos target, int extraHeight) {
        double dx = target.getX() + 0.5 - feet.x;
        double dz = target.getZ() + 0.5 - feet.z;
        int dy = target.getY() - Mth.floor(feet.y);
        if (dy == 0 && Mth.floor(feet.x) == target.getX() && Mth.floor(feet.z) == target.getZ()) {
            return false; // the block the builder stands in: it would build itself in
        }
        return dx * dx + dz * dz < HORIZONTAL_REACH * HORIZONTAL_REACH && dy >= -MAX_PLACE_DEPTH && dy <= MAX_PLACE_HEIGHT + extraHeight;
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
        return pathToPlace(builder, target, List.of());
    }

    /**
     * Like {@link #pathToPlace(PathfinderMob, BlockPos)}, but tries the build plan's standing spots for the block first
     * (D80): one of them chosen per builder (builders spread round the block), then the nearest of them; only when none can be
     * walked to (the ground differs from the plan's, another soldier stands there) the normal search runs.
     */
    @Nullable
    public static Path pathToPlace(PathfinderMob builder, BlockPos target, List<BlockPos> plannedSpots) {
        return check(builder, target, false, plannedSpots).path();
    }

    /**
     * Result of {@link #check}: a path to a spot the block can be placed from, or why there is none.
     *
     * @param path       the path, or {@code null} when unreachable
     * @param whyNot     short reason when unreachable (e.g. "no path gets close enough", "closed door in the way at (x, y, z)")
     * @param closedDoor the closed door in the way, when that is why (only looked for with {@code explainDoors})
     */
    public record Reach(@Nullable Path path, String whyNot, @Nullable BlockPos closedDoor) {
        public Reach(@Nullable Path path, String whyNot) {
            this(path, whyNot, null);
        }

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
        return check(builder, target, explainDoors, List.of());
    }

    /** Like {@link #check(PathfinderMob, BlockPos, boolean)}, with the build plan's standing spots tried first (D80). */
    public static Reach check(PathfinderMob builder, BlockPos target, boolean explainDoors, List<BlockPos> plannedSpots) {
        Reach reach;
        if (plannedSpots.isEmpty()) {
            reach = find(builder, target);
        } else if (Tuning.BUILD_PLAN_SPOTS_FIRST.get() == 0) {
            // The normal search first (the nearest spot the builder can place from), the plan's spots only when it fails.
            reach = find(builder, target);
            if (!reach.reachable()) {
                reach = findPlanned(builder, target, plannedSpots);
            }
        } else {
            reach = findPlanned(builder, target, plannedSpots);
        }
        if (reach.reachable()) {
            return reach;
        }
        DebugLog.event(DebugCategory.PATHING, builder, "can't place at %s: %s", target, reach.whyNot());
        if (explainDoors) {
            BlockPos door = closedDoorInTheWay(builder, target);
            if (door != null) {
                return new Reach(null, "closed door in the way at (" + door.toShortString() + ")", door);
            }
        }
        return reach;
    }

    private static Reach findPlanned(PathfinderMob builder, BlockPos target, List<BlockPos> plannedSpots) {
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos spot : plannedSpots) {
            if (builder.level().isLoaded(spot) && canStandAt(builder.level(), spot) && !otherSoldierAt(builder, spot)
                    && canPlaceFrom(new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5), target)) {
                free.add(spot);
            }
        }
        if (!free.isEmpty()) {
            // The nearest by path of the plan's free spots: they are spread round the block (both sides of a wall), and
            // a spot picked at random sent builders round the building half the time (D80; build times up to twice main's).
            Path path = longPath(builder, new HashSet<>(free));
            if (path != null && path.canReach() && endsWithinReach(path, target)) {
                return new Reach(path, "");
            }
        }
        DebugLog.event(DebugCategory.PATHING, builder, "none of the plan's %s standing spots for %s can be walked to (%s free), searching", plannedSpots.size(), target, free.size());
        return find(builder, target);
    }

    /** Whether another soldier (or any other mob) stands in {@code spot}: builders spread out over a block's spots. */
    private static boolean otherSoldierAt(PathfinderMob builder, BlockPos spot) {
        return !builder.level().getEntitiesOfClass(Mob.class, new AABB(spot), mob -> mob != builder).isEmpty();
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
        Path path = longPath(builder, spots);
        if (path != null && path.canReach() && endsWithinReach(path, target)) {
            return new Reach(path, "");
        }
        if (path == null) {
            return new Reach(null, "no path at all");
        }
        // Counted per reason in request summaries, so the details go to the recorder only.
        Node end = path.getEndNode();
        DebugLog.event(DebugCategory.PATHING, builder, () -> "path to a standing spot for %s ends at %s after %s steps (follow range %s, %s spots)"
                .formatted(target, end == null ? "-" : end.asBlockPos(), path.getNodeCount(), builder.getAttributeValue(Attributes.FOLLOW_RANGE), spots.size()));
        return new Reach(null, "can't walk to a standing spot within reach");
    }

    /**
     * A path to one of {@code targets} with a longer limit than the navigation's own (D61). Vanilla only accepts paths
     * whose walked length is under the follow range (16), measured from where the builder asks, usually beside its
     * storage, so the far side of bigger buildings was out of reach. This search may be {@code build.path_range_bonus}
     * blocks longer and look at {@code build.path_nodes_multiplier} times more steps (or as many as the navigation's own
     * searches, when more), with the navigation's own rules (its node evaluator: doors, water). A search that runs out of
     * steps goes on from where it ended ({@link ChainedPath}, D76). Nothing else changes: the soldier's follow range and its
     * navigation stay as they are, so walking to storage, combat and vanilla's own route updates keep the normal limits.
     */
    @Nullable
    private static Path longPath(PathfinderMob builder, Set<BlockPos> targets) {
        double baseRange = builder.getAttributeBaseValue(Attributes.FOLLOW_RANGE);
        float range = (float) Math.max(builder.getAttributeValue(Attributes.FOLLOW_RANGE), baseRange + Tuning.BUILD_PATH_RANGE_BONUS.get());
        float multiplier = Math.max(Tuning.BUILD_PATH_NODES_MULTIPLIER.get(),
                builder.getNavigation() instanceof DoorPathing.Ground ground ? ground.getNodesMultiplier() : 1.0F);
        int nodes = Mth.floor(baseRange * 16.0 * multiplier);
        return ChainedPath.find(builder, targets, range, nodes, 0);
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
     * If the block would be reachable when the builder could open every door (wooden ones it may not use, and iron
     * ones, which no soldier can open), returns the first door on that path. The navigation's door setting and the
     * builder's iron door path cost are restored afterwards, so this doesn't change behavior.
     */
    @Nullable
    private static BlockPos closedDoorInTheWay(PathfinderMob builder, BlockPos target) {
        NodeEvaluator evaluator = builder.getNavigation().getNodeEvaluator();
        boolean couldOpen = evaluator.canOpenDoors();
        float ironDoorMalus = builder.getPathfindingMalus(PathType.DOOR_IRON_CLOSED);
        evaluator.setCanOpenDoors(true);
        builder.setPathfindingMalus(PathType.DOOR_IRON_CLOSED, 0);
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
            builder.setPathfindingMalus(PathType.DOOR_IRON_CLOSED, ironDoorMalus);
        }
    }

    /**
     * Returns whether {@code builder} can walk somewhere it can place a block at {@code target} from.
     */
    public static boolean canReachToPlace(PathfinderMob builder, BlockPos target) {
        return pathToPlace(builder, target) != null;
    }

    /** Like {@link #canReachToPlace(PathfinderMob, BlockPos)}, with the build plan's standing spots tried first (D80). */
    public static boolean canReachToPlace(PathfinderMob builder, BlockPos target, List<BlockPos> plannedSpots) {
        return pathToPlace(builder, target, plannedSpots) != null;
    }

    private static boolean endsWithinReach(Path path, BlockPos target) {
        Node end = path.getEndNode();
        return end != null && canPlaceFrom(new Vec3(end.x + 0.5, end.y, end.z + 0.5), target);
    }
}
