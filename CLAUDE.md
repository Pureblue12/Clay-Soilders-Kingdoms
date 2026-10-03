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

## Milestones

- **M0 — in progress:** test the existing flow and reproduce suspected bugs
  (guide: `docs/TESTING.md`, background: `docs/ANALYSIS.md`).
- **M1:** fix confirmed bugs only.
- **M2:** command that places an easel + blueprint.
- **M3:** soldiers start a build on their own.
- **M4:** kingdom object (saved world data) with a founding block.
