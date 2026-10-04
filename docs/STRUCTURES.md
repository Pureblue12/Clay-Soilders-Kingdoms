# Structures (DRAFT)

> **DRAFT.** Master list and design briefs for every kingdom structure. No structure files exist for these yet; only
> the three test structures are ready (see [Pipeline reference](#pipeline-reference) at the end). Scale decision:
> [D43](DECISIONS.md). Block counts and build times are **estimates (about ±25 %)** until the files are made.
> Ideas from real towns and builds are used as references only; no build is copied.

## Contents

1. [Scale and detail rules](#scale-and-detail-rules)
2. [Tier table](#tier-table)
3. [Tier perks (draft)](#tier-perks-draft)
4. [Town styles](#town-styles-choose-one)
5. [Build order for the generation batches](#build-order-for-the-generation-batches)
6. [Open questions](#open-questions-for-the-owner)
7. [Buildability checklist](#buildability-checklist)
8. [Design briefs](#design-briefs): keep tiers, housing, food and wood, industry, civic and care, defense and
   military, wall modules, infrastructure, small props
9. [Budget overview](#budget-overview)
10. [Pipeline reference](#pipeline-reference) (structure files, metadata, paste tool, touch-up workflow)

## Scale and detail rules

Every brief follows these rules ([D43](DECISIONS.md)).

1. **Miniature scale.** Soldiers are 0.25 scale (about 0.5 blocks tall, 0.15 wide), so the town is a **tiny world**:
   roughly half the size of a player's build.
   - **Common buildings:** small footprints (a cottage is about 5 by 7), walls 3 to 4 blocks high, roofs add 2 to 3.
   - **Detail** comes from small blocks: slabs, stairs, trapdoors, walls, fences, buttons, carpets, candles, lanterns,
     flower pots, banners, signs.
   - **Doors:** a vanilla door is 2 blocks tall, so a room with a door needs walls of at least 3, or an arched
     opening without a door. Soldiers open wooden doors (`UseDoorGoal`), not iron doors or fence gates.
   - **Soldier-sized doorway variant:** a 1-block-tall opening with no door (a trapdoor or slab lintel above it).
     It suits half-scale houses, but it is **UNTESTED** with the door and pathing code (vanilla pathfinding treats
     every mob as at least 1 block tall, so it should pass, but it has never been tried). Players can't walk through
     it.
   - **Stairs, not ladders:** soldiers don't climb ladders (vanilla pathfinding never plans climbing), so every upper
     floor a soldier must reach has a staircase. Ladders are decoration only.
2. **Landmarks may be larger:** the keep from tier 4 up, the church and cathedral, the town hall, the main gate and a
   few towers.
   - **Size rule:** up to **28 blocks tall** (the tallest build tested with scaffolding, [`SCAFFOLDING.md`](SCAFFOLDING.md))
     and up to about 49 by 49 for the tier-10 fortress.
   - **Anything taller is flagged UNTESTED** (⚠). Anything over 9 tall needs scaffolding: builders need a free
     1-block strip along at least one outside face, and storage within about 14 blocks' walk.
3. **Three detail layers** per structure:
   - **SHELL:** walls, floor, roof, door.
   - **FITTINGS:** windows, stairs, beds, hearth, table, chests, the workstation.
   - **FLOURISH:** shutters, flower boxes, banners, lanterns, signs, chimney, clutter props, wear (moss, cracks).
     Flourish is an **upgrade**: a first build is shell plus fittings, so it finishes fast. **Level 2** and
     **level 3** looks are added later, on top of the standing building, as the kingdom prospers. Proposed unlocks
     ([D44](DECISIONS.md)): level 2 from tier 3, level 3 from tier 6, so kingdoms visibly get richer.
4. **Detail budget.** Each brief gives the block count per layer and the build time with 3 builders.
   - **Within reach:** 22.5 ticks per block (the Small House: 151 blocks in about 3,400 ticks).
   - **Above reach:** about 60 ticks per block, scaffolding trips included (the 20-high wall with corner tower:
     592 blocks in about 28,600 ticks).
   - 20 ticks = 1 second; 1,200 ticks = 1 minute; a Minecraft day is 24,000 ticks.
   - **Limits** (proposed tuning keys, [D45](DECISIONS.md); not in `tuning.json` yet, this is a docs-only change):

     | Proposed key | Value | Applies to |
     |---|---|---|
     | `structure.budget.first_build_blocks` | 200 (about 3.8 min) | Shell plus fittings of a common building |
     | `structure.budget.common_blocks` | 350 (about 6.6 min) | A common building with all its flourish |
     | `structure.budget.landmark_blocks` | 1,800 | A landmark, all layers |
     | `structure.budget.prop_blocks` | 30 | A small prop |
     | `structure.budget.module_blocks` | 60 | One wall module |
     | `structure.max_height` | 28 | Any structure (taller = untested) |

     A brief over a limit is flagged ⚠.
5. **Buildability.** Every block must be placeable by the builder pipeline. Each block type is marked **KNOWN-OK**
   (a test placed it), **UNKNOWN** or **BROKEN** ([checklist](#buildability-checklist)).
   - **No complexity-probe results exist in `docs/` yet**, so the marks come from the tests that exist and from
     reading the placement code ([D46](DECISIONS.md)). The probe will replace them.
   - **BROKEN blocks** get a replacement, or move to a separate decoration step run by code after the build.
6. **Interiors:** tiny furniture sized for soldiers, so citizens have real places to use.
   - Beds are one bed block (or a carpet on a slab).
   - Tables are a fence or wall post under a pressure plate or carpet.
   - Benches are stairs, shelves are trapdoors on a wall, hearths are a campfire or furnace under a chimney.
   - Every workplace has exactly one workstation block.
7. **Ruins:** each structure has a decay look built from the same layers.
   - What decays: walls broken down to stumps, cracked and mossy blocks, fallen pieces, vines; flourish mostly gone.
   - **Open question:** ruins are assumed to be seen from above at miniature scale, so they are designed for their
     outline and rubble rather than for interior detail.
8. **Style.** Early tiers are plain wood and cobblestone. By tiers 5 to 8 they grow into one of two looks (ideas
   only, nothing copied):
   - **A half-timbered medieval town:** plaster walls, dark wood frames, red-brown stair roofs, dormers, chimneys,
     paved streets with lamp posts, a church with a bell tower.
   - **A high-fantasy keep for the top tiers:** pale mixed stone, slender towers with conical gold caps, banners,
     arcades and arched bridges, balconies, on a high, gently sloped site.
9. **Terrain behavior** (every brief names one; the stage 2 recommendation is in [`TERRAIN.md`](TERRAIN.md)):

   | Behavior | Meaning |
   |---|---|
   | `ground_level` | A 1-block stone base layer; the metadata names the layer that sits on the ground; absorbs ±1 block (D33). |
   | `skirt` | `ground_level` plus a cobblestone foundation skirt down to the ground on the low side (slopes up to 4). |
   | `stepped` | The footprint is split into parts that sit at different heights (long halls, walls, terraces). |
   | `flexible` | Follows the ground block by block (fields, roads, props, gardens); never graded. |

## Tier table

What unlocks at each tier (renown thresholds: `kingdom.tier_unlock_renown`, a placeholder). A structure stays
available at all later tiers. **NEW** marks structures added to the owner's list. Tiers of the 16 existing
placeholders ([D12](DECISIONS.md)) are kept, except the storehouse (tier 1 → 3, [D47](DECISIONS.md)).

| Tier | Keep | Housing | Food and wood | Industry | Civic and care | Defense and military | Infrastructure and props |
|---|---|---|---|---|---|---|---|
| 1 | Keep 1: small cobblestone keep | Hovel or tent, cottage, inn / visitors' camp (NEW) | Farm 1, lumber camp | — | Well | — | Dirt road, bench, fire pit, signpost, flower bed, well variants |
| 2 | Keep 2: stone hall, timber palisade | — | Pens and stables, fishing dock, farm 2 | Carpenter or fletcher | Commons 1 (NEW), garden 1 (NEW) | Palisade modules and gate, watchtower | Gravel road, notice board, hitching post |
| 3 | Keep 3: hall, courtyard, gatehouse | Cottage flourish 2 | Butcher, bakery | Smithy, quarry, mine 1, tannery, storehouse | — | Stone wall modules, wall tower, barracks, training yard | Market stall, wooden bridge |
| 4 | Keep 4: two-floor stone keep, corner towers | Townhouse | Tavern or food hall, farm 3 | Stonemason | Market square, chapel, infirmary, graveyard, commons 2 | — | Cobble road, lamp post, banners |
| 5 | Keep 5: castle, curtain walls, bailey | Noble house | — | Mine 2 | Church, library or school, garden 2, herb garden | Gatehouse and gates (stone) | Stone bridge, fountain |
| 6 | Keep 6: great hall, throne room, barracks wing | King's guard quarters, cottage flourish 3 | Tree farm (lumber camp upgrade) | Armory | Town hall, hospital | Siege workshop | Statue |
| 7 | Keep 7: bigger castle, moat or ditch | — | — | Mine 3 | Royal garden, commons 3 | Moat or ditch segment | — |
| 8 | Keep 8: concentric inner and outer ward | — | — | — | Cathedral, garden 3 | — | — |
| 9 | Keep 9: grand castle, many towers, armory | — | — | — | — | — | — |
| 10 | Keep 10: fortress, grand throne hall, dungeon, treasury | — | — | — | — | — | — |

## Tier perks (draft)

What each building unlocks for soldiers ([D48](DECISIONS.md)). Perks only add; nothing is lost when a building is
missing (pillar 3, no punishing mechanics). Clay soldiers get their abilities from the items they hold and wear, so
most perks are **items the kingdom can hand out from the keep's storeroom**. Numbers are placeholders for the tuning
file.

| Tier | Perk | Unlocked by | What soldiers get |
|---|---|---|---|
| 1 | Sticks | Lumber camp | Every soldier can carry a stick (the basic weapon). |
| 1 | A bed and a hearth | Cottage, keep | Rested soldiers work a little longer before their break. |
| 1 | Welcome | Inn / visitors' camp | Visiting soldiers stay and join the kingdom (the population source, D7). |
| 2 | Bare horses | Pens and stables | Mounts for scouts and guards (horses without armor). |
| 2 | Leather armor | Pens and stables + tannery (tier 3) | Leather for soldiers' armor; before the tannery, only for the king's guard. |
| 2 | Sharpened sticks | Carpenter or fletcher | Better sticks, arrows for archers. |
| 2 | Meeting place | Commons 1 | Idle citizens gather there (observer feature, no stat). |
| 3 | Better gear | Smithy | Iron-tipped weapons and shields from mined iron. |
| 3 | Stone and ore | Quarry, mine 1 | Stone for stone buildings; coal and iron (mine 2: gold; mine 3: gems). |
| 3 | Drilled soldiers | Training yard | Recruits train there; trained soldiers fight a little better. |
| 4 | Well fed | Tavern or food hall | Fed soldiers work faster for a while; the tavern also draws visitors faster. |
| 4 | Healing | Infirmary | Wounded soldiers go there and heal slowly (hospital, tier 6: faster, and fallen soldiers can be rebuilt from clay). |
| 4 | Blessing | Chapel | Renown from festivals; church (5) and cathedral (8) add more, and a blessing that slowly heals soldiers near them. |
| 4 | Trade | Market square | Surplus is traded for missing materials. |
| 5 | Knowledge | Library or school | Faster unlocks (renown bonus). |
| 6 | Full armor | Armory | Armor and weapons stored and handed out to the guard; horse armor. |
| 6 | Siege engines | Siege workshop | For later warfare stages. |
| 6 | Order | Town hall | Better layout and work planning (larger build radius); its records feed the kingdom book. |

## Town styles (choose one)

Three directions, all using the same structure list; a style picks palettes, variants and the layout feel.

| | A: Timber hamlet → stone town | B: Compact fortified hill town | C: Riverside market town |
|---|---|---|---|
| **Palette** | Oak and spruce, cobblestone, then white plaster (white terracotta, calcite), dark oak frames, stone bricks, red-brown stair roofs (granite or brick stairs, or spruce) | Stone bricks, andesite, tuff, deepslate trim, slate-dark roofs; pale stone and gold caps at tiers 8 to 10 | Brick and mud brick, light wood (birch, oak), striped awnings (wool), teal and orange accents, gravel and cobble quays |
| **Layout feel** | Loose: houses spread along winding roads that follow the ground; fields around | Dense: houses packed inside walls on rising ground; narrow lanes, stepped terraces; keep at the top | Long: a main street along the water with the market at a bridge; docks and fishing; warehouses |
| **Preferred variants** | Cottage A (gable), timber-frame townhouse, open commons, farm with hedges | Cottage B (stone, steep roof), tall narrow townhouse, wall-integrated houses, stepped walls | Cottage C (brick, overhanging upper floor), market hall, fishing dock, stone bridge, warehouse storehouse |
| **Tier 1** | A small cobblestone keep, two tents, one log cottage, a field, dirt paths | The keep on the highest flat spot, a hovel, a field below it | The keep near the bank, a cottage, a field, a dirt path down to the water |
| **Tier 5** | Plaster and timber cottages around a green with a well, a stone castle with a curtain wall, a chapel, cobble streets | A walled town on the hill with stepped streets, gatehouse and wall towers, a church below the castle | A long market square by a stone bridge, docks, a church facing the river, the castle on the bank |
| **Tier 10** | A half-timbered town around a grand plaza with a statue, a cathedral with a bell tower, a fortress with banners | A concentric white-stone fortress with slender towers and conical gold caps on the hilltop, ring walls below | A busy river town with arcades along the quay, an arched bridge to the fortress, a cathedral by the water |

## Build order for the generation batches

Generate structure files in this order: core first, so stage 4 and 5 can use them as early as possible.

1. **Core (tier 1):** keep 1, hovel or tent, cottage (A, with flourish 2 and 3), inn / visitors' camp, farm 1,
   lumber camp, well, small props (bench, fire pit, signpost, flower bed), dirt road piece.
2. **Tier 2:** keep 2, palisade modules (straight, stepped, corner, gate), watchtower, pens and stables, fishing dock,
   carpenter, commons 1, garden 1.
3. **Industry and defense (tier 3):** keep 3, storehouse, smithy, quarry, mine 1, tannery, butcher, bakery,
   barracks, training yard, stone wall modules, wall tower.
4. **Town life (tier 4):** keep 4, townhouse, tavern or food hall, market square and stall, chapel, infirmary,
   graveyard, commons 2, stonemason, lamp post, cobble road.
5. **Landmarks (tiers 5 to 6):** keep 5 and 6, church, town hall, gatehouse, noble house, library, hospital, armory,
   king's guard quarters, siege workshop, bridges, fountain, statue.
6. **Late (tiers 7 to 10):** keeps 7 to 10, cathedral, moat or ditch, mines 2 and 3, royal garden, commons 3,
   garden 3.
7. **Ruins** of each batch right after it (same layers, decayed).

## Open questions for the owner

These change what you would see. Defaults in brackets are what the briefs assume.

1. **Town style:** A (timber hamlet to stone town), B (fortified hill town) or C (riverside market town)? [A]
2. **Doorways on common houses:** vanilla 2-tall doors (walls of 3 or more) or soldier-sized 1-tall openings with no
   door (more miniature, untested with pathing, players can't enter)? [vanilla doors, 1-tall as a variant]
3. **Flourish unlocks:** by tier (level 2 at tier 3, level 3 at tier 6) or by how prosperous the kingdom is
   (stockpile, happiness)? [by tier]
4. **Keep growth:** rebuild the keep in place at tier 4, 5 and 8 (big visible jumps) or always extend around the old
   one (D4 allows both)? [rebuild at tiers 4 and 8, extend at all others]
5. **Ruins:** pasted at once when the king dies, or the standing buildings decay over time? And are they meant to be
   seen from above? [decay over time, from above]
6. **Water:** builders can't place water. Moats, wells, fountains and docks either get a code step that adds the
   water after the build, or stay dry (a ditch, a dry fountain)? [a code step after the build]
7. **Fields:** farmland can't be placed by builders. Farms are built as dirt and farmers till it (visible work), or
   a code step makes the farmland? [farmers till]
8. **Decoration entities and data:** item frames, armor stands, paintings, banner patterns and sign text can't be
   placed by builders. Add them in a code step after the build, or leave them out? [code step, flourish only]
9. **Taller than 28:** the cathedral spire and the tier 9 to 10 towers want 30 to 34. Allow them as untested, or cap
   everything at 28? [cap at 28]
10. **Gold caps:** real gold blocks (costly, from mines) or gold-looking substitutes (yellow terracotta, honeycomb
    blocks) on the high-fantasy towers? [substitutes; gold blocks as a tier-10 flourish]
11. **Players inside:** should common buildings fit a player (walls of 3 or more everywhere) or be truly
    half-scale with tiny interiors only soldiers use? [walls of 3, interiors soldier-sized]
12. **Dungeon:** builders only place blocks (no digging), so the tier-10 dungeon sits inside a raised plinth rather
    than underground. Fine, or should digging come first? [inside the plinth]

## Buildability checklist

Every block type the briefs use, with its status ([D46](DECISIONS.md)).
- **KNOWN-OK:** a GameTest placed it: the Small House (151 blocks), the placing, door and scaffold tests.
- **UNKNOWN:** not tested yet. **Risk low** means it uses the same placement path as a KNOWN-OK block (an exact block
  state set by the builder, with one item fetched from storage).
- **BROKEN:** read from the code, it can't work as designed.

The complexity probe will replace these marks.

How builders place a block (code read, `ServerBlueprintPlan`):
- They set the blueprint's exact block state, then run the block's own "placed by" step.
- Only the lower half of doors and tall plants, and the foot of beds, are in the plan; the "placed by" step adds the
  other half.
- Blocks are ordered: full blocks first, then other shapes, then blocks with data, then doors.
- Saved block data (sign text, banner patterns, chest contents) is **not** applied.

| Block type | Status | Risk / note |
|---|---|---|
| Cobblestone, cobblestone slab | KNOWN-OK | Small House, placing tests |
| Oak planks, oak slab, oak stairs, oak log, oak wood, stripped oak log and wood, spruce log | KNOWN-OK | Small House |
| Wool (blue, cyan, green, light blue), glowstone, end stone, crimson nylium | KNOWN-OK | Small House |
| Oak door, spruce door | KNOWN-OK | Door tests, Small House; soldiers open wooden doors |
| Spruce trapdoor | KNOWN-OK | Small House |
| Torch, wall torch | KNOWN-OK | `builderPlacesWallTorch` |
| Oak wall sign (the block) | KNOWN-OK | `builderPlacesWallSign`; its text is BROKEN (below) |
| Scaffolding | KNOWN-OK | Scaffold tests (temporary) |
| Other wood species: planks, logs, stairs, slabs, doors, trapdoors, fences | UNKNOWN | Low |
| Stone, stone bricks, mossy and cracked stone bricks, chiseled stone bricks, smooth stone | UNKNOWN | Low |
| Bricks, mud bricks, packed mud, andesite, diorite, granite, tuff, calcite, deepslate bricks and tiles (and polished forms) | UNKNOWN | Low |
| Their stairs, slabs and walls; cobblestone and mossy cobblestone walls and stairs | UNKNOWN | Low; walls and fences change shape to connect (fine) |
| Terracotta (white, yellow, orange, red, brown, light gray), white and yellow concrete | UNKNOWN | Low |
| Quartz block, quartz pillar, quartz stairs, gold block | UNKNOWN | Low |
| Hay bale, bookshelf, barrel, crafting table, furnace, smoker, blast furnace, smithing table, fletching table, loom, cartography table, stonecutter, grindstone, lectern, composter, empty cauldron, anvil | UNKNOWN | Low (anvil: gravity block, needs a floor) |
| Chest | UNKNOWN | Low; contents are never placed (fine: storage starts empty) |
| Glass, glass panes, iron bars | UNKNOWN | Low; panes and bars connect |
| Carpet, moss carpet, pressure plates, buttons, ladders, chains | UNKNOWN | Low (ladders are decoration only) |
| Lantern (standing and hanging) | UNKNOWN | Medium: must be placed after the block it hangs from (other shapes come after full blocks, so it should be) |
| Candles (1 to 4 in a block) | UNKNOWN | Medium: one candle item places the whole cluster |
| Campfire | UNKNOWN | Medium: lit fire hurts soldiers who step in; use an unlit campfire or put it in a hearth |
| Bell | UNKNOWN | Medium: attachment (floor, ceiling, wall) must survive |
| Beds | UNKNOWN | Medium: only the foot is planned; the head is added by the bed's "placed by" step |
| Plain banners (wall and standing) | UNKNOWN | Medium: the plain banner places; patterns are BROKEN (below) |
| Single flowers, ferns, sweet berry bushes, short grass | UNKNOWN | Medium: need dirt or grass under them |
| Tall flowers, tall grass, large ferns | UNKNOWN | Medium: two halves, like doors |
| Leaves (oak, azalea, flowering azalea) | UNKNOWN | Medium: must be saved as persistent, or they decay |
| Vines, glow lichen | UNKNOWN | Medium: need a wall behind them |
| Gravel, sand, red sand, concrete powder | UNKNOWN | Medium: gravity blocks fall if nothing is under them |
| Grass block, dirt, coarse dirt, rooted dirt, mud, moss block | UNKNOWN | Low |
| Empty flower pot | UNKNOWN | Low |
| Iron door, fence gates | BROKEN for passage | They place, but soldiers can't open them (DOORS.md): only as decoration or to lock a room (treasury, dungeon). Replacement in walkways: an opening or a wooden door. |
| Water, lava, fire | BROKEN | No item to fetch. Water: a code step after the build (open question 6); lava and fire: never used. |
| Potted plants | BROKEN | No item (the potted block isn't the flower pot item). Replacement: empty pot; flowers in the decoration step. |
| Farmland | BROKEN | No item, and it turns back to dirt. Replacement: dirt, tilled by farmers (open question 7). |
| Dirt path | BROKEN | No item. Replacement: coarse dirt, packed mud, gravel. |
| Water cauldron | BROKEN | No item. Replacement: empty cauldron. |
| Sign text, banner patterns, decorated pot designs | BROKEN (data) | Saved block data isn't applied. Plain block now; data in the decoration step (open question 8). |
| Item frames, paintings, armor stands | BROKEN | Entities, not blocks: builders never place them. Decoration step only. |

**UNKNOWN or BROKEN blocks used by the briefs** (the list the probe should test first): lanterns, candles, campfire,
bell, beds, banners, flowers and tall plants, leaves, vines, gravity blocks, iron doors, fence gates, water, potted
plants, farmland, dirt path, sign text, banner patterns, item frames, armor stands. Every brief keeps BROKEN blocks
out of its shell and fittings; they appear only in flourish, marked "(decoration step)".

## Design briefs

Format of every brief: tier, zone and role, then size, look, inside, the blocks of each detail layer, the budget, the
flow audit (producer → carrier → store → consumer), perks, variants, ruin, terrain behavior and buildability watch
points. "(decoration step)" marks BROKEN blocks added by code after the build, if the owner agrees (open question 8).

### Keep tiers 1 to 10

The keep is the kingdom's heart and landmark.
- **Storeroom:** where economy output is delivered (carriers bring harvests, logs, stone and ore there). It counts in
  the stockpile, and perks' items are handed out from it.
- **Treasury:** gold and valuables.
- **Throne room:** where the king holds court.
- **King's quarters:** where he sleeps.

From tier 5 up, curtain walls and moats are built from the [wall modules](#wall-modules-new) as separate builds, so a
tier upgrade is a series of small builds rather than one huge one; the keep's own budget below excludes them.
Upgrades extend the old keep except at tiers 4 and 8, where it is rebuilt (open question 4). All keeps stand on a
stone base layer (D33) and face their door to the lowest side.

#### Keep, tier 1: small cobblestone keep (`csr:keep`)
Tier 1 · zone keep · keep; the king, 1 to 2 guards

- **Size:** 9 × 9 (D25), 9 tall. **Needs scaffolding:** no.
- **Look:** A squat cobblestone tower-house: walls 5 high above the base layer, a flat roof with a crenellated parapet (cobblestone walls on every other block), one oak door, arrow-slit windows (trapdoors). Signature: a torch-lit doorway under a slab hood and a corner turret 2 blocks taller.
- **Inside:** One room (7 × 7) and a roof terrace by a stair. See the floor plan below.
- **Blocks per layer:** Shell: cobblestone (base layer, walls), cobblestone slab (roof), cobblestone wall (crenels), oak door, oak planks (floor). Fittings: spruce trapdoor (window shutters inside), oak stairs (throne, roof stair), barrel and chest (storeroom), bed, fence + pressure plate (table), furnace (hearth), torches. Flourish L2: plain banner over the door, lanterns, carpet runner to the throne, mossy cobblestone patches, flower pots (empty). Flourish L3: banner patterns (decoration step), stone brick trim course, candles on the table, a second turret.
- **Budget:** Shell 300 · Fittings 30 · Flourish L2 +30 · L3 +40 = **400** blocks. First build (shell + fittings, 330): ~7,400 ticks (6.2 min); full: ~9,000 ticks (7.5 min).
- **Flow:** Producer: none (store). Carrier: every carrier delivers here. Store: the storeroom (counts in the stockpile). Consumer: perk hand-outs, building materials for builders.
- **Perks:** The kingdom exists; sticks are handed out once the lumber camp delivers.
- **Variants:** A: plain cobblestone. B: log-framed (oak log corners). C: half-sunk on slopes (stepped entrance).
- **Ruin:** Walls broken to 1 to 3 high, roof gone, mossy cobblestone, a vine-covered corner, the turret standing alone.
- **Terrain:** `ground_level` (base layer, D33); `skirt` up to 4 on one side.
- **Buildability watch:** beds, lanterns (UNKNOWN medium).
- **Floor plan:**
  - Ground floor (7 × 7 inside): throne (oak stairs on a 1-high dais) against the back wall facing the door.
  - The **storeroom** corner by the door: 2 barrels and 2 chests behind a fence rail, so carriers drop off without crossing the room.
  - The **king's quarters** in the other back corner: bed, chest, carpet. A hearth (furnace) on a side wall, a small table.
  - Roof (via an oak stair along the inside wall): lookout terrace, turret.

#### Keep, tier 2: stone hall with timber palisade (`csr:keep`)
Tier 2 · zone keep · keep; king, 2 to 3 guards

- **Size:** 11 × 9 hall (the old keep becomes its tower end), 8 tall. **Needs scaffolding:** no.
- **Look:** The tier-1 tower gets a stone hall attached: stone brick lower walls, oak-plank upper walls, a spruce stair roof. A timber palisade ring (palisade modules) encloses the keep zone. Signature: the old tower now has a pitched cap; a carved door frame (stripped logs).
- **Inside:** Hall with throne, a separate storeroom and king's chamber. See the floor plan below.
- **Blocks per layer:** Shell: stone bricks, cobblestone (base), oak planks, spruce stairs and slabs (roof), oak door, oak log frame. Fittings: oak stairs (throne, benches), fence + carpet tables, barrels, chests, bed, furnace hearth with cobblestone chimney, torches, glass panes. Flourish L2: banners, lanterns on chains, shutters (trapdoors), flower boxes (trapdoors + flowers), a sign over the storeroom. Flourish L3: banner patterns and sign text (decoration step), moss on the north side, candles.
- **Budget:** Shell 380 · Fittings 45 · Flourish L2 +40 · L3 +50 = **515** blocks. First build (shell + fittings, 425): ~9,600 ticks (8.0 min); full: ~11,600 ticks (9.7 min).
- **Flow:** As tier 1; the storeroom gets its own door to the yard so carriers don't cross the hall.
- **Perks:** Palisade: the keep zone is enclosed; guards patrol it.
- **Variants:** A: stone and timber. B: all-timber hall (long house). C: stone hall with a lean-to storeroom.
- **Ruin:** The timber upper walls gone, the stone lower walls and tower stump remain, collapsed roof beams (stripped logs on the floor).
- **Terrain:** `skirt` for the hall; the tower keeps its base layer.
- **Buildability watch:** beds, lanterns, chains.
- **Floor plan:**
  - Ground floor: hall (9 × 7) with the throne on a dais at the far end and two benches.
  - **Storeroom** (3 × 4, door to the yard) beside the entrance.
  - **King's chamber** in the old tower (bed, chest, hearth).
  - Tower upper floor (by stairs): lookout.

#### Keep, tier 3: hall with courtyard and gatehouse (`csr:keep`)
Tier 3 · zone keep · keep; king, guards, a steward

- **Size:** 17 × 19 (hall, courtyard, gatehouse), 8 tall. **Needs scaffolding:** no.
- **Look:** The hall gains a walled courtyard (4-high stone walls) and a small gatehouse (5 × 5) with an arched gate and a walkway over it. Signature: the gatehouse arch with a portcullis look (iron bars, decoration only).
- **Inside:** Hall and tower as tier 2, plus a courtyard with a well and a training dummy, and a gatehouse guard room.
- **Blocks per layer:** Shell: stone bricks, cobblestone walls and stairs, oak planks, spruce stairs, oak doors, base layer. Fittings: throne, benches, tables, barrels and chests (storeroom grows to 3 × 6), well (cobblestone ring; water: decoration step), guard room bed, torches. Flourish L2: banners on the gatehouse, lanterns, hay bales and a cart in the courtyard, iron bars portcullis. Flourish L3: banner patterns (decoration step), flower beds in the courtyard, mossy steps, a weather vane (lightning rod).
- **Budget:** Shell 620 · Fittings 60 · Flourish L2 +60 · L3 +70 = **810** blocks. First build (shell + fittings, 680): ~15,300 ticks (12.8 min); full: ~18,200 ticks (15.2 min).
- **Flow:** As tier 2; the courtyard is the carriers' drop-off yard by the storeroom door.
- **Perks:** The gatehouse's guard sees visitors arrive (the inn is near the gate).
- **Variants:** A: square courtyard. B: courtyard on the slope side with steps. C: courtyard with a wooden gallery.
- **Ruin:** Courtyard walls broken in two places, the gatehouse arch still standing, rubble piles (cobblestone and gravel) in the yard.
- **Terrain:** `stepped`: the courtyard and gatehouse may sit 1 to 2 lower than the hall, joined by steps.
- **Buildability watch:** iron bars (UNKNOWN low), well water (BROKEN → decoration step).
- **Floor plan:**
  - Hall ground floor: throne, benches, hearth.
  - **Storeroom** (3 × 6) opening onto the courtyard; **treasury**: one locked chest corner behind an iron door (decoration, nobody needs to enter).
  - Tower: king's quarters above the hall's end.
  - Gatehouse: guard room, stairs to the walkway.

#### Keep, tier 4: two-floor stone keep with corner towers (`csr:keep`)
Tier 4 · zone keep · keep (landmark); king, guards, steward, cook

- **Size:** 13 × 13 (rebuilt on the same site; towers 3 × 3), 16 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** A proper stone keep: two floors, 14 high, with four round-looking corner towers (3 × 3 with corner stairs) rising to 16 with crenellations. Signature: a big arched entrance with stairs and two lanterns; tall arched windows on the upper floor.
- **Inside:** Two floors linked by a wide stone stair. See the floor plan below.
- **Blocks per layer:** Shell: stone bricks, cobblestone base, stone brick stairs and slabs, oak planks floors, spruce roof cones on towers, oak doors. Fittings: throne, long table and benches, barrels and chests (storeroom 4 × 6), beds, furnace and smoker kitchen, bookshelves, glass panes, torches. Flourish L2: banners, lanterns, chandeliers (chains + lanterns), carpets, shutters, arrow-slit trim. Flourish L3: banner patterns (decoration step), mossy and cracked bricks on the base, candles, gold-look tower tips (yellow terracotta).
- **Budget:** Shell 950 · Fittings 110 · Flourish L2 +80 · L3 +100 = **1240** blocks (350 above reach). First build (shell + fittings, 1060): ~35,100 ticks (29.2 min); full: ~41,000 ticks (34.2 min).
- **Flow:** As tier 3; the kitchen is the first consumer of food (it feeds the guard).
- **Perks:** Corner towers give archers a view; the kitchen feeds the keep's people.
- **Variants:** A: square towers. B: round-looking towers (stepped corners). C: one big tower plus three small ones.
- **Ruin:** One tower collapsed into a rubble cone, the upper floor gone over half the keep, arched windows empty, vines.
- **Terrain:** `ground_level` with `skirt` (the bigger footprint makes skirts likely).
- **Buildability watch:** chains, lanterns (medium); above reach: about 350 blocks (scaffolding).
- **Floor plan:**
  - Ground floor: **great hall** (9 × 7) with long table and hearth; **storeroom** (4 × 6, its own door to the bailey side); kitchen.
  - Upper floor (stairs): **throne room** (9 × 5, throne under a banner, carpet runner); **king's quarters** (bed, chest, desk); **treasury** (2 × 3, iron door, chests: decoration lock).
  - Towers: stairs to the roof walk.

#### Keep, tier 5: castle with curtain walls and bailey (`csr:keep`)
Tier 5 · zone keep · keep (landmark); king, guard captain, steward, cook, stable hand

- **Size:** 25 × 25 (keep 4 inside a bailey; curtain wall from stone wall modules), 16 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** The tier-4 keep stands in a bailey enclosed by a curtain wall with four corner towers (wall modules) and a gatehouse. Inside the bailey: stables, a well, a small garden. Signature: a stone gatehouse with two towers and the kingdom's banners.
- **Inside:** As tier 4, plus a stable and well in the bailey.
- **Blocks per layer:** Shell: corner tower bodies (stone bricks, 4 × 4, 12 high), bailey paving (cobblestone, gravel), stable shell (oak, spruce). Fittings: stable stalls (fences, hay), well, benches, torches on the walls. Flourish L2: banners on every tower, lanterns along the bailey, flower beds, a cart. Flourish L3: banner patterns (decoration step), ivy (vines) on the curtain wall, mossy paving.
- **Budget:** Shell 700 · Fittings 80 · Flourish L2 +100 · L3 +120 = **1000** blocks (250 above reach). First build (shell + fittings, 780): ~24,900 ticks (20.7 min); full: ~31,900 ticks (26.6 min).
- **Flow:** Bailey = drop-off and stable yard; stables connect to pens and stables (horses).
- **Perks:** Bare horses for the guard (stable); the curtain wall protects the keep.
- **Variants:** A: square bailey. B: bailey following the hill (stepped). C: river-side bailey with a water gate.
- **Ruin:** Curtain wall breached in two places, towers roofless, the bailey overgrown (grass, flowers).
- **Terrain:** `stepped` bailey; towers `ground_level`; the curtain follows the wall module rules.
- **Buildability watch:** 16 stone wall modules (separate builds, about 16 × 45 blocks); above reach about 250.

#### Keep, tier 6: great hall, throne room, barracks wing (`csr:keep`)
Tier 6 · zone keep · keep (landmark); king, guard, steward, cooks, servants

- **Size:** 29 × 29, 18 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** Two wings join the keep: a great hall (9 × 15, 12 high, timber roof trusses, tall windows) and a barracks wing (7 × 13). Signature: the great hall's big stained-glass-look window (colored glass panes) behind the new throne.
- **Inside:** A proper **throne room** in the great hall; the barracks wing sleeps the king's guard (12 beds).
- **Blocks per layer:** Shell: stone bricks, dark oak frame, stone brick stairs roof, oak planks, glass, doors. Fittings: throne (gold-look), long tables, 12 beds, weapon racks (fences + item frames: decoration step), barrels, chests, bookshelves. Flourish L2: banners, chandeliers, carpets, colored glass panes, a minstrel gallery (slab balcony). Flourish L3: banner patterns, item frames, armor stands in the barracks (decoration step), candles.
- **Budget:** Shell 900 · Fittings 160 · Flourish L2 +120 · L3 +140 = **1320** blocks (200 above reach). First build (shell + fittings, 1060): ~29,900 ticks (24.9 min); full: ~37,200 ticks (31.0 min).
- **Flow:** The barracks wing draws gear from the storeroom; the great hall consumes food (feasts).
- **Perks:** Order and court: the throne room raises renown during feasts.
- **Variants:** A: wings in an L. B: wings either side (symmetrical). C: great hall on a raised terrace.
- **Ruin:** Great hall roof fallen (exposed trusses), barracks wing reduced to walls, the window frame empty.
- **Terrain:** `stepped` (each wing at its own height).
- **Buildability watch:** above reach about 200.
- **Floor plan:**
  - Keep (from tier 4): storeroom, kitchen, king's quarters, treasury.
  - Great hall: **throne room** (dais, throne, banners) and feasting tables.
  - Barracks wing: dormitory (12 beds), armory corner, captain's room.

#### Keep, tier 7: bigger castle with moat or ditch (`csr:keep`)
Tier 7 · zone keep · keep (landmark)

- **Size:** 33 × 33 inside a 2-wide moat, 20 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** The keep rises a floor (to 20) with a tall roof, the curtain wall is widened, and a moat (water: decoration step) or a dry ditch between raised earth banks rings the castle, crossed by a drawbridge-look stone bridge. Signature: the drawbridge gate (dark oak planks, chains).
- **Inside:** New top floor: the king's solar (private rooms) and a chapel corner.
- **Blocks per layer:** Shell: stone bricks, deepslate tile roof, dark oak, chains, moat banks (dirt, stone brick edging). Fittings: solar furniture (bed, bookshelves, desk), chapel corner (lectern, candles), stairs. Flourish L2: banners, lanterns, flower boxes, a balcony. Flourish L3: banner patterns (decoration step), mossy moat edging, lily pads (decoration step, need water).
- **Budget:** Shell 600 · Fittings 60 · Flourish L2 +100 · L3 +120 = **880** blocks (450 above reach). First build (shell + fittings, 660): ~27,500 ticks (22.9 min); full: ~36,700 ticks (30.6 min).
- **Flow:** As tier 6; the moat is a defense with no flow.
- **Perks:** Moat: attackers must use the bridge (later warfare stages).
- **Variants:** A: water moat. B: dry ditch with stakes (fences). C: river used as part of the moat.
- **Ruin:** Moat silted up (dirt, grass), the bridge broken in the middle, the top floor gone.
- **Terrain:** Moat and banks `flexible` (follow the ground); the keep `ground_level`.
- **Buildability watch:** 24 moat modules; water (BROKEN → decoration step); above reach about 450.

#### Keep, tier 8: concentric inner and outer ward (`csr:keep`)
Tier 8 · zone keep · keep (landmark)

- **Size:** keep core 15 × 15 (rebuilt); outer ward 41 × 41 from wall modules, 22 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** The keep is rebuilt as a pale-stone core (calcite, diorite, stone bricks) with four slender towers, and a second, lower ring wall (the outer ward) surrounds the old curtain (now the inner ward). Signature: the first conical caps (gold-look) on the towers.
- **Inside:** New core floor plan with a grand stair. See below.
- **Blocks per layer:** Shell: calcite, polished diorite, stone bricks, quartz trim, deepslate roof, oak floors. Fittings: grand stair, throne room furniture, storeroom racks, beds, kitchen, bookshelves. Flourish L2: banners, lanterns, arcade (stone brick walls + slabs), balconies, gold-look caps. Flourish L3: banner patterns (decoration step), real gold block cap tips (open question 10), candles.
- **Budget:** Shell 1300 · Fittings 180 · Flourish L2 +150 · L3 +170 = **1800** blocks (700 above reach). First build (shell + fittings, 1480): ~54,900 ticks (45.8 min); full: ~66,800 ticks (55.6 min).
- **Flow:** As tier 7; the inner ward is the delivery yard, the outer ward holds workshops.
- **Perks:** Two rings of walls: the outer ward slows attackers.
- **Variants:** A: square rings. B: rings following the hill (stepped). C: river side with a water gate.
- **Ruin:** The pale core's towers broken at different heights, caps fallen in the yard, outer ward walls in fragments.
- **Terrain:** `ground_level` core; wards `stepped` via wall modules.
- **Buildability watch:** above reach about 700; quartz and calcite (UNKNOWN low).
- **Floor plan:**
  - Ground floor: storeroom (5 × 7, double door to the inner ward), kitchen and buttery.
  - First floor: throne room and great hall merged into a long hall with an arcade.
  - Second floor: king's quarters, treasury (iron door), council room. Towers: stairs to the roof walk.

#### Keep, tier 9: grand castle with many towers and an armory (`csr:keep`)
Tier 9 · zone keep · keep (landmark)

- **Size:** 45 × 45, 26 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** Six more slender towers (3 × 3, up to 26) and an armory wing (9 × 11, 12 high) are added; arched bridges link towers to the core at the second floor. Signature: a skyline of conical gold-look caps with banners.
- **Inside:** Armory wing: weapon racks, armor stands (decoration step), a smithing corner.
- **Blocks per layer:** Shell: calcite, diorite, stone bricks, quartz, deepslate tile caps, arched bridges (stone brick stairs and slabs). Fittings: armory racks (fences), chests, anvil, smithing table, beds for armorers, stairs. Flourish L2: banners on every tower, lanterns on the bridges, balconies. Flourish L3: armor stands, item frames, banner patterns (decoration step); gold block cap tips.
- **Budget:** Shell 1100 · Fittings 120 · Flourish L2 +150 · L3 +180 = **1550** blocks (700 above reach). First build (shell + fittings, 1220): ~48,100 ticks (40.1 min); full: ~61,100 ticks (50.9 min).
- **Flow:** Armory: the smithy produces gear → carriers → armory (store) → guard (consumer).
- **Perks:** The armory equips the whole guard at once.
- **Variants:** A: towers on the curtain. B: towers clustered around the core. C: towers along the river front.
- **Ruin:** Towers snapped at different heights, bridges fallen, the armory roofless.
- **Terrain:** Towers `ground_level`; bridges join parts at different heights.
- **Buildability watch:** above reach about 700.

#### Keep, tier 10: fortress (`csr:keep`)
Tier 10 · zone keep · keep (landmark)

- **Size:** 49 × 49, 28 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** The core rises to 28 (the tested limit) with a grand throne hall (11 × 19, 16 high) on a raised plinth that holds the dungeon and treasury vault. Heavy walls (from wall modules, 8 high, double thickness). Signature: the grand throne hall's arcaded front and a gold-capped central spire.
- **Inside:** See the floor plan below.
- **Blocks per layer:** Shell: calcite, polished diorite, stone bricks, deepslate, quartz pillars, gold-look caps. Fittings: grand throne, feasting tables, treasury chests, dungeon cells (iron bars, iron doors: decoration lock), beds, kitchen, library corner. Flourish L2: banners, chandeliers, colored glass, balconies, statues (props). Flourish L3: banner patterns, armor stands, item frames (decoration step); gold blocks on the spire tip.
- **Budget:** Shell 1200 · Fittings 200 · Flourish L2 +180 · L3 +200 = **1780** blocks (800 above reach). First build (shell + fittings, 1400): ~55,100 ticks (45.9 min); full: ~70,000 ticks (58.4 min).
- **Flow:** As tier 9; the treasury stores gold (from mine 2+).
- **Perks:** The grand throne hall is the top renown feature; the dungeon holds captured enemies (warfare stages).
- **Variants:** A: symmetrical fortress. B: hill-top fortress (stepped wards). C: river fortress with water gate.
- **Ruin:** Spire fallen across the hall, plinth cracked open showing the dungeon, gold caps scattered.
- **Terrain:** `stepped` plinth (it absorbs the slope); towers `ground_level`.
- **Buildability watch:** above reach about 800; dungeon is in the plinth, not underground (open question 12).
- **Floor plan:**
  - Plinth level: **dungeon** (cells with iron bars), **treasury vault** (iron door), **storeroom** (6 × 8, doors to the yard).
  - Hall level: **grand throne hall**, kitchen, feasting hall.
  - Upper floors: **king's quarters**, council chamber, library. Spire: lookout.

### Housing

#### Hovel or tent (`csr:hovel`)
Tier 1 · zone inner · house; 1 citizen (newcomers before a cottage is free)

- **Size:** 3 × 4, 3 tall. **Needs scaffolding:** no.
- **Look:** A lean-to of oak slabs over log posts, or a wool tent (two wool slopes made of carpet on stairs). Signature: a tiny fire pit in front.
- **Inside:** One bed (or carpet bedroll), a chest.
- **Blocks per layer:** Shell: oak logs, oak slabs or white wool, carpet. Fittings: bed or carpet, chest. Flourish L2: fire pit (campfire, unlit), a lantern on a post. Flourish L3: flower pot (empty), a woodpile (stripped logs).
- **Budget:** Shell 30 · Fittings 4 · Flourish L2 +6 · L3 +6 = **46** blocks. First build (shell + fittings, 34): ~800 ticks (0.6 min); full: ~1,000 ticks (0.9 min).
- **Flow:** Producer: none. Carrier: builders bring its materials once. Store: none. Consumer: 1 citizen lives there (population).
- **Perks:** A citizen's first home: visitors who stay become citizens.
- **Variants:** A: lean-to. B: wool tent (team color). C: turf hut (dirt and grass block roof).
- **Ruin:** Posts only, a torn tent (carpet) on the ground.
- **Terrain:** `flexible` (fits anywhere, on any ground).
- **Buildability watch:** campfire, bed (medium).

#### Cottage (levels 1 to 3) (`csr:cottage`)
Tier 1 · zone inner · house; 2 citizens

- **Size:** 5 × 7, 7 tall. **Needs scaffolding:** no.
- **Look:** Level 1: cobblestone base course, oak plank walls 3 high with log corners, an oak stair gable roof (+3), one door, two windows. Level 2: shutters, flower boxes, a brick chimney. Level 3: plaster and dark-oak timber look on the gable (style A), a dormer, a lantern by the door, a fenced front garden. Signature: the chimney with a smoking campfire top (unlit unless hearth lit).
- **Inside:** Two beds, a table (fence + carpet) with two stair chairs, a hearth (furnace), a chest, a shelf (trapdoors), carpet.
- **Blocks per layer:** Shell: cobblestone (base course), oak planks, oak logs (corners), oak stairs and slabs (roof), oak door, glass panes. Fittings: beds, fence, carpet, oak stairs (chairs), furnace, chest, spruce trapdoors (shelf). Flourish L2: spruce trapdoor shutters, flower boxes (trapdoors + flowers; potted plants: decoration step), bricks (chimney). Flourish L3: white terracotta and dark oak (timber look), dormer (stairs), lantern, fence garden with flowers, mossy base.
- **Budget:** Shell 117 · Fittings 20 · Flourish L2 +22 · L3 +25 = **184** blocks. First build (shell + fittings, 137): ~3,100 ticks (2.6 min); full: ~4,100 ticks (3.5 min).
- **Flow:** Producer: none. Carrier: builders bring its materials once (the first consumer of building materials after the keep, stage 5). Store: none. Consumer: 2 citizens live there (population).
- **Perks:** Rested citizens (tier-1 perk).
- **Variants:** A: gable (style A). B: stone cottage with steep roof (style B). C: brick with an overhanging upper half-storey (style C). Soldier-sized doorway variant (UNTESTED).
- **Ruin:** Walls 1 to 2 high, roof fallen in, chimney standing, moss and vines.
- **Terrain:** `ground_level`; `skirt` up to 3.
- **Buildability watch:** beds, flowers (medium).

#### Inn / visitors' camp: camp stage (`csr:visitors_camp`) **NEW**
Tier 1 · zone inner (by the gate) · house; visiting soldiers stay until housed; an innkeeper later

- **Size:** 9 × 9, 4 tall. **Needs scaffolding:** no.
- **Look:** Three tents around a fire pit, a hitching post and a notice board, by the main road near the gate. Signature: a signpost reading "Welcome" (sign text: decoration step).
- **Inside:** Bedrolls (carpet) for 4 visitors, a shared chest.
- **Blocks per layer:** Shell: wool and carpet (tents), oak logs, slabs. Fittings: carpets, chest, fire pit. Flourish L2: notice board, hitching post, lantern posts. Flourish L3: banner (plain), signpost text (decoration step), flowers.
- **Budget:** Shell 60 · Fittings 15 · Flourish L2 +20 · L3 +25 = **120** blocks. First build (shell + fittings, 75): ~1,700 ticks (1.4 min); full: ~2,700 ticks (2.2 min).
- **Flow:** Producer: visitors (population, D7). Carrier: none. Store: the camp. Consumer: cottages (housed visitors leave the camp).
- **Perks:** Welcome: visitors stay and join.
- **Variants:** A: tents. B: lean-tos. C: covered wagon camp (wool on fence frames).
- **Ruin:** Fire pit ring, a fallen tent.
- **Terrain:** `flexible`.
- **Buildability watch:** campfire (medium).

#### Inn / visitors' camp: inn stage (`csr:inn`) **NEW**
Tier 4 · zone inner (by the gate) · house; visitors, an innkeeper

- **Size:** 7 × 9, 8 tall. **Needs scaffolding:** no.
- **Look:** A two-storey inn (3 + 3 walls, low roof) with a hanging sign and a stable lean-to. Signature: a projecting sign on a chain (sign text: decoration step) and lit windows.
- **Inside:** Ground floor: common room (tables, benches, a hearth, a bar of barrels). Upper floor by stairs: 6 small beds.
- **Blocks per layer:** Shell: cobblestone, oak and spruce planks, logs, spruce stairs roof, doors, glass panes. Fittings: 6 beds, tables, benches, barrels, hearth, stairs. Flourish L2: hanging sign, lanterns, shutters, flower boxes. Flourish L3: timber look, chimney smoke, banner.
- **Budget:** Shell 210 · Fittings 45 · Flourish L2 +30 · L3 +30 = **315** blocks. First build (shell + fittings, 255): ~5,700 ticks (4.8 min); full: ~7,100 ticks (5.9 min). ⚠ first build 255 > 200.
- **Flow:** As the camp; the tavern (food hall) supplies its meals.
- **Perks:** Visitors arrive faster (with the tavern).
- **Variants:** A: timber inn. B: stone inn in the wall. C: riverside inn with a jetty.
- **Ruin:** Upper floor gone, sign fallen, ground floor walls.
- **Terrain:** `skirt`.
- **Buildability watch:** beds, hanging sign (medium).

#### Townhouse (`csr:townhouse`)
Tier 4 · zone inner · house; 3 citizens

- **Size:** 5 × 6, 9 tall. **Needs scaffolding:** no.
- **Look:** Narrow, two floors (3 + 3) with a jettied (overhanging) upper floor and a steep roof. Signature: the overhang on dark-oak brackets (stairs upside down).
- **Inside:** Ground: kitchen-living room (table, hearth). Upper (stairs): 3 beds, a chest.
- **Blocks per layer:** Shell: cobblestone base, oak planks / white terracotta, dark oak logs and stairs, spruce or brick stairs roof, door. Fittings: 3 beds, table, hearth, stairs, chests, glass panes. Flourish L2: shutters, flower boxes, lantern. Flourish L3: dormer, chimney, timber cross braces (dark oak fences).
- **Budget:** Shell 175 · Fittings 30 · Flourish L2 +30 · L3 +30 = **265** blocks. First build (shell + fittings, 205): ~4,600 ticks (3.8 min); full: ~6,000 ticks (5.0 min). ⚠ first build 205 > 200.
- **Flow:** Producer: none. Carrier: builders, once. Store: none. Consumer: 3 citizens live there.
- **Perks:** Room for the growing population.
- **Variants:** A: timber. B: tall stone. C: brick with a shop front (a workstation on the ground floor).
- **Ruin:** Upper floor collapsed onto the ground floor, brackets sticking out.
- **Terrain:** `ground_level`; `stepped` rows on slopes.

#### Noble house (`csr:noble_house`)
Tier 5 · zone inner (near the keep) · house; a noble family (steward, captain)

- **Size:** 7 × 9, 9 tall. **Needs scaffolding:** no.
- **Look:** A stone and timber manor: two floors, a porch with columns (stone brick walls), a hip roof. Signature: a small walled garden with a bench.
- **Inside:** Hall, study (bookshelves, lectern), 3 bedrooms, a kitchen.
- **Blocks per layer:** Shell: stone bricks, white terracotta, dark oak, brick stairs roof, doors, glass. Fittings: beds, bookshelves, lectern, tables, chairs, hearth, chests. Flourish L2: banners, lanterns, flower boxes, porch columns. Flourish L3: garden wall, statue prop, candles, carpets.
- **Budget:** Shell 260 · Fittings 50 · Flourish L2 +50 · L3 +60 = **420** blocks. First build (shell + fittings, 310): ~7,000 ticks (5.8 min); full: ~9,400 ticks (7.9 min). ⚠ first build 310 > 200; ⚠ full 420 > 350.
- **Flow:** Producer: renown (the study, small). Carrier: none. Store: none. Consumer: a noble family lives there.
- **Perks:** Nobles run the town hall's work later.
- **Variants:** A: manor. B: tower house (style B). C: merchant's house with a warehouse.
- **Ruin:** Porch columns standing alone, walls to 2.
- **Terrain:** `skirt`.
- **Buildability watch:** Over the common budget: treat as a small landmark, or trim fittings.

#### King's guard quarters (`csr:guard_quarters`)
Tier 6 · zone keep · military housing; 8 guards

- **Size:** 7 × 9, 8 tall. **Needs scaffolding:** no.
- **Look:** A stone barracks house inside the keep zone with a guard post at the door. Signature: shields on the wall (item frames: decoration step) and a weapon rack.
- **Inside:** 8 beds in two rows, footlockers (chests), a mess table, a weapon rack.
- **Blocks per layer:** Shell: stone bricks, cobblestone, spruce, stone brick stairs roof, door. Fittings: 8 beds, chests, table, benches, fence racks. Flourish L2: banners, lanterns. Flourish L3: item frames, armor stands (decoration step).
- **Budget:** Shell 230 · Fittings 60 · Flourish L2 +30 · L3 +30 = **350** blocks. First build (shell + fittings, 290): ~6,500 ticks (5.4 min); full: ~7,900 ticks (6.6 min). ⚠ first build 290 > 200.
- **Flow:** Producer: none. Carrier: the armorer brings gear. Store: footlockers. Consumer: 8 guards (gear from the armory).
- **Perks:** A standing guard for the keep.
- **Variants:** A: long house. B: built into the curtain wall. C: two floors on a small footprint.
- **Ruin:** Roofless walls, a rack standing.
- **Terrain:** `ground_level`.
- **Buildability watch:** beds (medium).

### Food and wood

#### Farm (levels 1 to 3) (`csr:farm`)
Tier 1 · zone outer · farm; 1 to 2 farmers

- **Size:** 7 × 9 plot (level 3: 9 × 11), 7 tall. **Needs scaffolding:** no.
- **Look:** Level 1: a fenced plot on existing ground (farmers till it), a gap for the entrance, a composter. Level 2: a tool shed and a scarecrow (fence, carved pumpkin, carpet). Level 3: a small windmill (7 tall, wool sails) and a water channel. Signature: the scarecrow.
- **Inside:** Shed: a chest, a composter (the workstation), hay.
- **Blocks per layer:** Shell: oak fences (no gate: fence gates are BROKEN for passage), composter, path stones (coarse dirt). Fittings: shed (oak planks, slabs), chest, hay bales. Flourish L2: scarecrow, flower edges. Flourish L3: windmill (oak, white wool), water channel (water: decoration step).
- **Budget:** Shell 40 · Fittings 15 · Flourish L2 +25 · L3 +60 = **140** blocks. First build (shell + fittings, 55): ~1,200 ticks (1.0 min); full: ~3,200 ticks (2.6 min).
- **Flow:** Producer: crops. Carrier: farmers. Store: the keep's storeroom (later the storehouse). Consumer: bakery, tavern, food hall.
- **Perks:** Food for the food hall perks.
- **Variants:** A: crops. B: orchard (leaves and logs, UNKNOWN medium). C: terraced field on slopes.
- **Ruin:** Fence posts, a fallen shed, wild grass and wheat.
- **Terrain:** `flexible` (the field follows the ground; the shed `ground_level`).
- **Buildability watch:** farmland BROKEN (farmers till existing ground), water BROKEN (decoration step).

#### Lumber camp / tree farm (`csr:lumber_camp`)
Tier 1 · zone outer · resource; 1 to 2 woodcutters

- **Size:** 7 × 7 (tree farm, tier 6: 11 × 11 with sapling rows), 6 tall. **Needs scaffolding:** no.
- **Look:** A log shed, a log pile, a chopping block (stripped log + axe in an item frame: decoration step). Tree farm: rows of saplings (UNKNOWN medium) and a fence.
- **Inside:** Shed: chest, a grindstone (the workstation), log pile.
- **Blocks per layer:** Shell: oak logs, spruce planks, slabs. Fittings: chest, grindstone, log pile (stripped logs). Flourish L2: sawhorse (fences + slabs), lantern. Flourish L3: item frame axe (decoration step), sapling rows.
- **Budget:** Shell 55 · Fittings 12 · Flourish L2 +15 · L3 +20 = **102** blocks. First build (shell + fittings, 67): ~1,500 ticks (1.3 min); full: ~2,300 ticks (1.9 min).
- **Flow:** Producer: logs (from trees around and, later, the tree farm). Carrier: woodcutters. Store: storeroom. Consumer: builders, carpenter (sticks).
- **Perks:** Sticks for every soldier.
- **Variants:** A: forest-edge camp. B: riverside with a log slide. C: tree farm.
- **Ruin:** Rotting log pile (stripped logs, moss), shed roof gone.
- **Terrain:** `flexible`.
- **Buildability watch:** saplings (medium).

#### Butcher / meat hall (`csr:butcher`)
Tier 3 · zone inner · food; 1 butcher

- **Size:** 5 × 6, 7 tall. **Needs scaffolding:** no.
- **Look:** A cool stone shop with hooks (chains) and a smoker. Signature: hams on hooks (item frames: decoration step).
- **Inside:** Smoker (workstation), a block table (stripped log), barrels.
- **Blocks per layer:** Shell: cobblestone, stone bricks, spruce, spruce stairs roof, door. Fittings: smoker, chains, barrels, table. Flourish L2: awning (wool carpet on fences), lantern. Flourish L3: item frames (decoration step), sign.
- **Budget:** Shell 110 · Fittings 15 · Flourish L2 +20 · L3 +20 = **165** blocks. First build (shell + fittings, 125): ~2,800 ticks (2.3 min); full: ~3,700 ticks (3.1 min).
- **Flow:** Producer: meat from pens. Carrier: butcher. Store: storeroom. Consumer: tavern, food hall.
- **Perks:** Meat for the well-fed perk.
- **Variants:** A: shop. B: meat hall with an open front. C: smokehouse.
- **Ruin:** Walls, the smoker standing.
- **Terrain:** `ground_level`.
- **Buildability watch:** chains (low).

#### Bakery (`csr:bakery`)
Tier 3 · zone inner · food; 1 baker

- **Size:** 5 × 6, 7 tall. **Needs scaffolding:** no.
- **Look:** A cottage-like bakery with a big brick oven at one end and a chimney. Signature: the domed oven (bricks, brick stairs).
- **Inside:** Oven (furnace in bricks; the workstation is the smoker), flour sacks (white wool), table.
- **Blocks per layer:** Shell: cobblestone, oak planks, bricks (oven, chimney), oak stairs roof, door. Fittings: furnace, smoker, table, barrels. Flourish L2: shutters, sign, flower box. Flourish L3: bread display (decoration step), lantern.
- **Budget:** Shell 115 · Fittings 15 · Flourish L2 +20 · L3 +20 = **170** blocks. First build (shell + fittings, 130): ~2,900 ticks (2.4 min); full: ~3,800 ticks (3.2 min).
- **Flow:** Producer: bread from wheat. Carrier: baker. Store: storeroom. Consumer: tavern, food hall.
- **Perks:** Bread for the well-fed perk.
- **Variants:** A: cottage bakery. B: in a townhouse ground floor. C: by the mill.
- **Ruin:** The oven dome standing in rubble.
- **Terrain:** `ground_level`.

#### Tavern / food hall (`csr:food_hall`)
Tier 4 · zone inner (by the commons) · food; a cook, a server

- **Size:** 7 × 11, 8 tall. **Needs scaffolding:** no.
- **Look:** A long timber hall with a big hearth and long tables; the tavern variant has a bar and a hanging sign. Signature: the hearth and chimney at the far end.
- **Inside:** Long tables, benches, a hearth, a kitchen corner (smoker, barrels), a bar.
- **Blocks per layer:** Shell: cobblestone base, oak, spruce, spruce stairs roof, doors. Fittings: tables, benches, hearth, smoker, barrels. Flourish L2: lanterns, banners, hanging sign. Flourish L3: sign text (decoration step), candles, flower boxes.
- **Budget:** Shell 230 · Fittings 50 · Flourish L2 +30 · L3 +35 = **345** blocks. First build (shell + fittings, 280): ~6,300 ticks (5.2 min); full: ~7,800 ticks (6.5 min). ⚠ first build 280 > 200.
- **Flow:** Producer: meals (from farm, bakery, butcher). Carrier: servers. Store: the hall. Consumer: soldiers (perks).
- **Perks:** Well fed: soldiers work faster for a while; the tavern draws visitors.
- **Variants:** A: food hall (long tables). B: tavern (bar, upstairs rooms). C: open-sided market kitchen.
- **Ruin:** Roof fallen, the hearth standing, tables overturned (slabs).
- **Terrain:** `skirt`.
- **Buildability watch:** Fittings + shell 280 is over the first-build limit: split the hall into two builds or trim.

#### Fishing dock (`csr:fishing_dock`)
Tier 2 · zone outer (on water) · food; 1 to 2 fishers

- **Size:** 3 × 9 jetty plus a 3 × 4 hut, 5 tall. **Needs scaffolding:** no.
- **Look:** An oak jetty on log pilings into the water, a small hut with nets. Signature: nets drying on a fence frame (white carpets on fences).
- **Inside:** Barrels, a chest, a fishing spot at the end.
- **Blocks per layer:** Shell: oak logs (pilings, placed into water), oak planks and slabs. Fittings: barrels, chest. Flourish L2: net frames, lantern. Flourish L3: boat prop (decoration step: boat entity).
- **Budget:** Shell 60 · Fittings 8 · Flourish L2 +12 · L3 +15 = **95** blocks. First build (shell + fittings, 68): ~1,500 ticks (1.3 min); full: ~2,100 ticks (1.8 min).
- **Flow:** Producer: fish. Carrier: fishers. Store: storeroom. Consumer: food hall.
- **Perks:** Fish for the well-fed perk.
- **Variants:** A: straight jetty. B: L-jetty. C: riverside landing with steps.
- **Ruin:** Broken jetty (gaps), pilings only.
- **Terrain:** `flexible` (pilings to the water bed, max 4 deep).
- **Buildability watch:** blocks placed into water (UNKNOWN medium: water is replaceable, so it should place).

#### Pens and stables (`csr:stables`)
Tier 2 · zone outer · farm; 1 stable hand

- **Size:** 9 × 11, 6 tall. **Needs scaffolding:** no.
- **Look:** A fenced pen and an open-sided stable with stalls. Signature: hay loft under the roof.
- **Inside:** Stalls (fences), hay bales, a water trough (cauldron), a chest.
- **Blocks per layer:** Shell: oak fences, logs, spruce slabs roof, cobblestone base. Fittings: stalls, hay, cauldron, chest. Flourish L2: lanterns, a cart. Flourish L3: horse armor stand (decoration step), flowers.
- **Budget:** Shell 120 · Fittings 25 · Flourish L2 +20 · L3 +20 = **185** blocks. First build (shell + fittings, 145): ~3,300 ticks (2.7 min); full: ~4,200 ticks (3.5 min).
- **Flow:** Producer: horses, leather (hides), meat. Carrier: stable hand. Store: storeroom. Consumer: guard (horses), tannery, butcher.
- **Perks:** Bare horses; hides for leather.
- **Variants:** A: stable and pen. B: sheep and cow pens only. C: hillside stable (stepped).
- **Ruin:** Broken fences, roofless stalls.
- **Terrain:** `flexible` pen; stable `ground_level`.
- **Buildability watch:** fence gates BROKEN: pens have an opening and soldiers herd by walking.

### Industry

#### Storehouse (`csr:storehouse`)
Tier 3 · zone inner (by the keep and the main road) · storage; 1 storekeeper

- **Size:** 7 × 9, 8 tall. **Needs scaffolding:** no.
- **Look:** A stout timber and stone barn with a wide door and a loading crane arm. Signature: crates and barrels stacked outside under an awning.
- **Inside:** Rows of chests and barrels, a lectern ledger (the workstation).
- **Blocks per layer:** Shell: cobblestone base, spruce planks and logs, spruce stairs roof, double oak doors. Fittings: chests, barrels, lectern. Flourish L2: awning, crane arm (logs, chain), lantern. Flourish L3: sign (decoration step), hay and crates outside.
- **Budget:** Shell 190 · Fittings 40 · Flourish L2 +20 · L3 +20 = **270** blocks. First build (shell + fittings, 230): ~5,200 ticks (4.3 min); full: ~6,100 ticks (5.1 min). ⚠ first build 230 > 200.
- **Flow:** Store: overflow for the keep's storeroom (the storeroom stays the delivery point; carriers move surplus here). Producer and consumer: all of the economy.
- **Perks:** More storage before the stockpile is full (nothing is lost when it is full: pillar 3).
- **Variants:** A: barn. B: stone warehouse (style C, by the docks). C: granary on stilts (farms).
- **Ruin:** Roof fallen, barrels scattered.
- **Terrain:** `skirt`.

#### Mine (stages 1 to 3) (`csr:mine`)
Tier 3 · zone outer · resource; 2 to 4 miners

- **Size:** stage 1: 5 × 5 entrance; stage 2: + 3 × 3 headframe; stage 3: + 5 × 7 winch house, 9 tall. **Needs scaffolding:** no.
- **Look:** Stage 1: a timber-framed entrance into a hillside or a fenced pit with a ladder look (decoration). Stage 2: a headframe (log tower, 9 tall) with a chain. Stage 3: a winch house and ore carts (minecart: decoration step). Signature: the headframe. Digging itself is the dig work goal (D6), not the builders.
- **Inside:** Entrance hut: chest, a torch rack; stage 3: winch (grindstone), carts.
- **Blocks per layer:** Shell: oak logs, spruce planks, cobblestone, slabs. Fittings: chest, grindstone, torches. Flourish L2: stage 2: headframe, chains. Flourish L3: stage 3: winch house, rails (UNKNOWN), carts (decoration step).
- **Budget:** Shell 60 · Fittings 10 · Flourish L2 +40 · L3 +60 = **170** blocks. First build (shell + fittings, 70): ~1,600 ticks (1.3 min); full: ~3,800 ticks (3.2 min).
- **Flow:** Producer: stone, coal, iron (2: gold; 3: gems). Carrier: miners. Store: storeroom. Consumer: smithy, stonemason, armory, treasury.
- **Perks:** Mine materials for gear.
- **Variants:** A: hillside adit. B: open pit. C: river cliff.
- **Ruin:** Collapsed entrance (gravel), leaning headframe.
- **Terrain:** `flexible` (it goes where the hill is).
- **Buildability watch:** rails (UNKNOWN low), gravel (medium).

#### Quarry (`csr:quarry`)
Tier 3 · zone outer · resource; 2 quarrymen

- **Size:** a stone shed 5 × 5 by a cut face, 6 tall. **Needs scaffolding:** no.
- **Look:** A crane (logs, chain, a stone block) and a shed; the stepped cut face is made by digging, not building. Signature: the crane.
- **Inside:** Shed: stonecutter (workstation), chest.
- **Blocks per layer:** Shell: oak logs, cobblestone, slabs. Fittings: stonecutter, chest. Flourish L2: crane (logs, chain). Flourish L3: rubble piles, lantern.
- **Budget:** Shell 45 · Fittings 8 · Flourish L2 +20 · L3 +15 = **88** blocks. First build (shell + fittings, 53): ~1,200 ticks (1.0 min); full: ~2,000 ticks (1.6 min).
- **Flow:** Producer: stone. Carrier: quarrymen. Store: storeroom. Consumer: builders (stone tiers), stonemason.
- **Perks:** Stone for stone buildings.
- **Variants:** A: hillside. B: pit. C: riverside cliff.
- **Ruin:** Overgrown cut, crane fallen.
- **Terrain:** `flexible`.

#### Smithy (`csr:smithy`)
Tier 3 · zone inner · workshop; 1 smith

- **Size:** 5 × 7, 7 tall. **Needs scaffolding:** no.
- **Look:** Stone walls, an open front with a big chimney over the forge. Signature: the forge (blast furnace under a brick hood) and an anvil by the door.
- **Inside:** Forge (blast furnace), anvil, smithing table (workstation), barrels of ore, a quench trough (cauldron).
- **Blocks per layer:** Shell: cobblestone, stone bricks, bricks (hood), spruce stairs roof. Fittings: blast furnace, anvil, smithing table, cauldron, barrels. Flourish L2: awning, lantern, sign. Flourish L3: item frames with tools (decoration step), soot (blackstone patches).
- **Budget:** Shell 110 · Fittings 15 · Flourish L2 +20 · L3 +20 = **165** blocks. First build (shell + fittings, 125): ~2,800 ticks (2.3 min); full: ~3,700 ticks (3.1 min).
- **Flow:** Producer: gear (from iron). Carrier: smith → storeroom (later armory). Store: storeroom. Consumer: soldiers.
- **Perks:** Better gear; sharpened weapons.
- **Variants:** A: open forge. B: forge in the wall. C: waterwheel smithy (decoration).
- **Ruin:** Chimney and anvil in rubble.
- **Terrain:** `ground_level`.
- **Buildability watch:** anvil (gravity, needs a floor).

#### Tannery (`csr:tannery`)
Tier 3 · zone outer · workshop; 1 tanner

- **Size:** 5 × 6, 6 tall. **Needs scaffolding:** no.
- **Look:** A shed with drying racks outside (fences with brown carpet) and vats (cauldrons). Signature: the drying racks.
- **Inside:** Vats (cauldrons), a work table (crafting table, the workstation), barrels.
- **Blocks per layer:** Shell: cobblestone, spruce, slabs roof. Fittings: cauldrons, crafting table, barrels. Flourish L2: drying racks. Flourish L3: hides in item frames (decoration step).
- **Budget:** Shell 85 · Fittings 12 · Flourish L2 +20 · L3 +15 = **132** blocks. First build (shell + fittings, 97): ~2,200 ticks (1.8 min); full: ~3,000 ticks (2.5 min).
- **Flow:** Producer: leather (from hides). Carrier: tanner. Store: storeroom. Consumer: soldiers (armor).
- **Perks:** Leather armor.
- **Variants:** A: shed. B: riverside (smelly, outer zone). C: in the bailey.
- **Ruin:** Racks fallen, vats standing.
- **Terrain:** `flexible`.

#### Carpenter or fletcher (`csr:carpenter`)
Tier 2 · zone inner · workshop; 1 carpenter

- **Size:** 5 × 6, 7 tall. **Needs scaffolding:** no.
- **Look:** A timber workshop with a log stack and a sawhorse outside. Signature: an open workshop front with a fletching table.
- **Inside:** Fletching table (fletcher) or crafting table (carpenter), barrels of sticks, a bench.
- **Blocks per layer:** Shell: oak logs, oak planks, oak stairs roof, door. Fittings: fletching table / crafting table, barrels, bench. Flourish L2: sawhorse, sign. Flourish L3: arrows in item frames (decoration step), lantern.
- **Budget:** Shell 100 · Fittings 12 · Flourish L2 +20 · L3 +15 = **147** blocks. First build (shell + fittings, 112): ~2,500 ticks (2.1 min); full: ~3,300 ticks (2.8 min).
- **Flow:** Producer: sharpened sticks, arrows (from logs). Carrier: carpenter. Store: storeroom. Consumer: soldiers.
- **Perks:** Sharpened sticks, arrows.
- **Variants:** A: carpenter. B: fletcher. C: cooper (barrels).
- **Ruin:** Workshop roofless, log stack rotting.
- **Terrain:** `ground_level`.

#### Stonemason (`csr:stonemason`)
Tier 4 · zone inner · workshop; 1 mason

- **Size:** 5 × 6, 6 tall. **Needs scaffolding:** no.
- **Look:** An open stone yard with a covered bench. Signature: half-cut blocks (stairs and slabs) on display.
- **Inside:** Stonecutter (workstation), block piles, a chest.
- **Blocks per layer:** Shell: stone bricks, cobblestone, slabs roof. Fittings: stonecutter, chest. Flourish L2: display blocks. Flourish L3: statue in progress (prop).
- **Budget:** Shell 80 · Fittings 10 · Flourish L2 +20 · L3 +15 = **125** blocks. First build (shell + fittings, 90): ~2,000 ticks (1.7 min); full: ~2,800 ticks (2.3 min).
- **Flow:** Producer: dressed stone (from quarry stone). Carrier: mason. Store: storeroom. Consumer: builders (stone tiers).
- **Perks:** Faster stone buildings.
- **Variants:** A: yard. B: shed. C: by the quarry.
- **Ruin:** Scattered blocks.
- **Terrain:** `flexible`.

#### Armory (`csr:armory`)
Tier 6 · zone inner (keep side) · military store; 1 armorer

- **Size:** 7 × 9, 9 tall. **Needs scaffolding:** no.
- **Look:** A strong stone store with small windows, iron-bound door (wooden door, iron bars trim). Signature: weapon racks visible through the door.
- **Inside:** Racks, armor stands (decoration step), chests, smithing table (workstation).
- **Blocks per layer:** Shell: stone bricks, deepslate trim, stone brick stairs roof, door. Fittings: racks (fences), chests, smithing table. Flourish L2: banners, lanterns. Flourish L3: armor stands, item frames (decoration step).
- **Budget:** Shell 220 · Fittings 30 · Flourish L2 +30 · L3 +30 = **310** blocks. First build (shell + fittings, 250): ~5,600 ticks (4.7 min); full: ~7,000 ticks (5.8 min). ⚠ first build 250 > 200.
- **Flow:** Producer: smithy, tannery. Carrier: armorer. Store: armory. Consumer: the guard.
- **Perks:** Full armor, horse armor.
- **Variants:** A: standalone. B: keep wing (tier 9). C: in a wall tower.
- **Ruin:** Walls standing, racks bare.
- **Terrain:** `ground_level`.

### Civic and care

#### Well (`csr:well`)
Tier 1 · zone inner · utility; anyone

- **Size:** 3 × 3, 4 tall. **Needs scaffolding:** no.
- **Look:** A cobblestone ring with a small roof on two posts and a bucket on a chain. Signature: the little roof.
- **Inside:** Water (decoration step).
- **Blocks per layer:** Shell: cobblestone, cobblestone wall posts, oak slabs roof. Fittings: chain. Flourish L2: flower pots (empty), lantern. Flourish L3: mossy cobblestone, vines.
- **Budget:** Shell 20 · Fittings 2 · Flourish L2 +4 · L3 +4 = **30** blocks. First build (shell + fittings, 22): ~500 ticks (0.4 min); full: ~700 ticks (0.6 min).
- **Flow:** No flow (a meeting point; later the infirmary's water).
- **Perks:** None; citizens gather there.
- **Variants:** A: round. B: square stone (style B). C: pump well (iron bars).
- **Ruin:** Ring broken, roof gone.
- **Terrain:** `ground_level`.
- **Buildability watch:** water BROKEN (decoration step).

#### Market square (`csr:market`)
Tier 4 · zone inner · market; 2 to 4 traders

- **Size:** 11 × 11, 4 tall. **Needs scaffolding:** no.
- **Look:** Paved square with 4 market stalls (props) around a market cross or small fountain. Signature: striped awnings (wool).
- **Inside:** Stalls with barrels and crates, a scale (lectern), benches.
- **Blocks per layer:** Shell: cobblestone and gravel paving (flexible). Fittings: 4 market stalls (props), barrels, benches. Flourish L2: awnings, lanterns, banners. Flourish L3: flower beds, signs (decoration step).
- **Budget:** Shell 121 · Fittings 40 · Flourish L2 +30 · L3 +30 = **221** blocks. First build (shell + fittings, 161): ~3,600 ticks (3.0 min); full: ~5,000 ticks (4.1 min).
- **Flow:** Producer: none (trade). Carrier: traders. Store: stalls. Consumer: the stockpile (swaps surplus for missing materials).
- **Perks:** Trade.
- **Variants:** A: open square. B: covered market hall (style C, 9 tall). C: market street.
- **Ruin:** Stalls collapsed, paving with grass.
- **Terrain:** `flexible`.
- **Buildability watch:** gravel (medium).

#### Commons / town green (levels 1 to 3) (`csr:commons`) **NEW**
Tier 2 · zone inner (main road junction near the keep gate, next to the tavern) · civic; the social heart, no workers

- **Size:** level 1: 9 × 9; level 2: 13 × 13; level 3: 17 × 17, 5 tall. **Needs scaffolding:** no.
- **Look:** Level 1 (tier 2): a green with a well, a fire pit and benches. Level 2 (tier 4): a paved plaza with a fountain and market stalls. Level 3 (tier 7): a grand plaza with a statue, lamp posts and flower beds. It sits at a main road junction near the keep gate and next to the tavern, not necessarily the town's geometric center. The old "plaza" idea folds into this. Signature: level 3's statue.
- **Inside:** Benches, fire pit, well (1); fountain, stalls (2); statue, lamp posts (3).
- **Blocks per layer:** Shell: grass (existing ground), gravel and cobblestone paths. Fittings: benches, fire pit, well. Flourish L2: paving, fountain (water: decoration step), 2 stalls. Flourish L3: statue, lamp posts, flower beds, banners.
- **Budget:** Shell 40 · Fittings 25 · Flourish L2 +80 · L3 +100 = **245** blocks. First build (shell + fittings, 65): ~1,500 ticks (1.2 min); full: ~5,500 ticks (4.6 min).
- **Flow:** No flow; idle citizens go there (observer feature).
- **Perks:** Meeting place.
- **Variants:** A: green. B: square plaza (style B). C: riverside promenade (style C).
- **Ruin:** Overgrown, fountain dry, statue toppled.
- **Terrain:** `flexible`.
- **Buildability watch:** water (decoration step).

#### Garden (levels 1 to 3) (`csr:garden`) **NEW**
Tier 2 · zone inner · civic; a gardener (herb garden: the infirmary's herbalist)

- **Size:** level 1: 7 × 7; 2: 9 × 9; 3: 11 × 11, 3 tall. **Needs scaffolding:** no.
- **Look:** Ornamental and communal: flowers, hedges (leaves), gravel paths, benches; level 2 adds a pond (decoration step) or fountain; level 3 a pergola. Variants: herb garden (supplies the infirmary), royal garden beside the keep (tier 7).
- **Inside:** Benches, a tool chest.
- **Blocks per layer:** Shell: gravel paths, existing ground. Fittings: benches, chest. Flourish L2: hedges, pond or fountain. Flourish L3: pergola (fences, leaves), statue prop.
- **Budget:** Shell 30 · Fittings 10 · Flourish L2 +40 · L3 +50 = **130** blocks. First build (shell + fittings, 40): ~900 ticks (0.8 min); full: ~2,900 ticks (2.4 min).
- **Flow:** Herb garden: producer of herbs → herbalist → infirmary (store) → healing (consumer). Ornamental: no flow.
- **Perks:** Herbs for healing (herb garden).
- **Variants:** A: ornamental. B: herb garden (tier 5). C: royal garden (tier 7, 13 × 13, statues, topiary).
- **Ruin:** Weeds, broken pergola.
- **Terrain:** `flexible`.
- **Buildability watch:** flowers, leaves (medium).

#### Chapel (`csr:chapel`)
Tier 4 · zone inner · religious; 1 priest

- **Size:** 5 × 9, 9 tall. **Needs scaffolding:** no.
- **Look:** A small stone chapel with a bell cote over the gable (9 tall). Signature: the bell.
- **Inside:** Pews (stairs), an altar (lectern, candles), a bell.
- **Blocks per layer:** Shell: cobblestone, stone bricks, spruce stairs roof, door, glass panes. Fittings: pews, lectern, candles. Flourish L2: bell, lanterns, flowers. Flourish L3: colored glass, banner.
- **Budget:** Shell 150 · Fittings 20 · Flourish L2 +20 · L3 +20 = **210** blocks. First build (shell + fittings, 170): ~3,800 ticks (3.2 min); full: ~4,700 ticks (3.9 min).
- **Flow:** No material flow; festivals give renown.
- **Perks:** Blessing (renown).
- **Variants:** A: stone. B: timber. C: chapel in the keep (tier 7 solar corner).
- **Ruin:** Gable with the bell cote standing.
- **Terrain:** `ground_level`.
- **Buildability watch:** bell, candles (medium).

#### Church (`csr:church`)
Tier 5 · zone inner · religious (landmark); priest, bell ringer

- **Size:** 9 × 15, 20 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** Nave 10 high with a bell tower (20). Style A: stone with a timber porch; tier 8 grows to the cathedral. Signature: the bell tower.
- **Inside:** Pews, altar, bell, a small graveyard beside it.
- **Blocks per layer:** Shell: stone bricks, cobblestone, stone brick stairs roof, doors, glass. Fittings: pews, altar, bell, candles. Flourish L2: colored glass panes, banners, lanterns. Flourish L3: carvings (chiseled stone), flowers.
- **Budget:** Shell 520 · Fittings 60 · Flourish L2 +60 · L3 +60 = **700** blocks (150 above reach). First build (shell + fittings, 580): ~17,700 ticks (14.8 min); full: ~21,400 ticks (17.8 min).
- **Flow:** As chapel.
- **Perks:** Blessing: slow healing near it; more festival renown.
- **Variants:** A: tower at the front. B: tower at the side. C: twin towers (shorter).
- **Ruin:** Nave roofless, tower standing to half.
- **Terrain:** `stepped` (tower and nave).
- **Buildability watch:** above reach about 150.

#### Cathedral (`csr:cathedral`)
Tier 8 · zone inner · religious (landmark); clergy

- **Size:** 15 × 27, 28 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** A pale-stone cathedral with buttresses, a rose window (colored glass) and a spire to 28 (a taller spire is untested). Signature: the rose window.
- **Inside:** Nave, choir, side chapels, bells.
- **Blocks per layer:** Shell: calcite, stone bricks, deepslate roof, quartz trim, doors, glass. Fittings: pews, altar, bells, candles. Flourish L2: colored glass, banners, buttresses. Flourish L3: statues (props), gold-look spire tip.
- **Budget:** Shell 1300 · Fittings 120 · Flourish L2 +120 · L3 +150 = **1690** blocks (800 above reach). First build (shell + fittings, 1420): ~57,200 ticks (47.6 min); full: ~68,000 ticks (56.7 min).
- **Flow:** As church.
- **Perks:** Kingdom-wide blessing (renown).
- **Variants:** A: single spire. B: twin towers. C: domed (no spire).
- **Ruin:** Spire fallen, nave half-standing, rose window empty.
- **Terrain:** `stepped`.
- **Buildability watch:** above reach about 800.

#### Infirmary / hospital (`csr:infirmary`)
Tier 4 · zone inner · health; 1 healer (hospital: 3)

- **Size:** infirmary 7 × 9 (hospital, tier 6: 11 × 13, 12 tall), 7 tall. **Needs scaffolding:** no.
- **Look:** A white-plastered ward with beds in a row and a herb store. Hospital: two floors around a small courtyard. Signature: a red-cross banner (pattern: decoration step).
- **Inside:** Beds (6; hospital 14), a brewing stand corner (UNKNOWN low), cauldrons, herb shelves.
- **Blocks per layer:** Shell: white terracotta, oak, spruce stairs roof, doors. Fittings: beds, brewing stand, cauldron, shelves. Flourish L2: banners, lanterns, flower boxes. Flourish L3: banner pattern (decoration step), herb pots.
- **Budget:** Shell 190 · Fittings 45 · Flourish L2 +25 · L3 +25 = **285** blocks. First build (shell + fittings, 235): ~5,300 ticks (4.4 min); full: ~6,400 ticks (5.3 min). ⚠ first build 235 > 200.
- **Flow:** Producer: herbs (herb garden). Carrier: healer. Store: infirmary. Consumer: wounded soldiers.
- **Perks:** Healing (hospital: faster; fallen soldiers rebuilt from clay).
- **Variants:** A: ward. B: cloister hospital (tier 6). C: field hospital (tents).
- **Ruin:** Beds under fallen roof.
- **Terrain:** `ground_level`.
- **Buildability watch:** beds (medium).

#### Library or school (`csr:library`)
Tier 5 · zone inner · civic; a scholar

- **Size:** 7 × 9, 9 tall. **Needs scaffolding:** no.
- **Look:** A stone hall full of bookshelves with a reading table. Signature: a big round window (glass panes).
- **Inside:** Bookshelves, lecterns, tables, candles.
- **Blocks per layer:** Shell: stone bricks, oak, brick stairs roof, door, glass. Fittings: bookshelves, lecterns, tables. Flourish L2: candles, banners. Flourish L3: carpets, globe (prop).
- **Budget:** Shell 200 · Fittings 60 · Flourish L2 +25 · L3 +25 = **310** blocks. First build (shell + fittings, 260): ~5,800 ticks (4.9 min); full: ~7,000 ticks (5.8 min). ⚠ first build 260 > 200.
- **Flow:** No material flow; knowledge (renown bonus).
- **Perks:** Faster unlocks.
- **Variants:** A: library. B: school (benches). C: scriptorium tower.
- **Ruin:** Shelves toppled, roof gone.
- **Terrain:** `ground_level`.
- **Buildability watch:** candles (medium).

#### Town hall (`csr:town_hall`)
Tier 6 · zone inner (on the commons) · civic (landmark); a mayor, clerks

- **Size:** 11 × 13, 18 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** A half-timbered hall on an arcaded ground floor with a clock tower (18). Signature: the clock tower (a clock in an item frame: decoration step).
- **Inside:** Council room, records (bookshelves), the notice board outside.
- **Blocks per layer:** Shell: stone bricks arcade, white terracotta, dark oak, red stair roof, doors. Fittings: council table, chairs, bookshelves. Flourish L2: banners, lanterns. Flourish L3: clock (decoration step), flower boxes.
- **Budget:** Shell 560 · Fittings 60 · Flourish L2 +60 · L3 +60 = **740** blocks (200 above reach). First build (shell + fittings, 620): ~20,200 ticks (16.9 min); full: ~24,200 ticks (20.1 min).
- **Flow:** Records feed the kingdom book; no material flow.
- **Perks:** Order: larger build radius.
- **Variants:** A: arcaded hall. B: stone hall (style B). C: guild hall by the river.
- **Ruin:** Tower stump, arcade standing.
- **Terrain:** `stepped`.
- **Buildability watch:** above reach about 200.

#### Graveyard (`csr:graveyard`)
Tier 4 · zone outer (beside the chapel) · civic; the priest

- **Size:** 9 × 11, 3 tall. **Needs scaffolding:** no.
- **Look:** A fenced plot with gravestones (stone brick walls, chiseled stone), a yew (leaves), a gate opening. Signature: the yew tree.
- **Inside:** Graves, a bench.
- **Blocks per layer:** Shell: cobblestone wall, gravel paths. Fittings: gravestones, bench. Flourish L2: yew, lanterns. Flourish L3: flowers, mossy stones.
- **Budget:** Shell 50 · Fittings 20 · Flourish L2 +20 · L3 +15 = **105** blocks. First build (shell + fittings, 70): ~1,600 ticks (1.3 min); full: ~2,400 ticks (2.0 min).
- **Flow:** No flow (memory of fallen soldiers, a chronicle link).
- **Perks:** None.
- **Variants:** A: walled. B: hillside terraces. C: churchyard.
- **Ruin:** Overgrown, stones leaning (stairs).
- **Terrain:** `flexible`.
- **Buildability watch:** leaves (medium).

### Defense and military

#### Palisade segment and gate (`csr:palisade`)
Tier 2 · zone wall · defense

- **Size:** 5 long × 1 thick; the gate is 3 wide, 4 tall. **Needs scaffolding:** no.
- **Look:** Sharpened logs (spruce logs with fence tops), a walkway of slabs behind on posts. Gate (`csr:gate`): two log towers with an opening (no fence gate: BROKEN for passage). See the wall modules.
- **Inside:** —
- **Blocks per layer:** Shell: spruce logs, fences. Fittings: slab walkway, posts. Flourish L2: torches, banners. Flourish L3: moss, a lookout platform.
- **Budget:** Shell 25 · Fittings 8 · Flourish L2 +6 · L3 +6 = **45** blocks. First build (shell + fittings, 33): ~700 ticks (0.6 min); full: ~1,000 ticks (0.8 min).
- **Flow:** No flow.
- **Perks:** Encloses the keep zone.
- **Variants:** A: logs. B: stakes and earth bank. C: wattle (fences and leaves).
- **Ruin:** Gaps, fallen logs.
- **Terrain:** `stepped` (module rules).

#### Watchtower (`csr:watchtower`)
Tier 2 · zone wall or outer · defense; 1 lookout

- **Size:** 3 × 3, 9 tall. **Needs scaffolding:** no.
- **Look:** A log tower on four posts with a roofed platform at 7. Signature: a brazier (unlit campfire) on the platform.
- **Inside:** Stairs up (a spiral of stairs inside the posts), a platform.
- **Blocks per layer:** Shell: oak logs, spruce planks, slabs roof. Fittings: stairs, fence rail. Flourish L2: brazier, banner. Flourish L3: lantern, moss.
- **Budget:** Shell 60 · Fittings 15 · Flourish L2 +8 · L3 +8 = **91** blocks. First build (shell + fittings, 75): ~1,700 ticks (1.4 min); full: ~2,000 ticks (1.7 min).
- **Flow:** No flow.
- **Perks:** Sees attackers early (warfare stages).
- **Variants:** A: log. B: stone base. C: on a wall.
- **Ruin:** Leaning posts, platform gone.
- **Terrain:** `ground_level`.
- **Buildability watch:** campfire (medium).

#### Gatehouse and gates (`csr:gatehouse`)
Tier 5 · zone wall · defense (landmark: the main gate); 2 gate guards

- **Size:** 7 × 7, 12 tall. **Needs scaffolding:** yes (over 9 tall).
- **Look:** Two flanking towers with an arched passage, a walkway room above, a portcullis look (iron bars). Signature: banners over the arch.
- **Inside:** Guard room, stairs to the wall walk.
- **Blocks per layer:** Shell: stone bricks, cobblestone, stone brick stairs, doors. Fittings: guard beds, table, stairs. Flourish L2: banners, lanterns, iron bars. Flourish L3: banner patterns (decoration step), murder holes (trapdoors).
- **Budget:** Shell 330 · Fittings 25 · Flourish L2 +30 · L3 +30 = **415** blocks (80 above reach). First build (shell + fittings, 355): ~10,500 ticks (8.8 min); full: ~12,300 ticks (10.3 min).
- **Flow:** No flow.
- **Perks:** The only way through the wall; guards check visitors.
- **Variants:** A: twin towers. B: single tower gate. C: water gate.
- **Ruin:** Arch standing, towers broken.
- **Terrain:** `ground_level` (it sets the wall's height there).
- **Buildability watch:** above reach about 80.

#### Barracks (`csr:barracks`)
Tier 3 · zone inner · military; 6 soldiers

- **Size:** 7 × 11, 7 tall. **Needs scaffolding:** no.
- **Look:** A long stone and timber house with bunks. Signature: the training dummy at the door.
- **Inside:** 6 beds, chests, a table, a weapon rack.
- **Blocks per layer:** Shell: cobblestone, spruce, spruce stairs roof, doors. Fittings: beds, chests, table, racks. Flourish L2: banners, lanterns. Flourish L3: item frames, armor stands (decoration step).
- **Budget:** Shell 200 · Fittings 45 · Flourish L2 +20 · L3 +25 = **290** blocks. First build (shell + fittings, 245): ~5,500 ticks (4.6 min); full: ~6,500 ticks (5.4 min). ⚠ first build 245 > 200.
- **Flow:** Producer: none. Carrier: carriers bring gear and food. Store: footlockers. Consumer: 6 soldiers.
- **Perks:** Housing for the army.
- **Variants:** A: long house. B: two floors. C: in the bailey.
- **Ruin:** Roofless, bunks visible.
- **Terrain:** `skirt`.
- **Buildability watch:** beds (medium).

#### Training yard (`csr:training_yard`)
Tier 3 · zone inner · military; a drill master

- **Size:** 9 × 9, 3 tall. **Needs scaffolding:** no.
- **Look:** A fenced sand yard with dummies (fences, carved pumpkins, armor stands: decoration step) and archery targets (target blocks).
- **Inside:** Dummies, targets, a bench.
- **Blocks per layer:** Shell: fences, sand or gravel floor (flexible). Fittings: targets, dummies, bench. Flourish L2: banners. Flourish L3: armor stands (decoration step).
- **Budget:** Shell 40 · Fittings 20 · Flourish L2 +10 · L3 +15 = **85** blocks. First build (shell + fittings, 60): ~1,400 ticks (1.1 min); full: ~1,900 ticks (1.6 min).
- **Flow:** No flow.
- **Perks:** Drilled soldiers.
- **Variants:** A: yard. B: archery range. C: tilting lists.
- **Ruin:** Broken fences.
- **Terrain:** `flexible`.
- **Buildability watch:** sand (gravity, medium).

#### Siege workshop (`csr:siege_workshop`)
Tier 6 · zone outer · military workshop; 2 engineers

- **Size:** 9 × 11, 8 tall. **Needs scaffolding:** no.
- **Look:** A big open-sided timber shed with a half-built catapult (logs, fences). Signature: the catapult arm.
- **Inside:** Crafting tables, log piles, a catapult prop.
- **Blocks per layer:** Shell: oak logs, spruce, slabs roof. Fittings: crafting tables, log piles. Flourish L2: catapult prop, banners. Flourish L3: lanterns.
- **Budget:** Shell 180 · Fittings 30 · Flourish L2 +50 · L3 +40 = **300** blocks. First build (shell + fittings, 210): ~4,700 ticks (3.9 min); full: ~6,800 ticks (5.6 min). ⚠ first build 210 > 200.
- **Flow:** Producer: siege engines (later). Carrier: engineers. Store: the shed. Consumer: warfare stages.
- **Perks:** Siege engines.
- **Variants:** A: shed. B: yard. C: riverside with a barge.
- **Ruin:** Collapsed shed, catapult skeleton.
- **Terrain:** `flexible`.

### Wall modules (NEW)

The kingdom wall is a loop that follows uneven ground ([D3](DECISIONS.md)); it is built from modules, each a small
separate build. Organic wall layout testing is Stage T, later.

**How modules join across height changes:**
- **Wall walk:** every module has a wall-walk height (its top walkable layer) at each end; neighbours must match.
- **Small changes:** a **stepped** module changes the walk by 1 (stairs on the walk) or 2 (two flights) over its
  5-block length.
- **Bigger changes:** a **tower** or **tower-integrated corner** module is the joint. Its inside stair connects walks
  up to 4 apart.
- **More than 4 between neighbours:** the loop bends around the step (layout), or the segment is skipped on a cliff
  (TERRAIN.md: "a wall segment on a cliff is skipped").
- **Foundation:** each module's wall body goes down to the ground under it (a `skirt`), so the top follows the walk
  and the base follows the ground.

| Module | Size (stone / palisade) | Walk change | Notes |
|---|---|---|---|
| Straight | 5 × 2 × 6 / 5 × 1 × 4 | 0 | Crenellated stone (walls on top) or log points |
| Stepped | 5 × 2 × 6–8 / 5 × 1 × 4–6 | ±1 or ±2 | Stairs on the walk |
| Corner | 2 × 2 × 6 / 1 × 1 × 4 | 0 | Outside or inside corner |
| Tower-integrated corner | 5 × 5 × 9 / 3 × 3 × 6 | up to ±4 | Inside stair; 9 tall stays without scaffolding (landmark towers up to 13 use it) |
| Gate | 7 × 5 × 9 / 5 × 3 × 6 | 0 | Arched opening (no fence gate) |
| Wall-walk stairs | 2 × 4 × 6 | — | From the ground to the walk (soldiers can't climb ladders) |
| Moat or ditch segment | 5 × 4 × 2 | — | Two raised earth banks with stone brick edging and a 2-wide channel between (builders can't dig); water is a decoration step |

#### Stone wall module, straight (`csr:stone_wall`)
Tier 3 · zone wall · defense

- **Size:** 5 × 2, 6 tall. **Needs scaffolding:** no.
- **Look:** Stone brick wall 5 high with crenellations and a 2-wide walk. Other modules: see the table above.
- **Inside:** —
- **Blocks per layer:** Shell: stone bricks, cobblestone footing. Fittings: walk slabs. Flourish L2: banners, torches. Flourish L3: moss, cracks, vines.
- **Budget:** Shell 45 · Fittings 5 · Flourish L2 +5 · L3 +8 = **63** blocks. First build (shell + fittings, 50): ~1,100 ticks (0.9 min); full: ~1,400 ticks (1.2 min). ⚠ 63 > 60.
- **Flow:** No flow.
- **Perks:** Defense.
- **Variants:** A: stone. B: stone with a timber hoarding. C: brick (style C).
- **Ruin:** Broken crenels, a breach.
- **Terrain:** `stepped` (module rules).

#### Wall tower (`csr:wall_tower`)
Tier 3 · zone wall · defense; 1 archer

- **Size:** 5 × 5, 9 tall. **Needs scaffolding:** no.
- **Look:** A square tower on the wall with crenellations, 9 tall (no scaffolding); a landmark version reaches 13 with a cone roof. Signature: arrow slits.
- **Inside:** Inside stair, an archer platform.
- **Blocks per layer:** Shell: stone bricks, cobblestone. Fittings: stairs, slabs. Flourish L2: banners, torches. Flourish L3: cone roof (spruce stairs), moss.
- **Budget:** Shell 110 · Fittings 15 · Flourish L2 +10 · L3 +25 = **160** blocks. First build (shell + fittings, 125): ~2,800 ticks (2.3 min); full: ~3,600 ticks (3.0 min).
- **Flow:** No flow.
- **Perks:** Archers on the wall.
- **Variants:** A: square. B: round-looking. C: open-backed.
- **Ruin:** Top half gone.
- **Terrain:** `ground_level` joint (module rules).

### Infrastructure and life

#### Road pieces (dirt, gravel, cobble) (`csr:road`)
Tier 1 · zone all · infrastructure

- **Size:** 3 wide × 5 long per piece, 1 tall. **Needs scaffolding:** no.
- **Look:** Dirt road (tier 1: coarse dirt and packed mud; dirt path is BROKEN), gravel (tier 2), cobblestone with slab edges (tier 4). Replaces the top ground block where it can (builders can only place into air or replaceable blocks, so a road is laid on top as slabs or by a code step; see the note).
- **Inside:** —
- **Blocks per layer:** Shell: coarse dirt / gravel / cobblestone slabs. Fittings: —. Flourish L2: edge stones (cobblestone walls). Flourish L3: lamp posts every 8.
- **Budget:** Shell 15 · Fittings 0 · Flourish L2 +4 · L3 +4 = **23** blocks. First build (shell + fittings, 15): ~300 ticks (0.3 min); full: ~500 ticks (0.4 min).
- **Flow:** No flow; carriers walk faster on roads (later).
- **Perks:** None.
- **Variants:** A: straight. B: curve. C: junction.
- **Ruin:** Grass through the stones.
- **Terrain:** `flexible` (follows the ground).
- **Buildability watch:** Builders can't replace existing ground (grass is not replaceable), so roads are slabs on top or need a code step.

#### Bridge (`csr:bridge`)
Tier 3 · zone all · infrastructure

- **Size:** 3 wide × up to 11 long, 5 tall. **Needs scaffolding:** no.
- **Look:** Wooden bridge on log piers (tier 3); stone arched bridge (tier 5). Signature: the stone arch.
- **Inside:** —
- **Blocks per layer:** Shell: oak logs and planks / stone bricks and stairs. Fittings: fence or wall rails. Flourish L2: lanterns. Flourish L3: banners, moss.
- **Budget:** Shell 70 · Fittings 10 · Flourish L2 +8 · L3 +8 = **96** blocks. First build (shell + fittings, 80): ~1,800 ticks (1.5 min); full: ~2,200 ticks (1.8 min).
- **Flow:** No flow.
- **Perks:** Crossings for carriers.
- **Variants:** A: wood. B: stone arch. C: covered bridge.
- **Ruin:** Span broken in the middle.
- **Terrain:** `stepped` (each end sits on its bank).
- **Buildability watch:** piers placed into water (medium).

#### Fountain (`csr:fountain`)
Tier 5 · zone inner · decoration

- **Size:** 5 × 5, 4 tall. **Needs scaffolding:** no.
- **Look:** A stone basin with a central pillar. Water is a decoration step.
- **Inside:** —
- **Blocks per layer:** Shell: stone bricks, stone brick slabs and walls. Fittings: —. Flourish L2: flowers, lanterns. Flourish L3: statue top.
- **Budget:** Shell 35 · Fittings 0 · Flourish L2 +8 · L3 +8 = **51** blocks. First build (shell + fittings, 35): ~800 ticks (0.7 min); full: ~1,100 ticks (1.0 min).
- **Flow:** No flow.
- **Perks:** None (commons level 2).
- **Variants:** A: round. B: wall fountain. C: tiered.
- **Ruin:** Dry and cracked.
- **Terrain:** `ground_level`.
- **Buildability watch:** water (decoration step).

#### Statue (`csr:statue`)
Tier 6 · zone inner · decoration

- **Size:** 3 × 3, 6 tall. **Needs scaffolding:** no.
- **Look:** A plinth with a figure of the king (stone, armor stand: decoration step). Signature: gold-look crown (yellow terracotta).
- **Inside:** —
- **Blocks per layer:** Shell: stone bricks, smooth stone. Fittings: —. Flourish L2: flowers, lanterns. Flourish L3: gold block crown.
- **Budget:** Shell 30 · Fittings 0 · Flourish L2 +6 · L3 +6 = **42** blocks. First build (shell + fittings, 30): ~700 ticks (0.6 min); full: ~900 ticks (0.8 min). ⚠ 42 > 30.
- **Flow:** No flow.
- **Perks:** Renown (commons level 3).
- **Variants:** A: king. B: soldier. C: obelisk.
- **Ruin:** Toppled figure.
- **Terrain:** `ground_level`.

#### Lamp post (`csr:lamp_post`)
Tier 4 · zone all · decoration

- **Size:** 1 × 1, 3 tall. **Needs scaffolding:** no.
- **Look:** A fence post with a hanging lantern on a short arm.
- **Inside:** —
- **Blocks per layer:** Shell: fence or wall post. Fittings: —. Flourish L2: lantern. Flourish L3: chain.
- **Budget:** Shell 3 · Fittings 0 · Flourish L2 +1 · L3 +1 = **5** blocks. First build (shell + fittings, 3): ~100 ticks (0.1 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow.
- **Perks:** Lit streets.
- **Variants:** A: wood. B: iron (iron bars). C: stone.
- **Ruin:** Lantern gone, post leaning.
- **Terrain:** `flexible`.
- **Buildability watch:** lanterns (medium).

#### Banner pole (`csr:banner_pole`)
Tier 4 · zone all · decoration

- **Size:** 1 × 1, 4 tall. **Needs scaffolding:** no.
- **Look:** A fence pole with the kingdom's banner (plain; pattern in the decoration step).
- **Inside:** —
- **Blocks per layer:** Shell: fence post. Fittings: —. Flourish L2: banner. Flourish L3: pattern (decoration step).
- **Budget:** Shell 3 · Fittings 0 · Flourish L2 +1 · L3 +0 = **4** blocks. First build (shell + fittings, 3): ~100 ticks (0.1 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow.
- **Perks:** None.
- **Variants:** A: pole. B: wall banner. C: hanging from towers.
- **Ruin:** Torn (gone).
- **Terrain:** `flexible`.
- **Buildability watch:** banners (medium).

### Small props (NEW)

Reusable mini-structures the layout planner places to give citizens places to go.

#### Bench (`csr:bench`) **NEW**
Tier 1 · zone inner · prop

- **Size:** 2 × 1, 1 tall. **Needs scaffolding:** no.
- **Look:** Two oak stairs with trapdoor arms.
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 2 · Fittings 0 · Flourish L2 +2 · L3 +0 = **4** blocks. First build (shell + fittings, 2): ~0 ticks (0.0 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: wood. B: stone. C: log.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Fire pit (`csr:fire_pit`) **NEW**
Tier 1 · zone inner · prop

- **Size:** 3 × 3, 1 tall. **Needs scaffolding:** no.
- **Look:** Cobblestone slab ring around a campfire (unlit by default), log seats.
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 8 · Fittings 1 · Flourish L2 +2 · L3 +0 = **11** blocks. First build (shell + fittings, 9): ~200 ticks (0.2 min); full: ~200 ticks (0.2 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: ring. B: brazier. C: cooking spit.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Notice board (`csr:notice_board`) **NEW**
Tier 2 · zone inner · prop

- **Size:** 2 × 1, 2 tall. **Needs scaffolding:** no.
- **Look:** Two fence posts with spruce trapdoors and signs (text: decoration step).
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 4 · Fittings 0 · Flourish L2 +2 · L3 +0 = **6** blocks. First build (shell + fittings, 4): ~100 ticks (0.1 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: board. B: kiosk. C: wall-mounted.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Market stall (`csr:market_stall`) **NEW**
Tier 3 · zone inner · prop

- **Size:** 3 × 2, 3 tall. **Needs scaffolding:** no.
- **Look:** A plank counter, fence posts and a striped wool awning.
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 10 · Fittings 3 · Flourish L2 +4 · L3 +0 = **17** blocks. First build (shell + fittings, 13): ~300 ticks (0.2 min); full: ~400 ticks (0.3 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: awning. B: cart. C: tent.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Signpost (`csr:signpost`) **NEW**
Tier 1 · zone inner · prop

- **Size:** 1 × 1, 2 tall. **Needs scaffolding:** no.
- **Look:** A fence post with arrow signs.
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 1 · Fittings 0 · Flourish L2 +2 · L3 +0 = **3** blocks. First build (shell + fittings, 1): ~0 ticks (0.0 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: wood. B: stone. C: milestone.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Hitching post (`csr:hitching_post`) **NEW**
Tier 2 · zone inner · prop

- **Size:** 3 × 1, 1 tall. **Needs scaffolding:** no.
- **Look:** Fence rail with a hay bale and a trough (cauldron).
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 5 · Fittings 2 · Flourish L2 +1 · L3 +0 = **8** blocks. First build (shell + fittings, 7): ~200 ticks (0.1 min); full: ~200 ticks (0.1 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: rail. B: post. C: water trough.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Well variant (small) (`csr:well_small`) **NEW**
Tier 1 · zone inner · prop

- **Size:** 2 × 2, 2 tall. **Needs scaffolding:** no.
- **Look:** A tiny well: walls ring, slab roof (water: decoration step).
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 8 · Fittings 0 · Flourish L2 +2 · L3 +0 = **10** blocks. First build (shell + fittings, 8): ~200 ticks (0.1 min); full: ~200 ticks (0.2 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: stone. B: wood. C: pump.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

#### Flower bed (`csr:flower_bed`) **NEW**
Tier 1 · zone inner · prop

- **Size:** 3 × 1, 1 tall. **Needs scaffolding:** no.
- **Look:** A trapdoor edge with flowers on grass or dirt.
- **Inside:** —
- **Blocks per layer:** Shell: see look. Fittings: —. Flourish L2: small additions. Flourish L3: —.
- **Budget:** Shell 3 · Fittings 3 · Flourish L2 +0 · L3 +0 = **6** blocks. First build (shell + fittings, 6): ~100 ticks (0.1 min); full: ~100 ticks (0.1 min).
- **Flow:** No flow; a place citizens use.
- **Perks:** None.
- **Variants:** A: border. B: planter (barrel). C: wild.
- **Ruin:** Broken or overgrown.
- **Terrain:** `flexible`.

## Budget overview

All briefs, full build (every layer). ⚠ = over a proposed limit (see [Scale and detail rules](#scale-and-detail-rules)).

| Structure | Tier | Zone | Blocks | Full build | Flags |
|---|---|---|---|---|---|
| Keep, tier 1: small cobblestone keep | 1 | keep | 400 | ~9,000 ticks (7.5 min) | — |
| Keep, tier 2: stone hall with timber palisade | 2 | keep | 515 | ~11,600 ticks (9.7 min) | — |
| Keep, tier 3: hall with courtyard and gatehouse | 3 | keep | 810 | ~18,200 ticks (15.2 min) | — |
| Keep, tier 4: two-floor stone keep with corner towers | 4 | keep | 1240 | ~41,000 ticks (34.2 min) | — |
| Keep, tier 5: castle with curtain walls and bailey | 5 | keep | 1000 | ~31,900 ticks (26.6 min) | — |
| Keep, tier 6: great hall, throne room, barracks wing | 6 | keep | 1320 | ~37,200 ticks (31.0 min) | — |
| Keep, tier 7: bigger castle with moat or ditch | 7 | keep | 880 | ~36,700 ticks (30.6 min) | — |
| Keep, tier 8: concentric inner and outer ward | 8 | keep | 1800 | ~66,800 ticks (55.6 min) | — |
| Keep, tier 9: grand castle with many towers and an armory | 9 | keep | 1550 | ~61,100 ticks (50.9 min) | — |
| Keep, tier 10: fortress | 10 | keep | 1780 | ~70,000 ticks (58.4 min) | — |
| Hovel or tent | 1 | inner | 46 | ~1,000 ticks (0.9 min) | — |
| Cottage (levels 1 to 3) | 1 | inner | 184 | ~4,100 ticks (3.5 min) | — |
| Inn / visitors' camp: camp stage | 1 | inner (by the gate) | 120 | ~2,700 ticks (2.2 min) | — |
| Inn / visitors' camp: inn stage | 4 | inner (by the gate) | 315 | ~7,100 ticks (5.9 min) | ⚠ first build 255 > 200 |
| Townhouse | 4 | inner | 265 | ~6,000 ticks (5.0 min) | ⚠ first build 205 > 200 |
| Noble house | 5 | inner (near the keep) | 420 | ~9,400 ticks (7.9 min) | ⚠ first build 310 > 200; ⚠ full 420 > 350 |
| King's guard quarters | 6 | keep | 350 | ~7,900 ticks (6.6 min) | ⚠ first build 290 > 200 |
| Farm (levels 1 to 3) | 1 | outer | 140 | ~3,200 ticks (2.6 min) | — |
| Lumber camp / tree farm | 1 | outer | 102 | ~2,300 ticks (1.9 min) | — |
| Butcher / meat hall | 3 | inner | 165 | ~3,700 ticks (3.1 min) | — |
| Bakery | 3 | inner | 170 | ~3,800 ticks (3.2 min) | — |
| Tavern / food hall | 4 | inner (by the commons) | 345 | ~7,800 ticks (6.5 min) | ⚠ first build 280 > 200 |
| Fishing dock | 2 | outer (on water) | 95 | ~2,100 ticks (1.8 min) | — |
| Pens and stables | 2 | outer | 185 | ~4,200 ticks (3.5 min) | — |
| Storehouse | 3 | inner (by the keep and the main road) | 270 | ~6,100 ticks (5.1 min) | ⚠ first build 230 > 200 |
| Mine (stages 1 to 3) | 3 | outer | 170 | ~3,800 ticks (3.2 min) | — |
| Quarry | 3 | outer | 88 | ~2,000 ticks (1.6 min) | — |
| Smithy | 3 | inner | 165 | ~3,700 ticks (3.1 min) | — |
| Tannery | 3 | outer | 132 | ~3,000 ticks (2.5 min) | — |
| Carpenter or fletcher | 2 | inner | 147 | ~3,300 ticks (2.8 min) | — |
| Stonemason | 4 | inner | 125 | ~2,800 ticks (2.3 min) | — |
| Armory | 6 | inner (keep side) | 310 | ~7,000 ticks (5.8 min) | ⚠ first build 250 > 200 |
| Well | 1 | inner | 30 | ~700 ticks (0.6 min) | — |
| Market square | 4 | inner | 221 | ~5,000 ticks (4.1 min) | — |
| Commons / town green (levels 1 to 3) | 2 | inner (main road junction near the keep gate, next to the tavern) | 245 | ~5,500 ticks (4.6 min) | — |
| Garden (levels 1 to 3) | 2 | inner | 130 | ~2,900 ticks (2.4 min) | — |
| Chapel | 4 | inner | 210 | ~4,700 ticks (3.9 min) | — |
| Church | 5 | inner | 700 | ~21,400 ticks (17.8 min) | — |
| Cathedral | 8 | inner | 1690 | ~68,000 ticks (56.7 min) | — |
| Infirmary / hospital | 4 | inner | 285 | ~6,400 ticks (5.3 min) | ⚠ first build 235 > 200 |
| Library or school | 5 | inner | 310 | ~7,000 ticks (5.8 min) | ⚠ first build 260 > 200 |
| Town hall | 6 | inner (on the commons) | 740 | ~24,200 ticks (20.1 min) | — |
| Graveyard | 4 | outer (beside the chapel) | 105 | ~2,400 ticks (2.0 min) | — |
| Palisade segment and gate | 2 | wall | 45 | ~1,000 ticks (0.8 min) | — |
| Watchtower | 2 | wall or outer | 91 | ~2,000 ticks (1.7 min) | — |
| Gatehouse and gates | 5 | wall | 415 | ~12,300 ticks (10.3 min) | — |
| Barracks | 3 | inner | 290 | ~6,500 ticks (5.4 min) | ⚠ first build 245 > 200 |
| Training yard | 3 | inner | 85 | ~1,900 ticks (1.6 min) | — |
| Siege workshop | 6 | outer | 300 | ~6,800 ticks (5.6 min) | ⚠ first build 210 > 200 |
| Stone wall module, straight | 3 | wall | 63 | ~1,400 ticks (1.2 min) | ⚠ 63 > 60 |
| Wall tower | 3 | wall | 160 | ~3,600 ticks (3.0 min) | — |
| Road pieces (dirt, gravel, cobble) | 1 | all | 23 | ~500 ticks (0.4 min) | — |
| Bridge | 3 | all | 96 | ~2,200 ticks (1.8 min) | — |
| Fountain | 5 | inner | 51 | ~1,100 ticks (1.0 min) | — |
| Statue | 6 | inner | 42 | ~900 ticks (0.8 min) | ⚠ 42 > 30 |
| Lamp post | 4 | all | 5 | ~100 ticks (0.1 min) | — |
| Banner pole | 4 | all | 4 | ~100 ticks (0.1 min) | — |
| Bench | 1 | inner | 4 | ~100 ticks (0.1 min) | — |
| Fire pit | 1 | inner | 11 | ~200 ticks (0.2 min) | — |
| Notice board | 2 | inner | 6 | ~100 ticks (0.1 min) | — |
| Market stall | 3 | inner | 17 | ~400 ticks (0.3 min) | — |
| Signpost | 1 | inner | 3 | ~100 ticks (0.1 min) | — |
| Hitching post | 2 | inner | 8 | ~200 ticks (0.1 min) | — |
| Well variant (small) | 1 | inner | 10 | ~200 ticks (0.2 min) | — |
| Flower bed | 1 | inner | 6 | ~100 ticks (0.1 min) | — |

**Over a limit** (raise the limit, trim fittings, treat as a small landmark, or split into two builds):

- Inn / visitors' camp: inn stage: ⚠ first build 255 > 200
- Townhouse: ⚠ first build 205 > 200
- Noble house: ⚠ first build 310 > 200; ⚠ full 420 > 350
- King's guard quarters: ⚠ first build 290 > 200
- Tavern / food hall: ⚠ first build 280 > 200
- Storehouse: ⚠ first build 230 > 200
- Armory: ⚠ first build 250 > 200
- Infirmary / hospital: ⚠ first build 235 > 200
- Library or school: ⚠ first build 260 > 200
- Barracks: ⚠ first build 245 > 200
- Siege workshop: ⚠ first build 210 > 200
- Stone wall module, straight: ⚠ 63 > 60
- Statue: ⚠ 42 > 30

**Notes:**
- **Common buildings:** every tier-1 building finishes its first build in under 4 minutes. The two-floor common
  buildings of tiers 3 to 6 mostly go over the 200-block first-build limit: the main thing the owner may want to tune.
- **Top keeps:** the tier 8 to 10 keeps take close to an hour each (about 3 in-game days) with 3 builders, plus their
  wall modules. More builders per site shorten that roughly in proportion, until they get in each other's way (not
  measured).
- **Height:** nothing is over 28 tall; the tier-10 keep and the cathedral are exactly at the limit.

## Pipeline reference

> Moved here from the first version of this file (stage 0); unchanged except for this note.

### Pipeline

- **Geometry:** a vanilla structure file (`.nbt`, edit it with structure blocks) at
  `common/src/main/resources/data/csr/structure/<path>.nbt`, i.e. structure location `csr:<path>`.
- **Metadata:** one JSON file per structure at `common/src/main/resources/data/csr/csr/structures/<name>.json`:

  | Field | Required | Meaning |
  |---|---|---|
  | `id` | yes | Unique id, e.g. `csr:test_hut` (duplicates are reported). |
  | `name` | yes | Display name. |
  | `design` | yes | `ready` (has geometry) or `TBD` (placeholder, no geometry yet). |
  | `tier` | yes | 1–10. |
  | `zone` | yes | `keep`, `inner`, `wall` or `outer` (the soft rings, [D3](DECISIONS.md)). |
  | `role` | yes | keep, house, farm, resource, storage, defense, gate, military, workshop, market, religious, health, food, utility, decoration. |
  | `footprint` | ready only | Size `[x, y, z]`; must match the structure file. |
  | `cost` | no | `"auto"` (default: counted from the blocks, the way builders count them) or `{"minecraft:cobblestone": 30, ...}`. |
  | `variant_group` | no | Structures that are variants of each other share this. |
  | `palette` | no | Material palette name, e.g. `oak`. |
  | `ruin` | no | Structure file of the ruined version, e.g. `csr:kingdom/test_hut_ruin`. |
  | `structure` | ready only | Structure file location, e.g. `csr:kingdom/test_hut`. |

- **Code:** `common/.../structure/` (`StructureMeta`, `StructureRegistry`). Structure files are read through vanilla's
  structure manager, which looks in the world's `generated` folder first (structure-block saves), then data packs
  and the mod.
- **Validation** (on load, `/reload`, `/csr dev structure reload`): every ready entry's structure file exists and its
  size matches the footprint; zones, roles, tiers and design are legal; ids are unique; cost items exist; the ruin
  file exists. Problems go to the game log (`CSR structures: ...`) and the STRUCTURE recorder category.
  `StructureRegistryGameTests.shippedRegistryIsValid` fails if the shipped registry has any problem.
- **Facing:** a structure's local z = 0 side is its front, the side towards the easel (builders) or towards you
  (paste tool).

### Test structures (ready)

Made by `tools/make_test_structures.py`.

| id | Size | Zone | Role | What |
|---|---|---|---|---|
| `csr:test_hut` | 5×3×5 | inner | house | Cobblestone walls, oak door in the front, plank roof. |
| `csr:test_well` | 3×2×3 | inner | utility | Cobblestone ring with water, fence posts. |
| `csr:test_wall` | 5×3×1 | wall | defense | Stone brick wall segment. |

### Placeholders (design: TBD, no geometry)

> The briefs above supersede these names, tiers and zones once structure files are made; only the storehouse's tier changes (1 → 3, D47), and many NEW ids are added. Metadata files are updated when each structure is generated.


| id | Name | Tier | Zone | Role |
|---|---|---|---|---|
| `csr:keep` | Keep | 1 | keep | keep |
| `csr:cottage` | Cottage | 1 | inner | house |
| `csr:farm` | Farm | 1 | outer | farm |
| `csr:lumber_camp` | Lumber camp | 1 | outer | resource |
| `csr:storehouse` | Storehouse | 1 | inner | storage |
| `csr:palisade` | Palisade segment | 2 | wall | defense |
| `csr:gate` | Gate | 2 | wall | gate |
| `csr:stone_wall` | Stone wall segment | 3 | wall | defense |
| `csr:wall_tower` | Wall tower | 3 | wall | defense |
| `csr:barracks` | Barracks | 3 | inner | military |
| `csr:smithy` | Smithy | 3 | inner | workshop |
| `csr:quarry` | Quarry | 3 | outer | resource |
| `csr:market` | Market | 4 | inner | market |
| `csr:chapel` | Chapel | 4 | inner | religious |
| `csr:infirmary` | Infirmary | 4 | inner | health |
| `csr:food_hall` | Food hall | 4 | inner | food |

### Paste tool (development runs only)

| Command | Book (Structures) | Does |
|---|---|---|
| `/csr dev structure place <id> [north\|east\|south\|west]` | one "Paste" button per ready structure | Pastes the whole structure instantly. You stand where an easel would stand; the structure goes the way you face (or the given direction), starting 2 blocks away, centered. These are the same rules as builders and `/csr build`. |
| `/csr dev structure undo` | Undo last paste | Puts back the blocks the last paste replaced, including chest contents. Keeps the last `dev.paste_undo_depth` (10) pastes, in memory only. |
| `/csr dev structure reload` | Reload structures | Re-reads metadata and structure files from disk and validates them. |
| `/csr dev structure list` | List all | Every entry, including placeholders, plus problems. |

A paste is an exact copy of the file:
- Air inside the structure is placed too.
- Containers it replaces are emptied first, so nothing drops.
- No shape updates are sent, so nothing outside the box changes. Fences won't link up with blocks outside the structure.

The STRUCTURE recorder category logs pastes, undos, reloads and validation problems.

### Touch-up workflow (edit a structure in game)

1. **Paste it in the file's own orientation:** book → Structures → Paste, while facing **south**
   (or `/csr dev structure place csr:test_hut south`). The chat then prints exactly where to put the structure block
   and what to type into it.
2. **Edit the blocks by hand.**
3. **Save with a structure block** (`/give @s minecraft:structure_block`). Place it where the chat said: one block
   below the paste's first corner. Set:
   - mode **Save**;
   - name = the structure file, e.g. `csr:kingdom/test_hut`;
   - relative position `0 1 0`;
   - size = the printed size (e.g. `5 3 5`).

   Click **SAVE**. Minecraft writes `neoforge/run/saves/<world>/generated/csr/structures/kingdom/test_hut.nbt`.
4. **Reload:** book → Structures → **Reload structures**. Pastes now use your edited file. The world's `generated`
   folder wins over the mod's copy.
5. **Make it permanent:** copy that file over `common/src/main/resources/data/csr/structure/kingdom/test_hut.nbt`.
   If the size changed, update `footprint` in `common/src/main/resources/data/csr/csr/structures/test_hut.json`.
   Then delete the copy in the world's `generated` folder, or it keeps winning. Commit.

Notes:
- Air you leave inside the box is saved as air.
- A structure void block (`/give @s minecraft:structure_void`) marks "leave whatever is there".
- Builders' blueprints (the easel) don't read the `generated` folder; only the paste tool and the registry do.
- **New structure:** build it, save it with a structure block under a new name (e.g. `csr:kingdom/my_house`), copy the
  file into `common/src/main/resources/data/csr/structure/kingdom/`, add a metadata file next to the others in
  `data/csr/csr/structures/`, then run `./gradlew :neoforge:processResources` and Reload structures (or restart).

