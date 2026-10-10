"""Town hall (tier 6, look A, landmark): a half-timbered council hall raised on an open stone market arcade, with a clock
tower at the front (docs/STRUCTURES.md, "Town hall"). Terrain: stepped in the brief (tower and hall); here everything
sits on one stone base course (layer 0).

Layout (13 wide x 18 deep x 18 tall, front z = 0):
- Clock tower x 4..8, z 1..5 (5 x 5), stone bricks: an open vaulted porch at the bottom (arches to the front, both
  sides and into the arcade), the clerks' office above it (reached from the council chamber), a timber clock stage at
  y 11..15 with a clock face on all four sides (white plaster round a dark hub in a dark oak frame; the hands are
  item frames with clocks, the decoration step), and a spruce pyramid roof with one block of eaves, to 17 (18 tall:
  needs scaffolding).
- Hall x 2..10, z 5..16 (9 x 12): the ground floor is an open market arcade, 4 high (stone brick piers every 3 blocks,
  arches of upside-down stairs, dark oak brackets under the floor beam), closed at the back where a stone stair climbs
  to the upper floor. The upper storey (y 6..8) is white plaster in a dark oak frame with windows in every bay;
  spruce gable roof (dark oak eaves and ridge) with one block of overhang, ridge at 13.
- Upper floor: the council chamber: a long table with eight chairs and the mayor's seat, record chests, a doorway into
  the clerks' office in the tower. The notice board on the front piers, beside the tower porch.
Level 1: shell and fittings (arcade, chamber, table and chairs, records chests, stair, notice board, torches).
Level 2 (the hall at work): the clock hubs get hands (buttons), bookshelves of records and a lectern, market stalls
under the arcade (barrels, hay, a counter), lanterns under the floor and in the chamber, red banners on the tower.
Level 3 (the proud town): the clocks (decoration step), flower boxes on brackets under the upper windows, a
weathervane, benches and flower beds round the arcade, a lantern post, carpets in the chamber, vines.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, HANG, STAND, LEAVES, connect, gable_roof

W, H, D = 13, 18, 18
HX0, HX1, HZ0, HZ1 = 2, 10, 5, 16          # hall
TX0, TX1, TZ0, TZ1 = 4, 8, 1, 5            # tower (its back wall is the hall's front wall)
MID, TMZ = 6, 3                            # middle x; the tower's middle z
ARC = 4                                    # arcade height (y 1..4)
FLOOR = 5                                  # upper floor layer
UTOP = 8                                   # last upper wall layer
STONE_TOP = 10                             # tower stone up to here
CLOCK = (11, 15)                           # timber clock stage (faces y 12..14)
PIERS = (5, 8, 11, 14)
BAYS = ((6, 7), (9, 10), (12, 13))
CANDLES = dict(candles='3', lit='true', waterlogged='false')


def pyramid_ring(s, x0, x1, z0, z1, y, block='spruce_stairs'):
    """A ring of stairs rising towards the middle, the corners turned (outer corner shapes)."""
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


def clock_faces():
    """(x, z, axis, outside x, outside z) of the four clock hubs."""
    return [(MID, TZ0, 'z', MID, TZ0 - 1), (MID, TZ1, 'z', MID, TZ1 + 1),
            (TX0, TMZ, 'x', TX0 - 1, TMZ), (TX1, TMZ, 'x', TX1 + 1, TMZ)]


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
    hubs = {(x, z) for x, z, *_ in clock_faces()}
    for y in range(1, CLOCK[1] + 1):
        for x in range(TX0, TX1 + 1):
            for z in range(TZ0, TZ1 + 1):
                if not (x in (TX0, TX1) or z in (TZ0, TZ1)):
                    continue
                corner = x in (TX0, TX1) and z in (TZ0, TZ1)
                if y <= STONE_TOP:
                    P(s, x, y, z, 'cobblestone' if y == 1 else 'stone_bricks')
                elif corner:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y in CLOCK:
                    P(s, x, y, z, 'dark_oak_log', axis='x' if z in (TZ0, TZ1) else 'z')
                elif (x, z) in hubs and y == 13:
                    P(s, x, y, z, 'dark_oak_log', axis='z' if z in (TZ0, TZ1) else 'x')     # the hub
                elif y == 13 or (x, z) in hubs:
                    P(s, x, y, z, 'white_terracotta')                                     # the round face
                else:
                    P(s, x, y, z, 'dark_oak_planks')                                      # the face's corners
    for y in (1, 2, 3):                              # porch arches: front and back 3 high, sides 2 high
        s.remove(MID, y, TZ0); s.remove(MID, y, TZ1)
    for y in (1, 2):
        s.remove(TX0, y, TMZ); s.remove(TX1, y, TMZ)
    for x in range(TX0 + 1, TX1):                    # floors: the office (y 5), its ceiling (y 9), the deck (y 15)
        for z in range(TZ0 + 1, TZ1):
            for y in (FLOOR, 9, CLOCK[1]):
                P(s, x, y, z, 'spruce_planks')
    for x, z in ((TX0, TMZ), (TX1, TMZ), (MID, TZ0)):                 # office windows
        s.remove(x, 7, z)
    s.remove(MID, FLOOR + 1, TZ1); s.remove(MID, FLOOR + 2, TZ1)      # doorway from the council chamber
    P(s, MID, 4, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)   # a hood over the porch arch
    P(s, MID, 0, TZ0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
    for x in (TX0, TX1):                             # buttresses at the tower's front corners
        P(s, x, 0, TZ0 - 1, 'cobblestone')
        for y in (1, 2, 3):
            P(s, x, y, TZ0 - 1, 'stone_bricks')
        P(s, x, 4, TZ0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    for z in (TZ0, TZ1):                             # a string course where the stone meets the clock stage
        for x in range(TX0 - 1, TX1 + 2):
            if z == TZ0:
                P(s, x, STONE_TOP, z - 1, 'stone_brick_stairs', facing='south', **TOP)
    for z in range(TZ0, TZ1 + 1):
        for x, f in ((TX0 - 1, 'east'), (TX1 + 1, 'west')):
            A(s, x, STONE_TOP, z, 'stone_brick_stairs', facing=f, **TOP)
    # the pyramid roof: eaves one out, then one in per layer
    y = CLOCK[1]
    pyramid_ring(s, TX0 - 1, TX1 + 1, TZ0 - 1, TZ1 + 1, y)
    pyramid_ring(s, TX0, TX1, TZ0, TZ1, y + 1)
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            P(s, x, y + 1, z, 'spruce_planks')
    pyramid_ring(s, TX0 + 1, TX1 - 1, TZ0 + 1, TZ1 - 1, y + 2)
    P(s, MID, y + 2, TMZ, 'dark_oak_planks')

    # ---------------------------------------------------------------- hall: arcade
    for z in range(HZ0, HZ1 + 1):
        for x in (HX0, HX1):
            if z in PIERS or z >= HZ1 - 1:
                for yy in range(1, ARC + 1):
                    P(s, x, yy, z, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in (HX0 + 1, HX1 - 1):                     # front piers beside the tower (2 wide)
        for yy in range(1, ARC + 1):
            P(s, x, yy, HZ0, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in range(HX0 + 1, HX1):                    # back wall of the stair hall, two windows
        for yy in range(1, ARC + 1):
            A(s, x, yy, HZ1, 'cobblestone' if yy == 1 else 'stone_bricks')
    for a, b in BAYS:                                # arches between the piers
        for x in (HX0, HX1):
            P(s, x, ARC, a, 'stone_brick_stairs', facing='north', **TOP)
            P(s, x, ARC, b, 'stone_brick_stairs', facing='south', **TOP)
    # the stair to the upper floor along the back wall (rising to the east), solid underneath
    for i, x in enumerate((4, 5, 6, 7)):
        for yy in range(1, 1 + i):
            P(s, x, yy, HZ1 - 1, 'stone_bricks')
        P(s, x, 1 + i, HZ1 - 1, 'stone_brick_stairs', facing='east', **STAIR)
    for yy in range(1, ARC + 1):
        P(s, 8, yy, HZ1 - 1, 'stone_bricks')         # the landing pier
    # ---------------------------------------------------------------- hall: upper storey
    stairwell = {(x, HZ1 - 1) for x in (4, 5, 6, 7)}
    for x in range(HX0, HX1 + 1):                    # floor beams round the edge, planks inside
        for z in range(HZ0, HZ1 + 1):
            if TX0 <= x <= TX1 and z == HZ0:
                continue
            if x in (HX0, HX1):
                A(s, x, FLOOR, z, 'dark_oak_log', axis='z')
            elif z in (HZ0, HZ1):
                A(s, x, FLOOR, z, 'dark_oak_log', axis='x')
            elif (x, z) not in stairwell:
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
    wy = FLOOR + 2
    windows = [(x, wy, z) for x in (HX0, HX1) for a, b in BAYS for z in (a, b)] + \
              [(4, wy, HZ1), (8, wy, HZ1), (HX0 + 1, wy, HZ0), (HX1 - 1, wy, HZ0)]
    for w in windows:
        s.remove(*w)
    for x in (4, 8):
        s.remove(x, 2, HZ1)                          # stair hall windows
    gable_roof(s, HX0, HX1, HZ0, HZ1, UTOP, front=1, back=1, gable='white_terracotta', plates='dark_oak_log')
    for x in (HX0, HX1):                             # brackets under the floor beam at the piers
        for z in PIERS:
            P(s, x + (-1 if x == HX0 else 1), ARC, z, 'dark_oak_stairs', facing='east' if x == HX0 else 'west', **TOP)

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    for x in (4, 8):
        P(s, x, 2, HZ1, 'glass_pane')
    for x, z in ((TX0, TMZ), (TX1, TMZ), (MID, TZ0)):
        P(s, x, 7, z, 'glass_pane')
    # council chamber: long table, eight chairs, the mayor's seat, record chests
    for z in (8, 9, 10, 11):
        P(s, MID, FLOOR + 1, z, 'oak_fence'); P(s, MID, FLOOR + 2, z, 'oak_pressure_plate', powered='false')
        P(s, MID - 1, FLOOR + 1, z, 'oak_stairs', facing='west', **STAIR)
        P(s, MID + 1, FLOOR + 1, z, 'oak_stairs', facing='east', **STAIR)
    P(s, MID, FLOOR + 1, 12, 'spruce_stairs', facing='south', **STAIR)     # the mayor's seat at the head
    for z in (7, 13):
        P(s, HX0 + 1, FLOOR + 1, z, 'chest', facing='east', type='single', waterlogged='false')
        P(s, HX1 - 1, FLOOR + 1, z, 'chest', facing='west', type='single', waterlogged='false')
    # the clerks' office in the tower: a desk, a chair and a chest
    P(s, MID - 1, FLOOR + 1, TZ0 + 1, 'crafting_table')
    P(s, MID - 1, FLOOR + 1, TZ0 + 2, 'oak_stairs', facing='south', **STAIR)
    P(s, MID + 1, FLOOR + 1, TZ0 + 1, 'chest', facing='south', type='single', waterlogged='false')
    # the notice board on the front piers (its notices: decoration step)
    for x in (HX0, HX0 + 1, HX1 - 1, HX1):
        P(s, x, 2, HZ0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
        s.decorate(x, 2, HZ0 - 1, 'sign_text', note='town notices')
    # the arcade: benches against the back of the front piers, a chest and a barrel by the stair
    for x in (HX0 + 1, HX1 - 1):
        P(s, x, 1, HZ0 + 1, 'spruce_stairs', facing='north', **STAIR)
    P(s, HX1 - 1, 1, HZ1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, HX0 + 1, 1, HZ1 - 1, 'barrel', facing='up', open='false')
    # torches
    P(s, MID, FLOOR + 3, HZ1 - 1, 'wall_torch', facing='north')
    P(s, HX0 + 1, FLOOR + 2, 11, 'wall_torch', facing='east')
    P(s, HX1 - 1, FLOOR + 2, 11, 'wall_torch', facing='west')
    P(s, TX0 + 1, 2, TZ0 + 1, 'wall_torch', facing='east')
    P(s, TX1 - 1, 2, TZ0 + 1, 'wall_torch', facing='west')

    # ---------------------------------------------------------------- level 2: the hall at work
    s.at(2, 'flourish2')
    for x, z, axis, ox, oz in clock_faces():                              # hands on every clock hub
        f = {(0, -1): 'north', (0, 1): 'south', (-1, 0): 'west', (1, 0): 'east'}[(ox - x, oz - z)]
        P(s, ox, 13, oz, 'dark_oak_button', face='wall', facing=f, powered='false')
    for x in (HX0 + 1, HX1 - 1):                                          # records: bookshelves along the walls
        P(s, x, FLOOR + 2, 7, 'bookshelf'); P(s, x, FLOOR + 2, 13, 'bookshelf')
        P(s, x, FLOOR + 1, 14, 'bookshelf'); P(s, x, FLOOR + 2, 14, 'bookshelf')
    P(s, HX1 - 1, FLOOR + 1, HZ1 - 1, 'lectern', facing='west', has_book='false', powered='false')
    s.decorate(HX1 - 1, FLOOR + 1, HZ1 - 1, 'item', item='minecraft:writable_book', note='the town ledger')
    P(s, MID, FLOOR + 1, TZ0 + 1, 'bookshelf'); P(s, MID, FLOOR + 2, TZ0 + 1, 'bookshelf')
    P(s, MID - 1, FLOOR + 2, TZ0 + 1, 'candle', **CANDLES)              # on the clerks' desk
    for z in (9, 12):                                                     # chamber lanterns from the ridge (y 13)
        for yy in (12, 11):
            P(s, MID, yy, z, 'chain', axis='y')
        P(s, MID, 10, z, 'lantern', **HANG)
    for z in (7, 10, 13):                                                 # lanterns under the floor, in the arcade
        P(s, MID - 2, ARC, z, 'lantern', **HANG); P(s, MID + 2, ARC, z, 'lantern', **HANG)
    P(s, MID, ARC, TMZ, 'lantern', **HANG)                                # in the tower porch
    for x in (TX0 + 1, TX1 - 1):                                          # banners on the tower
        P(s, x, 8, TZ0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 8, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the town arms')
    # market stalls under the arcade: a counter and goods along the middle
    for z in (8, 9):
        P(s, MID - 2, 1, z, 'barrel', facing='up', open='false')
        P(s, MID + 2, 1, z, 'hay_block' if z == 8 else 'barrel', **({'axis': 'y'} if z == 8 else {'facing': 'up', 'open': 'false'}))
    for z in (11, 12):
        P(s, MID - 2, 1, z, 'spruce_slab', type='top', waterlogged='false')     # a counter
    P(s, MID + 2, 1, 11, 'chest', facing='west', type='single', waterlogged='false')
    P(s, MID - 2, 2, 9, 'barrel', facing='up', open='false')

    # ---------------------------------------------------------------- level 3: the proud town
    s.at(3, 'flourish3')
    for x, z, axis, ox, oz in clock_faces():
        s.decorate(ox, 13, oz, 'item_frame', item='minecraft:clock', note='the town clock, on the hub')
    for x, side in ((HX0 - 1, 'west'), (HX1 + 1, 'east')):               # flower boxes under the upper windows
        for a, b in BAYS:
            for z in (a, b):
                P(s, x, FLOOR + 1, z, 'coarse_dirt')
                P(s, x, FLOOR, z, 'dark_oak_stairs', facing='east' if side == 'west' else 'west', **TOP)
            P(s, x, FLOOR + 2, a, 'poppy'); P(s, x, FLOOR + 2, b, 'cornflower')
    for z in range(7, 14):                                                # carpet runners in the chamber
        A(s, MID - 2, FLOOR + 1, z, 'red_carpet'); A(s, MID + 2, FLOOR + 1, z, 'red_carpet')
    for z in range(TZ0 + 1, HZ0 + 2):                                     # and through the porch
        A(s, MID, 1, z, 'red_carpet')
    # benches and flower beds round the arcade, bushes, a lantern post
    for x in (0, W - 1):
        for z in (8, 11):
            P(s, x, 0, z, 'spruce_stairs', facing='west' if x == 0 else 'east', **STAIR)
        for z, f in ((7, 'poppy'), (9, 'oxeye_daisy'), (10, 'azure_bluet'), (12, 'cornflower')):
            A(s, x, 0, z, f)
    for x, z in ((0, 5), (W - 1, 5), (0, 15), (W - 1, 15), (2, 1), (10, 1)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for y in (0, 1):
        P(s, 1, y, 3, 'spruce_fence')
    P(s, 1, 2, 3, 'lantern', **STAND)
    for z in (15, 16):
        for yy in (1, 2):
            A(s, HX0 - 1, yy, z, 'vine', east='true', west='false', north='false', south='false', up='false')
    for x in (MID - 1, MID + 1):                                          # flowers by the porch step
        A(s, x, 0, 0, 'poppy' if x < MID else 'oxeye_daisy')
    connect(s)
    return s


STRUCTURES = [dict(path='town_hall', name='Town hall', tier=6, zone='inner', role='civic', group='town_hall',
                   terrain='stepped', door='front', make=town_hall, max_height=18, changes='First version.',
                   description='Needs scaffolding: 18 tall (the clock tower; the hall roof is 15). Half-timbered '
                               'council hall raised on an open stone market arcade, a stone clock tower at the front '
                               'with a clock face on every side and a vaulted porch; council chamber with a long table, '
                               'the mayor\'s seat and records, the clerks\' office in the tower, a stair at the back, a '
                               'notice board; L2 clock hands, records shelves, lanterns, banners and market stalls; L3 '
                               'the clocks (decoration step), flower boxes, benches, flowers and a lantern post.')]
