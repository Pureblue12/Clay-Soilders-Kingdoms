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
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.Clearable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.tuning.Tuning;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Dev tool: pastes a whole registry structure instantly, with undo. Uses the builders' conventions: you stand where an
 * easel would stand, and the structure goes in {@code buildDirection} (like {@code /csr build}), starting 2 blocks
 * away, centered.
 */
public final class StructurePaster {
    /** Newest first; at most {@code dev.paste_undo_depth} (tuning file). Memory only. */
    private static final Deque<Snapshot> UNDO = new ArrayDeque<>();

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
    public static synchronized Pasted paste(ServerLevel level, StructureMeta meta, BlockPos at, Direction buildDirection) {
        if (!meta.isReady() || meta.structure() == null) {
            throw new IllegalArgumentException(meta.id() + " is a placeholder (design " + StructureMeta.TBD + "): it has no structure file yet");
        }
        StructureTemplate template = level.getStructureManager().get(meta.structure()).orElseThrow(() ->
                new IllegalArgumentException("structure file " + meta.structure() + " for " + meta.id() + " not found"));
        Placement placement = placement(at, buildDirection, template.getSize());
        // Known shape: blocks are placed exactly as in the file, without shape updates to or from their neighbors, so
        // nothing outside the box changes (no torch pops off a replaced block) and undo restores everything exactly.
        StructurePlaceSettings settings = placement.settings().convertTo()
                .setIgnoreEntities(true)
                .setKnownShape(true)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        BoundingBox box = template.getBoundingBox(settings, placement.base());

        List<SavedBlock> saved = new ArrayList<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            saved.add(new SavedBlock(pos.immutable(), level.getBlockState(pos),
                    blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess())));
        });
        // Empty containers first, so replacing them doesn't drop their contents (undo puts the contents back).
        saved.forEach(block -> Clearable.tryClear(level.getBlockEntity(block.pos())));
        template.placeInWorld(level, placement.base(), placement.base(), settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        Pasted pasted = new Pasted(meta.id(), at.immutable(), buildDirection, box);
        UNDO.push(new Snapshot(pasted, level.getServer(), level.dimension(), List.copyOf(saved)));
        while (UNDO.size() > Tuning.DEV_PASTE_UNDO_DEPTH.get()) {
            UNDO.removeLast();
        }
        DebugLog.event(DebugCategory.STRUCTURE, null, "pasted %s at %s going %s", meta.id(), at, buildDirection.getName() + ", blocks " + describe(box));
        return pasted;
    }

    /**
     * Restores the blocks the last paste replaced (in the dimension it was pasted in). Empty when there is nothing to
     * undo (also after switching worlds).
     */
    public static synchronized Optional<Pasted> undo(ServerLevel level) {
        Snapshot snapshot = UNDO.peek();
        if (snapshot == null) {
            return Optional.empty();
        }
        if (snapshot.server() != level.getServer()) {
            UNDO.clear(); // pasted in another world
            return Optional.empty();
        }
        UNDO.pop();
        ServerLevel target = level.getServer().getLevel(snapshot.dimension());
        if (target == null) {
            return Optional.empty();
        }
        snapshot.blocks().forEach(block -> Clearable.tryClear(target.getBlockEntity(block.pos())));
        for (SavedBlock block : snapshot.blocks()) {
            target.setBlock(block.pos(), block.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (SavedBlock block : snapshot.blocks()) {
            BlockEntity blockEntity = block.blockEntity() == null ? null : target.getBlockEntity(block.pos());
            if (blockEntity != null) {
                blockEntity.loadWithComponents(block.blockEntity(), target.registryAccess());
                blockEntity.setChanged();
            }
        }
        Pasted pasted = snapshot.pasted();
        DebugLog.event(DebugCategory.STRUCTURE, null, "undid paste of %s at %s, blocks %s", pasted.id(), pasted.at(), describe(pasted.box()));
        return Optional.of(pasted);
    }

    /** How many pastes can be undone. */
    public static synchronized int undoDepth() {
        return UNDO.size();
    }

    /** Forgets all undo snapshots. */
    public static synchronized void clearUndo() {
        UNDO.clear();
    }

    /**
     * A box as "(x, y, z) to (x, y, z)".
     */
    public static String describe(BoundingBox box) {
        return "(%d, %d, %d) to (%d, %d, %d)".formatted(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private record SavedBlock(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity) {
    }

    private record Snapshot(Pasted pasted, MinecraftServer server, ResourceKey<Level> dimension, List<SavedBlock> blocks) {
    }
}
