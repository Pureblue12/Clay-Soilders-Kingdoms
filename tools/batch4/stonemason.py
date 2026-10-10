"""Stonemason (tier 4, look A): an open stone yard in front of a covered work shed (docs/STRUCTURES.md, "Stonemason").

Ground rule: a flush piece (ground_layer=0): layer y = 0 REPLACES the top ground block, so the shed floor and the yard
paving are level with the grass; everything standing starts at y = 1.

Layout (9 wide x 10 deep, front z = 0):
- Shed (walls x 2..6, z 4..8): open at the front (a 3-wide bay under a spruce lintel), stone brick walls on a
  cobblestone plinth course with spruce log corner posts, a spruce gable roof (dark oak eaves, slab ridge) with one block
  of overhang all round. Inside the covered bench: grindstone (L1 workstation: chisels; the stonecutter, the brief's
  workstation, is not builder-tested yet and comes at L2), a cutting bench, chest, barrel, torches.
- Yard (z 0..3): gravel paving with a cobblestone path to the shed, low cobblestone walls round it with stone brick
  gate piers (opening 3 wide), rough stone waiting by the left wall, dressed stone stacked by the right wall.
- Sides: rough blocks under the left eave, finished blocks on pallets under the right eave.
Level 2 (the working mason): the stonecutter, the display stand of half-cut blocks (the signature: stairs, slabs,
walls, chiseled and polished blocks) by the gate, a lantern on a chain in the shed, a tool shelf, the shop sign, a
block on the banker in the yard, lanterns on the gate piers.
Level 3 (the statue in progress and life): a half-carved soldier statue on a plinth in the yard with a scaffold beside
it and chips on the ground, a bench, a banner, flower pots on the walls, flowers and bushes, moss on the rough stone."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof)


def stonemason():
    W, D = 9, 10
    s = Structure('stonemason', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 6, 4, 8                     # shed walls
    mid = 4
    top = 3                                         # last wall layer (walls y 1..3)
    s.at(1, 'shell')
    # flush floor of the shed and the yard paving
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            P(s, x, 0, z, 'cobblestone' if x in (x0, x1) or z == z1 else 'stone_bricks')
    for x in range(0, W):
        for z in range(0, z0):
            if x == mid or (z == z0 - 1 and x0 <= x <= x1):
                P(s, x, 0, z, 'cobblestone')                      # the path and the apron in front of the bay
            elif 1 <= x <= W - 2 and z >= 1:
                P(s, x, 0, z, 'gravel' if noise(x, 0, z, 4) < 0.7 else 'coarse_dirt')
    # shed walls: spruce corner posts, cobblestone plinth course, stone bricks above
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z == z1 or z == z0):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'spruce_log', axis='y')
                elif z == z0:                                      # open bay: lintel only
                    if y == top:
                        P(s, x, y, z, 'spruce_log', axis='x')
                elif y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 2) < 0.25 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    windows = [(x0, 2, 6), (x1, 2, 6), (mid, 2, z1)]
    for w in windows:
        s.remove(*w)
    gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, gable='spruce_planks', plates='spruce_log')
    # yard walls: low cobblestone walls on the sides and front, stone brick gate piers (opening x 3..5)
    for z in range(0, z0 - 1):
        P(s, 0, 1, z, 'cobblestone_wall'); P(s, W - 1, 1, z, 'cobblestone_wall')
    for x in (1, W - 2):
        P(s, x, 1, 0, 'cobblestone_wall')
    for x in (x0, x1):
        P(s, x, 0, 0, 'stone_bricks')
        P(s, x, 1, 0, 'stone_bricks'); P(s, x, 2, 0, 'stone_bricks')
    for x in (0, W - 1):
        P(s, x, 0, 0, 'cobblestone'); P(s, x, 0, z0 - 1, 'cobblestone')

    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    P(s, x0 + 1, 1, z1 - 1, 'grindstone', face='floor', facing='east')         # L1 workstation
    P(s, x1 - 1, 1, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, x1 - 1, 1, z1 - 2, 'barrel', facing='up', open='false')
    for z in (z0 + 1, z0 + 2):                                                 # the cutting bench on the left wall
        P(s, x0 + 1, 1, z, 'spruce_slab', **SLAB_TOP)
    for x in (x0 + 1, x1 - 1):                                                 # torches on the back wall
        P(s, x, 3, z1 - 1, 'wall_torch', facing='north')
    # rough stone waiting by the left yard wall, dressed stone stacked by the right one
    for x, y, z, b in ((1, 1, 1, 'cobblestone'), (1, 1, 2, 'mossy_cobblestone'), (1, 2, 1, 'cobblestone'),
                       (2, 1, 2, 'cobblestone_slab'),
                       (7, 1, 1, 'stone_bricks'), (7, 2, 1, 'stone_bricks'), (7, 1, 2, 'stone_bricks')):
        P(s, x, y, z, b, **(SLAB if b.endswith('slab') else {}))
    # under the left eave: rough blocks; under the right eave: finished blocks on a pallet
    for z, h in ((5, 2), (6, 1), (7, 2)):
        for y in range(1, h + 1):
            P(s, 1, y, z, 'cobblestone' if (y + z) % 2 else 'mossy_cobblestone')
    for z in (5, 6, 7):
        P(s, 7, 1, z, 'spruce_slab', **SLAB)
    P(s, 7, 2, 7, 'stone_bricks') if False else None

    s.at(2, 'flourish2')
    P(s, mid, 1, z1 - 1, 'stonecutter', facing='north')                        # the workstation
    for y in (top + 2, top + 1):                                               # lantern on a chain from the ridge
        P(s, mid, y, 6, 'chain', axis='y')
    P(s, mid, top, 6, 'lantern', **HANG)
    for z in (z0 + 1, z0 + 2):                                                 # tool shelf over the bench
        P(s, x0 + 1, 3, z, 'spruce_trapdoor', facing='east', **SHELF)
    s.decorate(x0 + 1, 2, z0 + 1, 'item', item='minecraft:iron_pickaxe', note='chisel and mallet on the bench')
    # the display stand (signature): half-cut blocks on a stepped stand by the right gate pier
    P(s, 6, 1, 1, 'polished_andesite'); P(s, 6, 1, 2, 'polished_andesite')
    P(s, 6, 2, 1, 'stone_brick_stairs', facing='west', **STAIR)
    P(s, 6, 2, 2, 'chiseled_stone_bricks')
    P(s, 5, 1, 1, 'stone_brick_slab', **SLAB)
    P(s, 5, 1, 2, 'polished_andesite_stairs', facing='west', **STAIR)
    P(s, 7, 2, 2, 'stone_brick_wall')
    P(s, 7, 3, 1, 'smooth_stone_slab', **SLAB)
    # finished blocks on the pallet under the right eave
    for z, b in ((5, 'stone_bricks'), (6, 'chiseled_stone_bricks'), (7, 'stone_bricks')):
        P(s, 7, 2, z, b)
    P(s, 7, 3, 5, 'stone_brick_slab', **SLAB)
    P(s, 7, 1, 8, 'stone_brick_stairs', facing='west', **STAIR)
    # the banker (a work block) in the yard with a block being dressed on it
    P(s, 2, 1, 3, 'stripped_spruce_log', axis='y')
    P(s, 2, 2, 3, 'stone_brick_slab', **SLAB)
    P(s, x0, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')  # the shop sign on the left post
    s.decorate(x0, 2, z0 - 1, 'sign_text', lines=['', 'Stonemason', 'cut to order', ''])
    for x in (x0, x1):                                                         # lanterns on the gate piers
        P(s, x, 3, 0, 'lantern', **STAND)

    s.at(3, 'flourish3')
    # the statue in progress: a soldier on a chiseled plinth, the head still rough, a scaffold beside it
    P(s, 3, 1, 2, 'chiseled_stone_bricks')
    P(s, 3, 2, 2, 'andesite_wall')
    P(s, 3, 3, 2, 'stone_button', face='floor', facing='north', powered='false')
    P(s, 3, 1, 1, 'scaffolding', bottom='false', distance='0', waterlogged='false')
    P(s, 3, 2, 1, 'scaffolding', bottom='false', distance='0', waterlogged='false')
    P(s, 4, 1, 2, 'cobblestone_slab', **SLAB) if False else None
    s.decorate(3, 2, 2, 'item', item='minecraft:iron_pickaxe', note='a chisel leaning on the statue')
    # bench by the shed, banner on the right post, flower pots on the yard walls, flowers and bushes
    P(s, x1 + 1, 1, z0 - 1, 'spruce_stairs', facing='west', **STAIR) if False else None
    P(s, x1, 2, z0 - 1, 'gray_wall_banner', facing='north')
    s.decorate(x1, 2, z0 - 1, 'banner_pattern', banner='gray_wall_banner', note='a mason\'s square and chisel')
    for x, z in ((0, 1), (W - 1, 2)):
        P(s, x, 2, z, 'flower_pot')
        s.decorate(x, 2, z, 'potted_plant', plant='red_tulip')
    P(s, 1, 3, 1, 'moss_carpet')
    for x, z, b in ((0, z0, 'leaves'), (0, z0 + 2, 'poppy'), (0, z1, 'leaves'), (W - 1, z0, 'dandelion'),
                    (W - 1, z1 - 1, 'leaves'), (1, z1 + 1, 'cornflower'), (W - 1, z1 + 1, 'oxeye_daisy'),
                    (0, z1 + 1, 'fern')):
        if b == 'leaves':
            A(s, x, 1, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 1, z, b)
    for x in (x0 - 1, x1 + 1):                                                 # lanterns under the back eave corners
        A(s, x, top, z1 + 1, 'lantern', **HANG) if False else None
    connect(s)
    return s


STRUCTURES = [dict(path='stonemason', name='Stonemason', tier=4, zone='inner', role='workshop', group='stonemason',
                   terrain='flexible', door='front', make=stonemason, ground_layer=0, changes='First version.',
                   description='Flush piece (ground_layer 0: the mod does not support it yet). Stone yard in front of an '
                               'open stone brick shed under a spruce gable roof: grindstone, cutting bench, rough and '
                               'dressed stone; L2 adds the stonecutter and a stand of half-cut blocks by the gate; L3 a '
                               'statue in progress with its scaffold, banner, flowers.')]
