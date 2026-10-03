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
`DevToolkitGameTests`), prints
`All N required tests passed` or the list of failures, then exits (about 10 s). Gradle reports
BUILD FAILED when any test fails. The test world lives in `neoforge/runs/gametest` (gitignored).
Template: `data/csr/structure/gametest/platform.nbt` (regenerate with `tools/make_gametest_platform.py`).

- Bug tests assert the CORRECT behavior; a failing bug test = a confirmed bug (fix in M1).
  Don't change gameplay code just to make a test pass.
- Characterization tests pin current behavior on purpose (known limitations); update them deliberately.
- Test coordinates are relative to the structure block one below the template: floor y=1, ground y=2.
  Don't use `helper.relativePos` (wrong rotation in 1.21.1); compare absolute positions instead.
- Use `FakePlayerFactory` for players, not `makeMockServerPlayerInLevel` (Curios crashes on it).

Current status (2026-10-03): 10 pass; `easelIgnoresNonBlockItems` fails (confirmed bug, see below).

### Known bugs (confirmed)

Confirmed = a GameTest asserting the correct behavior fails. Not fixed yet.

- `easelIgnoresNonBlockItems` (`SuspectedBugGameTests`): right-clicking an easel that has a blueprint
  with a non-block item (e.g. a stick) throws `ClassCastException` in `ServerBlueprintPlan.tryPlacing`.

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

How it is built (`common/.../dev/`):
- `DevToolRegistry` — single source of truth: generates the `/csr dev` commands AND the book buttons.
- `DevTools` — the built-in categories (`DevCategory`, plain data) and tools (`DevTool`).
  Argument buttons (teams, blueprints) are read from registries.
- `DevBook` — builds the book with automatic pagination and contents links.
- `BookFormat` — the ONLY class with Minecraft-version-specific book/click/hover code and page limits.
- `DevScenarios` — scenario setup helpers, also used by GameTests.
Registered only when `PLATFORM.isDevEnv()` (NeoForge: `!FMLLoader.isProduction()`), so never in a
normal install. To add a dev command: add a `DevTool` in `DevTools` (new category: add a `DevCategory`).

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

## Milestones

- **M0 — done:** test the existing flow and reproduce suspected bugs
  (guide: `docs/TESTING.md`, background: `docs/ANALYSIS.md`, GameTests).
- **M1 — done:** fixed the 3 confirmed bugs (free blueprint blocks from an empty chest,
  dig not stopping at bedrock, saving cancelling work).
- **M2 — next:** command that places an easel + blueprint.
- **M3:** soldiers start a build on their own.
- **M4:** kingdom object (saved world data) with a founding block.
