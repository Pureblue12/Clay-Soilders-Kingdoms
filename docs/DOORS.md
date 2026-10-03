# Doors and clay soldiers — investigation notes (2026-10-03)

Read from the code before building door use. Minecraft 1.21.1 / NeoForge 21.1.194.

## How soldiers find their way today

- `AbstractClaySoldierEntity` has **two** navigations and switches between them in
  `updateOtherProperties()`: `groundNavigation` (vanilla `GroundPathNavigation`, from `createNavigation`) and
  `waterNavigation` (`AmphibiousPathNavigation`) for soldiers whose items let them swim. The `navigation` field
  that `Mob` creates in its constructor is a third instance, used until the first switch.
- Neither navigation sets any door option except vanilla's default `canPassDoors = true` (walk through OPEN doors).
  `canOpenDoors` is false, so the pathfinder (`WalkNodeEvaluator`) treats a closed wooden door
  (`PathType.DOOR_WOOD_CLOSED`, malus -1) as a wall. There is no goal that opens doors.
- Nothing else in the mod touches doors except `BuilderReach.closedDoorInTheWay` (a debug explanation that
  turns `canOpenDoors` on for one extra path search, then back off).

## How vanilla mobs use doors (1.21.1)

- Pathfinding: with `canOpenDoors` and `canPassDoors` on, `WalkNodeEvaluator.getPathTypeWithinMobBB` turns
  `DOOR_WOOD_CLOSED` into `WALKABLE_DOOR` (malus 0). Iron doors are `DOOR_IRON_CLOSED` (malus -1) and stay walls
  (`DoorBlock.type().canOpenByHand()` is false). Closed fence gates are `FENCE` and stay walls for every mob;
  vanilla has no fence-gate opening.
- Opening: `OpenDoorGoal(mob, closeDoor)` (zombies, villagers before Brain, illagers) extends `DoorInteractGoal`:
  - only for mobs whose CURRENT navigation is a `GroundPathNavigation` (the constructor throws otherwise, and
    `canUse` returns false otherwise);
  - starts only when the mob has bumped into something (`horizontalCollision`) and a wooden door is on one of the
    next path nodes within 1.5 blocks (it checks the block ABOVE each node, i.e. the door's upper half);
  - opens the door, and with `closeDoor` closes it again after 20 ticks or when the mob has passed the door's
    center, whichever comes first — even if the mob hasn't passed yet or another mob is in the doorway;
  - has no flags, so it runs next to movement goals.
- Villagers use the Brain behavior `InteractWithDoor` instead (tracks opened doors in memory, closes them when no
  other villager is near). Clay soldiers use goals, not a Brain, so that one doesn't fit.

## Tiny entities

Clay soldiers are 0.15 wide and 0.49 tall (0.6 x 1.95 times `DEFAULT_SCALE` 0.25), speed 0.3.
- Pathfinding works in whole blocks (`entityWidth` = 1, `entityHeight` = 1), so a tiny soldier gets the same door
  nodes as a zombie. A node counts as reached within 0.675 blocks (0.75 - width / 2).
- A closed door's panel is 3/16 thick at one edge of its block. A 0.15-wide soldier walking to the door block's
  center may not touch the panel until it walks on to the next node, so "has it bumped into something" is a late,
  side-dependent signal. Expected risk for `OpenDoorGoal`, to be confirmed by the GameTests.
- 20 ticks at speed 0.3 is roughly 1 block of walking, so vanilla's fixed 20-tick close can shut the door on a
  soldier still in the doorway, or on a second soldier right behind it.

## What this means for the design

- The pathfinder option (`canOpenDoors`) is what lets soldiers plan routes through wooden doors; it must be set on
  BOTH navigations (and the constructor's one), or swimming soldiers would plan through doors they can't open.
- Vanilla `OpenDoorGoal` would not run at all for swimming soldiers (wrong navigation type). Whether it works for
  tiny soldiers on ground navigation is tested by trying it first (see the GameTests in `DoorGameTests`).
