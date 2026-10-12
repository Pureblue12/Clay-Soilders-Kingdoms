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
    /** Where the build plan says a builder can stand to place this block (D80); empty without a plan. */
    private List<BlockPos> standingSpots = List.of();

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

    /** Where the build plan says a builder can stand to place this block (D80); empty without a plan. */
    public List<BlockPos> standingSpots() {
        return standingSpots;
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
