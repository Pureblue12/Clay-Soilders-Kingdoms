"""Keep (landmark), revised from Carlton's notes: he approved the Studio sample keep (studio/samples/keep.ts, 11 x 12 x
11) and wants it "bigger and grander, split into levels"; the batch-1 keep_l1..l3 (rejected) are scrapped.

This keep keeps the sample's look (cobblestone apron, battered cobblestone plinth, stone brick walls, battlements on
corbels, corner turrets, banners over the door) at 15 x 15 (17 x 17 with the apron) and 16 tall, with four corner
towers, a gatehouse front and a great hall. Upper floors are reached by stairs, never ladders.

Coordinates: x across, z = 0 the front (north), y = 0 the floor layer resting on the ground.
  body x 3..13, z 3..13 (walls y 1..10, roof deck y 10, battlements on corbels one block out)
  towers 3 x 3 at the corners (x 1..3 / 13..15, z 1..3 / 13..15), walls y 1..12, a 5 x 5 top on corbels at y 13..14
  gatehouse x 6..10, z 1..3: an arched passage (x 7..9) to the door at (8, 1, 3), a room over it, a roof at y 11
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

W = 17                       # box: 17 x 16 x 17
H = 16
B0, B1 = 3, 13               # body
MID = 8
TOWERS = [(1, 1), (13, 1), (1, 13), (13, 13)]   # tower corner (low x, low z)
GATE = (6, 10, 1, 3)         # gatehouse x0, x1, z0, z1
HALL_TOP = 5                 # hall y 1..5
UPPER = 6                    # upper floor (spruce) y 6, room y 7..9
DECK = 10                    # roof deck y 10
TOWER_TOP = 13               # tower floor and corbels y 13, parapet y 14, merlons y 15

TOP = dict(half='top', shape='straight', waterlogged='false')
BOTTOM = dict(half='bottom', shape='straight', waterlogged='false')
OUT = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
FLIP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def in_tower(x, z):
    return any(tx <= x <= tx + 2 and tz <= z <= tz + 2 for tx, tz in TOWERS)


def in_gate(x, z):
    return GATE[0] <= x <= GATE[1] and GATE[2] <= z <= GATE[3]


def in_body(x, z):
    return B0 <= x <= B1 and B0 <= z <= B1


def footprint(x, z):
    return in_body(x, z) or in_tower(x, z) or in_gate(x, z)


def passage(x, z):
    return 7 <= x <= 9 and 1 <= z <= 2


def plinth_mix(x, y, z):
    return 'mossy_cobblestone' if noise(x, y, z, 5) < 0.3 else 'cobblestone'


def brick_mix(x, y, z):
    """Level 2 and 3 masonry (battlement tops): stone bricks with mossy and cracked mixed in, about 1 in 4 (STYLE.md)."""
    n = noise(x, y, z, 7)
    return 'mossy_stone_bricks' if n < 0.13 else 'cracked_stone_bricks' if n < 0.26 else 'stone_bricks'


def overhang():
    """The body's battlement line one block outside the walls, between the towers and the gatehouse: (x, z, facing of
    the corbel, i.e. towards the wall)."""
    line = []
    for z in range(B0 + 1, B1):
        line += [(B0 - 1, z, 'east'), (B1 + 1, z, 'west')]
    for x in range(B0 + 1, B1):
        line.append((x, B1 + 1, 'north'))
        if x < GATE[0] or x > GATE[1]:
            line.append((x, B0 - 1, 'south'))
    return line


def gate_overhang():
    """The gatehouse battlement ring, one block out on the front and sides (x 5..11, z 0..3)."""
    line = [(x, 0, 'south') for x in range(GATE[0] - 1, GATE[1] + 2)]
    for z in range(1, GATE[3] + 1):
        line += [(GATE[0] - 1, z, 'east'), (GATE[1] + 1, z, 'west')]
    return line


def tower_ring(tx, tz):
    """The 5 x 5 ring round a tower's top (one block out on every side), with the corbel facing towards the tower."""
    ring = []
    for x in range(tx - 1, tx + 4):
        for z in range(tz - 1, tz + 4):
            if x in (tx - 1, tx + 3) or z in (tz - 1, tz + 3):
                f = 'south' if z == tz - 1 else 'north' if z == tz + 3 else 'east' if x == tx - 1 else 'west'
                ring.append((x, z, f))
    return ring


