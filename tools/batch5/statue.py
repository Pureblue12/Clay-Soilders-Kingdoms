"""Statue (tier 6, town style A): the founding king as a clay soldier in stone, sword raised, shield on his arm, on a
stepped plinth in a small paved round (docs/STRUCTURES.md, "Statue"; variant A, king). A landmark for the plaza.

Ground rule: a flush plaza piece (ground_layer=0, like tools/batch4/market.py). Layer y = 0 REPLACES the top grass
block: the paving is level with the lawn; cells outside the round stay grass. Everything that stands is at y >= 1.

Plan (9 x 9, centre (4, 4), the figure faces the front, z = 0): a paved round 9 across with a cobble path in from each
side; a plinth: a step of stairs all round (5 x 5), a 3 x 3 body with a plaque on the front, a corbelled cornice; the
figure on top: a tabard flaring at the hem, arms, shoulders, a head; the left arm carries a shield, the right one
raises a sword.
Level 1 (KNOWN-OK only): paving, plinth, plaque (its text by the decoration step), the figure, its shield and sword.
Level 2 (honoured): four lantern posts round the plinth, flowers in the grass corners and on the step, a blade that
shines (a lightning rod), the shield's coat of arms (a banner), moss on the step.
Level 3 (the king): a gold-look crown, a red cloak over the shoulders and down his back, candles on the cornice,
red and white banners on the lantern posts, wreaths of flowers in pots at the plinth's corners."""
import math

from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, HANG, STAND, LEAVES, connect, noise

SIZE = 9
C = SIZE // 2
FLOWERS = ('poppy', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'dandelion')
IN = {(0, -1): 'south', (0, 1): 'north', (-1, 0): 'east', (1, 0): 'west'}     # facing toward the middle


def d(x, z):
    return math.hypot(x - C, z - C)


def ring_stairs(s, r, y, half):
    """A square ring of stone brick stairs at distance r round the centre, high side toward the middle."""
    for x in range(C - r, C + r + 1):
        for z in range(C - r, C + r + 1):
            if max(abs(x - C), abs(z - C)) != r:
                continue
            if z in (C - r, C + r):
                f = 'south' if z == C - r else 'north'
            else:
                f = 'east' if x == C - r else 'west'
            shape = 'straight'
            if x in (C - r, C + r) and z in (C - r, C + r):
                shape = 'outer_left' if (x == C - r) == (z == C - r) else 'outer_right'
                if half == 'top':
                    shape = 'outer_right' if shape == 'outer_left' else 'outer_left'
            P(s, x, y, z, 'stone_brick_stairs', facing=f, half=half, shape=shape, waterlogged='false')


