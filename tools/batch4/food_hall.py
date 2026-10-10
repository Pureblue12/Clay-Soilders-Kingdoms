"""Tavern / food hall (tier 4, look A): a long timber hall with one long table, a big brick hearth at the far end, a
kitchen corner and a bar of barrels."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def food_hall(width=7, depth=11):
    """Food hall in a 13 x 9 x 15 box (x 0..2: the terrace) (9 tall: within builders' reach, no scaffolding).

    Walls x 4..10, z 2..12 (7 x 11), 3 high; spruce gable roof with dark oak eaves and ridge along the hall, one block
    of overhang all round, open rafters inside. The far (back) gable carries the signature: a brick hearth in the wall
    with its chimney breast outside, rising through the roof overhang.

    L1 (KNOWN-OK only): cobblestone plinth, a stone brick course under white plaster walls in a dark oak frame,
    spruce plank gables. Front door with windows, a kitchen door on the right side, windows down both long walls.
    Inside: one long table (spruce slabs) with a bench on each side, the hearth (a campfire inside the brick hearth),
    the kitchen corner (smoker, barrels, a chest) by the kitchen door, the bar on the left (a counter and kegs),
    torches.
    L2 (open for business): a projecting sign on a bracket, lanterns under the front gable and on a chain over the
    door, chandeliers over the table, shutters, red banners on the front, firewood at the
    hearth and by the kitchen door, a step at the kitchen door, plates on the table.
    L3 (the beer garden): a little terrace on the left with two outdoor tables and benches under a lantern post,
    flower boxes under the windows, candles on the long table, kegs and a crate outside the kitchen, flowers and
    bushes, sign text (decoration step)."""
    W, D, H = width + 6, depth + 4, 9
    s = Structure('food_hall', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 4, 3 + width, 2, 1 + depth          # 4..10, 2..12 (x 0..2: the terrace)
    mid = (x0 + x1) // 2                                   # 7
    top = 3
    posts = {z0, z0 + 3, z0 + 7, z1}                       # 2, 5, 9, 12 on the long walls
    kdoor = z1 - 2                                         # 10: kitchen door on the right wall
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 11) < 0.25 else 'cobblestone')
            else:
                P(s, x, 0, z, 'spruce_planks' if (x + z) % 4 else 'oak_planks')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner or (x in (x0, x1) and z in posts):
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == 1:
                    P(s, x, y, z, 'stone_bricks')
                else:
                    P(s, x, y, z, 'white_terracotta')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door', hinge='left')
    s.remove(x1, 1, kdoor); s.remove(x1, 2, kdoor)
    s.door(x1, 1, kdoor, 'east', block='oak_door', hinge='left')
    windows = [(mid - 2, 2, z0), (mid + 2, 2, z0),
               (x0, 2, z0 + 1), (x0, 2, z0 + 2), (x0, 2, z0 + 5), (x0, 2, z1 - 2),
               (x1, 2, z0 + 1), (x1, 2, z0 + 2), (x1, 2, z0 + 5)]
    for w in windows:
        s.remove(*w)
    # the hearth: a brick surround in the back wall, the chimney breast outside it, the stack up through the overhang
    for y in (1, 2, 3):
        P(s, mid - 1, y, z1, 'bricks'); P(s, mid + 1, y, z1, 'bricks')
    s.remove(mid, 1, z1)
    P(s, mid, 2, z1, 'bricks'); P(s, mid, 3, z1, 'bricks')
    for x in (mid - 1, mid, mid + 1):
        for y in (0, 1, 2, 3):
            P(s, x, y, z1 + 1, 'bricks')
    for y in range(4, 9):                                  # up to the top of the ridge cap
        P(s, mid, y, z1 + 1, 'bricks')
    # timber-framed gables: a tie beam, a king post, plaster panels, two little gable windows
    for z in (z0, z1):
        for x in range(x0 + 1, x1):
            if not (z == z1 and abs(x - mid) <= 1):
                P(s, x, top + 1, z, 'dark_oak_log', axis='x')
    for y in (top + 2, top + 3):                                       # king post on the front gable
        P(s, mid, y, z0, 'dark_oak_log', axis='y')
    for x in (mid - 1, mid + 1):
        P(s, x, top + 2, z0, 'glass_pane')
    # a dormer on the right slope over the long table: its window lights the hall, it breaks up the long roof
    dz = (z0 + 3, z0 + 4, z0 + 5)                                       # 5, 6, 7
    dormer = {(x1, top + 2, z) for z in dz} | {(x1 - 1, top + 3, z) for z in dz}
    gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, gable='white_terracotta',
               skip={(mid, 7, z1 + 1), (mid, 8, z1 + 1)} | dormer)
    P(s, x1, top + 2, dz[0], 'dark_oak_log', axis='y'); P(s, x1, top + 2, dz[2], 'dark_oak_log', axis='y')
    P(s, x1, top + 2, dz[1], 'glass_pane')
    for x in (x1 - 1, x1, x1 + 1):
        P(s, x, top + 3, dz[0], 'spruce_stairs', facing='south', **STAIR)
        P(s, x, top + 3, dz[2], 'spruce_stairs', facing='north', **STAIR)
        P(s, x, top + 3, dz[1], 'dark_oak_planks')
        P(s, x, top + 4, dz[1], 'spruce_slab', **SLAB)
    P(s, x1 + 1, top + 2, dz[0], 'spruce_stairs', facing='north', **TOP)    # corbels under the dormer's overhang
    P(s, x1 + 1, top + 2, dz[2], 'spruce_stairs', facing='south', **TOP)
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)     # step at the front door

    s.at(1, 'fittings')
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    P(s, mid, 1, z1, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')  # the hearth
    # the long table and its benches
    for z in range(z0 + 3, z0 + 8):                        # 5..9
        P(s, mid, 1, z, 'spruce_slab', **SLAB_TOP)
        P(s, mid - 1, 1, z, 'oak_stairs', facing='west', **STAIR)
        P(s, mid + 1, 1, z, 'oak_stairs', facing='east', **STAIR)
    # kitchen corner (right, by the kitchen door): smoker, barrels, chest
    P(s, x1 - 1, 1, z1 - 1, 'smoker', facing='west', lit='false')
    P(s, x1 - 2, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 1, 1, kdoor - 1, 'chest', facing='west', type='single', waterlogged='false')
    # the bar (left): a counter of slabs on barrels, kegs against the wall behind it
    P(s, x0 + 2, 1, z1 - 2, 'barrel', facing='up', open='false')
    P(s, x0 + 2, 1, z1 - 1, 'spruce_slab', **SLAB_TOP)
    for z in (z1 - 2, z1 - 1):
        P(s, x0 + 1, 1, z, 'barrel', facing='east', open='false')
    P(s, x0 + 1, 2, z1 - 1, 'barrel', facing='east', open='false')
    P(s, x0 + 1, 1, z0 + 1, 'barrel', facing='up', open='false')          # by the door
    for x, z, f in ((x0 + 1, z0 + 4, 'east'), (x1 - 1, z0 + 4, 'west'), (x0 + 1, z1 - 4, 'east'),
                    (x1 - 1, z1 - 4, 'west')):
        P(s, x, 3, z, 'wall_torch', facing=f)

    # ------------------------------------------------------------------ L2: open for business
    s.at(2, 'flourish2')
    # projecting sign: a fence bracket under the right front eave, the sign hanging from it
    P(s, x1 + 1, 3, z0, 'spruce_fence')
    P(s, x1 + 1, 2, z0, 'oak_hanging_sign', rotation='8', attached='true', waterlogged='false')
    s.decorate(x1 + 1, 2, z0, 'sign_text', lines=['', 'The Clay', 'Tankard', ''])
    for x in (mid - 2, mid + 2):                                       # lanterns under the gable corbels
        P(s, x, 4, z0 - 1, 'lantern', **HANG)
    for y in (5, 6):                                                    # over the door, on a chain from the ridge
        P(s, mid, y, z0 - 1, 'chain', axis='y')
    P(s, mid, 4, z0 - 1, 'lantern', **HANG)
    for z in (z0 + 3, z0 + 7):                                          # chandeliers over the table
        P(s, mid, 6, z, 'chain', axis='y'); P(s, mid, 5, z, 'lantern', **HANG)
    for x, y, z in windows[:2]:
        shutters(s, x, y, z, 'north')
    for x, side in ((x0, 'west'), (x1, 'east')):                       # side windows: pairs share outer shutters
        out = x - 1 if side == 'west' else x + 1
        for z in (z0, z0 + 3, z0 + 4, z0 + 6):
            if (out, z) != (x1 + 1, z0):                               # the sign bracket is there
                P(s, out, 2, z, 'spruce_trapdoor', facing=side, **OPEN)
    shutters(s, x0, 2, z1 - 2, 'west')
    P(s, mid - 1, 3, z0 - 1, 'red_wall_banner', facing='north')
    P(s, mid + 1, 3, z0 - 1, 'red_wall_banner', facing='north')
    for x in (mid - 1, mid + 1):
        s.decorate(x, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='a tankard')
    for x in (mid - 2, mid + 2):                                       # firewood beside the chimney breast
        P(s, x, 0, z1 + 1, 'spruce_log', axis='z'); P(s, x, 0, z1 + 2, 'spruce_log', axis='z')
        P(s, x, 1, z1 + 1, 'spruce_log', axis='z')
    # FUNCTION: a bigger kitchen. An outdoor kitchen under a lean-to outside the kitchen door (x 11..12, z 9..12):
    # a second smoker, a prep table, a barrel of supplies; a serving hatch (counter) between the kitchen corner and
    # the hall; more seats (a small table for two at the front of the hall).
    P(s, x1 + 1, 0, kdoor, 'cobblestone_stairs', facing='west', **STAIR)   # step at the kitchen door
    for z in range(kdoor - 1, kdoor + 3):                                # lean-to roof against the wall, under the eave
        P(s, x1 + 1, 3, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, x1 + 2, 2, z, 'spruce_stairs', facing='west', **STAIR)
    for z in (kdoor - 1, kdoor + 2):                                     # its posts
        P(s, x1 + 2, 0, z, 'spruce_fence'); P(s, x1 + 2, 1, z, 'spruce_fence')
    P(s, x1 + 1, 0, kdoor - 1, 'crafting_table')                        # prep table
    P(s, x1 + 1, 0, kdoor + 1, 'smoker', facing='east', lit='true')     # the second smoker
    P(s, x1 + 2, 0, kdoor, 'barrel', facing='up', open='false')         # supplies
    P(s, x1 + 1, 2, kdoor + 1, 'lantern', **HANG)                        # hangs from the lean-to roof
    P(s, x1 - 2, 1, kdoor, 'spruce_slab', **SLAB_TOP)                   # serving hatch: the cook passes food here
    s.decorate(x1 - 2, 2, kdoor, 'item', item='minecraft:cooked_beef', note='a plate waiting on the hatch')
    P(s, x1 - 1, 1, z0 + 2, 'spruce_slab', **SLAB_TOP)                  # a small table for two by the front window
    P(s, x1 - 1, 1, z0 + 1, 'oak_stairs', facing='north', **STAIR)
    P(s, x1 - 2, 1, z0 + 2, 'oak_stairs', facing='west', **STAIR)
    for z in (z0 + 4, z0 + 6):
        s.decorate(mid, 2, z, 'item', item='minecraft:bread', note='bread on the long table')
    P(s, mid - 1, 1, z0 + 1, 'red_carpet'); P(s, mid, 1, z0 + 1, 'red_carpet'); P(s, mid + 1, 1, z0 + 1, 'red_carpet')
    P(s, x0 + 1, 3, z1 - 2, 'spruce_trapdoor', facing='east', **SHELF)   # shelves over the bar

    # ------------------------------------------------------------------ L3: the beer garden
    s.at(3, 'flourish3')
    # terrace on the left: two outdoor tables (fence and plate) with stair seats, a lantern post between them,
    # a low hedge with flowers along the outside, a gravel floor patch under each table
    tx = x0 - 3                                                         # 1
    for z in (z0 + 2, z0 + 6):
        P(s, tx, 0, z, 'spruce_fence'); P(s, tx, 1, z, 'oak_pressure_plate', powered='false')
        P(s, tx, 0, z - 1, 'spruce_stairs', facing='north', **STAIR)
        P(s, tx, 0, z + 1, 'spruce_stairs', facing='south', **STAIR)
        P(s, tx + 1, 0, z, 'spruce_stairs', facing='east', **STAIR)
    s.decorate(tx, 2, z0 + 2, 'item', item='minecraft:bread', note='a meal on the outdoor table')
    for y in (0, 1):
        P(s, tx, y, z0 + 4, 'spruce_fence')
    P(s, tx, 2, z0 + 4, 'lantern', **STAND)
    for z in range(z0, z0 + 9):
        if z % 2 == 0:
            P(s, 0, 0, z, 'oak_leaves', **LEAVES)
        else:
            P(s, 0, 0, z, ('poppy', 'cornflower', 'oxeye_daisy', 'allium')[(z // 2) % 4])
    for z in (z0 + 4, z0 + 6):                                          # candles on the long table
        P(s, mid, 2, z, 'candle', candles='3', lit='true', waterlogged='false')
    # flower boxes under the right-hand windows
    for z, f in ((z0 + 1, 'poppy'), (z0 + 2, 'cornflower'), (z0 + 5, 'oxeye_daisy')):
        P(s, x1 + 1, 0, z, 'coarse_dirt'); P(s, x1 + 1, 1, z, f)
        P(s, x1 + 2, 0, z, 'spruce_trapdoor', facing='east', **OPEN)
    for z, f in ((z0, 'north'), (z0 + 3, 'south'), (z0 + 4, 'north'), (z0 + 6, 'south')):   # box ends
        P(s, x1 + 1, 0, z, 'spruce_trapdoor', facing=f, **OPEN)
    # FUNCTION: a bread oven behind the outdoor kitchen (its mouth opens into the lean-to) and a storeroom shed at the
    # back of the terrace (barrels, a chest, an open barrel of potatoes); the terrace itself is an extra dining area.
    for x in (x1 + 1, x1 + 2):
        for z in (z1 + 1, z1 + 2):
            P(s, x, 0, z, 'bricks'); P(s, x, 1, z, 'bricks')
            P(s, x, 2, z, 'brick_slab', type='bottom', waterlogged='false')
    s.remove(x1 + 1, 0, z1 + 1)
    P(s, x1 + 1, 0, z1 + 1, 'furnace', facing='north', lit='true')     # the oven mouth
    s.remove(x1 + 2, 2, z1 + 2)
    P(s, x1 + 2, 2, z1 + 2, 'bricks'); P(s, x1 + 2, 3, z1 + 2, 'brick_wall')   # its little flue
    P(s, x1 + 2, 0, kdoor - 2, 'hay_block', axis='y')                    # wheat for the oven
    sx0, sz0, sz1 = 0, z1 - 1, z1 + 2                                     # storeroom shed: x 0..2, z 11..14
    for z in range(sz0, sz1 + 1):                                        # cobblestone course, plank walls, log corners
        P(s, sx0, 0, z, 'cobblestone'); P(s, sx0, 1, z, 'spruce_log' if z in (sz0, sz1) else 'spruce_planks', **({'axis': 'y'} if z in (sz0, sz1) else {}))
    for x in (sx0 + 1, sx0 + 2):
        P(s, x, 0, sz1, 'cobblestone')
        P(s, x, 1, sz1, 'spruce_log' if x == sx0 + 2 else 'spruce_planks', **({'axis': 'y'} if x == sx0 + 2 else {}))
    P(s, sx0 + 2, 0, sz0, 'spruce_fence'); P(s, sx0 + 2, 1, sz0, 'spruce_fence')
    for x in range(sx0, sx0 + 3):
        for z in range(sz0, sz1 + 1):
            P(s, x, 2, z, 'spruce_slab', **SLAB)
    P(s, sx0 + 1, 0, sz1 - 1, 'barrel', facing='up', open='false'); P(s, sx0 + 1, 1, sz1 - 1, 'barrel', facing='up', open='false')
    P(s, sx0 + 1, 0, sz1 - 2, 'barrel', facing='east', open='false')
    P(s, sx0 + 2, 0, sz1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    P(s, sx0 + 1, 0, sz0, 'barrel', facing='up', open='true')
    s.decorate(sx0 + 1, 1, sz0, 'item', item='minecraft:potato', note='potatoes in the open barrel')
    for x, z in ((x0 - 1, z1),):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for x, z, f in ((x0 - 1, z0, 'allium'), (mid - 1, z0 - 1, 'poppy'), (mid + 1, z0 - 1, 'dandelion'),
                    (x1 + 1, z0 - 1, 'azure_bluet'), (x0, z1 + 1, 'cornflower'), (x1, z1 + 1, 'fern')):
        A(s, x, 0, z, f)
    return connect(s)


STRUCTURES = [dict(path='food_hall', name='Tavern / food hall', tier=4, zone='inner', role='food', group='food_hall',
                   terrain='skirt', door='front', make=food_hall, changes='First version.',
                   description='Over budget: 504 blocks vs 345 (first build 417 vs 280). Long timber food hall: one long table with benches under a dormer, a big brick hearth and chimney at '
                               'the far end, a kitchen corner (smoker) by a side door and a bar of kegs; L2 a '
                               'projecting sign, lanterns, chandeliers, banners and a chimney up to the ridge; L3 a beer-garden terrace, '
                               'candles, flower boxes and kegs by the kitchen.')]
