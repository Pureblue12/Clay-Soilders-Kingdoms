"""Royal garden (tier 7, look A): a formal walled parterre beside the keep, with clipped topiary, statues, a fountain
and a garden pavilion (docs/STRUCTURES.md, "Garden", variant C: royal garden, statues, topiary).

Ground rule: a flush piece (ground_layer=0), like the approved garden (tools/batch2/garden.py). Layer y = 0 REPLACES
the top grass block: paths, kerbs, bed soil and the fountain basin sit level with the lawn; an empty cell in layer 0
stays grass. Everything that stands is at y >= 1.

Plan (21 x 21, centre (10, 10), front z = 0): a stone kerb all round with a low box hedge on it between stone piers,
the gate in the middle of the front; a broad main walk (stone brick edges, gravel middle) from the gate to the
pavilion pad at the back, crossed by a walk from bench to bench; a round walk round the fountain; four parterre
quarters, each a square of box hedge round a bed with a diagonal cross of hedge inside (a knot), planted in the
king's colours.
Level 1 (KNOWN-OK only): the layout, the kerb, piers and box hedges, the knots, the fountain basin and its column,
poppies in the knots, benches.
Level 2 (topiary): clipped topiary at the corners of every quarter (balls on stems and tiered cones), a pair of tall
cones at the gate, the fountain's upper bowl and water, the beds planted in patterns of cornflowers, white tulips and
alliums, lanterns on the gate piers.
Level 3 (statues and pavilion): a round garden pavilion with a domed roof on the back pad, four statues of the king's
soldiers on plinths round the fountain, stone urns of flowers on the piers, banners on the gate, lanterns on every
pier, petals and lilies of the valley along the hedges."""
import math

from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, HANG, STAND, LEAVES, connect, noise

N = 21
C = N // 2                 # 10
E = N - 1                  # 20
PAD = (C - 2, C + 2, E - 5, E - 1)          # pavilion pad: x 8..12, z 15..19


def d(x, z, cz=C):
    return math.hypot(x - C, z - cz)


