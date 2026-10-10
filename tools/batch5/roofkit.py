"""Shared helpers for the batch-5 noble house and hospital (no structures of its own).

- hip_roof(s, cells, y0, ...): a 45-degree roof over any footprint (a rectangle gives a hip roof, a ring round a
  courtyard gives a cloister roof with hips at the outer corners and valleys at the inner ones). Each cell's level is
  its distance (8-way) to the footprint's edge; a cell at level L gets a stair at y0 + L facing uphill (outer hip
  corners and inner valleys get the matching corner shapes), the top cells a slab ridge. Level 0 (the eave row) uses
  `eave`, the rest `slope`. Positions already taken (a chimney, a dormer) are left alone.
- dormer(s, x, y, z, out, ...): a small gabled dormer whose window sits at (x, y, z) in the roof's wall line, facing
  `out`; build it BEFORE hip_roof so the roof fills in round it.
"""
from batch3.ws_kit import P, A, STAIR, TOP, SLAB

STRUCTURES = []   # helper module: nothing to build

DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
NAME = {v: k for k, v in DIRS.items()}
RIGHT = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}


def levels(cells):
    """8-way distance of every cell to the nearest cell outside the footprint, minus one (edge cells are 0)."""
    cells = set(cells)
    lv, frontier, n = {}, [], 0
    for x, z in cells:
        if any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            lv[(x, z)] = 0
            frontier.append((x, z))
    while frontier:
        n += 1
        nxt = []
        for x, z in frontier:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    c = (x + dx, z + dz)
                    if c in cells and c not in lv:
                        lv[c] = n
                        nxt.append(c)
        frontier = nxt
    return lv


def stair_for(H, x, z):
    """(facing, shape) of the roof stair at (x, z) of height map H, or None for a ridge cell (nothing higher round it)."""
    h = H[(x, z)]
    up = [d for d, (dx, dz) in DIRS.items() if H.get((x + dx, z + dz), -1) > h]
    diag = [(dx, dz) for dx in (-1, 1) for dz in (-1, 1) if H.get((x + dx, z + dz), -1) > h]
    if not up and not diag:
        return None
    if len(up) >= 2 and not ({'north', 'south'} <= set(up) or {'east', 'west'} <= set(up)):
        f, other = up[0], up[1]
        return f, ('inner_right' if RIGHT[f] == other else 'inner_left')
    if up:
        return up[0], 'straight'
    dx, dz = diag[0]
    f = NAME[(dx, 0)]
    return f, ('outer_right' if RIGHT[f] == NAME[(0, dz)] else 'outer_left')


def height_roof(s, H, eave_y, slope='spruce_stairs', eave='dark_oak_stairs', ridge='spruce_slab', skip=()):
    """A roof over the height map H ((x, z) -> y of the roof block there): stairs facing uphill (corner shapes at hips
    and valleys), a bottom slab where nothing round is higher (the ridge). Cells at eave_y use `eave`."""
    skip = set(skip)
    for (x, z), y in sorted(H.items()):
        if (x, y, z) in skip:
            continue
        st = stair_for(H, x, z)
        if st is None:
            A(s, x, y, z, ridge, **SLAB)
        else:
            A(s, x, y, z, eave if y == eave_y else slope, facing=st[0], half='bottom', shape=st[1], waterlogged='false')


def corbels(s, H, cells, block='spruce_stairs', peak='dark_oak_planks'):
    """Upside-down stairs under the roof stairs of an overhang row (cells), facing the other way; a plank under a
    ridge slab. Leaves the lowest row (the eave) alone."""
    low = min(H[c] for c in cells)
    for x, z in cells:
        y = H[(x, z)]
        if y == low:
            continue
        st = stair_for(H, x, z)
        if st is None:
            A(s, x, y - 1, z, peak)
        elif st[1] == 'straight':
            back = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[st[0]]
            A(s, x, y - 1, z, block, facing=back, **TOP)


def hip_roof(s, cells, y0, slope='spruce_stairs', eave='dark_oak_stairs', ridge='spruce_slab', skip=()):
    lv = levels(cells)
    height_roof(s, {c: y0 + L for c, L in lv.items()}, y0, slope, eave, ridge, skip)
    return lv


def dormer(s, x, y, z, out, cheek='dark_oak_log', gable='dark_oak_planks', roof='spruce_stairs',
           ridge='spruce_slab', window='glass_pane'):
    """A dormer: window at (x, y, z) between two cheek posts, a small gable roof one block above, reaching one block
    out (the overhang) and one block in (where it meets the main roof)."""
    ox, oz = DIRS[out]
    lx, lz = (1, 0) if ox == 0 else (0, 1)              # the lateral axis (along the wall)
    side_pos, side_neg = NAME[(lx, lz)], NAME[(-lx, -lz)]
    pane = dict(north=False, south=False, east=False, west=False, waterlogged='false')
    pane.update({side_pos: True, side_neg: True})
    P(s, x, y, z, window, **pane)
    for v in (-1, 1):
        P(s, x + lx * v, y, z + lz * v, cheek, axis='y')
    for u in (-1, 0, 1):                                  # u: -1 inward, 0 the face, 1 the overhang
        cx, cz = x + ox * u, z + oz * u
        for v in (-1, 1):
            # the stair at lateral offset v climbs toward the middle: it faces -v
            f = side_neg if v > 0 else side_pos
            P(s, cx + lx * v, y + 1, cz + lz * v, roof, facing=f, **STAIR)
        P(s, cx, y + 1, cz, gable)
        P(s, cx, y + 2, cz, ridge, **SLAB)


SWAP = {'east': 'west', 'west': 'east', 'left': 'right', 'right': 'left',
        'inner_left': 'inner_right', 'inner_right': 'inner_left', 'outer_left': 'outer_right', 'outer_right': 'outer_left'}


def mirror_x(s):
    """Mirrors a finished structure left to right (x -> width - 1 - x): facings, hinges, stair shapes, east/west
    connections and banner rotations follow. Run it before connect()."""
    w = s.size[0]
    blocks, layers = {}, {}
    for (x, y, z), (name, props, level) in s.blocks.items():
        p = {}
        for k, v in props.items():
            if k in ('facing', 'hinge', 'shape'):
                v = SWAP.get(v, v)
            if k == 'rotation':
                v = str((16 - int(v)) % 16)
            p[SWAP.get(k, k) if k in ('east', 'west') else k] = v
        blocks[(w - 1 - x, y, z)] = (name, p, level)
        layers[(w - 1 - x, y, z)] = s.layer_of[(x, y, z)]
    s.blocks, s.layer_of = blocks, layers
    for lv in s.decorations.values():
        for d in lv:
            d['pos'][0] = w - 1 - d['pos'][0]
    return s
