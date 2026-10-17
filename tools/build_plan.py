#!/usr/bin/env python3
"""Pre-baked build plans: a verified build order for every structure level, worked out from the block list alone.

  python3 tools/build_plan.py                  write the plan of every structure level (the ones StructureBuildGameTests builds)
  python3 tools/build_plan.py keep church      only these structure ids (keep:2 = only its level 2)
  python3 tools/build_plan.py --check          write nothing; fail if a plan file is missing or older than its structure
  python3 tools/build_plan.py --markdown       print the report as a Markdown table (for a CI run summary)
  python3 tools/build_plan.py --tsv out.tsv    also write one row per level

Like a shooter game's pre-baked navigation, the builders get their route worked out before the build starts instead of
searching for it live. Nothing is written by hand: the plan comes from the structure file, so a new building gets one
the moment it is generated or promoted (D82).

How ("assembly by disassembly"): start from the finished structure in the structure build test's layout (flat ground,
chest beside the easel; the walking and reach model of tools/buildability.py, i.e. the mod's own rules) and take it
apart one wave at a time. A block may leave in a wave when
  - a builder could place it from a standing spot that it walks to from the chest and back from, without standing on
    the block itself or on any other block leaving in the same wave;
  - nothing still standing rests on it (a torch on its wall, a lantern hanging from it, gravel on top of it, a door on
    its floor).
Blocks leave in the mod's own hand-out order, last first (ImmutableTemplate: full cubes and stairs, then other shapes,
then blocks with data, each bottom to top; doors last), and a wave keeps to one list and two layers, so the plan stays
close to that order and only changes it where it would fail. The build order is the waves reversed. Inside a wave the
order is free: every block of a wave can be placed whichever of the others are already there (the walks each one needs
only stand on blocks of earlier waves), which is what lets builders pick at random and spread out without leaving the
plan. Blocks the disassembly can't take out (above every standing spot: they need scaffold) are left out of the plan,
the plan is worked out again without them, and they are handed out after it, as today.

Verified: the build is replayed wave by wave from the bare ground; each wave's blocks must all be placeable before any
of them is there (the start of the wave) and their supports must be there. The blocks left out are then built with
buildability.py's scaffold columns to say whether the level finishes.

Each level gets a plan file next to the structure data, common/src/main/resources/data/csr/build_plan/<structure>.json:
  {"format": 1, "structure": "kingdom/studio/keep_l1", "source_sha1": "...", "size": [x, y, z],
   "waves": 120, "blocks": [[x, y, z, wave, spot, spot, ...], ...], "unplanned": [[x, y, z], ...]}
in build order, positions in the structure file's own coordinates. A spot is where a builder can stand to place the
block, packed as (dx + 2) + 5 * (dz + 2) + 25 * (dy + 8) with (dx, dy, dz) = feet - block (BuilderReach: dy -8..2); up to
4 per block, spread out. Spots outside the footprint assume flat ground; the mod checks every spot live and falls back to
its own search when none works.

A model, not the game (see buildability.py). Standard library only.
"""
import hashlib
import json
import multiprocessing
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import buildability as B  # noqa: E402

PLANS = os.path.join(B.ROOT, "common/src/main/resources/data/csr/build_plan/kingdom/studio")
FORMAT = 1
MAX_WAVE = 64          # blocks per wave
WAVE_LAYERS = 2        # layers per wave (keeps the build going up layer by layer)
MAX_SPOTS = 4          # standing spots kept per block
MAX_REPLANS = 6        # rounds of "plan again without the blocks left out"

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
STANDING = B.STAND_ON_TOP + ("rail", "pressure_plate", "button", "lever", "candle", "flower_pot", "bell", "snow")


