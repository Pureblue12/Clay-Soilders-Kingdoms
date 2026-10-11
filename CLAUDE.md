# CLAUDE.md

## Project overview

**Clay Soldiers Remake (CSR)** — a data-driven remake of the classic Clay Soldiers
mod, by **Bumble B33** (upstream: https://github.com/Bumble-B33/csr, branch `1.21.1`).
MIT licensed — see `LICENSE` (copyright jellyfish-bit). Mod id: `csr`.

- Target: **Minecraft 1.21.1 / NeoForge 21.1.x**, Java 21.
- The project is multi-loader (NeoForge + Fabric). **We only work on NeoForge for now**;
  Fabric can be ignored but must not be deleted.
- Long-term goal of this fork: clay soldier factions that build autonomous kingdoms
  (gathering, building, villages, walls) and wage war on each other. Not started yet.
- The user is new to Java and modding.

## Build and run

Run from the project root. The first run downloads Minecraft/NeoForge and is slow.

```bash
./gradlew :neoforge:runData      # generate recipes/models/lang/loot/data maps (see below)
./gradlew :neoforge:build        # build the mod jar -> neoforge/build/libs/
./gradlew :neoforge:runClient    # launch Minecraft with the mod (game dir: neoforge/run, gitignored)
./gradlew :neoforge:runServer    # launch a dedicated server
```

### Automated GameTests

```bash
./gradlew :neoforge:runGameTestServer
```

Starts a headless test server, runs every test in
`neoforge/src/main/java/net/bumblebee/claysoldiers/gametest/` (`ClaySoldierGameTests`,
`DevToolkitGameTests`, `SuspectedBugGameTests`, `BuildSiteGameTests`, `BuilderReachGameTests`,
`DebugLogGameTests`, `RecorderFeedbackGameTests`, `BuildRequestGameTests`, `PlacingGameTests`,
`DoorGameTests`, `TuningGameTests`, `StructureRegistryGameTests`, `StructurePasteGameTests`,
`LayoutCheckerGameTests`, `KingdomDataGameTests`, `KingdomGameTests`, `KingdomBookGameTests`, `KingdomBookRefreshGameTests`, `TerrainScoreGameTests`, `TerrainScanGameTests`,
`TerrainGradingGameTests`, `TerrainFoundingGameTests`, `ScaffoldPlannerGameTests`, `ScaffoldBuildGameTests`,
`ScaffoldCleanupGameTests`, `StructureBuildGameTests`), prints
`All N required tests passed` or the list of failures, then exits (about 10 s). Gradle reports
BUILD FAILED when any test fails. The test world lives in `neoforge/runs/gametest` (gitignored).
Templates: `data/csr/structure/gametest/platform.nbt` (16x8x16) and `terrain.nbt` (32x16x32, for test terrains);
regenerate with `tools/make_gametest_platform.py` (size arguments: see the script). `tools/suite.sh` deletes the test
world first, so every run starts fresh.

GitHub Actions (`.github/workflows/ci.yml`) runs `runData` then `tools/suite.sh` on every push to `main` and every pull
request; the logs (`build/suite.log`, `build/build.log`) are attached to the run as `suite-logs`. Builder tests that
can end short on CI (batch-1 builds, the scaffold builds) write `CSR stall report` lines shortly before their
timeout (`BuildStallReport`: blocks left, chest, builders, items on the ground, last recorded events); the run prints
them in its "Stall reports" step. GitHub's machines are slower: the one wall-clock limit
(`ScaffoldPlannerGameTests.PLAN_MILLIS`) is doubled there (`CI=true`).

- Bug tests assert the CORRECT behavior; a failing bug test = a confirmed bug (fix in M1).
  Don't change gameplay code just to make a test pass.
- Characterization tests pin current behavior on purpose (known limitations); update them deliberately.
- Test coordinates are relative to the structure block one below the template: floor y=1, ground y=2.
  Don't use `helper.relativePos` (wrong rotation in 1.21.1); compare absolute positions instead.
- Use `FakePlayerFactory` for players, not `makeMockServerPlayerInLevel` (Curios crashes on it).
- Builders place up to 2 blocks sideways from where they stand (Fix B), also over 2-high walls and from inside a
  doorway: a test block that must only be reachable through a door has to be 3+ blocks from every outside spot
  and from the doorway (see `DoorGameTests`).
- Tests that change global state (recorder, `DoorAccess.overrideRuleForTests`) get their own `batch` and reset it
  (`@AfterBatch` for the door rule).
- The framework puts barrier walls one block outside every test area. Founding runs the terrain check on an 11 by 11
  site around the king, so a king closer than 6 blocks to the platform's edge sees the wall as a cliff: tests that
  aren't about terrain found with `Kingdoms.found(..., true)` (force) there (D32).
- Grading and test-terrain undo share the structure paste's global undo history: tests that undo get their own batch.
- The framework also puts a barrier roof at the top of a test area (16 high for `terrain`): tests that build higher
  use `skyAccess = true`. Builders only follow paths up to their follow range (16, plus `build.path_range_bonus` 16
  while building, D61): keep a test's chest within about 28 walked blocks of every block.

### Structure build tests (every Studio structure, separate workflow)

`StructureBuildGameTests` has builders build every structure promoted from the Studio, every level, from scratch
(about 156 builds). Too slow for the suite: the builds only exist when `CSR_STRUCTURE_BUILDS` is set (`all` or a shard
`k/n`; `CSR_STRUCTURE_BUILDS_ONLY=keep,church` limits them) and are optional. Run `tools/structure_builds.sh`
(writes `build/structure-results.tsv`) or the GitHub workflow "Structure builds" (`.github/workflows/structure-builds.yml`,
4 shards, a result table per structure in the run summary; runs when structure files or the test change, or by hand).
A build fails at once after 6000 ticks without progress, with a stall report. The chest is kept stocked by
`dev/MaterialSupply` (most structures need more than 27 stacks; it stocks the items the easel hands out next). The test area
(`gametest/structure_build.nbt`, 48x24x48) has a grass floor. The required test `buildListMatchesRegistry` keeps the
list in `StructureBuildGameTests.STRUCTURES` in step with the registry: add a row when a structure is promoted.

Current status (2026-10-04, after stage 3 Tall construction spike and its in-game fixes D41, D42): all 168 required tests pass (the suite takes a few
minutes: the scaffold tests build whole towers and the Small House). `SuspectedBugGameTests` covers the easel right-click:
non-block item, unneeded block, needed block.

### Known bugs (confirmed)

Confirmed = a GameTest asserting the correct behavior fails. Not fixed yet.

- Fixed: easel `ClassCastException` with non-block items (`easelIgnoresNonBlockItems`).
- Fixed: wall-mounted blocks were never placed (found while measuring Fix B). A wall torch is requested with
  its item (torch), but placing compared the item's own BLOCK (standing torch) with the spot's block (wall
  torch), so the builder returned the torch, got it again, forever; the Small House stopped at 98 of 151.
  Placing now matches by ITEM (`BlueprintBlockInfo.isFor(Item)`) for builders (`tryPlacingRequest`) and by
  hand (`tryPlacing`). Same for wall signs, banners, skulls (`builderPlacesWallTorch`, `builderPlacesWallSign`,
  `handPlacesWallTorchAndSign`).
- Safety net (failed-placement cooldown): a block whose placing fails is not handed out again for
  `BlueprintBlockInfo.PLACE_FAIL_COOLDOWN_TICKS` (100), doubling after each further failure up to
  `MAX_PLACE_FAIL_COOLDOWN_TICKS` (1200); per block, not per soldier; not saved. Each time it fires, a BUILD
  event "placement cooldown: …" is recorded, and the request search summary counts blocks cooling down
  (`unplaceableBlockDoesNotStarveOthers`, `failedPlacementLogsCooldown`, `placementCooldownBacksOff`).
- Fixed (Fix A): builders froze while "Carrying" a block they could never place, because the reachable
  check (path ends within 2 steps) disagreed with placing (feet < 2 blocks straight-line); idle builders
  also asked the easel every tick. Now one shared rule (`BuilderReach`), a 400-tick give-up timer
  (`BuildBlueprintGoal.MAX_CARRY_TICKS`) and a 20–40 tick retry cooldown (`builderNeverStuckCarrying`).
- Fixed (from the 2026-10-03 recording, `BuildRequestGameTests`):
  - Placing at the wrong spot: builders now fill exactly their request's spot
    (`ServerBlueprintPlan.tryPlacingRequest`); players feeding the easel by hand still fill the first free
    spot (`placedAtRequestedSpot`).
  - Stair ping-pong: no taking over a block another builder has; an assigned block is freed only when
    cancelled, finished or stale (`BlueprintBlockInfo.STALE_REQUEST_TICKS` = 600 > `MAX_CARRY_TICKS` = 400,
    which now also covers fetching from storage) (`noBlockPingPong`, `staleTimeoutLongerThanGiveUpTimer`).
  - Jigsaw blocks sorted last: leftover jigsaw data is dropped, so e.g. the small house doorstep is #94
    instead of #149 (`jigsawBlocksSortWithTheirGroup`).
  - Door locked builders out: doors are handed out last (`doorDoesNotLockOutBuilders`); while BUILD is
    recorded, builders log why blocks are unreachable, incl. "closed door in the way at (x, y, z)".
- Fixed (door race): doors-last only ordered the list, so a door could be handed out while other blocks were
  still being carried (a glass pane got locked out in the 08:03 recording). Now `ServerBlueprintPlan.getRequest`
  hands out a door only when no other (non-door) block with a free spot remains; doors don't wait for each
  other or for blocks whose spot is taken. Trade-off: a non-door block that can never be placed (too high,
  keeps failing) keeps the doors from being handed out (`doorWaitsForBlocksInProgress`,
  `doorsDoNotWaitForEachOtherOrObstructedSpots`). Clay soldiers can't path through closed doors: vanilla
  ground navigation walks through open doors but treats closed ones as walls, and soldiers never open doors.
  While BUILD is recorded, each search logs "closed door at (x, y, z) blocks the way to N blueprint block(s),
  first at (x, y, z)" (`closedDoorBlockingABlockIsLogged`).

### Doors (soldiers open, pass and close wooden doors)

Background: `docs/DOORS.md`. Code: `common/.../entity/door/`.
- `DoorAccess.canUse(soldier, doorPos)` — THE one decision whether a soldier may open a door. Returns true for
  every soldier and every wooden door today. **Known future change (war milestone): restrict it by team or
  territory.** Both the pathfinder and the door goal already ask it, so a refused door counts as a wall.
  (`overrideRuleForTests` is for GameTests only.)
- `DoorPathing` — the soldiers' walking and swimming navigations (vanilla + the `DoorAccess` check);
  `UseDoorGoal.allowDoors` turns on vanilla's "plan through wooden doors" option for both.
- `UseDoorGoal` (all clay soldiers: builders, workers, fighters, wanderers) — opens a closed wooden door within
  1.5 blocks ahead on the path, closes it once the soldier is through (or turned back, or stood still 2 s) and
  nobody is walking in the doorway. Only doors soldiers opened get closed (`OpenedDoors`, memory only); the last
  soldier through closes it. Gives up (stops walking, so goals plan again) on a refused door or after 100 ticks
  of trying without getting through. Not vanilla `OpenDoorGoal` (why: `docs/DOORS.md`).
- Iron doors stay closed to everyone. Fence gates are not opened (vanilla pathing treats closed gates as walls;
  not trivial). Trapdoors: out of scope.
- Builders don't wait for work standing in a doorway, and take the last step into their path's end block.
- Recorder (PATHING): "opened door at", "closed door at ... behind it", "refused door at ...: <why>",
  "stuck at door at ... for N ticks, giving up", "left door at ... open: doorway not clear".
- "Doors are handed out last" in the build order stays as a safety net.
- Tests: `DoorGameTests` (closed room, door closed again, iron door, open door, two builders, the locked-out
  Small House roof, combat chase, door events, refused door, stuck at a door).

### Kingdoms (stage 1: data, founding, observer; no building or economy yet)

Code: `common/.../kingdom/` (`Kingdom`, `KingdomSavedData`, `Kingdoms` service, `KingdomBook`), commands
`common/.../commands/KingdomCommand`, game hooks `neoforge/.../event/KingdomEvents`. Decisions D16–D23.
- **Saved:** `<world>/data/csr_kingdoms.dat`, format version `KingdomSavedData.DATA_VERSION` (1); see the migration
  notes in `docs/DECISIONS.md`. Any format change raises the version and adds a migration.
- **At most one kingdom per team**, ACTIVE or RUINED. Change kingdoms only through `Kingdoms` (it marks the data
  dirty, writes the chronicle, records KINGDOM events).
- **Founding** is explicit and reversible, never automatic:
  - in game: sneak + right-click your own king (a clay soldier wearing a gold ingot, its team loyal to you) holding
    a gold block;
  - or `/csr kingdom found` (nearest loyal king within `kingdom.found_search_radius`).

  Refused: unsupported dimension (Overworld only), not a living king, team not loyal to you, team already has a
  kingdom, claim overlaps another kingdom, or the ground can't hold a keep (terrain check, below).
- **The claim** is a circle of `kingdom.claim_radius` around the king, fixed at founding.
- **The king's death** (NeoForge death event, lowest priority) sets RUINED and writes a chronicle line.
- **Population** is counted every `kingdom.census_interval_ticks`, but only while the whole claim is loaded;
  otherwise the last count stays, with its time.
- **Stockpile:** `Kingdoms.addResource` / `removeResource` (all or nothing, shortfall reported).
- **Commands** (any player, every install): `/csr kingdom found | disband [team] | book [team] | status [team] | list`.
  `goto_king <team>` needs operators. The book (status page with "Go to the king", stockpile, buildings, chronicle)
  rewrites itself whenever it's opened, in the hand or on a lectern (D24).
- **Dev only:** `/csr dev kingdom stock_add|stock_remove <resource> [amount]`, `stock_show` (book: Kingdom).

### Terrain (stage 2 spike: site checks; soldiers don't grade yet)

Code: `common/.../terrain/`; background, comparison and recommendation: `docs/TERRAIN.md`; prior art:
`docs/RESEARCH-terrain.md`; decisions D25–D32; numbers `terrain.*` (read through `SiteRules`).
- `TerrainScanner.scan` reads only loaded chunks (others are unknown, never loaded). Ground = top movement-blocking
  block that isn't a log or leaves; water is a depth, lava a flag.
- `SiteScorer` (pure) judges the keep site: 9 by 9 keep plus a margin of 1 = 11 by 11 around a center.
  - Hard refusals: cliff (a step of 4+), lava within 4, water 3+ deep, too steep (slope 75 %+ or range 8+).
  - Soft penalties lower a score from 100.
  - A site with unloaded columns is unknown, not refused.
- `SiteSearch` (pure): rings of candidates (step 4, radius 16, at most 81); best score, nearest on a tie.
- `GradingPlanner` (pure) plans flattest / cut_fill / foundation / clear_trees / avoid_trees; `Terrain.grade` applies a
  plan instantly with an undo snapshot (`StructurePaster.remember`, the same undo as the paste). Dev tool only.
- **Founding** runs `Terrain.checkSite` on the king's position:
  - refused with the reasons and the best site nearby;
  - warnings on soft penalties;
  - skipped and recorded when part of the site isn't loaded;
  - `/csr kingdom found force` (permission 2) skips it, recorded in KINGDOM and TERRAIN.
- Recorder TERRAIN: scans (with cost), site scores, searches, refusals, gradings and their block counts.

### Scaffolding (stage 3 spike: tall builds)

Code: `common/.../scaffold/`; comparison, recommendation and height rules: `docs/SCAFFOLDING.md`; prior art:
`docs/RESEARCH-scaffolding.md`; decisions D34–D40; numbers `scaffold.*`.
- **Reach:** builders place up to 8 above their feet (`BuilderReach`), so structures up to 9 tall never get scaffold
  and build exactly as before (Small House 151/151).
- `ScaffoldPlanner` (pure) simulates the build like builders work and plans scaffold. Approaches: (a) outside ramp,
  (b) step pillars, (c) structure tops + pillars, (d) vanilla scaffolding (counted, not usable), (e) climbed
  columns. Reachability is measured from the builders' storage with vanilla's path-length limit.
- **In game (`scaffold.approach` 4):** 1x1 scaffolding columns.
  - The easel hands out scaffold requests (`Scaffolds.placeRequest`) only when nothing is reachable and some block is
    above reach.
  - Builders walk to a column's base and rise inside it (`ScaffoldClimber`, called from `BuildBlueprintGoal`).
  - After the structure is finished, builders take each column down from its base (`Scaffolds.removeRequest`;
    refund carries it back).
  - Tiny soldiers can't reliably climb floating steps with vanilla navigation (measured), hence columns.
- **Jobs:** `ScaffoldSavedData` (`csr_scaffold.dat` per dimension, version 1).
  - Removed at once when the easel is broken (`EaselBlock.onRemove`), its blueprint is replaced or taken off, or by
    the sweep (easel gone or no plan, every `scaffold.sweep_ticks`, `Scaffolds.tick`).
  - A position a player changed is never removed: recorded (SCAFFOLD "kept") and nearby players are told.
- **While a build has scaffold, its builders** get a fresh route every 20 ticks and precise steps on scaffold blocks
  (`DoorPathing.Ground.followThePath`). Every builder gets a longer follow range and a bigger path search while it works
  for a build (`build.path_*`, D61; scaffold builds use the larger of `build.path_*` and `scaffold.path_*`).
- **Recorder SCAFFOLD:** planned, placed, removed, climbing, stuck, cleanup, kept.

### Known limitations

- Fixed (Fix B): builders reach higher. `BuilderReach`: a block's center less than 2.5 sideways (up to 2
  blocks away) and from 2 below to 8 above the feet (`MAX_PLACE_HEIGHT`); if no path gets close to the block,
  one path to the nearest standing spot within reach is planned; reach is measured from the center of the
  block the builder stands in (`builderPlacesHighBlockFromTheGround`, `blockAboveMaxReachIsNotTaken`,
  `smallTowerFinishes` 34/34).

