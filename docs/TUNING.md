# Tuning file

All balancing numbers (costs, radii, rates, caps, unlock thresholds) live in ONE data file, never hardcoded
(DESIGN DECISION PROTOCOL, rule 6, in [`DESIGN.md`](DESIGN.md)).

- **File:** `common/src/main/resources/data/csr/tuning/tuning.json`, read as the data resource `csr:tuning/tuning.json`.
- **Format:** one flat JSON object whose keys are namespaced: `kingdom.*`, `economy.*`, `performance.*`, `events.*`,
  `terrain.*`, `build.*`, `scaffold.*`, `dev.*`. Values are whole numbers, lists of 10 whole numbers (per tier), or block ids in
  quotes (`scaffold.material`).
  JSON has no comments, so every key is documented here.
- **Code:** `common/.../tuning/Tuning.java` (key definitions with defaults and ranges) and `TuningKey.java`.
  Read a value with `Tuning.KINGDOM_MAX_LOADED.get()`. The terrain keys are read together through
  `SiteRules.current()` (or `SiteRules.from(values)` in tests).
- **Missing key:** its default is used. That is not an error.
- **Bad value** (wrong type, out of range, wrong number of tiers): reported and the default is used.
  Problems go to the game log (`CSR tuning: ...`) and appear in `/csr dev tuning reload`.
- **Unknown key:** reported as a probable typo and ignored.
- **When it's read:** when data packs load (world start, `/reload`) and on `/csr dev tuning reload`
  (book → Tuning → "Reload tuning file").
- **Showing a value:** `/csr dev tuning show <key>` (book → Tuning → one button per key) shows the value, whether it
  came from the file, the default and the allowed range.
- **Tiers:** there are `Tuning.TIERS` = 10 kingdom tiers, a design constant. Per-tier keys are lists of exactly 10
  numbers, tier 1 first.

## Editing without recompiling

- **During development:** edit `common/src/main/resources/data/csr/tuning/tuning.json`, run
  `./gradlew :neoforge:processResources` (copies resources only, compiles nothing; seconds), then click
  "Reload tuning file". The running game reads the mod's resources from `neoforge/build/resources/main`, which that
  task refreshes.
- **In any world:** a data pack can override the file. Put it at
  `<world>/datapacks/<name>/data/csr/tuning/tuning.json` (with a `pack.mcmeta`), then `/reload` or "Reload tuning
  file". The top pack's file replaces the whole file; keys it leaves out use their defaults.

## Keys

