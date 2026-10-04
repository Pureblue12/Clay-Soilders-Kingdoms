# Research: scaffolding and reach for AI builders

Stage 3 (Tall construction spike), step 0. Bounded search, 2026-10-04: vanilla 1.21.1 code (read from the NeoForge
sources jar) plus 5 projects. Our project is MIT: **no code was copied**; only ideas are taken. "Not confirmed" means
the public pages didn't say.

## Vanilla Minecraft 1.21.1: the scaffolding block and mob pathing (read the code)

- **Link / license:** Mojang code, not open source; read for reference only.
- **What it does:**
  - `ScaffoldingBlock` is a climbable block. Players climb inside it (jump goes up, sneak goes down) and stand on its
    top. It falls when it is more than 7 blocks sideways from a supported column (`STABILITY_MAX_DISTANCE`).
  - `LivingEntity.onClimbable` lets any entity climb when it is inside a climbable block and pushes against a wall.
  - The pathfinder mobs use for walking (`WalkNodeEvaluator`) has **no idea of climbing**: it plans only walking,
    1-block jumps up and drops down. Only spiders (`WallClimberNavigation`) plan vertical movement.
  - So a mob can stand and walk on top of scaffolding, but its navigation never plans a path up a scaffolding
    column or a ladder.
- **Worth borrowing:** the warning: **ground mobs only climb what looks like steps**. A scaffold for our soldiers must
  be a staircase they can hop up 1 block at a time.

## MineColonies + Structurize (ldtteam)

- **Links:** https://github.com/ldtteam/minecolonies, https://github.com/ldtteam/Structurize, issues
  [#3045](https://github.com/ldtteam/minecolonies/issues/3045) and [#6895](https://github.com/ldtteam/minecolonies/issues/6895)
- **License:** GPL-3.0 (ideas only; nothing copied).
- **Version / loader:** 1.20.1 and 1.21.1 (Forge / NeoForge).
- **What it does:**
  - Builders don't put up scaffolding. They climb the building itself where they can and **teleport** when stuck.
    Issue #6895 reports builders jumping up and down when there is no foothold.
  - A proposal (#3045, closed, apparently not built) suggested a separate stage that puts scaffolding and ladders
    around buildings taller than about 5 blocks.
- **Worth borrowing:**
  - **Scaffolding as its own stage**, only for tall structures.
  - The failure mode to avoid: builders that depend on footholds the structure doesn't give them.
  - We don't teleport: soldiers walking up visible steps is better for an observer.

## Baritone (cabaletta)

- **Link:** https://github.com/cabaletta/baritone, settings: https://baritone.leijurv.com/baritone/api/Settings.html
- **License:** LGPL-3.0 (ideas only).
- **Version / loader:** 1.12.2 up to current versions; client-side bot.
- **What it does:**
  - With `allowPlace` it places "throwaway" blocks (`acceptableThrowawayItems`: dirt, cobblestone, netherrack,
    stone) to pillar up or bridge while building a schematic.
  - It doesn't remove them afterwards: issue [#2976](https://github.com/cabaletta/baritone/issues/2976) asks for
    that and is still open.
- **Worth borrowing:**
  - A **configurable scaffold material** (cheap, common blocks).
  - The open issue shows **removing temporary blocks has to be designed in from the start**: track every scaffold
    block.

## Create: the Schematicannon (Creators-of-Create)

- **Links:** https://github.com/Creators-of-Create/Create, wiki https://wiki.createmod.net
- **License:** MIT (per its GitHub repository; nothing copied).
- **Version / loader:** 1.20.1 / 1.21.1, Forge / NeoForge.
- **What it does:** a machine shoots blocks into place from up to 256 blocks away, fed from adjacent inventories. Reach
  and height don't matter; it only builds in loaded chunks.
- **Worth borrowing:** little for walking builders, but it marks the other end of the design space: a machine that
  removes the reach problem. That doesn't fit "soldiers build it".

## Millénaire

- **Link:** https://millenaire.org (NeoForge 1.21.1 port: https://github.com/Leviaria/Millenaire, GPL-3.0)
- **License:** GPL-3.0 (ideas only).
- **Version / loader:** 1.21.1, NeoForge (port).
- **What it does:** villagers stand near the site while the building appears block by block in front of them. They
  don't walk up the building, so no scaffolding is needed. Exact mechanics: not confirmed.
- **Worth borrowing:** "the builder stays on the ground" is the cheapest answer, at the price of believability.

## Ideas taken into stage 3

1. **Steps, not ladders or vanilla scaffolding:** soldiers' walking navigation only plans 1-block hops (vanilla). The
   scaffold is a staircase of full blocks.
2. **Scaffolding only for structures taller than reach** (MineColonies' proposal): structures that never exceed reach
   keep today's build order exactly.
3. **Its own stage in the order:** build what is reachable, raise the scaffold, continue; tear it down top-down after.
4. **Track every scaffold block** (Baritone's open issue): saved, removed when the build ends or is interrupted, and
   never remove a block a player changed.
5. **A cheap configurable material** (Baritone's throwaway blocks), set in the tuning file.
