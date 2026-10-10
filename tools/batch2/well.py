"""Well (tier 1, look A), quality check round 2: the batch-1 well (tools/make_batch1_structures.py, well()) was a
3 x 3 ring with a slab lid; this is a 7 x 7 well with a raised stone basin, two dark oak posts, a windlass beam and
a steep gable roof."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')


def well():
    """Well: a two-course 5 x 5 cobblestone basin (mossy in places) round a 3 x 3 pool, dark oak posts on the rim
    carrying a log beam (the windlass) with a chain down the middle, a crank on the right, and a spruce stair gable
    roof with dark oak eaves and ridge, one block of overhang all round. Water: decoration step. Round 3 widened the
    basin from 3 x 3: under the same roof the small basin looked top-heavy."""
    s = Structure('well', (7, 8, 7), palette='oak_a2')
    s.at(1, 'shell')
    mossy = {(1, 1, 4), (5, 1, 2), (2, 0, 1), (4, 1, 5)}
    for y in (0, 1):
        s.ring(1, 1, 5, 5, y, 'stone')
    for pos in mossy:
        s.remove(*pos); s.put(*pos, 'moss')
    for x in range(2, 5):
        for z in range(2, 5):
            s.decorate(x, 0, z, 'water', note='well water')
    for x in (1, 5):                                   # posts on the rim
        for y in (2, 3, 4, 5):
            s.put(x, y, 3, 'dark_frame', axis='y')
    for x in range(1, 6):                              # windlass beam
        s.put(x, 6, 3, 'dark_frame', axis='x')
    for x in range(0, 7):                              # roof: eaves, two slope rows, ridge
        for z, y in ((0, 4), (1, 5), (2, 6), (4, 6), (5, 5), (6, 4)):
            s.put(x, y, z, 'dark_stairs' if z in (0, 6) else 'roof_stairs', facing='south' if z < 3 else 'north', **STAIR)
        s.put(x, 7, 3, 'dark')
    s.at(1, 'fittings')
    for y in (3, 4, 5):                                # rope down to the water
        s.put(3, y, 3, 'chain', axis='y')
    s.put(6, 5, 3, 'post')                             # windlass crank
    s.at(2, 'flourish2')
    s.put(0, 6, 3, 'lantern', hanging='true', waterlogged='false')    # under the ridge at the left gable
    s.put(1, 2, 1, 'flower_pot'); s.decorate(1, 2, 1, 'potted_plant', plant='poppy')
    s.put(5, 2, 5, 'flower_pot'); s.decorate(5, 2, 5, 'potted_plant', plant='dandelion')
    s.put(6, 0, 2, 'barrel', facing='up', open='false')                # bucket store beside the well
    s.put(0, 0, 5, 'cauldron'); s.decorate(0, 0, 5, 'water_cauldron', note='drinking trough')
    s.at(3, 'flourish3')
    s.put(3, 0, 0, 'stone_slab', type='bottom', waterlogged='false')   # step up to the basin
    for x, z in ((0, 0), (6, 6), (0, 6)):                               # bushes at the corners
        s.put(x, 0, z, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.put(6, 0, 4, 'poppy'); s.put(2, 0, 0, 'dandelion')
    for x, z in ((3, 1), (2, 5), (1, 4), (5, 2)):
        s.put(x, 2, z, 'moss_carpet')                                   # moss on the rim
    return s


STRUCTURES = [dict(path='well', name='Well', tier=1, zone='core', role='water', group='well', terrain='flexible',
                   door=None, make=well,
                   description='Stone well: two-course cobblestone basin, dark oak posts and windlass beam with a chain, '
                               'steep spruce gable roof; 5 x 5 basin under a 7 x 7 roof, water by the decoration step.')]
