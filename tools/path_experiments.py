#!/usr/bin/env python3
"""Offline comparison of builder pathfinding techniques (experiment, not part of CI).

  python3 tools/path_experiments.py keep:1 town_hall:1 chapel:2     levels to compare (id:level, or id for all levels)
  python3 tools/path_experiments.py --all                           every structure level (slow: hours for keep L3)

Replays a structure build in the test area of StructureBuildGameTests with 3 builders, on the walking model of
tools/buildability.py (ScaffoldGrid's rules), and swaps only HOW a builder finds out whether a block is reachable and
how it gets there:

  vanilla   what the mod does today (BuilderReach.find): per block in hand-out order, vanilla A* (weighted heuristic
            1.5, node budget follow range * 16 = 256, range 16) towards the block with accuracy 2 then 1, then one
            multi-target A* to every standing spot (budget 1024 nodes, range 64, D61); a block found unreachable is
            skipped by that builder for 200 ticks; the path is searched once more when the builder sets off
            (moveToRequest). The budget and range are what make vanilla say "unreachable" for a block that IS reachable.
  vanilla3  the same with 3 times the long search's budget (draft PR #61).
  field     one Dijkstra flood from the chest over every standing spot (walked limit 64, no node budget), kept until
            the world changes; a block is reachable when one of its standing spots is in the flood, and the path to it
            is read off the flood (no extra search).
  planned   field, plus a build order planned before the build starts ("assembly by disassembly": take the finished
            structure apart one block at a time, always a block a builder could place from a spot reachable WITHOUT
            it, and with a way back to the chest; the build order is that sequence reversed).

Scaffold columns go up as in buildability.py, plus inner columns on the structure's own floors (D69, D75) when outer
ones fall short. Measured per level: blocks placed, simulated ticks to finish (walking at 4.3 ticks a block plus 40
ticks a trip), path-search work (nodes expanded, a CPU proxy), searches that said "unreachable" for a block the flood
reaches (false negatives), and blocks placed from a spot with no way back.
It is a model, not the game: same caveats as buildability.py.
"""
import heapq
import itertools
import math
import multiprocessing
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import buildability as B  # noqa: E402

FOLLOW = 16
SHORT_BUDGET = FOLLOW * 16            # vanilla maxVisitedNodes for the navigation's own createPath
LONG_BUDGET = FOLLOW * 16 * 4         # build.path_nodes_multiplier 4 (BuilderReach.longPath)
LONG_RANGE = FOLLOW + 48              # build.path_range_bonus
RECHECK = 200                          # build.unreachable_recheck_ticks
RETRY = 30                             # idle builder asks again (20-40)
TICKS_PER_BLOCK = 4.3                  # walking speed of a soldier, roughly
TRIP_OVERHEAD = 40                     # fetch from chest, turn, place
BUILDERS = 3
MAX_PATH_LIMIT = B.MAX_PATH
INNER_BASE_PATH = 16 + 48 - 8          # D75: column bases within 56 walked blocks


# ---------------------------------------------------------------------------------------------- vanilla A* emulation

def is_air(grid, x, y, z):
    return grid.at(x, y, z) == B.AIR


def ground_target(grid, t):
    """GroundPathNavigation.createPath(BlockPos): an air target moves down onto the first non-air block below it."""
    x, y, z = t
    if is_air(grid, x, y, z):
        y -= 1
        while y > 0 and is_air(grid, x, y, z):
            y -= 1
        return x, y + 1, z
    while y < B.AREA_Y and grid.solid(x, y, z):
        y += 1
    return x, y, z


