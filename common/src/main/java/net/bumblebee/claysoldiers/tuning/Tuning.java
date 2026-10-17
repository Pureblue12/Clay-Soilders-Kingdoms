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
    public static final TuningKey.IntKey KINGDOM_KEEP_LEVEL = register(new TuningKey.IntKey("kingdom.keep.level",
            "Which detail level of the keep (csr:keep) a new kingdom builds: 1 = the keep tower (the throne stands in the king's chamber), 2 = with the great hall and its throne, 3 = the whole castle. Read when the keep is planned.", 1, 1, 3));
    public static final TuningKey.IntKey KINGDOM_KEEP_SEARCH_RADIUS = register(new TuningKey.IntKey("kingdom.keep.search_radius",
            "How far from where the kingdom was founded the middle of its keep may be, in blocks (the nearest flat ground wins).", 12, 0, 64));
    public static final TuningKey.IntKey KINGDOM_KEEP_MAX_HEIGHT_RANGE = register(new TuningKey.IntKey("kingdom.keep.max_height_range",
            "The most the ground under a keep site (with the easel, the chest and a margin of 1) may rise and fall, in blocks; the kingdom levels it when it starts the keep.", 3, 0, 16));
    public static final TuningKey.IntKey KINGDOM_KEEP_BUILDERS = register(new TuningKey.IntKey("kingdom.keep.builders",
            "How many of the kingdom's soldiers build its keep: soldiers of the team in the claim with no job yet, not the king, nearest to the keep's chest first (wheat holders first); the kingdom hands the others wheat so they can work.", 3, 1, 16));
    public static final TuningKey.IntKey KINGDOM_KEEP_SCAFFOLD_STOCK = register(new TuningKey.IntKey("kingdom.keep.scaffold_stock",
            "How much scaffold material (scaffold.material) the kingdom asks for when its keep is taller than builders reach; builders carry it back when they take the scaffolding down.", 128, 0, 1024));
    public static final TuningKey.IntKey KINGDOM_KEEP_CHECK_TICKS = register(new TuningKey.IntKey("kingdom.keep.check_ticks",
            "How often a kingdom looks after its keep (stocks the builders' chest from the stockpile, picks builders, notices it is finished), in ticks.", 20, 1, 1200));
    public static final TuningKey.IntKey KINGDOM_KEEP_PLAN_RETRY_TICKS = register(new TuningKey.IntKey("kingdom.keep.plan_retry_ticks",
            "How long a kingdom waits before trying again to place its keep when it found no site (unloaded ground, too uneven, water, another easel), in ticks.", 600, 20, 72_000));
    public static final TuningKey.IntKey KINGDOM_KING_BONUS_HEALTH = register(new TuningKey.IntKey("kingdom.king_bonus_health",
            "Extra health a king gets once his keep is built (a clay soldier has 20).", 80, 0, 1000));
    public static final TuningKey.IntKey KINGDOM_THRONE_WALK_TICKS = register(new TuningKey.IntKey("kingdom.throne_walk_ticks",
            "How long a king walks toward his throne before his soldiers carry him there (vanilla paths are short and the throne may be up a winding stair), in ticks; 0 = never.", 1200, 0, 72_000));
    public static final TuningKey.IntKey KINGDOM_FOUND_PICK_TOLERANCE = register(new TuningKey.IntKey("kingdom.found_pick_tolerance",
            "A new king (a soldier who picked up a gold ingot) picks the spot for his kingdom at random among the usable keep sites near him that score at most this many points below the best (0 = always the best).", 10, 0, 100));
    public static final TuningKey.IntKey KINGDOM_FOUND_WALK_TICKS = register(new TuningKey.IntKey("kingdom.found_walk_ticks",
            "How long a new king walks toward the spot he picked for his kingdom before he picks another, in ticks.", 1200, 100, 72_000));
    public static final TuningKey.IntKey KINGDOM_FOUND_TRIES = register(new TuningKey.IntKey("kingdom.found_tries",
            "How many spots a new king tries in a row (out of reach, founding refused, or no site found) before he rests (kingdom.found_retry_ticks).", 5, 1, 100));
    public static final TuningKey.IntKey KINGDOM_FOUND_RETRY_TICKS = register(new TuningKey.IntKey("kingdom.found_retry_ticks",
            "How long a new king rests after kingdom.found_tries spots didn't work out before he looks again, in ticks.", 1200, 20, 72_000));
    public static final TuningKey.IntKey KINGDOM_KEEP_FREE_MATERIALS = register(new TuningKey.IntKey("kingdom.keep.free_materials",
            "1 = when a kingdom starts its keep, everything the keep needs is added to its stockpile (until soldiers gather materials, stage 5); 0 = the materials must be supplied.", 1, 0, 1));
    public static final TuningKey.IntKey ECONOMY_PLOT_OUTER_REACH = register(new TuningKey.IntKey("economy.plot.outer_reach",
            "How far beyond its claim a kingdom may put its outer buildings (lumber camp, fields), in blocks: they stand in a ring just outside where the wall will be; houses stay inside the claim.", 16, 0, 64));
    public static final TuningKey.IntKey ECONOMY_PLOT_GAP = register(new TuningKey.IntKey("economy.plot.gap",
            "Free blocks between a new building's plot (with its easel and chest) and every building already there (room for a path).", 3, 0, 16));
    public static final TuningKey.IntKey ECONOMY_PLOT_MAX_HEIGHT_RANGE = register(new TuningKey.IntKey("economy.plot.max_height_range",
            "The most the ground under a plot after the keep may rise and fall, in blocks; the kingdom levels it when it starts the building.", 3, 0, 16));
    public static final TuningKey.IntKey LAYOUT_ENABLED = register(new TuningKey.IntKey("layout.enabled",
            "1 = when its keep is finished a kingdom draws up its master plan (streets, house plots, a straight wall with gates, outer plots) and builds on it; 0 = buildings go on the nearest free ground (stage 5).", 1, 0, 1));
    public static final TuningKey.IntKey LAYOUT_SCAN_RADIUS = register(new TuningKey.IntKey("layout.scan_radius",
            "How far around its keep a kingdom looks at the land when it draws up its master plan, in blocks (only loaded ground counts).", 192, 32, 256));
    public static final TuningKey.IntKey LAYOUT_ROAD_WIDTH = register(new TuningKey.IntKey("layout.road_width",
            "How wide the main street and the cross street are, in blocks (an even number is rounded up to the next odd one).", 3, 1, 7));
    public static final TuningKey.IntKey LAYOUT_SQUARE_DEPTH = register(new TuningKey.IntKey("layout.square_depth",
            "How deep the open square in front of the keep's door is, in blocks.", 5, 1, 16));
    public static final TuningKey.IntKey LAYOUT_SQUARE_WIDTH = register(new TuningKey.IntKey("layout.square_width",
            "How wide the square in front of the keep's door is, in blocks.", 7, 1, 31));
    public static final TuningKey.IntKey LAYOUT_PLOT_GAP = register(new TuningKey.IntKey("layout.plot_gap",
            "Free blocks between two plots of the master plan (and between a plot and the keep).", 2, 0, 16));
    public static final TuningKey.IntKey LAYOUT_WALL_SETBACK = register(new TuningKey.IntKey("layout.wall_setback",
            "Free blocks between the wall and the plots inside it.", 3, 1, 16));
    public static final TuningKey.IntKey LAYOUT_WALL_SHIFT = register(new TuningKey.IntKey("layout.wall_shift",
            "The most a side of the wall moves out from the town to stay off water, cliffs and caves, in blocks.", 6, 0, 16));
    public static final TuningKey.IntKey LAYOUT_OUTER_BAND = register(new TuningKey.IntKey("layout.outer_band",
            "How far outside the wall the outer buildings (lumber camp, fields, the fishing plot) may stand, in blocks.", 24, 4, 64));
    public static final TuningKey.IntKey LAYOUT_LATER_PLOTS = register(new TuningKey.IntKey("layout.later_plots",
            "Extra house plots (the kingdom's first house again) along the streets the master plan keeps free for houses built later.", 4, 0, 16));
    public static final TuningKey.IntKey LAYOUT_ROSTER = register(new TuningKey.IntKey("layout.roster",
            "1 = the master plan keeps a plot for every Studio building the kingdom will add later (the wall is sized for the whole town); 0 = only for its own list and the extra house plots.", 1, 0, 1));
    public static final TuningKey.IntKey LAYOUT_SHOW_SECONDS = register(new TuningKey.IntKey("layout.show_seconds",
            "How long players near a kingdom see its plan in particles when it draws the plan up, and a building's plot when it starts the building, in seconds; 0 = never shown by itself.", 30, 0, 300));
    public static final TuningKey.IntKey LAYOUT_LAY_ROADS = register(new TuningKey.IntKey("layout.lay_roads",
            "1 = the kingdom lays its planned roads at once as dirt paths when it draws up its plan (soldiers don't lay roads yet); 0 = the roads are only planned.", 1, 0, 1));
    public static final TuningKey.IntKey LAYOUT_BRIDGE_MAX_LENGTH = register(new TuningKey.IntKey("layout.bridge_max_length",
            "The longest stretch of water (blocks) a laid road bridges with planks, with land at both ends (a stream, a pond); 0 = no bridges.", 8, 0, 32));
    public static final TuningKey.IntKey ECONOMY_WOODCUTTERS = register(new TuningKey.IntKey("economy.woodcutters",
            "How many idle soldiers become woodcutters at a finished lumber camp (picked while nothing is being built, so builders come first).", 2, 0, 16));
    public static final TuningKey.IntKey ECONOMY_FARMERS = register(new TuningKey.IntKey("economy.farmers",
            "How many idle soldiers become farmers at a finished field (picked while nothing is being built).", 1, 0, 16));
    public static final TuningKey.IntKey ECONOMY_LUMBER_RADIUS = register(new TuningKey.IntKey("economy.lumber.radius",
            "How far from its lumber camp's easel a woodcutter looks for trees, in blocks.", 24, 4, 64));
    public static final TuningKey.IntKey ECONOMY_LUMBER_MAX_TRUNK = register(new TuningKey.IntKey("economy.lumber.max_trunk",
            "The tallest trunk a woodcutter fells, in logs (its top must be within a builder's reach of the ground); taller trees are left alone.", 9, 1, 16));
    public static final TuningKey.IntKey ECONOMY_LUMBER_CHOP_TICKS = register(new TuningKey.IntKey("economy.lumber.chop_ticks",
            "How long a woodcutter chops each log, in ticks.", 20, 1, 400));
    public static final TuningKey.IntKey ECONOMY_LUMBER_WALK_TICKS = register(new TuningKey.IntKey("economy.lumber.walk_ticks",
            "How long a woodcutter or farmer walks toward a tree or field cell before leaving it alone for a while, in ticks.", 600, 40, 12000));
    public static final TuningKey.IntKey ECONOMY_FARM_TEND_TICKS = register(new TuningKey.IntKey("economy.farm.tend_ticks",
            "How long a farmer tends a growing crop to make it grow one stage, in ticks (0 = never tends: crops grow at vanilla speed only).", 40, 0, 2400));
    public static final TuningKey.IntKey ECONOMY_FARM_CARRY = register(new TuningKey.IntKey("economy.farm.carry",
            "How much of its harvest a farmer carries before it brings it to the field's chest.", 8, 1, 64));
    public static final TuningKey.IntKey ECONOMY_FOOD_PER_BUILDING = register(new TuningKey.IntKey("economy.food_per_building",
            "Wheat a building the kingdom pays for (the cottage) costs: the builders' meals, taken when it starts; it waits until the stockpile has it.", 9, 0, 640));
    public static final TuningKey.IntKey ECONOMY_MEAL_TICKS = register(new TuningKey.IntKey("economy.meal.ticks",
            "How long a meal keeps the builders of a building the kingdom paid food for well fed, in ticks; eaten again when it wears off while they still build it (0 = no meals).", 6000, 0, 72000));
    public static final TuningKey.IntKey ECONOMY_MEAL_SPEED_LEVEL = register(new TuningKey.IntKey("economy.meal.speed_level",
            "Speed level a meal gives (1 = Speed I, 20% faster walking; 0 = none).", 1, 0, 3));
    public static final TuningKey.IntKey ECONOMY_MEAL_HEALTH_LEVEL = register(new TuningKey.IntKey("economy.meal.health_level",
            "Health Boost level a meal gives (each level adds 4 health, 2 hearts; 0 = none).", 1, 0, 5));
    public static final TuningKey.IntKey ECONOMY_FREE_STARTER_BUILDINGS = register(new TuningKey.IntKey("economy.free_starter_buildings",
            "1 = the kingdom's starter buildings after the keep (lumber camp, field) get all their materials, like the keep; 0 = they must be supplied.", 1, 0, 1));
    public static final TuningKey.IntKey ECONOMY_FREE_OTHER_MATERIALS = register(new TuningKey.IntKey("economy.free_other_materials",
            "1 = a building the kingdom pays for with its wood and food gets its other materials (stone, bricks, glass) for free, as no job makes them before stage 9; 0 = a player supplies them through the chest.", 1, 0, 1));
    public static final TuningKey.IntKey ECONOMY_RENOWN_PER_BUILDING = register(new TuningKey.IntKey("economy.renown_per_building",
            "Renown a kingdom earns for every building it finishes after the keep.", 25, 0, 1000));
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
    public static final TuningKey.IntKey TERRAIN_CAVE_PROBE_DEPTH = register(new TuningKey.IntKey("terrain.cave_probe_depth",
            "How far under the ground the land scan looks for caves and holes, in blocks (0 = not at all). The kingdom planner keeps plots off them, and the kingdom fills any under a plot it levels.", 4, 0, 32));
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
    public static final TuningKey.IntKey BUILD_CARRY_COUNT = register(new TuningKey.IntKey("build.carry_count",
            "How many of a block's item a builder takes from its chest at once (D79). After placing one it goes straight to the nearest free block of that item in the same layer it can reach, and only walks back to the easel and its chest when there is none (then it puts back what is left). 1 = one block per trip to the chest.", 16, 1, 64));
    public static final TuningKey.IntKey BUILD_CARRY_MAX_TICKS = register(new TuningKey.IntKey("build.carry_max_ticks",
            "A builder keeps its block while it keeps getting closer to where it is going (its chest, then the block's spot), and gives it up after 400 ticks without getting a block closer (D87); this is the most it keeps one block in all, in ticks.", 2000, 400, 100000));
    public static final TuningKey.IntKey BUILD_PATH_NODES_MULTIPLIER = register(new TuningKey.IntKey("build.path_nodes_multiplier",
            "While a builder works for a build, its path searches may look at this many times more steps (vanilla: 256).", 4, 1, 16));
    public static final TuningKey.IntKey BUILD_FOLLOW_PLAN = register(new TuningKey.IntKey("build.follow_plan",
            "1: builders follow a structure's pre-baked build plan (data/csr/build_plan, made by tools/build_plan.py): the plan's waves in order, a random block of the current wave, the plan's standing spots first (D82). 0: hand out blocks in the blueprint's normal order and search for standing spots, as before.", 1, 0, 1));
    public static final TuningKey.IntKey BUILD_PLAN_LOOKAHEAD_CLEARANCE = register(new TuningKey.IntKey("build.plan_lookahead_clearance",
            "In builds that follow a plan, while other builders finish the current wave's last blocks, an idle builder may take a block of the next wave that is more than this many blocks from every block of the current wave still to place and its standing spots (D82); 0 = wait for the wave to finish.", 4, 0, 16));
    public static final TuningKey.IntKey BUILD_PLAN_LOOKAHEAD_WAVES = register(new TuningKey.IntKey("build.plan_lookahead_waves",
            "In builds that follow a plan, how many waves past the current one an idle builder may take a block from, and a builder with a stack may follow up into, when the block is clear of every earlier wave's blocks still to place (build.plan_lookahead_clearance) (D82); 1 = only the next wave.", 3, 1, 16));
    public static final TuningKey.IntKey BUILD_PLAN_FOLLOW_UP_CLEARANCE = register(new TuningKey.IntKey("build.plan_follow_up_clearance",
            "In builds that follow a plan, a builder with a stack (D79) may follow up into a block of the next build.plan_lookahead_waves waves that is more than this many blocks from every standing spot (and the cell above it) of an earlier wave's block still to place (D82); 0 = only not on such a spot, -1 = only the current wave.", 0, -1, 16));
    public static final TuningKey.IntKey BUILD_PLAN_SPOTS_FIRST = register(new TuningKey.IntKey("build.plan_spots_first",
            "In builds that follow a plan, 1: a builder walks to the nearest free one of the plan's standing spots for its block, and searches only when none can be walked to; 0: it searches the nearest spot it can place from first and uses the plan's spots only when that search fails (D82; 0 took 16% longer than main against 30% with 1, same-time runs).", 0, 0, 1));
    public static final TuningKey.IntKey BUILD_PAUSE_TICKS = register(new TuningKey.IntKey("build.pause_ticks",
            "After placing a block of a build that follows a plan, a builder waits a random 0 to this many ticks before asking for the next one, so a crew doesn't move in lockstep (D82); 0 = no pause.", 6, 0, 200));
    public static final TuningKey.IntKey BUILD_WALK_SPEED_SPREAD_PERCENT = register(new TuningKey.IntKey("build.walk_speed_spread_percent",
            "In builds that follow a plan, each builder walks to its blocks at its own pace: the normal speed give or take up to this many percent, fixed per soldier (D82); 0 = all the same.", 10, 0, 30));
    public static final TuningKey.IntKey BUILD_WALK_SPEED_PERCENT = register(new TuningKey.IntKey("build.walk_speed_percent",
            "How fast builders walk while they work for a build (to their blocks, the easel and their chest), in percent of their normal pace; 100 = normal.", 100, 50, 300));
    public static final TuningKey.IntKey WORK_FALL_DAMAGE_PERCENT = register(new TuningKey.IntKey("work.fall_damage_percent",
            "Fall damage to a soldier that is working (building, digging, farming), in percent of vanilla's (a fall of 4 blocks costs half a heart). Upstream multiplies every environmental damage by 100, so a 4-block drop kills; builders working on upper floors fell off and died on every tall build. 10000 restores that.", 100, 0, 10000));
    public static final TuningKey.IntKey WORK_PLANT_DAMAGE_PERCENT = register(new TuningKey.IntKey("work.plant_damage_percent",
            "Damage to a soldier that is working from touching a sweet berry bush or a cactus, in percent of vanilla's (half a heart a touch). Upstream multiplies every environmental damage by 100, so brushing a bush killed a builder at once (hospital L3, D81). 10000 restores that.", 100, 0, 10000));
    public static final TuningKey.IntKey SOLDIER_BOSS_FROM_EGGS = register(new TuningKey.IntKey("soldier.boss_from_eggs",
            "1 = a clay soldier that picks up an egg turns into a random clay boss that attacks everyone (the original mod's feature); 0 = eggs are left alone. Off: chickens lay eggs anywhere, and a boss killed the king in the owner's stage 4 test (D94).", 0, 0, 1));
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
    public static final TuningKey.IntKey SCAFFOLD_COLUMN_REACH_BONUS = register(new TuningKey.IntKey("scaffold.column_reach_bonus",
            "A builder inside a scaffolding column may place a block this many more above its feet than usual, only for blocks no column reaches the usual way (a spire top above a floor builders can't walk to; D78).", 2, 0, 4));
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
