package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Where the throne goes in each keep (stage 4), in the structure file's own coordinates (x, y, z from the file's first
 * corner), per detail level. The throne is a gold block the kingdom puts there when the keep is finished, replacing what
 * the file has in that spot; the king stands on it (the owner's call, 2026-10-13: "he's going to stand on a gold block,
 * that's the throne"). From the keep's generator ({@code tools/revise1/keep.py}): level 1 has no throne room, so it goes
 * in the middle of the king's chamber on the upper floor (on its carpet); levels 2 and 3 put it in place of the wooden
 * throne on the great hall's dais, between its arms and under its gold crest. The test hut's is the middle of its floor
 * (GameTests).
 * <p>
 * A table here rather than a metadata field: the structure metadata is written by the Studio's promote step, and only
 * keeps have a throne. Move it into the metadata when more structures get one.
 */
public final class KeepThrone {
    private static final Map<ResourceLocation, Map<Integer, BlockPos>> THRONES = Map.of(
            csr("keep"), Map.of(1, new BlockPos(15, 6, 15), 2, new BlockPos(15, 2, 29), 3, new BlockPos(15, 2, 29)),
            csr("test_hut"), Map.of(1, new BlockPos(2, 0, 2)));

    private KeepThrone() {
    }

    /** Where the throne of {@code structure} at detail level {@code level} goes, in file coordinates, if it has one. */
    public static Optional<BlockPos> throne(ResourceLocation structure, int level) {
        return Optional.ofNullable(THRONES.getOrDefault(structure, Map.of()).get(level));
    }

    private static ResourceLocation csr(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
    }
}
