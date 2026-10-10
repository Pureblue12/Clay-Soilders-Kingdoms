# Structure style guide (read before building)

Short rules for agents and people building structures for Clay Soldiers Kingdoms. The mod's full design notes are in
`../docs/STRUCTURES.md` (scale D43, style A D49, buildability D46). When this guide and that file disagree, that file
wins.

## Scale

- **Miniature.** Soldiers are about half a block tall. Common buildings are small: a cottage is about **5 x 7**,
  walls **3 high** (a vanilla 2-tall door fits), roofs add **2 to 4**.
- **Landmarks** (keep, church, town hall, main gate, a few towers) may be bigger, up to **28 tall**. Nothing taller.
- Upper floors need **stairs**, never ladders (soldiers don't climb).
- Turn on the **Scale** button in the viewer: a player (1.8) and a clay soldier (0.5) stand next to the build.

## Palette (town style A)

| Role | Early tiers (timber hamlet) | Later tiers (stone town) |
|---|---|---|
| Foundation, plinth | cobblestone, mossy cobblestone | cobblestone, stone bricks |
| Walls | oak planks, white terracotta (plaster) in a spruce or dark oak frame | stone bricks with mossy and cracked mixed in (about 1 in 4) |
| Posts and beams | spruce log, stripped spruce log, oak log | polished andesite quoins, stone brick |
| Roof | spruce stairs and slabs (oak for small sheds) | spruce or dark oak stairs; stone brick for towers |
| Trim | upside-down stairs under eaves, trapdoor shutters, slab sills | corbels (upside-down stairs), stone brick walls as posts |
| Accents | red (bed, carpet, banner), brick chimneys, flowers | red banners, lanterns |

Mix variants with a fixed pattern (see `noise()` in `samples/builder.ts`), never per-block random choices by hand.

## Shapes

- **Roofs:** gable roofs from stairs, 45 degrees (one up per one across), with **one block of overhang** on every side.
  Put the eaves **above** a top-plate beam, not level with the top of the wall, or the roof hides the walls (house v1
  made that mistake). Finish the ridge with slabs. Trim gable overhangs with upside-down stairs.
- **Walls:** never a flat face of one block. Break them up with posts at corners and every 3 to 4 blocks, a plinth row
  of stone at the bottom, and a beam at the top.
- **Openings:** windows at eye height (y 2 in a 3-high wall) with shutters or sills; doors centred on the front with a
  light on each side and a step in front.
- **Depth:** something should stick out or sink in on every face: sills, corbels, buttresses, flower boxes, chimneys.
- **Front** is z = 0 (north). The door goes on the front.

## Detail rules

- Detail comes from small blocks: stairs, slabs, trapdoors, fences, walls, buttons, carpets, lanterns, flower pots.
- Furniture at soldier size: a bed block, a fence plus pressure plate for a table, a stair for a chair, a trapdoor as
  a shelf. Every workplace has one workstation block.
- **Buildability** (the validator warns): builders can't place water, lava, fire, farmland, dirt paths, potted plants;
  soldiers can't open iron doors or fence gates. Blocks no builder GameTest has placed yet are listed as "not yet tested":
  fine to use, but say so in the description.
- Keep the block count in budget: common building first build about 200, with all detail 350; landmark 1,800; wall
  module 60 (the sample wall module is over: it's a demo of the look).

## Process

1. Read this file and the brief.
2. Build in code (`samples/builder.ts`) or by hand in the JSON.
3. `validate`, then `render` all views, look at every PNG.
4. Write down what looks wrong (the critique), fix it, save a new version with `--changes`.
5. Carlton approves or rejects in the UI. Never mark anything approved yourself.
