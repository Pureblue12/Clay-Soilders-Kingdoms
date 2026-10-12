package net.bumblebee.claysoldiers.capability;

import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface BlueprintRequestHandler {

    /**
     * Returns a new Blueprint Request or {@code null} if not suitable request exist.
     *
     * @param canReach a predicate that test whether a given {@code BlockPosition} can be reached.
     * @return a new {@code BlueprintRequest}
     */
    @Nullable BlueprintRequest getRequest(Predicate<BlockPos> canReach);

    /**
     * A builder that has just placed a block at {@code placed} and still carries more of {@code item} asks for its next
     * block of that item in the same layer, near it (D79); {@code null} when there is none: it then returns to storage.
     */
    default @Nullable BlueprintRequest getFollowUpRequest(Predicate<BlockPos> canReach, Item item, BlockPos placed) {
        return null;
    }

    /**
     * Executes a {@code BlueprintRequest}.
     * @param request the request to execute.
     * @return whether it was a success or not
     */
    boolean doRequest(@Nullable BlueprintRequest request);
}