Pending (not started):
- Doors: soldiers wander into player houses now (they open wooden doors on any path); restricting that is the
  `DoorAccess` war-milestone change. Fence gates aren't opened. Doors opened by soldiers are remembered in
  memory only (after a restart a door a soldier left open counts as opened by a player and stays open).
- Blocks more than 8 above any reachable standing spot stay unbuilt (by design of `MAX_PLACE_HEIGHT`).
- A blueprint block that is already there and replaceable (e.g. short grass where short grass grows) counts
  as free but can never be placed (placing the same block fails), so the blueprint never finishes; the
  cooldown only stops it from starving the rest. Not confirmed in game; not fixed.
- Layer-by-layer ordering: blocks are handed out as all full cubes (bottom to top, roof included), then
  non-cube blocks, then blocks with data (beds), then doors.

Known, unfixed (reported, not confirmed in game): `DigHoleGoal.resetBreakProgress` unregisters
`currentPos` even when this soldier never registered it; in dev runs `ErrorHandler` throws
("Trying to unregister an Entity for a Empty BreakData"), e.g. when a digger is unloaded or its job
changes while walking. In a normal install it is only a debug log.

### Dev toolkit book and `/csr dev` commands (development runs only)

In a `runClient` world (blueprint experiment on), type once: `/csr dev book`. The book has
clickable buttons for every dev command; its "Refresh this book" button (or `/csr dev book`
again) updates the copies you carry. Page 1 shows a version stamp `v<mod version> #<hash>` and
the time it was made; a different hash means the registry changed.

