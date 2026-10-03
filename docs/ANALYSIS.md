# Clay Soldiers Remake: Analysis for the "Kingdoms" Plan

Read-only analysis of the `1.21.1` branch of [Bumble-B33/csr](https://github.com/Bumble-B33/csr).
All paths are relative to `common/src/main/java/net/bumblebee/claysoldiers/` unless they start
with `neoforge/`. Findings come from reading the code, **not** from running the game. Anything marked
*(verify in game)* is what the code implies but hasn't been tested yet.

---

## Glossary

| Term | Meaning |
|---|---|
| **Entity** | Anything in the world that moves or acts: players, mobs, dropped items. Each clay soldier is an entity. |
| **Goal** | Minecraft's unit of mob AI, a small class with `canUse()` (should I start?), `start()`, `tick()` (do one step, 20×/second), `canContinueToUse()` and `stop()`. |
| **GoalSelector** | Each mob has two lists of goals. `goalSelector` controls *actions* (walk, attack, work) and `targetSelector` chooses *who to attack*. Each goal has a priority number, and **lower numbers win**. |
| **Goal flags** | Labels like `MOVE`, `LOOK`, `TARGET`. Two goals with the same flag can't run together, so the higher-priority one wins. A goal with **no** flags never blocks anything. |
| **POI** | "Point of interest", a block or item a soldier walks to and interacts with. This mod has two different kinds (see §4). |
| **Block entity** | Extra data attached to a block (a chest's inventory, an easel's blueprint). |
| **Capability** | A NeoForge way to ask a block "can you store items?" or "can you hand out building jobs?" without knowing what block it is. |
| **NBT / saving** | Minecraft's save format. `addAdditionalSaveData` / `readAdditionalSaveData` are where an entity writes and reads its save data. |
| **SavedData** | A per-world file for data that doesn't belong to any one entity or block. |
| **Datapack registry** | Content defined in JSON files instead of Java code (teams and blueprints work this way here). |
| **Feature flag / experiment** | A toggle chosen when creating a world. Content behind a flag doesn't exist unless it's turned on. |
| **Chunk** | A 16×16 column of the world. Chunks far from players are *unloaded* (saved to disk and frozen). |
| **Simulation distance** | How far from a player entities actually get ticked (updated). |
| **Pathfinding** | Working out a walking route. It's one of the most expensive things a mob does. |
| **God class** | One very large class that does too many jobs, which makes it hard to change safely. |

---

## Overview

- **What the mod is:** tiny clay soldiers you spawn from items. Their abilities come from what they carry or wear, and this is *data-driven*, meaning item effects are defined in data maps rather than hard-coded. Soldiers belong to color **teams**, and different teams fight automatically.
- **There's already some "village" groundwork:** a job system (`WorkSelectorGoal`) with farming, digging, item hauling and blueprint building; storage assignment (any chest); and a blueprint system that builds vanilla village houses block by block.
- **The catch:** almost all of it needs a **player** to set it up. The player has to claim the team, give the soldier wheat, pick a job with the Clay Brush, assign a chest with the Clay Brush, and place an easel with a blueprint. Teams are fixed color definitions with no memory of territory or property.
- **Code shape:** about 320 Java files in `common/`, with nearly all soldier logic in one 2,026-line class, `entity/soldier/AbstractClaySoldierEntity.java`.

---

## System-by-system findings

### 1. Work goals (`entity/goal/workgoal/`)

**Who can work at all:** only soldiers whose attack type is `PACIFIST`
(`AttackTypeProperty.canWork()` returns `this == PACIFIST`). A soldier becomes pacifist by carrying
**wheat** in a backpack slot (`neoforge/.../datagen/ModDataMapAndTagProvider.java:154`).
**Workers can't fight, and fighters can't work.**

**How a job is chosen:** by a **player**, by right-clicking a soldier with the **Clay Brush in WORK mode**.
That calls `WorkSelectorGoal.cycleWorkMode()`, which steps through: *resting → crops → pick up → seeds → dig → build → resting*.
Nothing in the code chooses a job automatically.

**Base classes:**
- `IWorkGoal`: interface for a job's display name and status (status is limited to the numbers 0–7).
- `AbstractWorkGoal`: shared helpers: walk to the assigned POI (`moveToPoi`), connect to the POI's item storage (`getCapCache`), and take a 20-tick break (`takeAShortBreak`).
- `WorkSelectorGoal`: the single "job slot" goal that wraps the active job (details in §2).

| Job (class) | Starts when (`canUse`) | What the soldier does | Needs | Ends when |
|---|---|---|---|---|
| **BreakCropGoal** (extends vanilla `MoveToBlockGoal`) | The `mobGriefing` game rule allows it, and a fully grown crop (`CropBlock.isMaxAge`) is within 16 blocks. Checks again every 2–4 s. | Walks to the crop, does a "stomping" animation for about 20 ticks, then `destroyBlock(pos, drop=true)`. | Nothing. Doesn't need a chest. | The crop is broken or the target is no longer valid (normal `MoveToBlockGoal` rules). **It doesn't replant or collect the drops.** |
| **PickUpItemsGoal** | Dropped items are within 8 blocks (±8 vertically), or the soldier is already carrying something. | Walks to the nearest dropped item and picks it up (the soldier allows *any* item while this job is active). Carries it to the assigned POI and puts it into storage, then takes a 1 s break. | A storage POI (chest) to deliver to. Without one, it picks up but never delivers. | No more items nearby. |
| **PlaceSeedsGoal** | Has an assigned POI with storage. | Takes **1 seed** (tag `villager_plantable_seeds`) from the chest, finds the nearest farmland with air above it (within 16 blocks), and places the crop block. | A chest with seeds, plus farmland. | Placed the seed, gave up after ~1–3 min, or the chest is empty (takes a break, then tries again). |
| **DigHoleGoal** (+ `DigBreakManger`, `StairPosMap`) | Has any assigned POI. | Digs a **5×5 shaft straight down**, centered on the POI, layer by layer, placing a **cobblestone stair spiral** along the walls as it goes. Hard blocks need several soldiers at once (2 for blocks a wooden tool can't mine, up to 64 for the hardest blocks); break progress is shared in `DigBreakManger`. Broken blocks drop as items. | A POI position. The stairs are **created from nothing** (no cost). | **Never ends on its own.** There is no depth limit (see Risks). |
| **BuildBlueprintGoal** (only if the *blueprint* experiment is on) | Has an assigned POI with storage. | Finds an **Easel** within 8 blocks horizontally and ±2 vertically, asks it for a "request" (one block to place), fetches that item from the chest, walks to the spot, and places the block. Brings leftovers back. | A chest with materials, an Easel holding a blueprint, and the experiment enabled. | The blueprint is finished (no more requests). |

**How work interacts with saving:** `AbstractClaySoldierEntity.addAdditionalSaveData` calls `workSelector.resetGoal()`, which **stops the active job every time the soldier is saved**. That happens on world autosave as well as on unload. The goal itself keeps running, but in-progress state (the current blueprint request, dig break progress) is cancelled *(verify in game)*.

### 2. Goal priorities and decision-making (`AbstractClaySoldierEntity.registerGoals`, line 253)

Remember: **lower number = higher priority**, and goals only block each other if they share a flag.

| Prio | Action goals (`goalSelector`) |
|---|---|
| 1 | `ClaySodlierBreathAirGoal`: swim up for air |
| 2 | `ClayMobSitGoal`: sit when ordered |
| 3 | `WorkSelectorGoal`: the current job (see §1) |
| 4 | `UseAssignedPoiGoal` (go use the assigned block), `FindNearestPoiGoal` (look for useful items or blocks), `ClaySoldierMountGoal` (ride things) |
| 5 | `ClaySoldierRangedAttackGoal`, `ClaySoldierMeleeAttackGoal` |
| 6 | `ClaySoldierMeleeSupportGoal`: healers and support |
| 7 | `ClayMobFollowTeamOwner`: follow the player who owns the team |
| 8 | `TemptGoal`: follow a player holding clay "food" |
| 9–10 | `RandomStrollGoal`, `RandomLookAroundGoal`: idle |

| Prio | Target goals (`targetSelector`) |
|---|---|
| 0 | `ClaySoldierHurtByTargetGoal`: hit back at whoever hurt me |
| 1 | `NearestRideableTargetGoal` |
| 2 | `ClayMobOwnerHurtByTarget`: defend the owner |
| 3 | `ClayMobOwnerTarget`: attack what the owner attacks |
| 4 | `ClaySoldierNearestTargetGoal`: nearest enemy within the 16-block follow range |

**How "fight vs work vs idle" is actually decided:**
- **Fight vs work isn't a runtime decision.** It's fixed by the attack type. `PACIFIST` soldiers can work but never pick a target (their attack predicate always returns false). Every other type fights but `WorkSelectorGoal.mayUse` refuses to run.
- **Work also requires a player owner:** `mayUse` returns false if `getClayTeamOwnerUUID() == null`, meaning no player has claimed this team.
- **Idle** happens when nothing above it can run.
- ⚠️ `WorkSelectorGoal` sets **no flags**, so the goal system won't stop priority-9 `RandomStrollGoal` from running at the same time and redirecting the soldier's navigation mid-job *(verify in game)*.
- The work wrapper allows at most **15 jobs** (`MAX_INDEX_SIZE`), because the job number and status are packed into a single byte for syncing to the client.

### 3. Blueprint system (`blueprint/`, `block/blueprint/`, `item/blueprint/`)

**It's experimental:** the blocks, items and the building job all check the `csr:blueprint` feature flag
(`neoforge/src/main/resources/META-INF/feature_flags.json`, `ClaySoldiersNeoForge.BLUEPRINT_FLAG`).
It has to be enabled as an *experiment* when you create the world.

**How a blueprint is defined:**
- `BlueprintData` is a datapack registry entry (`ModRegistries.BLUEPRINTS`) with `location` (the path to a **vanilla structure `.nbt` file**), `name` (translation key) and `marking`.
- Three ship with the mod (`neoforge/.../datagen/ModDatapackProvider.java:59`): `small_house`, `small_farm` and `large_house`, which point at vanilla **plains village** structures.
- `BlueprintManger` (a reload listener) loads each structure into an `ImmutableTemplate`, skipping blacklisted blocks (`ModTags.Blocks.BLUEPRINT_BLACK_LISTED`).
- `BlueprintPlan` / `ServerBlueprintPlan` hold the **remaining block list** and an **item shopping list** (`itemCountMap`).

**How it gets built, block by block:**
1. A **player** crafts a blueprint item at the **Escritoire** (`menu/escritoire/EscritoireMenu`, from blueprint pages) and right-clicks an **Easel** with it (`EaselBlock.useItemOn` → `EaselBlockEntity.setBlueprintData`).
2. The structure is positioned **2 blocks behind the easel**, centered, rotated to the easel's facing (`EaselBlockEntity.getTemplateBase`). It can be mirrored by right-clicking.
3. A soldier running `BuildBlueprintGoal` asks the easel for a request. `ServerBlueprintPlan.getRequest` walks the block list and returns the first block that is **reachable** (it builds a path to it) and **placeable** (the spot is air or replaceable). Each block can only be requested again after a timeout (`BlueprintBlockInfo.hasAvailableRequest`).
4. The soldier takes that item from **its assigned chest**, walks over, and `EaselBlockEntity.doBlueprintRequest` → `ServerBlueprintPlan.tryPlacing` places the block and removes it from the list.
5. Players can also feed blocks by hand by right-clicking the easel.

**Where materials come from:** only the soldier's **assigned chest** (any block with an item-handler capability).
⚠️ **Probable bug (free blocks):** `BuildBlueprintGoal.getRequestFromStorage` sets the request to "placing" **even if the chest had none of that item**, and `doBlueprintRequest` places `request.getItem().getDefaultInstance()` instead of what the soldier is actually carrying. So with an empty chest, soldiers would still build the whole house *(verify in game)*.

**Can it start without a player?** **No.** Code can do it: there's nothing stopping code from placing an `EaselBlock` and calling `setBlueprintData` (public method). But no existing code ever does this, and the soldier also needs a player-assigned chest, a player-chosen job, and a player-owned team.

**Other limits:** no terrain clearing or leveling (blocked spots are simply never requested, so a site with a tree in it **never finishes**). The easel must be within about 8 blocks of the soldier. `hasStarted` is never set to `true` on the server, so the mirror can be flipped mid-build.

### 4. Points of interest (`soldierpoi/`, `claypoifunction/`)

The word "POI" is used for **two unrelated things**:

**(a) "Effect" POIs: items or blocks that change a soldier** (`SoldierPoi`, `FindNearestPoiGoal`)
- Defined in **data maps** per item or block: a `predicate` (who can use it), a list of `ClayPoiFunction` effects, and a `break_chance` (the chance the item or block is used up).
- Effect types (`ClayPoiFunctions`): `EffectFunction` (potion effects), `DyeSoldierFunction` (recolor), `SetItem` (give or replace gear), `ConvertTo` (turn into zombie, vampire or wraith), `SelectRandom`.
- Examples: flint turns a held stick into a sharpened stick; dye recolors; nether wart makes a vampire; an end rod makes a wraith.
- **Finding:** about 1 in 60 ticks, a soldier scans dropped items within its follow range, then scans blocks in a roughly 19×19×9 area, and walks to the nearest usable one. When it arrives (`stop()`), the effect fires. There's **no claiming**, so many soldiers can head for the same item.

**(b) "Assigned" POI: a soldier's workplace** (`ClayMobEntity.poiPos`, `UseAssignedPoiGoal`, `capability/AssignablePoiCapability`)
- A single `BlockPos` per soldier, **set by a player** with the Clay Brush in POI mode (`ClayMobEntity.clayBrushEffect`). It's saved with the soldier.
- When the soldier is **not** working, `UseAssignedPoiGoal` walks to it and "uses" it. For the **Hamster Wheel** that means running to make power. For a **chest** (`StorageWrappedPoiCap`) it means taking gear from the chest to fill empty equipment slots, then the POI is cleared (one-time use).
- When the soldier **is** working, the assigned POI is the job's home chest (see §1).
- **No claiming or reservation.** Any number of soldiers can share one chest.

### 5. Teams (`team/`)

| Aspect | How it works |
|---|---|
| **Definition** | `ClayMobTeam` entries in a **datapack registry** (`ModRegistries.CLAY_MOB_TEAMS`). About 19 ship with the mod (colors, plus `csr:normal` and `csr:no_team`). Fields: name, color, friendly fire, a "from" item, team-wide soldier properties, and an optional allowed-players list. They're **fixed at load time**, so new teams can't be created during play. |
| **Membership** | Each soldier stores only its team **id** (`ClayMobTeam.save` → NBT `team_id`), synced to clients through `ClayMobTeamOwnerEntity.VARIANT_SYNC`. There is **no list of members**. Finding a team's soldiers means searching the world. |
| **Saved per world** | Only **loyalty**: `TeamLoyaltyManger.ServerTeamPlayerData` (SavedData `clayTeamLoyalty`, stored with the overworld) maps *team → one player*. A player can be loyal to only **one** team (`putPlayer` removes them from their old one). A player claims a team by right-clicking its soldier (`ClayMobEntity.tryClaimingTeam`). |
| **Ownership of things** | **None.** Teams don't own territory, storage, buildings or easels. A team only has soldiers (implicitly) and a player. |
| **Relations** | Purely binary: **same team = friends, different team = enemies** (`TeamHolder.shouldAttackTeamHolder`). There's no neutral or ally state. Special attack types override this: `AGGRESSIVE` attacks everyone, and `KING` attacks other kings (`AttackTypeProperty.royaltyPredicate`). |
| **Quirks** | `ClayMobTeam.equals` compares *name, color, friendly fire and players*, not the registry id, so two different teams with the same name and color count as one team. `isCooperative()` always returns `true`, which means `hasNoTeam()` is always `false` (dead code). |

### 6. Persistence and chunk loading

- **Soldiers never despawn:** the `ClayMobEntity` constructor calls `setPersistenceRequired()`.
- **Nothing keeps chunks loaded:** there are no chunk tickets anywhere in the code. When a player leaves, soldiers outside **simulation distance** stop ticking, and unloaded chunks are saved to disk. **The kingdom freezes** until a player comes back.
- **What survives a save:** team id, assigned POI, chosen job index (`WorkMode`), carried item, equipment, sitting state. The easel saves its blueprint and **remaining blocks**, so building progress persists.
- **What's lost:** in-flight job state (the current blueprint request, dig progress) and `DigBreakManger`, which is a **static in-memory map** that's never saved.
- **Team loyalty** is in SavedData and survives. A team that disappears from the datapack is removed from it on load.

### 7. Performance

**There are no limits or caps.** Nothing limits the number of soldiers. `MultiSpawnItem` spawns a whole stack at once, and there's no AI throttling beyond vanilla's random intervals.

| Hotspot | Why it's expensive | Scale concern |
|---|---|---|
| `ClaySoldierNearestTargetGoal.findTargetSpecial` | Every few ticks, each soldier lists all soldiers within 16 blocks and checks each one (combat targeting checks line of sight). If nothing matches, it runs vanilla `findTarget` (a **second** scan). | Grows with the **square** of soldier density in big battles. |
| `PickUpItemsGoal` | `getItemsInArea()` (an entity search) runs **every tick** in `canContinueToUse` and again in `tick`. | Linear in workers, but every tick. |
| `FindNearestPoiGoal` | About 1 in 60 ticks: an entity search, then up to about 3,000 block lookups plus data-map lookups. | Moderate per soldier, but it adds up over hundreds. |
| `ServerBlueprintPlan.getRequest` | Builds a **path** to each remaining block (path first, cheap "is it air?" check second) until it finds one. `EaselBlockEntity.getBlueprintRequest` pathfinds again. | Large blueprints with blocked spots mean many pathfinds per request. |
| Pathfinding range | `FOLLOW_RANGE` is 16, which limits how far vanilla pathfinding searches. | Soldiers can't plan long walks (across a village, to a wall) *(verify in game)*. |
| Per-tick extras | `dropCarriedTick` every tick, `AlertOthersGoal` area searches, two navigators per soldier. | Small individually. |

---

## Kingdom gap checklist

### A. What soldiers do autonomously today (no player input at all)

- Fight soldiers of **other teams** (and aggressive soldiers), hit back, alert nearby teammates, use ranged and special attacks, revive and heal (support types).
- Ride horses or other soldiers. Pick up and use effect POIs (dyes, flint, potions, conversions).
- Wander and look around.
- **Not work.** Working needs a player-owned team, wheat (pacifist), a brush-chosen job and usually a brush-assigned chest.

*After* a player sets them up, they keep farming, hauling, digging and building on their own until the job runs out.

### B. Checklist

| Kingdom feature | Status | What exists / what's missing |
|---|---|---|
| Resource gathering | **Partial** | Harvest crops, plant seeds from a chest, dig a shaft (drops items), haul dropped items to a chest. Missing: tree chopping, targeted mining, automatic setup, and a link between "dig drops" and "who collects them". |
| Shared storage | **Partial** | Any chest works as a soldier's assigned POI, and many soldiers can share one. Missing: storage that **belongs to a team**, a list of team chests, and stock tracking. |
| Job assignment | **Partial** | 5 jobs with a selector. Missing: **automatic** choice (only the Clay Brush picks jobs). Work also requires pacifist (wheat) and a player owner. |
| Construction | **Partial** | Easel + blueprint builds vanilla village houses block by block from chest materials. Missing: choosing what or where to build without a player, site prep, walls, roads, experiment-free use. Has a free-block bug. |
| Defense | **Partial** | Soldiers fight enemies within 16 blocks and alert allies. Missing: guard posts, patrols, gates, "return home when threatened". |
| Expansion | **Missing** | No territory, no new settlements, no population growth (soldiers only come from player items). |
| Diplomacy | **Missing** | Relations are only "same team or enemy". No alliances, truces or neutral state. |
| War | **Partial** | Different teams fight **when they happen to meet**. Missing: declaring war, raising armies, marching to a target, objectives. |
| Conquest | **Missing** | No territory to take. Closest thing: zombie and vampire conversion effects that change individual soldiers. |

---

## Smallest first step

**Goal:** a team of soldiers picks a building from a list and builds it with no player clicks.
**Key insight:** `BuildBlueprintGoal` + `EaselBlockEntity` already do the hard part (block-by-block building
from a chest). What's missing is *someone placing the easel and choosing the blueprint*, plus two player-only gates.

**Step 0: a test hook, no AI yet (smallest possible).**
Add a command, e.g. `/csr build <blueprint> <pos>`, next to the existing ones in `commands/ClaySoldierCommands.java`.
It places an `EaselBlock` (facing chosen) and calls `EaselBlockEntity.setBlueprintData(...)`.
This proves the "place the easel from code" piece works on its own.

**Step 1: let soldiers start it themselves (about 3 small edits plus 1 new class).**

| # | Change | File |
|---|---|---|
| 1 | Add a public way to set a job by type (e.g. `setWorkGoal(Class<?>)`). Today `setWorkMode` is private and only the brush cycles it. | `entity/goal/workgoal/WorkSelectorGoal.java` |
| 2 | Allow work without a player owner, behind a config option or team setting, so existing behavior stays the default. | `WorkSelectorGoal.mayUse` |
| 3 | New `PlanBuildingGoal` (or a small team "planner" called from a pacifist soldier): if the soldier has a storage POI and there is **no easel within 8 blocks**, pick a random valid `BlueprintData` from `ModRegistries.BLUEPRINTS`, find a site near the chest whose footprint (`template.getSize()`) is air above solid ground, place the easel, call `setBlueprintData`, then switch nearby same-team pacifist workers to `BuildBlueprintGoal`. | new file in `entity/goal/workgoal/` |
| 4 | **Recommended first:** fix the free-block bug (check the extracted stack isn't empty before `setPlacing`, and place the carried stack). Otherwise "materials" don't matter. | `BuildBlueprintGoal.getRequestFromStorage` / `placeBlock` |

Still required (setup, not code): enable the *blueprint* experiment, give workers wheat, and place a chest of materials.

---

## Risks

### D. Biggest technical risks for the kingdom plan

1. **Kingdoms freeze when no player is nearby.** There's no chunk loading. Choices: force-load chunks (heavy on the server), or simulate distant kingdoms "on paper" with an abstract model updated in SavedData. **This is the biggest design decision and it shapes everything else.**
2. **Performance at hundreds of soldiers.** Targeting cost grows with the square of density; there are per-tick entity searches and pathfinding-heavy blueprint requests. You'll need a team-level "brain" that makes decisions a few times per second for the whole team, instead of every soldier searching every tick.
3. **There's no "kingdom" object to hang things on.** Teams are fixed, read-only datapack entries with no member list and no saved state except one loyal player. Territory, storage, buildings and diplomacy all need a **new SavedData-backed faction model**. Dynamic new factions don't fit the current registry design.
4. **Player-centric assumptions are everywhere:** a player owner is required to work, loyalty is one player per team, and jobs and POIs are set with the brush. Each one has to be relaxed carefully without breaking normal play.
5. **Pathfinding for tiny mobs at village scale.** The 16-block follow range and vanilla navigation will struggle with walls, long routes and multi-level buildings.
6. **Diverging from upstream.** Upstream's default branch has moved to 26.1. Heavy edits inside `AbstractClaySoldierEntity` will make pulling in their fixes painful, so put new features in **new classes**.

### E. Fragile or unfinished code

| Issue | Where | Effect |
|---|---|---|
| `switch` with no `break` statements | `DigHoleGoal.breakBlock` | Every case falls through. The error case (`-2`) **still destroys the block**, and the status always ends up as "unbreakable". |
| Dig never stops | `DigHoleGoal.getNextPos` | No depth limit. Below bedrock, every position counts as "broken", so it keeps going downward forever *(verify in game)*. Also ignores `mobGriefing`, unlike `BreakCropGoal`. |
| Free stairs | `StairPosMap.placeStair` | Cobblestone stairs and slabs appear without using items. |
| Free blueprint blocks | `BuildBlueprintGoal` / `EaselBlockEntity.doBlueprintRequest` | See §3. |
| Saving resets the job | `AbstractClaySoldierEntity.addAdditionalSaveData` → `resetGoal()` | Autosaves interrupt work in progress. |
| `hasStarted` never set | `BlueprintPlan` | The mirror can be toggled mid-build, which would put later blocks in the wrong place. |
| Blocked sites never finish | `ServerBlueprintPlan.getRequest` | No clearing; the build stalls silently. |
| No goal flags on `WorkSelectorGoal` | `WorkSelectorGoal` | Idle wandering can run at the same time as work. |
| God class | `AbstractClaySoldierEntity` (2,026 lines) | Hard to change safely, and collides with upstream changes. |
| Job list hard-coded | `getOrCreateWorkSelectorGoal()` | Adding jobs means editing the soldier class. There's a 15-job cap and the status is limited to 0–7. |
| Static global state | `DigBreakManger.INSTANCE`, `ClayMobTeamManger.FROM_ITEM_MAP` | Not cleared between worlds in singleplayer, and not saved. |
| Team equality by name and color | `ClayMobTeam.equals` | Lookalike teams become allies. |
| No automated tests | `neoforge:test` reports `NO-SOURCE` | Changes can only be checked by playing. |
| Multi-loader split | `common/` vs `neoforge/` | `common` can't call NeoForge APIs directly; it has to go through `platform/services/` interfaces. That's an extra step for every feature. |
| Misspelled names (`Manger`, `Sodlier`, `VampiricOnwer`) | various | Cosmetic, but **don't rename** them. Save files use some of these strings, such as NBT tags. |
