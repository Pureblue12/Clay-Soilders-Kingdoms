package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/**
 * A builder's request to place or remove one scaffold block. Removal requests start "placing" (nothing to fetch from
 * storage); with {@code refund} the builder carries the removed block back to its storage.
 */
public class ScaffoldRequest extends BlueprintRequest {
    private final boolean removal;
    private final boolean refund;
    /** For a removal: how many scaffold blocks came down (a whole column for climbed columns). */
    private int removedCount;

    public ScaffoldRequest(Item item, BlockPos pos, long start, boolean removal, boolean refund) {
        super(item, pos, start);
        this.removal = removal;
        this.refund = refund;
        if (removal) {
            setPlacing();
        }
    }

    public boolean isRemoval() {
        return removal;
    }

    public boolean refund() {
        return refund;
    }

    public int removedCount() {
        return removedCount;
    }

    void setRemovedCount(int removedCount) {
        this.removedCount = removedCount;
    }

    @Override
    public String toString() {
        return (removal ? "Scaffold removal " : "Scaffold ") + super.toString();
    }
}
