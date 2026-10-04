# Research: how others handle terrain under placed or built structures

Stage 2 (Terrain spike), step 0. Bounded search, 2026-10-04: vanilla 1.21.1 code (read from the NeoForge sources jar)
plus 7 open-source projects. "Not confirmed" means the public page didn't say, and I didn't dig into the source.

## Vanilla Minecraft 1.21.1 (read the code)

- **Link / license:** Mojang code, not open source; read from `neoforge-21.1.194-minecraft-sources.jar` for reference only.
- **Terrain handling:**
  - `TerrainAdjustment` (`none`, `bury`, `beard_thin`, `beard_box`, `encapsulate`) plus `Beardifier`: during world
    generation, the terrain *noise* is bent around a structure's box, so ground rises to meet its floor
    ("beard" below) or is carved away around it. It works only while the chunk is generated, never on existing terrain.
  - `Structure.getLowestY` / `getLowestYIn5by5BoxOffset7Blocks` (woodland mansion): the structure's height is the
    **lowest** of the corner heights, so nothing floats. Then `WoodlandMansionStructure.afterPlace` fills **cobblestone
    down** from the floor under every footprint column until it hits solid ground: a foundation on pillars.
  - `StructurePiece.fillColumnDown` (igloo, desert temple and others): the same "pillar down until solid" idea per column.
  - `RuinedPortalStructure.findSuitableY`: tries heights and checks a few sample columns for solid ground.
  - Jigsaw pieces: `rigid` pieces (houses) keep their shape; `terrain_matching` pieces (village paths, fields)
    follow the surface block by block.
- **Worth borrowing:** "base at the lowest corner, fill down under every column" (cheap and never floats); the split
  between rigid buildings and terrain-following roads and fields.

## MineColonies + Structurize (ldtteam)

- **Links:** https://github.com/ldtteam/minecolonies, https://github.com/ldtteam/Structurize
- **License:** GPL-3.0 (both).
- **Version / loader:** Structurize shows 1.20.1 and 1.21.1 builds (Forge / NeoForge).
- **Terrain handling:**
  - Every schematic marks one block as `groundlevel`; the build tool aligns that block with the ground where the
    player places it.
  - The player chooses the site with a preview. The builder clears what's in the way, keeps what it removed, and fills
    holes and gaps with a "fill block" the player picks at the builder's hut (default: dirt).
  - No automatic site search; the wiki tells players to avoid water, trees, caves, mountains and lava when choosing.
- **Worth borrowing:** a per-structure **ground level** in the metadata (which layer of the file sits on the ground);
  **one fill block** (dirt) for holes under and around the building; removed blocks go back to storage.

## Millénaire (NeoForge 1.21.1 fork by Leviaria)

- **Links:** https://github.com/Leviaria/Millenaire (port), https://millenaire.org (original by Kinniken)
- **License:** the fork's README says GPL-3.0 (a search summary said MIT; I'd treat it as GPL-3.0 until checked).
- **Version / loader:** Minecraft 1.21.1, NeoForge 21.1.x.
- **Terrain handling:**
  - Building plans have an `around` value: how many blocks around the building are levelled before it's placed
    (0 = blend into the surroundings).
  - Villagers cut trees in the way, flatten the land, then build, all in real time.
  - Construction first searches for usable terrain. Exact scoring: not confirmed.
- **Worth borrowing:** a per-structure **margin** to level (so doors and paths aren't on a cliff edge); preparing the
  ground as a **visible job** the builders do before building (fits "good for an observer").

## Baritone (cabaletta)

- **Link:** https://github.com/cabaletta/baritone
- **License:** LGPL-3.0 (with an extra exception).
- **Version / loader:** 1.12.2 up to current versions; client-side.
- **Terrain handling** (from its schematic builder settings):
  - `buildInLayers` (finish a layer before the next), `layerOrder` (bottom-up or top-down);
  - `buildIgnoreBlocks` (treat as air), `okIfAir` (blocks that may become air);
  - `buildSubstitutes` (use other blocks instead), `buildIgnoreExisting`.
- **Worth borrowing:** a **"don't care" set** (leaves, grass, flowers count as air) when judging a site and when
  clearing; the builder clears and places **layer by layer** from the bottom up (our builders already do bottom-up).

## WorldEdit (EngineHub)

- **Link:** https://github.com/EngineHub/WorldEdit (docs: https://worldedit.enginehub.org)
- **License:** GPL-3.0.
- **Version / loader:** current versions, many platforms.
- **Terrain handling:** `//smooth [iterations]` turns the selection into a heightmap, blurs it and writes it back. It
  works on surfaces only, not on caves, walls or objects.
- **Worth borrowing:** **work on a heightmap, not on blocks**: score, cut and fill on a grid of column heights, then
  turn the result into block changes.

## GDMC (Generative Design in Minecraft) settlement competition / GDPC

- **Links:** https://gendesignmc.wikidot.com, https://github.com/avdstaaij/gdpc, paper
  https://www.pcgworkshop.com/archive/salge2018generative.pdf
- **License:** GDPC is MIT; entries are usually published open source after the competition.
- **Version / loader:** GDPC talks to the GDMC HTTP Interface mod (Java Edition).
- **Terrain handling:**
  - Entries read heightmaps and pick or adapt sites.
  - The organisers' main finding: generators "often rely on flattening large areas", which reads as unbelievable.
  - Roads projected onto terrain get awkward steps and holes.
- **Worth borrowing:** a warning more than a technique: **don't flatten everything**. Level only the footprint plus a
  small margin, pick sites that need little work, and let roads follow the land.

## Improved Village Placement (Apollounknowndev)

- **Link:** https://github.com/Apollounknowndev/improved-village-placement
- **License:** MIT.
- **Version / loader:** 1.20.1, 1.21.x and later, Fabric / Forge / NeoForge.
- **Terrain handling:** villages only spawn where an area is flat enough (80×80 for small villages, 128×128 for
  standard). The exact sampling and threshold weren't on the public pages (not confirmed).
- **Worth borrowing:** **refuse instead of fixing**: a site check before placing is the cheapest terrain strategy and
  needs no builder work.

## settlements (vberlier)

- **Link:** https://github.com/vberlier/settlements
- **License:** MIT.
- **Version / loader:** Minecraft 1.12 (Forge), proof of concept.
- **Terrain handling:** `/BuildSettlement x1 y1 z1 x2 y2 z2` builds houses, fields and windmills inside the area.
  No documented site choice or levelling.
- **Worth borrowing:** little; it confirms the simple approach (the player chooses the area, the generator fits it).

## Ideas taken into stage 2

1. **Heightmap first** (WorldEdit, GDMC): scan columns into a height grid; the scorer and the grading methods are
   pure math on that grid.
2. **Refuse bad ground** (Improved Village Placement): hard refusals before any building work.
3. **Lowest corner, fill down** (vanilla mansion, igloo): grading method (c), a foundation on pillars or fill.
4. **One fill block plus a "don't care" set** (MineColonies, Baritone): dirt for fill; leaves, plants and snow layers
   count as air for heights and scoring.
5. **Level the footprint plus a small margin, not the area** (Millénaire `around`, GDMC warning): the cut and fill
   method levels only the keep and a margin from tuning.
6. **Ground level per structure** (MineColonies `groundlevel`): a possible later metadata field; the placeholders
   all sit on layer 0 today.