def astar(grid, starts, targets, accuracy, budget, max_range):
    """Vanilla PathFinder.findPath: weighted A* (h * 1.5), stops at the first node within `accuracy` (Manhattan) of a
    target; nodes past `max_range` straight-line from the start aren't expanded, neighbours with a walked distance of
    `max_range` or more aren't opened. Returns (end node or None, nodes expanded)."""
    start = starts[0]

    def h(c):
        return min(math.dist(c, t) for t in targets) * 1.5

    g = {start: 0.0}
    walked = {start: 0.0}
    openq = [(h(start), start)]
    closed = set()
    expanded = 0
    while openq:
        _, c = heapq.heappop(openq)
        if c in closed:
            continue
        expanded += 1
        if expanded >= budget:
            return None, expanded
        closed.add(c)
        for t in targets:
            if abs(c[0] - t[0]) + abs(c[1] - t[1]) + abs(c[2] - t[2]) <= accuracy:
                return c, expanded
        if math.dist(c, start) >= max_range:
            continue
        for nx, ny, nz, _ in B.moves(grid, *c):
            n = (nx, ny, nz)
            if n in closed:
                continue
            step = math.dist(c, n)
            w = walked[c] + step
            ng = g[c] + step
            if w < max_range and ng < g.get(n, 1e18):
                g[n] = ng
                walked[n] = w
                heapq.heappush(openq, (ng + h(n), n))
    return None, expanded


def can_place_from(feet, target):
    dx, dz, dy = target[0] - feet[0], target[2] - feet[2], target[1] - feet[1]
    if dx == 0 and dz == 0 and dy == 0:
        return False
    return dx * dx + dz * dz < B.HORIZONTAL_REACH ** 2 and -B.MAX_PLACE_DEPTH <= dy <= B.MAX_PLACE_HEIGHT


def stand_spots(grid, target):
    tx, ty, tz = target
    out = []
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for feet in range(ty - B.MAX_PLACE_HEIGHT, ty + B.MAX_PLACE_DEPTH + 1):
                c = (tx + dx, feet, tz + dz)
                if c != target and grid.standable(*c) and can_place_from(c, target):
                    out.append(c)
    return out


def vanilla_find(grid, start, target, long_budget):
    """BuilderReach.find. Returns (end spot or None, nodes expanded)."""
    work = 0
    goal = ground_target(grid, target)
    for accuracy in (2, 1):
        end, n = astar(grid, [start], [goal], accuracy, SHORT_BUDGET, FOLLOW)
        work += n
        if end is not None and can_place_from(end, target):
            return end, work
    spots = stand_spots(grid, target)
    if not spots:
        return None, work
    end, n = astar(grid, [start], spots, 0, long_budget, LONG_RANGE)
    work += n
    if end is not None and can_place_from(end, target):
        return end, work
    return None, work


# ---------------------------------------------------------------------------------------------- scaffold, inner columns

def add_inner_columns(grid, level, remaining, final_cells):
    """Columns standing on the structure's own floors (D69, D75): any reachable spot whose column cells stay air in
    the finished structure."""
    spots = B.reachable(grid, INNER_BASE_PATH)
    top = max(b[1] for b in level.blocks)
    added = False
    taken = set()
    for (_, ty, tx, tz, _, _) in remaining:
        best = None
        for (cx, by, cz) in spots:
            dx, dz = cx - tx, cz - tz
            if dx * dx + dz * dz >= B.HORIZONTAL_REACH ** 2 or (cx, cz) in taken:
                continue
            need = max(by, ty - B.MAX_PLACE_HEIGHT)
            if need <= by and (cx, by, cz) != (tx, ty, tz):
                continue   # already reachable from the floor: not this one's problem
            cells = range(by, min(need, top) + 1)
            if all(grid.at(cx, y, cz) in (B.AIR, B.CLIMB) and (cx, y, cz) not in final_cells for y in cells):
                if best is None or need - by < best[0]:
                    best = (need - by, cx, cz, by, need)
        if best is not None:
            _, cx, cz, by, need = best
            for y in range(by, need + 1):
                if grid.at(cx, y, cz) == B.AIR:
                    grid.set((cx, y, cz), B.CLIMB)
                    added = True
            taken.add((cx, cz))
    return added


