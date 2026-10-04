# Clay Soldiers Kingdoms — Design (DRAFT)

> **DRAFT.** This design has many gaps on purpose. Gaps are filled using the protocol below, and every
> decision is logged in [`DECISIONS.md`](DECISIONS.md).

## Pillars

1. **A living kingdom:** clay soldier factions build, gather, grow and wage war on their own.
2. **Sims-like:** small soldiers with jobs and needs, fun to watch going about their day.
3. **No punishing mechanics:** nothing starves, rots or collapses because the player looked away.
4. **Good for an observer:** what happens is visible and readable in the world.

## What exists so far

- **Kingdom (stage 1):** a team's claim around where its king (a clay soldier wearing a gold ingot) stood when the
  player founded it. It has a status (active, or ruined when the king dies), a tier, a stockpile, a building list,
  a population count, renown and a chronicle written for humans. The observer reads it all in the kingdom book
  (`/csr kingdom book`), which shows the kingdom as it is whenever it's opened, also on a lectern. Decisions D16–D23.
- **Terrain (stage 2):** founding judges the ground around the king (keep site 11 by 11) and refuses cliffs, lava,
  deep water and too steep ground, naming the best site nearby; softer problems give a warning. Grading methods exist
  as a dev tool only; the recommendation for stage 4 is in [`TERRAIN.md`](TERRAIN.md). Flow audit: none of it adds a
  resource yet; tree clearing will become a producer of logs once woodcutters exist (stage 5). Decisions D25–D32.
- **Scaffolding (stage 3):** structures taller than builders' reach (9 blocks) get temporary scaffolding columns;
  builders climb them, and they come down afterwards. Flow audit: the scaffold material is fetched from and returned
  to the builders' storage (store → carrier → temporary use → store); it's never consumed when refunded. Height rules
  for designers: [`SCAFFOLDING.md`](SCAFFOLDING.md). Decisions D34–D40.
- **Flow audit, stockpile:** a store with no producer, carrier or consumer yet (dev commands only). Stage 5 adds
  farms and lumber camps (producers), carriers, the storehouse, and the cottage as the first consumer.

## The keep (design, not built yet)

- Tier-1 footprint 9 by 9 or less (D25; the terrain checks use 9 by 9 plus a 1-block margin).
- **Base layer and ground level (D33, approved):** the keep's structure has a 1-block stone base layer at the bottom,
  and its metadata says which layer sits on the ground ("ground level"), so the keep absorbs a 1-block bump without
  grading. Bigger unevenness follows the stage 2 recommendation (cut and fill, then a foundation; `TERRAIN.md`).
- **Door:** faces the lowest side of the site.
- **Height (recommended, needs the owner's OK):** at most 9 blocks above the base layer for tier 1, so no
  scaffolding is needed ([`SCAFFOLDING.md`](SCAFFOLDING.md)).
- Built in stage 4 (tier-1 keep built autonomously).

## Structures and scale (draft)

- **Miniature scale with a few larger landmarks (D43, the owner's decision):** soldiers are 0.25 scale, so the
  town is a tiny world.
  - Common buildings are roughly half-scale: a cottage about 5 by 7, walls 3 to 4, roofs +2 to 3, detail from small
    blocks.
  - Landmarks (the keep from tier 4, church and cathedral, town hall, main gate, a few towers) may be larger, up to
    28 blocks tall.
- **Detail grows:** each structure has shell, fittings and flourish layers. Flourish is an upgrade (levels 2 and 3),
  so early builds finish fast and kingdoms visibly get richer (D44).
- **Master list and design briefs:** every structure, the tier and perk tables, town styles and the buildability
  checklist are in [`STRUCTURES.md`](STRUCTURES.md) (draft; no structure files yet).

## DESIGN DECISION PROTOCOL

1. **Decide gaps yourself.** The design is a draft with many gaps. When a feature needs a decision the design
   does not cover, decide it; don't stop to ask.
2. **Simplest fit.** Fill gaps with the simplest option that fits the pillars (a living kingdom, Sims-like, no
   punishing mechanics, good for an observer). Reuse existing buildings and systems before inventing new ones.
3. **Flow audit.** For every structure or resource you add, write down its **producer**, **carrier**, **store**
   and **consumer**, and fix any missing link with the simplest addition.
   Example:

   | Link | Food |
   |---|---|
   | Producer | Farm |
   | Carrier | Farmers carry the harvest |
   | Store | The keep's storehouse; it counts in the stockpile |
   | Consumer | A food hall (later) turns food into perks |

   There is no starvation: a missing consumer is never a penalty.
4. **Log every decision** in [`DECISIONS.md`](DECISIONS.md) with: date, question, choice, why, alternatives,
   the tuning key that changes it, and whether it is easy to reverse.
5. **Stop and ask the owner only** when a decision:
   - (a) changes what the player would see or feel in a big way,
   - (b) is hard to undo, such as saved-data formats, or
   - (c) conflicts with a pillar.

   Say so clearly in the final report.
6. **Numbers live in one tuning data file:** costs, radii, rates, caps and unlock thresholds, never hardcoded.
   The file is `data/csr/tuning/tuning.json`, documented in [`TUNING.md`](TUNING.md) (since stage 0). Constants
   from before this protocol, such as `BuilderReach`, `UseDoorGoal` and `BlueprintBlockInfo`, move there when those
   features are next touched (list in `TUNING.md`).
7. **Saved-data format changes** need a migration note in `DECISIONS.md`.
8. **Every milestone's final report** lists the new decisions made. At the end of each milestone, run a short
   consistency check of `DECISIONS.md` and report any contradictions.
