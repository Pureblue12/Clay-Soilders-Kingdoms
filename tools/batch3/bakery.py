"""Bakery (tier 3, look A): a timber cottage bakery with a big brick oven built onto its back gable."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters, flower_box)


def bakery(width=5, depth=6, wall_height=3):
    """Bakery in a 10 x 11 box: walls x 2..6, z 2..7 (5 x 6), the oven behind the back gable (x 3..5, z 8..9), a
    yard column on the left, a stall yard on the right (x 7..9), a front row and a back row.

    L1 (KNOWN-OK only): cobblestone plinth, white plaster walls in a dark oak frame (corner posts, a post in each long
    wall, top plates), oak plank gables, spruce roof with dark oak eaves and a slab ridge. Front door between two glass
    windows, a window in each side. The brick oven stands against the back gable: its mouth is a furnace in the back
    wall, its chimney rises past the ridge. Inside: the smoker (workstation) beside the oven mouth, a kneading table,
    a chest and barrels, torches. Outside: a step, firewood beside the oven, a hay bale of wheat.
    L2 (the bakery dressed): the oven domed with brick stairs, smoke from the chimney, flour sacks (white wool),
    shutters, flower boxes under the front windows, the shop sign, a bench by the door, a lantern over the door.
    L3 (the bread stall): a little stall on the right with a slab roof on posts, a counter of barrels and slabs with
    bread on it (decoration step), lanterns, a stack of wheat bales by the oven, a banner, flowers and bushes."""
    W, D = width + 5, depth + 5
    s = Structure('bakery', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 1 + width, 2, 1 + depth           # 2..6, 2..7
    mid = (x0 + x1) // 2                                   # 4
    top = wall_height
    ov0, ov1 = z1 + 1, z1 + 2                              # oven rows 8, 9
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'spruce_planks')
    posts = {z0, z1, z0 + 3}
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in posts:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                else:
                    P(s, x, y, z, 'white_terracotta')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='oak_door')
    windows = [(mid - 1, 2, z0), (mid + 1, 2, z0), (x0, 2, z0 + 2), (x1, 2, z0 + 2), (x0, 2, z1 - 1)]
    for w in windows:
        s.remove(*w)
    s.remove(mid, 1, z1)                                    # the oven mouth goes here
    # the oven: a brick block behind the back wall, its chimney up past the ridge
    for x in range(mid - 1, mid + 2):
        for z in (ov0, ov1):
            for y in (0, 1):
                P(s, x, y, z, 'bricks')
    for z in (ov0, ov1):
        P(s, mid, 2, z, 'bricks')
    for y in range(3, 8):
        P(s, mid, y, ov1, 'bricks')
    gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, gable='oak_planks')

    s.at(1, 'fittings')
    P(s, mid, 1, z1, 'furnace', facing='north', lit='false')            # oven mouth
    P(s, mid + 1, 1, z1 - 1, 'smoker', facing='west', lit='false')      # the workstation
    P(s, mid - 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x0 + 1, 1, z0 + 3, 'chest', facing='east', type='single', waterlogged='false')
    P(s, x1 - 1, 1, z0 + 2, 'oak_slab', **SLAB_TOP)                      # kneading table
    P(s, x1 - 1, 1, z0 + 3, 'oak_slab', **SLAB_TOP)
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    P(s, x0 + 1, 2, z0 + 3, 'wall_torch', facing='east')
    P(s, mid - 1, 3, z1 - 1, 'wall_torch', facing='north')
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for z in (ov0, ov1):                                                # firewood beside the oven
        P(s, x1, 0, z, 'spruce_log', axis='x')
        P(s, x1 + 1, 0, z, 'spruce_log', axis='x')
        P(s, x1, 1, z, 'spruce_log', axis='x')
    P(s, x0, 0, ov0, 'hay_block', axis='y')                             # wheat for the flour

    s.at(2, 'flourish2')
    for z, f in ((ov0, 'east'), (ov1, 'east')):                          # the dome: brick stairs on the shoulders
        P(s, mid - 1, 2, z, 'brick_stairs', facing='east', **STAIR)
        P(s, mid + 1, 2, z, 'brick_stairs', facing='west', **STAIR)
    P(s, mid, 3, ov0, 'brick_slab', type='bottom', waterlogged='false')
    P(s, mid, 8, ov1, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    P(s, x0 + 1, 2, z1 - 1, 'white_wool')                               # flour sacks
    P(s, x0 + 1, 1, z0 + 2, 'white_wool')
    shutters(s, *windows[2], 'west'); shutters(s, *windows[3], 'east'); shutters(s, *windows[4], 'west')
    for x, f in ((mid - 1, 'poppy'), (mid + 1, 'dandelion')):           # flower boxes under the front windows
        P(s, x, 0, z0 - 1, 'coarse_dirt')
        P(s, x, 0, z0 - 2, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, x, 1, z0 - 1, f)
    P(s, x0, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(x0, 2, z0 - 1, 'sign_text', lines=['', 'Bakery', 'fresh bread', ''])
    for y in (top + 1, top + 2):                                        # over the door, on a chain from the ridge
        P(s, mid, y, z0 - 1, 'chain', axis='y')
    P(s, mid, top, z0 - 1, 'lantern', **HANG)
    P(s, x1 + 1, 0, z0 + 1, 'oak_stairs', facing='west', **STAIR)       # a bench by the corner
    P(s, x1 + 1, 0, z0 + 2, 'oak_stairs', facing='west', **STAIR)
    P(s, x1 - 1, 2, z0 + 2, 'oak_pressure_plate', powered='false')      # dough board on the table
    s.decorate(x1 - 1, 2, z0 + 3, 'item', item='minecraft:wheat', note='flour on the table')

    s.at(3, 'flourish3')
    # bread stall on the right: two posts and a slab roof, a counter of barrels and a slab
    sx0, sx1 = x1 + 2, W - 1
    for z in (z0 + 3, z0 + 5):
        for y in (0, 1):
            P(s, W - 1, y, z, 'spruce_fence')
    for x in range(sx0, sx1 + 1):
        for z in range(z0 + 3, z0 + 6):
            P(s, x, 2, z, 'spruce_slab', **SLAB)
    P(s, sx0, 0, z0 + 3, 'barrel', facing='up', open='false')
    P(s, sx0, 0, z0 + 4, 'spruce_slab', **SLAB_TOP)
    P(s, sx0, 0, z0 + 5, 'barrel', facing='up', open='false')
    for z in (z0 + 3, z0 + 4, z0 + 5):
        s.decorate(sx0, 1, z, 'item', item='minecraft:bread', note='bread on the counter')
    P(s, sx0 + 1, 1, z0 + 4, 'lantern', **HANG)
    # a stack of wheat bales by the oven
    for x in (0, 1):
        P(s, x, 0, ov1, 'hay_block', axis='x')
    P(s, 0, 1, ov1, 'hay_block', axis='x')
    P(s, x1, 3, z0 - 1, 'red_wall_banner', facing='north')
    s.decorate(x1, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='a sheaf of wheat')
    P(s, 0, 0, 0, 'spruce_fence'); P(s, 0, 1, 0, 'spruce_fence'); P(s, 0, 2, 0, 'lantern', **STAND)
    s.decorate(x1 - 1, 2, z1 - 1, 'item', item='minecraft:bread', note='a loaf on the smoker')
    for x, z in ((1, z0), (W - 1, D - 1), (x0, D - 1)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for x, z, f in ((1, z0 + 2, 'poppy'), (1, z0 + 4, 'cornflower'), (W - 1, z0, 'oxeye_daisy'),
                    (mid + 2, D - 1, 'dandelion'), (x1 + 1, z0 + 4, 'fern')):
        A(s, x, 0, z, f)
    return connect(s)


STRUCTURES = [dict(path='bakery', name='Bakery', tier=3, zone='inner', role='food', group='bakery',
                   terrain='ground_level', door='front', make=bakery, changes='First version.',
                   description='Plaster and timber cottage bakery with a brick oven built onto the back gable (furnace mouth, '
                               'chimney past the ridge) and the smoker as workstation; L2 domes the oven, adds smoke, '
                               'flour sacks, shutters, flower boxes, sign and bench; L3 a bread stall, wheat bales, '
                               'banner, lanterns and flowers.')]
