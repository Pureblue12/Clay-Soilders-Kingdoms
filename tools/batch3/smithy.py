"""Smithy (tier 3, look A): a stone forge with an open front bay and a big brick chimney on its side."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def smithy(width=5, depth=7, wall_height=3):
    """Smithy in a 9 x 11 box: walls x 2..6, z 2..8 (5 x 7), a yard column on each side, a front step row and a back
    planting row.

    L1 (shell and fittings, KNOWN-OK only): cobblestone plinth, a mossy cobblestone course under stone brick walls,
    dark oak corner posts, top plates and lintel; the front is an open bay three wide. A brick chimney breast stands
    against the right wall and rises past the eaves; inside it the forge (furnace) under a brick hood. Chest and
    barrels of ore at the back, a workbench, torches, shutters on the open windows, a firewood pile under the left
    eave, a step up into the bay. Spruce roof with dark oak eaves and ridge, corbels under the gable overhangs.
    L2 (a working smithy): anvil before the forge, smithing table (the workstation), blast furnace and quench
    cauldron beside the furnace under the hood, iron bars in the windows, smoke from the chimney (campfire on top),
    shop sign, a lantern on a chain over the anvil and one under the front gable, tool shelves, a grindstone outside.
    L3 (the yard): an ore and coal lean-to on the left (fence posts, stair roof, coal and raw iron, an open barrel of
    ore), soot on the floor, a red banner, a lantern post by the road, a weapon rack and a water barrel on the right,
    a planted bed behind, moss and a bush."""
    W, D = width + 4, depth + 4
    s = Structure('smithy', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 1 + width, 2, 1 + depth           # 2..6, 2..8
    mid = (x0 + x1) // 2                                   # 4
    top = wall_height                                      # 3
    cz = (z0 + z1) // 2                                    # 5: forge and chimney row
    chim_x = x1 + 1                                        # 7
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
                elif z == z0:                                       # open bay, dark oak lintel
                    if y == top:
                        P(s, x, y, z, 'dark_oak_log', axis='x')
                elif x == x1 and abs(z - cz) <= 1:                  # chimney back inside the right wall
                    P(s, x, y, z, 'bricks')
                elif y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 3) < 0.35 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    windows = [(x0, 2, cz - 1), (x0, 2, cz + 1), (mid, 2, z1)]
    for w in windows:
        s.remove(*w)
    for z in range(cz - 1, cz + 2):                        # the chimney breast outside the right wall
        for y in range(0, top + 1):
            P(s, chim_x, y, z, 'bricks')
    for y in range(top + 1, top + 5):                       # the stack, past the eaves
        P(s, chim_x, y, cz, 'bricks')
    gable_roof(s, x0, x1, z0, z1, top, skip={(chim_x, top + 1, cz)})

    s.at(1, 'fittings')
    P(s, x1 - 1, 1, cz, 'furnace', facing='west', lit='false')         # the forge
    for z in (cz - 1, cz, cz + 1):                                       # brick hood over it
        P(s, x1 - 1, 2, z, 'bricks')
    P(s, x1 - 1, 3, cz, 'bricks')
    P(s, mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    for x, y in ((x0 + 1, 1), (x0 + 1, 2), (x1 - 1, 1)):                 # barrels of ore and coal
        P(s, x, y, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x0 + 1, 1, cz, 'spruce_slab', **SLAB_TOP)                       # workbench
    P(s, x0 + 1, 2, cz, 'wall_torch', facing='east')
    P(s, x0 + 1, 3, z1 - 1, 'wall_torch', facing='north')
    P(s, x1, 3, z0 - 1, 'wall_torch', facing='north')                    # outside, on the front right post
    for x, y, z in windows[:2]:
        shutters(s, x, y, z, 'west')
    shutters(s, *windows[2], 'south')
    for x in range(x0 + 1, x1):                                          # step up into the bay
        P(s, x, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for z in (z0 + 1, z0 + 2, z0 + 3):                                   # firewood under the left eave
        P(s, x0 - 1, 0, z, 'spruce_log', axis='z')
        if z != z0 + 3:
            P(s, x0 - 1, 1, z, 'spruce_log', axis='z')

    s.at(2, 'flourish2')
    P(s, mid, 1, z0 + 2, 'anvil', facing='west')                         # anvil before the forge
    P(s, x0 + 1, 1, z0 + 2, 'smithing_table')                            # the workstation
    P(s, x1 - 1, 1, cz - 1, 'blast_furnace', facing='west', lit='false')
    P(s, x1 - 1, 1, cz + 1, 'cauldron')                                  # quench tub
    s.decorate(x1 - 1, 1, cz + 1, 'water_cauldron', note='quench water')
    for x, y, z in windows:
        P(s, x, y, z, 'iron_bars')
    P(s, chim_x, top + 5, cz, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    P(s, x1, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(x1, 2, z0 - 1, 'sign_text', lines=['', 'Smithy', '', ''])
    P(s, mid, top + 2, cz, 'chain', axis='y'); P(s, mid, top + 1, cz, 'chain', axis='y')
    P(s, mid, top, cz, 'lantern', **HANG)                                # over the anvil, on a chain from the ridge
    P(s, mid, top + 2, z0 - 1, 'chain', axis='y')
    P(s, mid, top + 1, z0 - 1, 'lantern', **HANG)                        # under the front gable
    for z in (z0 + 2, cz + 1):                                           # tool shelves on the left wall
        P(s, x0 + 1, 3, z, 'spruce_trapdoor', facing='east', **SHELF)
    P(s, x1 + 1, 0, z0, 'grindstone', face='floor', facing='north')      # grindstone out front, right
    s.decorate(x0 + 1, 2, z0 + 1, 'item_frame', item='minecraft:iron_sword', facing='east', note='a blade on the wall')

    s.at(3, 'flourish3')
    # ore and coal lean-to on the left: fence posts, a stair roof against the eave, the heaps under it
    for z in (z0 + 1, z1 - 1):
        for y in (0, 1, 2):
            P(s, 0, y, z, 'spruce_fence')
    for z in range(z0 + 1, z1):
        P(s, 0, 3, z, 'spruce_stairs', facing='east', **STAIR)
        P(s, 1, 3, z, 'spruce_slab', **SLAB)
    P(s, 0, 0, z0 + 2, 'coal_block'); P(s, 0, 0, z0 + 3, 'coal_block'); P(s, 0, 1, z0 + 2, 'coal_block')
    P(s, 0, 0, z0 + 4, 'raw_iron_block')
    P(s, 1, 0, z1 - 2, 'barrel', facing='up', open='true')
    s.decorate(1, 0, z1 - 2, 'item', item='minecraft:raw_iron', note='ore in the open barrel')
    P(s, 1, 0, z1 - 1, 'barrel', facing='up', open='false')
    for x, z in ((mid, cz), (mid + 1, cz), (mid, z0 + 1)):               # soot on the floor by the forge
        A(s, x, 1, z, 'black_carpet')
    P(s, x0, 3, z0 - 1, 'red_wall_banner', facing='north')
    s.decorate(x0, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='hammer and anvil')
    P(s, 1, 0, 0, 'spruce_fence'); P(s, 1, 1, 0, 'spruce_fence'); P(s, 1, 2, 0, 'lantern', **STAND)  # lamp post
    for z in (z1 - 1, z1):                                               # weapon rack on the right
        P(s, chim_x, 0, z, 'spruce_fence')
        P(s, chim_x, 1, z, 'spruce_slab', **SLAB)
    s.decorate(chim_x, 1, z1 - 1, 'armor_stand', item='minecraft:iron_chestplate', note='finished armour on show')
    P(s, W - 1, 0, z0 + 1, 'barrel', facing='up', open='false')
    P(s, W - 1, 1, z0 + 1, 'lantern', **STAND)
    for x in range(mid - 1, mid + 2):                                    # planted bed behind the forge
        P(s, x, 0, z1 + 1, 'coarse_dirt')
        P(s, x, 0, z1 + 2, 'spruce_trapdoor', facing='south', **OPEN)
    for x, f in ((mid - 1, 'poppy'), (mid, 'cornflower'), (mid + 1, 'dandelion')):
        P(s, x, 1, z1 + 1, f)
    for x, z in ((0, z1 + 1), (W - 1, z1 + 1)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    P(s, W - 1, 0, z1, 'fern')
    return connect(s)


STRUCTURES = [dict(path='smithy', name='Smithy', tier=3, zone='inner', role='workshop', group='smithy',
                   terrain='ground_level', door='front', make=smithy, changes='First version.',
                   description='Stone forge with an open front bay and a brick chimney breast on its right side '
                               '(furnace forge under a brick hood); L2 makes it a working smithy (anvil, smithing '
                               'table, blast furnace, quench tub, smoke, sign, lanterns), L3 adds the ore lean-to, '
                               'weapon rack and yard life.')]