| Command | Does |
|---|---|
| `/csr dev setup` | doMobSpawning false, doDaylightCycle false, time day |
| `/csr dev book` | gives / refreshes the toolkit book |
| `/csr dev kit` | brush, 3 red soldiers, 3 wheat, chest, easel, goggles, debug item, every blueprint |
| `/csr dev scenario build_test` / `build_test_empty` | 3 red builders + stocked / empty chest + easel with Small House |
| `/csr dev team claim <team>` | makes the team loyal to you |
| `/csr dev materials <blueprint>` | gives the materials from the blueprint's own item list |
| `/csr dev cleanup <radius>` | removes clay soldiers and easels in range (asks to confirm in chat) |
| `/csr build <blueprint>` (book link) | see "Starting a build" below |
| `/csr dev tuning reload` / `show <key>` | re-read the tuning file / show a value, its default and range (book: Tuning) |
| `/csr dev structure place <id> [facing]` | paste a registry structure where you stand, builders' conventions (book: Structures) |
| `/csr dev structure build <id> [level]` | 3 builders build a Studio structure (default: top level) ahead of you; the chest is refilled as it empties (book: Structures, "Builders build") |
| `/csr dev structure undo` / `reload` / `list` | undo the last paste / re-read structures from disk / list all |
| `/csr dev kingdom stock_add` / `stock_remove <resource> [amount]`, `stock_show` | test the stockpile of the kingdom you stand in (book: Kingdom, which also links `/csr kingdom found/book/status/disband`) |
| `/csr dev terrain scan` / `site` | scan the ground around you / score the keep site where you stand and show the best nearby (book: Terrain) |
| `/csr dev terrain make <shape>` | build a test terrain 16 blocks ahead: slope, hill, cliff, lake_edge, forest, lava_pool, plateau |
| `/csr dev scaffold build <shape> [height]` | test tower, wall or wall_tower (default 20 high) ahead of you: easel, stocked chest (cobblestone + 128 scaffolding), 3 builders (book: Scaffolding) |
| `/csr dev scaffold plan <shape> [height]` / `status` / `cleanup` / `undo` | print every approach's scaffold plan / list jobs nearby / remove scaffold nearby at once / undo the test build's blocks |
| `/csr dev terrain grade <method>` / `undo` | apply flattest, cut_fill, foundation, clear_trees or avoid_trees to the keep site where you stand / undo the last terrain change or paste |

