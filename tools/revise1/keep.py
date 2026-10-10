"""Keep (landmark), v5: the v4 plan made into a place clay soldiers live in, after Carlton's note on v4 ("some of the
areas seem inaccessible and don't seem like they really fit; remember soldiers will actually use this place"), and
steep cone roofs on every turret and tower (the stepped caps read as pagoda, not medieval).

Every floor, yard, wall walk and tower top can be walked to from the front gate (doors, open doorways, stairs; no
ladders, no fence gates): checked with a flood fill over the saved levels (stairs and full-block steps up 1, drops up to
3, wooden doors passable).

Materials: stone bricks (walls), cobblestone (base, plinth, footings), dark oak (roofs, beams, door frames, furniture).
Red banners and carpets are the accent.

Plan (x across, z = 0 the front, centre line x = 15; box 31 x 20 x 33):
  L1  the keep (the approved sample keep, its 9 x 9 body scaled to 11 x 11): body x 10..20, z 9..19 on a cobblestone
      apron, walls y 1..9, battlements on corbels, four corner turrets under steep dark oak cones; a guard hall on the
      ground floor, the king's chamber upstairs, stairs (no ladders) to the roof deck. Doors front and back.
  L2  the royal court: a GREAT HALL behind the keep (x 11..19, z 19..31; tables, two hearths, the THRONE on a dais),
      entered through the keep and by two side doors (z 21); a walled forecourt in front with the gate on the centre
      line, a 2-wide gateway each side of its back wall, and its front corners cut in with a postern door (the future
      tower doors).
  L3  the castle: the forecourt wall raised into a curtain carried round both side wards to the hall, merlons outside
      and a corbelled wall walk inside (y 6); four hollow 5 x 5 corner towers with a stair winding round a pillar from
      the ground door to both wall walks and up to a lookout under a cone; a gatehouse with a guard chamber over the
      passage (doors from both walks) and a stair to its battlements. Forecourt: storehouse and guardroom lean-tos, a
      well and a forge. West ward: flower garden by the keep, kitchen by the hall's side door, firewood. East ward:
      training yard by the keep, armory by the hall's side door, hay.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise

W, H, D = 31, 20, 33
CX = 15                                  # centre line
K0, K1 = 10, 20                          # keep body x (and K0..K1 is 11 wide)
KZ0, KZ1 = 9, 19                         # keep body z
UPPER, DECK = 5, 9                       # upper floor y 5, roof deck (and wall top) y 9
HX0, HX1, HZ1 = 11, 19, 31               # great hall: walls x 11 / 19, z 19 (the keep's back wall) .. 31
WALL_X0, WALL_X1, WALL_Z = 3, 27, 3      # forecourt / curtain lines
COURT_Z1 = KZ0                           # forecourt closed along z = 9
BACK_Z = 29                              # L3 curtain back line
GATE = (14, 16)                          # gate opening x
SIDE_DOOR = 21                           # z of the great hall's side doors
TURRET_FLAG = DECK + 9                   # the keep turrets' flagpole top (cone from DECK + 3)

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


def cone(s, cx, cz, y, r, fence, slab='dark_oak_slab', tip=2, add=False):
    """A steep pointed dark oak roof, taller than it is wide: one eave ring of radius r at y (overhanging the walls),
    then for each smaller radius a course of planks and a ring of stairs on it (two up per one in), a plank tip,
    a slab and a fence flagpole. Returns the flagpole's y."""
    put = s.add if add else s.put
    stair_ring(s, cx, cz, y, r, 'dark_oak_stairs', add)
    yy = y + 1
    for k in range(r - 1, 0, -1):
        for x in range(cx - k, cx + k + 1):
            for z in range(cz - k, cz + k + 1):
                put(x, yy, z, 'dark_oak_planks')
        stair_ring(s, cx, cz, yy + 1, k, 'dark_oak_stairs', add)
        for x in range(cx - k + 1, cx + k):
            for z in range(cz - k + 1, cz + k):
                put(x, yy + 1, z, 'dark_oak_planks')
        yy += 2
    for i in range(tip):
        put(cx, yy + i, cz, 'dark_oak_planks')
    if slab:
        put(cx, yy + tip, cz, slab, type='bottom', waterlogged='false')
    else:
        put(cx, yy + tip, cz, 'dark_oak_planks')                     # a plank tip (no extra block state)
    put(cx, yy + tip + 1, cz, fence)
    return yy + tip + 1


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
            if x in (K0 - 1, K1 + 1) and z in (KZ0 - 1, KZ1 + 1):
                s.put(x, 0, z, 'coarse_dirt')                        # planting holes at the apron's corners
            else:
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
        assert cone(s, tx, tz, DECK + 3, 2, 'spruce_fence', None) == TURRET_FLAG   # a steep dark oak cone
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
    s.put(13, UPPER + 1, 10, 'crafting_table')
    for x, z in ((CX, 12), (CX, 16)):
        s.put(x, DECK - 1, z, 'lantern', hanging='true', waterlogged='false')

    # ---------------------------------------------------------------- level 1 polish (v6, KNOWN-OK blocks only)
    # a belt course of upside-down stairs at the upper floor line on the front and sides (the upper windows' sills; the
    # back is the great hall's wall from level 2 on)
    for z in range(KZ0, KZ1 + 1):
        s.put(K0 - 1, UPPER, z, 'stone_brick_stairs', facing='east', **TOP)
        s.put(K1 + 1, UPPER, z, 'stone_brick_stairs', facing='west', **TOP)
    for x in range(K0, K1 + 1):
        s.put(x, UPPER, KZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)

    def shutters(x, z, along, facing, ys):
        """Open spruce trapdoors flat against the wall either side of a window (facing away from the wall)."""
        for d in (-1, 1):
            for y in ys:
                xx, zz = (x + d, z) if along == 'x' else (x, z + d)
                s.put(xx, y, zz, 'spruce_trapdoor', facing=facing, half='bottom', open='true', powered='false',
                      waterlogged='false')
    for c in (12, 18):                                               # front windows of the guard hall
        shutters(c, KZ0 - 1, 'x', 'north', (2, 3))
    shutters(K0 - 1, 15, 'z', 'west', (2, 3, 6, 7))                  # the middle side windows, both floors
    shutters(K1 + 1, 15, 'z', 'east', (2, 3, 6, 7))
    # the doors: a lantern on a stone bracket over each, torches on the door posts (front)
    for z, f in ((KZ0 - 1, 'south'), (KZ1 + 1, 'north')):
        s.put(CX, 4, z, 'stone_brick_stairs', facing=f, **TOP)          # (the back one inside the hall from L2)
        s.put(CX, 3, z, 'lantern', hanging='true', waterlogged='false')
    for x in (CX - 1, CX + 1):
        s.put(x, 2, KZ0 - 1, 'wall_torch', facing='north')
    # poppies and bushes in the apron's corners
    for x in (K0 - 1, K1 + 1):
        s.put(x, 1, KZ0 - 1, 'poppy')
        s.put(x, 1, KZ1 + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    # roof deck: a watch fire, the watch's stores against the west parapet, a railing round the stair head
    s.put(CX, DECK + 1, 15, 'campfire', facing='south', lit='true', signal_fire='false', waterlogged='false')
    for z in (14, 16):
        s.put(K0, DECK + 1, z, 'barrel', facing='up', open='false')
    s.put(K0, DECK + 1, 15, 'chest', facing='east', type='single', waterlogged='false')
    for z in (11, 13):                                               # two lantern posts by the stair head
        s.put(K1 - 2, DECK + 1, z, 'spruce_fence')
        s.put(K1 - 2, DECK + 2, z, 'lantern', hanging='false', waterlogged='false')
    # king's chamber: a wardrobe of barrels, a night stand with a lantern
    for z in (10, 11):
        s.put(K0 + 1, UPPER + 1, z, 'barrel', facing='up', open='false')
    s.put(14, UPPER + 1, 18, 'spruce_slab', type='top', waterlogged='false')
    s.put(14, UPPER + 2, 18, 'lantern', hanging='false', waterlogged='false')
    for z in (14, 15):                                               # and a table for two by the east windows
        s.put(18, UPPER + 1, z, 'spruce_slab', type='top', waterlogged='false')
        s.put(17, UPPER + 1, z, 'spruce_stairs', facing='west', **BOT)
        s.put(19, UPPER + 1, z, 'spruce_stairs', facing='east', **BOT)
    s.put(18, UPPER + 2, 14, 'lantern', hanging='false', waterlogged='false')

    # ================================================================ level 2: the royal court
    s.at(2, 'shell2')
    # ---- great hall: walls x 11 / 19 from z 20 to 31, the keep's back wall is its front; side doors at z 21
    def hall_wall(x, y, z):
        if y == 1:
            s.add(x, y, z, base_mix(x, y, z))
        else:
            s.add(x, y, z, brick_mix(x, y, z))
    windows = (23, 25, 26, 28, 29)
    for z in range(KZ1 + 1, HZ1 + 1):
        for x in (HX0, HX1):
            s.add(x, 0, z, 'cobblestone')
            for y in range(1, 7):
                if z in windows and 2 <= y <= 5:
                    s.put(x, y, z, 'glass_pane')
                elif z == 24 and y == 1:
                    s.put(x, y, z, 'campfire', facing='south', lit='true',
                          signal_fire='false', waterlogged='false')       # the hearths
                elif z == SIDE_DOOR and y in (1, 2):
                    continue
                else:
                    hall_wall(x, y, z)
            s.put(x, 7, z, 'dark_oak_log', axis='z')                 # top plate
    for x in (HX0, HX1):                                             # side doors to the wards (kitchen / armory)
        s.door(x, 1, SIDE_DOOR, 'east', block='spruce_door')
        s.put(x, 3, SIDE_DOOR, 'dark_oak_log', axis='z')
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

    # ---- forecourt wall (footing y 0, cobble y 1, stone bricks y 2..3, posts to y 4). Its front corners are cut in
    # (the footprint of the level-3 corner towers' inner corner) with a postern door, which becomes the tower door;
    # the back wall against the keep has a 2-wide gateway each side (to the wards of level 3).
    def court_cells():
        cells = []
        for side in (-1, 1):
            def X(x):
                return x if side < 0 else W - 1 - x
            cells += [(X(x), WALL_Z) for x in range(5, GATE[0])]                     # front, tower edge to gate
            cells += [(X(5), 4), (X(5), 5), (X(4), 5)]                               # the cut corner
            cells += [(X(WALL_X0), z) for z in range(5, COURT_Z1 + 1)]               # side
            cells += [(X(x), COURT_Z1) for x in range(WALL_X0 + 1, 7)]               # back, curtain to gateway
        return cells
    posterns = [(5, 4), (W - 1 - 5, 4)]
    for x, z in court_cells():
        if (x, z) in posterns:
            continue
        s.add(x, 0, z, 'cobblestone')
        s.add(x, 1, z, base_mix(x, 1, z))
        for y in (2, 3):
            s.add(x, y, z, brick_mix(x, y, z))
        post = (z == WALL_Z and x in (5, 9, 13, 17, 21, 25)) or (x in (WALL_X0, WALL_X1) and z in (5, 9)) or \
            (z == COURT_Z1 and x in (6, 24)) or (z == 5 and x in (5, 25))
        if post:
            s.add(x, 4, z, sb)
    for x, z in posterns:
        s.add(x, 0, z, 'cobblestone')
        s.door(x, 1, z, 'east', block='spruce_door')
        s.add(x, 3, z, sb)
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
    for tx in (13, 17):                                              # long tables with benches (z 21 is the aisle
        for z in range(22, 27):                                      # between the side doors)
            s.put(tx, 1, z, 'dark_oak_slab', type='top', waterlogged='false')
            if z != 24:
                s.put(tx - 1, 1, z, 'dark_oak_stairs', facing='west', **BOT)
                s.put(tx + 1, 1, z, 'dark_oak_stairs', facing='east', **BOT)
    for tx in (13, 17):
        s.put(tx, 2, 22, 'candle', candles='3', lit='true', waterlogged='false')
        s.put(tx, 2, 25, 'candle', candles='3', lit='true', waterlogged='false')
    for z in (22, 27):                                               # banners between the windows
        s.put(HX0 + 1, 4, z, 'red_wall_banner', facing='east')
        s.put(HX1 - 1, 4, z, 'red_wall_banner', facing='west')
    for z in (24, 28):                                               # lanterns on chains from the tie beams
        for x in (13, 17):
            s.put(x, 6, z, 'chain', axis='y', waterlogged='false')
            s.put(x, 5, z, 'lantern', hanging='true', waterlogged='false')
    # the keep dressed for the court: banners at the door, flags on the turrets
    for x in (CX - 2, CX + 2):
        s.put(x, 7, KZ0 - 1, 'red_wall_banner', facing='north')     # above the belt course
    for tx, tz in turrets:
        s.put(tx, TURRET_FLAG + 1, tz, 'red_banner', rotation='8')
    for x in (13, 17):                                               # lantern posts beside the path
        s.put(x, 0, 7, 'cobblestone'); s.put(x, 1, 7, 'dark_oak_fence'); s.put(x, 2, 7, 'dark_oak_fence')
        s.put(x, 3, 7, 'lantern', hanging='false', waterlogged='false')

    # ---- level 2 polish (v6)
    flowers = ['poppy', 'cornflower', 'oxeye_daisy']
    for x in (CX - 3, CX + 3):                                       # flowers at the foot of the lantern posts
        for z in (6, 7):
            s.put(x, 0, z, 'coarse_dirt')
            s.put(x, 1, z, flowers[(x + z) % len(flowers)])
    for z in (SIDE_DOOR, 27):                                        # red carpet across the hall: the side-door aisle
        for x in range(HX0 + 1, HX1):                                # and in front of the dais
            if (x, 1, z) not in s.blocks:
                s.put(x, 1, z, 'red_carpet')
    for x in (HX0 - 2, HX1 + 2):                                     # a lantern post by each side door
        s.put(x, 1, SIDE_DOOR + 1, 'dark_oak_fence'); s.put(x, 2, SIDE_DOOR + 1, 'dark_oak_fence')
        s.put(x, 3, SIDE_DOOR + 1, 'lantern', hanging='false', waterlogged='false')
    for x in (HX0 - 1, HX1 + 1):
        for z in (25, 26, 28):                                       # flower beds under the hall windows, between
            s.put(x, 0, z, 'coarse_dirt')                            # the chimney and the buttresses
            s.put(x, 1, z, flowers[(z * 3 + (x > CX)) % len(flowers)])
    for x in (14, 16):                                               # and either side of the back buttresses
        s.put(x, 0, HZ1 + 1, 'coarse_dirt')
        s.put(x, 1, HZ1 + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.put(CX, 0, HZ1 + 1, 'coarse_dirt'); s.put(CX, 1, HZ1 + 1, 'poppy')

    # ================================================================ level 3: the castle
    s.at(3, 'shell3')
    corners = [(WALL_X0, WALL_Z), (WALL_X1, WALL_Z), (WALL_X0, BACK_Z), (WALL_X1, BACK_Z)]

    def in_tower(x, z):
        return any(abs(x - cx) <= 2 and abs(z - cz) <= 2 for cx, cz in corners)

    def in_gatehouse(x, z):
        return 11 <= x <= 19 and 1 <= z <= 4

    # ---- curtain: the forecourt wall raised and carried round the wards to the hall, merlons on the outer line and a
    # walk on a corbelled ledge along the inside (soldiers walk at y 6), towers and gatehouse at the ends
    curtain, ledge = [], []
    for side in (-1, 1):
        def X(x):
            return x if side < 0 else W - 1 - x
        curtain += [(X(x), WALL_Z) for x in range(6, 11)] + [(X(x), BACK_Z) for x in range(6, 11)]
        curtain += [(X(WALL_X0), z) for z in range(6, 27)]
        ledge += [(X(x), WALL_Z + 1, 'north') for x in range(6, 11)] + [(X(x), BACK_Z - 1, 'south') for x in range(6, 11)]
        ledge += [(X(WALL_X0 + 1), z, 'west' if side < 0 else 'east') for z in range(6, 27)]
    for x, z in curtain:
        s.add(x, 0, z, 'cobblestone')
        s.add(x, 1, z, base_mix(x, 1, z))
        for y in range(2, 6):
            s.add(x, y, z, brick_mix(x, y, z))
        if (x + z) % 2 == 0:
            s.add(x, 6, z, brick_mix(x, 6, z))
    for x, z, f in ledge:
        s.add(x, 4, z, 'stone_brick_stairs', facing=f, **TOP)
        s.add(x, 5, z, 'stone_bricks')

    # ---- corner towers: 5 x 5, hollow, a stair winding round a middle pillar from the ground door to the wall walks
    # (y 6) and on to a lookout under a steep dark oak cone. Every opening is on the tower's inner corner.
    ring8 = [(1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0)]
    for cx, cz in corners:
        sx, sz = (1 if cx < CX else -1), (1 if cz < D // 2 else -1)     # towards the inside of the castle
        pos = [(cx + dx * sx, cz + dz * sz) for dx, dz in ring8]
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                s.add(x, 0, z, 'cobblestone')
                if abs(x - cx) < 2 and abs(z - cz) < 2:
                    continue
                for y in range(1, 12):
                    outer = (x - cx) * sx == -2 or (z - cz) * sz == -2
                    mid = x == cx or z == cz
                    if outer and mid and y in (3, 7, 10):
                        s.add(x, y, z, 'glass_pane')                  # arrow slits and lookout windows
                    elif (x - cx) in (-2, 2) and (z - cz) in (-2, 2) and y % 2 == 0:
                        s.add(x, y, z, 'cobblestone')                 # quoins
                    else:
                        s.add(x, y, z, base_mix(x, y, z) if y < 2 else brick_mix(x, y, z))
        door_x, door_z = (pos[0][0] + sx, pos[0][1]), (pos[0][0], pos[0][1] + sz)
        for y in (6, 7):                                             # openings onto both wall walks
            s.remove(*(door_x[0], y, door_x[1])); s.remove(*(door_z[0], y, door_z[1]))
        if not s.blocks[(door_x[0], 1, door_x[1])][0].endswith('_door'):   # ground door (front towers: posterns)
            s.remove(door_x[0], 1, door_x[1]); s.remove(door_x[0], 2, door_x[1])
            s.door(door_x[0], 1, door_x[1], 'east', block='spruce_door')
        for y in range(1, 12):
            s.add(cx, y, cz, 'stone_bricks')                          # the pillar
        prev = pos[0]
        for i in range(1, 13):                                       # steps 1..5, landing at y 6, steps 9..12
            p = pos[i % 8]
            y = i if i <= 5 else i - 3 if i >= 9 else None
            if y is not None:
                f = 'east' if p[0] > prev[0] else 'west' if p[0] < prev[0] else 'south' if p[1] > prev[1] else 'north'
                s.add(p[0], y, p[1], 'stone_brick_stairs', facing=f, **BOT)
            prev = p
        for k in (0, 1, 2, 6, 7):                                    # landing floor at the wall walks
            s.add(pos[k][0], 5, pos[k][1], 'spruce_planks')
        for k in (5, 6, 7, 0, 1):                                    # lookout floor
            s.add(pos[k][0], 9, pos[k][1], 'spruce_planks')
        corbel_ring(s, cx, cz, 10, 3, add=True)
        s.add(cx, cone(s, cx, cz, 11, 3, 'dark_oak_fence', tip=1, add=True) + 1, cz, 'red_banner', rotation='8')

    # ---- gatehouse: two piers flanking the passage, a guard chamber over it at wall-walk height (doors from both
    # walks), a stair up to the battlement roof
    for x in range(11, 20):
        for z in range(1, 5):
            pier = x <= 13 or x >= 17
            for y in range(0 if pier else 4, 6):
                quoin = pier and x in (11, 13, 17, 19) and z in (1, 4) and y % 2 == 0
                s.add(x, y, z, base_mix(x, y, z) if y < 2 else 'cobblestone' if quoin else brick_mix(x, y, z))
            for y in (6, 7, 8):
                if x in (11, 19) or z in (1, 4):
                    s.add(x, y, z, brick_mix(x, y, z))
            if (x, z) not in ((14, 2), (15, 2), (16, 2)):           # roof deck, open over the stair
                s.add(x, 9, z, 'stone_bricks')
    for i, x in enumerate(range(13, 17)):                            # stair up to the roof along the front
        s.add(x, 6 + i, 2, 'stone_brick_stairs', facing='east', **BOT)
    for x in (11, 19):                                               # doors from the wall walks
        for y in (6, 7):
            s.remove(x, y, 3)
    for x in (13, 17):
        s.remove(x, 7, 1); s.add(x, 7, 1, 'glass_pane')
    s.remove(15, 7, 4); s.add(15, 7, 4, 'glass_pane')
    for z in (1, 2, 3, 4):
        s.put(GATE[0], 3, z, 'stone_brick_stairs', facing='west', **TOP)
        s.put(GATE[1], 3, z, 'stone_brick_stairs', facing='east', **TOP)
    for x in range(10, 21):                                          # corbels, parapet and merlons, one block out
        for z in range(0, 6):
            if x in (10, 20) or z in (0, 5):
                f = 'south' if z == 0 else 'north' if z == 5 else 'east' if x == 10 else 'west'
                s.add(x, 9, z, 'stone_brick_stairs', facing=f, **TOP)
                s.add(x, 10, z, brick_mix(x, 10, z))
                if (x + z) % 2 == 0:
                    s.add(x, 11, z, brick_mix(x, 11, z))
    for x in (12, 18):
        s.put(x, 6, 0, 'red_wall_banner', facing='north')
        for y in (10, 11, 12):
            s.add(x, y, 3, 'dark_oak_fence')
        s.add(x, 13, 3, 'red_banner', rotation='8')
    s.put(CX, 4, 0, 'stone_brick_stairs', facing='south', **TOP)    # a hood over the arch

    # ---- lean-tos against the side curtains under the wall walk: dark oak timber, an open doorway to the ward.
    # Front pair (between the tower and the forecourt wall): storehouse (west), guardroom (east).
    # Back pair (by the hall's side doors): kitchen (west), armory (east).
    def lean_to(side, z0, z1, door):
        def X(x):
            return x if side < 0 else W - 1 - x
        f = 'west' if side < 0 else 'east'
        for z in range(z0, z1 + 1):
            edge = z in (z0, z1)
            for x in (4, 5, 6):
                if not edge and x != 6:
                    s.add(X(x), 0, z, 'spruce_planks')
                    continue
                s.add(X(x), 0, z, 'cobblestone')
                for y in (1, 2, 3):
                    if x == 6 and z == door and y < 3:
                        continue
                    post = x == 6 and edge
                    s.add(X(x), y, z, 'dark_oak_log' if post else 'dark_oak_planks', **({'axis': 'y'} if post else {}))
            if edge:
                s.add(X(5), 4, z, 'dark_oak_planks')
        for z in range(z0 - 1, z1 + 2):
            for x, y in ((7, 3), (6, 4), (5, 5)):
                s.add(X(x), y, z, 'dark_oak_stairs', facing=f, **BOT)
        s.remove(X(6), 2, door + 1); s.add(X(6), 2, door + 1, 'glass_pane')
        return X, door
    fronts = [lean_to(side, 5, 9, 6) for side in (-1, 1)]
    backs = [lean_to(side, 19, 23, SIDE_DOOR) for side in (-1, 1)]

    # ---- well (west) and forge (east) in the forecourt: log posts under small pointed roofs
    for cx, kind in ((10, 'well'), (20, 'forge')):
        cz = 6
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                if (x, z) == (cx, cz):
                    continue
                s.add(x, 0, z, 'cobblestone')
                if x != cx and z != cz:
                    for y in (1, 2, 3):
                        s.add(x, y, z, 'dark_oak_log', axis='y')
                elif kind == 'well':
                    s.add(x, 1, z, 'cobblestone')
        stair_ring(s, cx, cz, 4, 1, 'dark_oak_stairs', add=True)
        s.add(cx, 4, cz, 'dark_oak_planks')
        s.add(cx, 5, cz, 'dark_oak_slab', type='bottom', waterlogged='false')
        if kind == 'well':
            s.decorate(cx, 0, cz, 'water')
            s.add(cx, 3, cz, 'chain', axis='y', waterlogged='false')
        else:
            s.add(cx, 0, cz, 'cobblestone')
            s.add(cx, 1, cz, 'anvil', facing='east')
            s.add(cx + 1, 1, cz, 'blast_furnace', facing='east', lit='false')
            s.add(cx - 1, 1, cz, 'grindstone', face='floor', facing='west')

    # ================================================================ level 3 fittings
    s.at(3, 'flourish3')
    # gravel walks: gateway -> along the ward -> lean-to door and hall side door -> back tower door
    for side in (-1, 1):
        def X(x):
            return x if side < 0 else W - 1 - x
        for z in range(COURT_Z1, BACK_Z):
            for x in (7, 8):
                s.add(X(x), 0, z, 'gravel')
        for x in range(9, HX0):
            s.add(X(x), 0, SIDE_DOOR, 'gravel')
        s.add(X(6), 0, BACK_Z - 1, 'gravel')
    # west ward beside the keep: flower garden, two beds round a bush, a bench
    for zc in (12, 16):
        for x in range(4, 7):
            for z in range(zc - 1, zc + 2):
                s.add(x, 0, z, 'coarse_dirt')
                if (x, z) == (5, zc):
                    s.add(x, 1, z, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
                elif (x + z) % 2 == 0:
                    s.add(x, 1, z, ['poppy', 'cornflower', 'oxeye_daisy'][(zc // 4 + (x + z) // 2) % 3])
    s.add(4, 1, 14, 'spruce_stairs', facing='west', **BOT)            # bench between the beds
    # east ward beside the keep: training yard, sand-and-gravel floor, straw targets and a straw practice dummy
    for x in range(22, 27):
        for z in range(10, 19):
            s.add(x, 0, z, 'gravel' if noise(x, 0, z, 3) < 0.5 else 'coarse_dirt')
    for z in (12, 16):
        s.add(26, 1, z, 'hay_block', axis='y')
        s.add(26, 2, z, 'target')
    s.add(24, 1, 14, 'dark_oak_fence'); s.add(24, 2, 14, 'hay_block', axis='y')
    s.add(26, 1, 14, 'barrel', facing='up', open='false')             # practice weapons
    # back wards: firewood for the kitchen (west), hay for the yard (east)
    for x, z in ((4, 25), (4, 26), (5, 25), (5, 26)):
        s.add(x, 1, z, 'dark_oak_log', axis='z')
        s.add(W - 1 - x, 1, z, 'hay_block', axis='y')
    for z in (25, 26):
        s.add(4, 2, z, 'dark_oak_log', axis='z')
        s.add(W - 1 - 4, 2, z, 'hay_block', axis='y')
    # lean-to fittings (the cell inside each doorway stays free)
    (XW, dw), (XE, de) = fronts
    for z in (6, 8):                                                 # storehouse: chests and barrels
        s.add(XW(4), 1, z, 'chest', facing='west', type='single', waterlogged='false')
    s.add(XW(4), 1, 7, 'barrel', facing='up', open='false')
    s.add(XW(5), 1, 8, 'barrel', facing='up', open='false')
    for x, z in ((XE(4), 6), (XE(5), 7)):                             # guardroom: two bunks and a footlocker
        s.add(x, 1, z, 'red_bed', facing='south', part='foot', occupied='false')
        s.add(x, 1, z + 1, 'red_bed', facing='south', part='head', occupied='false')
    s.add(XE(4), 1, 8, 'chest', facing='west', type='single', waterlogged='false')
    (XW, dw), (XE, de) = backs
    s.add(XW(4), 1, 20, 'smoker', facing='east', lit='false')       # kitchen: smoker, furnace, table, stores
    s.add(XW(4), 1, 21, 'furnace', facing='west', lit='false')
    s.add(XW(4), 1, 22, 'barrel', facing='up', open='false')
    s.add(XW(5), 1, 20, 'crafting_table')
    s.add(XW(5), 1, 22, 'barrel', facing='up', open='false')
    s.add(XE(4), 1, 20, 'smithing_table')                            # armory: smithing table, weapon chests
    s.add(XE(4), 1, 21, 'chest', facing='west', type='single', waterlogged='false')
    s.add(XE(4), 1, 22, 'chest', facing='west', type='single', waterlogged='false')
    s.add(XE(5), 1, 20, 'grindstone', face='floor', facing='west')
    s.add(XE(5), 1, 22, 'barrel', facing='up', open='false')
    # gatehouse chamber: the gate watch's chest and barrel
    s.add(12, 6, 2, 'chest', facing='west', type='single', waterlogged='false')
    s.add(18, 6, 2, 'barrel', facing='up', open='false')
    s.add(15, 8, 3, 'lantern', hanging='true', waterlogged='false')
    # banners on the curtain front, torches by the gate
    for x in (7, 23):
        s.add(x, 4, WALL_Z - 1, 'red_wall_banner', facing='north')
    for x in (GATE[0] - 1, GATE[1] + 1):
        s.add(x, 2, 0, 'wall_torch', facing='north')

    # ---- level 3 polish (v6)
    curtain_set = set(curtain)
    # buttresses on the long outer faces of the curtain (and one on each front and back stretch), capped with a stair
    # (all under merlons, so the string course over them can't be stepped onto from a crenel)
    butts = [(WALL_X0 - 1, z, 'east') for z in (9, 15, 21)] + [(WALL_X1 + 1, z, 'west') for z in (9, 15, 21)]
    butts += [(x, WALL_Z - 1, 'south') for x in (9, W - 1 - 9)] + [(x, BACK_Z + 1, 'north') for x in (7, W - 1 - 7)]
    for x, z, f in butts:
        s.add(x, 0, z, 'cobblestone')
        s.add(x, 1, z, base_mix(x, 1, z))
        for y in (2, 3):
            s.add(x, y, z, brick_mix(x, y, z))
        s.add(x, 4, z, 'stone_brick_stairs', facing=f, **BOT)
    # a string course of upside-down stairs at y 5 round the outside of the towers and gatehouse, and corbels under
    # every merlon of the curtain (machicolations). Never next to a crenel: soldiers on the wall walk must not be able
    # to step out onto it.
    def merlon(x, z):
        return (x, z) in curtain_set and (x + z) % 2 == 0

    def crenel(x, z):
        return (x, z) in curtain_set and (x + z) % 2 == 1
    from collections import deque
    seen, todo = set(), deque()
    for x in range(W):
        for z in range(D):
            if (x in (0, W - 1) or z in (0, D - 1)) and (x, 5, z) not in s.blocks:
                seen.add((x, z)); todo.append((x, z))
    while todo:
        x, z = todo.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if 0 <= n[0] < W and 0 <= n[1] < D and n not in seen and (n[0], 5, n[1]) not in s.blocks:
                seen.add(n); todo.append(n)
    assert not any(6 <= x <= 24 and 6 <= z <= 26 for x, z in seen), 'string course flood leaked inside'
    for x, z in sorted(seen):
        sides = (('south', (0, 1)), ('north', (0, -1)), ('east', (1, 0)), ('west', (-1, 0)))
        if any(crenel(x + dx, z + dz) for _, (dx, dz) in sides):
            continue
        for f, (dx, dz) in sides:
            if (merlon(x + dx, z + dz) or in_tower(x + dx, z + dz) or in_gatehouse(x + dx, z + dz)) and \
                    (x + dx, 5, z + dz) in s.blocks:
                s.add(x, 5, z, 'stone_brick_stairs', facing=f, **TOP)
                break
    # red banners on the corner towers' outer faces, under the corbels; vines on their side faces
    for cx, cz in corners:
        sx, sz = (1 if cx < CX else -1), (1 if cz < D // 2 else -1)
        if sz > 0:
            s.add(cx, 9, cz - 3, 'red_wall_banner', facing='north')
        s.add(cx - 3 * sx, 9, cz, 'red_wall_banner', facing='west' if sx > 0 else 'east')
        vx = cx - 3 * sx
        for dz in (-2, -1, 1, 2):
            n = noise(vx, 0, cz + dz, 11)
            if n < 0.6:
                for y in range(9, 9 - (1 + int(n * 6)), -1):          # hanging 1..4 down from under the corbels
                    s.add(vx, y, cz + dz, 'vine', east=str(sx > 0).lower(), west=str(sx < 0).lower(),
                          north='false', south='false', up='false')
    for vx, sx in ((WALL_X0 - 1, 1), (WALL_X1 + 1, -1)):            # and low on the long curtain faces
        for z in range(7, 26):
            n = noise(vx, 1, z, 13)
            if n < 0.3 and (vx, 1, z) not in s.blocks:
                for y in range(3, 3 - (1 + int(n * 10)), -1):           # 1..3 long, never up to the corbels
                    s.add(vx, y, z, 'vine', east=str(sx > 0).lower(), west=str(sx < 0).lower(),
                          north='false', south='false', up='false')
    # lanterns: on the merlons along the side walks, under the arch of the gate, in every tower's lookout and on the
    # ward side of the wall walk's ledge
    for z in (9, 15, 21):
        for x in (WALL_X0, WALL_X1):
            s.add(x, 7, z, 'lantern', hanging='false', waterlogged='false')
    for x in (7, W - 1 - 7):
        s.add(x, 7, WALL_Z, 'lantern', hanging='false', waterlogged='false')
    s.add(CX, 3, 2, 'lantern', hanging='true', waterlogged='false')
    for cx, cz in corners:
        sx, sz = (1 if cx < CX else -1), (1 if cz < D // 2 else -1)
        dx, dz = ring8[6]
        s.add(cx + dx * sx, 11, cz + dz * sz, 'lantern', hanging='true', waterlogged='false')
    for z in (10, 14, 18):
        for x in (WALL_X0 + 1, WALL_X1 - 1):
            s.add(x, 3, z, 'lantern', hanging='true', waterlogged='false')
    # crates by the well and the forge, stores and a chopping block in the back wards
    for x in (9, 10, W - 1 - 9, W - 1 - 10):
        s.add(x, 1, 4, 'barrel', facing='up', open='false')
    for x in (10, W - 1 - 10):
        s.add(x, 2, 4, 'barrel', facing='up', open='false')
    for x in (4, 5, W - 1 - 4, W - 1 - 5):
        s.add(x, 1, 24, 'barrel', facing='up', open='false')
    s.add(6, 1, 25, 'dark_oak_log', axis='y')
    s.add(W - 1 - 6, 1, 25, 'hay_block', axis='y')
    # lean-to windows: shutters, and a flower box under the storehouse and guardroom windows
    for X, door in fronts + backs:
        f = 'east' if X(6) < CX else 'west'
        for dz in (0, 2):
            s.add(X(7), 2, door + dz, 'spruce_trapdoor', facing=f, half='bottom', open='true', powered='false',
                  waterlogged='false')
    for X, door in fronts:
        s.add(X(7), 1, door + 1, 'coarse_dirt')
        s.add(X(7), 2, door + 1, 'poppy')
    return connect(s)


CHANGES = ('For your note that some areas were unreachable and did not fit: every place can now be walked to from the '
           'gate (checked block by block). The side gardens have gateways from the forecourt and the great hall has '
           'side doors into them, so the hall is no longer reachable only through the keep. The curtain has a real '
           'wall walk on a corbelled ledge; the corner towers are hollow with a door at the bottom, a winding stair '
           'to both wall walks and a lookout at the top; the gatehouse has a guard chamber over the gate, entered '
           'from the wall walks, with a stair to its battlements. Rethought what each space is for: kitchen and '
           'armory now sit by the hall\'s side doors, storehouse and guardroom (bunks) by the gate next to the well '
           'and forge, a flower garden beside the keep on the west and a training yard with straw targets on the '
           'east. Turret and tower roofs are now steep cones (one overhang ring, narrowing upward, slab and '
           'flagpole), not stepped caps.')

STRUCTURES = [dict(path='keep', name='Keep', tier=4, zone='keep', role='keep', group='keep', terrain='skirt',
                   door='front', make=keep, max_height=20,
                   description='A castle that grows round the approved sample keep, symmetric on one centre line, '
                               'every part reachable on foot by soldiers. Level 1: the keep, 11 x 11 on a cobblestone '
                               'apron, battlements on corbels, four corner turrets under steep dark oak cones, a guard '
                               'hall, the king\'s chamber and stairs (no ladders) to the roof. Level 2: the royal '
                               'court, a 9 x 13 great hall behind the keep (side doors, two hearths, long tables, the '
                               'throne on a dais) and a walled forecourt with a gate, side gateways and posterns. '
                               'Level 3: the castle, a curtain with a wall walk round the whole site, four corner '
                               'towers with winding stairs to the walks and a lookout, a gatehouse with a guard '
                               'chamber and roof, storehouse and guardroom in the forecourt, a well and a forge, and '
                               'two side wards: flower garden and kitchen (west), training yard and armory (east). '
                               '20 tall: needs scaffold in game.',
                   changes=CHANGES)]
