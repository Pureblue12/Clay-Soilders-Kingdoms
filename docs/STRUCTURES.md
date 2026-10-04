# Structures (DRAFT)

> Created in stage 0 because it didn't exist yet: a minimal list from `docs/ROADMAP.md`. Names, tiers and zones of the
> placeholders are guesses ([D12](DECISIONS.md)); fill in the designs as they are made.

## Pipeline

- **Geometry:** a vanilla structure file (`.nbt`, edit it with structure blocks) at
  `common/src/main/resources/data/csr/structure/<path>.nbt`, i.e. structure location `csr:<path>`.
- **Metadata:** one JSON file per structure at `common/src/main/resources/data/csr/csr/structures/<name>.json`:

  | Field | Required | Meaning |
  |---|---|---|
  | `id` | yes | Unique id, e.g. `csr:test_hut` (duplicates are reported). |
  | `name` | yes | Display name. |
  | `design` | yes | `ready` (has geometry) or `TBD` (placeholder, no geometry yet). |
  | `tier` | yes | 1–10. |
  | `zone` | yes | `keep`, `inner`, `wall` or `outer` (the soft rings, [D3](DECISIONS.md)). |
  | `role` | yes | keep, house, farm, resource, storage, defense, gate, military, workshop, market, religious, health, food, utility, decoration. |
  | `footprint` | ready only | Size `[x, y, z]`; must match the structure file. |
  | `cost` | no | `"auto"` (default: counted from the blocks, the way builders count them) or `{"minecraft:cobblestone": 30, ...}`. |
  | `variant_group` | no | Structures that are variants of each other share this. |
  | `palette` | no | Material palette name, e.g. `oak`. |
  | `ruin` | no | Structure file of the ruined version, e.g. `csr:kingdom/test_hut_ruin`. |
  | `structure` | ready only | Structure file location, e.g. `csr:kingdom/test_hut`. |

- **Code:** `common/.../structure/` (`StructureMeta`, `StructureRegistry`). Structure files are read through vanilla's
  structure manager, which looks in the world's `generated` folder first (structure-block saves), then data packs
  and the mod.
- **Validation** (on load, `/reload`, `/csr dev structure reload`): every ready entry's structure file exists and its
  size matches the footprint; zones, roles, tiers and design are legal; ids are unique; cost items exist; the ruin
  file exists. Problems go to the game log (`CSR structures: ...`) and the STRUCTURE recorder category.
  `StructureRegistryGameTests.shippedRegistryIsValid` fails if the shipped registry has any problem.
- **Facing:** a structure's local z = 0 side is its front, the side towards the easel (builders) or towards you
  (paste tool).

## Test structures (ready)

Made by `tools/make_test_structures.py`.

| id | Size | Zone | Role | What |
|---|---|---|---|---|
| `csr:test_hut` | 5×3×5 | inner | house | Cobblestone walls, oak door in the front, plank roof. |
| `csr:test_well` | 3×2×3 | inner | utility | Cobblestone ring with water, fence posts. |
| `csr:test_wall` | 5×3×1 | wall | defense | Stone brick wall segment. |

## Placeholders (design: TBD, no geometry)

| id | Name | Tier | Zone | Role |
|---|---|---|---|---|
| `csr:keep` | Keep | 1 | keep | keep |
| `csr:cottage` | Cottage | 1 | inner | house |
| `csr:farm` | Farm | 1 | outer | farm |
| `csr:lumber_camp` | Lumber camp | 1 | outer | resource |
| `csr:storehouse` | Storehouse | 1 | inner | storage |
| `csr:palisade` | Palisade segment | 2 | wall | defense |
| `csr:gate` | Gate | 2 | wall | gate |
| `csr:stone_wall` | Stone wall segment | 3 | wall | defense |
| `csr:wall_tower` | Wall tower | 3 | wall | defense |
| `csr:barracks` | Barracks | 3 | inner | military |
| `csr:smithy` | Smithy | 3 | inner | workshop |
| `csr:quarry` | Quarry | 3 | outer | resource |
| `csr:market` | Market | 4 | inner | market |
| `csr:chapel` | Chapel | 4 | inner | religious |
| `csr:infirmary` | Infirmary | 4 | inner | health |
| `csr:food_hall` | Food hall | 4 | inner | food |

## Paste tool (development runs only)

| Command | Book (Structures) | Does |
|---|---|---|
| `/csr dev structure place <id> [north\|east\|south\|west]` | one "Paste" button per ready structure | Pastes the whole structure instantly. You stand where an easel would stand; the structure goes the way you face (or the given direction), starting 2 blocks away, centered. These are the same rules as builders and `/csr build`. |
| `/csr dev structure undo` | Undo last paste | Puts back the blocks the last paste replaced, including chest contents. Keeps the last `dev.paste_undo_depth` (10) pastes, in memory only. |
| `/csr dev structure reload` | Reload structures | Re-reads metadata and structure files from disk and validates them. |
| `/csr dev structure list` | List all | Every entry, including placeholders, plus problems. |

A paste is an exact copy of the file:
- Air inside the structure is placed too.
- Containers it replaces are emptied first, so nothing drops.
- No shape updates are sent, so nothing outside the box changes. Fences won't link up with blocks outside the structure.

The STRUCTURE recorder category logs pastes, undos, reloads and validation problems.

## Touch-up workflow (edit a structure in game)

1. **Paste it in the file's own orientation:** book → Structures → Paste, while facing **south**
   (or `/csr dev structure place csr:test_hut south`). The chat then prints exactly where to put the structure block
   and what to type into it.
2. **Edit the blocks by hand.**
3. **Save with a structure block** (`/give @s minecraft:structure_block`). Place it where the chat said: one block
   below the paste's first corner. Set:
   - mode **Save**;
   - name = the structure file, e.g. `csr:kingdom/test_hut`;
   - relative position `0 1 0`;
   - size = the printed size (e.g. `5 3 5`).

   Click **SAVE**. Minecraft writes `neoforge/run/saves/<world>/generated/csr/structures/kingdom/test_hut.nbt`.
4. **Reload:** book → Structures → **Reload structures**. Pastes now use your edited file. The world's `generated`
   folder wins over the mod's copy.
5. **Make it permanent:** copy that file over `common/src/main/resources/data/csr/structure/kingdom/test_hut.nbt`.
   If the size changed, update `footprint` in `common/src/main/resources/data/csr/csr/structures/test_hut.json`.
   Then delete the copy in the world's `generated` folder, or it keeps winning. Commit.

Notes:
- Air you leave inside the box is saved as air.
- A structure void block (`/give @s minecraft:structure_void`) marks "leave whatever is there".
- Builders' blueprints (the easel) don't read the `generated` folder; only the paste tool and the registry do.
- **New structure:** build it, save it with a structure block under a new name (e.g. `csr:kingdom/my_house`), copy the
  file into `common/src/main/resources/data/csr/structure/kingdom/`, add a metadata file next to the others in
  `data/csr/csr/structures/`, then run `./gradlew :neoforge:processResources` and Reload structures (or restart).