def support(name, props, pos):
    """The cell a block rests on (it drops without it), or None. Gravel and sand rest on the block below."""
    x, y, z = pos
    facing = DIRS.get(props.get("facing"))
    if name.endswith(("wall_torch", "wall_sign", "wall_banner", "wall_hanging_sign", "_wall_head", "_wall_skull")) \
            or name in ("ladder", "tripwire_hook"):
        return (x - facing[0], y, z - facing[2]) if facing else None
    if name.endswith(("_button", "lever")) or name == "lever":
        face = props.get("face")
        if face == "ceiling":
            return x, y + 1, z
        if face == "wall" and facing:
            return x - facing[0], y, z - facing[2]
        return x, y - 1, z
    if props.get("hanging") == "true" or name.endswith("hanging_sign"):
        return x, y + 1, z
    if name == "bell":
        # A bell hangs from the block above (the chapel steeple's) or from its wall, or stands on the floor.
        attachment = props.get("attachment")
        if attachment == "ceiling":
            return x, y + 1, z
        if attachment in ("single_wall", "double_wall") and facing:
            return x + facing[0], y, z + facing[2]
        return x, y - 1, z
    if name == "vine":
        # A vine hanging under a block (garden L3's vines under azalea leaves) rests on it, else on a wall it covers.
        if props.get("up") == "true":
            return x, y + 1, z
        for side, (dx, _, dz) in DIRS.items():
            if props.get(side) == "true":
                return x + dx, y, z + dz
        return None
    if name == "scaffolding":
        # Scaffolding standing on the block below (stonemason L3's column) takes its state from it; placed first, it
        # hangs from its neighbour or drops.
        return x, y - 1, z
    if B.named(name, B.GRAVITY) or B.named(name, B.PLANTS) or B.named(name, STANDING) or name.endswith("_door"):
        return x, y - 1, z
    return None


def rank_phase(name, props, has_nbt):
    """ImmutableTemplate's lists: 0 full cubes and stairs (D69), 1 other shapes, 2 with data, 3 doors."""
    p = B.phase(name, props, has_nbt)
    return 0 if p == 1 and name.endswith("_stairs") else p


