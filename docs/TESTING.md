# Manual Test Guide (Milestone M0)

Goal: check that the existing blueprint and worker features work, and reproduce the bugs suspected in
`docs/ANALYSIS.md`. **Don't fix anything yet.** Fill in the PASS/FAIL lines and report back.

Every id and command below was checked against the source code (file references are in brackets).
The materials list was computed from the vanilla structure file the blueprint points to.

**Words used below**
- **Right-click / left-click:** Minecraft's "use" and "attack" buttons. On a Mac trackpad, right-click is a two-finger click.
- **Sneak:** hold Shift.
- **Hotbar message:** the small text that briefly appears above your hotbar.
- **Tick:** 1/20 of a second (the game's clock).

---

## A. Test world setup

### A1. Launch the game

From the project folder (`~/Downloads/Clay soilders`) in Terminal:

```bash
./gradlew :neoforge:runData
```

```bash
./gradlew :neoforge:runClient
```

- `runData` generates recipes, item names, item effects, teams and the blueprint data pack into
  `common/src/generated/resources` (ignored by git). **Without it, wheat won't make a worker and blueprints won't exist.**
  It's already been run once in this folder. Run it again after downloading a fresh copy or changing datagen code.
- `runClient` opens Minecraft with the mod. Keep the Terminal window open, because closing it closes the game.
  The game's files (worlds, logs) go to `neoforge/run/`.
- You're signed in as a test player named `Dev`, which is normal for a development launch.

### A2. Create a superflat creative world with the blueprint experiment

1. **Singleplayer → Create New World**.
2. **Game** tab: Game Mode = **Creative**, Allow Commands = **ON**.
3. **World** tab: World Type = **Superflat** (click the button until it says Superflat).
4. **More** tab → **Experiments**. Turn on **"Clay Soldiers: Blueprints"** (description: *"Enabled Blueprint features for Clay Soldiers"*). Click **Done**.
   - Without this, the Easel, Escritoire, blueprints and the *Building* job don't exist (feature flag `csr:blueprint`).
5. **Create New World**. Click **Proceed** on the experimental-features warning.
6. In chat (press `T`), make the world calm and bright:

```
/gamerule doMobSpawning false
```
```
/gamerule doDaylightCycle false
```
```
/time set day
```

Superflat facts: bedrock is at y = -64, then dirt, dirt, grass (y = -61). You stand at y = -60.

### A3. Give yourself everything

| What | Command | Why |
|---|---|---|
| 3 red soldiers | `/give @s csr:clay_soldier[csr:clay_mob_team="csr:red"] 3` | Team `csr:red`; a team gives you a clear owner and color. |
| Clay Brush | `/give @s csr:clay_brush` | Gives orders: sit, job, workplace. |
| Wheat | `/give @s minecraft:wheat 3` | Wheat in the soldier's backpack makes it a **Pacifist**, the only type that can work. |
| Chest | `/give @s minecraft:chest` | The workers' material storage. |
| Easel | `/give @s csr:easel` | Holds the blueprint. |
| Small House blueprint | `/give @s csr:blueprint[csr:blueprint_data="csr:small_house"]` | Other ids: `csr:large_house`, `csr:small_farm`. |
| Clay Goggles (optional) | `/give @s csr:clay_goggles` | Wear on your head to see a status label over **your** soldiers. |
| Debug Item (optional) | `/give @s csr:debug_device` | Dev-only. Right-click a soldier or easel to print its internal state to the **Terminal**. |

**Shortest ways to get a blueprint without `/give`:**
- Creative inventory → the **Clay Soldiers** tab contains *Blueprint (Small House)*, *Blueprint (Farm)* and *Blueprint (Large House)* ready to use.
- Survival-style route: craft an **Empty Blueprint** (`csr:blueprint_page`: lapis lazuli over paper over clay ball, in one column). Craft an **Escritoire** (`csr:escritoire`: 2 Empty Blueprints on top, 4 planks below). Open the Escritoire, put an Empty Blueprint in the left slot, click a design, and take the result. It works like a stonecutter.

**Small House materials** (151 blocks, from `village/plains/houses/plains_small_house_1`):

```
/give @s minecraft:oak_stairs 50
```
```
/give @s minecraft:cobblestone 43
```
```
/give @s minecraft:oak_planks 34
```
```
/give @s minecraft:stripped_oak_log 16
```
```
/give @s minecraft:torch 3
```
```
/give @s minecraft:glass_pane 3
```
```
/give @s minecraft:oak_door 1
```
```
/give @s minecraft:white_bed 1
```

Double-check: hover over the blueprint item. Its tooltip lists the exact items needed. If it differs from the list above, **trust the tooltip** and write down the difference.

---

## B. Baseline flow: does building work normally?

**The Clay Brush has 3 modes.** Right-click the **air** (not a block, not a soldier) to cycle
**Command → Work → Poi → Command**. The new mode name appears as a hotbar message.
It starts in **Command**. *(`ClayBrushItem.cycleMode`)*
The brush only works on soldiers whose team is loyal to **you** *(`ClayMobEntity.mobInteract` → `isOwnedBy`)*.

| # | Step | What you should see |
|---|---|---|
| B1 | **Spawn workers:** hold the 3 red soldiers and **sneak + right-click** the ground 3 times (sneaking spawns one at a time; without sneaking, the whole stack spawns). | 3 small red soldiers. |
| B2 | **Claim the team:** `/csr team loyalty set csr:red @s`, then check with `/csr team loyalty`. *(Normal-gameplay alternative: drop a `minecraft:gold_ingot` so a soldier wears it and becomes a **King**, then right-click the King with a `csr:clay_cookie`.)* | Chat says something like *"Red is now loyal to Dev"*. |
| B3 | **Make them pacifists:** stand next to the soldiers and drop 1 wheat per soldier (`Q` key, one at a time). Wait about 3 s; soldiers pick up dropped items. | The wheat disappears from the ground. Check: **sneak + right-click** a soldier in creative to open its inventory; the wheat should be in a backpack slot. Close it. |
| B4 | **Place the chest** on the ground near the soldiers and put **all Small House materials** in it. | |
| B5 | **Place the easel** 3–4 blocks from the chest, **facing you**. The house will appear **on the far side of the easel** (2 blocks behind it, centered), about 7×7. Leave a clear 9×9 area there. Keep the easel within about 6 blocks of the chest, on the same height. | |
| B6 | **Put the blueprint on the easel:** right-click the easel holding the Small House blueprint. | The easel shows the blueprint (and probably an outline of the house). |
| B7 | **Set the job FIRST:** brush to **Work** mode (right-click air until the message says *Work*). Right-click a soldier **5 times**. Each click shows the next job: *Harvesting → Pick up Items → Replanting → Digging → **Building***. Stop at **Building**. Repeat for each soldier. | The hotbar says *Building*. If it shows a combat type like *Normal* instead, the soldier isn't a pacifist; go back to B3. |
| B8 | **Then assign the chest:** brush to **Poi** mode. **Left-click the chest** (hotbar: *Poi: (x, y, z)*), then **right-click each soldier** (hotbar: *Chest (x, y, z)*). | ⚠️ Order matters. A soldier that isn't working treats a chest as a one-time "take gear" spot and then forgets it *(`UseAssignedPoiGoal`)*. |
| B9 | **Watch.** With goggles on, the label reads like *Building: Carrying*. | Soldiers walk chest → house → chest, carrying **one block at a time**, and the house grows. 151 trips with 3 soldiers can take 15+ minutes. Get closer if it seems stuck. |

**Record:**
- Did the house finish completely? PASS / FAIL: ______
- Items left in the chest at the end (should be none): ______
- Blocks that never got placed (e.g. roof stairs, door, bed, torches): ______
- Anything odd (soldiers wandering off mid-trip, stuck on the roof, falling): ______

---

## C. Suspected-bug tests

Do them **in this order**. **C7 and C8 may crash the game**, so do them last and only in this test world.
Use a fresh spot and new soldiers for each test, or break the previous easel. After **every** test, write PASS (correct behavior) or FAIL (bug reproduced).

### C1. Free blocks: building with an EMPTY chest
*(Suspect: `BuildBlueprintGoal.getRequestFromStorage` / `EaselBlockEntity.doBlueprintRequest`)*
- **Setup:** repeat B1–B8 at a new spot, but leave the chest **completely empty**.
- **Do:** watch for 2 minutes.
- **Bug looks like:** soldiers walk to the chest, come back empty-handed, and **house blocks still appear**.
- **Correct looks like:** nothing gets built, and soldiers show *Searching* or wait at the chest.
- Result: PASS / FAIL ______ Notes: ______

### C2. Autosave interrupts work
*(Suspect: `AbstractClaySoldierEntity.addAdditionalSaveData` calls `workSelector.resetGoal()`)*
- **Setup:** a build in progress (B9), with goggles on.
- **Do:** wait until a soldier is walking **from the chest to the house carrying a block**, then type `/save-all`. Repeat 3 times.
- **Bug looks like:** right after the save, carrying soldiers **turn around and return the block to the chest** (the label changes to the untranslated text `clay_soldier_work.csr.job.returning`, which is itself a small cosmetic bug), or stop and restart.
- **Correct looks like:** saving has no visible effect, and they keep walking to place the block.
- Result: PASS / FAIL ______ Notes: ______

### C3. Mirror flip during a build
*(Suspect: `BlueprintPlan.hasStarted` is never set to true, so `EaselBlockEntity.cycleMirror` always works)*
- **Setup:** a build that has placed some blocks but isn't finished.
- **Do:** right-click the easel with an **empty hand, not sneaking** (sneak + empty hand would **take the blueprint off**).
- **Bug looks like:** the easel accepts the change (the outline flips), and later blocks go to mirrored spots, making a broken, mixed-up house.
- **Correct looks like:** the easel refuses to mirror once building has started.
- Result: PASS / FAIL ______ Notes: ______

### C4. Blocked building site never finishes
*(Suspect: `ServerBlueprintPlan.getRequest` skips spots that aren't air, and nothing clears them)*
- **Setup:** before B6, place 3–4 `minecraft:dirt` blocks in the area where the house walls will go (2–5 blocks behind the easel, at ground level). Then continue with B6–B9 and full materials.
- **Bug looks like:** the build stops with items left in the chest, and soldiers idle at *Building: Searching* forever.
- **Correct looks like:** soldiers clear the dirt, or the build reports that it's blocked.
- Result: PASS / FAIL ______ Items left over: ______

### C5. Digging: status label, free stairs, never stops, ignores mobGriefing
*(Suspects: `DigHoleGoal.breakBlock` switch fall-through, `StairPosMap` free stairs, no depth limit)*
- **Setup:** 1 new pacifist soldier (B1–B3), goggles on. Brush **Work** mode → click the soldier **4 times** → *Digging*. Then brush **Poi** mode → **left-click a grass block** about 5 blocks from any build → right-click the soldier.
- **Do:** watch until the hole reaches bedrock (superflat has only 3 diggable layers), then wait 2 more minutes. Then make a second digger with `/gamerule mobGriefing false` and repeat. Afterwards, set it back with `/gamerule mobGriefing true`.
- **Bugs look like:**
  - (a) While a block is visibly cracking, the label says *Digging: but cannot break block*.
  - (b) **Cobblestone stairs or slabs** appear in the shaft walls even though the soldier was never given cobblestone.
  - (c) At bedrock, the soldier never goes idle and the label keeps saying *Digging…* forever.
  - (d) It still digs with `mobGriefing false` (for comparison, the *Harvesting* job refuses).
- **Correct looks like:** (a) the label says *Digging: breaking blocks*; (b) stairs cost cobblestone or aren't placed; (c) it stops at bedrock; (d) digging respects `mobGriefing`.
- Results: (a) ____ (b) ____ (c) ____ (d) ____

### C6. Workers wandering mid-job *(observation only)*
*(Suspect: `WorkSelectorGoal` sets no goal flags, so `RandomStrollGoal` can run at the same time)*
- **Do:** during any build (B9), watch one soldier for 3 minutes.
- **Bug looks like:** the soldier randomly wanders off sideways while carrying a block, then resumes.
- Result: PASS / FAIL ______ How often: ______

### C7. ⚠️ May crash: right-clicking the easel with a non-block item
*(Suspect: `ServerBlueprintPlan.tryPlacing` casts the held item to `BlockItem`)*
- **Setup:** an easel with a blueprint on it. Run `/save-all` first.
- **Do:** right-click the easel holding a `minecraft:stick`.
- **Bug looks like:** the game crashes or kicks you to the menu, and the Terminal shows `ClassCastException … cannot be cast to … BlockItem`.
- **Correct looks like:** nothing happens. (Right-clicking with a **needed block**, e.g. cobblestone, is a real feature: you place it into the house by hand.)
- Result: PASS / FAIL ______ First error line from the Terminal: ______

### C8. ⚠️ May crash: the Farm blueprint (contains water)
*(Suspect: water has no item, so the request item is "air", which isn't a `BlockItem`)*
- **Setup:** `/save-all`. Repeat B1–B9 with `/give @s csr:blueprint[csr:blueprint_data="csr:small_farm"]` and the materials from that blueprint's tooltip (expected: 28 `oak_log`, 27 `farmland`, 27 `wheat_seeds`, 1 `dirt`, 1 `composter`).
- **Bug looks like:** a crash with `ClassCastException` once the soldiers reach the water blocks, or the farm never finishes because water is never placed.
- **Correct looks like:** the farm completes (water is skipped or handled).
- Result: PASS / FAIL ______ Notes: ______

---

## After testing

Send back the filled-in PASS/FAIL lines. If anything crashed, also send the newest file in
`neoforge/run/crash-reports/`, or the first red error lines from the Terminal.
Only bugs marked **FAIL** here get fixed in **M1**.