How it is built (`common/.../dev/`):
- `DevToolRegistry` — single source of truth: generates the `/csr dev` commands AND the book buttons.
- `DevTools` — the built-in categories (`DevCategory`, plain data) and tools (`DevTool`).
  Argument buttons (teams, blueprints) are read from registries.
- `DevBook` — builds the book with automatic pagination and contents links.
- `BookFormat` — the ONLY class with Minecraft-version-specific book/click/hover code and page limits.
- `DevScenarios` — scenario setup helpers, also used by GameTests.
Registered only when `PLATFORM.isDevEnv()` (NeoForge: `!FMLLoader.isProduction()`), so never in a
normal install. To add a dev command: add a `DevTool` in `DevTools` (new category: add a `DevCategory`).

### Debug recorder (`/csr dev log`, book pages "Recorder" and "Recorder (advanced)")

Records what soldiers and build plans do, to find out WHY something goes wrong.
1. Refresh the book first (`/csr dev book`): page 1's `#hash` must match the one **Status** prints.
2. Book → Recorder → **Start recording** (BUILD, WORK, DAMAGE; clears the old log). While recording, the
   action bar shows `● REC BUILD WORK DAMAGE · N events` (+ `· focus: red#2`) every second.
3. Reproduce the problem. Optional (advanced page): **Focus nearest soldier**, **Turn on/off** a category.
4. **Status (is it on?)** prints the state; every recorder button prints a one-line result in chat and
   writes it to the game log (Terminal) as `[CSR-DEBUG] ...`.