class Structure:
    """One structure level in the test area, as the mod's blueprint sees it (upper door halves and bed heads are
    placed with their other half, so they're not blocks of their own)."""

    def __init__(self, path):
        root = B.read_nbt(path)
        palette = root["palette"] if "palette" in root else root["palettes"][0]
        self.size = sx, sy, sz = root["size"]
        self.base = (B.EASEL[0] - sx // 2, B.GROUND, B.STRUCTURE_Z)
        states = [(e["Name"].split(":", 1)[-1], e.get("Properties", {})) for e in palette]
        self.cells = {}    # pos -> (kind, thin) of every structure cell, upper halves included
        self.blocks = {}   # pos -> (rank, name, props) of the blocks builders place
        order = []
        for block in root["blocks"]:
            name, props = states[block["state"]]
            if name in ("air", "cave_air", "void_air", "structure_void", "jigsaw") or B.decoration(name):
                continue
            x, y, z = block["pos"]
            pos = (self.base[0] + x, self.base[1] + y, self.base[2] + z)
            self.cells[pos] = (B.block_kind(name, props), B.thin_top(name))
            if props.get("half") == "upper" and name.endswith("_door") or props.get("part") == "head" and name.endswith("_bed"):
                continue
            order.append(((rank_phase(name, props, "nbt" in block), pos[1], pos[0], pos[2]), pos, name, props))
        order.sort()
        for i, (key, pos, name, props) in enumerate(order):
            self.blocks[pos] = (i, key[0], name, props)
        self.rel = lambda p: (p[0] - self.base[0], p[1] - self.base[1], p[2] - self.base[2])
        # The other half of a two-block block comes and goes with it.
        self.extra = {}
        for pos, (_, _, name, props) in self.blocks.items():
            if name.endswith("_door"):
                self.extra[pos] = [(pos[0], pos[1] + 1, pos[2])]
            elif name.endswith("_bed") and DIRS.get(props.get("facing")):
                d = DIRS[props["facing"]]
                self.extra[pos] = [(pos[0] + d[0], pos[1], pos[2] + d[2])]
        self.supports = {}
        for pos, (_, _, name, props) in self.blocks.items():
            s = support(name, props, pos)
            if s is not None and s in self.blocks:
                self.supports[pos] = s
        # Blocks that stay only while nothing next to them changes (a lantern hanging under leaves, which give it no
        # face to hang from; a plant on stone): the mod's normal order places them after everything around them, so
        # they are left out of the plan and come after it (garden L3: a lantern under azalea leaves dropped when a
        # fence of a later wave went in next to it).
        named = {pos: (name, props) for pos, (_, _, name, props) in self.blocks.items()}
        at = lambda p: named.get(p, ("grass_block", {}) if p[1] < self.base[1] else ("air", {}))
        self.fragile = set()
        for pos, (name, props) in named.items():
            above = at((pos[0], pos[1] + 1, pos[2]))[0]
            if props.get("hanging") == "true" and above.endswith("_leaves") \
                    or B.unsupported(name, props, pos, at):
                self.fragile.add(pos)

    def put(self, grid, pos):
        for cell in [pos] + self.extra.get(pos, []):
            if cell in self.cells:
                kind, thin = self.cells[cell]
                grid.set(cell, kind, thin)

    def take(self, grid, pos):
        for cell in [pos] + self.extra.get(pos, []):
            if cell in self.cells:
                grid.set(cell, B.AIR)


# ------------------------------------------------------------------------------------------------- walking with paths

def moves(grid, x, y, z):
    """buildability.py's moves plus a 1-block step down that mirrors its step up. Its drops need head room 2 above
    the lower cell, which a tiny soldier doesn't: without this a spiral stair is a way up with no way back."""
    seen = set()
    for move in B.moves(grid, x, y, z):
        seen.add(move[:3])
        yield move
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if (nx, y - 1, nz) not in seen and not grid.solid(nx, y, nz) and grid.standable(nx, y - 1, nz):
            yield nx, y - 1, nz, 1.4142


def flood(grid):
    """Standing spots builders walk to from the chest, with the step each was reached by: {cell: previous}."""
    best = {}
    prev = {}
    queue = []
    for cell in B.start_spots(grid):
        best[cell] = 0.0
        prev[cell] = None
        queue.append((0.0, cell))
    import heapq
    heapq.heapify(queue)
    done = {}
    while queue:
        d, cell = heapq.heappop(queue)
        if cell in done:
            continue
        done[cell] = prev[cell]
        for nx, ny, nz, cost in moves(grid, *cell):
            nd = d + cost
            n = (nx, ny, nz)
            if nd < B.MAX_PATH and nd < best.get(n, 1e9):
                best[n] = nd
                prev[n] = cell
                heapq.heappush(queue, (nd, n))
    return done


def flood_back(grid, spots):
    """The spots with a way back to the chest, with the next step on it: {cell: next}."""
    nxt = {}
    incoming = {}
    for cell in spots:
        for nx, ny, nz, _ in moves(grid, *cell):
            n = (nx, ny, nz)
            if n in spots:
                incoming.setdefault(n, []).append(cell)
    todo = deque()
    for cell in B.start_spots(grid):
        if cell in spots:
            nxt[cell] = None
            todo.append(cell)
    while todo:
        cell = todo.popleft()
        for p in incoming.get(cell, ()):
            if p not in nxt:
                nxt[p] = cell
                todo.append(p)
    return nxt


def floors(spot, forward, back):
    """The cells the walks there and back stand on (one below each step)."""
    out = set()
    c = spot
    while c is not None:
        out.add((c[0], c[1] - 1, c[2]))
        c = forward[c]
    c = spot
    while c is not None:
        out.add((c[0], c[1] - 1, c[2]))
        c = back[c]
    return out


def pack(spot, target):
    dx, dy, dz = spot[0] - target[0], spot[1] - target[1], spot[2] - target[2]
    return (dx + 2) + 5 * (dz + 2) + 25 * (dy + 8)


def spread(spots, target, keep):
    """Up to `keep` spots, spread round the block (different sides first), nearest-to-the-block first on a tie."""
    chosen = []
    for s in sorted(spots, key=lambda s: (abs(s[0] - target[0]) + abs(s[2] - target[2]), abs(s[1] - target[1]), s)):
        if len(chosen) >= keep:
            break
        if all(abs(s[0] - c[0]) + abs(s[2] - c[2]) + abs(s[1] - c[1]) >= 2 for c in chosen):
            chosen.append(s)
    return chosen


# ------------------------------------------------------------------------------------------------- disassembly

def disassemble(st, left_out):
    """Takes the structure (minus `left_out`) apart in waves. Returns (waves in build order: [[(pos, spots)]],
    blocks it couldn't take out)."""
    grid = B.Grid()
    present = {p for p in st.blocks if p not in left_out}
    for p in present:
        st.put(grid, p)
    resting = {}   # pos -> how many present blocks rest on it
    for p, s in st.supports.items():
        if p in present and s in present:
            resting[s] = resting.get(s, 0) + 1
    by_rank = sorted(present, key=lambda p: -st.blocks[p][0])
    waves = []
    while present:
        forward = flood(grid)
        back = flood_back(grid, forward)
        wave = {}            # pos -> chosen spot
        used = set()         # floors the chosen walks stand on
        window = None
        for p in by_rank:
            if p not in present or resting.get(p):
                continue
            rank, ph, _, _ = st.blocks[p]
            if window is not None and (ph != window[0] or p[1] < window[1] - WAVE_LAYERS + 1):
                continue
            if p in used or any(c in used for c in st.extra.get(p, [])):
                continue
            gone = set(wave) | {p}
            for w in list(wave):
                gone.update(st.extra.get(w, []))
            gone.update(st.extra.get(p, []))
            best = None
            for s in sorted(B.place_spots(p, back), key=lambda s: forward_len(s, forward)):
                if s in gone:
                    continue
                f = floors(s, forward, back)
                if not (f & gone):
                    best = (s, f)
                    break
            if best is None:
                continue
            wave[p] = best[0]
            used |= best[1]
            if window is None:
                window = (ph, p[1])
            if len(wave) >= MAX_WAVE:
                break
        if not wave:
            break
        gone = set(wave)
        for w in wave:
            gone.update(st.extra.get(w, []))
        entries = []
        for p in wave:
            # Every spot whose walk keeps off this wave's blocks is as good as the chosen one.
            ok = [s for s in B.place_spots(p, back) if s not in gone and not (floors(s, forward, back) & gone)]
            entries.append((p, spread(ok, p, MAX_SPOTS)))
        for p in wave:
            present.discard(p)
            st.take(grid, p)
            s = st.supports.get(p)
            if s is not None and s in resting:
                resting[s] -= 1
        waves.append(sorted(entries, key=lambda e: st.blocks[e[0]][0]))
    waves.reverse()
    return waves, present


def forward_len(spot, forward):
    n = 0
    c = forward[spot]
    while c is not None:
        n += 1
        c = forward[c]
    return n


def verify(st, waves, left_out):
    """Replays the plan from the bare ground; returns the problems found (empty when the plan holds)."""
    grid = B.Grid()
    placed = set()
    problems = []
    for i, wave in enumerate(waves):
        forward = flood(grid)
        back = flood_back(grid, forward)
        cells = {p for p, _ in wave}
        for p, spots in wave:
            if not any(s in back for s in B.place_spots(p, back) if s not in cells):
                problems.append(f"wave {i}: no standing spot for {st.blocks[p][2]} at {st.rel(p)}")
            s = st.supports.get(p)
            if s is not None and s not in placed and s not in left_out:
                problems.append(f"wave {i}: {st.blocks[p][2]} at {st.rel(p)} placed before what it rests on")
            if not spots:
                problems.append(f"wave {i}: no spot kept for {st.blocks[p][2]} at {st.rel(p)}")
        for p, _ in wave:
            st.put(grid, p)
            placed.add(p)
    return problems, grid


def finish_with_scaffold(st, grid, left_out):
    """Builds the blocks left out of the plan the way buildability.py does (reachable first, scaffold columns when
    nothing is): returns (blocks still left, scaffold used)."""
    remaining = sorted(left_out, key=lambda p: st.blocks[p][0])
    level = type("L", (), {})()
    level.columns = {(p[0], p[2]) for p in st.blocks}
    level.blocks = [(0, p[1], p[0], p[2], st.blocks[p][2], st.cells[p][0]) for p in st.blocks]
    rows = [(0, p[1], p[0], p[2], st.blocks[p][2], st.cells[p][0]) for p in remaining]
    scaffold = False
    for _ in range(400):
        if not rows:
            break
        spots = B.reachable(grid)
        left = []
        for row in rows:
            p = (row[2], row[1], row[3])
            if B.place_spots(p, spots):
                st.put(grid, p)
            else:
                left.append(row)
        if len(left) == len(rows):
            if not B.add_columns(grid, level, left):
                break
            scaffold = True
        rows = left
    return [(r[2], r[1], r[3]) for r in rows], scaffold


def sha1(path):
    with open(path, "rb") as f:
        return hashlib.sha1(f.read()).hexdigest()


def plan_level(job):
    """One level: (id, level, verdict, blocks, planned, waves, left out, notes, plan dict or None)."""
    sid, lv = job
    path = B.level_file(sid, lv)
    if not os.path.exists(path):
        return sid, lv, "NO FILE", 0, 0, 0, 0, path, None
    st = Structure(path)
    left_out = set(st.fragile)
    for _ in range(MAX_REPLANS):
        waves, stuck = disassemble(st, left_out)
        if not stuck:
            break
        left_out |= stuck
    else:
        waves, stuck = disassemble(st, left_out)
        left_out |= stuck
    problems, grid = verify(st, waves, left_out)
    remaining, scaffold = finish_with_scaffold(st, grid, left_out)
    planned = sum(len(w) for w in waves)
    if problems:
        verdict = "BROKEN"
    elif remaining:
        verdict = "STUCK"
    elif left_out:
        verdict = "SCAFFOLD" if scaffold else "PLAN+LIVE"
    else:
        verdict = "PLANNED"
    notes = problems[:3]
    if left_out:
        ys = sorted({p[1] - B.GROUND for p in left_out})
        notes.append(f"{len(left_out)} blocks left out of the plan at height {ys[0]}-{ys[-1]}")
    if remaining:
        notes.append(f"{len(remaining)} never placed, even with scaffold")
    structure = os.path.relpath(path, os.path.join(B.ROOT, "common/src/main/resources/data/csr/structure"))[:-4]
    plan = {
        "format": FORMAT,
        "structure": structure.replace(os.sep, "/"),
        "source_sha1": sha1(path),
        "size": list(st.size),
        "waves": len(waves),
        "blocks": [list(st.rel(p)) + [i] + [pack(s, p) for s in spots] for i, wave in enumerate(waves) for p, spots in wave],
        "unplanned": [list(st.rel(p)) for p in sorted(left_out, key=lambda p: st.blocks[p][0])],
    }
    return sid, lv, verdict, len(st.blocks), planned, len(waves), len(left_out), "; ".join(notes), plan


def plan_file(plan):
    return os.path.join(PLANS, os.path.basename(plan["structure"]) + ".json")


def write(plan):
    os.makedirs(PLANS, exist_ok=True)
    blocks = ",\n".join("    " + json.dumps(b, separators=(",", ":")) for b in plan["blocks"])
    head = {k: v for k, v in plan.items() if k not in ("blocks", "unplanned")}
    text = json.dumps(head, separators=(", ", ": "))[:-1]
    text += ',\n  "blocks": [\n' + blocks + '\n  ],\n  "unplanned": ' + json.dumps(plan["unplanned"], separators=(",", ":")) + "\n}\n"
    with open(plan_file(plan), "w", encoding="utf-8") as f:
        f.write(text)


def stale(sid, lv):
    """Why the level's plan file is missing or out of date, or None."""
    path = B.level_file(sid, lv)
    if not os.path.exists(path):
        return None
    target = os.path.join(PLANS, os.path.basename(path)[:-4] + ".json")
    try:
        with open(target, encoding="utf-8") as f:
            plan = json.load(f)
    except (OSError, ValueError):
        return "no plan file"
    if plan.get("format") != FORMAT:
        return "old plan format"
    if plan.get("source_sha1") != sha1(path):
        return "structure changed since its plan was made"
    return None


VERDICTS = ("BROKEN", "STUCK", "NO FILE", "SCAFFOLD", "PLAN+LIVE", "PLANNED")


def main(argv):
    tsv = None
    markdown = check = False
    ids = []
    args = list(argv[1:])
    while args:
        a = args.pop(0)
        if a == "--tsv":
            tsv = args.pop(0)
        elif a == "--markdown":
            markdown = True
        elif a == "--check":
            check = True
        elif a in ("-h", "--help"):
            print(__doc__)
            return 0
        else:
            ids.append(a.strip(",").removeprefix("csr:"))
    listed = dict(B.entries())
    for sid in {i.split(":")[0] for i in ids} - set(listed):
        # Just promoted, not in StructureBuildGameTests.STRUCTURES yet: its levels from its metadata.
        try:
            with open(os.path.join(B.META, sid + ".json"), encoding="utf-8") as f:
                listed[sid] = len(json.load(f)["levels"])
        except (OSError, KeyError, ValueError):
            pass
    jobs = [(sid, lv) for sid, levels in listed.items() for lv in range(1, levels + 1)
            if not ids or sid in ids or f"{sid}:{lv}" in ids]
    if not jobs:
        print("no structure matches " + ", ".join(ids), file=sys.stderr)
        return 2
    if check:
        bad = [(sid, lv, why) for sid, lv in jobs for why in [stale(sid, lv)] if why]
        for sid, lv, why in bad:
            print(f"{sid} L{lv}: {why}")
        if bad:
            print(f"{len(bad)} of {len(jobs)} build plans missing or out of date: run python3 tools/build_plan.py")
            return 1
        print(f"all {len(jobs)} build plans up to date")
        return 0
    order = sorted(jobs, key=lambda j: -os.path.getsize(B.level_file(*j)) if os.path.exists(B.level_file(*j)) else 0)
    with multiprocessing.Pool() as pool:
        found = {}
        for r in pool.imap_unordered(plan_level, order):
            found[(r[0], r[1])] = r
            if r[8] is not None:
                write(r[8])
    rows = [found[j][:8] for j in jobs]
    if tsv:
        with open(tsv, "w", encoding="utf-8") as f:
            f.write("id\tlevel\tverdict\tblocks\tplanned\twaves\tleft_out\tnotes\n")
            for r in rows:
                f.write("\t".join(str(c) for c in r) + "\n")
    counts = {}
    for r in rows:
        counts[r[2]] = counts.get(r[2], 0) + 1
    summary = ", ".join(f"{counts[k]} {k}" for k in VERDICTS if k in counts)
    if markdown:
        print("## Build plans (tools/build_plan.py)\n")
        print(f"{len(rows)} levels: {summary}. PLANNED: every block in a verified plan; PLAN+LIVE / SCAFFOLD: the rest "
              "builds after the plan (with scaffold columns for SCAFFOLD); STUCK: blocks no builder reaches; BROKEN: the "
              "plan failed its own check.\n")
        print("| Structure | Level | Verdict | Blocks | Planned | Waves | Left out | Notes |")
        print("|---|---|---|---|---|---|---|---|")
        for r in sorted(rows, key=lambda r: (VERDICTS.index(r[2]), r[0], r[1])):
            print(f"| {r[0]} | L{r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} | {r[6]} | {r[7].replace('|', '/')} |")
    else:
        for r in rows:
            print(f"{r[0]:20} L{r[1]}  {r[2]:9} {r[3]:5} blocks {r[4]:5} planned in {r[5]:4} waves {r[6]:4} left out  {r[7]}")
        print(f"{len(rows)} levels: {summary}")
    return 1 if counts.get("BROKEN") or counts.get("NO FILE") else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