def add_scaffold(grid, level, remaining, final_cells):
    return B.add_columns(grid, level, remaining) or add_inner_columns(grid, level, remaining, final_cells)


# ---------------------------------------------------------------------------------------------- planned order

def plan_order(level):
    """Assembly by disassembly. Start from the finished structure (scaffold-free); repeatedly remove the block with
    the highest hand-out rank among those a builder could place from a spot reachable from the chest (and with a way
    back) in the world without it. Blocks that can't be removed that way are left for scaffold, appended in the
    normal order. Returns (order, blocks that needed a fallback, flood count)."""
    grid = B.Grid()
    for (_, y, x, z, name, kind) in level.blocks:
        grid.set((x, y, z), kind, B.thin_top(name))
    left = {(b[2], b[1], b[3]): b for b in level.blocks}
    rank = {(b[2], b[1], b[3]): i for i, b in enumerate(level.blocks)}
    removed = []
    floods = 0
    # Candidate set kept small: only a block whose neighbourhood changed needs a new test.
    while left:
        spots = B.reachable(grid)
        back = B.way_back(grid, spots)
        floods += 1
        # The last block in the normal order that can be taken out now (top-most first keeps the build bottom-up).
        best = None
        tried = 0
        for pos in sorted(left, key=lambda p: -rank[p]):
            x, y, z = pos
            kind = grid.at(x, y, z)
            grid.set(pos, B.AIR)
            # Quick test with the old flood; the winner is checked with a new one (removing a block can cut a path).
            local = stand_spots(grid, pos)
            if any(s in spots and s in back for s in local):
                tried += 1
                s2 = B.reachable(grid)
                b2 = B.way_back(grid, s2)
                floods += 1
                if any(s in s2 and s in b2 for s in local):
                    best = pos
            grid.set(pos, kind, B.thin_top(left[pos][4]))
            if best is not None or tried >= 8:
                break
        if best is None:
            break
        grid.set(best, B.AIR)
        removed.append(left.pop(best))
    order = list(reversed(removed))
    stuck = sorted(left.values())
    return stuck + order, len(stuck), floods



# ---------------------------------------------------------------------------------------------- incremental reach map

