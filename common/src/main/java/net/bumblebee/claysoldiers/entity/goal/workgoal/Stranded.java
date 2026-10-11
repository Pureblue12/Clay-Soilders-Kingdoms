package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * A builder left on a roof or an upper floor it built, whose only way down is a drop it may not take ({@link EdgeGuard},
 * the pathfinder's 3-block limit), finds no path anywhere: keep L2 and town hall L2 builders stood on their upper
 * floors with one-node paths for 30,000 ticks (structure build run 38149380878). After {@code build.stranded_ticks}
 * without a way to its target it is put on a free spot beside its chest, like a player carrying it down (D72).
 */
final class Stranded {
    private Stranded() {
    }

    /**
     * Whether the soldier's current route can't get it to its target: no path, one that ends short, or one that starts
     * with a drop the edge guard won't let it take ({@link EdgeGuard#heldRecently}).
     */
    static boolean noWay(AbstractClaySoldierEntity soldier) {
        Path path = soldier.getNavigation().getPath();
        return path == null || !path.canReach() || EdgeGuard.heldRecently(soldier);
    }

    /**
     * Puts {@code soldier} on the free standing spot nearest to {@code storage} (within 2 blocks, the storage's level
     * first) and records it (BUILD).
     *
     * @return whether it was moved
     */
    static boolean bringBack(PathfinderMob soldier, @Nullable BlockPos storage, int ticks) {
        if (storage == null) {
            return false;
        }
        Level level = soldier.level();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy : new int[]{0, 1, -1}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos feet = storage.offset(dx, dy, dz);
                    double distance = storage.distSqr(feet);
                    if ((dx != 0 || dz != 0) && distance < bestDistance && canStandAt(level, feet)) {
                        bestDistance = distance;
                        best = feet;
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        if (best == null) {
            return false;
        }
        BlockPos from = soldier.blockPosition();
        soldier.getNavigation().stop();
        soldier.setPos(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        soldier.resetFallDistance();
        DebugLog.event(DebugCategory.BUILD, soldier, "stranded at %s for %s ticks with no way to its target: brought back beside its storage, to %s",
                from, ticks, best);
        return true;
    }

    private static boolean canStandAt(Level level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP);
    }
}
