# Roadmap — DRAFT v0, sizes are guesses, everything can change

Related: [`DESIGN.md`](DESIGN.md) (pillars, decision protocol), [`DECISIONS.md`](DECISIONS.md) (decision log).
Earlier work (M0–M2: testing, bug fixes, `/csr build`, builder fixes, doors) is listed in `CLAUDE.md`.

## Status

| # | Stage | Size | Status |
|---|---|---|---|
| 0 | Foundations | M | **done** (in-game checklist pending) |
| 1 | Kingdom core | M | **done** (in-game checklist pending) |
| 2 | Terrain spike | L | **done** (in-game checklist pending) |
| 3 | Tall construction spike | L | **done** (in-game checklist pending) |
| 4 | Tier-1 keep built autonomously | L | **in progress** (draft, not in the real mod yet) |
| 5 | Economy loop v1 | L | not started |
| 5a | Decoration step | M | not started |
| 6 | Layout and growth | XL | not started |
| 7 | Performance checkpoint | M | not started |
| 8 | Content tiers 2–5 | XL | not started |
| T | Organic wall and layout testing | L | not started |
| 9 | Mining, staged | XL | not started |
| 10 | Life layer | XL | not started |
| 11 | Events and defense | L | not started |
| 12 | Rivals and war | XL | not started |
| 13 | Decay and ruins | L | not started |
| 14 | Tiers 6–10 and polish | XL | not started |

## How to read this

Stages are ordered by dependency and risk. Every stage ends playable.

## Definition of done (every stage)

- Required tests pass, and nothing that passed before fails.
- A recording was reviewed.
- The owner did the in-game checklist.
- `docs/DESIGN.md` and `docs/DECISIONS.md` are updated.
- A flow audit is done for every new structure or resource.
- The work is pushed to origin.

## Per-stage template

**Goal** · **Acceptance test** · **Depends on** · **Size** (S/M/L/XL, a guess) · **Risks** ·
**Decisions** (links into `DECISIONS.md`) · **Status** · **Results**

## Stages

### 0 Foundations
- **Goal:** the design docs are saved; a tuning data file; a structure registry with tags; a book button that pastes any structure into the world; a layout-checker skeleton.
- **Acceptance test:** the owner pastes a test structure from the book and changes a number in the tuning file.
- **Depends on:** — · **Size:** M
- **Risks:** choosing the tuning file format.
- **Decisions:** [D8–D15](DECISIONS.md)
- **Status:** done (2026-10-03); the owner's in-game checklist and recording review are pending.
- **Results:**
  - **Tuning:** `data/csr/tuning/tuning.json`, 9 keys (`docs/TUNING.md`), book Tuning (reload, show).
  - **Registry:** 3 test structures plus 16 placeholders (`docs/STRUCTURES.md`).
  - **Paste tool:** `/csr dev structure place|undo|reload|list`, book Structures.
  - **Layout checker:** a pure-data skeleton.
  - **Recorder:** the STRUCTURE category.
  - **Tests:** 82 required (60 before), all pass.

### 1 Kingdom core
- **Goal:** a saved Kingdom object (area, king, stockpile numbers, building list, population, renown), founded by the gold-hat king, that survives save and reload. Observer v1: a status page and a chronicle book.
- **Acceptance test:** a founded kingdom survives a restart and is visible in the book.
- **Depends on:** 0 · **Size:** M
- **Risks:** the saved-data format (needs a migration note).
- **Decisions:** [D1](DECISIONS.md), [D16–D23](DECISIONS.md)
- **Status:** done (2026-10-03); the owner's in-game checklist and recording review are pending.
- **Results:**
  - **Saved kingdoms:** `csr_kingdoms.dat`, data version 1.
  - **Founding:** gold block on your king, or `/csr kingdom found`, with five refusals; disband.
  - **King death:** sets RUINED and writes a chronicle line.
  - **Stockpile:** all or nothing, shortfall reported.
  - **Census:** counted only when the whole claim is loaded.
  - **Chronicle:** capped by `kingdom.chronicle_max`.
  - **Observer:** kingdom book and `/csr kingdom` commands.
  - **Recorder:** KINGDOM category.
  - **Flow audit:** the stockpile is a store with no producer or consumer yet; only dev commands fill it (stage 5
    adds the first loop).
  - **Tests:** 105 required (82 before), all pass.
  - **Follow-up (2026-10-04):** the kingdom book refreshes when opened, in the hand or on a lectern (D24); 109 tests.

