package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.scaffold.Scaffolds;
import net.bumblebee.claysoldiers.scaffold.ScaffoldClimber;
import net.bumblebee.claysoldiers.scaffold.ScaffoldHome;
import net.bumblebee.claysoldiers.scaffold.ScaffoldRequest;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.capability.BlueprintRequestHandler;
import net.bumblebee.claysoldiers.capability.IBlockCache;
import net.bumblebee.claysoldiers.blueprint.PlannedReach;
import net.bumblebee.claysoldiers.blueprint.ReachOrigin;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.door.UseDoorGoal;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BuildBlueprintGoal extends AbstractWorkGoal {
    public static final String BUILDING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "building");

    @Nullable
    private IBlockCache<BlueprintRequestHandler> easelPos = null;
    private final int searchRange;
    private final int verticalSearchRange;
    protected int verticalSearchStart;
    /**
     * A builder that has had a request this long (fetching from storage + carrying) without placing it gives up:
     * it returns the block and releases the request, so the block can be handed out again (20 seconds).
     * Must stay shorter than {@code BlueprintBlockInfo.STALE_REQUEST_TICKS}.
     */
    public static final int MAX_CARRY_TICKS = 400;
    @Nullable
    private BlueprintRequest request = null;
    private boolean bringBack = false;
    private int carryTicks = 0;
    @Nullable
    private Map<String, Integer> unreachableReasons;
    /** While BUILD is recorded: closed doors found in the way during one search, and the blocks behind each. */
    @Nullable
    private Map<BlockPos, List<BlockPos>> blockedByDoor;
    /**
     * When the easel has no reachable block, the builder waits this many ticks plus up to
     * {@link #RETRY_COOLDOWN_RANDOM} more before asking again, instead of asking (and pathfinding) every tick.
     */
    public static final int RETRY_COOLDOWN_MIN = 20;
    public static final int RETRY_COOLDOWN_RANDOM = 20;
    private int retryCooldown = 0;
    /**
     * When a soldier is in the way of placing (standing in the spot, beside a fence the block would connect to, or it
     * would be walled in; D64), the builder keeps the block and tries again after this many ticks instead of carrying
     * it back to storage and asking again (a round trip of 50 or more ticks per fence of a farm). The give-up timer
     * ({@link #MAX_CARRY_TICKS}) keeps running, so a soldier that never moves still ends the request.
     */
    public static final int SOLDIER_IN_THE_WAY_RETRY_TICKS = 20;
    private int placeWait = 0;
    /**
     * The reachability test handed to the easel; it also tells scaffold planning where this builder comes from (its
     * storage), since walking distances are measured from there.
     */
    private final ReachTest reachTest = new ReachTest();

    private final class ReachTest implements java.util.function.Predicate<BlockPos>, ScaffoldHome, ReachOrigin, PlannedReach {
        @Override
        public boolean test(BlockPos pos) {
            return canReach(pos, List.of());
        }

        @Override
        public boolean canReach(BlockPos target, List<BlockPos> spots) {
            return BuildBlueprintGoal.this.canReach(target, spots);
        }

        @Override
        public BlockPos reachOrigin() {
            return soldier.blockPosition();
        }

        @Override
        public BlockPos scaffoldHome() {
            return getPoiPos();
        }
    }

    /** Climbs scaffolding columns (stage 3, approach e); idle otherwise. */
    private final ScaffoldClimber climber;
    /**
     * A builder walking somewhere (the easel, its storage) logs nothing while it doesn't arrive, so a trapped builder
     * looked like a quiet one: every this many ticks of not arriving, a BUILD event says where it is and what its route
     * looks like.
     */
    static final int STUCK_LOG_TICKS = 200;
    /** Ticks in a row this builder has been walking somewhere without arriving. */
    private int stuckTicks;
    /** Request searches taking this long or longer are recorded (BUILD). */
    private static final int SLOW_SEARCH_MILLIS = 20;
    /** Ticks since the route to the request was last planned (builds with scaffolding only). */
    private int repathTicks;
    /** The path search multiplier this goal last set (1 = normal). */
    private float pathSearchMultiplier = 1.0F;
    /** Whether the easel's build has scaffolding (its builders plan their route again every {@code scaffold.repath_ticks}). */
    private boolean scaffoldBuild;
    /**
     * Tiny soldiers count a path as walked when they are within 0.675 blocks of its last block's center, which is often
     * still the block before it; placing is measured from the block they stand in. So after the path, the builder
     * takes the last step into the path's end block directly, for at most this many ticks.
     */
    private static final int MAX_LAST_STEP_TICKS = 20;
    @Nullable
    private BlockPos pathEnd;
    private int lastStepTicks;
    /**
     * Where this builder just placed a block while it still carries more of that item ({@code build.carry_count}, D79):
     * it asks the easel for the nearest free block of the item in that layer before walking back to the easel and its chest.
     */
    @Nullable
    private BlockPos followUpFrom;
    /** Ticks waited for this builder to land before the follow-up search (vanilla finds no path while a mob is in the air). */
    private int followUpWait;
    /**
     * Whether this builder just climbed down a column for its follow-up search: it then only takes a block reachable from
     * the ground. Walking from one column's base to another's hugs the building and passes other columns, which tiny
     * soldiers climb into by accident (the 20-high tower test: a builder ended 12 up beside a column and gave up).
     */
    private boolean cameDown;
    /** Ticks a follow-up search waits at most for this builder to land. */
    private static final int MAX_FOLLOW_UP_WAIT_TICKS = 10;

    public BuildBlueprintGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector, int searchRange) {
        super(soldier, workSelector);
        this.climber = new ScaffoldClimber(soldier);
        this.searchRange = searchRange;
        this.verticalSearchRange = 2;
    }

    @Override
    public boolean canUse() {
        if (isOnBreak()) {
            return false;
        }
        return getPoiPos() != null && getCapCacheResetIfInvalid() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse();
    }


    @Override
    public void tick() {
        updatePathSearch();
        FallTrace.record(soldier, decodeStatus(getStatus()).getString());
        if (soldier.level() instanceof ServerLevel level && climber.rescueIfStranded(level)) {
            return; // stood in a scaffolding column without climbing it: come down first
        }
        if (!climber.isActive() && OffTheFence.stepDown(soldier)) {
            return; // stood on a fence top, where no path starts
        }
        if (climber.isDescending()) {
            climber.tick(); // come down a scaffolding column first
            return;
        }
        if (easelPos == null) {
            setStatus(REQUIRES_POI_ID);
            easelPos = findNearestBlock().map(this::getBlueprintCache).orElse(null);
            return;
        }

        if (request == null) {
            if (followUpFrom != null) {
                setStatus(SEARCHING_ID);
                findFollowUp();
            } else if (bringBack) {
                setStatus(RETURNING_ID);
                if (returnCarried()) {
                    bringBack = false;
                    stuckTicks = 0;
                } else {
                    notArrived("returning to storage", getPoiPos());
                }
            } else if (retryCooldown > 0) {
                retryCooldown--;
                setStatus(SEARCHING_ID);
            } else {
                getRequestJob();
                setStatus(SEARCHING_ID);
            }
        } else if (request.isCancelled() || request.isFinished()) {
            if (request.isCancelled()) DebugLog.event(DebugCategory.BUILD, soldier, "request released (cancelled): %s at %s", request.getItem(), request.getPos());
            bringBack = !soldier.getCarriedStack().isEmpty();
            climber.descend();
            request = null;
        } else if (request.hasStarted()) {
            // The give-up timer also runs while fetching the item from storage.
            if (++carryTicks > MAX_CARRY_TICKS) {
                giveUpRequest();
            } else {
                getRequestFromStorage();
            }
        } else if (request.isPlacing()) {
            setStatus(CARRYING_ID);
            if (carryTicks == MAX_CARRY_TICKS / 2) DebugLog.event(DebugCategory.BUILD, soldier, "still working on %s at %s after %s ticks", request.getItem(), request.getPos(), carryTicks);
            if (++carryTicks > MAX_CARRY_TICKS) {
                giveUpRequest();
            } else if (placeWait > 0) {
                placeWait--;
            } else if (moveToRequest()) {
                placeBlock();
            }
        }
    }

    /**
     * While this builder works for a build, its paths may be longer and its search bigger than vanilla's (D61, D62):
     * vanilla only accepts paths whose walked length is under the follow range (16; each step up counts about 1.4),
     * and the way around a big building, to a standing spot, back to the easel or to the chest, is longer than that.
     * Every path gets {@code build.path_range_bonus} more blocks and {@code build.path_nodes_multiplier} times more
     * steps; while the easel has scaffolding, the larger of these and the {@code scaffold.path_*} values (routes up a
     * scaffold wind around). A builder without an easel keeps vanilla's limits.
     */
    private void updatePathSearch() {
        boolean building = easelPos != null;
        scaffoldBuild = building && soldier.level() instanceof ServerLevel level && Scaffolds.hasJob(level, easelPos.pos());
        float multiplier = !building ? 1.0F : scaffoldBuild
                ? Math.max(Tuning.BUILD_PATH_NODES_MULTIPLIER.get(), Tuning.SCAFFOLD_PATH_NODES_MULTIPLIER.get()) : Tuning.BUILD_PATH_NODES_MULTIPLIER.get();
        if (multiplier != pathSearchMultiplier) {
            pathSearchMultiplier = multiplier;
            soldier.getNavigation().setMaxVisitedNodesMultiplier(multiplier);
        }
        int bonus = !building ? 0 : scaffoldBuild
                ? Math.max(Tuning.BUILD_PATH_RANGE_BONUS.get(), Tuning.SCAFFOLD_PATH_RANGE_BONUS.get()) : Tuning.BUILD_PATH_RANGE_BONUS.get();
        if (soldier.getNavigation() instanceof DoorPathing.Ground ground && ground.getRangeBonus() != bonus) {
            ground.setRangeBonus(bonus);
        }
    }

    private void getRequestFromStorage() {
        if (!moveToPoi()) {
            notArrived("fetching from storage", getPoiPos());
            return;
        }
        stuckTicks = 0;
        assert getCapCache() != null;
        var storage = getCapCache().getCapability();
        if (storage != null) {
            if (!soldier.getCarriedStack().isEmpty()) {
                var returned = storage.tryInserting(soldier.getCarriedStack());
                soldier.dropItemStack(returned);
            }
            // A stack of a block's item at once (D79): after placing one, the next block of the item nearby comes without a trip back.
            int amount = request instanceof ScaffoldRequest ? 1 : Tuning.BUILD_CARRY_COUNT.get();
            soldier.setCarriedStack(storage.tryExtracting(stack -> stack.is(request.getItem()), amount));
            if (soldier.getCarriedStack().isEmpty()) {
                // The storage does not have the item: don't place anything, ask again after a break.
                DebugLog.event(DebugCategory.BUILD, soldier, "storage has no %s for %s, request cancelled", request.getItem(), request.getPos());
                request.cancel();
                takeAShortBreak();
            } else {
                request.setPlacing();
            }
        } else {
            easelPos = null;
            request = null;
        }
    }

    /**
     * Cancels the current request (so the block can be handed out again) and brings the carried item back.
     */
    private void giveUpRequest() {
        climber.descend();
        DebugLog.event(DebugCategory.BUILD, soldier, "gave up %s at %s after %s ticks (fetching + carrying), releasing it", request.getItem(), request.getPos(), carryTicks);
        request.giveUp();
        request = null;
        bringBack = !soldier.getCarriedStack().isEmpty();
        carryTicks = 0;
        placeWait = 0;
    }

    private boolean returnCarried() {
        if (moveToPoi()) {
            assert getCapCache() != null;
            var storage = getCapCache().getCapability();
            if (storage != null) {
                soldier.spawnAtLocation(storage.tryInserting(soldier.getCarriedStack()));
                soldier.setCarriedStack(ItemStack.EMPTY);
            } else {
                soldier.dropCarried();
            }
            return true;
        }
        return false;
    }

    private void getRequestJob() {
        if (!moveMobToEasel()) {
            notArrived("walking to the easel", easelPos == null ? null : easelPos.pos());
            return;
        }
        if (!soldier.onGround()) {
            // Vanilla finds no path at all while a mob is in the air (mid-hop): a search now would find every block
            // out of reach, and remember it so (build.unreachable_recheck_ticks). Search once it has landed.
            notArrived("in the air at the easel", easelPos.pos());
            return;
        }
        stuckTicks = 0;
        var requestHandler = easelPos.getCapability();
        if (requestHandler != null) {
            // While BUILD is recorded, collect why blocks are unreachable (one summary line per search).
            unreachableReasons = DebugLog.isOn(DebugCategory.BUILD) ? new LinkedHashMap<>() : null;
            blockedByDoor = unreachableReasons == null ? null : new LinkedHashMap<>();
            long start = System.nanoTime();
            request = requestHandler.getRequest(reachTest);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (millis >= SLOW_SEARCH_MILLIS) DebugLog.event(DebugCategory.BUILD, soldier, "slow request search: %s ms", millis);
            pathEnd = null;
            logUnreachableReasons();
            if (request != null) DebugLog.event(DebugCategory.BUILD, soldier, "got request: %s at %s", request.getItem(), request.getPos());
            carryTicks = 0;
            if (request == null) {
                DebugLog.event(DebugCategory.BUILD, soldier, "no reachable block available, waiting");
                retryCooldown = RETRY_COOLDOWN_MIN + soldier.getRandom().nextInt(RETRY_COOLDOWN_RANDOM + 1);
            }
        } else {
            easelPos = null;
        }
    }

    /**
     * Called every tick this builder walks somewhere without arriving: every {@link #STUCK_LOG_TICKS} ticks in a row it
     * records where it is and what its route looks like (BUILD), so a trapped or floating builder shows in the log.
     */
    private void notArrived(String doing, @Nullable BlockPos target) {
        ++stuckTicks;
        int strandedTicks = Tuning.BUILD_STRANDED_TICKS.get();
        if (strandedTicks > 0 && stuckTicks >= strandedTicks && Stranded.noWay(soldier) && Stranded.bringBack(soldier, getPoiPos(), stuckTicks)) {
            stuckTicks = 0;
            return;
        }
        if (stuckTicks % STUCK_LOG_TICKS != 0) {
            return;
        }
        int ticks = stuckTicks;
        DebugLog.event(DebugCategory.BUILD, soldier, () -> {
            Path path = soldier.getNavigation().getPath();
            String route = path == null ? "no path" : "path of " + path.getNodeCount() + " nodes ending at "
                    + (path.getEndNode() == null ? "-" : path.getEndNode().asBlockPos().toShortString()) + (path.canReach() ? "" : " (incomplete)")
                    + (soldier.getNavigation().isDone() ? ", done" : "");
            return "stuck %s for %d ticks: at %s, target %s, onGround %s, %s".formatted(doing, ticks, soldier.blockPosition().toShortString(),
                    target == null ? "-" : target.toShortString(), soldier.onGround(), route);
        });
    }

    private boolean canReach(BlockPos pos, List<BlockPos> plannedSpots) {
        if (unreachableReasons == null) {
            return BuilderReach.canReachToPlace(soldier, pos, plannedSpots) || canClimbTo(pos);
        }
        if (canClimbTo(pos) && !BuilderReach.canReachToPlace(soldier, pos, plannedSpots)) {
            return true;
        }
        BuilderReach.Reach reach = BuilderReach.check(soldier, pos, true, plannedSpots);
        if (!reach.reachable()) {
            unreachableReasons.merge(reach.whyNot(), 1, Integer::sum);
            if (reach.closedDoor() != null && blockedByDoor != null) {
                blockedByDoor.computeIfAbsent(reach.closedDoor(), door -> new ArrayList<>()).add(pos);
            }
        }
        return reach.reachable();
    }

    /** Whether this builder could reach {@code pos} by climbing a scaffolding column of its easel (approach e). */
    private boolean canClimbTo(BlockPos pos) {
        return easelPos != null && soldier.level() instanceof ServerLevel level && climber.canReach(level, easelPos.pos(), pos);
    }

    private void logUnreachableReasons() {
        if (unreachableReasons != null && !unreachableReasons.isEmpty()) {
            int total = unreachableReasons.values().stream().mapToInt(Integer::intValue).sum();
            String reasons = unreachableReasons.entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey()).collect(Collectors.joining(", "));
            DebugLog.event(DebugCategory.BUILD, soldier, "skipped %s unreachable blocks: %s", total, reasons);
        }
        if (blockedByDoor != null) {
            blockedByDoor.forEach((door, blocked) -> DebugLog.event(DebugCategory.BUILD, soldier,
                    "closed door at %s blocks the way to %s blueprint block(s), first at %s", door, blocked.size(), blocked.get(0)));
        }
        unreachableReasons = null;
        blockedByDoor = null;
    }


    private void placeBlock() {
        boolean holding = climber.isHolding();
        // Up a scaffolding column with more blocks in hand, the builder stays up while the next block is placeable from there (D79).
        boolean stayUp = false;
        var requestHandler = easelPos.getCapability();
        if (requestHandler != null) {
            if (requestHandler.doRequest(request)) {
                // The easel only fills the request's own spot, so this is the actual position.
                DebugLog.event(DebugCategory.BUILD, soldier, "placed %s at %s", request.getItem(), request.getPos());
                ItemStack left = soldier.getCarriedStack().copy();
                left.shrink(1);
                soldier.setCarriedStack(left.isEmpty() || !left.is(request.getItem()) ? ItemStack.EMPTY : left);
                if (!soldier.getCarriedStack().isEmpty() && !(request instanceof ScaffoldRequest)) {
                    followUpFrom = request.getPos();
                    followUpWait = 0;
                    stayUp = holding;
                }
                if (request instanceof ScaffoldRequest scaffold && scaffold.isRemoval() && scaffold.refund() && scaffold.removedCount() > 0) {
                    // The scaffold taken down goes back to storage (a whole column for climbed columns).
                    soldier.setCarriedStack(new ItemStack(scaffold.getItem(), scaffold.removedCount()));
                    bringBack = true;
                }
                request.setFinished();
                // A short, random breather before asking for the next block, so builders don't move in lockstep (D82;
                // builds that follow a plan only: the others keep their timing).
                int pause = Tuning.BUILD_PAUSE_TICKS.get();
                retryCooldown = pause > 0 && !request.standingSpots().isEmpty() ? soldier.getRandom().nextInt(pause + 1) : 0;
            } else if (request.takeSoldierInTheWay()) {
                DebugLog.event(DebugCategory.BUILD, soldier, "soldier in the way of %s at %s, trying again in %s ticks", request.getItem(), request.getPos(), SOLDIER_IN_THE_WAY_RETRY_TICKS);
                placeWait = SOLDIER_IN_THE_WAY_RETRY_TICKS;
                if (!holding) {
                    stepAside(request.getPos());
                }
                return;
            } else {
                DebugLog.event(DebugCategory.BUILD, soldier, "could not place %s at %s (spot not free?), returning it", request.getItem(), request.getPos());
                bringBack = true;
                request.cancel();
            }
            request = null;
        } else {
            easelPos = null;
        }
        if (!stayUp) {
            climber.descend();
        }
    }

    /**
     * The builder itself may be the soldier in the way: standing next to the spot, the block would close its own way
     * out (a slab two above its feet takes away the hop, D64), and waiting where it stands changes nothing (hovel L3 on
     * CI: 17 refusals in a row while the other builders had nothing to do). It steps to a free cell next to it that is
     * two blocks from the spot, where the block can't pen it in and it can still place it. None: it stays.
     */
    private void stepAside(BlockPos target) {
        BlockPos feet = soldier.blockPosition();
        if (Math.abs(feet.getX() - target.getX()) > 1 || Math.abs(feet.getZ() - target.getZ()) > 1) {
            return; // not next to the spot: someone else is in the way
        }
        BlockPos best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = feet.offset(dx, dy, dz);
                    if (Math.max(Math.abs(cell.getX() - target.getX()), Math.abs(cell.getZ() - target.getZ())) != 2
                            || !BuilderReach.canStandAt(soldier.level(), cell)
                            || !soldier.level().getBlockState(cell.above()).getCollisionShape(soldier.level(), cell.above()).isEmpty()
                            || !BuilderReach.canPlaceFrom(new net.minecraft.world.phys.Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5), target)) {
                        continue;
                    }
                    if (best == null || cell.distSqr(feet) < best.distSqr(feet)) {
                        best = cell;
                    }
                }
            }
        }
        if (best != null) {
            DebugLog.event(DebugCategory.BUILD, soldier, "stepping aside to %s to place at %s", best, target);
            soldier.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 1.0);
        }
    }

    /**
     * Asks the easel for this builder's next block of the item it still carries, near the one it just placed (D79). Found:
     * it carries it there at once. None: it brings the rest back to storage and asks the easel as usual.
     */
    private void findFollowUp() {
        if (climber.isHolding()) {
            // Up a column: only a block placeable from right here; otherwise climb down and look again from the ground.
            var handler = easelPos == null ? null : easelPos.getCapability();
            ItemStack carried = soldier.getCarriedStack();
            request = handler == null || carried.isEmpty() ? null
                    : handler.getFollowUpRequest(pos -> BuilderReach.canPlaceFrom(soldier, pos) || climber.holdsInReachOf(pos), carried.getItem(), followUpFrom);
            if (request == null) {
                climber.descend();
                cameDown = true;
                return; // followUpFrom stays: the search runs again once down
            }
            followUpFrom = null;
            request.setPlacing();
            carryTicks = 0;
            placeWait = 0;
            DebugLog.event(DebugCategory.BUILD, soldier, "next %s at %s from the column (carrying %s)", request.getItem(), request.getPos(), carried.getCount());
            return;
        }
        if (!soldier.onGround() && followUpWait++ < MAX_FOLLOW_UP_WAIT_TICKS) {
            return; // mid-hop: a path search now would find nothing
        }
        BlockPos placed = followUpFrom;
        followUpFrom = null;
        followUpWait = 0;
        boolean groundOnly = cameDown;
        cameDown = false;
        ItemStack carried = soldier.getCarriedStack();
        var requestHandler = easelPos == null ? null : easelPos.getCapability();
        if (requestHandler != null && !carried.isEmpty()) {
            request = requestHandler.getFollowUpRequest(groundOnly ? pos -> BuilderReach.canReachToPlace(soldier, pos) : reachTest,
                    carried.getItem(), placed);
        }
        if (request != null) {
            request.setPlacing();
            carryTicks = 0;
            placeWait = 0;
            pathEnd = null;
            repathTicks = 0;
            DebugLog.event(DebugCategory.BUILD, soldier, "next %s at %s (carrying %s)", request.getItem(), request.getPos(), carried.getCount());
        } else {
            bringBack = !carried.isEmpty();
            if (bringBack) DebugLog.event(DebugCategory.BUILD, soldier, "no more %s to place nearby, returning %s to storage", carried.getItem(), carried.getCount());
        }
    }

    protected Optional<BlockPos> findNearestBlock() {
        BlockPos center = this.soldier.blockPosition();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int searchIndex = this.verticalSearchStart; searchIndex <= this.verticalSearchRange; searchIndex = searchIndex > 0 ? -searchIndex : 1 - searchIndex) {
            for (int rangeIndex = 0; rangeIndex < this.searchRange; rangeIndex++) {
                for (int i1 = 0; i1 <= rangeIndex; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < rangeIndex && i1 > -rangeIndex ? rangeIndex : 0; j1 <= rangeIndex; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        blockpos$mutableblockpos.setWithOffset(center, i1, searchIndex - 1, j1);
                        if (this.soldier.isWithinRestriction(blockpos$mutableblockpos) && this.isValidTarget(this.soldier.level(), blockpos$mutableblockpos)) {
                            return Optional.of(blockpos$mutableblockpos);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    protected boolean moveMobToEasel() {
        if (easelPos == null) {
            return false;
        }
        // Don't wait for work in a doorway (an easel can be 2 blocks from a door): keep walking towards the easel.
        if (easelPos.pos().closerToCenterThan(soldier.position(), 2f) && !UseDoorGoal.isInDoorway(soldier)) {
            this.soldier.getNavigation().stop();
            return true;
        }
        this.soldier.getNavigation().moveTo(this.easelPos.pos().getX() + 0.5, (this.easelPos.pos().getY() + 1), this.easelPos.pos().getZ() + 0.5, 1);
        return false;
    }

    protected boolean moveToRequest() {
        if (BuilderReach.canPlaceFrom(soldier, request.getPos()) || climber.holdsInReachOf(request.getPos())) {
            this.soldier.getNavigation().stop();
            pathEnd = null;
            return true;
        }
        if (climber.isActive()) {
            climber.tick(); // walking to a scaffolding column, or climbing it
            return false;
        }
        // With scaffolding, plan the route again every scaffold.repath_ticks: tiny soldiers climb floating steps better with fresh routes.
        boolean repath = scaffoldBuild && ++repathTicks >= Tuning.SCAFFOLD_REPATH_TICKS.get();
        if (this.soldier.getNavigation().isDone() || repath) {
            repathTicks = 0;
            if (!repath && pathEnd != null && !soldier.blockPosition().equals(pathEnd) && lastStepTicks++ < MAX_LAST_STEP_TICKS) {
                this.soldier.getMoveControl().setWantedPosition(pathEnd.getX() + 0.5, pathEnd.getY(), pathEnd.getZ() + 0.5, 1);
                return false;
            }
            // Walk the same kind of path the reachable check accepted: it ends where the block can be placed.
            Path path = BuilderReach.pathToPlace(soldier, request.getPos(), request.standingSpots(), request.planSpotsFirst());
            if (path == null && easelPos != null && soldier.level() instanceof ServerLevel level && climber.start(level, easelPos.pos(), request.getPos())) {
                return false; // out of reach from the ground: climb a scaffolding column
            }
            pathEnd = path == null || path.getEndNode() == null ? null : path.getEndNode().asBlockPos();
            lastStepTicks = 0;
            if (path != null) {
                this.soldier.getNavigation().moveTo(path, walkSpeed());
            }
        }
        return false;
    }

    /**
     * This builder's own pace on the way to a block (D82): the normal speed give or take
     * {@code build.walk_speed_spread_percent}, fixed per soldier (from its id), so a crew doesn't walk in step. Builds that
     * follow a plan only.
     */
    private double walkSpeed() {
        int spread = Tuning.BUILD_WALK_SPEED_SPREAD_PERCENT.get();
        if (spread <= 0 || request == null || request.standingSpots().isEmpty()) {
            return 1.0;
        }
        long bits = soldier.getUUID().getLeastSignificantBits();
        double unit = ((bits >>> 11) & 0xFFFF) / (double) 0xFFFF * 2.0 - 1.0; // -1 .. 1
        return 1.0 + unit * spread / 100.0;
    }

    protected boolean isValidTarget(LevelReader pLevel, @Nullable BlockPos pPos) {
        return pPos != null && pLevel.getBlockState(pPos).is(ModBlocks.EASEL_BLOCK.get());
    }


    private IBlockCache<BlueprintRequestHandler> getBlueprintCache(BlockPos pos) {
        if (pos != null) {
            return ClaySoldiersCommon.CAPABILITY_MANGER.createBlueprint((ServerLevel) soldier.level(), pos);
        }
        return null;
    }


    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean workRequiresItemCarrying() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(BUILDING_LANG);
    }

    @Override
    public void stop() {
        if (request != null) {
            request.cancel();
        }
        stuckTicks = 0;
        placeWait = 0;
        followUpFrom = null;
        cameDown = false;
        climber.reset();
        if (pathSearchMultiplier != 1.0F) {
            pathSearchMultiplier = 1.0F;
            soldier.getNavigation().setMaxVisitedNodesMultiplier(1.0F);
        }
        if (soldier.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(0);
        }
        scaffoldBuild = false;
    }

    @Override
    public String asString() {
        return "BuildBlueprintGoal{%s: %s(%s)}".formatted(
                easelPos == null ? "Null" : "Cap",
                request,
                request == null ? (bringBack ? "bringBack" : "-") : canReach(request.getPos(), request.standingSpots()) ? "Can Reach" : "Cannot Reach");
    }
}
