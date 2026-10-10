"""Stone wall modules, tier 3: straight, stepped, corner, gate and wall-walk stairs (docs/STRUCTURES.md, wall modules).

The look follows the approved "wall" and "keep" samples (studio/structures/wall, keep), at soldier scale:
a cobblestone footing with a battered (stair) plinth, a stone brick body with cobblestone patches, a crenellated
parapet carried out on upside-down stair corbels, and a 2-wide walk on top.

Every module shares one profile, so the pieces join seamlessly:
  - z = 0 is the OUTSIDE (the parapet on its corbels), z = 1..2 the wall body; the town side is z = 3 and up.
  - The body is 5 high (y 0..4): the walk is its top, soldiers stand on it at y = 5 (WALK). The parapet is 1 high
    (y 5). Merlons (y 6) follow one rule in every module so the pieces tile: a half merlon (a slab) at x = 0, then
    crenel, merlon, crenel, merlon (merlons at even x > 0). A module is odd in length, so its last column (a merlon)
    meets the next module's half merlon: the parapet alternates high / low across every joint. Placed around a town,
    a module's x = 0 end is its counter-clockwise end; the corner module and the gate fit that order
    (tools: scratch row check in build/batch3/).
  - The stepped module ends 1 higher (stand at y 6) and the stairs module climbs from the ground to the walk.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

WALK = 5                                   # soldiers stand on the walk at this y (the body's top block is y 4)
STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
NO_VINE = dict(north='false', south='false', east='false', west='false', up='false')


def masonry(x, y, z, seed=3):
    """The wall's stone, mixed with a fixed pattern: a mossy cobblestone and cobblestone footing, then stone bricks
    with a few cobblestone patches (old repairs). All KNOWN-OK, so level 1 can use it."""
    n = noise(x, y, z, seed)
    if y == 0:
        return 'mossy_cobblestone' if n < 0.4 else 'cobblestone'
    if y == 1:
        return 'cobblestone' if n < 0.45 else 'stone_bricks'
    return 'cobblestone' if n < 0.14 else 'stone_bricks'


def body(s, x, z, top, y0=0):
    for y in range(y0, top + 1):
        put_id(s, x, y, z, masonry(x, y, z))


def parapet(s, x, z, base, face, merlon, corbel=None):
    """Outside column of the wall at (x, z): a battered plinth stair at the foot, a corbel at y = base - 1, the parapet
    at base and on top what `merlon` says ('merlon', 'half', None; True = 'merlon'). face is the direction the
    outside faces ('north' or 'west')."""
    into = {'north': 'south', 'west': 'east'}[face]
    put_id(s, x, 0, z, 'cobblestone_stairs', facing=into, **STAIR)
    corbel = base - 1 if corbel is None else corbel
    put_id(s, x, corbel, z, 'stone_brick_stairs', facing=into, **TOP)
    for y in range(corbel + 1, base + 1):
        put_id(s, x, y, z, 'stone_bricks')
    battlement(s, x, z, base, 'merlon' if merlon is True else merlon or None)


def vine(s, x, y, z, side):
    put_id(s, x, y, z, 'vine', **{**NO_VINE, side: 'true'})


def crenel(x):
    """The shared merlon rule: 'half' at x = 0, a merlon at even x > 0, a crenel (gap) at odd x."""
    return 'half' if x == 0 else 'merlon' if x % 2 == 0 else None


def battlement(s, x, z, base, kind):
    """The block on top of the parapet at (x, base + 1, z): a stone brick merlon, a half merlon (cobblestone slab:
    KNOWN-OK, so level 1 can have it; it reads as an old repair like the cobblestone patches) or nothing."""
    if kind == 'merlon':
        put_id(s, x, base + 1, z, 'stone_bricks')
    elif kind == 'half':
        put_id(s, x, base + 1, z, 'cobblestone_slab', **SLAB)


def stone_wall_straight(length=5):
    s = Structure('stone_wall_straight', (length, 7, 3), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(length):
        for z in (1, 2):
            body(s, x, z, WALK - 1)
        parapet(s, x, 0, WALK, 'north', crenel(x))
    for x in (1, 3):                                                 # arrow-slit niches in the outer face
        s.remove(x, 2, 1)
    s.at(2, 'flourish2')
    for x in (1, 3):
        put_id(s, x, 2, 1, 'iron_bars')
    # a plain red banner on the outer face under the corbels (pattern by the decoration step), torches on the walk
    put_id(s, 2, 3, 0, 'red_wall_banner', facing='north')
    s.decorate(2, 3, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for x in (2, 4):
        put_id(s, x, WALK + 1, 1, 'wall_torch', facing='south')       # on the merlons' inner face
    put_id(s, 3, WALK, 2, 'stone_brick_wall')                        # a lamp post on the inner edge of the walk
    put_id(s, 3, WALK + 1, 2, 'lantern', **LANTERN)
    s.at(3, 'flourish3')
    # weather: ivy up the outer face, moss on the walk; a store of arrows on the walk
    for y in (1, 2):
        vine(s, 0, y, 0, 'south'); vine(s, 4, y, 0, 'south')
    vine(s, 1, 1, 0, 'south'); vine(s, 2, 1, 0, 'south')
    for x in (0, 2):
        put_id(s, x, WALK, 1, 'moss_carpet')
    put_id(s, 1, WALK, 2, 'barrel', facing='up', open='false')
    s.decorate(1, WALK + 1, 2, 'item', item='minecraft:arrow', note='arrows in the barrel')
    return connect(s)


def stone_wall_stepped(length=5):
    """The walk rises 1 block in the middle (a stair on the walk); the parapet and its corbels step up with it."""
    s = Structure('stone_wall_stepped', (length, 8, 3), palette='oak_a2')
    mid = length // 2
    s.at(1, 'shell')
    for x in range(length):
        high = x >= mid
        top = WALK if x > mid else WALK - 1                          # body top 4 (low end) or 5 (high end)
        for z in (1, 2):
            body(s, x, z, top)
            if x == mid:
                put_id(s, x, WALK, z, 'stone_brick_stairs', facing='east', **STAIR)   # the step up the walk
        parapet(s, x, 0, WALK + 1 if high else WALK, 'north', crenel(x))   # corbel one under the parapet: it steps too
    for x in (1, 3):                                                 # arrow-slit niches
        s.remove(x, 2, 1)
    s.at(2, 'flourish2')
    for x in (1, 3):
        put_id(s, x, 2, 1, 'iron_bars')
    put_id(s, 2, 3, 0, 'red_wall_banner', facing='north')
    s.decorate(2, 3, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for x in (2, 4):
        put_id(s, x, WALK + 2, 1, 'wall_torch', facing='south')
    put_id(s, 1, WALK, 2, 'stone_brick_wall')                        # lamp post on the low part of the walk
    put_id(s, 1, WALK + 1, 2, 'lantern', **LANTERN)
    s.at(3, 'flourish3')
    for y in (1, 2, 3):
        vine(s, 0, y, 0, 'south')
    vine(s, 4, 1, 0, 'south'); vine(s, 4, 2, 0, 'south'); vine(s, 3, 1, 0, 'south')
    put_id(s, 0, WALK, 1, 'moss_carpet'); put_id(s, 3, WALK + 1, 1, 'moss_carpet')
    put_id(s, 4, WALK + 1, 2, 'barrel', facing='up', open='false')   # arrows at the top of the step
    s.decorate(4, WALK + 2, 2, 'item', item='minecraft:arrow', note='arrows in the barrel')
    return connect(s)


def stone_wall_corner():
    """Outside corner: parapets on the front (z = 0) and the left (x = 0), a solid corner pier rising one higher.
    Merlons, going clockwise round a town: [module ends on a merlon] z 2 crenel, z 1 merlon, the pier, x 1 crenel,
    x 2 merlon [the next module starts with its half merlon]."""
    s = Structure('stone_wall_corner', (3, 8, 3), palette='oak_a2')
    s.at(1, 'shell')
    for x in (1, 2):
        for z in (1, 2):
            body(s, x, z, WALK - 1)
    for x in (1, 2):
        parapet(s, x, 0, WALK, 'north', x == 2)
    for z in (1, 2):
        parapet(s, 0, z, WALK, 'west', z == 1)
    for y in range(0, WALK + 2):                                     # corner pier, footing to merlon
        put_id(s, 0, y, 0, masonry(0, y, 0) if y < 2 else 'stone_bricks')
    put_id(s, 0, WALK + 2, 0, 'stone_brick_stairs', facing='south', **STAIR)   # it rises 1 higher, a sloped cap
    s.remove(2, 2, 1); s.remove(1, 2, 2)                             # arrow-slit niches on both faces
    s.at(2, 'flourish2')
    put_id(s, 2, WALK + 1, 1, 'wall_torch', facing='south')          # on the merlon at x 2
    put_id(s, 1, WALK + 1, 0, 'red_banner', rotation='10')            # a flag on the corner between the merlons
    s.decorate(1, WALK + 1, 0, 'banner_pattern', banner='red_banner', note='kingdom colours')
    put_id(s, 2, 2, 1, 'iron_bars'); put_id(s, 1, 2, 2, 'iron_bars')
    s.at(3, 'flourish3')
    for y in (1, 2, 3):
        vine(s, 1, y, 0, 'south')
    for y in (1, 2):
        vine(s, 0, y, 1, 'east')
    put_id(s, 1, WALK, 2, 'moss_carpet'); put_id(s, 2, WALK, 2, 'moss_carpet')
    return connect(s)


def stone_wall_gate(width=7):
    """Gate: two towers (2 x 4) flank an arched opening 3 wide and 3 high (no fence gate: soldiers can't open them).
    The walk crosses over the arch and through both towers at the wall's height (stand at y 5), roofed over inside
    the towers; the towers end in stone merlons 2 above the wall's. The towers stand out beyond the wall face on the
    outside (z = 0, flush with the parapet) and on the town side (z = 3); z = 4 is the town-side apron."""
    s = Structure('stone_wall_gate', (width, 9, 5), palette='oak_a2')
    towers = ((0, 1), (width - 2, width - 1))
    gx0, gx1 = 2, width - 3                                          # the passage
    mid = width // 2
    s.at(1, 'shell')
    for tx in towers:
        outer, inner = (tx[0], tx[1]) if tx[0] == 0 else (tx[1], tx[0])
        for x in tx:
            for z in range(4):
                body(s, x, z, WALK - 1)                              # solid up to the walk
            for z in (0, 3):                                         # front and back walls beside the walk
                for y in (WALK, WALK + 1):
                    put_id(s, x, y, z, masonry(x, y, z, seed=5))
            for z in range(4):
                put_id(s, x, WALK + 2, z, masonry(x, WALK + 2, z, seed=5))   # the roof over the walk
        for x, z in ((outer, 0), (inner, 1), (outer, 2), (inner, 3)):   # merlons on the tower top, chequered
            put_id(s, x, WALK + 3, z, 'stone_bricks')
    for x in range(gx0, gx1 + 1):
        for z in (1, 2):
            body(s, x, z, WALK - 1, y0=3)                            # the lintel over the passage
        parapet(s, x, 0, WALK, 'north', x == mid)                    # tower, crenel, merlon, crenel, tower
        s.remove(x, 0, 0)                                            # no plinth in the gateway
    for z in (1, 2):                                                 # the arch: upside-down stairs in the top corners
        put_id(s, gx0, 2, z, 'stone_brick_stairs', facing='west', **TOP)
        put_id(s, gx1, 2, z, 'stone_brick_stairs', facing='east', **TOP)
    put_id(s, gx0, 3, 0, 'stone_brick_stairs', facing='west', **TOP)   # the arch's front ring under the corbels
    put_id(s, gx1, 3, 0, 'stone_brick_stairs', facing='east', **TOP)
    for tx in towers:                                                # the towers: a battered foot and a corbel course
        for x in tx:                                                 # at the walk floor, front and town side, in line
            for z, f in ((0, 'south'), (3, 'north')):                # with the parapet corbels over the arch
                s.remove(x, 0, z); put_id(s, x, 0, z, 'cobblestone_stairs', facing=f, **STAIR)
                s.remove(x, WALK - 1, z); put_id(s, x, WALK - 1, z, 'stone_brick_stairs', facing=f, **TOP)
    s.at(1, 'fittings')
    put_id(s, gx0, 1, 0, 'wall_torch', facing='east')                # torches in the gateway on the tower sides
    put_id(s, gx1, 1, 0, 'wall_torch', facing='west')
    for x in (1, width - 2):                                         # torches in the walk passages
        put_id(s, x, WALK + 1, 2, 'wall_torch', facing='north')
    put_id(s, mid, 2, 1, 'lantern', **HANGING)                       # lanterns hanging in the arch
    put_id(s, mid, 2, 2, 'lantern', **HANGING)
    s.at(2, 'flourish2')
    put_id(s, mid, 3, 0, 'red_wall_banner', facing='north')          # banner over the arch (pattern: decoration step)
    s.decorate(mid, 3, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for tx in towers:
        outer, inner = (tx[0], tx[1]) if tx[0] == 0 else (tx[1], tx[0])
        put_id(s, inner, WALK + 3, 2, 'lantern', **LANTERN)          # a watch light on each tower top
        put_id(s, inner, 3, 4, 'red_wall_banner', facing='south')    # banners on the town side
        s.decorate(inner, 3, 4, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for x in (gx0 - 1, gx1 + 1):                                     # lamp posts on the town side of the gate
        put_id(s, x + (-1 if x < mid else 1), 0, 4, 'stone_brick_wall')
        put_id(s, x + (-1 if x < mid else 1), 1, 4, 'lantern', **LANTERN)
    s.at(3, 'flourish3')
    # a raised portcullis on the town side of the arch: two rows of iron bars under a stone lintel between the towers
    for x in range(gx0, gx1 + 1):
        for y in (3, 4):
            put_id(s, x, y, 3, 'iron_bars')
        put_id(s, x, WALK, 3, 'stone_bricks')
    for tx in towers:                                                # ivy on the towers' town sides
        inner = tx[1] if tx[0] == 0 else tx[0]
        for y in (1, 2):
            vine(s, inner, y, 4, 'north')
    put_id(s, mid, WALK + 2, 0, 'red_banner', rotation='8')          # a flag on the merlon over the gate
    s.decorate(mid, WALK + 2, 0, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in (gx0, gx1):                                             # moss on the walk over the arch
        put_id(s, x, WALK, 1, 'moss_carpet')
    for x, b in ((1, 'hay_block'), (width - 2, 'barrel')):           # supplies by the lamp posts
        put_id(s, x, 0, 4, b, **({'axis': 'y'} if b == 'hay_block' else {'facing': 'up', 'open': 'false'}))
    return connect(s)


def stone_wall_stairs(length=5):
    """A straight module with a flight of stone brick stairs on the town side (z = 3), from the ground at the west
    end up to the walk at the east end; z = 4 is the apron in front of the flight."""
    s = Structure('stone_wall_stairs', (length, 7, 5), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(length):
        for z in (1, 2):
            body(s, x, z, WALK - 1)
        parapet(s, x, 0, WALK, 'north', crenel(x))
    for x in (1, 3):
        s.remove(x, 2, 1)
    s.at(1, 'fittings')
    for x in range(length):                                          # the flight climbs east, step y = x
        for y in range(0, x):
            put_id(s, x, y, 3, masonry(x, y, 3))
        put_id(s, x, x, 3, 'stone_brick_stairs', facing='east', **STAIR)
    s.at(2, 'flourish2')
    for x in (1, 3):
        put_id(s, x, 2, 1, 'iron_bars')
    put_id(s, 2, 3, 0, 'red_wall_banner', facing='north')
    s.decorate(2, 3, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    put_id(s, 0, 0, 4, 'stone_brick_wall'); put_id(s, 0, 1, 4, 'lantern', **LANTERN)   # lamp post at the foot
    put_id(s, 4, 3, 4, 'red_wall_banner', facing='south')            # banner on the side of the flight
    s.decorate(4, 3, 4, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    put_id(s, 2, WALK + 1, 1, 'wall_torch', facing='south')
    s.at(3, 'flourish3')
    for x in (3, 4):                                                 # supplies stacked against the flight
        put_id(s, x, 0, 4, 'barrel', facing='up', open='false')
    put_id(s, 2, 0, 4, 'hay_block', axis='y')
    put_id(s, 4, 1, 4, 'barrel', facing='up', open='false')
    put_id(s, 1, 0, 4, 'flower_pot'); s.decorate(1, 0, 4, 'potted_plant', plant='cornflower')
    for y in (1, 2):
        vine(s, 0, y, 0, 'south'); vine(s, 4, y, 0, 'south')
    put_id(s, 2, WALK, 2, 'moss_carpet'); put_id(s, 3, WALK, 1, 'moss_carpet')
    return connect(s)


_COMMON = dict(tier=3, zone='wall', role='defense', group='stone_wall', terrain='stepped', door=None)
TILING = ('Quality check round 1: the merlon pattern changed across the whole set so modules tile end to end '
          '(a cobblestone-slab half merlon at x 0, merlons at x 2 and 4: neighbours now alternate high / low instead '
          'of two merlons side by side). ')
STRUCTURES = [
    dict(path='stone_wall_straight', name='Stone wall, straight', make=stone_wall_straight, **_COMMON,
         changes=TILING + 'Torches moved onto the merlons at x 2 and 4, the lamp post to x 3; the crenel fillers '
                          '(slab, mossy wall) removed from L3, which gets more ivy and moss and an arrow barrel.',
         description='Over budget: 66 blocks vs 60. 5 long stone wall on a battered cobblestone footing, crenellated '
                     'parapet on corbels, 2-wide walk (stand at 5). Outside is the front (z 0). Merlons tile across joints: half merlon, crenel, merlon, crenel, merlon (the next module starts with its half merlon).'),
    dict(path='stone_wall_stepped', name='Stone wall, stepped', make=stone_wall_stepped, **_COMMON,
         changes=TILING + 'The corbels of the high half (x 2..4) now step up with the parapet (upside-down stairs at '
                          'y 5, air at y 4). Torches on the merlons at x 2 and 4, the lamp post on the low walk; the '
                          'crenel fillers removed from L3 (an arrow barrel and more ivy instead).',
         description='Over budget: 72 blocks vs 60. Like the straight module, but the walk rises 1 block (stand at 5, '
                     'then 6) up a stair in the middle; the parapet and its corbels step up with it. Merlons tile across joints: half merlon, crenel, merlon, crenel, merlon (the next module starts with its half merlon).'),
    dict(path='stone_wall_corner', name='Stone wall, corner', make=stone_wall_corner, **_COMMON,
         changes=TILING + 'Corner merlons follow it (front arm: pier, crenel, merlon; side arm: pier, merlon, crenel). '
                          'The L2 torch moved from the banner onto the merlon at (2, 6, 1); the lamp post that narrowed '
                          'the corner turn to 1 block is gone. Arrow-slit niches on both faces (bars from L2).',
         description='Outside corner joining two straight modules, with a corner pier. Going clockwise round a town '
                     'its side arm (x 0) follows a module and its front arm (z 0) leads into the next.'),
    dict(path='stone_wall_gate', name='Stone wall gate', make=stone_wall_gate,
         **{**_COMMON, 'role': 'gate', 'group': 'gate'},
         changes=TILING + 'The open notches at the front corners (arrow slits) are filled. The towers end in '
                          'chequered stone merlons 2 above the wall merlons instead of flat spruce stair caps. A stone '
                          'lintel at y 5 over the L3 portcullis bars. More detail from L1: a battered foot and a '
                          'corbel course on the towers in line with the parapet corbels, the arch\'s front ring, '
                          'lanterns in the arch; a watch lantern on each tower top from L2.',
         description='Over budget: 151 blocks vs 60. Its wall line is z 0..2 like the others, the towers also reach '
                     'z 3. Two small crenellated towers and an arched opening 3 wide (no fence gate); the walk crosses '
                     'over it and through the towers.'),
    dict(path='stone_wall_stairs', name='Stone wall, walk stairs', make=stone_wall_stairs, **_COMMON,
         changes=TILING + 'The L2 torch moved onto the merlon at x 2; the slab that half-filled a crenel in L3 is '
                          'removed.',
         description='Over budget: 81 blocks vs 60. A straight module with a stone stair flight on the town side up '
                     'to the walk. Merlons tile across joints: half merlon, crenel, merlon, crenel, merlon (the next module starts with its half merlon).'),
]
