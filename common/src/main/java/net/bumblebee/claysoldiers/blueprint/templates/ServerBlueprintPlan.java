package net.bumblebee.claysoldiers.blueprint.templates;

import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerBlueprintPlan extends BlueprintPlan {
    private final List<BlueprintBlockInfo> blockInfoList;

    public ServerBlueprintPlan(Vec3i size, List<StructureTemplate.StructureBlockInfo> blockInfoList) {
        super(BlueprintUtil.getNeededItemsFromInfo(blockInfoList, StructureTemplate.StructureBlockInfo::state), size);
        this.blockInfoList = fromBlockInfoList(blockInfoList);
    }

    public static ServerBlueprintPlan load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerBlueprintPlan serverBlueprintPlan = new ServerBlueprintPlan(BlueprintUtil.getSizeFromTag(tag), ImmutableTemplate.loadPallet(registries.lookupOrThrow(Registries.BLOCK), tag.getList("palette", 10), tag.getList(StructureTemplate.BLOCKS_TAG, 10), List.of()));
        serverBlueprintPlan.loadHasStarted(tag);
        return serverBlueprintPlan;
    }

    public CompoundTag save(CompoundTag pTag) {
        if (this.blockInfoList.isEmpty()) {
            pTag.put("blocks", new ListTag());
            pTag.put("palette", new ListTag());
        } else {
            BlueprintUtil.SimplePalette simplePalette = new BlueprintUtil.SimplePalette();
            ListTag blockInfoAsPallet = new ListTag();

            blockInfoList.forEach(blockInfo -> {
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.put(
                        "pos",
                        this.newIntegerList(
                                blockInfo.getPos().getX(),
                                blockInfo.getPos().getY(),
                                blockInfo.getPos().getZ()
                        )
                );
                int stateId = simplePalette.idFor(blockInfo.getState());
                compoundtag.putInt("state", stateId);
                if (blockInfo.getNbt() != null) {
                    compoundtag.put("nbt", blockInfo.getNbt());
                }

                blockInfoAsPallet.add(compoundtag);
            });

            pTag.put("blocks", blockInfoAsPallet);
            ListTag palletTag = new ListTag();

            for (BlockState blockstate : simplePalette) {
                palletTag.add(NbtUtils.writeBlockState(blockstate));
            }

            pTag.put("palette", palletTag);
        }

        saveSize(pTag);
        saveHasStarted(pTag);
        return pTag;
    }

    @Override
    public PlaceResult tryPlacing(Level level, ItemStack itemStack, BlockPos base, BlueprintTemplateSettings settings) {
        if (!(itemStack.getItem() instanceof BlockItem)) {
            // Items that aren't blocks (e.g. a stick, or AIR for block states without an item) can never be placed.
            return PlaceResult.NOT_NEEDED;
        }
        var res = tryPlacingAll(itemStack.getItem(), itemStack, level, base, settings);
        if (res == PlaceResult.SUCCESS) {
            if (!hasItemAndShrink(itemStack.getItem())) {
                throw new IllegalStateException("Successfully placed a Block which was not needed");
            }
        }

        return res;
    }

    /**
     * Places the block of a builder's request exactly at the request's spot. Unlike {@link #tryPlacing}, never uses
     * another spot: if the request is no longer this plan's open request for its spot (cancelled, taken over, already
     * filled) or the item doesn't match, nothing is placed.
     */
    public PlaceResult tryPlacingRequest(Level level, BlueprintRequest request, ItemStack itemStack, BlockPos base, BlueprintTemplateSettings settings) {
        if (!(itemStack.getItem() instanceof BlockItem)) {
            return PlaceResult.NOT_NEEDED;
        }
        var iterator = blockInfoList.iterator();
        while (iterator.hasNext()) {
            var blockInfo = iterator.next();
            if (!blockInfo.isActiveRequest(request)) {
                continue;
            }
            BlockPos pos = targetPosition(base, settings, blockInfo);
            if (!pos.equals(request.getPos()) || !blockInfo.isFor(itemStack.getItem())) {
                return PlaceResult.NOT_NEEDED;
            }
            var updatedBlockInfo = processBlockInfos(settings, blockInfo);
            if (!placeBlock(itemStack, level, pos, updatedBlockInfo.state().mirror(settings.mirror()).rotate(settings.rotation()))) {
                return PlaceResult.CANT_PLACE;
            }
            iterator.remove();
            blockInfo.markDone();
            if (!hasItemAndShrink(itemStack.getItem())) {
                throw new IllegalStateException("Successfully placed a Block which was not needed");
            }
            return PlaceResult.SUCCESS;
        }
        return PlaceResult.NOT_NEEDED;
    }

    private PlaceResult tryPlacingAll(Item item, ItemStack stack, Level level, BlockPos base, BlueprintTemplateSettings settings) {
        var iterator = blockInfoList.iterator();
        while (iterator.hasNext()) {
            var blockInfo = iterator.next();
            if (!blockInfo.isFor(item)) {
                continue;
            }
            var updatedBlockInfo = processBlockInfos(settings, blockInfo);

            if (placeBlock(stack, level, updatedBlockInfo.pos().offset(base), updatedBlockInfo.state().mirror(settings.mirror()).rotate(settings.rotation()))) {
                iterator.remove();
                blockInfo.markDone();
                return PlaceResult.SUCCESS;
            }
        }
        return PlaceResult.CANT_PLACE;
    }

    private boolean placeBlock(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        if (!canPlaceBlock(level, pos)) {
            return false;
        }
        if (level.setBlock(pos, state, 3)) {
            state.getBlock().setPlacedBy(level, pos, state, null, stack);
            return true;
        }
        return false;
    }

    private boolean canPlaceBlock(Level level, BlockPos pos) {
        return canPlaceAt(level, pos);
    }

    /**
     * Returns whether a blueprint block can be placed at the given position (it is air or replaceable).
     * Blocks that fail this are never requested, so a blueprint with such a spot never finishes.
     */
    public static boolean canPlaceAt(LevelReader level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * Returns the world positions of the blocks this plan still has to place.
     *
     * @param base     the base position of the template, see {@code EaselBlockEntity.templateBase}
     * @param settings rotation and mirror of the template
     */
    public List<BlockPos> targetPositions(BlockPos base, BlueprintTemplateSettings settings) {
        return blockInfoList.stream().map(blockInfo -> targetPosition(base, settings, blockInfo)).toList();
    }

    private static BlockPos targetPosition(BlockPos base, BlueprintTemplateSettings settings, BlueprintBlockInfo blockInfo) {
        return base.offset(adjustForStructureSettings(settings, blockInfo.getPos()));
    }

    private static StructureTemplate.StructureBlockInfo processBlockInfos(BlueprintTemplateSettings pSettings, BlueprintBlockInfo pBlockInfo) {
        return new StructureTemplate.StructureBlockInfo(
                adjustForStructureSettings(pSettings, pBlockInfo.getPos()),
                pBlockInfo.getState(),
                pBlockInfo.getNbt() != null ? pBlockInfo.getNbt().copy() : null
        );
    }

    @Override
    public String toString() {
        return "ServerBlueprintTemplate{%s Blocks(%s) Items(%s): %s}".formatted(
                getSize(),
                blockInfoList.size(),
                getNumberOfItems(),
                getNeededItems());
    }

    /**
     * Returns the best suitable request for this template
     * @param base the base position from which this structure is build
     */
    public @Nullable BlueprintRequest getRequest(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings, Predicate<BlockPos> canReach) {
        // Blocks another builder has are skipped (no taking over); they become free when cancelled, finished or stale.
        // Counters for the debug summary only (same checks, same order as before).
        int checked = 0;
        int unreachable = 0;
        int notPlaceable = 0;
        int assigned = 0;

        for (BlueprintBlockInfo blockInfo : blockInfoList) {
            checked++;
            var infoAbsolutePos = targetPosition(base, settings, blockInfo);
            if (!canReach.test(infoAbsolutePos)) {
                unreachable++;
                continue;
            }
            if (!canPlaceBlock(level, infoAbsolutePos)) {
                notPlaceable++;
                continue;
            }
            if (blockInfo.hasAvailableRequest(level.getGameTime())) {
                logRequestSearch(checked, unreachable, notPlaceable, assigned, "handing out a free block");
                return blockInfo.getRequest(level.getGameTime(), infoAbsolutePos);
            }
            assigned++;
        }
        logRequestSearch(checked, unreachable, notPlaceable, assigned, "nothing to hand out");
        return null;
    }

    private void logRequestSearch(int checked, int unreachable, int notPlaceable, int assigned, String result) {
        if (!DebugLog.isOn(DebugCategory.BUILD)) {
            return;
        }
        DebugLog.event(DebugCategory.BUILD, null, () -> "request search: checked %d of %d remaining blocks (%d not reachable, %d not placeable, %d already assigned): %s"
                .formatted(checked, blockInfoList.size(), unreachable, notPlaceable, assigned, result));
    }

    @Override
    public boolean isFinished() {
        return blockInfoList.isEmpty();
    }

    private static BlockPos adjustForStructureSettings(BlueprintTemplateSettings settings, BlockPos pos) {
        return StructureTemplate.calculateRelativePosition(settings.convertTo(), pos.offset(BlockPos.ZERO));
    }

    /**
     * Maps the given {@code List<StructureTemplate.StructureBlockInfo>} to a deep copied {@code List<BlueprintBlockInfo>}.
     * @param infoList the list to copy.
     * @return deep copy {@code List<StructureTemplate.StructureBlockInfo>}
     */
    private static List<BlueprintBlockInfo> fromBlockInfoList(List<StructureTemplate.StructureBlockInfo> infoList) {
        return infoList.stream().map(BlueprintBlockInfo::fromInfo).collect(Collectors.toCollection(LinkedList::new));
    }
}
