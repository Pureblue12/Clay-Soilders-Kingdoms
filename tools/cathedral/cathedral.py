"""Cathedral (tier 8, look A, the town's showpiece landmark; docs/STRUCTURES.md, "Cathedral", variant A with a crossing
spire): a Gothic cross-plan cathedral with twin west towers, three west portals, a rose window over the great portal,
a tall nave with lower side aisles, a transept with a rose in each gable, a crossing tower carrying the spire (capped
at 28, D57), a choir with an apse at the east end and two side chapels.

Plan (25 wide x 48 deep x 28 tall, front z = 0, symmetric about x = 12):
- Parvis z 0..1 (paved forecourt), the great portal's porch z 2..3.
- West towers x 2..8 and 16..22, z 4..10, walls to y 19 with a cornice and a parapet; a side portal in each, belfry
  openings at y 14..16 over a belfry floor (y 13), the bell beams at y 18.
- Façade x 9..15 at z 4 between the towers: the great portal (double doors and a centre post) under a pointed porch,
  the rose window above it (centre y 12, 7 across).
- Nave x 8..16, z 4..26: walls to y 12, an open arcade (pointed arches, bays of 4) into the aisles, clerestory lancets
  over the aisle roofs; one cross roof (dark oak shingles, stone eaves, ridge 18) over nave, transept and choir.
- Aisles x 5..7 and 17..19, z 10..26, walls to y 5 with lean-to roofs; stepped buttresses at x 4 / 20.
- Transept x 2..22, z 26..34: gables at x 2 and 22 with a door and a small rose each.
- Crossing tower x 8..16, z 26..34: four great pointed arches, walls to y 20 with paired lantern windows over the
  roofs, a steep spire (y 21..26) and the gold-look tip at y 27 (level 3).
- Choir x 8..16, z 34..43, arcades into choir aisles (z 34..42), the apse (z 44..45) with three tall windows;
  side chapels x 1..5, z 35..39 off the choir aisles.

Level 1 (shell and fittings; builder-tested blocks only, so grey stone bricks, dark oak roofs): every wall, roof, tower
and the spire, the rose and transept roses as open stone tracery, clear glass in the lancets, pews, choir stalls, the
high altar, the bishop's throne (cathedra), chests, the bell beams, torches.
Level 2 (consecrated): pale-stone flying buttresses with quartz pinnacles along nave and choir, deepslate spirelets on
both west towers, pinnacles round the crossing spire, stained glass in the three roses and the apse, two bells and
their ropes, a red runner, a pulpit, a font, lanterns on long chains, altars in the side chapels, banners on the
towers.
Level 3 (the pilgrimage cathedral): gold-look tips on the spire and spirelets, statues of saints on the parvis and the
porch, lamp posts, clipped yews and flower beds on the parvis, gargoyles, vines, banners in the nave, candles.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, HANG, STAND, LEAVES, connect, noise

W, H, D = 25, 28, 48
MID = 12
NX0, NX1 = 8, 16              # nave and choir walls
AX0 = 5                       # aisle outer wall (left; right = mirror)
BX0 = 4                       # buttress line (left)
NTOP, ATOP = 14, 5            # nave / aisle wall tops (nave ridge 20)
TX0, TX1, TZ0, TZ1, WTOP = 2, 8, 4, 10, 21     # left west tower (right = mirror), walls y 1..21
LTOP = 14                     # the towers' lower stage (the belfry stage above is inset)
FZ = 4                        # the façade's z
CZ0, CZ1 = 26, 34             # crossing (and transept walls z)
FX0, FX1, FZ0, FZ1 = 10, 14, 28, 32   # the lantern and spire over the crossing
SX, SZ = MID, 30
TRX0 = 2                      # left transept gable
QZ1 = 43                      # choir walls end (apse beyond)
NAVE_BAYS = (11, 15, 19, 23)  # first z of each 3-long nave bay (piers at 10, 14, 18, 22, 26)
CHOIR_BAYS = (35, 39)         # choir bays (piers at 34, 38, 42)
FLYERS = (14, 18, 22, 42)     # buttress z that carry flying buttresses (level 2)
CH_X0, CH_Z0, CH_Z1 = 1, 35, 39               # side chapel walls (left)
ROSE = (MID, 12)              # centre (x, y) of the great rose, on z = FZ (y 9..15)
PANE = dict(waterlogged='false')
FLIP = {'east': 'west', 'west': 'east', 'outer_left': 'outer_right', 'outer_right': 'outer_left',
        'inner_left': 'inner_right', 'inner_right': 'inner_left', 'left': 'right', 'right': 'left'}
DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def mirror_props(p):
    return {k: FLIP.get(v, v) if k in ('facing', 'shape', 'hinge') else v for k, v in p.items()}


def PM(s, x, y, z, name, **p):
    """Place a block and its mirror image across x = MID."""
    P(s, x, y, z, name, **p)
    if x != MID:
        P(s, 2 * MID - x, y, z, name, **mirror_props(p))


def AM(s, x, y, z, name, **p):
    A(s, x, y, z, name, **p)
    if x != MID:
        A(s, 2 * MID - x, y, z, name, **mirror_props(p))


def RM(s, x, y, z):
    s.remove(x, y, z)
    s.remove(2 * MID - x, y, z)


def stone(x, y, z):
    """The wall stone: a cobblestone plinth course, dressed stone bricks with a rubble band every fifth course."""
    return 'cobblestone' if y == 1 or y % 5 == 0 else 'stone_bricks'


def pane(s, x, y, z, along, color=None, mirror=True):
    """A glass pane in a wall running along 'x' or 'z'."""
    name = f'{color}_stained_glass_pane' if color else 'glass_pane'
    conn = dict(north='true', south='true', east='false', west='false') if along == 'z' else \
        dict(east='true', west='true', north='false', south='false')
    (PM if mirror else P)(s, x, y, z, name, **conn, **PANE)


def pointed(s, cells, y_spring, plane, top_wall=True):
    """A pointed arch head over an opening. cells: the open positions along the wall (in order), as (x, z); the arch
    narrows by one cell per side per layer from y_spring up, with upside-down stairs as the curved soffit."""
    n = len(cells)
    y, i = y_spring, 0
    while i < (n + 1) // 2:
        a, b = cells[i], cells[n - 1 - i]
        for (x, z), side in ((a, 0), (b, 1)):
            if (x, y, z) in s.blocks:
                s.remove(x, y, z)
            if a == b:
                continue
            if plane == 'x':                     # wall along x: the soffit stair's tall side toward the jamb
                f = 'west' if side == 0 else 'east'
            else:
                f = 'north' if side == 0 else 'south'
            P(s, x, y, z, 'stone_brick_stairs', facing=f, **TOP)
        for (x, z) in cells[i + 1:n - 1 - i]:
            if (x, y, z) in s.blocks:
                s.remove(x, y, z)
        y += 1
        i += 1


def cut(s, x, z, ys, mirror=True):
    for y in ys:
        if mirror:
            RM(s, x, y, z)
        else:
            s.remove(x, y, z)


def hang(s, x, y, z):
    """A lantern at y on a chain that runs up to the first block above it."""
    P(s, x, y, z, 'lantern', **HANG)
    y += 1
    while (x, y, z) not in s.blocks:
        P(s, x, y, z, 'chain', axis='y')
        y += 1


# ---------------------------------------------------------------- roofs

def heightmap_roof(s, cells, mask, top, skip=lambda x, z: False, gable_walls=(), slope='dark_oak_stairs',
                   ridge='dark_oak_planks', cap='spruce_slab', plate='stone_bricks', walls=(), eave='stone_brick_stairs'):
    """One roof over any plan: each cell's height is its straight-line distance to the edge of `mask` (so long plans get
    gables or hips, crossing plans get valleys). cells: the roof positions (x, z); mask(x, z): inside the roof for the
    distance (extend it past a gable end so that end is a gable, not a hip). gable_walls: wall cells filled up to the
    roof. walls: wall cells that get a plate under the roof. Overhang cells (distance 1) are `eave` stairs."""
    xs = [x for x, _ in cells]
    zs = [z for _, z in cells]
    box = (min(xs) - 3, max(xs) + 3, min(zs) - 3, max(zs) + 3)
    far = {}                                     # Chebyshev distance to the nearest cell outside the mask
    todo = []
    for x in range(box[0], box[1] + 1):
        for z in range(box[2], box[3] + 1):
            if not mask(x, z):
                far[(x, z)] = 0
                todo.append((x, z))
    while todo:
        nxt = []
        for (x, z) in todo:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    c = (x + dx, z + dz)
                    if c not in far and box[0] <= c[0] <= box[1] and box[2] <= c[1] <= box[3]:
                        far[c] = far[(x, z)] + 1
                        nxt.append(c)
        todo = nxt

    def rays(x, z):
        out = {}
        for d, (dx, dz) in DIRS.items():
            k = 1
            while mask(x + dx * k, z + dz * k) and k < 40:
                k += 1
            out[d] = k
        return out
    h = {c: top + far.get(c, 1) for c in cells}
    for (x, z), y in h.items():
        if skip(x, z):
            continue
        higher = [d for d, (dx, dz) in DIRS.items() if h.get((x + dx, z + dz), -1) > y]
        if not higher:
            A(s, x, y, z, ridge)
            if cap:
                A(s, x, y + 1, z, cap, **SLAB)
            continue
        block = eave if y == top + 1 else slope
        lower = [d for d, (dx, dz) in DIRS.items() if h.get((x + dx, z + dz), top) < y]
        ew = [OPP[d] for d in lower if d in ('east', 'west')]
        ns = [OPP[d] for d in lower if d in ('north', 'south')]
        if len(ew) == 1 and len(ns) == 1:          # a hip corner
            ew, ns = ew[0], ns[0]
            shape = ('outer_right' if ns == 'south' else 'outer_left') if ew == 'east' else \
                ('outer_left' if ns == 'south' else 'outer_right')
            A(s, x, y, z, block, facing=ew, half='bottom', shape=shape, waterlogged='false')
        elif len(ew) == 1 or len(ns) == 1:
            A(s, x, y, z, block, facing=(ew or ns)[0], **STAIR)
        else:                                       # no single low side: rise toward the highest neighbour
            f = max(higher, key=lambda d: h.get((x + DIRS[d][0], z + DIRS[d][1]), -1))
            A(s, x, y, z, block, facing=f, **STAIR)
    for (x, z) in gable_walls:
        for y in range(top + 1, h[(x, z)]):
            A(s, x, y, z, 'stone_bricks')
    for (x, z) in walls:
        if (x, z) in h and h[(x, z)] > top + 1:
            A(s, x, top + 1, z, plate)
    return h


def lean_to(s, z0, z1, x_out=BX0, wall=AX0, top=ATOP, x_in=NX0):
    """A lean-to aisle roof against the nave wall (left side; mirrored), stone eave at x_out."""
    for z in range(z0, z1 + 1):
        AM(s, x_out, top + 1, z, 'stone_brick_stairs', facing='east', **STAIR)
        AM(s, wall, top + 1, z, 'stone_bricks')
        for i, x in enumerate(range(wall, x_in)):
            AM(s, x, top + 2 + i, z, 'dark_oak_stairs', facing='east', **STAIR)


def ring_stairs(s, x0, x1, z0, z1, y, block, corners=True, fill=None):
    """A square ring of stairs rising toward the middle (outer corners turned); fill: block for the inside."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                if fill:
                    P(s, x, y, z, fill)
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            if corner and not corners:
                continue
            if corner:
                f = 'east' if x == x0 else 'west'
                shape = ('outer_right' if z == z0 else 'outer_left') if x == x0 else \
                    ('outer_left' if z == z0 else 'outer_right')
                P(s, x, y, z, block, facing=f, half='bottom', shape=shape, waterlogged='false')
            else:
                f = 'east' if x == x0 else 'west' if x == x1 else 'south' if z == z0 else 'north'
                P(s, x, y, z, block, facing=f, **STAIR)


