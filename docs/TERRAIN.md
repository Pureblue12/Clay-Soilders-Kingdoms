# Terrain (stage 2 spike)

How a kingdom site handles uneven ground. The spike built a scanner, a scorer, a site search, five grading methods
(applied instantly by a dev tool, with undo) and the terrain check at founding. **Soldiers don't grade yet.**
Prior art: [`RESEARCH-terrain.md`](RESEARCH-terrain.md). Decisions: D25–D32 in [`DECISIONS.md`](DECISIONS.md).
Numbers: `terrain.*` in [`TUNING.md`](TUNING.md).

## Pieces (`common/.../terrain/`)

| Class | Does | World access |
|---|---|---|
| `TerrainScanner` | Columns around a point into a `TerrainScan`: ground, water depth, lava, tree blocks, plants, top, surface block, biome kind, cost. Only loaded chunks; others unknown. | reads loaded chunks |
| `TerrainScan` | The grid of columns (pure data; hand-made in tests). | none |
| `SiteRules` | Every terrain number, from the tuning file. | none |
| `SiteScorer` | Judges the keep site (keep 9 by 9 plus a margin of 1 = 11 by 11): hard refusals and a score from 100 minus penalties, with the measured numbers. | none |
| `SiteSearch` | Candidate sites on rings around a wanted point (step 4, radius 16, at most 81): the best usable one, or the counted reasons. | none |
| `GradingPlanner` | Plans a grading method: chosen site, level, work per column, estimated blocks, budget check. | none |
| `Terrain` | `checkSite` (scan + score + optional search), `grade` (plan + apply with undo), `makeTestTerrain`. Records TERRAIN events. | changes blocks (dev) |
| `TestTerrain` | Builds the test terrains: slope, hill, cliff, lake edge, forest, lava pool, plateau. | changes blocks (dev, tests) |

**Founding** (`Kingdoms.found`) judges the site centered on the king:
- refused with the reasons and the best site nearby on a cliff, lava, deep water or too steep ground;
- founded with a warning on soft penalties;
- unchanged (no check, recorded) when part of the site isn't loaded;
- `/csr kingdom found force` (operators) skips the check.

## Measurements

Scan cost (GameTest server, `scanCostStaysWithinBudget`): radius 64 = 16,641 columns in **1.5 ms** on average; the
forest terrain, radius 13 (729 columns), in 0.07 ms. Budget proposed and tested: **50 ms for radius 64**. A founding
check scans radius 9 (361 columns); a refused founding also searches (radius 25, 2,601 columns), well under 1 ms.

Scores at each test terrain's center (keep site 11 by 11):

| Terrain | Result |
|---|---|
| Slope (1 up per 2 blocks) | score 75: uneven 5 blocks (-15), slope 50 % (-10) |
| Hill (9 high, 1 up per block) | refused: too steep (slope 96 %, 9 blocks) |
| One-sided cliff (6 high) | refused: a cliff, a 6-block step |
| Lake edge (1 deep, then 4 deep) | refused: deep water, 4 blocks |
| Forest (oaks every 4 blocks) | score 92: trees on 44 % (-8) |
| Lava pool (3 by 3) | refused: lava on the site |
| Plateau (13 by 13 top) | score 100 on top; its edge: refused, a cliff |

## Grading methods compared (keep site 11 by 11 = 121 columns)

Block counts are planned at each terrain's center (cut + fill = earth moved; "trees" = logs and leaves removed).
✗ = refused by the method's limit.

