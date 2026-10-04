package net.bumblebee.claysoldiers.structure;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * All kingdom structures: their metadata ({@link StructureMeta}, from {@code data/<ns>/csr/structures/*.json}) and
 * geometry (vanilla structure files, read through the server's structure manager, which also sees structure-block
 * saves in the world's {@code generated} folder). Validated when loaded; problems are logged (STRUCTURE recorder
 * category and the game log).
 */
public final class StructureRegistry {
    /** Folder of the metadata files inside a data pack namespace: {@code data/<ns>/csr/structures/}. */
    public static final String DIRECTORY = ClaySoldiersCommon.MOD_ID + "/structures";
    private static final Gson GSON = new Gson();

    private static volatile List<StructureMeta> loaded = List.of();
    private static volatile List<String> loadProblems = List.of();
    @Nullable
    private static volatile Snapshot validated;
    @Nullable
    private static volatile MinecraftServer validatedFor;

    private StructureRegistry() {
    }

    /**
     * A validated set of structures.
     *
     * @param structures every entry, ready and placeholder ({@code TBD}), sorted by id
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
     * The entries read last (not validated yet; use {@link #get} for a validated snapshot). Used for book buttons,
     * which have no server at hand.
     */
    public static List<StructureMeta> loaded() {
        return loaded;
    }

    /**
     * Reads every metadata file from {@code resourceManager} (called when data packs load and by
     * {@link #reload}). Validation happens on the next {@link #get}.
     */
    public static synchronized void load(ResourceManager resourceManager) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(resourceManager, DIRECTORY, GSON, files);
        List<StructureMeta> structures = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        files.forEach((file, json) -> {
            try {
                structures.add(StructureMeta.fromJson(file, json));
            } catch (RuntimeException e) {
                problems.add(file + ": can't read the metadata file: " + e.getMessage());
            }
        });
        structures.sort(Comparator.comparing(meta -> meta.id().toString()));
        loaded = List.copyOf(structures);
        loadProblems = List.copyOf(problems);
        validated = null;
    }

    /**
     * Reads the metadata files again and forgets cached structure files, so edited files (e.g. saved with a structure
     * block into the world's {@code generated} folder) are read fresh. Returns the new, validated snapshot.
     */
    public static synchronized Snapshot reload(MinecraftServer server) {
        Set<ResourceLocation> files = new HashSet<>();
        for (StructureMeta meta : loaded) {
            if (meta.structure() != null) files.add(meta.structure());
            if (meta.ruin() != null) files.add(meta.ruin());
        }
        load(server.getResourceManager());
        validated = null;
        for (StructureMeta meta : loaded) {
            if (meta.structure() != null) files.add(meta.structure());
            if (meta.ruin() != null) files.add(meta.ruin());
        }
        files.forEach(server.getStructureManager()::remove);
        Snapshot snapshot = get(server);
        DebugLog.event(DebugCategory.STRUCTURE, null, "reloaded: %s structures (%s ready), %s problem(s)",
                snapshot.structures().size(), snapshot.ready().size(), snapshot.problems().size());
        return snapshot;
    }

    /**
     * The current, validated structures of {@code server}.
     */
    public static synchronized Snapshot get(MinecraftServer server) {
        Snapshot snapshot = validated;
        if (snapshot == null || validatedFor != server) {
            List<String> problems = new ArrayList<>();
            loadProblems.forEach(problem -> {
                problems.add(problem);
                DebugLog.event(DebugCategory.STRUCTURE, null, "validation: %s", problem);
                ClaySoldiersCommon.LOGGER.warn("CSR structures: {}", problem);
            });
            problems.addAll(validate(loaded, sizesFrom(server)));
            snapshot = new Snapshot(loaded, List.copyOf(problems));
            validated = snapshot;
            validatedFor = server;
            ClaySoldiersCommon.LOGGER.info("CSR structures: {} loaded ({} ready), {} problem(s)",
                    snapshot.structures().size(), snapshot.ready().size(), snapshot.problems().size());
        }
        return snapshot;
    }

    /**
     * Checks entries: unique ids; legal zone, role, tier and design; for ready entries the structure file exists and its
     * size matches the footprint, cost items exist, the ruin's structure file exists. Each problem is also logged
     * (STRUCTURE recorder category and the game log).
     *
     * @param sizeOf the size of a structure file, empty when there is no such file
     * @return problems, each naming the entry
     */
    public static List<String> validate(List<StructureMeta> structures, Function<ResourceLocation, Optional<Vec3i>> sizeOf) {
        List<String> problems = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (StructureMeta meta : structures) {
            String where = meta.id() + " (" + meta.source() + ")";
            if (!seen.add(meta.id())) {
                problems.add(where + ": duplicate id " + meta.id());
            }
            if (!StructureMeta.ZONES.contains(meta.zone())) {
                problems.add(where + ": zone \"" + meta.zone() + "\" is not one of " + StructureMeta.ZONES);
            }
            if (!StructureMeta.ROLES.contains(meta.role())) {
                problems.add(where + ": role \"" + meta.role() + "\" is not one of " + StructureMeta.ROLES);
            }
            if (meta.tier() < 1 || meta.tier() > Tuning.TIERS) {
                problems.add(where + ": tier " + meta.tier() + " is not 1 to " + Tuning.TIERS);
            }
            if (!meta.isReady() && !StructureMeta.TBD.equals(meta.design())) {
                problems.add(where + ": design \"" + meta.design() + "\" is not \"" + StructureMeta.READY + "\" or \"" + StructureMeta.TBD + "\"");
            }
            if (meta.cost() != null) {
                meta.cost().forEach((item, count) -> {
                    if (!BuiltInRegistries.ITEM.containsKey(item)) {
                        problems.add(where + ": cost item " + item + " doesn't exist");
                    }
                    if (count <= 0) {
                        problems.add(where + ": cost of " + item + " must be at least 1, got " + count);
                    }
                });
            }
            if (meta.ruin() != null && sizeOf.apply(meta.ruin()).isEmpty()) {
                problems.add(where + ": ruin structure file " + meta.ruin() + " not found");
            }
            if (meta.isReady()) {
                validateGeometry(meta, where, sizeOf, problems);
                validateLevels(meta, where, sizeOf, problems);
            }
            if (meta.terrain() != null && !StructureMeta.TERRAIN_BEHAVIORS.contains(meta.terrain())) {
                problems.add(where + ": terrain \"" + meta.terrain() + "\" is not one of " + StructureMeta.TERRAIN_BEHAVIORS);
            }
            if (meta.doorSide() != null && !StructureMeta.DOOR_SIDES.contains(meta.doorSide())) {
                problems.add(where + ": door_side \"" + meta.doorSide() + "\" is not one of " + StructureMeta.DOOR_SIDES);
            }
            if (meta.batch() != null && meta.batch() < 1) {
                problems.add(where + ": batch must be at least 1, got " + meta.batch());
            }
        }
        problems.forEach(problem -> {
            DebugLog.event(DebugCategory.STRUCTURE, null, "validation: %s", problem);
            ClaySoldiersCommon.LOGGER.warn("CSR structures: {}", problem);
        });
        return List.copyOf(problems);
    }

    private static void validateGeometry(StructureMeta meta, String where, Function<ResourceLocation, Optional<Vec3i>> sizeOf, List<String> problems) {
        if (meta.structure() == null) {
            problems.add(where + ": a ready structure needs \"structure\" (its structure file)");
            return;
        }
        Optional<Vec3i> size = sizeOf.apply(meta.structure());
        if (size.isEmpty()) {
            problems.add(where + ": structure file " + meta.structure() + " not found (expected data/"
                    + meta.structure().getNamespace() + "/structure/" + meta.structure().getPath() + ".nbt)");
            return;
        }
        if (meta.footprint() == null) {
            problems.add(where + ": a ready structure needs \"footprint\"; the file is " + format(size.get()));
        } else if (!meta.footprint().equals(size.get())) {
            problems.add(where + ": footprint " + format(meta.footprint()) + " doesn't match the structure file's size " + format(size.get()));
        }
    }

    /**
     * Detail levels: level 1 is the structure file; every level's file exists and has the footprint's size (a level only
     * adds blocks, so the box never changes); ground_level is a layer of the structure.
     */
    private static void validateLevels(StructureMeta meta, String where, Function<ResourceLocation, Optional<Vec3i>> sizeOf, List<String> problems) {
        if (!meta.levels().isEmpty()) {
            if (!meta.levels().getFirst().equals(meta.structure())) {
                problems.add(where + ": levels must start with the structure file " + meta.structure() + ", got " + meta.levels().getFirst());
            }
            for (int i = 0; i < meta.levels().size(); i++) {
                ResourceLocation file = meta.levels().get(i);
                Optional<Vec3i> size = sizeOf.apply(file);
                if (size.isEmpty()) {
                    problems.add(where + ": level " + (i + 1) + " structure file " + file + " not found");
                } else if (meta.footprint() != null && !meta.footprint().equals(size.get())) {
                    problems.add(where + ": level " + (i + 1) + " file " + file + " is " + format(size.get())
                            + ", not the footprint " + format(meta.footprint()));
                }
            }
        }
        if (meta.groundLevel() != null && (meta.groundLevel() < 0 || meta.footprint() != null && meta.groundLevel() >= meta.footprint().getY())) {
            problems.add(where + ": ground_level " + meta.groundLevel() + " is not a layer of the structure");
        }
    }

    private static String format(Vec3i size) {
        return "[" + size.getX() + ", " + size.getY() + ", " + size.getZ() + "]";
    }

    /**
     * Structure file sizes as the server sees them (the world's generated folder first, then data packs and the mod).
     */
    public static Function<ResourceLocation, Optional<Vec3i>> sizesFrom(MinecraftServer server) {
        return location -> server.getStructureManager().get(location).map(StructureTemplate::getSize);
    }
}
