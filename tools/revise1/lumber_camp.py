"""Lumber camp, revised from Carlton's notes (revise1): a miniature sawmill instead of a log shed.

A long open mill hall along x with the carriage line (z = 5) running through it end to end. The saw bench is the
centrepiece in the middle of the hall: a sash-saw frame of dark oak over the line, the blade (a chain) cutting the log
on the carriage. The log yard (piles and a pulley post) is at the right end (+x, the end the iso view shows), the
plank yard at the left end. Fences connect (farm.Connected sets their connection properties).
"""
from .farm import Connected, STAIR, TOP_STAIR, SLAB, TOP_SLAB, LANTERN, HANG, SHUT

LOG_X = dict(axis='x')


def sawmill():
    """Sawmill.
    Level 1 (shell and fittings): a 13 x 5 open mill hall: dark oak log posts on cobblestone feet, dark oak plates and
    tie beams, a spruce plank floor, a spruce gable roof with dark oak eaves and gable trim and one block of overhang
    all round, plank gable triangles above the tie beams (the ends stay open under them). The carriage line (stripped
    spruce logs) runs through the hall from the log yard to the plank yard. Saw bench in the middle: a sash-saw frame
    over the line with a chain blade cutting a log on the carriage; the grindstone (workstation) beside it. Log yard:
    two log piles and a pulley post with a hanging log; plank yard: plank and board stacks. Chest, barrels, torches.
    Level 2: a waterwheel in a stone race behind the hall (water by the decoration step) with its axle to the saw
    frame, the stonecutter as a second saw (UNKNOWN block), a sawhorse, a hand cart of boards, lanterns, sawdust, a
    sign board. Level 3: a plank drying rack in front, more logs in the yard, saplings for replanting, moss, an axe in
    the chopping block (decoration step)."""
    W, D, H = 21, 12, 9
    s = Connected('lumber_camp', (W, H, D), palette='oak_a2')
    xa, xb, za, zb = 4, 16, 3, 7                   # hall posts
    posts_x = (4, 8, 12, 16)
    line, saw = 5, 10                              # carriage line (z), saw (x)
    s.at(1, 'shell')
    for x in range(0, W):                          # the carriage track
        s.put(x, 0, line, 'stripped_spruce_log', **LOG_X)
    for x in range(xa, xb + 1):                    # floor, cobblestone feet under the posts
        for z in range(za, zb + 1):
            if z == line:
                continue
            s.put(x, 0, z, 'base' if x in posts_x and z in (za, zb) else 'plank2')
    for x in posts_x:
        for z in (za, zb):
            for y in range(1, 5):
                s.put(x, y, z, 'dark_frame', axis='y')
        for z in range(za + 1, zb):                # tie beams across the hall
            s.put(x, 5, z, 'dark_frame', axis='z')
    for x in range(xa, xb + 1):                    # plates along both sides
        s.put(x, 5, za, 'dark_frame', **LOG_X); s.put(x, 5, zb, 'dark_frame', **LOG_X)
    rows = {za - 1: 5, za: 6, za + 1: 7, zb - 1: 7, zb: 6, zb + 1: 5}
    for z, y in rows.items():                      # gable roof, ridge along x, dark oak eaves and gable ends
        for x in range(xa - 1, xb + 2):
            dark = z in (za - 1, zb + 1) or x in (xa - 1, xb + 1)
            s.put(x, y, z, 'dark_stairs' if dark else 'oak_stairs', facing='south' if z < line else 'north', **STAIR)
    for x in range(xa - 1, xb + 2):
        s.put(x, 8, line, 'roof_slab', **SLAB)                       # ridge (spruce: a third tone)
    for x in (xa, xb):                                               # gable triangles above the tie beams
        for z in range(za + 1, zb):
            s.put(x, 6, z, 'plank2')
        s.put(x, 7, line, 'plank2')
    for z, y in rows.items():                                        # trim under the gable overhangs
        if y > 5:
            for x in (xa - 1, xb + 1):
                s.put(x, y - 1, z, 'dark_stairs', facing='south' if z < line else 'north', **TOP_STAIR)
    # the saw bench: a sash-saw frame over the line, the chain blade cutting the log on the carriage
    for z in (line - 1, line + 1):
        s.put(saw, 0, z, 'base')
        for y in (1, 2, 3, 4):
            s.put(saw, y, z, 'stripped', axis='y')                   # light stripped oak against the dark hall
    s.put(saw, 5, line, 'stripped', axis='z')
    s.at(1, 'fittings')
    s.put(saw, 1, line, 'grindstone', face='floor', facing='east')   # the saw blade (and the workstation)
    for y in (2, 3, 4):
        s.put(saw, y, line, 'chain', axis='y')                       # the drive belt from the beam
    for x in range(6, saw):
        s.put(x, 1, line, 'frame', **LOG_X)                          # the log riding into the saw
    for x in (saw + 1, saw + 2):
        s.put(x, 1, line, 'stripped', **LOG_X)                       # the squared beam coming out
    for x in (saw + 3, saw + 4):
        s.put(x, 1, line, 'spruce_slab', **SLAB)                     # boards
    s.put(xa + 1, 1, zb - 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(xa + 1, 1, za + 1, 'barrel', facing='up', open='false'); s.put(xa + 2, 1, za + 1, 'barrel', facing='up', open='false')
    for x, z, f in ((saw - 1, line - 1, 'west'), (saw + 1, line - 1, 'east')):
        s.put(x, 3, z, 'wall_torch', facing=f)                       # torches on the saw frame
    # log yard (right end): two log piles beside the line, a pulley post with a hanging log over the carriage
    for z0, z1 in ((1, 3), (7, 9)):
        for x in (18, 19, 20):
            for z in range(z0, z1 + 1):
                s.put(x, 0, z, 'frame', **LOG_X)
            for z in range(z0 + (1 if z0 == 1 else 0), z1 + (0 if z0 == 1 else 0)):
                s.put(x, 1, z, 'frame', **LOG_X)
        s.put(19, 2, 2 if z0 == 1 else 8, 'frame', **LOG_X)
    for y in range(0, 7):
        s.put(19, y, 10, 'shed_post', axis='y')
    for z in range(line, 10):
        s.put(19, 7, z, 'shed_post', axis='z')
    s.put(19, 6, 9, 'spruce_stairs', facing='south', half='top', shape='straight', waterlogged='false')   # brace
    s.put(19, 6, line, 'chain', axis='y'); s.put(19, 5, line, 'chain', axis='y')
    s.put(19, 4, line, 'frame', **LOG_X)                             # the log on the hook
    # plank yard (left end): plank and board stacks beside the line
    for x in (0, 1, 2):
        for z in (2, 3):
            s.put(x, 0, z, 'oak_planks'); s.put(x, 1, z, 'oak_planks' if x < 2 else 'oak_slab',
                                                 **({} if x < 2 else SLAB))
        for z in (7, 8):
            s.put(x, 0, z, 'plank2'); s.put(x, 1, z, 'spruce_slab', **SLAB)
    s.put(0, 2, 2, 'oak_slab', **SLAB)

    s.at(2, 'flourish2')
    wy, wz = 3, 10                                                   # waterwheel behind the hall, axle to the saw
    for x in range(saw - 3, saw + 4):
        s.put(x, 0, wz - 1, 'base'); s.put(x, 0, wz + 1, 'base')
    s.put(saw - 3, 0, wz, 'base'); s.put(saw + 3, 0, wz, 'base')
    for x in range(saw - 2, saw + 3):
        s.decorate(x, 0, wz, 'water', note='mill race under the waterwheel')
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) == 2 and abs(dy) == 2:
                continue
            if abs(dx) == 2 or abs(dy) == 2:
                s.put(saw + dx, wy + dy, wz, 'plank2')               # rim
            elif dx == 0 and dy == 0:
                s.put(saw, wy, wz, 'spruce_log', axis='z')           # hub
            elif dx == 0 or dy == 0:
                s.put(saw + dx, wy + dy, wz, 'stripped_spruce_log', axis='y' if dx == 0 else 'x')   # spokes
    for z in (zb + 1, zb + 2):
        s.put(saw, wy, z, 'stripped_spruce_log', axis='z')          # axle
    s.put(saw + 2, 1, line + 1, 'stonecutter', facing='east')       # the second saw, trimming the beams
    for x in (13, 15):                                               # sawhorse in the front aisle
        s.put(x, 0, 1, 'fence')
    for x in (13, 14, 15):
        s.put(x, 1, 1, 'oak_slab', **SLAB)
    for x in (6, 7):                                                 # a hand cart of boards in front
        s.put(x, 0, 1, 'spruce_slab', **TOP_SLAB)
        s.put(x, 1, 1, 'spruce_slab', **SLAB)
        s.put(x, 0, 0, 'oak_trapdoor', facing='north', **SHUT)
    s.put(8, 0, 1, 'fence')                                          # handle
    for x in (8, 12):                                                # lanterns under the tie beams
        s.put(x, 4, line, 'lantern', **HANG)
    for x, z in ((11, 4), (12, 6), (13, 4), (9, 4)):                 # sawdust round the saws
        s.add(x, 1, z, 'brown_carpet')
    s.put(12, 3, za - 1, 'oak_wall_sign', facing='north', waterlogged='false')   # sign board on the front post
    s.decorate(12, 3, za - 1, 'sign_text', lines=['', 'Sawmill', '', ''])
    s.put(19, 0, line - 1, 'stripped', axis='y')                     # chopping block in the log yard

    s.at(3, 'flourish3')
    for x in (1, 4):                                                 # plank drying rack in front (left)
        for y in (0, 1, 2):
            s.put(x, y, 0, 'fence')
    for x in range(1, 5):
        for y in (1, 2):
            s.add(x, y, 0, 'oak_slab', type='top', waterlogged='false')
    for x in (2, 3):
        s.put(x, 0, 0, 'oak_slab', **TOP_SLAB)
    s.decorate(19, 1, line - 1, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    for z in (0, 10, 11):                                            # saplings for replanting by the yard
        s.put(20, 0, z, 'dirt'); s.put(20, 1, z, 'oak_sapling', stage='0')
    for x, z in ((17, 0), (18, 0)):                                  # more logs waiting
        s.put(x, 0, z, 'frame', **LOG_X)
    for x, z in ((18, 3), (20, 2), (18, 7), (20, 8)):                # moss on the piles
        s.add(x, 2, z, 'moss_carpet')
    s.put(xb + 1, 0, za - 1, 'barrel', facing='up', open='false'); s.put(xb + 1, 1, za - 1, 'lantern', **LANTERN)
    s.put(xa - 1, 0, za - 1, 'barrel', facing='up', open='false'); s.put(xa - 1, 1, za - 1, 'lantern', **LANTERN)
    return s


STRUCTURES = [dict(path='lumber_camp', name='Lumber camp (sawmill)', tier=1, zone='outer', role='resource',
                   group='lumber_camp', terrain='flexible', door=None, make=sawmill,
                   description='A miniature sawmill: a long open mill hall (dark oak frame on cobblestone feet, spruce '
                               'roof with dark oak eaves and trim) with the saw bench in the middle, a sash-saw frame '
                               'whose chain blade cuts the log on the carriage line; the grindstone (workstation) '
                               'beside it. Log yard with piles and a pulley at the right end, plank yard at the left. '
                               'L2 adds a waterwheel behind the hall driving the saw, a stonecutter, sawhorse, cart, '
                               'lanterns, sign; L3 a drying rack, more logs, saplings, moss.',
                   changes='Rebuilt as a long open mill hall with the saw bench as the centrepiece (a sash-saw frame '
                           'over the log line), a log yard on one end and plank stacks on the other, a two-tone roof '
                           'with overhang and trim on a dark frame and cobblestone feet; each level adds something '
                           'visible (L2 waterwheel, second saw, cart; L3 drying rack, saplings, more logs).')]
