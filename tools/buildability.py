#!/usr/bin/env python3
"""Fast static "can this be built" check for every promoted structure level, before any game test runs.

  python3 tools/buildability.py                    every structure level (the ones StructureBuildGameTests builds)
  python3 tools/buildability.py keep church        only these structure ids (keep:2 = only its level 2)
  python3 tools/buildability.py --tsv out.tsv      also write one row per level
  python3 tools/buildability.py --markdown         print the report as a Markdown table (for a CI run summary)

Simulates 3 builders building the level in the structure build test's layout (StructureBuildGameTests: 48 by 48 grass
floor, easel at (24, 2, 6), chest at (22, 2, 5), structure from z = 8, centered on the easel) with the mod's own rules,
read from the Java code and kept in step by hand:
  - build order (ImmutableTemplate): full cubes, then other shapes, then blocks with data, each bottom to top; doors
    last. A builder takes the FIRST block in that order it can reach (ServerBlueprintPlan.getRequest), so the
    simulation places, pass after pass, every block it can reach until nothing more can be placed;
  - reach (BuilderReach): from a standing spot whose center is less than 2.5 blocks sideways from the block's center
    and 2 below to 8 above the feet, never the builder's own cell;
  - walking (ScaffoldGrid.reachable): a standing spot is a free cell with a free cell above and a floor below; a step
    is level, a hop up 1, or a drop of up to 3; paths from the chest at most 64 long (follow range 16 +
    build.path_range_bonus 48), a hop up counts 1.4;
  - scaffold (ScaffoldPlanner, approach 4): when nothing is reachable and blocks are left, 1 by 1 climbed columns go up
    in (x, z) columns that hold no structure block, base reachable within 24, open on 2 sides.
Soldiers are tiny: a fence, wall, pane or iron bars top is NOT a floor here (vanilla's pathfinder counts it as one, and
soldiers fall through it: town hall falls, 2026-10-11 handoff, "Cause A").

Each level gets a verdict:
  BUILDABLE  every block placed without scaffold, nothing dangerous found;
  SCAFFOLD   every block placed, some only from scaffold columns (tall; builds that need scaffold are slow and the
             least proven part of the mod);
  RISKY      every block placed, but builders can get stuck or fall: standing spots with no way back to the chest
             ("one-way": reached by a drop of 2 or 3 they can't climb back), blocks placeable only from such spots, or
             fence/wall/pane tops the pathfinder would walk on 2+ above the ground;
  STUCK      blocks left that no standing spot (ground, floors already built, scaffold) reaches: the build can't finish.
It's a model, not the game: it doesn't know about falling sand, block support (torches need a wall), the stall timer,
or how long anything takes. Standard library only; about a second per level.
"""
import heapq
import json
import multiprocessing
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nbt_read import read_nbt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STUDIO = os.path.join(ROOT, "common/src/main/resources/data/csr/structure/kingdom/studio")
META = os.path.join(ROOT, "common/src/main/resources/data/csr/csr/structures")
TESTS = os.path.join(ROOT, "neoforge/src/main/java/net/bumblebee/claysoldiers/gametest/StructureBuildGameTests.java")

# Test area (StructureBuildGameTests.TEMPLATE, 48 x 24 x 48): floor y = 1, ground (first free cell) y = 2.
AREA_X, AREA_Y, AREA_Z = 48, 24, 48
GROUND = 2
EASEL = (24, 2, 6)
CHEST = (22, 2, 5)
STRUCTURE_Z = 8

HORIZONTAL_REACH = 2.5   # BuilderReach.HORIZONTAL_REACH
MAX_PLACE_HEIGHT = 8     # BuilderReach.MAX_PLACE_HEIGHT
MAX_PLACE_DEPTH = 2      # BuilderReach.MAX_PLACE_DEPTH
MAX_DROP = 3             # ScaffoldGrid.MAX_DROP
MAX_PATH = 16 + 48       # follow range + build.path_range_bonus (D61, D62)
COLUMN_BASE_PATH = 16 + 16 - 8  # Scaffolds: follow range + scaffold.path_range_bonus - PATH_MARGIN
# A fence/wall/pane top this high above the ground is a fall that hurts (town hall: 7, 4.0 damage a fall); lower ones
# (garden fences, fence rows on the ground) are harmless and every passing structure has them.
FALL_HEIGHT = 4

