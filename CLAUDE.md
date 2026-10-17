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
`ScaffoldCleanupGameTests`, `StructureBuildGameTests`, `BuilderSafetyGameTests`, `KeepFallGameTests`, `BuildPlanGameTests`, `KingdomKeepGameTests`, `KingFoundingGameTests`, `KingdomEconomyGameTests`, `KingdomStoryGameTests`, `DecorationGameTests`, `KingdomPlannerGameTests`, `KingdomTownGameTests`), prints
`All N required tests passed` or the list of failures, then exits (about 10 s). Gradle reports
BUILD FAILED when any test fails. The test world lives in `neoforge/runs/gametest` (gitignored).
Templates: `data/csr/structure/gametest/platform.nbt` (16x8x16) and `terrain.nbt` (32x16x32, for test terrains);
regenerate with `tools/make_gametest_platform.py` (size arguments: see the script). `tools/suite.sh` deletes the test
world first, so every run starts fresh. With `SUITE_MINUTES` set (CI: 15) a test server still running after that long is
stopped, after printing samples of its server thread, batch starts, failures and the story test's progress lines. A test
must pass or fail inside its own tick (`runAtTickTime`, `onEachTick`): `helper.succeed()` from a server tick event marks it
done without telling its batch, and the suite never ends.

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
  use `skyAccess = true`. Builders working for a build follow paths up to their follow range (16) plus `build.path_range_bonus` (48, D61,
  D62); the planner's reach is measured with a margin (`Scaffolds.PATH_MARGIN`), so keep a test's chest within about 28 walked blocks of every block.

### Structure build tests (every Studio structure, separate workflow)

`StructureBuildGameTests` has builders build every structure promoted from the Studio, every level, from scratch
(about 156 builds). Too slow for the suite: the builds only exist when `CSR_STRUCTURE_BUILDS` is set (`all` or a shard
`k/n`; `CSR_STRUCTURE_BUILDS_ONLY=keep,church` limits them, `keep:2` to one level) and are optional. Run `tools/structure_builds.sh`
(writes `build/structure-results.tsv`) or the GitHub workflow "Structure builds" (`.github/workflows/structure-builds.yml`,
one machine per build up to 20, a result table per structure in the run summary; runs when structure files or the test
change, or by hand). Automatic runs skip keep L3 (`CSR_STRUCTURE_BUILDS_SKIP=keep:3`; it alone fills a machine's 45 minutes)
unless the change touches Java code, tuning or the keep's own files; a run by hand always builds it. While they run, the test server ticks as fast as it can (`StructureBuildSprint`, like `/tick sprint`;
results are in ticks, so only wall-clock time changes). Their per-tick checks run through `gametest/EveryTick`, never `helper.onEachTick` or `succeedWhen`: with timeouts
this long those two took 85% of the server's time (keep L3: 73 min, now 6).

