"""Library or school (tier 5, look A): a stone reading hall with a great round window over its door
(docs/STRUCTURES.md, "Library or school"). Terrain: ground_level (layer 0 is the base course and floor).

Layout (11 wide x 11 deep x 9 tall, front z = 0): walls x 2..8, z 1..9 (7 x 9 as in the brief; the hall inside is
5 x 7), 4 high: a cobblestone plinth course (some mossy) under stone brick walls, stepped buttresses at the four
corners, a spruce gable roof (dark oak eaves on dark oak top plates, a dark oak ridge) with one block of overhang all
round. The front is the round window (the signature): 19 panes in rows of 3-5-5-5-3 around a stone hub, rising from
just over the door into the gable. The back gable is white plaster in a dark oak frame, with a brick chimney.
- Inside: a reading table down the middle with chairs, book stacks standing out from both side walls at z 4 and 6, so
  each side window (z 3, 5, 7) lights its own reading alcove; at the back the hearth, the archive chest, the scribe's
  desk and chair and a tall scroll press.
Level 1: the hall, its stacks holding barrels of scrolls, the table, the desk, the hearth, torches.
Level 2 (the library stocked): bookshelves on every stack (two high) and the press, the librarian's lectern, candles,
hanging lanterns, red banners and a lantern at the door, shutters and flower boxes on the side windows.
Level 3 (the scholars' library): carpets, a globe on a stand and a map table in the middle alcoves, a bench, flowers
and a lantern post outside, vines on the stone, a framed map (decoration step).
"""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, OPEN, HANG, STAND, LEAVES, connect, noise, gable_roof, shutters,
                           flower_box)

W, H, D = 11, 9, 11
X0, X1, Z0, Z1, TOP_Y = 2, 8, 1, 9, 4
MID = 5
WIN_Z = (3, 5, 7)
STACK_Z = (4, 6)
ROSE = {7: (-1, 0, 1), 6: (-2, -1, 0, 1, 2), 5: (-2, -1, 0, 1, 2), 4: (-2, -1, 0, 1, 2), 3: (-1, 0, 1)}
ROSE = {y: tuple(MID + d for d in ds) for y, ds in ROSE.items()}
HUB = (MID, 5)
HEARTH_X = X0 + 1
CANDLES = dict(candles='3', lit='true', waterlogged='false')


