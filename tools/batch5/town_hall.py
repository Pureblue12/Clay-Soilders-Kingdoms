"""Town hall (tier 6, look A, landmark): a half-timbered council hall raised on an open stone market arcade, with a clock
tower at the front (docs/STRUCTURES.md, "Town hall"). Terrain: stepped in the brief (tower and hall); here everything
sits on one stone base course (layer 0).

Layout (13 wide x 18 deep x 18 tall, front z = 0):
- Clock tower x 4..8, z 1..5 (5 x 5), stone bricks: an open vaulted porch at the bottom (arches to the front, both
  sides and into the arcade), the clerks' office above it (reached from the council chamber), a timber clock stage at
  y 11..15 with a clock face on three sides (white plaster round a dark hub in a dark oak frame; the hands are
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
Level 3 (the proud town): the clocks (decoration step), flower boxes on brackets under the upper windows, benches
and flower beds round the arcade, a lantern post, carpet runners in the chamber and the porch, vines.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, HANG, STAND, LEAVES, OPEN, SLAB_TOP, connect, gable_roof

W, H, D = 13, 18, 18
HX0, HX1, HZ0, HZ1 = 2, 10, 5, 16          # hall
TX0, TX1, TZ0, TZ1 = 4, 8, 1, 5            # tower (its back wall is the hall's front wall)
MID, TMZ = 6, 3                            # middle x; the tower's middle z
ARC = 4                                    # arcade height (y 1..4)
FLOOR = 5                                  # upper floor layer
UTOP = 8                                   # last upper wall layer
STONE_TOP = 10                             # tower stone up to here
CLOCK = (11, 15)                           # timber clock stage (faces y 12..14)
PIERS = (5, 9, 13)
BAYS = ((6, 7, 8), (10, 11, 12))             # arcade bays, 3 wide
WIN_Z = (6, 8, 10, 12, 15)                 # upper windows on the long sides
DORMER_Z = 11
CHIMNEY_TOP = 15                           # the back chimney (through the gable overhang)
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
    """(x, z, axis, outside x, outside z) of the three clock hubs (front and sides; the hall roof hides the back)."""
    return [(MID, TZ0, 'z', MID, TZ0 - 1), (TX0, TMZ, 'x', TX0 - 1, TMZ), (TX1, TMZ, 'x', TX1 + 1, TMZ)]


def face_of(x, z):
    """The clock face a tower-wall column belongs to (its hub), or None for a corner post."""
    if x in (TX0, TX1) and z in (TZ0, TZ1):
        return None
    if z == TZ0:
        return (MID, TZ0)
    if x in (TX0, TX1):
        return (x, TMZ)
    return None


def dormer(s, x, out):
    """A small gabled dormer standing on the eave wall at x (out: -1 left, +1 right), 3 wide around DORMER_Z, its
    window at y 9 and its ridge running into the main roof."""
    z0, z1, zm = DORMER_Z - 1, DORMER_Z + 1, DORMER_Z
    y = UTOP + 1                                     # 9: the plate layer
    P(s, x, y, z0, 'dark_oak_log', axis='y'); P(s, x, y, z1, 'dark_oak_log', axis='y')
    P(s, x, y + 1, zm, 'white_terracotta')
    for i in range(3):                               # roof: overhang out, the wall line, one in
        xx = x + out - out * i
        P(s, xx, y + 1, z0, 'spruce_stairs', facing='south', **STAIR)
        P(s, xx, y + 1, z1, 'spruce_stairs', facing='north', **STAIR)
        P(s, xx, y + 2, zm, 'dark_oak_planks')
    P(s, x + out, y, z0, 'spruce_stairs', facing='north', **TOP)        # corbels under the dormer's gable
    P(s, x + out, y, z1, 'spruce_stairs', facing='south', **TOP)


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
                corner = x in (TX0, TX1) and z in (TZ0, TZ1)
                if y <= STONE_TOP:
                    P(s, x, y, z, 'cobblestone' if y == 1 else 'stone_bricks')
                elif corner:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y in CLOCK:
                    P(s, x, y, z, 'dark_oak_log', axis='x' if z in (TZ0, TZ1) else 'z')
                elif face_of(x, z) is not None and z != TZ1:
                    P(s, x, y, z, 'white_wool')                    # the dial: bright white in the dark frame
                else:
                    P(s, x, y, z, 'white_terracotta')                                     # the back (under the roof)
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
    # the stair from the office up to the clock chamber: along the east wall (rising north), then one step west
    # through the office ceiling (y 9); solid underneath, open above every step
    for x, y, z, f in ((TX1 - 1, 6, TZ1 - 1, 'north'), (TX1 - 1, 7, TMZ, 'north'), (TX1 - 1, 8, TZ0 + 1, 'north'),
                       (MID, 9, TZ0 + 1, 'west')):
        P(s, x, y, z, 'spruce_stairs', facing=f, **STAIR)
        for yy in range(FLOOR + 1, y if y < 9 else FLOOR + 1):
            P(s, x, yy, z, 'spruce_planks')
    s.remove(TX1 - 1, 9, TMZ); s.remove(TX1 - 1, 9, TZ0 + 1)          # the stairwell through the office ceiling
    P(s, MID, 4, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)   # a hood over the porch arch
    P(s, MID, 0, TZ0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    for x in (TX0, TX1):                             # buttresses at the tower's front corners
        P(s, x, 0, TZ0 - 1, 'cobblestone')
        for y in (1, 2, 3):
            P(s, x, y, TZ0 - 1, 'stone_bricks')
        P(s, x, 4, TZ0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    for x in range(TX0 - 1, TX1 + 2):                # a string course where the stone meets the clock stage
        P(s, x, STONE_TOP, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)
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
    P(s, MID, y + 2, TMZ, 'spruce_planks')

    # ---------------------------------------------------------------- hall: arcade
    for z in range(HZ0, HZ1 + 1):
        for x in (HX0, HX1):
            if z in PIERS or z >= HZ1 - 2:
                for yy in range(1, ARC + 1):
                    P(s, x, yy, z, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in (HX0 + 1, HX1 - 1):                     # front piers beside the tower (2 wide)
        for yy in range(1, ARC + 1):
            P(s, x, yy, HZ0, 'cobblestone' if yy == 1 else 'stone_bricks')
    for x in range(HX0 + 1, HX1):                    # back wall of the stair hall, two windows
        for yy in range(1, ARC + 1):
            A(s, x, yy, HZ1, 'cobblestone' if yy == 1 else 'stone_bricks')
    for a, b, c in BAYS:                             # arches between the piers, a keystone in the middle
        for x in (HX0, HX1):
            P(s, x, ARC, a, 'stone_brick_stairs', facing='north', **TOP)
            P(s, x, ARC, b, 'stone_bricks')
            P(s, x, ARC, c, 'stone_brick_stairs', facing='south', **TOP)
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
    posts_z = set(PIERS) | {HZ1, 7, 11}
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
    windows = [(x, wy, z) for x in (HX0, HX1) for z in WIN_Z] + \
              [(4, wy, HZ1), (8, wy, HZ1), (HX0 + 1, wy, HZ0), (HX1 - 1, wy, HZ0)]
    for w in windows:
        s.remove(*w)
    for x in (4, 8):
        s.remove(x, 2, HZ1)                          # stair hall windows
    for x, out in ((HX0, -1), (HX1, 1)):             # a dormer over the middle bay on each side
        dormer(s, x, out)
    dormer_windows = {(HX0, UTOP + 1, DORMER_Z), (HX1, UTOP + 1, DORMER_Z)}
    gable_roof(s, HX0, HX1, HZ0, HZ1, UTOP, front=1, back=1, gable='white_terracotta', plates='dark_oak_log',
               skip=dormer_windows | {(HX0 - 1, UTOP + 1, DORMER_Z), (HX1 + 1, UTOP + 1, DORMER_Z)})
    for x in (HX0, HX1):                             # brackets under the floor beam at the piers
        for z in PIERS:
            P(s, x + (-1 if x == HX0 else 1), ARC, z, 'dark_oak_stairs', facing='east' if x == HX0 else 'west', **TOP)

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for x, y, z in windows:
        P(s, x, y, z, 'glass_pane')
    for x in (HX0, HX1):
        P(s, x, UTOP + 1, DORMER_Z, 'glass_pane')
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
    P(s, MID - 1, FLOOR + 1, TZ1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    # the clock chamber (y 10): the clockwork (a grindstone)
    P(s, MID - 1, 10, TZ1 - 1, 'grindstone', face='floor', facing='east')
    # the notice board on the front piers (its notices: decoration step)
    for x in (HX0, HX0 + 1, HX1 - 1, HX1):
        P(s, x, 2, HZ0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')
        s.decorate(x, 2, HZ0 - 1, 'sign_text', note='town notices')
    # the arcade: benches against the back of the front piers, a chest and a barrel by the stair
    for x in (HX0 + 1, HX1 - 1):
        P(s, x, 1, HZ0 + 1, 'spruce_stairs', facing='north', **STAIR)
    P(s, HX1 - 1, 1, HZ1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, HX0 + 1, 1, HZ1 - 1, 'barrel', facing='up', open='false')
    # outside: shutters beside the windows, slab sills under them
    for x, side in ((HX0 - 1, 'west'), (HX1 + 1, 'east')):
        for z in (5, 7, 9, 11, 13, 14, 16):                             # side windows (shared between neighbours)
            P(s, x, wy, z, 'spruce_trapdoor', facing=side, **OPEN)
    for x in (4, 8):                                                     # back: upper windows
        P(s, x - 1, wy, HZ1 + 1, 'spruce_trapdoor', facing='south', **OPEN)
        P(s, x + 1, wy, HZ1 + 1, 'spruce_trapdoor', facing='south', **OPEN)
        P(s, x, wy - 1, HZ1 + 1, 'spruce_slab', **SLAB_TOP)
        P(s, x, 1, HZ1 + 1, 'stone_brick_stairs', facing='north', **TOP)    # sill of the stair hall window
    for x in (HX0 + 1, HX1 - 1):                                         # front windows of the hall
        P(s, x, wy - 1, HZ0 - 1, 'spruce_slab', **SLAB_TOP)
    P(s, MID, wy - 1, TZ0 - 1, 'spruce_slab', **SLAB_TOP)                 # the office windows in the tower
    for x, side, f in ((TX0 - 1, 'west', -1), (TX1 + 1, 'east', 1)):
        P(s, x, wy - 1, TMZ, 'spruce_slab', **SLAB_TOP)
        P(s, x, wy, TMZ - 1, 'spruce_trapdoor', facing=side, **OPEN)
        P(s, x, wy, TMZ + 1, 'spruce_trapdoor', facing=side, **OPEN)
    # the back: buttresses at the corners and a brick chimney stack up the middle of the gable
    for x in (HX0, HX1):
        P(s, x, 0, HZ1 + 1, 'cobblestone')
        for yy in (1, 2):
            P(s, x, yy, HZ1 + 1, 'stone_bricks')
        P(s, x, 3, HZ1 + 1, 'stone_brick_stairs', facing='north', **STAIR)
    for x in (MID - 1, MID, MID + 1):
        P(s, x, 0, HZ1 + 1, 'cobblestone')
        P(s, x, 1, HZ1 + 1, 'stone_bricks')
    P(s, MID - 1, 2, HZ1 + 1, 'stone_brick_stairs', facing='east', **STAIR)
    P(s, MID + 1, 2, HZ1 + 1, 'stone_brick_stairs', facing='west', **STAIR)
    for yy in range(2, CHIMNEY_TOP + 1):
        P(s, MID, yy, HZ1 + 1, 'bricks')
    # torches
    P(s, MID, FLOOR + 3, HZ1 - 1, 'wall_torch', facing='north')
    P(s, HX0 + 1, FLOOR + 2, 11, 'wall_torch', facing='east')
    P(s, HX1 - 1, FLOOR + 2, 11, 'wall_torch', facing='west')
    P(s, TX0 + 1, 2, TZ0 + 1, 'wall_torch', facing='east')
    P(s, TX1 - 1, 2, TZ0 + 1, 'wall_torch', facing='west')

    # ---------------------------------------------------------------- level 2: the hall at work
    s.at(2, 'flourish2')
    for x, z, axis, ox, oz in clock_faces():                              # black hands on every white dial
        f = {(0, -1): 'north', (0, 1): 'south', (-1, 0): 'west', (1, 0): 'east'}[(ox - x, oz - z)]
        rx, rz = {'north': (-1, 0), 'south': (1, 0), 'west': (0, 1), 'east': (0, -1)}[f]   # the viewer's right
        P(s, ox, 13, oz, 'chain', axis='z' if z == TZ0 else 'x')         # the hub (a chain end-on: a dark dot)
        P(s, ox, 14, oz, 'chain', axis='y')                               # minute hand at 12
        P(s, ox + rx, 13, oz + rz, 'chain', axis='x' if rx else 'z')     # hour hand at 3 (a thin dark bar)
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
        P(s, MID, ARC, z, 'lantern', **HANG)
    for x in (HX0 - 1, HX1 + 1):                                          # and under the brackets outside
        for z in PIERS:
            P(s, x, ARC - 1, z, 'lantern', **HANG)
    P(s, MID, ARC, TMZ, 'lantern', **HANG)                                # in the tower porch
    for x in (TX0 + 1, TX1 - 1):                                          # banners on the tower
        P(s, x, 8, TZ0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 8, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the town arms')
    P(s, MID, 10, TZ1 - 1, 'bell', attachment='floor', facing='east', powered='false')   # the clock bell
    # two market stalls in the middle bays, facing the aisle: a counter with goods, the seller behind it between two
    # posts, a striped awning over both
    for cx, sx, f, stripe, goods in ((MID - 2, MID - 3, 'west', 'red_wool',
                                      (('hay_block', dict(axis='y')), ('pumpkin', {}), ('barrel', dict(facing='up', open='false')))),
                                     (MID + 2, MID + 3, 'east', 'blue_wool',
                                      (('red_wool', {}), ('lantern', STAND), ('white_wool', {})))):
        for i, z in enumerate((10, 11, 12)):
            P(s, cx, 1, z, 'spruce_stairs', facing=f, **TOP)               # the counter
            P(s, cx, 2, z, goods[i][0], **goods[i][1])
            for x in (sx, cx, 2 * cx - sx):                                # the awning, striped across
                P(s, x, 3, z, stripe if z != 11 else 'white_wool')
        for z in (10, 12):
            for yy in (1, 2):
                P(s, sx, yy, z, 'spruce_fence')
    P(s, MID - 3, 1, 9, 'barrel', facing='up', open='false')              # stock behind the stalls
    P(s, MID + 3, 1, 9, 'barrel', facing='up', open='false')
    P(s, MID + 3, 2, 9, 'barrel', facing='up', open='false')
    P(s, MID - 3, 1, 8, 'hay_block', axis='y')

    # ---------------------------------------------------------------- level 3: the proud town
    s.at(3, 'flourish3')
    s.decorate(MID, FLOOR + 2, HZ1 - 1, 'item_frame', item='minecraft:clock',
               note='the town clock, on the back wall of the chamber above the stairwell (facing north)')
    for x, side in ((HX0 - 1, 'west'), (HX1 + 1, 'east')):               # a long flower box under the upper windows
        out = -1 if side == 'west' else 1
        for z in range(WIN_Z[0], WIN_Z[3] + 1):                           # soil on brackets, a trapdoor front
            P(s, x, FLOOR + 1, z, 'coarse_dirt')
            P(s, x + out, FLOOR + 1, z, 'spruce_trapdoor', facing=side, **OPEN)
        for i, z in enumerate(WIN_Z[:4]):
            P(s, x, FLOOR, z, 'dark_oak_stairs', facing='east' if side == 'west' else 'west', **TOP)
            P(s, x, FLOOR + 2, z, ('poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet')[i])
        P(s, x, FLOOR + 1, WIN_Z[0] - 1, 'spruce_trapdoor', facing='north', **OPEN)    # the box's end boards
        P(s, x, FLOOR + 1, WIN_Z[3] + 1, 'spruce_trapdoor', facing='south', **OPEN)
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
                   description='Needs scaffolding: 18 tall (the clock tower; the hall roof is 15). Over budget: 1159 blocks vs '
                               '740 (first build 1051 vs 620). Half-timbered '
                               'council hall raised on an open stone market arcade, a stone clock tower at the front '
                               'with a clock face on every side and a vaulted porch; council chamber with a long table, '
                               'the mayor\'s seat and records, the clerks\' office in the tower, a stair at the back, a '
                               'notice board; L2 clock hands, records shelves, lanterns, banners and market stalls; L3 '
                               'the clocks (decoration step), flower boxes, benches, flowers and a lantern post.')]
