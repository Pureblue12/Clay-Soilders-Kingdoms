"""Mine, stage 3 (tier 7, look A, gems): all of mine 2 plus a winch house that hauls the hoist rope, a gem cutter's
bench and a yard of raw crystal."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, HANG, connect, shutters
from batch6a import mine2, minekit as k

W, H, D = 28, 9, 11
X0, X1, Z0, Z1 = 20, 26, 2, 6        # the winch house's walls (7 long, 5 deep), ridge along x at z 4
TOPW = 4                             # last wall layer (y 0 stone, 1..4 timber frame), ridge y 7, cap y 8
ZM = (Z0 + Z1) // 2


def winch_house_l1(s):
    """Stone footing, dark oak posts, white plaster infill, a 45-degree spruce roof with the ridge along the house
    (no overhang on the headframe side, where the rope comes in), windows, a door, the rope from the sheave to the
    ridge pulley and down to the winch drum inside, a chest and a lantern."""
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if X0 < x < X1 and Z0 < z < Z1:
                continue
            post = (x in (X0, X1) and z in (Z0, Z1)) or (x == 23 and z in (Z0, Z1)) or (z == ZM and x in (X0, X1))
            for y in range(0, TOPW + 1):
                if y == 0:
                    b = 'cobblestone'
                elif post:
                    b = 'dark_oak_log'
                elif y == 3:
                    b = 'stripped_spruce_log'           # a rail half way up the walls
                else:
                    b = 'white_terracotta'
                props = {'axis': 'y'} if b == 'dark_oak_log' else \
                    {'axis': 'x' if z in (Z0, Z1) else 'z'} if b == 'stripped_spruce_log' else {}
                P(s, x, y, z, b, **props)
    for x, z in ((21, Z0), (25, Z0), (22, Z1), (24, Z1)):   # windows
        s.blocks.pop((x, 2, z)); s.layer_of.pop((x, 2, z))
        P(s, x, 2, z, 'glass_pane', waterlogged='false')
    for y in (0, 1):                                   # the door
        s.blocks.pop((22, y, Z0)); s.layer_of.pop((22, y, Z0))
    s.door(22, 0, Z0, facing='north', block='spruce_door')
    half = (Z1 - Z0) // 2
    for x in range(X0, X1 + 1):                        # top plates on the long walls
        for z in (Z0, Z1):
            P(s, x, TOPW + 1, z, 'dark_oak_log', axis='x')
    for x in range(X0, X1 + 2):                        # the roof: one over at the far end and along the eaves
        P(s, x, TOPW + 1, Z0 - 1, 'dark_oak_stairs', facing='south', **STAIR)
        P(s, x, TOPW + 1, Z1 + 1, 'dark_oak_stairs', facing='north', **STAIR)
        for i in range(half):
            A(s, x, TOPW + 2 + i, Z0 + i, 'spruce_stairs', facing='south', **STAIR)
            A(s, x, TOPW + 2 + i, Z1 - i, 'spruce_stairs', facing='north', **STAIR)
        P(s, x, TOPW + 1 + half, ZM, 'dark_oak_planks')
        P(s, x, TOPW + 2 + half, ZM, 'spruce_slab', **SLAB)
    for x in (X0, X1):                                 # gable walls (planks over the plaster)
        for z in range(Z0 + 1, Z1):
            i = min(z - Z0, Z1 - z)
            for y in range(TOPW + 1, TOPW + 2 + i if i < half else TOPW + 1 + half):
                A(s, x, y, z, 'spruce_planks')
    for i in range(half):                              # corbels under the far gable's overhang
        P(s, X1 + 1, TOPW + 1 + i, Z0 + i, 'spruce_stairs', facing='north', **TOP)
        P(s, X1 + 1, TOPW + 1 + i, Z1 - i, 'spruce_stairs', facing='south', **TOP)
    for x in range(k.SX + 1, X0):                      # the hoist rope: sheave to the ridge pulley
        A(s, x, k.DECK + 1, ZM, 'chain', axis='x')
    for y in range(2, TOPW + 3):                       # down from the ridge to the drum
        P(s, 21, y, ZM, 'chain', axis='y')
    for z in (3, 5):                                   # the winch: a log drum on posts, with a crank wheel
        P(s, 21, 0, z, 'spruce_fence')
    for z in (3, 4, 5):
        P(s, 21, 1, z, 'stripped_spruce_log', axis='z')
    P(s, 22, 1, 3, 'grindstone', face='wall', facing='east')
    s.at(1, 'fittings')
    P(s, 25, 0, 5, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 25, 0, 3, 'barrel', facing='up', open='false')
    P(s, 24, 0, 5, 'crafting_table')
    P(s, 23, 2, Z0 - 1, 'wall_torch', facing='north')


def gem_bench_l2(s):
    """Stage 3's new job, gem cutting: a pent-roofed bench along the front of the winch house (posts, a plate, a
    spruce roof under the eaves) with a stonecutter, a polishing wheel, a jeweller's table and barrels of rough
    crystal, lanterns under it, shutters on the windows."""
    for x in (X0, 23, X1):                             # posts and plate of the pent roof
        for y in range(0, 3):
            P(s, x, y, 0, 'spruce_log', axis='y')
    for x in range(X0, X1 + 1):
        P(s, x, 3, 0, 'stripped_spruce_log', axis='x')
        P(s, x, 4, 1, 'spruce_stairs', facing='south', **STAIR)
    for x in range(X0, X1 + 1):
        A(s, x, 4, 0, 'spruce_slab', **SLAB)
    P(s, 21, 0, 1, 'stonecutter', facing='south')
    P(s, 24, 0, 1, 'grindstone', face='floor', facing='south')
    P(s, 25, 0, 1, 'spruce_slab', **SLAB_TOP)          # the jeweller's table
    P(s, 25, 1, 1, 'amethyst_cluster', facing='up', waterlogged='false')
    for x in (19, 27):
        P(s, x, 0, 1, 'barrel', facing='up', open='false')
        P(s, x, 1, 1, 'amethyst_cluster', facing='up', waterlogged='false')
    for x in (21, 25):                                 # hanging from the pent roof
        P(s, x, 3, 1, 'lantern', **HANG)
    for x in (22, 24):                                 # shutters on the back windows
        shutters(s, x, 2, Z1, 'south')


def crystal_yard_l3(s):
    """Stage 3's gems: a yard of raw crystal behind the winch house (a calcite and amethyst heap with clusters
    growing on it), purple banners, flower boxes, a lantern on the far gable, a "Gem Mine" sign, flowers."""
    heap = {(20, 8): 2, (21, 8): 1, (20, 9): 3, (21, 9): 2, (22, 9): 1, (19, 9): 1, (20, 10): 2, (21, 10): 2,
            (19, 10): 1, (22, 10): 1}
    for (x, z), h in heap.items():
        for y in range(h):
            top = y == h - 1
            P(s, x, y, z, 'amethyst_block' if top and (x + z) % 2 == 0 else 'calcite' if y == 0 else 'amethyst_block')
        if (x + z) % 3 != 1:
            P(s, x, h, z, 'amethyst_cluster' if h > 1 else 'medium_amethyst_bud', facing='up', waterlogged='false')
    for x, z in ((24, 9), (25, 9)):                     # crates of cut gems by the heap
        P(s, x, 0, z, 'barrel', facing='up', open='true')
    s.decorate(24, 0, 9, 'item', item='minecraft:amethyst_shard', note='cut gems in the open barrels')
    P(s, 26, 0, 9, 'chest', facing='north', type='single', waterlogged='false')
    for x in (X0, X1):
        P(s, x, 3, Z0 - 1, 'purple_wall_banner', facing='north')
    for x, f in ((22, 'allium'), (24, 'cornflower')):  # planters under the back windows
        P(s, x, 1, Z1 + 1, 'coarse_dirt')
        P(s, x, 1, Z1 + 2, 'spruce_trapdoor', facing='south', **OPEN)
        P(s, x, 2, Z1 + 1, f)
        P(s, x, 0, Z1 + 1, 'spruce_planks')
    P(s, 23, 1, Z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(23, 1, Z0 - 1, 'sign_text', lines=['', 'Gem Mine', '', ''])
    for x, z, b in ((19, 4, 'allium'), (19, 6, 'fern'), (27, 4, 'cornflower'), (27, 9, 'fern'), (23, 9, 'allium'),
                    (27, 7, 'poppy')):
        if (x, 0, z) not in s.blocks:
            P(s, x, 0, z, b)


def mine3():
    """Level 1: mine 2's level 1 (the stage-1 adit, the headframe over the shaft) and the winch house: a stone
    footing, dark oak frame with white plaster, a spruce roof, the hoist rope running from the sheave to the ridge
    pulley and down to the winch drum with its crank wheel inside, a chest, a barrel, a bench.
    Level 2: mine 2's level 2 (sorting shed, ladder, spur) and a new job, gem cutting: a pent-roofed bench along the
    winch house front with a stonecutter, a polishing wheel and a jeweller's table, barrels of rough crystal.
    Level 3: mine 2's level 3 (the gold lockup, banners, sign) and the gems: a heap of raw crystal (calcite and
    amethyst with clusters growing on it) in the yard behind, barrels of cut gems, purple banners, flower boxes, a
    "Gem Mine" sign."""
    s = Structure('mine3', (W, H, D), palette='oak_a2')
    mine2.build(s, winch=True)
    s.at(1, 'shell')
    winch_house_l1(s)
    s.at(2, 'flourish2')
    gem_bench_l2(s)
    s.at(3, 'flourish3')
    crystal_yard_l3(s)
    return connect(s)


STRUCTURES = [dict(path='mine3', name='Mine (stage 3, gems)', tier=7, zone='outer', role='resource', group='mine',
                   terrain='flexible', door='front', make=mine3, changes='First version.',
                   description='Over budget: 841 blocks vs 170 (it holds stages 1 and 2 too). Stage 3 of the mine (gems, tier 7): all of mine 2 (adit, headframe, sorting shed, '
                               'gold lockup) plus a timber-and-plaster winch house that hauls the hoist rope from the '
                               'headframe. L2 adds a gem cutter\'s bench under a pent roof along its front; L3 a yard '
                               'of raw crystal (amethyst) behind it, barrels of cut gems, purple banners, flower boxes '
                               'and a sign.')]
