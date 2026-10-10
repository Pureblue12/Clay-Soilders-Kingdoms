"""Well (tier 1, look A), revision from Carlton's note on v3 ("too big and the base is square; I'd like it to be
circular, please find a middle ground"): v2 was a 3 x 3 basin under a 5 x 5 roof, v3 a 5 x 5 square basin under a
7 x 7 roof. This one is a round basin in a 5 x 5 box: an octagon of cobblestone with the four corners cut and
rounded off by corner stairs, a second cobblestone course on top, and v3's posts, windlass and gable roof at 5 wide."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
# the octagon: the 5 x 5 outline without its corners (x, z)
RING = [(1, 0), (2, 0), (3, 0), (4, 1), (4, 2), (4, 3), (3, 4), (2, 4), (1, 4), (0, 3), (0, 2), (0, 1)]
# corner stairs rising towards the centre (same corners as a hip roof): they round the cut corners off
# the inner diagonals are stone too, so the pool is round (a plus of five) as well
INNER = [(1, 1), (3, 1), (3, 3), (1, 3)]
POOL = [(2, 1), (1, 2), (2, 2), (3, 2), (2, 3)]
CORNERS = {(0, 0): 'south', (4, 0): 'west', (4, 4): 'north', (0, 4): 'east'}


def well():
    """Well: L1 is the working well (round basin, two dark oak posts on the rim, the windlass axle and its chain down
    to the water); L2 roofs it (posts up to a spruce gable with dark oak eaves and ridge) and finishes the windlass
    (crank, corbels under the axle, gable brackets); L3 adds flowers, a water tub, moss and lanterns. Water: decoration
    step."""
    s = Structure('well', (5, 6, 5), palette='oak_a2')
    s.at(1, 'shell')
    mossy = {(0, 3), (3, 0), (4, 2), (1, 4)}
    for x, z in RING + INNER:                                  # lower course: cobblestone, a little moss
        s.put(x, 0, z, 'moss' if (x, z) in mossy else 'stone')
    for (x, z), facing in CORNERS.items():                     # cut corners, rounded by outer-corner stairs
        s.put(x, 0, z, 'stone_stairs', facing=facing, half='bottom', shape='outer_left', waterlogged='false')
    for x, z in POOL:
        s.decorate(x, 0, z, 'water', note='well water')
    for x, z in RING:                                          # upper course: the round rim
        if (x, z) not in ((2, 0), (2, 4)):
            s.put(x, 1, z, 'moss' if (x, z) in ((0, 1), (4, 3)) else 'stone')
    for z in (0, 4):                                           # posts on the rim, front and back, the windlass axle
        for y in (1, 2, 3):                                    # between them (the gable faces the front)
            s.put(2, y, z, 'dark_frame', axis='y')
    for z in (1, 2, 3):
        s.put(2, 3, z, 'dark_frame', axis='z')
    s.at(1, 'fittings')
    for y in (1, 2):                                           # the rope down to the water
        s.put(2, y, 2, 'chain', axis='y')
    s.at(2, 'flourish2')
    for z in (0, 4):                                           # posts up to the ridge
        s.put(2, 4, z, 'dark_frame', axis='y')
    for z in range(0, 5):                                      # low gable roof: dark eaves, spruce slope, dark ridge
        for x, y in ((0, 3), (1, 4), (3, 4), (4, 3)):
            s.put(x, y, z, 'dark_stairs' if x in (0, 4) else 'roof_stairs', facing='east' if x < 2 else 'west', **STAIR)
        s.put(2, 5, z, 'dark')
    for z in (0, 4):                                           # gable brackets: upside-down stairs under the slope
        for x, f in ((1, 'west'), (3, 'east')):
            s.put(x, 3, z, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    for z, f in ((1, 'north'), (3, 'south')):                  # corbels under the axle beside the posts
        s.put(2, 2, z, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    s.put(3, 2, 0, 'lever', face='wall', facing='east', powered='false')    # the windlass crank on the front post
    s.put(2, 4, 2, 'roof_slab', type='top', waterlogged='false')            # pulley block over the axle
    s.at(3, 'flourish3')
    for (x, z), plant in zip(((0, 0), (4, 0)), ('poppy', 'dandelion')):     # flower pots on the front corners
        s.put(x, 1, z, 'flower_pot'); s.decorate(x, 1, z, 'potted_plant', plant=plant)
    s.put(0, 1, 4, 'flowering_azalea_leaves', persistent='true', distance='1', waterlogged='false')   # flowering bush
    s.put(4, 1, 4, 'cauldron'); s.decorate(4, 1, 4, 'water_cauldron', note='water tub on the rim')
    s.decorate(4, 1, 4, 'item', item='minecraft:bucket', note='a bucket by the tub')
    for x, z in ((1, 0), (4, 1), (3, 4), (0, 3), (4, 2)):      # moss on the rim
        s.put(x, 2, z, 'moss_carpet')
    for x, z in ((0, 0), (4, 0)):                              # lanterns under the front eaves at the corners
        s.put(x, 2, z, 'lantern', hanging='true', waterlogged='false')
    return s


STRUCTURES = [dict(path='well', name='Well', tier=1, zone='core', role='water', group='well', terrain='flexible',
                   door=None, make=well,
                   changes='Smaller and round: a 5 x 5 octagon basin with cut, stair-rounded corners and a round pool '
                           '(was a 5 x 5 square basin under a 7 x 7 roof), keeping the windlass and a 5-wide gable roof facing the front; '
                           'L1 is the working well, L2 adds the roof and windlass crank, L3 flowers, a water tub, moss '
                           'and lanterns.',
                   description='Round stone well: octagon cobblestone basin with stair-rounded corners, '
                               'dark oak posts and windlass with a chain; L2 adds the spruce gable roof and crank, '
                               'L3 flowers, a water tub, moss and lanterns. 5 x 5 x 6, water by the decoration step.')]
