"""Lumber camp, revised from Carlton's notes (revise1): a miniature sawmill instead of a log shed.

Front (z = 0) to back: a log yard with a crane, a carriage line (x = 5) running through an open-sided timber shed
where the saws cut the logs, and a plank yard behind it. The shed's lean-to roof is high on the right (+x, the side the
iso view shows), so the line and the saws are seen under it. A waterwheel in a stone race on the right turns an axle
into the shed (level 2). Fences connect (farm.Connected sets their connection properties).
"""
from .farm import Connected, SLAB, TOP_SLAB, HANG

LOG_Z = dict(axis='z')


def sawmill():
    """Sawmill.
    Level 1 (shell and fittings): a 7 x 7 open-sided shed: spruce log posts on cobblestone feet, a spruce plank floor,
    a low plank wall on the left, a log top plate along the tall right side, and a spruce slab lean-to roof rising from
    the left (3 high) to the right (6 high) with one block of overhang. The carriage line is a track of stripped spruce
    logs from the front yard through the shed to the plank yard; a log rides it into the saw (the grindstone, also the
    workstation) and a squared beam comes out behind it. Front yard: a log pile and a crane (post, jib, chain, a
    hanging log). Inside: chest and barrels. Torches on the front posts.
    Level 2: the waterwheel in a cobblestone race (water by the decoration step) with its axle into the shed and a
    chain belt down to the stonecutter, the second saw (UNKNOWN block); boards and plank stacks in the back yard, a
    sawhorse, sawdust, a lantern, a sign. Level 3: an axe in the chopping block (decoration step), saplings for
    replanting, moss on the log pile, more stacks."""
    W, D, H = 14, 13, 8
    s = Connected('lumber_camp', (W, H, D), palette='oak_a2')
    xa, xb, za, zb = 2, 8, 2, 8                    # shed posts
    line = 5                                       # carriage line (x)
    s.at(1, 'shell')
    for z in range(0, D):                          # the carriage track (dark rails)
        s.put(line, 0, z, 'dark_frame', **LOG_Z)
    for x in range(xa + 1, xb):                    # plank floor
        for z in range(za, zb + 1):
            if x != line:
                s.put(x, 0, z, 'plank2')
    for z in (za, (za + zb) // 2, zb):             # low left side: posts 2 high, a plank wall between them
        s.put(xa, 0, z, 'base')
        s.put(xa, 1, z, 'shed_post', axis='y'); s.put(xa, 2, z, 'shed_post', axis='y')
    for z in range(za + 1, zb):
        if z != (za + zb) // 2:
            s.put(xa, 0, z, 'plank2'); s.put(xa, 1, z, 'plank2'); s.put(xa, 2, z, 'plank2')
    for z in (za, zb):                             # tall right side: corner posts only (open), a top plate
        s.put(xb, 0, z, 'base')
        for y in range(1, 5):
            s.put(xb, y, z, 'shed_post', axis='y')
    for z in range(za, zb + 1):
        s.put(xb, 5, z, 'frame', axis='z')
    for z in range(za + 1, zb):
        s.put(xb, 0, z, 'plank2')
    for x in range(xa - 1, xb + 2):                # lean-to roof: half a block up per row, overhang all round
        k = x - (xa - 1)
        y, half = 3 + k // 2, ('top' if k % 2 else 'bottom')
        for z in range(za - 1, zb + 2):
            over_frame = x in (xa, xb) and za <= z <= zb or z in (za, zb) and xa <= x <= xb
            s.put(x, y, z, 'roof_slab', type='double' if half == 'top' and over_frame else half, waterlogged='false')
        if xa < x < xb:                            # gable ends: a beam over the line opening, planks up to the roof
            for z in (za, zb):
                s.put(x, 3, z, 'frame', axis='x')
                for yy in range(4, y):
                    s.put(x, yy, z, 'plank2')
    s.at(1, 'fittings')
    s.put(line, 1, 4, 'grindstone', face='floor', facing='north')     # the saw (and the workstation)
    for z in (0, 1, 2, 3):                                           # a log riding the carriage into the saw
        s.put(line, 1, z, 'frame', **LOG_Z)
    s.put(line, 1, 5, 'stripped', **LOG_Z)                           # the squared beam coming out
    s.put(xa + 1, 1, za + 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(xa + 1, 1, za + 2, 'barrel', facing='up', open='false')
    s.put(xa + 1, 1, zb - 1, 'barrel', facing='up', open='false')
    for x in range(1, 4):                                            # log pile in the front yard
        for z in (0, 1):
            s.put(x, 0, z, 'frame', **LOG_Z)
        s.put(x, 1, 0, 'frame', **LOG_Z)
    s.put(2, 1, 1, 'frame', **LOG_Z); s.put(2, 2, 0, 'frame', **LOG_Z)
    for y in range(0, 7):                                            # crane: post at the yard corner, jib over the pile
        s.put(0, y, 0, 'shed_post', axis='y')
    for x in range(1, 5):
        s.put(x, 6, 0, 'shed_post', axis='x')
    s.put(1, 5, 0, 'spruce_stairs', facing='west', half='top', shape='straight', waterlogged='false')   # brace
    s.put(3, 5, 0, 'chain', axis='y'); s.put(3, 4, 0, 'chain', axis='y')
    s.put(3, 3, 0, 'frame', axis='x')                                # the log on the hook
    s.put(xb - 1, 2, za, 'wall_torch', facing='west')                # torches on the front posts
    s.put(xa + 1, 2, za, 'wall_torch', facing='east')

    s.at(2, 'flourish2')
    wx, wy, wz = 12, 3, 7                                            # waterwheel in a stone race on the right
    for z in range(wz - 3, wz + 4):
        s.put(wx - 1, 0, z, 'base'); s.put(wx + 1, 0, z, 'base')
    s.put(wx, 0, wz - 3, 'base'); s.put(wx, 0, wz + 3, 'base')
    for z in range(wz - 2, wz + 3):
        s.decorate(wx, 0, z, 'water', note='mill race under the waterwheel')
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dz) == 2 and abs(dy) == 2:
                continue
            if abs(dz) == 2 or abs(dy) == 2:
                s.put(wx, wy + dy, wz + dz, 'plank2')                     # rim
            elif dz == 0 and dy == 0:
                s.put(wx, wy, wz, 'spruce_log', axis='x')                # hub
            elif dz == 0 or dy == 0:
                s.put(wx, wy + dy, wz + dz, 'stripped_spruce_log', axis='y' if dz == 0 else 'z')   # spokes
    for x in range(line + 1, wx):                                    # axle into the shed, chain belt to the saw
        s.put(x, wy, wz, 'stripped_spruce_log', axis='x')
    s.put(line, 3, wz, 'chain', axis='y'); s.put(line, 2, wz, 'chain', axis='y')
    s.put(line, 1, wz, 'stonecutter', facing='north')               # the second saw, driven by the wheel
    for z in (6, 8, 9):                                              # boards on the track
        s.put(line, 1, z, 'spruce_slab', **SLAB)
    for x, z in ((1, 10), (2, 10), (3, 10), (1, 11), (2, 11)):       # plank stacks in the back yard
        s.put(x, 0, z, 'oak_planks'); s.put(x, 1, z, 'oak_slab', **SLAB)
    for x, z in ((7, 10), (8, 10), (7, 11)):
        s.put(x, 0, z, 'plank2'); s.put(x, 1, z, 'spruce_slab', **SLAB)
    s.put(xb + 1, 0, 0, 'fence'); s.put(xb + 3, 0, 0, 'fence')       # sawhorse in the front yard
    for x in (xb + 1, xb + 2, xb + 3):
        s.put(x, 1, 0, 'oak_slab', **SLAB)
    s.put(xb, 4, 5, 'lantern', **HANG)                               # lantern under the high eave's plate
    for x, z in ((6, 3), (6, 4), (7, 4), (4, 5)):                    # sawdust by the saws
        s.put(x, 1, z, 'brown_carpet')
    s.put(xa + 1, 2, za + 2, 'barrel', facing='up', open='false')
    s.put(xb + 1, 2, za, 'oak_wall_sign', facing='east', waterlogged='false')
    s.decorate(xb + 1, 2, za, 'sign_text', lines=['', 'Sawmill', '', ''])
    s.put(4, 5, 0, 'lantern', **HANG)                                # lantern on the crane jib

    s.at(3, 'flourish3')
    s.put(xb + 2, 0, 2, 'stripped', axis='y')                        # chopping block with an axe in it
    s.decorate(xb + 2, 1, 2, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    for z in (11, 12):                                               # saplings for replanting, back right
        for x in (10, 11):
            s.put(x, 0, z, 'dirt'); s.put(x, 1, z, 'oak_sapling', stage='0')
    for x, y, z in ((1, 2, 0), (2, 2, 1), (1, 1, 1), (3, 1, 1)):     # moss on the log pile
        s.put(x, y, z, 'moss_carpet')
    s.put(7, 2, 10, 'spruce_slab', **SLAB)
    s.put(8, 1, 11, 'plank2'); s.put(8, 0, 11, 'plank2')
    s.put(3, 0, 11, 'oak_planks'); s.put(3, 1, 11, 'oak_slab', **SLAB)
    return s


STRUCTURES = [dict(path='lumber_camp', name='Lumber camp (sawmill)', tier=1, zone='outer', role='resource',
                   group='lumber_camp', terrain='flexible', door=None, make=sawmill,
                   description='A miniature sawmill: logs from the front yard (log pile, crane with a hanging log) ride '
                               'a carriage line of stripped logs into an open-sided timber shed under a lean-to roof, '
                               'through the saw (grindstone, the workstation; a stonecutter joins at L2) and out to '
                               'the plank yard behind. L2 adds a waterwheel in a stone race driving the saw, plank '
                               'stacks, sawhorse, sawdust, lanterns, a sign; L3 an axe in the chopping block, '
                               'saplings, moss.',
                   changes='Rebuilt as a proper sawmill you can read at a glance: an open timber shed with the saw on '
                           'a log-carriage line, a log pile and crane in front, plank stacks behind, and from L2 a '
                           'waterwheel driving the saw.')]
