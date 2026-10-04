package net.bumblebee.claysoldiers.tuning;

import com.google.gson.JsonObject;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tuning file: every balancing number (costs, radii, rates, caps, unlock thresholds) lives in ONE data file,
 * {@code data/csr/tuning/tuning.json}, never hardcoded (see the DESIGN DECISION PROTOCOL in {@code docs/DESIGN.md}).
 * Each key is defined here with a default and an allowed range and documented in {@code docs/TUNING.md}.
 * <p>
 * A missing key uses its default; a bad value is reported and the default is used. The file is read when data packs
 * load ({@code /reload}, world start) and by {@code /csr dev tuning reload}.
 */
public final class Tuning {
    /** Where the tuning file is read from (any data pack, including the mod's own resources). */
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "tuning/tuning.json");
    /** How many kingdom tiers there are; per-tier lists have exactly this many entries. A design constant, not a tuning value. */
    public static final int TIERS = 10;

    private static final Map<String, TuningKey<?>> KEYS = new LinkedHashMap<>();

    public static final TuningKey.IntKey KINGDOM_MAX_LOADED = register(new TuningKey.IntKey("kingdom.max_loaded",
            "How many kingdoms are kept loaded (simulated) at once (D1).", 4, 1, 64));
    public static final TuningKey.IntListKey KINGDOM_TIER_UNLOCK_RENOWN = register(new TuningKey.IntListKey("kingdom.tier_unlock_renown",
            "Renown needed to reach each tier, tier 1 first (placeholder).", List.of(0, 100, 250, 500, 900, 1400, 2100, 3000, 4200, 6000), 0, 1_000_000));
    public static final TuningKey.IntListKey KINGDOM_ZONE_KEEP_RADIUS = register(new TuningKey.IntListKey("kingdom.zone.keep_radius",
            "Radius of the keep zone per tier, in blocks (placeholder).", List.of(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 1, 256));
    public static final TuningKey.IntListKey KINGDOM_ZONE_INNER_RADIUS = register(new TuningKey.IntListKey("kingdom.zone.inner_radius",
            "Outer edge of the inner zone per tier, in blocks (placeholder).", List.of(16, 20, 24, 28, 32, 36, 40, 44, 48, 52), 1, 256));
    public static final TuningKey.IntListKey KINGDOM_WALL_RADIUS = register(new TuningKey.IntListKey("kingdom.zone.wall_radius",
            "Distance of the wall ring from the keep per tier, in blocks (placeholder).", List.of(20, 24, 28, 32, 36, 40, 44, 48, 52, 56), 1, 256));
    public static final TuningKey.IntListKey KINGDOM_ZONE_OUTER_RADIUS = register(new TuningKey.IntListKey("kingdom.zone.outer_radius",
            "Outer edge of the outer zone (fields, camps) per tier, in blocks (placeholder).", List.of(32, 38, 44, 50, 56, 62, 68, 74, 80, 86), 1, 512));
    public static final TuningKey.IntListKey PERFORMANCE_SOLDIER_CAP = register(new TuningKey.IntListKey("performance.soldier_cap_per_tier",
            "Most clay soldiers a kingdom may have per tier (placeholder until the stage 7 performance checkpoint).",
            List.of(8, 12, 16, 20, 26, 32, 40, 48, 56, 64), 1, 1000));
    public static final TuningKey.IntKey EVENTS_WARNING_TIME = register(new TuningKey.IntKey("events.warning_time_ticks",
            "How long before a raid or event the kingdom is warned, in ticks (20 ticks = 1 second).", 1200, 0, 72_000));

    private Tuning() {
    }

    private static <K extends TuningKey<?>> K register(K key) {
        if (KEYS.putIfAbsent(key.id(), key) != null) {
            throw new IllegalStateException("Duplicate tuning key " + key.id());
        }
        return key;
    }

    /** Every key, in the order they are defined. */
    public static List<TuningKey<?>> keys() {
        return List.copyOf(KEYS.values());
    }

    public static Optional<TuningKey<?>> key(String id) {
        return Optional.ofNullable(KEYS.get(id));
    }

    /**
     * The current value of {@code key}.
     */
    public static <T> T get(TuningKey<T> key) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /**
     * Problems found the last time the file was read (empty when it was fine).
     */
    public static List<String> problems() {
        return Collections.emptyList();
    }

    /**
     * The result of reading a tuning file: values for every key (defaults where missing or bad), the keys the file
     * set (with a good value), and the problems found.
     */
    public record Values(Map<String, Object> values, Set<String> setInFile, List<String> problems) {
        public <T> T get(TuningKey<T> key) {
            throw new UnsupportedOperationException("Stage 0: not built yet");
        }
    }

    /**
     * Reads tuning values from a parsed file, without changing the current values.
     */
    public static Values parse(JsonObject json) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }

    /**
     * Reads {@link #FILE} from {@code resourceManager} and makes its values current. A missing file means all
     * defaults (reported as a problem).
     *
     * @return the values now current
     */
    public static Values reload(ResourceManager resourceManager) {
        throw new UnsupportedOperationException("Stage 0: not built yet");
    }
}