Before any game test: `python3 tools/buildability.py [ids]` (standard library, no Gradle, about 2 minutes for all 156 levels
on 4 cores) simulates the builders with the mod's rules (build order, `BuilderReach`, the walking model of `ScaffoldGrid`,
scaffold columns outside the structure) and gives each level a verdict: STUCK (blocks no standing spot reaches, or gravel
placed before the block under it), RISKY (blocks placeable only from a spot with no way back, fence/wall/pane tops 4+ high
the pathfinder walks on), DECOR (finishes, but hanging lanterns under top slabs or plants on air won't match the file; the
fidelity check ignores a daylight detector's power and a brewing stand's bottles), SCAFFOLD, BUILDABLE. The workflow puts its table in the run summary. Against the
2026-10-11 run (107 of 156 built) it flagged 41 of the 47 failed levels with 3 false alarms; it misses training yard,
graveyard L2, market stall L3 and gatehouse L1. A model, not the game: keep it in step when the rules it copies change.
A build fails at once after 6000 ticks without progress, with a stall report, and ends as "too slow" (⏱️ in the table) after
45 minutes of real time (`CSR_STRUCTURE_BUILD_MINUTES`, workflow input `minutes`; 0 = no limit): keep L2 and L3 need longer, so
verify them with `only=keep` and `minutes=0`. For experiments the workflow also takes `tuning` (a tuning file's JSON for every build, `CSR_STRUCTURE_BUILD_TUNING`) and
`profile` (`CSR_STRUCTURE_BUILD_PROFILE`: each finished build writes a "CSR build profile" line, `BuildProfile`: blocks handed out,
follow-ups, stacks taken back, waits; the run prints them in its "Build profiles" step). The chest is kept stocked by
`dev/MaterialSupply` (most structures need more than 27 stacks; it stocks the items the easel hands out next, counting the slots partial stacks take,
brings back an item lost with a builder, D70, and merges the part stacks builders put back, making room for the item needed now, D79). The test area
(`gametest/structure_build.nbt`, 48x24x48) has a grass floor. The required test `buildListMatchesRegistry` keeps the
list in `StructureBuildGameTests.STRUCTURES` in step with the registry: add a row when a structure is promoted.

Current status (2026-10-04, after stage 3 Tall construction spike and its in-game fixes D41, D42): all 174 required tests pass (the suite takes a few
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
  (`BuildBlueprintGoal.MAX_CARRY_TICKS`) and a 20–40 tick retry cooldown (`builderNeverStuckCarrying`). Since D87 the
  timer counts ticks without getting 1 block closer, at most `build.carry_max_ticks` in all: the walk to keep L1's last
  roof flagpole took longer than 400 ticks (`builderOnALongWalkKeepsItsBlock`).
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
- Fixed (from the structure build run 38120112603 with damage recording, `BuilderSafetyGameTests`, D63, D64):
  - Builders died walking off the upper floors they had built (keep, town hall): upstream multiplies fall damage by
    100 like any environmental damage, so a 4-block drop killed. A working soldier now takes vanilla fall damage
    (`work.fall_damage_percent`), recorded in DAMAGE (`workingBuilderSurvivesAFall`).
  - Blocks were placed into builders (church: cobblestone walls, window panes), leaving them stuck inside. The easel
    refuses a block whose shape would overlap a creature's body (vanilla's placement rule; the farm fence showed a
    builder half inside the cell counts too, and so does one standing in the cell of a neighbouring fence, wall or pane,
    which grows an arm toward the new block), and a builder never places into its own cell
    (`blockIsNotPlacedIntoASoldier`).
  - Builders walled themselves into a 1x1 shaft of the keep. The easel refuses a block that would close the last way
    out of the cell a soldier stands in next to it (`ServerBlueprintPlan.wouldTrap`; `blockDoesNotTrapASoldier`).
    The builder keeps a refused block and tries again after `SOLDIER_IN_THE_WAY_RETRY_TICKS` (20) until its give-up
    timer runs out (not a failure: the farm showed that the failure cooldown and the round trip to the chest double a
    fence-heavy build's time); a released block waits `SOLDIER_IN_THE_WAY_TICKS` (40) before it is handed out again.
  - Town hall builders kept falling to their deaths after D63 (run 38124453564): the way back from the upper floor
    left through an unglazed window onto its open shutters, and vanilla's pathfinder treats an open trapdoor as a
    floor (the vanilla trapdoor trap). The soldiers' pathfinder now counts an open trapdoor's cell as air and the cell
    above it as floorless (`DoorPathing.withoutOpenTrapdoorFloors`, D65; `soldierDoesNotWalkOntoAnOpenTrapdoor`,
    `soldierWalksOverClosedTrapdoors`). They also fell through the tops of fences, walls and panes, which vanilla counts
    as floors though a tiny soldier misses the post: those cells have no floor for soldiers (`DoorPathing.withoutThinBlockFloors`,
    D68; `soldierDoesNotStandOnAFenceTop`). Off their paths (a hop that overshoots, a cut corner) they walked off ledges: a working soldier
    backs off from an edge with no floor within 3.5 below (`EdgeGuard`, D70; `workingBuilderDoesNotStepOffALedge`). A builder
    that finds no way to its target for `build.stranded_ticks` (or keeps being stopped by the edge guard, D73) is put beside its chest (`Stranded`, D72; `builderHeldAtAnEdgeIsBroughtBack`,
    `strandedBuilderIsBroughtBack`). Town hall builders still died at the foot of the front steps, 1.0 per fall (run
    38165377544): they hopped off a stair's low step at the top of a planned 3-block drop (vanilla hops whenever the feet
    are below the top of their block), and drops onto a half-height block measure 3.5. Soldiers no longer hop where a
    step does (`ClaySoldierMoveControl`), and a working soldier's fall up to `EdgeGuard.SAFE_DROP` costs nothing (D74;
    `soldierDoesNotHopOffAStairAtADrop`, `workingBuilderIsNotHurtByAPlannedDrop`). Keep builders went back and forth under the
    benches' top slabs (the owner's game, 2026-10-17): a tiny soldier fits under a top slab, and vanilla hopped it toward
    the slab's top from right underneath, into the slab. Under a ceiling a soldier never hops, and to climb it first steps
    out to a free cell with room to hop (`ClaySoldierMoveControl`, PATHING "stepping out"; `soldierClimbsOutFromUnderASlab`). Still open (not confirmed why): keep L2 builders also die at one spot in the
    forecourt, 2.0 per fall, after placing on the upper floor, with no trapdoor placed yet; needs a PATHING recording.
  - A cottage builder stood inside a fence post (the lantern post) for 9,200 ticks (suite run 38128880972): a tiny
    soldier fits beside the post, so the fence was placed around it, and vanilla then starts every path in a cell the
    soldier may not enter (one-node paths). The easel never places a fence, wall or pane in a cell a creature stands
    in, and a soldier inside such a cell starts its path from the nearest free cell beside it that it can slide to
    without hitting the post (D66; `fenceIsNotPlacedAroundASoldier`, `soldierInsideAFenceCellWalksOut`). Penned in a
    corner pocket between the post and two arms (farm L2/L3 on CI, 4,000 ticks with a path it could never start), a
    builder is put out onto the nearest free spot beside the cell (`OffTheFence`, BUILD event;
    `builderInAFencePocketGetsOut`).
  - Keep L3 builders stood in a 1x1 shaft of the keep's ground floor, cobblestone 2 high all around, for 15,000 ticks
    (structure build run 38129993219): vanilla's pathfinder drops a mob up to 3 blocks into any cell, pits included.
    The soldiers' pathfinder blocks every cell without a way out sideways (`DoorPathing.withoutPits`, D67;
    `soldierDoesNotDropIntoAPit`).
