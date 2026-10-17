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
| `kingdom.keep.level` | 1 | 1–3 | Which detail level of the keep (`csr:keep`) a new kingdom builds: 1 = the keep tower (the throne stands in the king's chamber), 2 = with the great hall and its throne, 3 = the whole castle. Read when the keep is planned (stage 4). |
| `kingdom.keep.search_radius` | 12 | 0–64 | How far from where the kingdom was founded the middle of its keep may be; the nearest flat ground wins. |
| `kingdom.keep.max_height_range` | 3 | 0–16 | The most the ground under a keep site (with the easel, chest and a margin of 1) may rise and fall; the kingdom levels it when it starts the keep. |
| `kingdom.keep.builders` | 3 | 1–16 | How many of the kingdom's soldiers build its keep: soldiers of the team in the claim with no job, not the king, nearest to the keep's chest first (wheat holders first); the kingdom hands the others wheat so they can work. |
| `kingdom.keep.scaffold_stock` | 128 | 0–1024 | Scaffold material the kingdom asks for when its keep is taller than builders reach (carried back when the scaffolding comes down). |
| `kingdom.keep.check_ticks` | 20 | 1–1200 | How often a kingdom looks after its keep: stocks the builders' chest from the stockpile, picks builders, notices it is finished. |
| `kingdom.keep.plan_retry_ticks` | 600 | 20–72000 | How long a kingdom waits before trying again to place its keep when it found no site. |
| `kingdom.king_bonus_health` | 80 | 0–1000 | Extra health a king gets once his keep is built (a clay soldier has 20). |
| `kingdom.throne_walk_ticks` | 1200 | 0–72000 | How long a king walks toward his throne before his soldiers carry him there; 0 = never. |
| `kingdom.found_pick_tolerance` | 10 | 0–100 | A new king (a soldier who picked up a gold ingot) picks his kingdom's spot at random among the usable keep sites near him scoring at most this many points below the best (0 = always the best). |
| `kingdom.found_walk_ticks` | 1200 | 100–72000 | How long a new king walks toward the spot he picked before he picks another. |
| `kingdom.found_tries` | 5 | 1–100 | Spots a new king tries in a row (out of reach, founding refused, no site) before he rests. |
| `kingdom.found_retry_ticks` | 1200 | 20–72000 | How long a new king rests after `kingdom.found_tries` spots didn't work out before he looks again. |
| `kingdom.keep.free_materials` | 1 | 0–1 | 1 = when a kingdom starts its keep, everything the keep needs goes into its stockpile (until soldiers gather materials, stage 5); 0 = the materials must be supplied. |
| `economy.plot.outer_reach` | 16 | 0–64 | How far beyond its claim a kingdom may put its outer buildings (lumber camp, fields), in blocks: they stand in a ring just outside where the wall will be; houses stay inside the claim. |
| `economy.plot.gap` | 3 | 0–16 | Free blocks between a new building's plot (with its easel and chest) and every building already there (room for a path). |
| `economy.plot.max_height_range` | 3 | 0–16 | The most the ground under a plot after the keep may rise and fall, in blocks; the kingdom levels it when it starts the building. |
| `layout.enabled` | 1 | 0–1 | 1 = when its keep is finished a kingdom draws up its master plan (stage 6: streets, house plots, a straight wall with gates, outer plots) and builds on it; 0 = buildings go on the nearest free ground (stage 5). |
| `layout.scan_radius` | 192 | 32–256 | How far around its keep a kingdom looks at the land for its master plan, in blocks (only loaded ground counts). |
| `layout.road_width` | 3 | 1–7 | Width of the main street and the cross street (odd; an even number is rounded up). |
| `layout.square_depth` | 5 | 1–16 | Depth of the open square in front of the keep's door. |
| `layout.square_width` | 7 | 1–31 | Width of that square. |
| `layout.plot_gap` | 2 | 0–16 | Free blocks between two plots of the master plan (and between a plot and the keep). |
| `layout.wall_setback` | 3 | 1–16 | Free blocks between the wall and the plots inside it. |
| `layout.wall_shift` | 6 | 0–16 | The most a wall side moves out from the town to stay off water, cliffs and caves. |
| `layout.outer_band` | 24 | 4–64 | How far outside the wall outer buildings (lumber camp, fields, the fishing plot) may stand. |
| `layout.later_plots` | 4 | 0–16 | Extra house plots (the kingdom's first house again) along the streets kept free for houses built later. |
| `layout.roster` | 1 | 0–1 | 1 = the master plan keeps a plot for every Studio building the kingdom adds later (inner and outer, not street decoration, at its top level's size), so the wall is sized for the whole town; 0 = only its own list and the extra house plots. |
| `layout.show_seconds` | 30 | 0–300 | How long players within 128 blocks see a kingdom's plan in particles when it is drawn up (white sparks: wall, flames: gates, green: building plots, orange: kept for later), and a building's plot (green) when the kingdom starts it; 0 = only through the dev book. |
| `layout.lay_roads` | 1 | 0–1 | 1 = the planned roads are laid at once as dirt paths when the plan is drawn up (soldiers don't lay roads yet); 0 = only planned. |
| `layout.bridge_max_length` | 8 | 0–32 | The longest stretch of water a laid road bridges with spruce planks, with land at both ends (a stream, a pond, not the sea); 0 = no bridges (D114). |
| `economy.woodcutters` | 2 | 0–16 | How many idle soldiers become woodcutters at a finished lumber camp (picked while nothing is being built, so builders come first). |
| `economy.farmers` | 1 | 0–16 | How many idle soldiers become farmers at a finished field (picked while nothing is being built). |
| `economy.lumber.radius` | 24 | 4–64 | How far from its lumber camp's easel a woodcutter looks for trees, in blocks. |
| `economy.lumber.max_trunk` | 9 | 1–16 | The tallest trunk a woodcutter fells, in logs (its top must be within a builder's reach of the ground); taller trees are left alone. |
| `economy.lumber.chop_ticks` | 20 | 1–400 | How long a woodcutter chops each log, in ticks. |
| `economy.lumber.walk_ticks` | 600 | 40–12000 | How long a woodcutter, farmer or quarryman walks toward a tree, field cell or block to dig before leaving it alone for a while, in ticks. |
| `economy.farm.tend_ticks` | 40 | 0–2400 | How long a farmer tends a growing crop to make it grow one stage, in ticks (0 = never tends: crops grow at vanilla speed only). |
| `economy.farm.carry` | 8 | 1–64 | How much of its harvest a farmer carries before it brings it to the field's chest. |
| `economy.food_per_building` | 9 | 0–640 | Wheat a building the kingdom pays for (the cottage) costs: the builders' meals, taken when it starts; it waits until the stockpile has it. |
| `economy.meal.ticks` | 6000 | 0–72000 | How long a meal keeps the builders of a building the kingdom paid food for well fed, in ticks; eaten again when it wears off while they still build it (0 = no meals). |
| `economy.meal.speed_level` | 1 | 0–3 | Speed level a meal gives (1 = Speed I, 20% faster walking; 0 = none). |
| `economy.meal.health_level` | 1 | 0–5 | Health Boost level a meal gives (each level adds 4 health, 2 hearts; 0 = none). |
| `economy.free_starter_buildings` | 1 | 0–1 | 1 = the kingdom's starter buildings after the keep (lumber camp, field) get all their materials, like the keep; 0 = they must be supplied. |
| `economy.free_other_materials` | 1 | 0–1 | 1 = a building the kingdom pays for with its wood and food gets its other materials (stone, bricks, glass) for free, as no job makes them before stage 9; 0 = a player supplies them through the chest. |
| `economy.renown_per_building` | 25 | 0–1000 | Renown a kingdom earns for every building it finishes after the keep. |
| `economy.quarrymen` | 2 | 0–16 | How many idle soldiers become quarrymen at a finished quarry (picked while nothing is being built). |
| `economy.quarry.pit_size` | 16 | 4–48 | The width of a quarry's pit behind the quarry, in blocks; it is dug in steps, one block smaller per layer, so a pit reaches half its width deep. |
| `economy.quarry.max_depth` | 8 | 1–32 | The deepest a quarry's pit goes below the ground, in layers. |
| `economy.quarry.dig_ticks` | 30 | 1–400 | How long a quarryman digs each block, in ticks. |
| `economy.quarry.carry` | 16 | 1–64 | How much a quarryman carries before it brings it to the quarry's chest. |
| `economy.workshop.crafters_per_level` | 1 | 0–8 | How many idle soldiers become crafters at a finished workshop, for each workshop level. |
| `economy.workshop.craft_ticks` | 100 | 1–2400 | How long a crafter works on one batch at a level 1 workshop, in ticks (divided by the workshop's level). |
| `economy.workshop.batch` | 8 | 1–64 | The most of one item a crafter makes in one batch. |
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
| `terrain.cave_probe_depth` | 4 | 0–32 | How far under the ground the land scan looks for caves and holes (0 = not at all): plots are levelled up to 3 down, so a cave deeper than 4 doesn't matter. The kingdom planner keeps plots off them (D106) and the kingdom fills any under a plot it levels; keep sites and stage 5 plots only report them. |
| `terrain.search.max_candidates` | 81 | 1–1000 | Most candidates one search judges, nearest first (81 = every candidate of the default radius and step). |
| `terrain.grade.cut_fill_budget` | 250 | 0–100,000 | Most ground blocks cut and fill may move (cut + filled). |
| `terrain.grade.foundation_budget` | 300 | 0–100,000 | Most blocks a foundation platform (floor + pillars) may use. |
| `terrain.grade.foundation_max_height` | 6 | 1–32 | Highest a foundation may stand above the lowest ground under it (builders reach 8). |
| `terrain.grade.pillar_spacing` | 4 | 1–16 | Distance between foundation pillars (edges and corners always get one). |
| `terrain.grade.clear_trees_budget` | 500 | 0–100,000 | Most tree blocks clearing a site may remove. |
| `build.unreachable_recheck_ticks` | 200 | 0–1,200 | A blueprint block a builder found out of reach isn't path-searched again by that builder for this long, unless a block is placed or scaffold put up within 3 blocks sideways of it. Keeps request searches cheap when much is out of reach ([D42](DECISIONS.md)). 0: always search. |
| `build.path_range_bonus` | 48 | 0–64 | While a builder works for a build, every path it walks (to a standing spot, back to the easel, to its chest) may be this many blocks longer than its follow range (16) allows. Combat and paths outside building work keep the normal limit; scaffold builds use the larger of this and `scaffold.path_range_bonus` ([D61](DECISIONS.md), [D62](DECISIONS.md)). |
| `build.stranded_ticks` | 1200 | 0–100,000 | A builder that has found no way to where it is going for this many ticks in a row (no path, or one ending short) is put on a free spot beside its chest, like a player carrying it down: stranded on a roof or upper floor it built, whose only way down is a deadly drop ([D72](DECISIONS.md)). 0 = never. |
| `build.path_nodes_multiplier` | 4 | 1–16 | While a builder works for a build, its path searches may look at this many times more steps (base 256) ([D61](DECISIONS.md), [D62](DECISIONS.md)). |
| `build.follow_plan` | 1 | 0–1 | 1: builders follow a structure's pre-baked build plan (`data/csr/build_plan`, made by `tools/build_plan.py`): the plan's waves in order, a random block of the current wave (away from blocks other builders carry), the plan's standing spots tried first ([D82](DECISIONS.md)). 0: the blueprint's normal order and the normal standing-spot search. |
| `build.plan_lookahead_clearance` | 4 | 0–16 | In builds that follow a plan: while other builders finish the current wave's last blocks, an idle builder takes a block of the next wave that is more than this many blocks (on every axis) from every block of the current wave still to place and from their planned standing spots, instead of waiting; its stack's follow-ups may too ([D82](DECISIONS.md)). 0: wait for the wave to finish. |
| `build.plan_lookahead_waves` | 3 | 1–16 | In builds that follow a plan: how many waves past the current one the lookahead above may take blocks from (an idle builder, or a builder with a stack following up). A block of a later wave must be clear (`build.plan_lookahead_clearance`) of every block of an earlier wave still to place and of its planned standing spots ([D82](DECISIONS.md)). 1: only the next wave. |
| `build.plan_follow_up_clearance` | 0 | -1–16 | In builds that follow a plan: a builder with a stack ([D79](DECISIONS.md)) may follow up into a block of the next `build.plan_lookahead_waves` waves when it is more than this many blocks from every planned standing spot (and the cell above it) of an earlier wave's block still to place ([D82](DECISIONS.md)). 0: only not on such a spot. -1: only the current wave. (With the idle builders' clearance of 4 around the earlier blocks themselves, stacks almost never got past a wave's end: a wave holds at most 64 blocks, a layer often several waves.) |
| `build.plan_spots_first` | 0 | 0–1 | In builds that follow a plan: 1, a builder walks to the nearest free one of the plan's standing spots for its block and searches only when none can be walked to; 0, it searches the nearest spot it can place from first and uses the plan's spots only when that fails ([D82](DECISIONS.md)). |
| `build.pause_ticks` | 6 | 0–200 | After placing a block, a builder waits a random 0 to this many ticks before asking for the next one, so a crew doesn't move in lockstep ([D82](DECISIONS.md)); builds that follow a plan only. 0 = no pause. |
| `build.walk_speed_spread_percent` | 10 | 0–30 | Each builder walks to its blocks at its own pace: the normal speed give or take up to this many percent, fixed per soldier ([D82](DECISIONS.md)); builds that follow a plan only. 0 = all the same. |
| `build.walk_speed_percent` | 100 | 50–300 | How fast builders walk while they work for a build (to their blocks, the easel and their chest), in percent of their normal pace. 100 = normal. |
| `build.carry_count` | 16 | 1–64 | How many of a block's item a builder takes from its chest at once. After placing one it goes straight to the nearest free block of that item in the same layer it can reach; only when there is none does it walk back to the easel and its chest and put back the rest ([D79](DECISIONS.md)). 1 = one block per trip. |
| `build.carry_max_ticks` | 2000 | 400–100,000 | A builder keeps its block while it keeps getting a block closer to where it is going (its chest, then the block's spot) and gives it up after 400 ticks without getting closer; this is the most it keeps one block in all ([D87](DECISIONS.md)). |
| `work.fall_damage_percent` | 100 | 0–10,000 | Fall damage to a soldier that is working (building, digging, farming), in percent of vanilla's (a 4-block fall costs half a heart). Upstream multiplies every environmental damage by 100, so a 4-block drop kills a soldier; builders fell off upper floors and died on every tall build ([D63](DECISIONS.md)). 10,000 restores upstream's behavior. |
| `work.plant_damage_percent` | 100 | 0–10,000 | Damage to a soldier that is working from touching a sweet berry bush or a cactus, in percent of vanilla's (half a heart a touch). Upstream multiplies every environmental damage by 100, so brushing a bush killed a builder at once; hospital L3 lost all three builders in its garden ([D81](DECISIONS.md)). 10,000 restores upstream's behavior. |
| `soldier.boss_from_eggs` | 0 | 0–1 | 1 = a clay soldier that picks up an egg turns into a random clay boss that attacks everyone (the original mod's feature); 0 = eggs are left alone. Off because chickens lay eggs anywhere and a boss killed the king in the owner's stage 4 test ([D94](DECISIONS.md)). |
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
| `scaffold.path_nodes_multiplier` | 4 | 1–16 | While a build has scaffolding, its builders' path searches may look at this many times more steps (the larger of this and `build.path_nodes_multiplier`). |
| `scaffold.walk_ticks_per_step` | 20 | 5–200 | A builder walking to a scaffold column's base gives up after this many ticks per step of its path, at least 200 ticks ([D76](DECISIONS.md)). |
| `scaffold.column_reach_bonus` | 2 | 0–4 | A builder inside a scaffolding column may place a block this many more above its feet than the usual 8, only for blocks no column reaches the usual way (the church spire top; [D78](DECISIONS.md)). 0 turns it off. |
| `scaffold.path_range_bonus` | 16 | 0–64 | While a build has scaffolding, its builders' paths may be this many blocks longer (vanilla limits a path to the follow range, 16; the larger of this and `build.path_range_bonus`). |
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
