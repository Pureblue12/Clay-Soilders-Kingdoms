"""Stonemason (tier 4, look A): an open stone yard round a covered work shed (docs/STRUCTURES.md, "Stonemason").

Ground rule: a flush piece (ground_layer=0): layer y = 0 REPLACES the top ground block, so the shed floor and the yard
paving are level with the grass; everything standing starts at y = 1.

Layout (11 wide x 10 deep, front z = 0):
- Shed (back left, walls x 1..5, z 4..8): open at the front (a 3-wide bay under a spruce lintel), stone brick walls on
  a cobblestone plinth course with spruce log corner posts, a spruce gable roof (dark oak eaves, slab ridge) with one
  block of overhang all round. Inside, the covered bench: grindstone (L1 workstation: chisels; the stonecutter, the
  brief's workstation, is not builder-tested yet and comes at L2), a cutting bench, chest, barrel, torches.
- Yard (an L round the shed: the front x 0..10, z 0..3 and the right x 7..10, z 4..9): gravel paving with a cobblestone
  path from the gate to the bay, a low cobblestone wall all round with stone brick gate piers (opening 3 wide, in front
  of the shed). Rough stone waits in the front left, dressed stone is stacked at the back right, a banker (work block)
  stands in the right yard.
Level 2 (the working mason): the stonecutter, the display stand of half-cut blocks along the front wall (the signature:
stairs, slabs, walls, chiseled and polished blocks), finished blocks on a pallet, a lantern on a chain in the shed, a
tool shelf, the shop sign, a block being dressed on the banker, lanterns on the gate piers.
Level 3 (the statue in progress and life): a half-carved soldier statue on a plinth in the right yard with a scaffold
beside it and chips round it, a banner, flower pots on the wall, moss on the rough stone, flowers and bushes."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, SLAB, SLAB_TOP, SHELF, HANG, STAND, LEAVES, connect, noise, gable_roof

SCAFFOLD = dict(bottom='false', distance='0', waterlogged='false')


def stonemason():
    W, D = 11, 10
    s = Structure('stonemason', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 1, 5, 4, 8                     # shed walls
    mid = 3
    top = 3                                         # last wall layer (walls y 1..3)
    gate = (2, 3, 4)                                # the gate opening in the front wall
    s.at(1, 'shell')
    # flush floor of the shed, the yard paving and the path
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            P(s, x, 0, z, 'cobblestone' if x in (x0, x1) or z == z1 else 'stone_bricks')
    for x in range(0, W):
        for z in range(0, D):
            if (x, 0, z) in s.blocks:
                continue
            yard = z < z0 or x > x1 + 1
            edge = x in (0, W - 1) or z == 0 or (z == D - 1 and x > x1 + 1)
            if not yard:
                continue
            if edge:
                P(s, x, 0, z, 'cobblestone')                       # under the yard wall
            elif x == mid and z < z0:
                P(s, x, 0, z, 'cobblestone')                       # the path to the bay
            else:
                P(s, x, 0, z, 'gravel' if noise(x, 0, z, 4) < 0.7 else 'coarse_dirt')
    for x in gate:
        s.remove(x, 0, 0)
        P(s, x, 0, 0, 'cobblestone')
    # shed walls: spruce corner posts, cobblestone plinth course, stone bricks above
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
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
    # the yard wall: low cobblestone wall all round the yard, stone brick piers at the gate and the corners
    for x in range(0, W):
        if x not in gate:
            P(s, x, 1, 0, 'cobblestone_wall')
    for z in range(1, z0 - 1):
        P(s, 0, 1, z, 'cobblestone_wall')
    for z in range(1, D):
        P(s, W - 1, 1, z, 'cobblestone_wall')
    for x in range(x1 + 2, W - 1):
        P(s, x, 1, D - 1, 'cobblestone_wall')
    for x in (gate[0] - 1, gate[-1] + 1):
        s.remove(x, 1, 0)
        P(s, x, 1, 0, 'stone_bricks'); P(s, x, 2, 0, 'stone_bricks')
    for x, z in ((W - 1, 0), (W - 1, D - 1)):
        s.remove(x, 1, z)
        P(s, x, 1, z, 'stone_bricks')

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
    # rough stone waiting in the front left of the yard
    for x, y, z in ((1, 1, 1), (1, 1, 2), (1, 2, 1)):
        P(s, x, y, z, 'mossy_cobblestone' if (x + y + z) % 3 == 0 else 'cobblestone')
    P(s, 1, 1, 3, 'cobblestone_slab', **SLAB)
    # dressed stone stacked at the back right of the yard
    for x, y, z in ((9, 1, 8), (9, 2, 8), (8, 1, 8), (9, 1, 7)):
        P(s, x, y, z, 'stone_bricks')
    P(s, 8, 2, 8, 'cobblestone_slab', **SLAB)
    # the banker: a block on a stump where the mason dresses stone in the open
    P(s, 8, 1, 5, 'stripped_spruce_log', axis='y')

    s.at(2, 'flourish2')
    P(s, mid, 1, z1 - 1, 'stonecutter', facing='north')                        # the workstation
    for y in (top + 2, top + 1):                                               # lantern on a chain from the ridge
        P(s, mid, y, 6, 'chain', axis='y')
    P(s, mid, top, 6, 'lantern', **HANG)
    for z in (z0 + 1, z0 + 2):                                                 # tool shelf over the bench
        P(s, x0 + 1, 3, z, 'spruce_trapdoor', facing='east', **SHELF)
    s.decorate(x0 + 1, 2, z0 + 1, 'item', item='minecraft:iron_pickaxe', note='chisel and mallet on the bench')
    # the display stand (signature): half-cut blocks on a stepped stand along the front wall, facing the street
    for x in (6, 7, 8, 9):
        P(s, x, 1, 1, 'polished_andesite')
    for x, b, p in ((6, 'stone_brick_stairs', dict(facing='south', **STAIR)), (7, 'chiseled_stone_bricks', {}),
                    (8, 'stone_brick_wall', {}), (9, 'polished_andesite_stairs', dict(facing='south', **STAIR))):
        P(s, x, 2, 1, b, **p)
    for x, b, p in ((6, 'stone_brick_slab', SLAB), (8, 'smooth_stone_slab', SLAB), (9, 'andesite_wall', {})):
        P(s, x, 1, 2, b, **p)
    # finished blocks on a pallet in the right yard
    for z in (6, 7):
        P(s, 7, 1, z, 'spruce_slab', **SLAB)
    P(s, 7, 1, 8, 'spruce_slab', **SLAB)
    P(s, 9, 3, 8, 'stone_brick_slab', **SLAB)
    P(s, 8, 2, 5, 'stone_brick_slab', **SLAB)                                   # a block being dressed on the banker
    s.decorate(8, 2, 5, 'item', item='minecraft:stone_bricks', note='chips on the banker')
    P(s, x0, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')  # the shop sign on the left post
    s.decorate(x0, 2, z0 - 1, 'sign_text', lines=['', 'Stonemason', 'cut to order', ''])
    for x in (gate[0] - 1, gate[-1] + 1):                                      # lanterns on the gate piers
        P(s, x, 3, 0, 'lantern', **STAND)
    for x in (x0, x1):                                                         # lanterns under the front eave
        A(s, x, top, z0 - 1, 'lantern', **HANG)

    s.at(3, 'flourish3')
    # the statue in progress: a soldier on a chiseled plinth, the head still rough, a scaffold beside it
    sx, sz = 8, 3
    P(s, sx, 1, sz, 'chiseled_stone_bricks')
    P(s, sx, 2, sz, 'polished_andesite')
    P(s, sx, 3, sz, 'andesite_wall')
    P(s, sx, 4, sz, 'stone_button', face='floor', facing='north', powered='false')
    for y in (1, 2, 3):
        P(s, sx + 1, y, sz, 'scaffolding', **SCAFFOLD)
    for x, z in ((sx - 1, sz), (sx, sz + 1)):                                  # stone chips round it
        P(s, x, 1, z, 'cobblestone_slab', **SLAB)
    s.decorate(sx, 2, sz, 'item', item='minecraft:iron_pickaxe', note='a chisel leaning on the statue')
    P(s, x1, 2, z0 - 1, 'gray_wall_banner', facing='north')
    s.decorate(x1, 2, z0 - 1, 'banner_pattern', banner='gray_wall_banner', note="a mason's square and chisel")
    for x, z in ((0, 1), (W - 1, 3), (W - 1, 6)):
        P(s, x, 2, z, 'flower_pot')
        s.decorate(x, 2, z, 'potted_plant', plant='red_tulip')
    P(s, 1, 3, 1, 'moss_carpet'); P(s, 1, 2, 2, 'moss_carpet')
    for x, z, b in ((0, z0, 'leaves'), (0, z0 + 2, 'poppy'), (0, z1 + 1, 'leaves'), (1, D - 1, 'cornflower'),
                    (x1 + 1, D - 1, 'fern'), (6, 7, 'dandelion'), (8, 6, 'azure_bluet'), (9, 4, 'oxeye_daisy')):
        if b == 'leaves':
            A(s, x, 1, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 1, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='stonemason', name='Stonemason', tier=4, zone='inner', role='workshop', group='stonemason',
                   terrain='flexible', door='front', make=stonemason, ground_layer=0, changes='First version.',
                   description='Over budget: 312 blocks vs 125 (first build 262 vs 90; the walled, paved yard is most of it). Flush piece (ground_layer 0: the mod does not support it yet). '
                               'Walled stone yard round an open stone brick shed under a spruce gable roof: grindstone, '
                               'cutting bench, rough and dressed stone, a banker; L2 adds the stonecutter and a stand '
                               'of half-cut blocks along the front wall; L3 a statue in progress with its scaffold, '
                               'banner, flowers.')]
