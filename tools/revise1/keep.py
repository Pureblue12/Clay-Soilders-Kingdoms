"""Keep (landmark), v4: one symmetric plan that grows level by level, after Carlton's notes on v3 ("more differentiators
between levels in function and decoration; expand, get bigger, a grand hall and a throne for the king"; then "the keep
is all over the place": one clear plan, symmetric, on one grid, 2 to 3 materials).

Materials: stone bricks (walls), cobblestone (base, plinth, footings), dark oak (roofs, beams, door frames, furniture).
Red banners and carpets are the accent.

Plan (x across, z = 0 the front, centre line x = 15; box 31 x 19 x 33):
  L1  the keep (the approved sample keep, its 9 x 9 body scaled to 11 x 11): body x 10..20, z 9..19 on a cobblestone
      apron, walls y 1..9, battlements on corbels, four corner turrets with pointed dark oak caps; a guard hall on the
      ground floor, the king's chamber upstairs, stairs (no ladders) to the roof deck. Doors front and back.
  L2  the royal court: a GREAT HALL wing on the centre line behind the keep (x 11..19, z 19..31, 9 x 13, dark oak roof,
      tall windows, two fireplaces, long tables, the THRONE on a raised dais at the far end under banners), entered
      through the keep; and a walled forecourt in front (wall x = 2 and 28, z = 2, closed at z = 9 against the keep)
      with a gate on the centre line.
  L3  the castle: the forecourt wall raised into a curtain with merlons and carried round the whole site (back to
      z = 30), four corner towers with pointed dark oak roofs and flags, a gatehouse over the gate, a kitchen and a
      barracks lean-to against the curtain, a well and a forge under matching roofs, walled gardens by the hall.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise

W, H, D = 31, 19, 33
CX = 15                                  # centre line
K0, K1 = 10, 20                          # keep body x (and K0..K1 is 11 wide)
KZ0, KZ1 = 9, 19                         # keep body z
UPPER, DECK = 5, 9                       # upper floor y 5, roof deck (and wall top) y 9
HX0, HX1, HZ1 = 11, 19, 31               # great hall: walls x 11 / 19, z 19 (the keep's back wall) .. 31
WALL_X0, WALL_X1, WALL_Z = 2, 28, 2      # forecourt / curtain lines
COURT_Z1 = KZ0                           # forecourt closed along z = 9
BACK_Z = 30                              # L3 curtain back line
GATE = (14, 16)                          # gate opening x

TOP = dict(half='top', shape='straight', waterlogged='false')
BOT = dict(half='bottom', shape='straight', waterlogged='false')
TOWARD = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def base_mix(x, y, z):
    return 'mossy_cobblestone' if noise(x, y, z, 5) < 0.3 else 'cobblestone'


def brick_mix(x, y, z):
    """Level 2 and 3 masonry: stone bricks with mossy and cracked mixed in (STYLE.md), a fixed pattern."""
    n = noise(x, y, z, 7)
    return 'mossy_stone_bricks' if n < 0.12 else 'cracked_stone_bricks' if n < 0.24 else 'stone_bricks'


def stair_ring(s, cx, cz, y, k, block, add=False):
    """A square ring of stairs of radius k round (cx, cz), rising to the middle (a pyramid course)."""
    put = s.add if add else s.put
    for x in range(cx - k, cx + k + 1):
        for z in range(cz - k, cz + k + 1):
            if max(abs(x - cx), abs(z - cz)) != k:
                continue
            f = 'south' if z == cz - k else 'north' if z == cz + k else 'east' if x == cx - k else 'west'
            shape = 'straight'
            if abs(x - cx) == k and abs(z - cz) == k:
                west = x < cx
                shape = ('outer_left' if west else 'outer_right') if f == 'south' else ('outer_right' if west else 'outer_left')
            put(x, y, z, block, facing=f, half='bottom', shape=shape, waterlogged='false')


def spire(s, cx, cz, y, r, fence, add=False):
    """A pointed dark oak roof: a flared eave course of radius r, then the radius-1 course twice (steep), a plank core
    and a fence finial on top (a flag goes one above). Returns the finial's top y."""
    put = s.add if add else s.put
    courses = list(range(r, 1, -1)) + [1, 1]
    for i, k in enumerate(courses):
        stair_ring(s, cx, cz, y + i, k, 'dark_oak_stairs', add)
        for x in range(cx - k + 1, cx + k):
            for z in range(cz - k + 1, cz + k):
                put(x, y + i, z, 'dark_oak_planks')
    top = y + len(courses)
    put(cx, top, cz, 'dark_oak_planks')
    put(cx, top + 1, cz, fence)
    return top + 1


