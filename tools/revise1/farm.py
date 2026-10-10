"""Farm, revised from Carlton's notes (revise1): one detailed timber farmhouse the farmer lives in (levels 1 to 3, a big
tower windmill beside it at level 3), plus one-level field modules the farmer lays out around it (wheat, carrots,
potatoes), each a raised bed with a water trench down the middle.

Fences, walls, glass panes and iron bars get their connection properties (north/east/south/west, walls also up) set
here for every level (Connected.level_blocks): the Studio and the NBT never update them on their own, so batch 1's
fences stood as lone posts. A level only adds blocks; a fence may gain an arm at a higher level when a neighbour is
added next to it, exactly as the game updates it when the builder places that neighbour.
"""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP_STAIR = dict(half='top', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
TOP_SLAB = dict(type='top', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
SHUT = dict(half='bottom', open='true', powered='false', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')

# ---------------------------------------------------------------- connections (fences, walls, panes, bars)

_NOT_FULL = ('stairs', 'slab', 'fence', 'pane', 'iron_bars', 'trapdoor', 'door', 'carpet', 'torch', 'lantern', 'chain',
             'button', 'pressure_plate', 'sign', 'banner', 'bed', 'flower', 'poppy', 'dandelion', 'sapling', 'campfire',
             'chest', 'grindstone', 'stonecutter', 'leaves', 'pumpkin', 'wheat', 'carrots', 'potatoes', 'grass', 'fern',
             'candle', 'cauldron', 'lectern', 'anvil', 'bell', 'ladder', 'scaffolding', 'rail', 'lever', 'skull',
             'head', 'tulip', 'allium', 'cornflower', 'bluet', 'daisy', 'orchid', 'rose', 'lily', 'vine', 'air',
             'bee_nest', 'beehive', 'pot', 'hopper', 'brewing', 'enchanting', 'end_rod', 'lightning')
_SIDES = (('north', 0, -1), ('east', 1, 0), ('south', 0, 1), ('west', -1, 0))


def _kind(name):
    if name.endswith('_fence'):
        return 'fence'
    if name.endswith('_wall') and not name.endswith(('_sign', '_torch', '_banner')):
        return 'wall'
    if name.endswith('glass_pane') or name == 'iron_bars':
        return 'pane'
    if name.endswith('_fence_gate'):
        return 'gate'
    return None


def _full(name):
    return _kind(name) is None and not any(t in name for t in _NOT_FULL)


def _joins(kind, other, side_axis):
    """Does a `kind` block connect to the block `other` (name, props) on a side along side_axis ('x' or 'z')?"""
    name, props = other
    k = _kind(name)
    if k == 'gate':                                  # a gate joins when its opening runs across that side
        f = props.get('facing', 'north')
        return (f in ('north', 'south')) == (side_axis == 'x')
    if kind == 'fence':
        return k == 'fence' or _full(name)
    if kind == 'wall':
        return k in ('wall', 'pane') or _full(name)
    return k in ('pane', 'wall') or _full(name)       # pane, bars


def connect(blocks):
    """Sets connection properties on every fence, wall, pane and bar in a {pos: (name, props)} dict (in place)."""
    for (x, y, z), (name, props) in list(blocks.items()):
        kind = _kind(name)
        if kind not in ('fence', 'wall', 'pane'):
            continue
        p = {k: v for k, v in props.items() if k == 'waterlogged'}
        p.setdefault('waterlogged', 'false')
        above = blocks.get((x, y + 1, z))
        on = {}
        for side, dx, dz in _SIDES:
            n = blocks.get((x + dx, y, z + dz))
            on[side] = bool(n) and _joins(kind, n, 'x' if dx else 'z')
        if kind == 'wall':
            tall = bool(above) and (_full(above[0]) or _kind(above[0]) == 'wall')
            for side in on:
                p[side] = ('tall' if tall else 'low') if on[side] else 'none'
            straight = (on['north'] and on['south'] and not on['east'] and not on['west']) or \
                       (on['east'] and on['west'] and not on['north'] and not on['south'])
            post_above = bool(above) and (any(t in above[0] for t in ('torch', 'lantern', 'banner', 'sign')) or
                                          (_kind(above[0]) == 'wall' and above[1].get('up') == 'true'))
            p['up'] = 'false' if straight and not post_above else 'true'
        else:
            for side in on:
                p[side] = 'true' if on[side] else 'false'
        blocks[(x, y, z)] = (name, p)
    return blocks


class Connected(Structure):
    """A Structure whose fences, walls, panes and bars connect to their neighbours in every level."""

    def level_blocks(self, level):
        return connect(super().level_blocks(level))


# ---------------------------------------------------------------- the farmhouse (levels 1 to 3, windmill at 3)

class _Shift:
    """Draws into a Structure with an x offset (the farmhouse is drawn in its own coordinates, right of the mill)."""

    def __init__(self, s, ox):
        self.s, self.ox = s, ox

    def __getattr__(self, name):
        if name in ('put', 'add', 'remove', 'door', 'bed', 'decorate'):
            return lambda x, *a, **k: getattr(self.s, name)(x + self.ox, *a, **k)
        return getattr(self.s, name)

    def has(self, x, y, z):
        return (x + self.ox, y, z) in self.s.blocks


def farmhouse():
    """The farmer's house (right) and, at level 3, a tower windmill beside it (left).

    House, drawn in its own x (0 = left overhang, walls 1..9, 10 = right overhang and chimney): a two-storey
    farmhouse. Ground floor (9 x 6, z 2..7): cobblestone walls with mossy patches and stone brick quoins on a plinth.
    Upper floor (9 x 7, z 1..7): a timber frame (dark oak posts, white plaster panels, a row of windows) jettied one
    block out over the front on corbels. Spruce gable roof with one block of overhang all round, a brick chimney on
    the right gable. A stair along the back wall leads up (soldiers don't climb ladders).
    Level 1 also has the farm props at the door (composter workstation, hay bales, barrel, chopping block, flower
    boxes, lanterns). Level 2 adds a lean-to woodshed on the right gable, shutters, upper flower pots, gable trim and
    a bench. Level 3 adds the windmill, beehives and a sunflower behind the woodshed, potted flowers and a lantern."""
    W, D, H = 24, 10, 15
    s = Connected('farm', (W, H, D), palette='oak_a2')
    h = _Shift(s, 11)
    x0, x1, zg, zu, zb = 1, 9, 2, 1, 7             # walls: ground front z 2, upper (jetty) front z 1, back z 7
    mid, zm = 5, 4                                 # door column, ridge row
    s.at(1, 'shell')
    # ground floor: plinth, floor, stone walls (mossy patches, stone brick quoins)
    for x in range(x0, x1 + 1):
        for z in range(zg, zb + 1):
            if x in (x0, x1) or z in (zg, zb):
                h.put(x, 0, z, 'base')
            else:
                h.put(x, 0, z, 'floor')
    g_windows = {(3, zg), (7, zg), (x0, zm), (x1, 3), (3, zb)}
    for y in (1, 2, 3):
        for x in range(x0, x1 + 1):
            for z in range(zg, zb + 1):
                if not (x in (x0, x1) or z in (zg, zb)):
                    continue
                if (x, z) == (mid, zg) and y < 3:
                    continue
                if (x, z) in g_windows and y == 2:
                    h.put(x, y, z, 'window')
                elif x in (x0, x1) and z in (zg, zb):
                    h.put(x, y, z, 'stone_bricks')
                elif x == x1 and z == zm:
                    h.put(x, y, z, 'chimney')            # fireback
                else:
                    h.put(x, y, z, 'moss' if (x * 5 + y * 3 + z * 7) % 6 == 0 else 'base')
    h.put(mid, 3, zg, 'stone_brick_stairs', facing='north', half='top', shape='straight', waterlogged='false')  # lintel
    h.door(mid, 1, zg, 'north')
    # upper floor: beam ring (jetty over the front), plank floor with a stair hole
    for x in range(x0, x1 + 1):
        for z in range(zu, zb + 1):
            if x in (x0, x1) or z in (zu, zb):
                h.put(x, 4, z, 'dark_frame', axis='x' if z in (zu, zb) else 'z')
            elif not (z == 6 and 5 <= x <= 7):
                h.put(x, 4, z, 'floor')
    for x in (x0, 3, 7, x1):                                    # corbels under the jetty
        h.put(x, 3, zu, 'dark_stairs', facing='south', half='top', shape='straight', waterlogged='false')
    u_posts = {(x, zu) for x in (x0, 3, mid, 7, x1)} | {(x, zb) for x in (x0, mid, x1)} | \
              {(x, zm) for x in (x0, x1)}
    u_windows = {(2, zu), (4, zu), (6, zu), (8, zu), (x0, 2), (x0, 6), (x1, 2), (x1, 6), (3, zb), (7, zb)}
    for y in (5, 6, 7):
        for x in range(x0, x1 + 1):
            for z in range(zu, zb + 1):
                if not (x in (x0, x1) or z in (zu, zb)):
                    continue
                if (x, z) in u_posts:
                    h.put(x, y, z, 'dark_frame', axis='y')
                elif (x, z) in u_windows and y == 6:
                    h.put(x, y, z, 'window')
                else:
                    h.put(x, y, z, 'white_terracotta')
    for x in range(x0, x1 + 1):                                 # top plates
        h.put(x, 8, zu, 'dark_frame', axis='x'); h.put(x, 8, zb, 'dark_frame', axis='x')
    for z in range(zu + 1, zb):
        h.put(x0, 8, z, 'dark_frame', axis='z'); h.put(x1, 8, z, 'dark_frame', axis='z')
    for y in range(0, 14):                                      # chimney on the right gable, through the overhang
        h.put(x1 + 1, y, zm, 'chimney')
    rows = {0: 8, 1: 9, 2: 10, 3: 11, 5: 11, 6: 10, 7: 9, 8: 8}  # gable roof, ridge along x, overhang all round
    for z, y in rows.items():
        for x in range(0, x1 + 2):
            if not h.has(x, y, z):
                eave = z in (0, 8) or x in (0, x1 + 1)
                h.put(x, y, z, 'dark_stairs' if eave else 'roof_stairs', facing='south' if z < zm else 'north', **STAIR)
    for x in range(0, x1 + 1):
        h.put(x, 12, zm, 'roof_slab', **SLAB)                   # ridge
    for x in (x0, x1):                                          # gables: plaster around a dark oak king post
        for y, zs in ((9, range(2, 7)), (10, range(3, 6)), (11, (zm,))):
            for z in zs:
                h.put(x, y, z, 'dark_frame' if z == zm else 'white_terracotta', **({'axis': 'y'} if z == zm else {}))
    for i, x in enumerate((5, 6, 7)):                           # stair along the back wall up to the floor hole
        h.put(x, 1 + i, 6, 'oak_stairs', facing='east', **STAIR)
    h.put(mid, 0, 1, 'stone_stairs', facing='south', **STAIR)   # doorstep
    s.at(1, 'fittings')
    for x in (3, 7):                                            # flower boxes under the ground windows
        h.put(x, 0, 1, 'base'); h.put(x, 1, 1, 'coarse_dirt'); h.put(x, 2, 1, 'poppy')
        h.put(x, 1, 0, 'trapdoor', facing='north', **SHUT)
    for x in (mid - 1, mid + 1):                                # lanterns under the jetty, each side of the door
        h.put(x, 3, zu, 'lantern', **HANG)
    h.put(x1, 0, 0, 'composter')                                # the workstation, by the door
    h.put(x1 + 1, 0, 0, 'hay_block', axis='y'); h.put(x1 + 1, 0, 1, 'hay_block', axis='x')
    h.put(x1 + 1, 1, 1, 'hay_block', axis='z')
    h.put(1, 0, 0, 'barrel', facing='up', open='false'); h.put(0, 0, 0, 'stripped', axis='y')   # barrel, chopping block
    h.put(x1 - 1, 1, zm, 'hearth', facing='west', lit='false')   # ground floor: kitchen
    h.put(x1 - 1, 1, 3, 'barrel', facing='up', open='false')
    h.put(x1 - 1, 1, 6, 'chest', facing='west', type='single', waterlogged='false')
    h.put(3, 1, zm, 'roof_slab', **SLAB)                        # table and chairs
    h.put(3, 1, 3, 'chair', facing='north', **STAIR); h.put(2, 1, zm, 'chair', facing='east', **STAIR)
    h.put(2, 1, 6, 'crafting_table')
    for x, z, f in ((2, 3, 'south'), (7, 3, 'south')):
        h.put(x, 3, z, 'wall_torch', facing=f)
    h.bed(3, 5, 3, 'north')                                     # upper floor: bedroom
    h.put(2, 5, 2, 'chest', facing='east', type='single', waterlogged='false')
    for x in (6, 7, 8):
        h.put(x, 5, 2, 'hay_block', axis='x')                   # grain store
    h.put(2, 7, 5, 'wall_torch', facing='east'); h.put(8, 7, 5, 'wall_torch', facing='west')

    s.at(2, 'flourish2')
    # lean-to woodshed on the right gable: log posts, slab roof, stacked logs, a saw horse
    for z in (2, 7):
        for y in (0, 1, 2):
            h.put(x1 + 3, y, z, 'shed_post', axis='y')
    for x in (x1 + 1, x1 + 2, x1 + 3):
        for z in range(2, 8):
            h.add(x, 3, z, 'roof_slab', **SLAB)
    for z in (2, 3, 5, 6):
        for y in (0, 1):
            h.add(x1 + 2, y, z, 'stripped', axis='x')
    h.put(x1 + 1, 0, 6, 'stripped', axis='x'); h.put(x1 + 1, 0, 7, 'stripped', axis='x')
    for x in (2, 4, 6, 8):                                      # flower pots on shelves at the upper windows
        h.add(x, 5, zu - 1, 'shelf', facing='south', half='top', open='false', powered='false', waterlogged='false')
        h.add(x, 6, zu - 1, 'flower_pot')
    for x in (3, 7):                                            # shutters beside the ground windows
        for dx in (-1, 1):
            h.add(x + dx, 2, zg - 1, 'trapdoor', facing='north', **SHUT)
    for z, y in rows.items():                                   # gable trim: upside-down stairs under the overhangs
        if y > 8:
            for x in (0, x1 + 1):
                h.add(x, y - 1, z, 'roof_stairs', facing='south' if z < zm else 'north', **TOP_STAIR)
    h.put(x1 + 1, 14, zm, 'stone_slab', **SLAB)                 # chimney cap
    h.put(x1 - 2, 0, 0, 'chair', facing='south', **STAIR); h.put(x1 - 3, 0, 0, 'chair', facing='south', **STAIR)
    for x in (4, 5):
        h.put(x, 1, 3, 'rug'); h.put(x, 1, zm, 'rug')
    h.put(6, 5, 4, 'rug'); h.put(5, 5, 4, 'rug')
    for z in (3, 5, 6):                                         # flower bed along the left wall
        h.put(0, 0, z, 'soil'); h.put(0, 1, z, 'flower' if z != 5 else 'dandelion')

    s.at(3, 'flourish3')
    for x, plant in ((2, 'dandelion'), (4, 'poppy'), (6, 'cornflower'), (8, 'poppy')):
        h.decorate(x, 6, zu - 1, 'potted_plant', plant=plant)
    h.decorate(mid, 2, 6, 'item_frame', item='minecraft:iron_hoe', facing='north', note='hoe on the back wall')
    for z in (8, 9):                                            # beehives on a bench behind the woodshed
        h.put(x1 + 2, 0, z, 'spruce_slab', **TOP_SLAB)
        h.put(x1 + 2, 1, z, 'beehive', facing='east', honey_level='0')
    h.put(x1 + 3, 0, 8, 'sunflower', half='lower'); h.put(x1 + 3, 1, 8, 'sunflower', half='upper')
    h.put(x1 + 2, 2, 4, 'lantern', **HANG)                      # lantern under the woodshed roof
    windmill(s, cx=5, front=3)
    return s


def _ring_stairs(s, cx, cz, r, y):
    """A ring of stairs (radius r round cx, cz) rising to the centre, corners as outer corners: a hip roof layer."""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            if x not in (cx - r, cx + r) and z not in (cz - r, cz + r):
                continue
            if z in (cz - r, cz + r):
                f = 'south' if z == cz - r else 'north'
            else:
                f = 'east' if x == cx - r else 'west'
            shape = 'straight'
            if x in (cx - r, cx + r) and z in (cz - r, cz + r):
                shape = 'outer_right' if (x == cx - r) == (z == cz - r) else 'outer_left'
            s.put(x, y, z, 'spruce_stairs', facing=f, half='bottom', shape=shape, waterlogged='false')


def windmill(s, cx, front):
    """A tower mill: a 5 x 5 cobblestone base (door at the front), a 5 x 5 plank tower with dark oak corner posts, a
    deck with a reefing stage and fence railing all round, a 3 x 3 plastered upper tower, a hipped spruce cap with a
    finial, a log axle out of the front and four sails (log stock, white wool cloth, fence lattice on the outer edge).
    cx is the tower's centre column, front its front row (z)."""
    xa, xb, za, zb = cx - 2, cx + 2, front, front + 4
    zc = front + 2
    for y in range(0, 7):
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                if not (x in (xa, xb) or z in (za, zb)):
                    continue
                corner = x in (xa, xb) and z in (za, zb)
                if y <= 2:
                    s.put(x, y, z, 'moss' if (x + 2 * y + z) % 5 == 0 else 'base')
                elif corner:
                    s.put(x, y, z, 'dark_frame', axis='y')
                else:
                    s.put(x, y, z, 'oak_planks')
    for y in (0, 1):
        s.remove(cx, y, za)
    s.door(cx, 0, za, 'north')
    s.put(cx, 2, za, 'stone_stairs', facing='north', half='top', shape='straight', waterlogged='false')  # lintel
    for x, z in ((cx, za), (xa, zc), (xb, zc), (cx, zb)):
        s.put(x, 4, z, 'window')
    for x in range(xa + 1, xb):                                  # a plaster band under the stage
        for z in (za, zb):
            s.put(x, 6, z, 'white_terracotta')
    for z in range(za + 1, zb):
        for x in (xa, xb):
            s.put(x, 6, z, 'white_terracotta')
    for x in range(xa - 1, xb + 2):                             # deck and reefing stage (y 7), railing (y 8)
        for z in range(za - 1, zb + 2):
            outer = x in (xa - 1, xb + 1) or z in (za - 1, zb + 1)
            s.put(x, 7, z, 'spruce_slab' if outer else 'spruce_planks', **(SLAB if outer else {}))
            if outer:
                s.put(x, 8, z, 'spruce_fence')
    for y in (8, 9, 10):                                        # upper tower 3 x 3
        for x in range(cx - 1, cx + 2):
            for z in range(zc - 1, zc + 2):
                if (x, z) == (cx, zc):
                    continue
                if x != cx and z != zc:
                    s.put(x, y, z, 'dark_frame', axis='y')
                else:
                    s.put(x, y, z, 'white_terracotta')
    _ring_stairs(s, cx, zc, 2, 11)                              # hipped cap
    _ring_stairs(s, cx, zc, 1, 12)
    s.put(cx, 12, zc, 'spruce_planks')
    s.put(cx, 13, zc, 'spruce_fence'); s.put(cx, 14, zc, 'lantern', **LANTERN)
    hy, sz = 9, front - 2                                       # axle out of the upper tower, sails in z = front - 2
    s.put(cx, hy, zc - 2, 'stripped_spruce_log', axis='z')
    s.put(cx, hy, sz, 'spruce_log', axis='z')                   # hub
    for ax, ay in ((0, 1), (1, 0), (0, -1), (-1, 0)):           # up, right, down, left; lattice sails, turning
        ox, oy = ay, -ax                                         # the side the sail cloth is on
        for i in range(1, 6):
            s.put(cx + ax * i, hy + ay * i, sz, 'stripped_spruce_log', axis='x' if ax else 'y')   # the stock
            for j in (1, 2, 3):
                x, y = cx + ax * i + ox * j, hy + ay * i + oy * j
                frame = j == 3 or i in (1, 5)
                s.put(x, y, sz, 'spruce_fence' if frame else 'white_wool')


# ---------------------------------------------------------------- field modules (one level each)

def field(crop, prop, width=11, border='fence'):
    """A raised field bed the farmer lays out next to the farmhouse: an oak log border, dirt in two blocks of 4 rows,
    an open trench down the middle (the decoration step fills it with water: every row is within 4 blocks of it),
    a fence (or a low hedge) on the border with an entrance at the front centre, reached by a step up from the plank
    boardwalk along the front; a composter on the boardwalk. Farmers till the dirt into farmland and the decoration
    step sows the crop (builders can place neither). `width` leaves room on the right for the crop's own feature."""
    W, D = width, 9
    s = Connected('field_' + crop, (W, 5 if crop == 'wheat' else 3, D), palette='oak_a2')
    w, d = 10, D - 1                              # the bed's right border column
    t = 5                                         # trench column
    s.at(1, 'shell')
    for x in range(0, w + 1):                     # boardwalk path along the front, a step up at the entrance
        if x == t:
            s.put(x, 0, 0, 'spruce_stairs', facing='south', **STAIR)
        else:
            s.put(x, 0, 0, 'spruce_slab', **SLAB)
    for x in range(0, w + 1):                     # log border (axis along the edge)
        s.put(x, 0, 1, 'frame', axis='x'); s.put(x, 0, d, 'frame', axis='x')
    for z in range(2, d):
        s.put(0, 0, z, 'frame', axis='z'); s.put(w, 0, z, 'frame', axis='z')
    s.put(t, 0, 1, 'base'); s.put(t, 0, d, 'base')  # stone ends hold the trench water
    for x in range(1, w):
        for z in range(2, d):
            if x == t:
                s.decorate(x, 0, z, 'water', note='irrigation trench (raised bed: water sits on the ground below)')
            else:
                s.put(x, 0, z, 'dirt')
                s.decorate(x, 0, z, 'farmland', note='tilled by the farmer (D55)')
                s.decorate(x, 1, z, 'crop', block=f'minecraft:{crop}[age=7]')
    edge = [(x, z) for x in range(0, w + 1) for z in (1, d) if not (z == 1 and x == t)] + \
           [(x, z) for z in range(2, d) for x in (0, w)]
    for x, z in edge:
        if border == 'hedge':
            s.put(x, 1, z, 'oak_leaves', **LEAVES)
        else:
            s.put(x, 1, z, 'fence')
    s.at(1, 'fittings')
    for x in (0, w):
        s.put(x, 2, 1, 'lantern', **LANTERN)
    s.put(0, 0, 0, 'composter')
    prop(s, w, d, t)
    return s


def _wheat(s, w, d, t):
    """Scarecrow in the left bed (fence post, hay body and head, fence arms, carpet hat); hay bales by the path."""
    x, z = 2, 5
    s.remove(x, 0, z)
    s.decorations[1] = [e for e in s.decorations[1] if e['pos'][:1] + e['pos'][2:] != [x, z]]
    s.put(x, 0, z, 'coarse_dirt')
    s.put(x, 1, z, 'fence'); s.put(x, 2, z, 'hay_block', axis='y'); s.put(x, 3, z, 'hay_block', axis='y')
    s.put(x - 1, 2, z, 'spruce_fence'); s.put(x + 1, 2, z, 'spruce_fence')
    s.put(x, 4, z, 'brown_carpet')                                       # hat
    s.put(w, 0, 0, 'hay_block', axis='y'); s.put(w - 1, 0, 0, 'hay_block', axis='x'); s.put(w, 1, 0, 'hay_block', axis='x')


def _carrots(s, w, d, t):
    """A low rabbit-proof hedge instead of the fence, and a hand cart of carrots beside the bed (plank bed on
    trapdoor wheels, fence handles, a barrel and a crate of carrots; the carrots in it by the decoration step)."""
    cx = w + 2
    for z in (3, 4, 5):                                                  # cart bed
        s.put(cx, 0, z, 'spruce_slab', **TOP_SLAB)
    for z in (3, 5):                                                     # wheels
        s.put(cx - 1, 0, z, 'oak_trapdoor', facing='west', **SHUT)
        s.put(cx + 1, 0, z, 'oak_trapdoor', facing='east', **SHUT)
    s.put(cx, 1, 3, 'barrel', facing='up', open='false'); s.put(cx, 1, 4, 'composter')
    s.decorate(cx, 1, 4, 'item', item='minecraft:carrot', note='a heap of carrots in the cart')
    s.put(cx - 1, 0, 6, 'fence'); s.put(cx + 1, 0, 6, 'fence')           # handles
    s.put(w, 0, 0, 'barrel', facing='up', open='false')


def _potatoes(s, w, d, t):
    """A root cellar beside the bed: a turf mound on a cobblestone pad, the cellar hatch (a trapdoor lying flat)
    between two cobblestone wall posts, one with a lantern; a sack pile and a chest by the path."""
    xa, xb = w + 1, w + 3
    for x in range(xa, xb + 1):
        for z in range(2, 7):
            s.put(x, 0, z, 'base')
        for z in (4, 5, 6):
            s.put(x, 1, z, 'dirt')
    for z in (5, 6):
        s.put(xa + 1, 2, z, 'dirt')
    s.put(xa, 2, 5, 'oak_leaves', **LEAVES); s.put(xb, 2, 6, 'oak_leaves', **LEAVES)   # bushes on the mound
    s.put(xa + 1, 1, 3, 'spruce_trapdoor', facing='south', half='bottom', open='false', powered='false',
          waterlogged='false')                                             # the hatch
    s.put(xa, 1, 3, 'stone_wall'); s.put(xb, 1, 3, 'stone_wall')
    s.put(xa, 2, 3, 'lantern', **LANTERN)
    s.put(xa + 1, 1, 2, 'stone_stairs', facing='south', **STAIR)         # step down to the hatch
    s.put(w, 0, 0, 'barrel', facing='up', open='false'); s.put(w, 1, 0, 'brown_carpet')
    s.put(w - 1, 0, 0, 'chest', facing='north', type='single', waterlogged='false')


def field_wheat():
    return field('wheat', _wheat)


def field_carrots():
    return field('carrots', _carrots, width=14, border='hedge')


def field_potatoes():
    return field('potatoes', _potatoes, width=14)


_FIELD_DESC = ('A raised field bed, one level: dirt in two blocks of 4 rows each side of an open trench, oak log '
               'border and fence with an entrance at the front centre, plank boardwalk and composter in front. '
               'Builders place the dirt; farmers till it into farmland and the decoration step adds the trench water '
               'and the {crop} (builders can place neither water, farmland nor crops). Modules tile side by side and '
               'around the farmhouse.')
_FIELD_CHANGES = ('New: the field is now a separate one-level module the farmer lays out around his house, with a '
                  'water trench down the middle for hydration and fences that connect.')

STRUCTURES = [
    dict(path='farm', name='Farm (farmhouse)', tier=1, zone='outer', role='farm', group='farm', terrain='ground_level',
         door='front', make=farmhouse, max_height=16,
         description='The farmer\'s house: a big two-storey farmhouse, a cobblestone ground floor with stone brick '
                     'quoins under a dark oak timber frame jettied over the front, spruce roof with dark eaves, brick '
                     'chimney, a stair inside; flower boxes, lanterns, composter, hay and barrel at the door from L1. '
                     'L2 adds a lean-to woodshed, gable trim, potted flowers and a bench; L3 a 15-tall tower windmill '
                     'with lattice sails and beehives. The fields are separate modules (farm_field_*).',
         changes='The house is now a big two-storey farmhouse from L1 (stone ground floor, jettied timber upper '
                 'floor, stairs inside, flower boxes and farm props at the door); L2 adds a woodshed, L3 the windmill '
                 'with lattice sails (fence frame and wool panels) and beehives, so each level adds something visible.'),
    dict(path='farm_field_wheat', name='Farm field: wheat', tier=1, zone='outer', role='farm', group='farm_field',
         terrain='flexible', door=None, make=field_wheat,
         description=_FIELD_DESC.format(crop='wheat') + ' A scarecrow stands in the left bed; hay by the path.',
         changes='A step up from the boardwalk at the entrance, so soldiers can walk into the field; the scarecrow '
                 'and hay mark it as the wheat field.'),
    dict(path='farm_field_carrots', name='Farm field: carrots', tier=1, zone='outer', role='farm', group='farm_field',
         terrain='flexible', door=None, make=field_carrots,
         description=_FIELD_DESC.format(crop='carrots').replace('fence', 'low leaf hedge') +
         ' A low rabbit-proof hedge instead of the fence, and a hand cart of carrots beside the bed.',
         changes='Its own look now: a low rabbit-proof hedge instead of the fence and a hand cart of carrots, plus a '
                 'step up at the entrance so soldiers can walk in.'),
    dict(path='farm_field_potatoes', name='Farm field: potatoes', tier=1, zone='outer', role='farm',
         group='farm_field', terrain='flexible', door=None, make=field_potatoes,
         description=_FIELD_DESC.format(crop='potatoes') + ' A root cellar beside the bed: a turf mound with a '
                     'cellar hatch between stone posts and a lantern; a sack pile and a chest by the path.',
         changes='Its own look now: a root cellar (turf mound, cellar hatch, stone posts, lantern) beside the bed, '
                 'plus a step up at the entrance so soldiers can walk in.'),
]