def royal_garden():
    s = Structure('royal_garden', (N, 9, N), palette='oak_a2')
    walk_main = {(x, z) for x in (C - 1, C, C + 1) for z in range(0, PAD[2])}
    walk_cross = {(x, z) for x in range(1, E) for z in (C - 1, C, C + 1)}
    fountain_r, walk_r = 3.2, 4.9
    basin = {(x, z) for x in range(N) for z in range(N) if d(x, z) <= fountain_r}
    round_walk = {(x, z) for x in range(N) for z in range(N) if fountain_r < d(x, z) <= walk_r}
    pad = {(x, z) for x in range(PAD[0], PAD[1] + 1) for z in range(PAD[2], PAD[3] + 1)
           if not (x in (PAD[0], PAD[1]) and z in (PAD[2], PAD[3]))}
    paths = (walk_main | walk_cross | round_walk) - basin
    statues = [(C - 3, C - 3), (C + 3, C - 3), (C - 3, C + 3), (C + 3, C + 3)]
    quarters = [(1, 1, C - 3, C - 3), (C + 3, 1, E - 1, C - 3), (1, C + 3, C - 3, E - 1), (C + 3, C + 3, E - 1, E - 1)]

    def in_quarter(x, z):
        return any(x0 <= x <= x1 and z0 <= z <= z1 for x0, z0, x1, z1 in quarters)

    s.at(1, 'shell')
    for x in range(N):                                    # the kerb all round
        for z in range(N):
            if x in (0, E) or z in (0, E):
                P(s, x, 0, z, 'stone_bricks')
    for x, z in sorted(paths | pad):
        if (x, z) in pad:
            P(s, x, 0, z, 'stone_bricks')
        elif (x, z) in round_walk:
            P(s, x, 0, z, 'gravel' if d(x, z) < walk_r - 0.9 else 'stone_bricks')
        elif x in (0, E) or z in (0, E):
            continue
        elif (x, z) in walk_main and x != C or (x, z) in walk_cross and z != C and (x, z) not in walk_main:
            P(s, x, 0, z, 'stone_bricks')                 # the walks' edges
        else:
            P(s, x, 0, z, 'gravel')
    for x, z in sorted(basin):                            # the fountain basin: a stone bowl, sunk into the lawn
        P(s, x, 0, z, 'stone_bricks')
    rim = {(x, z) for x, z in basin if d(x, z) > fountain_r - 1.05}
    for x, z in sorted(rim):                              # the rim: stairs facing in, a step round the water
        dx, dz = x - C, z - C
        P(s, x, 1, z, 'stone_brick_stairs', facing=_out(dx, dz), **STAIR)
    for y in (1, 2):                                      # the column in the middle
        P(s, C, y, C, 'stone_bricks')
    # the four knots: a box hedge square round each quarter, a diagonal cross inside, soil between
    for x0, z0, x1, z1 in quarters:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, 0, z) in s.blocks:
                    continue
                if (x, z) in statues:
                    P(s, x, 0, z, 'stone_bricks')         # a pad where a statue will stand (level 3)
                    continue
                edge = x in (x0, x1) or z in (z0, z1)
                cross = abs((x - x0) - (z - z0)) == 0 or abs((x - x0) - (z1 - z)) == 0
                P(s, x, 0, z, 'coarse_dirt')
                if edge or cross:
                    s.at(1, 'fittings')
                    P(s, x, 1, z, 'oak_leaves', **LEAVES)
                    s.at(1, 'shell')
    s.at(1, 'fittings')
    piers = [(x, z) for x in range(0, N, 4) for z in (0, E)] + [(x, z) for z in range(4, E, 4) for x in (0, E)]
    gate = (C - 2, C + 2)
    for x, z in piers:                                    # stone piers on the kerb, box hedge between
        if z == 0 and x in (C - 1, C, C + 1):
            continue
        for y in (1, 2):
            P(s, x, y, z, 'stone_bricks')
    for x in gate:                                        # the gate piers, taller, a cap
        for y in (1, 2, 3):
            P(s, x, y, 0, 'stone_bricks')
        P(s, x, 4, 0, 'stone_brick_stairs', facing='south', **STAIR)
    for x in range(N):
        for z in range(N):
            if (x in (0, E) or z in (0, E)) and (x, 1, z) not in s.blocks and not (z == 0 and C - 1 <= x <= C + 1):
                P(s, x, 1, z, 'oak_leaves', **LEAVES)
    for x0, z0, x1, z1 in quarters:
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, 1, z) not in s.blocks and (x + z) % 2 == 0:
                    P(s, x, 1, z, 'poppy')
    for x, back in ((1, 'west'), (E - 1, 'east')):        # benches at the ends of the cross walk
        for z in (C - 1, C, C + 1):
            P(s, x, 1, z, 'oak_stairs', facing=back, **STAIR)
    for x, z in sorted(basin - rim):
        if (x, 1, z) not in s.blocks:
            s.decorate(x, 1, z, 'water')

    s.at(2, 'flourish2')
    for x0, z0, x1, z1 in quarters:                       # topiary at the corners of every quarter
        corners = [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]
        for x, z in corners:
            if (x, z) in statues:
                continue                                  # the inner corners: statues (level 3)
            if z == 1 and abs(x - C) == 3:
                cone(s, x, z, tall=True)                  # a pair of tall cones flanking the gate walk
            elif abs(x - C) == 3 or abs(z - C) == 3:
                cone(s, x, z)
            else:
                ball(s, x, z)
    P(s, C, 3, C, 'stone_brick_wall')                     # the fountain's upper bowl
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        P(s, C + dx, 3, C + dz, 'stone_brick_slab', **SLAB_TOP)
    P(s, C, 4, C, 'stone_brick_slab', **SLAB)
    for x0, z0, x1, z1 in quarters:                       # the beds planted in the king's colours
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, 1, z) in s.blocks or (x, 0, z) not in s.blocks:
                    continue
                k = (abs(x - (x0 + x1) / 2) + abs(z - (z0 + z1) / 2))
                P(s, x, 1, z, 'cornflower' if k < 1.5 else 'white_tulip' if k < 2.5 else 'allium')
    for x in gate:
        P(s, x, 5, 0, 'lantern', **STAND)
    lawn_under(s, 2)

    s.at(3, 'flourish3')
    pavilion(s)
    for x, z in statues:
        statue(s, x, z)
    for x, z in piers:                                    # lanterns on the piers, urns of flowers on the corners
        if z == 0 and x in gate or (x, 3, z) in s.blocks or (x, 2, z) not in s.blocks:
            continue
        if x in (0, E) and z in (0, E):
            P(s, x, 3, z, 'decorated_pot', facing='south', waterlogged='false')
        else:
            P(s, x, 3, z, 'lantern', **STAND)
    for x, f in ((C - 3, 'west'), (C + 3, 'east')):     # banners on the outer faces of the gate piers
        P(s, x, 3, 0, 'red_wall_banner', facing=f)
    for x in range(1, E):                                 # petals and lilies along the inside of the hedge
        for z in (1, E - 1):
            if (x, 1, z) not in s.blocks and (x, 0, z) not in s.blocks and noise(x, 1, z, 4) < 0.6:
                P(s, x, 1, z, 'lily_of_the_valley' if (x % 3) else 'pink_petals',
                  **({} if x % 3 else dict(facing='north', flower_amount='3')))
    for z in range(1, E):
        for x in (1, E - 1):
            if (x, 1, z) not in s.blocks and (x, 0, z) not in s.blocks and noise(x, 1, z, 5) < 0.6:
                P(s, x, 1, z, 'lily_of_the_valley')
    lawn_under(s, 3)
    return connect(s)


