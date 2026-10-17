"""Workshop (tier 2, look A): the kingdom's workshop for everything that isn't wood (stage 6b, D115). Each level is a
trade the workshop learns: L1 the stone bench (stone bricks, stairs and slabs from cobblestone), L2 the kiln (glass
and bricks from sand and clay), L3 the forge (iron pieces from raw iron and coal).

Layout (13 wide x 11 deep, front z = 0):
- Hall (walls x 2..8, z 2..8): an open front bay three wide under a dark oak lintel, stone brick walls on a cobblestone
  course with dark oak corner posts, a spruce gable roof (dark eaves, slab ridge). The middle aisle (x = 5) stays free
  from the bay to the back wall.
- Left yard (x 0..1): the raw stone pile at L1, the ore and coal lean-to at L3.
- Right yard (x 9..12): the brick kiln at L2.
L1 (KNOWN-OK only): the hall, crafting table and grindstone (the stone bench), chest and barrels, a cutting bench,
torches, shutters, a step up into the bay, rough cobblestone stacked in the left yard.
L2 (the kiln): a stonecutter at the bench (not builder-tested yet), glass panes in the windows, a brick kiln in the
right yard with two furnaces and a stack past the eaves, heaps of sand and clay, a lantern on a chain over the aisle.
L3 (the forge): anvil, blast furnace and quench tub in the back right corner, iron bars over the forge window, the ore
and coal lean-to, a banner, a lamp post by the road, a planted bed behind."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def workshop():
    W, D = 13, 11
    s = Structure('workshop', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 8, 2, 8
    mid = 5                                                # the aisle
    top = 3                                                # last wall layer
    bay = (4, 5, 6)                                        # the open front bay
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'cobblestone')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif z == z0 and x in bay:                          # open bay, dark oak lintel
                    if y == top:
                        P(s, x, y, z, 'dark_oak_log', axis='x')
                elif z == z0 and x in (bay[0] - 1, bay[-1] + 1):    # posts either side of the bay
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 5) < 0.3 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    windows = [(x0, 2, 4), (x0, 2, 6), (x1, 2, 4), (x1, 2, 6), (mid, 2, z1)]
    for w in windows:
        s.remove(*w)
    gable_roof(s, x0, x1, z0, z1, top)

    s.at(1, 'fittings')
    P(s, x0 + 1, 1, z0 + 2, 'crafting_table')                            # the stone bench, left
    P(s, x0 + 1, 1, z0 + 3, 'grindstone', face='floor', facing='east')
    P(s, x0 + 1, 1, z0 + 4, 'spruce_slab', **SLAB_TOP)                   # cutting bench
    P(s, x0 + 1, 1, z0 + 5, 'spruce_slab', **SLAB_TOP)
    P(s, mid + 1, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    for x, y in ((x0 + 1, 1), (x0 + 1, 2), (x1 - 1, 1)):                 # barrels of stock
        P(s, x, y, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x0 + 1, 2, z0 + 3, 'wall_torch', facing='east')
    P(s, x1 - 1, 2, z0 + 3, 'wall_torch', facing='west')
    P(s, mid, 3, z1 - 1, 'wall_torch', facing='north')
    for x in (bay[0] - 1, bay[-1] + 1):                                  # outside, on the bay posts
        P(s, x, 2, z0 - 1, 'wall_torch', facing='north')
    for x, y, z in windows[:2]:
        shutters(s, x, y, z, 'west')
    for x, y, z in windows[2:4]:
        shutters(s, x, y, z, 'east')
    shutters(s, *windows[4], 'south')
    for x in bay:                                                        # step up into the bay
        P(s, x, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for z in (z0 + 1, z0 + 2, z0 + 3):                                   # rough stone waiting in the left yard
        P(s, 0, 0, z, 'cobblestone')
        P(s, 1, 0, z, 'cobblestone' if z != z0 + 3 else 'mossy_cobblestone')
        if z != z0 + 3:
            P(s, 0, 1, z, 'cobblestone')
    P(s, 1, 1, z0 + 1, 'cobblestone_slab', type='bottom', waterlogged='false')

    s.at(2, 'kiln')
    P(s, x0 + 1, 1, z0 + 1, 'stonecutter', facing='east')                # the stone bench gets its saw
    for x, y, z in windows[:4]:
        P(s, x, y, z, 'glass_pane')
    # the brick kiln in the right yard: a brick body with two furnaces facing the road, a stack past the eaves
    kx0, kx1, kz0, kz1 = 10, 12, 4, 6
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            for y in range(0, 3):
                if z == kz0 and y == 1 and x in (kx0, kx0 + 1):
                    continue
                P(s, x, y, z, 'bricks')
    for x in (kx0, kx0 + 1):
        P(s, x, 1, kz0, 'furnace', facing='north', lit='true')
    for y in range(3, 7):
        P(s, kx1, y, kz1, 'bricks')
    P(s, kx1, 7, kz1, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    for x in range(kx0, kx1 + 1):                                        # a brick lip on the kiln's top
        P(s, x, 3, kz0, 'brick_slab', type='bottom', waterlogged='false')
    # sand and clay heaps beside it, a barrel of clay
    P(s, 10, 0, 8, 'sand'); P(s, 11, 0, 8, 'sand'); P(s, 10, 1, 8, 'sand')
    P(s, 12, 0, 8, 'red_sand')
    P(s, 10, 0, 9, 'clay'); P(s, 11, 0, 9, 'clay')
    P(s, 12, 0, 9, 'barrel', facing='up', open='true')
    s.decorate(12, 0, 9, 'item', item='minecraft:clay_ball', note='clay in the open barrel')
    P(s, mid, top + 2, 5, 'chain', axis='y'); P(s, mid, top + 1, 5, 'chain', axis='y')
    P(s, mid, top, 5, 'lantern', **HANG)                                 # over the aisle
    for z in (z0 + 1, z0 + 2):                                           # shelves of finished blocks, right wall
        P(s, x1 - 1, 3, z, 'spruce_trapdoor', facing='west', **SHELF)
    P(s, x1 - 1, 1, z0 + 1, 'stone_brick_slab', type='bottom', waterlogged='false')   # a pallet of cut blocks
    P(s, x1 - 1, 1, z0 + 2, 'stone_brick_stairs', facing='west', **STAIR)
    P(s, x1, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(x1, 2, z0 - 1, 'sign_text', lines=['', 'Workshop', '', ''])

    s.at(3, 'forge')
    P(s, x1 - 1, 1, z1 - 2, 'blast_furnace', facing='west', lit='false')
    P(s, x1 - 1, 1, z1 - 3, 'anvil', facing='north')
    P(s, x1 - 2, 1, z1 - 2, 'cauldron')                                  # quench tub beside the forge
    s.decorate(x1 - 2, 1, z1 - 2, 'water_cauldron', note='quench water')
    P(s, x1 - 1, 2, z1 - 2, 'bricks'); P(s, x1 - 1, 3, z1 - 2, 'bricks')  # brick hood over the forge
    P(s, mid, 2, z1, 'iron_bars')                                        # the back window, over the forge
    P(s, x1 - 2, top, z1 - 3, 'chain', axis='y')
    P(s, x1 - 2, top - 1, z1 - 3, 'lantern', **HANG)
    # ore and coal lean-to in the left yard, behind the stone pile
    for z in (z0 + 4, z1):
        for y in (0, 1, 2):
            P(s, 0, y, z, 'spruce_fence')
    for z in range(z0 + 4, z1 + 1):
        P(s, 0, 3, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, 1, 3, z, 'spruce_slab', **SLAB)
    P(s, 0, 0, z0 + 5, 'coal_block'); P(s, 0, 0, z0 + 6, 'coal_block'); P(s, 0, 1, z0 + 5, 'coal_block')
    P(s, 1, 0, z0 + 5, 'raw_iron_block'); P(s, 1, 0, z0 + 6, 'raw_copper_block')
    P(s, x0, 3, z0 - 1, 'red_wall_banner', facing='north')
    s.decorate(x0, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='hammer and chisel')
    P(s, 12, 0, 0, 'spruce_fence'); P(s, 12, 1, 0, 'spruce_fence'); P(s, 12, 2, 0, 'lantern', **STAND)   # lamp post
    for x, z in ((mid, z1 - 3), (mid + 1, z1 - 3)):                      # soot by the forge
        A(s, x, 1, z, 'black_carpet')
    for x in range(mid - 1, mid + 2):                                    # planted bed behind
        P(s, x, 0, z1 + 1, 'coarse_dirt')
        P(s, x, 0, z1 + 2, 'spruce_trapdoor', facing='south', **OPEN)
    for x, f in ((mid - 1, 'poppy'), (mid, 'cornflower'), (mid + 1, 'dandelion')):
        P(s, x, 1, z1 + 1, f)
    A(s, 0, 0, z1 + 1, 'oak_leaves', **LEAVES)
    A(s, W - 1, 0, z1 + 1, 'oak_leaves', **LEAVES)
    P(s, 9, 0, 1, 'fern')
    return connect(s)


STRUCTURES = [dict(path='workshop', name='Workshop', tier=2, zone='inner', role='workshop', group='workshop',
                   terrain='ground_level', door='front', make=workshop, changes='First version.',
                   description='The kingdom\'s workshop for everything that isn\'t wood (stage 6b). Stone hall with an '
                               'open front bay. L1 the stone bench (crafting table, grindstone, raw stone pile); L2 '
                               'the kiln (stonecutter, glass windows, brick kiln with two furnaces and a stack, sand and '
                               'clay heaps); L3 the forge (anvil, blast furnace, quench tub, ore and coal lean-to, '
                               'banner, lamp post). Stonecutter, anvil and blast furnace are not builder-tested yet.')]
