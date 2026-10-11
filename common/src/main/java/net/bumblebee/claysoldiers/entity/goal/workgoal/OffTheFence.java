package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tiny soldiers can step from a block one high (soil, hay, a slab) onto a fence or wall top next to it (0.5 up, under
 * their step height), but vanilla pathfinding can't start a path from on top of a fence or wall (1.5 high): a builder
 * standing there finds no way anywhere and waits for good (found in the batch-1 builder tests). This puts it down on a
 * free spot next to the fence.
 * <p>
 * They also fit INSIDE a fence, wall or pane cell, beside the post (0.375 blocks of room). Pushed into a corner pocket
 * between the post and an arm, no path can start: the way to every free cell crosses the post (farm L2/L3 on CI: three
 * builders stood in one pocket for 4,000 ticks, each with a path it could never start walking; D66). This puts such a
 * soldier out onto the nearest free spot beside the cell.
 */
final class OffTheFence {
    private OffTheFence() {
    }

    /**
     * When {@code soldier} stands on a fence, wall or fence gate top, or inside a fence, wall or pane cell, moves it onto
     * the nearest free standing spot beside (the cell's level first, then one higher) and records it (BUILD).
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
        if (below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS) || below.is(BlockTags.FENCE_GATES)) {
            return moveBeside(soldier, on, "stood on %s at %s (no path from there): stepped down to %s", below);
        }
        BlockPos feet = soldier.blockPosition();
        BlockState in = level.getBlockState(feet);
        if (in.getBlock() instanceof CrossCollisionBlock || in.getBlock() instanceof WallBlock) {
            return moveBeside(soldier, feet, "stood inside %s at %s (no way out past the post): stepped out to %s", in);
        }
        return false;
    }

    private static boolean moveBeside(PathfinderMob soldier, BlockPos cell, String message, BlockState block) {
        Level level = soldier.level();
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos feet = cell.relative(direction).above(dy);
                double distance = soldier.position().distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                if (distance < bestDistance && canStandAt(level, feet)) {
                    bestDistance = distance;
                    best = feet;
                }
            }
            if (best != null) {
                soldier.getNavigation().stop();
                soldier.setPos(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
                DebugLog.event(DebugCategory.BUILD, soldier, message, block.getBlock().getName().getString(), cell, best);
                return true;
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