# Block kinds (movement): AIR no collision; FLOOR collision with a top to stand on; WALL collision, nothing to stand on
# for a soldier (fence/wall/pane/bars tops, leaves); CLIMB scaffolding/ladders/vines (free, can stand and climb in).
AIR, FLOOR, WALL, CLIMB = 0, 1, 2, 3
NO_COLLISION = ("air", "cave_air", "void_air", "structure_void", "torch", "wall_torch", "sign", "hanging_sign", "banner",
                "button", "lever", "rail", "pressure_plate", "carpet", "sapling", "short_grass", "tall_grass", "fern",
                "dead_bush", "poppy", "dandelion", "allium", "azure_bluet", "blue_orchid", "tulip", "oxeye_daisy",
                "cornflower", "lily_of_the_valley", "rose_bush", "lilac", "peony", "sunflower", "pink_petals",
                "sweet_berry_bush", "candle", "flower_pot", "chain", "lantern", "lightning_rod", "redstone_wire",
                "tripwire", "wheat", "carrots", "potatoes", "beetroots", "sugar_cane", "seagrass", "glow_lichen",
                "water", "lava", "snow")
CLIMBABLE = ("scaffolding", "ladder", "vine")
# Blocks without an item: builders never place them; the decoration step adds them once the build is finished (stage 5a,
# StructureDecoration, which asks the game: Block.asItem() is air). Left out of the simulated build.
NO_ITEM = ("water", "lava", "fire", "soul_fire", "farmland", "dirt_path", "water_cauldron", "lava_cauldron",
           "powder_snow_cauldron", "bubble_column", "frosted_ice", "tall_seagrass", "piston_head", "cave_vines_plant",
           "kelp_plant", "weeping_vines_plant", "twisting_vines_plant", "big_dripleaf_stem", "candle_cake",
           "nether_portal", "end_portal", "end_gateway")


def decoration(name):
    """Whether a block (minecraft: prefix removed) is added by the decoration step instead of builders."""
    return name in NO_ITEM or name.startswith(("potted_", "attached_")) or name.endswith("_candle_cake")
THIN = ("_fence", "_wall", "_pane", "iron_bars", "_leaves", "_fence_gate")


def block_kind(name, props):
    """The movement kind of a block state (minecraft: prefix removed)."""
    if name in CLIMBABLE:
        return CLIMB
    if name.endswith("_door"):
        return WALL if name.startswith("iron") else AIR   # soldiers open wooden doors (UseDoorGoal)
    if name.endswith("_trapdoor"):
        return AIR if props.get("open") == "true" else FLOOR
    if name.endswith("_fence_gate"):
        return AIR if props.get("open") == "true" else WALL
    if name.endswith("wall_torch") or name.endswith("wall_sign") or name.endswith("wall_banner") or name.endswith("wall_hanging_sign"):
        return AIR
    if name == "rail" or name.endswith("_rail"):
        # No collision, but vanilla's pathfinder (WalkNodeEvaluator, UNPASSABLE_RAIL) keeps a mob that doesn't start its
        # path on a rail off every rail cell: for builders a rail is a wall (mine L2/L3: the last rails of a tunnel
        # whose only way in is the rail track were never reached).
        return WALL
    if any(name == n or name.endswith("_" + n) for n in NO_COLLISION):
        return AIR
    if name.endswith(THIN) or name == "glass_pane":
        return WALL
    return FLOOR


def thin_top(name):
    """Fence, wall, pane, bars: vanilla's pathfinder lets mobs walk on top; tiny soldiers fall through."""
    return name.endswith(("_fence", "_wall", "_pane", "iron_bars", "_fence_gate")) and not name.endswith(
        ("wall_torch", "wall_sign", "wall_banner", "wall_hanging_sign")) or name == "glass_pane"


