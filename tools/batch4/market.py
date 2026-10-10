"""Market square (tier 4, town style A): a paved square with four market stalls round a stone market cross
(docs/STRUCTURES.md, "Market square").

The four stalls are the batch-3 market stall (tools/batch3/market_stall.py), stamped in level by level: its core
(posts, counter, striped awning, stock; x 1..5, z 0..4 of the stall) keeps its look, the stall's own street props
(sign post and its shop sign, side melon, bush, compost bin) are left out so four fit. Two stalls stand on the north
side and two on the south side, all with their counters facing the cross; gravel aisles lead in from the middle of
every side.

Ground rule: this is a flush piece (ground_layer=0). Layer y = 0 REPLACES the top grass block, so the paving and the
flower beds are level with the lawn (the mod's builders can't replace ground yet). Everything that stands is at
y >= 1."""

from make_batch1_structures import Structure
from batch3.market_stall import market_stall
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
FLIP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
FLOWERS = ('poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet')

SIZE = 15
STALL_CORE = (1, 5, 0, 4)          # x0, x1, z0, z1 of the stall module that is stamped in


def stamp_stall(s, ox, oz, flip, wool=None):
    """Copies the stall's core into s with its local (1, 0, 0) at (ox, 1, oz), level by level. flip turns it round
    (180 degrees) so its counter faces +z (the north row faces the cross). wool maps block ids (awning colours)."""
    st = market_stall()
    x0, x1, z0, z1 = STALL_CORE
    w, d = x1 - x0, z1 - z0

    def where(x, z):
        lx, lz = x - x0, z - z0
        return (ox + w - lx, oz + d - lz) if flip else (ox + lx, oz + lz)

    def skip(name, x):
        return name == 'oak_wall_sign' and x == x0      # the shop sign hangs on the post that is left outside

    for level in (1, 2, 3):
        s.at(level, {1: 'fittings', 2: 'flourish2', 3: 'flourish3'}[level])
        for (x, y, z), (name, props, lv) in sorted(st.blocks.items()):
            if lv != level or not (x0 <= x <= x1 and z0 <= z <= z1) or skip(name, x):
                continue
            p = dict(props)
            name = (wool or {}).get(name, name)
            if flip and p.get('facing') in FLIP:
                p['facing'] = FLIP[p['facing']]
            if flip and 'rotation' in p:
                p['rotation'] = str((int(p['rotation']) + 8) % 16)
            if flip and p.get('type') in ('left', 'right'):
                p['type'] = 'right' if p['type'] == 'left' else 'left'
            if flip and p.get('hinge') in ('left', 'right'):
                p['hinge'] = 'right' if p['hinge'] == 'left' else 'left'
            wx, wz = where(x, z)
            put_id(s, wx, y + 1, wz, name, **p)
        for dct in st.decorations[level]:
            x, y, z = dct['pos']
            if x0 <= x <= x1 and z0 <= z <= z1 and (x, y, z) in st.blocks and not skip(st.blocks[(x, y, z)][0], x):
                wx, wz = where(x, z)
                s.decorate(wx, y + 1, wz, **{k: v for k, v in dct.items() if k != 'pos'})