def statue():
    s = Structure('statue', (SIZE, 9, SIZE), palette='oak_a2')
    cells = [(x, z) for x in range(SIZE) for z in range(SIZE)]
    paving = {(x, z) for x, z in cells if d(x, z) <= 4.4}
    path = {(x, z) for x, z in paving if (x == C or z == C) and d(x, z) > 2.9}
    corners = [(x, z) for x, z in cells if d(x, z) > 4.4]
    posts = [(C + a * 3, C + b * 3) for a in (-1, 1) for b in (-1, 1)]

    s.at(1, 'shell')
    for x, z in sorted(paving):                                         # the paving: a stone brick round, cobble paths
        if (x, z) in path:
            P(s, x, 0, z, 'cobblestone')
        elif max(abs(x - C), abs(z - C)) <= 2:
            P(s, x, 0, z, 'stone_bricks')
        else:
            P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 9) < 0.25 else 'cobblestone')
    ring_stairs(s, 2, 1, 'bottom')                                      # the step round the plinth
    s.fill(C - 1, 1, C - 1, C + 1, 2, C + 1, 'stone_bricks')            # the plinth's body
    P(s, C, 3, C, 'stone_bricks')                                       # the cornice: corbels round a core
    ring_stairs(s, 1, 3, 'top')
    P(s, C, 2, C - 2, 'oak_wall_sign', facing='north', waterlogged='false')   # the plaque
    s.decorate(C, 2, C - 2, 'sign_text', lines=['', 'Our Founder', 'the First King', ''])

    s.at(1, 'fittings')                                                 # the figure (faces the front)
    P(s, C, 4, C, 'white_terracotta')                                   # fired clay: a clay soldier
    P(s, C - 1, 4, C, 'stone_brick_stairs', facing='east', **STAIR)     # its hem flaring out
    P(s, C + 1, 4, C, 'stone_brick_stairs', facing='west', **STAIR)
    P(s, C, 5, C, 'white_terracotta')                                   # chest
    P(s, C - 1, 5, C, 'cobblestone_wall')                               # left arm, down
    P(s, C - 1, 6, C, 'stone_brick_stairs', facing='east', **TOP)       # shoulders
    P(s, C + 1, 6, C, 'stone_brick_stairs', facing='west', **TOP)
    P(s, C, 6, C, 'white_terracotta')                                   # neck and collar
    P(s, C, 7, C, 'white_terracotta')                                   # head
    P(s, C - 1, 5, C - 1, 'spruce_trapdoor', facing='north', **OPEN)    # the shield on the left arm
    P(s, C + 1, 7, C, 'cobblestone_wall')                               # the right arm raised, the fist round the grip
    P(s, C + 1, 8, C, 'chain', axis='y')                                # the blade

    s.at(2, 'flourish2')
    for x, z in posts:                                                  # lantern posts round the plinth
        P(s, x, 1, z, 'stone_brick_wall')
        P(s, x, 2, z, 'stone_brick_wall')
        P(s, x, 3, z, 'lantern', **STAND)
    for i, (x, z) in enumerate(corners):                                # flowers in the grass corners
        P(s, x, 0, z, 'grass_block', snowy='false')
        P(s, x, 1, z, FLOWERS[(x * 3 + z) % len(FLOWERS)])
    for x, z in posts:                                                  # bushes hugging the posts
        for bx, bz in ((x, z + (1 if z < C else -1)), (x + (1 if x < C else -1), z)):
            P(s, bx, 1, bz, 'flowering_azalea_leaves' if (bx + bz) % 2 else 'azalea_leaves', **LEAVES)
    s.at(3, 'flourish3')
    P(s, C, 8, C, 'light_weighted_pressure_plate', power='0')           # the gold-look crown
    for y in (5, 6):                                                    # the cloak down his back
        P(s, C, y, C + 1, 'red_wool')
    P(s, C, 4, C + 1, 'red_carpet')
    P(s, C - 1, 7, C, 'red_carpet')                                     # over the shoulders
    for x, z in ((C - 1, C - 1), (C + 1, C - 1)):                       # candles on the cornice's front corners
        A(s, x, 4, z, 'candle', candles='3', lit='true', waterlogged='false')
    for i, (x, z) in enumerate(posts):                                  # banners on the lantern posts
        side = 'north' if z < C else 'south'
        bz = z + (-1 if z < C else 1)
        P(s, x, 2, bz, 'red_wall_banner' if i % 2 == 0 else 'white_wall_banner', facing=side)
        s.decorate(x, 2, bz, 'banner_pattern', note='kingdom colours')
    return connect(s)


STRUCTURES = [dict(path='statue', name='Statue', tier=6, zone='inner', role='decoration', group='statue',
                   terrain='flexible', door=None, make=statue, ground_layer=0, changes='First version.',
                   description='Flush plaza piece (ground_layer 0: the paving replaces the grass, level with the lawn; '
                               'the mod\'s builders can\'t do that yet). The founding king as a clay soldier in stone, '
                               'sword raised and shield on his arm, on a stepped plinth with a plaque, in a paved round '
                               '9 across. L2 lantern posts, bushes and flowers, a shining blade, the arms on his shield, '
                               'moss; L3 a gold-look crown, a red cloak, candles, banners and flower wreaths.')]
