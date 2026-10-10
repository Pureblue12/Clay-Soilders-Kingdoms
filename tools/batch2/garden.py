"""Garden, level 1 (tier 2): an ornamental 7 x 7 garden with a gravel cross, flower beds, benches and a tool chest
(docs/STRUCTURES.md, "Garden (levels 1 to 3)"). Layer y = 0 is level with the ground (paths and beds replace the top
ground block); everything else stands on it. Grass is the existing ground."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')


def garden(size=7):
    """Level 1: a gravel cross with a cobblestone hub (paths in from all four sides), two flower beds of poppies at the
    front, a bench facing a lawn edged with poppies at the back left, a seat and the tool chest at the back right.
    Level 2: low hedges round the front beds, a 2 x 2 pond in the back-left lawn (water by the
    decoration step), lanterns on the hedges at the entrance. Level 3: a leaf-roofed pergola over the back-right seat,
    a garden urn on a column, mixed flowers."""
    s = Structure('garden', (size, 4, size), palette='oak_a2')
    c, e = size // 2, size - 1                                    # center 3, last 6
    left_bed, right_bed = (1, 2), (c + 1, c + 2)                  # the beds' x columns, z 1..2
    pond = [(x, z) for x in (1, 2) for z in (c + 1, c + 2)]
    s.at(1, 'shell')
    for i in range(size):                                         # gravel cross
        s.put(c, 0, i, 'gravel' if i != c else 'stone')
        if i != c:
            s.put(i, 0, c, 'gravel')
    for xs in (left_bed, right_bed):
        for x in xs:
            for z in (1, 2):
                s.put(x, 0, z, 'soil')
    s.at(1, 'fittings')
    for xs in (left_bed, right_bed):
        for x in xs:
            for z in (1, 2):
                s.put(x, 1, z, 'flower')
    for x in (1, 2):                                              # bench at the back left, looking into the garden
        s.put(x, 1, e, 'chair', facing='south', **STAIR)
    for z in (c + 1, c + 2):                                      # a poppy strip beside the lawn (pond at level 2)
        s.put(0, 0, z, 'soil'); s.put(0, 1, z, 'flower')
    s.put(c + 2, 1, e, 'chair', facing='south', **STAIR)          # a seat at the back right
    s.put(e, 1, c + 2, 'chest', facing='west', type='single', waterlogged='false')   # the tool chest

    s.at(2, 'flourish2')
    hedges = [(0, 0), (1, 0), (2, 0), (0, 1), (0, 2), (e, 0), (e - 1, 0), (e - 2, 0), (e, 1), (e, 2)]
    for x, z in hedges:
        s.put(x, 1, z, 'oak_leaves', **LEAVES)
    for x in (c - 1, c + 1):                                      # lanterns on the hedge ends at the entrance
        s.put(x, 2, 0, 'lantern', **LANTERN)
    for x, z in pond:
        s.decorate(x, 0, z, 'water', note='garden pond, replaces one ground block')

    s.at(3, 'flourish3')
    px0, px1, pz0, pz1 = c + 1, e, c + 1, e                       # pergola over the back-right corner (x 4..6, z 4..6)
    for x, z in ((px0, pz0), (px1, pz0), (px0, pz1), (px1, pz1)):
        s.put(x, 1, z, 'fence'); s.put(x, 2, z, 'fence')
    s.add_ring(px0, pz0, px1, pz1, 3, 'oak_leaves', **LEAVES)
    s.put(0, 1, e, 'polished_andesite')                           # a garden urn on a column, back left corner
    s.put(0, 2, e, 'andesite_wall', up='true', north='none', east='none', south='none', west='none', waterlogged='false')
    s.put(0, 3, e, 'flower_pot'); s.decorate(0, 3, e, 'potted_plant', plant='fern')
    for x, z, f in ((px0, pz0 + 1, 'allium'), (px0 + 1, pz0, 'cornflower')):   # flowers under the pergola's front
        s.put(x, 0, z, 'soil'); s.put(x, 1, z, f)
    return s


STRUCTURES = [dict(path='garden', name='Garden (level 1)', tier=2, zone='inner', role='civic', group='garden',
                   terrain='flexible', door=None, make=garden,
                   description='A 7 x 7 ornamental garden: gravel cross, poppy beds, benches and a tool chest; later '
                               'levels add hedges, a pond and a leafy pergola.')]
