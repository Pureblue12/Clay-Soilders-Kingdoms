package net.bumblebee.claysoldiers.scaffold;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A scaffold plan (pure data, from {@link ScaffoldPlanner}): where the temporary blocks go, in placing order, and
 * what the simulation of the build found.
 *
 * @param scaffold           scaffold blocks in placing order (lowest first); torn down in reverse height order
 * @param complete           every structure block got placed in the simulation
 * @param unreachable        structure blocks the simulation couldn't place
 * @param structureBlocks    how many structure blocks were to be placed
 * @param teardownComplete   the simulation could remove every scaffold block afterwards, top-down
 * @param note               a short explanation (e.g. why an approach isn't usable)
 */
public record ScaffoldPlan(ScaffoldApproach approach, List<BlockPos> scaffold, boolean complete, List<BlockPos> unreachable,
                           int structureBlocks, boolean teardownComplete, String note) {
    public ScaffoldPlan {
        scaffold = List.copyOf(scaffold);
        unreachable = List.copyOf(unreachable);
    }

    public boolean usable() {
        return approach.usableBySoldiers() && complete && teardownComplete;
    }

    /** Placing plus removing every scaffold block: the extra builder trips. */
    public int extraPlacements() {
        return 2 * scaffold.size();
    }

    public String describe() {
        return approach.getSerializedName() + ": " + scaffold.size() + " scaffold blocks for " + structureBlocks + " structure blocks, "
                + (complete ? "every block reachable" : unreachable.size() + " unreachable")
                + (teardownComplete ? "" : ", teardown stuck")
                + (approach.usableBySoldiers() ? "" : ", not usable by soldiers")
                + (note.isEmpty() ? "" : " (" + note + ")");
    }
}
