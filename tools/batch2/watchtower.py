"""Watchtower (tier 2, look A): a log tower on four posts with a spiral stair inside and a roofed lookout platform."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
NO_VINE = dict(north='false', south='false', east='false', west='false', up='false')


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


def vine(s, x, y, z, side):
    s.put(x, y, z, 'vine', **{**NO_VINE, side: 'true'})


def watchtower(floor_y=4):
    """Watchtower: four oak log posts (4 x 4) round a 2 x 2 spiral of stairs, a spruce plank platform at floor_y
    with a fence rail, and a two-course hip roof (dark oak eaves, spruce course, flat plank top) overhanging by one.
    A 3 x 3 tower (the brief) has no room for a stair soldiers can walk (they need 2 free blocks above every step),
    hence 4 x 4. The roof sits one lower than v3 so that level 3's pointed cap and flag fit in 9 blocks.
    L2 turns the open base into a guardhouse (plank walls with arrow slits and shutters, an awning with an alarm bell
    over the door, banners, a brazier, supply crates); L3 crowns it (pointed cap with a flag, corner lanterns) and
    dresses it (ivy, shields by the door, a weapon rack)."""
    s = Structure('watchtower', (6, 9, 6), palette='oak_a2')
    posts = [(1, 1), (4, 1), (1, 4), (4, 4)]
    # spiral: (x, z, facing) of the steps at y = 0, 1, 2, ...; entered from the front at (2, 1); exit east at floor_y
    spiral = [(2, 2, "south"), (3, 2, "east"), (3, 3, "south"), (2, 3, "west"), (2, 2, "north")][:floor_y]
    holes = {(spiral[-1][0], spiral[-1][1]), (spiral[-2][0], spiral[-2][1])}   # headroom over the two top steps
    top = floor_y + 2                                              # eaves course (6)
    s.at(1, 'shell')
    for x, z in posts:
        s.put(x, 0, z, 'stone')                                    # cobblestone footings
        for y in range(1, top + 1):
            s.put(x, y, z, 'frame', axis='y')
    for x in range(1, 5):                                          # platform (the tower top)
        for z in range(1, 5):
            if (x, z) not in posts and (x, z) not in holes:
                s.put(x, floor_y, z, 'plank2')
    hip_ring(s, 0, 0, 5, 5, top, 'dark_stairs')                    # roof: dark eaves just over the rail, then spruce
    hip_ring(s, 1, 1, 4, 4, top + 1, 'roof_stairs')
    s.fill(2, top + 1, 2, 3, top + 1, 3, 'plank2')                 # flat top (level 3 puts the cap on it)
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
    s.put(2, top, 2, 'lantern', hanging='true', waterlogged='false')   # lantern under the roof
    s.put(5, 0, 1, 'hay_block', axis='y')                          # supplies at the foot: hay, a barrel, a bush
    s.put(5, 0, 2, 'barrel', facing='up', open='false')
    s.put(0, 0, 3, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    for x, z in ((1, 2), (1, 3), (4, 2), (4, 3)):                  # rails between the posts on three sides
        s.put(x, 2, z, 'stripped_spruce_log', axis='z')
    for x in (2, 3):
        s.put(x, 2, 4, 'stripped_spruce_log', axis='x')
    s.at(2, 'flourish2')
    # the base becomes a guardhouse: oak plank walls on the sides and back, an arrow slit in each, spruce shutters
    for (x, z) in ((1, 2), (1, 3), (4, 2), (4, 3), (3, 4)):
        s.put(x, 1, z, 'wall')
    for (x, z) in ((1, 2), (4, 3), (2, 4)):                        # top course; the gaps beside them are the slits
        s.put(x, 3, z, 'wall')
    s.put(0, 3, 2, 'trapdoor', facing='west', **OPEN)              # shutters folded back beside the slits
    s.put(5, 3, 3, 'trapdoor', facing='east', **OPEN)
    s.put(2, 3, 5, 'trapdoor', facing='south', **OPEN)
    for x in (2, 3):                                               # lintel and an awning over the door
        s.put(x, 3, 1, 'stripped_spruce_log', axis='x')
    for x in range(1, 5):
        s.put(x, 3, 0, 'roof_stairs', facing='south', shape='straight', **STAIR)
    s.put(3, 2, 0, 'bell', attachment='ceiling', facing='north', powered='false')   # alarm bell under the awning
    s.put(2, floor_y + 2, 1, 'campfire', facing='south', lit='false', signal_fire='false', waterlogged='false')  # brazier on the rail
    s.put(4, floor_y + 1, 0, 'red_wall_banner', facing='north')    # banners on the posts, under the eaves
    s.put(5, floor_y + 1, 4, 'red_wall_banner', facing='east')
    s.put(0, floor_y + 1, 1, 'white_wall_banner', facing='west')
    s.decorate(0, floor_y + 1, 1, 'banner_pattern', banner='white_wall_banner', note='kingdom colours')
    s.put(5, 1, 2, 'barrel', facing='up', open='false')            # supply crates stacked at the foot
    s.put(5, 0, 3, 'barrel', facing='east', open='false')
    s.put(5, 0, 4, 'chest', facing='west', type='single', waterlogged='false')
    s.put(5, 1, 1, 'spruce_slab', type='bottom', waterlogged='false')   # a board on the hay: the quartermaster's
    s.at(3, 'flourish3')
    for (x, z), f in (((2, 3), 'east'), ((3, 3), 'north'), ((2, 2), 'south')):   # pointed cap, flag in the fourth corner
        s.put(x, top + 2, z, 'dark_stairs', facing=f, half='bottom', shape='outer_left', waterlogged='false')
    s.put(3, top + 2, 2, 'red_banner', rotation='10')
    s.decorate(3, top + 2, 2, 'banner_pattern', banner='red_banner', note='kingdom flag')
    for x, z in ((0, 0), (5, 0), (5, 5), (0, 5)):                  # lanterns hung under the eave corners
        s.put(x, top - 1, z, 'lantern', hanging='true', waterlogged='false')
    for y in (1, 2, 3):                                            # ivy up the back posts and the front left post
        vine(s, 5, y, 4, 'west')
        vine(s, 0, y, 4, 'east')
    for y in (1, 2):
        vine(s, 0, y, 1, 'east')
    for x in (1, 4):                                               # shields (small banners) beside the door
        s.put(x, 2, 0, 'blue_wall_banner', facing='north')
        s.decorate(x, 2, 0, 'banner_pattern', banner='blue_wall_banner', note='shield device')
    for z in (0, 1):                                               # spear rack at the front left: a fence rail
        s.put(0, 0, z, 'spruce_fence')                             # with spears (lightning rods) standing in it
        s.put(0, 1, z, 'lightning_rod', facing='up', powered='false', waterlogged='false')
    s.decorate(0, 0, 0, 'item_frame', item='minecraft:iron_sword', note='weapons on the rack')
    s.put(3, floor_y + 2, 4, 'lantern', hanging='false', waterlogged='false')   # lantern on the back rail
    for x, z in ((0, 2), (5, 5)):                                  # moss at the feet
        s.put(x, 0, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='watchtower', name='Watchtower', tier=2, zone='wall', role='defense', group='watchtower',
                   terrain='ground_level', door=None, make=watchtower,
                   changes='More detail on L2 and L3: L2 boards the base into a guardhouse with arrow slits and shutters, '
                           'an awning with an alarm bell over the door, banners, a brazier and supply crates; L3 adds a '
                           'pointed roof cap with a flag, corner lanterns, ivy, shields by the door and a weapon rack '
                           '(the roof sits one lower so the cap fits in 9).',
                   description='Log watchtower: spiral stair inside four posts up to a railed lookout (floor at y 4), hip roof; '
                               'L2 walls the base with arrow slits, awning and bell; L3 adds a pointed cap with a flag. '
                               '4 x 4 core, not 3 x 3 (a walkable stair needs it).')]