class ReachMap:
    """The flood from the chest, repaired locally when a block changes instead of recomputed (dynamic shortest paths:
    drop the part of the shortest-path tree whose step was affected, then relax again from its border)."""

    def __init__(self, grid):
        self.grid = grid
        self.dist = {}
        self.parent = {}
        self.children = {}
        self.work = 0
        self.full()

    def full(self):
        self.dist.clear()
        self.parent.clear()
        self.children.clear()
        heap = []
        for c in B.start_spots(self.grid):
            self.dist[c] = 0.0
            self.parent[c] = None
            heap.append((0.0, c))
        heapq.heapify(heap)
        self.relax(heap)

    def relax(self, heap):
        done = set()
        while heap:
            d, c = heapq.heappop(heap)
            if c in done or self.dist.get(c) != d:
                continue
            done.add(c)
            self.work += 1
            for nx, ny, nz, cost in B.moves(self.grid, *c):
                n = (nx, ny, nz)
                nd = d + cost
                if nd < MAX_PATH_LIMIT and nd < self.dist.get(n, 1e18):
                    old = self.parent.get(n)
                    if old is not None:
                        self.children[old].discard(n)
                    self.dist[n] = nd
                    self.parent[n] = c
                    self.children.setdefault(c, set()).add(n)
                    heapq.heappush(heap, (nd, n))

    def changed(self, p):
        """A block was placed or removed at p."""
        g = self.grid
        px, py, pz = p
        near = [(px + dx, py + dy, pz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in range(-4, 3)]
        bad = []
        for c in near:
            if c in self.dist:
                if not g.standable(*c):
                    bad.append(c)
                # a step out of c may be gone
                outs = {(m[0], m[1], m[2]) for m in B.moves(g, *c)}
                for child in list(self.children.get(c, ())):
                    if child not in outs:
                        bad.append(child)
        removed = set()
        stack = list(bad)
        while stack:
            c = stack.pop()
            if c in removed or c not in self.dist:
                continue
            removed.add(c)
            self.work += 1
            stack.extend(self.children.get(c, ()))
        for c in removed:
            par = self.parent.pop(c, None)
            if par is not None and par in self.children:
                self.children[par].discard(c)
            del self.dist[c]
            self.children.pop(c, None)
        # Border: every remaining node next to the removed set or to the change (new steps may exist there).
        seeds = set()
        for c in list(removed) + near:
            cx, cy, cz = c
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for dy in range(-4, 5):
                        n = (cx + dx, cy + dy, cz + dz)
                        if n in self.dist:
                            seeds.add(n)
        heap = [(self.dist[c], c) for c in seeds]
        heapq.heapify(heap)
        self.relax(heap)
        if not self.dist:
            self.full()

# ---------------------------------------------------------------------------------------------- the build

def simulate(level_path, mode):
    level = B.Level(level_path)
    blocks = list(level.blocks)
    final_cells = {(b[2], b[1], b[3]) for b in blocks}
    plan_info = ""
    if mode.startswith("planned"):
        blocks, fallback, floods = plan_order(level)
        plan_info = f"plan: {fallback} blocks without a plan, {floods} floods"
    grid = B.Grid()
    remaining = list(blocks)       # in hand-out order
    doors_last = [b for b in remaining if b[0] == 3]
    remaining = [b for b in remaining if b[0] != 3] + doors_last
    in_flight = {}                 # pos -> block
    start = min(B.start_spots(grid), key=lambda c: math.dist(c, B.CHEST))
    unreachable = [dict() for _ in range(BUILDERS)]   # per builder: pos -> until tick
    field = None
    field_version = -1
    version = 0
    stats = dict(work=0, searches=0, false_neg=0, trap=0, scaffold=0, placed=0)
    t = 0
    free_at = [0] * BUILDERS
    events = []                    # (tick, seq, builder, pos or None)
    seq = itertools.count()
    for i in range(BUILDERS):
        heapq.heappush(events, (i * 7, next(seq), i, None))
    idle_since = {}
    max_ticks = 2_000_000

    reach_map = ReachMap(grid) if mode in ("field_inc", "planned_inc") else None

    def flood():
        nonlocal field, field_version
        if reach_map is not None:
            return reach_map.dist
        if field_version != version:
            field = B.reachable(grid)
            stats["work"] += len(field)
            field_version = version
        return field

    while events and t < max_ticks:
        t, _, b, done = heapq.heappop(events)
        if done is not None:
            blk = in_flight.pop(done)
            grid.set(done, blk[5], B.thin_top(blk[4]))
            stats["placed"] += 1
            version += 1
            if reach_map is not None:
                reach_map.changed(done)
                if CHECK_INC and any(abs(reach_map.dist.get(k, -9) - v) > 1e-6 for k, v in B.reachable(grid).items()) or CHECK_INC and len(reach_map.dist) != len(B.reachable(grid)):
                    raise AssertionError(f"reach map out of step after {done}")
            for memory in unreachable:   # forgetUnreachableNear, radius 2
                for p in [p for p in memory if abs(p[0] - done[0]) <= 2 and abs(p[2] - done[2]) <= 2]:
                    del memory[p]
        if not remaining and not in_flight:
            break
        # Request search (ServerBlueprintPlan.getRequest).
        got = None
        doors_wait = any(r[0] != 3 for r in remaining) or any(r[0] != 3 for r in in_flight.values())
        for blk in remaining:
            ph, y, x, z, name, kind = blk
            pos = (x, y, z)
            if ph == 3 and doors_wait:
                continue
            if mode.startswith("vanilla"):
                if unreachable[b].get(pos, -1) > t:
                    continue
                end, work = vanilla_find(grid, start, pos, LONG_BUDGET * (3 if mode == "vanilla3" else 1))
                stats["work"] += work
                stats["searches"] += 1
                if end is None:
                    unreachable[b][pos] = t + RECHECK
                    if any(s in flood_cache(grid) for s in stand_spots(grid, pos)):
                        stats["false_neg"] += 1
                    continue
                _, work = vanilla_find(grid, start, pos, LONG_BUDGET * (3 if mode == "vanilla3" else 1))
                stats["work"] += work   # moveToRequest searches again
                dist = flood_cache(grid).get(end, path_len(end))
                got = (blk, end, dist)
                break
            f = flood()
            stats["searches"] += 1
            spots = [s for s in stand_spots(grid, pos) if s in f]
            if not spots:
                continue
            end = min(spots, key=lambda s: f[s])
            got = (blk, end, f[end])
            break
        if got is None:
            idle_since.setdefault(b, t)
            if len(idle_since) == BUILDERS and not in_flight:
                if not remaining:
                    break
                if not add_scaffold(grid, level, remaining, final_cells):
                    break   # stuck: nothing reachable, no column helps
                stats["scaffold"] += 1
                version += 1
                if reach_map is not None:
                    reach_map.full()
                for memory in unreachable:
                    memory.clear()
                idle_since.clear()
                for i in range(BUILDERS):
                    heapq.heappush(events, (t + 20 + i, next(seq), i, None))
                continue
            heapq.heappush(events, (t + RETRY, next(seq), b, None))
            continue
        idle_since.pop(b, None)
        blk, end, dist = got
        remaining.remove(blk)
        pos = (blk[2], blk[1], blk[3])
        in_flight[pos] = blk
        back = B.way_back(grid, flood_cache(grid))
        if end not in back:
            stats["trap"] += 1
        heapq.heappush(events, (t + int(2 * dist * TICKS_PER_BLOCK + TRIP_OVERHEAD), next(seq), b, pos))
    if reach_map is not None:
        stats["work"] = reach_map.work
    left = len(remaining) + len(in_flight)
    return dict(mode=mode, blocks=len(level.blocks), left=left, ticks=t, info=plan_info, **stats)


_flood_cache = {}


def flood_cache(grid):
    """Ground truth for statistics only (not counted as work): the flood for the grid's current contents."""
    key = (id(grid), bytes(grid.kind))
    hit = _flood_cache.get(id(grid))
    if hit is None or hit[0] != key:
        _flood_cache[id(grid)] = (key, B.reachable(grid))
    return _flood_cache[id(grid)][1]


def path_len(end):
    return math.dist(end, B.CHEST)


MODES = ("vanilla", "vanilla3", "field", "field_inc", "planned", "planned_inc")
CHECK_INC = os.environ.get("CHECK_INC") == "1"


def run(job):
    sid, lv, mode = job
    path = B.level_file(sid, lv)
    r = simulate(path, mode)
    return sid, lv, r


def main(argv):
    args = argv[1:]
    modes = MODES
    if "--modes" in args:
        i = args.index("--modes")
        modes = tuple(args[i + 1].split(","))
        del args[i:i + 2]
    entries = B.entries()
    jobs = []
    for sid, levels in entries:
        for lv in range(1, levels + 1):
            if "--all" in args or sid in args or f"{sid}:{lv}" in args:
                jobs += [(sid, lv, m) for m in modes]
    with multiprocessing.Pool() as pool:
        for sid, lv, r in pool.imap_unordered(run, jobs):
            print(f"{sid:12} L{lv} {r['mode']:9} placed {r['placed']:5}/{r['blocks']:5} left {r['left']:4} "
                  f"ticks {r['ticks']:8} work {r['work']:10} searches {r['searches']:7} false-unreachable "
                  f"{r['false_neg']:6} trap {r['trap']:4} scaffold-rounds {r['scaffold']:3} {r['info']}", flush=True)


if __name__ == "__main__":
    main(sys.argv)
