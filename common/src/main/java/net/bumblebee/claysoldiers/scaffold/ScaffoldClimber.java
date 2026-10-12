package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.ChainedPath;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A builder's climbing behavior for scaffolding columns (approach e). Vanilla mobs never plan climbing, so builders
 * walk to a column's base on the ground (an ordinary path, which tiny soldiers follow reliably), then rise inside the
 * column to the height they need, hold there while placing, and come back down. Moving inside the column is done
 * directly (a smooth climb of {@code scaffold.climb_speed_hundredths} per tick, no gravity), so they can't fall off anything.
 * One per builder goal; {@link #reset} restores gravity.
 */
public final class ScaffoldClimber {
    /** The least time walking to a column's base may take before giving up, in ticks; longer paths get more (D76). */
    private static final int MIN_WALK_TICKS = 200;
    /** How many levels up a column a builder standing there (not climbing) counts as stranded. */
    private static final int STRANDED_LEVEL = 2;

    private enum State { IDLE, WALKING, CLIMBING, HOLDING, DESCENDING }

    private final PathfinderMob soldier;
    private State state = State.IDLE;
    @Nullable
    private BlockPos base;
    private int standY;
    private int walkTicks;
    private int walkLimit;
    /** Per game tick: whether a column base could be walked to (path searches are expensive). */
    private final Map<BlockPos, Boolean> baseReachable = new HashMap<>();
    private long cacheTick = -1;

    public ScaffoldClimber(PathfinderMob soldier) {
        this.soldier = soldier;
    }

    /**
     * The column cell (lowest first) a builder inside it could work on {@code target} from, if the job is climbed
     * columns and that column stands from its base up to the cell.
     */
    @Nullable
    static BlockPos standingCell(ServerLevel level, ScaffoldJob job, BlockPos target) {
        if (!job.columns()) {
            return null;
        }
        Block material = Scaffolds.materialOf(job);
        BlockPos best = null;
        for (BlockPos cell : job.placed()) {
            if (cell.equals(target) || best != null && cell.getY() >= best.getY()
                    || !BuilderReach.canPlaceFrom(new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5), target)) {
                continue;
            }
            List<BlockPos> column = job.column(cell);
            boolean standing = true;
            for (BlockPos below : column) {
                if (below.getY() <= cell.getY() && !level.getBlockState(below).is(material)) {
                    standing = false;
                }
            }
            if (standing && column.getFirst().getY() + column.size() > cell.getY()) {
                best = cell;
            }
        }
        return best;
    }

    /**
     * If this builder isn't climbing but stands in one of a build's scaffolding columns, {@link #STRANDED_LEVEL} or
     * more levels above its base, starts climbing it down. Builders get there when vanilla climbing (walking against
     * a wall while in scaffolding, which is climbable) carries them up a column; up there they can't find a path
     * anywhere and would stand still for good. Lower than that they walk out by themselves.
     *
     * @return true when it climbs down now (call {@link #tick} until {@link #isDescending} is false)
     */
    public boolean rescueIfStranded(ServerLevel level) {
        if (state != State.IDLE) {
            return false;
        }
        BlockPos at = soldier.blockPosition();
        if (!ScaffoldSavedData.get(level).isScaffold(at) && !ScaffoldSavedData.get(level).isScaffold(at.below())) {
            return false;
        }
        for (ScaffoldJob job : ScaffoldSavedData.get(level).jobs()) {
            BlockPos cell = job.placed().contains(at) ? at : at.below();
            if (!job.placed().contains(cell)) {
                continue;
            }
            BlockPos columnBase = job.column(cell).getFirst();
            if (!soldier.onGround() || soldier.getY() < columnBase.getY() + STRANDED_LEVEL) {
                return false;
            }
            DebugLog.event(DebugCategory.SCAFFOLD, soldier, "stranded at y=%s in the column at %s: climbing down", (int) soldier.getY(), columnBase);
            base = columnBase;
            soldier.getNavigation().stop();
            soldier.setNoGravity(true);
            state = State.DESCENDING;
            return true;
        }
        return false;
    }

    /**
     * Whether this builder could work on {@code target} by climbing a column of the easel's scaffold.
     */
    public boolean canReach(ServerLevel level, BlockPos easel, BlockPos target) {
        Optional<ScaffoldJob> job = ScaffoldSavedData.get(level).job(easel);
        if (job.isEmpty() || !job.get().columns()) {
            return false;
        }
        BlockPos cell = standingCell(level, job.get(), target);
        if (cell == null) {
            return false;
        }
        BlockPos columnBase = job.get().column(cell).getFirst();
        if (cacheTick != level.getGameTime()) {
            baseReachable.clear();
            cacheTick = level.getGameTime();
        }
        return baseReachable.computeIfAbsent(columnBase, pos -> pathToBase(pos) != null);
    }

    /**
     * Starts climbing to work on {@code target}: walks to the base of the column with the lowest cell in reach.
     *
     * @return false when no column helps
     */
    public boolean start(ServerLevel level, BlockPos easel, BlockPos target) {
        Optional<ScaffoldJob> job = ScaffoldSavedData.get(level).job(easel);
        BlockPos cell = job.map(j -> standingCell(level, j, target)).orElse(null);
        if (cell == null) {
            return false;
        }
        base = job.get().column(cell).getFirst();
        standY = cell.getY();
        state = State.WALKING;
        walkTicks = 0;
        Path path = pathToBase(base);
        // Tiny soldiers walk slowly: the keep's upper-floor columns are 50 steps from the chest (D76).
        walkLimit = Math.max(MIN_WALK_TICKS, path == null ? 0 : path.getNodeCount() * Tuning.SCAFFOLD_WALK_TICKS_PER_STEP.get());
        if (path != null) {
            soldier.getNavigation().moveTo(path, 1);
        }
        DebugLog.event(DebugCategory.SCAFFOLD, soldier, "climbing the column at %s to y=%s for %s (walk up to %s ticks)", base, standY, target, walkLimit);
        return true;
    }

    /**
     * Moves the climb on by one tick.
     *
     * @return true while holding at the working height (the builder can place now)
     */
    public boolean tick() {
        if (base == null) {
            return false;
        }
        double x = base.getX() + 0.5;
        double z = base.getZ() + 0.5;
        switch (state) {
            case WALKING -> {
                BlockPos at = soldier.blockPosition();
                if (at.getX() == base.getX() && at.getZ() == base.getZ() && Math.abs(at.getY() - base.getY()) <= 1) {
                    soldier.getNavigation().stop();
                    soldier.setNoGravity(true);
                    state = State.CLIMBING;
                } else if (++walkTicks > walkLimit) {
                    DebugLog.event(DebugCategory.SCAFFOLD, soldier, "stuck: couldn't walk to the column at %s in %s ticks", base, walkLimit);
                    reset();
                } else if (soldier.getNavigation().isDone() || walkTicks % Tuning.SCAFFOLD_REPATH_TICKS.get() == 0) {
                    if (soldier.position().distanceToSqr(x, base.getY(), z) < 2.5 * 2.5) {
                        soldier.getMoveControl().setWantedPosition(x, base.getY(), z, 1);
                    } else {
                        Path path = pathToBase(base);
                        if (path != null) {
                            soldier.getNavigation().moveTo(path, 1);
                        }
                    }
                }
            }
            case CLIMBING -> {
                double y = Math.min(soldier.getY() + speed(), standY);
                hold(x, y, z);
                if (y >= standY) {
                    state = State.HOLDING;
                }
            }
            case HOLDING -> hold(x, standY, z);
            case DESCENDING -> {
                double y = Math.max(soldier.getY() - speed(), base.getY());
                hold(x, y, z);
                if (y <= base.getY()) {
                    reset();
                }
            }
            case IDLE -> {
            }
        }
        return state == State.HOLDING;
    }

    /** Blocks per tick climbed or descended ({@code scaffold.climb_speed_hundredths}). */
    private static double speed() {
        return Tuning.SCAFFOLD_CLIMB_SPEED.get() / 100.0;
    }

    private void hold(double x, double y, double z) {
        soldier.setPos(x, y, z);
        soldier.setDeltaMovement(Vec3.ZERO);
        soldier.resetFallDistance();
    }

    /** After working up there: climbs back down (call {@link #tick} until {@link #isDescending} is false). */
    public void descend() {
        if (state == State.CLIMBING || state == State.HOLDING) {
            state = State.DESCENDING;
        } else if (state == State.WALKING) {
            reset();
        }
    }

    /** Stops at once and gives the builder its gravity back (inside scaffolding it slides down gently). */
    public void reset() {
        if (state != State.IDLE) {
            soldier.setNoGravity(false);
            soldier.resetFallDistance();
        }
        state = State.IDLE;
        base = null;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public boolean isDescending() {
        return state == State.DESCENDING;
    }

    @Nullable
    private Path pathToBase(BlockPos columnBase) {
        return pathToBase(soldier, columnBase);
    }

    /**
     * A path for {@code soldier} to a column's base, or null: the navigation's own limits, and a search that runs out of
     * steps goes on from where it ended ({@link ChainedPath}, D76).
     */
    @Nullable
    public static Path pathToBase(PathfinderMob soldier, BlockPos columnBase) {
        PathNavigation navigation = soldier.getNavigation();
        float bonus = 0;
        float multiplier = 1;
        if (navigation instanceof DoorPathing.Ground ground) {
            bonus = ground.getRangeBonus();
            multiplier = ground.getNodesMultiplier();
        }
        float range = (float) soldier.getAttributeValue(Attributes.FOLLOW_RANGE) + bonus;
        int nodes = Mth.floor(soldier.getAttributeBaseValue(Attributes.FOLLOW_RANGE) * 16.0 * multiplier);
        for (int accuracy = 0; accuracy <= 1; accuracy++) {
            Path path = ChainedPath.find(soldier, Set.of(columnBase), range, nodes, accuracy);
            if (path != null && path.canReach()) {
                return path;
            }
        }
        return null;
    }
}
