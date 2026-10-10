"""Commons / town green (tier 2): a round village plaza at a road junction with a statue of the king in the middle,
benches facing it, lantern posts, planters, a notice board and a market stall (docs/STRUCTURES.md, "Commons / town
green").

Ground rule: this is a flush piece (ground_layer=0). Layer y = 0 REPLACES the top grass block, so the paving is level
with the lawn; cells outside the circle stay grass. Everything that stands is at y >= 1. Planters are raised beds: soil
on the paving with a wooden border."""

import math

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


def planter(s, cells, y=1, soil='coarse_dirt', border='spruce_trapdoor', skip=()):
    """A raised bed: soil blocks on the paving, an open trapdoor hugging every free outer face (the wooden border).
    Returns the soil cells (plants go one above)."""
    cells = list(cells)
    for x, z in cells:
        put_id(s, x, y, z, soil)
    for x, z in cells:
        for (dx, dz), f in AWAY.items():
            p = (x + dx, z + dz)
            if p not in cells and p not in skip and (p[0], y, p[1]) not in s.blocks:
                put_id(s, p[0], y, p[1], border, facing=f, **OPEN)
    return cells


def bench(s, cells, back, y=1, block='oak_stairs'):
    for x, z in cells:
        put_id(s, x, y, z, block, facing=back, shape='straight', **STAIR)