- Fixed (berry bushes, D81): hospital L3's three builders died in its garden (structure build runs 38222520802,
  38272077859), each after ONE "hurt for 1.0 by sweetBerryBush": upstream multiplies environmental damage by 100, so
  brushing a bush (a tiny soldier's body clips a bush cell while walking past or turning a corner beside it) killed at
  once. A working soldier now takes vanilla's damage from a berry bush or cactus (`work.plant_damage_percent`, DAMAGE event;
  `workingBuilderSurvivesABerryBush`). Also, a bush has no collision box, so the easel's "block would overlap a creature"
  rule would place it on a soldier: the easel now also waits while any part of a creature is in the cell of a block that
  hurts whoever is inside it (sweet berry bush, wither rose, fire, powder snow; `ServerBlueprintPlan.hurtsInside`;
  `berryBushIsNotPlacedOnASoldier`).
- Fixed (slow builders, D79): every block was a round trip (easel, chest, ONE item, spot, back), about one block per
  114 ticks for keep L3's crew. A builder now takes up to `build.carry_count` (16) of the item and, after placing one, goes
  straight to the nearest free block of that item in the same layer it can reach (`ServerBlueprintPlan.getFollowUpRequest`:
  no doors, nothing over a spot still to fill); only when there is none does it walk back and put the rest in the chest
  (`followUpIsNearestInTheSameLayer`, `builderPlacesARowWithOneTripToTheChest`).
