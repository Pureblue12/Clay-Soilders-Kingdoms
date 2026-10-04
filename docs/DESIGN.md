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
- **Flow audit, stockpile:** a store with no producer, carrier or consumer yet (dev commands only). Stage 5 adds
  farms and lumber camps (producers), carriers, the storehouse, and the cottage as the first consumer.

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
