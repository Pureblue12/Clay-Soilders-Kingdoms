package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tiny soldiers can step from a block one high (soil, hay, a slab) onto a fence or wall top next to it (0.5 up, under
 * their step height), but vanilla pathfinding can't start a path from on top of a fence or wall (1.5 high): a builder
 * standing there finds no way anywhere and waits for good (found in the batch-1 builder tests). This puts it down on a
 * free spot next to the fence.
 */
final class OffTheFence {
    private OffTheFence() {
    }

    /**
     * When {@code soldier} stands on a fence, wall or fence gate top, moves it onto the nearest free standing spot beside
     * (the fence's level first, then one higher) and records it (BUILD).
     *
     * @return whether it was moved
     */
    static boolean stepDown(PathfinderMob soldier) {
        if (!soldier.onGround()) {
            return false;
        }
        Level level = soldier.level();
        BlockPos on = soldier.getOnPos();
        BlockState below = level.getBlockState(on);
        if (!below.is(BlockTags.FENCES) && !below.is(BlockTags.WALLS) && !below.is(BlockTags.FENCE_GATES)) {
            return false;
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos feet = on.relative(direction).above(dy);
                if (canStandAt(level, feet)) {
                    soldier.getNavigation().stop();
                    soldier.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                    DebugLog.event(DebugCategory.BUILD, soldier, "stood on %s at %s (no path from there): stepped down to %s",
                            below.getBlock().getName().getString(), on, feet);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canStandAt(Level level, BlockPos feet) {
        BlockState under = level.getBlockState(feet.below());
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && !under.getCollisionShape(level, feet.below()).isEmpty()
                && !under.is(BlockTags.FENCES) && !under.is(BlockTags.WALLS) && !under.is(BlockTags.FENCE_GATES);
    }
}