def ball(s, x, z):
    """A clipped ball on a short stem: an oak log foot, a 3 x 3 x 3 ball of leaves with its corners cut."""
    A(s, x, 1, z, 'oak_log', axis='y')                 # (inside the hedge corner where it stands in one)
    P(s, x, 2, z, 'oak_log', axis='y')
    for y in (3, 4, 5):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                corner = dx and dz
                if (y in (3, 5) and corner) or not (0 <= x + dx < N and 0 <= z + dz < N):
                    continue
                if y == 5 and (dx or dz) and corner:
                    continue
                A(s, x + dx, y, z + dz, 'spruce_leaves' if (y == 4 and corner) else 'oak_leaves', **LEAVES)


def cone(s, x, z, tall=False):
    """A tiered cone of yew (spruce leaves): 3 x 3 at the foot, narrowing to a point, with a collar of a second green."""
    top = 5 if tall else 4
    for y in range(1, top + 1):
        if y <= (2 if tall else 1):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx and dz:
                        continue
                    if 0 <= x + dx < N and 0 <= z + dz < N:
                        A(s, x + dx, y, z + dz, 'spruce_leaves', **LEAVES)
        else:
            A(s, x, y, z, 'spruce_leaves' if y != top - 1 or not tall else 'azalea_leaves', **LEAVES)


def statue(s, x, z):
    """A clay soldier of the king's guard in stone on a plinth, facing the fountain: a stair-stepped plinth, legs, a
    tunic, arms (buttons), a head and a spear."""
    P(s, x, 1, z, 'chiseled_stone_bricks')
    P(s, x, 2, z, 'andesite_wall')
    P(s, x, 3, z, 'polished_andesite')
    P(s, x, 4, z, 'andesite_wall')
    out = _out(x - C, z - C)
    side = {'north': ('east', 'west'), 'south': ('east', 'west'), 'east': ('north', 'south'),
            'west': ('north', 'south')}[out]
    for f in side:
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[f]
        P(s, x + dx, 3, z + dz, 'stone_button', face='wall', facing=f)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):     # stairs round the plinth's foot
        A(s, x + dx, 1, z + dz, 'stone_brick_stairs', facing=_toward(dx, dz), **STAIR)


