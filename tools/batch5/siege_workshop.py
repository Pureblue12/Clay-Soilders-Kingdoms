"""Siege workshop (tier 6, docs/STRUCTURES.md "Siege workshop"): a big open-sided timber shed in the outer zone where
engineers build siege engines; a catapult stands half-built in its open front, half out in the yard (the signature).

Layout (x 0..14, z 0..12; front z = 0, the open gable end the engines roll out of):
  - The shed (x 4..10, z 3..11): oak log posts on stone brick plinths at x 4 / x 10 and z 3, 7, 11, knee braces under
    dark oak top plates, a spruce stair gable roof (dark oak eaves and ridge, slab cap) with one block of overhang,
    9 tall. The front gable is open below a raised tie beam (y 5) with braces, plaster and a king post above it; the
    back wall is stone with a plaster gable in a dark oak frame. Open on both long sides.
  - The tool store (west, x 0..3, z 4..10): a catslide lean-to carrying the main roof's slope down to a low stone
    wall, stone end walls, open to the shed.
  - The yard in front (z 0..2): a cobble and gravel apron the engine stands on; log piles under the east eaves; a bush
    and flowers on the grass.
  - Inside (L1): the catapult's chassis on the apron and deck (rails, cross beams, trapdoor wheels) with its A-frame,
    stop bar and axle; the crafting table (workstation) at the back with the tool chest, barrels, shelves, a sawhorse.
Level 2 (the engine takes shape): the throwing arm and its cup on the catapult, held by a hoist chain from the tie beam;
banners on the front posts, shelves and a grindstone in the store.
Level 3 (the war shop): a long battering ram hanging in its frame on the east side, lanterns under the middle tie
beam and in the store, a pile of stone shot, barrels, more flowers, an axe over the bench (decoration step).
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, connect, noise, gable_roof
from batch5.mil_kit import masonry

W, D, H = 15, 13, 9
X0, X1, Z0, Z1 = 4, 10, 3, 11
POSTS_Z = (3, 7, 11)
MID = 7
LOG_Y = dict(axis='y')


def siege_workshop():
    s = Structure('siege_workshop', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    # floor: trodden earth and gravel, a plank deck under the engine's back half, a cobble apron in front
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) and z in POSTS_Z:
                continue
            if 5 <= x <= 9 and z <= 6:
                P(s, x, 0, z, 'spruce_planks' if x in (5, 9) or z == 6 else 'oak_planks')
            else:
                n = noise(x, 0, z, 12)
                P(s, x, 0, z, 'gravel' if n < 0.4 else 'coarse_dirt' if n < 0.75 else 'cobblestone')
    for x in range(5, 10):
        for z in range(0, Z0):
            P(s, x, 0, z, 'gravel' if noise(x, 0, z, 5) < 0.35 else 'cobblestone')
    # posts on stone plinths, top plates, knee braces
    for x in (X0, X1):
        for z in POSTS_Z:
            P(s, x, 0, z, 'cobblestone'); P(s, x, 1, z, 'stone_bricks')
            P(s, x, 2, z, 'oak_log', **LOG_Y); P(s, x, 3, z, 'oak_log', **LOG_Y)
        for z in range(Z0, Z1 + 1):
            P(s, x, 4, z, 'dark_oak_log', axis='z')
        for z in POSTS_Z:
            for dz, f in ((-1, 'south'), (1, 'north')):
                if Z0 <= z + dz <= Z1 and (z + dz) not in POSTS_Z:
                    P(s, x, 3, z + dz, 'oak_stairs', facing=f, **TOP)
    # the front: a tie beam raised to y 5 with braces, plaster and a king post above it (the engine's doorway)
    for x in range(X0 + 1, X1):
        P(s, x, 5, Z0, 'dark_oak_log', axis='x')
    P(s, X0 + 1, 4, Z0, 'dark_oak_stairs', facing='west', **TOP)
    P(s, X1 - 1, 4, Z0, 'dark_oak_stairs', facing='east', **TOP)
    P(s, MID - 1, 6, Z0, 'white_terracotta'); P(s, MID + 1, 6, Z0, 'white_terracotta')
    P(s, MID, 6, Z0, 'dark_oak_log', **LOG_Y)
    # a tie beam across the middle bay (lanterns hang from it at L3)
    for x in range(X0 + 1, X1):
        P(s, x, 4, 7, 'dark_oak_log', axis='x')
    # the back wall: stone up to the plates, a dark oak tie beam, a plaster gable with a king post
    for x in range(X0 + 1, X1):
        for y in (1, 2, 3):
            P(s, x, y, Z1, masonry(x, y, Z1, 9))
        P(s, x, 4, Z1, 'dark_oak_log', axis='x')
    P(s, MID, 5, Z1, 'dark_oak_log', **LOG_Y); P(s, MID, 6, Z1, 'dark_oak_log', **LOG_Y)
    gable_roof(s, X0, X1, Z0, Z1, 3, front=1, back=1, gable='white_terracotta', corbels=False, gable_at=(Z1,))
    # the tool store: a catslide lean-to on the west (x 0..3, z 4..10)
    for z in range(4, 11):
        P(s, 1, 0, z, 'cobblestone'); P(s, 1, 1, z, masonry(1, 2, z, 4))
        if z not in (4, 10):
            for x in (2, 3):
                P(s, x, 0, z, 'spruce_planks')
    for z in (4, 10):
        for x, top in ((2, 2), (3, 3)):
            P(s, x, 0, z, 'cobblestone')
            for y in range(1, top + 1):
                P(s, x, y, z, masonry(x, y, z, 4))
    for z in range(Z0, Z1 + 1):
        P(s, 2, 3, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, 1, 2, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, 0, 1, z, 'dark_oak_stairs', facing='east', **STAIR)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    # the catapult in the open front, half out on the apron: rails, cross beams, trapdoor wheels
    for z in range(0, 6):
        P(s, MID - 1, 1, z, 'stripped_oak_log', axis='z'); P(s, MID + 1, 1, z, 'stripped_oak_log', axis='z')
    for z in (0, 5):
        P(s, MID, 1, z, 'stripped_oak_log', axis='x')
    for z in (1, 4):
        P(s, MID - 2, 1, z, 'spruce_trapdoor', facing='west', **OPEN)
        P(s, MID + 2, 1, z, 'spruce_trapdoor', facing='east', **OPEN)
    # its A-frame: two uprights braced fore and aft, the stop bar across the top, the axle behind
    for x in (MID - 1, MID + 1):
        P(s, x, 2, 2, 'oak_fence')
        P(s, x, 2, 1, 'oak_stairs', facing='south', **STAIR)
        P(s, x, 2, 3, 'oak_stairs', facing='north', **STAIR)
    for x in range(MID - 1, MID + 2):
        P(s, x, 3, 2, 'stripped_oak_log', axis='x')
        P(s, x, 2, 4, 'stripped_spruce_log', axis='x')
    # the workbench at the back: crafting table (workstation), tool chest, barrels, shelves
    P(s, MID, 1, 10, 'crafting_table')
    P(s, MID - 1, 1, 10, 'chest', facing='north', type='single', waterlogged='false')
    P(s, MID + 1, 1, 10, 'barrel', facing='up', open='false')
    P(s, MID + 2, 1, 10, 'barrel', facing='up', open='false')
    for x in (MID - 1, MID + 1):
        P(s, x, 3, 10, 'spruce_trapdoor', facing='north', **SHELF)
    # a sawhorse with a plank on it, a plank stack
    P(s, 5, 1, 8, 'oak_fence'); P(s, 5, 1, 9, 'oak_fence')
    P(s, 5, 2, 8, 'oak_slab', **SLAB); P(s, 5, 2, 9, 'oak_slab', **SLAB)
    P(s, 9, 1, 8, 'oak_planks'); P(s, 9, 1, 9, 'oak_planks'); P(s, 9, 2, 9, 'oak_slab', **SLAB)
    # light: torches on the middle posts
    P(s, X0 + 1, 2, 7, 'wall_torch', facing='east'); P(s, X1 - 1, 2, 7, 'wall_torch', facing='west')
    # the store: chests and barrels along its low wall
    for z, b in ((5, 'barrel'), (6, 'chest'), (8, 'chest'), (9, 'barrel')):
        P(s, 2, 1, z, b, **({'facing': 'up', 'open': 'false'} if b == 'barrel' else
                            {'facing': 'east', 'type': 'single', 'waterlogged': 'false'}))
    # log piles under the east eaves
    for z in (4, 5, 6, 8, 9):
        P(s, 11, 0, z, 'oak_log', axis='z')
    for z in (4, 5, 8):
        P(s, 11, 1, z, 'oak_log', axis='z')
    # life: a bush at the front corners, flowers on the grass
    P(s, 3, 0, 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    P(s, 11, 0, 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    for (x, z) in ((3, 2), (4, 1), (11, 2), (10, 1)):
        P(s, x, 0, z, 'poppy')

    # ------------------------------------------------------------------ L2: the engine takes shape
    s.at(2, 'flourish2')
    P(s, MID, 3, 3, 'spruce_stairs', facing='north', **STAIR)       # the throwing arm, up from the axle onto the bar
    P(s, MID, 4, 2, 'stripped_spruce_log', axis='z')
    P(s, MID, 5, 2, 'cauldron')                                      # the cup
    P(s, MID, 4, 3, 'chain', axis='y')                               # the hoist holding the arm from the tie beam
    for x in (X0, X1):                                               # banners on the front posts
        P(s, x, 3, Z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, Z0 - 1, 'banner_pattern', banner='red_wall_banner', note='the engineers\' colours')
    P(s, 3, 2, 5, 'spruce_trapdoor', facing='south', **SHELF)       # shelves on the store's end walls
    P(s, 3, 2, 9, 'spruce_trapdoor', facing='north', **SHELF)
    P(s, 3, 1, 7, 'grindstone', face='floor', facing='east')         # the store's grindstone
    P(s, 6, 1, 9, 'barrel', facing='up', open='false')              # pitch and nails by the bench
    P(s, 8, 1, 9, 'oak_planks'); P(s, 8, 2, 9, 'oak_slab', **SLAB)

    # ------------------------------------------------------------------ L3: the war shop
    s.at(3, 'flourish3')
    # a long battering ram hanging in its frame on the east side (x 12..14, z 3..11)
    for x in (12, 14):
        for z in (5, 9):
            P(s, x, 0, z, 'cobblestone'); P(s, x, 1, z, 'spruce_log', **LOG_Y); P(s, x, 2, z, 'spruce_log', **LOG_Y)
        for z in range(4, 11):
            P(s, x, 3, z, 'spruce_log', axis='z')
    for z in (5, 9):
        P(s, 13, 3, z, 'spruce_log', axis='x')
        P(s, 13, 2, z, 'chain', axis='y')
    for z in range(4, 12):
        P(s, 13, 1, z, 'dark_oak_log', axis='z')
    P(s, 13, 1, 3, 'polished_andesite')                              # the ram's capped head
    for x in (12, 14):                                               # a slab roof keeps the rain off the ram
        P(s, x, 4, 4, 'spruce_slab', **SLAB); P(s, x, 4, 10, 'spruce_slab', **SLAB)
    P(s, 13, 4, 5, 'spruce_slab', **SLAB); P(s, 13, 4, 9, 'spruce_slab', **SLAB)
    # lanterns under the middle tie beam and in the store
    P(s, MID - 1, 3, 7, 'lantern', **HANG); P(s, MID + 1, 3, 7, 'lantern', **HANG)
    P(s, 2, 2, 7, 'lantern', **HANG)
    # a pile of stone shot by the engine
    P(s, 3, 0, 0, 'andesite'); P(s, 4, 0, 0, 'andesite'); P(s, 2, 0, 0, 'andesite'); P(s, 3, 1, 0, 'andesite_slab', type='bottom', waterlogged='false')
    # barrels and a cart wheel by the ram, more flowers
    P(s, 10, 0, 0, 'barrel', facing='up', open='false'); P(s, 11, 0, 0, 'barrel', facing='up', open='false')
    P(s, 10, 1, 0, 'barrel', facing='up', open='false')
    for (x, z, f) in ((12, 2, 'dandelion'), (14, 2, 'poppy'), (12, 12, 'dandelion'), (0, 2, 'poppy'), (0, 12, 'dandelion')):
        P(s, x, 0, z, f)
    P(s, 14, 0, 12, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.decorate(MID, 2, 10, 'item_frame', item='minecraft:iron_axe', facing='north', note='an axe over the workbench')
    return connect(s)


STRUCTURES = [dict(path='siege_workshop', name='Siege workshop', tier=6, zone='outer', role='military',
                   group='siege_workshop', terrain='flexible', door=None, make=siege_workshop,
                   changes='Quality-check notes: the catapult now stands in the open front, half out on a cobble apron, '
                           'with its A-frame, stop bar and axle already at L1 and the arm, cup and hoist chain at L2; '
                           'the front tie beam is raised to y 5 (braced) and the front gable closed above it with '
                           'plaster and a king post; the back gable is stone and plaster with no hole; the floating '
                           'sawhorse slabs now sit on fence legs; the tool store is a sloped catslide lean-to with '
                           'stone walls; stone plinths under the posts, a bush and flowers on the grass at L1; L3 has '
                           'a long ram (8 logs and a capped head) hanging on chains in its own frame on the east side.',
                   description='Over budget: about 480 blocks vs 300. Open-sided timber shed where engineers build '
                               'siege engines: a catapult in the open front (L2 adds its arm and cup), crafting table, '
                               'log piles, a catslide tool store; L3 a long battering ram in its frame, lanterns.')]
