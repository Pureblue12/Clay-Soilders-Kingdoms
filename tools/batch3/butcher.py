"""Butcher / meat hall (tier 3, look A): a cool stone shop with a serving window, a smoker and meat hooks."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters, flower_box)


def butcher(width=5, depth=6, wall_height=3):
    """Butcher in a 10 x 10 box: walls x 2..6, z 2..7 (5 x 6), a yard on the left (x 0..1), a pen yard on the right
    (x 7..9), a front row and a back row.

    L1 (KNOWN-OK only): cobblestone plinth, stone brick walls with spruce log corners and a stripped spruce top plate,
    white plaster gables with a spruce post, spruce roof with dark oak eaves. Front: a spruce door in the middle and
    a serving window on each side with a counter (slab) inside. Inside: the smoker (workstation) at the back, a
    stripped log chopping table, a chest and barrels, a tie beam across the room with meat hooks (chains) under it,
    torches. Outside: a step, a chopping block and kindling on the left.
    L2 (the shop): a red and white striped awning over the front, a lantern at each end of the awning, hams on the
    hooks (decoration step), salt barrels and a stone sink (cauldron) by the smoker, a meat display on the counter,
    shutters at the side windows, a scrap tub by the chopping block.
    L3 (the yard): a small fenced holding pen on the right (hay, trough, water tub, a gap to walk in), the shop sign,
    banner, flower box, a lantern post, bushes and flowers."""
    W, D = width + 5, depth + 4
    s = Structure('butcher', (W, 8, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 1 + width, 2, 1 + depth           # 2..6, 2..7
    mid = (x0 + x1) // 2                                   # 4
    top = wall_height
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'stone_bricks')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'spruce_log', axis='y')
                elif y == 1 and noise(x, y, z, 5) < 0.3:
                    P(s, x, y, z, 'mossy_cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    serve = [(mid - 1, 2, z0), (mid + 1, 2, z0)]           # serving windows either side of the door
    side_win = [(x1, 2, z0 + 2), (x0, 2, z1 - 1), (mid, 2, z1)]
    for w in serve + side_win:
        s.remove(*w)
    for z in (z0, z1):                                     # a spruce post up the middle of each plaster gable
        for y in (top + 1, top + 2):
            P(s, mid, y, z, 'spruce_log', axis='y')
    gable_roof(s, x0, x1, z0, z1, top, plates='stripped_spruce_log', gable='white_terracotta')
    s.at(1, 'fittings')
    for x, y, z in serve:                                  # counters inside the serving windows
        P(s, x, 1, z0 + 1, 'spruce_slab', **SLAB_TOP)
    P(s, x0 + 1, 1, z1 - 1, 'smoker', facing='east', lit='false')      # the workstation
    P(s, mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    P(s, x1 - 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 1, 2, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 1, 1, z0 + 3, 'stripped_spruce_log', axis='z')            # chopping table
    for x in range(x0 + 1, x1):                                         # tie beam with meat hooks
        P(s, x, top + 1, z0 + 3, 'stripped_spruce_log', axis='x')
    for x in (x0 + 1, x1 - 1):
        P(s, x, top, z0 + 3, 'chain', axis='y')
    P(s, mid, top, z0 + 3, 'chain', axis='y')
    P(s, x0 + 1, 2, z0 + 2, 'wall_torch', facing='east')
    P(s, x1 - 1, 3, z1 - 1, 'wall_torch', facing='north')
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for x, y, z in side_win:                                            # panes in the side windows
        P(s, x, y, z, 'glass_pane')
    # chopping block and kindling in the left yard
    P(s, 0, 0, z0 + 2, 'stripped_oak_log', axis='y')
    P(s, 0, 0, z0 + 3, 'spruce_log', axis='z'); P(s, 1, 0, z0 + 3, 'spruce_log', axis='z')
    P(s, 0, 1, z0 + 3, 'spruce_log', axis='z')

    s.at(2, 'flourish2')
    for x in range(x0, x1 + 1):                                         # striped awning over the front
        P(s, x, top, z0 - 1, 'red_wool' if x % 2 == 0 else 'white_wool')
    for x in (x0, x1):                                                  # lanterns under the awning ends
        P(s, x, top - 1, z0 - 1, 'lantern', **HANG)
    for x in (x0 + 1, x1 - 1, mid):
        s.decorate(x, top - 1, z0 + 3, 'item', item='minecraft:porkchop', note='a ham on the hook')
    P(s, x0 + 1, 1, z1 - 2, 'barrel', facing='up', open='true')         # salt barrel by the smoker
    s.decorate(x0 + 1, 1, z1 - 2, 'item', item='minecraft:sugar', note='salt')
    P(s, x1 - 1, 1, z0 + 2, 'cauldron')                                 # stone sink by the window
    s.decorate(x1 - 1, 1, z0 + 2, 'water_cauldron', note='wash water')
    for x, y, z in serve:
        s.decorate(x, 2, z0 + 1, 'item', item='minecraft:cooked_beef', note='meat on the counter')
    shutters(s, *side_win[0], 'east')
    shutters(s, *side_win[1], 'west')
    shutters(s, *side_win[2], 'south')
    P(s, x1 - 1, 2, z0 + 3, 'oak_pressure_plate', powered='false')      # a board on the chopping table
    s.decorate(x1 - 1, 2, z0 + 3, 'item', item='minecraft:iron_axe', note='the cleaver')
    P(s, 1, 0, z0 + 1, 'barrel', facing='up', open='false')             # tub for scraps by the chopping block

    s.at(3, 'flourish3')
    # holding pen on the right: fence on three sides against the shop wall, a gap at the front to walk in
    px0, px1, pz0, pz1 = x1 + 1, W - 1, z0 + 1, z1
    for x in range(px0, px1 + 1):
        if x != px0:
            P(s, x, 0, pz0, 'spruce_fence')                             # gap at (px0, pz0)
        P(s, x, 0, pz1, 'spruce_fence')
    for z in range(pz0, pz1 + 1):
        P(s, px1, 0, z, 'spruce_fence')
    P(s, px0, 0, pz1 - 1, 'hay_block', axis='y')
    P(s, px0 + 1, 0, pz1 - 1, 'composter')                              # feed trough
    P(s, px0 + 1, 0, pz0 + 1, 'cauldron')
    s.decorate(px0 + 1, 0, pz0 + 1, 'water_cauldron', note='water for the pen')
    P(s, px1, 1, pz0, 'lantern', **STAND)
    P(s, x0 - 1, 2, z0, 'oak_wall_sign', facing='west', waterlogged='false')
    s.decorate(x0 - 1, 2, z0, 'sign_text', lines=['', 'Butcher', '', ''])
    P(s, x1 + 1, 2, z0, 'red_wall_banner', facing='east')
    s.decorate(x1 + 1, 2, z0, 'banner_pattern', banner='red_wall_banner', note='a boar')
    s.decorate(x1 - 1, 2, z1 - 1, 'item_frame', item='minecraft:iron_axe', facing='north', note='cleaver on the wall')
    flower_box(s, x0 - 1, 0, z1 - 1, 'west', 'poppy')
    P(s, 0, 0, 0, 'spruce_fence'); P(s, 0, 1, 0, 'spruce_fence'); P(s, 0, 2, 0, 'lantern', **STAND)
    for x, z, f in ((1, z1 + 1, 'oak_leaves'), (W - 1, z1 + 1, 'oak_leaves'), (mid, z1 + 1, 'oak_leaves')):
        A(s, x, 0, z, f, **LEAVES)
    for x, z, f in ((mid - 1, z1 + 1, 'dandelion'), (mid + 1, z1 + 1, 'poppy'), (1, 0, 'cornflower'),
                    (W - 1, 0, 'oxeye_daisy'), (x1 + 1, 0, 'fern')):
        A(s, x, 0, z, f)
    return connect(s)


STRUCTURES = [dict(path='butcher', name='Butcher / meat hall', tier=3, zone='inner', role='food', group='butcher',
                   terrain='ground_level', door='front', make=butcher, changes='First version.',
                   description='Stone butcher shop with a plaster gable: door between two serving windows, smoker '
                               '(workstation), chopping table and meat hooks on a tie beam; L2 a striped awning, '
                               'lanterns, salt barrel and sink; L3 a fenced holding pen with hay and trough, sign, '
                               'banner and flowers.')]
