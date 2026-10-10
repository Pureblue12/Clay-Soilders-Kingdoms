"""Commons / town green, level 1 (tier 2): a green at a road junction with a fire pit, a well and benches
(docs/STRUCTURES.md, "Commons / town green"). Layer y = 0 is level with the ground: paths and paving replace the top
ground block, everything else stands on it (y >= 1). Grass is the existing ground (nothing placed there)."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')
FLOWERS = ('poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet')   # fixed order, no randomness


def arm(s, x, z, facing):
    """A bench arm: an open trapdoor standing against the bench end (batch-1 bench, level 2)."""
    s.put(x, 1, z, 'shelf', facing=facing, half='bottom', open='true', powered='false', waterlogged='false')


def put_well(s, x0, z0):
    """The batch-1 well (3 x 3), one layer up: a cobblestone base in the ground, a rim on it, two wall posts, a slab
    roof and a chain; water one below the rim (decoration step). Returns its corner for the flourish."""
    s.ring(x0, z0, x0 + 2, z0 + 2, 0, 'stone')                    # base in the ground layer (holds the water)
    s.ring(x0, z0, x0 + 2, z0 + 2, 1, 'stone')                    # rim
    for x in (x0, x0 + 2):
        s.put(x, 2, z0 + 1, 'stone_wall'); s.put(x, 3, z0 + 1, 'stone_wall')
    s.fill(x0, 4, z0, x0 + 2, 4, z0 + 2, 'roof_slab', **SLAB)
    s.put(x0 + 1, 3, z0 + 1, 'chain', axis='y')
    s.put(x0, 2, z0, 'torch')                                     # on the rim corner facing the green
    s.decorate(x0 + 1, 0, z0 + 1, 'water', note='well water, one below the rim')


def commons(size=9, well_corner=(6, 6)):
    """Level 1: gravel spokes from the four edge midpoints (it sits on a road junction) to a fire pit in the middle,
    a rounded cobblestone apron round the pit, the batch-1 well in the back right corner, three benches facing the
    fire. Level 2: bench arms, lantern posts, flower beds behind the benches, pots on the well. Level 3: two standing
    banners at the entrance, a log pile, hedges and moss. Everything stays in the 9 x 9 box (the bigger plazas are
    later tiers)."""
    s = Structure('commons', (size, 5, size), palette='oak_a2')
    c, e = size // 2, size - 1                                    # center 4, last 8
    wx, wz = well_corner
    s.at(1, 'shell')
    for i in range(0, c - 1):                                     # four gravel spokes, 3 long each
        for x, z in ((c, i), (c, e - i), (i, c), (e - i, c)):
            s.put(x, 0, z, 'gravel')
    s.fill(c - 1, 0, c - 1, c + 1, 0, c + 1, 'stone')             # hearth stones under the fire pit
    for dx, dz in ((-1, -2), (1, -2), (-1, 2), (1, 2), (-2, -1), (-2, 1), (2, -1), (2, 1)):   # rounded apron
        s.put(c + dx, 0, c + dz, 'stone')
    s.at(1, 'fittings')
    s.ring(c - 1, c - 1, c + 1, c + 1, 1, 'stone_slab', **SLAB)   # the batch-1 fire pit, on its hearth stones
    s.remove(c, 1, c)
    s.put(c, 1, c, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')
    put_well(s, wx, wz)
    benches = [((1, 2), 1, 'north'), ((e - 2, e - 1), 1, 'north'), ((1, 2), e - 1, 'south')]   # backs away from the fire
    for xs, z, back in benches:
        for x in xs:
            s.put(x, 1, z, 'chair', facing=back, **STAIR)

    s.at(2, 'flourish2')
    for xs, z, back in benches:
        arm(s, xs[0] - 1, z, 'west'); arm(s, xs[1] + 1, z, 'east')
        bz = z - 1 if back == 'north' else z + 1                  # a flower bed behind the bench's back
        for i, x in enumerate(xs):
            s.put(x, 0, bz, 'soil'); s.put(x, 1, bz, FLOWERS[(x + bz) % len(FLOWERS)])
    for x, z in ((0, 0), (e, 0), (0, e)):                         # lantern posts on three corners
        s.put(x, 1, z, 'fence'); s.put(x, 2, z, 'lantern', **LANTERN)
    s.put(wx + 2, 2, wz, 'flower_pot'); s.decorate(wx + 2, 2, wz, 'potted_plant', plant='poppy')     # batch-1 well L2
    s.put(wx, 2, wz + 2, 'flower_pot'); s.decorate(wx, 2, wz + 2, 'potted_plant', plant='dandelion')
    s.put(wx + 2, 2, wz + 2, 'lantern', **LANTERN)

    s.at(3, 'flourish3')
    for x in (c - 1, c + 1):                                      # banners flanking the front entrance
        s.put(x, 1, 0, 'white_banner', rotation='8')          # facing the road (north)
        s.decorate(x, 1, 0, 'banner_pattern', banner='white_banner', note='kingdom colours')
    for x in (e - 1, e):                                          # firewood (bark logs) by the fire, before the well
        s.put(x, 1, c + 1, 'frame', axis='x')
    s.put(e, 2, c + 1, 'frame', axis='x')
    for x, z in ((0, 2), (0, 3), (e, 2), (e, 3)):                 # low hedges between the front benches and the side spokes
        s.put(x, 1, z, 'oak_leaves', **LEAVES)
    for x, z in ((wx + 1, wz), (wx + 1, wz + 2)):                 # batch-1 well L3 moss on the rim
        s.put(x, 2, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='commons', name='Commons (level 1)', tier=2, zone='inner', role='civic', group='commons',
                   terrain='flexible', door=None, make=commons,
                   description='A 9 x 9 village green on a road junction: gravel paths to a fire pit, the well, '
                               'three benches; later levels add lanterns, flower beds and banners.')]
