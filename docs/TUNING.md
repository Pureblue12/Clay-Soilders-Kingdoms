# Tuning file

All balancing numbers (costs, radii, rates, caps, unlock thresholds) live in ONE data file, never hardcoded
(DESIGN DECISION PROTOCOL, rule 6, in [`DESIGN.md`](DESIGN.md)).

- **File:** `common/src/main/resources/data/csr/tuning/tuning.json`, read as the data resource `csr:tuning/tuning.json`.
- **Format:** one flat JSON object whose keys are namespaced: `kingdom.*`, `economy.*`, `performance.*`, `events.*`.
  JSON has no comments, so every key is documented here.
- **Code:** `common/.../tuning/Tuning.java` (key definitions with defaults and ranges) and `TuningKey.java`.
  Read a value with `Tuning.KINGDOM_MAX_LOADED.get()`.
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
`dev.paste_undo_depth`.
