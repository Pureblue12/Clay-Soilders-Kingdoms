"""Wall tower, tier 3 (docs/STRUCTURES.md, wall modules): a square stone tower that the stone wall runs through.

It shares the stone wall's profile (tools/batch3/stone_wall.py): its front (tower z = 0) is flush with the wall's
parapet, and the 2-wide wall walk (stand at y 5) enters through doorways in both sides at z = 1 and 2.
The box is 1 wider than the tower on every side (OX, OZ) so the level 3 roof overhangs: the tower's front wall is at
box z = 1, its sides at box x = 1 and 5 (place it 1 block forward and 1 to the left of the wall line).
A wooden door on the town side (z = 4) opens on a stair that climbs to the walk room and on to the archer platform
on the roof, so soldiers reach the whole wall walk from the ground through it. Every step has 2 free blocks above.

Plan in tower coordinates (inside x 1..3, z 1..3), steps as (x, z) at y:
  ground floor (stand y 1): door (2, 4) -> (2, 3); steps (1, 3) y1 west, (1, 2) y2 north, (2, 2) y3 east,
  (3, 2) y4 east = the walk room (stand y 5); then (2, 3) y5 west, (1, 3) y6 west, (1, 2) y7 north = the roof.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id
from batch3.stone_wall import masonry, vine, STAIR, TOP, SLAB, LANTERN, HANGING

WALK = 5
ROOF = 7                    # the platform floor (stand y 8); merlons at y 8
OX, OZ = 1, 1               # the tower's (0, 0) in the box: the box is 1 wider on every side for the roof overhang
SLITS = ((2, 2, 0), (0, 2, 2), (4, 2, 2), (2, 6, 0), (0, 6, 3), (4, 6, 3))


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
    """Coordinates below are the tower's own (tower x 0..4, z 0..4, door apron z 5); the box is 1 wider on every side
    (OX, OZ) so the level 3 roof can overhang."""
    s = Structure('wall_tower', (7, 13, 7), palette='oak_a2')

    def P(x, y, z, name, **p):
        return put_id(s, x + OX, y, z + OZ, name, **p)

    def R(x, y, z):
        s.remove(x + OX, y, z + OZ)

    def ring(x0, z0, x1, z1, y, block, half='bottom'):
        ring_stairs(s, x0 + OX, z0 + OZ, x1 + OX, z1 + OZ, y, block, half=half)

    s.at(1, 'shell')
    for y in range(0, ROOF):                                         # the tower walls, 5 x 5
        for x in range(5):
            for z in range(5):
                if x in (0, 4) or z in (0, 4):
                    corner = x in (0, 4) and z in (0, 4)
                    band = y == WALK - 1 and not corner        # a cobblestone band at the walk floor
                    P(x, y, z, 'cobblestone' if band else
                      'stone_bricks' if corner and y > 1 else masonry(x, y, z, seed=7))
    ring(0, 0, 4, 4, ROOF, 'stone_brick_stairs', half='top')        # top course: a shadow line under the merlons
    for x in range(1, 4):                                            # ground floor
        for z in range(1, 4):
            P(x, 0, z, 'cobblestone')
    for x, z in ((1, 1), (2, 1), (3, 1), (1, 3), (2, 3), (3, 3)):    # walk-room floor (holes over the stairwell)
        P(x, WALK - 1, z, 'stone_bricks')
    for x, z in ((1, 1), (2, 1), (3, 1), (2, 2), (3, 2), (3, 3)):    # platform floor
        P(x, ROOF, z, 'cobblestone' if (x + z) % 2 else 'stone_bricks')
    for x in range(5):                                               # merlons on the roof (corners and middles)
        for z in range(5):
            if (x in (0, 4) or z in (0, 4)) and (x + z) % 2 == 0:
                P(x, ROOF + 1, z, 'stone_bricks')
    for y in (1, 2):                                                 # door opening (town side)
        R(2, y, 4)
    for x, z in ((0, 1), (0, 2), (4, 1), (4, 2)):                    # the 2-wide wall walk passes through the sides
        for y in (WALK, WALK + 1):
            R(x, y, z)
    for x, y, z in SLITS:                                            # arrow slits
        R(x, y, z)
    P(2, 0, 5, 'cobblestone_stairs', facing='north', **STAIR)        # the step up to the door
    s.at(1, 'fittings')
    s.door(2 + OX, 1, 4 + OZ, 'north', block='spruce_door')
    for i, (x, z, f) in enumerate(((1, 3, 'west'), (1, 2, 'north'), (2, 2, 'east'), (3, 2, 'east'))):
        P(x, 1 + i, z, 'stone_brick_stairs', facing=f, **STAIR)
    for i, (x, z, f) in enumerate(((2, 3, 'west'), (1, 3, 'west'), (1, 2, 'north'))):
        P(x, WALK + i, z, 'spruce_stairs', facing=f, **STAIR)
    P(3, 1, 3, 'barrel', facing='up', open='false')                  # stores under the walk room
    P(3, 1, 1, 'chest', facing='west', type='single', waterlogged='false')
    P(1, 1, 1, 'barrel', facing='up', open='false')
    P(3, ROOF + 1, 2, 'chest', facing='west', type='single', waterlogged='false')   # the archer's arrows
    P(2, 3, 3, 'wall_torch', facing='north')                         # light over the door, on the inside
    P(2, WALK + 1, 1, 'wall_torch', facing='south')                  # light in the walk room
    s.at(2, 'flourish2')
    for x, y, z in SLITS:
        P(x, y, z, 'iron_bars')
    P(2, 4, 5, 'red_wall_banner', facing='south')                    # banner over the door
    s.decorate(2 + OX, 4, 5 + OZ, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    for x in (1, 3):                                                 # torches beside the door
        P(x, 2, 5, 'wall_torch', facing='south')
    P(2, ROOF + 1, 2, 'lantern', **LANTERN)                          # a lantern in the middle of the platform
    P(3, ROOF + 1, 3, 'barrel', facing='up', open='false')
    s.at(3, 'flourish3')
    # a pointed roof on the merlons (archers shoot between them): spruce courses at 45 degrees with one block of
    # overhang all round, a flag on top
    ring(-1, -1, 5, 5, ROOF + 2, 'spruce_stairs')
    ring(0, 0, 4, 4, ROOF + 3, 'spruce_stairs')
    ring(1, 1, 3, 3, ROOF + 4, 'spruce_stairs')
    P(2, ROOF + 3, 2, 'spruce_planks')                               # the king post the flag stands on
    P(2, ROOF + 4, 2, 'dark_oak_fence')
    P(2, ROOF + 5, 2, 'red_banner', rotation='8')
    s.decorate(2 + OX, ROOF + 5, 2 + OZ, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in (1, 3):                                                 # moss on the platform
        P(x, ROOF + 1, 1, 'moss_carpet')
    for x in (1, 3):                                                 # ivy beside the door
        for y in (1, 3):
            vine(s, x + OX, y, 5 + OZ, 'north')
    P(0, 0, 5, 'barrel', facing='up', open='false')                  # supplies by the door
    P(4, 0, 5, 'hay_block', axis='y')
    return connect(s)


STRUCTURES = [dict(path='wall_tower', name='Wall tower', tier=3, zone='wall', role='defense', group='wall_tower',
                   terrain='ground_level', door='back', make=wall_tower, max_height=13,
                   changes='Quality check round 1: the wall walk enters the tower 2 wide from both sides (it was 1 wide from '
                           'the west); the level 3 cone roof overhangs 1 block all round (the box is 1 wider on every '
                           'side for it, so the tower front is at z 1).',
                   description='Needs scaffolding: 13 tall (level 3 cone roof; levels 1 and 2 are 9 tall). Over budget: 227 blocks vs 160. Box 7 x 7 '
                               'for the roof overhang: the tower stands at x 1..5, z 1..5. Square stone '
                               'tower the wall walk runs through (stand at 5); a door on the town side and a stone stair '
                               'up to the walk room and the archer platform on the roof.')]
