"""Tannery (tier 3, look A, outer zone): a spruce work shed open to a yard of soaking vats and hide drying racks."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def tannery(width=5, depth=6, wall_height=3):
    """Tannery in a 12 x 10 box: the shed (walls x 1..5, z 2..7) on the left, its right side open to the vat yard
    (x 6..11), a front row and a back row.

    L1 (KNOWN-OK only): cobblestone plinth, a cobblestone course under oak plank walls on spruce log posts, the right wall open in two bays
    under a log beam, so the tanner walks straight from the bench to the vats. Spruce roof with dark oak eaves and a
    slab ridge. Inside: crafting table (the workstation), a scraping bench (a stripped log), chest and barrels,
    torches. In the yard: four soaking vats (composters) and two open barrels of tan liquor in rows with walkways
    between them, a woodpile of bark by the shed.
    L2 (the drying rack): a long rack, its rail above soldier height on fence posts, raw and tanned hides
    (brown and white carpet) laid over it, two lime
    pits (cauldrons; liquid by the decoration step), leather stacked on the bench, shelves, a lantern on a chain in
    the shed and one on a post in the yard, shutters.
    L3 (finishing): stretching frames (trapdoors on fence posts) with hides, barrels of finished leather, a water trough
    (decoration step), the sign, a banner, hides on the walls (item frames, decoration step), flowers and bushes."""
    W, D = 12, depth + 4
    s = Structure('tannery', (W, 8, D), palette='oak_a2')
    x0, x1, z0, z1 = 1, width, 2, 1 + depth                # 1..5, 2..7
    mid = (x0 + x1) // 2                                   # 3
    top = wall_height
    posts_r = {z0, z0 + 3, z1}                             # right wall posts; open bays between them
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'spruce_planks')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'spruce_log', axis='y')
                elif x == x1:                                       # open side to the yard
                    if z in posts_r:
                        P(s, x, y, z, 'spruce_log', axis='y')
                    elif y == top:
                        P(s, x, y, z, 'stripped_spruce_log', axis='z')
                elif y == 1 and noise(x, y, z, 9) < 0.3:
                    P(s, x, y, z, 'mossy_cobblestone')
                elif y == 1:
                    P(s, x, y, z, 'cobblestone')
                else:
                    P(s, x, y, z, 'oak_planks')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    windows = [(x0, 2, z0 + 2), (mid, 2, z1), (x0, 2, z1 - 1)]
    for w in windows:
        s.remove(*w)
    gable_roof(s, x0, x1, z0, z1, top, gable='oak_planks')

    s.at(1, 'fittings')
    P(s, x0 + 1, 1, z0 + 1, 'crafting_table')                            # the workstation
    P(s, x0 + 1, 1, z0 + 3, 'stripped_spruce_log', axis='z')            # scraping bench
    P(s, x0 + 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    P(s, x1 - 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 1, 2, z1 - 1, 'barrel', facing='up', open='false')
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    P(s, x0 + 1, 3, z0 + 3, 'wall_torch', facing='east')
    P(s, x1 - 1, 3, z1 - 1, 'wall_torch', facing='north')
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    # the vat yard: soaking vats (composters) and open barrels of tan liquor, walkways between the rows
    for x in (7, 8):
        for z in (z0 + 1, z0 + 2):
            P(s, x, 0, z, 'composter')
        P(s, x, 0, z0 + 4, 'barrel', facing='up', open='true')
    P(s, x1 + 1, 0, z1, 'spruce_log', axis='z'); P(s, x1 + 1, 1, z1, 'spruce_log', axis='z')   # bark pile

    s.at(2, 'flourish2')
    # drying racks: posts with a rail, hides over the rail
    rx = 10
    for z in range(z0 - 1, z1 + 1):
        P(s, rx, 2, z, 'spruce_fence')                                   # the rail, above soldier height
        if z in (z0 - 1, z0 + 2, z1):                                    # posts down to the ground
            P(s, rx, 0, z, 'spruce_fence'); P(s, rx, 1, z, 'spruce_fence')
        P(s, rx, 3, z, 'brown_carpet' if z % 2 else 'white_carpet')      # raw and tanned hides over the rail
    for x in (7, 8):                                                     # lime pits
        P(s, x, 0, z0 + 5, 'cauldron')
        s.decorate(x, 0, z0 + 5, 'water_cauldron', note='lime water')
    s.decorate(7, 0, z0 + 1, 'item', item='minecraft:rabbit_hide', note='hides soaking')
    P(s, x0 + 1, 2, z0 + 3, 'brown_carpet')                              # leather on the bench
    P(s, x0 + 1, 2, z1 - 1, 'brown_carpet')
    P(s, x0 + 1, 3, z0 + 2, 'spruce_trapdoor', facing='east', **SHELF)   # a shelf by the window
    for y in (top + 1, top + 2):
        P(s, mid, y, z0 + 3, 'chain', axis='y')
    P(s, mid, top, z0 + 3, 'lantern', **HANG)
    P(s, 9, 0, z0 - 1, 'spruce_fence'); P(s, 9, 1, z0 - 1, 'lantern', **STAND)
    shutters(s, *windows[0], 'west'); shutters(s, *windows[1], 'south'); shutters(s, *windows[2], 'west')

    s.at(3, 'flourish3')
    # stretching frames along the back of the yard: fence posts with hides stretched on open trapdoors
    for x in (7, 9):
        for y in (0, 1):
            P(s, x, y, D - 1, 'spruce_fence')
    for x in (8,):
        P(s, x, 1, D - 1, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, x, 0, D - 1, 'spruce_trapdoor', facing='north', **OPEN)
    for x in (7, 8, 9):
        P(s, x, 2, D - 1, 'spruce_slab', **SLAB)
    s.decorate(8, 1, D - 1, 'item_frame', item='minecraft:leather', facing='north', note='hide on the frame')
    # finished leather waiting by the shed front: barrels with a folded hide on top
    P(s, x1 + 1, 0, z0 - 1, 'barrel', facing='up', open='false')
    P(s, x1 + 2, 0, z0 - 1, 'barrel', facing='up', open='false')
    P(s, x1 + 1, 1, z0 - 1, 'brown_carpet')
    P(s, x0, 2, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(x0, 2, z0 - 1, 'sign_text', lines=['', 'Tannery', '', ''])
    P(s, x1, 3, z0 - 1, 'brown_wall_banner', facing='north')
    s.decorate(x1, 3, z0 - 1, 'banner_pattern', banner='brown_wall_banner', note='a hide')
    s.decorate(mid, 2, z0 + 1, 'item_frame', item='minecraft:leather', facing='south', note='leather on the wall')
    P(s, 11, 0, z0 + 1, 'cauldron'); s.decorate(11, 0, z0 + 1, 'water_cauldron', note='rinse trough')
    P(s, 11, 0, z0 + 2, 'cauldron'); s.decorate(11, 0, z0 + 2, 'water_cauldron', note='rinse trough')
    for x, z in ((0, 0), (0, D - 1), (11, D - 1), (5, D - 1)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for x, z, f in ((0, z0 + 1, 'poppy'), (0, z1, 'dandelion'), (11, z0 + 5, 'cornflower'), (6, D - 1, 'fern'),
                    (x0 + 1, D - 1, 'oxeye_daisy'), (11, 0, 'fern')):
        A(s, x, 0, z, f)
    return connect(s)


STRUCTURES = [dict(path='tannery', name='Tannery', tier=3, zone='outer', role='workshop', group='tannery',
                   terrain='flexible', door='front', make=tannery, changes='First version.',
                   description='Over budget: 252 blocks vs 132. Spruce work shed open on one side to a yard of soaking vats (composters, open barrels); '
                               'crafting table as workstation, scraping bench; L2 adds a long hide drying rack, lime '
                               'pits, leather and lanterns; L3 stretching frames, barrels of finished leather, rinse troughs, '
                               'sign, banner and flowers. Wants level ground.')]