def commons(size=15):
    """Level 1: a round plaza 13 across, set into the grass (stone brick rings with cobblestone and moss between them,
    gravel spokes to the four roads), a stepped stone plinth in the middle, four raised planters on the diagonals.
    Level 2: the statue of the king on the plinth (a robe, arms, shoulders, a chiselled head, a gold crown, a red cloak,
    a banner on a pole in his hand), a plaque, eight benches facing him, lantern posts in the planters, a notice board
    with a little gable roof at the front right.
    Level 3: red and white banners on posts at the front and back road ends, a market stall with a pitched roof at the
    back right, flowers in the planters and at the plinth's foot, azalea bushes and flowers in the grass corners."""
    s = Structure('commons', (size, 9, size), palette='oak_a2')
    c, e = size // 2, size - 1                                     # centre 7, last 14

    def d(x, z):
        return math.hypot(x - c, z - c)

    plaza = {(x, z) for x in range(size) for z in range(size) if d(x, z) <= 6.5}
    spokes = {(x, z) for x, z in plaza if (x == c or z == c) and d(x, z) > 2.6}
    planters = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            planters.append([(c + sx * a, c + sz * b) for a in (3, 4) for b in (3, 4)])

    seats = {}                                                     # eight benches facing the statue (level 2)
    for (ax, az), back in (((0, -1), 'north'), ((0, 1), 'south'), ((-1, 0), 'west'), ((1, 0), 'east')):
        for side in (-1, 1):
            for k in (1, 2):
                seats[(c + ax * 4 + (side * k if az else 0), c + az * 4 + (side * k if ax else 0))] = back

    sx0, sx1, sz0, sz1 = e - 3, e - 1, e - 2, e - 1                 # the market stall's posts (level 3)
    stall_posts = {(x, z) for x in (sx0, sx1) for z in (sz0, sz1)}

    s.at(1, 'shell')
    for x, z in sorted(plaza):                                     # the paving: rings of stone brick, cobble between
        r = d(x, z)
        if (x, z) in spokes:
            put_id(s, x, 0, z, 'gravel')
        elif r <= 2.6 or 3.6 < r <= 4.4 or r > 5.6:
            put_id(s, x, 0, z, 'stone_bricks')
        else:
            put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 7) < 0.25 else 'cobblestone')

    s.at(1, 'fittings')
    step_rim(s, c - 2, c - 2, c + 2, c + 2, y=1, block='stone_brick_stairs')   # the plinth: a step all round
    s.fill(c - 1, 1, c - 1, c + 1, 2, c + 1, 'stone_bricks')                     # and a 3 x 3 block
    for cells in planters:                                         # raised planters on the four diagonals
        planter(s, cells, skip=set(seats) | stall_posts)

    s.at(2, 'flourish2')
    # the statue of the king (facing the front): a robe flaring at the hem, arms, shoulders, a chiselled head with a
    # gold crown, a red cloak down his back, and a banner on a pole in his right hand
    TOP = dict(half='top', shape='straight', waterlogged='false')
    BOT = dict(half='bottom', shape='straight', waterlogged='false')
    for y in (3, 4, 5):
        put_id(s, c, y, c, 'polished_andesite')
    put_id(s, c - 1, 3, c, 'stone_brick_stairs', facing='east', **BOT)
    put_id(s, c + 1, 3, c, 'stone_brick_stairs', facing='west', **BOT)
    put_id(s, c - 1, 4, c, 'stone_brick_wall')                     # left arm down; the right one holds the pole
    put_id(s, c - 1, 5, c, 'stone_brick_stairs', facing='east', **TOP)
    put_id(s, c + 1, 5, c, 'stone_brick_stairs', facing='west', **TOP)
    put_id(s, c, 6, c, 'chiseled_stone_bricks')
    put_id(s, c - 1, 6, c, 'red_carpet'); put_id(s, c + 1, 6, c, 'red_carpet')   # the cloak over his shoulders
    put_id(s, c, 7, c, 'light_weighted_pressure_plate', power='0')
    for y in (3, 4, 5):
        put_id(s, c, y, c + 1, 'red_wool')
    put_id(s, c - 1, 3, c + 1, 'red_carpet'); put_id(s, c + 1, 3, c + 1, 'red_carpet')
    for y in range(3, 8):
        put_id(s, c + 1, y, c - 1, 'spruce_fence')
    put_id(s, c + 1, 8, c - 1, 'red_banner', rotation='8')
    put_id(s, c, 2, c - 2, 'oak_wall_sign', facing='north', waterlogged='false')   # a plaque on the plinth
    s.decorate(c, 2, c - 2, 'sign_text', lines=['', 'Our King', 'founder of', 'the realm'])
    for (x, z), back in seats.items():                             # the benches facing the statue
        bench(s, [(x, z)], back)
    # lantern posts in the planters' inner corners
    for cells in planters:
        x, z = min(cells, key=lambda p: d(*p))
        put_id(s, x, 2, z, 'spruce_fence'); put_id(s, x, 3, z, 'spruce_fence'); put_id(s, x, 4, z, 'lantern', **LANTERN)
    # notice board at the front right, outside the circle, facing the road: posts, a plank board, a little gable roof
    for x in (e - 3, e - 1):
        put_id(s, x, 1, 1, 'spruce_fence')
    for x in range(e - 4, e + 1):                                  # roof one past the board on each side
        if e - 3 <= x <= e - 1:
            put_id(s, x, 2, 1, 'spruce_planks'); put_id(s, x, 3, 1, 'spruce_planks')
        put_id(s, x, 4, 0, 'spruce_stairs', facing='south', **STAIR, shape='straight')
        put_id(s, x, 4, 1, 'spruce_planks')
        put_id(s, x, 4, 2, 'spruce_stairs', facing='north', **STAIR, shape='straight')
        put_id(s, x, 5, 1, 'spruce_slab', **SLAB)
    for x in (e - 3, e - 1):
        put_id(s, x, 2, 0, 'oak_wall_sign', facing='north', waterlogged='false')
    put_id(s, e - 2, 3, 0, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(e - 2, 3, 0, 'sign_text', lines=['', 'Notices', '', ''])
    s.decorate(e - 3, 2, 0, 'sign_text', lines=['', 'Market day', 'at noon', ''])
    s.decorate(e - 1, 2, 0, 'sign_text', lines=['', 'Long live', 'the King', ''])

    s.at(3, 'flourish3')
    # red and white banners on posts at the front and back road ends
    for i, ((ax, az), rot) in enumerate((((0, -1), '8'), ((0, 1), '0'))):
        for side in (-1, 1):
            x = c + ax * 6 + (2 * side if az else 0)
            z = c + az * 6 + (2 * side if ax else 0)
            put_id(s, x, 1, z, 'spruce_fence'); put_id(s, x, 2, z, 'spruce_fence')
            put_id(s, x, 3, z, 'red_banner' if side < 0 else 'white_banner', rotation=rot)
    # market stall at the back right: counter, crates and a pitched spruce roof
    for x in (sx0, sx1):
        for z in (sz0, sz1):
            put_id(s, x, 1, z, 'spruce_fence'); put_id(s, x, 2, z, 'spruce_fence')
    put_id(s, sx0 + 1, 1, sz0, 'barrel', facing='up', open='false')
    put_id(s, sx0 + 1, 2, sz0, 'spruce_slab', **SLAB)
    put_id(s, sx0 + 1, 1, sz1, 'hay_block', axis='y')
    put_id(s, sx0 + 1, 2, sz1, 'pumpkin')
    for x in range(sx0 - 1, sx1 + 2):                              # gable roof, ridge along x, overhang all round
        put_id(s, x, 3, sz0 - 1, 'spruce_stairs', facing='south', shape='straight', **STAIR)
        put_id(s, x, 3, sz1 + 1, 'spruce_stairs', facing='north', shape='straight', **STAIR)
        put_id(s, x, 3, sz0, 'spruce_planks' if x in (sx0, sx1) else 'red_wool')
        put_id(s, x, 3, sz1, 'spruce_planks' if x in (sx0, sx1) else 'white_wool')
        put_id(s, x, 4, sz0, 'spruce_stairs', facing='south', shape='straight', **STAIR)
        put_id(s, x, 4, sz1, 'spruce_stairs', facing='north', shape='straight', **STAIR)
    # flowers in the planters (round the lantern posts)
    for i, cells in enumerate(planters):
        for j, (x, z) in enumerate(cells):
            if (x, 2, z) not in s.blocks:
                put_id(s, x, 2, z, FLOWERS[(i * 3 + j) % len(FLOWERS)])
    # azalea bushes and flowers in the grass corners
    for x, z in ((0, 0), (e, 0), (0, e)):
        put_id(s, x, 1, z, 'flowering_azalea')
    for x, z in ((1, 0), (0, 1), (e - 1, 0), (e, 1), (1, e), (0, e - 1), (2, 0), (0, 2)):
        if (x, 1, z) not in s.blocks:
            put_id(s, x, 1, z, FLOWERS[(x + 2 * z) % len(FLOWERS)])
    for (x, y, z), b in list(s.blocks.items()):                    # grass under them (layer 0 replaces the ground)
        if y == 1 and b[2] == 3 and (x, z) not in plaza and (x, 0, z) not in s.blocks:
            put_id(s, x, 0, z, 'grass_block', snowy='false')
    return connect(s)


STRUCTURES = [dict(path='commons', name='Commons (level 1)', tier=2, zone='inner', role='civic', group='commons',
                   terrain='flexible', door=None, make=commons, ground_layer=0,
                   changes='Now a round plaza set level into the grass with a statue of the king on a stepped plinth '
                           'as its centrepiece: ringed stone paving, gravel spokes, benches facing the statue, lantern '
                           'planters, a notice board, banners and a market stall with a pitched roof.',
                   description='A round paved plaza 13 across, level with the grass, at a road junction: a statue of the '
                               'king on a stepped plinth in the middle, ringed paving, gravel spokes, planters; later '
                               'levels add the statue, benches, lanterns, a notice board, banners and a market stall.')]
