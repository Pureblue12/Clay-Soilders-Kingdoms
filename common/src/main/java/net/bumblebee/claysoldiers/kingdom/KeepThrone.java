package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Where the king sits in each keep (stage 4), in the structure file's own coordinates (x, y, z from the file's first
 * corner), per detail level. From the keep's generator ({@code tools/revise1/keep.py}): level 1 has no throne room yet,
 * so the king sits in the chair at the desk of his chamber on the upper floor; levels 2 and 3 have the throne on the dais
 * of the great hall. The test hut's seat is the middle of its floor (GameTests).
 * <p>
 * A table here rather than a metadata field: the structure metadata is written by the Studio's promote step, and only
 * keeps have a throne. Move it into the metadata when more structures get seats.
 */
public final class KeepThrone {
    private static final Map<ResourceLocation, Map<Integer, BlockPos>> SEATS = Map.of(
            csr("keep"), Map.of(1, new BlockPos(17, 6, 17), 2, new BlockPos(15, 2, 29), 3, new BlockPos(15, 2, 29)),
            csr("test_hut"), Map.of(1, new BlockPos(2, 0, 2)));

    private KeepThrone() {
    }

    /** The seat of {@code structure} at detail level {@code level}, in file coordinates, if it has one. */
    public static Optional<BlockPos> seat(ResourceLocation structure, int level) {
        return Optional.ofNullable(SEATS.getOrDefault(structure, Map.of()).get(level));
    }

    private static ResourceLocation csr(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
    }
}