def market():
    """Level 1 (the market works): the paved square (stone brick border, cobblestone with some moss, gravel aisles in
    from all four sides), the stepped market cross in the middle with four flush flower beds (poppies) at its corners,
    the four striped stalls with their counters, goods, chests and crates, benches on the west and east sides facing
    the cross.
    Level 2 (market day): the cross gets its head (chiselled block, stone arms with two hanging lanterns, a lantern on
    top), the stalls open for trade (red and white or yellow and white wool front edges, lanterns, produce, shelves),
    the weighing scale (a lectern) and the merchants' strongbox by the cross, red and white banners on poles at the
    north and south entrances, and two covered sitting areas: striped wool canopies (red and white west, yellow and
    white east) on spruce posts over the benches and the west and east entrances, lanterns hung under them.
    Level 3 (a prosperous market): the covered market cross (four stone posts and a stepped spruce pyramid roof over
    the cross, its top lantern as the finial), more flowers in the beds, the stalls' banners and stools, red and white wall
    banners on the stalls' backs, goods (hay, crates, pumpkins) stacked against the stalls' outer sides."""
    s = Structure('market', (SIZE, 8, SIZE), palette='oak_a2')
    e = SIZE - 1
    c = SIZE // 2                  # 7
    corners = [(c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)]
    beds = {(x, z): (x + (1 if x > c else -1), z) for x, z in corners}   # each bed: the corner and one cell outward
    soil = set(beds) | set(beds.values())

    s.at(1, 'shell')
    for x in range(SIZE):
        for z in range(SIZE):
            if (x, z) in soil:
                put_id(s, x, 0, z, 'coarse_dirt')                    # flower beds, flush with the paving
            elif x in (0, e) or z in (0, e):
                put_id(s, x, 0, z, 'gravel' if (x == c or z == c) else 'stone_bricks')
            elif x == c or z == c:
                put_id(s, x, 0, z, 'gravel')
            elif abs(x - c) <= 2 and abs(z - c) <= 2:
                put_id(s, x, 0, z, 'stone_bricks')
            else:
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 11) < 0.2 else 'cobblestone')

    s.at(1, 'fittings')
    # the market cross: a stair ring round a stone block, a cobblestone wall shaft
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                continue
            if dz == -1:
                f = 'south'
            elif dz == 1:
                f = 'north'
            else:
                f = 'east' if dx == -1 else 'west'
            shape = 'straight'
            if dx and dz:
                shape = 'outer_left' if (dx == -1) == (dz == -1) else 'outer_right'
            put_id(s, c + dx, 1, c + dz, 'stone_brick_stairs', facing=f, half='bottom', shape=shape,
                   waterlogged='false')
    put_id(s, c, 1, c, 'stone_bricks')
    put_id(s, c, 2, c, 'stone_bricks')
    put_id(s, c, 3, c, 'cobblestone_wall')
    put_id(s, c, 4, c, 'cobblestone_wall')
    for x, z in sorted(beds):                                     # poppies in the beds from the start
        put_id(s, x, 1, z, 'poppy')
    # benches on the west and east sides, facing the cross, either side of the entrance
    for x, back in ((1, 'west'), (e - 1, 'east')):
        for z in (c - 2, c - 1, c + 1, c + 2):
            put_id(s, x, 1, z, 'oak_stairs', facing=back, **STAIR)

    # the four stalls (levels 1 to 3 of the stall go into levels 1 to 3 of the square)
    brown = {'red_carpet': 'brown_carpet', 'red_wool': 'yellow_wool'}   # two awning colours, crosswise
    stamp_stall(s, 1, 0, flip=True)
    stamp_stall(s, e - 5, 0, flip=True, wool=brown)
    stamp_stall(s, 1, e - 4, flip=False, wool=brown)
    stamp_stall(s, e - 5, e - 4, flip=False)

    s.at(2, 'flourish2')
    # the cross's head: a chiselled block, short stone arms with a lantern hanging under each
    put_id(s, c, 5, c, 'chiseled_stone_bricks')
    for dx, f in ((-1, 'east'), (1, 'west')):
        put_id(s, c + dx, 5, c, 'stone_brick_stairs', facing=f, **TOP)
        put_id(s, c + dx, 4, c, 'lantern', **HANG)
    put_id(s, c, 6, c, 'stone_brick_wall')
    put_id(s, c, 7, c, 'lantern', **STAND)
    # the weighing scale and the strongbox by the cross
    put_id(s, c + 2, 1, c, 'lectern', facing='west', has_book='false', powered='false')
    put_id(s, c - 2, 1, c, 'chest', facing='east', type='single', waterlogged='false')
    # banners on poles at the north and south entrances
    for z, rot in ((0, '8'), (e, '0')):
        for x, col in ((c - 1, 'red_banner'), (c + 1, 'white_banner')):
            put_id(s, x, 1, z, 'spruce_fence')
            put_id(s, x, 2, z, 'spruce_fence')
            put_id(s, x, 3, z, col, rotation=rot)
    # covered sitting areas: a striped wool canopy on four spruce posts over the benches and the west and east
    # entrances (red and white on the west, yellow and white on the east), lanterns hung under them
    for xa, xb, col in ((0, 2, 'red_wool'), (e - 2, e, 'yellow_wool')):
        for x in (xa, xb):
            for z in (c - 2, c + 2):
                for y in (1, 2, 3):
                    put_id(s, x, y, z, 'spruce_log', axis='y')
        for x in range(xa, xb + 1):
            for z in range(c - 2, c + 3):
                put_id(s, x, 4, z, col if z % 2 else 'white_wool')
        for z in (c - 1, c + 1):
            put_id(s, (xa + xb) // 2, 3, z, 'lantern', **HANG)

    s.at(3, 'flourish3')
    # the market cross becomes a covered cross: four stone posts on the step's corners carry a small pyramid roof
    # (spruce stairs, a dark oak ceiling) round the cross's head, the top lantern as its finial
    for dx in (-1, 1):
        for dz in (-1, 1):
            for y in (2, 3, 4):
                put_id(s, c + dx, y, c + dz, 'stone_brick_wall')
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            if (x, 5, z) in s.blocks:
                continue                                          # the cross's head and arms
            if abs(x - c) == 2 or abs(z - c) == 2:              # eaves, high side inward
                if abs(z - c) == 2 and abs(x - c) < 2:
                    f = 'south' if z < c else 'north'
                elif abs(x - c) == 2 and abs(z - c) < 2:
                    f = 'east' if x < c else 'west'
                else:
                    f = 'south' if z < c else 'north'
                shape = 'straight'
                if abs(x - c) == 2 and abs(z - c) == 2:
                    shape = 'outer_left' if (x < c) == (z < c) else 'outer_right'
                put_id(s, x, 5, z, 'spruce_stairs', facing=f, half='bottom', shape=shape, waterlogged='false')
            else:
                put_id(s, x, 5, z, 'dark_oak_planks')
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            if (x, z) == (c, c):
                continue
            if abs(z - c) == 1 and x == c:
                f = 'south' if z < c else 'north'
            elif abs(x - c) == 1 and z == c:
                f = 'east' if x < c else 'west'
            else:
                f = 'south' if z < c else 'north'
            shape = 'straight'
            if x != c and z != c:
                shape = 'outer_left' if (x < c) == (z < c) else 'outer_right'
            put_id(s, x, 6, z, 'spruce_stairs', facing=f, half='bottom', shape=shape, waterlogged='false')
    for i, (x, z) in enumerate(sorted(beds.values())):           # the beds fill up: a second flower in each
        put_id(s, x, 1, z, FLOWERS[(i + 1) % len(FLOWERS)])
    # the stalls' backs face the roads: a wall banner on each back post's outer face, red on the red stalls, white on
    # the brown ones
    for x0, z, f, col in ((1, 0, 'north', 'red'), (e - 5, 0, 'north', 'white'), (1, e, 'south', 'white'),
                          (e - 5, e, 'south', 'red')):
        for x in (x0 + 1, x0 + 3):
            put_id(s, x, 2, z, f'{col}_wall_banner', facing=f)
    # goods stacked against the outer sides of the stalls (west and east edges): hay, a lidded crate, a pumpkin
    for x, f in ((0, 'east'), (e, 'west')):
        for z0, z1 in ((1, 3), (e - 3, e - 1)):
            if (x, 1, z0) not in s.blocks:
                put_id(s, x, 1, z0, 'hay_block', axis='y')
            if (x, 1, z0 + 1) not in s.blocks:
                put_id(s, x, 1, z0 + 1, 'barrel', facing='up', open='false')
                put_id(s, x, 2, z0 + 1, 'spruce_trapdoor', half='bottom', open='false', powered='false',
                       waterlogged='false', facing=f)
            if (x, 1, z1) not in s.blocks:
                put_id(s, x, 1, z1, 'pumpkin')
    connect(s)
    return s


STRUCTURES = [dict(path='market', name='Market square', tier=4, zone='inner', role='market', group='market',
                   terrain='flexible', door=None, make=market, ground_layer=0,
                   changes='Carlton: L2 and L3 now differ clearly. L2 adds two covered sitting areas (striped red '
                           'and white / yellow and white wool canopies on spruce posts over the benches and the west '
                           'and east entrances, with hanging lanterns) instead of the small lantern posts; L3 turns '
                           'the market cross into a covered cross (stone posts and a pyramid spruce roof). Also: the stall copies follow the market stall\'s quality-check round 2 (a flower pot and grain '
                           'on the front barrels at level 1, produce at the front edge). The flower beds round the '
                           'cross are now flush with the paving (coarse dirt in the ground layer, poppies from level 1, '
                           'a second flower at level 3) instead of raised dirt with trapdoor borders. The stall\'s '
                           'shop sign is no longer copied without its post (it floated).',
                   description='Over budget: 702 blocks vs 221 (15 x 15 instead of 11 x 11, so four full stalls fit). Flush piece '
                               '(ground_layer 0: the paving replaces the grass; the mod\'s builders can\'t do that '
                               'yet). A paved square (stone brick border, cobblestone, gravel aisles in from all four '
                               'sides) with four market stalls (the market stall design, red-white and brown-white '
                               'awnings) round a stepped stone market cross with flush flower beds; benches face the '
                               'cross. L2: the cross head with lanterns, stalls open for trade, a weighing scale '
                               '(lectern), a strongbox, banner poles, striped canopies over the benches. L3: a covered '
                               'market cross with a pyramid roof, more flowers, banners on the stall backs, stall stools and banners, goods stacked at the '
                               'sides.')]
