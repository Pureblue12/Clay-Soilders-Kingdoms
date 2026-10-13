package net.bumblebee.claysoldiers.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.List;

public class BlueprintRequest {
    private final Item item;
    private final BlockPos pos;
    private final long start;
    private Status status = Status.STARTED;
    /** Set by the plan when a soldier was in the way of placing (D64); the builder reads and clears it, then tries again. */
    private boolean soldierInTheWay;
    /** Where the build plan says a builder can stand to place this block (D82); empty without a plan. */
    private List<BlockPos> standingSpots = List.of();
    /** Whether the builder walks to the plan's standing spots before searching the normal way (a block given up before). */
    private boolean planSpotsFirst;
    /** Whether the builder gave this request up (its give-up timer ran out before it could place the block). */
    private boolean givenUp;

    public BlueprintRequest(Item item, BlockPos pos, long start) {
        this.item = item;
        this.pos = pos;
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public Item getItem() {
        return item;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean hasStarted() {
        return status == Status.STARTED;
    }
    public boolean isPlacing() {
        return status == Status.PLACING;
    }

    public void setPlacing() {
        status = Status.PLACING;
    }
    public void setFinished() {
        status = Status.FINISHED;
    }
    public boolean isFinished() {
        return status == Status.FINISHED;
    }
    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }
    public void cancel() {
        status = Status.CANCELLED;
    }

    /** Cancels this request because the builder gave it up (see {@link #wasGivenUp}). */
    public void giveUp() {
        givenUp = true;
        cancel();
    }

    public boolean wasGivenUp() {
        return givenUp;
    }

    public void setSoldierInTheWay() {
        soldierInTheWay = true;
    }

    /** Whether the last placing attempt found a soldier in the way; clears the flag. */
    public boolean takeSoldierInTheWay() {
        boolean was = soldierInTheWay;
        soldierInTheWay = false;
        return was;
    }

    public void setStandingSpots(List<BlockPos> spots) {
        standingSpots = List.copyOf(spots);
    }

    /** Where the build plan says a builder can stand to place this block (D82); empty without a plan. */
    public List<BlockPos> standingSpots() {
        return standingSpots;
    }

    public void setPlanSpotsFirst(boolean first) {
        planSpotsFirst = first;
    }

    /**
     * Whether the builder tries the plan's standing spots before the nearest spot it can place from (D82): set for a block
     * builders gave up before, whose nearest spot may be one they can't really get to (chapel L2/L3's annex chest).
     */
    public boolean planSpotsFirst() {
        return planSpotsFirst;
    }

    @Override
    public String toString() {
        return "BlueprintRequest{%s at: %s|%s}".formatted(item, pos, status);
    }

    private enum Status {
        /**
         * Indicates this Request has started but the item has not yet been retrieved.
         */
        STARTED,
        /**
         * The Item has been retrieved and is ready to be placed.
         */
        PLACING,
        /**
         * The item has been placed down.
         */
        FINISHED,
        /**
         * The Request got cancelled, the Item has not been placed down. The request should not be attempted to fulfill
         */
        CANCELLED;
    }
}