def pavilion(s):
    """A round garden pavilion on the back pad: eight stone pillars on a ring, an entablature of slabs, a dome of
    stairs and slabs with a lantern finial, a bench inside facing the fountain."""
    x0, x1, z0, z1 = PAD
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    pillars = [(x0, z0 + 1), (x0, z1 - 1), (x1, z0 + 1), (x1, z1 - 1), (x0 + 1, z0), (x1 - 1, z0), (x0 + 1, z1),
               (x1 - 1, z1)]
    for x, z in pillars:
        for y in (1, 2, 3):
            P(s, x, y, z, 'stone_brick_wall' if y < 3 else 'chiseled_stone_bricks')
    for x in range(x0, x1 + 1):                           # the roof: an eave of stairs on the pillars,
        for z in range(z0, z1 + 1):                       # a second ring of stairs, a cap and a lantern finial
            dx, dz = x - cx, z - cz
            ring = max(abs(dx), abs(dz))
            if ring == 0:
                continue
            f = _toward(-dx, 0) if abs(dx) > abs(dz) else _toward(0, -dz) if abs(dz) > abs(dx) else \
                _toward(-dx, 0)
            shape = 'straight'
            if abs(dx) == abs(dz):                        # corners: outer corner stairs
                shape = 'outer_left' if (dx * dz < 0) == (dx > 0) else 'outer_right'
            P(s, x, 6 - ring, z, 'dark_oak_stairs', facing=f, half='bottom', shape=shape, waterlogged='false')
            if ring == 2 and abs(dx) != abs(dz):
                A(s, x, 3, z, 'dark_oak_slab', **SLAB_TOP)    # lintels between the pillars
    P(s, cx, 4, cz, 'dark_oak_planks')
    P(s, cx, 5, cz, 'dark_oak_planks')
    P(s, cx, 6, cz, 'dark_oak_slab', **SLAB)
    P(s, cx, 7, cz, 'lantern', **STAND)
    P(s, cx, 3, cz, 'lantern', **HANG)
    for x in (cx - 1, cx, cx + 1):                        # a bench inside, facing the fountain
        P(s, x, 1, cz + 1, 'oak_stairs', facing='south', **STAIR)


def lawn_under(s, level):
    """A plant or post on the lawn gets a grass block under it (layer 0 replaces the ground)."""
    for (x, y, z), b in list(s.blocks.items()):
        if y == 1 and b[2] == level and (x, 0, z) not in s.blocks:
            s.put(x, 0, z, 'grass_block', snowy='false')


def _out(dx, dz):
    """The direction from the centre toward (dx, dz) (a stair facing this way has its tall side outward)."""
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def _toward(dx, dz):
    """The facing whose tall side is on the (dx, dz) side."""
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


STRUCTURES = [dict(path='royal_garden', name='Royal garden', tier=7, zone='inner', role='civic', group='garden',
                   terrain='flexible', door=None, make=royal_garden, ground_layer=0, changes='First version.',
                   description='Over budget: 982 blocks vs 130 for a garden (most of it is the paving and soil '
                               'set into the lawn in layer 0). A 21 x 21 formal garden beside the keep, set into the lawn: a stone kerb with box '
                               'hedges and piers, a gate, broad walks, a fountain and four knot parterres. L2 adds '
                               'clipped topiary (balls and cones), the fountain\'s upper bowl and the beds planted in '
                               'the king\'s colours; L3 a domed pavilion, four statues of the king\'s guard, urns, '
                               'banners and lanterns.')]
