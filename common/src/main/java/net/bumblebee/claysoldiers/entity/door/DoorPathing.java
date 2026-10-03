package net.bumblebee.claysoldiers.entity.door;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * The soldiers' two navigations (walking, and swimming for soldiers that can swim), identical to vanilla's except that
 * the pathfinder asks {@link DoorAccess} about every closed wooden door: a door the soldier may not open counts as a
 * wall, like an iron door. So soldiers never plan a path through a door they would be refused at.
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
     * Vanilla ground navigation with {@link #withDoorAccess}.
     */
    public static class Ground extends GroundPathNavigation {
        public Ground(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new WalkNodeEvaluator() {
                @Override
                public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                    return withDoorAccess(mob, context, x, y, z, super.getPathType(context, x, y, z));
                }
            };
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }

    /**
     * Vanilla amphibious navigation (soldiers that can swim) with {@link #withDoorAccess}.
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
                    return withDoorAccess(mob, context, x, y, z, super.getPathType(context, x, y, z));
                }
            };
            this.nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }
    }
}