5. **Stop + save** prints the event count, duration and file path:
   `neoforge/run/debuglogs/csr-debug-<time>.txt` (last 20 files kept). Paste the file.
   Lines: `[t=tick] CATEGORY team#entityId @(pos) message (xN until t=..)`, oldest first.

Code: `common/.../debug/` — `DebugLog.event(category, soldier, "text %s", arg)` is the one-line entry point
(returns after one field read when the category is off; pass raw objects, not built strings; use a
`Supplier<String>` for anything expensive). Categories: `DebugCategory` (BUILD, WORK, DAMAGE, PATHING, TEAM, STRUCTURE, KINGDOM, TERRAIN, SCAFFOLD;
add one with `DebugCategory.register` and it gets commands and book buttons automatically).
Commands/buttons: `common/.../dev/RecorderTools`; action-bar indicator: `neoforge/.../event/RecorderIndicator`
(registered only in dev). Ring buffer default 5000 events (`/csr dev log capacity`).
State lives in memory only: it resets when the game restarts (not when you just re-join a world).
Instrumented so far: `BuildBlueprintGoal`, `ServerBlueprintPlan.getRequest` (summary),
`BlueprintBlockInfo` (stale requests, placement cooldown), `BuilderReach` and `UseDoorGoal`
(PATHING), `AbstractClaySoldierEntity.hurt` (DAMAGE), `WorkSelectorGoal.setWorkMode` (WORK).
TEAM has no events yet.

