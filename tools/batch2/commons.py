"""Commons / town green (tier 2): a paved village square at a road junction with a fire pit, a well, benches, planters,
a tree and a notice board (docs/STRUCTURES.md, "Commons / town green").

Ground rule (BuildSitePlanner never digs): layer y = 0 is the first layer ON TOP of the grass. So the whole pad is one
flat paved floor at y = 0 with a step of stairs round its rim (it reads as a low raised square, not a stray strip), and
everything else stands on it (y >= 1). Planters are raised beds: soil on the paving with a wooden border."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
FLOWERS = ('poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet')   # fixed order, no randomness
AWAY = {(0, -1): 'north', (0, 1): 'south', (-1, 0): 'west', (1, 0): 'east'}


def step_rim(s, x0, z0, x1, z1, y=0, block='cobblestone_stairs'):
    """A ring of stairs, high side inward, so the pad steps down to the grass on every side (outer corners)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            if z in (z0, z1):
                f = 'south' if z == z0 else 'north'
            else:
                f = 'east' if x == x0 else 'west'
            shape = 'straight'
            if x in (x0, x1) and z in (z0, z1):
                shape = 'outer_left' if (x == x0) == (z == z0) else 'outer_right'
            put_id(s, x, y, z, block, facing=f, shape=shape, **STAIR)


def planter(s, cells, y=1, soil='coarse_dirt', border='spruce_trapdoor'):
    """A raised bed: soil blocks on the paving, an open trapdoor hugging every free outer face (the wooden border).
    Returns the soil cells (plants go one above)."""
    cells = list(cells)
    for x, z in cells:
        put_id(s, x, y, z, soil)
    for x, z in cells:
        for (dx, dz), f in AWAY.items():
            p = (x + dx, z + dz)
            if p not in cells and (p[0], y, p[1]) not in s.blocks:
                put_id(s, p[0], y, p[1], border, facing=f, **OPEN)
    return cells


def bench(s, cells, back, y=1, block='oak_stairs'):
    for x, z in cells:
        put_id(s, x, y, z, block, facing=back, shape='straight', **STAIR)


