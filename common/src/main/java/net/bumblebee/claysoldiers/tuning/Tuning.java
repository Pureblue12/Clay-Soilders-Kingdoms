package net.bumblebee.claysoldiers.tuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static volatile Values current;

    public static final TuningKey.IntKey KINGDOM_CLAIM_RADIUS = register(new TuningKey.IntKey("kingdom.claim_radius",
            "Radius of the land a new kingdom claims around its king, in blocks. Changing it affects only kingdoms founded afterwards.", 32, 4, 256));
    public static final TuningKey.IntKey KINGDOM_CHRONICLE_MAX = register(new TuningKey.IntKey("kingdom.chronicle_max",
            "How many chronicle lines a kingdom keeps; the oldest are dropped (applied on the next new line).", 100, 5, 1000));
    public static final TuningKey.IntKey KINGDOM_FOUND_SEARCH_RADIUS = register(new TuningKey.IntKey("kingdom.found_search_radius",
            "How far /csr kingdom found looks for your king, in blocks.", 16, 1, 64));
    public static final TuningKey.IntKey KINGDOM_CENSUS_INTERVAL = register(new TuningKey.IntKey("kingdom.census_interval_ticks",
            "How often a kingdom's population and its king's position are updated (only while the whole claim is loaded), in ticks.", 100, 20, 12_000));
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
    public static final TuningKey.IntKey TERRAIN_SCAN_RADIUS = register(new TuningKey.IntKey("terrain.scan_radius",
            "How far /csr dev terrain scan looks around you, in blocks (the scan is a square of 2r+1 columns).", 24, 1, 64));
    public static final TuningKey.IntKey TERRAIN_KEEP_SIZE = register(new TuningKey.IntKey("terrain.keep_size",
            "Width of the tier-1 keep's footprint used for site checks and grading, in blocks (placeholder until the keep is designed).", 9, 3, 31));
    public static final TuningKey.IntKey TERRAIN_KEEP_MARGIN = register(new TuningKey.IntKey("terrain.keep_margin",
            "Blocks around the keep footprint that are judged and levelled with it.", 1, 0, 8));
    public static final TuningKey.IntKey TERRAIN_REFUSE_HEIGHT_RANGE = register(new TuningKey.IntKey("terrain.refuse.height_range",
            "A site whose highest and lowest columns differ by this much or more is refused as too steep.", 8, 1, 64));
    public static final TuningKey.IntKey TERRAIN_REFUSE_SLOPE = register(new TuningKey.IntKey("terrain.refuse.slope_percent",
            "A site sloping this much or more (percent along the steeper axis; 100 = 1 up per block) is refused as too steep.", 75, 1, 1000));
    public static final TuningKey.IntKey TERRAIN_REFUSE_CLIFF_STEP = register(new TuningKey.IntKey("terrain.refuse.cliff_step",
            "A step of this many blocks or more between neighbouring columns of a site is a cliff and refused (soldiers hop 1).", 4, 2, 64));
    public static final TuningKey.IntKey TERRAIN_REFUSE_WATER_DEPTH = register(new TuningKey.IntKey("terrain.refuse.water_depth",
            "Water this deep or deeper anywhere on a site is refused as deep water.", 3, 1, 64));
    public static final TuningKey.IntKey TERRAIN_REFUSE_LAVA_DISTANCE = register(new TuningKey.IntKey("terrain.refuse.lava_distance",
            "Lava on the site or within this many blocks of it refuses the site.", 4, 0, 32));
    public static final TuningKey.IntKey TERRAIN_PENALTY_HEIGHT_RANGE = register(new TuningKey.IntKey("terrain.penalty.height_range",
            "Score points lost per block of height difference across a site.", 3, 0, 100));
    public static final TuningKey.IntKey TERRAIN_PENALTY_SLOPE = register(new TuningKey.IntKey("terrain.penalty.slope",
            "Score points lost per 10 percent of slope.", 2, 0, 100));
    public static final TuningKey.IntKey TERRAIN_PENALTY_WATER = register(new TuningKey.IntKey("terrain.penalty.water",
            "Score points lost per 10 percent of the site's columns under (shallow) water.", 4, 0, 100));
    public static final TuningKey.IntKey TERRAIN_PENALTY_TREES = register(new TuningKey.IntKey("terrain.penalty.trees",
            "Score points lost per 10 percent of the site's columns with trees.", 2, 0, 100));
    public static final TuningKey.IntKey TERRAIN_PENALTY_STEP = register(new TuningKey.IntKey("terrain.penalty.step",
            "Score points lost per 10 percent of the site's columns next to a step of 2 or more blocks (smaller than a cliff).", 4, 0, 100));
    public static final TuningKey.IntKey TERRAIN_PENALTY_BIOME = register(new TuningKey.IntKey("terrain.penalty.biome",
            "Score points lost on a wet (ocean, river, beach) or mountain biome.", 10, 0, 100));
    public static final TuningKey.IntKey TERRAIN_SEARCH_RADIUS = register(new TuningKey.IntKey("terrain.search.radius",
            "How far the site search looks around the wanted point for a better keep site, in blocks.", 16, 0, 64));
    public static final TuningKey.IntKey TERRAIN_SEARCH_STEP = register(new TuningKey.IntKey("terrain.search.step",
            "Distance between candidate sites in the search, in blocks.", 4, 1, 16));
    public static final TuningKey.IntKey TERRAIN_SEARCH_MAX_CANDIDATES = register(new TuningKey.IntKey("terrain.search.max_candidates",
            "Most candidate sites one search judges (nearest first).", 81, 1, 1000));
    public static final TuningKey.IntKey TERRAIN_GRADE_CUT_FILL_BUDGET = register(new TuningKey.IntKey("terrain.grade.cut_fill_budget",
            "Most ground blocks cut and fill may remove plus place to level a keep site.", 250, 0, 100000));
    public static final TuningKey.IntKey TERRAIN_GRADE_FOUNDATION_BUDGET = register(new TuningKey.IntKey("terrain.grade.foundation_budget",
            "Most blocks a foundation platform (floor plus pillars) may use.", 300, 0, 100000));
    public static final TuningKey.IntKey TERRAIN_GRADE_FOUNDATION_MAX_HEIGHT = register(new TuningKey.IntKey("terrain.grade.foundation_max_height",
            "Highest a foundation platform may stand above the lowest ground under it, in blocks (builders reach 8 up).", 6, 1, 32));
    public static final TuningKey.IntKey TERRAIN_GRADE_PILLAR_SPACING = register(new TuningKey.IntKey("terrain.grade.pillar_spacing",
            "Distance between the pillars under a foundation platform, in blocks (edges and corners always get one).", 4, 1, 16));
    public static final TuningKey.IntKey TERRAIN_GRADE_CLEAR_TREES_BUDGET = register(new TuningKey.IntKey("terrain.grade.clear_trees_budget",
            "Most tree blocks (logs and leaves) clearing a keep site may remove.", 500, 0, 100000));
    public static final TuningKey.IntKey BUILD_UNREACHABLE_RECHECK_TICKS = register(new TuningKey.IntKey("build.unreachable_recheck_ticks",
            "A blueprint block builders found out of reach isn't checked again (an expensive path search) for this many ticks, unless a block of the build is placed or removed near it first. 0: always check.", 200, 0, 1200));
    public static final TuningKey.IntKey BUILD_PATH_RANGE_BONUS = register(new TuningKey.IntKey("build.path_range_bonus",
            "While a builder works for a build, every path it walks (to a standing spot, back to the easel, to its chest) may be this many blocks longer than its follow range (16) allows (vanilla rejects longer paths and counts the walked length generously: a 20-step walk around a wall measured over 32; the way around a big building, back to the easel, is longer than 16). Combat and paths outside building work keep the normal limit; builds with scaffolding use the larger of this and scaffold.path_range_bonus.", 48, 0, 64));
    public static final TuningKey.IntKey BUILD_STRANDED_TICKS = register(new TuningKey.IntKey("build.stranded_ticks",
            "A builder that has found no way to where it is going for this many ticks in a row (no path, or one that ends short) is brought back to a free spot beside its chest, like a player carrying it down (stranded on a roof or upper floor it built, with no way down that isn't a deadly drop); in ticks, 0 = never.", 1200, 0, 100000));
    public static final TuningKey.IntKey BUILD_PATH_NODES_MULTIPLIER = register(new TuningKey.IntKey("build.path_nodes_multiplier",
            "While a builder works for a build, its path searches may look at this many times more steps (vanilla: 256).", 4, 1, 16));
    public static final TuningKey.IntKey BUILD_FOLLOW_PLAN = register(new TuningKey.IntKey("build.follow_plan",
            "1: builders follow a structure's pre-baked build plan (data/csr/build_plan, made by tools/build_plan.py): the plan's waves in order, a random block of the current wave, the plan's standing spots first (D78). 0: hand out blocks in the blueprint's normal order and search for standing spots, as before.", 1, 0, 1));
    public static final TuningKey.IntKey BUILD_PAUSE_TICKS = register(new TuningKey.IntKey("build.pause_ticks",
            "After placing a block of a build that follows a plan, a builder waits a random 0 to this many ticks before asking for the next one, so a crew doesn't move in lockstep (D78); 0 = no pause.", 6, 0, 200));
    public static final TuningKey.IntKey BUILD_WALK_SPEED_SPREAD_PERCENT = register(new TuningKey.IntKey("build.walk_speed_spread_percent",
            "In builds that follow a plan, each builder walks to its blocks at its own pace: the normal speed give or take up to this many percent, fixed per soldier (D78); 0 = all the same.", 10, 0, 30));
    public static final TuningKey.IntKey WORK_FALL_DAMAGE_PERCENT = register(new TuningKey.IntKey("work.fall_damage_percent",
            "Fall damage to a soldier that is working (building, digging, farming), in percent of vanilla's (a fall of 4 blocks costs half a heart). Upstream multiplies every environmental damage by 100, so a 4-block drop kills; builders working on upper floors fell off and died on every tall build. 10000 restores that.", 100, 0, 10000));
    public static final TuningKey.IntKey SCAFFOLD_COLUMNS_FIRST = register(new TuningKey.IntKey("scaffold.columns_first",
            "1: once builders start putting up a build's scaffolding (nothing else in reach), they put all of it up before working above reach; 0: they raise it a level at a time, whenever nothing else is in reach.", 1, 0, 1));
    public static final TuningKey.BlockKey SCAFFOLD_MATERIAL = register(new TuningKey.BlockKey("scaffold.material",
            "The block builders put up as temporary scaffolding (they fetch it from their storage like any block). Climbed columns (approach 4) need a block soldiers can be inside, like scaffolding; with another block, step pillars are used.",
            ResourceLocation.withDefaultNamespace("scaffolding")));
    public static final TuningKey.IntKey SCAFFOLD_REFUND = register(new TuningKey.IntKey("scaffold.refund",
            "1: builders carry each scaffold block back to their storage when they tear it down (and cleanup drops it); 0: it is used up.", 1, 0, 1));
    public static final TuningKey.IntKey SCAFFOLD_APPROACH = register(new TuningKey.IntKey("scaffold.approach",
            "How builders put up scaffolding: 1 a ramp up the outside, 2 step pillars beside the structure, 3 the structure's own tops plus step pillars where needed, 4 scaffolding columns they climb.", 4, 1, 4));
    public static final TuningKey.IntKey SCAFFOLD_MAX_BLOCKS = register(new TuningKey.IntKey("scaffold.max_blocks",
            "Most scaffold blocks one build may plan; a structure needing more is left unfinished (and recorded as stuck).", 200, 0, 10_000));
    public static final TuningKey.IntKey SCAFFOLD_REPLAN_TICKS = register(new TuningKey.IntKey("scaffold.replan_ticks",
            "How often a stuck build (blocks out of reach) checks again whether it needs (more) scaffolding, in ticks.", 200, 20, 12_000));
    public static final TuningKey.IntKey SCAFFOLD_SWEEP_TICKS = register(new TuningKey.IntKey("scaffold.sweep_ticks",
            "How often scaffold of builds whose easel is gone or has no plan is cleaned up, in ticks.", 100, 20, 12_000));
    public static final TuningKey.IntKey SCAFFOLD_STALE_TICKS = register(new TuningKey.IntKey("scaffold.request_stale_ticks",
            "A scaffold request a builder has had this long without finishing it can be handed to another builder, in ticks.", 600, 100, 12_000));
    public static final TuningKey.IntKey SCAFFOLD_PATH_NODES_MULTIPLIER = register(new TuningKey.IntKey("scaffold.path_nodes_multiplier",
            "While a builder works for a build with scaffolding, its path searches may look at this many times more steps (routes up a scaffold wind around); the larger of this and build.path_nodes_multiplier counts.", 4, 1, 16));
    public static final TuningKey.IntKey SCAFFOLD_WALK_TICKS_PER_STEP = register(new TuningKey.IntKey("scaffold.walk_ticks_per_step",
            "A builder walking to a scaffold column's base gives up after this many ticks per step of its path (at least 200 ticks; D76).", 20, 5, 200));
    public static final TuningKey.IntKey SCAFFOLD_PATH_RANGE_BONUS = register(new TuningKey.IntKey("scaffold.path_range_bonus",
            "While a builder works for a build with scaffolding, its paths may be this many blocks longer (vanilla limits a path's walked length to the follow range, 16; each step up a scaffold counts about 1.4); the larger of this and build.path_range_bonus counts.", 16, 0, 64));
    public static final TuningKey.IntKey SCAFFOLD_REPATH_TICKS = register(new TuningKey.IntKey("scaffold.repath_ticks",
            "While a builder walks to a block of a build with scaffolding, it plans its route again this often (tiny soldiers climb floating steps better with fresh routes), in ticks.", 20, 5, 200));
    public static final TuningKey.IntKey SCAFFOLD_STEP_PRECISION = register(new TuningKey.IntKey("scaffold.step_precision_hundredths",
            "On scaffold blocks soldiers follow their path precisely: a step counts as reached within this many hundredths of a block of its center, and they don't cut corners (vanilla: 67 for tiny soldiers, plus corner cutting).", 30, 5, 67));
    public static final TuningKey.IntKey SCAFFOLD_START_AFTER_STUCK_TICKS = register(new TuningKey.IntKey("scaffold.start_after_stuck_ticks",
            "Builders start putting up a build's scaffolding only after finding nothing in reach for this long with no block placed in between (one builder boxed in for a moment doesn't start it), in ticks.", 100, 0, 6000));
    public static final TuningKey.IntKey SCAFFOLD_CLIMB_SPEED = register(new TuningKey.IntKey("scaffold.climb_speed_hundredths",
            "How fast builders climb up and down scaffolding columns, in hundredths of a block per tick.", 30, 5, 100));
    public static final TuningKey.IntKey SCAFFOLD_TEARDOWN_STUCK_TICKS = register(new TuningKey.IntKey("scaffold.teardown_stuck_ticks",
            "When builders can't take the scaffold down for this long after the structure is finished, it is removed at once, in ticks.", 1200, 100, 72_000));
    public static final TuningKey.IntKey DEV_PASTE_UNDO_DEPTH = register(new TuningKey.IntKey("dev.paste_undo_depth",
            "How many structure pastes /csr dev structure undo can undo (dev tool).", 10, 1, 100));

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
        return current().get(key);
    }

    /**
     * Problems found the last time the file was read (empty when it was fine).
     */
    public static List<String> problems() {
        return current().problems();
    }

    /**
     * The current values (all defaults until the file has been read).
     */
    public static Values current() {
        Values values = current;
        if (values == null) {
            values = parse(new JsonObject());
            current = values;
        }
        return values;
    }

    /**
     * The result of reading a tuning file: values for every key (defaults where missing or bad), the keys the file
     * set (with a good value), and the problems found.
     */
    public record Values(Map<String, Object> values, Set<String> setInFile, List<String> problems) {
        @SuppressWarnings("unchecked")
        public <T> T get(TuningKey<T> key) {
            Object value = values.get(key.id());
            return value == null ? key.defaultValue() : (T) value;
        }
    }

    /**
     * Reads tuning values from a parsed file, without changing the current values.
     */
    public static Values parse(JsonObject json) {
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> setInFile = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (TuningKey<?> key : KEYS.values()) {
            values.put(key.id(), key.defaultValue());
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            TuningKey<?> key = KEYS.get(entry.getKey());
            if (key == null) {
                problems.add("unknown key \"" + entry.getKey() + "\" (a typo?), ignored");
                continue;
            }
            try {
                values.put(key.id(), key.parse(entry.getValue()));
                setInFile.add(key.id());
            } catch (IllegalArgumentException e) {
                problems.add(key.id() + ": " + e.getMessage() + "; using the default " + formatDefault(key));
            }
        }
        return new Values(Collections.unmodifiableMap(values), Collections.unmodifiableSet(setInFile), List.copyOf(problems));
    }

    private static <T> String formatDefault(TuningKey<T> key) {
        return key.format(key.defaultValue());
    }

    /**
     * Reads {@link #FILE} from {@code resourceManager} and makes its values current. A missing file means all
     * defaults (reported as a problem).
     *
     * @return the values now current
     */
    public static Values reload(ResourceManager resourceManager) {
        Values values;
        Optional<Resource> resource = resourceManager.getResource(FILE);
        if (resource.isEmpty()) {
            values = withProblem(parse(new JsonObject()), "tuning file " + FILE + " not found; using all defaults");
        } else {
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                values = json.isJsonObject() ? parse(json.getAsJsonObject())
                        : withProblem(parse(new JsonObject()), "tuning file " + FILE + " is not a JSON object; using all defaults");
            } catch (IOException | JsonParseException e) {
                values = withProblem(parse(new JsonObject()), "tuning file " + FILE + " could not be read (" + e.getMessage() + "); using all defaults");
            }
        }
        current = values;
        values.problems().forEach(problem -> ClaySoldiersCommon.LOGGER.warn("CSR tuning: {}", problem));
        return values;
    }

    private static Values withProblem(Values values, String problem) {
        List<String> problems = new ArrayList<>(values.problems());
        problems.add(problem);
        return new Values(values.values(), values.setInFile(), List.copyOf(problems));
    }
}
