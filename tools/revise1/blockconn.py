"""Shared helpers for the visitors' camp, road, fire pit and keep revisions (no structures of its own).

- connect(s): sets the north/east/south/west (and wall 'up') states of every fence, wall, pane and iron bar so they
  join their neighbours. Neither the Studio nor a structure .nbt updates them on their own. A block only looks at
  neighbours of its own level or earlier (levels only add: an L1 fence must keep its state in L2).
- noise(x, y, z, seed): the Studio's fixed per-position noise (studio/samples/builder.ts), for mixing variants with a
  fixed pattern instead of choices by hand.
"""

STRUCTURES = []   # helper module: nothing to build

DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
FULL = {'cobblestone', 'mossy_cobblestone', 'gravel', 'dirt', 'coarse_dirt', 'packed_mud', 'mud_bricks', 'andesite',
        'polished_andesite', 'stone', 'hay_block', 'barrel', 'crafting_table', 'furnace', 'smoker', 'glowstone',
        'chiseled_stone_bricks', 'bookshelf', 'smooth_stone'}


def _imul(a, b):
    return (a * b) & 0xFFFFFFFF


def noise(x, y, z, seed=0):
    """Deterministic 0..1 per position; the same numbers as noise() in studio/samples/builder.ts."""
    h = _imul(x + 1013, 374761393) ^ _imul(y + 7, 668265263) ^ _imul(z + 31, 2246822519) ^ _imul(seed + 1, 3266489917)
    h = _imul(h ^ (h >> 13), 1274126177)
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967295


def kind(name):
    if name.endswith('_fence_gate'):
        return 'gate'
    if name.endswith('_fence'):
        return 'fence'
    if name.endswith('_wall') and not name.endswith(('wall_torch', 'wall_sign', 'wall_banner', 'wall_hanging_sign')):
        return 'wall'
    if name.endswith('glass_pane') or name == 'iron_bars':
        return 'pane'
    return None


def full(name):
    return name in FULL or name.endswith(('_planks', '_log', '_wood', '_wool', 'bricks', 'terracotta'))


def connect(s):
    for (x, y, z), (name, props, level) in list(s.blocks.items()):
        k = kind(name)
        if k not in ('fence', 'wall', 'pane'):
            continue

        def near(dx, dy, dz):
            b = s.blocks.get((x + dx, y + dy, z + dz))
            return b if b and b[2] <= level else None

        joined = {}
        for d, (dx, dz) in DIRS.items():
            b = near(dx, 0, dz)
            nk = kind(b[0]) if b else None
            if not b:
                joined[d] = False
            elif k == 'fence':
                joined[d] = full(b[0]) or nk == 'gate' or (nk == 'fence' and (b[0] == 'nether_brick_fence') == (name == 'nether_brick_fence'))
            else:   # walls and panes join each other, gates (walls) and full blocks
                joined[d] = full(b[0]) or nk in ('wall', 'pane') or (k == 'wall' and nk == 'gate')
        p = dict(props)
        if k == 'wall':
            above = near(0, 1, 0)
            straight = joined == {'north': True, 'south': True, 'east': False, 'west': False} or \
                joined == {'north': False, 'south': False, 'east': True, 'west': True}
            up = not straight or bool(above and (kind(above[0]) == 'wall' and above[1].get('up') == 'true'))
            tall = bool(above and (full(above[0]) or kind(above[0]) == 'wall'))
            for d, j in joined.items():
                p[d] = ('tall' if tall else 'low') if j else 'none'
            p['up'] = str(up).lower()
        else:
            for d, j in joined.items():
                p[d] = str(j).lower()
        p['waterlogged'] = 'false'
        s.blocks[(x, y, z)] = (name, p, level)
    return s


def put_id(s, x, y, z, name, **props):
    """Places a block by its exact id, also when the id is a palette role name (batch 1's palette maps 'stone_slab' to
    cobblestone_slab, 'wall' to oak planks...). Same checks as Structure.put."""
    saved = s.p
    s.p = {}
    try:
        return s.put(x, y, z, name, **props)
    finally:
        s.p = saved