def commons(size=13):
    """Level 1: a 13 x 13 paved square (cobblestone with moss mixed in by noise, a stone brick circle in the middle,
    gravel spokes from the four road ends) on a step of cobblestone stairs; a fire pit in the middle; a covered well
    at the back right (rim, two posts, a log beam, a spruce gable roof, a chain); a ring bench of stairs round a
    soil planter at the back left; two benches facing the fire, each with a raised bed of poppies behind it.
    Level 2: an oak tree in the ring planter, lantern posts on the four corners, a notice board with two signs on the
    left, bench arms, a barrel and a hay bale by the well.
    Level 3: red banners on posts at the front and back road ends, a market stall (barrels under a striped awning) on
    the right, hedges with flowers, lanterns hanging from the well beam and the tree, firewood by the fire, flower
    pots and moss on the well."""
    s = Structure('commons', (size, 7, size), palette='oak_a2')
    c, e = size // 2, size - 1                                    # center 6, last 12
    spokes = {(c, z) for z in list(range(1, c - 2)) + list(range(c + 3, e))} | \
             {(x, c) for x in list(range(1, c - 2)) + list(range(c + 3, e))}

    def circle(x, z):
        dx, dz = abs(x - c), abs(z - c)
        return dx <= 2 and dz <= 2 and not (dx == 2 and dz == 2)

    s.at(1, 'shell')
    step_rim(s, 0, 0, e, e)
    for x in range(1, e):                                         # the paved floor
        for z in range(1, e):
            if (x, z) in spokes:
                put_id(s, x, 0, z, 'gravel')
            elif circle(x, z):
                put_id(s, x, 0, z, 'stone_bricks')
            else:
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 7) < 0.22 else 'cobblestone')

    s.at(1, 'fittings')
    # fire pit: a cobblestone slab ring round a campfire
    s.ring(c - 1, c - 1, c + 1, c + 1, 1, 'stone_slab', **SLAB)
    s.remove(c, 1, c)
    put_id(s, c, 1, c, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')

    # covered well, back right (x 8..10, z 8..10): rim, posts, log beam, spruce gable roof, chain over the shaft
    wx, wz = c + 2, c + 2
    for x in range(wx, wx + 3):
        for z in range(wz, wz + 3):
            if (x, z) == (wx + 1, wz + 1):
                continue
            corner = x != wx + 1 and z != wz + 1
            put_id(s, x, 1, z, 'stone_bricks' if corner else 'cobblestone')
    for x in (wx, wx + 2):
        put_id(s, x, 2, wz + 1, 'cobblestone_wall'); put_id(s, x, 3, wz + 1, 'cobblestone_wall')
    for x in range(wx - 1, wx + 4):                               # beam and roof overhang one past the posts
        if x in range(wx, wx + 3):
            put_id(s, x, 4, wz + 1, 'spruce_log', axis='x')
        put_id(s, x, 4, wz, 'spruce_stairs', facing='south', shape='straight', **STAIR)
        put_id(s, x, 4, wz + 2, 'spruce_stairs', facing='north', shape='straight', **STAIR)
        put_id(s, x, 5, wz + 1, 'spruce_slab', **SLAB)
    put_id(s, wx + 1, 3, wz + 1, 'chain', axis='y')
    s.decorate(wx + 1, 1, wz + 1, 'water', note='well water inside the rim')
    put_id(s, wx, 2, wz, 'torch'); put_id(s, wx, 2, wz + 2, 'torch')

    # ring bench round a planter, back left (center 3, 9): stairs with their backs to the soil
    tx, tz = 3, c + 3
    put_id(s, tx, 1, tz, 'coarse_dirt')
    for x in range(tx - 1, tx + 2):
        for z in range(tz - 1, tz + 2):
            if (x, z) == (tx, tz):
                continue
            if z != tz and x != tx:
                shape = 'outer_right' if (x < tx) == (z < tz) else 'outer_left'
                f = 'south' if z < tz else 'north'
            else:
                shape = 'straight'
                f = 'south' if z < tz else 'north' if z > tz else 'east' if x < tx else 'west'
            put_id(s, x, 1, z, 'spruce_stairs', facing=f, shape=shape, **STAIR)

    # two benches facing the fire, a planter (raised bed) behind each
    front_benches = [(c - 3, c - 2), (c + 2, c + 3)]
    for xs in front_benches:
        bench(s, [(x, c - 3) for x in xs], 'north')
        planter(s, [(x, c - 4) for x in xs])
        for x in xs:
            put_id(s, x, 2, c - 4, 'poppy')

    s.at(2, 'flourish2')
    # the tree in the ring planter
    for y in range(2, 5):
        put_id(s, tx, y, tz, 'oak_log', axis='y')
    for x in range(tx - 2, tx + 3):
        for z in range(tz - 2, tz + 3):
            corner = abs(x - tx) == 2 and abs(z - tz) == 2
            if 0 <= x < size and 0 < z < e and not corner and (x, z) != (tx, tz):
                put_id(s, x, 4, z, 'oak_leaves', **LEAVES)
            if abs(x - tx) <= 1 and abs(z - tz) <= 1:
                put_id(s, x, 5, z, 'oak_leaves', **LEAVES)
    put_id(s, tx, 6, tz, 'oak_leaves', **LEAVES)
    # lantern posts on the four inner corners
    for x, z in ((1, 1), (e - 1, 1), (1, e - 1), (e - 1, e - 1)):
        put_id(s, x, 1, z, 'spruce_fence'); put_id(s, x, 2, z, 'spruce_fence'); put_id(s, x, 3, z, 'lantern', **LANTERN)
    # notice board on the left, facing the square: two posts, a plank board, a slab cap, two signs
    bx = 1
    for z in (c - 3, c - 1):
        put_id(s, bx, 1, z, 'spruce_fence')
    for z in range(c - 3, c):
        put_id(s, bx, 2, z, 'spruce_planks')
        put_id(s, bx, 3, z, 'spruce_slab', **SLAB)
    for z in (c - 3, c - 1):
        put_id(s, bx + 1, 2, z, 'oak_wall_sign', facing='east', waterlogged='false')
    s.decorate(bx + 1, 2, c - 3, 'sign_text', lines=['', 'Market day', 'at noon', ''])
    s.decorate(bx + 1, 2, c - 1, 'sign_text', lines=['', 'By order of', 'the King', ''])
    # bench arms and flowers
    for xs in front_benches:
        put_id(s, xs[0] - 1, 1, c - 3, 'oak_trapdoor', facing='west', **OPEN)
        put_id(s, xs[1] + 1, 1, c - 3, 'oak_trapdoor', facing='east', **OPEN)
    # well dressing: a barrel (bucket) and a hay bale beside it
    put_id(s, wx + 3, 1, wz + 2, 'barrel', facing='up', open='false')
    put_id(s, wx - 1, 1, wz + 2, 'hay_block', axis='y')
    s.decorate(wx + 3, 2, wz + 2, 'item', item='minecraft:bucket', note='a bucket on the barrel')

    s.at(3, 'flourish3')
    # red banners on posts flanking the front and back road ends
    for z in (1, e - 1):
        for x in (c - 1, c + 1):
            put_id(s, x, 1, z, 'spruce_fence')
            put_id(s, x, 2, z, 'red_banner', rotation='8' if z == 1 else '0')
    # hedges under the tree on the left edge and beside the well on the right, with flowers in them
    for z in range(c + 1, c + 5):
        put_id(s, 1, 1, z, 'oak_leaves', **LEAVES)
    for z in (c + 1, c + 2):
        put_id(s, e - 1, 1, z, 'oak_leaves', **LEAVES)
    for x, z in ((1, c + 2), (1, c + 4), (e - 1, c + 1)):
        put_id(s, x, 2, z, FLOWERS[(x + z) % len(FLOWERS)])
    # market stall on the right edge: two barrels under a striped awning (slabs with red and white carpet) on posts
    for z in (c - 3, c - 2):
        put_id(s, e - 1, 1, z, 'barrel', facing='up', open='false')
    for z in (c - 4, c - 1):
        put_id(s, e - 1, 1, z, 'spruce_fence'); put_id(s, e - 1, 2, z, 'spruce_fence')
    for z in range(c - 4, c):
        put_id(s, e - 1, 3, z, 'spruce_slab', type='top', waterlogged='false')
        put_id(s, e - 1, 4, z, 'red_carpet' if z % 2 else 'white_carpet')
    # lanterns hanging from the well beam ends and in the tree
    put_id(s, wx - 1, 3, wz + 1, 'lantern', **HANGING)
    put_id(s, wx + 3, 3, wz + 1, 'lantern', **HANGING)
    put_id(s, tx + 2, 3, tz, 'lantern', **HANGING)
    # firewood by the fire, pots and moss on the well rim
    for z in (c - 1, c, c + 1):
        put_id(s, c - 2, 1, z, 'oak_log', axis='z')
    put_id(s, c - 2, 2, c, 'oak_log', axis='z')
    put_id(s, wx + 2, 2, wz + 2, 'flower_pot'); s.decorate(wx + 2, 2, wz + 2, 'potted_plant', plant='poppy')
    put_id(s, wx + 2, 2, wz, 'flower_pot'); s.decorate(wx + 2, 2, wz, 'potted_plant', plant='dandelion')
    put_id(s, wx + 1, 2, wz, 'moss_carpet'); put_id(s, wx + 1, 2, wz + 2, 'moss_carpet')
    return connect(s)


STRUCTURES = [dict(path='commons', name='Commons (level 1)', tier=2, zone='inner', role='civic', group='commons',
                   terrain='flexible', door=None, make=commons,
                   changes='Bigger (13 x 13 instead of 9 x 9) and sits on the grass: one flat paved square with a '
                           'stair step all round; level 1 has a proper covered well, a fire pit, benches and planters, '
                           'level 2 adds a tree, lantern posts and a notice board, level 3 adds banners, hedges, a '
                           'market stall and hanging lanterns.',
                   description='A 13 x 13 paved village square on a road junction: fire pit, covered well, benches, '
                               'planters, a ring bench round a tree; later levels add lanterns, a notice board, banners '
                               'and a market stall.')]
