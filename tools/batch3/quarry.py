"""Quarry (tier 3, look A): a stone shed and a cut-stone yard by the quarry floor, with a log crane over the cut."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
LEAVES = dict(persistent='true', distance='1', waterlogged='false')


def quarry():
    """Quarry. Ground rule: a flush piece (ground_layer=0): layer y = 0 REPLACES the top ground block, so the shed's
    floor, the yard's paving and the quarry floor are level with the grass; everything standing starts at y = 1.
    Builders never dig: the stepped cut face itself is made by the quarrymen (dig work goal, D6), down from the quarry
    floor at the back right (x 6..10, z 8..10). The builders only lay that floor's broken-rock surface.

    - Shed (front left, walls x 1..5, z 1..5): cobblestone walls 3 high (some mossy) with oak log corners, a spruce
      stair gable roof with dark oak eaves on log top plates, one block of overhang at the sides and the front, door
      at the front. Inside: grindstone (L1 workstation: sharpening the chisels; the stonecutter, the brief's
      workstation, is not builder-tested yet and comes at L2), chest, barrel, torches.
    - Yard (front right, x 6..10, z 0..6): gravel and coarse dirt paving with stacks of cut blocks for the carriers.
    - The lip (z 7): a cobblestone wall rail between the yard and the quarry floor, a gap at x 7 to walk down.
    Level 2: the crane (the signature) on the west side of the cut: an oak log mast on a stone brick foot with stair
    braces, a jib out over the cut with a counterweight and a brace, a chain with a stone block on it, a lantern on
    the mast; the stonecutter in the shed, a hanging lantern, a tool rack, fresh stone in the yard.
    Level 3: rubble piles and a sled of blocks behind the shed, a lantern post by the road, the shop sign (text by the
    decoration step), a banner on the mast, lanterns under the eaves, flowers and bushes, a minecart (decoration)."""
    s = Structure('quarry', (11, 8, 11), palette='oak_a2')
    x0, x1, z0, z1 = 1, 5, 1, 5                      # shed walls
    mid, midz, top = 3, 3, 4                         # walls y 1..3 above the flush base, top plates at y 4
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')         # flush base course and floor
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            put_id(s, x, 0, z, 'coarse_dirt')
    for y in range(1, top):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    if x in (x0, x1) and z in (z0, z1):
                        put_id(s, x, y, z, 'oak_log', axis='y')
                    else:
                        put_id(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 3) < 0.2 else 'cobblestone')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    windows = [(x0, 2, midz), (x1, 2, midz), (mid, 2, z1)]
    for w in windows:
        s.remove(*w)
    for x in (x0, x1):                               # top plates
        for z in range(z0, z1 + 1):
            put_id(s, x, top, z, 'oak_log', axis='z')
    for z in range(z0 - 1, z1 + 2):                  # gable roof, ridge along z, one block of overhang all round
        put_id(s, x0 - 1, top, z, 'dark_oak_stairs', facing='east', **STAIR)
        put_id(s, x1 + 1, top, z, 'dark_oak_stairs', facing='west', **STAIR)
        put_id(s, x0, top + 1, z, 'spruce_stairs', facing='east', **STAIR)
        put_id(s, x1, top + 1, z, 'spruce_stairs', facing='west', **STAIR)
        put_id(s, x0 + 1, top + 2, z, 'spruce_stairs', facing='east', **STAIR)
        put_id(s, x1 - 1, top + 2, z, 'spruce_stairs', facing='west', **STAIR)
        put_id(s, mid, top + 2, z, 'dark_oak_planks')
    for z in (z0, z1):                               # gable ends
        for x in range(x0 + 1, x1):
            put_id(s, x, top, z, 'spruce_planks'); put_id(s, x, top + 1, z, 'spruce_planks')
    # flush paving: a path from the door to the yard, the yard, and the quarry floor (broken rock)
    for x in range(0, 11):
        for z in range(0, 11):
            if (x, 0, z) in s.blocks:
                continue
            n = noise(x, 0, z, 11)
            if x >= 6 and z >= 8:                    # quarry floor
                put_id(s, x, 0, z, 'gravel' if n < 0.3 else 'mossy_cobblestone' if n < 0.45 else 'cobblestone')
            elif x >= 6 or (z == 0 and 2 <= x <= 5):  # yard and path
                put_id(s, x, 0, z, 'gravel' if n < 0.55 else 'coarse_dirt')
    for x, z, h, b in ((7, 1, 2, 'cobblestone'), (8, 1, 1, 'cobblestone'), (7, 2, 1, 'cobblestone'),
                       (9, 3, 2, 'stone_bricks'), (10, 3, 1, 'stone_bricks'), (10, 4, 2, 'stone_bricks')):
        for y in range(1, h + 1):                    # stacks of cut blocks in the yard
            put_id(s, x, y, z, b)
    for x in range(6, 11):                           # the lip: a rail with a gap at x 7
        if x != 7:
            put_id(s, x, 1, 7, 'cobblestone_wall')
    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        put_id(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    put_id(s, x0 + 1, 1, z0 + 1, 'grindstone', face='floor', facing='east')
    put_id(s, x1 - 1, 1, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, x0 + 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    for x in (x0 + 1, x1 - 1):                       # torches inside the front wall
        put_id(s, x, 2, z0 + 1, 'wall_torch', facing='south')

    s.at(2, 'flourish2')
    cx, cz = 5, 9                                    # the crane: mast west of the cut, jib out east over it
    put_id(s, cx, 0, cz, 'stone_bricks')             # its foot (flush)
    for dx, dz, f in ((-1, 0, 'east'), (0, -1, 'south'), (0, 1, 'north')):   # stair braces round the mast
        put_id(s, cx + dx, 1, cz + dz, 'stone_brick_stairs', facing=f, **STAIR)
    for y in range(1, 7):
        put_id(s, cx, y, cz, 'oak_log', axis='y')
    for x in range(cx - 1, 10):                      # jib along x, from the counterweight arm to the far end
        if x != cx:
            put_id(s, x, 6, cz, 'stripped_oak_log', axis='x')
    put_id(s, cx - 1, 5, cz, 'cobblestone')          # counterweight under the short arm
    put_id(s, cx + 1, 5, cz, 'oak_fence')            # brace under the jib
    for y in (5, 4, 3):
        put_id(s, 9, y, cz, 'chain', axis='y')       # the hoist chain with a stone block on it
    put_id(s, 9, 2, cz, 'stone')
    put_id(s, cx, 7, cz, 'lantern', **STAND)         # a light on the mast top
    put_id(s, x1 - 1, 1, z0 + 1, 'stonecutter', facing='west')               # the workstation
    put_id(s, mid, top + 1, midz, 'chain', axis='y')                        # lantern on a chain from the ridge
    put_id(s, mid, top, midz, 'chain', axis='y')
    put_id(s, mid, top - 1, midz, 'lantern', **HANG)
    for z in (z0 + 1, z0 + 3):                       # tool rack on the shed's right side (outside)
        put_id(s, x1 + 1, 1, z, 'oak_fence')
    for z in range(z0 + 1, z0 + 4):
        put_id(s, x1 + 1, 2, z, 'spruce_slab', **BOTTOM)
    s.decorate(x1 + 1, 1, z0 + 2, 'item_frame', item='minecraft:iron_pickaxe', facing='east', note='pick on the rack')
    for x, z in ((8, 3), (9, 1)):                    # fresh stone in the yard
        put_id(s, x, 1, z, 'stone')
    put_id(s, 8, 2, 3, 'stone_brick_slab', **BOTTOM)
    put_id(s, 9, 2, 1, 'stone')

    # the worked face at the back of the quarry floor: benches of raw rock the quarrymen cut blocks from
    for x, z, h in ((6, 10, 2), (7, 10, 2), (8, 10, 2), (9, 10, 2), (10, 10, 3), (10, 9, 2), (10, 8, 1), (6, 9, 1)):
        for y in range(1, h + 1):
            n = noise(x, y, z, 5)
            put_id(s, x, y, z, 'andesite' if n < 0.3 else 'cobblestone' if n < 0.5 else 'stone')

    s.at(3, 'flourish3')
    for x, y, z, b, p in ((1, 1, 8, 'cobblestone', {}), (2, 1, 8, 'gravel', {}), (1, 1, 9, 'gravel', {}),
                          (1, 2, 8, 'cobblestone_slab', BOTTOM),
                          (2, 1, 9, 'cobblestone_stairs', dict(facing='west', **STAIR)),
                          (3, 1, 8, 'cobblestone_slab', BOTTOM), (0, 1, 10, 'mossy_cobblestone', {}),
                          (1, 1, 10, 'andesite', {}), (0, 1, 9, 'cobblestone_slab', BOTTOM)):
        put_id(s, x, y, z, b, **p)                   # rubble piles behind the shed
    put_id(s, 3, 1, 7, 'spruce_slab', **BOTTOM)      # a sled with a block on it, ready to drag
    put_id(s, 2, 1, 7, 'spruce_slab', **BOTTOM)
    s.decorate(7, 1, 5, 'minecart', note='a minecart of stone in the yard')
    put_id(s, 10, 1, 0, 'oak_fence'); put_id(s, 10, 2, 0, 'oak_fence'); put_id(s, 10, 3, 0, 'lantern', **STAND)
    put_id(s, mid + 1, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(mid + 1, 2, z0 - 1, 'sign_text', lines=['', 'Quarry', '', ''])
    put_id(s, cx, 4, cz - 1, 'red_wall_banner', facing='north')             # banner on the mast
    for x in (x0, x1):                               # lanterns under the front eaves
        put_id(s, x, top, z0 - 1, 'lantern', **HANG)
    for x, z, b in ((0, 1, 'poppy'), (0, 3, 'leaves'), (0, 5, 'dandelion'), (6, 5, 'leaves'), (1, 6, 'cornflower'),
                    (4, 6, 'leaves'), (0, 0, 'azure_bluet')):
        if b == 'leaves':
            put_id(s, x, 1, z, 'oak_leaves', **LEAVES)
        else:
            put_id(s, x, 1, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='quarry', name='Quarry', tier=3, zone='outer', role='resource', group='quarry',
                   terrain='flexible', door='front', make=quarry, ground_layer=0, changes='First version.',
                   description='Over budget: 296 blocks vs 88 (the flush yard and quarry-floor paving is 70 of them). '
                               'Flush piece (ground_layer 0: the mod does not support it yet). Cobblestone shed with a '
                               'spruce gable roof by a gravel yard of cut blocks and a broken-rock quarry floor the '
                               'quarrymen dig down from (builders never dig); L2 adds the log crane over the cut, the '
                               'worked rock face, the stonecutter and a tool rack; L3 rubble piles, sign, banner, lanterns, '
                               'flowers.')]
