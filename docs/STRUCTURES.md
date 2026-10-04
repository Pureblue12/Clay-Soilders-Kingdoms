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