def corbel_ring(s, cx, cz, y, k, block='stone_brick_stairs', add=False):
    """Upside-down stairs round (cx, cz) at radius k, each leaning towards the middle (corbels under an overhang)."""
    put = s.add if add else s.put
    for x in range(cx - k, cx + k + 1):
        for z in range(cz - k, cz + k + 1):
            if max(abs(x - cx), abs(z - cz)) != k:
                continue
            f = 'south' if z == cz - k else 'north' if z == cz + k else 'east' if x == cx - k else 'west'
            put(x, y, z, block, facing=f, **TOP)


def keep():
    s = Structure('keep', (W, H, D))
    s.p = {}                                                         # exact block ids only (no palette roles)
    sb = 'stone_bricks'

    def in_body(x, z):
        return K0 <= x <= K1 and KZ0 <= z <= KZ1

    turrets = [(K0, KZ0), (K1, KZ0), (K0, KZ1), (K1, KZ1)]          # turret centres = the body corners

    def in_turret(x, z):
        return any(abs(x - tx) <= 1 and abs(z - tz) <= 1 for tx, tz in turrets)

    # ================================================================ level 1: the keep (KNOWN-OK blocks only)
    s.at(1, 'shell')
    for x in range(K0 - 1, K1 + 2):                                  # apron and ground floor
        for z in range(KZ0 - 1, KZ1 + 2):
            s.put(x, 0, z, sb if in_body(x, z) and not (x in (K0, K1) or z in (KZ0, KZ1)) else base_mix(x, 0, z))

    def quoin(x, z):
        return x in (K0, K1) and z in (KZ0, KZ1)

    for y in range(1, DECK + 1):                                     # walls
        for x in range(K0, K1 + 1):
            for z in range(KZ0, KZ1 + 1):
                if x in (K0, K1) or z in (KZ0, KZ1):
                    if y == 1:
                        s.put(x, y, z, base_mix(x, y, z))
                    elif quoin(x, z) and y % 2 == 0:
                        s.put(x, y, z, 'cobblestone')
                    else:
                        s.put(x, y, z, sb)
    for x in range(K0 + 1, K1):                                      # upper floor and roof deck
        for z in range(KZ0 + 1, KZ1):
            s.put(x, UPPER, z, 'spruce_planks')
            s.put(x, DECK, z, sb)
    # stairs: ground -> upper along the west wall (x 11, rising to the front), upper -> deck along the east wall
    for i in range(4):
        s.put(K0 + 1, 1 + i, 17 - i, 'spruce_stairs', facing='north', **BOT)
    for z in range(14, 18):
        s.remove(K0 + 1, UPPER, z)
    for i in range(4):
        y, z = UPPER + 1 + i, 11 + i
        s.remove(K1 - 1, y, z)
        s.put(K1 - 1, y, z, 'spruce_stairs', facing='south', **BOT)
    for z in (11, 12, 13):
        s.remove(K1 - 1, DECK, z)

    # doors front and back on the centre line, in dark oak frames
    for z, f in ((KZ0, 'north'), (KZ1, 'south')):
        s.remove(CX, 1, z); s.remove(CX, 2, z)
        s.door(CX, 1, z, f, block='spruce_door')
        for y in (1, 2):
            for x in (CX - 1, CX + 1):
                s.remove(x, y, z); s.put(x, y, z, 'dark_oak_log', axis='y')
        for x in (CX - 1, CX, CX + 1):
            s.remove(x, 3, z); s.put(x, 3, z, 'dark_oak_log', axis='x')

    def window(x, y, z):
        s.remove(x, y, z); s.put(x, y, z, 'glass_pane')
    for c in (12, 18):                                               # ground floor: front and back
        for y in (2, 3):
            window(c, y, KZ0); window(c, y, KZ1)
    for c in (12, 15, 18):                                           # ground floor sides, upper floor all round
        for y in (2, 3):
            window(K0, y, c); window(K1, y, c)
        for y in (6, 7):
            window(K0, y, c); window(K1, y, c); window(c, y, KZ0); window(c, y, KZ1)

    # battlements on corbels one block out, with merlons; turrets on the corners
    ring = []
    for z in range(KZ0, KZ1 + 1):
        ring += [(K0 - 1, z, 'east'), (K1 + 1, z, 'west')]
    for x in range(K0, K1 + 1):
        ring += [(x, KZ0 - 1, 'south'), (x, KZ1 + 1, 'north')]
    for x, z, f in ring:
        if in_turret(x, z):
            continue
        s.put(x, DECK, z, 'stone_brick_stairs', facing=f, **TOP)
        s.put(x, DECK + 1, z, sb)
        if (x + z) % 2 == 0:
            s.put(x, DECK + 2, z, sb)
    for tx, tz in turrets:                                           # a turret on corbels over each corner
        for x in range(tx - 1, tx + 2):
            for z in range(tz - 1, tz + 2):
                if not in_body(x, z):
                    f = 'south' if z < tz else 'north' if z > tz else 'east' if x < tx else 'west'
                    s.put(x, DECK, z, 'stone_brick_stairs', facing=f, **TOP)
                for y in range(DECK + 1, DECK + 4):
                    s.put(x, y, z, 'cobblestone' if (x != tx and z != tz and y % 2 == 0) else sb)
        spire(s, tx, tz, DECK + 4, 2, 'spruce_fence')                # under a pointed dark oak cap
    # battered plinth: cobblestone stairs leaning on the walls (not at the doors)
    for x in range(K0 - 1, K1 + 2):
        for z in range(KZ0 - 1, KZ1 + 2):
            if in_body(x, z) or x == CX:
                continue
            for f, (dx, dz) in {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}.items():
                if in_body(x + dx, z + dz):
                    s.put(x, 1, z, 'cobblestone_stairs', facing=f, **BOT)
                    break

    # ---------------------------------------------------------------- level 1 fittings: guard hall, king's chamber
    s.at(1, 'fittings')
    for z in range(KZ0 + 1, KZ1):
        s.put(CX, 1, z, 'red_carpet')
    for tx in (13, 17):                                              # the garrison's mess tables
        for z in range(12, 17):
            s.put(tx, 1, z, 'spruce_slab', type='top', waterlogged='false')
            s.put(tx - 1 if tx == 13 else tx + 1, 1, z, 'spruce_stairs', facing='west' if tx == 13 else 'east', **BOT)
    s.put(K1 - 1, 1, 17, 'furnace', facing='west', lit='false')
    s.put(K1 - 1, 1, 18, 'grindstone', face='floor', facing='west')
    for z in (10, 11):
        s.put(K0 + 1, 1, z, 'barrel', facing='up', open='false')
        s.put(K1 - 1, 1, z, 'chest', facing='west', type='single', waterlogged='false')
    s.put(K0 + 1, 2, 10, 'barrel', facing='up', open='false')
    s.put(K0 + 1, 1, 18, 'crafting_table')
    for x, z in ((13, 12), (17, 12), (13, 16), (17, 16)):
        s.put(x, UPPER - 1, z, 'lantern', hanging='true', waterlogged='false')
    s.put(13, UPPER + 1, 17, 'red_bed', facing='south', part='foot', occupied='false')   # the king's chamber
    s.put(13, UPPER + 1, 18, 'red_bed', facing='south', part='head', occupied='false')
    s.put(12, UPPER + 1, 18, 'chest', facing='east', type='single', waterlogged='false')
    for x in range(14, 17):
        for z in range(14, 17):
            s.put(x, UPPER + 1, z, 'red_carpet')
    s.put(17, UPPER + 1, 18, 'spruce_slab', type='top', waterlogged='false')
    s.put(17, UPPER + 1, 17, 'spruce_stairs', facing='north', **BOT)
    s.put(12, UPPER + 1, 10, 'crafting_table')
    for x, z in ((CX, 12), (CX, 16)):
        s.put(x, DECK - 1, z, 'lantern', hanging='true', waterlogged='false')

    # ================================================================ level 2: the royal court
    s.at(2, 'shell2')
    # ---- great hall: walls x 11 / 19 from z 20 to 31, the keep's back wall is its front
    def hall_wall(x, y, z):
        if y == 1:
            s.add(x, y, z, base_mix(x, y, z))
        else:
            s.add(x, y, z, brick_mix(x, y, z))
    windows = (22, 23, 25, 26, 28, 29)
    for z in range(KZ1 + 1, HZ1 + 1):
        for x in (HX0, HX1):
            s.add(x, 0, z, 'cobblestone')
            for y in range(1, 7):
                if z in windows and 2 <= y <= 5:
                    s.put(x, y, z, 'glass_pane')
                elif z == 24 and y == 1:
                    s.put(x, y, z, 'campfire', facing='east' if x == HX0 else 'west', lit='true',
                          signal_fire='false', waterlogged='false')       # the hearths
                else:
                    hall_wall(x, y, z)
            s.put(x, 7, z, 'dark_oak_log', axis='z')                 # top plate
    for x in range(HX0 + 1, HX1):
        s.add(x, 0, HZ1, 'cobblestone')
        for y in range(1, 7):
            hall_wall(x, y, HZ1)
        s.put(x, 7, HZ1, 'dark_oak_log', axis='x')
        for y in (7, 8):
            s.put(x, y, KZ1 + 1, sb)                                 # closes the gap under the keep's battlements
    for x in range(HX0 + 1, HX1):                                    # floor
        for z in range(KZ1 + 2, HZ1):
            s.put(x, 0, z, 'dark_oak_planks')
    # chimneys outside the hearths, buttresses between the bays
    for x, f in ((HX0 - 1, 'east'), (HX1 + 1, 'west')):
        for y in range(0, 10):
            s.put(x, y, 24, 'cobblestone' if y < 2 else sb)
        for z in (27, HZ1):
            for y in range(0, 5):
                s.put(x, y, z, base_mix(x, y, z) if y < 2 else sb)
            s.put(x, 5, z, 'stone_brick_stairs', facing=f, **BOT)
    for x in (13, 17):                                               # back buttresses
        for y in range(0, 5):
            s.put(x, y, HZ1 + 1, base_mix(x, y, HZ1 + 1) if y < 2 else sb)
        s.put(x, 5, HZ1 + 1, 'stone_brick_stairs', facing='north', **BOT)
    # roof: dark oak, 45 degrees, eaves on the top plates, one block overhang at the back
    for z in range(KZ1 + 2, HZ1 + 2):
        for i in range(5):
            y = 7 + i
            for x, f in ((HX0 - 1 + i, 'east'), (HX1 + 1 - i, 'west')):
                if (x, y, z) not in s.blocks:
                    s.put(x, y, z, 'dark_oak_stairs', facing=f, **BOT)
        s.put(CX, 11, z, 'dark_oak_planks')
        s.put(CX, 12, z, 'dark_oak_slab', type='bottom', waterlogged='false')
    for y in range(8, 11):                                           # back gable
        for x in range(HX0 + y - 7, HX1 - y + 8):
            if (x, y, HZ1) not in s.blocks:
                s.put(x, y, HZ1, brick_mix(x, y, HZ1))
    for y in (8, 9):
        s.remove(CX, y, HZ1); s.put(CX, y, HZ1, 'glass_pane')        # a tall window over the throne
    for z in (24, 28):                                               # tie beams
        for x in range(HX0 + 1, HX1):
            s.put(x, 7, z, 'dark_oak_log', axis='x')

    # ---- forecourt wall: footing y 0, cobble y 1, stone bricks y 2..3, posts to y 4 every fourth block
    def wall_line():
        cells = [(x, WALL_Z) for x in range(WALL_X0, WALL_X1 + 1) if not GATE[0] <= x <= GATE[1]]
        for z in range(WALL_Z + 1, COURT_Z1 + 1):
            cells += [(WALL_X0, z), (WALL_X1, z)]
        for x in list(range(WALL_X0 + 1, K0)) + list(range(K1 + 1, WALL_X1)):
            cells.append((x, COURT_Z1))
        return cells
    for x, z in wall_line():
        s.add(x, 0, z, 'cobblestone')
        s.add(x, 1, z, base_mix(x, 1, z))
        for y in (2, 3):
            s.add(x, y, z, brick_mix(x, y, z))
        post = (x in (WALL_X0, WALL_X1) and (z - WALL_Z) % 4 == 3) or (z == WALL_Z and abs(x - CX) % 4 == 2) or \
            (z == COURT_Z1 and x in (5, 25))
        if post:
            s.add(x, 4, z, sb)
    for x in (GATE[0] - 1, GATE[1] + 1):                            # gate piers
        s.add(x, 4, WALL_Z, sb)
    for x in range(GATE[0], GATE[1] + 1):                           # path: gate to the keep door
        for z in range(WALL_Z, KZ0 - 1):
            s.put(x, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')

    # ---- level 2 fittings: the throne room
    s.at(2, 'flourish2')
    for z in range(KZ1 + 2, 28):                                     # red runner from the keep's back door
        s.put(CX, 1, z, 'red_carpet')
    for x in range(HX0 + 1, HX1):                                    # dais (z 29..30) with a step at z 28
        for z in (29, 30):
            s.put(x, 1, z, 'dark_oak_planks')
        s.put(x, 1, 28, 'dark_oak_stairs', facing='south', **BOT)
    for x in range(13, 18):
        if x != CX:
            s.put(x, 2, 29, 'red_carpet')
    s.put(CX, 2, 29, 'dark_oak_stairs', facing='south', **BOT)        # the throne: seat, arms, high back, gold crest
    s.put(CX - 1, 2, 29, 'dark_oak_stairs', facing='west', **TOP)
    s.put(CX + 1, 2, 29, 'dark_oak_stairs', facing='east', **TOP)
    for y in (2, 3):
        s.put(CX, y, 30, 'dark_oak_planks')
    s.put(CX, 4, 30, 'gold_block')
    s.put(CX, 5, 30, 'red_wall_banner', facing='north')
    for x in (13, 17):
        s.put(x, 4, 30, 'red_wall_banner', facing='north')
        s.put(x, 2, 30, 'lantern', hanging='false', waterlogged='false')
    for tx, (b0, b1) in ((13, ('west', 'east')), (17, ('west', 'east'))):   # long tables with benches
        for z in range(21, 27):
            s.put(tx, 1, z, 'dark_oak_slab', type='top', waterlogged='false')
            if z != 24:
                s.put(tx - 1, 1, z, 'dark_oak_stairs', facing=b0, **BOT)
                s.put(tx + 1, 1, z, 'dark_oak_stairs', facing=b1, **BOT)
    for tx in (13, 17):
        s.put(tx, 2, 22, 'candle', candles='3', lit='true', waterlogged='false')
        s.put(tx, 2, 25, 'candle', candles='3', lit='true', waterlogged='false')
    for z in (21, 27):                                               # banners between the windows
        s.put(HX0 + 1, 4, z, 'red_wall_banner', facing='east')
        s.put(HX1 - 1, 4, z, 'red_wall_banner', facing='west')
    for z in (24, 28):                                               # lanterns on chains from the tie beams
        for x in (13, 17):
            s.put(x, 6, z, 'chain', axis='y', waterlogged='false')
            s.put(x, 5, z, 'lantern', hanging='true', waterlogged='false')
    # the keep dressed for the court: banners at the door, flags on the turrets
    for x in (CX - 2, CX + 2):
        s.put(x, 6, KZ0 - 1, 'red_wall_banner', facing='north')
    for x in (CX - 1, CX + 1):
        s.put(x, 2, KZ0 - 1, 'wall_torch', facing='north')
    for tx, tz in turrets:
        s.put(tx, DECK + 4 + 5, tz, 'red_banner', rotation='8')
    for x in (12, 18):                                               # lantern posts in the forecourt
        s.put(x, 1, 5, 'dark_oak_fence'); s.put(x, 2, 5, 'dark_oak_fence')
        s.put(x, 3, 5, 'lantern', hanging='false', waterlogged='false')

    # ================================================================ level 3: the castle
    s.at(3, 'shell3')
    corners = [(WALL_X0, WALL_Z), (WALL_X1, WALL_Z), (WALL_X0, BACK_Z), (WALL_X1, BACK_Z)]

    def in_tower(x, z):
        return any(abs(x - cx) <= 1 and abs(z - cz) <= 1 for cx, cz in corners)

    def gate_tower(x, z):
        return 1 <= z <= 3 and (11 <= x <= 13 or 17 <= x <= 19)

    curtain = wall_line()
    for z in range(COURT_Z1 + 1, BACK_Z + 1):                        # carried back round the gardens to the hall
        curtain += [(WALL_X0, z), (WALL_X1, z)]
    for x in list(range(WALL_X0 + 1, HX0)) + list(range(HX1 + 1, WALL_X1)):
        curtain.append((x, BACK_Z))
    for x, z in curtain:
        if in_tower(x, z) or gate_tower(x, z):
            continue
        inner = z == COURT_Z1 and x not in (WALL_X0, WALL_X1)       # the old forecourt wall stays low
        s.add(x, 0, z, 'cobblestone')
        s.add(x, 1, z, base_mix(x, 1, z))
        for y in (2, 3):
            s.add(x, y, z, brick_mix(x, y, z))
        if inner:
            if (x + z) % 2 == 0:
                s.add(x, 4, z, brick_mix(x, 4, z))
            continue
        for y in (4, 5):
            s.add(x, y, z, brick_mix(x, y, z))
        if (x + z) % 2 == 0:
            s.add(x, 6, z, brick_mix(x, 6, z))
    for cx, cz in corners:                                           # corner towers
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                for y in range(0, 10):
                    slit = y in (4, 7) and (x, z) in ((cx - 1 if cx < CX else cx + 1, cz), (cx, cz - 1 if cz < 10 else cz + 1))
                    if slit:
                        s.add(x, y, z, 'glass_pane')
                    else:
                        s.add(x, y, z, base_mix(x, y, z) if y < 2 else 'cobblestone' if (x != cx and z != cz and y % 2 == 0) else brick_mix(x, y, z))
        corbel_ring(s, cx, cz, 9, 2, add=True)
        spire(s, cx, cz, 10, 2, 'dark_oak_fence', add=True)
        s.add(cx, 10 + 5, cz, 'red_banner', rotation='8')
    # gatehouse: two towers flanking the gate joined over it into one block, a corbelled battlement on top
    for x in range(11, 20):
        for z in range(1, 4):
            tower = x <= 13 or x >= 17
            for y in range(0 if tower else 4, 9):
                quoin = tower and x in (11, 13, 17, 19) and z in (1, 3) and y % 2 == 0
                s.add(x, y, z, base_mix(x, y, z) if y < 2 else 'cobblestone' if quoin else brick_mix(x, y, z))
    for z in (1, 2, 3):
        s.put(GATE[0], 3, z, 'stone_brick_stairs', facing='west', **TOP)
        s.put(GATE[1], 3, z, 'stone_brick_stairs', facing='east', **TOP)
    for x in range(10, 21):                                          # corbels, parapet and merlons, one block out
        for z in range(0, 5):
            if x in (10, 20) or z in (0, 4):
                f = 'south' if z == 0 else 'north' if z == 4 else 'east' if x == 10 else 'west'
                s.add(x, 9, z, 'stone_brick_stairs', facing=f, **TOP)
                s.add(x, 10, z, brick_mix(x, 10, z))
                if (x + z) % 2 == 0:
                    s.add(x, 11, z, brick_mix(x, 11, z))
            else:
                s.add(x, 9, z, 'stone_bricks')
    for x in (12, 18):
        s.put(x, 6, 0, 'red_wall_banner', facing='north')
        for y in (10, 11, 12):
            s.add(x, y, 2, 'dark_oak_fence')
        s.add(x, 13, 2, 'red_banner', rotation='8')
    s.put(CX, 6, 0, 'stone_brick_stairs', facing='south', **TOP)    # a hood over the arch
    # lean-tos against the curtain: kitchen (west) and barracks (east), dark oak timber on a cobble footing
    for side in (-1, 1):
        wx = CX + side * (CX - WALL_X0)                              # the curtain line
        x_in, x_front, x_eave = wx - side, wx - 3 * side, wx - 4 * side
        for z in range(4, 8):
            for x in (x_in, wx - 2 * side, x_front):
                edge = x == x_front or z in (4, 7)
                if not edge:
                    continue
                s.add(x, 0, z, 'cobblestone')
                for y in (1, 2, 3):
                    post = (x == x_front and z in (4, 7)) or (z in (4, 7) and x == x_in)
                    s.add(x, y, z, 'dark_oak_log' if post else 'dark_oak_planks', **({'axis': 'y'} if post else {}))
        for z in (4, 7):                                             # gable infill under the slope
            s.add(x_in, 4, z, 'dark_oak_planks'); s.add(x_in, 5, z, 'dark_oak_planks')
            s.add(wx - 2 * side, 4, z, 'dark_oak_planks')
        f = 'west' if side < 0 else 'east'
        for z in range(3, 9):
            for x, y in ((x_eave, 3), (x_front, 4), (wx - 2 * side, 5), (x_in, 6)):
                s.add(x, y, z, 'dark_oak_stairs', facing=f, **BOT)
        for x in (x_in, wx - 2 * side):
            s.add(x, 0, 5, 'dark_oak_planks'); s.add(x, 0, 6, 'dark_oak_planks')
        s.remove(x_front, 1, 5); s.remove(x_front, 2, 5)
        s.door(x_front, 1, 5, 'east' if side < 0 else 'west', block='spruce_door')
        s.remove(x_front, 2, 6); s.put(x_front, 2, 6, 'glass_pane')
    # well (west) and forge (east): matching posts under small pointed roofs
    for cx, kind in ((9, 'well'), (21, 'forge')):
        cz = 5
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                if (x, z) == (cx, cz):
                    continue
                s.add(x, 0, z, 'cobblestone')
                if x != cx and z != cz:
                    for y in (1, 2, 3):
                        s.add(x, y, z, 'dark_oak_fence')
                elif kind == 'well':
                    s.add(x, 1, z, 'cobblestone_wall')
        stair_ring(s, cx, cz, 4, 1, 'dark_oak_stairs', add=True)
        s.add(cx, 4, cz, 'dark_oak_planks')
        s.add(cx, 5, cz, 'dark_oak_slab', type='bottom', waterlogged='false')
        if kind == 'well':
            s.decorate(cx, 0, cz, 'water')
            s.add(cx, 3, cz, 'chain', axis='y', waterlogged='false')
        else:
            s.add(cx, 0, cz, 'cobblestone')
            s.add(cx, 1, cz, 'anvil', facing='east')
            s.add(cx + 1, 1, cz, 'blast_furnace', facing='west', lit='false')
            s.add(cx - 1, 1, cz, 'grindstone', face='floor', facing='west')
    # walled gardens beside the great hall: flower beds round a bush, a gravel walk
    s.at(3, 'flourish3')
    for side in (-1, 1):
        for zc in (14, 21, 27):
            xc = CX + side * 9
            for x in range(xc - 2, xc + 3):
                for z in range(zc - 1, zc + 2):
                    s.add(x, 0, z, 'coarse_dirt')
                    if (x, z) == (xc, zc):
                        s.add(x, 1, z, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
                    elif (x + z) % 2 == 0:
                        s.add(x, 1, z, ['poppy', 'cornflower', 'oxeye_daisy'][(zc // 7 + (x + z) // 2) % 3])
        for z in range(KZ1 + 2, BACK_Z):                             # gravel walk along the hall
            s.add(CX + side * 6, 0, z, 'gravel')
    for x in (WALL_X0 + 4, WALL_X1 - 4, CX - 6, CX + 6):            # banners on the curtain front
        s.add(x, 4, WALL_Z - 1, 'red_wall_banner', facing='north')
    for x in (GATE[0] - 1, GATE[1] + 1):                            # torches either side of the gate, outside
        s.add(x, 2, 0, 'wall_torch', facing='north')
    for x in (CX - 4, CX + 4):                                       # more lanterns in the forecourt
        s.add(x, 1, 7, 'dark_oak_fence'); s.add(x, 2, 7, 'lantern', hanging='false', waterlogged='false')
    for side in (-1, 1):                                             # kitchen and barracks fittings
        wx = CX + side * (CX - WALL_X0)
        x_in = wx - side
        if side < 0:
            s.add(x_in, 1, 5, 'smoker', facing='east', lit='false'); s.add(x_in, 1, 6, 'barrel', facing='up', open='false')
        else:
            s.add(x_in, 1, 5, 'red_bed', facing='south', part='foot', occupied='false')
            s.add(x_in, 1, 6, 'red_bed', facing='south', part='head', occupied='false')
    return connect(s)


STRUCTURES = [dict(path='keep', name='Keep', tier=4, zone='keep', role='keep', group='keep', terrain='skirt',
                   door='front', make=keep, max_height=20,
                   description='A castle that grows round the approved sample keep, symmetric on one centre line. '
                               'Level 1: the keep, 11 x 11 on a cobblestone apron, battlements on corbels, four '
                               'corner turrets with pointed dark oak caps, a guard hall, the king\'s chamber and '
                               'stairs (no ladders) to the roof. Level 2: the royal court, a 9 x 13 great hall behind '
                               'the keep (dark oak roof, tall windows, two hearths, long tables, the throne on a dais '
                               'under banners) and a walled forecourt with a gate. Level 3: the castle, a curtain '
                               'wall round the whole site with four corner towers (pointed roofs, flags), a '
                               'gatehouse, kitchen and barracks lean-tos, a well, a forge and walled gardens. '
                               '19 tall: needs scaffold in game.',
                   changes='Rebuilt as one symmetric plan that grows outward: level 1 is the sample keep with pointed '
                           'turret caps (no flat lids), level 2 adds a great hall with the throne on a dais behind it '
                           'and a walled forecourt, level 3 turns it into a castle with a curtain wall, four corner '
                           'towers, a gatehouse, a kitchen, barracks, a well and gardens.')]