def ring(s, x0, x1, z0, z1, y, block, corners=True, **p):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (corners or not (x in (x0, x1) and z in (z0, z1))):
                P(s, x, y, z, block, **p)


def cornice(s, x0, x1, z0, z1, y, faces=('n', 's', 'w', 'e'), block='stone_brick_stairs'):
    """Upside-down stairs all round a block of walls, one block out (a cornice or string course)."""
    if 'n' in faces:
        for x in range(x0, x1 + 1):
            A(s, x, y, z0 - 1, block, facing='south', **TOP)
    if 's' in faces:
        for x in range(x0, x1 + 1):
            A(s, x, y, z1 + 1, block, facing='north', **TOP)
    if 'w' in faces:
        for z in range(z0, z1 + 1):
            A(s, x0 - 1, y, z, block, facing='east', **TOP)
    if 'e' in faces:
        for z in range(z0, z1 + 1):
            A(s, x1 + 1, y, z, block, facing='west', **TOP)


def rose_cells(r):
    """The rose window, as (dx, dy) -> 'stone' (the boss and the spokes, all joined to the frame) or 'petal' (glass,
    level 2): eight petals between spokes along the axes and the diagonals."""
    out = {}
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            if dx * dx + dy * dy > r * r + 1:
                continue
            spoke = dx == 0 or dy == 0 or abs(dx) == abs(dy)
            out[(dx, dy)] = 'stone' if spoke else 'petal'
    return out


