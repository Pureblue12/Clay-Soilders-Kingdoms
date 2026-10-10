"""Road piece (tier 1), revised from Carlton's notes: l1 "boring, add different dirt and blocks, be creative"; l2 "missing
textures; also whatever is on the side of it is sunk into ground".

The missing textures in road l2 v1 were its edge posts: minecraft:cobblestone_wall stored with no properties (no 'up',
no sides), a state the Studio has no model for, so it drew a placeholder post ("?" texture). This version has no
walls at all and every state is complete.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

SLAB = dict(type='bottom', waterlogged='false')


# The fixed pattern, one string per column (x), one character per row (z = 0..5); it tiles end to end.
#   g gravel, c coarse dirt, d dirt, C cobblestone, M mossy cobblestone (level 1, all KNOWN-OK)
#   a andesite, p packed mud (level 2)
PATTERN = {
    2: 'CMCCgC',      # curb
    3: 'cgcdcc',      # wheel rut
    4: 'dCcdMc',      # crown with set stones
    5: 'dcgccg',      # wheel rut
    6: 'CCMCgM',      # curb
    1: 'apppaa',      # shoulder (level 2)
    7: 'papaap',
}
BLOCK = {'g': 'gravel', 'c': 'coarse_dirt', 'd': 'dirt', 'C': 'cobblestone', 'M': 'mossy_cobblestone',
         'a': 'andesite', 'p': 'packed_mud'}


def road(length=6):
    """9 wide: a verge (x 0 and 8), a shoulder (x 1 and 7), a curb (x 2 and 6) and a 3-wide surface (x 3..5), all
    from PATTERN.
    Level 1: gravel and dirt wheel ruts, a coarse dirt crown with set cobblestones, mossy cobblestone curbs: a complete
    road from KNOWN-OK blocks.
    Level 2: shoulders flush with the road (andesite curbstones and packed mud) and stone slab stepping stones on the
    verge, half a block down to the grass.
    Level 3: grass tufts, ferns and flowers on the verges and a lamp post (spruce fence and lantern)."""
    s = Structure('road', (9, 3, length))
    s.at(1, 'shell')
    for x in (2, 3, 4, 5, 6):
        for z in range(length):
            s.put(x, 0, z, BLOCK[PATTERN[x][z % 6]])

    s.at(2, 'flourish2')
    for x in (1, 7):
        for z in range(length):
            s.put(x, 0, z, BLOCK[PATTERN[x][z % 6]])
    steps = {(0, 1), (0, 4), (8, 2), (8, 5)}          # stepping stones, three apart (they tile end to end)
    for x, z in sorted(steps):
        if z < length:
            put_id(s, x, 0, z, 'stone_slab', **SLAB)

    s.at(3, 'flourish3')
    lamp = (0, 3)
    s.put(lamp[0], 0, lamp[1], 'spruce_fence'); s.put(lamp[0], 1, lamp[1], 'spruce_fence')
    s.put(lamp[0], 2, lamp[1], 'lantern', hanging='false', waterlogged='false')
    plants = {0: 'Gf.L.G', 8: 'Fy.Gp.'}               # G short grass, f fern, F/y/p flowers (no plant on the steps)
    plant = {'G': 'short_grass', 'f': 'fern', 'F': 'cornflower', 'y': 'dandelion', 'p': 'poppy'}
    for x, row in plants.items():
        for z in range(length):
            ch = row[z % 6]
            if ch in plant and (x, z) not in steps and (x, z) != lamp:
                s.put(x, 0, z, plant[ch])
    return connect(s)


STRUCTURES = [dict(path='road', name='Road piece (dirt)', tier=1, zone='inner', role='utility', group='road',
                   terrain='flexible', door=None, make=road,
                   description='A 3-wide road of gravel ruts and a coarse dirt crown with set cobblestones between cobblestone '
                               'curbs; level 2 adds shoulders of andesite and packed mud with stone slab stepping stones; '
                               'level 3 grass, ferns, flowers and a lamp post. Tiles end to end.',
                   changes='More varied: gravel ruts, a coarse dirt and cobblestone crown, mossy cobblestone curbs and '
                           'andesite and packed mud shoulders in a fixed pattern, with stone slab stepping stones down to '
                           'the grass, flowers and a lamp; the edge posts that showed missing textures (cobblestone walls '
                           'stored without a state) are gone.')]
