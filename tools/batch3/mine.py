"""Mine, stage 1 (tier 3, look A): a timber-framed adit into a built spoil mound."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
LEAVES = dict(persistent='true', distance='1', waterlogged='false')

W, D = 11, 11
PX0, PX1 = 4, 6               # passage (open, 3 wide, 2 high: y 0..1)
FX0, FX1 = 3, 7               # frame posts
ZF = 3                        # the mound's front face (portal)
ZE = 8                        # the passage's end: where the miners dig down


def height(x, z):
    """Mound height (blocks) of column (x, z): highest over the adit, falling away to the sides and the back, with a
    fixed-noise wobble on the slopes so it reads as a heap of rock, not a step pyramid."""
    if z < ZF or z > 10 or not 1 <= x <= 9:
        return 0
    hz = {3: 3, 4: 4, 5: 4, 6: 4, 7: 4, 8: 4, 9: 3, 10: 2}[z]
    d = min(x - 1, 9 - x) + 1
    h = min(hz, d)
    if FX0 <= x <= FX1 and z <= ZE:
        return max(h, 3)                             # always over the adit
    n = noise(x, 0, z, 9)
    if h >= 2 and n < 0.3:
        h -= 1
    elif h < hz and n > 0.8:
        h += 1
    return h


def mine():
    """Mine, stage 1 (the headframe and the winch house are stages 2 and 3, later structures). Builders never dig
    (D60: like the keep's dungeon, everything is built above ground): the builders raise a spoil mound of cobblestone,
    gravel and dirt (x 1..9, z 3..10, up to 4 high) around a timber-framed adit. The adit is 3 wide and 2 high, its
    floor is the ground itself, with oak log frames every two blocks and a spruce plank ceiling; it runs 6 blocks in
    to its end (z 8), where the miners (the dig work goal, D6) start digging down. In a hillside the mound simply
    meets the hill.

    Level 1: the mound with cobblestone wing walls at the portal, the adit and its frames, a second frame out front
    (the entrance), the miners' chest and a barrel of pit props inside the mouth, a rack of torches, a log pile.
    Level 2: the working mine: a rail track out of the adit (rails not builder-tested yet), a pent hood over the
    entrance, lanterns on chains under the frames, a tool rack, an ore bin and a spoil heap of gravel.
    Level 3: the mine's life: a sign (text by the decoration step, on the hood), banners, lantern posts on the path, moss, flowers
    and bushes on the mound, a minecart of ore and picks on the rack (decoration step)."""
    s = Structure('mine', (W, 7, D), palette='oak_a2')
    mid = (PX0 + PX1) // 2
    s.at(1, 'shell')
    for x in range(W):
        for z in range(D):
            h = height(x, z)
            for y in range(h):
                if PX0 <= x <= PX1 and ZF <= z <= ZE and y <= 2:
                    continue                         # the adit (its ceiling is added below)
                if x in (FX0, FX1) and ZF <= z <= ZE and y <= 1:
                    continue                         # frame posts / side walls added below
                top = y == h - 1
                n = noise(x, y, z, 21)
                if top:
                    b = 'coarse_dirt' if n < 0.3 else 'cobblestone' if n < 0.55 else 'mossy_cobblestone' \
                        if n < 0.7 else 'gravel' if n < 0.8 else 'dirt'
                else:
                    b = 'gravel' if n < 0.25 else 'mossy_cobblestone' if n < 0.4 else 'cobblestone'
                put_id(s, x, y, z, b)
    for z in range(ZF, ZE + 1):                      # adit: frames every two blocks, cobblestone walls between
        frame = (z - ZF) % 2 == 0
        for x in (FX0, FX1):
            for y in (0, 1):
                put_id(s, x, y, z, 'oak_log' if frame else 'cobblestone', **({'axis': 'y'} if frame else {}))
        for x in range(PX0, PX1 + 1):
            put_id(s, x, 2, z, 'oak_log' if frame else 'spruce_planks', **({'axis': 'x'} if frame else {}))
        if frame:
            for x in (FX0, FX1):
                put_id(s, x, 2, z, 'oak_log', axis='x')
    for x in range(PX0, PX1 + 1):                    # mound over the ceiling
        for z in range(ZF, ZE + 1):
            for y in range(3, height(x, z)):
                n = noise(x, y, z, 21)
                top = y == height(x, z) - 1
                put_id(s, x, y, z, ('coarse_dirt' if n < 0.3 else 'cobblestone' if n < 0.6 else 'mossy_cobblestone')
                       if top else 'cobblestone')
    for x in range(PX0, PX1 + 1):                    # ceiling between the entrance frame and the portal
        put_id(s, x, 2, 2, 'spruce_planks')
    for x in (1, 2, 8, 9):                           # wing walls of dressed cobblestone either side of the portal
        for y in range(height(x, ZF)):
            s.blocks.pop((x, y, ZF), None); s.layer_of.pop((x, y, ZF), None)
            put_id(s, x, y, ZF, 'cobblestone')
    for x in (0, 10):
        put_id(s, x, 0, ZF, 'cobblestone_wall')
    for x in (FX0, FX1):                             # the entrance frame out front (z 1)
        for y in (0, 1):
            put_id(s, x, y, 1, 'oak_log', axis='y')
    for x in range(FX0, FX1 + 1):
        put_id(s, x, 2, 1, 'oak_log', axis='x')
    for x in range(FX0, FX1 + 1):                    # beams joining it to the portal
        if x in (FX0, FX1):
            put_id(s, x, 2, 2, 'oak_log', axis='z')
    for x in range(W):                               # cobblestone stairs soften every step of the heap
        for z in range(ZF, D):
            h = height(x, z)
            best = None
            for f, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('west', (-1, 0)), ('east', (1, 0))):
                nh = height(x + dx, z + dz) if 0 <= x + dx < W and 0 <= z + dz < D else 0
                if nh > h and (best is None or nh > best[1]):
                    best = (f, nh)
            if best and (x, h, z) not in s.blocks and not (FX0 <= x <= FX1 and z == ZF) and not (
                    PX0 <= x <= PX1 and z <= ZE and h < 3):
                put_id(s, x, h, z, 'cobblestone_stairs', facing=best[0], **STAIR)
    s.at(1, 'fittings')
    put_id(s, PX1, 0, ZF + 1, 'chest', facing='west', type='single', waterlogged='false')   # miners' chest
    put_id(s, PX0, 0, ZF + 1, 'barrel', facing='up', open='false')                         # pit props
    for x, f in ((FX0, 'west'), (FX1, 'east')):      # torches on the entrance frame
        put_id(s, x - 1 if f == 'west' else x + 1, 1, 1, 'wall_torch', facing=f)
    for z in (ZF + 2, ZE):                           # torches inside on the frames
        put_id(s, PX0, 1, z, 'wall_torch', facing='east')
    for x in (0, 1):                                 # log pile (pit props) left of the entrance
        put_id(s, x, 0, 1, 'oak_log', axis='z'); put_id(s, x, 0, 2, 'oak_log', axis='z')
    put_id(s, 0, 1, 1, 'oak_log', axis='z')

    s.at(2, 'flourish2')
    for z in range(0, ZE + 1):                       # the track out of the adit
        put_id(s, mid, 0, z, 'rail', shape='north_south', waterlogged='false')
    for x in range(FX0 - 1, FX1 + 2):                # a pent hood over the entrance frame, overhanging its sides
        put_id(s, x, 3, 0, 'spruce_stairs', facing='south', **STAIR)
        for z in (1, 2):
            if (x, 3, z) not in s.blocks:
                put_id(s, x, 3, z, 'spruce_slab', type='bottom', waterlogged='false')
    for x in (FX0, FX1):                             # brackets under it at the front corners
        put_id(s, x, 2, 0, 'spruce_stairs', facing='south', half='top', shape='straight', waterlogged='false')
    put_id(s, mid, 1, 1, 'lantern', **HANG)          # lanterns under the beams
    put_id(s, mid, 1, ZF + 2, 'lantern', **HANG)
    put_id(s, 9, 0, 2, 'oak_fence'); put_id(s, 8, 0, 2, 'oak_fence')   # tool rack by the right wing wall
    put_id(s, 8, 1, 2, 'spruce_slab', **BOTTOM); put_id(s, 9, 1, 2, 'spruce_slab', **BOTTOM)
    s.decorate(9, 1, 2, 'item_frame', item='minecraft:iron_pickaxe', facing='north', note='picks on the rack')
    put_id(s, 10, 0, 1, 'barrel', facing='up', open='true')             # ore bin
    s.decorate(10, 0, 1, 'item', item='minecraft:raw_iron', note='ore in the open bin')
    for x, z, h in ((10, 5, 2), (10, 6, 1), (10, 4, 1), (0, 6, 1), (0, 7, 1)):   # spoil heaps of gravel
        for y in range(h):
            if (x, y, z) not in s.blocks:
                put_id(s, x, y, z, 'gravel')

    s.at(3, 'flourish3')
    put_id(s, mid, 2, 0, 'oak_wall_sign', facing='north', waterlogged='false')   # on the front lintel
    s.decorate(mid, 2, 0, 'sign_text', lines=['', 'Mine', '', ''])
    for x in (FX0, FX1):                             # banners on the entrance posts
        put_id(s, x, 1, 0, 'red_wall_banner', facing='north')
    for x in (1, 9):                                 # lantern posts on the path
        put_id(s, x, 0, 0, 'oak_fence'); put_id(s, x, 1, 0, 'lantern', **STAND)
    s.decorate(mid, 0, 0, 'minecart', note='a minecart of ore on the track')
    for (x, z), b in (((2, 6), 'moss_carpet'), ((7, 5), 'moss_carpet'), ((5, 7), 'moss_carpet'), ((3, 9), 'poppy'),
                      ((6, 9), 'dandelion'), ((8, 7), 'leaves'), ((2, 4), 'leaves'), ((4, 5), 'fern'),
                      ((7, 9), 'leaves'), ((1, 8), 'cornflower')):
        y = height(x, z)
        while (x, y, z) in s.blocks:                 # on top of a softening stair
            y += 1
        if b == 'leaves':
            put_id(s, x, y, z, 'oak_leaves', **LEAVES)
        else:
            put_id(s, x, y, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='mine', name='Mine (stage 1)', tier=3, zone='outer', role='resource', group='mine',
                   terrain='flexible', door='front', make=mine, changes='First version.',
                   description='Over budget: 280 blocks vs 170 (first build 222 vs 70: the spoil mound is most of it). '
                               'Stage 1 only (headframe and winch house are later stages). Builders never dig: a '
                               'timber-framed adit, 3 wide and 2 high, runs 6 blocks into a built rocky spoil mound; '
                               'miners dig down from its end. L2 adds the rail track, a hood over the entrance, '
                               'lanterns, a tool rack, an ore bin and gravel spoil heaps; L3 sign, banners, lantern '
                               'posts, moss, flowers and bushes on the mound.')]
