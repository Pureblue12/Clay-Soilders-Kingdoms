package net.bumblebee.claysoldiers.structure;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * All kingdom structures: their metadata ({@link StructureMeta}, from {@code data/<ns>/csr/structures/*.json}) and
 * geometry (vanilla structure files, read through the server's structure manager, which also sees structure-block
 * saves in the world's {@code generated} folder). Validated when loaded; problems are logged (STRUCTURE recorder
 * category and the game log).
 */
public final class StructureRegistry {
    private StructureRegistry() {
    }

    /**
     * A validated set of structures.
     *
     * @param structures every entry, ready and placeholder ({@code TBD})
     * @param problems   validation and parse problems (empty when all is fine)
     */
    public record Snapshot(List<StructureMeta> structures, List<String> problems) {
        public Optional<StructureMeta> byId(ResourceLocation id) {
            return structures.stream().filter(meta -> meta.id().equals(id)).findFirst();
        }

        public List<StructureMeta> ready() {
            return structures.stream().filter(StructureMeta::isReady).toList();
        }
    }

    /**
     * Checks entries: unique ids; legal zone, role, tier and design; for ready entries the structure file exists and its
     * size matches the footprint, cost items exist, the ruin's structure file exists.
     *
     * @param sizeOf the size of a structure file, empty when there is no such file
     * @return problems, each naming the entry
     */
    public static List<String> validate(List<StructureMeta> structures, Function<ResourceLocation, Optional<Vec3i>> sizeOf) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /**
     * Structure file sizes as the server sees them (generated folder first, then data packs and the mod).
     */
    public static Function<ResourceLocation, Optional<Vec3i>> sizesFrom(MinecraftServer server) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /**
     * The current, validated structures of {@code server}.
     */
    public static Snapshot get(MinecraftServer server) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }
}
