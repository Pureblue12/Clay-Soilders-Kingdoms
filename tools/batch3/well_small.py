"""Well variant, small (tier 1, look A): a tiny stone well for a corner of the hamlet, a little sister of csr:well."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')


def well_small():
    """Small well in a 5 x 4 box: a ring of eight cobblestone blocks round one block of water (decoration step),
    centred at x 1..3, z 0..2, with a free row behind it for the level 3 tub and bushes.
    L1 (what builders put up first): the rim (cobblestone with two mossy blocks), two oak fence posts on the left and
    right rim, a fence axle across their tops and a chain down to the water.
    L2: a little spruce gable roof on the posts (dark oak ridge, trapdoor brackets), a water barrel by the right post, a bucket on the rim.
    L3: life round it: a lantern on the barrel, a water tub behind, flowers, a fern and a bush, moss on the rim."""
    s = Structure('well_small', (5, 6, 4), palette='oak_a')
    x0, x1, z0, z1 = 1, 3, 0, 2
    cx, cz = 2, 1
    s.at(1, 'shell')
    mossy = {(x0, z0), (x1, z1)}
    for x in range(x0, x1 + 1):                                # the rim: eight blocks round the water
        for z in range(z0, z1 + 1):
            if (x, z) != (cx, cz):
                s.put(x, 0, z, 'moss' if (x, z) in mossy else 'stone')
    s.decorate(cx, 0, cz, 'water', note='well water')
    for x in (x0, x1):                                         # posts on the left and right rim
        for y in (1, 2, 3):
            s.put(x, y, cz, 'fence')
    s.put(cx, 3, cz, 'fence')                                  # the windlass axle between the post tops
    s.at(1, 'fittings')
    s.put(cx, 2, cz, 'chain', axis='y')                        # the rope down to the water
    s.put(cx, 1, cz, 'chain', axis='y')

    s.at(2, 'flourish2')
    for x in range(x0, x1 + 1):                                # a little spruce gable roof on the posts,
        put_id(s, x, 4, z0, 'spruce_stairs', facing='south', **STAIR)   # dark oak ridge
        put_id(s, x, 4, z1, 'spruce_stairs', facing='north', **STAIR)
        put_id(s, x, 4, cz, 'dark_oak_planks')
        put_id(s, x, 5, cz, 'dark_oak_slab', type='bottom', waterlogged='false')
    for x in (x0, x1):                                         # brackets under the gable ends
        for z, f in ((z0, 'north'), (z1, 'south')):
            put_id(s, x, 3, z, 'spruce_trapdoor', facing=f, half='top', open='false', powered='false',
                   waterlogged='false')
    s.decorate(cx, 1, z0, 'item', item='minecraft:bucket', note='a bucket on the front rim')
    put_id(s, x1 + 1, 0, cz, 'barrel', facing='up', open='false')   # a water barrel by the right post

    s.at(3, 'flourish3')
    put_id(s, x1 + 1, 1, cz, 'lantern', hanging='false', waterlogged='false')   # a lantern on the barrel
    put_id(s, x1, 0, 3, 'cauldron')                            # a water tub behind the right post
    s.decorate(x1, 0, 3, 'water_cauldron', note='water tub')
    put_id(s, x0, 0, 3, 'oak_leaves', persistent='true', distance='1', waterlogged='false')   # a bush behind left
    put_id(s, 0, 0, z0, 'poppy'); put_id(s, 0, 0, z1, 'dandelion')       # flowers at the sides
    put_id(s, 4, 0, z0, 'azure_bluet'); put_id(s, 4, 0, z1, 'oxeye_daisy')
    put_id(s, cx, 0, 3, 'flower_pot'); s.decorate(cx, 0, 3, 'potted_plant', plant='red_tulip')
    put_id(s, 0, 0, 3, 'fern')
    for x, z in ((x0, z1), (x1, z0)):                          # moss on the rim
        put_id(s, x, 1, z, 'moss_carpet')
    connect(s)
    return s


STRUCTURES = [dict(path='well_small', name='Well variant (small)', tier=1, zone='inner', role='water',
                   group='well', terrain='flexible', door=None, make=well_small, changes='First version.',
                   description='Over budget: 49 blocks vs 10. Tiny stone well (3 x 3 rim): eight cobblestone blocks '
                               'round one block of water (decoration step), fence posts and axle, a chain; L2 a little '
                               'spruce gable roof and a water barrel; L3 a lantern, water tub, flowers and moss.')]
