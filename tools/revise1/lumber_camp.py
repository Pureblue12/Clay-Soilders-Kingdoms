"""Lumber camp, revised from Carlton's notes (revise1): a miniature sawmill instead of a log shed.

v4 (note on v3: "scale is off, needs to be slightly smaller, remember these are small guys; detail is good but needs
more to differentiate between them all"): the footprint is about a third smaller (16 x 11 instead of 21 x 12, a
9-long hall instead of 13, 3 high under the plates instead of 4), and each level adds its own big thing:
L1 the mill shed and the saw, L2 the waterwheel and the log yard, L3 the crane, the drying rack and the sign.

A short open mill hall along x with the carriage line (z = 4) running through it. The saw bench is the centrepiece in
the middle of the hall: a sash-saw frame of stripped oak over the line, the blade (a grindstone) cutting the log on the
carriage. The waterwheel is on the right gable end (+x, the end the iso view shows), the log yard at the left end, the
plank stacks in front. Fences connect (farm.Connected sets their connection properties).
"""
from .farm import Connected, STAIR, TOP_STAIR, SLAB, TOP_SLAB, LANTERN, HANG, SHUT

LOG_X = dict(axis='x')
LOG_Z = dict(axis='z')


def sawmill():
    """Sawmill.
    Level 1 (shell and fittings): a 9 x 5 open mill shed: dark oak log posts on cobblestone feet, dark oak plates and
    tie beams, a spruce plank floor, an oak gable roof with dark oak eaves and gable trim and one block of overhang all
    round, plank gable triangles. The carriage line (stripped spruce logs) runs through the shed. Saw bench in the
    middle: a sash-saw frame (stripped oak) over the line with a chain belt down to the blade (the grindstone, the
    workstation), a log riding in and a squared beam and boards coming out. Chest, barrels, torches, a chopping block,
    a short woodpile and plank stacks in front.
    Level 2: a waterwheel on the right gable end in a stone race (water by the decoration step), its axle into the
    shed; the log yard at the left end (two log piles); the stonecutter as a second saw (UNKNOWN block), lanterns,
    sawdust, a sawhorse.
    Level 3: a crane over the log pile (spruce post, jib, chain, a hanging log), a plank drying rack in front, a sign on
    a post, lantern posts, saplings for replanting, moss on the piles, an axe in the chopping block
    (decoration step)."""
    W, D, H = 16, 11, 8
    s = Connected('lumber_camp', (W, H, D), palette='oak_a2')
    xa, xb, za, zb = 3, 11, 2, 6                   # shed posts
    posts_x = (3, 7, 11)
    line, saw = 4, 7                               # carriage line (z), saw (x: the middle tie beam is the saw head)
    s.at(1, 'shell')
    for x in range(0, xb + 2):                     # the carriage track, from the log yard to the wheel race
        s.put(x, 0, line, 'stripped_spruce_log', **LOG_X)
    for x in range(xa, xb + 1):                    # floor, cobblestone feet under the posts
        for z in range(za, zb + 1):
            if z == line:
                continue
            s.put(x, 0, z, 'base' if x in posts_x and z in (za, zb) else 'plank2')
    for x in posts_x:
        for z in (za, zb):
            for y in range(1, 4):
                s.put(x, y, z, 'dark_frame' if x != saw else 'stripped', axis='y')
        for z in range(za + 1, zb):                # tie beams across the shed (the middle one is the saw head)
            s.put(x, 4, z, 'dark_frame' if x != saw else 'stripped', axis='z')
    for x in range(xa, xb + 1):                    # plates along both sides
        if x != saw:
            s.put(x, 4, za, 'dark_frame', **LOG_X); s.put(x, 4, zb, 'dark_frame', **LOG_X)
    s.put(saw, 4, za, 'dark_frame', **LOG_X); s.put(saw, 4, zb, 'dark_frame', **LOG_X)
    rows = {za - 1: 4, za: 5, za + 1: 6, zb - 1: 6, zb: 5, zb + 1: 4}
    for z, y in rows.items():                      # gable roof, ridge along x, dark oak eaves and gable ends
        for x in range(xa - 1, xb + 2):
            dark = z in (za - 1, zb + 1) or x in (xa - 1, xb + 1)
            s.put(x, y, z, 'dark_stairs' if dark else 'oak_stairs', facing='south' if z < line else 'north', **STAIR)
    for x in range(xa - 1, xb + 2):
        s.put(x, 7, line, 'roof_slab', **SLAB)                       # ridge (spruce: a third tone)
    for x in (xa, xb):                                               # gable triangles above the tie beams
        for z in range(za + 1, zb):
            s.put(x, 5, z, 'plank2')
        s.put(x, 6, line, 'plank2')
    for z, y in rows.items():                                        # trim under the gable overhangs
        if y > 4:
            for x in (xa - 1, xb + 1):
                s.put(x, y - 1, z, 'dark_stairs', facing='south' if z < line else 'north', **TOP_STAIR)
    # the saw bench: a sash-saw frame of stripped oak (the middle posts and tie beam) over the line
    for z in (line - 1, line + 1):
        s.put(saw, 0, z, 'base')
        for y in (1, 2, 3):
            s.put(saw, y, z, 'stripped', axis='y')
    s.at(1, 'fittings')
    s.put(saw, 1, line, 'grindstone', face='floor', facing='east')   # the saw blade (and the workstation)
    for y in (2, 3):
        s.put(saw, y, line, 'chain', axis='y')                       # the drive belt from the saw head
    for x in (4, 5, 6):
        s.put(x, 1, line, 'frame', **LOG_X)                          # the log riding into the saw
    s.put(saw + 1, 1, line, 'stripped', **LOG_X)                     # the squared beam coming out
    for x in (saw + 2, saw + 3):
        s.put(x, 1, line, 'spruce_slab', **SLAB)                     # boards
    s.put(xb - 1, 1, zb - 1, 'chest', facing='west', type='single', waterlogged='false')
    s.put(xa + 1, 1, za + 1, 'barrel', facing='up', open='false'); s.put(xa + 1, 1, zb - 1, 'barrel', facing='up', open='false')
    for x, z, f in ((saw - 1, line - 1, 'west'), (saw + 1, line - 1, 'east')):
        s.put(x, 2, z, 'wall_torch', facing=f)                       # torches on the saw frame
    for x in (8, 9, 10):                                             # plank stacks in front (the mill's output)
        s.put(x, 0, 0, 'oak_planks' if x < 10 else 'plank2')
        s.put(x, 1, 0, 'oak_slab' if x < 10 else 'spruce_slab', **SLAB)
    s.put(8, 0, 1, 'plank2'); s.put(9, 0, 1, 'spruce_slab', **SLAB)
    s.put(1, 0, 7, 'frame', **LOG_Z); s.put(2, 0, 7, 'frame', **LOG_Z)   # a short woodpile by the track
    s.put(1, 1, 7, 'frame', **LOG_Z)
    s.put(xa - 1, 0, za - 1, 'stripped', axis='y')                   # chopping block at the front corner

    s.at(2, 'flourish2')
    # the waterwheel on the right gable end: a stone race (water by the decoration step), the wheel in the race, the
    # axle into the shed
    wx, wy, wz = 14, 3, line
    for z in range(wz - 3, wz + 4):
        s.put(wx - 1, 0, z, 'base'); s.put(wx + 1, 0, z, 'moss' if z % 3 == 0 else 'base')
    s.put(wx, 0, wz - 3, 'base'); s.put(wx, 0, wz + 3, 'base')
    for z in range(wz - 2, wz + 3):
        s.decorate(wx, 0, z, 'water', note='mill race under the waterwheel')
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dz) == 2 and abs(dy) == 2:
                continue
            if abs(dz) == 2 or abs(dy) == 2:
                s.put(wx, wy + dy, wz + dz, 'dark')                  # rim
            elif dz == 0 and dy == 0:
                s.put(wx, wy, wz, 'spruce_log', **LOG_X)             # hub
            elif dz == 0 or dy == 0:
                s.put(wx, wy + dy, wz + dz, 'stripped_spruce_log', axis='y' if dz == 0 else 'z')   # spokes
    for x in (xb + 1, xb + 2):
        s.put(x, wy, wz, 'stripped_spruce_log', **LOG_X)             # axle into the shed
    # the log yard at the left end: two piles beside the track
    for z0, z1 in ((0, 2), (6, 8)):
        for x in (0, 1, 2):
            for z in range(z0, z1 + 1):
                s.add(x, 0, z, 'frame', **LOG_Z)
            for z in range(z0 + 1, z1 + 1) if z0 == 0 else range(z0, z1):
                s.add(x, 1, z, 'frame', **LOG_Z)
        s.add(1, 2, 1 if z0 == 0 else 7, 'frame', **LOG_Z)
    s.put(saw + 2, 1, line + 1, 'stonecutter', facing='east')       # the second saw, trimming the beams
    for x in (xa, xb):                                               # lanterns hung from the end tie beams
        s.put(x, 3, line, 'lantern', **HANG)
    for x, z in ((8, 3), (6, 5), (9, 3), (6, 3)):                    # sawdust round the saws
        s.add(x, 1, z, 'brown_carpet')
    for x in (4, 6):                                                 # sawhorse in front of the shed
        s.put(x, 0, 0, 'fence')
    for x in (4, 5, 6):
        s.put(x, 1, 0, 'oak_slab', **SLAB)

    s.at(3, 'flourish3')
    # a crane over the front log pile: a spruce post beside the track, a jib out over the pile, a log on the hook
    for y in range(0, 6):
        s.put(0, y, 3, 'shed_post', axis='y')
    for z in range(0, 4):
        s.put(0, 6, z, 'shed_post', **LOG_Z)
    s.put(0, 5, 2, 'spruce_stairs', facing='south', **TOP_STAIR)    # brace
    s.put(0, 5, 0, 'chain', axis='y'); s.put(0, 4, 0, 'chain', axis='y')
    s.put(0, 3, 0, 'frame', **LOG_X)                                 # the log on the hook
    for z in (9, 10):                                                # logs waiting behind the yard
        s.add(0, 0, z, 'frame', **LOG_X); s.add(1, 0, z, 'frame', **LOG_X); s.add(2, 0, z, 'frame', **LOG_X)
    s.add(1, 1, 10, 'frame', **LOG_X)
    # a plank drying rack in front of the wheel: two fence posts, boards laid on three rails
    for x in (12, 15):
        for y in (0, 1, 2):
            s.put(x, y, 0, 'fence')
    for x in (13, 14):
        for y in (0, 1, 2):
            s.put(x, y, 0, 'oak_slab' if y else 'spruce_slab', type='top', waterlogged='false')
    # the sign on a post by the plank stacks
    s.put(11, 0, 0, 'fence'); s.put(11, 1, 0, 'fence')
    s.put(11, 2, 0, 'oak_sign', rotation='8', waterlogged='false')
    s.decorate(11, 2, 0, 'sign_text', lines=['', 'Sawmill', '', ''])
    s.decorate(xa - 1, 1, za - 1, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    for x in (13, 14, 15):                                           # saplings for replanting beside the race
        s.put(x, 0, 9, 'dirt'); s.put(x, 1, 9, 'oak_sapling', stage='0')
    s.put(15, 0, 10, 'dirt'); s.put(15, 1, 10, 'oak_sapling', stage='0')
    for x, z in ((0, 1), (2, 2), (0, 6), (2, 7)):                    # moss on the piles
        s.add(x, 2, z, 'moss_carpet')
    for x, z in ((xb + 1, za - 1), (xb + 1, zb + 1)):                # lantern posts at the shed corners
        s.put(x, 0, z, 'fence'); s.put(x, 1, z, 'lantern', **LANTERN)
    return s


STRUCTURES = [dict(path='lumber_camp', name='Lumber camp (sawmill)', tier=1, zone='outer', role='resource',
                   group='lumber_camp', terrain='flexible', door=None, make=sawmill,
                   description='A miniature sawmill (16 x 11): a short open mill shed (dark oak frame on cobblestone '
                               'feet, oak roof with dark oak eaves and trim) with the saw bench in the middle, a '
                               'sash-saw frame whose belt drives the blade on the carriage line; the grindstone '
                               '(workstation). L1 the shed, the saw and plank stacks; L2 a waterwheel on the end and '
                               'the log yard; L3 a crane over the logs, a drying rack, a sign and saplings.',
                   changes='Fix: the two lanterns in the shed hung from thin air (they would drop in game); they now hang '
                           'from the end tie beams. Nothing else changed.')]
