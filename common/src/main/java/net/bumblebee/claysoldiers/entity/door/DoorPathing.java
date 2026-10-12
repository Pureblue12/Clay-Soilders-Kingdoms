package net.bumblebee.claysoldiers.entity.door;

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The soldiers' two navigations (walking, and swimming for soldiers that can swim), identical to vanilla's except that
 * the pathfinder asks {@link DoorAccess} about every closed wooden door: a door the soldier may not open counts as a
 * wall, like an iron door. So soldiers never plan a path through a door they would be refused at. On scaffold blocks
 * the walking navigation also follows its path precisely (stage 3). An open trapdoor is never a floor (D65): vanilla
 * lets mobs walk onto open trapdoors and fall through them. Nor is the top of a fence, wall or pane (D68): a tiny
 * soldier falls past the post.
 */
public final class DoorPathing {
    private DoorPathing() {
    }

    /**
     * Turns a closed wooden door the soldier may not open into a locked one ({@code DOOR_IRON_CLOSED}).
     */
    static PathType withDoorAccess(@Nullable Mob mob, PathfindingContext context, int x, int y, int z, PathType type) {
        if (type == PathType.DOOR_WOOD_CLOSED && mob instanceof AbstractClaySoldierEntity soldier) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = context.level().getBlockState(pos);
            BlockPos lower = state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            if (!DoorAccess.canUse(soldier, lower)) {
                return PathType.DOOR_IRON_CLOSED;
            }
        }
        return type;
    }

    /**
     * Vanilla treats every trapdoor cell as something to stand in, open or closed, and the cell above it as having a
     * floor: mobs walk onto open trapdoors and fall (the vanilla trapdoor trap). The town hall's builders walked out of
     * an unglazed window onto its open shutters and fell seven blocks, over and over, until they died (D65). Here an
     * open trapdoor's cell is air to the pathfinder (so vanilla's fall check runs below it), and the cell above an open
     * trapdoor has no floor. Closed trapdoors stay walkable.
     */
    static PathType withoutOpenTrapdoorFloors(PathfindingContext context, int x, int y, int z, PathType type) {
        if (type == PathType.TRAPDOOR && isOpenTrapdoor(context, x, y, z)) {
            PathType below = context.getPathTypeFromState(x, y - 1, z);
            if (below == PathType.TRAPDOOR && isOpenTrapdoor(context, x, y - 1, z)) {
                below = PathType.OPEN;
            }
            // the same as vanilla does for an air cell
            PathType here = below != PathType.WALKABLE && below != PathType.OPEN && below != PathType.WATER && below != PathType.LAVA
                    ? PathType.WALKABLE : PathType.OPEN;
            return here == PathType.WALKABLE ? WalkNodeEvaluator.checkNeighbourBlocks(context, x, y, z, here) : here;
        }
        if (type == PathType.WALKABLE && isOpenTrapdoor(context, x, y - 1, z)) {
            return PathType.OPEN;
        }
        return type;
    }

    private static boolean isOpenTrapdoor(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
        return state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
    }

    /**
     * Vanilla counts the cell above a fence, wall, glass pane or iron bars as a floor, because a big mob always touches
     * the post. A tiny soldier at the cell's edge misses it and falls through: town hall builders planned a 3-block drop
     * onto a pane or fence top, fell straight through to the ground below, and died after a few such falls (structure
     * build run 38140495889, fall traces; D68). For soldiers such a cell has no floor.
     */
    static PathType withoutThinBlockFloors(PathfindingContext context, int x, int y, int z, PathType type) {
        if (type != PathType.WALKABLE) {
            return type;
        }
        BlockPos feet = new BlockPos(x, y, z);
        BlockGetter level = context.level();
        if (!open(level, feet)) {
            return type;
        }
        BlockState below = level.getBlockState(feet.below());
        return below.getBlock() instanceof CrossCollisionBlock || below.getBlock() instanceof WallBlock ? PathType.OPEN : type;
    }

    /**
     * A tiny soldier fits beside the post of a fence, wall or pane, inside its cell. Vanilla then starts every path
     * from a cell it may not enter: the path is one step long and the soldier stands there for good (a cottage builder
     * returning to the chest, 9,200 ticks). Such a soldier starts its path from the nearest free cell beside it
     * instead, and walks out (D66). Vanilla's own fallback looks at the corners of the mob's body, which for a tiny
     * soldier are all in the same cell. Only a cell the soldier can slide to counts ({@link #canSlideTo}); when there
     * is none, vanilla's one-step path stays and {@code OffTheFence} puts the soldier out (BUILD).
     */
    @Nullable
    static Node startOutsideABlockedCell(Mob mob, Node start, java.util.function.Predicate<BlockPos> canStartAt,
                                         java.util.function.Function<BlockPos, Node> startNode) {
        if (start == null || start.costMalus >= 0.0F) {
            return start;
        }
        BlockPos here = start.asBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = here.relative(side);
            if (canStartAt.test(next) && canSlideTo(mob, next)) {
                double distance = mob.position().distanceToSqr(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = next;
                }
            }
        }
        if (best == null) {
            return start;
        }
        if (mob instanceof AbstractClaySoldierEntity soldier) {
            DebugLog.event(DebugCategory.PATHING, soldier, "inside %s at %s, starting the path from %s", start.type, here, best);
        }
        return startNode.apply(best);
    }

    /**
     * Whether {@code mob} can move in a straight line to the middle of {@code cell} without hitting a block: the whole
     * sweep of its body must be free. A soldier in a fence cell's corner pocket, behind the post and an arm, can't
     * (farm L2/L3 on CI: three builders stood in such a pocket with an 8-step path they could never start walking).
     */
    private static boolean canSlideTo(Mob mob, BlockPos cell) {
        double dx = cell.getX() + 0.5 - mob.getX();
        double dz = cell.getZ() + 0.5 - mob.getZ();
        return mob.level().noCollision(mob, mob.getBoundingBox().expandTowards(dx, 0, dz));
    }

    /**
     * Vanilla lets a mob drop up to 3 blocks into any cell, also into a 1x1 pit it can never leave: three keep builders
     * ended in a shaft of the keep's ground floor, walled 2 high on every side, for 15,000 ticks (structure build run
     * 38129993219, keep L3; the easel already refuses to wall a soldier in, D64, so they dropped in). A cell with no way
     * out sideways (no open neighbour, no door, no block to hop onto) is blocked to the pathfinder (D67). The cell a
     * soldier already stands in is not checked here: it is the path's start, and {@link #startOutsideABlockedCell}
     * handles that.
     */
    static PathType withoutPits(PathfindingContext context, int x, int y, int z, PathType type) {
        if (type != PathType.WALKABLE) {
            return type;
        }
        return hasExit(context.level(), new BlockPos(x, y, z)) ? type : PathType.BLOCKED;
    }

    /**
     * Whether a soldier standing with its feet at {@code feet} can leave the cell sideways: walk or drop into an open
     * neighbour (a block over it that doesn't reach down to its cell's bottom leaves room, {@link #headRoom}), go through
     * a door, or hop onto a one-block step. The easel's {@code ServerBlueprintPlan.hasExit} is the same rule, but with
     * doors closed and two free cells over a neighbour (stricter: it only makes the easel wait).
     */
    static boolean hasExit(BlockGetter level, BlockPos feet) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = feet.relative(side);
            if (level.getBlockState(next).getBlock() instanceof DoorBlock) {
                return true;
            }
            boolean nextFeet = open(level, next);
            boolean nextHead = headRoom(level, next.above());
            if (nextFeet && nextHead) {
                return true; // walk (or drop)
            }
            if (!nextFeet && nextHead && open(level, next.above(2)) && open(level, feet.above(2))) {
                return true; // hop up one block
            }
        }
        return false;
    }

    private static boolean open(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Whether the cell above a soldier's feet leaves it room: nothing there, or a block whose shape doesn't reach down to
     * the cell's bottom (a hanging lantern, a top slab, a closed top trapdoor). A soldier is under half a block tall: keep
     * L3's back tower nook, whose only way out passed under the top room's hanging lantern, counted as a pit, so no
     * builder ever reached the scaffold column beside it (D80; the scaffold planner's {@code ScaffoldInput.overhead}).
     */
    private static boolean headRoom(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() || shape.min(Direction.Axis.Y) > 0;
    }

    /** All soldier rules on vanilla's answer. */
    static PathType soldierRules(@Nullable Mob mob, PathfindingContext context, int x, int y, int z, PathType type) {
        return withDoorAccess(mob, context, x, y, z, withoutPits(context, x, y, z, withoutThinBlockFloors(context, x, y, z, withoutOpenTrapdoorFloors(context, x, y, z, type))));
    }

    /**
     * Vanilla ground navigation with {@link #withDoorAccess} and {@link #withoutOpenTrapdoorFloors}.
     */
    public static class Ground extends GroundPathNavigation {
        /** Extra walked length every path of this navigation may have (D62); 0 outside building work. */
        private float rangeBonus;
        /** How many times more steps this navigation's searches may look at (vanilla's base: 16 per block of follow range). */
        private float nodesMultiplier = 1.0F;

        public Ground(Mob mob, Level level) {
            super(mob, level);
        }

        /**
         * Lets every path of this soldier be {@code bonus} blocks longer than its follow range allows (vanilla rejects
         * paths walked longer than the follow range, 16, and counts the length generously). Builders set it while they
         * work for a build (D62): the way around a big building, back to the easel or the chest, is longer than 16.
         * The region searched grows with it, so searches cost more while it is set.
         */
        public void setRangeBonus(float bonus) {
            this.rangeBonus = bonus;
        }

        public float getRangeBonus() {
            return rangeBonus;
        }

        @Override
        public void setMaxVisitedNodesMultiplier(float multiplier) {
            super.setMaxVisitedNodesMultiplier(multiplier);
            this.nodesMultiplier = multiplier;
        }

        /** The multiplier last given to {@link #setMaxVisitedNodesMultiplier} (vanilla keeps it private); 1 by default. */
        public float getNodesMultiplier() {
            return nodesMultiplier;
        }

        @Nullable
        @Override
        protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange + rangeBonus);
        }

        /**
         * On scaffold (standing on a scaffold block, or the next step is one), the path is followed precisely: a step
         * counts as reached only near its center, and no corners are cut. Tiny soldiers otherwise count a step as
         * reached 0.675 blocks early and skip ahead, which walks them off 1-block floating steps. Elsewhere vanilla.
         */
        @Override
        protected void followThePath() {
            if (!(mob.level() instanceof ServerLevel serverLevel) || !onScaffold(serverLevel)) {
                super.followThePath();
                return;
            }
            Vec3 position = this.getTempMobPos();
            double precision = Tuning.SCAFFOLD_STEP_PRECISION.get() / 100.0;
            this.maxDistanceToWaypoint = (float) precision;
            Vec3i next = this.path.getNextNodePos();
            double dx = Math.abs(mob.getX() - (next.getX() + 0.5));
            double dy = Math.abs(mob.getY() - next.getY());
            double dz = Math.abs(mob.getZ() - (next.getZ() + 0.5));
            if (dx <= precision && dz <= precision && dy < 1.0) {
                this.path.advance();
            }
            this.doStuckDetection(position);
        }

        private boolean onScaffold(ServerLevel level) {
            if (this.path == null || this.path.isDone()) {
                return false;
            }
            ScaffoldSavedData scaffold = ScaffoldSavedData.get(level);
            return !scaffold.jobs().isEmpty() && (scaffold.isScaffold(mob.blockPosition().below())
                    || scaffold.isScaffold(BlockPos.containing(this.path.getNextNodePos().getX(), this.path.getNextNodePos().getY() - 1, this.path.getNextNodePos().getZ())));
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new WalkNodes(mob);
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /**
     * Vanilla amphibious navigation (soldiers that can swim) with the same rules.
     */
    public static class Water extends AmphibiousPathNavigation {
        public Water(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new SwimNodes(mob);
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /**
     * A node evaluator whose next search can start somewhere other than where the soldier stands: {@link ChainedPath}
     * continues a search from the cell where the previous one ran out of steps (D76).
     */
    public interface StartAt {
        /** The cell the next search starts from, or null for where the soldier stands (always reset after use). */
        void startAt(@Nullable BlockPos cell);
    }

    /** The walking navigation's node evaluator: vanilla's with {@link #soldierRules}. */
    static final class WalkNodes extends WalkNodeEvaluator implements StartAt {
        private final Mob owner;
        @Nullable
        private BlockPos startAt;

        WalkNodes(Mob owner) {
            this.owner = owner;
        }

        @Override
        public void startAt(@Nullable BlockPos cell) {
            this.startAt = cell;
        }

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            return soldierRules(owner, context, x, y, z, super.getPathType(context, x, y, z));
        }

        @Override
        public Node getStart() {
            if (startAt != null) {
                return getStartNode(startAt);
            }
            return startOutsideABlockedCell(owner, super.getStart(), this::canStartAt, this::getStartNode);
        }
    }

    /** The swimming navigation's node evaluator: vanilla's amphibious one with {@link #soldierRules}. */
    static final class SwimNodes extends AmphibiousNodeEvaluator implements StartAt {
        private final Mob owner;
        @Nullable
        private BlockPos startAt;

        SwimNodes(Mob owner) {
            super(false);
            this.owner = owner;
        }

        @Override
        public void startAt(@Nullable BlockPos cell) {
            this.startAt = cell;
        }

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            return soldierRules(owner, context, x, y, z, super.getPathType(context, x, y, z));
        }

        @Override
        public Node getStart() {
            if (startAt != null) {
                return getStartNode(startAt);
            }
            return startOutsideABlockedCell(owner, super.getStart(), this::canStartAt, this::getStartNode);
        }
    }
}
