# Scaffolding (stage 3: tall construction spike)

How builders finish structures taller than their reach. Prior art: [`RESEARCH-scaffolding.md`](RESEARCH-scaffolding.md).
Decisions: D34–D40 in [`DECISIONS.md`](DECISIONS.md). Numbers: `scaffold.*` in [`TUNING.md`](TUNING.md).

## 1. Reach model (how builders reach high blocks today)

- **The rule:** `BuilderReach`. A builder places a block whose center is less than 2.5 blocks sideways from the center
  of the block it stands in (up to 2 blocks away), from 2 below to 8 above its feet (`MAX_PLACE_HEIGHT`).
- **Finding a spot:** builders try a path toward the block. If that fails, they try one path to any standing spot
  within reach: an empty cell with a solid floor, 2 above to 8 below the block, within 2 blocks sideways.
- **Where it stops working:**
  - **From the ground:** the highest block is 8 above the builder's feet, so a structure can be **9 blocks tall**
    (layers 0 to 8) if every block is within 2 blocks sideways of open ground. The planner confirms 9 needs nothing
    and 10 needs scaffold.
  - **Inside:** blocks more than 2 blocks from any outside spot need a floor or open interior builders can walk to
    (the Small House works: 151 of 151, no scaffold).
- **Carrying a block while hopping:** carrying doesn't slow soldiers, and they hop 1 block. Two limits matter more:
  - **Path length (vanilla):** a mob only follows a path whose walked length is under its follow range (16 for clay
    soldiers), and each hop up a step counts about 1.4. So a builder 8 blocks from a stair only gets 5 or 6 steps up.
    Measured: from most directions a straight 12-step floating stair could be planned only 0 to 3 steps up.
  - **Footing:** tiny soldiers count a step as reached about 0.7 blocks early and cut corners, so on 1-block floating
    steps they fall off, especially where the stair turns. Measured: 2 by 2 spiral pillars gave 3 to 23 give-ups per
    20-block tower.

## 2. Approaches compared

Scaffold block counts are from the planner on flat ground. In-game results are from GameTests with 3 builders and the
chest 7 to 10 blocks from the structure; 20 ticks = 1 second. "Trips" = builder trips added (placing plus removing).

