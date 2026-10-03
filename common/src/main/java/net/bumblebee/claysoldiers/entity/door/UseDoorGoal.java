package net.bumblebee.claysoldiers.entity.door;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a clay soldier open a closed wooden door on its path, walk through, and close it behind itself.
 * Has no flags, so it runs next to whatever goal is moving the soldier (work, combat, wandering).
 * <p>
 * Not vanilla's {@code OpenDoorGoal} (see {@code docs/DOORS.md}): that one only runs for mobs on ground navigation
 * (swimming soldiers use another one), only after bumping into the door, and closes it after 20 ticks no matter
 * what. A soldier is so small that it counts the door block as "reached" (within 0.675 blocks of its center) before
 * it touches the panel, so when the door is the last block of its path it never bumps into it.
 * This goal opens a door as soon as it is close ahead on the path, and closes it once the soldier has gone through
 * (or turned back) and the doorway is clear. Only doors soldiers opened get closed (see {@link OpenedDoors}).
 * It never lets a soldier freeze at a door: a refused door ({@link DoorAccess}) or a door the soldier doesn't get
 * through within {@link #STUCK_TICKS} makes it stop walking (its goals then plan again). Every step is recorded in
 * the PATHING category.
 */
public class UseDoorGoal extends Goal {
    /** A door on the path is opened when its block's center is this close (sideways). */
    public static final double OPEN_DISTANCE = 1.5;
    /** The soldier stops using a door when it is this far away without having gone through (its plans changed). */
    private static final double LEAVE_DISTANCE = 3.0;
    /** After going through, the soldier waits at most this long for the doorway to be clear, then leaves it open. */
    public static final int CLOSE_WAIT_TICKS = 60;
    /** A soldier that hasn't got through a door this long after reaching it gives up (5 seconds). */
    public static final int STUCK_TICKS = 100;
    /** How many path nodes past the next one are checked for a door. */
    private static final int LOOK_AHEAD = 2;

    private final AbstractClaySoldierEntity soldier;
    @Nullable
    private BlockPos door;
    /** The path and door last handled, so a finished path that ends at a door doesn't open it again. */
    @Nullable
    private Path handledPath;
    @Nullable
    private BlockPos handledDoor;
    private boolean joined;
    /** Which side of the door the soldier came from: -1 or +1 along the door's walking axis. */
    private int startSide;
    private boolean entered;
    private boolean passed;
    private boolean done;
    private int ticks;
    private int closeWait;

    public UseDoorGoal(AbstractClaySoldierEntity soldier) {
        this.soldier = soldier;
    }

    /**
     * Lets the navigation plan paths through wooden doors (and open ones). Every navigation a soldier uses must get
     * this, otherwise it plans around closed doors.
     */
    public static <T extends PathNavigation> T allowDoors(T navigation) {
        navigation.getNodeEvaluator().setCanOpenDoors(true);
        navigation.getNodeEvaluator().setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean canUse() {
        if (soldier.isPassenger() || !soldier.getNavigation().getNodeEvaluator().canOpenDoors()) {
            return false;
        }
        Path path = soldier.getNavigation().getPath();
        if (path == null) {
            return false;
        }
        BlockPos found = doorAhead(path);
        if (found == null || (path == handledPath && found.equals(handledDoor))) {
            return false;
        }
        door = found;
        return true;
    }

    /**
     * The first door close ahead on the path that this goal should handle: a closed wooden door, or an open one that
     * soldiers opened (to go through it together and let the last one close it). For a finished path, only its last
     * block counts (the soldier may have stopped just before it).
     */
    @Nullable
    private BlockPos doorAhead(Path path) {
        int last = path.getNodeCount() - 1;
        int from = path.isDone() ? last : Math.max(0, path.getNextNodeIndex() - 1);
        int to = path.isDone() ? last : Math.min(last, path.getNextNodeIndex() + LOOK_AHEAD);
        Level level = soldier.level();
        for (int i = from; i <= to; i++) {
            BlockPos node = path.getNode(i).asBlockPos();
            BlockPos lower = doorAt(level, node);
            if (lower == null) {
                lower = doorAt(level, node.above());
            }
            if (lower != null && isClose(lower, OPEN_DISTANCE)) {
                BlockState state = level.getBlockState(lower);
                if (DoorBlock.isWoodenDoor(state) && (!state.getValue(DoorBlock.OPEN) || OpenedDoors.openedBySoldier(level, lower))) {
                    return lower;
                }
            }
        }
        return null;
    }

    /**
     * Returns the lower half of the door at {@code pos}, or {@code null} when there is no door.
     */
    @Nullable
    private static BlockPos doorAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return null;
        }
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public void start() {
        entered = false;
        passed = false;
        done = false;
        joined = false;
        ticks = 0;
        closeWait = 0;
        handledPath = soldier.getNavigation().getPath();
        handledDoor = door;
        int side = side();
        startSide = side != 0 ? side : (centerAlongAxis() < 0.5 ? -1 : 1);
        Level level = soldier.level();
        if (!isOpen()) {
            if (!DoorAccess.canUse(soldier, door)) {
                refuse();
                return;
            }
            setOpen(true);
            OpenedDoors.opened(level, door);
            DebugLog.event(DebugCategory.PATHING, soldier, "opened door at %s", door);
        }
        OpenedDoors.join(level, door, soldier);
        joined = true;
    }

    /**
     * The soldier may not open the door (it was allowed when the path was planned): stop walking, so its goals plan
     * again; the pathfinder now treats the door as a wall.
     */
    private void refuse() {
        DebugLog.event(DebugCategory.PATHING, soldier, "refused door at %s: %s", door, DoorAccess.whyNot(soldier, door));
        soldier.getNavigation().stop();
        done = true;
    }

    @Override
    public boolean canContinueToUse() {
        return !done;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (done) {
            return;
        }
        if (!(soldier.level().getBlockState(door).getBlock() instanceof DoorBlock)) {
            done = true; // the door is gone
            return;
        }
        if (!passed) {
            int side = side();
            if (side == 0) {
                entered = true;
            }
            if (side == -startSide || (entered && side == startSide) || !isClose(door, LEAVE_DISTANCE)) {
                passed = true; // went through, turned back, or went elsewhere
            } else if (++ticks > STUCK_TICKS) {
                DebugLog.event(DebugCategory.PATHING, soldier, "stuck at door at %s for %s ticks, giving up", door, ticks);
                soldier.getNavigation().stop();
                passed = true;
            } else if (!isOpen()) {
                // Someone closed it before we got through.
                if (!DoorAccess.canUse(soldier, door)) {
                    refuse();
                    return;
                }
                setOpen(true);
                OpenedDoors.opened(soldier.level(), door);
                DebugLog.event(DebugCategory.PATHING, soldier, "opened door at %s", door);
            }
        }
        if (passed) {
            tryClose();
        }
    }

    /**
     * Which side of the door the soldier is on along the door's walking axis: -1 or +1 when it is completely out of the
     * door's block, 0 while any part of it is inside.
     */
    private int side() {
        Direction.Axis axis = walkingAxis();
        AABB box = soldier.getBoundingBox();
        int doorMin = door.get(axis);
        if (box.max(axis) <= doorMin) {
            return -1;
        }
        return box.min(axis) >= doorMin + 1 ? 1 : 0;
    }

    private double centerAlongAxis() {
        Direction.Axis axis = walkingAxis();
        return (axis == Direction.Axis.X ? soldier.getX() : soldier.getZ()) - door.get(axis);
    }

    /**
     * The axis you walk along to go through the door (a door facing north is walked through north-south).
     */
    private Direction.Axis walkingAxis() {
        BlockState state = soldier.level().getBlockState(door);
        return state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING).getAxis() : Direction.Axis.Z;
    }

    /**
     * Closes the door once nobody is in the doorway, unless other soldiers are still going through it (the last one
     * closes it) or it was already closed. Gives up after {@link #CLOSE_WAIT_TICKS}, leaving the door open.
     */
    private void tryClose() {
        Level level = soldier.level();
        if (!isOpen() || OpenedDoors.otherUsers(level, door, soldier) > 0) {
            done = true;
        } else if (level.getEntitiesOfClass(LivingEntity.class, doorway()).isEmpty()) {
            setOpen(false);
            OpenedDoors.closed(level, door);
            DebugLog.event(DebugCategory.PATHING, soldier, "closed door at %s behind it", door);
            done = true;
        } else if (++closeWait > CLOSE_WAIT_TICKS) {
            DebugLog.event(DebugCategory.PATHING, soldier, "left door at %s open: doorway not clear after %s ticks", door, CLOSE_WAIT_TICKS);
            done = true;
        }
    }

    @Override
    public void stop() {
        if (joined) {
            OpenedDoors.leave(soldier.level(), door, soldier);
        }
        joined = false;
        door = null;
    }

    private AABB doorway() {
        return new AABB(door).expandTowards(0, 1, 0);
    }

    private boolean isClose(BlockPos pos, double distance) {
        double dx = pos.getX() + 0.5 - soldier.getX();
        double dz = pos.getZ() + 0.5 - soldier.getZ();
        return dx * dx + dz * dz <= distance * distance && Math.abs(pos.getY() - soldier.getY()) < 1.5;
    }

    private boolean isOpen() {
        BlockState state = soldier.level().getBlockState(door);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN);
    }

    private void setOpen(boolean open) {
        BlockState state = soldier.level().getBlockState(door);
        if (state.getBlock() instanceof DoorBlock doorBlock) {
            doorBlock.setOpen(soldier, soldier.level(), state, door, open);
        }
    }
}