- Fixed (kingdom builders far from the easel, 2026-10-16 in-game recording): a builder looks for its easel only within 8
  blocks of where it stands, so a kingdom's builders picked farther from the keep's easel stood still forever. A builder
  with no easel near it now walks to its chest (beside the easel) first, BUILD event "no easel within 8, walking to its
  chest" (`builderFarFromItsEaselWalksThereAndBuilds`). Also, a builder worked for the easel nearest to ITSELF, so the
  lumber camp's builders, picked while standing at the finished keep, kept asking the keep's easel: a builder now looks
  for its easel around its chest first, then around itself, and again when its chest changes
  (`builderWorksForTheEaselBesideItsChest`).
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
- **At most one kingdom per team**, ACTIVE or RUINED; a new king of a team with a RUINED kingdom founds a new one in its place (D95). Change kingdoms only through `Kingdoms` (it marks the data
  dirty, writes the chronicle, records KINGDOM events).
- **Founding** (D93, the owner's call 2026-10-16: "simply dropping a single gold piece to a soldier and the rest will take
  care of itself"): a soldier who picks up a gold ingot is a king, and a king whose team has no kingdom founds one by
  himself. He picks a spot at random among the good keep sites near him (`kingdom.found_pick_tolerance`), walks there and
  founds the kingdom around himself (`KingFounding`, `KingFoundingGoal`); players near him are told. A spot he can't reach
  (`kingdom.found_walk_ticks`) or where founding is refused is left out; after `kingdom.found_tries` in a row he rests
  `kingdom.found_retry_ticks`. No team loyalty is involved anywhere in kingdoms: a kingdom's soldiers work without a loyal
  player (`WorkSelectorGoal`). Kings in a GameTest server stay put unless a test calls `KingFounding.enableInTestsForTests`.
  `/csr kingdom found` founds at once around the nearest king without a kingdom (within `kingdom.found_search_radius`).

  Refused: unsupported dimension (Overworld only), not a living king, team already has a kingdom, claim overlaps another
  kingdom, or the ground can't hold a keep (terrain check, below).
- **Bosses from eggs** (an original-mod feature: a soldier picking up an egg became a random clay boss that attacks
  everyone; one killed the king in the owner's test) are off: `soldier.boss_from_eggs` 0 (`soldierpoi/BossEggs`, D94).
- **The claim** is a circle of `kingdom.claim_radius` around the king, fixed at founding.
- **The king's death** (NeoForge death event, lowest priority) sets RUINED and writes a chronicle line.
- **Population** is counted every `kingdom.census_interval_ticks`, but only while the whole claim is loaded;
  otherwise the last count stays, with its time.
- **Stockpile:** `Kingdoms.addResource` / `removeResource` (all or nothing, shortfall reported).
- **Commands** (any player, every install): `/csr kingdom found | disband [team] | book [team] | status [team] | list`;
  without a team they act on the kingdom you stand in, else the nearest one.
  `goto_king <team>` needs operators. The book (status page with "Go to the king", stockpile, buildings, chronicle)
  rewrites itself whenever it's opened, in the hand or on a lectern (D24).
- **Dev only:** `/csr dev kingdom stock_add|stock_remove <resource> [amount]`, `stock_show` (book: Kingdom).

### The kingdom builds its keep (stage 4)

Code: `common/.../kingdom/KingdomKeep` (planner and server tick), `KeepSite` (pure site search), `KingdomStore`
(stockpile as the builders' chest store, through `dev/MaterialSupply.Store`), `KeepThrone` (throne spot per structure level),
`entity/goal/KingThroneGoal`. Decisions D88–D91, D93; numbers `kingdom.keep.*`, `kingdom.king_bonus_health`,
`kingdom.throne_walk_ticks`.
- Every `kingdom.keep.check_ticks` an active kingdom without a building plans its keep (master plan v0: the first
  building is the keep): nearest flat ground to its center, levelled at once, easel + chest like `/csr build`. No site:
  retried after `kingdom.keep.plan_retry_ticks`; the book's Keep page says why.
- Builders: up to `kingdom.keep.builders` soldiers of the team in the claim with no job and no POI, not the king; wheat
  holders first, the others are handed wheat (D93). Materials: when the keep starts, everything it needs goes into the
  stockpile (`kingdom.keep.free_materials`, until stage 5's gathering); the chest is stocked from the stockpile; items
  put in the chest that aren't needed soon go to the stockpile.
- Finished (easel finished): BUILT, chronicle line, the kingdom puts a gold block (the throne) in its spot (keep L1: the
  middle of the king's chamber; L2/L3: in place of the great hall's wooden throne), the king gets
  +`kingdom.king_bonus_health` and stands on the gold block. Disbanding takes the health back.
- The server tick does nothing in a GameTest server (other tests' kingdoms stay untouched): tests call
  `KingdomKeep.update` themselves.
- Saved: `KingdomBuilding.level` (kingdom data version 2). Supplies, crews and planning problems are memory only.

### Decoration (stage 5a, D103)

Code: `common/.../structure/StructureDecoration`. What builders can't place comes from the structure file itself: blocks
without an item (water, lava, fire, potted plants, farmland, dirt paths, filled cauldrons), block entity data (sign text,
banner patterns) and entities (item frames, armor stands, paintings). `StructureBlueprints` splits the file: builders get
the rest (signs and banners placed blank), and the decoration is placed with vanilla's structure placement (the easel's
rotation and mirror) when the last block is placed, exactly once; until then it is saved with the easel's plan (BUILD event
"decoration: ..."). Water flows like any water, so a structure must hold it in. `tools/buildability.py` and
`tools/build_plan.py` leave blocks without an item out the same way (`buildability.decoration`). Test structure
`csr:test_garden` (`tools/make_test_structures.py`); tests `DecorationGameTests`. Ruin stages that remove it don't exist yet.

### The kingdom's economy (stage 5)

Code: `common/.../kingdom/KingdomEconomy` (master plan v1, buildings after the keep, workers), `PlotPicker` (pure: plots
around the keep), `WoodCost` (the carpenter: wooden blocks from logs of any kind), `entity/goal/workgoal/FellTreesGoal`
(woodcutter), `TendFieldGoal` (farmer). Decisions D96–D101; numbers `economy.*`.
- After the keep is BUILT, `KingdomEconomy.update` (with the keep's, every `kingdom.keep.check_ticks`) builds `PLAN` in order:
  lumber camp, wheat field (starter buildings, materials given with `economy.free_starter_buildings`), cottage (paid: waits
  for the logs of its wooden blocks and `economy.food_per_building` wheat; non-wooden materials given with
  `economy.free_other_materials`). Plots: free ground around the keep facing it, away from water and other claims, outer
  buildings up to `economy.plot.outer_reach` beyond the claim, levelled at once like the keep's site.
- Builders of a finished building (keep included) are let go once its scaffolding is down. While nothing is being built, idle
  soldiers become woodcutters (`economy.woodcutters`) and farmers (`economy.farmers`): job = the goal, POI = their building's
  chest. They deliver there; the chests of finished buildings are the storehouse (swept into the stockpile).
- Builders of a paid building (the cottage) eat its wheat and are well fed while they build: Speed and Health Boost for
  `economy.meal.ticks` (`Meals`, D102; `mealMakesABuilderFasterAndTougher`).
- The carpenter uses the stockpile's spare planks before cutting logs (a log cut for planks leaves some over; without this a
  cottage's door waited forever, found by the story test; `sparePlanksAreUsedBeforeLogs`). A paid building's logs count spare
  planks as logs (4 to a log). It never touches planks or logs the build still needs as they are (its remaining blocks of
  that item less what its chest holds): it once cut the keep's own spruce planks into dark oak logs and stairs, and the keep
  stopped 13 planks short in the owner's game (`carpenterLeavesTheBuildsOwnPlanks`).
- New jobs are appended to the soldier's job list (saved job numbers of older jobs stay the same).
- Dev book "Economy": look after it now, finish the current building (pastes it, keep included), supply the next building,
  ripen the fields.
- `KingdomStoryGameTests.kingdomGrowsFromOneGoldIngot` plays the whole story like a player, no dev shortcuts: scattered
  plain soldiers, a dropped gold ingot, then founding, keep, camp, field, woodcutters, farmer and a paid cottage, with small
  stand-in structures (`KingdomKeep.useKeepForTests`, `KingdomEconomy.usePlanForTests`; `test_camp`, `test_field`,
  `test_cottage`) on the 48x48 grass template; the server sprints through it. A change to any step's hand-over must keep it green.

### The kingdom planner (stage 6, part 1)

Code: `common/.../kingdom/layout/KingdomPlanner` (pure: the master plan), `MasterPlan` (plain data: plots, roads, wall
corners, gates), `Rect`, `Side`, `Ground`; world side `kingdom/KingdomLayout` (draws the plan, lays roads, fills caves, the
dev view), saved by `MasterPlanCodec`. Decisions D105–D116; numbers `layout.*` and `terrain.cave_probe_depth`.
- Once the keep is BUILT and the kingdom has no other building, `KingdomEconomy.update` calls `KingdomLayout.update`: it scans
  `layout.scan_radius` around the keep and plans, from the blueprints' real footprints in all 4 directions, with the keep in the
  middle (D116): a ring road round the keep, a square in front of it, four streets out of the ring (one each way), house plots along
  them (fronts on the road, nearest the keep first), a straight-sided wall rectangle with 4 gates, the outer buildings in a band outside it (paths to the street ends, never in front of a gate),
  a plot by water for a fishing village when there is water. It also keeps a plot for every ready Studio building the kingdom adds later (`KingdomLayout.roster`, `layout.roster`, D111: inner and outer, not street decoration, at the top level's size; outer ones try a band twice as wide when the first is full), so the wall fits the whole town (about 190 by 140); the n-th building of a structure takes its plan plots first, then those kept for later. Every plot's easel stands more than `BuildSitePlanner.EASEL_SPACING` (8) from the keep's and every other plot's easel (the easel check refuses closer ones). `LayoutChecker` must find no problem (now also: nothing on a road
  or the wall), or there is no plan (retried every `kingdom.keep.plan_retry_ticks`). The claim grows to hold the plan.
- Saved in the kingdom (data version 3, optional `plan`). Roads are laid at once as dirt paths on earth (`layout.lay_roads`), with a spruce plank bridge over water up to `layout.bridge_max_length` long with land at both ends (D114).
- After its own buildings (`KingdomEconomy.PLAN`) the kingdom builds every other plot of the plan, the ones kept for later
  included, one at a time in the plan's order, at level 1, paid like the cottage (`KingdomEconomy.townSlot`, `economy.build_town`,
  D115); a structure whose plot is refused 3 times in a row is left out until restart.
- `KingdomEconomy.plan` puts the n-th building of a structure on the plan's n-th plot of it, re-judged as it is now; when it
  can't be used, the stage 5 `PlotPicker` picks a plot off every planned one. Every levelled plot gets its caves filled
  (`KingdomLayout.sealHollows`).
- Caves: `TerrainScanner` notes the first open block within `terrain.cave_probe_depth` under the ground (`TerrainScan.hollow`).
  Only the planner weighs it; keep sites and stage 5 plots just report it (a GameTest floor stands on a layer of air, D106).
- Off in a GameTest server unless a test calls `KingdomLayout.enableInTestsForTests(true)` (the stage 5 tests stay as they were).
- Players within 128 blocks see the plan for `layout.show_seconds` when it is drawn up, and a new building's plot (green) when it starts (D112). Where the wall line runs over water the sea guards the town: no wall there, and the view leaves it out (D113).
- Dev book "Economy": "Show the town plan" (`/csr dev economy plan`): particles for 30 s (white sparks: the wall, flames: gates,
  green: building plots, orange: plots kept free) and the plan in chat.
- Tests: `KingdomPlannerGameTests` (pure cases in `KingdomPlannerCases`, run them outside the game by compiling that file with
  the `kingdom/layout` classes; a save round trip; the scan finding a cave; roads as dirt paths) and
  `KingdomTownGameTests.kingdomPlansItsTownOnRoughLand` (the whole story on stepped land with a cave, an 84x84 template
  `gametest/kingdom_town`).

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
- `ScaffoldPlanner` (pure) simulates the build like builders work and plans scaffold (columns inside the footprint
  only when the outside ones fall short, D69). Approaches: (a) outside ramp,
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
  (`DoorPathing.Ground.followThePath`). Every builder's search for a standing spot next to a blueprint block has its own longer, bigger
  path search (`build.path_*`, D61), and since D62 every path a builder walks while it works for a build (to the easel, to
  its chest) has the same longer limit (`DoorPathing.Ground.setRangeBonus`); paths outside building work keep vanilla's. A builder's search for a
  standing spot or a column's base that runs out of steps goes on from where it ended (`ChainedPath`, up to 3 searches, D76): the way up
  the keep's two staircases ran out of steps on the grass around the chest. Walking to a column's base may take
  `scaffold.walk_ticks_per_step` ticks per step of the path (at least 200).
- **Column footing (D77):** vanilla scaffolding falls at once unless the block under it has a full top face, so the planner
  never stands a column on a stair, slab, carpet or other floor without one (`ScaffoldInput.noFooting`, blocks still to place
  count as what they will be). Keep L2's annex columns stood on the pews and were never there to climb.
- **Recorder SCAFFOLD:** planned, placed, removed, climbing, stuck, cleanup, kept.

### Build plans (pre-baked routes, D82)

Every structure level ships `data/csr/build_plan/kingdom/studio/<level>.json`, made by `python3 tools/build_plan.py [ids]`
(standard library, about 2 minutes for all levels) from the structure file alone: the build order as waves and, per block,
up to 4 spots to stand on. Worked out by taking the finished structure apart on buildability.py's model; every wave is
checked from bare ground. Promote runs it; CI's "Build plans up to date" step (`--check`) fails on a missing or stale plan,
and the "Structure builds" summary has its table. Never edit a plan by hand: change the structure, run the tool.
- `StructureBlueprints` loads the plan (`BuildPlans`) and orders the blueprint by it (`ImmutableTemplate.withPlan`); the
  plan is saved with the easel's build.
- `ServerBlueprintPlan.getRequest` hands out a random block of the lowest unfinished wave, away from blocks other builders
  carry and among the nearest to the builder (steps of 4 blocks); when none can be handed out and no builder holds one, it goes on with the
  next wave that has something (stacks may then follow up in any wave up to it), and only then searches every block as before (BUILD event "plan: ...").
  A builder carrying a stack (D79) takes its next nearby block from that same wave (`getFollowUpRequest`), or from the next
  `build.plan_lookahead_waves` waves when the block is off every planned standing spot of an earlier wave's block still to place
  (`build.plan_follow_up_clearance`).
  While the wave's last blocks are carried, idle builders take blocks of the next `build.plan_lookahead_waves` (3) waves clear
  of every earlier wave's blocks still to place and their spots (`build.plan_lookahead_clearance`).
  A block builders gave up twice (`ServerBlueprintPlan.PLAN_GIVE_UPS`) leaves the plan: it no longer holds its wave back, and
  it is handed out without the plan's spots, so scaffolding can start (keep L1's chest; `blockGivenUpTwiceLeavesThePlan`); after one give-up its builder tries the plan's spots before the nearest one, and once more after the third (chapel's annex chest).
- Builders walk to the nearest spot they can place from (the normal search) and use the plan's spots only when that fails (`build.plan_spots_first` 0; with 1 they went to the plan's nearest free spot first, `BuilderReach.findPlanned`, and builds took 30% longer than main instead of 16%).
- Blocks the plan leaves out (need scaffold) come after it. `build.follow_plan` 0 turns it all off.
- Natural touches for builds that follow a plan: a random pause after placing (`build.pause_ticks`), each builder's own pace
  (`build.walk_speed_spread_percent`).

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
- Scaffold columns stand outside the structure's footprint first; only when those can't reach every block (a roof
  more than 2 blocks inside the footprint, the chapel steeple's cap) does the planner add columns standing on the
  structure's own floors inside it (D69): on any floor builders walk to, attic floors and lower roofs included (D75). An inner
  column the simulated build never gets into is dropped. The planner leaves doors out and counts wooden doors as open (D71),
  so a building's doorway is a way in. A block counts as in reach within 24 walked blocks of the chest, a column's base within
  56 (the builders' real limit, 16 + 48, minus `Scaffolds.PATH_MARGIN`; D75).
  A block no column reaches within 8 (the church spire top, 9 and 10 above the highest column cell under it) is placed by a
  builder stretching up from inside a column, up to `scaffold.column_reach_bonus` (2) more (D78); higher than that it stays unbuilt.
  The planner lets soldiers walk under a block whose shape doesn't reach its cell's bottom (hanging lantern, top slab; D80),
  and when nothing else adds scaffold it tries once more with the walks of up to 3 chained searches (184 blocks; D80); a builder climbs
  the lowest column cell in reach whose base it can walk to; the soldiers' pit rule (D67) counts such a block over a neighbour
  as head room, so a nook whose way out passes under a hanging lantern is no pit (`soldierWalksIntoANookUnderAHangingLantern`).
- A blueprint block that is already there and replaceable (e.g. short grass where short grass grows) counts
  as free but can never be placed (placing the same block fails), so the blueprint never finishes; the
  cooldown only stops it from starving the rest. Not confirmed in game; not fixed.
- Layer-by-layer ordering: blocks are handed out as all full cubes and stairs (bottom to top, roof included; stairs
  since D69, so inside staircases exist before the floors above them), then other non-cube blocks, then blocks with data (beds), then doors. A wall-hung block (wall torch, sign, banner, head, ladder) waits until the block it hangs on is placed (D83).

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
| `/csr dev kingdom keep_materials` / `keep_now` | put everything the keep needs in the stockpile / let the kingdom look after its keep at once (book: Kingdom) |
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

### Session log (always on, the owner's call 2026-10-17)

Every development game is logged in full with nothing to press: from world open to close, every recorder category is on
and every event goes straight to `neoforge/run/debuglogs/csr-session-<time>.txt` (`debug/SessionLog`, started by
`neoforge/.../event/SessionLogEvents`; never in a GameTest server). Every 30 s of real time it adds a snapshot of every
kingdom (keep, economy, buildings, stockpile) and every loaded clay soldier. Flushed every second, so it can be read while
the game runs. When the owner reports something from a game, read the newest session file first; don't ask him to record.

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
Instrumented so far: `BuildBlueprintGoal` (incl. `FallTrace`: while DAMAGE is on, every builder's position each tick and
its route are kept for 3 s, and a fall writes them as one DAMAGE event "fall trace"), `ServerBlueprintPlan.getRequest` (summary),
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
- **Stage 4 (Tier-1 keep built autonomously) — done (2026-10-16, passed the owner's in-game test):** see "The kingdom builds its keep".
- **Stage 6 (Layout and growth) — part 1 built (2026-10-17, waiting for CI and the owner's in-game test):** the kingdom
  planner, see "The kingdom planner".
