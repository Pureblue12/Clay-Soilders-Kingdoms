"""Library or school (tier 5, look A): a stone reading hall with a great round window filling its front gable
(docs/STRUCTURES.md, "Library or school"). Terrain: ground_level (layer 0 is the base course and floor).

Layout (11 wide x 11 deep x 9 tall, front z = 0): walls x 1..9, z 1..9 (9 x 9; the hall inside is 7 x 7), 3 high:
a cobblestone plinth course (some mossy) under stone brick walls, buttresses at the corners and halfway along both
sides, a spruce gable roof (dark oak eaves on dark oak top plates and a dark oak ridge) with one block of overhang all
round. The front gable is the round window: 21 panes in rows of 3-5-5-5-3 around a stone hub, framed by stone; the
back gable is white plaster in a dark oak frame with a brick chimney beside it.
- Inside: a reading table (3 posts) down the middle with chairs, book stacks standing out from both side walls at
  z 4 and 6, so every window (z 3, 5, 7) lights its own reading alcove; at the back the hearth, the archive chest,
  the scribe's desk and chair.
Level 1: the hall with its stacks as barrels of scrolls, the table, the desk, the hearth, torches.
Level 2 (the library stocked): bookshelves on every stack (two high), the librarian's lectern, candles, hanging
lanterns, red banners by the door, shutters and flower boxes on the side windows.
Level 3 (the scholars' library): carpets, a globe on a stand and a map table in the alcoves, a reading bench and
flowers outside, a lantern post, vines on the stone, pots on the hearth.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters, flower_box)

W, H, D = 13, 9, 11
X0, X1, Z0, Z1, TOP_Y = 2, 10, 1, 9, 3
MID = 6
WIN_Z = (3, 5, 7)
STACK_Z = (4, 6)
ROSE = {7: (-1, 0, 1), 6: (-2, -1, 0, 1, 2), 5: (-2, -1, 0, 1, 2), 4: (-2, -1, 0, 1, 2), 3: (-1, 0, 1)}
ROSE = {y: tuple(MID + d for d in ds) for y, ds in ROSE.items()}
HEARTH_X = 4


def library():
    s = Structure('library', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            P(s, x, 0, z, 'cobblestone' if edge else 'spruce_planks')
    for y in range(1, TOP_Y + 1):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                if y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 5) < 0.25 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    s.remove(MID, 1, Z0); s.remove(MID, 2, Z0)
    s.door(MID, 1, Z0, 'north', block='spruce_door')
    for z in WIN_Z:
        for x in (X0, X1):
            s.remove(x, 2, z)
            if z == 5:
                s.remove(x, 3, z)                               # the middle windows are 2 tall
    s.remove(HEARTH_X, 1, Z1)                                   # the hearth goes into the back wall
    # the round window: stone frame and hub, glass set in the fittings
    for y, xs in ROSE.items():
        for x in xs:
            if y == TOP_Y:
                s.remove(x, y, Z0)
    for y in (4, 5):
        P(s, X0 + 1, y, Z0, 'stone_bricks'); P(s, X1 - 1, y, Z0, 'stone_bricks')
    P(s, MID, 5, Z0, 'stone_bricks')                            # the hub
    # back gable: plaster in a dark oak frame (king post, beam), a small window
    for x in range(X0 + 1, X1):
        P(s, x, TOP_Y + 1, Z1, 'dark_oak_log', axis='x')
    for y in (5, 6, 7):
        P(s, MID, y, Z1, 'dark_oak_log', axis='y')
    for x in (MID - 2, MID + 2):
        P(s, x, 5, Z1, 'dark_oak_log', axis='y')
    # chimney: a brick breast behind the hearth, a stack through the back overhang
    chimney = [(HEARTH_X, y, Z1 + 1) for y in range(0, 8)]
    gable_roof(s, X0, X1, Z0, Z1, TOP_Y, front=1, back=1, cap=None, gable='white_terracotta', plates='dark_oak_log',
               skip=set(chimney) | {(x, y, Z0) for y, xs in ROSE.items() for x in xs})
    for x, y, z in chimney:
        P(s, x, y, z, 'bricks')
    for y in (0, 1):
        P(s, HEARTH_X - 1, y, Z1 + 1, 'bricks'); P(s, HEARTH_X + 1, y, Z1 + 1, 'bricks')
    # buttresses at the corners (facing out the sides) and halfway along both sides
    for z in (Z0, (Z0 + Z1) // 2 + 0, Z1):
        for x, f in ((X0 - 1, 'east'), (X1 + 1, 'west')):
            if z == (Z0 + Z1) // 2:
                continue
            P(s, x, 0, z, 'cobblestone'); P(s, x, 1, z, 'stone_bricks')
            P(s, x, 2, z, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (X0, X1):                                          # front corners also face the street
        P(s, x, 0, Z0 - 1, 'cobblestone'); P(s, x, 1, Z0 - 1, 'stone_bricks')
        P(s, x, 2, Z0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    P(s, MID, 0, Z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)    # the step
    for x in (MID - 1, MID + 1):                                # the door frame: stone jambs carrying a hood
        P(s, x, 0, Z0 - 1, 'cobblestone'); P(s, x, 1, Z0 - 1, 'stone_bricks')
    P(s, MID - 1, 2, Z0 - 1, 'stone_brick_stairs', facing='west', **TOP)
    P(s, MID + 1, 2, Z0 - 1, 'stone_brick_stairs', facing='east', **TOP)

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for y, xs in ROSE.items():
        for x in xs:
            if (x, y) != (MID, 5):
                P(s, x, y, Z0, 'glass_pane')
    for z in WIN_Z:
        for x in (X0, X1):
            P(s, x, 2, z, 'glass_pane')
            if z == 5:
                P(s, x, 3, z, 'glass_pane')
    P(s, HEARTH_X, 1, Z1, 'furnace', facing='north', lit='false')      # the hearth
    # the stacks: barrels of scrolls standing out from the walls (books come at level 2)
    for z in STACK_Z:
        for x in (X0 + 1, X0 + 2, X1 - 2, X1 - 1):
            P(s, x, 1, z, 'barrel', facing='up', open='false')
    # reading table down the middle, chairs either side
    for z in (4, 5, 6):
        P(s, MID, 1, z, 'oak_fence'); P(s, MID, 2, z, 'oak_pressure_plate', powered='false')
        if z == 5:
            P(s, MID - 1, 1, z, 'oak_stairs', facing='west', **STAIR)
            P(s, MID + 1, 1, z, 'oak_stairs', facing='east', **STAIR)
    # reading alcoves at the front windows: a chair at each
    for x, f in ((X0 + 1, 'east'), (X1 - 1, 'west')):
        P(s, x, 1, 3, 'oak_stairs', facing=f, **STAIR)
    # back: archive chest, the scribe's desk and chair, scroll barrels
    P(s, MID, 1, Z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    P(s, MID + 1, 1, Z1 - 1, 'crafting_table')
    P(s, MID + 2, 1, Z1 - 1, 'oak_stairs', facing='east', **STAIR)
    P(s, X1 - 1, 1, Z1 - 1, 'barrel', facing='up', open='false')
    P(s, X1 - 1, 2, Z1 - 1, 'barrel', facing='up', open='false')
    P(s, X0 + 1, 1, Z1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    P(s, X0 + 1, 3, Z0 + 1, 'wall_torch', facing='east'); P(s, X1 - 1, 3, Z0 + 1, 'wall_torch', facing='west')
    P(s, MID, 3, Z1 - 1, 'wall_torch', facing='north')

    # ---------------------------------------------------------------- level 2: the library stocked
    s.at(2, 'flourish2')
    for z in STACK_Z:
        for x in (X0 + 1, X0 + 2, X1 - 2, X1 - 1):
            P(s, x, 2, z, 'bookshelf'); P(s, x, 3, z, 'bookshelf')
    P(s, X1 - 1, 3, Z1 - 1, 'bookshelf')
    P(s, MID, 1, Z1 - 2, 'lectern', facing='north', has_book='false', powered='false')   # the librarian's lectern
    s.decorate(MID, 1, Z1 - 2, 'item', item='minecraft:writable_book', note='the catalogue on the lectern')
    CANDLES = dict(candles='3', lit='true', waterlogged='false')
    P(s, MID + 1, 2, Z1 - 1, 'candle', **CANDLES)                   # on the desk
    P(s, MID, 2, Z1 - 1, 'candle', **CANDLES)                       # on the archive chest
    for z in (4, 6):                                               # lanterns on chains from the ridge (y 8)
        P(s, MID, 7, z, 'chain', axis='y'); P(s, MID, 6, z, 'lantern', **HANG)
    for x in (MID - 2, MID + 2):                                   # banners by the door
        P(s, x, 2, Z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 2, Z0 - 1, 'banner_pattern', banner='red_wall_banner', note='an open book')
    for x, side in ((X0, 'west'), (X1, 'east')):                   # shutters and flower boxes, side windows
        for z in (3, 7):
            shutters(s, x, 2, z, side)
            flower_box(s, x + (-1 if side == 'west' else 1), 1, z, side, 'poppy' if z == 3 else 'cornflower')
    P(s, MID, 3, Z0 - 1, 'lantern', **STAND)                       # on the door hood

    # ---------------------------------------------------------------- level 3: the scholars' library
    s.at(3, 'flourish3')
    for z in (Z0 + 1, Z0 + 2, Z1 - 1 + 0):
        A(s, MID, 1, z, 'red_carpet')
    for x in (X0 + 2, X1 - 2):                                     # carpets in the middle alcoves
        A(s, x, 1, 5, 'green_carpet')
    for x in (MID - 1, MID + 1):
        for z in (4, 6):
            A(s, x, 1, z, 'red_carpet')
    # the globe on a stand (left middle alcove) and a map table (right)
    P(s, X0 + 1, 1, 5, 'spruce_fence')
    P(s, X0 + 1, 2, 5, 'light_blue_stained_glass')
    P(s, X1 - 1, 1, 5, 'cartography_table')
    s.decorate(X1 - 1, 2, 5, 'item_frame', item='minecraft:filled_map', note='the kingdom map, framed on the wall')
    # outside: a reading bench and flower bed along the front, a lantern post, vines on the stone
    for x in (X0 + 1, X0 + 2):
        P(s, x, 0, 0, 'spruce_stairs', facing='north', **STAIR)
    for x, f in ((X1 - 2, 'oxeye_daisy'), (X1 - 1, 'azure_bluet')):
        P(s, x, 0, 0, f)
    for y in (0, 1):
        P(s, X1 + 1, y, 0, 'spruce_fence')
    P(s, X1 + 1, 2, 0, 'lantern', **STAND)
    for z in (Z1 - 1, Z1 - 2):
        for y in (1, 2):
            A(s, X0 - 1, y, z, 'vine', east='true', west='false', north='false', south='false', up='false')
    for x, z, b in ((0, 1, 'leaves'), (W - 1, 1, 'leaves'), (0, 9, 'leaves'), (W - 1, 9, 'poppy'), (7, 10, 'cornflower'),
                    (1, 10, 'leaves'), (W - 2, 10, 'oxeye_daisy')):
        if b == 'leaves':
            A(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 0, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='library', name='Library or school', tier=5, zone='inner', role='civic', group='library',
                   terrain='ground_level', door='front', make=library, changes='First version.',
                   description='Stone reading hall whose front gable is a great round window; book stacks stand out '
                               'from both walls between the windows, a reading table down the middle, the hearth, '
                               'archive and scribe\'s desk at the back; L2 fills the stacks with bookshelves and adds '
                               'the lectern, candles, lanterns, banners, shutters and flower boxes; L3 carpets, a globe '
                               'and map table, a bench, flowers and a lantern post outside.')]
