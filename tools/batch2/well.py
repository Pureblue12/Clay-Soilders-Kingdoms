"""Well (tier 1, look A), revision from Carlton's note on v5 ("still not circular and have the rim only be 1 block
tall") and the quality check on v6 (the 5 x 5 cut-corner outline still read as a square from the top, the barrel and
the tub filled the cut corners, level 1 was bare, the roof did not overhang its posts).

- Sunk (ground_layer=0, like the garden, commons and stables): layer y = 0 replaces the top grass block, so the water
  sits level with the grass inside a stone lining and only ONE course of stone (y = 1) stands above the grass.
- Round: the rim is a 7-across pixel circle (3 blocks on each flat side, two steps at every corner), the roundest
  outline a 1-thick ring can have at this size; the pool inside is a round 21 blocks. Every prop stands on the grass
  apart from the ring (none touches it along a side), so nothing squares the outline off.
- The roof is a narrow spruce gable over the axle that overhangs the posts by one block on all sides."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
OX = 1                                                          # the ring's x offset in the 9-wide box (roof overhang)
CIRCLE = ['..XXX..',
          '.X...X.',
          'X.....X',
          'X.....X',
          'X.....X',
          '.X...X.',
          '..XXX..']
RING = [(x + OX, z) for z, row in enumerate(CIRCLE) for x, ch in enumerate(row) if ch == 'X']
POOL = [(x + OX, z) for z, row in enumerate(CIRCLE) for x in range(7)
        if row[x] == '.' and 'X' in row[:x] and 'X' in row[x:]]
POSTS = ((OX, 3), (OX + 6, 3))
CX = OX + 3                                                     # the well's middle (x), z = 3
G = 1                                                           # the first layer above the grass
MOSSY = {(OX + 2, 0), (OX + 6, 2), (OX + 1, 5), (OX, 4), (OX + 4, 6)}


def well():
    """L1: the working well (round lining and one-course rim, two dark oak posts, the windlass axle and its chain down
    to the water), a bench of two stairs and a barrel on the grass, poppies. L2: posts up to a spruce gable with a
    dark slab ridge and one block of overhang all round, gable brackets, corbels under the axle, the crank, a pulley
    and a lantern. L3: a water tub, a flower pot on the barrel, moss on the rim, two more lanterns, flowers."""
    s = Structure('well', (9, 6, 7), palette='oak_a2')
    s.at(1, 'shell')
    for x, z in RING:                                           # in the ground: the stone lining, flush with the grass
        s.put(x, 0, z, 'moss' if (x, z) in MOSSY else 'stone')
    for x, z in POOL:
        s.decorate(x, 0, z, 'water', note='well water, sunk in the ground')
    for x, z in RING:                                           # the rim: ONE round course above the grass
        if (x, z) not in POSTS:
            s.put(x, G, z, 'moss' if (x, z) in ((OX + 1, 1), (OX + 5, 5), (OX + 3, 6)) else 'stone')
    for x, z in POSTS:                                          # posts on the rim, left and right, the axle between
        for y in (G, G + 1, G + 2):
            s.put(x, y, z, 'dark_frame', axis='y')
    for x in range(OX + 1, OX + 6):
        s.put(x, G + 2, 3, 'dark_frame', axis='x')
    s.at(1, 'fittings')
    for y in (G, G + 1):                                        # the rope down to the water
        s.put(CX, y, 3, 'chain', axis='y')
    s.put(8, G, 5, 'barrel', facing='up', open='false')         # a barrel on the grass, back right
    for z in (0, 1):                                            # a bench on the grass, front left, facing the well
        s.put(0, G, z, 'spruce_stairs', facing='west', **STAIR)
    for x, z in ((0, 6), (8, 0), (7, 0), (1, 6), (8, 6), (2, 0)):   # poppies on the grass
        s.put(x, G, z, 'poppy')
    s.put(0, G, 2, 'spruce_fence'); s.put(0, G + 1, 2, 'oak_pressure_plate')   # a bucket stand at the bench's end

    s.at(2, 'flourish2')
    for x, z in POSTS:                                          # posts up to the ridge
        s.put(x, G + 3, z, 'dark_frame', axis='y')
    for x in range(0, 9):                                       # spruce slopes and a dark slab ridge, one block wider
        for z, f in ((2, 'south'), (4, 'north')):               # than the posts on every side
            s.put(x, G + 3, z, 'roof_stairs', facing=f, **STAIR)
        s.put(x, G + 4, 3, 'dark_oak_slab', type='bottom', waterlogged='false')
    for x in (0, 8):                                            # the gable ends under the ridge: upside-down stairs
        s.put(x, G + 3, 3, 'dark_stairs', facing='west' if x == 0 else 'east', half='top', shape='straight',
              waterlogged='false')
    for x, _ in POSTS:                                          # gable brackets under the slopes beside the posts
        for z, f in ((2, 'north'), (4, 'south')):
            s.put(x, G + 2, z, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    for x, f in ((OX + 1, 'west'), (OX + 5, 'east')):           # corbels under the axle beside the posts
        s.put(x, G + 1, 3, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    s.put(OX, G + 1, 2, 'lever', face='wall', facing='north', powered='false')   # the crank on the left post
    s.put(CX, G + 3, 3, 'roof_slab', type='top', waterlogged='false')            # pulley block over the axle
    s.put(OX + 6, G + 1, 2, 'lantern', **HANGING)               # under the front right bracket

    s.at(3, 'flourish3')
    s.put(0, G, 5, 'cauldron'); s.decorate(0, G, 5, 'water_cauldron', note='water tub on the grass, left')
    s.decorate(0, G, 5, 'item', item='minecraft:bucket', note='a bucket by the tub')
    s.put(8, G + 1, 5, 'flower_pot'); s.decorate(8, G + 1, 5, 'potted_plant', plant='cornflower')   # on the barrel
    for x, z in ((OX + 2, 0), (OX + 4, 0), (OX + 2, 6), (OX + 6, 4)):   # moss on the rim
        s.put(x, G + 1, z, 'moss_carpet')
    for x, _ in POSTS:                                          # lanterns under the back brackets
        s.put(x, G + 1, 4, 'lantern', **HANGING)
    for x, z, f in ((8, 2, 'cornflower'), (8, 4, 'oxeye_daisy'), (8, 1, 'dandelion'), (1, 0, 'azure_bluet'),
                    (7, 6, 'oxeye_daisy')):
        s.put(x, G, z, f)
    return s


STRUCTURES = [dict(path='well', name='Well', tier=1, zone='core', role='water', group='well', terrain='flexible',
                   door=None, make=well, ground_layer=0,
                   changes='For your note ("still not circular", "rim only 1 block tall"): the rim is one block '
                           'tall (the well is sunk, the water level with the grass) and is now a real circle, 7 '
                           'across with two steps at every corner, round a round pool. The barrel, a new bench and the '
                           'tub stand on the grass apart from the ring, so nothing squares it off. The roof overhangs '
                           'its posts by one block all round; level 1 gets a bench and poppies, level 3 more flowers.',
                   description='Round stone well sunk into the ground: a one-course circular cobblestone rim round a '
                               'round pool, dark oak posts and windlass with a chain, a bench and a barrel on the grass; '
                               'L2 adds the spruce gable roof, crank, pulley and a lantern, L3 a water tub, a flower '
                               'pot, moss, lanterns and flowers. 9 x 7 x 6 (one layer in the ground), water by the '
                               'decoration step.')]