def tower_roof(s, tx, tz):
    """A low dark oak pyramid roof inside a tower's parapet: a ring of dark oak stairs (rising to the middle, outer
    corner pieces at the corners), a dark oak plank peak and a spruce fence finial. It closes the tower top."""
    y = TOWER_TOP + 1
    for x in range(tx, tx + 3):
        for z in range(tz, tz + 3):
            if (x, z) == (tx + 1, tz + 1):
                s.put(x, y, z, 'dark_oak_planks')
                continue
            f = 'south' if z == tz else 'north' if z == tz + 2 else 'east' if x == tx else 'west'
            shape = 'straight'
            if x != tx + 1 and z != tz + 1:                          # corners
                west = x == tx
                shape = ('outer_left' if west else 'outer_right') if f == 'south' else ('outer_right' if west else 'outer_left')
            s.put(x, y, z, 'dark_oak_stairs', facing=f, half='bottom', shape=shape, waterlogged='false')
    s.put(tx + 1, y + 1, tz + 1, 'spruce_fence')


def merlon(x, z):
    return (x + z) % 2 == 0


def keep():
    s = Structure('keep', (W, H, W))
    sb = 'stone_bricks'

    # ------------------------------------------------------------ level 1: the shell (KNOWN-OK blocks only)
    s.at(1, 'shell')
    for x in range(W):                                               # floor layer and apron
        for z in range(W):
            s.put(x, 0, z, sb if footprint(x, z) else plinth_mix(x, 0, z))

    def quoin(x, z):
        """An outer corner of a tower or of the gatehouse front."""
        for tx, tz in TOWERS:
            if x in (tx, tx + 2) and z in (tz, tz + 2) and not in_body(x, z):
                return True
        return z == GATE[2] and x in (GATE[0], GATE[1])

    def wall(x, y, z):
        if y == 1:
            s.put(x, y, z, plinth_mix(x, y, z))
        elif quoin(x, z) and y % 2 == 0:
            s.put(x, y, z, 'cobblestone')                            # rough quoins on the corners, every other course
        else:
            s.put(x, y, z, sb)

    for y in range(1, DECK + 1):                                     # body walls up to the deck
        for x in range(B0, B1 + 1):
            for z in range(B0, B1 + 1):
                if x in (B0, B1) or z in (B0, B1):
                    wall(x, y, z)
    for tx, tz in TOWERS:                                            # towers: 3 x 3 rings, hollow
        for y in range(1, TOWER_TOP):
            for x in range(tx, tx + 3):
                for z in range(tz, tz + 3):
                    if (x, z) != (tx + 1, tz + 1):
                        wall(x, y, z)
    g0, g1, gz0, gz1 = GATE
    for y in range(1, DECK + 1):                                     # gatehouse walls
        for x in range(g0, g1 + 1):
            for z in range(gz0, gz1 + 1):
                if x in (g0, g1) or z == gz0:
                    wall(x, y, z)
    # arched passage through the gatehouse: 3 wide, 4 high in the middle, arch stairs at the top corners
    for z in (1, 2):
        for x in (7, 8, 9):
            for y in range(1, 5):
                s.remove(x, y, z)
        s.put(7, 4, z, 'stone_brick_stairs', facing='west', **TOP)
        s.put(9, 4, z, 'stone_brick_stairs', facing='east', **TOP)
    for x in (7, 8, 9):
        s.put(x, 5, 2, sb)                                           # passage ceiling
        s.put(x, UPPER, 2, 'spruce_planks')                         # gatehouse room floor
    s.remove(MID, 1, B0); s.remove(MID, 2, B0)
    s.door(MID, 1, B0, 'north', block='spruce_door')
    for y in (1, 2):                                                 # a dark oak door frame: posts and a lintel
        for x in (MID - 1, MID + 1):
            s.put(x, y, B0, 'dark_oak_log', axis='y')
    for x in (MID - 1, MID, MID + 1):
        s.put(x, 3, B0, 'dark_oak_log', axis='x')
    s.remove(MID, UPPER + 1, B0); s.remove(MID, UPPER + 2, B0)      # king's room -> room over the gate
    for x in range(g0, g1 + 1):                                      # gatehouse roof
        for z in range(gz0, gz1 + 1):
            s.put(x, DECK + 1, z, sb)

    # floors: hall ceiling / upper floor (spruce) and roof deck, with the stair openings
    for x in range(B0 + 1, B1):
        for z in range(B0 + 1, B1):
            s.put(x, UPPER, z, 'spruce_planks')
            s.put(x, DECK, z, sb)
    # stairs hall -> upper floor along the right wall (x 12), rising to the front: y 1..6 at z 10..5
    for i in range(6):
        y, z = 1 + i, 10 - i
        s.remove(B1 - 1, y, z)
        s.put(B1 - 1, y, z, 'spruce_stairs', facing='north', **BOTTOM)
    for z in range(6, 11):
        s.remove(B1 - 1, UPPER, z)
    # stairs upper floor -> roof deck along the left wall (x 4), rising to the back: y 7..10 at z 5..8
    for i in range(4):
        y, z = UPPER + 1 + i, 5 + i
        s.remove(B0 + 1, y, z)
        s.put(B0 + 1, y, z, 'stone_brick_stairs', facing='south', **BOTTOM)
    for z in (6, 7):
        s.remove(B0 + 1, DECK, z)
    s.put(MID, DECK + 1, B0 + 1, 'stone_brick_stairs', facing='north', **BOTTOM)   # deck -> gatehouse roof

    # windows (glass panes; connect() joins them to the walls): hall 3 high, upper floor 2 high, tower slits
    def window(x, y, z):
        s.remove(x, y, z); s.put(x, y, z, 'glass_pane')
    for z in (6, 10):
        for y in (2, 3, 4):
            window(B0, y, z); window(B1, y, z)
    for x in (6, 10):
        for y in (2, 3, 4):
            window(x, y, B1)
    for c in (5, 8, 11):
        for y in (7, 8):
            window(B0, y, c); window(B1, y, c); window(c, y, B1)
    for y in (7, 8):
        window(MID, y, 1)                                            # gatehouse front, over the arch
    for tx, tz in TOWERS:
        for y in (4, 9):
            fx = tx if tx < MID else tx + 2                          # the tower's outer faces
            fz = tz if tz < MID else tz + 2
            window(fx, y, tz + 1); window(tx + 1, y, fz)

    # depth on the faces: a string course of upside-down stairs at the upper floor, corbels and battlements
    for x, z, f in overhang():
        s.put(x, DECK, z, 'stone_brick_stairs', facing=f, **TOP)
        s.put(x, DECK + 1, z, sb)
    for x, z, f in gate_overhang():
        s.put(x, DECK + 1, z, 'stone_brick_stairs', facing=f, **TOP)
        s.put(x, DECK + 2, z, sb)
    for tx, tz in TOWERS:
        for x in range(tx, tx + 3):
            for z in range(tz, tz + 3):
                s.put(x, TOWER_TOP, z, sb)
        for x, z, f in tower_ring(tx, tz):
            s.put(x, TOWER_TOP, z, 'stone_brick_stairs', facing=f, **TOP)
            s.put(x, TOWER_TOP + 1, z, sb)
        tower_roof(s, tx, tz)
    # battered plinth: cobblestone stairs leaning on every outside wall at y 1 (not in front of the arch)
    for x in range(W):
        for z in range(W):
            if footprint(x, z) or (7 <= x <= 9 and z == 0):
                continue
            for f, (dx, dz) in OUT.items():
                if footprint(x + dx, z + dz) and not passage(x + dx, z + dz):
                    s.put(x, 1, z, 'cobblestone_stairs', facing=f, **BOTTOM)
                    break

    # ------------------------------------------------------------ level 1: fittings
    s.at(1, 'fittings')
    # great hall: a red runner from the door to the throne, a dais with the throne, two long tables with benches
    for z in range(B0 + 1, 11):
        s.put(MID, 1, z, 'red_carpet')
    for x in range(6, 11):
        s.put(x, 1, 12, 'spruce_planks')
        s.put(x, 1, 11, 'spruce_stairs', facing='south', **BOTTOM)
    s.put(MID, 2, 12, 'dark_oak_stairs', facing='south', **BOTTOM)  # the throne, back to the back wall
    for tx in (6, 10):
        for z in range(5, 10):
            s.put(tx, 1, z, 'spruce_slab', type='top', waterlogged='false')       # table
            s.put(tx - 1, 1, z, 'spruce_stairs', facing='west', **BOTTOM)          # benches, backs away
            if tx + 1 < B1 - 1:
                s.put(tx + 1, 1, z, 'spruce_stairs', facing='east', **BOTTOM)
    s.put(B0 + 1, 1, 8, 'furnace', facing='east', lit='false')      # hearth on the left wall
    for z in (4, 5):                                                 # stores by the door
        s.put(B0 + 1, 1, z, 'barrel', facing='up', open='false')
    s.put(B0 + 1, 2, 4, 'barrel', facing='up', open='false')
    s.put(B1 - 1, 1, 4, 'chest', facing='west', type='single', waterlogged='false')
    for x, z in ((6, 7), (10, 7), (MID, 10), (MID, 5)):             # hanging lanterns under the upper floor
        s.put(x, HALL_TOP, z, 'lantern', hanging='true', waterlogged='false')
    for x in (7, 9):                                                 # torches in the passage, by the door
        s.put(x, 2, 2, 'wall_torch', facing='north')
    # king's rooms upstairs
    s.bed(10, UPPER + 1, 11, 'south')
    s.put(11, UPPER + 1, 12, 'chest', facing='west', type='single', waterlogged='false')
    for x in range(7, 10):
        for z in range(7, 10):
            s.put(x, UPPER + 1, z, 'red_carpet')
    s.put(6, UPPER + 1, 11, 'spruce_slab', type='top', waterlogged='false')   # writing table and chair
    s.put(6, UPPER + 1, 10, 'spruce_stairs', facing='north', **BOTTOM)
    s.put(B1 - 2, UPPER + 1, 4, 'crafting_table')
    for x, z in ((MID, 8), (6, 5), (10, 5)):
        s.put(x, DECK - 1, z, 'lantern', hanging='true', waterlogged='false')
    s.put(MID, UPPER + 1, 2, 'lantern', hanging='false', waterlogged='false')   # in the room over the gate

    # ------------------------------------------------------------ level 2: grandeur
    s.at(2, 'flourish2')
    for x, z, f in overhang():                                       # merlons on every battlement
        if merlon(x, z):
            s.put(x, DECK + 2, z, brick_mix(x, DECK + 2, z))
    for x, z, f in gate_overhang():
        if merlon(x, z):
            s.put(x, DECK + 3, z, brick_mix(x, DECK + 3, z))
    for tx, tz in TOWERS:                                            # tower tops: merlons round the roofs
        for x, z, f in tower_ring(tx, tz):
            if merlon(x, z) or (x in (tx - 1, tx + 3) and z in (tz - 1, tz + 3)):
                s.put(x, TOWER_TOP + 2, z, brick_mix(x, TOWER_TOP + 2, z))
    for x in (7, 9):                                                 # two flags on the gatehouse roof
        s.put(x, DECK + 2, 2, 'spruce_fence'); s.put(x, DECK + 3, 2, 'spruce_fence')
        put_id(s, x, DECK + 4, 2, 'red_banner', rotation='8')
    for x in (g0, g1):                                               # great banners on the gatehouse front
        put_id(s, x, 8, 0, 'red_wall_banner', facing='north')
    for x in (B0 + 1, B1 - 1):                                       # and on the front recesses
        put_id(s, x, 4, B0 - 1, 'red_wall_banner', facing='north')
    # sills under the upper windows, outside
    for c in (5, 8, 11):
        for x, z in ((B0 - 1, c), (B1 + 1, c), (c, B1 + 1)):
            if (x, UPPER, z) not in s.blocks:
                put_id(s, x, UPPER, z, 'stone_brick_slab', type='top', waterlogged='false')
    # hall: banners behind the throne, lanterns on the dais
    put_id(s, MID, 4, B1 - 1, 'red_wall_banner', facing='north')
    for x in (6, 10):
        s.put(x, 2, 12, 'lantern', hanging='false', waterlogged='false')
    put_id(s, B0 + 1, 4, 8, 'red_wall_banner', facing='east')       # banner over the hearth
    for x in (7, 8, 9):                                              # a hood moulding over the arch
        s.put(x, 5, 0, 'stone_brick_stairs', facing='south', **TOP)
    for x in (g0, g1):                                               # torches either side of the arch, outside
        s.put(x, 3, 0, 'wall_torch', facing='north')
    for y in range(DECK + 1, DECK + 4):                              # flagpole in the middle of the roof deck
        s.put(MID, y, MID, 'spruce_fence')
    put_id(s, MID, DECK + 4, MID, 'red_banner', rotation='8')

    # ------------------------------------------------------------ level 3: finishing touches
    s.at(3, 'flourish3')
    put_id(s, MID, 4, 1, 'iron_bars')                                # portcullis teeth in the arch
    put_id(s, MID, 4, 2, 'iron_bars')
    # timber hoardings: a dark oak pent roof over every battlement walk (body and gatehouse), sloping outwards
    for x, z, f in overhang():                                       # posts in the crenels carry it
        if (x, DECK + 2, z) not in s.blocks:
            put_id(s, x, DECK + 2, z, 'dark_oak_fence')
    for x, z, f in gate_overhang():
        if (x, DECK + 3, z) not in s.blocks:
            put_id(s, x, DECK + 3, z, 'dark_oak_fence')
    for x, z, f in overhang():
        if (x, DECK + 3, z) not in s.blocks:
            put_id(s, x, DECK + 3, z, 'dark_oak_stairs', facing=f, half='bottom', shape='straight', waterlogged='false')
    for x, z, f in gate_overhang():
        put_id(s, x, DECK + 4, z, 'dark_oak_stairs', facing=f, half='bottom', shape='straight', waterlogged='false')
    for x in (5, 11):                                                # hall: banners on the side tables' ends
        put_id(s, B0 + 1 if x == 5 else B1 - 1, 4, 11, 'red_wall_banner', facing='east' if x == 5 else 'west')
    for x in (6, 10):                                                # candles on the tables
        s.put(x, 2, 7, 'candle', candles='3', lit='false', waterlogged='false')
    for x, z in ((7, 10), (9, 10)):                                  # carpet by the bed and the desk upstairs
        s.add(x, UPPER + 1, z, 'brown_carpet')
    s.put(B1 - 1, UPPER + 1, 12, 'barrel', facing='up', open='false')
    return connect(s)


STRUCTURES = [dict(path='keep', name='Keep', tier=4, zone='keep', role='keep', group='keep', terrain='skirt',
                   door='front', make=keep, max_height=H,
                   description='A grand stone keep after the approved sample: 15 x 15 on a cobblestone apron, four '
                               'corner towers with corbelled tops, a gatehouse with an arched passage, battlements on '
                               'corbels, a great hall with a throne, tables and a hearth, stairs (no ladders) to the '
                               "king's rooms and the roof deck. 16 tall: needs scaffold in game.",
                   changes='Dark oak ties it to the kingdom look: a dark oak door frame (level 1) and low dark oak '
                           'pyramid roofs that close the four tower tops (level 1, no more open boxes); level 3 now has '
                           'its own feature, timber hoardings (dark oak posts and pent roofs) over every battlement.')]
