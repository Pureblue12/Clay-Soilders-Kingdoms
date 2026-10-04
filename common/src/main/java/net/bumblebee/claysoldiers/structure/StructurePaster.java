package net.bumblebee.claysoldiers.structure;

import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

/**
 * Dev tool: pastes a whole registry structure instantly, with undo. Uses the builders' conventions: you stand where an
 * easel would stand, and the structure goes in {@code buildDirection} (like {@code /csr build}), starting 2 blocks
 * away, centered.
 */
public final class StructurePaster {
    private StructurePaster() {
    }

    /**
     * Where and how a structure of {@code size} is placed when an easel stands at {@code at} and the building goes in
     * {@code buildDirection}: exactly what {@code BuildSitePlanner} and the builders use.
     *
     * @param easelFacing the easel's facing (opposite of the build direction)
     * @param settings    rotation (from the easel's facing), no mirror
     * @param base        the structure's local (0, 0, 0) in the world
     */
    public record Placement(Direction easelFacing, BlueprintTemplateSettings settings, BlockPos base) {
    }

    public static Placement placement(BlockPos at, Direction buildDirection, Vec3i size) {
        Direction easelFacing = buildDirection.getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(size, Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
        return new Placement(easelFacing, settings, EaselBlockEntity.templateBase(at, easelFacing, settings));
    }

    /**
     * One paste: what, where, and the box of blocks it changed.
     */
    public record Pasted(ResourceLocation id, BlockPos at, Direction buildDirection, BoundingBox box) {
    }

    /**
     * Pastes {@code meta} and remembers the blocks it replaced for {@link #undo}.
     *
     * @throws IllegalArgumentException with a readable reason when it can't be pasted (placeholder, missing file)
     */
    public static Pasted paste(ServerLevel level, StructureMeta meta, BlockPos at, Direction buildDirection) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /**
     * Restores the blocks the last paste in {@code level} replaced. Empty when there is nothing to undo.
     */
    public static Optional<Pasted> undo(ServerLevel level) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /** How many pastes can be undone. */
    public static int undoDepth() {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /** Forgets all undo snapshots. */
    public static void clearUndo() {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }
}
