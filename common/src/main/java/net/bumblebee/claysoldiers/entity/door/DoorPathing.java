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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
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
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The soldiers' two navigations (walking, and swimming for soldiers that can swim), identical to vanilla's except that
 * the pathfinder asks {@link DoorAccess} about every closed wooden door: a door the soldier may not open counts as a
 * wall, like an iron door. So soldiers never plan a path through a door they would be refused at. On scaffold blocks
 * the walking navigation also follows its path precisely (stage 3). An open trapdoor is never a floor (D65): vanilla
 * lets mobs walk onto open trapdoors and fall through them.
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

    /** Both soldier rules on vanilla's answer. */
    static PathType soldierRules(@Nullable Mob mob, PathfindingContext context, int x, int y, int z, PathType type) {
        return withDoorAccess(mob, context, x, y, z, withoutOpenTrapdoorFloors(context, x, y, z, type));
    }

    /**
     * Vanilla ground navigation with {@link #withDoorAccess} and {@link #withoutOpenTrapdoorFloors}.
     */
    public static class Ground extends GroundPathNavigation {
        /** Extra walked length every path of this navigation may have (D62); 0 outside building work. */
        private float rangeBonus;

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
            this.nodeEvaluator = new WalkNodeEvaluator() {
                @Override
                public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                    return soldierRules(mob, context, x, y, z, super.getPathType(context, x, y, z));
                }

                @Override
                public Node getStart() {
                    return startOutsideABlockedCell(mob, super.getStart(), this::canStartAt, this::getStartNode);
                }
            };
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
            this.nodeEvaluator = new AmphibiousNodeEvaluator(false) {
                @Override
                public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                    return soldierRules(mob, context, x, y, z, super.getPathType(context, x, y, z));
                }

                @Override
                public Node getStart() {
                    return startOutsideABlockedCell(mob, super.getStart(), this::canStartAt, this::getStartNode);
                }
            };
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }
}
