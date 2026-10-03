package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.capability.BlueprintRequestHandler;
import net.bumblebee.claysoldiers.capability.IBlockCache;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

public class BuildBlueprintGoal extends AbstractWorkGoal {
    public static final String BUILDING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "building");

    @Nullable
    private IBlockCache<BlueprintRequestHandler> easelPos = null;
    private final int searchRange;
    private final int verticalSearchRange;
    protected int verticalSearchStart;
    /**
     * A builder that carries a block this long without placing it gives up: it returns the block and
     * releases the request, so the block can be handed out again (20 seconds).
     */
    public static final int MAX_CARRY_TICKS = 400;
    @Nullable
    private BlueprintRequest request = null;
    private boolean bringBack = false;
    private int carryTicks = 0;
    /**
     * When the easel has no reachable block, the builder waits this many ticks plus up to
     * {@link #RETRY_COOLDOWN_RANDOM} more before asking again, instead of asking (and pathfinding) every tick.
     */
    public static final int RETRY_COOLDOWN_MIN = 20;
    public static final int RETRY_COOLDOWN_RANDOM = 20;
    private int retryCooldown = 0;

    public BuildBlueprintGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector, int searchRange) {
        super(soldier, workSelector);
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
        if (easelPos == null) {
            setStatus(REQUIRES_POI_ID);
            easelPos = findNearestBlock().map(this::getBlueprintCache).orElse(null);
            return;
        }

        if (request == null) {
            if (bringBack) {
                setStatus(RETURNING_ID);
                if (returnCarried()) {
                    bringBack = false;
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
            request = null;
        } else if (request.hasStarted()) {
            getRequestFromStorage();
        } else if (request.isPlacing()) {
            setStatus(CARRYING_ID);
            if (carryTicks == MAX_CARRY_TICKS / 2) DebugLog.event(DebugCategory.BUILD, soldier, "still carrying %s to %s after %s ticks", request.getItem(), request.getPos(), carryTicks);
            if (++carryTicks > MAX_CARRY_TICKS) {
                giveUpRequest();
            } else if (moveToRequest()) {
                placeBlock();
            }
        }
    }

    private void getRequestFromStorage() {
        if (moveToPoi()) {
            assert getCapCache() != null;
            var storage = getCapCache().getCapability();
            if (storage != null) {
                if (!soldier.getCarriedStack().isEmpty()) {
                    var returned = storage.tryInserting(soldier.getCarriedStack());
                    soldier.dropItemStack(returned);
                }
                soldier.setCarriedStack(storage.tryExtracting(stack -> stack.is(request.getItem()), 1));
                if (soldier.getCarriedStack().isEmpty()) {
                    // The storage does not have the item: don't place anything, ask again after a break.
                    DebugLog.event(DebugCategory.BUILD, soldier, "storage has no %s for %s, request cancelled", request.getItem(), request.getPos());
                    request.cancel();
                    takeAShortBreak();
                } else {
                    request.setPlacing();
                    carryTicks = 0;
                }
            } else {
                easelPos = null;
                request = null;
            }
        }
    }

    /**
     * Cancels the current request (so the block can be handed out again) and brings the carried item back.
     */
    private void giveUpRequest() {
        DebugLog.event(DebugCategory.BUILD, soldier, "gave up %s at %s after %s ticks of carrying, returning it", request.getItem(), request.getPos(), carryTicks);
        request.cancel();
        request = null;
        bringBack = !soldier.getCarriedStack().isEmpty();
        carryTicks = 0;
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
            return;
        }
        var requestHandler = easelPos.getCapability();
        if (requestHandler != null) {
            request = requestHandler.getRequest(this::canReach);
            if (request != null) DebugLog.event(DebugCategory.BUILD, soldier, "got request: %s at %s", request.getItem(), request.getPos());
            if (request == null) {
                DebugLog.event(DebugCategory.BUILD, soldier, "no reachable block available, waiting");
                retryCooldown = RETRY_COOLDOWN_MIN + soldier.getRandom().nextInt(RETRY_COOLDOWN_RANDOM + 1);
            }
        } else {
            easelPos = null;
        }
    }

    private boolean canReach(BlockPos pos) {
        return BuilderReach.canReachToPlace(soldier, pos);
    }


    private void placeBlock() {
        var requestHandler = easelPos.getCapability();
        if (requestHandler != null) {
            if (requestHandler.doRequest(request)) {
                DebugLog.event(DebugCategory.BUILD, soldier, "placed %s at %s", request.getItem(), request.getPos());
                soldier.setCarriedStack(ItemStack.EMPTY);
                request.setFinished();
            } else {
                DebugLog.event(DebugCategory.BUILD, soldier, "could not place %s at %s (spot not free?), returning it", request.getItem(), request.getPos());
                bringBack = true;
                request.cancel();
            }
            request = null;
        } else {
            easelPos = null;
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
        if (easelPos.pos().closerToCenterThan(soldier.position(), 2f)) {
            this.soldier.getNavigation().stop();
            return true;
        }
        this.soldier.getNavigation().moveTo(this.easelPos.pos().getX() + 0.5, (this.easelPos.pos().getY() + 1), this.easelPos.pos().getZ() + 0.5, 1);
        return false;
    }

    protected boolean moveToRequest() {
        if (BuilderReach.canPlaceFrom(soldier.position(), request.getPos())) {
            this.soldier.getNavigation().stop();
            return true;
        }
        if (this.soldier.getNavigation().isDone()) {
            // Walk the same kind of path the reachable check accepted: it ends where the block can be placed.
            Path path = BuilderReach.pathToPlace(soldier, request.getPos());
            if (path != null) {
                this.soldier.getNavigation().moveTo(path, 1);
            }
        }
        return false;
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
    }

    @Override
    public String asString() {
        return "BuildBlueprintGoal{%s: %s(%s)}".formatted(
                easelPos == null ? "Null" : "Cap",
                request,
                request == null ? (bringBack ? "bringBack" : "-") : canReach(request.getPos()) ? "Can Reach" : "Cannot Reach");
    }
}
