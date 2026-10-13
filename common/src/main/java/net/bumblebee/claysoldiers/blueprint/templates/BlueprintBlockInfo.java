package net.bumblebee.claysoldiers.blueprint.templates;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

public class BlueprintBlockInfo {
    /**
     * An open request older than this (30 seconds) is considered abandoned and its block is free again.
     * Must stay longer than {@code BuildBlueprintGoal.MAX_CARRY_TICKS}, so a working builder always gives up first
     * (checked by the GameTest {@code staleTimeoutLongerThanGiveUpTimer}).
     */
    public static final int STALE_REQUEST_TICKS = 600;
    /**
     * After a block fails to place, it isn't handed out again for this many ticks (5 seconds), so one block that keeps
     * failing can't starve the blocks after it. Each further failure doubles the wait, up to
     * {@link #MAX_PLACE_FAIL_COOLDOWN_TICKS}. Not saved: a reload starts over.
     */
    public static final int PLACE_FAIL_COOLDOWN_TICKS = 100;
    /**
     * The longest wait after repeated failed placements (1 minute).
     */
    public static final int MAX_PLACE_FAIL_COOLDOWN_TICKS = 1200;
    /**
     * A block whose spot a soldier is in the way of (standing there, or it would be walled in, D64) waits this long
     * (2 seconds) before it is handed out again: soldiers move on, so this isn't a failure and doesn't back off.
     */
    public static final int SOLDIER_IN_THE_WAY_TICKS = 40;
    /**
     * The Relative position to the Structures Base.
     */
    private final BlockPos pos;
    private final BlockState state;
    @Nullable
    private final CompoundTag nbt;
    @Nullable
    private BlueprintRequest lastRequest;
    private int placeFailures = 0;
    /** How many earlier requests for this block builders gave up (not counting {@link #lastRequest}); not saved. */
    private int givenUp = 0;
    private long cooldownUntil = 0;

    public BlueprintBlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        this.pos = pos;
        this.state = state;
        this.nbt = nbt;
    }

    public static BlueprintBlockInfo fromInfo(StructureTemplate.StructureBlockInfo info) {
        return new BlueprintBlockInfo(info.pos(), info.state(), info.nbt() != null ? info.nbt().copy() : null);
    }

    /**
     * Returns whether this block is built from {@code item}. Compared by item, not by block, because some items place
     * a different block than their own: a torch item places a wall torch on walls, a sign item a wall sign, a banner
     * item a wall banner. The item is the same one the plan asks for ({@code getRequest}, the needed-items list).
     */
    public boolean isFor(Item item) {
        return state.getBlock().asItem() == item;
    }

    /**
     * Returns whether this block is a door. Clay soldiers can't open doors, so doors are handed out last
     * (see {@code ServerBlueprintPlan.getRequest}).
     */
    public boolean isDoor() {
        return state.getBlock() instanceof DoorBlock;
    }

    /**
     * @return the relative position of this BlockInfo.
     */
    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public @Nullable CompoundTag getNbt() {
        return nbt;
    }

    /**
     * Returns whether this BlockInfo has an available {@code BlueprintRequest}.
     * A {@code BlueprintRequest} is available, when no previous {@code BlueprintRequest} was created
     * or the last {@code BlueprintRequest} got cancelled or was not fulfilled with in {@value #STALE_REQUEST_TICKS} ticks
     * of its builder last getting closer to it (D87).
     * @param gameTime the current game time.
     * @return whether this BlockInfo has an available {@code BlueprintRequest}
     */
    public boolean hasAvailableRequest(long gameTime) {
        return lastRequest == null || lastRequest.isCancelled() || lastRequest.getLastProgress() + STALE_REQUEST_TICKS < gameTime;
    }

    /**
     * Creates a new {@code BlueprintRequest} for this BlockInfo, if an old {@code BlueprintRequest} was already created it will be canceled.
     * @param gameTime the current game time.
     * @param destinationPos the position in the world where this request will be placed.
     * @return a new {@code BlueprintRequest}.
     */
    public BlueprintRequest getRequest(long gameTime, BlockPos destinationPos) {
        var req = new BlueprintRequest(state.getBlock().asItem(), destinationPos, gameTime);

        if (lastRequest != null) {
            if (lastRequest.wasGivenUp()) {
                givenUp++;
            }
            if (!lastRequest.isCancelled() && !lastRequest.isFinished()) {
                DebugLog.event(DebugCategory.BUILD, null, "request %s went stale after %s ticks, freeing its block",
                        lastRequest, gameTime - lastRequest.getStart());
            }
            lastRequest.cancel();
        }
        lastRequest = req;

        return req;
    }

    /** How many times builders gave up a request for this block (their give-up timer ran out). */
    public int timesGivenUp() {
        return givenUp + (lastRequest != null && lastRequest.wasGivenUp() ? 1 : 0);
    }

    /**
     * Returns whether {@code request} is this BlockInfo's current request and is still open (not cancelled or finished).
     */
    public boolean isActiveRequest(BlueprintRequest request) {
        return lastRequest == request && !request.isCancelled() && !request.isFinished();
    }

    /**
     * Records that placing this block (at {@code worldPos}) failed: it isn't handed out again for
     * {@link #placeFailCooldown} ticks, longer after each failure. Logs a BUILD event.
     *
     * @return the cooldown in ticks
     */
    public int placementFailed(long gameTime, BlockPos worldPos) {
        placeFailures++;
        int failures = placeFailures;
        int cooldown = placeFailCooldown(failures);
        cooldownUntil = gameTime + cooldown;
        DebugLog.event(DebugCategory.BUILD, null, () -> "placement cooldown: %s at (%s) failed to place %d time(s), not handed out for %d ticks"
                .formatted(state.getBlock().asItem(), worldPos.toShortString(), failures, cooldown));
        return cooldown;
    }

    /** A soldier was in the way of placing this block (D64): a short wait, not counted as a failure. */
    public void soldierInTheWay(long gameTime, BlockPos worldPos) {
        cooldownUntil = Math.max(cooldownUntil, gameTime + SOLDIER_IN_THE_WAY_TICKS);
        DebugLog.event(DebugCategory.BUILD, null, () -> "soldier in the way: %s at (%s) waits %d ticks"
                .formatted(state.getBlock().asItem(), worldPos.toShortString(), SOLDIER_IN_THE_WAY_TICKS));
    }

    /**
     * The wait after the given number of failed placements: {@value #PLACE_FAIL_COOLDOWN_TICKS} ticks, doubled for each
     * further failure, at most {@value #MAX_PLACE_FAIL_COOLDOWN_TICKS}.
     */
    public static int placeFailCooldown(int failures) {
        int doublings = Math.min(Math.max(failures - 1, 0), 16);
        return (int) Math.min((long) PLACE_FAIL_COOLDOWN_TICKS << doublings, MAX_PLACE_FAIL_COOLDOWN_TICKS);
    }

    /**
     * Returns whether this block is still waiting after a failed placement, so it must not be handed out yet.
     */
    public boolean isCoolingDown(long gameTime) {
        return gameTime < cooldownUntil;
    }

    /**
     * Marks this BlockInfo as placed.
     */
    public void markDone() {
        if (lastRequest != null) {
            lastRequest.setFinished();
        }
    }

    @Override
    public String toString() {
        return "BlockInfo{%s, %s, [%s]}".formatted(pos, state, lastRequest);
    }
}