| Approach | Scaffold blocks: tower 20 / wall 12 / corner tower 14 on walls 10 | Material | Extra trips (tower 20) | In game | Tiny soldiers can use it? |
|---|---|---|---|---|---|
| (a) Ramp up the outside | 18 / 11 / 13 (incomplete for walls 20 high or more: one ramp can't reach the far face) | any full block | 36 | Tower: not finished in 23,000 ticks (132–158 of 160), 1–3 give-ups | Climbs straight runs; falls at turns |
| (b) Step pillars (2 by 2 spirals) | 23 / 14 / 17 | any full block | 46 | Tower: finished in 1 of 4 runs (10,787 ticks); the others 152–159 of 160; 3–23 give-ups | Unreliable: falls off where it turns every step |
| (c) Structure tops + step pillars | 23 / 14 / 17 (same as b: these test structures have no floors) | any full block | 46 | As (b) | Depends on the structure having floors |
| (d) Vanilla scaffolding | 24 / 16 / 20 | scaffolding | 48 | — | **No**: vanilla navigation never plans climbing |
| **(e) Climbed scaffolding columns** | 24 / 16 / 20 | scaffolding | 24 placements + 2 removals (a whole column comes down from its base) | **Tower 20: finished in every run (9 runs), 0 give-ups**, 6,500–13,200 ticks; wall 12: 4,000–6,400; corner tower: 10,800–11,100. Scaffold gone 70–230 ticks after the structure | **Yes**: they walk to the column's base and rise inside it |

How (e) works:
- Planner: 1 by 1 columns beside the structure (each covers the blocks within 2 blocks sideways), only as tall as
  needed, at least 1 block apart, each base with 2 open sides, and within about 24 blocks' walk of the builders' chest.
- Building: builders build everything they can reach. When the whole build has found nothing in reach for
  `scaffold.start_after_stuck_ticks`, they put up all the scaffolding (each column's lowest missing block first;
  higher ones from inside the column; D42), then continue: a builder walks to a column's base on
  the ground, rises inside the column (0.2 blocks per tick, no gravity, no falling), places its block and comes back
  down.
- Teardown: when the structure is finished, a builder walks to each column's base and the column comes down whole
  (top-down), carried back to the chest when `scaffold.refund` is 1.

## 3. Recommendation

1. **Use climbed scaffolding columns (e)**; it is the default (`scaffold.approach` 4). Steps and ramps (a to c) stay
   as tuning options for comparison only: tiny soldiers can't use floating steps reliably.
2. **Don't scaffold what fits in reach:** structures up to 9 blocks tall never get scaffold, so their build is
   unchanged (Small House: 151 of 151).
3. **Storage near the work:** builders only follow paths up to their follow range. Every block of a scaffolded
   structure should be within about 14 blocks' walk of the builders' storage. Long works (city walls) need a storage
   depot per segment.
4. **Room beside the walls:** columns need a free 1-block strip along at least one face, at most 2 blocks from the
   blocks they serve, with 2 open sides at the base. Inner corners between walls get no column (they aren't used).

### What this means for the keep and wall designs (needs your OK)

- **Keep:** make the tier-1 keep at most **9 blocks tall** above its base layer (D33). Then it never needs scaffold:
  cheaper, faster, and the first playable keep (stage 4) doesn't depend on scaffolding. Taller keeps at later tiers
  are fine with columns: budget roughly one column per 5 blocks of wall face. Keep the 9 by 9 footprint (D25): its
  inside is within reach from the doorway and floor.
- **City walls:**
  - Plain wall segments at most 9 tall need no scaffold. Taller walls cost about 2 scaffolding blocks per block of
    wall length per 8 blocks of extra height.
  - Build walls in segments of at most about 12 blocks, each with its own easel and storage.
  - Leave the outside face clear for columns.
  - Towers at corners work (tested 14 high on 10-high walls).

## 4. Height rules for structure designers

| Rule | Value |
|---|---|
| Tallest without scaffold | **9 blocks** (top block 8 above the ground builders stand on), if every block is within 2 blocks sideways of open ground or a walkable floor |
| Tallest with scaffold | Tested **28** in game (tower) and **34** in the planner. Limits: `scaffold.max_blocks` (200), a free column spot within 2 blocks of every block, storage within about 14 blocks' walk |
| Cost per extra 8 blocks, 3 by 3 tower | +16 scaffolding (2 columns × 8), placed and removed; about +3,000 to 5,000 ticks with 3 builders |
| Cost per extra 8 blocks, 16-long wall | +32 scaffolding (4 columns × 8) |
| Time per block | about 25 ticks per block within reach (3 builders); about 3 times that for blocks reached from a column |
| Teardown | about 100 to 250 ticks after the structure is finished |

## 5. Limits and risks

- Scaffolding is climbable, so a builder walking against the structure from a column's base is sometimes carried up
  the column by vanilla climbing. One standing 2 or more levels up (without climbing) is climbed straight back down
  (D41). It costs a little time; the dev book tower still finishes in about 5,800 to 6,100 ticks.
- Builders rise inside a column by direct movement (smooth, no vanilla climbing animation): it looks like gliding up
  the scaffolding.
- Request searches: each builder remembers blocks it couldn't reach and skips their (expensive) path search for
  `build.unreachable_recheck_ticks`, unless something is placed near them (D42). Before, a stall with 500 blocks out
  of reach cost about 1 s of server time per builder per search. A few such searches remain per build (each builder's
  first full search when the build runs out of reach).
- The planner runs on the server thread when builders get stuck (at most every `scaffold.replan_ticks`). It takes
  about 50 to 400 ms for one approach on the bigger test structures (up to 1.8 s comparing all five on a 34-high
  corner tower). Large or many builds could stutter.
- Vanilla scaffolding collapses above a cell a player breaks; the job forgets missing cells.
- A player block of the same material put back where scaffold was removed can't be told apart from scaffold.
- Builds with scaffolding change their builders' path settings while they work for it: longer paths, a bigger
  search, re-planning every 20 ticks, precise steps on scaffold blocks. Other builds are unchanged.
- GameTests: the framework puts a barrier roof on 16-high test areas (tall builds use sky access) and barrier walls
  around every area.