### Starting a build (M2)

```
/csr build <blueprint> [<x y z>] [north|east|south|west]
```

Permission level 2, registered in every install (not dev-only). Places an easel with the blueprint at
the position (default: the block you stand in); the building goes in the given direction (default: the
way you face) and starts 2 blocks behind the easel, centered on it. Refuses with a reason if the
blueprint is unknown, blueprints are disabled, the site is outside the build height or not loaded,
the easel spot is taken or unsupported, any blueprint block is obstructed, there is no solid ground
under the building, or another easel is within 8 blocks. It never clears terrain.
Code: `common/.../blueprint/site/BuildSitePlanner` (`check`, `place`, `checkAndPlace`; reusable by AI),
`BuildSite`, `SiteCheck`; command in `common/.../commands/BuildCommand`. The book's Build category has
a "Build here" button per blueprint (a `DevTool.link`).

**Generated resources are not in the repo.** `common/src/generated/resources` is
gitignored upstream. It is produced by `runData` from the datagen classes in
`neoforge/src/main/java/net/bumblebee/claysoldiers/datagen/`. Without it the game runs
but items have missing textures/names and there are no recipes. Re-run `runData`
whenever you change a datagen provider or add items/blocks/recipes.

## Structure Studio (`studio/`)

A separate Node tool (not part of the Gradle build) to view structures with real Minecraft textures, review them
(approve / reject, versions, compare) and render screenshots. Run with `cd studio && npm install && npm run dev`.
Its own guide is `studio/CLAUDE.md`; building rules `studio/STYLE.md`. Only Carlton approves structures there.
Approved structures reach the mod only through `npm run studio -- promote` (table `studio/promote.json`; rules in
`docs/STRUCTURES.md`, "Promote step"). The batch-1 files stay as test copies `csr:batch1_<name>`.

## Code structure

- `common/` — loader-independent code (almost all gameplay; ~320 Java files).
  Compiled against vanilla Minecraft only, so it must not import NeoForge or Fabric classes.
- `neoforge/` — NeoForge entry points, platform implementations, capabilities, datagen.
- `fabric/` — Fabric entry points (ignored for now).
- `buildSrc/` — shared Gradle logic (`multiloader-common.gradle`, `multiloader-loader.gradle`)
  that copies `common` sources/resources into each loader's jar.
