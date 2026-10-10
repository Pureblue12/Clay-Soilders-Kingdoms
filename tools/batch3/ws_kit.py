"""Shared helpers for the batch-3 workshops (smithy, tannery, butcher, bakery, well_small): no structures of its own.

- P(s, x, y, z, id, **props): put a block by its exact id (palette role names don't apply).
- gable_roof(...): a 45-degree gable roof with the ridge along z (a ridge block capped with slabs), top plates, dark eaves, one block of overhang at the
  sides and `front` / `back` rows of overhang at the gable ends, filled gable walls, and corbels (upside-down stairs)
  under the gable overhang.
"""
from revise1.blockconn import connect, noise, put_id  # noqa: F401  (re-exported for the workshop modules)

STRUCTURES = []   # helper module: nothing to build

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
SLAB_TOP = dict(type='top', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
LEAVES = dict(persistent='true', distance='1', waterlogged='false')


def P(s, x, y, z, name, **props):
    return put_id(s, x, y, z, name, **props)


def A(s, x, y, z, name, **props):
    """Like P, but leaves a position that already has a block alone."""
    if (x, y, z) not in s.blocks:
        return put_id(s, x, y, z, name, **props)


def gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, slope='spruce_stairs', eave='dark_oak_stairs',
               ridge='dark_oak_planks', cap='spruce_slab', gable='spruce_planks', plates='dark_oak_log', skip=(),
               corbels=True, gable_at=None):
    """Roof over walls x0..x1 (odd width) and z0..z1 whose last wall layer is `top`. Positions in `skip` stay empty
    (a chimney passes there). gable_at: the z rows that get a filled gable wall (default z0 and z1)."""
    skip = set(skip)
    half = (x1 - x0) // 2
    mid = x0 + half

    def put(x, y, z, name, **p):
        if (x, y, z) not in skip:
            A(s, x, y, z, name, **p)

    for z in range(z0, z1 + 1):                                   # top plates on the eave walls
        for x in (x0, x1):
            put(x, top + 1, z, plates, axis='z')
    for z in range(z0 - front, z1 + back + 1):
        put(x0 - 1, top + 1, z, eave, facing='east', **STAIR)
        put(x1 + 1, top + 1, z, eave, facing='west', **STAIR)
        for i in range(half):
            put(x0 + i, top + 2 + i, z, slope, facing='east', **STAIR)
            put(x1 - i, top + 2 + i, z, slope, facing='west', **STAIR)
        put(mid, top + 1 + half, z, ridge)
        if cap:
            put(mid, top + 2 + half, z, cap, **SLAB)                  # the ridge finished with slabs
    for z in (gable_at if gable_at is not None else (z0, z1)):     # gable walls
        for x in range(x0 + 1, x1):
            i = min(x - x0, x1 - x)
            roof_y = top + 2 + i if i < half else top + 1 + half
            for y in range(top + 1, roof_y):
                put(x, y, z, gable)
    if corbels:                                                    # upside-down stairs under the gable overhangs
        for z in ([z0 - front] if front else []) + ([z1 + back] if back else []):
            for i in range(half):
                put(x0 + i, top + 1 + i, z, slope, facing='west', **TOP)
                put(x1 - i, top + 1 + i, z, slope, facing='east', **TOP)
    return mid


def shutters(s, x, y, z, side, block='spruce_trapdoor'):
    """Open trapdoor shutters on both sides of a window in a wall. side: the outward direction
    ('north', 'south', 'east', 'west')."""
    dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[side]
    if dx:
        for d in (-1, 1):
            P(s, x + dx, y, z + d, block, facing=side, **OPEN)
    else:
        for d in (-1, 1):
            P(s, x + d, y, z + dz, block, facing=side, **OPEN)


def flower_box(s, x, y, z, side, flower, block='spruce_trapdoor'):
    """A flower box below a window: soil with a flower, a trapdoor face outside it.
    (x, y, z) is the soil block, already one out from the wall; side is the outward direction."""
    dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[side]
    P(s, x, y, z, 'coarse_dirt')
    P(s, x + dx, y, z + dz, block, facing=side, **OPEN)
    P(s, x, y + 1, z, flower)