# ---------------------------------------------------------------- the building

def statue(s, x, z, y=1, plinth=True):
    """A saint: a carved plinth, a pale robed body and a head (level 3 props)."""
    if plinth:
        P(s, x, y, z, 'chiseled_stone_bricks')
        y += 1
    P(s, x, y, z, 'diorite_wall')
    P(s, x, y + 1, z, 'diorite_wall')
    P(s, x, y + 2, z, 'polished_diorite')


def pinnacle(s, x, y, z):
    """A pale pinnacle: calcite shaft, carved quartz, a white spike."""
    P(s, x, y, z, 'calcite')
    P(s, x, y + 1, z, 'chiseled_quartz_block')
    P(s, x, y + 2, z, 'end_rod', facing='up')


def cathedral():
    s = Structure('cathedral', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    tmx, tmz = (TX0 + TX1) // 2, (TZ0 + TZ1) // 2      # 5, 7: the left tower's middle

    # ------------------------------------------------ footprint and floor (y = 0)
    floor = set()
    for x in range(TX0, W - TX0):
        for z in range(TZ0, CZ1 + 1):
            if z <= TZ1 or NX0 - 3 <= x <= NX1 + 3 or z >= CZ0:
                floor.add((x, z))
    for x in range(AX0, W - AX0):
        for z in range(CZ1, 43):
            floor.add((x, z))
    for x in range(NX0, NX1 + 1):
        floor.add((x, 43))
    for x in range(NX0 + 1, NX1):
        floor.add((x, 44))
    for x in range(NX0 + 2, NX1 - 1):
        floor.add((x, 45))
    for x in range(CH_X0, W - CH_X0):
        for z in range(CH_Z0, CH_Z1 + 1):
            if x <= AX0 or x >= W - 1 - AX0:
                floor.add((x, z))
    for x in range(W):                                   # the parvis (forecourt) across the whole front
        for z in range(0, FZ):
            floor.add((x, z))
    for (x, z) in floor:
        if NX0 < x < NX1 and FZ < z < 45:
            b = 'white_terracotta' if (x + z) % 2 else 'stone_bricks'
        elif z < FZ:
            b = 'stone_bricks' if NX0 <= x <= NX1 or z == FZ - 1 else \
                ('cobblestone' if noise(x, 0, z, 5) < 0.6 else 'mossy_cobblestone')
        else:
            b = 'cobblestone' if noise(x, 0, z, 3) < 0.5 else 'stone_bricks'
        P(s, x, 0, z, b)

    # ------------------------------------------------ west towers (left, mirrored): a square lower stage to y 14,
    # an inset belfry stage (5 x 5) to y 21 (level 2 puts a spirelet on it)
    BX, BZ = (TX0 + 1, TX1 - 1), (TZ0 + 1, TZ1 - 1)      # belfry walls x 3..7, z 5..9
    for y in range(1, LTOP + 1):
        for x in range(TX0, TX1 + 1):
            for z in range(TZ0, TZ1 + 1):
                if x in (TX0, TX1) or z in (TZ0, TZ1):
                    PM(s, x, y, z, stone(x, y, z))
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            PM(s, x, LTOP, z, 'stone_bricks')            # the lower stage's roof = the belfry floor
    for y in range(LTOP + 1, WTOP + 1):
        for x in range(BX[0], BX[1] + 1):
            for z in range(BZ[0], BZ[1] + 1):
                if x in BX or z in BZ:
                    PM(s, x, y, z, 'stone_bricks')
    for x in range(BX[0] + 1, BX[1]):
        for z in range(BZ[0] + 1, BZ[1]):
            PM(s, x, WTOP, z, 'stone_bricks')            # the belfry roof
    RM(s, tmx, LTOP, tmz)                                # the bell rope's hole
    cut(s, tmx, TZ0, (1, 2))                             # side portal under a pointed head
    PM(s, tmx - 1, 3, TZ0, 'stone_brick_stairs', facing='west', **TOP)
    PM(s, tmx + 1, 3, TZ0, 'stone_brick_stairs', facing='east', **TOP)
    cut(s, tmx, TZ0, range(8, 13))                       # tall lancets: front, outer side, back
    cut(s, TX0, tmz, range(8, 13))
    cut(s, tmx, TZ1, range(10, 13))
    cut(s, TX1, tmz, (1, 2))                             # into the narthex
    for y in range(LTOP + 2, WTOP - 1):                  # the belfry: paired openings on every face
        for x in (tmx - 1, tmx + 1):
            RM(s, x, y, BZ[0]); RM(s, x, y, BZ[1])
        for z in (tmz - 1, tmz + 1):
            RM(s, BX[0], y, z); RM(s, BX[1], y, z)
    for x in (tmx - 1, tmx + 1):                         # pointed heads over them (stairs in the wall)
        for z, f in ((BZ[0], 'south'), (BZ[1], 'north')):
            pass
    # string course (y 6), the ledge cornice (y 14) with a parapet, the belfry cornice (y 21)
    cornice(s, TX0, TX1, TZ0, TZ1, 6, faces=('n', 'w'))
    cornice(s, W - 1 - TX1, W - 1 - TX0, TZ0, TZ1, 6, faces=('n', 'e'))
    cornice(s, TX0, TX1, TZ0, TZ1, LTOP, faces=('n', 'w', 's'))
    cornice(s, W - 1 - TX1, W - 1 - TX0, TZ0, TZ1, LTOP, faces=('n', 'e', 's'))
    ring(s, TX0, TX1, TZ0, TZ1, LTOP + 1, 'cobblestone_wall', corners=False)
    ring(s, W - 1 - TX1, W - 1 - TX0, TZ0, TZ1, LTOP + 1, 'cobblestone_wall', corners=False)
    cornice(s, BX[0], BX[1], BZ[0], BZ[1], WTOP)
    cornice(s, W - 1 - BX[1], W - 1 - BX[0], BZ[0], BZ[1], WTOP)
    for cx, cz in ((BX[0] - 1, BZ[0] - 1), (BX[1] + 1, BZ[0] - 1), (BX[0] - 1, BZ[1] + 1), (BX[1] + 1, BZ[1] + 1)):
        PM(s, cx, WTOP, cz, 'stone_bricks')
    for x in (tmx - 1, tmx + 1):                         # hood moulds over the belfry openings
        AM(s, x, WTOP - 1, BZ[0] - 1, 'stone_brick_stairs', facing='south', **TOP)
        AM(s, x, WTOP - 1, BZ[1] + 1, 'stone_brick_stairs', facing='north', **TOP)
    for z in (tmz - 1, tmz + 1):
        AM(s, BX[0] - 1, WTOP - 1, z, 'stone_brick_stairs', facing='east', **TOP)
    # clasping buttresses at the tower corners
    for bx, bz, f in ((TX0, TZ0 - 1, 'south'), (TX1, TZ0 - 1, 'south'), (TX0 - 1, TZ0, 'east'),
                      (TX0 - 1, TZ1, 'east')):
        PM(s, bx, 0, bz, 'cobblestone')
        for y in range(1, LTOP - 1):
            PM(s, bx, y, bz, stone(bx, y, bz))
        PM(s, bx, LTOP - 1, bz, 'stone_brick_stairs', facing=f, **STAIR)
    for x in range(BX[0] + 1, BX[1]):                    # the bell beam
        PM(s, x, WTOP - 1, tmz, 'dark_oak_log', axis='x')

    # ------------------------------------------------ façade wall, the great portal and the rose
    for y in range(1, NTOP + 1):
        for x in range(NX0 + 1, NX1):
            P(s, x, y, FZ, stone(x, y, FZ))
    for x in (MID - 1, MID + 1):                         # double doors with a centre post
        s.remove(x, 1, FZ); s.remove(x, 2, FZ)
    P(s, MID, 1, FZ, 'stone_bricks'); P(s, MID, 2, FZ, 'stone_bricks')
    for z, j in ((3, 2), (2, 3)):                        # the porch: two receding pointed orders
        for x in range(MID - j, MID + j + 1):
            for y in range(1, 6):
                P(s, x, y, z, stone(x, y, z))
        cells = [(x, z) for x in range(MID - j + 1, MID + j)]
        for (x, _) in cells:
            for y in (1, 2, 3):
                s.remove(x, y, z)
        pointed(s, cells, 4, 'x')
    for z in (2, 3):                                     # the porch gable, peak just under the rose
        for y, x0, x1 in ((6, MID - 3, MID + 3), (7, MID - 2, MID + 2), (8, MID - 1, MID + 1)):
            for x in range(x0, x1 + 1):
                if z == 2 and x == x0:
                    P(s, x, y, z, 'stone_brick_stairs', facing='east', **STAIR)
                elif z == 2 and x == x1:
                    P(s, x, y, z, 'stone_brick_stairs', facing='west', **STAIR)
                elif z == 3 and x in (x0, x1):
                    continue
                else:
                    P(s, x, y, z, 'stone_bricks')
    for x in (MID - 3, MID + 3):                         # porch buttresses
        P(s, x, 0, 1, 'cobblestone')
        for y in range(1, 5):
            P(s, x, y, 1, stone(x, y, 1))
        P(s, x, 5, 1, 'stone_brick_stairs', facing='south', **STAIR)
    rose = rose_cells(3)

    # ------------------------------------------------ nave walls (arcade, triforium, clerestory) and aisles
    for y in range(1, NTOP + 1):
        for z in range(TZ1, CZ0 + 1):
            PM(s, NX0, y, z, stone(NX0, y, z))
    for z0 in NAVE_BAYS:
        cells = [(NX0, z) for z in range(z0, z0 + 3)]
        for (x, z) in cells:
            for y in range(1, 5):
                RM(s, x, y, z)
        pointed(s, cells, 5, 'z')
        pointed(s, [(W - 1 - NX0, z) for (_, z) in cells], 5, 'z')
        cut(s, NX0, z0 + 1, range(10, 14))               # clerestory lancet
    for y in range(1, ATOP + 1):
        for z in range(TZ1, CZ0 + 1):
            PM(s, AX0, y, z, stone(AX0, y, z))
    for z0 in NAVE_BAYS:
        cut(s, AX0, z0 + 1, (2, 3, 4))
    for z in (14, 18, 22):                               # stepped buttresses (level 2 lifts them into fliers)
        PM(s, BX0, 0, z, 'cobblestone')
        for y in range(1, ATOP + 2):
            PM(s, BX0, y, z, stone(BX0, y, z))
        PM(s, BX0 - 1, 0, z, 'cobblestone')
        for y in (1, 2, 3):
            PM(s, BX0 - 1, y, z, stone(BX0 - 1, y, z))
        PM(s, BX0 - 1, 4, z, 'stone_brick_stairs', facing='east', **STAIR)

    # ------------------------------------------------ transept
    for y in range(1, NTOP + 1):
        for x in range(TRX0, NX0 + 1):
            for z in (CZ0, CZ1):
                PM(s, x, y, z, stone(x, y, z))
        for z in range(CZ0, CZ1 + 1):
            PM(s, TRX0, y, z, stone(TRX0, y, z))
    for x in (AX0 + 1, AX0 + 2):                         # the aisles open into the transept
        for z in (CZ0, CZ1):
            cut(s, x, z, (1, 2, 3))
    cut(s, TRX0, SZ, (1, 2))                             # the transept door
    PM(s, TRX0, 3, SZ - 1, 'stone_brick_stairs', facing='north', **TOP)
    PM(s, TRX0, 3, SZ + 1, 'stone_brick_stairs', facing='south', **TOP)
    for (dx, dy), kind in rose_cells(2).items():         # the transept rose (glass in level 2)
        if kind != 'stone':
            RM(s, TRX0, 10 + dy, SZ + dx)
    cut(s, TRX0 + 1, CZ0, range(3, 12))                  # tall lancets in the transept walls
    cut(s, TRX0 + 1, CZ1, range(8, 12))
    for x, z, f in ((TRX0 - 1, CZ0, 'east'), (TRX0 - 1, CZ1, 'east'), (TRX0, CZ0 - 1, 'south')):
        PM(s, x, 0, z, 'cobblestone')                    # corner buttresses
        for y in range(1, 10):
            PM(s, x, y, z, stone(x, y, z))
        PM(s, x, 10, z, 'stone_brick_stairs', facing=f, **STAIR)

    # ------------------------------------------------ choir, apse, choir aisles, side chapels
    for y in range(1, NTOP + 1):
        for z in range(CZ1, QZ1 + 1):
            PM(s, NX0, y, z, stone(NX0, y, z))
        PM(s, NX0 + 1, y, 44, stone(NX0 + 1, y, 44))
        for x in range(NX0 + 2, MID + 1):
            PM(s, x, y, 45, stone(x, y, 45))
    for z0 in CHOIR_BAYS:
        cells = [(NX0, z) for z in range(z0, z0 + 3)]
        for (x, z) in cells:
            for y in range(1, 5):
                RM(s, x, y, z)
        pointed(s, cells, 5, 'z')
        pointed(s, [(W - 1 - NX0, z) for (_, z) in cells], 5, 'z')
        cut(s, NX0, z0 + 1, range(10, 14))
    for x in (MID - 2, MID, MID + 2):                    # the apse's three tall windows (glass in level 2)
        for y in range(3, 12):
            s.remove(x, y, 45)
    cut(s, NX0 + 1, 44, range(4, 11))                    # and one in each canted side
    for y in range(1, ATOP + 1):
        for z in range(CZ1, 43):
            PM(s, AX0, y, z, stone(AX0, y, z))
        for x in range(AX0, NX0):
            PM(s, x, y, 42, stone(x, y, 42))
    cut(s, AX0 + 1, 42, (2, 3, 4))
    for z in range(CH_Z0 + 1, CH_Z1):                    # arch into the side chapel
        cut(s, AX0, z, (1, 2))
    PM(s, AX0, 3, CH_Z0 + 1, 'stone_brick_stairs', facing='north', **TOP)
    PM(s, AX0, 3, CH_Z1 - 1, 'stone_brick_stairs', facing='south', **TOP)
    RM(s, AX0, 3, CH_Z0 + 2)
    PM(s, BX0, 0, 42, 'cobblestone')                     # the choir's corner buttress
    for y in range(1, ATOP + 2):
        PM(s, BX0, y, 42, stone(BX0, y, 42))
    for x, z, f in ((NX0, 44, 'east'), (NX0 + 1, 45, 'east')):     # apse buttresses
        PM(s, x, 0, z, 'cobblestone')
        for y in range(1, 11):
            PM(s, x, y, z, stone(x, y, z))
        PM(s, x, 11, z, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (MID - 1, MID + 1):
        P(s, x, 0, 46, 'cobblestone')
        for y in range(1, 11):
            P(s, x, y, 46, stone(x, y, 46))
        P(s, x, 11, 46, 'stone_brick_stairs', facing='north', **STAIR)
    for y in range(1, 5):
        for z in range(CH_Z0, CH_Z1 + 1):
            PM(s, CH_X0, y, z, stone(CH_X0, y, z))
        for x in range(CH_X0, AX0):
            PM(s, x, y, CH_Z0, stone(x, y, CH_Z0))
            PM(s, x, y, CH_Z1, stone(x, y, CH_Z1))
    cut(s, CH_X0, 37, (2, 3))

    # ------------------------------------------------ the lantern over the crossing and its spire
    for y in range(15, 22):
        ring(s, FX0, FX1, FZ0, FZ1, y, 'stone_bricks')
    for y in (18, 19, 20):
        for x, z in ((SX, FZ0), (SX, FZ1), (FX0, SZ), (FX1, SZ)):
            s.remove(x, y, z)
    cornice(s, FX0, FX1, FZ0, FZ1, 21)
    for cx, cz in ((FX0 - 1, FZ0 - 1), (FX1 + 1, FZ0 - 1), (FX0 - 1, FZ1 + 1), (FX1 + 1, FZ1 + 1)):
        P(s, cx, 21, cz, 'stone_bricks')
    ring_stairs(s, FX0, FX1, FZ0, FZ1, 22, 'dark_oak_stairs', corners=False, fill='dark_oak_planks')
    for x in range(SX - 1, SX + 2):
        for z in range(SZ - 1, SZ + 2):
            P(s, x, 23, z, 'dark_oak_planks')
    ring_stairs(s, SX - 1, SX + 1, SZ - 1, SZ + 1, 24, 'dark_oak_stairs', fill='dark_oak_planks')
    P(s, SX, 25, SZ, 'dark_oak_planks')
    P(s, SX, 26, SZ, 'dark_oak_fence') if False else P(s, SX, 26, SZ, 'dark_oak_planks')

    # ------------------------------------------------ roofs
    def in_plan(x, z):
        if NX0 <= x <= NX1 and FZ <= z <= QZ1:
            return True
        if TRX0 <= x <= W - 1 - TRX0 and CZ0 <= z <= CZ1:
            return True
        return z == 44 and NX0 + 1 <= x <= NX1 - 1 or z == 45 and NX0 + 2 <= x <= NX1 - 2
    cells = {(x + dx, z + dz) for x in range(W) for z in range(D) if in_plan(x, z)
             for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    cells = {(x, z) for (x, z) in cells if 0 <= x < W and 0 <= z < D}

    def mask(x, z):
        if (x, z) in cells:
            return True
        if NX0 - 1 <= x <= NX1 + 1 and QZ1 < z <= 46:
            return True                                   # the apse: a clean rectangular hip
        if z < FZ and NX0 - 1 <= x <= NX1 + 1:
            return True                                   # the façade gable
        return CZ0 - 1 <= z <= CZ1 + 1 and (x < TRX0 or x > W - 1 - TRX0)     # the transept gables

    def skip(x, z):
        tower = (TX0 <= x <= TX1 or W - 1 - TX1 <= x <= W - 1 - TX0) and TZ0 - 1 <= z <= TZ1
        lantern = FX0 <= x <= FX1 and FZ0 <= z <= FZ1
        facade = z <= FZ
        transept_ends = CZ0 - 1 <= z <= CZ1 + 1 and (x <= TRX0 or x >= W - 1 - TRX0)
        return tower or lantern or facade or transept_ends
    walls = [(x, z) for (x, z) in cells if in_plan(x, z) and (x, NTOP, z) in s.blocks and not skip(x, z)]
    h = heightmap_roof(s, cells, mask, NTOP, skip=skip, walls=walls)
    # stone gables standing proud of the roof, with a stepped coping and a finial
    copes = [(x, FZ, 'east' if x < MID else 'west' if x > MID else None) for x in range(NX0 + 1, NX1)]
    for gx in (TRX0, W - 1 - TRX0):
        copes += [(gx, z, 'south' if z < SZ else 'north' if z > SZ else None) for z in range(CZ0, CZ1 + 1)]
    for x, z, f in copes:
        for y in range(NTOP + 1, h[(x, z)] + 1):
            A(s, x, y, z, 'stone_bricks')
        if f:
            A(s, x, h[(x, z)] + 1, z, 'stone_brick_stairs', facing=f, **STAIR)
        else:
            A(s, x, h[(x, z)] + 1, z, 'stone_bricks')
            A(s, x, h[(x, z)] + 2, z, 'cobblestone_wall')
    for (dx, dy), kind in rose.items():                   # open the rose (tracery stays; glass in level 2)
        if kind != 'stone':
            s.remove(ROSE[0] + dx, ROSE[1] + dy, FZ)
    for x in range(NX0 + 1, NX1):                         # the band over the rose, the hood's corners
        A(s, x, ROSE[1] + 4, FZ - 1, 'stone_brick_stairs', facing='south', **TOP)
    for dx in (-3, 3):
        A(s, ROSE[0] + dx, ROSE[1] + 3, FZ - 1, 'stone_brick_stairs', facing='east' if dx < 0 else 'west', **TOP)
    cut(s, MID, FZ, (18,), mirror=False)                  # a small lancet high in the gable
    lean_to(s, TZ1 + 1, CZ0 - 1)
    lean_to(s, CZ1 + 1, 41)
    for z in (42, 43):                                    # the choir aisles' east ends
        AM(s, BX0, ATOP + 1, z, 'stone_brick_stairs', facing='east', **STAIR)
        for i, x in enumerate(range(AX0, NX0)):
            AM(s, x, ATOP + 2 + i, z, 'dark_oak_stairs', facing='east', **STAIR)
            if z == 42:
                for y in range(ATOP + 1, ATOP + 2 + i):
                    AM(s, x, y, z, 'stone_bricks')
    for x in range(CH_X0 + 1, AX0):                       # side chapels: a stone vault and a parapet
        for z in range(CH_Z0 + 1, CH_Z1):
            PM(s, x, 4, z, 'stone_bricks')
    for z in range(CH_Z0, CH_Z1 + 1):
        PM(s, CH_X0, 5, z, 'cobblestone_wall')
    for x in range(CH_X0 + 1, AX0):
        PM(s, x, 5, CH_Z0, 'cobblestone_wall')
        PM(s, x, 5, CH_Z1, 'cobblestone_wall')

    # ------------------------------------------------ fittings
    s.at(1, 'fittings')
    for z0 in NAVE_BAYS + CHOIR_BAYS:                     # clear glass: clerestory and aisles
        for y in range(10, 14):
            pane(s, NX0, y, z0 + 1, 'z')
    for z0 in NAVE_BAYS:
        for y in (2, 3, 4):
            pane(s, AX0, y, z0 + 1, 'z')
    for y in (2, 3, 4):
        pane(s, AX0 + 1, y, 42, 'x')
    for y in range(8, 13):
        pane(s, tmx, y, TZ0, 'x')
        pane(s, TX0, y, tmz, 'z')
    for y in range(10, 13):
        pane(s, tmx, y, TZ1, 'x')
    for y in range(3, 12):
        pane(s, TRX0 + 1, y, CZ0, 'x')
    for y in range(8, 12):
        pane(s, TRX0 + 1, y, CZ1, 'x')
    for y in range(4, 11):
        pane(s, NX0 + 1, y, 44, 'z')
    for y in (18, 19, 20):                                # the lantern's windows
        pane(s, SX, y, FZ0, 'x', mirror=False); pane(s, SX, y, FZ1, 'x', mirror=False)
        pane(s, FX0, y, SZ, 'z')
    pane(s, MID, 18, FZ, 'x', mirror=False)
    for y in (2, 3):
        pane(s, CH_X0, y, 37, 'z')
    for x in (MID - 2, MID, MID + 2):                     # inside shutters on the apse windows (until level 2)
        for y in range(3, 12):
            P(s, x, y, 44, 'spruce_trapdoor', facing='north', **OPEN)
    # doors
    s.door(MID - 1, 1, FZ, 'north', block='spruce_door', hinge='left')
    s.door(MID + 1, 1, FZ, 'north', block='spruce_door', hinge='right')
    s.door(tmx, 1, TZ0, 'north', block='spruce_door', hinge='left')
    s.door(W - 1 - tmx, 1, TZ0, 'north', block='spruce_door', hinge='right')
    s.door(TRX0, 1, SZ, 'west', block='spruce_door')
    s.door(W - 1 - TRX0, 1, SZ, 'east', block='spruce_door')
    # pews in two blocks with a centre aisle, facing the altar
    for z in range(12, 25, 2):
        for x in (MID - 3, MID - 2, MID - 1):
            PM(s, x, 1, z, 'spruce_stairs', facing='north', **STAIR)
    # choir stalls, the high altar, the bishop's throne (the cathedra)
    for z in range(35, 41):
        if z != 38:
            PM(s, NX0 + 1, 1, z, 'spruce_stairs', facing='west', **STAIR)
    for x in range(MID - 1, MID + 2):
        P(s, x, 1, 41, 'cobblestone_slab', **SLAB)
    P(s, MID - 1, 1, 42, 'stone_brick_stairs', facing='east', **TOP)
    P(s, MID, 1, 42, 'stone_bricks')
    P(s, MID + 1, 1, 42, 'stone_brick_stairs', facing='west', **TOP)
    P(s, MID, 2, 42, 'white_carpet')
    P(s, NX1 - 1, 1, 42, 'dark_oak_stairs', facing='east', **STAIR)
    P(s, NX1 - 1, 1, 41, 'spruce_trapdoor', facing='south', **OPEN)
    P(s, NX1 - 1, 1, 43, 'spruce_trapdoor', facing='north', **OPEN)
    P(s, NX1 - 1, 2, 42, 'red_carpet')
    PM(s, TRX0 + 1, 1, CZ1 - 1, 'chest', facing='east', type='single', waterlogged='false')     # sacristy
    PM(s, TRX0 + 1, 1, CZ1 - 2, 'barrel', facing='up', open='false')
    PM(s, TRX0 + 1, 1, CZ0 + 1, 'barrel', facing='up', open='false')
    for z in (14, 18, 22, 38):                            # torches
        PM(s, NX0 + 1, 3, z, 'wall_torch', facing='east')
    for z in (12, 16, 20, 24):
        PM(s, AX0 + 1, 3, z, 'wall_torch', facing='east')
    PM(s, TRX0 + 1, 4, SZ - 1, 'wall_torch', facing='east')
    PM(s, TRX0 + 1, 4, SZ + 1, 'wall_torch', facing='east')
    PM(s, TX0 + 1, 3, tmz, 'wall_torch', facing='east')
    PM(s, CH_X0 + 1, 3, 36, 'wall_torch', facing='east')

    # ------------------------------------------------ level 2: consecrated
    s.at(2, 'flourish2')
    for z in FLYERS:                                      # flying buttresses with pinnacles
        for y in range(ATOP + 2, 13):
            PM(s, BX0, y, z, 'calcite')
        PM(s, BX0, 13, z, 'chiseled_quartz_block')
        PM(s, BX0, 14, z, 'end_rod', facing='up')
        for i, x in enumerate(range(BX0 + 1, NX0)):
            PM(s, x, 10 + i, z, 'quartz_stairs', facing='west', **TOP)
            PM(s, x, 11 + i, z, 'quartz_stairs', facing='east', **STAIR)
    for x0 in (TX0 + 1, W - TX1):                         # pinnacles on the belfry tops' corners
        for cx in (x0, x0 + 4):
            for cz in (TZ0 + 1, TZ1 - 1):
                pinnacle(s, cx, WTOP + 1, cz)
    for cx in (TX0, TX1, W - 1 - TX1, W - 1 - TX0):      # pinnacles on the lower stages' corners
        for cz in (TZ0, TZ1):
            pinnacle(s, cx, LTOP + 1, cz)
    for cx in (FX0, FX1):                                 # pinnacles round the spire, lucarnes on its faces
        for cz in (FZ0, FZ1):
            pinnacle(s, cx, 22, cz)
    for x, z in ((SX, SZ - 2), (SX, SZ + 2), (SX - 2, SZ), (SX + 2, SZ)):
        P(s, x, 23, z, 'quartz_bricks')
        P(s, x, 24, z, 'quartz_slab', type='bottom', waterlogged='false')
    for (dx, dy), kind in rose.items():                   # stained glass: the great rose (petals alternate)
        if kind == 'petal':
            inner = abs(dx) + abs(dy) <= 3
            c = ('red' if inner else 'blue') if (dx > 0) == (dy > 0) else ('yellow' if inner else 'purple')
            pane(s, ROSE[0] + dx, ROSE[1] + dy, FZ, 'x', c, mirror=False)
    for (dx, dy), kind in rose_cells(2).items():          # the transept roses
        if kind != 'stone':
            pane(s, TRX0, 10 + dy, SZ + dx, 'z', 'red' if (dx > 0) == (dy > 0) else 'blue')
    for x in (MID - 2, MID, MID + 2):                     # the apse windows
        for y in range(3, 12):
            c = ('red', 'blue', 'blue', 'red', 'yellow', 'red', 'blue', 'blue', 'red')[y - 3] if x == MID else \
                ('blue', 'red', 'red', 'blue', 'purple', 'blue', 'red', 'red', 'blue')[y - 3]
            pane(s, x, y, 45, 'x', c, mirror=False)
    for bx in (tmx, W - 1 - tmx):                         # the bells and their ropes
        P(s, bx, WTOP - 2, tmz, 'bell', attachment='ceiling', facing='north', powered='false')
        for y in range(4, WTOP - 2):
            P(s, bx, y, tmz, 'chain', axis='y')
    for z in list(range(1, FZ)) + list(range(FZ + 1, 41)):   # a red runner from the parvis to the altar
        P(s, MID, 1, z, 'red_carpet')
    P(s, NX0 + 1, 1, CZ0 - 1, 'lectern', facing='east', has_book='false', powered='false')   # the pulpit
    s.decorate(NX0 + 1, 1, CZ0 - 1, 'item', item='minecraft:book', note='the book on the pulpit')
    PM(s, NX0 + 2, 1, 6, 'cauldron')                      # fonts by the doors
    for x in (MID - 1, MID + 1):
        P(s, x, 2, 42, 'candle', candles='4', lit='true', waterlogged='false')
    for z, y in ((13, 13), (17, 13), (21, 13), (37, 13), (41, 12), (SZ, 12)):
        hang(s, MID, y, z)                                # lanterns on long chains
    PM(s, CH_X0 + 1, 1, 37, 'stone_bricks')               # side chapel altars
    PM(s, CH_X0 + 1, 1, 36, 'stone_brick_stairs', facing='south', **TOP)
    PM(s, CH_X0 + 1, 1, 38, 'stone_brick_stairs', facing='north', **TOP)
    PM(s, CH_X0 + 1, 2, 37, 'candle', candles='3', lit='true', waterlogged='false')
    PM(s, CH_X0 + 3, 1, 36, 'flower_pot')
    PM(s, CH_X0 + 3, 1, 38, 'flower_pot')
    for x in (tmx - 1, tmx + 1):                          # banners on the towers
        PM(s, x, 10, TZ0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 10, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the kingdom colours')
        s.decorate(W - 1 - x, 10, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the kingdom colours')
    P(s, MID, 5, 2, 'lantern', **HANG)                    # under the porch arch

    # ------------------------------------------------ level 3: the pilgrimage cathedral
    s.at(3, 'flourish3')
    P(s, SX, 27, SZ, 'yellow_terracotta')                 # the gold-look spire tip
    for x0 in (TX0 + 1, W - TX1):                         # deepslate spirelets on the belfries (y 22..26)
        mx = x0 + 2
        ring_stairs(s, x0, x0 + 4, TZ0 + 1, TZ1 - 1, WTOP + 1, 'deepslate_tile_stairs', corners=False,
                    fill='deepslate_tiles')
        for x in range(mx - 1, mx + 2):
            for z in range(tmz - 1, tmz + 2):
                P(s, x, WTOP + 2, z, 'deepslate_tiles')
        ring_stairs(s, mx - 1, mx + 1, tmz - 1, tmz + 1, WTOP + 3, 'deepslate_tile_stairs', fill='deepslate_tiles')
        P(s, mx, WTOP + 4, tmz, 'deepslate_tiles')
        P(s, mx, WTOP + 5, tmz, 'deepslate_tiles')
    for mx in (tmx, W - 1 - tmx):                         # gilded spirelet tips
        P(s, mx, WTOP + 6, tmz, 'yellow_terracotta')
    for x, z in [(MID, FZ)] + [(gx, SZ) for gx in (TRX0, W - 1 - TRX0)]:    # gilded orbs on the gable finials
        y = max(yy for yy in range(H) if (x, yy, z) in s.blocks)
        P(s, x, y + 1, z, 'yellow_terracotta')
    statue(s, MID, 2, y=9, plinth=False)                  # the patron saint before the rose
    for x in (tmx - 1, tmx + 1, W - 1 - tmx - 1, W - 1 - tmx + 1):   # saints before the towers
        statue(s, x, TZ0 - 1)
    for x in (MID - 3, MID + 3):                          # and on the porch buttresses
        statue(s, x, 0)
    for x in (MID - 2, MID + 2):                          # lamp posts on the parvis
        P(s, x, 1, 0, 'cobblestone_wall')
        P(s, x, 2, 0, 'cobblestone_wall')
        P(s, x, 3, 0, 'lantern', **STAND)
    for x0 in (0, W - 2):                                 # clipped yews at the front corners
        for x in (x0, x0 + 1):
            for z in (0, 1, 2):
                A(s, x, 1, z, 'spruce_leaves', **LEAVES)
                if z == 1:
                    A(s, x, 2, z, 'spruce_leaves', **LEAVES)
        A(s, x0 + (1 if x0 == 0 else 0), 3, 1, 'spruce_leaves', **LEAVES)
    for x, f in ((TX0 + 1, 'flowering_azalea'), (TX1 - 1, 'azalea')):
        PM(s, x, 1, TZ0 - 2, f)
    for x, z in ((TX0 - 1, TZ0 - 1), (TX1 + 1, TZ0 - 1), (TX0 - 1, TZ1 + 1)):    # gargoyles under the ledge
        AM(s, x, LTOP - 1, z, 'stone_brick_stairs', facing='south' if z < TZ0 else 'north', **TOP)
    for z in (14, 18, 22):                                # banners in the nave
        PM(s, NX0 + 1, 7, z, 'white_wall_banner', facing='east')
        s.decorate(NX0 + 1, 7, z, 'banner_pattern', banner='white_wall_banner', note='a gold cross')
        s.decorate(W - 2 - NX0, 7, z, 'banner_pattern', banner='white_wall_banner', note='a gold cross')
    PM(s, NX0 + 1, 1, 11, 'candle', candles='3', lit='true', waterlogged='false')
    for z in (CH_Z0 + 1, CH_Z1 - 1):
        PM(s, CH_X0 + 3, 2, z, 'poppy' if z < 37 else 'cornflower') if False else None
    for z in (15, 23):                                    # vines on the aisle buttresses
        for y in (1, 2, 3):
            AM(s, AX0 - 1, y, z, 'vine', east='true', west='false', north='false', south='false', up='false')
    for z in (CH_Z0 + 1, CH_Z1 - 1):
        AM(s, CH_X0 - 1, 1, z, 'oak_leaves', **LEAVES)
    connect(s)
    return s


STRUCTURES = [dict(path='cathedral', name='Cathedral', tier=8, zone='inner', role='religious', group='cathedral',
                   terrain='stepped', door='front', make=cathedral, max_height=28, changes='First version.',
                   description='The town showpiece. Needs scaffolding: 28 tall (crossing spire; west towers 27 with '
                               'their spirelets, nave ridge 21). Over budget on purpose (landmark; Carlton: grand, no '
                               'trimming). Gothic cross plan: twin west towers (a square lower stage, an inset belfry) '
                               'and three portals, a rose window over the great portal, a tall nave with an open '
                               'arcade and lower aisles, a transept with a rose in each stone gable, a lantern and '
                               'steep spire over the crossing, a choir with an apse and two side chapels. L1 uses '
                               'only builder-tested blocks (grey stone, dark oak roofs; the roses are open stone '
                               'tracery until L2). L2 (consecrated): pale flying buttresses with pinnacles, '
                               'pinnacles on the towers and round the spire, stained glass in the roses and the apse, '
                               'bells, a runner, pulpit, fonts, lanterns on long chains, chapel altars, banners. L3 '
                               '(the pilgrimage cathedral): deepslate spirelets on both towers, gold-look tips and '
                               'orbs, statues of saints across the front, lamp posts, yews, gargoyles, banners in the '
                               'nave, vines.')]