- `gradle.properties` — all version numbers (Minecraft, NeoForge, dependencies).

Main package: `net.bumblebee.claysoldiers` (in `common/src/main/java`):

- `ClaySoldiersCommon` / `ClaySoldiersClient` — shared setup.
  NeoForge entry point: `neoforge/.../ClaySoldiersNeoForge.java` (+ `ClaySoldiersNeoForgeClient`).
- `init/` — registration of entity types, items, blocks, effects, POIs, etc. (`Mod*.java`).
- `platform/services/` — interfaces for loader-specific behaviour; NeoForge implementations
  are in `neoforge/.../platform/` (loaded via Java ServiceLoader, `META-INF/services`).
- `entity/soldier/` — **the soldier**. `AbstractClaySoldierEntity` (~2000 lines) holds
  almost all soldier logic; `ClaySoldierEntity`, `ZombieClaySoldierEntity`,
  `VampireClaySoldierEntity` are variants. Base classes are in `entity/`
  (`ClayMobEntity` → `ClayMobRideableEntity` → `ClayMobTeamOwnerEntity`).
- `entity/goal/` — **soldier AI** (vanilla Goal system). Goals are registered in
  `AbstractClaySoldierEntity.registerGoals()`. Subfolders: `target/` (who to attack),
  `workgoal/` (digging, crops, item pickup, building blueprints), `horse/`.
- `team/` — teams/factions (`ClayMobTeam`, `ClayMobTeamManger`, loyalty).
- `soldierproperties/`, `soldieritemtypes/`, `datamap/` — data-driven item effects on soldiers.
- `blueprint/`, `block/blueprint/`, `item/blueprint/` — blueprint building system.
- `soldierpoi/`, `claypoifunction/` — points of interest soldiers can work at.
- `dev/` — dev-only toolkit: `DevToolRegistry`, `DevTools`, `DevBook`, `BookFormat`, `DevScenarios`
  (see "Dev toolkit book" above).
- `blueprint/site/` — `BuildSitePlanner`: checks build sites and places easels (M2).
- `debug/` — `DebugLog` recorder and `DebugCategory` (dev debugging; see "Debug recorder").
- `entity/door/` — door use: `DoorAccess` (who may open which door), `UseDoorGoal`, `DoorPathing`, `OpenedDoors`.
- `tuning/` — the tuning file (`Tuning`, `TuningKey`): every balancing number; see `docs/TUNING.md`.
- `structure/` — kingdom structure registry (`StructureMeta`, `StructureRegistry`) and the dev paste tool
  (`StructurePaster`); see `docs/STRUCTURES.md`.
- `kingdom/` — kingdoms (stage 1): `Kingdom`, `KingdomSavedData` (saved, versioned), `Kingdoms` (all changes),
  `KingdomBook` (observer book); commands in `commands/KingdomCommand`.
- `terrain/` — terrain spike (stage 2): `TerrainScanner`, `TerrainScan`, `SiteRules`, `SiteScorer`, `SiteSearch`,
  `GradingPlanner`, `Terrain` (world side), `TestTerrain` (test terrains); dev tools in `dev/TerrainTools`.
- `scaffold/` — tall builds (stage 3): `ScaffoldPlanner` + `ScaffoldGrid` (pure), `Scaffolds` (world side, requests,
  cleanup), `ScaffoldClimber`, `ScaffoldSavedData`/`ScaffoldJob`, `TestStructures`; dev tools in `dev/ScaffoldTools`.
- `kingdom/layout/` — layout checker skeleton (pure data: `LayoutPlan`, `Footprint`, `RoadGraph`, `WallLoop`,
  `LayoutChecker`).
- `neoforge/.../event/SoldierWorkCleanup` — stops a soldier's work goal when it leaves the level
  (saving no longer does this).
- `mixin/` — mixins (config: `common/src/main/resources/csr.mixins.json`).
- Client rendering/models: `entity/client/`.

Optional integrations (JEI, Jade, Accessories, Curios, Create, SmartBrainLib) are in
`integration/` and declared in `neoforge/build.gradle`.

## Rules

- **Never commit unless the required suite passed on the code being committed.** Chain it so a failure stops the
  commit: `tools/suite.sh && git commit ...` (runs the GameTests and the build). The only exception is a tests-first
  commit: name the new tests that must fail, and it passes only if exactly those failed:
  `tools/suite.sh newtestname && git commit ...`.
- Known rare flake: `soldierStuckAtDoorGivesUp` failed once in 30+ runs (2026-10-03), cause unknown. If it fails
  again, record it first (run it with the recorder and keep the log) before touching the test or the door code.
