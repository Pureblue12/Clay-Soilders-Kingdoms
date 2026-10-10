"""Road piece (tier 1), revised from Carlton's notes: l1 "boring, add different dirt and blocks, be creative"; l2 "missing
textures; also whatever is on the side of it is sunk into ground"; v2 (QC): the road stood a full block above the grass.

The mod builds layer 0 on top of the ground and never digs, so a road of full blocks always stands proud. This road is
slab height: half a block, like a real paving of flat stones. The earth stretches between the stones (coarse dirt,
gravel, dirt path) can't be slabs and builders can't place dirt paths, so they are 'ground_replace' decorations: the
decoration step swaps the top ground block under that position (until then the grass shows there).

The missing textures in road l2 v1 were its edge posts: minecraft:cobblestone_wall stored with no properties, a state
the Studio has no model for (placeholder "?" posts). This road has no such blocks.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')

# The fixed pattern, one string per column x (2..6), one character per row z (0..5); it tiles end to end.
#   S cobblestone slab (level 1, KNOWN-OK)
#   c coarse dirt, g gravel, p dirt path: earth, a ground_replace decoration (level 1)
#   M mossy cobblestone slab, T stone slab, A andesite slab, U mud brick slab, F tuff slab (level 2; the ground under
#     them is first marked gravel in level 1)
PATTERN = {
    2: 'SSSSSS',      # curb
    3: 'gAcSUc',
    4: 'SpTSpM',      # crown: set stones and a dirt path line
    5: 'MgSFcT',
    6: 'SSSSSS',      # curb
}
LEVEL2 = {'M': 'mossy_cobblestone_slab', 'T': 'stone_slab', 'A': 'andesite_slab', 'U': 'mud_brick_slab', 'F': 'tuff_slab'}
EARTH = {'c': 'coarse_dirt', 'g': 'gravel', 'p': 'dirt_path'}


def road(length=6):
    """9 wide: two verge strips each side (x 0..1 and 7..8) and a 5-wide road (x 2..6) of flat stones and earth from
    PATTERN.
    Level 1: unbroken cobblestone slab curbs and set stones, the earth between them (ground_replace decorations), poppies,
    twigs (oak buttons) and a lamp post (spruce fence and lantern) on the verges.
    Level 2: the paving filled out with mossy cobblestone, stone, andesite, mud brick and tuff slabs; moss carpet
    patches and pebbles (stone buttons) along the edges.
    Level 3: wildflowers, grass tufts and ferns along both verges and a mossy milestone."""
    s = Structure('road', (9, 3, length))
    s.at(1, 'shell')
    for x, row in PATTERN.items():
        for z in range(length):
            ch = row[z % 6]
            if ch == 'S':
                s.put(x, 0, z, 'cobblestone_slab', **SLAB)
            else:
                s.decorate(x, 0, z, 'ground_replace', block=EARTH.get(ch, 'gravel'),
                           note='swap the top ground block under this position')
    s.at(1, 'fittings')
    lamp = (1, 2)
    s.put(lamp[0], 0, lamp[1], 'spruce_fence'); s.put(lamp[0], 1, lamp[1], 'spruce_fence')
    s.put(lamp[0], 2, lamp[1], 'lantern', **LANTERN)
    for x, z in ((1, 4), (7, 1), (7, 5)):
        s.put(x, 0, z, 'poppy')
    for x, z in ((1, 0), (7, 3)):                                   # twigs on the verge
        s.put(x, 0, z, 'oak_button', face='floor', facing='north' if z == 0 else 'east', powered='false')

    s.at(2, 'flourish2')
    for x, row in PATTERN.items():
        for z in range(length):
            ch = row[z % 6]
            if ch in LEVEL2:
                put_id(s, x, 0, z, LEVEL2[ch], **SLAB)
    for x, z in ((1, 1), (1, 5), (0, 5), (7, 2), (7, 4), (8, 4)):  # moss creeping in from the verges
        s.put(x, 0, z, 'moss_carpet')
    for x, z in ((1, 3), (7, 0)):                                   # pebbles
        s.put(x, 0, z, 'stone_button', face='floor', facing='north', powered='false')

    s.at(3, 'flourish3')
    for x, row in ((0, 'fGcdaG'), (8, 'Gcf.da')):                  # wildflowers and grass in the gaps of the verges
        for z in range(length):
            plant = {'G': 'short_grass', 'f': 'fern', 'd': 'dandelion', 'c': 'cornflower', 'a': 'azure_bluet'}.get(row[z % 6])
            if plant:
                s.add(x, 0, z, plant)
    put_id(s, 8, 0, 3, 'mossy_cobblestone_wall')                    # a milestone
    s.put(8, 1, 3, 'stone_button', face='floor', facing='north', powered='false')
    return connect(s)


STRUCTURES = [dict(path='road', name='Road piece (dirt)', tier=1, zone='inner', role='utility', group='road',
                   terrain='flexible', door=None, make=road,
                   description='A slab-height road (half a block over the grass, never a full block): cobblestone slab '
                               'curbs and set stones with earth between them; the earth (coarse dirt, gravel, a dirt path '
                               'line) is a ground_replace decoration that swaps the top ground block, so the Studio shows '
                               'grass there. Level 2 fills the paving with mossy cobblestone, stone, andesite, mud brick '
                               'and tuff slabs and adds moss and pebbles; level 3 wildflowers, grass and a milestone. '
                               'Tiles end to end.',
                   changes='Slab height now (half a block, not a full block above the grass): a fixed pattern of '
                           'cobblestone, mossy, stone, andesite, mud brick and tuff slabs with earth stretches recorded '
                           'for the decoration step, plus poppies, twigs, moss, pebbles and a lamp post along the edges.')]
