"""Wall tower, tier 3 (docs/STRUCTURES.md, wall modules): a square stone tower that the stone wall runs through.

It shares the stone wall's profile (tools/batch3/stone_wall.py): its front (z = 0) is flush with the wall's parapet,
and the wall walk (stand at y 5) enters through doorways in its sides at z = 1 (both sides) and z = 2 (east side).
A wooden door on the town side (z = 4) opens on a stair that climbs to the walk room and on to the archer platform
on the roof, so soldiers reach the whole wall walk from the ground through it. Every step has 2 free blocks above.

Plan (inside x 1..3, z 1..3), steps as (x, z) at y:
  ground floor (stand y 1): door (2, 4) -> (2, 3); steps (1, 3) y1 west, (1, 2) y2 north, (2, 2) y3 east,
  (3, 2) y4 east = the walk room (stand y 5); then (2, 3) y5 west, (1, 3) y6 west, (1, 2) y7 north = the roof.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id
from batch3.stone_wall import masonry, vine, STAIR, TOP, SLAB, LANTERN, HANGING

WALK = 5
ROOF = 7                    # the platform floor (stand y 8); merlons at y 8


def ring_stairs(s, x0, z0, x1, z1, y, block, half='bottom'):
    """One course of a pyramid roof: stairs rising inwards, outer-corner stairs on the corners."""
    corners = {(x0, z0): 'south', (x1, z0): 'west', (x1, z1): 'north', (x0, z1): 'east'}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if (x, z) in corners:
                put_id(s, x, y, z, block, facing=corners[(x, z)], half=half, shape='outer_left', waterlogged='false')
            else:
                f = 'south' if z == z0 else 'north' if z == z1 else 'east' if x == x0 else 'west'
                put_id(s, x, y, z, block, facing=f, half=half, shape='straight', waterlogged='false')


def wall_tower():
    s = Structure('wall_tower', (5, 13, 6), palette='oak_a2')
    s.at(1, 'shell')
    for y in range(0, ROOF):                                         # the tower walls, 5 x 5
        for x in range(5):
            for z in range(5):
                if x in (0, 4) or z in (0, 4):
                    corner = x in (0, 4) and z in (0, 4)
                    band = y == WALK - 1 and not corner        # a cobblestone band at the walk floor
                    put_id(s, x, y, z, 'cobblestone' if band else
                           'stone_bricks' if corner and y > 1 else masonry(x, y, z, seed=7))
    ring_stairs(s, 0, 0, 4, 4, ROOF, 'stone_brick_stairs', half='top')   # top course: a shadow line under the merlons
    s.fill(1, 0, 1, 3, 0, 3, 'cobblestone')                          # ground floor
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 3), (2, 3), (3, 3)):    # walk-room floor (holes over the stairwell)
        put_id(s, x, WALK - 1, z, 'stone_bricks')
    for x, z in ((1, 1), (2, 1), (3, 1), (2, 2), (3, 2), (3, 3)):    # platform floor
        put_id(s, x, ROOF, z, 'cobblestone' if (x + z) % 2 else 'stone_bricks')
    for x in range(5):                                               # merlons on the roof (corners and middles)
        for z in range(5):
            if (x in (0, 4) or z in (0, 4)) and (x + z) % 2 == 0:
                put_id(s, x, ROOF + 1, z, 'stone_bricks')
    for y in (1, 2):                                                 # door opening (town side)
        s.remove(2, y, 4)
    for x, z in ((0, 1), (4, 1), (4, 2)):                            # the wall walk passes through the sides
        for y in (WALK, WALK + 1):
            s.remove(x, y, z)
    for x, y, z in ((2, 2, 0), (0, 2, 2), (4, 2, 2), (2, 6, 0), (0, 6, 3), (4, 6, 3)):   # arrow slits
        s.remove(x, y, z)
    put_id(s, 2, 0, 5, 'cobblestone_stairs', facing='north', **STAIR)   # the step up to the door
    s.at(1, 'fittings')
    s.door(2, 1, 4, 'north', block='spruce_door')
    for i, (x, z, f) in enumerate(((1, 3, 'west'), (1, 2, 'north'), (2, 2, 'east'), (3, 2, 'east'))):
        put_id(s, x, 1 + i, z, 'stone_brick_stairs', facing=f, **STAIR)
    for i, (x, z, f) in enumerate(((2, 3, 'west'), (1, 3, 'west'), (1, 2, 'north'))):
        put_id(s, x, WALK + i, z, 'spruce_stairs', facing=f, **STAIR)
    put_id(s, 3, 1, 3, 'barrel', facing='up', open='false')          # stores under the walk room
    put_id(s, 3, 1, 1, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, 1, 1, 1, 'barrel', facing='up', open='false')
    put_id(s, 3, ROOF + 1, 2, 'chest', facing='west', type='single', waterlogged='false')   # the archer's arrows
    put_id(s, 2, 3, 3, 'wall_torch', facing='north')                 # light over the door, on the inside
    put_id(s, 2, WALK + 1, 1, 'wall_torch', facing='south')          # light in the walk room
    s.at(2, 'flourish2')
    for x, y, z in ((2, 2, 0), (0, 2, 2), (4, 2, 2), (2, 6, 0), (0, 6, 3), (4, 6, 3)):
        put_id(s, x, y, z, 'iron_bars')
    put_id(s, 2, 4, 5, 'red_wall_banner', facing='south')            # banner over the door
    s.decorate(2, 4, 5, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for x in (1, 3):                                                 # lanterns beside the door
        put_id(s, x, 2, 5, 'wall_torch', facing='south')
    put_id(s, 2, ROOF + 1, 2, 'lantern', **LANTERN)                  # a lantern in the middle of the platform
    put_id(s, 3, ROOF + 1, 3, 'barrel', facing='up', open='false')
    s.at(3, 'flourish3')
    # a pointed roof on the merlons (archers shoot between them): spruce courses at 45 degrees, a flag on top
    ring_stairs(s, 0, 0, 4, 4, ROOF + 2, 'spruce_stairs')
    ring_stairs(s, 1, 1, 3, 3, ROOF + 3, 'spruce_stairs')
    put_id(s, 2, ROOF + 3, 2, 'spruce_planks')
    put_id(s, 2, ROOF + 4, 2, 'dark_oak_fence')
    put_id(s, 2, ROOF + 5, 2, 'red_banner', rotation='8')
    s.decorate(2, ROOF + 5, 2, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in (1, 3):                                                 # moss on the platform
        put_id(s, x, ROOF + 1, 1, 'moss_carpet')
    for y in (1, 2, 3):                                              # ivy beside the door
        vine(s, 1, y, 5, 'north') if y != 2 else None
        vine(s, 3, y, 5, 'north') if y != 2 else None
    put_id(s, 0, 0, 5, 'barrel', facing='up', open='false')          # supplies by the door
    put_id(s, 4, 0, 5, 'hay_block', axis='y')
    return connect(s)


STRUCTURES = [dict(path='wall_tower', name='Wall tower', tier=3, zone='wall', role='defense', group='wall_tower',
                   terrain='ground_level', door='back', changes='First version.', make=wall_tower, max_height=13,
                   description='Needs scaffolding: 13 tall (level 3 cone roof; levels 1 and 2 are 9 tall). Over budget: 205 blocks vs 160. Square stone '
                               'tower the wall walk runs through (stand at 5); a door on the town side and a stone stair '
                               'up to the walk room and the archer platform on the roof.')]
