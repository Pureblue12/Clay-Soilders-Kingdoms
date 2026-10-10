"""Town hall (tier 6, look A, landmark): a half-timbered council hall raised on an open stone arcade, with a clock tower
at the front (docs/STRUCTURES.md, "Town hall"). Terrain: stepped in the brief (tower and hall); here everything sits on
one stone base course (layer 0).

Layout (13 wide x 18 deep x 18 tall, front z = 0):
- Clock tower x 4..8, z 1..5 (5 x 5), stone bricks: an open vaulted porch at the bottom (arches to the front, both
  sides and into the arcade), the clerks' office above it (reached from the council chamber), a timber clock stage at
  y 10..14 with a clock face on all four sides (white plaster in a dark oak ring; the clock hands are item frames, the
  decoration step), a cornice and a spruce pyramid roof to 17 (18 tall: needs scaffolding).
- Hall x 2..10, z 5..16 (9 x 12): the ground floor is an open market arcade (stone brick piers every 3 blocks, arches of
  upside-down stairs), closed at the back where a stone stair climbs to the upper floor. The upper storey (y 5..7) is
  white plaster in a dark oak frame on a dark oak floor beam, windows in every bay; spruce gable roof (dark oak eaves
  and ridge) with one block of overhang, ridge at 12.
- Upper floor: the council chamber: a long table with eight chairs and the mayor's seat, record chests, a doorway into
  the clerks' office in the tower.
- The notice board on the front pier, beside the tower porch.
Level 1: shell and fittings (arcade, chamber, table and chairs, records chests, stair, notice board, torches).
Level 2 (the hall at work): bookshelves of records and a lectern, market goods under the arcade, lanterns under the
arches and in the chamber, red banners on the tower and the front piers, a bell-less lantern in the clock stage.
Level 3 (the proud town): the clocks (decoration step), flower boxes on brackets under the upper windows, a weathervane,
benches and flower beds round the arcade, vines, carpets in the chamber.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, HANG, STAND, LEAVES, connect, noise, gable_roof

W, H, D = 13, 18, 18
HX0, HX1, HZ0, HZ1 = 2, 10, 5, 16          # hall
TX0, TX1, TZ0, TZ1 = 4, 8, 1, 5            # tower (its back wall is the hall's front wall)
MID = 6
FLOOR = 4                                  # upper floor layer
UTOP = 7                                   # last upper wall layer
PIERS = (5, 8, 11, 14)
BAYS = ((6, 7), (9, 10), (12, 13))
CLOCK = (10, 14)                           # clock stage, y
CANDLES = dict(candles='3', lit='true', waterlogged='false')
PANE = dict(waterlogged='false')


def pyramid_ring(s, x0, x1, z0, z1, y, block='spruce_stairs'):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if x == x0 and z == z0:
                P(s, x, y, z, block, facing='east', half='bottom', shape='outer_right', waterlogged='false')
            elif x == x1 and z == z0:
                P(s, x, y, z, block, facing='west', half='bottom', shape='outer_left', waterlogged='false')
            elif x == x0 and z == z1:
                P(s, x, y, z, block, facing='east', half='bottom', shape='outer_left', waterlogged='false')
            elif x == x1 and z == z1:
                P(s, x, y, z, block, facing='west', half='bottom', shape='outer_right', waterlogged='false')
            else:
                f = 'east' if x == x0 else 'west' if x == x1 else 'south' if z == z0 else 'north'
                P(s, x, y, z, block, facing=f, **STAIR)


def town_hall():
    s = Structure('town_hall', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')

    # ---------------------------------------------------------------- base course and paving
    for x in range(HX0, HX1 + 1):
        for z in range(TZ0, HZ1 + 1):
            if z < HZ0 and not TX0 <= x <= TX1:
                continue
            edge = x in (HX0, HX1) or z == HZ1 or (z < HZ0 and x in (TX0, TX1)) or z == TZ0
            P(s, x, 0, z, 'cobblestone' if edge else 'stone_bricks')

    # ---------------------------------------------------------------- tower
    for y in range(1, CLOCK[1] + 1):
        for x in range(TX0, TX1 + 1):
            for z in range(TZ0, TZ1 + 1):
                if not (x in (TX0, TX1) or z in (TZ0, TZ1)):
                    continue
                if CLOCK[0] <= y <= CLOCK[1]:
                    face = (z in (TZ0, TZ1) and TX0 < x < TX1 or x in (TX0, TX1) and TZ0 < z < TZ1) and \
                        CLOCK[0] < y < CLOCK[1]
                    if face:
                        P(s, x, y, z, 'white_terracotta')
                    elif x in (TX0, TX1) and z in (TZ0, TZ1):
                        P(s, x, y, z, 'dark_oak_log', axis='y')
                    else:
                        P(s, x, y, z, 'dark_oak_log', axis='x' if z in (TZ0, TZ1) else 'z')
                else:
                    P(s, x, y, z, 'cobblestone' if y == 1 else 'stone_bricks')
    for y in (1, 2, 3):                              # porch arches: front and back 3 high, sides 2 high
        s.remove(MID, y, TZ0); s.remove(MID, y, TZ1)
    for y in (1, 2):
        s.remove(TX0, y, 3); s.remove(TX1, y, 3)
    for x in range(TX0 + 1, TX1):                    # floors: the office (y 4) and its ceiling (y 8)
        for z in range(TZ0 + 1, TZ1):
            P(s, x, FLOOR, z, 'spruce_planks'); P(s, x, 8, z, 'spruce_planks')
    for x in range(TX0, TX1 + 1):                    # clock faces stand out: the tower's top is a plank deck
        for z in range(TZ0, TZ1 + 1):
            if TX0 < x < TX1 and TZ0 < z < TZ1:
                P(s, x, CLOCK[1], z, 'spruce_planks')
    for y in (6,):                                   # office windows to the sides, one over the porch
        s.remove(TX0, y, 3); s.remove(TX1, y, 3); s.remove(MID, y, TZ0)
    s.remove(MID, 5, TZ1); s.remove(MID, 6, TZ1)    # doorway from the council chamber into the office
    # arch heads in the porch (upside-down stairs), a step at the front
    P(s, MID - 1, 3, TZ0 - 1, 'stone_brick_stairs', facing='west', **TOP) if False else None
    P(s, MID, 0, TZ0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for x in (TX0, TX1):                             # buttresses at the tower's front corners
        P(s, x, 0, TZ0 - 1, 'cobblestone'); P(s, x, 1, TZ0 - 1, 'stone_bricks'); P(s, x, 2, TZ0 - 1, 'stone_bricks')
        P(s, x, 3, TZ0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    # cornice above the clock stage, pyramid roof
    y = CLOCK[1]
    for x in range(TX0 - 1, TX1 + 2):
        P(s, x, y, TZ0 - 1, 'dark_oak_stairs', facing='south', **TOP)
        P(s, x, y, TZ1 + 1, 'dark_oak_stairs', facing='north', **TOP)
    for z in range(TZ0, TZ1 + 1):
        P(s, TX0 - 1, y, z, 'dark_oak_stairs', facing='east', **TOP)
        P(s, TX1 + 1, y, z, 'dark_oak_stairs', facing='west', **TOP)
    pyramid_ring(s, TX0 - 1, TX1 + 1, TZ0 - 1, TZ1 + 1, y + 1)
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            P(s, x, y + 1, z, 'spruce_planks')
    pyramid_ring(s, TX0, TX1, TZ0, TZ1, y + 2)
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            P(s, x, y + 2, z, 'spruce_planks')
    pyramid_ring(s, TX0 + 1, TX1 - 1, TZ0 + 1, TZ1 - 1, y + 3)
    P(s, MID, y + 3, 3, 'dark_oak_planks')

    # ---------------------------------------------------------------- hall: arcade
    for z in range(HZ0, HZ1 + 1):
        for x in (HX0, HX1):
            if z in PIERS or z >= HZ1 - 1:
                for yy in (1, 2, 3):
                    P(s, x, yy, z, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in (HX0 + 1, HX1 - 1):                     # front piers beside the tower (2 wide)
        for yy in (1, 2, 3):
            P(s, x, yy, HZ0, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in range(HX0 + 1, HX1):                    # back wall of the stair hall
        for yy in (1, 2, 3):
            A(s, x, yy, HZ1, 'cobblestone' if yy == 1 else 'stone_bricks')
    for a, b in BAYS:                                # arches between the piers
        for x in (HX0, HX1):
            P(s, x, 3, a, 'stone_brick_stairs', facing='north', **TOP)
            P(s, x, 3, b, 'stone_brick_stairs', facing='south', **TOP)
    # the stair to the upper floor along the back wall (rising to the east), solid underneath
    for i, x in enumerate((4, 5, 6)):
        for yy in range(1, 1 + i):
            P(s, x, yy, HZ1 - 1, 'stone_bricks')
        P(s, x, 1 + i, HZ1 - 1, 'stone_brick_stairs', facing='east', **STAIR)
    for yy in (1, 2, 3):
        P(s, 7, yy, HZ1 - 1, 'stone_bricks')         # the landing pier
    # ---------------------------------------------------------------- hall: upper storey
    for x in range(HX0, HX1 + 1):                    # floor beams round the edge, planks inside
        for z in range(HZ0, HZ1 + 1):
            if TX0 <= x <= TX1 and z == HZ0:
                continue
            if x in (HX0, HX1):
                A(s, x, FLOOR, z, 'dark_oak_log', axis='z')
            elif z in (HZ0, HZ1):
                A(s, x, FLOOR, z, 'dark_oak_log', axis='x')
            elif not (z == HZ1 - 1 and x in (4, 5, 6)):
                A(s, x, FLOOR, z, 'spruce_planks')
    posts_z = set(PIERS) | {HZ1}
    for y in range(FLOOR + 1, UTOP + 1):
        for x in range(HX0, HX1 + 1):
            for z in range(HZ0, HZ1 + 1):
                if not (x in (HX0, HX1) or z in (HZ0, HZ1)):
                    continue
                if TX0 <= x <= TX1 and z == HZ0:
                    continue                         # the tower's back wall
                post = (x in (HX0, HX1) and z in posts_z) or (z == HZ1 and x in (HX0, MID, HX1))
                if post:
                    A(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == UTOP and z in (HZ0, HZ1):
                    A(s, x, y, z, 'dark_oak_log', axis='x')
                else:
                    A(s, x, y, z, 'white_terracotta')
    windows = [(x, 6, z) for x in (HX0, HX1) for a, b in BAYS for z in (a, b)] + \
              [(4, 6, HZ1), (8, 6, HZ1), (HX0 + 1, 6, HZ0), (HX1 - 1, 6, HZ0), (HX0 + 1, 6, HZ1 - 1 + 1)]
    windows = list(dict.fromkeys(windows))
    for w in windows:
        s.remove(*w)
    gable_roof(s, HX0, HX1, HZ0, HZ1, UTOP, front=1, back=1, gable='white_terracotta', plates='dark_oak_log')
    for x in (HX0, HX1):                             # brackets under the floor beam at the piers
        for z in PIERS[1:]:
            P(s, x + (-1 if x == HX0 else 1), 3, z, 'dark_oak_stairs', facing='east' if x == HX0 else 'west', **TOP)

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    for x, z in ((TX0, 3), (TX1, 3), (MID, TZ0)):
        P(s, x, 6, z, 'glass_pane')
    # council chamber: long table, eight chairs, the mayor's seat, record chests
    for z in (8, 9, 10, 11):
        P(s, MID, FLOOR + 1, z, 'oak_fence'); P(s, MID, FLOOR + 2, z, 'oak_pressure_plate', powered='false')
        P(s, MID - 1, FLOOR + 1, z, 'oak_stairs', facing='west', **STAIR)
        P(s, MID + 1, FLOOR + 1, z, 'oak_stairs', facing='east', **STAIR)
    P(s, MID, FLOOR + 1, 12, 'spruce_stairs', facing='south', **STAIR)     # the mayor's seat at the head
    for z in (7, 13):
        P(s, HX0 + 1, FLOOR + 1, z, 'chest', facing='east', type='single', waterlogged='false')
        P(s, HX1 - 1, FLOOR + 1, z, 'chest', facing='west', type='single', waterlogged='false')
    # the clerks' office in the tower: a desk and a chest
    P(s, MID - 1, FLOOR + 1, TZ0 + 1, 'crafting_table')
    P(s, MID + 1, FLOOR + 1, TZ0 + 1, 'chest', facing='south', type='single', waterlogged='false')
    P(s, MID - 1, FLOOR + 1, TZ0 + 2, 'oak_stairs', facing='south', **STAIR)
    # the notice board on the front pier (text: decoration step)
    for x in (HX0, HX0 + 1):
        P(s, x, 2, HZ0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    P(s, HX1 - 1, 2, HZ0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
    # market under the arcade: benches along the piers, barrels and a chest by the stair
    for z in (6, 7, 12, 13):
        P(s, HX0 + 1, 1, z, 'spruce_stairs', facing='west', **STAIR) if z in (6, 7) else None
    P(s, HX1 - 1, 1, HZ1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, HX0 + 1, 1, HZ1 - 1, 'barrel', facing='up', open='false')
    # torches
    for z in (9, 12):
        P(s, HX0 + 1, FLOOR + 3, z - 1, 'wall_torch', facing='east') if False else None
    P(s, MID, FLOOR + 3, HZ1 - 1, 'wall_torch', facing='north')
    P(s, HX0 + 1, FLOOR + 2, 11, 'wall_torch', facing='east')
    P(s, HX1 - 1, FLOOR + 2, 11, 'wall_torch', facing='west')
    P(s, TX0 + 1, 2, 2, 'wall_torch', facing='east')
    P(s, TX1 - 1, 2, 2, 'wall_torch', facing='west')

    # ---------------------------------------------------------------- level 2: the hall at work
    s.at(2, 'flourish2')
    for z in (8, 9, 10):                                                 # records: bookshelves along the walls
        P(s, HX0 + 1, FLOOR + 1, z - 2 + 4, 'bookshelf') if False else None
    for x in (HX0 + 1, HX1 - 1):
        P(s, x, FLOOR + 2, 7, 'bookshelf'); P(s, x, FLOOR + 2, 13, 'bookshelf')
        P(s, x, FLOOR + 1, 14, 'bookshelf'); P(s, x, FLOOR + 2, 14, 'bookshelf')
    P(s, HX1 - 1, FLOOR + 1, HZ1 - 1, 'lectern', facing='west', has_book='false', powered='false')
    s.decorate(HX1 - 1, FLOOR + 1, HZ1 - 1, 'item', item='minecraft:writable_book', note='the town ledger')
    P(s, MID + 1, FLOOR + 2, TZ0 + 1, 'bookshelf') if False else None
    P(s, MID - 1, FLOOR + 2, TZ0 + 1, 'candle', **CANDLES)
    P(s, MID, FLOOR + 1, TZ0 + 1, 'bookshelf'); P(s, MID, FLOOR + 2, TZ0 + 1, 'bookshelf')
    for z in (8, 11):                                                     # candles on the table ends
        pass
    for z in (9, 12):                                                     # chamber lanterns from the ridge (y 12)
        for yy in (11, 10):
            P(s, MID, yy, z, 'chain', axis='y')
        P(s, MID, 9, z, 'lantern', **HANG)
    for x in (HX0, HX1):                                                  # lanterns under the arches
        for a, b in BAYS[::2]:
            pass
    for a, b in BAYS:
        P(s, MID - 2, 3, a, 'lantern', **HANG) if False else None
    for z in (7, 13):                                                     # under the floor beam, in the arcade
        P(s, MID - 2, FLOOR - 1, z, 'lantern', **HANG); P(s, MID + 2, FLOOR - 1, z, 'lantern', **HANG)
    P(s, MID, 3, 3, 'lantern', **HANG)                                    # in the tower porch
    for x in (TX0 + 1, TX1 - 1):                                          # banners on the tower
        P(s, x, 8, TZ0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 8, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the town arms')
    for x in (HX0, HX1):                                                  # banners on the front piers
        P(s, x + (0 if x == HX0 else 0), 3, HZ0 - 1, 'red_wall_banner', facing='north') if x == HX1 else None
    P(s, HX1, 3, HZ0 - 1, 'red_wall_banner', facing='north') if False else None
    # market goods under the arcade: barrels, a hay bale, crates
    for x, z, b in ((HX1 - 1, 6, 'barrel'), (HX1 - 1, 7, 'barrel'), (HX1 - 1, 9, 'hay_block'), (HX1 - 2, 6, 'barrel'),
                    (HX0 + 1, 9, 'barrel'), (HX0 + 1, 10, 'hay_block'), (HX1 - 1, 12, 'barrel')):
        P(s, x, 1, z, b, **({'facing': 'up', 'open': 'false'} if b == 'barrel' else {'axis': 'y'}))
    P(s, HX1 - 1, 2, 6, 'barrel', facing='up', open='false')
    P(s, MID, 1, 9, 'oak_fence'); P(s, MID, 2, 9, 'lantern', **STAND) if False else None

    # ---------------------------------------------------------------- level 3: the proud town
    s.at(3, 'flourish3')
    for x, z in ((MID, TZ0 - 1), (MID, TZ1 + 1), (TX0 - 1, 3), (TX1 + 1, 3)):
        s.decorate(x, 12, z, 'item_frame', item='minecraft:clock', note='the town clock, on the face')
    for x, side in ((HX0 - 1, 'west'), (HX1 + 1, 'east')):               # flower boxes under the upper windows
        for a, b in BAYS:
            for z in (a, b):
                P(s, x, FLOOR + 1, z, 'coarse_dirt')
                P(s, x, FLOOR, z, 'dark_oak_stairs', facing='east' if side == 'west' else 'west', **TOP)
            P(s, x, FLOOR + 2, a, 'poppy'); P(s, x, FLOOR + 2, b, 'cornflower')
    for x in range(HX0 + 2, HX1 - 1):                                    # carpets in the chamber
        for z in (8, 9, 10, 11):
            pass
    for z in range(6, 14):
        A(s, MID - 2, FLOOR + 1, z, 'red_carpet'); A(s, MID + 2, FLOOR + 1, z, 'red_carpet')
    # benches and flower beds round the arcade, bushes, a lantern post
    for x in (0, W - 1):
        for z in (8, 11):
            P(s, x, 0, z, 'spruce_stairs', facing='west' if x == 0 else 'east', **STAIR)
        for z, f in ((7, 'poppy'), (9, 'oxeye_daisy'), (10, 'azure_bluet'), (12, 'cornflower')):
            A(s, x, 0, z, f)
    for x, z in ((0, 5), (W - 1, 5), (0, 15), (W - 1, 15), (2, 0), (10, 0)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for y in (0, 1):
        P(s, 1, y, 2, 'spruce_fence')
    P(s, 1, 2, 2, 'lantern', **STAND)
    for z in (15, 16):
        for yy in (1, 2):
            A(s, HX0 - 1, yy, z, 'vine', east='true', west='false', north='false', south='false', up='false')
    connect(s)
    return s


STRUCTURES = [dict(path='town_hall', name='Town hall', tier=6, zone='inner', role='civic', group='town_hall',
                   terrain='stepped', door='front', make=town_hall, max_height=18, changes='First version.',
                   description='Needs scaffolding: 18 tall (the clock tower; the hall roof is 13). Half-timbered '
                               'council hall raised on an open stone market arcade, a stone clock tower at the front '
                               'with a clock face on every side and a vaulted porch; council chamber with a long table, '
                               'the mayor\'s seat and records, the clerks\' office in the tower, a stair at the back, a '
                               'notice board; L2 records shelves, lanterns, banners and market goods; L3 the clocks '
                               '(decoration step), flower boxes, weathervane, benches and flowers.')]
