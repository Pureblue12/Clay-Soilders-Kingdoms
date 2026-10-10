"""Shared helpers for the batch-5 military buildings (gatehouse, armory, guard quarters, siege workshop): no structures
of its own.

- masonry(x, y, z, seed): the stone wall's mix (tools/batch3/stone_wall.py, copied so this kit doesn't depend on that
  module's internals): a mossy cobblestone / cobblestone footing, then stone bricks with a few cobblestone patches.
  All KNOWN-OK, so level 1 can use it.
- ring_stairs(...): one course of stairs round a rectangle, facing inwards (a pyramid or cone course), corner stairs
  turned outwards.
- cone(...): a steep turret cone (2 up for every 1 in) over a square tower, with one block of eave overhang.
- vine(...): a vine on one face.
"""
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, noise  # noqa: F401

STRUCTURES = []   # helper module: nothing to build

NO_VINE = dict(north='false', south='false', east='false', west='false', up='false')
INTO = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def masonry(x, y, z, seed=3):
    n = noise(x, y, z, seed)
    if y == 0:
        return 'mossy_cobblestone' if n < 0.4 else 'cobblestone'
    if y == 1:
        return 'cobblestone' if n < 0.45 else 'stone_bricks'
    return 'cobblestone' if n < 0.14 else 'stone_bricks'


def ring_stairs(s, x0, z0, x1, z1, y, block, half='bottom', add=False):
    """Stairs round the rectangle x0..x1, z0..z1 at y, each rising towards the inside (corner stairs as outer
    corners). add=True leaves positions that already have a block alone."""
    put = A if add else P
    corners = {(x0, z0): 'south', (x1, z0): 'west', (x1, z1): 'north', (x0, z1): 'east'}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if (x, z) in corners:
                put(s, x, y, z, block, facing=corners[(x, z)], half=half, shape='outer_left', waterlogged='false')
            else:
                f = 'south' if z == z0 else 'north' if z == z1 else 'east' if x == x0 else 'west'
                put(s, x, y, z, block, facing=f, half=half, shape='straight', waterlogged='false')


def ring(s, x0, z0, x1, z1, y, block, add=False, **props):
    put = A if add else P
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put(s, x, y, z, block, **props)


def cone(s, x0, z0, x1, z1, y, stairs='spruce_stairs', body='spruce_planks', eave=None):
    """A steep turret cone over the square tower x0..x1, z0..z1 (odd size) starting at y: an eave course one block
    out, then each ring two stair courses high (shingles: 2 up for every 1 in) and one block further in, up to a
    centre post. Every visible block is a stair of one material, so it reads as one cone, not stacked tiers.
    Returns the y above the last block (where a finial goes)."""
    ring_stairs(s, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, eave or stairs)
    k, yy = 0, y
    while x0 + k < x1 - k:
        ring_stairs(s, x0 + k, z0 + k, x1 - k, z1 - k, yy, stairs)
        ring_stairs(s, x0 + k, z0 + k, x1 - k, z1 - k, yy + 1, stairs)
        k += 1
        yy += 2
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    P(s, cx, yy, cz, body)
    return yy + 1


def vine(s, x, y, z, side):
    """A vine at (x, y, z) clinging to the block on its `side` (the side it hangs against)."""
    P(s, x, y, z, 'vine', **{**NO_VINE, side: 'true'})