- Design gaps: follow the DESIGN DECISION PROTOCOL in `docs/DESIGN.md` (DRAFT) and log every decision in
  `docs/DECISIONS.md`.
- Plan of work: `docs/ROADMAP.md` (stages, definition of done, status table). Update a stage's status and
  results when it changes.
- Tuning file: every balancing number (costs, radii, rates, caps, unlock thresholds) is a key in
  `data/csr/tuning/tuning.json` (`Tuning.java`, documented in `docs/TUNING.md`), never hardcoded. A new key needs a
  default, a range, a line in `tuning.json` and a row in `docs/TUNING.md`.
- Structure pipeline: a kingdom structure is a vanilla `.nbt` (`data/csr/structure/...`, edit with structure blocks)
  plus a metadata JSON (`data/csr/csr/structures/`). Validation must stay clean
  (`StructureRegistryGameTests.shippedRegistryIsValid`). Workflow: `docs/STRUCTURES.md`.

- Make small, incremental changes; one feature at a time.
- Never break the existing build — run `./gradlew :neoforge:build` after every change.
- Explain changes in plain language (the user is new to Java and modding).
- Keep the original authors' credit intact (LICENSE, `mod_author`, `authors` in mods.toml,
  README). Add our credit alongside, never in place of theirs.
- Put loader-independent code in `common/`; only NeoForge-specific code in `neoforge/`.
- Git is set up locally. Commit after each working change with a clear message.
  Never use force flags or delete history without asking me.
- Before changing anything, read the relevant existing code and summarize how it works.
- Put new behavior in new classes. Do not grow `AbstractClaySoldierEntity`; only add
  minimal hook points there.
- After each feature, give me an exact in-game test checklist.
- Do not rename existing misspelled classes/fields (`Manger`, `Sodlier`, `VampiricOnwer`);
  save files depend on some of them.
- Fix only bugs I have confirmed in game. Do not fix things on suspicion.
- Every dev command, scenario, or test helper is defined through `DevToolRegistry` and gets a
  book button. The coverage test (`DevToolkitGameTests.everyDevCommandHasARegistryEntry`) enforces it.
- Milestone work isn't done until its commands are in the book.
- A gameplay change that adds a decision point (a goal giving up, a request released, a job change)
  must log it through `DebugLog`.
- When investigating a bug, record first (`/csr dev log start` … `stop`) and read the log; do not guess.
- New recorder categories get book buttons through the registry (they do automatically via
  `DebugCategory.register` + `RecorderTools`).

## Milestones

- **M0 — done:** test the existing flow and reproduce suspected bugs
  (guide: `docs/TESTING.md`, background: `docs/ANALYSIS.md`, GameTests).
- **M1 — done:** fixed the 3 confirmed bugs (free blueprint blocks from an empty chest,
  dig not stopping at bedrock, saving cancelling work).
- **M2 — done:** `/csr build` + `BuildSitePlanner` (site checks, easel placement), book link, tests.
- Since then: Fix A/B (builder freeze, reach), recorder, wall-mounted blocks, placement cooldown, door race,
  door use (see "Known bugs" and "Doors").
- **Now: `docs/ROADMAP.md`** (DRAFT v0, stages 0–14) replaces the old M3/M4 plan; the old M3 (soldiers start
  builds) is part of stage 4, the old M4 (kingdom object) is stage 1.
- **Stage 0 (Foundations) — done (2026-10-03, waiting for the in-game checklist):** tuning file, structure registry,
  paste tool with undo/reload, layout checker skeleton, STRUCTURE recorder category.
- **Stage 1 (Kingdom core + observer v1) — done (2026-10-03, waiting for the in-game checklist):** saved kingdoms,
  founding/disbanding, king death -> RUINED, stockpile, census, chronicle, kingdom book and commands, KINGDOM
  recorder category.
- **Stage 2 (Terrain spike) — done (2026-10-04, waiting for the in-game checklist):** research, terrain scan, site
  scorer and search, five grading methods (dev tool, undo), founding terrain check with force for operators, Terrain
  dev book category, TERRAIN recorder category. Recommendation in `docs/TERRAIN.md`.
- **Stage 3 (Tall construction spike) — done (2026-10-04, waiting for the in-game checklist):** research, scaffold
  planner (5 approaches), climbed scaffolding columns in game, saved jobs with cleanup, Scaffolding dev book category,
  SCAFFOLD recorder category. Recommendation and height rules in `docs/SCAFFOLDING.md`.
- **Stage 4 (Tier-1 keep built autonomously) — next.**
