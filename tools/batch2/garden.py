"""Garden (tier 2): an ornamental garden with a gravel cross, raised flower beds, benches, a pergola and a tool chest
(docs/STRUCTURES.md, "Garden (levels 1 to 3)").

Ground rule (BuildSitePlanner never digs): layer y = 0 is the first layer ON TOP of the grass. So the whole garden is one
flat floor at y = 0 (gravel paths, soil beds, a spruce deck, cobblestone paving) framed by a step of cobblestone stairs,
and the beds are framed by a border of stripped logs; everything else stands on it (y >= 1). The layout is version 1's
(Carlton: "sunk into ground otherwise good"), moved in by the one-block step ring."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id
from batch2.commons import step_rim

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')


def garden(size=9):
    """Level 1: a step ring of cobblestone stairs; inside it the version 1 garden: a gravel cross with a cobblestone
    hub, two front beds of poppies framed by stripped spruce logs on their outer sides, a poppy bed and a bench at the
    back left, a spruce deck at the back right with a seat and the tool chest.
    Level 2: low hedges on the log borders round the front beds, lanterns on the hedge ends at the entrance, trapdoor
    arms on the bench, a bird bath (a cauldron on a wall post) in the back left bed.
    Level 3: a leaf-roofed pergola over the deck with a hanging lantern, a garden urn on a column, mixed flowers."""
    s = Structure('garden', (size, 5, size), palette='oak_a2')
    c, e = size // 2, size - 1                                    # center 4, last 8
    left_bed = [(x, z) for x in (2, 3) for z in (2, 3)]
    right_bed = [(x, z) for x in (5, 6) for z in (2, 3)]
    left_border = [(1, 1), (2, 1), (3, 1), (1, 2), (1, 3)]        # under the level 2 hedges
    right_border = [(5, 1), (6, 1), (7, 1), (7, 2), (7, 3)]
    back_bed = [(1, 5), (1, 6), (2, 5), (2, 6), (3, 5), (3, 6)]  # poppies and the bird bath
    deck = [(x, z) for x in (5, 6, 7) for z in (5, 6, 7)]
    s.at(1, 'shell')
    step_rim(s, 0, 0, e, e)
    for i in range(1, e):                                         # gravel cross, cobblestone hub
        put_id(s, c, 0, i, 'gravel' if i != c else 'cobblestone')
        if i != c:
            put_id(s, i, 0, c, 'gravel')
    for x, z in left_bed + right_bed + back_bed:
        put_id(s, x, 0, z, 'coarse_dirt')
    for x, z in left_border + right_border:                       # log border on the beds' outer sides
        put_id(s, x, 0, z, 'stripped_spruce_log', axis='x' if z == 1 else 'z')
    for x, z in deck:
        put_id(s, x, 0, z, 'spruce_planks')
    for x in (1, 2, 3):                                           # paving under the back bench
        put_id(s, x, 0, e - 1, 'cobblestone')
    s.at(1, 'fittings')
    for x, z in left_bed + right_bed:
        put_id(s, x, 1, z, 'poppy')
    for x, z in ((1, 5), (1, 6), (3, 5), (2, 6)):
        put_id(s, x, 1, z, 'poppy')
    for x in (2, 3):                                              # bench at the back left, looking into the garden
        put_id(s, x, 1, e - 1, 'oak_stairs', facing='south', **STAIR)
    put_id(s, 6, 1, e - 1, 'oak_stairs', facing='south', **STAIR)   # a seat on the deck
    put_id(s, e - 1, 1, 6, 'chest', facing='west', type='single', waterlogged='false')   # the tool chest

    s.at(2, 'flourish2')
    for x, z in left_border + right_border:                       # hedges on the log borders
        put_id(s, x, 1, z, 'oak_leaves', **LEAVES)
    for x in (c - 1, c + 1):                                      # lanterns on the hedge ends at the entrance
        put_id(s, x, 2, 1, 'lantern', **LANTERN)
    put_id(s, 2, 1, 5, 'cobblestone_wall')                        # bird bath in the back bed
    put_id(s, 2, 2, 5, 'cauldron')
    s.decorate(2, 2, 5, 'water', note='bird bath: fill the cauldron (water_cauldron, level 3)')

    s.at(3, 'flourish3')
    px0, px1, pz0, pz1 = 5, e - 1, 5, e - 1                       # pergola over the deck (x 5..7, z 5..7)
    for x, z in ((px0, pz0), (px1, pz0), (px0, pz1), (px1, pz1)):
        put_id(s, x, 1, z, 'oak_fence'); put_id(s, x, 2, z, 'oak_fence')
    s.fill(px0, 3, pz0, px1, 3, pz1, 'oak_leaves', **LEAVES)
    put_id(s, 6, 4, 6, 'oak_leaves', **LEAVES)
    put_id(s, 6, 2, 6, 'lantern', **HANGING)                       # a lantern hanging in the pergola
    for x, z, f in ((5, 6, 'allium'), (6, 5, 'cornflower')):      # potted flowers on the deck
        put_id(s, x, 1, z, 'flower_pot'); s.decorate(x, 1, z, 'potted_plant', plant=f)
    put_id(s, 1, 1, e - 1, 'polished_andesite')                   # a garden urn on a column by the bench
    put_id(s, 1, 2, e - 1, 'andesite_wall')
    put_id(s, 1, 3, e - 1, 'flower_pot'); s.decorate(1, 3, e - 1, 'potted_plant', plant='fern')
    put_id(s, 3, 1, 6, 'allium')                                  # mixed flowers in the beds
    return connect(s)


STRUCTURES = [dict(path='garden', name='Garden (level 1)', tier=2, zone='inner', role='civic', group='garden',
                   terrain='flexible', door=None, make=garden,
                   changes='Now sits on the grass instead of sinking into it: the same garden on one flat floor (gravel '
                           'paths, soil beds framed by logs, a spruce deck) with a step of cobblestone stairs all round; '
                           'small additions: a bird bath, a lantern under the pergola.',
                   description='A 9 x 9 ornamental garden on a low stepped floor: gravel cross, log-framed poppy beds, '
                               'a bench and a tool chest; later levels add hedges, a bird bath, a leafy pergola and an urn.')]
