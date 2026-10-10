"""Watchtower (tier 2, look A): a log tower on four posts with a spiral stair inside and a roofed lookout platform."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', waterlogged='false')


def hip_ring(s, x0, z0, x1, z1, y, block, half='bottom'):
    """A ring of stairs rising inwards (one course of a hip roof); corners are outer corners."""
    corners = {(x0, z0): 'south', (x1, z0): 'west', (x1, z1): 'north', (x0, z1): 'east'}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in corners:
                s.put(x, y, z, block, facing=corners[(x, z)], half=half, shape='outer_left', waterlogged='false')
            elif z == z0:
                s.put(x, y, z, block, facing='south', half=half, shape='straight', waterlogged='false')
            elif z == z1:
                s.put(x, y, z, block, facing='north', half=half, shape='straight', waterlogged='false')
            elif x == x0:
                s.put(x, y, z, block, facing='east', half=half, shape='straight', waterlogged='false')
            elif x == x1:
                s.put(x, y, z, block, facing='west', half=half, shape='straight', waterlogged='false')


def watchtower(floor_y=4):
    """Watchtower: four oak log posts (4 x 4) round a 2 x 2 spiral of stairs, a spruce plank platform at floor_y
    with a fence rail, and a two-course spruce hip roof overhanging by one. A 3 x 3 tower (the brief) has no room
    for a stair soldiers can walk (they need 2 free blocks above every step), hence 4 x 4."""
    s = Structure('watchtower', (6, 9, 6), palette='oak_a2')
    posts = [(1, 1), (4, 1), (1, 4), (4, 4)]
    # spiral: (x, z, facing) of the steps at y = 0, 1, 2, ...; entered from the front at (2, 1); exit east at floor_y
    spiral = [(2, 2, "south"), (3, 2, "east"), (3, 3, "south"), (2, 3, "west"), (2, 2, "north")][:floor_y]
    holes = {(spiral[-1][0], spiral[-1][1]), (spiral[-2][0], spiral[-2][1])}   # headroom over the two top steps
    s.at(1, 'shell')
    for x, z in posts:
        s.put(x, 0, z, 'stone')                                    # cobblestone footings (quality check round 2)
        for y in range(1, 8):
            s.put(x, y, z, 'frame', axis='y')
    for x in range(1, 5):                                          # platform (the tower top)
        for z in range(1, 5):
            if (x, z) not in posts and (x, z) not in holes:
                s.put(x, floor_y, z, 'plank2')
    hip_ring(s, 0, 0, 5, 5, 7, 'dark_stairs')                      # roof: dark eaves over the rail, then the cap
    hip_ring(s, 1, 1, 4, 4, 8, 'roof_stairs')
    s.fill(2, 8, 2, 3, 8, 3, 'roof_slab', type='top', waterlogged='false')
    s.at(1, 'fittings')
    for y, (x, z, facing) in enumerate(spiral):
        s.put(x, y, z, 'oak_stairs', facing=facing, shape='straight', **STAIR)
    rail = {(x, z) for x in range(1, 5) for z in range(1, 5) if (x in (1, 4) or z in (1, 4)) and (x, z) not in posts}
    for x, z in rail:                                              # fence rail round the platform, joined up
        joins = lambda dx, dz: (x + dx, z + dz) in rail or (x + dx, z + dz) in posts
        s.put(x, floor_y + 1, z, 'post', north=joins(0, -1), south=joins(0, 1), west=joins(-1, 0), east=joins(1, 0),
              waterlogged='false')
    s.put(2, 1, 4, 'wall_torch', facing='east')                   # light in the stairwell, on the back left post
    s.put(1, floor_y + 2, 2, 'torch')                              # light on the platform, on the rail
    s.put(2, 7, 2, 'chain', axis='y')                              # lantern on a chain under the roof
    s.put(2, 6, 2, 'lantern', hanging='true', waterlogged='false')
    s.put(5, 0, 1, 'hay_block', axis='y')                          # supplies at the foot: hay, a barrel, a bush
    s.put(5, 0, 2, 'barrel', facing='up', open='false')
    s.put(0, 0, 3, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    for x, z in ((1, 2), (1, 3), (4, 2), (4, 3)):                  # rails between the posts on both sides (round 3: L1)
        s.put(x, 2, z, 'stripped_spruce_log', axis='z')
    s.at(2, 'flourish2')
    s.put(2, floor_y + 2, 1, 'campfire', facing='south', lit='false', signal_fire='false', waterlogged='false')  # brazier on the rail
    s.put(0, floor_y + 1, 2, 'white_wall_banner', facing='west')  # banner hung on the west rail
    s.decorate(0, floor_y + 1, 2, 'banner_pattern', banner='white_wall_banner', note='kingdom colours')
    s.at(3, 'flourish3')
    s.put(3, floor_y + 2, 4, 'lantern', hanging='false', waterlogged='false')
    for x in (2, 3):                                               # back rail between the posts
        s.put(x, 2, 4, 'stripped_spruce_log', axis='x')
    for x, z in ((0, 1), (5, 4), (0, 4)):                          # moss at the post feet
        s.put(x, 0, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='watchtower', name='Watchtower', tier=2, zone='wall', role='defense', group='watchtower',
                   terrain='ground_level', door=None, make=watchtower,
                   description='Log watchtower: spiral stair inside four posts up to a railed lookout (floor at y 4), hip roof; '
                               '4 x 4 core, not 3 x 3 (a walkable stair needs it).')]