def library():
    s = Structure('library', (W, H, D), palette='oak_a2')
    rose = {(x, y, Z0) for y, xs in ROSE.items() for x in xs}
    s.at(1, 'shell')
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            P(s, x, 0, z, 'cobblestone' if edge else 'spruce_planks')
    for y in range(1, TOP_Y + 1):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)) or (x, y, z) in rose:
                    continue
                if y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 5) < 0.25 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    s.remove(MID, 1, Z0); s.remove(MID, 2, Z0)
    s.door(MID, 1, Z0, 'north', block='spruce_door')
    for z in WIN_Z:
        for x in (X0, X1):
            s.remove(x, 2, z); s.remove(x, 3, z)
    s.remove(HEARTH_X, 1, Z1)                                   # the hearth goes into the back wall
    P(s, *HUB, Z0, 'stone_bricks')                              # the round window's hub
    # back gable: plaster in a dark oak frame (tie beam, king post, two posts)
    for x in range(X0 + 1, X1):
        P(s, x, TOP_Y + 1, Z1, 'dark_oak_log', axis='x')
    for y in (TOP_Y + 2, TOP_Y + 3):
        P(s, MID, y, Z1, 'dark_oak_log', axis='y')
    for x in (MID - 2, MID + 2):
        P(s, x, TOP_Y + 2, Z1, 'dark_oak_log', axis='y')
    chimney = [(HEARTH_X, y, Z1 + 1) for y in range(0, H)]
    gable_roof(s, X0, X1, Z0, Z1, TOP_Y, front=1, back=1, cap=None, gable='white_terracotta', plates='dark_oak_log',
               skip=set(chimney) | rose)
    for x, y, z in chimney:                                     # a brick chimney, one above the roof
        P(s, x, y, z, 'bricks')
    for y in (0, 1):
        P(s, HEARTH_X - 1, y, Z1 + 1, 'bricks'); P(s, HEARTH_X + 1, y, Z1 + 1, 'bricks')
    # stepped buttresses at the corners, facing the sides and the front / back
    for z in (Z0, Z1):
        for x, f in ((X0 - 1, 'east'), (X1 + 1, 'west')):
            P(s, x, 0, z, 'cobblestone'); P(s, x, 1, z, 'stone_bricks'); P(s, x, 2, z, 'stone_bricks')
            P(s, x, 3, z, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (X0, X1):
        P(s, x, 0, Z0 - 1, 'cobblestone'); P(s, x, 1, Z0 - 1, 'stone_bricks')
        P(s, x, 2, Z0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    # the door: stone jambs either side, a step
    for x in (MID - 1, MID + 1):
        P(s, x, 0, Z0 - 1, 'cobblestone'); P(s, x, 1, Z0 - 1, 'stone_bricks'); P(s, x, 2, Z0 - 1, 'stone_bricks')
    P(s, MID, 0, Z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for x, y, z in sorted(rose):
        if (x, y) != HUB:
            P(s, x, y, z, 'glass_pane')
    for z in WIN_Z:
        for x in (X0, X1):
            P(s, x, 2, z, 'glass_pane'); P(s, x, 3, z, 'glass_pane')
    P(s, HEARTH_X, 1, Z1, 'furnace', facing='north', lit='false')      # the hearth
    for z in STACK_Z:                                                   # the stacks: scroll barrels for now
        for x in (X0 + 1, X1 - 1):
            P(s, x, 1, z, 'barrel', facing='up', open='false')
    for z in (4, 5, 6):                                                 # reading table, chairs either side
        P(s, MID, 1, z, 'oak_fence'); P(s, MID, 2, z, 'oak_pressure_plate', powered='false')
    P(s, MID - 1, 1, 5, 'oak_stairs', facing='west', **STAIR)
    P(s, MID + 1, 1, 5, 'oak_stairs', facing='east', **STAIR)
    P(s, X0 + 1, 1, 3, 'oak_stairs', facing='west', **STAIR)            # window seats in the front alcoves
    P(s, X1 - 1, 1, 3, 'oak_stairs', facing='east', **STAIR)
    P(s, X0 + 1, 1, 7, 'chest', facing='east', type='single', waterlogged='false')   # back alcoves: storage
    P(s, X1 - 1, 1, 7, 'barrel', facing='west', open='false')
    P(s, MID - 1, 1, Z1 - 1, 'chest', facing='north', type='single', waterlogged='false')   # the archive
    P(s, MID, 1, Z1 - 1, 'crafting_table')                              # the scribe's desk
    P(s, MID + 1, 1, Z1 - 1, 'oak_stairs', facing='east', **STAIR)
    P(s, X1 - 1, 1, Z1 - 1, 'barrel', facing='up', open='false')        # the scroll press
    P(s, X1 - 1, 2, Z1 - 1, 'barrel', facing='up', open='false')
    P(s, X0 + 1, 3, Z0 + 1, 'wall_torch', facing='east'); P(s, X1 - 1, 3, Z0 + 1, 'wall_torch', facing='west')
    P(s, MID, 3, Z1 - 1, 'wall_torch', facing='north')

    # ---------------------------------------------------------------- level 2: the library stocked
    s.at(2, 'flourish2')
    for z in STACK_Z:
        for x in (X0 + 1, X1 - 1):
            P(s, x, 2, z, 'bookshelf'); P(s, x, 3, z, 'bookshelf')
    P(s, X1 - 1, 3, Z1 - 1, 'bookshelf')
    P(s, MID, 1, Z1 - 2, 'lectern', facing='north', has_book='false', powered='false')   # the librarian's lectern
    s.decorate(MID, 1, Z1 - 2, 'item', item='minecraft:writable_book', note='the catalogue on the lectern')
    P(s, MID, 2, Z1 - 1, 'candle', **CANDLES)                         # on the desk
    P(s, MID - 1, 2, Z1 - 1, 'candle', **CANDLES)                     # on the archive chest
    P(s, X0 + 1, 2, 7, 'candle', **CANDLES)
    for z in STACK_Z:                                                  # lanterns on chains from the ridge (y 8)
        P(s, MID, 7, z, 'chain', axis='y'); P(s, MID, 6, z, 'lantern', **HANG)
    for x in (X0 + 1, X1 - 1):                                         # banners either side of the door
        P(s, x, 3, Z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, Z0 - 1, 'banner_pattern', banner='red_wall_banner', note='an open book')
    for x in (MID - 1, MID + 1):                                       # lanterns on the door jambs
        P(s, x, 3, Z0 - 1, 'lantern', **STAND)
    for x, side in ((X0, 'west'), (X1, 'east')):                      # shutters and flower boxes, side windows
        for z in (3, 7):
            shutters(s, x, 2, z, side); shutters(s, x, 3, z, side)
            flower_box(s, x + (-1 if side == 'west' else 1), 1, z, side, 'poppy' if z == 3 else 'cornflower')

    # ---------------------------------------------------------------- level 3: the scholars' library
    s.at(3, 'flourish3')
    for z in (Z0 + 1, Z0 + 2, Z1 - 1):
        A(s, MID, 1, z, 'red_carpet')
    for x in (MID - 1, MID + 1):
        for z in (4, 6):
            A(s, x, 1, z, 'red_carpet')
    for x in (X0 + 1, X1 - 1):
        A(s, x, 1, 2, 'green_carpet')
    P(s, X0 + 1, 1, 5, 'spruce_fence')                                 # the globe on a stand
    P(s, X0 + 1, 2, 5, 'light_blue_stained_glass')
    P(s, X1 - 1, 1, 5, 'cartography_table')                            # the map table
    s.decorate(X1, 3, 6, 'item_frame', item='minecraft:filled_map', note='the kingdom map, on the wall over the map table')
    for x, f in ((X0 + 1, 'poppy'), (X1 - 1, 'oxeye_daisy')):          # flowers either side of the steps
        A(s, x, 0, 0, f)
    for x in (MID + 1, MID + 2):                                       # a bench behind, by the chimney
        P(s, x, 0, Z1 + 1, 'spruce_stairs', facing='north', **STAIR)
    for y in (0, 1):                                                   # lantern post at the front right
        P(s, X1 + 1, y, 0, 'spruce_fence')
    P(s, X1 + 1, 2, 0, 'lantern', **STAND)
    for z in (4, 6):                                                   # vines between the side windows
        for y in (2, 3):
            A(s, X0 - 1, y, z, 'vine', east='true', west='false', north='false', south='false', up='false')
            A(s, X1 + 1, y, z, 'vine', west='true', east='false', north='false', south='false', up='false')
    for x, z, b in ((0, 1, 'leaves'), (W - 1, 2, 'leaves'), (0, 9, 'leaves'), (W - 1, 9, 'poppy'), (0, 5, 'azure_bluet'),
                    (W - 1, 5, 'oxeye_daisy'), (X1 + 1, 10, 'leaves'), (X0 - 1, 10, 'cornflower')):
        A(s, x, 0, z, 'oak_leaves', **LEAVES) if b == 'leaves' else A(s, x, 0, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='library', name='Library or school', tier=5, zone='inner', role='civic', group='library',
                   terrain='ground_level', door='front', make=library, changes='First version.',
                   description='Over budget: 469 blocks vs 310 (first build 394 vs 260). Stone reading hall whose front rises into a great round window over the door; book '
                               'stacks stand out from both walls between the windows, a reading table down the middle, '
                               'hearth, archive and scribe\'s desk at the back; L2 fills the stacks with bookshelves and '
                               'adds the lectern, candles, lanterns, banners, shutters and flower boxes; L3 carpets, a '
                               'globe and map table, a bench, flowers, vines and a lantern post.')]
