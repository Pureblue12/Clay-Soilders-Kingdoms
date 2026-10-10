"""Fountain (tier 5, town style A): a round, two-tier stone fountain on a small paved round
(docs/STRUCTURES.md, "Fountain"; variant C, tiered, on the round plan of variant A).

Ground rule: a flush plaza piece (ground_layer=0, like tools/batch4/market.py). Layer y = 0 REPLACES the top grass
block: the paving and the basin floor are level with the lawn; cells outside the round stay grass. Everything that
stands is at y >= 1, so the basin rim is one block tall above the grass (Carlton on the well: "the rim only 1 block
tall", round, not square).

Plan (9 x 9, centre (4, 4)): the basin is a rounded octagon 7 across (rim of 16 blocks one high), the paving a ring
around it 9 across with a cobble path in from each side. In the middle a stem carries an upper bowl (a ring of
upside-down stairs) at y 3 and a column through it. Water (the basin at y 1) is added by the decoration step; the
Studio shows it as water.
Level 1 (KNOWN-OK only): paving, basin and rim, stem, upper bowl and column.
Level 2 (a plaza fountain): four lantern posts at the diagonals with flowering azalea bushes round their feet, flower
beds in the grass corners, lily pads on the basin.
Level 3 (the town's pride): a statue top on the column (a chiselled block, a post and a gold-look finial), flower pots
on the rim's diagonal corners, red and white banners on the lantern posts."""
import math

from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, HANG, STAND, LEAVES, connect, noise

SIZE = 9
C = SIZE // 2
FLOWERS = ('poppy', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'dandelion')
OUT = {(0, -1): 'north', (0, 1): 'south', (-1, 0): 'west', (1, 0): 'east'}
FLIP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def d(x, z):
    return math.hypot(x - C, z - C)


def fountain():
    s = Structure('fountain', (SIZE, 8, SIZE), palette='oak_a2')
    cells = [(x, z) for x in range(SIZE) for z in range(SIZE)]
    basin = {(x, z) for x, z in cells if d(x, z) <= 3.4}
    water = {(x, z) for x, z in basin if d(x, z) <= 2.4 and (x, z) != (C, C)}
    rim = basin - water - {(C, C)}
    paving = {(x, z) for x, z in cells if 3.4 < d(x, z) <= 4.4}
    path = {(x, z) for x, z in paving if x == C or z == C}
    corners = [(x, z) for x, z in cells if d(x, z) > 4.4]             # grass corners (flower beds at L2)
    diag = [(C + a * 3, C + b * 3) for a in (-1, 1) for b in (-1, 1)]  # lantern posts

    s.at(1, 'shell')
    for x, z in sorted(paving):                                         # the paving ring, a cobble path on each axis
        if (x, z) in path:
            P(s, x, 0, z, 'cobblestone')
        else:
            P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 5) < 0.2 else 'stone_bricks')
    for x, z in sorted(basin):                                          # the basin floor
        P(s, x, 0, z, 'stone_bricks')
    for x, z in sorted(rim):                                            # the rim, one block above the grass
        P(s, x, 1, z, 'stone_bricks')
    P(s, C, 1, C, 'stone_bricks')                                       # the stem's foot in the water
    P(s, C, 2, C, 'cobblestone_wall')                                   # the stem
    P(s, C, 3, C, 'stone_bricks')                                       # the upper bowl: corbelled stairs round a core
    for (dx, dz), f in OUT.items():
        P(s, C + dx, 3, C + dz, 'stone_brick_stairs', facing=FLIP[f], **TOP)
    for a in (-1, 1):
        for b in (-1, 1):
            fz = 'south' if b < 0 else 'north'                          # back toward the middle row
            right = {'north': 'east', 'south': 'west'}[fz]
            shape = 'outer_right' if (a > 0) == (right == 'east') else 'outer_left'
            P(s, C + a, 3, C + b, 'stone_brick_stairs', facing=fz, half='top', shape=shape, waterlogged='false')
    P(s, C, 4, C, 'stone_bricks')                                       # the column through the upper bowl
    for x, z in sorted(water):
        s.decorate(x, 1, z, 'water')

    s.at(2, 'flourish2')
    for x, z in diag:                                                   # lantern posts at the diagonals
        P(s, x, 1, z, 'stone_brick_wall')
        P(s, x, 2, z, 'stone_brick_wall')
        P(s, x, 3, z, 'lantern', **STAND)
    for x, z in diag:                                                   # flowering bushes hugging each post
        for bx, bz in ((x, z + (1 if z < C else -1)), (x + (1 if x < C else -1), z)):
            P(s, bx, 1, bz, 'flowering_azalea_leaves' if (bx + bz) % 2 else 'azalea_leaves', **LEAVES)
    for i, (x, z) in enumerate(corners):                                # flower beds in the grass corners
        P(s, x, 0, z, 'grass_block', snowy='false')
        P(s, x, 1, z, FLOWERS[(x * 3 + z) % len(FLOWERS)])
    for x, z in ((C - 1, C + 2), (C + 2, C - 1), (C - 2, C - 2 + 1)):
        s.decorate(x, 2, z, 'lily_pad')

    s.at(3, 'flourish3')
    P(s, C, 5, C, 'chiseled_stone_bricks')                              # the statue top
    P(s, C, 6, C, 'stone_brick_wall')
    P(s, C, 7, C, 'light_weighted_pressure_plate', power='0')           # gold-look finial
    for a in (-2, 2):                                                   # flower pots on the rim's diagonal corners
        for b in (-2, 2):
            P(s, C + a, 2, C + b, 'flower_pot')
            s.decorate(C + a, 2, C + b, 'potted_plant', plant=('red_tulip', 'blue_orchid')[(a + b) % 4 == 0])
    for i, (x, z) in enumerate(diag):                                   # banners on the lantern posts
        side = 'north' if z < C else 'south'
        P(s, x, 2, z + (-1 if z < C else 1), 'red_wall_banner' if i % 2 == 0 else 'white_wall_banner', facing=side)
        s.decorate(x, 2, z + (-1 if z < C else 1), 'banner_pattern', note='kingdom colours')
    return connect(s)


STRUCTURES = [dict(path='fountain', name='Fountain', tier=5, zone='inner', role='decoration', group='fountain',
                   terrain='flexible', door=None, make=fountain, ground_layer=0, changes='First version.',
                   description='Over budget: 160 blocks vs 51 (9 x 9 with its paving instead of 5 x 5). Flush plaza piece (ground_layer 0: the paving and basin floor replace the grass, level '
                               'with the lawn; the mod\'s builders can\'t do that yet). A round two-tier stone fountain: '
                               'a basin 7 across with a rim one block tall, a stem and corbelled upper bowl, on a paved '
                               'round 9 across with a cobble path in from each side; the basin water by the decoration step. '
                               'L2 lantern posts with flowering bushes, flower beds in the grass corners, lily pads; '
                               'L3 a statue top with a gold-look finial, flower pots on the rim, banners.')]
