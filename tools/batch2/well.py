"""Well (tier 1, look A), revision from Carlton's note on v5 ("still not circular and have the rim only be 1 block
tall"). v5 was a 5 x 5 box: an octagon with its corners filled by corner stairs (square from every side), two courses
of stone above the grass and a small plus of water in a thick stone block. This one:

- is sunk (ground_layer=0, like the garden, commons and stables): layer y = 0 replaces the top grass block, so the
  water sits in the ground inside a stone lining and only ONE course of stone (y = 1) stands above the grass;
- has a thin round rim: the 5 x 5 outline without its corners (3 + 1 + 3 + 1 ..., the roundest outline at 5 across)
  round a 3 x 3 pool; the four corner cells stay grass (no corner stairs), so no view shows a square.

Rim styles compared in renders (build/batch2/variants): 'open' (full stone, thin ring, 3 x 3 pool), 'plus' (stone on
the inner diagonals too, a plus of water: reads as a stone lump with a little water) and 'walls' (cobblestone walls:
the diagonal steps don't join, so it reads as broken posts). 'open' reads most like a round well."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
# the octagon: the 5 x 5 outline without its corners (x, z)
RING = [(1, 0), (2, 0), (3, 0), (4, 1), (4, 2), (4, 3), (3, 4), (2, 4), (1, 4), (0, 3), (0, 2), (0, 1)]
INNER = [(1, 1), (3, 1), (3, 3), (1, 3)]                       # the pool's corners
POOL = [(2, 1), (1, 2), (2, 2), (3, 2), (2, 3)]
POSTS = ((0, 2), (4, 2))
G = 1                                                           # the first layer above the grass


def well(shape='open'):
    """Well: L1 is the working well (a sunk round pool in a one-course round stone rim, two dark oak posts on the rim,
    the windlass axle and its chain down to the water, a barrel and poppies by the rim); L2 roofs it (posts up to a
    spruce gable with dark oak brackets and ridge) and finishes the windlass (crank, corbels under the axle, a pulley,
    a lantern under a bracket); L3 adds a flower pot, a water tub, moss on the rim, flowers and two more lanterns.
    Water: decoration step."""
    s = Structure('well', (5, 7, 5), palette='oak_a2')
    s.at(1, 'shell')
    lining = RING + (INNER if shape != 'open' else [])
    pool = POOL + (INNER if shape == 'open' else [])
    mossy = {(0, 3), (3, 0), (4, 1), (1, 4)}
    for x, z in lining:                                         # in the ground: the stone lining, flush with the grass
        s.put(x, 0, z, 'moss' if (x, z) in mossy else 'stone')
    for x, z in pool:
        s.decorate(x, 0, z, 'water', note='well water, sunk in the ground')
    for x, z in lining:                                         # the rim: ONE round course above the grass
        if (x, z) in POSTS:
            continue
        if shape == 'walls' and (x, z) in RING:
            s.put(x, G, z, 'stone_wall')
        else:
            s.put(x, G, z, 'moss' if (x, z) in ((0, 1), (4, 3)) else 'stone')
    for x, _ in POSTS:                                          # posts on the rim, left and right, the windlass axle
        for y in (G, G + 1, G + 2):                             # between them (the roof slopes to front and back,
            s.put(x, y, 2, 'dark_frame', axis='y')              # so the pool shows from the front)
    for x in (1, 2, 3):
        s.put(x, G + 2, 2, 'dark_frame', axis='x')
    s.at(1, 'fittings')
    for y in (G, G + 1):                                        # the rope down to the water
        s.put(2, y, 2, 'chain', axis='y')
    s.put(4, G, 0, 'barrel', facing='up', open='false')         # a barrel in the front right corner, by the rim
    for x, z in ((0, 0), (4, 4)):                               # poppies on the grass in two corners
        s.put(x, G, z, 'poppy')

    s.at(2, 'flourish2')
    for x, _ in POSTS:                                          # posts up to the ridge
        s.put(x, G + 3, 2, 'dark_frame', axis='y')
    for x in range(0, 5):                                       # a narrow, light roof over the axle: spruce slopes and
        for z, f in ((1, 'south'), (3, 'north')):               # a dark slab ridge; the rim stays open
            s.put(x, G + 3, z, 'roof_stairs', facing=f, **STAIR)
        s.put(x, G + 4, 2, 'dark_oak_slab', type='bottom', waterlogged='false')
    for x in (0, 4):                                            # gable brackets: upside-down stairs under the slope
        for z, f in ((1, 'north'), (3, 'south')):
            s.put(x, G + 2, z, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    for x, f in ((1, 'west'), (3, 'east')):                     # corbels under the axle beside the posts
        s.put(x, G + 1, 2, 'dark_stairs', facing=f, half='top', shape='straight', waterlogged='false')
    s.put(0, G + 1, 1, 'lever', face='wall', facing='north', powered='false')   # the windlass crank on the left post
    s.put(2, G + 3, 2, 'roof_slab', type='top', waterlogged='false')            # pulley block over the axle
    s.put(4, G + 1, 1, 'lantern', **HANGING)                    # lantern under the front right gable bracket

    s.at(3, 'flourish3')
    s.put(0, G, 4, 'cauldron'); s.decorate(0, G, 4, 'water_cauldron', note='water tub in the back left corner')
    s.decorate(0, G, 4, 'item', item='minecraft:bucket', note='a bucket by the tub')
    s.put(4, G + 1, 0, 'flower_pot'); s.decorate(4, G + 1, 0, 'potted_plant', plant='cornflower')   # pot on the barrel
    for x, z in ((1, 0), (3, 0), (3, 4), (1, 4)):              # moss on the rim
        s.put(x, G + 1, z, 'moss_carpet')
    for x in (0, 4):                                           # lanterns under the back gable brackets
        s.put(x, G + 1, 3, 'lantern', **HANGING)
    return s


STRUCTURES = [dict(path='well', name='Well', tier=1, zone='core', role='water', group='well', terrain='flexible',
                   door=None, make=well, ground_layer=0,
                   changes='For your note: the rim is now ONE block tall. The well is sunk into the ground (the water '
                           'is level with the grass, inside a stone lining) and only one course of stone stands above '
                           'it. Rounder: the corner stairs are gone (grass there now), the rim is a thin round ring '
                           'and the pool inside is bigger. Posts, roof, windlass and the per-level extras stay; poppies '
                           'by the rim on level 1, a flower pot on the barrel on level 3.',
                   description='Round stone well sunk into the ground: a one-course round cobblestone rim round a '
                               'pool, dark oak posts and windlass with a chain; L2 adds the spruce gable roof, crank, '
                               'pulley and a lantern, L3 a water tub, a flower pot, moss and lanterns. 5 x 5 x 7 (one '
                               'layer in the ground), water by the decoration step.')]