def phase(name, props, has_nbt):
    """ImmutableTemplate's three lists (0 full cubes, 1 other shapes, 2 with data); doors go after all (3)."""
    if name.endswith("_door"):
        return 3
    if has_nbt:
        return 2
    full = block_kind(name, props) == FLOOR and not any(s in name for s in (
        "slab", "stairs", "trapdoor", "bed", "chest", "anvil", "campfire", "daylight_detector", "lectern",
        "grindstone", "stonecutter", "bell", "cauldron", "composter", "brewing_stand", "hopper", "enchanting",
        "cake", "head", "skull", "farmland", "dirt_path", "conduit", "end_portal_frame", "bamboo", "cactus"))
    return 0 if full else 1


# --------------------------------------------------------------------------------------------------------------------

class Level:
    """One structure level placed in the test area."""

    def __init__(self, path):
        root = read_nbt(path)
        palette = root["palette"] if "palette" in root else root["palettes"][0]
        self.size = sx, sy, sz = root["size"]
        self.base = (EASEL[0] - sx // 2, GROUND, STRUCTURE_Z)
        states = []
        for entry in palette:
            name = entry["Name"].split(":", 1)[-1]
            states.append((name, entry.get("Properties", {})))
        self.blocks = []   # (phase, y, x, z, name, kind) in build order
        self.props = {}    # (x, y, z) -> block state properties
        for block in root["blocks"]:
            name, props = states[block["state"]]
            if name in ("air", "cave_air", "void_air", "structure_void", "jigsaw") or decoration(name):
                continue
            x, y, z = block["pos"]
            pos = (self.base[0] + x, self.base[1] + y, self.base[2] + z)
            self.blocks.append((phase(name, props, "nbt" in block), pos[1], pos[0], pos[2], name, block_kind(name, props)))
            self.props[pos] = props
        self.blocks.sort()
        self.columns = {(b[2], b[3]) for b in self.blocks}


class Grid:
    """The test area: cell kinds, flat array indexed (x, y, z)."""

    def __init__(self):
        self.kind = bytearray(AREA_X * AREA_Y * AREA_Z)
        self.thin = bytearray(AREA_X * AREA_Y * AREA_Z)
        for x in range(AREA_X):
            for z in range(AREA_Z):
                self.set((x, 0, z), FLOOR)
                self.set((x, 1, z), FLOOR)
        self.set(EASEL, FLOOR)
        self.set(CHEST, FLOOR)

    @staticmethod
    def index(x, y, z):
        return (y * AREA_Z + z) * AREA_X + x

    @staticmethod
    def inside(x, y, z):
        return 0 <= x < AREA_X and 0 <= y < AREA_Y and 0 <= z < AREA_Z

    def at(self, x, y, z):
        if not self.inside(x, y, z):
            return WALL   # barrier walls and roof
        return self.kind[self.index(x, y, z)]

    def set(self, pos, kind, thin=False):
        i = self.index(*pos)
        self.kind[i] = kind
        self.thin[i] = 1 if thin else 0

    def solid(self, x, y, z):
        k = self.at(x, y, z)
        return k == FLOOR or k == WALL

    def standable(self, x, y, z, vanilla=False):
        k = self.at(x, y, z)
        if k == CLIMB:
            return True
        if y < 1 or k != AIR or self.solid(x, y + 1, z):
            return False
        below = self.at(x, y - 1, z)
        if below == FLOOR or below == CLIMB:
            return True
        return vanilla and below == WALL and self.inside(x, y - 1, z) and self.thin[self.index(x, y - 1, z)]

    def climbable(self, x, y, z):
        return self.at(x, y, z) == CLIMB


def moves(grid, x, y, z, vanilla=False):
    """Neighbouring standing spots with their step cost (ScaffoldGrid.reachable)."""
    if grid.climbable(x, y, z):
        if grid.climbable(x, y + 1, z):
            yield x, y + 1, z, 0.0
        if grid.climbable(x, y - 1, z):
            yield x, y - 1, z, 0.0
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if not grid.solid(x, y + 1, z) and grid.standable(nx, y + 1, nz, vanilla):
            yield nx, y + 1, nz, 1.4142
        if grid.standable(nx, y, nz, vanilla):
            yield nx, y, nz, 1.0
        if not grid.solid(nx, y, nz) and not grid.solid(nx, y + 1, nz):
            for drop in range(1, MAX_DROP + 1):
                if grid.standable(nx, y - drop, nz, vanilla):
                    yield nx, y - drop, nz, (1 + drop * drop) ** 0.5
                    break
                if grid.solid(nx, y - drop, nz):
                    break


def start_spots(grid, vanilla=False):
    hx, hy, hz = CHEST
    for x in range(hx - 2, hx + 3):
        for z in range(hz - 2, hz + 3):
            for y in (hy - 1, hy, hy + 1):
                if grid.standable(x, y, z, vanilla):
                    yield x, y, z


def reachable(grid, limit=MAX_PATH, vanilla=False):
    """Standing spots builders walk to from the chest: {cell: path length}."""
    best = {}
    queue = []
    for cell in start_spots(grid, vanilla):
        best[cell] = 0.0
        queue.append((0.0, cell))
    heapq.heapify(queue)
    done = set()
    while queue:
        d, cell = heapq.heappop(queue)
        if cell in done:
            continue
        done.add(cell)
        for nx, ny, nz, cost in moves(grid, *cell, vanilla):
            nd = d + cost
            if nd < limit and nd < best.get((nx, ny, nz), 1e9):
                best[(nx, ny, nz)] = nd
                heapq.heappush(queue, (nd, (nx, ny, nz)))
    return {cell: best[cell] for cell in done}


def way_back(grid, spots):
    """The spots in `spots` from which a builder can walk back to the chest (reverse search over the same moves)."""
    starts = set(start_spots(grid))
    back = set(starts & set(spots))
    incoming = {}
    for cell in spots:
        for nx, ny, nz, _ in moves(grid, *cell):
            if (nx, ny, nz) in spots:
                incoming.setdefault((nx, ny, nz), []).append(cell)
    todo = list(back)
    while todo:
        cell = todo.pop()
        for prev in incoming.get(cell, ()):
            if prev not in back:
                back.add(prev)
                todo.append(prev)
    return back


def place_spots(target, spots):
    """The standing spots in `spots` a builder can place `target` from (BuilderReach.canPlaceFrom)."""
    tx, ty, tz = target
    found = []
    for dx in (-2, -1, 0, 1, 2):
        for dz in (-2, -1, 0, 1, 2):
            if dx * dx + dz * dz >= HORIZONTAL_REACH * HORIZONTAL_REACH:
                continue
            for feet in range(ty - MAX_PLACE_HEIGHT, ty + MAX_PLACE_DEPTH + 1):
                if dx == 0 and dz == 0 and feet == ty:
                    continue
                cell = (tx + dx, feet, tz + dz)
                if cell in spots:
                    found.append(cell)
    return found


def add_columns(grid, level, remaining):
    """Puts up 1 by 1 climbed columns (outside every structure column) that reach remaining blocks; True if any."""
    spots = reachable(grid, COLUMN_BASE_PATH)
    lowest = {}
    for (x, y, z) in spots:
        if (x, z) not in level.columns and ((x, z) not in lowest or y < lowest[(x, z)]):
            lowest[(x, z)] = y
    top = max(b[1] for b in level.blocks)
    added = False
    taken = set()
    for (_, ty, tx, tz, _, _) in remaining:
        best = None
        for (cx, cz), base in lowest.items():
            dx, dz = cx - tx, cz - tz
            if dx * dx + dz * dz >= HORIZONTAL_REACH * HORIZONTAL_REACH or any(
                    abs(cx - ox) <= 1 and abs(cz - oz) <= 1 and (cx, cz) != (ox, oz) for ox, oz in taken):
                continue
            open_sides = sum(1 for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (cx + ox, base, cz + oz) in spots)
            if open_sides < 2:
                continue
            need = max(base, ty - MAX_PLACE_HEIGHT)
            if all(grid.at(cx, y, cz) in (AIR, CLIMB) for y in range(base, min(need, top) + 1)):
                if best is None or need - base < best[2]:
                    best = (cx, cz, need - base, base, need)
        if best is not None:
            cx, cz, _, base, need = best
            for y in range(base, need + 1):
                if grid.at(cx, y, cz) == AIR:
                    grid.set((cx, y, cz), CLIMB)
                    added = True
            taken.add((cx, cz))
    return added


PLANTS = ("sapling", "short_grass", "tall_grass", "fern", "dead_bush", "poppy", "dandelion", "allium", "azure_bluet",
          "blue_orchid", "tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "rose_bush", "lilac", "peony",
          "sunflower", "pink_petals", "sweet_berry_bush", "azalea", "flowering_azalea", "wheat", "carrots", "potatoes",
          "beetroots", "sugar_cane")
SOIL = ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud", "farmland", "mycelium")
STAND_ON_TOP = ("torch", "lantern", "carpet", "pressure_plate", "rail", "redstone_wire", "banner", "sign", "candle",
                "flower_pot", "_door", "gravel", "sand", "concrete_powder", "snow")
GRAVITY = ("gravel", "sand", "red_sand", "concrete_powder", "anvil", "chipped_anvil", "damaged_anvil")
CENTER_POSTS = ("_fence", "_wall", "_pane", "iron_bars", "chain")


def named(name, words):
    return any(name == n or name.endswith("_" + n) or n.startswith("_") and name.endswith(n) for n in words)


def unsupported(name, props, pos, at):
    """Why a block that can't float has no valid support in the finished structure, or None. `at(pos)` gives the
    (name, props) there ("air" for nothing; the test area's floor is grass). Such a block drops as soon as anything next
    to it changes (gravel at once): the 2026-10-11 run's missing hanging lanterns, poppies and gravel."""
    x, y, z = pos
    if props.get("hanging") == "true" or name.endswith("hanging_sign") and not name.endswith("wall_hanging_sign"):
        above, above_props = at((x, y + 1, z))
        half = above_props.get("half", above_props.get("type"))
        if above == "air":
            return "hangs from nothing"
        # Under a top stair it stays: stairs are placed before lanterns (D69) and never change after (keep, inn, town hall).
        if "slab" in above and half == "top":
            return f"hangs from the top half of {above} (no face to hang from)"
        if phase(above, above_props, False) != 0 and "slab" not in above and "stairs" not in above \
                and not named(above, CENTER_POSTS) and not above.endswith("_leaves"):
            return f"hangs from {above}"
        return None
    if name.endswith(("wall_torch", "wall_sign", "wall_banner")):
        return None   # on a wall: floating ones stayed in every build so far (hovel, stone wall corner)
    below, below_props = at((x, y - 1, z))
    if named(name, PLANTS):
        if name in ("wheat", "carrots", "potatoes", "beetroots"):
            return None if below == "farmland" else f"a crop on {below}"
        return None if below in SOIL or name == "sugar_cane" and below in ("sand", "sugar_cane") or below == name else f"a plant on {below}"
    if name.endswith("_door") and props.get("half") == "upper":
        return None
    if named(name, STAND_ON_TOP):
        return f"stands on {below}" if block_kind(below, below_props) == AIR else None
    return None


def check(path):
    level = Level(path)
    grid = Grid()
    remaining = list(level.blocks)
    placed_from_trap = []
    decor = []   # (x, y, z, name, why)
    falls = []   # gravel, sand placed before the block under it: it falls and takes that block's spot
    finished = {(b[2], b[1], b[3]): (b[4], level.props[(b[2], b[1], b[3])]) for b in level.blocks}

    def at(pos):
        if pos[1] < GROUND:
            return "grass_block", {}
        return finished.get(pos, ("air", {}))

    for (_, y, x, z, name, _) in level.blocks:
        why = unsupported(name, level.props[(x, y, z)], (x, y, z), at)
        if why:
            decor.append((x, y, z, name, why))
    scaffold_cells = 0
    scaffold_used = False
    passes = 0
    while remaining:
        passes += 1
        progress = False
        spots = reachable(grid)
        back = way_back(grid, spots)
        current = None
        dirty = False
        left = []
        doors_wait = any(b[0] != 3 for b in remaining)
        for block in remaining:
            ph, y, x, z, name, kind = block
            if ph == 3 and doors_wait:
                left.append(block)
                continue
            # Recompute where builders can stand when the build moves to a new layer or list.
            if current != (ph, y) and dirty:
                spots = reachable(grid)
                back = way_back(grid, spots)
                dirty = False
            current = (ph, y)
            where = place_spots((x, y, z), spots)
            if not where:
                left.append(block)
                continue
            if not any(s in back for s in where):
                placed_from_trap.append((x, y, z, name))
            if named(name, GRAVITY) and grid.at(x, y - 1, z) == AIR:
                # It falls into the cell below, which the build still needs (mine L2: gravel above a stair, 1 block left).
                below = next((b[4] for b in remaining if (b[2], b[1], b[3]) == (x, y - 1, z)), "nothing")
                falls.append((x, y, z, name, f"falls: {below} below it is placed after it"))
            grid.set((x, y, z), kind, thin_top(name))
            progress = dirty = True
        remaining = left
        if remaining and not progress:
            before = sum(1 for k in grid.kind if k == CLIMB)
            if not add_columns(grid, level, remaining):
                break
            scaffold_used = True
            scaffold_cells = sum(1 for k in grid.kind if k == CLIMB) - before + scaffold_cells
        if passes > 400:
            break
    # The finished structure: one-way spots (no way back down), fence tops the pathfinder would walk on.
    final = reachable(grid)
    back = way_back(grid, final)
    one_way = sorted(s for s in final if s not in back)
    vanilla = reachable(grid, vanilla=True)
    fence_tops = sorted(s for s in vanilla if s not in final and s[1] - GROUND >= FALL_HEIGHT
                        and grid.inside(s[0], s[1] - 1, s[2]) and grid.thin[grid.index(s[0], s[1] - 1, s[2])])
    decor = [d for d in decor if not any(d[:3] == f[:3] for f in falls)]
    return level, remaining, placed_from_trap, scaffold_used, scaffold_cells, one_way, fence_tops, decor, falls


VERDICTS = ("STUCK", "RISKY", "DECOR", "SCAFFOLD", "BUILDABLE", "NO FILE")


def verdict(remaining, trap, scaffold, fence_tops, decor, falls=()):
    if remaining or falls:
        return "STUCK"
    if trap or fence_tops:
        return "RISKY"
    if decor:
        return "DECOR"
    return "SCAFFOLD" if scaffold else "BUILDABLE"


def heights(cells):
    ys = sorted({c[1] - GROUND for c in cells})
    if not ys:
        return ""
    return f"{ys[0]}" if ys[0] == ys[-1] else f"{ys[0]}-{ys[-1]}"


def entries():
    with open(TESTS, encoding="utf-8") as f:
        return [(sid, len(blocks.split(","))) for sid, blocks in
                re.findall(r'new Entry\("([a-z0-9_]+)", \d+, \d+, ([\d, ]+)\)', f.read())]


def level_file(sid, lv):
    """The level's structure file, from the structure's metadata JSON (its "levels" list)."""
    try:
        with open(os.path.join(META, sid + ".json"), encoding="utf-8") as f:
            levels = json.load(f)["levels"]
        return os.path.join(STUDIO, levels[lv - 1].split("kingdom/studio/", 1)[1] + ".nbt")
    except (OSError, KeyError, IndexError, ValueError):
        return os.path.join(STUDIO, f"{sid}_l{lv}.nbt")


def check_level(job):
    """One row: (id, level, verdict, blocks, left, notes)."""
    sid, lv = job
    path = level_file(sid, lv)
    if not os.path.exists(path):
        return sid, lv, "NO FILE", 0, 0, path
    level, remaining, trap, scaffold, columns, one_way, fence_tops, decor, falls = check(path)
    v = verdict(remaining, trap, scaffold, fence_tops, decor, falls)
    notes = [f"{name} at height {y - GROUND} {why}" for x, y, z, name, why in falls]
    if remaining:
        first = remaining[0]
        notes.append(f"{len(remaining)} blocks no standing spot reaches, at height {heights([(b[2], b[1], b[3]) for b in remaining])}"
                     f" (first: {first[4]} at height {first[1] - GROUND})")
    if scaffold:
        notes.append(f"needs {columns} scaffold cells")
    if trap:
        notes.append(f"{len(trap)} blocks placeable only from a spot with no way back to the chest"
                     f" (first: {trap[0][3]} at height {trap[0][1] - GROUND})")
    if fence_tops:
        notes.append(f"{len(fence_tops)} fence/wall/pane tops the pathfinder walks on at height {heights(fence_tops)}"
                     " (soldiers fall through)")
    for x, y, z, name, why in decor[:3]:
        notes.append(f"{name} at height {y - GROUND}: {why}")
    if len(decor) > 3:
        notes.append(f"{len(decor) - 3} more like that")
    high_one_way = [c for c in one_way if c[1] > GROUND]
    if high_one_way:
        notes.append(f"({len(high_one_way)} one-way spots at height {heights(high_one_way)}: a drop builders can't climb back)")
    return sid, lv, v, len(level.blocks), len(remaining), "; ".join(notes)


def main(argv):
    tsv = None
    markdown = False
    ids = []
    args = list(argv[1:])
    while args:
        a = args.pop(0)
        if a == "--tsv":
            tsv = args.pop(0)
        elif a == "--markdown":
            markdown = True
        elif a in ("-h", "--help"):
            print(__doc__)
            return 0
        else:
            ids.append(a.strip(",").removeprefix("csr:"))
    jobs = [(sid, lv) for sid, levels in entries() for lv in range(1, levels + 1)
            if not ids or sid in ids or f"{sid}:{lv}" in ids]
    if not jobs:
        print("no structure matches " + ", ".join(ids), file=sys.stderr)
        return 2
    # The biggest levels first, spread over every core.
    order = sorted(jobs, key=lambda j: -os.path.getsize(level_file(*j)) if os.path.exists(level_file(*j)) else 0)
    with multiprocessing.Pool() as pool:
        found = {(r[0], r[1]): r for r in pool.imap_unordered(check_level, order)}
    rows = [found[j] for j in jobs]
    if tsv:
        with open(tsv, "w", encoding="utf-8") as f:
            f.write("id\tlevel\tverdict\tblocks\tleft\tnotes\n")
            for r in rows:
                f.write("\t".join(str(c) for c in r) + "\n")
    counts = {}
    for r in rows:
        counts[r[2]] = counts.get(r[2], 0) + 1
    summary = ", ".join(f"{counts[k]} {k}" for k in VERDICTS if k in counts)
    if markdown:
        print("## Buildability check (static model, tools/buildability.py)\n")
        print(f"{len(rows)} levels: {summary}. STUCK: blocks no builder can reach; RISKY: builders can get stuck or fall; "
              "DECOR: finishes, but blocks drop or change; SCAFFOLD: needs scaffold columns.\n")
        print("| Structure | Level | Verdict | Blocks | Left | Notes |")
        print("|---|---|---|---|---|---|")
        for r in sorted(rows, key=lambda r: (VERDICTS.index(r[2]), r[0], r[1])):
            print(f"| {r[0]} | L{r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5].replace('|', '/')} |")
    else:
        for r in rows:
            print(f"{r[0]:20} L{r[1]}  {r[2]:9} {r[3]:5} blocks {r[4]:4} left  {r[5]}")
        print(f"{len(rows)} levels: {summary}")
    return 1 if counts.get("STUCK") or counts.get("NO FILE") else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
