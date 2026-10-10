"""Quarry (tier 3, look A): a stone shed and a cut-stone yard by the quarry floor; L2 a small log crane, L3 the big
works (a terraced, wider pit, a treadwheel crane with a spruce boom, a stone-cutting shed, a cart track)."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, HANG, STAND, LEAVES, connect, noise, gable_roof


def rock(s, x, y, z, seed=5):
    n = noise(x, y, z, seed)
    P(s, x, y, z, 'andesite' if n < 0.3 else 'cobblestone' if n < 0.5 else 'stone')


def floor(s, x, z, seed=11):
    """The quarry floor's broken-rock surface (flush: layer 0 replaces the top ground block)."""
    n = noise(x, 0, z, seed)
    A(s, x, 0, z, 'gravel' if n < 0.3 else 'mossy_cobblestone' if n < 0.45 else 'cobblestone')


def yard(s, x, z, seed=11):
    A(s, x, 0, z, 'gravel' if noise(x, 0, z, seed) < 0.55 else 'coarse_dirt')


def quarry():
    """Quarry. Ground rule: a flush piece (ground_layer=0): layer y = 0 REPLACES the top ground block, so the shed's
    floor, the yard's paving and the quarry floor are level with the grass; everything standing starts at y = 1.
    Builders never dig: the cut face is made by the quarrymen (dig work goal, D6); the builders lay the floor's
    broken-rock surface, and the stepped rock benches stand on it (the face as the quarrymen leave it).

    - Shed (front left, walls x 1..5, z 1..5): cobblestone walls 3 high (some mossy) with oak log corners, a spruce
      stair gable roof with dark oak eaves on log top plates, door at the front. Inside: grindstone (L1), chest,
      barrel, torches; the stonecutter (the brief's workstation, not builder-tested yet) comes at L2.
    - Yard (x 6..10, z 0..7): gravel and coarse dirt with stacks of rough blocks; the lip rail at z 7 (gap at x 7).
    - Quarry floor (x 6..10, z 8..13).
    Level 2, the working quarry: a small log crane west of the cut (oak mast, SPRUCE jib, chain and a stone), the first
    rock benches at the back (z 12..13), the stonecutter, a lantern on a chain, a tool rack, fresh stone in the yard,
    rubble piles and a sled behind the shed, the sign and eave lanterns.
    Level 3, the big works (everything east of x 11 is new): the pit twice as wide (floor x 11..16) and terraced (a
    third bench, 3 high, along the whole back, a stair path up at the right), a treadwheel crane (a 5-high wheel, a
    7-high spruce mast with a long spruce boom over the cart track), the cart track along x 11 from the pit to the
    road, a stone-cutting shed (open front, stonecutter, mason's bench) with stacks of dressed stone (stone bricks,
    polished andesite, smooth stone), a banner, a lamp post, flowers and bushes, a minecart (decoration)."""
    s = Structure('quarry', (18, 9, 17), palette='oak_a2')
    x0, x1, z0, z1 = 1, 5, 1, 5                      # shed walls
    mid, midz, top = 3, 3, 4                         # walls y 1..3 above the flush base, top plates at y 4
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')         # flush base course and floor
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            P(s, x, 0, z, 'coarse_dirt')
    for y in range(1, top):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    if x in (x0, x1) and z in (z0, z1):
                        P(s, x, y, z, 'oak_log', axis='y')
                    else:
                        P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 3) < 0.2 else 'cobblestone')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    windows = [(x0, 2, midz), (x1, 2, midz), (mid, 2, z1)]
    for w in windows:
        s.remove(*w)
    for x in (x0, x1):                               # top plates
        for z in range(z0, z1 + 1):
            P(s, x, top, z, 'oak_log', axis='z')
    for z in range(z0 - 1, z1 + 2):                  # gable roof, ridge along z, one block of overhang all round
        P(s, x0 - 1, top, z, 'dark_oak_stairs', facing='east', **STAIR)
        P(s, x1 + 1, top, z, 'dark_oak_stairs', facing='west', **STAIR)
        P(s, x0, top + 1, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, x1, top + 1, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, x0 + 1, top + 2, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, x1 - 1, top + 2, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, mid, top + 2, z, 'dark_oak_planks')
    for z in (z0, z1):                               # gable ends
        for x in range(x0 + 1, x1):
            P(s, x, top, z, 'spruce_planks'); P(s, x, top + 1, z, 'spruce_planks')
    for x in range(2, 6):                            # the path from the door
        yard(s, x, 0)
    for x in range(6, 11):                           # the yard and the quarry floor
        for z in range(0, 14):
            (floor if z >= 8 else yard)(s, x, z)
    for x, z, h, b in ((7, 1, 2, 'cobblestone'), (8, 1, 1, 'cobblestone'), (7, 2, 1, 'cobblestone'),
                       (9, 3, 2, 'stone_bricks'), (10, 3, 1, 'stone_bricks'), (10, 4, 2, 'stone_bricks')):
        for y in range(1, h + 1):                    # stacks of rough blocks in the yard
            P(s, x, y, z, b)
    for x in range(6, 11):                           # the lip: a rail with a gap at x 7
        if x != 7:
            P(s, x, 1, 7, 'cobblestone_wall')
    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    P(s, x0 + 1, 1, z0 + 1, 'grindstone', face='floor', facing='east')
    P(s, x1 - 1, 1, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, x0 + 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    for x in (x0 + 1, x1 - 1):                       # torches inside the front wall
        P(s, x, 2, z0 + 1, 'wall_torch', facing='south')
    P(s, 0, 1, 1, 'poppy')                           # flowers stand on the ground (layer 0 is the ground)

    # ---------------------------------------------------------------- level 2: the working quarry
    s.at(2, 'flourish2')
    cx, cz = 5, 10                                   # the small crane: mast west of the cut, jib out east over it
    P(s, cx, 0, cz, 'stone_bricks')                  # its foot (flush)
    for dx, dz, f in ((-1, 0, 'east'), (0, -1, 'south'), (0, 1, 'north')):   # stair braces round the mast
        P(s, cx + dx, 1, cz + dz, 'stone_brick_stairs', facing=f, **STAIR)
    for y in range(1, 7):
        P(s, cx, y, cz, 'oak_log', axis='y')
    for x in range(cx - 1, 10):                      # spruce jib along x, from the counterweight arm to the far end
        if x != cx:
            P(s, x, 6, cz, 'spruce_log', axis='x')
    P(s, cx - 1, 5, cz, 'cobblestone')               # counterweight under the short arm
    P(s, cx + 1, 5, cz, 'oak_fence')                 # brace under the jib
    for y in (5, 4, 3):
        P(s, 9, y, cz, 'chain', axis='y')            # the hoist chain with a stone block on it
    P(s, 9, 2, cz, 'stone')
    P(s, cx, 7, cz, 'lantern', **STAND)              # a light on the mast top
    for x, z, h in ((6, 13, 2), (7, 13, 2), (8, 13, 2), (9, 13, 2), (10, 13, 2), (6, 12, 1), (9, 12, 1),
                    (10, 12, 1), (10, 11, 1)):       # the first rock benches at the back of the floor
        for y in range(1, h + 1):
            rock(s, x, y, z)
    P(s, x1 - 1, 1, z0 + 1, 'stonecutter', facing='west')                   # the workstation
    P(s, mid, top + 1, midz, 'chain', axis='y')                            # lantern on a chain from the ridge
    P(s, mid, top, midz, 'chain', axis='y')
    P(s, mid, top - 1, midz, 'lantern', **HANG)
    for z in (z0 + 1, z0 + 3):                       # tool rack on the shed's right side (outside)
        P(s, x1 + 1, 1, z, 'oak_fence')
    for z in range(z0 + 1, z0 + 4):
        P(s, x1 + 1, 2, z, 'spruce_slab', **SLAB)
    s.decorate(x1 + 1, 1, z0 + 2, 'item_frame', item='minecraft:iron_pickaxe', facing='east', note='pick on the rack')
    for x, z in ((8, 3), (9, 1)):                    # fresh stone in the yard
        P(s, x, 1, z, 'stone')
    P(s, 8, 2, 3, 'stone_brick_slab', **SLAB)
    P(s, 9, 2, 1, 'stone')
    for x, y, z, b, p in ((1, 1, 8, 'cobblestone', {}), (2, 1, 8, 'gravel', {}), (1, 1, 9, 'gravel', {}),
                          (1, 2, 8, 'cobblestone_slab', SLAB),
                          (2, 1, 9, 'cobblestone_stairs', dict(facing='west', **STAIR)),
                          (3, 1, 8, 'cobblestone_slab', SLAB), (0, 1, 10, 'mossy_cobblestone', {}),
                          (1, 1, 10, 'andesite', {}), (0, 1, 9, 'cobblestone_slab', SLAB)):
        P(s, x, y, z, b, **p)                        # rubble piles behind the shed
    P(s, 3, 1, 7, 'spruce_slab', **SLAB)             # a sled with a block on it, ready to drag
    P(s, 2, 1, 7, 'spruce_slab', **SLAB)
    P(s, mid + 1, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(mid + 1, 2, z0 - 1, 'sign_text', lines=['', 'Quarry', '', ''])
    for x in (x0, x1):                               # lanterns under the front eaves
        P(s, x, top, z0 - 1, 'lantern', **HANG)
    for x, z, b in ((0, 3, 'leaves'), (0, 5, 'dandelion'), (1, 6, 'cornflower')):
        P(s, x, 1, z, 'oak_leaves', **LEAVES) if b == 'leaves' else P(s, x, 1, z, b)

    # ---------------------------------------------------------------- level 3: the big works (x 11 and beyond)
    s.at(3, 'flourish3')
    for x in range(11, 17):                          # the pit twice as wide, the yard paved to the cutting shed
        for z in range(0, 14):
            (floor if z >= 8 else yard)(s, x, z)
    for z in range(8, 14):
        floor(s, 17, z)
    for x in range(12, 18):                          # the lip goes on past the cart track
        P(s, x, 1, 7, 'cobblestone_wall')
    # the hill the pit is cut into: a rock bench 3 high (4 in places) along the whole back with a soil cap, falling
    # away behind in two grassy steps; it tapers out behind the shed
    for x in range(4, 18):
        h = 1 if x == 4 else 2 if x == 5 else 4 if noise(x, 0, 14, 9) < 0.35 and x < 15 else 3
        for y in range(1, h):
            rock(s, x, y, 14)
        P(s, x, h, 14, 'grass_block', snowy='false')
        if h >= 2:
            P(s, x, 1, 15, 'dirt'); P(s, x, 2, 15, 'grass_block', snowy='false')
            P(s, x, 1, 16, 'grass_block', snowy='false')
        else:
            P(s, x, 1, 15, 'grass_block', snowy='false')
    for x in range(11, 15):                          # the second bench on the new side
        for y in (1, 2):
            rock(s, x, y, 13)
    P(s, 17, 1, 12, 'cobblestone_stairs', facing='south', **STAIR)   # the stair path up the benches
    P(s, 17, 1, 13, 'cobblestone'); P(s, 17, 2, 13, 'cobblestone_stairs', facing='south', **STAIR)

    # the cart track along x 11, from the pit (a buffer at z 12) to the road
    for z in range(1, 12):
        P(s, 11, 1, z, 'rail', shape='north_south', waterlogged='false')
    P(s, 11, 1, 12, 'stone_bricks')
    s.decorate(11, 1, 4, 'minecart', note='a minecart of dressed stone on the track')

    # the treadwheel crane: a wheel (x 15, centre z 11, y 3; a ring of planks rounded with stairs, fence spokes) on
    # an axle between two posts, the 7-high mast (x 13) and the long spruce boom (x 9..17), a dressed block on the chain
    wz, wy = 11, 3
    for dz, dy in ((-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2), (1, 2), (-2, -1), (-2, 0), (-2, 1), (2, -1), (2, 0),
                   (2, 1)):
        P(s, 15, wy + dy, wz + dz, 'stripped_spruce_log' if dz in (-2, 2) else 'spruce_planks',
          **({'axis': 'y'} if dz in (-2, 2) else {}))
    for dz, f in ((-2, 'south'), (2, 'north')):      # rounded corners: upside-down stairs on top, stairs below
        P(s, 15, wy + 2, wz + dz, 'spruce_stairs', facing=f, **TOP)
        P(s, 15, wy - 2, wz + dz, 'spruce_stairs', facing=f, **STAIR)
    for dz, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):   # spokes
        P(s, 15, wy + dy, wz + dz, 'spruce_fence')
    for x in (14, 16):
        for y in (1, 2):
            P(s, x, y, wz, 'spruce_log', axis='y')
    for x in (14, 15, 16):
        P(s, x, wy, wz, 'oak_log', axis='x')
    P(s, 15, 6, wz, 'chain', axis='y')               # the rope from the wheel up to the boom
    P(s, 13, 0, wz, 'stone_bricks')
    for dx, dz, f in ((-1, 0, 'east'), (0, -1, 'south'), (0, 1, 'north')):
        P(s, 13 + dx, 1, wz + dz, 'stone_brick_stairs', facing=f, **STAIR)
    for y in range(1, 8):
        P(s, 13, y, wz, 'spruce_log', axis='y')
    for x in range(9, 18):
        if x != 13:
            P(s, x, 7, wz, 'spruce_log', axis='x')
    for x in (12, 14):
        P(s, x, 6, wz, 'spruce_fence')               # braces under the boom
    P(s, 17, 6, wz, 'cobblestone')                   # counterweight
    for y in (6, 5, 4, 3):
        P(s, 10, y, wz, 'chain', axis='y')
    P(s, 10, 2, wz, 'stone_bricks')                  # a dressed block coming up out of the old pit
    P(s, 13, 8, wz, 'lantern', **STAND)
    P(s, 13, 5, wz - 1, 'red_wall_banner', facing='north')

    # the stone-cutting shed (x 13..17, z 0..4): a low open shed on log posts, half wall at the back, slab roof
    for x in (13, 17):
        for z in (1, 4):
            for y in (1, 2):
                P(s, x, y, z, 'spruce_log', axis='y')
    for x in (14, 15, 16):
        P(s, x, 1, 4, 'mossy_cobblestone' if noise(x, 1, 4, 2) < 0.3 else 'cobblestone')
        P(s, x, 2, 4, 'cobblestone_wall')
    for x in range(13, 18):
        for z in range(0, 5):
            if z == 0:
                P(s, x, 3, z, 'spruce_stairs', facing='south', **STAIR)
            elif x in (13, 17):
                P(s, x, 3, z, 'spruce_stairs', facing='east' if x == 13 else 'west', **STAIR)
            else:
                P(s, x, 3, z, 'spruce_slab', type='top', waterlogged='false')
    P(s, 15, 1, 2, 'stonecutter', facing='north')
    P(s, 14, 1, 3, 'oak_fence'); P(s, 14, 2, 3, 'stone_brick_slab', **SLAB)   # the mason's bench, a block on it
    P(s, 16, 1, 3, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 16, 1, 1, 'barrel', facing='up', open='false')
    P(s, 15, 2, 2, 'lantern', **HANG)
    for x, z, h, b in ((14, 6, 2, 'stone_bricks'), (15, 6, 2, 'stone_bricks'), (16, 6, 1, 'polished_andesite'),
                       (17, 6, 2, 'smooth_stone'), (12, 1, 1, 'stone_bricks'), (12, 2, 2, 'polished_andesite')):
        for y in range(1, h + 1):                    # stacks of dressed stone, ready to haul
            P(s, x, y, z, b)
    for x, z in ((14, 6), (15, 6)):
        P(s, x, 3, z, 'stone_brick_slab', **SLAB)
    P(s, 16, 2, 6, 'stone_brick_slab', **SLAB)
    P(s, 10, 1, 0, 'oak_fence'); P(s, 10, 2, 0, 'oak_fence'); P(s, 10, 3, 0, 'lantern', **STAND)   # lamp post
    for x, y, z, b in ((17, 1, 5, 'poppy'), (0, 1, 12, 'leaves'), (4, 1, 6, 'leaves'), (0, 1, 0, 'azure_bluet'),
                       (5, 1, 13, 'poppy'), (4, 1, 12, 'dandelion'), (7, 4, 14, 'poppy'), (9, 4, 14, 'leaves'),
                       (12, 4, 14, 'cornflower'), (16, 4, 14, 'leaves'), (8, 3, 15, 'dandelion'), (14, 3, 15, 'poppy'),
                       (11, 2, 16, 'leaves'), (6, 2, 16, 'azure_bluet')):
        if z >= 14:                                  # on the hill: on top of its grass
            y = max(yy for (xx, yy, zz) in s.blocks if (xx, zz) == (x, z)) + 1
        elif (x, 0, z) not in s.blocks:              # a tuft of grass under loose plants (flush: it is the ground)
            P(s, x, 0, z, 'grass_block', snowy='false')
        P(s, x, y, z, 'oak_leaves', **LEAVES) if b == 'leaves' else P(s, x, y, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='quarry', name='Quarry', tier=3, zone='outer', role='resource', group='quarry',
                   terrain='flexible', door='front', make=quarry, ground_layer=0,
                   changes='Carlton: "L2 and L3 should differentiate from each other better". L2 stays the working '
                           'quarry (small log crane, now with a spruce jib instead of the pale one, first rock '
                           'benches, stonecutter, tool rack, rubble, sign). L3 is now the big works and doubles the '
                           'site: the pit twice as wide and terraced (a 3-high bench along the whole back with a stair '
                           'path up), a treadwheel crane with a tall spruce mast and a long spruce boom lowering '
                           'dressed stone onto a cart track that runs from the pit to the road, an open stone-cutting '
                           'shed with a stonecutter and a mason\'s bench, stacks of dressed stone. Still flush '
                           '(ground_layer 0).',
                   description='Over budget (Carlton asked for more). Flush piece (ground_layer 0: the mod does not '
                               'support it yet). Cobblestone shed with a spruce gable roof by a gravel yard and a '
                               'broken-rock quarry floor the quarrymen dig down from (builders never dig); L2 the '
                               'small log crane, first rock benches, stonecutter, tool rack, rubble; L3 the big '
                               'works: wider terraced pit, treadwheel crane with a spruce boom, cart track, '
                               'stone-cutting shed, dressed stone.')]