### 2 Terrain spike
- **Goal:** site scoring, foundations or terraforming; founding refuses or warns on bad ground.
- **Acceptance test:** a keep foundation on a slope and one near water are handled; a cliff is refused with a reason.
- **Depends on:** 1 · **Size:** L
- **Risks:** may change the keep design.
- **Decisions:** D25–D32 · **Status:** done (2026-10-04), waiting for the in-game checklist · **Results:**
  - **Research:** `docs/RESEARCH-terrain.md` (vanilla terrain adaptation plus 7 open-source projects).
  - **Scan, scorer, search:** loaded chunks only (unknown otherwise); radius 64 scans in 1.5 ms.
  - **Grading:** five methods applied instantly with undo, compared on the test terrains; comparison and
    recommendation in `docs/TERRAIN.md`.
  - **Founding:** refuses cliff, lava, deep water and too steep ground with the best site nearby; warns on soft
    penalties; operators can force it.
  - **Dev tools and recorder:** Terrain category in the dev book; TERRAIN recorder category.
  - **Acceptance:** a slope gets a foundation (123 blocks) or cut and fill (165); a lake edge gets a foundation (79);
    a cliff is refused at founding with its reason.
  - **Recommendation:** no work, then cut and fill (range 2 to 3), then foundation (range 4 to 6), else refuse or
    move. The keep gets a 1-block plinth and a `ground_level` metadata field (needs the owner's OK).
  - **Tests:** 145 required (109 before), all pass.

### 3 Tall construction spike
- **Goal:** scaffolding with a build order, removed afterward.
- **Acceptance test:** a 20-block tower is built and its scaffolding disappears.
- **Depends on:** existing builder reach and doors · **Size:** L
- **Risks:** pathing on scaffolding for tiny soldiers.
- **Decisions:** D34–D40 · **Status:** done (2026-10-04), waiting for the in-game checklist · **Results:**
  - **Research:** `docs/RESEARCH-scaffolding.md`.
  - **Planner:** pure; simulates the build like builders work; plans 5 approaches.
  - **In game:** climbed scaffolding columns (builders walk to a column's base and rise inside it). Measured: tiny
    soldiers can't reliably use floating steps (falls at turns, vanilla's 16-block path limit).
  - **Acceptance:** a 20-block tower is built and its scaffolding disappears; also a 16-long, 12-high wall and a
    14-high corner tower on 10-high walls.
  - **Unchanged:** the Small House still gets 151 of 151 with no scaffold.
  - **Tracking:** saved jobs, cleanup when interrupted, player blocks kept; dev tools and a SCAFFOLD recorder
    category.
  - **Height rules and recommendation:** `docs/SCAFFOLDING.md` (tier-1 keep at most 9 high: needs the owner's OK).
  - **Tests:** 163 required (145 before), all pass.
  - **In-game fix (D41):** builders carried up a column by vanilla climbing got stuck there (dev book tower stalled);
    they are now climbed back down. 165 tests, including the dev book's exact tower layout.
  - **In-game fix (D42):** tall walls crawled above reach with server lag; now no search lag, scaffold goes up in one
    go once needed, re-plans don't add column sets. 168 tests.

### 4 Tier-1 keep built autonomously
- **Goal:**
  - master plan v0 (keep slot only), and a kingdom-level planner (replaces the old M3);
  - builders assigned by the kingdom, using materials from the stockpile (the owner supplies them);
  - a throne room; the king sits on the throne and has high health.
- **Acceptance test:** a gold-hat king plus the supplied items yields a finished keep with the king on the throne. First real playable.
- **Depends on:** 1, 2, 3 · **Size:** L
- **Risks:** keep size versus terrain.
- **Decisions:** [D3](DECISIONS.md), [D4](DECISIONS.md), D82–D85 · **Status:** in progress (2026-10-13, draft) · **Results:**
  - **Master plan v0 and planner (`KingdomKeep`, `KeepSite`):** an active kingdom without a keep picks the nearest flat
    ground to where it was founded (all four directions), levels it at once (logs to the stockpile) and places the
    keep's easel and chest. Keep level from `kingdom.keep.level` (1).
  - **Builders:** 3 of the kingdom's own wheat-holding soldiers without a job (never the king, never fighters).
  - **Materials:** from the stockpile; the owner fills the keep's chest, unneeded items go to the stockpile; the book
    and `/csr kingdom status` list what is missing. Dev: `/csr dev kingdom keep_materials`, `keep_now`.
  - **Throne:** a gold block the kingdom puts in the finished keep, and the king stands on it (the owner's call). Keep L1
    has no throne room, so it goes in the middle of the king's chamber (L2/L3: in place of the great hall's wooden
    throne); +80 health once the keep is built; carried to the throne if he can't walk there in time.
  - **Tests:** `KingdomKeepGameTests` (the whole loop with the test hut, chest to stockpile, site search, levelling,
    saved level). The full keep L1 build in game takes about 132,000 ticks (about 2 hours with 3 builders).
  - **Open:** the owner's in-game checklist; whether the king's chamber is the right room for the L1 throne.

### 5 Economy loop v1
- **Goal:** lumber camp with replanting, farm, carrier, storehouse, stockpile and renown; a flow audit for each.
- **Acceptance test:** the kingdom gathers wood and food unaided and spends them on a cottage.
- **Depends on:** 4 · **Size:** L
- **Risks:** carrier traffic and pathing load.
- **Decisions:** [D5](DECISIONS.md) · **Status:** not started · **Results:** —

### 5a Decoration step
- **Goal:** a code step that adds what builders can't carry once a structure (or a flourish level) is finished:
  water (moats, wells, fountains, docks, farm channels), potted plants and other plants, sign text, banner patterns,
  item frames and armor stands ([D54](DECISIONS.md), [D56](DECISIONS.md)). It is where most of the small details come
  from.
- **Acceptance test:** a finished structure with decoration data gets its water, plants, sign text, banner patterns,
  item frames and armor stands, exactly once. Nothing is added to an unfinished build. Decoration survives save and
  reload. The ruin stages remove it again.
- **Depends on:** structures batch 1 (the core tier-1 structure files, `STRUCTURES.md` build order), 5 · **Size:** M
- **Risks:**
  - Where the decoration data lives: the structure file's own entities and block data, or a separate list.
  - Water flowing out of open structures.
  - Entities in unloaded chunks.
- **Decisions:** [D46](DECISIONS.md), [D54](DECISIONS.md), [D56](DECISIONS.md) · **Status:** not started · **Results:** —

### 6 Layout and growth
- **Goal:** zones, radial roads, scored slots, cottages and farms, visitors joining, a palisade and tier 2, the layout checker enforced.
- **Acceptance test:** a small village forms with no overlaps and a closed wall.
- **Depends on:** 5 · **Size:** XL
- **Risks:** layout on real terrain.
- **Decisions:** [D3](DECISIONS.md), [D7](DECISIONS.md) · **Status:** not started · **Results:** —

### 7 Performance checkpoint
- **Goal:** many soldiers, loaded chunks, a tick budget, caps per tier in the tuning file.
- **Acceptance test:** a measured report of soldier count, tick cost and the loaded-chunk limit.
- **Depends on:** 6 · **Size:** M
- **Risks:** the caps may be lower than the design wants.
- **Decisions:** [D1](DECISIONS.md) · **Status:** not started · **Results:** —

### 8 Content tiers 2–5
- **Goal:** stone wall and towers, gates, barracks, smithy, market, chapel, infirmary, quarry, wall rings.
- **Acceptance test:** a tier 5 castle stands with a stone wall, and every building's flow audit is complete.
- **Depends on:** 6, 7 · **Size:** XL
- **Risks:** structure review time.
- **Decisions:** [D4](DECISIONS.md) · **Status:** not started · **Results:** —

### T Organic wall and layout testing
- **Goal:** test the organic wall loop and the town layout on uneven, real terrain:
  - wall modules joining across height changes (stepped modules, towers as joints, skipped cliff segments);
  - the loop bending around steep steps;
  - roads and fields following the ground;
  - slots on slopes using `ground_level`, skirts and stepped footprints ([`STRUCTURES.md`](STRUCTURES.md)).
- **Acceptance test:** on a set of saved real-terrain test sites (plains, hills, a river, a forest edge), a closed
  wall loop and a tier-5 layout are planned and built with no overlaps, no unreachable modules and no floating or
  buried pieces; a recording and screenshots for each site.
- **Depends on:** the wall modules and the structures of tiers 1 to 5 (stages 6 and 8) · **Size:** L
- **Risks:** real terrain may need more module types; build time of a full loop.
- **Decisions:** [D3](DECISIONS.md), [D43](DECISIONS.md) · **Status:** not started · **Results:** —

### 9 Mining, staged
- **Goal:** (a) scanned shaft and strip mine, (b) hazard rules for lava and water, (c) caves, (d) mobs.
- **Acceptance test:** each sub-stage passes its own lava, water and cave tests.
- **Depends on:** 5; may run in parallel with 8 · **Size:** XL
- **Risks:** soldiers dying in hazards; terrain damage.
- **Decisions:** [D6](DECISIONS.md) · **Status:** not started · **Results:** —

### 10 Life layer
- **Goal:** homes, schedules, places to go, mingling, soft wants with no penalties, a population budget.
- **Acceptance test:** citizens visibly follow routines at a measured tick cost.
- **Depends on:** 6, 7 · **Size:** XL
- **Risks:** may exceed the performance budget.
- **Decisions:** [D7](DECISIONS.md) · **Status:** not started · **Results:** —

### 11 Events and defense
- **Goal:** alerts and warning time, mob raids, guards and the king's defense, event scaling and an off switch.
- **Acceptance test:** to be defined.
- **Depends on:** 8, 10 · **Size:** L
- **Risks:** feeling punishing (pillar).
- **Decisions:** — · **Status:** not started · **Results:** —

### 12 Rivals and war
- **Goal:** decide where rivals come from; foreign parties, harassment, then full invasion aimed at the king.
- **Acceptance test:** to be defined.
- **Depends on:** 11 · **Size:** XL
- **Risks:** where rival kingdoms come from is undecided; restrict `DoorAccess` by team or territory here.
- **Decisions:** — · **Status:** not started · **Results:** —

### 13 Decay and ruins
- **Goal:** the king's death triggers ruins, and his soldiers migrate.
- **Acceptance test:** to be defined.
- **Depends on:** 12 · **Size:** L
- **Risks:** the saved-data format for ruins.
- **Decisions:** [D2](DECISIONS.md) · **Status:** not started · **Results:** —

### 14 Tiers 6–10 and polish
- **Goal:** biome palettes, structure variants, interiors, balance.
- **Acceptance test:** to be defined.
- **Depends on:** 8 onward · **Size:** XL
- **Risks:** content volume.
- **Decisions:** — · **Status:** not started · **Results:** —

## Ordering rationale

- Kingdom data comes before anything that depends on it.
- Observer tools come early.
- Scary unknowns (terrain, scaffolding, performance) get small spikes before content depends on them.
- Economy comes before events.
- The Sims layer comes after the performance checkpoint.

## Cut lines

- **Stages 0–6:** a first kingdom that grows and can be watched. Stopping after stage 6 still leaves a real mod.
- **Stages 7–10:** it feels alive and deep.
- **Stages 11–14:** pressure and endgame.

## Known risks

- The Sims layer may exceed the performance budget.
- Where rival kingdoms come from is undecided.
- Structure review takes most of the owner's time.
- Terrain handling may change the keep design.
