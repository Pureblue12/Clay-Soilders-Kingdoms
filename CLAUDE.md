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
`DebugLogGameTests`, `RecorderFeedbackGameTests`, `BuildRequestGameTests`), prints
`All N required tests passed` or the list of failures, then exits (about 10 s). Gradle reports
BUILD FAILED when any test fails. The test world lives in `neoforge/runs/gametest` (gitignored).
Template: `data/csr/structure/gametest/platform.nbt` (regenerate with `tools/make_gametest_platform.py`).

- Bug tests assert the CORRECT behavior; a failing bug test = a confirmed bug (fix in M1).
  Don't change gameplay code just to make a test pass.
- Characterization tests pin current behavior on purpose (known limitations); update them deliberately.
- Test coordinates are relative to the structure block one below the template: floor y=1, ground y=2.
  Don't use `helper.relativePos` (wrong rotation in 1.21.1); compare absolute positions instead.
- Use `FakePlayerFactory` for players, not `makeMockServerPlayerInLevel` (Curios crashes on it).

Current status (2026-10-03, after Fix B): all 41 required tests pass (`smallTowerFinishes` is required
again since Fix B). `SuspectedBugGameTests` covers the easel right-click:
non-block item, unneeded block, needed block.

### Known bugs (confirmed)

Confirmed = a GameTest asserting the correct behavior fails. Not fixed yet.

- Wall-mounted blocks are never placed by builders (found while measuring Fix B): a blueprint's wall torch
  is requested with its item (torch), but `ServerBlueprintPlan.tryPlacingRequest` (and `tryPlacing` for hand
  placing) checks the item's BLOCK (standing torch) against the spot's block (wall torch), so placing fails.
  The torch is then handed out again forever and blocks everything after it in the order: the Small House
  stops at 98 of 151. A temporary experiment matching by item finished it 151/151. Same for wall signs,
  banners and skulls. Not fixed yet; no GameTest yet.
- Door race: a door can be handed out while other blocks are still being carried (doors-last only orders
  the list); one glass pane got locked out in the 08:03 recording. Not fixed yet.
- Fixed: easel `ClassCastException` with non-block items (`easelIgnoresNonBlockItems`).
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

### Known limitations

- Fixed (Fix B): builders reach higher. `BuilderReach`: a block's center less than 2.5 sideways (up to 2
  blocks away) and from 2 below to 8 above the feet (`MAX_PLACE_HEIGHT`); if no path gets close to the block,
  one path to the nearest standing spot within reach is planned; reach is measured from the center of the
  block the builder stands in (`builderPlacesHighBlockFromTheGround`, `blockAboveMaxReachIsNotTaken`,
  `smallTowerFinishes` 34/34).

Pending (not started):
- Blocks more than 8 above any reachable standing spot stay unbuilt (by design of `MAX_PLACE_HEIGHT`).
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
`Supplier<String>` for anything expensive). Categories: `DebugCategory` (BUILD, WORK, DAMAGE, PATHING, TEAM;
add one with `DebugCategory.register` and it gets commands and book buttons automatically).
Commands/buttons: `common/.../dev/RecorderTools`; action-bar indicator: `neoforge/.../event/RecorderIndicator`
(registered only in dev). Ring buffer default 5000 events (`/csr dev log capacity`).
State lives in memory only: it resets when the game restarts (not when you just re-join a world).
Instrumented so far: `BuildBlueprintGoal`, `ServerBlueprintPlan.getRequest` (summary), `BuilderReach`
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
- `neoforge/.../event/SoldierWorkCleanup` — stops a soldier's work goal when it leaves the level
  (saving no longer does this).
- `mixin/` — mixins (config: `common/src/main/resources/csr.mixins.json`).
- Client rendering/models: `entity/client/`.

Optional integrations (JEI, Jade, Accessories, Curios, Create, SmartBrainLib) are in
`integration/` and declared in `neoforge/build.gradle`.

## Rules

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
- **M3 — next:** soldiers start a build on their own (should call `BuildSitePlanner`).
- **M4:** kingdom object (saved world data) with a founding block.