| Key | Default | Allowed | What it does |
|---|---|---|---|
| `kingdom.claim_radius` | 32 | 4–256 | Radius of the land a new kingdom claims around its king. Read when a kingdom is founded and stored in it: changing it affects only kingdoms founded afterwards. |
| `kingdom.chronicle_max` | 100 | 5–1000 | How many chronicle lines a kingdom keeps; the oldest are dropped. After lowering it, a kingdom is trimmed when it gets its next line. |
| `kingdom.found_search_radius` | 16 | 1–64 | How far `/csr kingdom found` looks for your king. |
| `kingdom.census_interval_ticks` | 100 | 20–12000 | How often population and the king's position are updated (population only while the whole claim is loaded). Used on the next tick after a reload. |
| `kingdom.max_loaded` | 4 | 1–64 | How many kingdoms are kept loaded (simulated) at once ([D1](DECISIONS.md)). |
| `kingdom.tier_unlock_renown` | 0, 100, 250, 500, 900, 1400, 2100, 3000, 4200, 6000 | 10 × 0–1,000,000 | Renown needed to reach each tier, tier 1 first. **Placeholder.** |
| `kingdom.zone.keep_radius` | 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 | 10 × 1–256 | Radius of the keep zone per tier, in blocks. **Placeholder.** |
| `kingdom.zone.inner_radius` | 16, 20, 24, 28, 32, 36, 40, 44, 48, 52 | 10 × 1–256 | Outer edge of the inner zone per tier. **Placeholder.** |
| `kingdom.zone.wall_radius` | 20, 24, 28, 32, 36, 40, 44, 48, 52, 56 | 10 × 1–256 | Distance of the wall ring from the keep per tier. **Placeholder.** |
| `kingdom.zone.outer_radius` | 32, 38, 44, 50, 56, 62, 68, 74, 80, 86 | 10 × 1–512 | Outer edge of the outer zone (fields, camps) per tier. **Placeholder.** |
| `performance.soldier_cap_per_tier` | 8, 12, 16, 20, 26, 32, 40, 48, 56, 64 | 10 × 1–1000 | Most clay soldiers a kingdom may have per tier. **Placeholder** until the stage 7 performance checkpoint. |
| `events.warning_time_ticks` | 1200 | 0–72000 | How long before a raid or event the kingdom is warned, in ticks (20 ticks = 1 s). |
| `terrain.scan_radius` | 24 | 1–64 | How far `/csr dev terrain scan` looks around you (a square of 2r+1 columns). |
| `terrain.keep_size` | 9 | 3–31 | Width of the tier-1 keep's footprint for site checks and grading ([D25](DECISIONS.md)). **Placeholder** until the keep is designed. |
| `terrain.keep_margin` | 1 | 0–8 | Blocks around the keep judged and levelled with it (the site is keep + 2 × margin wide: 11). |
| `terrain.refuse.height_range` | 8 | 1–64 | Highest minus lowest column of a site at or above this refuses it as too steep (builders reach 8 up). |
| `terrain.refuse.slope_percent` | 75 | 1–1000 | Slope (percent along the steeper axis; 100 = 1 up per block) at or above this refuses a site as too steep. |
| `terrain.refuse.cliff_step` | 4 | 2–64 | A step this high or higher between neighbouring columns is a cliff and refuses the site. |
| `terrain.refuse.water_depth` | 3 | 1–64 | Water this deep anywhere on the site refuses it. |
| `terrain.refuse.lava_distance` | 4 | 0–32 | Lava on the site or within this many blocks of it refuses it. |
| `terrain.penalty.height_range` | 3 | 0–100 | Score points lost per block of height range. |
| `terrain.penalty.slope` | 2 | 0–100 | Score points lost per 10 % of slope. |
| `terrain.penalty.water` | 4 | 0–100 | Score points lost per 10 % of the site under shallow water. |
| `terrain.penalty.trees` | 2 | 0–100 | Score points lost per 10 % of the site with trees. |
| `terrain.penalty.step` | 4 | 0–100 | Score points lost per 10 % of the site next to a step of 2 or more (below a cliff). |
| `terrain.penalty.biome` | 10 | 0–100 | Score points lost on a wet (ocean, river, beach) or mountain biome. |
| `terrain.search.radius` | 16 | 0–64 | How far the site search looks around the wanted point. |
| `terrain.search.step` | 4 | 1–16 | Distance between candidate sites. |
| `terrain.search.max_candidates` | 81 | 1–1000 | Most candidates one search judges, nearest first (81 = every candidate of the default radius and step). |
| `terrain.grade.cut_fill_budget` | 250 | 0–100,000 | Most ground blocks cut and fill may move (cut + filled). |
| `terrain.grade.foundation_budget` | 300 | 0–100,000 | Most blocks a foundation platform (floor + pillars) may use. |
| `terrain.grade.foundation_max_height` | 6 | 1–32 | Highest a foundation may stand above the lowest ground under it (builders reach 8). |
| `terrain.grade.pillar_spacing` | 4 | 1–16 | Distance between foundation pillars (edges and corners always get one). |
| `terrain.grade.clear_trees_budget` | 500 | 0–100,000 | Most tree blocks clearing a site may remove. |
| `build.unreachable_recheck_ticks` | 200 | 0–1,200 | A blueprint block a builder found out of reach isn't path-searched again by that builder for this long, unless a block is placed or scaffold put up within 3 blocks sideways of it. Keeps request searches cheap when much is out of reach ([D42](DECISIONS.md)). 0: always search. |
| `build.path_range_bonus` | 16 | 0–64 | A builder's path searches to blueprint blocks may be this many blocks longer (vanilla limits a path to the follow range, 16, measured from where the builder asks, usually by its chest). Walking to storage, combat and other paths keep the normal limit; scaffold builds keep `scaffold.path_range_bonus` ([D61](DECISIONS.md)). |
| `build.path_nodes_multiplier` | 4 | 1–16 | A builder's path searches to blueprint blocks may look at this many times more steps (the larger of this and `scaffold.path_nodes_multiplier` while a build has scaffolding) ([D61](DECISIONS.md)). |
| `scaffold.columns_first` | 1 | 0–1 | 1: once builders start putting up the scaffolding, they put all of it up before working above reach (no stall at every level); 0: one level at a time whenever nothing else is in reach ([D42](DECISIONS.md)). |
| `scaffold.start_after_stuck_ticks` | 100 | 0–6,000 | Builders start putting up scaffolding only after finding nothing in reach for this long with no block placed in between (one builder boxed in for a moment doesn't start it). |
| `scaffold.climb_speed_hundredths` | 30 | 5–100 | How fast builders climb up and down scaffolding columns, in hundredths of a block per tick. |
| `scaffold.material` | `"minecraft:scaffolding"` | a block id | The temporary scaffold block (builders fetch it from storage like any block). Climbed columns (approach 4) need a block soldiers can be inside, like scaffolding; with another block, step pillars are used. |
| `scaffold.refund` | 1 | 0–1 | 1: builders carry scaffold they take down back to storage (cleanup drops it as items); 0: it is used up. |
| `scaffold.approach` | 4 | 1–4 | How builders put up scaffolding: 1 ramp up the outside, 2 step pillars, 3 structure tops plus step pillars, 4 scaffolding columns they climb ([D34](DECISIONS.md)). |
| `scaffold.max_blocks` | 200 | 0–10,000 | Most scaffold blocks one build may plan; a structure needing more stays unfinished (recorded as stuck). |
| `scaffold.replan_ticks` | 200 | 20–12,000 | How often a stuck build checks again whether it needs (more) scaffolding. |
| `scaffold.sweep_ticks` | 100 | 20–12,000 | How often scaffold of builds whose easel is gone or has no plan is cleaned up. |
| `scaffold.request_stale_ticks` | 600 | 100–12,000 | A scaffold request a builder has had this long can go to another builder. |
| `scaffold.path_nodes_multiplier` | 4 | 1–16 | While a build has scaffolding, its builders' path search may look at this many times more steps. |
| `scaffold.path_range_bonus` | 16 | 0–64 | While a build has scaffolding, its builders' paths may be this many blocks longer (vanilla limits a path to the follow range, 16). |
| `scaffold.repath_ticks` | 20 | 5–200 | While a build has scaffolding, builders plan their route again this often while walking to a block or a column. |
| `scaffold.step_precision_hundredths` | 30 | 5–67 | On scaffold blocks soldiers follow their path precisely (a step counts as reached within this many hundredths of a block of its center; vanilla 67 plus corner cutting). Matters for step approaches 1–3. |
| `scaffold.teardown_stuck_ticks` | 1200 | 100–72,000 | When builders can't take the scaffold down for this long after the structure is finished, it is removed at once. |
| `dev.paste_undo_depth` | 10 | 1–100 | How many structure pastes `/csr dev structure undo` can undo (dev tool). |

`economy.*` has no keys yet; stage 5 adds them.

Adding a key: define it in `Tuning.java` (id, description, default, range), add it to `tuning.json`, and add a
row here. `TuningGameTests.shippedTuningFileIsComplete` fails if the shipped file leaves a key out.

## Candidates to migrate later (not migrated in stage 0)

These numbers are still hardcoded in Java. Move each into the tuning file when its feature is next touched.

| Where | Constant | Value |
|---|---|---|
| `BuilderReach` | `HORIZONTAL_REACH` / `MAX_PLACE_HEIGHT` / `MAX_PLACE_DEPTH` | 2.5 / 8 / 2 |
| `BuildBlueprintGoal` | `MAX_CARRY_TICKS` / `RETRY_COOLDOWN_MIN` / `RETRY_COOLDOWN_RANDOM` / `MAX_LAST_STEP_TICKS` | 400 / 20 / 20 / 20 |
| `BlueprintBlockInfo` | `STALE_REQUEST_TICKS` / `PLACE_FAIL_COOLDOWN_TICKS` / `MAX_PLACE_FAIL_COOLDOWN_TICKS` | 600 / 100 / 1200 |
| `UseDoorGoal` | `OPEN_DISTANCE` / `LEAVE_DISTANCE` / `CLOSE_WAIT_TICKS` / `STUCK_TICKS` / `IDLE_TICKS` / `LOOK_AHEAD` | 1.5 / 3 / 60 / 100 / 40 / 2 |
| `BuildSitePlanner` | `EASEL_SPACING` | 8 |
| `FindNearestPoiGoal` | `SEARCH_RANGE` / `VERTICAL_SEARCH_RANGE` / `DEFAULT_RANDOM_INTERVAL` | 10 / 4 / 60 |
| `UseAssignedPoiGoal` | `MAX_WAIT_BEFORE_NEXT_ATTEMPT` | 200 |
| `AbstractWorkGoal` | `MAX_BREAK_TIME` | 20 |
| `BreakCropGoal` | `WAIT_AFTER_BLOCK_FOUND` | 20 |
| `PickUpItemsGoal` | `VERTICAL_SEARCH_RANGE` | 8 |
| `ClaySoldierMeleeGoal` | `COOLDOWN_BETWEEN_CAN_USE_CHECKS` / `COOLDOWN_BETWEEN_CAN_ATTACKS` | 10 / 2 |
| `ClaySoldierRangedAttackGoal` | `DEFAULT_ATTACK_TIME` | 40 |
| `ConfigNeoForge` (server config) | existing upstream settings | (stay in the NeoForge config) |

Not candidates: sizes, render and model numbers, bit flags, item stack limits, and dev-tool limits other than
`dev.paste_undo_depth`. The test terrains' shapes (`TestTerrain`: radius 13, a 6-block cliff, a 4-deep lake, ...) are
test fixtures, not balancing numbers, and stay in code; so does the scan's 64-block walk limit. Likewise the test
structures (`TestStructures`), the climber's speed (0.2 blocks per tick) and walk limit (200 ticks), the scaffold
planner's region margin (4) and path margin (8), and the soldiers' safe drop (3, vanilla). The planner uses
`BuilderReach`'s constants (still to migrate, below) so planning and placing share one rule.
