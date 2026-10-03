package net.bumblebee.claysoldiers.blueprint.site;

import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A checked place to build a blueprint, see {@link BuildSitePlanner#check}.
 *
 * @param blueprintId  registry id of the blueprint, or {@code null} for an unregistered (test) blueprint
 * @param blueprint    the blueprint
 * @param easelPos     where the easel goes
 * @param easelFacing  the easel's facing; the building goes the opposite way
 * @param templateBase the template's base position (see {@code EaselBlockEntity.templateBase})
 * @param bounds       box around all block positions of the blueprint
 * @param targets      world positions of all blocks the blueprint places
 */
public record BuildSite(@Nullable ResourceLocation blueprintId, BlueprintData blueprint, BlockPos easelPos, Direction easelFacing,
                        BlockPos templateBase, BoundingBox bounds, List<BlockPos> targets) {
    /**
     * The direction from the easel towards the building.
     */
    public Direction buildDirection() {
        return easelFacing.getOpposite();
    }
}