| Method | What it looks like | Slope | Hill | Cliff | Lake edge | Forest | How soldiers would do it | Risks |
|---|---|---|---|---|---|---|---|---|
| (a) Flattest site | Nothing changes; torches mark the keep's corners where the flattest usable site is | stays (nothing flatter fits in the test area) | 8 blocks away (range 3) | 8 blocks away (flat) | 8 blocks away | 8 blocks away | Nothing to build; the planner just picks the spot | The keep may sit away from where the king stood; in rough land there may be no usable site |
| (b) Cut and fill | A flat dirt pad at the median height; the high side cut away, the low side filled | 165 (66 cut, 99 fill) | 221 (85 cut, 136 fill) | ✗ 330 > 250 | 154 fill (water filled) | 0 earth, 132 trees | Diggers cut top-down (like dig-hole work), builders fill bottom-up from a blueprint of dirt layers; all within 2 blocks of where they stand. Cut earth can feed the fill. | Bare dirt walls on the cut side; cutting next to water lets water flow in; costs grow fast with height |
| (c) Foundation | A cobblestone floor at the highest ground, on pillars every 4 blocks; nothing cut | 123 | ✗ 9 high > 6 | 95 | 79 (pillars in the water) | 0, 132 trees | Builders place pillars, then the floor from the high side outward, standing on what they built; the floor is at most 6 above the ground, inside the 8-block reach; no digging; doors don't matter (outdoors) | Needs stone from the stockpile (none of it comes from the site); looks like stilts on steep sides; mobs can walk under |
| (d) Clear trees | Logs and leaves on the site removed; the ground untouched | — | — | — | — | 132 trees (54 columns) | Woodcutters (stage 5) fell the trees; leaves above 8 blocks are out of builders' reach | Needs woodcutters first; leaves of trees outside the site are cut in half |
| (d) Avoid trees | Nothing changes; torches mark the best site without trees | stays | 8 blocks away | 8 blocks away | 8 blocks away | 8 blocks away (the clearing) | Nothing to build | In a big forest there may be no clearing within 16 blocks |

Every method was applied in GameTests and undone exactly (`TerrainGradingGameTests`).

## Recommendation (for stage 4; needs the owner's OK where it changes what players see)

1. **Keep the founding refusals as built** (cliff, lava, deep water, too steep), with the best site nearby in the
   message. Refusing is the cheapest terrain strategy and the message tells the player what to do.
2. **The stage 4 keep planner picks a site in this order:**
   1. **No work.** Search the claim (as `SiteSearch`); if a site with score 80 or more and a height range of 1 or
      less is within reach, build there. On plains and in forests with a clearing this is the common case.
   2. **Small unevenness (range 2 to 3): cut and fill.** The cut earth roughly balances the fill, it needs only dirt,
      and "levelling the ground" is a visible job for diggers and builders, which is good for an observer. About 100
      to 170 blocks for the keep site.
   3. **One-sided slopes, cliff edges, water edges (range 4 to 6): a foundation.** Builders only place blocks, all
      within reach; about 80 to 125 cobblestone from the stockpile. This is the vanilla mansion's approach.
   4. **Steeper or higher: refuse or move** (the flattest site nearby). Don't level hills; the GDMC competition's
      main lesson is that flattening big areas looks wrong.
   5. **Trees: work around them until woodcutters exist** (stage 5). After that, clear them: the logs go to the
      stockpile, so tree clearing becomes a producer in the economy's flow audit.
3. **Small change to the keep design (approved by the owner, D33; built in stage 4):** give the keep a 1-block stone plinth as its bottom layer, and add a
   `ground_level` field to the structure metadata (MineColonies' idea: which layer sits on the ground). The keep can
   then absorb ±1 block without any grading. Keep the tier-1 footprint at 9 by 9 or less: grading cost grows with
   area times height. Face the keep's door toward the lowest or flattest side.
4. **Later structures:** houses and storehouses use the same pipeline with their own footprint (smaller, so cheaper).
   Roads and fields follow the ground (vanilla's `terrain_matching` idea) and are never graded. Walls follow the
   terrain with steps, and a wall segment on a cliff is skipped.
5. **Let "too steep" become "needs grading" (open question for stage 4):** today a hill that cut and fill could level
   within budget (221 blocks) is still refused at founding. Once soldiers can grade, founding could accept a site
   when a grading method is feasible.

## What the spike didn't cover

- Caves and overhangs under a site (the scan is heightmap-based; a cavern under the keep isn't seen).
- Frozen lakes (ice counts as ground; the water under it isn't measured).
- Lava just outside the loaded area (not seen, D28).
- Water flowing into cut areas next to real lakes (the test lake is contained).
- Real worlds: only the GameTest superflat and the built test terrains were measured.
