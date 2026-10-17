package net.bumblebee.claysoldiers.structure;

import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * The decoration step (roadmap stage 5a, D54, D56): what builders can't carry or place, added by code once the last block
 * of a structure's build is placed. It comes from the structure file itself:
 * <ul>
 *     <li>blocks without an item (water, lava, fire, potted plants, farmland, dirt paths, filled cauldrons): left out of
 *     the builders' blueprint and placed here;</li>
 *     <li>block entity data (sign text, banner patterns, a container's items): builders place the block, this step puts
 *     the file's block with its data in again;</li>
 *     <li>entities (item frames, armor stands, paintings).</li>
 * </ul>
 * It is kept as a small structure tag (the file's size and palette, the decoration's blocks and entities) and placed with
 * vanilla's own structure placement, the same rotation and mirror as the builders' blocks. Water flows like any water:
 * a structure must hold it in (a basin, a moat with walls).
 */
public final class StructureDecoration {
    private static final String BLOCKS = StructureTemplate.BLOCKS_TAG;
    private static final String ENTITIES = "entities";
    private static final String PALETTE = "palette";
    private final CompoundTag tag;

    private StructureDecoration(CompoundTag tag) {
        this.tag = tag;
    }

    /**
     * A structure file split in two: what builders build (the file without blocks that have no item and without
     * entities) and its decoration ({@code null} when the file has none).
     */
    public record Split(CompoundTag forBuilders, @Nullable StructureDecoration decoration) {
    }

    /**
     * Splits a structure file's tag ({@link StructureTemplate#save}). Air and blacklisted blocks stay with the builders'
     * part, which drops them itself.
     */
    public static Split split(CompoundTag file, HolderLookup<Block> blocks, Collection<Holder<Block>> blacklist) {
        ListTag palette = file.contains("palettes", Tag.TAG_LIST)
                ? file.getList("palettes", Tag.TAG_LIST).getList(0) : file.getList(PALETTE, Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = NbtUtils.readBlockState(blocks, palette.getCompound(i));
        }
        ListTag builders = new ListTag();
        ListTag decorationBlocks = new ListTag();
        for (Tag entry : file.getList(BLOCKS, Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            int id = block.getInt("state");
            BlockState state = id >= 0 && id < states.length ? states[id] : null;
            if (state == null || state.isAir() || blacklist.stream().anyMatch(state::is)) {
                builders.add(block);
                continue;
            }
            boolean noItem = state.getBlock().asItem() == Items.AIR;
            if (!noItem) {
                builders.add(block);
            }
            if (noItem || block.contains("nbt", Tag.TAG_COMPOUND)) {
                decorationBlocks.add(block.copy());
            }
        }
        ListTag entities = file.getList(ENTITIES, Tag.TAG_COMPOUND);
        CompoundTag forBuilders = file.copy();
        forBuilders.put(BLOCKS, builders);
        forBuilders.put(ENTITIES, new ListTag());
        if (decorationBlocks.isEmpty() && entities.isEmpty()) {
            return new Split(forBuilders, null);
        }
        CompoundTag decoration = new CompoundTag();
        decoration.put("size", file.getList("size", Tag.TAG_INT).copy());
        decoration.put(PALETTE, palette.copy());
        decoration.put(BLOCKS, decorationBlocks);
        decoration.put(ENTITIES, entities.copy());
        return new Split(forBuilders, new StructureDecoration(decoration));
    }

    public int blockCount() {
        return tag.getList(BLOCKS, Tag.TAG_COMPOUND).size();
    }

    public int entityCount() {
        return tag.getList(ENTITIES, Tag.TAG_COMPOUND).size();
    }

    /**
     * Places the decoration of a build whose blueprint starts at {@code base} (the easel's template base) with the
     * easel's rotation and mirror. Records a BUILD event.
     */
    public void apply(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings) {
        StructurePlaceSettings place = settings.convertTo();
        CompoundTag placed = tag.copy();
        // Hanging entities (item frames, paintings) keep the block they hang in as TileX/Y/Z: put the world position
        // there too, so they hang in the right spot however the entity reads its position.
        for (Tag entry : placed.getList(ENTITIES, Tag.TAG_COMPOUND)) {
            CompoundTag entity = (CompoundTag) entry;
            CompoundTag data = entity.getCompound("nbt");
            if (data.contains("TileX")) {
                ListTag local = entity.getList("blockPos", Tag.TAG_INT);
                BlockPos world = StructureTemplate.calculateRelativePosition(place, new BlockPos(local.getInt(0), local.getInt(1), local.getInt(2))).offset(base);
                data.putInt("TileX", world.getX());
                data.putInt("TileY", world.getY());
                data.putInt("TileZ", world.getZ());
            }
        }
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), placed);
        template.placeInWorld(level, base, base, place, level.getRandom(), Block.UPDATE_ALL);
        DebugLog.event(DebugCategory.BUILD, null, "decoration: placed %s blocks and %s entities at %s",
                blockCount(), entityCount(), base);
    }

    public CompoundTag save() {
        return tag.copy();
    }

    public static StructureDecoration load(CompoundTag tag) {
        return new StructureDecoration(tag.copy());
    }
}
