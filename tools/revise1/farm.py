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

    House, drawn in its own x (0 = left overhang, walls 1..9, 10 = right overhang and chimney): a 9 x 5 timber-framed
    farmhouse (dark oak posts and top plates, white plaster panels, a cobblestone plinth with moss) under a spruce
    gable roof with one block of overhang all round, a brick chimney on the right gable. Inside: hearth on the brick
    fireback, bed, table and chair, chest, barrel, torches; the composter (the farmer's workstation) and hay stand
    outside under the front eave.
    Level 2: a front porch (log posts, slab roof, hanging lantern), shutters, flower boxes, gable trim, a woodpile, a
    bench, a chopping block, a crafting table and barrel, a rug, a hay loft over the bed. Level 3: flowers in the
    boxes, the hoe on the wall, a rain barrel, and the windmill."""
    W, D, H = 20, 10, 15
    s = Connected('farm', (W, H, D), palette='oak_a2')
    h = _Shift(s, 9)
    x0, x1, z0, z1 = 1, 9, 2, 6                    # walls
    mid, zm = 5, 4                                 # door column, ridge row
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):                    # plinth and floor
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                h.put(x, 0, z, 'moss' if (x * 3 + z * 5) % 7 == 0 else 'base')
            else:
                h.put(x, 0, z, 'floor')
    posts = {(x0, z0), (x1, z0), (x0, z1), (x1, z1), (4, z0), (6, z0), (mid, z1)}
    windows = [(2, z0), (3, z0), (7, z0), (8, z0), (x0, zm), (3, z1), (7, z1)]
    for y in (1, 2, 3):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if (x, z) in posts:
                    h.put(x, y, z, 'dark_frame', axis='y')
                elif (x, z) == (mid, z0) and y < 3:
                    continue                                    # doorway
                elif (x, z) in windows and y == 2:
                    h.put(x, y, z, 'window')
                elif x == x1 and z == zm:
                    h.put(x, y, z, 'chimney')                   # brick fireback behind the hearth
                else:
                    h.put(x, y, z, 'white_terracotta')
    h.put(mid, 3, z0, 'dark_frame', axis='x')                   # lintel
    h.door(mid, 1, z0, 'north')
    for x in range(x0, x1 + 1):                                 # top plates the roof sits on
        h.put(x, 4, z0, 'dark_frame', axis='x'); h.put(x, 4, z1, 'dark_frame', axis='x')
    for z in range(z0 + 1, z1):
        h.put(x0, 4, z, 'dark_frame', axis='z'); h.put(x1, 4, z, 'dark_frame', axis='z')
    for y in range(0, 9):                                       # chimney on the right gable, through the overhang
        h.put(x1 + 1, y, zm, 'chimney')
    rows = {1: 4, 2: 5, 3: 6, 5: 6, 6: 5, 7: 4}                 # gable roof, ridge along x, overhang all round
    for z, y in rows.items():
        for x in range(0, x1 + 2):
            if not h.has(x, y, z):
                h.put(x, y, z, 'roof_stairs', facing='south' if z < zm else 'north', **STAIR)
    for x in range(0, x1 + 1):
        h.put(x, 7, zm, 'roof_slab', **SLAB)                    # ridge
    for x in (x0, x1):                                          # gables: plaster around a dark oak king post
        h.put(x, 5, 3, 'white_terracotta'); h.put(x, 5, 5, 'white_terracotta')
        h.put(x, 5, zm, 'dark_frame', axis='y'); h.put(x, 6, zm, 'dark_frame', axis='y')
    h.put(mid, 0, 1, 'stone_stairs', facing='south', **STAIR)   # doorstep
    s.at(1, 'fittings')
    h.put(x1 - 1, 0, z0 - 1, 'composter')                       # the workstation, under the front eave
    h.put(x1, 0, z0 - 1, 'hay_block', axis='y')
    for x in (mid - 1, mid + 1):                                # a light each side of the door
        h.put(x, 2, z0 - 1, 'wall_torch', facing='north')
    h.put(x1 - 1, 1, zm, 'hearth', facing='west', lit='false')  # hearth against the brick fireback
    h.put(x1 - 1, 1, 3, 'barrel', facing='up', open='false')
    h.put(x1 - 1, 1, 5, 'chest', facing='west', type='single', waterlogged='false')
    h.put(6, 1, 5, 'roof_slab', **SLAB)                         # table (a slab: soldiers step on and off)
    h.put(6, 1, zm, 'chair', facing='south', **STAIR)
    h.bed(2, 1, zm, 'south')                                    # bed along the left wall, head at the back
    h.put(2, 1, 3, 'chest', facing='east', type='single', waterlogged='false')
    for x, z, f in ((3, 3, 'south'), (7, 3, 'south'), (4, 5, 'north')):
        h.put(x, 3, z, 'wall_torch', facing=f)

    s.at(2, 'flourish2')
    for x in (mid - 2, mid + 2):                                # porch: log posts, slab roof under the eave
        for y in (0, 1, 2):
            h.put(x, y, 0, 'spruce_fence')
    for x in range(mid - 2, mid + 3):
        h.put(x, 3, 1, 'roof_slab', **TOP_SLAB)
        if x not in (mid - 2, mid + 2):
            h.put(x, 3, 0, 'roof_slab', **TOP_SLAB)
    h.put(mid, 2, 0, 'lantern', **HANG)
    for x in (2, 3, 7, 8):                                      # flower boxes under the front windows
        h.put(x, 1, z0 - 1, 'shelf', facing='south', half='top', open='false', powered='false', waterlogged='false')
    for x in (x0, x1):                                          # outer shutters of the front windows
        h.put(x, 2, z0 - 1, 'trapdoor', facing='north', **SHUT)
    for x in (3, 7):                                            # back windows
        for dx in (-1, 1):
            h.put(x + dx, 2, z1 + 1, 'trapdoor', facing='south', **SHUT)
    for dz in (-1, 1):                                          # left window
        h.put(x0 - 1, 2, zm + dz, 'trapdoor', facing='west', **SHUT)
    for z, y in rows.items():                                   # gable trim: upside-down stairs under the overhangs
        if y > 4:
            for x in (0, x1 + 1):
                h.add(x, y - 1, z, 'roof_stairs', facing='south' if z < zm else 'north', **TOP_STAIR)
    h.put(x1 + 1, 9, zm, 'stone_slab', **SLAB)                  # chimney cap
    for z in (5, 6):                                            # woodpile against the right gable
        h.put(x1 + 1, 0, z, 'stripped', axis='z'); h.put(x1 + 1, 1, z, 'stripped', axis='z')
    h.put(x1 + 1, 0, 3, 'hay_block', axis='z'); h.put(x1 + 1, 0, 2, 'hay_block', axis='z')
    h.put(x1 + 1, 1, 2, 'hay_block', axis='x')
    h.put(2, 0, 0, 'stripped', axis='y')                        # chopping block
    h.put(x1 - 1, 0, 0, 'chair', facing='south', **STAIR)       # bench by the porch
    h.put(x1, 0, 0, 'chair', facing='south', **STAIR)
    h.put(7, 1, 5, 'crafting_table')
    h.put(4, 1, 5, 'barrel', facing='up', open='false')
    for x in (4, 5):                                            # rug
        h.put(x, 1, 3, 'rug'); h.put(x, 1, zm, 'rug')
    h.put(6, 4, zm, 'chain', axis='y'); h.put(6, 3, zm, 'lantern', **HANG)
    for x in (2, 3, 4):                                         # hay loft over the bed end
        for z in (3, 4, 5):
            h.put(x, 4, z, 'spruce_slab', **SLAB)
    h.put(2, 5, zm, 'hay_block', axis='x'); h.put(3, 5, zm, 'hay_block', axis='x')
    for z in (2, 6):                                            # flowers by the left wall
        h.put(x0 - 1, 0, z, 'soil'); h.put(x0 - 1, 1, z, 'flower')

    s.at(3, 'flourish3')
    for x, plant in ((2, 'dandelion'), (3, 'poppy'), (7, 'poppy'), (8, 'cornflower')):
        h.put(x, 2, z0 - 1, 'flower_pot'); h.decorate(x, 2, z0 - 1, 'potted_plant', plant=plant)
    h.decorate(5, 2, z1 - 1, 'item_frame', item='minecraft:iron_hoe', facing='north', note='hoe on the back wall')
    h.put(x1 + 1, 0, z1 + 1, 'barrel', facing='up', open='false')    # rain barrel at the back corner
    h.put(x1 + 1, 1, z1 + 1, 'oak_pressure_plate', powered='false')  # its lid
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
    for ax, ay in ((0, 1), (1, 0), (0, -1), (-1, 0)):           # up, right, down, left; cloth on one side, turning
        ox, oy = ay, -ax
        for i in range(1, 6):
            x, y = cx + ax * i, hy + ay * i
            s.put(x, y, sz, 'stripped_spruce_log', axis='x' if ax else 'y')
            if i >= 2:
                s.put(x + ox, y + oy, sz, 'white_wool')
                s.put(x + 2 * ox, y + 2 * oy, sz, 'spruce_fence')


# ---------------------------------------------------------------- field modules (one level each)

def field(crop, prop):
    """A raised field bed the farmer lays out next to the farmhouse: an oak log border, dirt in two blocks of 4 rows,
    an open trench down the middle (the decoration step fills it with water: every row is within 4 blocks of it),
    a fence on the border with an entrance at the front centre, a plank boardwalk along the front with a composter.
    Farmers till the dirt into farmland and the decoration step sows the crop (builders can place neither)."""
    W, D = 11, 9
    s = Connected('field_' + crop, (W, 5 if crop == 'wheat' else 3, D), palette='oak_a2')
    w, d = W - 1, D - 1
    t = W // 2                                    # trench column
    s.at(1, 'shell')
    for x in range(0, W):                         # boardwalk path along the front (half a step up)
        s.put(x, 0, 0, 'spruce_slab', **SLAB)
    for x in range(0, W):                         # log border (axis along the edge)
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
    for x in range(0, W):
        for z in (1, d):
            if not (z == 1 and x == t):           # entrance over the trench's front stone
                s.put(x, 1, z, 'fence')
    for z in range(2, d):
        s.put(0, 1, z, 'fence'); s.put(w, 1, z, 'fence')
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
    """Two barrels by the path (the harvest) and a lantern post at the back."""
    s.put(w, 0, 0, 'barrel', facing='up', open='false'); s.put(w - 1, 0, 0, 'barrel', facing='up', open='false')


def _potatoes(s, w, d, t):
    """A sack pile (brown carpet on barrels) and a chest by the path."""
    s.put(w, 0, 0, 'barrel', facing='up', open='false'); s.put(w, 1, 0, 'brown_carpet')
    s.put(w - 1, 0, 0, 'chest', facing='north', type='single', waterlogged='false')


def field_wheat():
    return field('wheat', _wheat)


def field_carrots():
    return field('carrots', _carrots)


def field_potatoes():
    return field('potatoes', _potatoes)


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
         description='The farmer\'s house: a 9 x 5 timber-framed farmhouse (plaster in dark oak, spruce gable roof, '
                     'brick chimney) with hearth, bed, table, chests; composter and hay under the front eave. L2 adds '
                     'a porch, shutters, flower boxes, gable trim, woodpile, hay loft; L3 a 15-tall tower windmill '
                     'beside it. The fields are separate modules (farm_field_*).',
         changes='Rebuilt as one big, detailed timber farmhouse the farmer lives in (the fields are now separate '
                 'one-level modules around it), with fences that connect and, at L3, a much bigger tower windmill '
                 'with a stage, cap and four wool sails.'),
    dict(path='farm_field_wheat', name='Farm field: wheat', tier=1, zone='outer', role='farm', group='farm_field',
         terrain='flexible', door=None, make=field_wheat,
         description=_FIELD_DESC.format(crop='wheat') + ' A scarecrow stands in the left bed.',
         changes=_FIELD_CHANGES),
    dict(path='farm_field_carrots', name='Farm field: carrots', tier=1, zone='outer', role='farm', group='farm_field',
         terrain='flexible', door=None, make=field_carrots,
         description=_FIELD_DESC.format(crop='carrots') + ' Harvest barrels by the path.',
         changes=_FIELD_CHANGES),
    dict(path='farm_field_potatoes', name='Farm field: potatoes', tier=1, zone='outer', role='farm',
         group='farm_field', terrain='flexible', door=None, make=field_potatoes,
         description=_FIELD_DESC.format(crop='potatoes') + ' A sack pile and a chest by the path.',
         changes=_FIELD_CHANGES),
]
